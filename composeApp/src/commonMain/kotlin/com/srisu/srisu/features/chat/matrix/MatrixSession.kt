package com.srisu.srisu.features.chat.matrix

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiEnvironmentKey
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.data.remote.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val MATRIX_PROTOCOL_ID = "matrix-e2ee-v1"
internal const val MATRIX_JWT_LOGIN_TYPE = "org.matrix.login.jwt"

@Serializable
data class MatrixSessionDto(
    @SerialName("protocol_id") val protocolId: String,
    @SerialName("homeserver_url") val homeserverUrl: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("room_alias") val roomAlias: String? = null,
    @SerialName("membership_epoch") val membershipEpoch: Long,
    @SerialName("relationship_id") val relationshipId: Long,
    @SerialName("self_user_id") val selfUserId: Long,
    @SerialName("self_matrix_user_id") val selfMatrixUserId: String,
    @SerialName("partner_user_id") val partnerUserId: Long,
    @SerialName("partner_matrix_user_id") val partnerMatrixUserId: String,
    @SerialName("initiator_user_id") val initiatorUserId: Long,
    @SerialName("login_type") val loginType: String,
    @SerialName("login_token") val loginToken: String,
    @SerialName("login_token_expires_at") val loginTokenExpiresAt: String,
    @SerialName("login_token_one_time") val loginTokenOneTime: Boolean,
    @SerialName("refresh_token_enabled") val refreshTokenEnabled: Boolean,
    @SerialName("access_token_expires_in_seconds") val accessTokenExpiresInSeconds: Long? = null,
) {
    fun validated(expectedAccountId: Long, allowInsecureHomeserver: Boolean = false): MatrixSessionDto {
        require(protocolId == MATRIX_PROTOCOL_ID) { "Unsupported secure-chat protocol." }
        require(loginType == MATRIX_JWT_LOGIN_TYPE) { "Unsupported Matrix login type." }
        require(!loginTokenOneTime) { "Unexpected one-time Matrix login contract." }
        require(!refreshTokenEnabled) { "Matrix refresh tokens must remain disabled." }
        require(accessTokenExpiresInSeconds != null && accessTokenExpiresInSeconds in 300L..900L) {
            "Matrix access-token lifetime is outside the supported bound."
        }
        require(loginToken.isNotBlank() && loginToken.length <= 16_384) { "Invalid Matrix login token." }
        require(loginTokenExpiresAt.isNotBlank()) { "Matrix login expiry is missing." }
        require(selfUserId == expectedAccountId) { "Matrix session belongs to another account." }
        require(selfUserId != partnerUserId && relationshipId > 0 && membershipEpoch > 0)
        require(initiatorUserId == selfUserId || initiatorUserId == partnerUserId)
        require(selfMatrixUserId.isMatrixUserId() && partnerMatrixUserId.isMatrixUserId())
        require(roomId.startsWith("!") && ':' in roomId && roomId.length <= 512) { "Invalid Matrix room." }
        roomAlias?.let { require(it.startsWith("#") && ':' in it && it.length <= 512) { "Invalid Matrix room alias." } }
        val homeserver = Url(homeserverUrl)
        require(homeserver.protocol == URLProtocol.HTTPS ||
            (allowInsecureHomeserver && homeserver.protocol == URLProtocol.HTTP))
        require(homeserver.user == null && homeserver.password == null && homeserver.fragment.isEmpty())
        require(homeserver.encodedPath in setOf("", "/") && homeserver.parameters.isEmpty()) {
            "Homeserver must be an HTTPS origin."
        }
        return this
    }
}

private fun String.isMatrixUserId(): Boolean = startsWith("@") && ':' in this && length <= 512

private val djangoRoomIdRegex =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")

internal fun normalizeDjangoRoomIdOrNull(roomId: String): String? =
    roomId.takeIf(djangoRoomIdRegex::matches)?.lowercase()

internal fun validatedDjangoRoomId(roomId: String): String =
    requireNotNull(normalizeDjangoRoomIdOrNull(roomId)) { "Invalid room identifier." }

interface MatrixSessionRemote {
    suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?>
}

class MatrixSessionApiService(
    private val httpClient: HttpClient,
    private val environment: ApiEnvironment = httpClient.attributes[ApiEnvironmentKey],
) : MatrixSessionRemote {
    override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> =
        httpClient.safeRequest<MatrixSessionDto> {
            url(environment.baseUrl + "api/chat/v2/rooms/${validatedDjangoRoomId(roomId)}/matrix/session/")
            method = HttpMethod.Post
        }.result
}
