package com.srisu.srisu.core.data.network

import com.srisu.srisu.core.logger.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.JsonConvertException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class NetworkAPIResult<T> {
    data class Success<T>(val response: T, val message: String? = null) : NetworkAPIResult<T>()
    data class Error<T>(
        val error: String?,
        val errorType: ErrorType = ErrorType.GENERIC
    ) : NetworkAPIResult<T>()

    enum class ErrorType {
        UNAUTHORIZED,  // 401
        FORBIDDEN,     // 403
        NOT_FOUND,      // 404
        SERVER,   // 500+
        BAD_REQUEST,    // 400
        TIMEOUT,       // Connection/Request timeout
        NETWORK,       // Network-related issues
        SERIALIZATION, // Serialization/Parsing issues
        GENERIC        // Any other errors
    }
}

suspend inline fun <reified T> HttpClient.safeRequest(
    execute: HttpRequestBuilder.() -> Unit
): ResultHandler<T?> {


    return try {
        val response: HttpResponse = request { execute() }
        handleResponse<T>(response)
    } catch (ex: Exception) {
        AppLogger.log("INSIDE EXCEPTION")
        handleException(exception = ex)
    }
}

suspend inline fun <reified T> handleResponse(response: HttpResponse): ResultHandler<T?> {
    return when {
        response.status.isSuccess() -> {
            val defaultResponse: DefaultResponse<T> = response.body()
            ResultHandler(
                NetworkAPIResult.Success(
                    response = defaultResponse.data,
                    message = defaultResponse.message
                )
            )
        }


        else -> handleErrorResponse(response)
    }
}

inline fun <reified T> handleException(
    exception: Exception,
): ResultHandler<T?> {
    val errorType: NetworkAPIResult.ErrorType
    val errorMessage: String = when (exception) {
        is kotlinx.io.IOException -> {
            errorType = NetworkAPIResult.ErrorType.NETWORK
            "Unable to connect. Please check your internet connection."
        }

        is HttpRequestTimeoutException, is SocketTimeoutException -> {
            errorType = NetworkAPIResult.ErrorType.TIMEOUT
            "Request timed out. Try again later."
        }

        is ClientRequestException -> {
            errorType = NetworkAPIResult.ErrorType.BAD_REQUEST
            "Request timed out. Try again later."
        }

        is SerializationException, is JsonConvertException, is NoTransformationFoundException -> {
            errorType = NetworkAPIResult.ErrorType.SERIALIZATION
            exception.message ?: "Failed to parse the response."
        }

        else -> {
//            if (response != null) {
//                val errorHandlerResult = handleErrorResponse<T>(response)
//                errorType = (errorHandlerResult.result as? NetworkAPIResult.Error)?.errorType
//                    ?: NetworkAPIResult.ErrorType.GENERIC
//                (errorHandlerResult.result as? NetworkAPIResult.Error)?.error
//                    ?: "An unknown error occurred."
//            } else {
            errorType = NetworkAPIResult.ErrorType.GENERIC
            "An unknown error occurred."
//            }
        }
    }
    return ResultHandler(NetworkAPIResult.Error(error = errorMessage, errorType = errorType))
}


suspend inline fun <reified T> handleErrorResponse(response: HttpResponse): ResultHandler<T?> {
    val rawBody = response.bodyAsText() // Always succeeds (unless network stream breaks)

    val errorType: NetworkAPIResult.ErrorType
    val errorMessage: String

    val errorResponse: DefaultErrorResponse? = try {
        parseErrorResponse(rawBody)
    } catch (e: Exception) {
        null
    }


    AppLogger.log("ERROR RESPONSE = ${Json.encodeToString(errorResponse)}")

    errorMessage = when (response.status) {
        HttpStatusCode.BadRequest -> {
            errorType = NetworkAPIResult.ErrorType.BAD_REQUEST
            errorResponse?.message ?: "Bad Request: The request was invalid or cannot be served."
        }

        HttpStatusCode.Unauthorized -> {
            errorType = NetworkAPIResult.ErrorType.UNAUTHORIZED
            errorResponse?.message ?: "Unauthorized: Authentication is required or has failed."
        }

        HttpStatusCode.Forbidden -> {
            errorType = NetworkAPIResult.ErrorType.FORBIDDEN
            "Forbidden: You do not have permission to access this resource."
        }

        HttpStatusCode.NotFound -> {
            errorType = NetworkAPIResult.ErrorType.NOT_FOUND
            "Not Found: The requested resource could not be found."
        }

        else -> {
            errorType = if (response.status.value in 500..599) {
                NetworkAPIResult.ErrorType.SERVER
            } else {
                NetworkAPIResult.ErrorType.GENERIC
            }
            errorResponse?.message ?: extractFirstFieldError(rawBody)
            ?: "An unexpected error occurred. Please try again later."
        }
    }

    return ResultHandler(NetworkAPIResult.Error(error = errorMessage, errorType = errorType))
}

fun extractFirstFieldError(jsonString: String): String? {
    return try {
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
        jsonObject.entries.firstOrNull()?.value?.jsonArray?.firstOrNull()?.toString()
            ?.removeSurrounding("\"")
    } catch (e: Exception) {
        null
    }
}

fun parseErrorResponse(jsonString: String): DefaultErrorResponse {
    return try {
        val json = Json { ignoreUnknownKeys = true } // Ignore unexpected fields
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject

        // Extracting "message" field
        val message =
            jsonObject["message"]?.jsonPrimitive?.contentOrNull?.substringAfter(": ")?.trim()

        // Extracting "error_details" field
        val errorDetails = jsonObject["error_details"]?.jsonObject?.let { errorDetailsObj ->
            DefaultErrorResponse.ErrorDetails(
                detail = errorDetailsObj["detail"]?.jsonPrimitive?.contentOrNull
            )
        }

        // Return parsed error response
        DefaultErrorResponse(errorDetails = errorDetails, message = message ?: "Unknown error")
    } catch (e: Exception) {
        AppLogger.log("Parsing error response failed: ${e.message}")
        DefaultErrorResponse(errorDetails = null, message = "Unknown error")
    }
}


class ResultHandler<T>(val result: NetworkAPIResult<T>) {

    inline fun onSuccess(action: (T, String?) -> Unit): ResultHandler<T> {
        if (result is NetworkAPIResult.Success) {
            action(result.response, result.message)
        }
        return this
    }

    inline fun onError(action: (String?, NetworkAPIResult.ErrorType) -> Unit): ResultHandler<T> {
        if (result is NetworkAPIResult.Error) {
            action(result.error, result.errorType)
        }
        return this
    }
}


