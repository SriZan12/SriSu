package com.srisu.srisu.features.explore.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import com.srisu.srisu.features.moments.data.MomentPreview
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

typealias ExploreResult<T> = NetworkAPIResult<T?>
@Serializable data class ExploreCouple(val id: Long, @SerialName("display_name") val displayName: String? = null, val avatars: List<String> = emptyList()) {
    val label get() = displayName?.takeIf { it.isNotBlank() } ?: "A SriSu couple"
}
@Serializable data class ExploreCard(val couple: ExploreCouple, val preview: MomentPreview,
    @SerialName("is_faved") val isFaved: Boolean = false, @SerialName("has_unopened") val hasUnopened: Boolean = false,
    @SerialName("active_moment_count") val activeCount: Int = 0, val reason: String? = null)
@Serializable data class ExplorePage(val results: List<ExploreCard> = emptyList(), val next: String? = null,
    @SerialName("server_time") val serverTime: String, @SerialName("empty_reason") val emptyReason: String? = null,
    @SerialName("fave_version") val faveVersion: Long = 0, val coverage: String? = null,
    @SerialName("pagination_available") val paginationAvailable: Boolean = true)
@Serializable data class SavedCouple(@SerialName("couple_id") val coupleId: Long, val couple: ExploreCouple? = null,
    val available: Boolean = false, @SerialName("has_active_content") val hasActiveContent: Boolean = false)
@Serializable data class SavedPage(val results: List<SavedCouple> = emptyList(), val next: String? = null,
    @SerialName("fave_version") val faveVersion: Long = 0)
@Serializable data class FaveSnapshot(@SerialName("couple_id") val coupleId: Long,
    @SerialName("is_faved") val isFaved: Boolean, val version: Long, val available: Boolean = true)

interface ExploreSource {
    suspend fun feed(section: String, scope: String, cursor: String?): ExploreResult<ExplorePage>
    suspend fun validate(ids: List<Long>, section: String): ExploreResult<ExplorePage>
    suspend fun saved(cursor: String?): ExploreResult<SavedPage>
    suspend fun faveState(id: Long): ExploreResult<FaveSnapshot>
    suspend fun setFave(id: Long, desired: Boolean, version: Long): ExploreResult<JsonObject>
    suspend fun image(url: String): ByteArray?
}

class ExploreRepository(private val client: HttpClient, private val environment: ApiEnvironment,
    private val media: CoupleProfileRepository) : ExploreSource {
    private fun path(suffix: String) = environment.baseUrl + "api/social/" + suffix
    override suspend fun feed(section: String, scope: String, cursor: String?) = client.safeRequest<ExplorePage> {
        url(path("couple-feed/"))
        if(cursor == null) { parameter("section",section); parameter("scope",scope); parameter("page_size",10) }
        else parameter("cursor",cursor)
    }.result
    override suspend fun validate(ids: List<Long>, section: String) = client.safeRequest<ExplorePage> {
        url(path("couple-feed/visible/")); parameter("couples",ids.joinToString(",")); parameter("section",section)
    }.result
    override suspend fun saved(cursor: String?) = client.safeRequest<SavedPage> {
        url(path("couple-faves/")); if(cursor == null) parameter("page_size",20) else parameter("cursor",cursor)
    }.result
    override suspend fun faveState(id: Long) = client.safeRequest<FaveSnapshot> { url(path("couple-faves/$id/")) }.result
    override suspend fun setFave(id: Long, desired: Boolean, version: Long) = client.safeRequest<JsonObject>(
        shape=if(desired) ResponseShape.ENVELOPE else ResponseShape.EMPTY) {
        url(path("couple-faves/$id/")); method=if(desired) HttpMethod.Put else HttpMethod.Delete
        header(HttpHeaders.IfMatch,version.toString())
    }.result
    override suspend fun image(url: String) = media.image(url,10*1024*1024)
}
