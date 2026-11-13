package com.srisu.srisu.features.chat.matrix

import de.connect2x.trixnity.client.media.mappings.EventContentMediaMapping
import de.connect2x.trixnity.client.media.mappings.EventContentMediaMappings
import de.connect2x.trixnity.client.media.mappings.EventContentMediaUploader
import de.connect2x.trixnity.client.media.mappings.EventContentUriExtractor
import de.connect2x.trixnity.client.media.mappings.default
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.FileBased.Audio
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.core.serialization.events.invoke
import de.connect2x.trixnity.core.serialization.events.messageOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.math.roundToInt

/*
 * Voice notes follow MSC3245 on top of MSC1767 extensible audio: an ordinary encrypted m.audio
 * event plus "org.matrix.msc1767.audio" { duration, waveform } and an "org.matrix.msc3245.voice"
 * marker. Trixnity's typed m.audio drops unknown keys, so these events travel as
 * RoomMessageEventContent.Unknown("m.audio") whose raw JSON keeps the extension blocks.
 */
internal const val MSC1767_AUDIO = "org.matrix.msc1767.audio"
internal const val MSC3245_VOICE = "org.matrix.msc3245.voice"
internal const val VOICE_WAVEFORM_MAX_SAMPLES = 100
/** Larger arrays are treated as malformed rather than processed. */
internal const val VOICE_WAVEFORM_MAX_RECEIVED = 1024
private const val MSC1767_WAVEFORM_MAX = 1024
private const val RECORDER_LEVEL_MAX = 100

private val voiceJson = createMatrixEventJson()

/** Keeps the loudest level of each bucket so short peaks survive downsampling. */
private fun List<Int>.peaks(max: Int): List<Int> {
    if (size <= max) return this
    return List(max) { i ->
        val from = i * size / max
        subList(from, maxOf(from + 1, (i + 1) * size / max)).max()
    }
}

/** Recorder levels (0–100) to at most [VOICE_WAVEFORM_MAX_SAMPLES] MSC1767 samples (0–1024). */
internal fun encodeVoiceWaveform(levels: List<Int>): List<Int> =
    levels.map { it.coerceIn(0, RECORDER_LEVEL_MAX) }.peaks(VOICE_WAVEFORM_MAX_SAMPLES)
        .map { (it * MSC1767_WAVEFORM_MAX + RECORDER_LEVEL_MAX / 2) / RECORDER_LEVEL_MAX }

/** MSC1767 samples back to bounded 0–100 levels; anything malformed yields no waveform. */
internal fun decodeVoiceWaveform(element: JsonElement?): List<Int> {
    val array = element as? JsonArray ?: return emptyList()
    if (array.size > VOICE_WAVEFORM_MAX_RECEIVED) return emptyList()
    val samples = array.map { sample ->
        val value = (sample as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
        if (value == null || !value.isFinite()) return emptyList()
        (value.coerceIn(0.0, MSC1767_WAVEFORM_MAX.toDouble()) * RECORDER_LEVEL_MAX / MSC1767_WAVEFORM_MAX).roundToInt()
    }
    return samples.peaks(VOICE_WAVEFORM_MAX_SAMPLES)
}

/** Wraps a typed m.audio with the MSC3245/MSC1767 voice blocks; older clients still see plain m.audio. */
internal fun voiceMessageContent(audio: Audio, waveform: List<Int>): RoomMessageEventContent.Unknown {
    val base = voiceJson.encodeToJsonElement(Audio.serializer(), audio).jsonObject
    val extension = buildJsonObject {
        put("duration", audio.info?.duration ?: 0L)
        encodeVoiceWaveform(waveform).takeIf { it.isNotEmpty() }?.let { samples ->
            put("waveform", JsonArray(samples.map(::JsonPrimitive)))
        }
    }
    val raw = JsonObject(base + mapOf(MSC1767_AUDIO to extension, MSC3245_VOICE to JsonObject(emptyMap())))
    return RoomMessageEventContent.Unknown(Audio.TYPE, audio.body, raw, audio.format, audio.formattedBody,
        audio.relatesTo, audio.mentions, audio.externalUrl)
}

/** Typed view of a voice-extended m.audio; null for every other content. */
internal fun RoomMessageEventContent.voiceAudio(): Audio? =
    (this as? RoomMessageEventContent.Unknown)?.takeIf { it.type == Audio.TYPE }
        ?.let { runCatching { voiceJson.decodeFromJsonElement(Audio.serializer(), it.raw) }.getOrNull() }

internal fun RoomMessageEventContent.voiceExtension(): JsonObject? =
    (this as? RoomMessageEventContent.Unknown)?.takeIf { it.type == Audio.TYPE }?.raw?.get(MSC1767_AUDIO) as? JsonObject

/** Content as Trixnity would type it without the voice extension. */
internal fun RoomMessageEventContent.typed(): RoomMessageEventContent = voiceAudio() ?: this

/** Keeps m.audio extension blocks that Trixnity's typed content would discard. */
internal object VoiceAwareRoomMessageSerializer : KSerializer<RoomMessageEventContent> {
    override val descriptor = RoomMessageEventContent.Serializer.descriptor

    override fun deserialize(decoder: Decoder): RoomMessageEventContent {
        require(decoder is JsonDecoder)
        val json = decoder.decodeJsonElement().jsonObject
        val content = decoder.json.decodeFromJsonElement(RoomMessageEventContent.Serializer, json)
        return if (content is Audio && (json[MSC1767_AUDIO] is JsonObject || json[MSC3245_VOICE] is JsonObject))
            RoomMessageEventContent.Unknown(Audio.TYPE, content.body, json, content.format, content.formattedBody,
                content.relatesTo, content.mentions, content.externalUrl)
        else content
    }

    override fun serialize(encoder: Encoder, value: RoomMessageEventContent) =
        RoomMessageEventContent.Serializer.serialize(encoder, value)
}

internal val matrixEventContentSerializerMappings: EventContentSerializerMappings =
    EventContentSerializerMappings.default + EventContentSerializerMappings {
        messageOf("m.room.message", VoiceAwareRoomMessageSerializer)
    }

private fun RoomMessageEventContent.Unknown.voiceFileUrl(): String? =
    takeIf { it.type == Audio.TYPE }?.let { ((it.raw["file"] as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull }

/** Mirrors Trixnity's audio uploader for voice-extended content; only the encrypted file branch exists. */
internal object VoiceMessageMediaUploader : EventContentMediaUploader<RoomMessageEventContent.Unknown> {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: RoomMessageEventContent.Unknown,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String,
    ): RoomMessageEventContent.Unknown {
        val cacheUrl = content.voiceFileUrl() ?: return content
        val mxcUri = upload(cacheUrl, uploadProgress)
        val file = content.raw.getValue("file").jsonObject
        return content.copy(raw = JsonObject(content.raw + ("file" to JsonObject(file + ("url" to JsonPrimitive(mxcUri))))))
    }
}

internal object VoiceMessageUriExtractor : EventContentUriExtractor<RoomMessageEventContent.Unknown> {
    override suspend fun invoke(content: RoomMessageEventContent.Unknown): Set<String> = setOfNotNull(content.voiceFileUrl())
}

internal val matrixEventContentMediaMappings = EventContentMediaMappings(
    EventContentMediaMappings.default.mappings +
        EventContentMediaMapping.of(VoiceMessageMediaUploader, VoiceMessageUriExtractor),
)
