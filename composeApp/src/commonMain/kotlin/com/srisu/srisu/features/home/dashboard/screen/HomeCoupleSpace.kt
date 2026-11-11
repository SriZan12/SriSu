package com.srisu.srisu.features.home.dashboard.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.home.dashboard.components.*
import com.srisu.srisu.features.home.dashboard.state.*
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.sparks.data.SparkDaily
import com.srisu.srisu.theme.HomeSpace as H
import com.srisu.srisu.theme.sriSuTypography
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@Stable class HomeInteractions(
    val status: (String) -> Unit = {}, val suggest: (String) -> Unit = {},
    val idea: (DateIdea, Boolean, Boolean) -> Unit = { _, _, _ -> },
    val accept: (Plan) -> Unit = {}, val retry: () -> Unit = {}, val reminder: (Plan, Int?) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeCoupleSpace(state: HomeUiState, load: HomeImageLoader, retry: () -> Unit,
    actions: HomeActions, interactions: HomeInteractions, modifier: Modifier) {
    MaterialTheme(colorScheme = H.colors, typography = H.type(sriSuTypography())) {
        var statusPicker by rememberSaveable { mutableStateOf(false) }
        var ideaPicker by rememberSaveable { mutableStateOf(false) }
        var requests by rememberSaveable { mutableStateOf(false) }
        var reminderId by rememberSaveable { mutableStateOf<Long?>(null) }
        val c = state.couple
        val canAct = !state.stale
        val canStatus = canAct && "status" !in state.blockedActions
        val canIdea = canAct && "idea" !in state.blockedActions
        val canPlan = canAct && "plan" !in state.blockedActions
        Surface(modifier.fillMaxSize(), color = H.paper) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val compact = maxWidth < 360.dp
                LazyColumn(Modifier.widthIn(max = 560.dp).fillMaxSize().statusBarsPadding(),
                    state = rememberLazyListState(), contentPadding = PaddingValues(horizontal = if (compact) 17.dp else 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    item("heading") { SpaceHeading(state, actions) { requests = true } }
                    if (state.refreshing) item("progress") {
                        LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Refreshing Home" }, color = H.purple, trackColor = H.lilac)
                    }
                    if (state.stale) item("stale") { RetryRow("Showing your last update. Home couldn’t refresh.", retry) }
                    if (state.notice != null) item("notice") {
                        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(state.notice, style = MaterialTheme.typography.bodyMedium, color = H.muted)
                            if (state.pendingAction) SpaceButton("Retry action", interactions.retry, enabled = !state.busy)
                        }
                    }
                    when {
                        state.status == HomeStatus.LOADING -> items(3) { HomePlaceholderCard() }
                        state.status == HomeStatus.FAILED -> item("failed") { RetryRow("Home couldn’t load. Your connection hasn’t been checked yet.", retry) }
                        c == null -> item("unpaired") { Unpaired(state, actions, retry) }
                        else -> {
                            item("couple") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CouplePortraits(c, load, actions, canStatus, compact) { statusPicker = true }
                                if (c.statusState == HomeStatus.FAILED) RetryRow("Statuses couldn’t refresh.", retry)
                            } }
                            item("question") { when (val q = c.question) {
                                HomeSection.Loading -> HomePlaceholderCard()
                                HomeSection.Failed -> Column { RetryRow("Your question couldn’t refresh.", retry); SpaceButton("Open secure chat", actions.onChat) }
                                HomeSection.Empty -> RetryRow("Your question isn’t available yet.", retry)
                                is HomeSection.Ready -> QuestionHero(q.value, c, load, { actions.onQuestion(q.value.session.id) }, retry, compact)
                            } }
                            item("from") { FromPartner(c, load, actions) }
                            item("plans") {
                                SpaceSection("Coming up", "View all", { ideaPicker = true }) {
                                    ComingUp(c, canPlan, actions, interactions, retry, { reminderId = it }, canIdea) { ideaPicker = true }
                                    if (c.featuredPlan != null) TextButton(onClick = { ideaPicker = true }) { Text("Shared ideas", color = H.muted) }
                                    if (c.pendingInvitations > 0) TextButton(onClick = { requests = true }) {
                                        Text("${c.pendingInvitations} ${if (c.pendingInvitations == 1) "request" else "requests"} to review", color = H.purple)
                                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 6.dp).size(16.dp), tint = H.purple)
                                    }
                                }
                            }
                            item("challenge") { SpaceSection("Your challenge") { Challenge(c, load, actions, retry) } }
                            item("story") { Story(c, actions) }
                        }
                    }
                }
            }
        }
        if (requests) ModalBottomSheet(onDismissRequest = { requests = false }, containerColor = H.paper) {
            Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Requests for you", style = MaterialTheme.typography.titleLarge)
                if (state.status != HomeStatus.READY || state.stale) RetryRow("Requests haven’t refreshed.", retry)
                else if (c == null) {
                    when {
                        state.invitationsLoading -> Text("Checking invitations…")
                        state.invitationsFailed -> RetryRow("Invitations couldn’t refresh.", retry)
                        state.invitation != null -> SpaceButton("Review invitation ${if (state.invitation.incoming) "from" else "to"} ${state.invitation.name}", { requests = false; actions.onInvitations() }, modifier = Modifier.fillMaxWidth())
                        else -> Text("No invitations right now.", color = H.muted)
                    }
                } else {
                    if (c.pendingInvitations > 0) SpaceButton("${c.pendingInvitations} Plan ${if (c.pendingInvitations == 1) "request" else "requests"}", { requests = false; actions.onNotifications() }, modifier = Modifier.fillMaxWidth())
                    else if (!c.planFresh) RetryRow("Plan requests haven’t refreshed.", retry)
                    val challenge = (c.challenge as? HomeSection.Ready)?.value
                    if (challenge?.incoming == true) SpaceButton("A challenge from ${c.partner.name}", { requests = false; actions.onChallenge(challenge.id) }, modifier = Modifier.fillMaxWidth())
                    when (val sharing = c.sharingRequest) {
                        HomeSection.Loading -> Text("Checking sharing requests…", color = H.muted)
                        HomeSection.Failed -> RetryRow("Sharing requests couldn’t refresh.", retry)
                        is HomeSection.Ready -> if (sharing.value) SpaceButton("Review profile sharing", { requests = false; actions.onSharing() }, modifier = Modifier.fillMaxWidth())
                        else -> Unit
                    }
                    if (c.pendingInvitations == 0 && c.planFresh && c.sharingRequest == HomeSection.Ready(false) && c.challenge !is HomeSection.Loading && c.challenge !is HomeSection.Failed && challenge?.incoming != true)
                        Text("No requests right now.", color = H.muted)
                }
            }
        }
        val reminderPlan = c?.featuredPlan?.takeIf { it.id == reminderId }
        if (reminderPlan != null) ModalBottomSheet(onDismissRequest = { reminderId = null }, containerColor = H.paper) {
            Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Remind me", style = MaterialTheme.typography.titleLarge)
                Text(reminderPlan.title, style = MaterialTheme.typography.titleMedium)
                Text("Only on this phone. Delivery depends on notification and battery settings.", style = MaterialTheme.typography.bodyMedium, color = H.muted)
                val selected = state.reminders.choices.firstOrNull { it.plan == reminderPlan.id }?.minutes
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "Off", 15 to "15 min before", 60 to "1 hour before", 1440 to "1 day before").forEach { (minutes, title) ->
                        FilterChip(selected == minutes, { interactions.reminder(reminderPlan, minutes) }, enabled = c.planFresh, label = { Text(title) })
                    }
                }
                Text(state.reminders.error ?: when {
                    state.reminders.permission == "denied" -> "Notifications are disabled in this phone’s settings."
                    reminderPlan.id in state.reminders.scheduled -> "Scheduled on this phone"
                    selected != null && reminderPlan.instant.toEpochMilliseconds() - selected * 60_000L <= Clock.System.now().toEpochMilliseconds() -> "That reminder time has passed. Choose a later one."
                    selected != null -> "This phone hasn’t scheduled the reminder yet."
                    else -> "Your partner controls their own reminders."
                }, style = MaterialTheme.typography.bodyMedium, color = H.muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        if (statusPicker && c != null) ModalBottomSheet(onDismissRequest = { statusPicker = false }, containerColor = H.paper) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("How are you feeling?", style = MaterialTheme.typography.titleLarge)
                Text("Only your partner can see this. Clears after 24 hours.", style = MaterialTheme.typography.bodyMedium, color = H.muted)
                listOf("Feeling cosy", "Thinking of you", "Could use a hug", "A little busy", "").forEach { value ->
                    SpaceButton(value.ifBlank { "Clear my status" }, { interactions.status(value); statusPicker = false }, enabled = canStatus && c.statusesFresh,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (ideaPicker && c != null) ModalBottomSheet(onDismissRequest = { ideaPicker = false }, containerColor = H.paper) {
            Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Plans & ideas", style = MaterialTheme.typography.titleLarge)
                SpaceButton("Open your agenda", { ideaPicker = false; actions.onPlan(null) }, icon = Icons.Outlined.CalendarToday, modifier = Modifier.fillMaxWidth())
                if (c.pendingInvitations > 0) SpaceButton("${c.pendingInvitations} requests to review", { ideaPicker = false; requests = true }, modifier = Modifier.fillMaxWidth())
                Text("Shared ideas aren’t bookings. Agree a time when you’re both interested.", style = MaterialTheme.typography.bodyMedium, color = H.muted)
                if (c.ideas is HomeSection.Loading) Text("Checking shared ideas…", color = H.muted)
                if (c.ideas is HomeSection.Failed) RetryRow("Shared ideas couldn’t refresh.", retry)
                val ideas = (c.ideas as? HomeSection.Ready)?.value?.items.orEmpty()
                ideas.forEach { idea -> IdeaPanel(idea, c, canIdea, actions, interactions) }
                if (ideas.none { it.creator == c.account }) IdeaChoices(canIdea && c.ideasFresh) { interactions.suggest(it); ideaPicker = false }
                else Text("Withdraw your current idea before sharing another.", style = MaterialTheme.typography.bodySmall, color = H.muted)
                SpaceButton("Plan a date", { ideaPicker = false; actions.onPlanner() }, dark = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable private fun SpaceHeading(state: HomeUiState, actions: HomeActions, requests: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).clickable(role = Role.Button,
            onClickLabel = if (state.couple != null) "Open your couple profile" else "Explore SriSu",
            onClick = if (state.couple != null) actions.onCoupleProfile else actions.onExplore)) {
            Text(state.couple?.let { "You & ${it.partner.name.ifBlank { "your partner" }}" } ?: "Your space",
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.couple?.title?.takeIf(String::isNotBlank) ?: if (state.couple != null) "Your couple profile" else "Explore SriSu", color = H.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 5.dp).size(16.dp), tint = H.muted)
            }
        }
        IconButton(onClick = requests,
            modifier = Modifier.semantics { contentDescription = "Requests${if ((state.couple?.pendingInvitations ?: 0) > 0 || state.invitation?.incoming == true || state.couple?.sharingRequest == HomeSection.Ready(true) || (state.couple?.challenge as? HomeSection.Ready)?.value?.incoming == true) ", new items to review" else ""}" }) {
            Box {
                Icon(Icons.Outlined.Notifications, null, Modifier.size(21.dp))
                if ((state.couple?.pendingInvitations ?: 0) > 0 || state.invitation?.incoming == true || state.couple?.sharingRequest == HomeSection.Ready(true) || (state.couple?.challenge as? HomeSection.Ready)?.value?.incoming == true)
                    Box(Modifier.align(Alignment.TopEnd).size(7.dp).background(H.orange, CircleShape))
            }
        }
    }
}

