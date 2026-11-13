package com.srisu.srisu.features.home.profile.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.local.CatalogueDao
import com.srisu.srisu.core.data.local.CatalogueSnapshot
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Public reference data only. Writes/profile edits always require the server. */
data class CatalogueResult(val value: InterestResponse?, val offline: Boolean = false, val error: ApiError? = null)

@OptIn(ExperimentalTime::class)
class InterestCatalogueRepository(
    private val dao: CatalogueDao,
    private val remote: ProfileApiService,
    environment: ApiEnvironment,
    private val sessions: SessionCoordinator,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val scope = "interests:1:${environment.baseUrl}"
    private val mutex = Mutex()

    suspend fun load(force: Boolean = false): CatalogueResult = mutex.withLock {
        val stamp = sessions.stamp()
        val cached = storageOrNull { dao.read(scope) }
        val age = cached?.let { now() - it.fetchedAtMillis }
        val value = cached?.let { storageOrNull { ApiJson.decodeFromString<InterestResponse>(it.payload).takeIf(::valid) } }
        if (!force && value != null && age != null && age in 0..FRESH_MS) {
            sessions.ensureCurrent(stamp)
            return@withLock CatalogueResult(value)
        }
        val result = remote.getInterestList().result
        sessions.ensureCurrent(stamp)
        when (result) {
            is NetworkAPIResult.Success -> {
                val response = result.response?.takeIf(::valid) ?: return@withLock CatalogueResult(null, error = ApiError(NetworkAPIResult.ErrorType.SERIALIZATION, "missing_data", "The interests catalogue is unavailable."))
                storageOrNull { dao.replace(CatalogueSnapshot(scope, ApiJson.encodeToString(response), now())) }
                sessions.ensureCurrent(stamp)
                CatalogueResult(response)
            }
            is NetworkAPIResult.Error -> {
                // Never bypass an authentication/permission rejection with cached content.
                val canFallback = result.failure.kind in setOf(NetworkAPIResult.ErrorType.NETWORK, NetworkAPIResult.ErrorType.TIMEOUT, NetworkAPIResult.ErrorType.SERVER)
                CatalogueResult(if (canFallback && age != null && age in 0..MAX_STALE_MS) value else null,
                    offline = canFallback && value != null && age != null && age in 0..MAX_STALE_MS, error = result.failure)
            }
        }
    }

    private fun valid(value: InterestResponse): Boolean = value.interests?.all {
        it != null && it.id != null && it.id > 0 && it.name != null
    } == true

    private suspend fun <T> storageOrNull(block: suspend () -> T): T? = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null } // Public cache failure must not make the API unavailable.

    companion object {
        const val FRESH_MS = 300_000L
        const val MAX_STALE_MS = 86_400_000L
    }
}
