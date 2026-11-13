package com.srisu.srisu.features.chat.matrix.presentation

import com.srisu.srisu.features.chat.matrix.MatrixChatMessage
import com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Consecutive messages from one sender within this window share a visual group. */
internal const val CHAT_GROUP_WINDOW_MILLIS = 5 * 60_000L

internal sealed interface ChatTimelineItem {
    val key: String

    data object Typing : ChatTimelineItem { override val key: String = "partner-typing" }

    data class DayHeader(val date: LocalDate) : ChatTimelineItem {
        override val key: String get() = "day:$date"
    }

    data class Message(
        val message: MatrixChatMessage,
        val time: LocalDateTime,
        /** Visually joins the message above (same sender, same day, within the group window). */
        val joinsPrevious: Boolean,
        /** Visually joins the message below. */
        val joinsNext: Boolean,
    ) : ChatTimelineItem {
        override val key: String get() = message.stableKey

        /** Time and status sit on the last bubble of a group, and on any bubble needing attention. */
        val showMeta: Boolean
            get() = !joinsNext || message.delivery != MatrixMessageDelivery.SENT
    }
}

/**
 * Builds the rendered timeline in chronological order. Keys are unique: a duplicated stable key
 * (possible only during an outbox/timeline race) keeps the first occurrence.
 */
internal fun buildChatTimeline(messages: List<MatrixChatMessage>, timeZone: TimeZone): List<ChatTimelineItem> {
    val unique = messages.distinctBy(MatrixChatMessage::stableKey)
    val times = unique.map { Instant.fromEpochMilliseconds(it.timestampMillis).toLocalDateTime(timeZone) }
    fun joins(a: Int, b: Int): Boolean {
        if (a !in unique.indices || b !in unique.indices) return false
        val first = unique[a]
        val second = unique[b]
        return first.attachment == null && second.attachment == null &&
            first.sparkReference == null && second.sparkReference == null &&
            first.planReference == null && second.planReference == null &&
            first.challengeReference == null && second.challengeReference == null &&
            first.momentReference == null && second.momentReference == null &&
            first.mine == second.mine &&
            times[a].date == times[b].date &&
            second.timestampMillis - first.timestampMillis in 0..CHAT_GROUP_WINDOW_MILLIS &&
            first.delivery != MatrixMessageDelivery.FAILED
    }
    val items = ArrayList<ChatTimelineItem>(unique.size + 4)
    unique.forEachIndexed { index, message ->
        if (index == 0 || times[index - 1].date != times[index].date) {
            items += ChatTimelineItem.DayHeader(times[index].date)
        }
        items += ChatTimelineItem.Message(
            message = message,
            time = times[index],
            joinsPrevious = joins(index - 1, index),
            joinsNext = joins(index, index + 1),
        )
    }
    return items
}

/** "8:02 AM" — a fixed 12-hour format; platform 24-hour preference is not yet read. */
internal fun formatChatTime(time: LocalDateTime): String {
    val hour12 = (time.hour % 12).let { if (it == 0) 12 else it }
    val minute = time.minute.toString().padStart(2, '0')
    val period = if (time.hour < 12) "AM" else "PM"
    return "$hour12:$minute $period"
}

internal enum class ChatDayLabel { TODAY, YESTERDAY, DATE }

internal fun chatDayLabel(date: LocalDate, today: LocalDate): ChatDayLabel = when {
    date == today -> ChatDayLabel.TODAY
    date.toEpochDays() == today.toEpochDays() - 1 -> ChatDayLabel.YESTERDAY
    else -> ChatDayLabel.DATE
}

private val shortWeekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val shortMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Fri, 16 Oct" this year, otherwise "16 Oct 2025". English, matching the app's current strings. */
internal fun formatChatDate(date: LocalDate, today: LocalDate): String {
    val month = shortMonths[date.month.ordinal]
    return if (date.year == today.year) "${shortWeekdays[date.dayOfWeek.ordinal]}, ${date.day} $month"
    else "${date.day} $month ${date.year}"
}
