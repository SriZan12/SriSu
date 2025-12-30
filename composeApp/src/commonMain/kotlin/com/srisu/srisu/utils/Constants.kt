package com.srisu.srisu.utils

object Constants {

    object Auth {
        const val OTP_LENGTH = 6
        const val TOTAL_PROGRESS = 7
        const val COUNTRY_JSON_FILE_NAME = "Countries.json"
        const val SESSION_FILE = "Session_File"
        const val SESSION_KEY = "Session_Key"

        const val FIRST_INSTALL_FLAG = "firstInstallFlag"
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

    object ConnectionStatus {
        const val NOTHING = "NOTHING"
        const val ACCEPTED = "ACCEPTED"
        const val REJECTED = "REJECTED"
    }

    object ChatConstants {

        const val FETCH_MESSAGES = "fetch_messages"
        const val SEND_MESSAGE = "send_message"
        const val EDIT_MESSAGE = "edit_message"
        const val DELETE_MESSAGE = "delete_message"
        const val TYPING = "typing"
        const val MESSAGE_READ = "message_read"
        const val MESSAGE_DELIVERED = "message_delivered"

        const val DELETE_FOR_ME = "DELETE_FOR_ME"
        const val DELETE_FOR_EVERYONE = "DELETE_FOR_EVERYONE"
    }
}
