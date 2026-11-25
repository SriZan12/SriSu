package com.srisu.srisu.features.notifications

import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.chat.matrix.MatrixChatRepository
import kotlinx.coroutines.withTimeoutOrNull

sealed interface PreviewResult {
    data class Ready(val value: NotificationPreview, val complete: Boolean = true) : PreviewResult
    data object Unavailable : PreviewResult
    data object Retry : PreviewResult
}

/** Private display content exists only in memory and the OS notification UI. */
class NotificationPreviewReader(private val api: NotificationApi, private val sessions: SessionCoordinator,
    private val matrix: MatrixChatRepository) {
    suspend fun load(deliveryId: String): PreviewResult {
        val stamp = sessions.stamp()
        if (stamp.accountId == null || !validNotificationId(deliveryId)) return PreviewResult.Unavailable
        val result = api.preview(deliveryId).result
        sessions.ensureCurrent(stamp)
        if (result is NetworkAPIResult.Error) return if (result.failure.status in listOf(401,403,404,410))
            PreviewResult.Unavailable else PreviewResult.Retry
        val value = (result as? NetworkAPIResult.Success)?.response ?: return PreviewResult.Retry
        if (value.eventKind != "matrix.message") return PreviewResult.Ready(value)
        val room = value.roomId?.takeIf(::validNotificationId) ?: return PreviewResult.Unavailable
        val event = value.matrixEventId?.takeIf { it.startsWith('$') && it.length in 2..255 && it.none(Char::isWhitespace) }
            ?: return PreviewResult.Unavailable
        val body = withTimeoutOrNull(18_000) { matrix.notificationPreview(room, event) }
        sessions.ensureCurrent(stamp)
        // Membership/redaction may change during sync/decryption. Reauthorize
        // before replacing the safe notification with private content.
        val confirmed = api.preview(deliveryId).result
        sessions.ensureCurrent(stamp)
        if (confirmed is NetworkAPIResult.Error) return if (confirmed.failure.status in listOf(401,403,404,410))
            PreviewResult.Unavailable else PreviewResult.Retry
        val current = (confirmed as? NetworkAPIResult.Success)?.response ?: return PreviewResult.Retry
        if (current.eventKind != value.eventKind || current.roomId != room || current.matrixEventId != event)
            return PreviewResult.Unavailable
        return PreviewResult.Ready(current.copy(body = body ?: current.body), complete = body != null)
    }
}