@Composable private fun CouplePortraits(c: HomeCoupleUi, load: HomeImageLoader, actions: HomeActions, canAct: Boolean, compact: Boolean, status: () -> Unit) {
    val own = c.moments.firstOrNull { it.own }
    val partner = c.moments.firstOrNull { !it.own }
    val large = LocalDensity.current.fontScale > 1.3f
    val avatarSize = if (compact) 76.dp else if (LocalDensity.current.fontScale > 1.5f) 78.dp else 90.dp
    BoxWithConstraints {
        val centerWidth = if (compact) 67.dp else 76.dp
        val bridgeWidth = (maxWidth + centerWidth) / 2 - avatarSize - 10.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PortraitColumn(own, true, c, load, actions, canAct, status, avatarSize, Modifier.weight(1f))
                if (!large) {
                    Column(Modifier.width(centerWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(22.dp))
                        DayConnector(c, actions, bridgeWidth)
                    }
                }
                PortraitColumn(partner, false, c, load, actions, canAct, {}, avatarSize, Modifier.weight(1f))
            }
            if (large) DayConnector(c, actions)
        }
    }
}

@Composable private fun DayConnector(c: HomeCoupleUi, actions: HomeActions, bridgeWidth: androidx.compose.ui.unit.Dp? = null) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Canvas((if (bridgeWidth != null) Modifier.requiredWidth(bridgeWidth) else Modifier.fillMaxWidth()).height(56.dp)) {
            // A shallow arch between the portraits; the date rests at its crest.
            val arc = Path().apply {
                moveTo(0f, size.height * .8f)
                quadraticTo(size.width / 2, -size.height * .1f, size.width, size.height * .8f)
            }
            drawPath(arc, H.connector, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
        }
        // Keep the touch area generous without making the visible pill 48 dp tall.
        Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = actions.onCoupleProfile), contentAlignment = Alignment.Center) {
            Surface(shape = H.pill, color = H.paper, shadowElevation = 2.dp, border = BorderStroke(1.dp, H.line)) {
                Text(c.daysTogether?.let { "Day $it" } ?: "Add date", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun PortraitColumn(moment: HomeMomentUi?, own: Boolean, c: HomeCoupleUi, load: HomeImageLoader,
    actions: HomeActions, canAct: Boolean, onStatus: () -> Unit, avatarSize: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val person = moment?.person ?: if (own) HomePersonUi(c.account ?: 0, "You", null) else c.partner
    val text = c.statuses?.items?.firstOrNull { it.author == person.id }?.text?.takeIf(String::isNotBlank)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.heightIn(min = 48.dp).then(if (own) Modifier.clickable(enabled = canAct && c.statusesFresh, role = Role.Button, onClickLabel = "Set your private status", onClick = onStatus) else Modifier), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Surface(shape = H.pill, color = H.paper, shadowElevation = if (text != null) 4.dp else 0.dp,
                    border = BorderStroke(1.dp, H.line), modifier = Modifier.widthIn(max = 112.dp)) {
                    Text(text ?: if (c.statusState == HomeStatus.READY) { if (own) "Set status" else "No status shared" } else if (c.statusState == HomeStatus.LOADING) "Checking status…" else "Status unavailable",
                        style = MaterialTheme.typography.bodySmall, fontWeight = if (text != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (text != null) H.ink else H.muted, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
                }
                if (text != null) Canvas(Modifier.offset(y = 6.dp).size(12.dp, 8.dp)) {
                    val tail = Path().apply { moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f); close() }
                    drawPath(tail, H.paper)
                    drawLine(H.line, Offset(0f, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                    drawLine(H.line, Offset(size.width / 2, size.height), Offset(size.width, 0f), 1.dp.toPx())
                }
            }
        }
        Box(contentAlignment = Alignment.Center) {
            val size = avatarSize
            Box(Modifier.size(size + 10.dp).clip(CircleShape).then(if (moment?.hasUnseen == true && c.momentsFresh) Modifier.border(2.dp, H.purple, CircleShape) else Modifier)
                .clickable(enabled = c.momentsFresh, onClickLabel = "Open ${if (own) "your" else person.name + "’s"} Moments") {
                    if (own && (moment?.activeCount ?: 0) == 0) actions.onCreateMoment(c.id) else actions.onViewMoments(c.id, person.id)
                }, contentAlignment = Alignment.Center) {
                PersonAvatar(person, load, size)
            }
            if (own) IconButton(onClick = { actions.onCreateMoment(c.id) }, enabled = c.momentsFresh,
                modifier = Modifier.align(Alignment.BottomEnd).size(44.dp)) {
                Surface(shape = CircleShape, color = H.paper, shadowElevation = 2.dp, border = BorderStroke(1.dp, H.line), modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "Add a Moment", Modifier.size(18.dp)) }
                }
            }
        }
        Text(if (own) "Your Moments" else if (moment?.hasUnseen == true && c.momentsFresh) "New Moments" else if ((moment?.activeCount ?: 0) == 0) "No Moments yet" else "${person.name}’s Moments",
            style = MaterialTheme.typography.bodySmall, color = H.muted, textAlign = TextAlign.Center)
    }
}

@Composable private fun QuestionHero(daily: SparkDaily, c: HomeCoupleUi, load: HomeImageLoader, open: () -> Unit, retry: () -> Unit, compact: Boolean) {
    val q = daily.session
    val mine = q.submitted(c.account)
    val theirs = q.submitted(c.partner.id)
    val large = LocalDensity.current.fontScale > 1.3f
    Surface(shape = H.hero, color = H.lilac) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 18.dp else 20.dp)) {
            Text(if (daily.carried && q.open) "YOUR QUESTION" else if (daily.revisit) "A QUESTION TO REVISIT" else "TODAY", color = H.purple, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BalancedQuestion(q.question, Modifier.weight(1f))
                if (!large) TogetherArt(Modifier.padding(start = 10.dp).size(if (compact) 58.dp else 74.dp))
            }
            Spacer(Modifier.height(18.dp))
            if (theirs && !mine && q.open) Surface(shape = H.card, color = H.paper) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PersonAvatar(c.partner, load, 28.dp)
                    // Fixed geometry only: no answer, envelope or length is loaded into this strip.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.fillMaxWidth(.8f).height(6.dp).background(H.line, H.pill))
                        Box(Modifier.fillMaxWidth(.6f).height(6.dp).background(H.line, H.pill))
                    }
                    Icon(Icons.Outlined.Lock, "Their answer is hidden until you answer", Modifier.size(18.dp), tint = H.muted)
                }
            }
            if (theirs && !mine && q.open) Spacer(Modifier.height(15.dp))
            val note = when { q.revealed -> "You both answered. Keep the conversation going."; !q.open -> "Skipped together. A new question arrives tomorrow.";
                mine -> "Your answer is saved. Waiting for ${c.partner.name}."; else -> null }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = H.muted); Spacer(Modifier.height(15.dp)) }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoupleMini(c, load)
                SpaceButton(when { q.revealed -> "See your answers"; !q.open -> "View question"; mine -> "View my answer"; theirs -> "Answer to reveal"; else -> "Go first" }, open,
                    dark = true, icon = if (q.open && theirs && !mine) Icons.Outlined.Lock else Icons.AutoMirrored.Outlined.ArrowForward)
            }
            if (!c.questionFresh) {
                Spacer(Modifier.height(16.dp))
                RetryRow("Question hasn’t refreshed. Open it to check the latest state.", retry)
            }
        }
    }
}

