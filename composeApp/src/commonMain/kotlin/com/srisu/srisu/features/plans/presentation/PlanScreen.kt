package com.srisu.srisu.features.plans.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.*
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.chat.matrix.presentation.PartnerAvatar
import com.srisu.srisu.features.couplechat.presentation.ProtectConversation
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.features.moments.presentation.MomentError
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.theme.*
import kotlinx.datetime.*
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.compose.viewmodel.koinViewModel

@Composable fun PlanScreen(id:Long?,onBack:()->Unit,onChat:()->Unit,onMoment:(Long)->Unit,vm:PlanViewModel=koinViewModel(),initialCreate:Boolean=false) {
    val state by vm.state.collectAsStateWithLifecycle()
    val reminders by vm.reminders.state.collectAsStateWithLifecycle()
    var creationOpened by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(state.canAct) {if(initialCreate && state.canAct && !creationOpened) {creationOpened=true;vm.editPlan()}}
    LaunchedEffect(id) { vm.open(id) };MomentLifecycle(vm::visible);ProtectConversation()
    PlanContent(state,reminders,onBack,onChat,onMoment,vm::retry,vm::browse,vm::detail,vm::more,vm::editPlan,
        vm::resumeDraft,vm::action,vm::reminder,vm::calendar,vm::place,vm::publishCard)
    if(state.editing && state.draft!=null) PlanEditor(state,vm::edit,vm::save,vm::closeEditor,vm::discardDraft,vm::retry)
}

