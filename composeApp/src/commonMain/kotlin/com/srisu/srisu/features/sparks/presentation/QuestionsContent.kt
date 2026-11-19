package com.srisu.srisu.features.sparks.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.rememberDraftEditor
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.chat.matrix.SparkRecoveryStatus
import com.srisu.srisu.features.chat.matrix.presentation.PartnerAvatar
import com.srisu.srisu.features.home.dashboard.screen.QuestionPrompt
import com.srisu.srisu.features.home.dashboard.screen.TogetherArt
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.theme.HomeSpace as Q
import com.srisu.srisu.theme.QuestionsStyle as QS

/** Catalogue-only search. Never indexes private answer text or encrypted payloads. */
internal fun questionMatches(decks: List<SparkDeck>, query: String): List<SparkQuestion> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return decks.flatMap { deck -> deck.questions.filter { question -> terms.all { term ->
        question.text.contains(term, ignoreCase = true) || deck.name.contains(term, ignoreCase = true)
    } } }
}

@Composable internal fun SparkContent(
    state: SparkState, onBack: () -> Unit, onChat: () -> Unit, onRefresh: () -> Unit,
    onStart: () -> Unit, onDraft: (String) -> Unit, onSubmit: () -> Unit, onSkip: () -> Unit,
    onOpen: (String) -> Unit, onBrowse: () -> Unit, onCard: () -> Unit, onKeys: () -> Unit,
    onRecovery: () -> Unit = {}, page: QuestionPage = QuestionPage.HOME, selection: String? = null,
    onNavigate: (QuestionPage, String?) -> Unit = { _, _ -> }, onConnect: () -> Unit = {},
    onInvitations: () -> Unit = {}, tab: Boolean = false,
) {
    val base = MaterialTheme.typography
    MaterialTheme(colorScheme = Q.colors, typography = QS.type(base)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Q.paper)) {
            val gutter = if (maxWidth < 360.dp) 16.dp else 20.dp
            val session = state.session
            val home = state.home
            val detail = session != null
            val title = when {
                detail -> "Question"
                page == QuestionPage.HISTORY -> "Answered"
                page == QuestionPage.SEARCH -> "Search"
                page == QuestionPage.TOPIC -> home?.decks?.firstOrNull { it.id == selection }?.name ?: "Topic"
                page == QuestionPage.PREVIEW -> "Question"
                else -> "Questions"
            }
            var skip by remember { mutableStateOf(false) }
            var query by remember { mutableStateOf("") }
            val largeText = LocalDensity.current.fontScale > 1.2f
            var menu by remember { mutableStateOf(false) }
            val current = state.daily?.session ?: home?.active
            val preview = home?.decks?.flatMap { it.questions }?.firstOrNull { it.id == selection }
            val available = home?.couple != null && state.fresh && home.unavailable == null
            Scaffold(modifier = Modifier.imePadding(), containerColor = Q.paper, topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = if (tab) gutter else 8.dp)
                    .heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!tab) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                    Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                    if (largeText) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "Question options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (detail) DropdownMenuItem(text = { Text("Protect and recover answers") }, enabled = state.secure, onClick = { menu = false; onRecovery() })
                            if (home?.couple != null && !detail && page == QuestionPage.HOME) {
                                DropdownMenuItem(text = { Text("Recently answered") }, onClick = { menu = false; onNavigate(QuestionPage.HISTORY, null) })
                                DropdownMenuItem(text = { Text("Search questions") }, onClick = { menu = false; onNavigate(QuestionPage.SEARCH, null) })
                            }
                            DropdownMenuItem(text = { Text("Refresh") }, enabled = !state.busy, onClick = { menu = false; onRefresh() })
                        }
                    }
                    else if (detail) IconButton(onClick = onRecovery, enabled = state.secure) { Icon(Icons.Outlined.Key, "Protect and recover answers") }
                    else if (home?.couple != null && page == QuestionPage.HOME) {
                        IconButton(onClick = { onNavigate(QuestionPage.HISTORY, null) }) { Icon(Icons.Outlined.History, "Recently answered questions") }
                        IconButton(onClick = { onNavigate(QuestionPage.SEARCH, null) }) { Icon(Icons.Outlined.Search, "Search questions") }
                    }
                    if (!largeText && page != QuestionPage.SEARCH) IconButton(onClick = onRefresh, enabled = !state.busy) {
                        Icon(Icons.Outlined.Refresh, "Refresh questions", Modifier.size(20.dp), tint = Q.muted)
                    }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Q.purple)
                    key(session?.id, page, selection) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("questions-scroll")
                            .padding(horizontal = gutter).padding(top = 12.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            state.error?.let { Note("Couldn’t update questions", it, Icons.Outlined.CloudOff, "Retry", onRefresh) }
                            when {
                                state.loading && home == null -> Note("A moment for you two", "Loading your questions…", Icons.Outlined.AutoAwesome)
                                home == null -> if (state.error == null) Note("Questions are unavailable", "Reconnect to try again.", Icons.Outlined.CloudOff, "Retry", onRefresh)
                                home.couple == null -> NoPartner(state, onConnect, onInvitations, onRefresh)
                                session != null -> {
                                    Prompt(session.question, session.deck.uppercase(), art = !session.revealed)
                                    if (state.backupPending || state.recovery.status == SparkRecoveryStatus.NotSetUp) {
                                        TextButton(onClick = onRecovery, enabled = state.secure) {
                                            Icon(Icons.Outlined.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                            Text(if (state.backupPending) "Backup needs attention" else "Protect your answers for your next phone")
                                        }
                                    }
                                    when {
                                        session.state == "skipped" -> Note("Room for another question", "This question was skipped. Unrevealed answers have been removed.", Icons.Outlined.AutoAwesome)
                                        session.revealed -> Reveal(state, onRecovery, onKeys)
                                        session.submitted(state.account) -> {
                                            val partner = home.members.firstOrNull { it.id != state.account }?.name ?: "your partner"
                                            Note("Your answer is locked in", "Waiting for $partner. Both answers open when you’ve each answered. There’s no rush.", Icons.Outlined.Lock)
                                            state.answers[state.account]?.let { answer ->
                                                home.members.firstOrNull { it.id == state.account }?.let { AnswerBubble(it, answer, true) }
                                            }
                                            if (state.keyError) KeyNotice(state.secure, onRecovery, onKeys)
                                        }
                                        else -> {
                                            if (session.submittedBy.isNotEmpty()) Note("Your partner has answered", "Answer to reveal what you both said.", Icons.Outlined.Lock)
                                            AnswerEditor(state, onDraft)
                                            Text("Only you two can read your answers. Both open after you’ve answered; submitted answers can’t be edited.", style = MaterialTheme.typography.bodyMedium, color = Q.muted)
                                            if (!state.secure) Note("Connecting your private space", if (state.secureConnecting) "Connecting to secure chat…" else state.secureMessage ?: "Reconnect before submitting. Your draft is kept.", Icons.Outlined.Lock,
                                                if (!state.secureConnecting) "Reconnect secure chat" else null, onKeys)
                                        }
                                    }
                                    if (session.creator == state.account && session.cardEventId.isEmpty() && session.state != "skipped")
                                        TextButton(onClick = onCard, enabled = state.secure && !state.busy) { Text("Add to chat") }
                                    if (session.open) TextButton(onClick = { skip = true }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip this question", color = Q.muted) }
                                }
                                page == QuestionPage.HISTORY -> {
                                    Text("Recent answers, together", style = MaterialTheme.typography.titleLarge)
                                    Text("Revisit the answered questions in your recent activity.", style = MaterialTheme.typography.bodyMedium, color = Q.muted)
                                    val answered = home.recent.filter { it.revealed }
                                    if (answered.isEmpty()) Note("Your story starts here", "Answer a question together and it will appear here.", Icons.Outlined.Forum, "Browse questions", onBrowse)
                                    answered.forEach { item -> QuestionRow(item.question, "Both answered · ${deckName(home, item.deck)}", { onOpen(item.id) }) }
                                }
                                page == QuestionPage.SEARCH -> {
                                    val focus = LocalFocusManager.current
                                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("question-search"),
                                        placeholder = { Text("Find a question or topic") }, singleLine = true,
                                        leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } },
                                        shape = Q.pill, colors = fieldColors(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
                                    Text(if (query.isBlank()) "Explore the questions" else "Search results", style = MaterialTheme.typography.titleLarge)
                                    val results = questionMatches(home.decks, query)
                                    if (results.isEmpty()) Note("No questions found", "Try a different word or browse a topic.", Icons.Outlined.Search, "Clear search", { query = "" })
                                    results.forEach { question -> QuestionRow(question.text, deckName(home, question.deck), { onNavigate(QuestionPage.PREVIEW, question.id) }) }
                                }
                                page == QuestionPage.TOPIC -> {
                                    val deck = home.decks.firstOrNull { it.id == selection }
                                    if (deck == null) Note("This topic is unavailable", "Refresh to see the latest questions.", Icons.Outlined.AutoAwesome, "Retry", onRefresh)
                                    else {
                                        TopicIntro(deck)
                                        deck.questions.forEachIndexed { index, question -> QuestionRow(question.text, "Question ${index + 1}", { onNavigate(QuestionPage.PREVIEW, question.id) }) }
                                    }
                                }
                                page == QuestionPage.PREVIEW -> {
                                    if (preview == null) Note("This question is unavailable", "Refresh to see the latest questions.", Icons.Outlined.AutoAwesome, "Retry", onRefresh)
                                    else {
                                        Prompt(preview.text, deckName(home, preview.deck).uppercase())
                                        if (state.startPending) Note("Confirming your previous question", "Retry keeps the question you already chose. It won’t create another.", Icons.Outlined.Sync)
                                        Note(if (home.active?.open == true) "One question at a time" else "A little closer, at your pace",
                                            if (home.active?.open == true) "Finish or skip your current question before starting another." else "Write your answers separately. Discover them together once you’ve both answered.", Icons.Outlined.Lock)
                                    }
                                }
                                else -> {
                                    state.dailyError?.let { Note("Daily question is unavailable", it, Icons.Outlined.CloudOff, "Retry", onRefresh) }
                                    home.unavailable?.let { Note("Your shared space is getting ready", it, Icons.Outlined.Lock, "Open secure chat", onChat) }
                                    if (current != null) CurrentQuestion(state, current, { onOpen(current.id) })
                                    else if (state.dailyError == null && home.unavailable == null) Note("A question for the two of you", "Choose a topic and find a new way into conversation.", Icons.Outlined.Forum)
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text("Find your next conversation", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                                        TopicGrid(home.decks) { onNavigate(QuestionPage.TOPIC, it) }
                                    }
                                    val recent = home.recent.filter { it.revealed }
                                    if (recent.isNotEmpty()) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text("Answered together", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                                            TextButton(onClick = { onNavigate(QuestionPage.HISTORY, null) }) { Text("View recent") }
                                        }
                                        recent.take(2).forEach { QuestionRow(it.question, "Both answered", { onOpen(it.id) }) }
                                    } else Note("More of your story", "Your answered questions will live here, ready to revisit.", Icons.Outlined.FavoriteBorder)
                                    Text("Private between you two. No right answer, no rush.", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Q.muted)
                                }
                            }
                        }
                    }
                    val action = when {
                        session?.state == "skipped" -> "Back to questions"
                        session?.revealed == true -> "Continue in chat"
                        session?.submitted(state.account) == true -> "Open chat"
                        session != null -> if (state.draft.envelope != null) "Retry submission" else if (session.submittedBy.isEmpty()) "Lock in my answer" else "Answer & reveal"
                        page == QuestionPage.PREVIEW && preview != null && home?.couple != null -> if (home.active?.open == true) "Continue current question" else if (state.startPending) "Retry starting question" else "Answer this question"
                        else -> null
                    }
                    if (action != null) {
                        val focus = LocalFocusManager.current
                        Button(onClick = {
                            focus.clearFocus()
                            when {
                                session == null -> onStart()
                                session.state == "skipped" -> onBrowse()
                                session.revealed || session.submitted(state.account) -> onChat()
                                else -> onSubmit()
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 8.dp).heightIn(min = 48.dp).testTag("question-primary"), shape = Q.pill,
                            enabled = !state.loading && !state.busy && when {
                                session == null -> available && (state.startPending || state.selectedQuestion?.id == preview?.id)
                                session.revealed || session.state == "skipped" || session.submitted(state.account) -> true
                                else -> state.secure && state.draft.text.isNotBlank()
                            }) { Text(action, Modifier.padding(vertical = 4.dp), textAlign = TextAlign.Center) }
                    }
                }
            }
            if (skip) AlertDialog(onDismissRequest = { skip = false }, title = { Text("Skip this question?") },
                text = { Text("Neither pending answer will be revealed. You can browse another question; the next daily question arrives on a later day.") },
                confirmButton = { TextButton(onClick = { skip = false; onSkip() }) { Text("Skip question") } },
                dismissButton = { TextButton(onClick = { skip = false }) { Text("Keep it") } })
        }
    }
}