/** Find the narrowest natural paragraph that keeps the minimum line count.
 * TextMeasurer uses the actual Satoshi glyph metrics on each platform; no demo-specific breaks. */
@Composable private fun BalancedQuestion(question: String, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.headlineMedium.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Simple)
    BoxWithConstraints(modifier) {
        val available = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val width = remember(question, available, style, density.fontScale, measurer) {
            fun lines(px: Int) = measurer.measure(question, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = px)).lineCount
            val count = lines(available)
            var low = available / 2; var high = available
            repeat(9) { if (low < high) { val mid = (low + high) / 2; if (lines(mid.coerceAtLeast(1)) <= count) high = mid else low = mid + 1 } }
            high
        }
        Text(question, style = style, modifier = Modifier.width(with(density) { width.toDp() }))
    }
}

@Composable private fun TogetherArt(modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            fun friend(x: Float, width: Float, color: androidx.compose.ui.graphics.Color) {
                val path = Path().apply { moveTo(x, size.height * .83f); cubicTo(x + width * .1f, size.height * .3f, x + width * .85f, size.height * .3f, x + width, size.height * .83f); close() }
                drawPath(path, color)
                val eyeY = size.height * .63f
                val center = x + width * .5f
                drawCircle(H.purple, 1.5.dp.toPx(), Offset(center - width * .1f, eyeY))
                drawCircle(H.purple, 1.5.dp.toPx(), Offset(center + width * .1f, eyeY))
                drawArc(H.purple, 15f, 150f, false, Offset(center - width * .09f, eyeY + 2.dp.toPx()), Size(width * .18f, size.height * .08f), style = Stroke(1.5.dp.toPx()))
            }
            friend(0f, size.width * .62f, H.lavender)
            friend(size.width * .4f, size.width * .6f, H.peach)
            val corner = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            val position = Offset(size.width * .57f, 0f)
            val bubble = Size(size.width * .4f, size.height * .27f)
            drawRoundRect(H.paper, position, bubble, corner)
            drawRoundRect(H.line, position, bubble, corner, style = Stroke(1.dp.toPx()))
            val tail = Path().apply { moveTo(size.width * .65f, bubble.height - 1.dp.toPx()); lineTo(size.width * .65f, bubble.height + 6.dp.toPx()); lineTo(size.width * .74f, bubble.height - 1.dp.toPx()); close() }
            drawPath(tail, H.paper)
            repeat(3) { drawCircle(H.purple, 1.6.dp.toPx(), Offset(size.width * (.67f + it * .09f), bubble.height / 2)) }
        }
    }
}

