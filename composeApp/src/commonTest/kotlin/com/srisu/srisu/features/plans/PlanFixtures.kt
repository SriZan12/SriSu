package com.srisu.srisu.features.plans
import androidx.compose.runtime.Composable
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.plans.presentation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*

internal object PlanFixtures {
    val plan=Plan(7,3,2,"Sunrise walk by the river","2099-10-24T00:00:00Z","Asia/Kathmandu","Riverside viewpoint","pending","invited",1)
    val home=PlanHome(3,listOf(PlanMember(1,"Alex"),PlanMember(2,"Sam")))
    val agreed=plan.copy(response="yes",state="agreed",checklist=listOf(PlanItem("11111111-1111-4111-8111-111111111111","A flask of tea",1),PlanItem("22222222-2222-4222-8222-222222222222","A warm layer",2,true,2)))
    val change=PlanChange("A slower morning together","2099-10-24T01:00:00Z","The park café","Asia/Kathmandu",2)
    val base=PlanState(home=home,account=1,loading=false,fresh=true)
}
@Composable internal fun PlanRenderFixture(name:String) {
    val p=PlanFixtures.plan;val base=PlanFixtures.base
    val state=when {
        name.startsWith("invited")->base.copy(plan=p)
        name=="agreed"->base.copy(plan=PlanFixtures.agreed)
        name=="change"->base.copy(plan=PlanFixtures.agreed.copy(state="change_proposed",change=PlanFixtures.change))
        name=="completed"->base.copy(plan=PlanFixtures.agreed.copy(state="completed",completedBy=2))
        name=="cancelled"->base.copy(plan=p.copy(state="cancelled",cancelledBy=1))
        name=="failed"->base.copy(plan=p,fresh=false,error="Connection interrupted. Check your invitation before trying again.",pending=PlanIntent("synthetic",3,p.id,1,"accept"))
        name=="past"->base.copy(tab="past",page=PlanPage(listOf(p.copy(state="cancelled"),p.copy(id=8,state="completed",response="yes"))))
        name=="empty"->base
        else->base.copy(page=PlanPage(listOf(PlanFixtures.agreed,PlanFixtures.agreed.copy(id=8,title="A movie night at home",startsAt="2099-10-26T15:00:00Z"))))
    }
    PlanContent(state,ReminderState(),{},{},{},{},{},{},{},{},{},{_,_->},{},{},{},{})
}
internal open class FakePlans:PlanSource {
    override val changes=MutableStateFlow(0L)
    override fun changed() {changes.value++}
    var plan=PlanFixtures.plan
    var page=PlanPage(listOf(PlanFixtures.agreed))
    var home=PlanFixtures.home
    var getFailure:Int?=null
    var networkGet=false
    var submitFailure:Int?=null
    var networkSubmit=false
    var reminderPlans=listOf(PlanFixtures.agreed)
    val requests=mutableListOf<PlanIntent>()
    override suspend fun home()=NetworkAPIResult.Success<PlanHome?>(home)
    override suspend fun list(mode:String,cursor:String?)=NetworkAPIResult.Success<PlanPage?>(page)
    override suspend fun get(id:Long):NetworkAPIResult<Plan?> = if(networkGet || getFailure!=null) MomentFixtures.failed(getFailure) else NetworkAPIResult.Success(plan)
    override suspend fun submit(intent:PlanIntent):NetworkAPIResult<Plan?> {
        requests+=intent
        if(networkSubmit || submitFailure!=null) return MomentFixtures.failed(submitFailure)
        plan=when(intent.action) {
            "cancel"->plan.copy(state="cancelled",revision=plan.revision+1)
            "propose"->plan.copy(state="change_proposed",revision=plan.revision+1)
            null->plan.copy(title=intent.values.getValue("title").jsonPrimitive.content,creator=1)
            else->plan.copy(state="agreed",response="yes",revision=plan.revision+1)
        }
        return NetworkAPIResult.Success(plan)
    }
    override suspend fun reminders(ids:List<Long>)=NetworkAPIResult.Success<PlanPage?>(PlanPage(reminderPlans.filter {it.id in ids}))
    override suspend fun card(id:Long,event:String)=NetworkAPIResult.Success<Plan?>(plan.copy(cardEventId=event))
}
internal class FakePlanDevice:PlanDevice {
    var permission="granted"
    var alarms=emptyList<PlanAlarm>()
    var permissionRequests=0
    var calendarCalls=0
    override fun permission(result:(String)->Unit)=result(permission)
    override fun requestPermission(result:(String)->Unit) {permissionRequests++;result(permission)}
    override fun replaceReminders(alarms:List<PlanAlarm>,result:(String?)->Unit) {this.alarms=alarms;result(null)}
    var calendarMessage="Calendar copy saved."
    override fun calendar(title:String,atMillis:Long,place:String,result:(String)->Unit) {calendarCalls++;result(calendarMessage)}
    override fun openPlace(place:String)=Unit
}