@Composable private fun Prompt(text: String, label: String, art: Boolean = true, content: @Composable ColumnScope.() -> Unit = {}) {
    val large = LocalDensity.current.fontScale > 1.2f
    Surface(color = Q.lilac, shape = QS.hero) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(label, color = Q.purple, style = MaterialTheme.typography.labelSmall)
            QuestionPrompt(text, if (art && !large) 66.dp else null)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CurrentQuestion(state: SparkState, session: SparkSession, onOpen: () -> Unit) {
    val self = session.submitted(state.account)
    val partner = session.submittedBy.any { it != state.account }
    val label = when { session.revealed -> "ANSWERED TOGETHER"; session.state == "skipped" -> "SKIPPED TODAY"; state.daily?.carried == true -> "YOUR QUESTION"; else -> "TODAY" }
    val action = when { session.revealed -> "Read answers"; session.state == "skipped" -> "View question"; self -> "View my answer"; partner -> "Answer to reveal"; else -> "Go first" }
    Prompt(session.question, label) {
        if (partner && !self && session.open) Surface(color = Q.paper.copy(alpha = .8f), shape = QS.inset) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp)); Text("Their answer is waiting for yours", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (self && session.open) Text("Your answer is locked in. Take your time, together.", style = MaterialTheme.typography.bodyMedium, color = Q.muted)
        if (!session.open) Text("Your next daily question arrives on a later day.", style = MaterialTheme.typography.bodyMedium, color = Q.muted)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                state.home?.members?.take(2)?.forEach { MemberAvatar(it, 26) }
            }
            Button(onClick = onOpen, shape = Q.pill, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), modifier = Modifier.widthIn(max = 210.dp).heightIn(min = 48.dp)) {
                Text(action, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun AnswerEditor(state: SparkState, onDraft: (String) -> Unit) {
    key(state.session?.id) {
        val editor = rememberDraftEditor(state.draft.text)
        val bringIntoView = remember { BringIntoViewRequester() }
        var focused by remember { mutableStateOf(false) }
        val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
        LaunchedEffect(focused, keyboard) {
            if (focused && keyboard > 0) {
                // Recalculate after the native keyboard has resized the viewport.
                withFrameNanos { }
                bringIntoView.bringIntoView()
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your answer", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(editor.value, { value -> if (value.text.length <= SPARK_ANSWER_LIMIT && editor.edit(value)) onDraft(value.text) },
                Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).onFocusChanged { focused = it.isFocused }.testTag("question-answer"), enabled = state.canEdit, minLines = 4, maxLines = 8,
                placeholder = { Text("Take a moment. Say it your way.") }, textStyle = MaterialTheme.typography.bodyLarge,
                shape = QS.row, colors = fieldColors())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.draft.envelope != null) "Submission pending · draft kept" else "Private draft · saved on this device", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Q.muted)
                Text("${editor.value.text.length}/$SPARK_ANSWER_LIMIT", style = MaterialTheme.typography.bodySmall, color = Q.muted)
            }
        }
    }
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Q.warm, unfocusedContainerColor = Q.warm, disabledContainerColor = Q.warm,
    unfocusedBorderColor = QS.invisible, focusedBorderColor = Q.purple, disabledBorderColor = QS.invisible,
    cursorColor = Q.purple, focusedTextColor = Q.ink, unfocusedTextColor = Q.ink,
)

