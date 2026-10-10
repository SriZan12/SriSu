@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,kotlin.uuid.ExperimentalUuidApi::class,kotlin.time.ExperimentalTime::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.features.coupleprofile.data.*
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable internal fun ChatCardBubble(message: LocalChatMessage,state: ConversationState,repository: ConversationRepository,
    onPlan: (Long,Long)->Unit,onMakePlan: (String)->Unit,onMoment: (Long)->Unit) {
    val card=(message.content as? ChatContent.Card)?.card ?: return
    var answer by remember(message.id) {mutableStateOf(state.privateDrafts["answer:${message.id}"].orEmpty())}
    val pending=message.id in state.pendingCards
    LaunchedEffect(pending) {if(pending)answer=""}
    when(card) {
        is ChatCard.Ask -> {
            Text("Ask",style=MaterialTheme.typography.labelLarge)
            Text(card.question,style=MaterialTheme.typography.titleMedium)
            val reply=message.cardResponses.values.firstOrNull()
            if(reply!=null) {
                Text("${if(message.authorId==state.accountId)state.partnerName else "You"}: ${reply.vote?.let {card.options.getOrNull(it)} ?: reply.answer}")
                TextButton(onClick={onMakePlan(card.question)}) {Text("Make it a plan")}
            } else Text(if(pending)"Response queued" else if(message.authorId==state.accountId)"Waiting for your partner" else "What do you think?",style=MaterialTheme.typography.bodySmall)
            if(message.authorId!=state.accountId && !pending) {
                if(card.options.isEmpty()) {
                    OutlinedTextField(answer,{if(it.length<=2000) {answer=it;repository.privateDraft("answer:${message.id}",it)}},label={Text("Your answer")},maxLines=5)
                    TextButton(onClick={repository.answerCard(message.id,CardReply(answer.trim()))},enabled=answer.isNotBlank()) {Text(if(reply==null)"Answer" else "Update answer")}
                } else card.options.forEachIndexed {index,option ->
                    FilterChip(selected=reply?.vote==index,onClick={repository.answerCard(message.id,CardReply(vote=index))},label={Text(option)})
                }
            }
        }
        is ChatCard.Spark -> {
            val spark=state.sparks[message.id] ?: LocalSparkState()
            Text("Conversation Spark · ${card.deck}",style=MaterialTheme.typography.labelLarge)
            Text(card.question,style=MaterialTheme.typography.titleMedium)
            if(spark.revealed) {
                spark.answers.forEach {(actor,text) -> Text("${if(actor==state.accountId)"You" else state.partnerName}: $text")}
            } else {
                Text(when {pending->"Your answer is queued";spark.submitted->"Your answer is saved. Waiting for your partner.";spark.partnerSubmitted->"Your partner has answered. Add yours to open both.";else->"Both answers open after you each answer."},style=MaterialTheme.typography.bodySmall)
                spark.answers[state.accountId]?.let {Text("Your answer: $it")}
                if(!spark.submitted && !pending) {
                    OutlinedTextField(answer,{if(it.length<=2000) {answer=it;repository.privateDraft("answer:${message.id}",it)}},label={Text("Your answer")},maxLines=5)
                    TextButton(onClick={repository.answerSpark(message.id,answer.trim())},enabled=answer.isNotBlank()) {Text("Submit answer")}
                    Text("Submitted answers cannot be changed.",style=MaterialTheme.typography.labelSmall)
                }
            }
            spark.error?.let {Text(it,style=MaterialTheme.typography.bodySmall);TextButton(onClick=repository::retry) {Text("Try again")}}
        }
        is ChatCard.Plan -> PlanChatCard(card,state.accountId,onPlan)
        is ChatCard.Moment -> {
            Text("Moment",style=MaterialTheme.typography.labelLarge)
            Text("A shared moment. Availability follows the original 24-hour audience.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={onMoment(card.momentId)}) {Text("Open moment")}
        }
        is ChatCard.Challenge -> {Text("Challenge",style=MaterialTheme.typography.labelLarge);Text("This Challenge is unavailable in this version.",style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun PlanChatCard(card: ChatCard.Plan,accountId: Long?,onOpen: (Long,Long)->Unit) {
    val profiles=org.koin.compose.koinInject<CoupleProfileRepository>()
    val scope=rememberCoroutineScope()
    var revision by remember(card) {mutableIntStateOf(0)}
    var plan by remember(card) {mutableStateOf<CouplePlan?>(null)}
    var busy by remember(card) {mutableStateOf(true)}
    var error by remember(card) {mutableStateOf<String?>(null)}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,card) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_START)revision++
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)plan=null
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(card,revision) {
        busy=true;error=null
        when(val result=profiles.plan(card.coupleId,card.planId)) {
            is NetworkAPIResult.Success -> plan=result.response
            is NetworkAPIResult.Error -> {plan=null;error=if(result.failure.status in listOf(403,404))"This plan is no longer available." else "Connect to load the current plan."}
        };busy=false
    }
    Text("Planner",style=MaterialTheme.typography.labelLarge)
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    plan?.let {value ->
        Text(value.title,style=MaterialTheme.typography.titleMedium);Text(value.startsAt,style=MaterialTheme.typography.bodySmall)
        Text(when(value.response) {"yes"->"Accepted";"another_time"->"Another time";"no"->"Can’t this time";else->"Waiting for a response"})
        if(value.createdBy!=accountId)FlowRow {
            listOf("yes" to "Yes","another_time" to "Another time","no" to "Can’t").forEach {(response,label) ->
                TextButton(enabled=!busy,onClick={scope.launch {
                    busy=true;error=null
                    when(val result=profiles.respond(card.coupleId,card.planId,buildJsonObject {put("expected_revision",value.revision);put("response",response)})) {
                        is NetworkAPIResult.Success -> plan=result.response
                        is NetworkAPIResult.Error -> {error="The plan may have changed. Reload before trying again.";revision++}
                    };busy=false
                }}) {Text(label)}
            }
        }
        TextButton(onClick={onOpen(card.coupleId,card.planId)}) {Text("View plan")}
        Text("Plan details are stored by SriSu’s Plans feature.",style=MaterialTheme.typography.labelSmall)
    }
    error?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
    TextButton(onClick={revision++},enabled=!busy) {Text("Refresh plan")}
}

@Composable internal fun ChatCardSheet(kind: String,initialTitle: String,state: ConversationState,repository: ConversationRepository,onDismiss: ()->Unit) {
    val profiles=org.koin.compose.koinInject<CoupleProfileRepository>()
    val scope=rememberCoroutineScope()
    var question by remember {mutableStateOf(state.privateDrafts["$kind:question"] ?: initialTitle)}
    var options by remember {mutableStateOf(state.privateDrafts["$kind:options"].orEmpty())}
    var date by remember {mutableStateOf("")}
    var day by remember {mutableStateOf(state.privateDrafts["planner:day"])}
    var hour by remember {mutableIntStateOf(state.privateDrafts["planner:hour"]?.toIntOrNull() ?: 18)}
    var minute by remember {mutableIntStateOf(state.privateDrafts["planner:minute"]?.toIntOrNull() ?: 0)}
    var chooseDay by remember {mutableStateOf(false)}
    var chooseTime by remember {mutableStateOf(false)}
    var deck by remember {mutableStateOf(SparkDecks.questions.keys.first())}
    var selectedQuestion by remember {mutableStateOf(SparkDecks.questions.getValue(deck).first())}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var plans by remember {mutableStateOf<List<CouplePlan>>(emptyList())}
    var moments by remember {mutableStateOf<List<SharedMoment>>(emptyList())}
    val api=org.koin.compose.koinInject<CoupleChatApi>()
    var createdPlanId by remember {mutableStateOf(state.privateDrafts["planner:created"]?.toLongOrNull())}
    val immutablePlanRequest=state.privateDrafts["planner:request"]!=null
    LaunchedEffect(kind,state.relationshipId) {
        if(kind=="moment")state.relationshipId?.let {couple ->
            busy=true
            when(val result=api.moments(couple).result) {is NetworkAPIResult.Success -> moments=result.response?.results.orEmpty();is NetworkAPIResult.Error -> error="Moments could not load. Reconnect and reopen the picker."}
            busy=false
        }
        if(kind=="planner")state.relationshipId?.let {couple ->
            when(val result=profiles.plans(couple,false)) {is NetworkAPIResult.Success -> plans=result.response?.results.orEmpty();is NetworkAPIResult.Error -> error="Saved plans could not load. Reconnect and reopen Planner."}
        }
    }
    ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(MaterialTheme.spacing.gutter),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Text(when(kind) {"spark"->"Conversation Sparks";"planner"->"Planner";"moment"->"Share a Moment";else->"Ask"},style=MaterialTheme.typography.headlineSmall)
            when(kind) {
                "moment" -> {
                    Text("Your couple’s latest ten available Moments. Sharing keeps the original audience and 24-hour expiry.",style=MaterialTheme.typography.bodySmall)
                    if(!busy && moments.isEmpty() && error==null)Text("No available Moments to share.")
                    moments.forEach {moment -> TextButton(onClick={repository.sendCard(ChatCard.Moment(moment.id,moment.expiresAt),kind);onDismiss()}) {Text(moment.title?.takeIf(String::isNotBlank) ?: "Moment ${moment.id}")} }
                }
                "spark" -> {
                    Text("Choose a deck",style=MaterialTheme.typography.titleMedium)
                    SparkDecks.questions.keys.forEach {name -> FilterChip(selected=deck==name,onClick={deck=name;selectedQuestion=SparkDecks.questions.getValue(name).first()},label={Text(name)})}
                    SparkDecks.questions.getValue(deck).forEach {text -> FilterChip(selected=selectedQuestion==text,onClick={selectedQuestion=text},label={Text(text)})}
                    Text("Up to two open Sparks. Answers open only after both submit.",style=MaterialTheme.typography.bodySmall)
                    Button(onClick={repository.sendCard(ChatCard.Spark(deck,selectedQuestion),kind);onDismiss()}) {Text("Send Spark")}
                }
                "planner" -> {
                    Text("Choose an existing plan",style=MaterialTheme.typography.titleMedium)
                    plans.forEach {plan -> TextButton(enabled=!busy,onClick={state.relationshipId?.let {repository.sendCard(ChatCard.Plan(it,plan.id),kind);onDismiss()}}) {Text("${plan.title} · ${plan.startsAt}")} }
                    Text("Or make a plan",style=MaterialTheme.typography.titleMedium)
                    OutlinedTextField(question,{if(it.length<=120) {question=it;repository.privateDraft("$kind:question",it)}},label={Text("Plan title")},enabled=createdPlanId==null && !immutablePlanRequest && !busy)
                    TextButton(onClick={chooseDay=true},enabled=createdPlanId==null && !immutablePlanRequest && !busy) {Text(day ?: "Choose a date")}
                    TextButton(onClick={chooseTime=true},enabled=createdPlanId==null && !immutablePlanRequest && !busy) {Text("${hour.toString().padStart(2,'0')}:${minute.toString().padStart(2,'0')} · ${TimeZone.currentSystemDefault().id}")}
                    Text("Creating a plan saves its title and time in SriSu’s Plans feature. The chat shares an encrypted reference. No reminders are scheduled.",style=MaterialTheme.typography.bodySmall)
                    Button(enabled=!busy && question.isNotBlank() && date.isNotBlank() && state.relationshipId!=null,onClick={scope.launch {
                        busy=true;error=null
                        val couple=state.relationshipId ?: return@launch
                        val saved=createdPlanId
                        if(saved!=null) {repository.sendCard(ChatCard.Plan(couple,saved),kind);onDismiss()}
                        else try {
                            val body=repository.planIntent(question,date) ?: return@launch
                            when(val result=profiles.createPlan(couple,body)) {
                                is NetworkAPIResult.Success -> result.response?.let {createdPlanId=it.id;repository.privateDraft("planner:created",it.id.toString());repository.sendCard(ChatCard.Plan(couple,it.id),kind);onDismiss()}
                                is NetworkAPIResult.Error -> {
                                    if(result.failure.status in listOf(400,422))repository.privateDraft("planner:request","")
                                    error="Plan could not be saved. Retry uses the original title and time until the server confirms the result."
                                }
                            }
                        } catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                        catch(_:Exception) {error="The Plan request could not be saved safely. Check storage and try again."}
                        finally {busy=false}
                        busy=false
                    }}) {Text("Create plan and share")}
                }
                else -> {
                    OutlinedTextField(question,{if(it.length<=500) {question=it;repository.privateDraft("$kind:question",it)}},label={Text("Your question")},maxLines=5)
                    OutlinedTextField(options,{if(it.length<=484) {options=it;repository.privateDraft("$kind:options",it)}},label={Text("Optional poll choices, one per line")},maxLines=4)
                    val choices=options.lines().map(String::trim).filter(String::isNotEmpty)
                    val valid=question.isNotBlank() && (choices.isEmpty() || choices.size in 2..4) && choices.all {it.length<=120} && choices.distinct().size==choices.size
                    Text("Use two to four distinct choices for a poll, or leave choices empty for a written answer.",style=MaterialTheme.typography.bodySmall)
                    Button(enabled=valid,onClick={repository.sendCard(ChatCard.Ask(question.trim(),choices),kind);onDismiss()}) {Text("Send Ask")}
                }
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
    LaunchedEffect(day,hour,minute) {
        if(kind=="planner") {repository.privateDraft("planner:day",day.orEmpty());repository.privateDraft("planner:hour",hour.toString());repository.privateDraft("planner:minute",minute.toString())}
        date=day?.let {LocalDateTime.parse("${it}T${hour.toString().padStart(2,'0')}:${minute.toString().padStart(2,'0')}:00").toInstant(TimeZone.currentSystemDefault()).toString()}.orEmpty()
    }
    if(chooseDay) {
        val picker=rememberDatePickerState()
        DatePickerDialog(onDismissRequest={chooseDay=false},confirmButton={TextButton(enabled=picker.selectedDateMillis!=null,onClick={
            day=picker.selectedDateMillis?.let {Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date.toString()};chooseDay=false
        }) {Text("Choose date")}},dismissButton={TextButton(onClick={chooseDay=false}) {Text("Cancel")}}) {DatePicker(picker)}
    }
    if(chooseTime) {
        val picker=rememberTimePickerState(hour,minute,is24Hour=true)
        AlertDialog(onDismissRequest={chooseTime=false},title={Text("Choose a time")},text={TimePicker(picker)},
            confirmButton={TextButton(onClick={hour=picker.hour;minute=picker.minute;chooseTime=false}) {Text("Choose time")}},
            dismissButton={TextButton(onClick={chooseTime=false}) {Text("Cancel")}})
    }
}
