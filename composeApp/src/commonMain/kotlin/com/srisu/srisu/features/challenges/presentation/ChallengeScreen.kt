package com.srisu.srisu.features.challenges.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.*
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.chat.matrix.presentation.PartnerAvatar
import com.srisu.srisu.features.challenges.data.*
import com.srisu.srisu.features.couplechat.presentation.ProtectConversation
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.features.moments.presentation.MomentError
import com.srisu.srisu.theme.*
import org.koin.compose.viewmodel.koinViewModel

@Composable fun ChallengeScreen(id: String?, onBack: () -> Unit, onChat: () -> Unit,
    onMoment: (Long) -> Unit, vm: ChallengeViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.open(id) }
    MomentLifecycle(vm::visible)
    ProtectConversation()
    ChallengeContent(state, onBack, onChat, onMoment, vm::retry, vm::select, vm::collection,
        vm::filter, vm::browse, vm::openSession, vm::invite, vm::action, vm::moreHistory, vm::publishCard)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun ChallengeContent(state: ChallengeState, onBack: () -> Unit, onChat: () -> Unit,
    onMoment: (Long) -> Unit, onRetry: () -> Unit, onSelect: (ChallengeActivity) -> Unit,
    onCollection: (String) -> Unit, onFilter: (Boolean, Boolean) -> Unit, onBrowse: (Boolean) -> Unit,
    onOpen: (String) -> Unit, onInvite: () -> Unit, onAction: (String) -> Unit,
    onMore: () -> Unit, onCard: () -> Unit) {
    val space = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val session = state.session
    val activity = session?.activity ?: state.selected
    val listState = rememberLazyListState()
    // Opening a detail/history screen starts at its heading; progress polls keep the reading position.
    LaunchedEffect(activity?.id, session?.id, state.historyTab) { listState.scrollToItem(0) }
    var confirmEnd by remember(session?.id) { mutableStateOf(false) }
    Scaffold(containerColor = colors.background, topBar = {
        TopAppBar(title = { Text("Challenges", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = { IconButton(onClick = onRetry, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Refresh Challenges") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), state = listState,
            contentPadding = PaddingValues(start = space.gutter, end = space.gutter, top = space.small, bottom = space.large),
            verticalArrangement = Arrangement.spacedBy(space.large)) {
            if (state.loading || state.busy) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Updating Challenge" }) }
            if (state.error != null) item("error") { MomentError(state.error, onRetry) }
            if (state.pending != null) item("pending") {
                Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(space.medium), verticalArrangement = Arrangement.spacedBy(space.small)) {
                        Text("Checking your last action", style = MaterialTheme.typography.titleMedium)
                        Text("It may already be saved. Retry checks the same request, without doing it twice.", style = MaterialTheme.typography.bodyMedium)
                        SriSuButton("Check and retry", onRetry, enabled = !state.busy)
                    }
                }
            }
            if (!state.loading && state.home?.couple == null && state.error == null) item("unpaired") {
                Text("Something for the two of you", style = MaterialTheme.typography.headlineMedium)
                Text("Connect with your partner from Home to choose an activity together.", style = MaterialTheme.typography.bodyLarge)
            }
            else if (activity != null) {
                item("browse") { TextButton(onClick = { onBrowse(false) }, enabled = !state.busy) { Text("All activities") } }
                item("activity") { ActivityHeading(activity, state.home?.collections?.firstOrNull { it.id == activity.collection }?.name, compact = session != null) }
                if (session != null) item("progress") { ChallengeProgress(state) }
                item("actions") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.small)) {
                        if (session == null) {
                            if (state.home?.current != null) {
                                Text("You already have a Challenge together.", style = MaterialTheme.typography.bodyMedium)
                                SriSuButton("Open your Challenge", { onOpen(state.home.current.id) }, Modifier.fillMaxWidth(), enabled = !state.busy)
                            } else {
                                SriSuButton("Invite ${state.partner?.name ?: "your partner"}", onInvite, Modifier.fillMaxWidth(), enabled = state.canAct)
                                Text("An invitation, not a commitment. Your partner chooses whether to join.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        } else {
                            if (session.allows("accept", state.account)) {
                                SriSuButton("Let’s do it", { onAction("accept") }, Modifier.fillMaxWidth(), enabled = state.canAct)
                                SriSuButton("Not this time", { onAction("decline") }, Modifier.fillMaxWidth(), enabled = state.canAct, variant = SriSuButtonVariant.Ghost)
                            }
                            if (session.allows("confirm", state.account)) SriSuButton("I’ve done my part", { onAction("confirm") }, Modifier.fillMaxWidth(), enabled = state.canAct)
                            if (session.allows("unconfirm", state.account)) SriSuButton("Undo my confirmation", { onAction("unconfirm") }, Modifier.fillMaxWidth(), enabled = state.canAct, variant = SriSuButtonVariant.Outline)
                            if (session.allows("resume", state.account)) SriSuButton("I’m ready to resume", { onAction("resume") }, Modifier.fillMaxWidth(), enabled = state.canAct)
                            if (session.allows("pause", state.account)) TextButton(onClick = { onAction("pause") }, enabled = state.canAct) { Text(if (session.state == "paused") "Pause for me too" else "Pause for now") }
                            if (session.state == "completed") {
                                SriSuButton("Share a Moment", { onMoment(session.couple) }, Modifier.fillMaxWidth(), enabled = state.fresh)
                                Text("Optional. You choose what to share and who can see it.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            TextButton(onClick = onChat) { Text("Continue in chat") }
                            if (session.allows("cancel", state.account)) TextButton(onClick = { confirmEnd = true }, enabled = state.canAct) { Text(if (session.state == "invited") "Withdraw invitation" else "End this Challenge", color = colors.error) }
                            else TextButton(onClick = { onBrowse(true) }) { Text("View your history") }
                        }
                    }
                }
                item("steps") {
                    Column(verticalArrangement = Arrangement.spacedBy(space.medium)) {
                        Text("A little way to begin", style = MaterialTheme.typography.titleMedium)
                        activity.steps.forEachIndexed { index, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(space.compact)) {
                                Text("${index + 1}.", style = MaterialTheme.typography.titleMedium)
                                Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                        Text(activity.alternative, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                if (session?.state == "invited" && session.creator == state.account && session.cardEventId.isBlank()) item("card") {
                    Column(verticalArrangement = Arrangement.spacedBy(space.small)) {
                        Text(state.cardError ?: if (state.cardBusy) "Sending your invitation to chat…" else "Your partner can also find this invitation on Home.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        if (!state.cardBusy) TextButton(onClick = onCard, enabled = state.fresh) { Text("Send invitation to chat") }
                    }
                }
                item("privacy") { Text("Private to your couple. Activity choices and progress are stored by SriSu; personal reflections stay in your encrypted chat.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            } else if (state.home?.couple != null) {
                item("intro") {
                    Text("Little things, together", style = MaterialTheme.typography.headlineMedium)
                    Text("Make room for an experience you can share. At your own pace.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = space.small))
                }
                state.home.current?.let { current -> item("current") { ChallengeSummaryCard(current, state.home.members, { onOpen(current.id) }) } }
                item("tabs") {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.small)) {
                        FilterChip(!state.historyTab, { onBrowse(false) }, { Text("Discover") })
                        FilterChip(state.historyTab, { onBrowse(true) }, { Text("Our history") })
                    }
                }
                if (state.historyTab) {
                    if (state.history.items.isEmpty() && !state.busy) item("empty-history") {
                        Text("Your shared experiences will live here", style = MaterialTheme.typography.titleMedium)
                        Text("Completed activities and invitations you passed on stay separate, without scores or streaks.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    items(state.history.items, key = { "history:${it.id}" }) { entry -> ChallengeSummaryCard(entry, state.home.members, { onOpen(entry.id) }) }
                    if (state.history.next != null) item("more") { SriSuButton("Load more", onMore, enabled = !state.busy, variant = SriSuButtonVariant.Outline) }
                } else {
                    item("collections") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(space.small)) {
                            state.home.collections.forEach { group -> FilterChip(state.collection == group.id, { onCollection(group.id) }, { Text(group.name) }) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(space.small)) {
                            FilterChip(state.apart, { onFilter(!state.apart, state.quick) }, { Text("Works apart") })
                            FilterChip(state.quick, { onFilter(state.apart, !state.quick) }, { Text("15 minutes or less") })
                        }
                    }
                    if (state.activities.isEmpty()) item("empty-filter") { Text("No activities with these filters. Try another collection or clear a filter.", style = MaterialTheme.typography.bodyMedium) }
                    items(state.activities, key = { it.id }) { template ->
                        Surface(onClick = { onSelect(template) }, shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(space.large), verticalArrangement = Arrangement.spacedBy(space.small)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.medium)) {
                                    ChallengeMark()
                                    Text(template.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                                }
                                Text(template.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                ActivityMetadata(template)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmEnd && session != null) AlertDialog(onDismissRequest = { confirmEnd = false },
        title = { Text(if (session.state == "invited") "Withdraw this invitation?" else "End this Challenge?") },
        text = { Text("It will stay in your private history as ended. You can choose another activity whenever you’re ready.") },
        confirmButton = { TextButton(onClick = { confirmEnd = false; onAction("cancel") }) { Text("End Challenge") } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep it") } })
}

@Composable private fun ActivityHeading(activity: ChallengeActivity, collection: String?, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        if (!compact) ChallengeMark()
        collection?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(activity.title, style = MaterialTheme.typography.headlineMedium)
        Text(activity.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ActivityMetadata(activity)
    }
}
@Composable private fun ActivityMetadata(activity: ChallengeActivity) {
    val mode = when (activity.mode) { "together" -> "Together"; "apart" -> "Apart"; else -> "Together or apart" }
    val cost = when (activity.cost) { "low" -> "Low cost"; "paid" -> "Some cost"; else -> "Free" }
    Text("About ${activity.minutes} min · $mode · $cost", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun ChallengeMark() {
    Surface(shape = MaterialTheme.shapes.pill, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.reminder,
            modifier = Modifier.padding(MaterialTheme.spacing.small).size(MaterialTheme.spacing.chromeIcon))
    }
}
internal fun challengeStatus(session: ChallengeSession) = when (session.state) {
    "invited" -> "Invitation pending"; "active" -> if (session.confirmedBy.isEmpty()) "At your own pace" else "One of you has confirmed"
    "paused" -> "Paused · no rush"; "completed" -> "Completed together"; "declined" -> "Passed on"; else -> "Ended"
}
@Composable internal fun ChallengeSummaryCard(session: ChallengeSession, members: List<ChallengeMember>, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
            ChallengeMark()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                Text(session.activity.title, style = MaterialTheme.typography.titleMedium)
                Text(challengeStatus(session), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(-MaterialTheme.spacing.small)) {
                members.take(2).forEach { MemberAvatar(it) }
            }
        }
    }
}
@Composable private fun MemberAvatar(member: ChallengeMember) {
    PartnerAvatar(ChatPartnerIdentity(member.name.ifBlank { "Your partner" }, member.photo),
        MaterialTheme.spacing.extraLarge, MaterialTheme.typography.labelMedium)
}
@Composable private fun ChallengeProgress(state: ChallengeState) {
    val session = state.session ?: return
    val completed = session.state == "completed"
    val duration = currentCoroutineContextForMotion()
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            AnimatedVisibility(completed, enter = fadeIn(tween(duration))) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.success)
                    Text("You did this together", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (!completed) Text(when (session.state) {
                "invited" -> if (session.creator == state.account) "A little invitation, sent" else "${state.partner?.name ?: "Your partner"} invited you"
                "paused" -> "Room to take your time"; "declined" -> "Maybe another activity"; "cancelled" -> "This Challenge has ended"; else -> "Your shared progress"
            }, style = MaterialTheme.typography.titleLarge)
            state.home?.members?.forEach { member ->
                val status = when {
                    member.id in session.pausedBy && session.state == "paused" -> "Paused"
                    member.id in session.confirmedBy -> "Confirmed done"
                    session.state == "invited" -> if (member.id in session.acceptedBy) "Invited you".takeIf { member.id != state.account } ?: "Ready to join" else "Hasn’t accepted yet"
                    session.state in listOf("declined", "cancelled") -> challengeStatus(session)
                    else -> "Not confirmed yet"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
                    MemberAvatar(member)
                    Column(Modifier.weight(1f)) {
                        Text(if (member.id == state.account) "You" else member.name, style = MaterialTheme.typography.titleMedium)
                        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (member.id in session.confirmedBy) Icon(Icons.Outlined.CheckCircle, "Confirmed done", tint = MaterialTheme.colorScheme.success)
                }
            }
            Text(when (session.state) {
                "paused" -> "Each of you can resume your own pause. Progress stays saved until you’re both ready."
                "active" -> "Each of you confirms your own part. It’s complete when you both confirm."
                "invited" -> "Nothing starts until you both agree. It’s always okay to pass."
                "completed" -> "A little experience to keep in your shared history."
                else -> "No score to lose. Choose something else whenever it feels right."
            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun currentCoroutineContextForMotion(): Int {
    val scope = rememberCoroutineScope()
    return if ((scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) 0 else 180
}
