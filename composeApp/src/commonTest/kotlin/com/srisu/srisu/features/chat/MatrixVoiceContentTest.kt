package com.srisu.srisu.features.chat

import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.chat.matrix.presentation.toDisplay
import com.srisu.srisu.features.couplechat.data.ChatContent
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.AudioInfo
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.FileBased.Audio
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Synthetic voice-note content only; no recordings, keys or message text. */
class MatrixVoiceContentTest {
    private val me = "@one:test"
    private val partner = "@two:test"
    private val json = createMatrixEventJson(matrixEventContentSerializerMappings)
    private val file = ChatRecoveryFixtures.file
    private fun audio(relatesTo: RelatesTo? = null) = Audio("Voice message", fileName = "voice.wav", file = file,
        info = AudioInfo(duration = 18_000, mimeType = "audio/wav", size = 4096), relatesTo = relatesTo)
    private fun encode(content: RoomMessageEventContent) = json.encodeToJsonElement(VoiceAwareRoomMessageSerializer, content).jsonObject
    private fun decode(content: JsonObject) = json.decodeFromJsonElement(VoiceAwareRoomMessageSerializer, content)
    private fun withVoice(block: JsonElement) = JsonObject(encode(audio()) + mapOf(MSC1767_AUDIO to block, MSC3245_VOICE to JsonObject(emptyMap())))
    private fun waveformOf(samples: JsonElement) = decode(withVoice(buildJsonObject { put("duration", 18_000); put("waveform", samples) })).attachment()!!.waveform
    private fun event(id: String, content: RoomMessageEventContent, sender: String = partner) = TimelineEvent(
        MessageEvent(MegolmEncryptedMessageEventContent(MegolmMessageValue("synthetic"), sessionId = "test"),
            EventId(id), UserId(sender), RoomId("!synthetic:test"), 1), Result.success(content))

    @Test fun sentVoiceContentCarriesBoundedScaledMsc1767Waveform() {
        val levels = List(250) { (it * 7) % 101 } + listOf(-20, 140)
        val content = encode(voiceMessageContent(audio(RelatesTo.Reply(RelatesTo.ReplyTo(EventId("\$original")))), levels))
        assertEquals("m.audio", content["msgtype"]?.jsonPrimitive?.content)
        assertEquals("Voice message", content["body"]?.jsonPrimitive?.content)
        assertEquals(file.url, content["file"]?.jsonObject?.get("url")?.jsonPrimitive?.content)
        assertEquals(18_000, content["info"]?.jsonObject?.get("duration")?.jsonPrimitive?.int)
        assertEquals("\$original", content["m.relates_to"]?.jsonObject?.get("m.in_reply_to")?.jsonObject?.get("event_id")?.jsonPrimitive?.content)
        assertEquals(JsonObject(emptyMap()), content[MSC3245_VOICE])
        val voice = content[MSC1767_AUDIO]!!.jsonObject
        assertEquals(18_000, voice["duration"]?.jsonPrimitive?.int)
        val waveform = voice["waveform"]!!.jsonArray.map { it.jsonPrimitive.int }
        assertEquals(VOICE_WAVEFORM_MAX_SAMPLES, waveform.size)
        assertTrue(waveform.all { it in 0..1024 })
        assertEquals(1024, waveform.max())
        assertEquals(listOf(0, 512, 1024), encodeVoiceWaveform(listOf(0, 50, 100)))
    }

    @Test fun emptyRecorderLevelsOmitTheWaveformButKeepTheVoiceMarker() {
        val content = encode(voiceMessageContent(audio(), emptyList()))
        assertNull(content[MSC1767_AUDIO]!!.jsonObject["waveform"])
        assertTrue(decode(content).attachment()!!.waveform.isEmpty())
    }

    @Test fun voiceContentRoundTripsThroughTheMessageSerializer() {
        val levels = List(40) { (it * 13) % 101 }
        val restored = decode(encode(voiceMessageContent(audio(), levels)))
        assertIs<RoomMessageEventContent.Unknown>(restored)
        val attachment = restored.attachment()!!
        assertEquals("voice", attachment.kind)
        assertEquals("audio/wav", attachment.mime)
        assertEquals(file, attachment.file)
        assertEquals(18_000, attachment.durationMs)
        assertEquals(levels, attachment.waveform)
        assertEquals("", restored.displayBody())
        // Persisted outbox/timeline JSON keeps the extension blocks byte-for-byte.
        assertEquals(encode(voiceMessageContent(audio(), levels)), encode(restored))
    }

