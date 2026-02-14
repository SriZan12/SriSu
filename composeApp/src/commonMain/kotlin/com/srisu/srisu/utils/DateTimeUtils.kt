package com.srisu.srisu.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object DateTimeUtils {

    private val MONTH_SHORT_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun formatLocalDate(date: LocalDate): String {
        return "${date.year}-${
            date.month.number.toString().padStart(2, '0')
        }-${date.day.toString().padStart(2, '0')}"
    }


    @OptIn(ExperimentalTime::class)
    fun formatTimeInHourAndMinute(isoTime: String?): String {
        if (!isoTime.isNullOrEmpty()) {
            val instant = Instant.parse(isoTime)
            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

            val hour24 = localDateTime.hour
            val minute = localDateTime.minute

            val isAm = hour24 < 12
            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }

            val minuteStr = minute.toString().padStart(2, '0')
            val amPm = if (isAm) "AM" else "PM"

            return "$hour12:$minuteStr $amPm"
        } else {
            return ""
        }
    }

    fun getDayAndMonthIndividually(dateString: String): Pair<Int, Int> {
        val dateParts = dateString.split("-")
        val month = dateParts[1].toInt()
        val day = dateParts[2].toInt()

        return Pair(month, day)
    }

    fun formatDateInZodiacRange(dateString: String): Int {
        val dateParts = dateString.split("-") // Splits into ["1996", "08", "21"]
        val month = dateParts[1].toInt()      // 8
        val day = dateParts[2].toInt()        // 21

        val dateAsNumber = month * 100 + day  // 821
        return dateAsNumber
    }

    @OptIn(ExperimentalTime::class)
    fun calculateAge(dateString: String?): Int? {
        if (dateString != null) {
            val birthDate = LocalDate.parse(dateString)
            val currentDate =
                kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            return currentDate.year - birthDate.year
        }

        return null
    }


    @Composable
    fun CountdownTimer(
        totalSeconds: Long,
        onTick: (Long) -> Unit = {},
        onFinish: () -> Unit,
        content: @Composable (Long) -> Unit
    ) {
        var timeLeft by remember { mutableStateOf(totalSeconds) }

        // These will always hold the latest versions of the lambdas passed to CountdownTimer
        val currentOnTick by rememberUpdatedState(onTick)
        val currentOnFinish by rememberUpdatedState(onFinish)


        LaunchedEffect(timeLeft) {
            if (timeLeft > 0) {
                delay(1000L) // Wait for 1 second
                currentOnTick(timeLeft - 1)
                timeLeft--
            } else {
                if (timeLeft == 0L) {
                    currentOnFinish()
                }
            }
        }

        content(timeLeft)
    }

    @OptIn(ExperimentalTime::class)
    fun parseTimestampToMillis(timestamp: String?): Long {
        if (timestamp.isNullOrBlank()) return Long.MAX_VALUE

        return try {
            Instant.parse(timestamp).toEpochMilliseconds()

            // If backend uses offset timestamps like "2025-12-03T10:12:45+05:45"
            // ZonedDateTime.parse(timestamp).toInstant().toEpochMilli()

        } catch (e: Exception) {
            Long.MAX_VALUE // fallback so invalid timestamps go at bottom
        }
    }

    @OptIn(ExperimentalTime::class)
    fun parse(timestamp: String): Instant {
        return Instant.parse(timestamp)
    }

    @OptIn(ExperimentalTime::class)
    fun getDate(timestamp: String?): String? {
        timestamp?.let {
            val dateTime = parse(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
            return "${dateTime.date}"
        }

        return null

    }


    @OptIn(ExperimentalTime::class)
    fun getReadableDate(timestamp: String?): String? {

        if (timestamp.isNullOrBlank()) return null

        val dateTime = parse(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        val monthName = MONTH_SHORT_NAMES[dateTime.month.number - 1]
        val day = dateTime.day

        return "$monthName $day"
    }

    @OptIn(ExperimentalTime::class)
    fun getTime(timestamp: String): String {
        val dateTime = parse(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
        return dateTime.time.toString()
    }

    @OptIn(ExperimentalTime::class)
    fun getReadableTime(timestamp: String): String {
        val dt = parse(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())

        val hour12 = when (val h = dt.hour % 12) {
            0 -> 12
            else -> h
        }

        val amPm = if (dt.hour < 12) "AM" else "PM"

        return "${hour12.toString().padStart(2, '0')}:${
            dt.minute.toString().padStart(2, '0')
        } $amPm"
    }


    @OptIn(ExperimentalTime::class)
    fun getDayOfWeek(timestamp: String): DayOfWeek {
        return parse(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.dayOfWeek
    }

    fun getDayName(timestamp: String): String {
        return getDayOfWeek(timestamp).name.lowercase().replaceFirstChar { it.uppercase() }
    }

    @OptIn(ExperimentalTime::class)
    fun getMonth(timestamp: String): Month {
        return parse(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .month
    }

    fun getMonthName(timestamp: String): String {
        return getMonth(timestamp).name.lowercase().replaceFirstChar { it.uppercase() }
    }


    @OptIn(ExperimentalTime::class)
    fun getChatTimestamp(timestamp: String?): String? {
        if (timestamp.isNullOrBlank()) return null

        try {
            val now = kotlin.time.Clock.System.now()
            val timeZone = TimeZone.currentSystemDefault()

            val messageInstant = Instant.parse(timestamp)

            val nowDateTime = now.toLocalDateTime(timeZone)
            val messageDateTime = messageInstant.toLocalDateTime(timeZone)

            val durationSeconds = (now - messageInstant).inWholeSeconds

            return when {
                durationSeconds < 60 ->
                    "Just now"

                durationSeconds < 3600 -> {
                    val minutes = durationSeconds / 60
                    "$minutes min ago"
                }

                durationSeconds < 86400 &&
                        nowDateTime.date == messageDateTime.date -> {
                    val hours = durationSeconds / 3600
                    "$hours hour${if (hours > 1) "s" else ""} ago"
                }

                messageDateTime.date == nowDateTime.date.minus(1, DateTimeUnit.DAY) ->
                    "Yesterday"

                nowDateTime.year == messageDateTime.year -> {
                    val month = MONTH_SHORT_NAMES[messageDateTime.month.number - 1]
                    "$month ${messageDateTime.day}"
                }

                else -> {
                    val years = nowDateTime.year - messageDateTime.year
                    "$years year${if (years > 1) "s" else ""} ago"
                }
            }
        } catch (exception: Exception) {
            AppLogger.log("Exception While formatting chat timestamp = ${exception.message}")
            return null
        }

    }


}