internal fun planTime(value:String):String = runCatching {
    val d=Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault())
    "${d.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase)}, ${d.day} ${d.month.name.lowercase().take(3).replaceFirstChar(Char::uppercase)} ${d.year} · ${d.hour.toString().padStart(2,'0')}:${d.minute.toString().padStart(2,'0')}"
}.getOrDefault("Time unavailable")
internal fun planStatus(plan:Plan):String=when(plan.state) {
    "agreed"->"Both going";"invited"->"Waiting for a reply";"change_proposed"->"New details suggested"
    "cancelled"->"Cancelled";"declined"->"Not this time";"completed"->"Done together";else->"Time has passed"
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun PlanContent(state:PlanState,reminders:ReminderState,onBack:()->Unit,onChat:()->Unit,
    onMoment:(Long)->Unit,onRetry:()->Unit,onBrowse:(String)->Unit,onOpen:(Long)->Unit,onMore:()->Unit,
    onEdit:(Boolean)->Unit,onResumeDraft:()->Unit,onAction:(String,JsonObject)->Unit,onReminder:(Int?)->Unit,
    onCalendar:()->Unit,onPlace:()->Unit,onCard:()->Unit) {
    val space=MaterialTheme.spacing;val colors=MaterialTheme.colorScheme;val plan=state.plan
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    fun finishTyping() {focus.clearFocus();keyboard?.hide()}
    val scroll=rememberLazyListState()
    val snackbar=remember {SnackbarHostState()}
    LaunchedEffect(state.notice,state.editing) {if(!state.editing) state.notice?.let {snackbar.showSnackbar(it,duration=SnackbarDuration.Long)}}
    LaunchedEffect(plan?.id,state.tab) { scroll.scrollToItem(0) }
    var cancel by remember(plan?.id) { mutableStateOf(false) }
    var remove by remember(plan?.id) { mutableStateOf<String?>(null) }
    var itemText by rememberSaveable(plan?.id) { mutableStateOf("") }
    var assigned by rememberSaveable(plan?.id) { mutableStateOf<Long?>(null) }
    var acknowledgedItem by remember(plan?.id) {mutableStateOf(state.addedItemRequest)}
    LaunchedEffect(state.addedItemRequest) {
        if(state.addedItemRequest!=acknowledgedItem) {itemText="";acknowledgedItem=state.addedItemRequest}
    }
    fun act(action:String)=onAction(action,buildJsonObject {})
    Scaffold(modifier=Modifier.imePadding(),containerColor=colors.background,snackbarHost={SnackbarHost(snackbar)},topBar={
        TopAppBar(title={Text("Plans",style=MaterialTheme.typography.titleLarge)},navigationIcon={IconButton(onClick=onBack) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}},
            actions={IconButton(onClick=onRetry,enabled=!state.busy) {Icon(Icons.Outlined.Refresh,"Refresh Plans")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=colors.background))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),state=scroll,
            contentPadding=PaddingValues(start=space.gutter,end=space.gutter,top=space.small,bottom=space.extraLarge),verticalArrangement=Arrangement.spacedBy(space.large)) {
            if(state.loading || state.busy) item("loading") {LinearProgressIndicator(Modifier.fillMaxWidth().semantics {contentDescription="Updating Plans"})}
            state.error?.let { item("error") {MomentError(it,onRetry)} }
            if(state.pending!=null) item("pending") {PlanSurface {
                Text("Checking your last action",style=MaterialTheme.typography.titleMedium)
                Text("It may already be saved. Retry checks the same request.",style=MaterialTheme.typography.bodyMedium)
                SriSuButton("Check and retry",onRetry,enabled=!state.busy)
            } }
            if(!state.loading && state.home?.couple==null && state.error==null) item("unpaired") {
                Text("Something to look forward to",style=MaterialTheme.typography.headlineMedium)
                Text("Connect with your partner from Home to make a plan together.")
            }
            else if(plan==null) {
                item("intro") {
                    Text("Time for the two of you",style=MaterialTheme.typography.headlineMedium)
                    Text("A small idea. A time that works for you both.",style=MaterialTheme.typography.bodyLarge,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=space.small))
                }
                item("create") { SriSuButton("Plan something",{onEdit(false)},Modifier.fillMaxWidth(),enabled=state.canAct && state.home?.couple!=null) }
                if(state.draft!=null) item("draft") {PlanSurface {
                    Text("Your saved draft",style=MaterialTheme.typography.titleMedium)
                    Text(state.draft.title.ifBlank {"An idea for you two"},style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick=onResumeDraft,enabled=state.canAct) {Text("Continue draft")}
                } }
                item("tabs") {FlowRow(horizontalArrangement=Arrangement.spacedBy(space.small)) {
                    listOf("upcoming" to "Upcoming","invitations" to if((state.home?.invitations ?: 0)>0) "Invitations (${state.home?.invitations})" else "Invitations","past" to "Past").forEach { (key,label) ->
                        FilterChip(state.tab==key,{onBrowse(key)},enabled=!state.busy && state.pending==null,label={Text(label)})
                    }
                } }
                if(state.page.items.isEmpty() && !state.loading && state.error==null) item("empty") {PlanSurface {
                    Icon(Icons.Outlined.CalendarMonth,null,tint=colors.planner)
                    Text(when(state.tab) {"invitations"->"No invitations waiting";"past"->"Your shared days will live here";else->"Your next plan starts with an idea"},style=MaterialTheme.typography.titleMedium)
                    Text(when(state.tab) {"invitations"->"Suggestions and changes appear here until you respond.";"past"->"Completed, cancelled, and past plans stay clearly labelled.";else->"A walk, a meal, a call. Choose something you would enjoy together."},style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                } }
                items(state.page.items,key={it.id}) {p->PlanSummary(p,{onOpen(p.id)})}
                if(state.page.next!=null) item("more") {TextButton(onClick=onMore,enabled=!state.busy) {Text("More plans")}}
            } else {
                item("agenda") {TextButton(onClick={onBrowse(state.tab)},enabled=!state.busy && state.pending==null) {Text("All plans")}}
                item("heading") {Column(verticalArrangement=Arrangement.spacedBy(space.medium)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.medium)) {
                        PlanDateTile(plan.startsAt)
                        Column(Modifier.weight(1f)) {Text(plan.title,style=MaterialTheme.typography.headlineSmall);Text("Suggested by ${state.home?.members?.firstOrNull {it.id==plan.creator}?.name ?: "your partner"}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                        state.home?.members.orEmpty().forEach { member->PartnerAvatar(ChatPartnerIdentity(member.name,member.photo),size=space.extraLarge,initialsStyle=MaterialTheme.typography.labelMedium) }
                        Text(planStatus(plan),style=MaterialTheme.typography.labelMedium,color=if(plan.response=="yes" && !plan.ended) colors.success else colors.onSurfaceVariant)
                    }
                } }
                item("details") {PlanSurface {
                    PlanDetailRow(Icons.Outlined.CalendarMonth,planTime(plan.startsAt),"Shown in ${TimeZone.currentSystemDefault().id}")
                    if(plan.timeZone.isNotBlank() && plan.timeZone!=TimeZone.currentSystemDefault().id) Text("Plan time zone: ${plan.timeZone}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    if(plan.place.isNotBlank()) {
                        PlanDetailRow(Icons.Outlined.Place,plan.place,null)
                        TextButton(onClick=onPlace) {Text(if(plan.place.startsWith("https://")) "Open link" else "Open in Maps")}
                    }
                } }
                if(plan.responseNote.isNotBlank()) item("earlier-reply") {PlanSurface {
                    Text("Earlier reply",style=MaterialTheme.typography.titleMedium)
                    Text(plan.responseNote,style=MaterialTheme.typography.bodyLarge)
                } }
                plan.change?.let {change->item("change") {PlanSurface {
                    Text("${state.home?.members?.firstOrNull {it.id==change.author}?.name ?: "Your partner"} suggested a change",style=MaterialTheme.typography.titleMedium)
                    Text(change.title,style=MaterialTheme.typography.bodyLarge)
                    Text(planTime(change.startsAt),style=MaterialTheme.typography.bodyMedium)
                    if(change.place.isNotBlank()) Text(change.place,style=MaterialTheme.typography.bodyMedium)
                    Text(if(plan.response=="yes") "Your agreed details above stay in place until you both agree." else "Waiting for the other partner to respond.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                } } }
                item("actions") {Column(verticalArrangement=Arrangement.spacedBy(space.small)) {
                    if(plan.allows("accept",state.account)) SriSuButton(if(plan.change==null) "Yes, let’s go" else "Accept these details",{act("accept")},Modifier.fillMaxWidth(),enabled=state.canAct)
                    if(plan.allows("propose",state.account)) SriSuButton(if(plan.change!=null) "Edit my suggestion" else "Another time or place",{onEdit(true)},Modifier.fillMaxWidth(),enabled=state.canAct,variant=SriSuButtonVariant.Outline)
                    if(plan.allows("decline",state.account)) TextButton(onClick={act("decline")},enabled=state.canAct) {Text(if(plan.change!=null && plan.response=="yes") "Keep our agreed details" else "Can’t this time")}
                    if(plan.allows("withdraw_change",state.account)) TextButton(onClick={act("withdraw_change")},enabled=state.canAct) {Text("Withdraw my change")}
                    if(plan.allows("complete",state.account)) SriSuButton("We did this",{act("complete")},Modifier.fillMaxWidth(),enabled=state.canAct)
                    if(plan.state=="completed") {
                        Text("Marked done by ${state.home?.members?.firstOrNull {it.id==plan.completedBy}?.name ?: "one of you"}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        SriSuButton("Share a Moment",{onMoment(plan.couple)},Modifier.fillMaxWidth())
                        TextButton(onClick={act("undo_complete")},enabled=state.canAct) {Text("Undo completion")}
                    }
                } }
                if(plan.response=="yes" && !plan.ended) {
                    if(plan.instant>Clock.System.now()) item("reminder") {PlanSurface {
                        Text("Remind me on this phone",style=MaterialTheme.typography.titleMedium)
                        Text("Reminders update when SriSu syncs. Delivery depends on this phone’s notification and battery settings.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        val selected=reminders.choices.firstOrNull {it.plan==plan.id}?.minutes
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(space.small)) {
                            listOf(null to "Off",15 to "15 min",60 to "1 hour",1440 to "1 day").forEach {(minutes,label)->FilterChip(selected==minutes,{onReminder(minutes)},enabled=state.fresh,label={Text(label)})}
                        }
                        Text(when {reminders.permission=="denied"->"Notifications are disabled in this phone’s settings.";plan.id in reminders.scheduled->"Scheduled on this phone";selected!=null && plan.instant.toEpochMilliseconds()-selected*60_000L<=Clock.System.now().toEpochMilliseconds()->"The reminder time has passed.";selected!=null->"This phone hasn’t scheduled the reminder yet.";else->"Only you control your reminders."},style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        reminders.error?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=colors.error)}
                    } }
                    item("checklist") {Column(verticalArrangement=Arrangement.spacedBy(space.small)) {
                        Text("Bring & prepare",style=MaterialTheme.typography.titleLarge)
                        Text("${plan.checklist.count {it.checked}} of ${plan.checklist.size} ready",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        plan.checklist.forEach {item->Surface(shape=MaterialTheme.shapes.medium,color=colors.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().padding(space.small),verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(item.checked,{checked->onAction("set_item",buildJsonObject {put("item_id",item.id);put("checked",checked)})},enabled=state.canAct,modifier=Modifier.semantics {contentDescription="${item.text}, ${if(item.checked) "ready" else "not ready"}"})
                                Column(Modifier.weight(1f)) {Text(item.text,style=MaterialTheme.typography.bodyLarge);Text(state.home?.members?.firstOrNull {it.id==item.assignedTo}?.name ?: "Either of you",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                                IconButton(onClick={remove=item.id},enabled=state.canAct) {Icon(Icons.Outlined.Close,"Remove ${item.text}")}
                            }
                        } }
                        OutlinedTextField(itemText,{if(it.length<=120)itemText=it},label={Text("Add something to bring")},modifier=Modifier.fillMaxWidth(),enabled=state.canAct,
                            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={finishTyping()}),maxLines=3)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(space.small)) {
                            FilterChip(assigned==null,{assigned=null},label={Text("Either of us")})
                            state.home?.members.orEmpty().forEach {m->FilterChip(assigned==m.id,{assigned=m.id},label={Text(m.name)})}
                        }
                        TextButton(onClick={finishTyping();onAction("add_item",buildJsonObject {put("text",itemText.trim());put("assigned_to",assigned)})},enabled=state.canAct && itemText.isNotBlank()) {Icon(Icons.Outlined.Add,null);Text("Add item")}
                    } }
                }
                if(plan.state=="completed" && plan.checklist.isNotEmpty()) item("finished-list") {PlanSurface {
                    Text("What you prepared",style=MaterialTheme.typography.titleMedium)
                    plan.checklist.forEach {Text("${if(it.checked) "Ready" else "Not checked"} · ${it.text}",style=MaterialTheme.typography.bodyMedium)}
                } }
                item("links") {Column(verticalArrangement=Arrangement.spacedBy(space.small)) {
                    SriSuButton("Chat about this",onChat,Modifier.fillMaxWidth(),variant=SriSuButtonVariant.Outline)
                    if(plan.response=="yes" && !plan.ended) TextButton(onClick=onCalendar,enabled=state.fresh) {Icon(Icons.Outlined.CalendarMonth,null);Spacer(Modifier.width(space.small));Text("Add a calendar copy")}
                    if(plan.creator==state.account && !plan.ended && plan.response!="yes" && plan.cardEventId.isBlank()) {
                        state.cardError?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=colors.error)}
                        TextButton(onClick=onCard,enabled=!state.cardBusy && state.fresh) {Text(if(state.cardBusy) "Sending invitation…" else "Send invitation to chat")}
                    }
                    if(plan.allows("cancel",state.account)) TextButton(onClick={cancel=true},enabled=state.canAct) {Text("Cancel this plan",color=colors.error)}
                    state.notice?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                    Text("Private to your couple. Plan details are stored by SriSu; personal conversation stays in encrypted chat.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                } }
            }
        }
    }
    if(cancel) AlertDialog(onDismissRequest={cancel=false},title={Text("Cancel this plan?")},text={Text("Both of you will see it as cancelled. It stays in Past.")},confirmButton={TextButton(onClick={cancel=false;act("cancel")}) {Text("Cancel plan")}},dismissButton={TextButton(onClick={cancel=false}) {Text("Keep plan")}})
    remove?.let {id->AlertDialog(onDismissRequest={remove=null},title={Text("Remove this checklist item?")},confirmButton={TextButton(onClick={remove=null;onAction("remove_item",buildJsonObject {put("item_id",id)})}) {Text("Remove")}},dismissButton={TextButton(onClick={remove=null}) {Text("Keep")}})}
}

@Composable internal fun PlanSurface(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small),content=content)
    }
}
@Composable internal fun PlanSummary(plan:Plan,onOpen:()->Unit) {
    Surface(onClick=onOpen,shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            PlanDateTile(plan.startsAt)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                Text(plan.title,style=MaterialTheme.typography.titleMedium)
                Text(planTime(plan.startsAt),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(planStatus(plan),style=MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward,null)
        }
    }
}
@Composable internal fun PlanDateTile(value:String) {
    val date=runCatching {Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault()).date}.getOrNull()
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal=MaterialTheme.spacing.medium,vertical=MaterialTheme.spacing.small),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(date?.month?.name?.take(3) ?: "—",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.planner)
            Text(date?.day?.toString() ?: "—",style=MaterialTheme.typography.headlineMedium)
            Text(date?.dayOfWeek?.name?.take(3)?.lowercase()?.replaceFirstChar(Char::uppercase).orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun PlanDetailRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String?) {
    Row(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.compact),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.bodyLarge);subtitle?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    }
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun PlanEditor(state:PlanState,onEdit:(PlanDraft)->Unit,onSave:()->Unit,onClose:()->Unit,onDiscard:()->Unit,onRetry:()->Unit) {
    val draft=state.draft ?: return;val space=MaterialTheme.spacing
    val placeFocus=remember {FocusRequester()}
    var datePicker by remember {mutableStateOf(false)};var timePicker by remember {mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)};var dateError by remember {mutableStateOf<String?>(null)}
    val zone=runCatching {TimeZone.of(draft.timeZone)}.getOrDefault(TimeZone.currentSystemDefault())
    val current=runCatching {Instant.parse(draft.startsAt).toLocalDateTime(zone)}.getOrNull() ?: Clock.System.now().toLocalDateTime(zone)
    fun setDate(date:LocalDate,time:LocalTime) {
        val local=LocalDateTime(date,time);val instant=local.toInstant(zone)
        if(instant.toLocalDateTime(zone)!=local) {dateError="That local time does not exist because the clocks change. Choose another time.";return}
        dateError=null;onEdit(draft.copy(startsAt=instant.toString()))
    }
    Dialog(onDismissRequest={if(!state.busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
        fun finishTyping() {focus.clearFocus();keyboard?.hide()}
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal=space.gutter)) {
            Row(verticalAlignment=Alignment.CenterVertically) {Text(if(draft.planId==null) "Plan something" else "Suggest new details",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));IconButton(onClick=onClose,enabled=!state.busy) {Icon(Icons.Outlined.Close,"Save draft and close")}}
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=space.medium,bottom=space.large),verticalArrangement=Arrangement.spacedBy(space.medium)) {
            if(draft.planId==null) FlowRow(horizontalArrangement=Arrangement.spacedBy(space.small)) {
                listOf("Dinner together","A little walk","Movie night","A video date").forEach {idea->SuggestionChip(onClick={onEdit(draft.copy(title=idea))},label={Text(idea)},enabled=state.canAct)}
            }
            OutlinedTextField(draft.title,{if(it.length<=120)onEdit(draft.copy(title=it))},label={Text("What would you like to do?")},modifier=Modifier.fillMaxWidth(),enabled=!state.busy && state.pending==null,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={placeFocus.requestFocus()}),maxLines=3)
            OutlinedButton(onClick={finishTyping();datePicker=true},modifier=Modifier.fillMaxWidth(),enabled=state.canAct) {Icon(Icons.Outlined.CalendarMonth,null);Spacer(Modifier.width(space.small));Text(if(draft.startsAt.isBlank()) "Choose a date" else current.date.toString())}
            OutlinedButton(onClick={finishTyping();timePicker=true},modifier=Modifier.fillMaxWidth(),enabled=state.canAct) {Icon(Icons.Outlined.Schedule,null);Spacer(Modifier.width(space.small));Text(if(draft.startsAt.isBlank()) "Choose a time" else "${current.hour.toString().padStart(2,'0')}:${current.minute.toString().padStart(2,'0')}")}
            Text("Time zone: ${zone.id}. Your partner sees their local time.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(draft.place,{if(it.length<=240)onEdit(draft.copy(place=it))},label={Text("Place or https link (optional)")},modifier=Modifier.fillMaxWidth().focusRequester(placeFocus),enabled=!state.busy && state.pending==null,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={finishTyping()}),maxLines=3)
            state.notice?.let {Text(it,style=MaterialTheme.typography.bodyMedium)}
            (dateError ?: state.error)?.let {MomentError(it,onRetry)}
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.pending!=null) SriSuButton("Check and retry",onRetry,Modifier.fillMaxWidth(),enabled=!state.busy)
            else SriSuButton(if(draft.planId==null) "Invite ${state.partner?.name ?: "your partner"}" else "Suggest these details",onSave,Modifier.fillMaxWidth(),enabled=state.canAct)
            Text("Your draft stays on this phone until you send it. Both of you agree before a plan is confirmed.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={discard=true},enabled=!state.busy && state.pending==null) {Text("Discard draft")}
            }
        }
        }
    }
    if(datePicker) {
        val now=Clock.System.now().toLocalDateTime(zone).date
        val picker=rememberDatePickerState(initialSelectedDateMillis=current.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),selectableDates=object:SelectableDates {
            override fun isSelectableDate(utcTimeMillis:Long)=utcTimeMillis>=now.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        })
        DatePickerDialog(onDismissRequest={datePicker=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let {setDate(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date,LocalTime(current.hour,current.minute))};datePicker=false}) {Text("Choose date")}},dismissButton={TextButton(onClick={datePicker=false}) {Text("Cancel")}}) {DatePicker(picker)}
    }
    if(timePicker) {
        val picker=rememberTimePickerState(initialHour=current.hour,initialMinute=current.minute)
        AlertDialog(onDismissRequest={timePicker=false},title={Text("Choose a time")},text={TimeInput(picker)},confirmButton={TextButton(onClick={setDate(current.date,LocalTime(picker.hour,picker.minute));timePicker=false}) {Text("Choose time")}},dismissButton={TextButton(onClick={timePicker=false}) {Text("Cancel")}})
    }
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("Discard this draft?")},text={Text("This only removes the draft on this phone. Saved plans stay unchanged.")},confirmButton={TextButton(onClick={discard=false;onDiscard()}) {Text("Discard")}},dismissButton={TextButton(onClick={discard=false}) {Text("Keep draft")}})
}