@Composable private fun FromPartner(c: HomeCoupleUi, load: HomeImageLoader, actions: HomeActions) {
    val daily = (c.question as? HomeSection.Ready)?.value
    val spark = daily?.session?.takeIf { it.state in listOf("open", "revealed") && daily.partnerActivityAt != null }
    val message = c.conversation
    val sparkTime = daily?.partnerActivityAt?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0
    val showSpark = spark != null && (message == null || sparkTime > message.timestampMillis || (sparkTime == message.timestampMillis && spark.id > message.messageId.orEmpty()))
    val ready = c.chatAvailability is MatrixChatAvailability.Ready
    val quiet = ready && c.unread == false && c.questionFresh && c.pendingInvitations == 0 && !showSpark
    SpaceSection("From ${c.partner.name.ifBlank { "your partner" }}", "Open chat", actions.onChat) {
        when {
            showSpark -> Surface(onClick = { actions.onQuestion(spark!!.id) }, shape = H.card, color = H.warm, contentColor = H.ink) {
                Row(Modifier.fillMaxWidth().heightIn(min = 73.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    PersonAvatar(c.partner, load, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${c.partner.name} ${if (daily?.partnerActivityKind == "sent") "sent a question" else "answered your question"}${if (!c.questionFresh || !ready) " · Last synced" else ""}", style = MaterialTheme.typography.bodySmall, color = H.muted)
                        Text(if (spark!!.revealed) "Both answers are open" else spark.question, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if (spark!!.revealed) Icons.Outlined.ChevronRight else Icons.Outlined.Lock, if (spark.revealed) "Answers revealed" else "Answer hidden", Modifier.size(18.dp), tint = H.muted)
                }
            }
            quiet -> {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(c.partner, load, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(if (message == null) "Your conversation starts here" else "You’re all caught up", style = MaterialTheme.typography.titleMedium)
                        Text(message?.let { "Last from ${c.partner.name}: ${it.text}" } ?: "Say a little hello.", style = MaterialTheme.typography.bodySmall, color = H.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("Send a little something", style = MaterialTheme.typography.labelMedium, color = H.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("thinking" to "👋 Thinking of you", "coffee" to "☕ Coffee later?", "miss" to "🌙 Miss you").forEach { (key, title) ->
                        SpaceButton(title, { actions.onDraft(key) })
                    }
                }
            }
            message != null -> Surface(onClick = { actions.onChatMessage(message.messageId) }, shape = H.card, color = H.warm, contentColor = H.ink) {
                Row(Modifier.fillMaxWidth().heightIn(min = 73.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    PersonAvatar(c.partner, load, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${c.partner.name} · ${message.sentAt}${if (!ready || !c.questionFresh) " · Last synced" else ""}", style = MaterialTheme.typography.bodySmall, color = H.muted)
                        Text(message.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (c.unread == true && ready) Surface(shape = H.pill, color = H.orange) {
                        Text("New", style = MaterialTheme.typography.labelMedium, color = H.paper, modifier = Modifier.padding(6.dp))
                    }
                }
            }
            else -> Surface(onClick = actions.onChat, shape = H.card, color = H.warm, contentColor = H.ink) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = H.muted)
                    Text(if (c.chatAvailability is MatrixChatAvailability.Loading) "Checking your conversation…" else "Chat couldn’t refresh. Open chat to reconnect.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        AdaptiveActions {
            SpaceButton("Ask", actions.onAsk, icon = Icons.Outlined.HelpOutline, modifier = Modifier.weight(1f))
            SpaceButton("Plan a date", actions.onPlanner, icon = Icons.Outlined.CalendarToday, modifier = Modifier.weight(1f))
        }
    }
}

@Composable private fun ComingUp(c: HomeCoupleUi, canAct: Boolean, actions: HomeActions, interactions: HomeInteractions, retry: () -> Unit, reminder: (Long) -> Unit, canIdea: Boolean, ideas: () -> Unit) {
    val plan = c.featuredPlan
    when {
        c.plan is HomeSection.Loading -> HomePlaceholderCard()
        c.plan is HomeSection.Failed -> RetryRow("Plans couldn’t refresh.", retry)
        plan != null -> PlanPanel(plan, c, canAct, actions, interactions, reminder)
        c.plan is HomeSection.Ready -> { // Older server/preview fallback; only claims data actually supplied.
            val value = c.plan.value
            Surface(onClick = { actions.onPlan(value.id) }, shape = H.card, color = H.warm, contentColor = H.ink) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.CalendarToday, null, tint = H.blue)
                    Text(value.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
        else -> when (val section = c.ideas) {
            HomeSection.Loading -> HomePlaceholderCard()
            HomeSection.Failed -> RetryRow("Shared ideas couldn’t refresh.", retry)
            else -> {
                val all = (section as? HomeSection.Ready)?.value?.items.orEmpty()
                val idea = all.sortedWith(compareByDescending<DateIdea> { it.interestedBy.size > 1 }.thenBy { it.creator == c.account }).firstOrNull()
                if (idea != null) {
                    IdeaPanel(idea, c, canIdea, actions, interactions)
                    TextButton(onClick = ideas) { Text(if (all.size > 1) "View both ideas" else "Other ideas", color = H.muted) }
                } else Surface(shape = H.card, color = H.warm, contentColor = H.ink) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarToday, null, tint = H.muted)
                            Column(Modifier.weight(1f)) {
                                Text("Nothing planned yet", style = MaterialTheme.typography.titleMedium)
                                Text("Share an idea. Agree a time together later.", style = MaterialTheme.typography.bodySmall, color = H.muted)
                            }
                        }
                        IdeaChoices(canIdea && c.ideasFresh, interactions.suggest)
                    }
                }
                if (!c.ideasFresh && section is HomeSection.Ready) RetryRow("Ideas haven’t refreshed. Retry before responding.", retry)
            }
        }
    }
    if (plan != null && !c.planFresh) RetryRow("Plan hasn’t refreshed. Open it to check the latest details.", retry)
}

@Composable private fun PlanPanel(plan: Plan, c: HomeCoupleUi, canAct: Boolean, actions: HomeActions, interactions: HomeInteractions, reminder: (Long) -> Unit) {
    val zone = TimeZone.currentSystemDefault()
    val today = runCatching { plan.instant.toLocalDateTime(zone).date == Clock.System.now().toLocalDateTime(zone).date }.getOrDefault(false)
    val elapsed = plan.instant <= Clock.System.now()
    val incoming = plan.allows("accept", c.account)
    val waiting = plan.state in listOf("invited", "change_proposed") && plan.proposedBy == c.account
    Surface(shape = H.card, color = if (today && plan.response == "yes") H.lilac else H.warm, contentColor = H.ink) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clip(H.card).clickable { actions.onPlan(plan.id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CalendarToday, null, Modifier.size(28.dp), tint = H.blue)
                Column(Modifier.weight(1f)) {
                    Text(when { incoming -> "${c.partner.name} asked: ${plan.change?.title ?: plan.title}"; waiting -> "Waiting on ${c.partner.name}"; today -> "${if (elapsed) "Earlier today" else "Today"} · ${plan.title}"; else -> plan.title }, style = MaterialTheme.typography.titleMedium)
                    Text("${shortPlanTime(plan.change?.startsAt ?: plan.startsAt)} · ${when { incoming -> "Your reply"; waiting -> plan.change?.title ?: plan.title; else -> "Both going" }}",
                        style = MaterialTheme.typography.bodySmall, color = H.muted)
                    if (plan.timeZone != zone.id) Text("Your time · ${zone.id}", style = MaterialTheme.typography.bodySmall, color = H.muted)
                    if (plan.response == "yes" && plan.change != null) Text("Agreed: ${plan.title} · ${shortPlanTime(plan.startsAt)}", style = MaterialTheme.typography.bodySmall, color = H.muted)
                }
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = H.muted)
            }
            when {
                incoming -> AdaptiveActions {
                    SpaceButton("I’m in", { interactions.accept(plan) }, dark = true, enabled = canAct && c.planFresh, modifier = Modifier.weight(1f))
                    SpaceButton("Another day", { actions.onEditPlan(plan.id) }, white = true, modifier = Modifier.weight(1f))
                }
                waiting -> AdaptiveActions {
                    SpaceButton("👋 Nudge in chat", { actions.onDraft("nudge") }, white = true, modifier = Modifier.weight(1f))
                    SpaceButton("Edit", { actions.onEditPlan(plan.id) }, modifier = Modifier.weight(1f))
                }
                today -> SpaceButton(if (elapsed) "Open plan" else "Remind me", { if (elapsed) actions.onPlan(plan.id) else reminder(plan.id) }, dark = true, icon = Icons.Outlined.Schedule)
            }
        }
    }
}

@Composable private fun IdeaPanel(idea: DateIdea, c: HomeCoupleUi, canAct: Boolean, actions: HomeActions, interactions: HomeInteractions) {
    val mine = idea.creator == c.account
    val interested = c.account in idea.interestedBy
    val mutual = idea.interestedBy.size > 1
    Surface(shape = H.card, color = H.warm, contentColor = H.ink) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (mutual) "You both like this idea" else "Nothing planned yet", style = MaterialTheme.typography.titleMedium)
            Text(if (mine) "Your idea: ${idea.title}" else "${c.partner.name} is up for ${idea.title.lowercase()}", style = MaterialTheme.typography.bodyMedium, color = H.muted)
            AdaptiveActions {
                if (mutual) SpaceButton("Pick a time", { actions.onIdeaPlan(idea.id) }, dark = true, enabled = canAct && c.ideasFresh, modifier = Modifier.weight(1f))
                else if (!mine) SpaceButton("Me too", { interactions.idea(idea, true, false) }, dark = true, enabled = canAct && c.ideasFresh, modifier = Modifier.weight(1f))
                else SpaceButton("Waiting for a reply", {}, enabled = false, modifier = Modifier.weight(1f))
                SpaceButton(if (mine) "Withdraw idea" else if (interested) "Undo interest" else "Open chat",
                    { if (mine) interactions.idea(idea, false, true) else if (interested) interactions.idea(idea, false, false) else actions.onChat() },
                    white = true, enabled = canAct && c.ideasFresh, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun IdeaChoices(enabled: Boolean, choose: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("coffee" to "☕ Coffee", "walk" to "🚶 Walk", "movie" to "🎬 Movie").forEach { (key, title) ->
            SpaceButton(title, { choose(key) }, white = true, enabled = enabled)
        }
    }
}

@Composable private fun Challenge(c: HomeCoupleUi, load: HomeImageLoader, actions: HomeActions, retry: () -> Unit) {
    when (val section = c.challenge) {
        HomeSection.Loading -> HomePlaceholderCard()
        HomeSection.Failed -> RetryRow("Your challenge couldn’t refresh.", retry)
        HomeSection.Empty -> Surface(onClick = { actions.onChallenge(null) }, shape = H.card, color = H.warm, contentColor = H.ink) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A little experience, together", style = MaterialTheme.typography.titleMedium)
                Text("Choose your first challenge →", style = MaterialTheme.typography.bodyMedium, color = H.muted)
            }
        }
        is HomeSection.Ready -> Surface(onClick = { actions.onChallenge(section.value.id.takeUnless { section.value.status == HomeChallengeStatus.COMPLETED }) }, shape = H.card, color = H.warm, contentColor = H.ink) {
            val item = section.value
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HomeIconPlate(if (item.status == HomeChallengeStatus.COMPLETED) Icons.Outlined.Check else Icons.Outlined.WbSunny,
                    if (item.status == HomeChallengeStatus.COMPLETED) H.green else H.orange, 38.dp, content = H.paper, glyph = 23.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(when (item.status) { HomeChallengeStatus.COMPLETED -> item.completedAt?.let { "You both did it · ${shortPlanTime(it)}" } ?: "You both did it"; HomeChallengeStatus.PAUSED -> "Paused. Resume when you’re ready."; HomeChallengeStatus.INVITED -> "An invitation to do this together"; HomeChallengeStatus.ENDED -> "This challenge ended"; else -> "At your own pace" }, style = MaterialTheme.typography.bodySmall, color = H.muted)
                    Text(when { item.status == HomeChallengeStatus.COMPLETED -> "Choose another →"; item.status == HomeChallengeStatus.ONE_CONFIRMED && item.members.any { it.person.id == c.partner.id && it.confirmed } -> "${c.partner.name} did it · Your turn →"; item.status == HomeChallengeStatus.ONE_CONFIRMED -> "You did it · Waiting for your partner"; else -> "Open challenge →" }, style = MaterialTheme.typography.labelMedium)
                }
                if (LocalDensity.current.fontScale <= 1.3f) CoupleMini(c, load)
            }
        }
    }
}

