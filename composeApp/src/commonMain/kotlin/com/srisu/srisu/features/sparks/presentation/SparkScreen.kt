package com.srisu.srisu.features.sparks.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.couplechat.presentation.ProtectConversation
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.features.sparks.data.SparkSession
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable enum class QuestionPage { HOME, HISTORY, SEARCH, TOPIC, PREVIEW }

@Composable fun SparkScreen(
    id: String?, onBack: () -> Unit, onChat: () -> Unit,
    vm: SparkViewModel = koinViewModel(), page: QuestionPage = QuestionPage.HOME,
    selection: String? = null, tab: Boolean = false,
    onNavigate: (QuestionPage, String?) -> Unit = { _, _ -> },
    onSession: ((String) -> Unit)? = null,
    onConnect: () -> Unit = {}, onInvitations: () -> Unit = {},
    onChatSession: ((SparkSession?) -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.open(id, daily = page == QuestionPage.HOME) }
    LaunchedEffect(selection, state.home, state.busy) {
        if (page == QuestionPage.PREVIEW && selection != null) vm.selectQuestion(selection)
    }
    MomentLifecycle(vm::visible)
    ProtectConversation()
    if (state.recovery.open) {
        SparkRecoveryContent(state.recovery, state.secure, vm::closeRecovery, vm::generateRecoveryKey,
            vm::recoverExisting, vm::recoveryKey, vm::recoveryConfirmation, vm::enableRecovery,
            vm::unlockRecovery, vm::backupRecovery, vm::openRecovery)
        return
    }
    SparkContent(state, onBack, { if (onChatSession != null) onChatSession(state.session) else onChat() },
        { vm.refresh() }, vm::startSpark, vm::draft, vm::submit, vm::skip,
        onSession ?: vm::openSession, onBack, vm::publishCard, vm::retryKeys, vm::openRecovery,
        page, selection, onNavigate, onConnect, onInvitations, tab)
}
