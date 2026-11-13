package com.srisu.srisu.features.home.dashboard.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.challenges.data.ChallengeSource
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.coupleprofile.data.CoupleStatusSource
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.dashboard.state.*
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.sparks.data.SparkSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** Only IDs and bounded status/idea presets are persisted here, never Matrix plaintext. */
@Serializable private data class HomeMutation(val couple: Long, val requestId: String, val kind: String,
    val value: String = "", val id: String = "", val revision: Long = 0, val interested: Boolean = false, val members: List<Long> = emptyList()) {
    val group get() = when (kind) { "status" -> "status"; "plan" -> "plan"; else -> "idea" }
}

@OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)
class HomeViewModel(
    private val moments: MomentSource,
    private val challenges: ChallengeSource,
    private val plans: PlanSource,
    private val sessions: SessionCoordinator,
    private val clock: Clock = Clock.System,
    private val zone: () -> TimeZone = TimeZone::currentSystemDefault,
    private val sparks: SparkSource? = null,
    private val statuses: CoupleStatusSource? = null,
    private val ideas: DateIdeaSource? = null,
    private val matrix: MatrixChatRepository? = null,
    private val resolver: CurrentCoupleRoomResolver? = null,
    private val connections: ConnectionRepository? = null,
    private val apiOrigin: String = "",
    private val profiles: com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository? = null,
    private val reminders: PlanReminders? = null,
) : ViewModel() {
    private val stamp = sessions.stamp()
    private val data = MutableStateFlow(HomeData(name = sessions.currentSession()?.fullName.orEmpty()))
    val state: StateFlow<HomeUiState> = combine(data, reminders?.state ?: MutableStateFlow(ReminderState())) { d, r -> d.toUiState(clock.now(), zone()).copy(reminders = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), data.value.toUiState(clock.now(), zone()))
    private val jobs = mutableMapOf<String, Job>()
    private var expiry: Job? = null
    private var statusExpiry: Job? = null
    private var poll: Job? = null
    private var chatJob: Job? = null
    private var room: String? = null
    private var visible = false
    private var generation = 0L
    private val owner = MatrixChatRoomOwner()
    private val pendingKey = "home-action:${stamp.accountId}:$apiOrigin"
    private val json = Json { ignoreUnknownKeys = true }
    private var pending = runCatching { json.decodeFromString<Map<String, HomeMutation>>(sessions.getSession(pendingKey).orEmpty()) }.getOrDefault(emptyMap())

    init {
        viewModelScope.launch { moments.changes.drop(1).collect { if (visible) refresh() } }
        viewModelScope.launch { challenges.changes.drop(1).collect { if (visible) loadSections() } }
        viewModelScope.launch { plans.changes.drop(1).collect { if (visible) loadSections() } }
        sparks?.let { source -> viewModelScope.launch { source.changes.drop(1).collect { if (visible) loadSections() } } }
        viewModelScope.launch { sessions.state.collect { if (it != stamp) {
            generation++; jobs.values.toList().forEach(Job::cancel); expiry?.cancel(); statusExpiry?.cancel(); poll?.cancel(); closeChat()
            data.value = HomeData(momentsLoading = false, momentsFailed = true)
        } } }
    }

    fun visible(value: Boolean) {
        visible = value; poll?.cancel()
        if (!value) { closeChat(); return }
        refresh()
        poll = viewModelScope.launch { while (isActive) { delay(30_000); refresh() } }
    }
    fun refresh() {
        launchRead("moments", onFailure = { data.update { it.copy(momentsLoading = false, momentsFailed = true, momentsFresh = false) } }) {
            data.update { it.copy(momentsLoading = true) }
            val start = TimeSource.Monotonic.markNow()
            val home = moments.home().value()
            val previous = data.value.moments?.couple
            // Membership changes invalidate every feature projection, including local decrypted previews.
            if (previous?.id != home.couple?.id || previous?.members?.map { it.id } != home.couple?.members?.map { it.id }) {
                generation++; jobs.filterKeys { it != "moments" }.values.toList().forEach(Job::cancel)
                expiry?.cancel(); statusExpiry?.cancel(); closeChat()
                data.value = HomeData(name = home.user.name)
            }
            if (pending.values.any { it.couple != home.couple?.id || it.members != home.couple?.members?.map { m -> m.id }?.sorted() }) storePending(null)
            data.update { it.copy(moments = home, momentsLoading = false, momentsFailed = false, momentsFresh = true,
                pendingAction = pending.isNotEmpty(), blockedActions = pending.keys, notice = if (pending.isNotEmpty()) "An action needs checking. Retry to confirm its result." else it.notice) }
            scheduleMoments(home, start.elapsedNow().inWholeMilliseconds)
            if (home.couple == null) loadInvitations() else { loadSections(); startChat() }
        }
    }
    suspend fun image(url: String): ByteArray? = moments.image(url)

    private fun loadSections() {
        val couple = data.value.moments?.couple?.id ?: return
        launchRead("challenge", { data.update { it.copy(challengeLoading = false, challengeFailed = true) } }) {
            val value = challenges.home().value(); require(value.couple == couple)
            data.update { it.copy(challenge = value, challengeLoading = false, challengeFailed = false) }
        }
        launchRead("plan", { data.update { it.copy(planLoading = false, planFailed = true) } }) {
            val value = plans.home().value(); require(value.couple == couple)
            data.update { it.copy(plan = value, planLoading = false, planFailed = false) }
        }
        profiles?.let { source -> launchRead("profile", { data.update { it.copy(profileLoading = false, profileFailed = true) } }) {
            val value = source.load(couple).value(); require(value.id == couple && value.canEdit)
            data.update { it.copy(profile = value, profileLoading = false, profileFailed = false) }
        } }
        sparks?.let { source -> launchRead("question", { data.update { it.copy(questionLoading = false, questionFailed = true) } }) {
            val value = source.daily(couple, zone().id).value(); require(value.session.couple == couple)
            data.update { it.copy(question = value, questionLoading = false, questionFailed = false) }
        } }
        statuses?.let { source -> launchRead("statuses", { data.update { it.copy(statusesLoading = false, statusesFailed = true, statuses = null) } }) {
            val start = TimeSource.Monotonic.markNow()
            val value = source.get(couple).value(); require(value.couple == couple)
            data.update { it.copy(statuses = value, statusesLoading = false, statusesFailed = false) }
            statusExpiry?.cancel()
            val remaining = value.items.mapNotNull { it.expiresAt }.mapNotNull {
                runCatching { (Instant.parse(it) - Instant.parse(value.serverTime)).inWholeMilliseconds - start.elapsedNow().inWholeMilliseconds }.getOrNull()
            }.minOrNull()
            if (remaining != null) statusExpiry = viewModelScope.launch {
                delay(remaining.coerceAtLeast(1)); data.update { it.copy(statuses = null, statusesLoading = true) }
                if (visible) loadSections()
            }
        } }
        ideas?.let { source -> launchRead("ideas", { data.update { it.copy(ideasLoading = false, ideasFailed = true) } }) {
            val value = source.list(couple).value(); require(value.couple == couple)
            data.update { it.copy(ideas = value, ideasLoading = false, ideasFailed = false) }
        } }
    }

    private fun loadInvitations() {
        val source = connections ?: return
        launchRead("invitations", { data.update { it.copy(invitationsLoading = false, invitationsFailed = true) } }) {
            data.update { it.copy(invitationsLoading = true) }
            val incoming = source.getLoveRequests(20, 1).result.value().results.orEmpty().filterNotNull()
                .firstOrNull { it.connectionStatus == "PENDING" }
            val outgoing = if (incoming == null) source.getSentLoveRequests(20, 1).result.value().results.orEmpty().filterNotNull()
                .firstOrNull { it.connectionStatus == "PENDING" } else null
            val request = incoming ?: outgoing
            data.update { it.copy(invitation = request?.let { r -> HomeInvitationUi(r.receiver?.fullName?.takeIf(String::isNotBlank) ?: "Your partner", incoming != null) },
                invitationsLoading = false, invitationsFailed = false) }
        }
    }

    private fun startChat() {
        val repository = matrix ?: return
        val source = resolver ?: return
        if (!visible || chatJob?.isActive == true) return
        val expected = generation
        chatJob = viewModelScope.launch {
            try {
                val id = source.resolve().value()
                if (expected != generation || !visible) return@launch
                room = id; repository.openSummary(id, owner)
                repository.observeRoom(id).collect { snapshot ->
                    sessions.ensureCurrent(stamp)
                    if (expected == generation) data.update { it.copy(chat = if (snapshot.accountId == stamp.accountId) snapshot else snapshot.copy(messages = emptyList(), unread = null)) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (sessions.stamp() == stamp && expected == generation) data.update {
                it.copy(chat = MatrixChatRoomState("", availability = MatrixChatAvailability.Error("Chat couldn’t refresh. Open chat to retry.")))
            } }
        }
    }
    private fun closeChat() { chatJob?.cancel(); chatJob = null; matrix?.closeSummary(owner); room = null; data.update { it.copy(chat = null) } }

    private fun scheduleMoments(home: MomentHome, elapsed: Long) {
        expiry?.cancel()
        val remaining = home.couple?.members?.mapNotNull { it.nextExpiry }?.mapNotNull {
            runCatching { (Instant.parse(it) - Instant.parse(home.serverTime)).inWholeMilliseconds - elapsed }.getOrNull()
        }?.minOrNull() ?: return
        expiry = viewModelScope.launch { delay(remaining.coerceAtLeast(1)); data.update { it.copy(momentsFresh = false) }; if (visible) refresh() }
    }

    fun setStatus(text: String) {
        val d = data.value; val couple = d.moments?.couple?.id ?: return
        if (d.statuses == null || d.statusesFailed || d.statusesLoading) return
        mutate(HomeMutation(couple, uuid(), "status", text, revision = d.statuses.items.firstOrNull { it.author == stamp.accountId }?.revision ?: 0))
    }
    fun suggest(preset: String) { val couple = data.value.moments?.couple?.id ?: return
        if (preset !in listOf("coffee", "walk", "movie") || data.value.ideasFailed) return
        mutate(HomeMutation(couple, uuid(), "idea", preset)) }
    fun ideaAction(idea: DateIdea, interested: Boolean = true, withdraw: Boolean = false) {
        if (data.value.ideasFailed) return
        mutate(HomeMutation(idea.couple, uuid(), if (withdraw) "withdraw" else "interest", id = idea.id, revision = idea.revision, interested = interested))
    }
    fun acceptPlan(plan: Plan) {
        if (data.value.planFailed || !plan.allows("accept", stamp.accountId, clock.now())) return
        mutate(HomeMutation(plan.couple, uuid(), "plan", id = plan.id.toString(), revision = plan.revision))
    }
    fun reminder(plan: Plan, minutes: Int?) {
        if (!data.value.momentsFresh || data.value.planFailed || plan.couple != data.value.moments?.couple?.id) return
        viewModelScope.launch { reminders?.choose(plan, minutes) }
    }
    fun retryAction() { if (pending.isEmpty()) refresh() else pending.values.toList().forEach(::runMutation) }
    private fun mutate(intent: HomeMutation) {
        if (intent.group in pending || !data.value.momentsFresh || intent.couple != data.value.moments?.couple?.id) return
        val scoped = intent.copy(members = data.value.moments?.couple?.members.orEmpty().map { it.id }.sorted())
        storePending(scoped); runMutation(scoped)
    }
    private fun runMutation(intent: HomeMutation) {
        if (jobs["mutation:${intent.group}"]?.isActive == true || sessions.stamp() != stamp || intent.couple != data.value.moments?.couple?.id || intent.members != data.value.moments?.couple?.members.orEmpty().map { it.id }.sorted()) return
        jobs["mutation:${intent.group}"] = viewModelScope.launch {
            data.update { it.copy(busy = true, notice = null) }
            try {
                when (intent.kind) {
                    "status" -> checkNotNull(statuses).save(intent.couple, intent.value, intent.revision, intent.requestId).value()
                    "idea" -> checkNotNull(ideas).create(intent.couple, intent.value, intent.requestId).value()
                    "interest", "withdraw" -> checkNotNull(ideas).act(intent.id, intent.revision, intent.requestId, intent.kind,
                        buildJsonObject { if (intent.kind == "interest") put("interested", intent.interested) }).value()
                    "plan" -> plans.submit(PlanIntent(intent.requestId, intent.couple, intent.id.toLong(), intent.revision, "accept")).value()
                }
                sessions.ensureCurrent(stamp); storePending(null, intent.group)
                data.update { it.copy(notice = when (intent.kind) { "status" -> "Status saved for 24 hours."; "idea" -> "Idea shared. You can agree a time together."; "interest" -> if (intent.interested) "You’re both interested. Pick a time when you’re ready." else "Your interest was removed."; "withdraw" -> "Idea withdrawn."; else -> "You’re both going." }) }
                loadSections()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (sessions.stamp() == stamp) {
                    val status = (error as? PlanFailure)?.status
                    if (status in listOf(400, 401, 403, 404, 409)) storePending(null, intent.group)
                    if (status in listOf(401, 403, 404)) revoke() else {
                        data.update { it.copy(notice = if (status == 409) "This changed on another device. Refresh and try again." else "Couldn’t confirm this action. Retry to check its result.") }
                        loadSections()
                    }
                }
            } finally { if (sessions.stamp() == stamp) data.update { it.copy(busy = false) } }
        }
    }
    private fun storePending(value: HomeMutation?, group: String? = null) {
        sessions.ensureCurrent(stamp)
        pending = when { value != null -> pending + (value.group to value); group != null -> pending - group; else -> emptyMap() }
        sessions.saveSession(json.encodeToString(pending), pendingKey)
        data.update { it.copy(pendingAction = pending.isNotEmpty(), blockedActions = pending.keys) }
    }
    private fun revoke() {
        generation++; jobs.values.toList().forEach(Job::cancel)
        storePending(null)
        expiry?.cancel(); statusExpiry?.cancel(); closeChat()
        data.value = HomeData(momentsLoading = false, momentsFailed = true, notice = "Your connection needs checking. Refresh Home.")
    }
    private fun launchRead(key: String, onFailure: () -> Unit, block: suspend () -> Unit) {
        if (sessions.stamp() != stamp || jobs[key]?.isActive == true) return
        val expected = generation
        jobs[key] = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (sessions.stamp() == stamp && (expected == generation || key == "moments")) {
                if ((failure as? PlanFailure)?.status in listOf(401, 403, 404)) revoke() else onFailure()
            } }
        }
    }
    /** safeRequest guards the session; this extra check also protects fake/delayed sources. */
    private suspend fun <T> NetworkAPIResult<T?>.value(): T { currentCoroutineContext().ensureActive(); sessions.ensureCurrent(stamp); return planValue() }
    private fun uuid() = Uuid.random().toString()
    override fun onCleared() { matrix?.closeSummary(owner) }
}
