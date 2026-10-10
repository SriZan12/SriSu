@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.srisu.srisu.features.couplechat.presentation

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private fun local(value:String)=try {Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault())} catch(_:IllegalArgumentException) {null}
internal fun chatLocalDate(value:String)=local(value)?.date?.toString() ?: "Date unavailable"
internal fun chatLocalTime(value:String)=local(value)?.let {"${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}"} ?: ""
