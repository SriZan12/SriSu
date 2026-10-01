package com.srisu.srisu.features.coupleprofile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.coupleprofile.data.*
import com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository
import com.srisu.srisu.utils.FileManager
import com.srisu.srisu.utils.MediaFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.time.Clock
import kotlin.time.Instant

enum class ProfilePage { PROFILE, STORY, STORY_EDIT, SONG, INTERESTS, COVER, POSITION, DATE, SHARING, PLANS, NEW_PLAN, PLAN, ANSWER, DISCOVER }

data class ProfileDraft(
    val section: String, val revision: String, val original: Map<String, String>,
    val values: Map<String, String> = original, val photo: MediaFile? = null, val source: Long? = null,
    val sourceUrl: String? = null, val focalY: Float = .5f, val initialFocalY: Float = .5f,
) {
    val dirty get() = original != values || photo != null || source != null || focalY != initialFocalY
}
data class CoupleProfileState(
    val profile: CoupleProfile? = null, val page: ProfilePage = ProfilePage.PROFILE,
    val loading: Boolean = false, val saving: Boolean = false, val error: ApiError? = null,
    val draft: ProfileDraft? = null, val discardPrompt: Boolean = false,
    val images: Map<String, ByteArray> = emptyMap(), val history: List<ProfileChange> = emptyList(),
    val historyBefore: Long? = null, val covers: List<CoverChoice> = emptyList(), val coversBefore: Long? = null,
    val plans: List<CouplePlan> = emptyList(), val plansBefore: Long? = null, val past: Boolean = false,
    val discovery: List<DiscoveryCard> = emptyList(), val discoveryCursor: String? = null,
    val inviteSent: Set<String> = emptySet(),
    val catalogue: Map<String, List<String>> = emptyMap(), val listLoading: Boolean = false,
)

