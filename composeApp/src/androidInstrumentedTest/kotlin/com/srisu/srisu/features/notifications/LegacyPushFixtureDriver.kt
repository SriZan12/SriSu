package com.srisu.srisu.features.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * These standalone transport fixtures own in-memory sessions and crypto clients.
 * Keep them on schema 1 to exercise old-client compatibility; schema-2 background
 * previews use the real application's process graph and are verified separately.
 */
internal class LegacyPushFixtureDriver(private val delegate: NotificationPlatform) : NotificationPlatform by delegate {
    override fun registration(binding: String) = delegate.registration(binding)?.let { request ->
        request.copy(capabilities = request.capabilities.filterNot { it == "notification-previews-1" })
    }
    fun restore() { NativeNotifications.install(delegate) }
}

internal suspend fun legacyPushFixtureDriver(): LegacyPushFixtureDriver = withContext(Dispatchers.Main) {
    LegacyPushFixtureDriver(requireNotNull(NativeNotifications.driver)).also {
        it.renewRegistration()
        NativeNotifications.install(it)
    }
}
