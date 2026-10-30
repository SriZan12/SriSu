package com.srisu.srisu.features.challenges.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.challenges.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

internal class ChallengeFailure(val status: Int?, message: String) : Exception(message)
internal fun <T> NetworkAPIResult<T?>.challengeValue(): T = when (this) {
    is NetworkAPIResult.Success -> response ?: throw ChallengeFailure(null, "Challenges could not be loaded. Try again.")
    is NetworkAPIResult.Error -> throw ChallengeFailure(failure.status, failure.message)
}

data class ChallengeState(val home: ChallengeHome? = null, val session: ChallengeSession? = null,
    val selected: ChallengeActivity? = null, val history: ChallengeHistory = ChallengeHistory(),
    val account: Long? = null, val loading: Boolean = true, val busy: Boolean = false, val fresh: Boolean = false,
    val error: String? = null, val pending: ChallengeIntent? = null, val collection: String = "gestures",
    val apart: Boolean = false, val quick: Boolean = false, val historyTab: Boolean = false,
    val cardBusy: Boolean = false, val cardError: String? = null) {
    val partner get() = home?.members?.firstOrNull { it.id != account }
    val canAct get() = fresh && !busy && pending == null
    val activities get() = home?.collections?.firstOrNull { it.id == collection }?.activities.orEmpty()
        .filter { (!apart || it.mode != "together") && (!quick || it.minutes <= 15) }
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class ChallengeViewModel(private val source: ChallengeSource, private val matrix: MatrixChatRepository,
    private val sessions: SessionCoordinator, environment: ApiEnvironment) : ViewModel() {
    private val stamp = sessions.stamp()
    private val storageKey = "challenge-pending:${stamp.accountId}:${environment.baseUrl}"
    private val mutable = MutableStateFlow(ChallengeState(account = stamp.accountId))
    val state = mutable.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private val owner = MatrixChatRoomOwner()
    private var work: Job? = null
    private var poll: Job? = null
    private var cardWork: Job? = null
    private var room: String? = null
    private var started = false
    private var initialId: String? = null
    private var reading = false
    private var taskId = 0L

    init { viewModelScope.launch { sessions.state.collect { if (it != stamp) {
        work?.cancel(); poll?.cancel(); cardWork?.cancel(); closeRoom()
        mutable.value = ChallengeState(loading = false, error = "Sign in again to open your Challenges.")
    } } } }
    fun open(id: String?) { if (!started) { started = true; initialId = id; refresh() } }
    fun visible(active: Boolean) {
        poll?.cancel()
        if (!active) { cardWork?.cancel(); closeRoom(); return }
        if (started) refresh()
        poll = viewModelScope.launch { while (isActive) { delay(8_000); refresh(quiet = true) } }
    }
    fun refresh(quiet: Boolean = false) = task(!quiet, readOnly = true) { load(refreshHistory = !quiet) }
    private suspend fun load(refreshHistory: Boolean = true) {
        val home = source.home().challengeValue(); sessions.ensureCurrent(stamp)
        val pending = sessions.getSession(storageKey)?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<ChallengeIntent>(it) }
        if (pending != null && pending.couple != home.couple) savePending(null)
        mutable.update { it.copy(home = home, pending = pending?.takeIf { p -> p.couple == home.couple }, fresh = true) }
        val id = initialId ?: state.value.session?.id
        if (id != null) {
            accept(source.get(id).challengeValue())
            initialId = null
        }
        if (refreshHistory && state.value.historyTab && state.value.session == null) {
            val page = source.history().challengeValue(); sessions.ensureCurrent(stamp)
            mutable.update { it.copy(history = page) }
        }
        if (state.value.pending == null) mutable.update { it.copy(error = null) }
    }
    private fun leaveDetail() {
        // A quiet read may still be waiting for the old detail. It must not undo navigation.
        if (reading && work?.isActive == true) { ++taskId; work?.cancel(); work = null }
        initialId = null
        cardWork?.cancel(); closeRoom()
    }
    fun select(activity: ChallengeActivity) {
        if (!state.value.busy && state.value.pending == null) {
            leaveDetail()
            mutable.update { it.copy(selected = activity, session = null, error = null, cardError = null) }
        }
    }
    fun collection(id: String) { mutable.update { it.copy(collection = id) } }
    fun filter(apart: Boolean, quick: Boolean) { mutable.update { it.copy(apart = apart, quick = quick) } }
    fun browse(history: Boolean = false) {
        if (state.value.busy) return
        leaveDetail()
        mutable.update { it.copy(selected = null, session = null, historyTab = history, cardError = null) }; refresh()
    }
    fun openSession(id: String) = task {
        initialId = id
        accept(source.get(id).challengeValue())
        initialId = null
    }
    fun moreHistory() = task {
        val before = state.value.history.next ?: return@task
        val page = source.history(before).challengeValue(); sessions.ensureCurrent(stamp)
        mutable.update { it.copy(history = page.copy(items = (it.history.items + page.items).distinctBy { c -> c.id })) }
    }
    fun invite() {
        val current = state.value
        if (!current.canAct || current.home?.current != null) return
        val template = current.selected ?: return
        val couple = current.home?.couple ?: return
        submit(ChallengeIntent(Uuid.random().toString(), couple, templateId = template.id))
    }
    fun action(action: String) {
        val current = state.value; val session = current.session ?: return
        if (!current.canAct || !session.allows(action, stamp.accountId)) return
        submit(ChallengeIntent(Uuid.random().toString(), session.couple, sessionId = session.id, action = action, revision = session.revision))
    }
    fun retry() { state.value.pending?.let(::submit) ?: refresh() }
    private fun savePending(intent: ChallengeIntent?) {
        sessions.ensureCurrent(stamp)
        sessions.saveSession(intent?.let { json.encodeToString(it) }.orEmpty(), storageKey)
        mutable.update { it.copy(pending = intent) }
    }
    private fun submit(intent: ChallengeIntent) = task {
        savePending(intent)
        val result = source.submit(intent)
        sessions.ensureCurrent(stamp)
        if (result is NetworkAPIResult.Error && result.failure.status in listOf(400, 403, 404, 409)) {
            savePending(null)
            if (result.failure.status == 409) load()
        }
        val session = result.challengeValue()
        savePending(null); accept(session); source.changed()
        mutable.update { it.copy(home = it.home?.copy(current = session.takeIf { c -> c.live })) }
        if (session.state == "invited" && session.creator == stamp.accountId && session.cardEventId.isBlank()) publishCard()
    }
    private fun accept(session: ChallengeSession) {
        sessions.ensureCurrent(stamp)
        require(session.couple == state.value.home?.couple)
        if (session.state != "invited") { cardWork?.cancel(); closeRoom() }
        mutable.update { it.copy(session = session, selected = null, fresh = true) }
    }
    fun publishCard() {
        val session = state.value.session ?: return
        if (cardWork?.isActive == true || session.state != "invited" || session.creator != stamp.accountId || session.cardEventId.isNotBlank()) return
        cardWork = viewModelScope.launch {
            mutable.update { it.copy(cardBusy = true, cardError = null) }
            try {
                val id = session.roomId ?: throw IllegalStateException()
                if (room != id) { closeRoom(); room = id; matrix.openRoom(id, owner) }
                withTimeout(25_000) { matrix.observeRoom(id).first { it.availability is MatrixChatAvailability.Ready } }
                val event = matrix.publishChallengeCard(session).getOrThrow()
                val updated = source.card(session.id, event).challengeValue(); sessions.ensureCurrent(stamp)
                // Publication may finish after navigation or another progress update.
                mutable.update { it.copy(session = it.session?.let { c -> if (c.id == updated.id) c.copy(cardEventId = updated.cardEventId) else c }) }
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException && sessions.stamp() == stamp)
                    mutable.update { it.copy(cardError = "Your invitation is saved. The chat card could not be sent yet.") }
                else throw cancelled
            } catch (_: Exception) {
                if (sessions.stamp() == stamp) mutable.update { it.copy(cardError = "Your invitation is saved. The chat card could not be sent yet.") }
            } finally { if (sessions.stamp() == stamp) mutable.update { it.copy(cardBusy = false) } }
        }
    }
    private fun task(showBusy: Boolean = true, readOnly: Boolean = false, block: suspend () -> Unit) {
        if (sessions.stamp() != stamp) return
        if (work?.isActive == true) {
            if (reading && !readOnly) work?.cancel() else return
        }
        reading = readOnly
        val currentTask = ++taskId
        work = viewModelScope.launch {
            mutable.update { it.copy(busy = showBusy, error = if (showBusy) null else it.error) }
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (sessions.stamp() == stamp) {
                    val denied = (failure as? ChallengeFailure)?.status in listOf(401, 403, 404)
                    if (denied) { cardWork?.cancel(); closeRoom() }
                    mutable.update { it.copy(error = (failure as? ChallengeFailure)?.message ?: "Could not update your Challenge. Retry to check its status.",
                        fresh = false, home = if (denied) null else it.home, session = if (denied) null else it.session,
                        selected = if (denied) null else it.selected, history = if (denied) ChallengeHistory() else it.history) }
                }
            } finally { if (sessions.stamp() == stamp && currentTask == taskId) mutable.update { it.copy(loading = false, busy = false) } }
        }
    }
    private fun closeRoom() { room?.let { matrix.closeRoom(it, owner) }; room = null }
    override fun onCleared() { closeRoom() }
}
