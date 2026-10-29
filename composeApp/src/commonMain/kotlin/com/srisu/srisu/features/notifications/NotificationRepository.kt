package com.srisu.srisu.features.notifications

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.navigation.graph.Route
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class NotificationApi(private val client: HttpClient, environment: ApiEnvironment) {
    private val root = environment.baseUrl + "api/notifications/v1/"
    suspend fun preferences() = client.safeRequest<NotificationPreferences>(readRetries=1) { url(root+"preferences/") }
    suspend fun save(value: PreferenceWrite) = client.safeRequest<NotificationPreferences> { method=HttpMethod.Put;url(root+"preferences/");contentType(ContentType.Application.Json);setBody(value) }
    suspend fun register(value: InstallationRequest) = client.safeRequest<InstallationBinding> { method=HttpMethod.Put;url(root+"installations/");contentType(ContentType.Application.Json);setBody(value) }
    suspend fun resolve(id: String) = client.safeRequest<NotificationDestination>(readRetries=1) { url(root+"deliveries/$id/") }
}

data class NotificationSettingsState(val preferences: NotificationPreferences? = null, val busy: Boolean = false, val error: String? = null, val registrationFailed: Boolean = false)
sealed interface NotificationResolution {
    data class Ready(val route: Route) : NotificationResolution
    data object Unavailable : NotificationResolution
    data object Retry : NotificationResolution
}

class NotificationRepository(private val api: NotificationApi, private val sessions: SessionCoordinator, private val lifetime: ApplicationLifetime) {
    private val mutable = MutableStateFlow(NotificationSettingsState())
    val state = mutable.asStateFlow()
    private val writes = Mutex()
    private var registrationRecovery: String? = null
    private var lastAccount: Long? = null
    private var lastGeneration = sessions.stamp().generation
    private var observedSession = false
    private val job = lifetime.scope.launch {
        combine(sessions.state, lifetime.foreground) { stamp, foreground -> stamp to foreground }.collectLatest { (stamp, foreground) ->
            if (!observedSession && stamp.accountId == null) NativeNotifications.driver?.clearAccount()
            observedSession = true
            if (lastAccount != null && (lastAccount != stamp.accountId || lastGeneration != stamp.generation)) {
                NativeNotifications.clear(); mutable.value = NotificationSettingsState()
            }
            lastAccount=stamp.accountId;lastGeneration=stamp.generation
            if(stamp.accountId != null && foreground) {
                while(isActive) {
                    NativeNotifications.driver?.refresh()
                    reconcile()
                    delay(30_000)
                }
            }
        }
    }
    private val nativeJob = lifetime.scope.launch {
        NativeNotifications.state.drop(1).collectLatest { if(sessions.stamp().accountId != null && lifetime.foreground.value) reconcile() }
    }
    @OptIn(ExperimentalEncodingApi::class)
    private fun binding(): String? = try {
        // The sid never leaves local secure storage except in the existing JWT. It is not telemetry.
        val payload = sessions.accessToken()?.split('.')?.getOrNull(1) ?: return null
        Json.parseToJsonElement(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()).jsonObject["sid"]?.jsonPrimitive?.content
    } catch (_: Exception) { null }
    suspend fun reconcile() = writes.withLock {
        val stamp=sessions.stamp()
        if(stamp.accountId == null) return@withLock
        val native=NativeNotifications.driver
        val registration=try { binding()?.let { native?.registration(it) } } catch (_: Exception) {
            CrashReporter.report(DiagnosticCode.NATIVE_BRIDGE)
            mutable.update { it.copy(registrationFailed=true) }; null
        }
        if(registration != null) {
            val result=api.register(registration).result
            sessions.ensureCurrent(stamp)
            val recoveryKey=registration.installationId+":"+stamp.toString()
            if(result is NetworkAPIResult.Success) registrationRecovery=null
            else if((result as? NetworkAPIResult.Error)?.failure?.status==409 && registrationRecovery!=recoveryKey) {
                registrationRecovery=recoveryKey
                native?.renewRegistration()
            }
            mutable.update { it.copy(registrationFailed = result !is NetworkAPIResult.Success) }
        }
        if(mutable.value.preferences == null) loadLocked()
    }
    private suspend fun loadLocked() {
        val stamp=sessions.stamp()
        mutable.update { it.copy(busy=true,error=null) }
        val result=api.preferences().result
        sessions.ensureCurrent(stamp)
        val value=(result as? NetworkAPIResult.Success)?.response?.takeIf {it.contract=="notifications-1"}
        mutable.update { it.copy(busy=false,preferences=value,
            error=if(value != null) null else "Couldn’t load notification settings. Try again.") }
    }
    fun reload() { lifetime.scope.launch { writes.withLock { loadLocked() } } }
    fun save(push: Boolean?=null, chat: Boolean?=null, activity: Boolean?=null, mute: String="unchanged") {
        lifetime.scope.launch { writes.withLock {
            val stamp=sessions.stamp()
            val old=mutable.value.preferences ?: return@withLock
            mutable.update { it.copy(busy=true,error=null) }
            val result=api.save(PreferenceWrite(old.revision,push ?: old.push,chat ?: old.chat,activity ?: old.activity,mute)).result
            sessions.ensureCurrent(stamp)
            val value=(result as? NetworkAPIResult.Success)?.response?.takeIf {it.contract=="notifications-1"}
            mutable.update { it.copy(busy=false,preferences=value ?: old,
                error=if(value != null) null else "Your change wasn’t saved. Reload and try again.") }
        } }
    }
    suspend fun resolve(signal: NotificationSignal): NotificationResolution {
        val stamp=sessions.stamp()
        if(stamp.accountId == null) return NotificationResolution.Retry
        val result=api.resolve(signal.deliveryId).result
        sessions.ensureCurrent(stamp)
        return when(result) {
            is NetworkAPIResult.Success -> result.response?.route()?.let(NotificationResolution::Ready) ?: run { CrashReporter.report(DiagnosticCode.NOTIFICATION_CONTRACT); NotificationResolution.Unavailable }
            is NetworkAPIResult.Error -> if(result.failure.status in listOf(403,404,410)) NotificationResolution.Unavailable else NotificationResolution.Retry
        }
    }
    fun close() { job.cancel();nativeJob.cancel() }
}