@Composable private fun Story(c: HomeCoupleUi, actions: HomeActions) {
    SpaceSection("Your story so far") {
        val large = LocalDensity.current.fontScale > 1.3f
        if (large) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(c.daysTogether?.toString() ?: "Add date", "Days", H.pink, Icons.Outlined.FavoriteBorder, actions.onCoupleProfile, Modifier.fillMaxWidth())
            Stat(c.questionsDone?.toString() ?: "—", "Questions", H.apricot, Icons.Outlined.Forum, actions.onSpark, Modifier.fillMaxWidth())
            Stat(c.plansDone?.toString() ?: "—", "Plans done", H.sky, Icons.Outlined.CalendarToday, { actions.onPlan(null) }, Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(c.daysTogether?.toString() ?: "—", "Days", H.pink, Icons.Outlined.FavoriteBorder, actions.onCoupleProfile, Modifier.weight(1f))
            Stat(c.questionsDone?.toString() ?: "—", "Questions", H.apricot, Icons.Outlined.Forum, actions.onSpark, Modifier.weight(1f))
            Stat(c.plansDone?.toString() ?: "—", "Plans done", H.sky, Icons.Outlined.CalendarToday, { actions.onPlan(null) }, Modifier.weight(1f))
        }
    }
}

@Composable private fun Stat(value: String, label: String, color: androidx.compose.ui.graphics.Color, icon: ImageVector, action: () -> Unit, modifier: Modifier) {
    Surface(onClick = action, shape = H.card, color = color, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = H.ink)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = H.muted)
        }
    }
}

