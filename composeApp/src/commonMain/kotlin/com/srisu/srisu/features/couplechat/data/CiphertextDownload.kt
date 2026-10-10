package com.srisu.srisu.features.couplechat.data

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.refreshSessionIfNeeded
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException

/** Uses the shared auth/session owner. Raw ciphertext has a hard allocation bound. */
internal suspend fun HttpClient.readBoundedChatCiphertext(url: String, capability: String, size: Int): ResultHandler<ByteArray?> {
    require(size in 29..16*1024*1024+28)
    try {
        refreshSessionIfNeeded()?.let { return ResultHandler(NetworkAPIResult.Error(it)) }
        val coordinator = attributes[SessionCoordinatorKey]
        val stamp = coordinator.stamp()
        val result = prepareGet(url) {
            timeout { requestTimeoutMillis = 300_000 }
            header("X-Chat-Media-Capability",capability)
            attributes.put(RequestScopeKey,RequestScope(coordinator,stamp))
        }.execute { response ->
            coordinator.ensureCurrent(stamp)
            if (!response.status.isSuccess()) return@execute handleErrorResponse<ByteArray>(response)
            val channel = response.bodyAsChannel()
            val bytes = ByteArray(size)
            var offset = 0
            while (offset<size) {
                val read = channel.readAvailable(bytes,offset,size-offset)
                if (read<0) break
                offset+=read
            }
            val extra = ByteArray(1)
            if (offset != size || channel.readAvailable(extra,0,1)>0) {
                channel.cancel(null)
                ResultHandler(NetworkAPIResult.Error(ApiError(NetworkAPIResult.ErrorType.SERIALIZATION,"attachment_integrity","The encrypted attachment is incomplete.")))
            } else ResultHandler(NetworkAPIResult.Success<ByteArray?>(bytes))
        }
        coordinator.ensureCurrent(stamp)
        val error = (result.result as? NetworkAPIResult.Error)?.failure
        if (error?.status==401) {
            val refreshed = refreshSessionIfNeeded(force=true)
            if (refreshed != null) return ResultHandler(NetworkAPIResult.Error(refreshed))
            return ResultHandler(NetworkAPIResult.Error(ApiError(NetworkAPIResult.ErrorType.NETWORK,"session_refreshed","Try downloading again.",retryable=true)))
        }
        return result
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { return handleException(failure) }
}