@Composable private fun Reveal(state: SparkState, onRecovery: () -> Unit, onKeys: () -> Unit) {
    val session = state.session ?: return
    val complete = session.submittedBy.size == 2 && session.submittedBy.all { it in state.answers }
    Text(if (complete) "Both answers are open" else "Both answers are submitted", style = MaterialTheme.typography.titleLarge)
    if (!complete) {
        if (state.keyError) KeyNotice(state.secure, onRecovery, onKeys)
        else Note("Opening your answers", "Your answers stay encrypted while this device syncs its keys.", Icons.Outlined.Lock)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            state.home?.members?.sortedBy { if (it.id == state.account) 1 else 0 }?.forEach { member ->
                state.answers[member.id]?.let { AnswerBubble(member, it, member.id == state.account) }
            }
        }
        Text("There’s more to every answer. Keep the conversation going.", style = MaterialTheme.typography.bodyMedium, color = Q.muted)
    }
}

@Composable private fun AnswerBubble(member: SparkMember, answer: String, own: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (own) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemberAvatar(member, 24); Text(if (own) "You" else member.name, style = MaterialTheme.typography.labelLarge)
        }
        Surface(Modifier.fillMaxWidth(.94f), color = if (own) Q.lilac else Q.warm,
            shape = if (own) QS.sent else QS.received) {
            Text(answer, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable private fun MemberAvatar(member: SparkMember, size: Int) {
    PartnerAvatar(ChatPartnerIdentity(member.name, member.photo), size.dp, MaterialTheme.typography.bodySmall)
}

@Composable private fun KeyNotice(secure: Boolean, onRecovery: () -> Unit, onKeys: () -> Unit) {
    Note("Answers unavailable on this device", "Your answers are still encrypted. Reconnect and retry. On a new phone, unlock your encrypted backup with your recovery key. Answers never backed up need an existing device.", Icons.Outlined.Lock)
    TextButton(onClick = onRecovery, enabled = secure) { Text("Recover answers") }
    TextButton(onClick = onKeys) { Text("Retry secure sync") }
}

@Composable private fun NoPartner(state: SparkState, onConnect: () -> Unit, onInvitations: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TogetherArt(Modifier.size(112.dp))
        Text(when { state.invitation?.incoming == true -> "Your invitation is here"; state.invitation != null -> "Your invitation is sent"; else -> "Questions take two" }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(when { state.invitation?.incoming == true -> "${state.invitation.name} wants to share this space with you."
            state.invitation != null -> "Once ${state.invitation.name} joins, your questions and answers will live here."
            else -> "Connect with your person. A little question can become your favourite conversation." },
            style = MaterialTheme.typography.bodyLarge, color = Q.muted, textAlign = TextAlign.Center)
        if (state.invitationError) Note("Couldn’t check invitations", "Your connection status needs a refresh.", Icons.Outlined.CloudOff, "Retry", onRefresh)
        Button(onClick = if (state.invitation != null || state.invitationError) onInvitations else onConnect, shape = Q.pill, enabled = !state.loading && !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (state.invitation != null || state.invitationError) "View invitations" else "Connect with your partner")
        }
        if (state.invitation == null && !state.invitationError) TextButton(onClick = onInvitations) { Text("Check invitations") }
    }
}

@Composable private fun Note(title: String, text: String, icon: ImageVector, action: String? = null, onAction: () -> Unit = {}) {
    Surface(color = Q.warm, shape = QS.row) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.size(18.dp), tint = Q.muted)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Q.muted)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable private fun QuestionRow(text: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Q.warm, shape = QS.row, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Q.muted)
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

