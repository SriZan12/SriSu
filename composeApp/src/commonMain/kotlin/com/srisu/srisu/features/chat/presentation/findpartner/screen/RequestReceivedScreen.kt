package com.srisu.srisu.features.chat.presentation.findpartner.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cash.paging.compose.collectAsLazyPagingItems
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.theme.PartnerLinkTokens
import com.srisu.srisu.theme.SriSuPartnerLinkTypography
import com.srisu.srisu.theme.spacing

/** Retains the existing typed route, sharing the same paginated invitation UI. */
@Composable
fun ReceivedLoveRequestScreen(findPartnerViewModel: FindPartnerViewModel, onNavigateBack: () -> Unit, onNavigateToProfile: (String?) -> Unit) {
    val invitations = findPartnerViewModel.loveRequests.collectAsLazyPagingItems()
    val state by findPartnerViewModel.findPartnerUIState.collectAsStateWithLifecycle()
    LaunchedEffect(findPartnerViewModel) { findPartnerViewModel.onScreenEntered() }
    MaterialTheme(typography = SriSuPartnerLinkTypography()) {
        Scaffold(topBar = { PartnerLinkTopBar(onNavigateBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = PartnerLinkTokens.contentMaxWidth).fillMaxSize(),
                    contentPadding = PaddingValues(MaterialTheme.spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                    if (state.acceptedPartnerName != null) {
                        item { PartnerMessage("You’re connected!", action = "Continue", onAction = onNavigateBack) }
                    } else partnerInvitationItems(invitations, state, onRespond = { request, status ->
                        findPartnerViewModel.updateLoveRequest(request.id, request.senderNumber, request.receiverNumber, status, request.receiver)
                    }, onProfile = { onNavigateToProfile(findPartnerViewModel.getUserProfile(it)) })
                }
            }
        }
    }
}
