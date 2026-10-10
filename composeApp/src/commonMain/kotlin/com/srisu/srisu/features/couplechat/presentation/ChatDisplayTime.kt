@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.srisu.srisu.features.couplechat.presentation

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate
import com.srisu.srisu.features.couplechat.data.LocalChatMessage
import kotlin.time.Clock
import kotlin.time.Instant

private fun local(value:String)=try {Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault())} catch(_:IllegalArgumentException) {null}
internal fun chatLocalDate(value:String)=local(value)?.date?.toString() ?: "Date unavailable"
internal fun chatLocalTime(value:String)=local(value)?.let {"${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}"} ?: ""

internal fun chatDateLabel(value: String, today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date): String {
    val date = local(value)?.date ?: return "Date unavailable"
    return when (today.toEpochDays() - date.toEpochDays()) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> "${date.day} ${date.month.name.lowercase().replaceFirstChar(Char::uppercase)}" +
            if (date.year != today.year) " ${date.year}" else ""
    }
}

internal fun messagesGroup(first: LocalChatMessage?, second: LocalChatMessage?): Boolean {
    if (first == null || second == null || first.authorId != second.authorId ||
        chatLocalDate(first.createdAt) != chatLocalDate(second.createdAt) || first.deleted || first.hidden || second.deleted || second.hidden) return false
    return try { (Instant.parse(second.createdAt) - Instant.parse(first.createdAt)).inWholeSeconds in 0..300 }
    catch (_: IllegalArgumentException) { false }
}
