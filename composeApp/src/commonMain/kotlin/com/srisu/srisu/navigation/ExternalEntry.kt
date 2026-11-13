package com.srisu.srisu.navigation

import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.features.coupleprofile.navigation.CoupleProfileDestination
import com.srisu.srisu.features.chat.matrix.normalizeDjangoRoomIdOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Deliberately small allowlist. URLs never name internal classes or execute mutations. */
fun parseExternalEntry(url: String): Route? {
    if (url.length > 512) return null
    val plan = Regex("^srisu://plans/([1-9][0-9]{0,18})/([1-9][0-9]{0,18})/?$").matchEntire(url)
    if (plan != null && plan.groupValues[1].toLongOrNull() != null) return plan.groupValues[2].toLongOrNull()?.let { com.srisu.srisu.features.plans.navigation.PlansDestination(it) }
    val couple = Regex("^srisu://couples/([1-9][0-9]{0,18})/?$").matchEntire(url)
    if (couple != null) return couple.groupValues[1].toLongOrNull()?.let { CoupleProfileDestination(it) }
    val chat = Regex("^srisu://chats/([^/?#]+)/?$").matchEntire(url)
    val roomId = chat?.groupValues?.get(1)?.let(::normalizeDjangoRoomIdOrNull)
    return roomId?.let { ChatNav.CoupleConversation(roomId = it, origin = "external") }
}

data class PendingEntry(val deliveryId: String, val route: Route, val accountId: Long?)
/** Platform intent bridge only. No session, profile, or navigation stack is stored here.
 * Pending intents are memory-only; signed-out links can cross one successful login.
 */
class ExternalEntryInbox {
    private val mutable = MutableStateFlow<PendingEntry?>(null)
    val pending = mutable.asStateFlow()
    private var account: Long? = null
    private var generation: Long = 0
    private val delivered = mutableSetOf<String>()
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun receive(url: String, deliveryId: String = kotlin.uuid.Uuid.random().toString()): Boolean {
        val route = parseExternalEntry(url) ?: return false
        if (deliveryId.length > 512 || deliveryId in delivered || mutable.value?.deliveryId == deliveryId || mutable.value?.route == route) return false
        mutable.value = PendingEntry(deliveryId, route, account)
        return true
    }
    fun reconcile(accountId: Long?, sessionGeneration: Long = 0) {
        if (account != null && (account != accountId || generation != sessionGeneration)) { mutable.value = null; delivered.clear() }
        account = accountId
        generation = sessionGeneration
    }
    fun consume(entry: PendingEntry, accountId: Long): Route? {
        if (mutable.value != entry) return null
        mutable.value = null
        if (entry.accountId != null && entry.accountId != accountId) return null
        delivered += entry.deliveryId
        if (delivered.size > 32) delivered.remove(delivered.first())
        return entry.route
    }
}
/** Called on the platform UI thread by URL and notification-tap adapters. */
object PlatformEntry {
    val inbox = ExternalEntryInbox()
    fun openUrl(url: String) { inbox.receive(url) }
    fun openNotification(url: String, deliveryId: String) { inbox.receive(url, deliveryId) }
}