@Composable private fun Unpaired(state: HomeUiState, actions: HomeActions, retry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(HomePersonUi(0, state.name.ifBlank { "You" }, null), { null }, 82.dp)
            Icon(Icons.Outlined.FavoriteBorder, null, Modifier.padding(20.dp), tint = H.purple)
            Box(Modifier.size(82.dp).border(1.dp, H.line, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PersonOutline, null, tint = H.muted) }
        }
        Surface(shape = H.hero, color = H.lilac) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(state.invitation?.let { if (it.incoming) "${it.name} invited you" else "An invitation for ${it.name}" } ?: "A space for the two of you", style = MaterialTheme.typography.headlineMedium)
                Text(state.invitation?.let { if (it.incoming) "Review the invitation before connecting." else "Waiting for them to accept. You can review or cancel your invitation." }
                    ?: "Connect with your partner to share Moments, questions and plans—just between you.", style = MaterialTheme.typography.bodyMedium, color = H.muted)
                SpaceButton(if (state.invitation != null) "Review invitation" else "Find your partner", if (state.invitation != null) actions.onInvitations else actions.onFindPartner,
                    dark = true, modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.invitationsLoading) Text("Checking invitations…", style = MaterialTheme.typography.bodyMedium, color = H.muted)
        if (state.invitationsFailed) RetryRow("Invitations couldn’t refresh. Review them or try again.", retry)
        Text("Your shared story starts when you connect.", style = MaterialTheme.typography.titleLarge)
        Text("No shared activity yet. Take a look around while you wait.", style = MaterialTheme.typography.bodyMedium, color = H.muted)
        SpaceButton("Explore SriSu", actions.onExplore, icon = Icons.AutoMirrored.Outlined.ArrowForward)
    }
}

