package com.srisu.srisu.core.data.remote

import com.srisu.srisu.core.logger.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import kotlin.time.TimeSource

/** Same machine codes/field codes as backend core-1; no raw transport or body. */
data class ApiError(
    val kind: NetworkAPIResult.ErrorType,
    val code: String,
    val message: String,
    val fields: Map<String, List<String>> = emptyMap(),
    val status: Int? = null,
    val requestId: String? = null,
    val retryAfterSeconds: Long? = null,
    val retryable: Boolean = false,
)
data class ResponseMetadata(val status: Int, val requestId: String?, val hasBody: Boolean)

sealed class NetworkAPIResult<T> {
    data class Success<T>(val response: T, val message: String? = null, val metadata: ResponseMetadata? = null) : NetworkAPIResult<T>()
    data class Error<T>(val failure: ApiError) : NetworkAPIResult<T>() {
        constructor(error: String?, errorType: ErrorType = ErrorType.GENERIC) : this(ApiError(errorType, errorType.name.lowercase(), error ?: "Request failed."))
        val error: String get() = failure.message
        val errorType: ErrorType get() = failure.kind
    }
    enum class ErrorType { UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, RATE_LIMITED, SERVER, BAD_REQUEST, TIMEOUT, NETWORK, SERIALIZATION, GENERIC }
}

enum class ResponseShape { ENVELOPE, RAW, EMPTY }

/** Retries are opt-in and limited to GET/HEAD; never automatically replay writes. */
suspend inline fun <reified T> HttpClient.safeRequest(
    shape: ResponseShape = ResponseShape.ENVELOPE,
    readRetries: Int = 0,
    execute: HttpRequestBuilder.() -> Unit,
): ResultHandler<T?> {
    require(readRetries in 0..2)
    val builder = HttpRequestBuilder().apply(execute)
    val scope = attributes.getOrNull(SessionCoordinatorKey)?.let { RequestScope(it, it.stamp()) }
    scope?.let { builder.attributes.put(RequestScopeKey, it) }
    val retryLimit = if (builder.method == HttpMethod.Get || builder.method == HttpMethod.Head) readRetries else 0
    for (attempt in 0..retryLimit) {
        scope?.ensureCurrent()
        val started = TimeSource.Monotonic.markNow()
        val result = try {
            val response = request(builder)
            val parsed = handleResponse<T>(response, shape)
            response.call.request.attributes.getOrNull(RequestScopeKey)?.ensureCurrent()
            AppLogger.http(response.status.value, started.elapsedNow().inWholeMilliseconds, response.headers["X-Request-ID"])
            parsed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            handleException<T>(failure)
        }
        scope?.ensureCurrent()
        val error = (result.result as? NetworkAPIResult.Error)?.failure
        if (attempt == retryLimit || error == null || !error.retryable || error.kind == NetworkAPIResult.ErrorType.RATE_LIMITED) return result
        delay(150L * (attempt + 1))
    }
    error("Unreachable retry state")
}

suspend inline fun <reified T> handleResponse(response: HttpResponse, shape: ResponseShape = ResponseShape.ENVELOPE): ResultHandler<T?> {
    if (!response.status.isSuccess()) return handleErrorResponse(response)
    if (shape == ResponseShape.EMPTY || response.status.value == 204 || response.status.value == 205) {
        return ResultHandler(NetworkAPIResult.Success(null, metadata = ResponseMetadata(response.status.value, response.headers["X-Request-ID"], false)))
    }
    val body = response.bodyAsText()
    if (body.isBlank()) return ResultHandler(NetworkAPIResult.Success(null, metadata = ResponseMetadata(response.status.value, response.headers["X-Request-ID"], false)))
    val root = ApiJson.parseToJsonElement(body)
    val envelope = root as? JsonObject
    val value = when (shape) {
        ResponseShape.ENVELOPE -> {
            if (envelope == null || !envelope.containsKey("data")) throw SerializationException("Missing response data")
            envelope["data"]!!
        }
        ResponseShape.RAW -> root
        ResponseShape.EMPTY -> JsonNull
    }
    val decoded = if (value == JsonNull) null else ApiJson.decodeFromJsonElement<T>(value)
    return ResultHandler(NetworkAPIResult.Success(decoded, if (shape == ResponseShape.ENVELOPE) (envelope?.get("message") as? JsonPrimitive)?.contentOrNull else null,
        ResponseMetadata(response.status.value, response.headers["X-Request-ID"], true)))
}

fun <T> handleException(exception: Exception): ResultHandler<T?> {
    if (exception is CancellationException) throw exception
    val kind = when (exception) {
        is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException -> NetworkAPIResult.ErrorType.TIMEOUT
        is SerializationException -> NetworkAPIResult.ErrorType.SERIALIZATION
        is kotlinx.io.IOException -> NetworkAPIResult.ErrorType.NETWORK
        else -> NetworkAPIResult.ErrorType.GENERIC
    }
    val retry = kind == NetworkAPIResult.ErrorType.TIMEOUT || kind == NetworkAPIResult.ErrorType.NETWORK
    return ResultHandler(NetworkAPIResult.Error(ApiError(kind, kind.name.lowercase(), safeMessage(kind), retryable = retry)))
}

