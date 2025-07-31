package com.srisu.srisu.utils

object Constants {

    object Auth {
        const val OTP_LENGTH = 6
        const val TOTAL_PROGRESS = 7
        const val COUNTRY_JSON_FILE_NAME = "Countries.json"
        const val SESSION_FILE = "Session_File"
        const val SESSION_KEY = "Session_Key"
        const val PHONE_NUMBER_VERIFICATION_PROGRESS = 2
        const val FULL_NAME_PROGRESS = 3
        const val OTP_WAITING_TIME = 2 * 60 * 1000L // 2 minutes
    }

    object HomeGraph {
        const val FILTER_APPLIED = "filter_applied"
        const val FILTER_CLEARED = "filter_cleared"
        const val EDITED_INTERESTS = "edited_interests"
    }

    // Add more sections when needed, like:
    object Network {
        const val TIMEOUT = 30_000L
    }

    object UI {
        const val ANIMATION_DURATION = 300
    }
}
