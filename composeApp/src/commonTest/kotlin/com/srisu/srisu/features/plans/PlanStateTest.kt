package com.srisu.srisu.features.plans
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.plans.presentation.*
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.sparks.FakeSparkMatrix
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PlanStateTest {
    private val environment=ApiEnvironment("https://api.example.invalid/")
    private val lives=mutableListOf<ApplicationLifetime>()
    private val models=mutableListOf<PlanViewModel>()
    @BeforeTest fun before() {Dispatchers.setMain(StandardTestDispatcher())}
    @AfterTest fun after() {models.forEach {it.viewModelScope.cancel()};lives.forEach {it.close()};Dispatchers.resetMain()}
    private fun reminders(source:FakePlans,sessions:SessionCoordinator,device:FakePlanDevice)=PlanReminders(source,sessions,environment,device,ApplicationLifetime().also {lives+=it})
    private fun vm(source:FakePlans=FakePlans(),sessions:SessionCoordinator=MomentFixtures.sessions(),device:FakePlanDevice=FakePlanDevice()):PlanViewModel =
        PlanViewModel(source,FakeSparkMatrix(),sessions,environment,reminders(source,sessions,device),device).also {models+=it}
    @Test fun invitationEntryLoadsInvitationsAndDoesNotOverrideLaterTabSelection()=runTest {
        val requested=mutableListOf<String>()
        val source=object:FakePlans(){override suspend fun list(mode:String,cursor:String?):com.srisu.srisu.core.data.remote.NetworkAPIResult.Success<PlanPage?> {
            requested+=mode;return super.list(mode,cursor)
        }}
        val model=vm(source);model.open(null,showInvitations=true);runCurrent()
        assertEquals("invitations",model.state.value.tab);assertEquals(listOf("invitations"),requested)
        model.browse("past");runCurrent();model.open(null,showInvitations=true);runCurrent()
        assertEquals("past",model.state.value.tab);assertEquals(listOf("invitations","past"),requested)
        val normal=vm();normal.open(null);runCurrent();assertEquals("upcoming",normal.state.value.tab)
        val detail=vm();detail.open(7,showInvitations=true);runCurrent();assertEquals(7,detail.state.value.plan?.id)
    }
    @Test fun draftSurvivesCloseAndViewModelRecreation()=runTest {
        val sessions=MomentFixtures.sessions();val source=FakePlans();val one=vm(source,sessions)
        one.open(null);runCurrent();one.editPlan();one.edit(requireNotNull(one.state.value.draft).copy(title="Dinner",startsAt="2099-11-03T12:00:00Z",place="Home"));one.closeEditor();one.viewModelScope.cancel()
        val two=vm(source,sessions);two.open(null);runCurrent();assertEquals("Dinner",two.state.value.draft?.title);two.resumeDraft();assertTrue(two.state.value.editing)
    }
    @Test fun editingAnotherPlanKeepsTheExistingDraft()=runTest {
        val vm=vm();vm.open(null);runCurrent();vm.editPlan();vm.edit(requireNotNull(vm.state.value.draft).copy(title="Keep this idea"));vm.closeEditor()
        vm.detail(7);runCurrent();vm.editPlan(true)
        assertEquals("Keep this idea",vm.state.value.draft?.title);assertNull(vm.state.value.draft?.planId);assertNotNull(vm.state.value.notice)
    }
    @Test fun quietRefreshReconcilesPartnerActions()=runTest {
        val source=FakePlans().apply {page=PlanPage(listOf(plan))};val vm=vm(source);vm.open(null);runCurrent()
        source.page=PlanPage();vm.refresh(true);runCurrent();assertTrue(vm.state.value.page.items.isEmpty())
    }
    @Test fun uncertainCreationRetainsDraftAndExactIntentAcrossRestart()=runTest {
        val sessions=MomentFixtures.sessions();val source=FakePlans().apply {networkSubmit=true};val one=vm(source,sessions)
        one.open(null);runCurrent();one.editPlan();one.edit(requireNotNull(one.state.value.draft).copy(title="Dinner",startsAt="2099-11-03T12:00:00Z"));one.save();runCurrent()
        val request=one.state.value.pending;assertNotNull(request);assertNotNull(one.state.value.draft);one.viewModelScope.cancel()
        val two=vm(source,sessions);two.open(null);runCurrent();assertEquals(request,two.state.value.pending)
        source.networkSubmit=false;two.retry();runCurrent();assertEquals(source.requests.first(),source.requests.last());assertNull(two.state.value.pending);assertNull(two.state.value.draft)
    }
    @Test fun transientDetailFailureRetriesTheRequestedPlan()=runTest {
        val source=FakePlans().apply {networkGet=true};val vm=vm(source);vm.open(7);runCurrent();assertNull(vm.state.value.plan)
        source.networkGet=false;vm.retry();runCurrent();assertEquals(7,vm.state.value.plan?.id)
    }
    @Test fun unavailablePlanPreservesAnUnrelatedDraftAcrossRestart()=runTest {
        val sessions=MomentFixtures.sessions();val source=FakePlans();val one=vm(source,sessions)
        one.open(null);runCurrent();one.editPlan();one.edit(requireNotNull(one.state.value.draft).copy(title="Keep this idea"));one.closeEditor()
        one.detail(7);runCurrent();source.getFailure=404;one.refresh(true);runCurrent()
        assertNull(one.state.value.plan);assertNotNull(one.state.value.home);assertEquals("Keep this idea",one.state.value.draft?.title)
        one.retry();runCurrent();assertTrue(one.state.value.fresh);one.viewModelScope.cancel()
        val two=vm(source,sessions);two.open(7);runCurrent();assertEquals("Keep this idea",two.state.value.draft?.title)
        two.browse();runCurrent();two.resumeDraft();assertTrue(two.state.value.editing)
    }
    @Test fun unavailablePlanClearsItsOwnEditAndPermissionLossStillClearsPrivateDrafts()=runTest {
        val source=FakePlans();val model=vm(source);model.open(7);runCurrent();model.editPlan(true)
        source.getFailure=404;model.refresh(true);runCurrent();assertNull(model.state.value.draft);assertFalse(model.state.value.editing)
        model.browse();runCurrent();model.editPlan();model.edit(requireNotNull(model.state.value.draft).copy(title="Private idea"));model.closeEditor()
        source.getFailure=403;model.detail(7);runCurrent();assertNull(model.state.value.draft);assertNull(model.state.value.home)
    }
    @Test fun leavingDetailCancelsAReadThatCouldReopenIt()=runTest {
        val gate=CompletableDeferred<Unit>();var hold=false
        val source=object:FakePlans(){override suspend fun get(id:Long):com.srisu.srisu.core.data.remote.NetworkAPIResult<Plan?> {if(hold)gate.await();return super.get(id)}}
        val vm=vm(source);vm.open(7);runCurrent();hold=true;vm.refresh(true);runCurrent();vm.browse();gate.complete(Unit);runCurrent();assertNull(vm.state.value.plan)
    }
    @Test fun conflictRefreshesLatestPlanWithoutDiscardingEdit()=runTest {
        val source=FakePlans();val vm=vm(source);vm.open(7);runCurrent();vm.editPlan(true)
        vm.edit(requireNotNull(vm.state.value.draft).copy(title="Our revised title"));source.plan=source.plan.copy(revision=3);source.submitFailure=409
        vm.save();runCurrent();assertEquals("Our revised title",vm.state.value.draft?.title);assertEquals(3,vm.state.value.draft?.revision);assertNull(vm.state.value.pending);assertNotNull(vm.state.value.error)
    }
    @Test fun accountLossClearsVisiblePrivateStateAndAlarms()=runTest {
        val source=FakePlans();val sessions=MomentFixtures.sessions();val device=FakePlanDevice();val vm=vm(source,sessions,device)
        vm.open(7);runCurrent();vm.reminders.choose(PlanFixtures.agreed,60);assertTrue(device.alarms.isNotEmpty())
        sessions.clearSession();runCurrent();assertNull(vm.state.value.home);assertTrue(device.alarms.isEmpty())
    }
    @Test fun declinedNotificationPermissionDoesNotClaimScheduled()=runTest {
        val source=FakePlans();val device=FakePlanDevice().apply {permission="denied"};val reminders=reminders(source,MomentFixtures.sessions(),device);runCurrent()
        reminders.choose(PlanFixtures.agreed,60);assertEquals(1,device.permissionRequests);assertTrue(reminders.state.value.scheduled.isEmpty());assertTrue(reminders.state.value.choices.isEmpty());assertNotNull(reminders.state.value.error)
    }
    @Test fun rescheduleAndCancellationReplaceDeviceReminders()=runTest {
        val source=FakePlans();val device=FakePlanDevice();val reminders=reminders(source,MomentFixtures.sessions(),device);runCurrent()
        reminders.choose(PlanFixtures.agreed,60);val old=device.alarms.single().atMillis
        source.reminderPlans=listOf(PlanFixtures.agreed.copy(startsAt="2099-10-25T00:00:00Z"));reminders.refresh();assertEquals(86_400_000L,device.alarms.single().atMillis-old)
        source.reminderPlans=emptyList();reminders.refresh();assertTrue(device.alarms.isEmpty());assertTrue(reminders.state.value.choices.isEmpty())
    }
    @Test fun pendingChangeKeepsReminderAtLastAgreedTime() {
        val plan=PlanFixtures.agreed.copy(change=PlanFixtures.change,state="change_proposed")
        val result=reminderAlarms(listOf(plan),listOf(ReminderChoice(7,3,60)))
        assertEquals(Instant.parse(plan.startsAt).toEpochMilliseconds()-3_600_000,result.single().atMillis)
        assertTrue(reminderAlarms(listOf(plan),listOf(ReminderChoice(7,99,60))).isEmpty())
    }
    @Test fun referenceAndActionOwnershipAreBounded() {
        val p=PlanFixtures.plan
        assertEquals(PlanReference(7,3),PlanReference.parse("https://srisu.app/plans/3/7"))
        assertNull(PlanReference.parse("https://srisu.app.evil/plans/3/7"));assertNull(PlanReference.parse("https://srisu.app/plans/3/0"))
        assertFalse(p.allows("accept",2));assertTrue(p.allows("accept",1));assertFalse(p.copy(state="cancelled").allows("propose",1))
        assertFalse(p.copy(change=PlanFixtures.change.copy(author=1)).allows("accept",1))
    }
    @Test fun listPaginationPreservesEarlierRowsAndStableIds()=runTest {
        val source=FakePlans().apply {page=PlanPage(listOf(plan),"next")};val vm=vm(source);vm.open(null);runCurrent()
        source.page=PlanPage(listOf(source.plan,source.plan.copy(id=8)));vm.more();runCurrent();assertEquals(listOf(7L,8L),vm.state.value.page.items.map {it.id})
        vm.refresh(true);runCurrent();assertEquals(2,vm.state.value.page.items.size)
    }
    @Test fun calendarReportsSaveAndCancellationWithoutChangingThePlan()=runTest {
        val source=FakePlans().apply {plan=PlanFixtures.agreed}
        val device=FakePlanDevice();val model=vm(source=source,device=device)
        model.open(source.plan.id);runCurrent()
        device.calendarMessage="Calendar copy wasn’t saved."
        model.calendar();assertEquals(device.calendarMessage,model.state.value.notice)
        device.calendarMessage="Calendar copy saved. Later plan changes won’t update this copy."
        model.calendar();assertEquals(device.calendarMessage,model.state.value.notice)
        assertEquals(source.plan,model.state.value.plan);assertEquals(2,device.calendarCalls)
    }

}
