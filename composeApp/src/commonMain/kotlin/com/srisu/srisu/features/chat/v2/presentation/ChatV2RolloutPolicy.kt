package com.srisu.srisu.features.chat.v2.presentation

import com.srisu.srisu.core.config.ApiEnvironment

enum class ChatExperienceMode {
    /** Pre-existing released implementation; never selected because v2 failed at runtime. */
    LEGACY_COMPATIBILITY,
    /** Non-production v2 shell. Capability or crypto failure remains fail-closed. */
    SECURE_V2_EXPERIMENTAL,
    /** A secure route is required and remains unavailable rather than downgrading. */
    SECURE_REQUIRED_UNAVAILABLE,
}

enum class ChatEntryTarget { LEGACY_COMPATIBILITY, SECURE_V2 }

data class ChatV2RolloutPolicy(val mode: ChatExperienceMode) {
    val entryTarget: ChatEntryTarget
        get() = when (mode) {
            ChatExperienceMode.LEGACY_COMPATIBILITY -> ChatEntryTarget.LEGACY_COMPATIBILITY
            ChatExperienceMode.SECURE_V2_EXPERIMENTAL,
            ChatExperienceMode.SECURE_REQUIRED_UNAVAILABLE -> ChatEntryTarget.SECURE_V2
        }

    /** New entry points must never introduce a user into plaintext compatibility mode. */
    val exposesSecureAcceptanceAction: Boolean get() = entryTarget == ChatEntryTarget.SECURE_V2

    companion object {
        fun configured(environment: ApiEnvironment): ChatV2RolloutPolicy = ChatV2RolloutPolicy(
            mode = if (environment.development) ChatExperienceMode.SECURE_V2_EXPERIMENTAL
            else ChatExperienceMode.LEGACY_COMPATIBILITY,
        )
    }
}
