package com.srisu.srisu.features.auth.domain

/** Matches the existing backend column's 15-character international representation. */
fun isInternationalPhoneValid(prefix: String, number: String): Boolean =
    Regex("\\+[1-9][0-9]{7,13}").matches(prefix + number)
