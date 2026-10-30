package com.srisu.srisu.features.sparks.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.*
import com.srisu.srisu.features.couplechat.presentation.ProtectConversation
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.theme.*
import org.koin.compose.viewmodel.koinViewModel

@Composable fun SparkScreen(id: String?, onBack: () -> Unit, onChat: () -> Unit, vm: SparkViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.open(id) }
    MomentLifecycle(vm::visible)
    ProtectConversation()
    if (state.recovery.open) {
        SparkRecoveryContent(state.recovery, state.secure, vm::closeRecovery, vm::generateRecoveryKey,
            vm::recoverExisting, vm::recoveryKey, vm::recoveryConfirmation, vm::enableRecovery,
            vm::unlockRecovery, vm::backupRecovery, vm::openRecovery)
        return
    }
    SparkContent(state, onBack, onChat, { vm.refresh() }, vm::selectDeck, vm::moveQuestion, vm::shuffle,
        vm::startSpark, vm::draft, vm::submit, vm::skip, vm::openSession, vm::browse, vm::publishCard, vm::retryKeys, vm::openRecovery)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SparkContent(state: SparkState, onBack: () -> Unit, onChat: () -> Unit,
    onRefresh: () -> Unit, onDeck: (Int) -> Unit, onQuestion: (Int) -> Unit, onShuffle: () -> Unit,
    onStart: () -> Unit, onDraft: (String) -> Unit, onSubmit: () -> Unit, onSkip: () -> Unit,
    onOpen: (String) -> Unit, onBrowse: () -> Unit, onCard: () -> Unit, onKeys: () -> Unit, onRecovery: () -> Unit = {}) {
    val space = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val session = state.session
    var confirmSkip by remember { mutableStateOf(false) }
    Scaffold(containerColor = colors.background, topBar = {
        TopAppBar(title = { Text("Sparks", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = {
                if (session != null) IconButton(onClick = onRecovery, enabled = state.secure) { Icon(Icons.Outlined.Key, "Protect and recover answers") }
                IconButton(onClick = onRefresh, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Refresh Sparks") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.spark)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter, vertical = space.medium), verticalArrangement = Arrangement.spacedBy(space.large)) {
                state.error?.let { message ->
                    Surface(color = colors.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(space.medium)) {
                            Text(message, color = colors.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Refresh") }
                        }
                    }
                }
                when {
                    state.loading && state.home == null -> SparkNotice("A little closer", "Loading your Sparks…", Icons.Outlined.AutoAwesome)
                    state.error != null && state.home == null -> SparkNotice("Sparks are unavailable", "Refresh when you’re connected to open your private questions.", Icons.Outlined.CloudOff)
                    state.home?.couple == null -> SparkNotice("A space for the two of you", "Connect with your partner to answer a Spark together.", Icons.Outlined.People)
                    session == null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(space.small)) {
                            Text("One question. Two perspectives.", style = SriSuPartnerLinkTypography().headlineLarge)
                            Text("Choose a Spark, answer in your own time, then discover each other’s answer together.",
                                style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        state.home.active?.takeIf { it.open }?.let { active ->
                            Column(verticalArrangement = Arrangement.spacedBy(space.small)) {
                                HomeSparkCard(active, { onOpen(active.id) })
                                Text("Finish or skip your current Spark before sending another.",
                                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                        SparkDeckSelector(state.home.decks, state.deck, !state.busy && !state.startPending, onDeck)
                        state.selectedQuestion?.let { question ->
                            SparkQuestionCard(question.text, question.deck, compact = false,
                                number = "${state.question + 1}".padStart(2, '0'))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onQuestion(-1) }, enabled = !state.startPending && !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous question") }
                                Text("${state.question + 1} of ${state.selectedDeck?.questions?.size}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                TextButton(onClick = onShuffle, enabled = !state.startPending && !state.busy) { Icon(Icons.Outlined.Shuffle, null, Modifier.size(space.icon)); Spacer(Modifier.width(space.tiny)); Text("Shuffle") }
                                IconButton(onClick = { onQuestion(1) }, enabled = !state.startPending && !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Next question") }
                            }
                        }
                        state.home.unavailable?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant); TextButton(onClick = onChat) { Text("Open secure chat") } }
                        PrivacyNote()
                        if (state.home.recent.isNotEmpty()) {
                            Text("Your Sparks", style = MaterialTheme.typography.titleMedium)
                            state.home.recent.forEach { recent ->
                                Surface(onClick = { onOpen(recent.id) }, shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(space.hairline, colors.outlineVariant), color = colors.surface) {
                                    Column(Modifier.fillMaxWidth().padding(space.medium), verticalArrangement = Arrangement.spacedBy(space.tiny)) {
                                        Text(if (recent.revealed) "Both answers open" else "Skipped", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                        Text(recent.question, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        SparkQuestionCard(session.question, session.deck, compact = true)
                        if (state.recovery.status == com.srisu.srisu.features.chat.matrix.SparkRecoveryStatus.NotSetUp || state.backupPending) {
                            TextButton(onClick = onRecovery, enabled = state.secure) {
                                Icon(Icons.Outlined.Key, null); Spacer(Modifier.width(space.small))
                                Text(if (state.backupPending) "Backup needs attention" else "Protect your answers for your next phone")
                            }
                        }
                        when {
                            session.state == "skipped" -> SparkNotice("Room for another question", "This Spark was skipped. Unrevealed answers have been removed.", Icons.Outlined.AutoAwesome)
                            session.revealed -> {
                                val complete = session.submittedBy.size == 2 && session.submittedBy.all { it in state.answers }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (complete) "Both answers are open" else "Both answers are submitted", Modifier.weight(1f), style = SriSuPartnerLinkTypography().headlineMedium)
                                    TextButton(onClick = onBrowse) { Text("All decks") }
                                }
                                if (!complete) {
                                    if (state.keyError) SparkKeyNotice()
                                    else SparkNotice("Opening your answers", "Your answers stay encrypted while this device syncs its keys.", Icons.Outlined.Lock)
                                    if (state.keyError) { TextButton(onClick = onRecovery, enabled = state.secure) { Text("Recover answers") }; TextButton(onClick = onKeys) { Text("Retry secure sync") } }
                                }
                                val scope = rememberCoroutineScope()
                                val motion = scope.coroutineContext[MotionDurationScale]?.scaleFactor != 0f
                                AnimatedVisibility(complete, enter = fadeIn(tween(if (motion) 180 else 0))) {
                                    Column(verticalArrangement = Arrangement.spacedBy(space.medium)) {
                                        state.home?.members?.sortedBy { if (it.id == state.account) 0 else 1 }?.forEach { member ->
                                            state.answers[member.id]?.let { answer -> SparkAnswerCard(member, answer, member.id == state.account) }
                                        }
                                    }
                                }
                                if (complete) Text("There’s more to every answer. Take yours into the conversation.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                            session.submitted(state.account) -> {
                                val partner = state.home?.members?.firstOrNull { it.id != state.account }?.name ?: "your partner"
                                SparkNotice("Your answer is safely in", "Waiting for $partner. Both answers open when you’ve each submitted. There’s no rush.", Icons.Outlined.Lock)
                                state.answers[state.account]?.let { own -> state.home?.members?.firstOrNull { it.id == state.account }?.let { SparkAnswerCard(it, own, true) } }
                                if (state.keyError) {
                                    SparkKeyNotice()
                                    TextButton(onClick = onRecovery, enabled = state.secure) { Text("Recover answers") }
                                    TextButton(onClick = onKeys) { Text("Retry secure sync") }
                                }
                            }
                            else -> {
                                if (session.submittedBy.isNotEmpty()) Surface(color = colors.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                                    Row(Modifier.fillMaxWidth().padding(space.medium), horizontalArrangement = Arrangement.spacedBy(space.small)) {
                                        Icon(Icons.Outlined.Lock, null)
                                        Text("Your partner has answered. Yours will open both answers.", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                OutlinedTextField(value = state.draft.text, onValueChange = onDraft,
                                    modifier = Modifier.fillMaxWidth(), enabled = state.canEdit,
                                    label = { Text("Your answer") }, placeholder = { Text("Take a moment. Say it your way.") },
                                    minLines = 4, maxLines = 8, shape = MaterialTheme.shapes.medium,
                                    supportingText = { Row(Modifier.fillMaxWidth()) {
                                        Text(if (state.draft.envelope != null) "Submission pending · draft kept" else "Private draft · saved on this device", Modifier.weight(1f))
                                        Text("${state.draft.text.length}/$SPARK_ANSWER_LIMIT")
                                    } })
                                PrivacyNote()
                                Text("Your answer becomes visible when you’ve both submitted. Submitted answers cannot be edited.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                if (!state.secure) {
                                    Text(if (state.secureConnecting) "Connecting to secure chat…" else state.secureMessage
                                        ?: "Secure chat needs to reconnect before you can submit. Your draft is kept.",
                                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                    if (!state.secureConnecting) TextButton(onClick = onKeys, enabled = !state.busy) { Text("Reconnect secure chat") }
                                }
                            }
                        }
                        if (session.creator == state.account && session.cardEventId.isEmpty() && session.state != "skipped")
                            TextButton(onClick = onCard, enabled = state.secure && !state.busy) { Text("Add to chat") }
                        if (session.open) TextButton(onClick = { confirmSkip = true }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip this Spark", color = colors.onSurfaceVariant) }
                    }
                }
            }
            val action = when {
                session == null && state.home?.active?.open == true -> "Continue current Spark"
                session == null && state.home?.couple != null -> if (state.startPending) "Retry sending Spark" else "Send into chat"
                session?.revealed == true -> "Continue in chat"
                session?.state == "skipped" -> "Choose another Spark"
                session?.submitted(state.account) == true -> "Back to chat"
                session != null -> if (state.draft.envelope != null) "Retry submission" else if (session.submittedBy.isEmpty()) "Submit my answer" else "Submit & reveal"
                else -> null
            }
            if (action != null) Surface(color = colors.background) {
                SriSuButton(action, onClick = {
                    when { session == null -> onStart(); session.state == "skipped" -> onBrowse(); session.revealed || session.submitted(state.account) -> onChat(); else -> onSubmit() }
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = space.gutter, vertical = space.medium), size = SriSuButtonSize.Large,
                    enabled = !state.busy && !state.loading && when {
                        session == null -> state.fresh && (state.home?.active?.open == true || state.home?.unavailable == null)
                        session.revealed || session.state == "skipped" || session.submitted(state.account) -> true
                        else -> state.secure && state.draft.text.isNotBlank()
                    })
            }
        }
    }
    if (confirmSkip) AlertDialog(onDismissRequest = { confirmSkip = false }, title = { Text("Skip this Spark?") },
        text = { Text("Neither pending answer will be revealed. You can choose another question whenever you like.") },
        confirmButton = { TextButton(onClick = { confirmSkip = false; onSkip() }) { Text("Skip Spark") } },
        dismissButton = { TextButton(onClick = { confirmSkip = false }) { Text("Keep it") } })
}

@Composable private fun SparkKeyNotice() = SparkNotice(
    "Answers unavailable on this device",
    "Your answers are still encrypted. Reconnect and retry. On a new phone, unlock your encrypted backup with your recovery key. Answers that were never backed up need an existing device.",
    Icons.Outlined.Lock,
)

@Composable internal fun deckColor(deck: String): Color = when (deck) {
    "memories" -> MaterialTheme.colorScheme.ask
    "playful" -> MaterialTheme.colorScheme.planner
    "future" -> MaterialTheme.colorScheme.moments
    else -> MaterialTheme.colorScheme.spark
}
@Composable internal fun SparkQuestionCard(question: String, deck: String, compact: Boolean, number: String? = null) {
    val space = MaterialTheme.spacing
    val accent = deckColor(deck)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (compact) space.hairline else 2.dp, accent.copy(alpha = if (compact) .45f else .8f))) {
        Column(Modifier.fillMaxWidth().padding(if (compact) space.large else space.extraLarge),
            verticalArrangement = Arrangement.spacedBy(if (compact) space.medium else space.extraLarge)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(deck.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (number != null) Text(number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Icon(Icons.Outlined.AutoAwesome, null, tint = accent, modifier = Modifier.size(space.icon))
            }
            if (!compact) Box(Modifier.align(Alignment.CenterHorizontally).size(space.huge).clip(CircleShape).background(accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = accent, modifier = Modifier.size(space.extraLarge))
            }
            Text(question, style = if (compact) SriSuPartnerLinkTypography().headlineMedium else SriSuPartnerLinkTypography().headlineLarge,
                textAlign = if (compact) TextAlign.Start else TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (!compact) Text("SRISU SPARK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
@Composable private fun PrivacyNote() {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(MaterialTheme.spacing.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Just for you two · end-to-end encrypted answers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun SparkNotice(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.spark)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun SparkAnswerCard(member: SparkMember, answer: String, own: Boolean) {
    Surface(shape = MaterialTheme.shapes.medium, color = if (own) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                com.srisu.srisu.features.chat.matrix.presentation.PartnerAvatar(
                    com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity(member.name, member.photo), MaterialTheme.spacing.extraLarge, MaterialTheme.typography.bodySmall)
                Text(if (own) "You" else member.name, style = MaterialTheme.typography.labelLarge)
            }
            Text(answer, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable private fun SparkDeckSelector(decks: List<SparkDeck>, selected: Int, enabled: Boolean, onDeck: (Int) -> Unit) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (fontScale > 1.2f || maxWidth < 320.dp) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            decks.withIndex().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    row.forEach { (index, deck) ->
                        Surface(onClick = { onDeck(index) }, enabled = enabled, modifier = Modifier.weight(1f)
                            .semantics { this.selected = selected == index }, shape = MaterialTheme.shapes.small,
                            color = if (index == selected) deckColor(deck.id).copy(alpha = .12f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(MaterialTheme.spacing.hairline, if (index == selected) deckColor(deck.id) else MaterialTheme.colorScheme.outlineVariant)) {
                            Box(Modifier.heightIn(min = MaterialTheme.spacing.touchTarget).padding(MaterialTheme.spacing.small), contentAlignment = Alignment.Center) {
                                Text(deck.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