@OptIn(ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)
class CoupleProfileViewModel(
    private val repository: CoupleProfileRepository,
    private val sessions: SessionCoordinator,
    private val catalogue: InterestCatalogueRepository,
    lifetime: ApplicationLifetime,
) : ViewModel() {
    private val mutable = MutableStateFlow(CoupleProfileState())
    val state = mutable.asStateFlow()
    val accountId get() = sessions.stamp().accountId
    private var target: Long? = null
    private var generation = 0L
    private var pageGeneration = 0L
    private var mediaGeneration = 0L
    private var discoveryRoute = false
    private var entered = false
    private var pendingPage: String? = null
    private var pendingPlan: Long? = null
    private val inviteRequests = mutableMapOf<String, String>()
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var pageJob: Job? = null
    private val mediaJobs = mutableMapOf<String, Job>()
    private var planRequest = Uuid.random().toString()

    init {
        viewModelScope.launch {
            sessions.state.drop(1).collect {
                generation++; inviteRequests.clear(); loadJob?.cancel(); saveJob?.cancel(); pageJob?.cancel()
                mediaJobs.values.forEach { it.cancel() }; mediaJobs.clear()
                mutable.value = CoupleProfileState(page = if(discoveryRoute) ProfilePage.DISCOVER else ProfilePage.PROFILE)
                if (entered && it.accountId != null) refresh()
            }
        }
        viewModelScope.launch {
            lifetime.foreground.collectLatest { foreground ->
                if (foreground) while (isActive) {
                    repeat(30) {
                        delay(1_000)
                        mutable.update { it.copy(discovery = unexpired(it.discovery)) }
                    }
                    if (entered && !state.value.saving) refresh(quiet = true)
                }
            }
        }
    }

    fun enter(id: Long?, initialPage: String? = null, planId: Long? = null) {
        if (!entered || target != id) {
            generation++; inviteRequests.clear(); discoveryRoute = initialPage == "DISCOVER"; target = id; entered = true; pendingPage = initialPage; pendingPlan = planId
            loadJob?.cancel(); saveJob?.cancel(); pageJob?.cancel()
            mediaJobs.values.forEach { it.cancel() }; mediaJobs.clear()
            mutable.value = CoupleProfileState()
        }
        if(initialPage == "DISCOVER") {
            pendingPage = null
            mutable.update { it.copy(page = ProfilePage.DISCOVER) }
            discover()
        } else refresh()
    }
    fun leave() { entered = false; loadJob?.cancel(); pageJob?.cancel() }

    fun refresh(quiet: Boolean = false) {
        if(state.value.page == ProfilePage.DISCOVER) { discover(); return }
        if (loadJob?.isActive == true || state.value.saving) return
        val current = generation
        val stamp = sessions.stamp()
        loadJob = viewModelScope.launch {
            if (!quiet) mutable.update { it.copy(loading = true, error = null) }
            when (val result = repository.load(target)) {
                is NetworkAPIResult.Success -> if (current == generation && stamp == sessions.stamp()) {
                    result.response?.takeIf { target == null || it.id == target }?.let { profile -> accept(profile); mutable.update { it.copy(loading = false) }; refreshVisibleList() }
                        ?: failure(ApiError(NetworkAPIResult.ErrorType.SERIALIZATION, "missing_profile", "Profile is unavailable."))
                }
                is NetworkAPIResult.Error -> if (current == generation && stamp == sessions.stamp()) failure(result.failure)
            }
        }
    }
    private fun accept(profile: CoupleProfile) {
        val previous = state.value.profile
        mediaGeneration++; mediaJobs.values.forEach { it.cancel() }; mediaJobs.clear()
        if (previous != null && (previous.id != profile.id || (previous.canEdit && !profile.canEdit))) {
            pageJob?.cancel(); mutable.value = CoupleProfileState(profile = profile)
        } else mutable.update { it.copy(profile = profile, error = null) }
        val urls = (profile.members.orEmpty().mapNotNull { it.photoUrl } + listOfNotNull(profile.cover?.url)).toSet()
        mutable.update { it.copy(images = it.images.filterKeys { url -> url in urls || it.covers.any { c -> c.url == url } }) }
        // Conditional saves can replace media at the same guarded URL.
        urls.forEach { loadImage(it, force = true) }
        pendingPage?.let { requested ->
            pendingPage = null
            val plan = pendingPlan; pendingPlan = null
            if (requested == "PLAN" && plan != null && profile.canEdit) openPlan(plan)
            else ProfilePage.entries.firstOrNull { it.name == requested }?.let(::open)
        }
    }
    private fun failure(error: ApiError) {
        if (error.kind in setOf(NetworkAPIResult.ErrorType.UNAUTHORIZED, NetworkAPIResult.ErrorType.FORBIDDEN, NetworkAPIResult.ErrorType.NOT_FOUND)) {
            mediaGeneration++; pageGeneration++; pageJob?.cancel(); mediaJobs.values.forEach { it.cancel() }; mediaJobs.clear()
            mutable.value = CoupleProfileState(error = error)
        } else mutable.update { it.copy(error = error, loading = false, saving = false, listLoading = false) }
    }
    fun open(page: ProfilePage) {
        val profile = state.value.profile ?: return
        if (page !in setOf(ProfilePage.PROFILE, ProfilePage.STORY) && !profile.canEdit) return
        pageGeneration++; pageJob?.cancel()
        val section = when (page) {
            ProfilePage.STORY_EDIT -> "story"; ProfilePage.SONG -> "song"; ProfilePage.INTERESTS -> "interests"
            ProfilePage.COVER -> "cover"; ProfilePage.DATE -> "date"; ProfilePage.SHARING -> "sharing"
            ProfilePage.NEW_PLAN -> "plans"; else -> null
        }
        val values = when (section) {
            "story" -> PROMPTS.associateWith { prompt -> profile.story.orEmpty().firstOrNull { it.prompt == prompt && it.authorId == accountId }?.answer.orEmpty() }
            "song" -> mapOf("title" to profile.song?.title.orEmpty(), "artist" to profile.song?.artist.orEmpty(), "band" to profile.song?.band.orEmpty(), "note" to profile.song?.note.orEmpty())
            "interests" -> mapOf("names" to profile.interests?.mine.orEmpty().joinToString("\n"))
            "date" -> mapOf("anniversary_date" to profile.anniversaryDate.orEmpty())
            "sharing" -> mapOf("sections" to profile.sharing?.proposal.orEmpty().joinToString("\n"))
            "plans" -> mapOf("title" to "", "starts_at" to "")
            else -> emptyMap()
        }
        if (page == ProfilePage.NEW_PLAN) planRequest = Uuid.random().toString()
        mutable.update { it.copy(page = page, listLoading = false, draft = section?.let { key -> ProfileDraft(key, profile.revisions[key].orEmpty(), values, focalY = profile.cover?.focalY ?: .5f, initialFocalY = profile.cover?.focalY ?: .5f) }, error = null) }
        when (page) {
            ProfilePage.STORY -> if (profile.canEdit) history()
            ProfilePage.COVER -> covers()
            ProfilePage.PLANS -> plans(false)
            ProfilePage.INTERESTS -> pageJob = viewModelScope.launch {
                val stamp = sessions.stamp(); val gen = generation
                val result = catalogue.load()
                if (stamp == sessions.stamp() && gen == generation) mutable.update { it.copy(catalogue = result.value?.interests.orEmpty().filterNotNull().filter { i -> i.name != null }.groupBy { i -> i.category?.label.orEmpty() }.mapValues { (_, rows) -> rows.mapNotNull { i -> i.name } }) }
            }
            else -> Unit
        }
    }
    fun openAnswer(prompt: String) {
        if(prompt !in PROMPTS || state.value.profile?.canEdit != true) return
        open(ProfilePage.STORY_EDIT)
        mutable.update { it.copy(page = ProfilePage.ANSWER, draft = it.draft?.let { d ->
            val values = d.values.filterKeys { key -> key == prompt }
            d.copy(original = values, values = values)
        }) }
    }
    fun edit(key: String, value: String) {
        if (state.value.saving) return
        mutable.update { it.copy(draft = it.draft?.let { d -> d.copy(values = d.values + (key to value)) }, error = null) }
    }
    fun toggle(key: String, value: String) {
        val selected = state.value.draft?.values?.get(key).orEmpty().split('\n').filter(String::isNotBlank).toMutableList()
        if (!selected.remove(value)) selected += value
        edit(key, selected.joinToString("\n"))
    }
    fun back(): Boolean {
        if(state.value.page == ProfilePage.POSITION && !state.value.saving) { pickDifferentPhoto(); return false }
        if (state.value.saving) return false
        if (state.value.draft?.dirty == true) { mutable.update { it.copy(discardPrompt = true) }; return false }
        if (state.value.page in setOf(ProfilePage.PROFILE, ProfilePage.DISCOVER)) return true
        discard(); return false
    }
    fun keepEditing() { mutable.update { it.copy(discardPrompt = false) } }
    fun discard() { pageJob?.cancel(); mutable.update { it.copy(page = ProfilePage.PROFILE, draft = null, discardPrompt = false, error = null, covers = emptyList(), images = emptyMap()) }; refresh() }
    fun reloadDraft() { if(state.value.loading || state.value.saving) return; val page = state.value.page; if(page == ProfilePage.ANSWER) state.value.draft?.values?.keys?.firstOrNull()?.let(::openAnswer) else open(page) }

    fun save(remove: Boolean = false, sharingAction: String = "propose") {
        val snapshot = state.value; val profile = snapshot.profile ?: return
        val draft = snapshot.draft ?: return
        if (!profile.canEdit || snapshot.saving) return
        val invalid = validateDraft(draft, remove)
        if(invalid.isNotEmpty()) {
            failure(ApiError(NetworkAPIResult.ErrorType.BAD_REQUEST,"validation_failed","Check the highlighted fields.",fields=invalid))
            return
        }
        loadJob?.cancel()
        val gen = generation; val stamp = sessions.stamp()
        mutable.update { it.copy(saving = true, error = null) }
        saveJob = viewModelScope.launch {
            if (draft.section == "plans") {
                val body = buildJsonObject { put("request_id", planRequest); put("title", draft.values["title"].orEmpty()); put("starts_at", draft.values["starts_at"].orEmpty()) }
                when (val result = repository.createPlan(profile.id, body)) {
                    is NetworkAPIResult.Success -> if (gen == generation && stamp == sessions.stamp()) { if(result.response == null) failure(missingSave()) else { mutable.update { it.copy(saving = false, draft = null) }; open(ProfilePage.PLANS); refresh() } }
                    is NetworkAPIResult.Error -> if (gen == generation && stamp == sessions.stamp()) failure(result.failure)
                }
                return@launch
            }
            val result = if (draft.section == "cover") repository.cover(profile.id, draft.revision, draft.focalY, draft.photo, draft.source, remove)
            else repository.save(profile.id, draft.section, buildJsonObject {
                put("expected_revision", draft.revision)
                when (draft.section) {
                    "story" -> put("answers", buildJsonObject { draft.values.forEach { (k,v) -> put(k,v) } })
                    "song" -> { put("remove",remove); draft.values.forEach { (k,v) -> put(k,v) } }
                    "interests" -> put("names", JsonArray(draft.values["names"].orEmpty().split('\n').filter(String::isNotBlank).map(::JsonPrimitive)))
                    "date" -> put("anniversary_date", draft.values["anniversary_date"].orEmpty().takeIf(String::isNotBlank)?.let(::JsonPrimitive) ?: JsonNull)
                    "sharing" -> { put("action", sharingAction); put("sections", JsonArray(draft.values["sections"].orEmpty().split('\n').filter(String::isNotBlank).map(::JsonPrimitive))) }
                }
            })
            if (gen != generation || stamp != sessions.stamp()) return@launch
            when (result) {
                is NetworkAPIResult.Success -> result.response?.let { saved ->
                    accept(saved); mutable.update { it.copy(page = ProfilePage.PROFILE, draft = null, saving = false, covers = emptyList()) }
                } ?: failure(ApiError(NetworkAPIResult.ErrorType.SERIALIZATION, "missing_profile", "Please refresh to check whether your change was saved."))
                is NetworkAPIResult.Error -> failure(result.failure)
            }
        }
    }
    fun position(value: Float) { if (!state.value.saving) mutable.update { it.copy(draft = it.draft?.copy(focalY = value.coerceIn(0f,1f))) } }
    fun chooseCover(choice: CoverChoice) { mutable.update { it.copy(page = ProfilePage.POSITION, draft = it.draft?.copy(source = choice.id, sourceUrl = choice.url, photo = null)) }; loadImage(choice.url) }
    fun choosePhoto(path: String) {
        val gen = generation; val stamp = sessions.stamp()
        pageJob = viewModelScope.launch {
            val file = FileManager().createProfilePhotoFromPath(path)
            if (gen != generation || stamp != sessions.stamp()) return@launch
            if (file == null) failure(ApiError(NetworkAPIResult.ErrorType.BAD_REQUEST,"invalid_image","Choose a JPEG, PNG or WebP under 5 MB and 20 megapixels."))
            else mutable.update { it.copy(page = ProfilePage.POSITION, draft = it.draft?.copy(photo = file, source = null, sourceUrl = null)) }
        }
    }
    private fun validateDraft(draft: ProfileDraft, remove: Boolean): Map<String,List<String>> {
        if(remove) return emptyMap()
        val errors = mutableMapOf<String,List<String>>()
        draft.values.forEach { (key,value) ->
            val limit = when { draft.section == "story" || key == "note" -> 240; key in setOf("title","artist","band") -> 120; else -> null }
            if(limit != null && unicodeLength(value) > limit) errors[key] = listOf("max_length")
        }
        if(draft.section in setOf("song","plans") && draft.values["title"].isNullOrBlank()) errors["title"] = listOf("required")
        if(draft.section == "plans" && draft.values["starts_at"].isNullOrBlank()) errors["starts_at"] = listOf("required")
        if(draft.section == "interests") {
            val names = draft.values["names"].orEmpty().split('\n').filter(String::isNotBlank)
            if(names.size > 20 || names.any { unicodeLength(it) > 100 }) errors["names"] = listOf("max_length")
        }
        return errors
    }
    fun cameraPhoto(photo: MediaFile) { if (state.value.draft?.section != "cover" || state.value.profile?.canEdit != true) return; mutable.update { it.copy(page = ProfilePage.POSITION, draft = it.draft?.copy(photo = photo, source = null, sourceUrl = null)) } }
    fun cameraError() { failure(ApiError(NetworkAPIResult.ErrorType.BAD_REQUEST, "camera_unavailable", "Allow camera access in Settings, or choose a photo from your library.")) }
    fun pickDifferentPhoto() { mutable.update { it.copy(page = ProfilePage.COVER) } }
    fun loadImage(url: String, force: Boolean = false) {
        if (mediaJobs[url]?.isActive == true || (!force && url in state.value.images)) return
        val gen = generation; val media = mediaGeneration; val stamp = sessions.stamp()
        mediaJobs[url] = viewModelScope.launch {
            val bytes = repository.image(url)
            if (gen == generation && media == mediaGeneration && stamp == sessions.stamp()) mutable.update { it.copy(images = if (bytes == null) it.images - url else boundedImages(it.images + (url to bytes))) }
        }
    }
    private fun boundedImages(images: Map<String, ByteArray>): Map<String, ByteArray> {
        val result = images.toMutableMap()
        while (result.size > 24 || result.values.sumOf { it.size.toLong() } > 20L * 1024 * 1024) result.remove(result.keys.first())
        return result
    }
    fun history(more: Boolean = false) = listRequest { id, valid ->
        val result = repository.history(id, if (more) state.value.historyBefore else null)
        if(!valid()) return@listRequest
        when (result) {
            is NetworkAPIResult.Success -> result.response?.let { page -> mutable.update { it.copy(history = (if (more) it.history else emptyList()) + page.results, historyBefore = page.nextBefore) } }
            is NetworkAPIResult.Error -> failure(result.failure)
        }
    }
    fun covers(more: Boolean = false) = listRequest { id, valid ->
        val result = repository.covers(id, if (more) state.value.coversBefore else null)
        if(!valid()) return@listRequest
        when (result) {
            is NetworkAPIResult.Success -> result.response?.let { page -> mutable.update { it.copy(covers = page.results, coversBefore = page.nextBefore) }; page.results.forEach { loadImage(it.url) } }
            is NetworkAPIResult.Error -> failure(result.failure)
        }
    }
    fun plans(past: Boolean = state.value.past, more: Boolean = false) {
        if (state.value.listLoading) return
        mutable.update { it.copy(past = past, plans = if (more) it.plans else emptyList()) }
        listRequest { id, valid ->
            val result = repository.plans(id, past, if (more) state.value.plansBefore else null)
            if(!valid()) return@listRequest
            when (result) {
            is NetworkAPIResult.Success -> result.response?.let { page -> mutable.update { it.copy(plans = ((if (more) it.plans else emptyList()) + page.results).distinctBy(CouplePlan::id).sortedBy(CouplePlan::startsAt), plansBefore = page.nextBefore) } }
            is NetworkAPIResult.Error -> failure(result.failure)
        } }
    }
    private fun listRequest(block: suspend (Long, () -> Boolean) -> Unit) {
        val id = state.value.profile?.id ?: return
        if (state.value.listLoading) return
        val stamp = sessions.stamp(); val gen = generation; val page = pageGeneration
        val valid = { stamp == sessions.stamp() && gen == generation && page == pageGeneration && state.value.profile?.id == id }
        pageJob = viewModelScope.launch {
            mutable.update { it.copy(listLoading = true) }
            try { block(id, valid) } finally { if(valid()) mutable.update { it.copy(listLoading = false) } }
        }
    }
    fun respond(plan: CouplePlan, response: String? = null, note: String = "", completed: Boolean? = null) {
        val id = state.value.profile?.id ?: return
        if (state.value.saving) return
        val gen = generation; val stamp = sessions.stamp()
        mutable.update { it.copy(saving = true, error = null) }
        saveJob = viewModelScope.launch {
            val result = repository.respond(id, plan.id, buildJsonObject { put("expected_revision",plan.revision); response?.let { put("response",it); put("response_note",note) }; completed?.let { put("completed",it) } })
            if (gen != generation || stamp != sessions.stamp()) return@launch
            when (result) {
                is NetworkAPIResult.Success -> { if(result.response == null) { failure(missingSave()); return@launch }; mutable.update { it.copy(saving = false) }; if (state.value.page == ProfilePage.PLAN) openPlan(plan.id) else plans() }
                is NetworkAPIResult.Error -> failure(result.failure)
            }
        }
    }
    fun discover(more: Boolean = false) {
        if(loadJob?.isActive == true) return
        val stamp = sessions.stamp(); val gen = generation
        loadJob = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            val result = repository.discovery(if(more) state.value.discoveryCursor else null)
            if(stamp != sessions.stamp() || gen != generation) return@launch
            when(result) {
                is NetworkAPIResult.Success -> result.response?.let { page -> mutable.update { it.copy(discovery = unexpired(page.results), discoveryCursor = page.next?.let { url -> io.ktor.http.Url(url).parameters["cursor"] }, loading = false) } } ?: failure(ApiError(NetworkAPIResult.ErrorType.SERIALIZATION, "missing_discovery", "Please try again."))
                is NetworkAPIResult.Error -> mutable.update { it.copy(discovery = emptyList(), loading = false, error = result.failure) }
            }
        }
    }
    fun openPlan(planId: Long) {
        val id = state.value.profile?.id ?: return
        val stamp = sessions.stamp(); val gen = generation
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            mutable.update { it.copy(page = ProfilePage.PLAN, plans = emptyList(), listLoading = true) }
            when (val result = repository.plan(id, planId)) {
                is NetworkAPIResult.Success -> if (stamp == sessions.stamp() && gen == generation) (result.response?.let { plan -> mutable.update { it.copy(plans = listOf(plan), listLoading = false) } } ?: failure(ApiError(NetworkAPIResult.ErrorType.SERIALIZATION, "missing_plan", "Please try again.")))
                is NetworkAPIResult.Error -> if (stamp == sessions.stamp() && gen == generation) failure(result.failure)
            }
        }
    }
    private fun unexpired(cards: List<DiscoveryCard>) = cards.filter { card ->
        runCatching { Instant.parse(card.preview.expiresAt) > Clock.System.now() }.getOrDefault(false)
    }
    private fun refreshVisibleList() {
        if(state.value.listLoading) return
        when(state.value.page) {
            ProfilePage.STORY -> if(state.value.profile?.canEdit == true) history()
            ProfilePage.PLANS -> plans()
            ProfilePage.PLAN -> state.value.plans.firstOrNull()?.let { openPlan(it.id) }
            else -> Unit
        }
    }
    fun invite(prompt: String) {
        val id = state.value.profile?.takeIf { it.canEdit }?.id ?: return
        if (state.value.saving) return
        val stamp = sessions.stamp(); val gen = generation
        val request = inviteRequests.getOrPut(prompt) { Uuid.random().toString() }
        mutable.update { it.copy(saving = true, error = null) }
        saveJob = viewModelScope.launch {
            when (val result = repository.invite(id, prompt, request)) {
                is NetworkAPIResult.Success -> if (stamp == sessions.stamp() && gen == generation) if(result.response == null) failure(missingSave()) else mutable.update { it.copy(saving = false, inviteSent = it.inviteSent + prompt) }
                is NetworkAPIResult.Error -> if (stamp == sessions.stamp() && gen == generation) failure(result.failure)
            }
        }
    }
    fun fave() {
        val profile = state.value.profile ?: return
        if (!profile.canFave || state.value.saving) return
        val stamp = sessions.stamp(); val gen = generation
        mutable.update { it.copy(saving = true) }
        saveJob = viewModelScope.launch {
            val result = repository.fave(profile.id, !profile.isFaved)
            if (stamp != sessions.stamp() || gen != generation) return@launch
            when (result) {
                is NetworkAPIResult.Success -> { mutable.update { it.copy(saving = false) }; refresh() }
                is NetworkAPIResult.Error -> failure(result.failure)
            }
        }
    }
    private fun missingSave() = ApiError(NetworkAPIResult.ErrorType.SERIALIZATION, "missing_saved_resource", "Please refresh to check whether your change was saved.")
    companion object { val PROMPTS = listOf("how_met", "first_move", "first_impression") }
}

internal fun unicodeLength(value: String): Int = value.indices.count { i -> !(value[i].code in 0xDC00..0xDFFF && i > 0 && value[i-1].code in 0xD800..0xDBFF) }