@Composable private fun SpaceSection(title: String, action: String? = null, click: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (LocalDensity.current.fontScale > 1.3f) Column {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            action?.let { TextButton(onClick = click, contentPadding = PaddingValues(0.dp)) { Text("$it →", style = MaterialTheme.typography.bodySmall, color = H.muted) } }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            action?.let { TextButton(onClick = click, contentPadding = PaddingValues(start = 8.dp)) { Text("$it →", style = MaterialTheme.typography.bodySmall, color = H.muted) } }
        }
        content()
    }
}

@Composable private fun PersonAvatar(person: HomePersonUi, load: HomeImageLoader, size: androidx.compose.ui.unit.Dp) {
    if (person.photoUrl != null) HomeAvatar(person.photoUrl, load, size, person.name)
    else Box(Modifier.size(size).background(H.lilac, CircleShape), contentAlignment = Alignment.Center) {
        Text(person.name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = H.purple)
    }
}
@Composable private fun CoupleMini(c: HomeCoupleUi, load: HomeImageLoader) {
    Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy((-9).dp), verticalAlignment = Alignment.CenterVertically) {
        c.moments.take(2).forEach { Box(Modifier.border(2.dp, H.paper, CircleShape).padding(2.dp)) { PersonAvatar(it.person, load, 25.dp) } }
    }
}
@Composable private fun RetryRow(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = H.muted)
        TextButton(onClick = retry, contentPadding = PaddingValues(0.dp)) { Text("Try again", color = H.purple) }
    }
}
@Composable private fun AdaptiveActions(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2, content = content)
}
@Composable private fun SpaceButton(label: String, click: () -> Unit, modifier: Modifier = Modifier,
    dark: Boolean = false, white: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = click, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = H.pill,
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (dark) H.ink else if (white) H.paper else H.warm,
            contentColor = if (dark) H.paper else H.ink, disabledContainerColor = H.warm, disabledContentColor = H.muted)) {
        icon?.let { Icon(it, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
private fun shortPlanTime(value: String): String = runCatching {
    val d = Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault())
    "${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase)} ${d.day} · ${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
}.getOrDefault("Check date and time")
