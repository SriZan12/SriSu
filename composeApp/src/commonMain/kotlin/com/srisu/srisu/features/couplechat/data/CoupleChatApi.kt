package com.srisu.srisu.features.couplechat.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.data.remote.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.url
import io.ktor.client.request.setBody
import io.ktor.client.request.parameter
import io.ktor.http.HttpMethod
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.http.content.ByteArrayContent
import io.ktor.client.request.header

class CoupleChatApi(private val client: HttpClient, private val environment: ApiEnvironment) {
    suspend fun moment(id: Long): ResultHandler<SharedMoment?> = client.safeRequest(shape=com.srisu.srisu.core.data.remote.ResponseShape.RAW) {
        method=HttpMethod.Get;url(environment.baseUrl+"api/social/couple-moments/$id/")
    }
    suspend fun moments(coupleId: Long): ResultHandler<SharedMoments?> = client.safeRequest {
        method=HttpMethod.Get;url(environment.baseUrl+"api/social/couple-moments/");parameter("couple",coupleId);parameter("page_size",10)
    }
    private val root get() = "${environment.baseUrl}api/couple-chat/v1/"
    suspend fun snapshot(): ResultHandler<CoupleChatSnapshot?> = client.safeRequest(readRetries = 1) {
        method = HttpMethod.Get
        url(root + "state/")
    }

    suspend fun register(value: ChatRegistration): ResultHandler<ChatDevice?> = client.safeRequest {
        method = HttpMethod.Post; url(root + "devices/"); contentType(ContentType.Application.Json); setBody(value)
    }

    suspend fun replenish(value: ChatKeyPublication): ResultHandler<ChatDevice?> = client.safeRequest {
        method = HttpMethod.Post; url(root + "devices/keys/"); contentType(ContentType.Application.Json); setBody(value)
    }

    suspend fun participantDevice(roomId: String, deviceId: String): ResultHandler<ChatDevice?> = client.safeRequest(readRetries = 1) {
        method = HttpMethod.Get; url(root + "rooms/$roomId/devices/$deviceId/")
    }

    suspend fun claim(roomId: String, value: ChatBundleClaim): ResultHandler<ChatBundle?> = client.safeRequest {
        method = HttpMethod.Post; url(root + "rooms/$roomId/bundles/claim/"); contentType(ContentType.Application.Json); setBody(value)
    }

    suspend fun send(roomId: String, value: ChatEnvelope): ResultHandler<ChatAcceptance?> = client.safeRequest {
        method = HttpMethod.Post; url(root + "rooms/$roomId/operations/"); contentType(ContentType.Application.Json); setBody(value)
    }

    suspend fun changes(roomId: String, deviceId: String, after: Long): ResultHandler<ChatChanges?> = client.safeRequest(readRetries = 1) {
        method = HttpMethod.Get; url(root + "rooms/$roomId/changes/")
        parameter("device_id", deviceId); parameter("after", after); parameter("limit", 50)
    }

    suspend fun reserve(room: String, value: AttachmentReservation): ResultHandler<AttachmentState?> = client.safeRequest {
        method = HttpMethod.Post; url(root+"rooms/$room/attachments/"); contentType(ContentType.Application.Json); setBody(value)
    }
    suspend fun upload(room: String, id: String, device: String, bytes: ByteArray, progress: (Float) -> Unit): ResultHandler<AttachmentState?> = client.safeRequest {
        method = HttpMethod.Put; url(root+"rooms/$room/attachments/$id/upload/"); header("X-Chat-Device",device)
        contentType(ContentType.Application.OctetStream); setBody(ByteArrayContent(bytes,ContentType.Application.OctetStream))
        timeout { requestTimeoutMillis = 300_000 }
        onUpload { sent, length -> if (length != null && length > 0) progress((sent.toFloat()/length).coerceIn(0f,1f)) }
    }
    suspend fun finalize(room: String, id: String, device: String): ResultHandler<AttachmentState?> = client.safeRequest {
        method = HttpMethod.Post; url(root+"rooms/$room/attachments/$id/finalize/"); contentType(ContentType.Application.Json);setBody(AttachmentDevice(device))
    }
    suspend fun cancelAttachment(room: String, id: String, device: String): ResultHandler<AttachmentState?> = client.safeRequest {
        method = HttpMethod.Post;url(root+"rooms/$room/attachments/$id/cancel/");contentType(ContentType.Application.Json);setBody(AttachmentDevice(device))
    }
    suspend fun mediaCapability(room: String, id: String, device: String): ResultHandler<MediaCapability?> = client.safeRequest {
        method = HttpMethod.Post;url(root+"rooms/$room/attachments/$id/capability/");contentType(ContentType.Application.Json);setBody(AttachmentDevice(device))
    }
    suspend fun download(room: String, descriptor: AttachmentDescriptor, capability: String): ResultHandler<ByteArray?> =
        client.readBoundedChatCiphertext(root+"rooms/$room/attachments/${descriptor.id}/content/",capability,descriptor.ciphertextSize)

    suspend fun spark(room: String,id: String,device: String,initialize: Boolean=false): ResultHandler<SparkStatus?> = client.safeRequest {
        url(root+"rooms/$room/sparks/$id/")
        if(initialize) {method=HttpMethod.Post;setBody(AttachmentDevice(device))} else {method=HttpMethod.Get;parameter("device_id",device)}
    }
    suspend fun answerSpark(room: String,id: String,envelope: ChatEnvelope): ResultHandler<SparkStatus?> = client.safeRequest {
        method=HttpMethod.Post;url(root+"rooms/$room/sparks/$id/answer/");setBody(envelope)
    }
}
