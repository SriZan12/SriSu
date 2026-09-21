package com.srisu.srisu.features.chat.presentation.findpartner.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonVariant
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.theme.PartnerLinkTokens
import com.srisu.srisu.theme.SriSuPartnerLinkTypography
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.partner_invite_landscape
import srisu.composeapp.generated.resources.partner_invite_pending

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InviteSentScreen(findPartnerViewModel: FindPartnerViewModel, onNavigateBack: () -> Unit) {
    val state by findPartnerViewModel.findPartnerUIState.collectAsStateWithLifecycle()
    var confirmCancel by rememberSaveable { mutableStateOf(value = false) }
    var hadInvitation by rememberSaveable { mutableStateOf(value = state.sentInvitation != null) }

    LaunchedEffect(key1 = findPartnerViewModel) { findPartnerViewModel.onScreenEntered() }

    LaunchedEffect(key1 = state.sentInvitation) {
        if (state.sentInvitation != null) hadInvitation = true
        else if (hadInvitation && state.acceptedPartnerName == null) onNavigateBack()
    }

    MaterialTheme(typography = SriSuPartnerLinkTypography()) {

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { PartnerLinkTopBar(onBack = onNavigateBack) }) { padding ->

            BoxWithConstraints(
                Modifier.fillMaxSize().padding(paddingValues = padding),
                contentAlignment = Alignment.TopCenter
            ) {

                val viewportHeight = maxHeight

                Column(
                    Modifier.widthIn(max = PartnerLinkTokens.contentMaxWidth).fillMaxWidth()
                        .verticalScroll(state = rememberScrollState())
                        .heightIn(min = viewportHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {

                    Spacer(Modifier.height(MaterialTheme.spacing.spacious))

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(
                            Modifier.size(PartnerLinkTokens.pendingBadge),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(Res.drawable.partner_invite_pending),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(PartnerLinkTokens.pendingIcon)
                            )
                        }
                    }

                    Spacer(Modifier.height(MaterialTheme.spacing.gutter))

                    val invitation = state.sentInvitation

                    Column(Modifier.padding(horizontal = MaterialTheme.spacing.gutter)) {

                        PartnerLinkHeading(
                            title = if (state.acceptedPartnerName != null) "You’re connected!" else "Invite sent!",
                            subtitle = if (state.acceptedPartnerName != null) "Your partner has accepted your invitation."
                            else invitation?.let { "Waiting for ${it.name} to join. You’ll be notified once they accept." }
                                ?: "Check your pending invitation below.",
                        )

                        Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))

                        if (invitation != null) {
                            Card(
                                Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                PartnerIdentity(
                                    invitation.name,
                                    detail = if (state.acceptedPartnerName != null) "Connected" else "Invite sent · Waiting for acceptance",
                                    invitation.photoUrl,
                                    Modifier.padding(
                                        horizontal = MaterialTheme.spacing.medium,
                                        vertical = MaterialTheme.spacing.compact
                                    ),
                                    pending = true
                                )
                            }

                            FlowRow(
                                Modifier.fillMaxWidth()
                                    .padding(top = MaterialTheme.spacing.compact),
                                horizontalArrangement = Arrangement.spacedBy(
                                    MaterialTheme.spacing.small,
                                    Alignment.CenterHorizontally
                                )
                            ) {
                                SriSuButton(
                                    label = "Check status",
                                    onClick = findPartnerViewModel::sendHaveCoupleConnectionRequested,
                                    enabled = !state.isCheckingInvitation && !state.isCancellingInvitation,
                                    variant = SriSuButtonVariant.Ghost
                                )
                                if (state.acceptedPartnerName == null) SriSuButton(
                                    label = "Cancel invite", onClick = { confirmCancel = true },
                                    enabled = !state.isCancellingInvitation && !state.isCheckingInvitation && invitation.requestId != null,
                                    variant = SriSuButtonVariant.Ghost
                                )
                            }
                            if (invitation.requestId == null && !state.isCheckingInvitation) {
                                PartnerMessage("Check status to retrieve this invitation before cancelling it.")
                            }
                        } else if (!state.isCheckingInvitation) {
                            PartnerMessage(
                                "No pending invitation was found.",
                                action = "Back to partner search",
                                onAction = onNavigateBack
                            )
                        }
                        if (state.isCheckingInvitation) PartnerProgress("Checking invitation…")
                        if (state.isCancellingInvitation) PartnerProgress("Cancelling invitation…")
                        state.invitationStatusError?.let {
                            PartnerMessage(
                                it,
                                isError = true,
                                action = "Retry",
                                onAction = findPartnerViewModel::sendHaveCoupleConnectionRequested
                            )
                        }
                        state.invitationError?.let {
                            PartnerMessage(it, true, "Retry cancellation", { confirmCancel = true })
                        }
                    }
                    Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
                    Spacer(Modifier.weight(1f))
                    // One replaceable asset exported from the Figma InviteSentScreen illustration.
                    Image(
                        painterResource(Res.drawable.partner_invite_landscape),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(PartnerLinkTokens.landscapeAspectRatio)
                    )
                }
            }
        }

        if (confirmCancel) AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel invitation?") },
            text = { Text("Your partner will no longer be able to accept this invitation. You can invite them again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false; findPartnerViewModel.cancelSentInvitation()
                }) { Text("Cancel invite") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmCancel = false
                }) { Text("Keep invitation") }
            },
        )
    }
}
