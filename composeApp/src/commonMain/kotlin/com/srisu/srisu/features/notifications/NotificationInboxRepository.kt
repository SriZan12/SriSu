package com.srisu.srisu.features.notifications

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val INBOX_CONTRACT = "notification-inbox-1"

@Serializable data class InboxItem(val id: String, val kind: String, val category: String,
    val title: String, val body: String, @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null, val available: Boolean = false)
@Serializable data class InboxPage(val contract: String, val items: List<InboxItem>,
    @SerialName("unread_count") val unreadCount: Int,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("read_token") val readToken: String,
    @SerialName("server_time") val serverTime: String,
    @SerialName("retention_days") val retentionDays: Int = 30)
@Serializable data class InboxSummary(val contract: String, @SerialName("unread_count") val unreadCount: Int)
@Serializable data class InboxOpen(val contract: String, @SerialName("unread_count") val unreadCount: Int,
    val available: Boolean, val destination: NotificationDestination? = null)
@Serializable private data class InboxReadRequest(val through: String)

interface NotificationInboxSource {
    val unreadCount: StateFlow<Int?>
    suspend fun page(cursor: String? = null, unread: Boolean = false): NetworkAPIResult<InboxPage?>
    suspend fun readAll(through: String): NetworkAPIResult<InboxSummary?>
    suspend fun open(id: String): NetworkAPIResult<InboxOpen?>
}

class NotificationInboxRepository(private val client: HttpClient, environment: ApiEnvironment,
    private val sessions: SessionCoordinator, private val lifetime: ApplicationLifetime) : NotificationInboxSource {
    private val root = environment.baseUrl + "api/notifications/v1/inbox/"
    private val count = MutableStateFlow<Int?>(null)
    override val unreadCount = count.asStateFlow()
    private var revision = 0L
    private val polling = lifetime.scope.launch {
        combine(sessions.state, lifetime.foreground) { stamp, active -> stamp to active }.collectLatest { (stamp, active) ->
            revision++; count.value = null
            if (stamp.accountId != null && active) while (isActive) {
                val request = ++revision
                val result = client.safeRequest<InboxSummary>(readRetries=1) { url(root + "summary/") }.result
                if (sessions.stamp() == stamp && revision == request) {
                    count.value = (result as? NetworkAPIResult.Success)?.response?.takeIf { it.contract == INBOX_CONTRACT }?.unreadCount
                }
                delay(30_000)
            }
        }
    }

    override suspend fun page(cursor: String?, unread: Boolean): NetworkAPIResult<InboxPage?> {
        val stamp = sessions.stamp()
        val result = client.safeRequest<InboxPage>(readRetries=1) {
            url(root); parameter("unread", unread.toString()); cursor?.let { parameter("cursor", it) }
        }.result
        sessions.ensureCurrent(stamp)
        (result as? NetworkAPIResult.Success)?.response?.takeIf { it.contract == INBOX_CONTRACT }?.let { acceptCount(it.unreadCount) }
        return result
    }

    override suspend fun readAll(through: String): NetworkAPIResult<InboxSummary?> {
        val stamp = sessions.stamp()
        val result = client.safeRequest<InboxSummary> {
            url(root + "read/"); method = HttpMethod.Post; contentType(ContentType.Application.Json); setBody(InboxReadRequest(through))
        }.result
        sessions.ensureCurrent(stamp)
        (result as? NetworkAPIResult.Success)?.response?.takeIf { it.contract == INBOX_CONTRACT }?.let { acceptCount(it.unreadCount) }
        return result
    }

    override suspend fun open(id: String): NetworkAPIResult<InboxOpen?> {
        require(validNotificationId(id))
        val stamp = sessions.stamp()
        val result = client.safeRequest<InboxOpen> { url(root + "$id/open/"); method = HttpMethod.Post }.result
        sessions.ensureCurrent(stamp)
        (result as? NetworkAPIResult.Success)?.response?.takeIf { it.contract == INBOX_CONTRACT }?.let { acceptCount(it.unreadCount) }
        return result
    }

    private fun acceptCount(value: Int) { revision++; count.value = value.coerceAtLeast(0) }
    fun close() { polling.cancel() }
}
