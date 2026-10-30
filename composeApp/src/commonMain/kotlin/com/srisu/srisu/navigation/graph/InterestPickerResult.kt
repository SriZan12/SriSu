package com.srisu.srisu.navigation.graph

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** SavedStateHandle carries this bounded result only on the originating entry. */
@Serializable data class InterestPickerResult(val requestId: String, val ids: List<Int>)
internal fun decodeInterestResult(requestId: String?, encoded: String?): InterestPickerResult? {
    if (requestId == null || encoded == null || encoded.length > 8192) return null
    return runCatching { Json.decodeFromString<InterestPickerResult>(encoded) }.getOrNull()
        ?.takeIf { it.requestId == requestId && it.ids.size <= 256 && it.ids.all { id -> id > 0 } }
}
