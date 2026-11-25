package com.srisu.srisu.features.chat.matrix

internal fun matrixNotificationText(message: MatrixChatMessage): String? {
    if (message.deleted || message.unavailable || message.mine) return null
    val caption = message.body.replace(Regex("[\\p{Cc}\\u202a-\\u202e\\u2066-\\u2069]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    val media = when (message.attachment?.kind) {
        "photo" -> "Photo"
        "video" -> "Video"
        "voice" -> "Voice message"
        null -> null
        else -> "Attachment"
    }
    val value = listOfNotNull(media, caption.takeIf(String::isNotBlank)).joinToString(" · ")
        .ifBlank { "New message" }
    return if (value.length <= 240) value else value.take(239).trimEnd { it.isHighSurrogate() } + "…"
}