fun safeMessage(kind: NetworkAPIResult.ErrorType): String = when (kind) {
    NetworkAPIResult.ErrorType.UNAUTHORIZED -> "Sign in to continue."
    NetworkAPIResult.ErrorType.FORBIDDEN -> "This action is not allowed."
    NetworkAPIResult.ErrorType.NOT_FOUND -> "This resource is unavailable."
    NetworkAPIResult.ErrorType.CONFLICT -> "This item changed. Refresh before trying again."
    NetworkAPIResult.ErrorType.RATE_LIMITED -> "Please wait before trying again."
    NetworkAPIResult.ErrorType.BAD_REQUEST -> "Check the supplied fields."
    NetworkAPIResult.ErrorType.NETWORK -> "Unable to connect. Check your connection."
    NetworkAPIResult.ErrorType.TIMEOUT -> "The request timed out."
    NetworkAPIResult.ErrorType.SERIALIZATION -> "The service returned an unexpected response."
    else -> "The service could not complete this request."
}

fun statusKind(status: Int): NetworkAPIResult.ErrorType = when (status) {
    400, 413, 415, 422 -> NetworkAPIResult.ErrorType.BAD_REQUEST
    401 -> NetworkAPIResult.ErrorType.UNAUTHORIZED
    403 -> NetworkAPIResult.ErrorType.FORBIDDEN
    404, 410 -> NetworkAPIResult.ErrorType.NOT_FOUND
    409 -> NetworkAPIResult.ErrorType.CONFLICT
    429 -> NetworkAPIResult.ErrorType.RATE_LIMITED
    in 500..599 -> NetworkAPIResult.ErrorType.SERVER
    else -> NetworkAPIResult.ErrorType.GENERIC
}

fun decodeApiError(status: Int, body: String, requestId: String? = null, retryAfter: String? = null): ApiError {
    val kind = statusKind(status)
    val root = try { ApiJson.parseToJsonElement(body) as? JsonObject } catch (_: SerializationException) { null }
    val error = root?.get("error") as? JsonObject
    val fields = (error?.get("fields") as? JsonObject)?.mapValues { (_, value) ->
        (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    }.orEmpty().ifEmpty {
        // Old endpoints do not expose DRF machine codes: retain field identity,
        // but never pass arbitrary response strings/private input through to UI.
        if (status in listOf(400, 422)) (root?.get("error_details") as? JsonObject)?.keys
            ?.associateWith { listOf("invalid") }.orEmpty() else emptyMap()
    }
    return ApiError(kind,
        code = (error?.get("code") as? JsonPrimitive)?.contentOrNull ?: when (kind) {
            NetworkAPIResult.ErrorType.UNAUTHORIZED -> "unauthenticated"
            NetworkAPIResult.ErrorType.BAD_REQUEST -> "validation_failed"
            NetworkAPIResult.ErrorType.RATE_LIMITED -> "rate_limited"
            NetworkAPIResult.ErrorType.SERVER -> if (status == 503) "temporarily_unavailable" else "server_error"
            NetworkAPIResult.ErrorType.NOT_FOUND -> if (status == 410) "cursor_expired" else "not_found"
            else -> kind.name.lowercase()
        },
        message = safeMessage(kind), fields = fields, status = status,
        requestId = requestId ?: (root?.get("request_id") as? JsonPrimitive)?.contentOrNull,
        retryAfterSeconds = (retryAfter?.toLongOrNull() ?: (error?.get("retry_after_seconds") as? JsonPrimitive)?.longOrNull)?.coerceAtLeast(0),
        retryable = (error?.get("retryable") as? JsonPrimitive)?.booleanOrNull ?: (status in listOf(502, 503, 504)),
    )
}

suspend fun <T> handleErrorResponse(response: HttpResponse): ResultHandler<T?> = ResultHandler(
    NetworkAPIResult.Error(decodeApiError(response.status.value, response.bodyAsText(), response.headers["X-Request-ID"], response.headers["Retry-After"]))
)

class ResultHandler<T>(val result: NetworkAPIResult<T>) {
    inline fun onSuccess(action: (T, String?) -> Unit): ResultHandler<T> {
        if (result is NetworkAPIResult.Success) action(result.response, result.message)
        return this
    }
    inline fun onError(action: (String?, NetworkAPIResult.ErrorType) -> Unit): ResultHandler<T> {
        if (result is NetworkAPIResult.Error) action(result.error, result.errorType)
        return this
    }
    inline fun onFailure(action: (ApiError) -> Unit): ResultHandler<T> {
        if (result is NetworkAPIResult.Error) action(result.failure)
        return this
    }
}
