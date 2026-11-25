package com.srisu.srisu.features.chat

import com.srisu.srisu.features.chat.matrix.*
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import kotlin.io.encoding.Base64

/** Synthetic artwork and keys, confined to tests. */
object ChatRecoveryFixtures {
    val image = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAGAAAABACAIAAABqVuVZAAAA70lEQVR4nO3QuxECMQxF0e2MQmhgU9qhGjJyCqEGAhiChfXa8keydGdu4OiNfJbr/UGJFvULjAcQQAABBJDhAAIIIIAKet4uvwG0S1PP5AToUEdsBFAAoEwdmRFAAAEEEEAAATSHkWAZoBhAOUayWT9ACaaaQW9AzQMIIIAAAshwAAEEEEAA2eu0nt8BtBXZFBpoDyU6UI5LRKAil0BAMhf/QJUunoFa0XyAvi/1j5ly+QM0qVQnlxTQFFK9XbKADEoNcykDUpca7yIEGomliNIGqLmXukVfIK8BBBBAAAFkOIAAAggggAwHEEAAde0FfSg4bBee/m0AAAAASUVORK5CYII=")
    val file = EncryptedFile("mxc://test/synthetic", EncryptedFile.JWK("synthetic"), "synthetic", mapOf("sha256" to "synthetic"))
    /** Synthetic speech-like envelope, not derived from any recording. */
    val waveform = List(48) { i -> listOf(18, 34, 62, 88, 70, 46, 28, 54, 92, 66, 40, 24)[i % 12] }
    fun media(video: Boolean = false): MatrixChatRoomState {
        val base = ChatFixtures.conversation().copy(accountId = 1)
        val incoming = base.messages.last().copy(id = "photo", body = "Our little escape this weekend.",
            attachment = MatrixChatAttachment(if (video) "video" else "photo", if (video) "video/mp4" else "image/png", file,
                960, 640, if (video) 12000 else 0, file), replyTo = null, reactions = emptyMap())
        val voice = incoming.copy(id = "voice", mine = true, senderMatrixId = "@one:test", body = "", timestampMillis = incoming.timestampMillis + 1000,
            attachment = MatrixChatAttachment("voice", "audio/wav", file, durationMs = 18000, waveform = waveform), read = true)
        val reply = voice.copy(id = "reply", body = "I would love that.", timestampMillis = voice.timestampMillis + 1000,
            attachment = null, replyTo = incoming.id, reactions = mapOf(incoming.senderMatrixId to "❤️"))
        return base.copy(messages = listOf(incoming, voice, reply))
    }
}
