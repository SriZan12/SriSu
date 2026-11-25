package com.srisu.srisu.features.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.SessionCoordinator
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*

@Serializable data class SettingsPartner(
    @SerialName("user_id") val userId: Long, val name: String, val photo: String? = null,
    @SerialName("couple_id") val coupleId: Long,
    @SerialName("connection_id") val connectionId: Long, val revision: Long,
    @SerialName("connected_days") val connectedDays: Int? = null,
)
@Serializable data class SettingsContext(val contract: String, val partner: SettingsPartner? = null)
@Serializable private data class DisconnectWrite(@SerialName("connection_id") val connectionId: Long,
    @SerialName("expected_revision") val revision: Long)
interface SettingsSource {
    suspend fun load(): NetworkAPIResult<SettingsContext?>
    suspend fun disconnect(partner: SettingsPartner): NetworkAPIResult<SettingsContext?>
}
class SettingsRepository(private val client: HttpClient, environment: ApiEnvironment) : SettingsSource {
    private val root = environment.baseUrl + "api/social/settings/"
    override suspend fun load() = client.safeRequest<SettingsContext>(readRetries = 1) { url(root) }.result
    override suspend fun disconnect(partner: SettingsPartner) = client.safeRequest<SettingsContext> {
        url(root + "disconnect/"); method = HttpMethod.Post
        contentType(ContentType.Application.Json); setBody(DisconnectWrite(partner.connectionId, partner.revision))
    }.result
}
data class SettingsState(val loading: Boolean = true, val loaded: Boolean = false,
    val partner: SettingsPartner? = null, val error: String? = null, val busy: Boolean = false,
    val notice: String? = null)
class SettingsViewModel(private val source: SettingsSource, private val sessions: SessionCoordinator,
    private val connectionChanged: () -> Unit) : ViewModel() {
    private val stamp = sessions.stamp()
    private val mutable = MutableStateFlow(SettingsState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    init { viewModelScope.launch { sessions.state.collect {
        if (it != stamp) { work?.cancel(); mutable.value = SettingsState(loading=false) }
    } } }
    fun reload() {
        if (state.value.busy || work?.isActive == true || sessions.stamp() != stamp) return
        work = viewModelScope.launch {
            mutable.update { it.copy(loading=true, error=null) }
            val result = source.load()
            sessions.ensureCurrent(stamp)
            val context = (result as? NetworkAPIResult.Success)?.response?.takeIf { it.contract == "settings-1" }
            mutable.value = if (context != null) SettingsState(loading=false, loaded=true, partner=context.partner)
                else SettingsState(loading=false, error="Couldn’t check your connection. Try again.")
        }
    }
    fun disconnect(expected: SettingsPartner) {
        if (state.value.busy || state.value.loading || !state.value.loaded || state.value.partner != expected || sessions.stamp() != stamp) return
        mutable.update { it.copy(busy=true, error=null, notice=null) }
        work = viewModelScope.launch {
            val result = source.disconnect(expected)
            sessions.ensureCurrent(stamp)
            val value = (result as? NetworkAPIResult.Success)?.response
            if (value?.contract == "settings-1" && value.partner == null) {
                mutable.value = SettingsState(loading=false, loaded=true, notice="Partner disconnected.")
                connectionChanged()
            } else {
                // A failed response can follow a committed write. Reconcile before allowing another confirmation.
                val latest = source.load()
                sessions.ensureCurrent(stamp)
                val context = (latest as? NetworkAPIResult.Success)?.response?.takeIf { it.contract == "settings-1" }
                mutable.value = if (context != null && context.partner == null)
                    SettingsState(loading=false, loaded=true, notice="Partner disconnected.").also { connectionChanged() }
                else SettingsState(loading=false, loaded=context != null, partner=context?.partner,
                    error="Couldn’t confirm the disconnection. Check your connection and try again.")
            }
        }
    }
    fun signOut() { sessions.clearIfCurrent(stamp) }
}

enum class AppAppearance(val label: String) { SYSTEM("Use device setting"), LIGHT("Light"), DARK("Dark") }
/** Device-wide UI preference only; never changes the partner's appearance or chat palette. */
class AppearancePreferences(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("settings.appearance.v1")
    val appearance: Flow<AppAppearance> = flow {
        var hasValue=false
        // Keep observing after a temporary disk failure. A catch-only fallback completes
        // the stream, so later successful saves would otherwise never update the UI.
        emitAll(store.data.onEach {hasValue=true}.retryWhen { error, attempt ->
            // Native DataStore and its Okio file system use separate IO exception
            // classes; both are java.io.IOException aliases on Android.
            if (error !is okio.IOException && error !is androidx.datastore.core.IOException) return@retryWhen false
            if(!hasValue) {emit(emptyPreferences());hasValue=true}
            delay((1_000L * (attempt.coerceAtMost(29) + 1)))
            true
        })
    }.map { prefs ->
        AppAppearance.entries.firstOrNull { it.name == prefs[key] } ?: AppAppearance.SYSTEM
    }.distinctUntilChanged()
    suspend fun save(value: AppAppearance) { store.edit { it[key] = value.name } }
}
