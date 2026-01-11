package com.srisu.srisu.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object DateTimeUtils {

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


}