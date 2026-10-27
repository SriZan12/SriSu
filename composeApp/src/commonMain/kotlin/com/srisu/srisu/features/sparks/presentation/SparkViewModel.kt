package com.srisu.srisu.features.sparks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.sparks.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

internal class SparkRequestFailure(val status: Int?, message: String) : Exception(message)
internal fun <T> NetworkAPIResult<T?>.required(): T = when(this) {
    is NetworkAPIResult.Success -> response ?: throw SparkRequestFailure(null, "Sparks could not be loaded. Please try again.")
    is NetworkAPIResult.Error -> throw SparkRequestFailure(failure.status, failure.message)
}

data class SparkState(val home: SparkHome? = null, val session: SparkSession? = null,
    val account: Long? = null, val draft: SparkDraft = SparkDraft(), val answers: Map<Long, String> = emptyMap(),
    val loading: Boolean = true, val busy: Boolean = false, val fresh: Boolean = false,
    val secure: Boolean = false, val secureConnecting: Boolean = false, val secureMessage: String? = null,
    val error: String? = null, val keyError: Boolean = false,
    val recovery: SparkRecoveryUi = SparkRecoveryUi(), val backupPending: Boolean = false,
    val deck: Int = 0, val question: Int = 0, val startPending: Boolean = false) {
    val selectedDeck get() = home?.decks?.getOrNull(deck)
    val selectedQuestion get() = selectedDeck?.questions?.getOrNull(question)
    val canEdit get() = session?.open == true && session.submitted(account).not() && draft.envelope == null && !busy
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class SparkViewModel(private val source: SparkSource, private val matrix: MatrixChatRepository,
    private val sessions: SessionCoordinator) : ViewModel() {
    private val stamp = sessions.stamp()
    private val mutable = MutableStateFlow(SparkState(account = stamp.accountId))
    val state = mutable.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private val owner = MatrixChatRoomOwner()
    private var started = false
    private var initialId: String? = null
    private var room: String? = null
    private var work: Job? = null
    private var poll: Job? = null
    private var roomObserver: Job? = null
    private var decrypt: Job? = null
    private var visible = false
    private var cardAttempted: String? = null
    private var recoveryWork: Job? = null
    private val intentKey get() = "spark-start:${stamp.accountId}"
    private fun draftKey(id: String) = "spark-draft:${stamp.accountId}:$id"
    init {
        viewModelScope.launch { sessions.state.collect { if (it != stamp) {
            work?.cancel(); poll?.cancel(); decrypt?.cancel(); roomObserver?.cancel(); recoveryWork?.cancel()
            room?.let { id -> matrix.closeRoom(id, owner) }; room = null
            mutable.value = SparkState(loading = false, error = "Sign in again to open your Sparks.")
        } } }
    }
    fun open(id: String?) {
        if (started) return
        started = true; initialId = id; refresh()
    }
    fun visible(value: Boolean) {
        visible = value; poll?.cancel()
        if (!value) { decrypt?.cancel(); recoveryWork?.cancel();
            mutable.update { it.copy(recovery = it.recovery.copy(key = "", confirmation = "", busy = false, creating = false)) }; return }
        if (started) refresh()
        poll = viewModelScope.launch {
            while (isActive) { delay(8_000); if (state.value.session != null) refresh(quiet = true) }
        }
    }
    fun refresh(quiet: Boolean = false) = runWork(showBusy = !quiet) {
        if (quiet && state.value.session != null) {
            accept(source.get(state.value.session!!.id).required()); return@runWork
        }
        val home = source.home().required(); sessions.ensureCurrent(stamp)
        val intent = read<SparkStartIntent>(intentKey)
        mutable.update { it.copy(home = home, startPending = intent != null, fresh = true) }
        // A destination without an ID is the catalogue, even with a Spark in progress.
        // Only an explicit card/session selection opens the private answer screen.
        val id = state.value.session?.id ?: initialId
        initialId = null
        if (id != null) accept(source.get(id).required())
    }
    fun selectDeck(index: Int) {
        if (state.value.startPending || state.value.busy) return
        mutable.update { it.copy(deck = index, question = 0) }
    }
    fun moveQuestion(delta: Int) {
        if (state.value.startPending || state.value.busy) return
        val count = state.value.selectedDeck?.questions?.size ?: return
        if (count > 0) mutable.update { it.copy(question = (it.question + delta + count) % count) }
    }
    fun shuffle() {
        val count = state.value.selectedDeck?.questions?.size ?: return
        if (count > 1) moveQuestion(kotlin.random.Random.nextInt(1, count))
    }
    fun startSpark() = runWork {
        val current = state.value
        current.home?.active?.takeIf { it.open }?.let {
            accept(source.get(it.id).required()); return@runWork
        }
        val couple = current.home?.couple ?: return@runWork
        val question = current.selectedQuestion ?: return@runWork
        val intent = read<SparkStartIntent>(intentKey)?.takeIf { it.couple == couple }
            ?: SparkStartIntent(Uuid.random().toString(), couple, question.id)
        save(intentKey, json.encodeToString(intent))
        mutable.update { it.copy(startPending = true) }
        val result = source.start(intent)
        if (result is NetworkAPIResult.Error && result.failure.status == 409) {
            save(intentKey, ""); mutable.update { it.copy(startPending = false) }
        }
        val session = result.required(); sessions.ensureCurrent(stamp)
        save(intentKey, ""); mutable.update { it.copy(startPending = false) }
        accept(session); source.changed()
    }
    fun openSession(id: String) = runWork { accept(source.get(id).required()) }
    fun browse() {
        if (state.value.session?.open == true || state.value.busy) return
        room?.let { matrix.closeRoom(it, owner) }; room = null; roomObserver?.cancel(); decrypt?.cancel()
        mutable.update { it.copy(session = null, answers = emptyMap(), draft = SparkDraft(), secure = false,
            secureConnecting = false, secureMessage = null) }
        // The home snapshot predates the just-completed Spark. Refresh history
        // now so All decks immediately includes the question the couple finished.
        refresh()
    }
    fun draft(text: String) {
        val current = state.value
        if (!current.canEdit) return
        val id = current.session?.id ?: return
        val draft = SparkDraft(text.take(SPARK_ANSWER_LIMIT))
        mutable.update { it.copy(draft = draft, error = null) }
        try { save(draftKey(id), json.encodeToString(draft)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(error = "Your draft could not be saved on this device. Keep this screen open and try again.") } }
    }
    fun submit() = runWork {
        val session = state.value.session ?: return@runWork
        val draft = state.value.draft
        if (!session.open || session.submitted(stamp.accountId) || draft.text.isBlank()) return@runWork
        // Persist the exact ciphertext BEFORE the request. A timeout/restart reuses it.
        val envelope = draft.envelope ?: matrix.encryptSparkAnswer(session, draft.text.trim()).getOrElse {
            throw SparkRequestFailure(null, "Your answer is kept. Secure chat is not ready to encrypt it yet; retry in a moment.")
        }
        val pending = draft.copy(envelope = envelope)
        save(draftKey(session.id), json.encodeToString(pending))
        mutable.update { it.copy(draft = pending) }
        val result = source.answer(session.id, envelope)
        if (result is NetworkAPIResult.Error && result.failure.status == 400) {
            // A validation rejection is definitive; the user may correct the retained draft.
            val editable = pending.copy(envelope = null)
            save(draftKey(session.id), json.encodeToString(editable))
            mutable.update { it.copy(draft = editable) }
        }
        accept(result.required()); source.changed()
    }
    fun skip() = runWork {
        val session = state.value.session ?: return@runWork
        accept(source.skip(session.id).required()); source.changed()
    }
    fun publishCard() = runWork {
        val session = state.value.session ?: return@runWork
        if (session.creator != stamp.accountId || session.cardEventId.isNotEmpty()) return@runWork
        val event = matrix.publishSparkCard(session).getOrElse {
            throw SparkRequestFailure(null, "Your Spark is saved. Its chat card could not be sent; tap Add to chat to retry.")
        }
        accept(source.card(session.id, event).required())
    }
    fun retryKeys() {
        val session = state.value.session ?: return
        mutable.update { it.copy(keyError = false, secure = false, secureConnecting = true, secureMessage = null) }
        matrix.retryRoom(session.roomId, owner)
    }
    fun openRecovery() {
        mutable.update { it.copy(recovery = it.recovery.copy(open = true, error = null)) }
        recoveryTask {
            val session = state.value.session ?: throw IllegalStateException()
            val status = matrix.sparkRecoveryStatus(session).getOrThrow()
            updateRecovery { it.copy(status = status) }
        }
    }
    fun closeRecovery() {
        recoveryWork?.cancel()
        mutable.update { it.copy(recovery = it.recovery.copy(open = false, key = "", confirmation = "", busy = false, creating = false)) }
    }
    fun generateRecoveryKey() { updateRecovery { it.copy(key = newSparkRecoveryKey(), confirmation = "", creating = true, error = null) } }
    fun recoveryKey(value: String) { updateRecovery { it.copy(key = value.take(100), error = null) } }
    fun recoveryConfirmation(value: String) { updateRecovery { it.copy(confirmation = value.take(100), error = null) } }
    fun recoverExisting() { updateRecovery { it.copy(creating = false, key = "", confirmation = "", status = SparkRecoveryStatus.Locked, error = null) } }
    fun enableRecovery() = recoveryTask {
        val session = state.value.session ?: error("No conversation")
        val ui = state.value.recovery
        require(ui.key.filterNot(Char::isWhitespace) == ui.confirmation.filterNot(Char::isWhitespace))
        matrix.enableSparkRecovery(session, ui.key).getOrThrow()
        updateRecovery { it.copy(status = SparkRecoveryStatus.Ready, key = "", confirmation = "", creating = false) }
        backupHistory()
    }
    fun unlockRecovery() = recoveryTask {
        val session = state.value.session ?: error("No conversation")
        matrix.unlockSparkRecovery(session, state.value.recovery.key).getOrThrow()
        updateRecovery { it.copy(status = SparkRecoveryStatus.Ready, key = "", confirmation = "", creating = false,
            message = "Recovery is unlocked on this phone. Open an older Spark to read its saved answers.") }
        decrypt?.cancel(); decrypt = null
        mutable.update { it.copy(keyError = false) }; decryptAnswers()
    }
    fun backupRecovery() = recoveryTask {
        if (state.value.recovery.status == SparkRecoveryStatus.Pending) {
            matrix.resumeSparkRecovery(requireNotNull(state.value.session)).getOrThrow()
            updateRecovery { it.copy(status = SparkRecoveryStatus.Ready) }
        }
        backupHistory()
    }
    private suspend fun backupHistory() {
        var cursor: String? = null
        var backedUp = 0
        var unavailable = 0
        val visited = mutableSetOf<String>()
        do {
            val page = source.history(cursor).required()
            sessions.ensureCurrent(stamp)
            for (id in page.ids) {
                if (!visited.add(id)) continue
                val session = source.get(id).required()
                sessions.ensureCurrent(stamp)
                if (session.answers.isNotEmpty()) {
                    val result = matrix.backUpSparkAnswers(session)
                    if (result.isSuccess) backedUp += result.getOrThrow() else unavailable += session.answers.size
                    updateRecovery { it.copy(message = "Saved $backedUp answers…") }
                }
            }
            require(page.next == null || page.next != cursor)
            cursor = page.next
        } while (cursor != null)
        updateRecovery { it.copy(message = if (unavailable == 0)
            "$backedUp answers saved in your encrypted backup. New answers are backed up when they sync on this phone."
            else "$backedUp answers saved. $unavailable answers could not be backed up. Keep your old phone and retry when connected; missing keys may require another existing device.") }
    }
    private fun updateRecovery(block: (SparkRecoveryUi) -> SparkRecoveryUi) {
        sessions.ensureCurrent(stamp); mutable.update { it.copy(recovery = block(it.recovery)) }
    }
    private fun recoveryTask(block: suspend () -> Unit) {
        if (recoveryWork?.isActive == true) return
        recoveryWork = viewModelScope.launch {
            updateRecovery { it.copy(busy = true, error = null) }
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { updateRecovery { it.copy(error = "Recovery could not finish. Check your connection and the recovery key for this account, then retry. Existing backups have not been replaced.") } }
            finally { if (sessions.stamp() == stamp) updateRecovery { it.copy(busy = false) } }
        }
    }

    private suspend fun accept(session: SparkSession) {
        sessions.ensureCurrent(stamp)
        val old = state.value.session
        read<SparkStartIntent>(intentKey)?.takeIf { it.id == session.id && it.couple == session.couple }?.let {
            save(intentKey, ""); mutable.update { it.copy(startPending = false) }
        }
        require(session.couple == state.value.home?.couple)
        val draft = if (old?.id == session.id) state.value.draft else read<SparkDraft>(draftKey(session.id)) ?: SparkDraft()
        val finished = !session.open || session.submitted(stamp.accountId)
        if (finished) save(draftKey(session.id), "")
        mutable.update { it.copy(session = session, fresh = true, draft = if (finished) SparkDraft() else draft,
            answers = if (old?.id == session.id && session.state != "skipped") it.answers else emptyMap()) }
        if (session.state == "skipped") { decrypt?.cancel(); return }
        if (room != session.roomId) {
            room?.let { matrix.closeRoom(it, owner) }; roomObserver?.cancel()
            room = session.roomId
            mutable.update { it.copy(secure = false, secureConnecting = true, secureMessage = null) }
            roomObserver = viewModelScope.launch {
                matrix.observeRoom(session.roomId).map { it.availability to it.sparkBackupPending }.distinctUntilChanged().collect { (availability, backupPending) ->
                    mutable.update { it.copy(backupPending = backupPending) }
                    if (availability is MatrixChatAvailability.Unavailable) {
                        decrypt?.cancel(); recoveryWork?.cancel()
                        mutable.update { it.copy(answers = emptyMap(), draft = SparkDraft(), session = null,
                            home = null, secure = false, secureConnecting = false, secureMessage = null,
                            fresh = false, recovery = SparkRecoveryUi(), error = availability.message) }
                        return@collect
                    }
                    val ready = availability is MatrixChatAvailability.Ready
                    mutable.update { it.copy(secure = ready, secureConnecting = availability is MatrixChatAvailability.Loading,
                        secureMessage = when (availability) {
                            is MatrixChatAvailability.Error -> availability.message
                            is MatrixChatAvailability.Offline -> availability.message
                            else -> null
                        }) }
                    if (ready) {
                        if (state.value.recovery.status == null && recoveryWork?.isActive != true) recoveryTask {
                            matrix.sparkRecoveryStatus(requireNotNull(state.value.session)).getOrNull()?.let { status -> updateRecovery { it.copy(status = status) } }
                        }
                        decryptAnswers()
                        val current = state.value.session
                        if (visible && current != null && current.creator == stamp.accountId && current.cardEventId.isEmpty() && cardAttempted != current.id) {
                            cardAttempted = current.id
                            // Wait for the fetch/start operation to finish before the single card attempt.
                            viewModelScope.launch { work?.join(); publishCard() }
                        }
                    }
                }
            }
            matrix.openRoom(session.roomId, owner)
        }
        if (state.value.secure) decryptAnswers()
    }
    private fun decryptAnswers() {
        val session = state.value.session ?: return
        if (session.state == "skipped" || session.answers.isEmpty() || !visible) return
        if (state.value.answers.keys.containsAll(session.answers.map { it.author })) return
        if (decrypt?.isActive == true) return
        decrypt = viewModelScope.launch {
            val values = linkedMapOf<Long, String>()
            for (answer in session.answers) {
                val text = matrix.decryptSparkAnswer(session, answer).getOrNull()
                sessions.ensureCurrent(stamp)
                if (text == null) {
                    mutable.update { it.copy(keyError = true) }; return@launch
                }
                values[answer.author] = text
            }
            if (state.value.session?.id == session.id && state.value.session?.revision == session.revision) {
                mutable.update { it.copy(answers = values, keyError = false) }
            }
        }
    }
    private inline fun <reified T> read(key: String): T? = sessions.getSession(key)?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<T>(it) }
    private fun save(key: String, value: String) { sessions.ensureCurrent(stamp); sessions.saveSession(value, key) }
    private fun runWork(showBusy: Boolean = true, action: suspend () -> Unit) {
        if (work?.isActive == true || sessions.stamp() != stamp) return
        work = viewModelScope.launch {
            if (showBusy) mutable.update { it.copy(busy = true, error = null) }
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                sessions.ensureCurrent(stamp)
                val denied = (failure as? SparkRequestFailure)?.status in listOf(401, 403, 404)
                if (denied) decrypt?.cancel()
                mutable.update { it.copy(error = (failure as? SparkRequestFailure)?.message ?: "Sparks could not be updated. Your draft is kept; please retry.", fresh = false,
                    session = if (denied) null else it.session, answers = if (denied) emptyMap() else it.answers,
                    draft = if (denied) SparkDraft() else it.draft, home = if (denied) null else it.home) }
            } finally { mutable.update { it.copy(loading = false, busy = false) } }
        }
    }
    override fun onCleared() { room?.let { matrix.closeRoom(it, owner) } }
}
