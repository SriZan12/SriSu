package com.srisu.srisu.features.plans.data
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
interface PlanSource {
    val changes: StateFlow<Long>
    fun changed()
    suspend fun home(): NetworkAPIResult<PlanHome?>
    suspend fun list(mode: String, cursor: String? = null): NetworkAPIResult<PlanPage?>
    suspend fun get(id: Long): NetworkAPIResult<Plan?>
    suspend fun submit(intent: PlanIntent): NetworkAPIResult<Plan?>
    suspend fun reminders(ids: List<Long>): NetworkAPIResult<PlanPage?>
    suspend fun card(id: Long, event: String): NetworkAPIResult<Plan?>
}
class PlanRepository(private val client: HttpClient, private val environment: ApiEnvironment): PlanSource {
    private val mutable = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = mutable
    override fun changed() { mutable.value += 1 }
    private fun path(value: String) = environment.baseUrl + "api/social/plans/" + value
    override suspend fun home() = client.safeRequest<PlanHome> { url(path("home/")) }.result
    override suspend fun list(mode: String, cursor: String?) = client.safeRequest<PlanPage> { url(path("")); parameter("mode", mode); cursor?.let { parameter("cursor", it) } }.result
    override suspend fun get(id: Long) = client.safeRequest<Plan> { url(path("$id/")) }.result
    override suspend fun submit(intent: PlanIntent) = client.safeRequest<Plan> {
        url(path(intent.planId?.let { "$it/actions/" } ?: "")); method = HttpMethod.Post
        setBody(buildJsonObject {
            put("request_id",intent.requestId)
            if (intent.planId == null) { put("couple",intent.couple); intent.values.forEach { (k,v) -> put(k,v) } }
            else { put("expected_revision",intent.revision); put("action",intent.action); put("values",intent.values) }
        })
    }.result
    override suspend fun reminders(ids: List<Long>) = client.safeRequest<PlanPage> { url(path("reminders/")); parameter("ids",ids.joinToString(",")) }.result
    override suspend fun card(id: Long, event: String) = client.safeRequest<Plan> { url(path("$id/card/")); method = HttpMethod.Put; setBody(buildJsonObject { put("event_id",event) }) }.result
}
class PlanFailure(val status: Int?, message: String): Exception(message)
fun <T> NetworkAPIResult<T?>.planValue(): T = when(this) {
    is NetworkAPIResult.Success -> response ?: throw PlanFailure(null,"Plans could not be loaded. Try again.")
    is NetworkAPIResult.Error -> throw PlanFailure(failure.status,failure.message)
}
