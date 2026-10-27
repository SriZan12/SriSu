package com.srisu.srisu.features.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.plans.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class PlanState(val home: PlanHome? = null, val plan: Plan? = null, val page: PlanPage = PlanPage(),
    val account: Long? = null, val tab: String = "upcoming", val loading: Boolean = true, val busy: Boolean = false,
    val fresh: Boolean = false, val error: String? = null, val pending: PlanIntent? = null,
    val draft: PlanDraft? = null, val editing: Boolean = false, val cardBusy: Boolean = false, val cardError: String? = null,
    val notice: String? = null, val addedItemRequest: String? = null) {
    val canAct get() = fresh && !busy && pending == null
    val partner get() = home?.members?.firstOrNull { it.id!=account }
}
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class PlanViewModel(private val source: PlanSource, private val matrix: MatrixChatRepository,
    private val sessions: SessionCoordinator, environment: ApiEnvironment,
    val reminders: PlanReminders, private val device: PlanDevice): ViewModel() {
    private val stamp=sessions.stamp()
    private val key="plans:${stamp.accountId}:${environment.baseUrl}"
    private val json=Json { ignoreUnknownKeys=true }
    private val mutable=MutableStateFlow(PlanState(account=stamp.accountId))
    val state=mutable.asStateFlow()
    private var started=false
    private var desiredId:Long?=null
    private var work:Job?=null
    private var poll:Job?=null
    private var cardWork:Job?=null
    private var readOnly=false
    private var generation=0L
    private val owner=MatrixChatRoomOwner()
    private var room:String?=null
    init { viewModelScope.launch { sessions.state.collect { if(it!=stamp) {
        work?.cancel();poll?.cancel();cardWork?.cancel();closeRoom()
        mutable.value=PlanState(loading=false,error="Sign in again to open your Plans.")
    } } } }
    fun open(id:Long?,showInvitations:Boolean=false) {
        if(started) return
        started=true;desiredId=id
        mutable.update { it.copy(tab=if(id==null && showInvitations) "invitations" else "upcoming",
            draft=runCatching { json.decodeFromString<PlanDraft>(sessions.getSession("$key:draft").orEmpty()) }.getOrNull(),
            pending=runCatching { json.decodeFromString<PlanIntent>(sessions.getSession("$key:pending").orEmpty()) }.getOrNull()) }
        refresh()
    }
    fun visible(active:Boolean) {
        poll?.cancel()
        if(!active) { cardWork?.cancel();closeRoom();return }
        if(started) refresh(quiet=true)
        poll=viewModelScope.launch { while(isActive) { delay(8_000);refresh(quiet=true) } }
    }
    fun refresh(quiet:Boolean=false)=task(showBusy=!quiet,reading=true) { load(!quiet) }
    private suspend fun load(reloadList:Boolean=true) {
        val home=source.home().planValue();sessions.ensureCurrent(stamp)
        if(state.value.draft?.couple!=home.couple) storeDraft(null)
        if(state.value.pending?.couple!=home.couple) storePending(null)
        mutable.update { it.copy(home=home,fresh=true) }
        if(home.couple==null) {
            desiredId=null;mutable.update { it.copy(plan=null,page=PlanPage(),editing=false) };return
        }
        val id=desiredId ?: state.value.plan?.id
        if(id!=null) { accept(readPlan(id));desiredId=null }
        else {
            // Reconcile loaded pages without dropping rows or changing their stable identities.
            val wanted=if(reloadList) 0 else state.value.page.items.size
            var page=source.list(state.value.tab).planValue()
            val seen=mutableSetOf<String>()
            while(page.items.size<wanted && page.next!=null && seen.add(page.next)) {
                val next=source.list(state.value.tab,page.next).planValue()
                page=next.copy(items=(page.items+next.items).distinctBy(Plan::id))
            }
            sessions.ensureCurrent(stamp);mutable.update { it.copy(page=page) }
        }
        if(state.value.pending==null) mutable.update { it.copy(error=null) }
        reminders.refresh()
    }
    private fun leaveDetail() {
        if(readOnly && work?.isActive==true) { ++generation;work?.cancel();work=null }
        desiredId=null;cardWork?.cancel();closeRoom()
    }
    fun browse(tab:String=state.value.tab) {
        if(state.value.busy || state.value.pending!=null) return
        leaveDetail();mutable.update { it.copy(plan=null,tab=tab,editing=false,page=PlanPage(),cardError=null,notice=null) };refresh()
    }
    fun detail(id:Long)=task { desiredId=id;accept(readPlan(id));desiredId=null }
    private class MissingPlan(val id:Long):Exception("This plan is no longer available.")
    private suspend fun readPlan(id:Long):Plan = try { source.get(id).planValue() }
        catch(error:PlanFailure) { if(error.status==404) throw MissingPlan(id) else throw error }
    fun more()=task {
        val cursor=state.value.page.next ?: return@task
        val page=source.list(state.value.tab,cursor).planValue();sessions.ensureCurrent(stamp)
        mutable.update { it.copy(page=page.copy(items=(it.page.items+page.items).distinctBy(Plan::id))) }
    }
    fun editPlan(change:Boolean=false) {
        val current=state.value
        if(!current.canAct) return
        val couple=current.home?.couple ?: return
        val plan=current.plan.takeIf { change }
        if(change && (plan==null || !plan.allows("propose",stamp.accountId))) return
        leaveDetail()
        if(current.draft!=null && current.draft.planId!=plan?.id) {
            mutable.update { it.copy(editing=true,notice="Finish or discard your saved draft before starting another.") };return
        }
        val existing=current.draft?.takeIf { it.planId==plan?.id && it.couple==couple }
        storeDraft(existing ?: PlanDraft(couple,title=plan?.change?.title ?: plan?.title.orEmpty(),
            startsAt=plan?.change?.startsAt ?: plan?.startsAt.orEmpty(),place=plan?.change?.place ?: plan?.place.orEmpty(),
            timeZone=plan?.change?.timeZone ?: plan?.timeZone?.takeIf { it.isNotBlank() } ?: TimeZone.currentSystemDefault().id,planId=plan?.id,revision=plan?.revision))
        mutable.update { it.copy(editing=true,error=null,notice=null) }
    }
    fun resumeDraft() { if(state.value.canAct && state.value.draft!=null) { leaveDetail();mutable.update { it.copy(editing=true) } } }
    fun edit(draft:PlanDraft) { if(!state.value.busy && state.value.pending==null) storeDraft(draft) }
    fun closeEditor() { if(!state.value.busy) mutable.update { it.copy(editing=false) } }
    fun discardDraft() { if(!state.value.busy && state.value.pending==null) { storeDraft(null);closeEditor() } }
    private fun storeDraft(draft:PlanDraft?) { sessions.ensureCurrent(stamp);sessions.saveSession(draft?.let { json.encodeToString(it) }.orEmpty(),"$key:draft");mutable.update { it.copy(draft=draft) } }
    private fun storePending(intent:PlanIntent?) { sessions.ensureCurrent(stamp);sessions.saveSession(intent?.let { json.encodeToString(it) }.orEmpty(),"$key:pending");mutable.update { it.copy(pending=intent) } }
    fun save() {
        val draft=state.value.draft ?: return
        if(!state.value.canAct) return
        if(draft.title.isBlank() || draft.title.length>120 || draft.place.length>240 || runCatching { Instant.parse(draft.startsAt)>Clock.System.now() }.getOrDefault(false).not()) {
            mutable.update { it.copy(error="Add a title and choose a future date and time.") };return
        }
        submit(PlanIntent(Uuid.random().toString(),draft.couple,draft.planId,draft.revision,if(draft.planId==null) null else "propose",buildJsonObject {
            put("title",draft.title.trim());put("starts_at",draft.startsAt);put("place",draft.place.trim());put("time_zone",draft.timeZone)
        }))
    }
    fun action(action:String,values:JsonObject=buildJsonObject {}) {
        val plan=state.value.plan ?: return
        if(!state.value.canAct || !plan.allows(action,stamp.accountId)) return
        submit(PlanIntent(Uuid.random().toString(),plan.couple,plan.id,plan.revision,action,values))
    }
    fun retry() { state.value.pending?.let(::submit) ?: refresh() }
    private fun submit(intent:PlanIntent)=task {
        storePending(intent)
        val result=source.submit(intent);sessions.ensureCurrent(stamp)
        if(result is NetworkAPIResult.Error && result.failure.status in listOf(400,403,404,409)) {
            storePending(null)
            if(result.failure.status==409) {
                load()
                // Retain the text, but require a deliberate save against the refreshed revision.
                state.value.draft?.let { draft -> if(draft.planId==state.value.plan?.id) storeDraft(draft.copy(revision=state.value.plan?.revision)) }
            }
        }
        val plan=result.planValue()
        storePending(null)
        if(intent.planId==null || intent.action=="propose") { storeDraft(null);mutable.update { it.copy(editing=false) } }
        accept(plan)
        if(intent.action=="add_item") mutable.update {it.copy(addedItemRequest=intent.requestId)}
        source.changed();reminders.refresh()
        if(intent.planId==null && plan.creator==stamp.accountId && plan.state=="invited") publishCard()
    }
    private fun accept(plan:Plan) {
        sessions.ensureCurrent(stamp)
        require(plan.couple==state.value.home?.couple)
        if(plan.ended || plan.response=="yes") { cardWork?.cancel();closeRoom() }
        mutable.update { it.copy(plan=plan,fresh=true) }
    }
    fun reminder(minutes:Int?) {
        val plan=state.value.plan ?: return
        viewModelScope.launch { reminders.choose(plan,minutes) }
    }
    fun calendar() {
        val plan=state.value.plan ?: return
        if(!state.value.fresh || plan.ended || plan.response!="yes") return
        mutable.update {it.copy(notice=null)}
        device.calendar(plan.title,plan.instant.toEpochMilliseconds(),plan.place) { message ->
            if(sessions.stamp()==stamp) mutable.update { it.copy(notice=message) }
        }
    }
    fun place() { state.value.plan?.place?.takeIf { it.isNotBlank() }?.let(device::openPlace) }
    fun publishCard() {
        val plan=state.value.plan ?: return
        if(cardWork?.isActive==true || plan.ended || plan.response=="yes" || plan.creator!=stamp.accountId || plan.cardEventId.isNotBlank()) return
        cardWork=viewModelScope.launch {
            mutable.update { it.copy(cardBusy=true,cardError=null) }
            try {
                val id=plan.roomId ?: error("Unavailable")
                if(room!=id) { closeRoom();room=id;matrix.openRoom(id,owner) }
                withTimeout(25_000) { matrix.observeRoom(id).first { it.availability is MatrixChatAvailability.Ready } }
                val event=matrix.publishPlanCard(plan).getOrThrow()
                val updated=source.card(plan.id,event).planValue();sessions.ensureCurrent(stamp)
                mutable.update { it.copy(plan=it.plan?.let { p -> if(p.id==updated.id) p.copy(cardEventId=updated.cardEventId) else p }) }
            } catch(cancelled:CancellationException) {
                if(cancelled is TimeoutCancellationException) mutable.update { it.copy(cardError="Your plan is saved. Retry sending its chat invitation.") } else throw cancelled
            } catch(_:Exception) { if(sessions.stamp()==stamp) mutable.update { it.copy(cardError="Your plan is saved. Retry sending its chat invitation.") } }
            finally { if(sessions.stamp()==stamp) mutable.update { it.copy(cardBusy=false) } }
        }
    }
    private fun task(showBusy:Boolean=true,reading:Boolean=false,block:suspend ()->Unit) {
        if(sessions.stamp()!=stamp) return
        if(work?.isActive==true) { if(readOnly && !reading) work?.cancel() else return }
        readOnly=reading;val task=++generation
        work=viewModelScope.launch {
            mutable.update { it.copy(busy=showBusy,error=if(showBusy) null else it.error) }
            try { block() }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) {
                if(sessions.stamp()==stamp) {
                    val missing=error as? MissingPlan
                    val denied=(error as? PlanFailure)?.status in listOf(401,403,404)
                    if(denied) { storeDraft(null);storePending(null);cardWork?.cancel();closeRoom() }
                    if(missing!=null) {
                        // A stale link must not erase a draft for another plan in the same couple.
                        if(state.value.draft?.planId==missing.id) storeDraft(null)
                        if(state.value.pending?.planId==missing.id) storePending(null)
                        desiredId=null;cardWork?.cancel();closeRoom()
                    }
                    mutable.update { it.copy(fresh=false,error=missing?.message ?: (error as? PlanFailure)?.message ?: "Couldn’t update your plan. Retry to check its status.",
                        home=if(denied) null else it.home,plan=if(denied || missing!=null) null else it.plan,
                        page=if(denied) PlanPage() else if(missing!=null) it.page.copy(items=it.page.items.filterNot {p->p.id==missing.id}) else it.page,
                        editing=if(denied || missing!=null) false else it.editing) }
                }
            } finally { if(sessions.stamp()==stamp && task==generation) mutable.update { it.copy(loading=false,busy=false) } }
        }
    }
    private fun closeRoom() { room?.let { matrix.closeRoom(it,owner) };room=null }
    override fun onCleared() { closeRoom() }
}