    @Test fun olderClientsAndStockTrixnityStillSeePlainEncryptedAudio() {
        val sent = encode(voiceMessageContent(audio(), List(10) { 50 }))
        val stock = createMatrixEventJson().decodeFromJsonElement(RoomMessageEventContent.Serializer, sent)
        assertIs<Audio>(stock)
        assertEquals(file, stock.file)
        assertEquals(18_000, stock.info?.duration)
    }

    @Test fun legacyVoiceWithoutExtensionStaysTypedAndUsesPlaceholderBars() {
        val legacy = decode(encode(audio()))
        assertIs<Audio>(legacy)
        val attachment = legacy.attachment()!!
        assertEquals(18_000, attachment.durationMs)
        assertTrue(attachment.waveform.isEmpty())
    }

    @Test fun malformedMissingOrOversizedWaveformsAreIgnored() {
        assertTrue(waveformOf(JsonPrimitive("loud")).isEmpty())
        assertTrue(waveformOf(JsonArray(listOf(JsonPrimitive(10), JsonPrimitive("20")))).isEmpty())
        assertTrue(waveformOf(JsonArray(listOf(JsonPrimitive(10), JsonObject(emptyMap())))).isEmpty())
        assertTrue(waveformOf(JsonArray(listOf(JsonPrimitive(10), JsonNull))).isEmpty())
        assertTrue(waveformOf(JsonArray(List(VOICE_WAVEFORM_MAX_RECEIVED + 1) { JsonPrimitive(512) })).isEmpty())
        assertTrue(decode(withVoice(buildJsonObject { put("duration", 18_000) })).attachment()!!.waveform.isEmpty())
        // A non-object extension block is not a voice extension; the stock m.audio still renders.
        val wrongShape = JsonObject(encode(audio()) + (MSC1767_AUDIO to JsonPrimitive(5)))
        assertIs<Audio>(decode(wrongShape))
        assertTrue(decode(wrongShape).attachment()!!.waveform.isEmpty())
    }

    @Test fun receivedSamplesAreClampedScaledAndDownsampled() {
        assertEquals(listOf(0, 0, 50, 100, 100), waveformOf(JsonArray(listOf(-5, 0, 512, 1024, 5000).map(::JsonPrimitive))))
        assertEquals(listOf(50), waveformOf(JsonArray(listOf(JsonPrimitive(512.4)))))
        val long = waveformOf(JsonArray(List(300) { JsonPrimitive(if (it % 3 == 0) 1024 else 0) }))
        assertEquals(VOICE_WAVEFORM_MAX_SAMPLES, long.size)
        assertTrue(long.all { it == 100 })
    }

    @Test fun plaintextVoiceExtensionStaysUnavailable() {
        val plain = JsonObject(withVoice(buildJsonObject { put("duration", 1000) }) - "file" + ("url" to JsonPrimitive("mxc://test/plain")))
        val mapped = matrixTimelineMessages(listOf(event("\$plain", decode(plain))), me, partner, null).single()
        assertTrue(mapped.unavailable)
        assertNull(mapped.attachment)
    }

    @Test fun receivedVoiceMessageMapsWaveformIntoChatMedia() {
        val levels = ChatRecoveryFixtures.waveform
        val content = decode(encode(voiceMessageContent(audio(), levels)))
        val mapped = matrixTimelineMessages(listOf(event("\$voice", content)), me, partner, null).single()
        assertFalse(mapped.unavailable)
        assertEquals("", mapped.body)
        assertEquals(levels, mapped.attachment?.waveform)
        val room = MatrixChatRoomState("room", listOf(mapped), accountId = 1)
        val media = (mapped.toDisplay(room, emptyMap()).content as ChatContent.Media).items.single()
        assertEquals("voice", media.kind)
        assertEquals(levels, media.waveform)
    }

    @Test fun uploaderSwapsOnlyTheCachedFileUrl() = runTest {
        val content = voiceMessageContent(audio(), List(10) { 40 })
        val cacheUrl = file.url
        assertEquals(setOf(cacheUrl), VoiceMessageUriExtractor(content))
        val uploaded = VoiceMessageMediaUploader(MutableStateFlow(null), content) { url, _ ->
            assertEquals(cacheUrl, url); "mxc://test/uploaded"
        }
        assertEquals("mxc://test/uploaded", uploaded.raw["file"]?.jsonObject?.get("url")?.jsonPrimitive?.content)
        assertEquals(content.raw["file"]?.jsonObject?.get("key"), uploaded.raw["file"]?.jsonObject?.get("key"))
        assertEquals(content.raw[MSC1767_AUDIO], uploaded.raw[MSC1767_AUDIO])
        assertEquals(List(10) { 40 }, uploaded.attachment()?.waveform)
    }
}