private fun topicColor(id: String) = when (id) { "memories" -> Q.apricot; "playful" -> Q.pink; "future" -> Q.sky; else -> Q.lilac }
private fun topicIcon(id: String) = when (id) { "memories" -> Icons.Outlined.PhotoCamera; "playful" -> Icons.Outlined.WbSunny; "future" -> Icons.Outlined.Nightlight; else -> Icons.Outlined.FavoriteBorder }
private fun deckName(home: SparkHome, id: String) = home.decks.firstOrNull { it.id == id }?.name ?: id

@Composable private fun TopicIntro(deck: SparkDeck) {
    Surface(color = topicColor(deck.id), shape = QS.topic) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(topicIcon(deck.id), null, Modifier.size(28.dp))
            Text(deck.subtitle, style = MaterialTheme.typography.headlineMedium)
            Text("${deck.questions.size} questions · take your time together", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun TopicGrid(decks: List<SparkDeck>, onOpen: (String) -> Unit) {
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        decks.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { deck ->
                    Surface(onClick = { onOpen(deck.id) }, color = topicColor(deck.id), shape = QS.tile, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(topicIcon(deck.id), null, Modifier.size(28.dp))
                            Text(deck.name, style = MaterialTheme.typography.titleSmall)
                            Text("${deck.questions.size} questions", style = MaterialTheme.typography.bodyMedium, color = Q.muted)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
