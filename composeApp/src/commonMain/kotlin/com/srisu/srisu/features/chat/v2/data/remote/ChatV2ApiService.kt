package com.srisu.srisu.features.chat.v2.data.remote

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiEnvironmentKey
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.data.remote.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

interface ChatV2Remote {
    suspend fun capabilities(): NetworkAPIResult<ChatV2CapabilitiesDto?>
    suspend fun messages(roomId: String, beforeSequence: Long? = null, limit: Int = 50): NetworkAPIResult<ChatV2MessagesPageDto?>
    suspend fun changes(roomId: String, afterSequence: Long, limit: Int = 100): NetworkAPIResult<ChatV2ChangesPageDto?>
    suspend fun sendMessage(roomId: String, request: ChatV2SendMessageRequest): NetworkAPIResult<ChatV2OperationResultDto?>
    suspend fun operation(roomId: String, request: ChatV2OperationRequest): NetworkAPIResult<ChatV2OperationResultDto?>
    suspend fun receipts(roomId: String, request: ChatV2ReceiptRequest): NetworkAPIResult<ChatV2ReceiptResultDto?>
}

class ChatV2ApiService(
    private val httpClient: HttpClient,
    private val environment: ApiEnvironment = httpClient.attributes[ApiEnvironmentKey],
) : ChatV2Remote {
    override suspend fun capabilities(): NetworkAPIResult<ChatV2CapabilitiesDto?> =
        httpClient.safeRequest<ChatV2CapabilitiesDto>(readRetries = 1) {
            url(environment.baseUrl + ChatV2Routes.CAPABILITIES)
            method = HttpMethod.Get
            chatV2Contract()
        }.result

    override suspend fun messages(roomId: String, beforeSequence: Long?, limit: Int): NetworkAPIResult<ChatV2MessagesPageDto?> =
        httpClient.safeRequest<ChatV2MessagesPageDto>(readRetries = 1) {
            url(environment.baseUrl + ChatV2Routes.messages(roomId))
            method = HttpMethod.Get
            beforeSequence?.let { parameter("before_sequence", it) }
            parameter("limit", limit.coerceIn(1, 50))
            chatV2Contract()
        }.result

    override suspend fun changes(roomId: String, afterSequence: Long, limit: Int): NetworkAPIResult<ChatV2ChangesPageDto?> =
        httpClient.safeRequest<ChatV2ChangesPageDto>(readRetries = 1) {
            url(environment.baseUrl + ChatV2Routes.changes(roomId))
            method = HttpMethod.Get
            parameter("after_sequence", afterSequence.coerceAtLeast(0))
            parameter("limit", limit.coerceIn(1, 100))
            chatV2Contract()
        }.result

    override suspend fun sendMessage(roomId: String, request: ChatV2SendMessageRequest): NetworkAPIResult<ChatV2OperationResultDto?> =
        httpClient.safeRequest<ChatV2OperationResultDto> {
            url(environment.baseUrl + ChatV2Routes.messages(roomId))
            method = HttpMethod.Post
            chatV2Contract()
            setBody(request)
        }.result

    override suspend fun operation(roomId: String, request: ChatV2OperationRequest): NetworkAPIResult<ChatV2OperationResultDto?> =
        httpClient.safeRequest<ChatV2OperationResultDto> {
            url(environment.baseUrl + ChatV2Routes.operations(roomId))
            method = HttpMethod.Post
            chatV2Contract()
            setBody(request)
        }.result

    override suspend fun receipts(roomId: String, request: ChatV2ReceiptRequest): NetworkAPIResult<ChatV2ReceiptResultDto?> =
        httpClient.safeRequest<ChatV2ReceiptResultDto> {
            url(environment.baseUrl + ChatV2Routes.receipts(roomId))
            method = HttpMethod.Put
            chatV2Contract()
            setBody(request)
        }.result

    private fun io.ktor.client.request.HttpRequestBuilder.chatV2Contract() {
        header(ChatV2Routes.CONTRACT_HEADER, ChatV2Routes.CONTRACT_VERSION)
    }
}
