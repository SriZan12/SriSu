package com.srisu.srisu.features.chat

import com.srisu.srisu.features.chat.matrix.*
import kotlin.test.*

class MatrixNotificationTextTest {
    private fun message(body: String) = MatrixChatMessage("\$synthetic", body, "@synthetic:example.test", false, 1, MatrixMessageDelivery.SENT)

    @Test fun textPreservesUnicodeAndDoesNotExposeDeletedOrOwnMessages() {
        assertEquals("माया गर्छु 💛", matrixNotificationText(message("माया गर्छु 💛")))
        assertNull(matrixNotificationText(message("Synthetic deleted").copy(deleted = true)))
        assertNull(matrixNotificationText(message("Synthetic own").copy(mine = true)))
        assertNull(matrixNotificationText(message("Synthetic unavailable").copy(unavailable = true)))
        assertEquals("Hello world", matrixNotificationText(message("Hello\n\u202eworld\u0000")))
        val value = matrixNotificationText(message("🌻".repeat(200)))!!
        assertTrue(value.length <= 240)
        assertFalse(value.dropLast(1).last().isHighSurrogate())
    }

    @Test fun mediaPreviewsDescribeTypeAndOptionalCaption() {
        for ((kind, label) in listOf("photo" to "Photo", "video" to "Video", "voice" to "Voice message")) {
            val attachment = MatrixChatAttachment(kind, "synthetic/type", ChatRecoveryFixtures.file)
            assertEquals(label, matrixNotificationText(message("").copy(attachment = attachment)))
            assertEquals("$label · Synthetic caption", matrixNotificationText(message("Synthetic caption").copy(attachment = attachment)))
        }
    }
}
