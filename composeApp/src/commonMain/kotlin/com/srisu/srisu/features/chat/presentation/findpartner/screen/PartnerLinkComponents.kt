package com.srisu.srisu.features.chat.presentation.findpartner.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import app.cash.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonSize
import com.srisu.srisu.components.SriSuButtonVariant
import com.srisu.srisu.features.chat.presentation.findpartner.state.FindPartnerState
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.theme.PartnerLinkTokens
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.utils.Constants
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.image_placeholder
import srisu.composeapp.generated.resources.partner_link_back

@Composable
internal fun PartnerLinkTopBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = MaterialTheme.spacing.small)) {
        IconButton(onClick = onBack) {
            Icon(painterResource(Res.drawable.partner_link_back), contentDescription = "Back",
                modifier = Modifier.size(MaterialTheme.spacing.icon))
        }
    }
}

@Composable
internal fun PartnerLinkHeading(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.compact),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun PartnerIdentity(
    name: String,
    detail: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
        AsyncImage(
            model = photoUrl,
            placeholder = painterResource(Res.drawable.image_placeholder),
            error = painterResource(Res.drawable.image_placeholder),
            fallback = painterResource(Res.drawable.image_placeholder),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(if (pending) PartnerLinkTokens.pendingAvatar else PartnerLinkTokens.avatar).clip(CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PartnerProgress(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.medium)
        .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(MaterialTheme.spacing.chromeIcon))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun PartnerMessage(message: String, isError: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) SriSuButton(action, onAction, variant = SriSuButtonVariant.Outline)
    }
}

internal fun LazyListScope.partnerInvitationItems(
    invitations: LazyPagingItems<CoupleConnectionRequestResponse.Result>,
    state: FindPartnerState,
    onRespond: (CoupleConnectionRequestResponse.Result, String) -> Unit,
    onProfile: (CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
) {
    val refresh = invitations.loadState.refresh
    item(key = "invitations_heading") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Invitations", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            SriSuButton("Refresh", invitations::refresh, variant = SriSuButtonVariant.Ghost,
                enabled = refresh !is LoadState.Loading, size = SriSuButtonSize.Small)
        }
    }
    if (refresh is LoadState.Loading && invitations.itemCount == 0) {
        item(key = "invitations_loading") { PartnerProgress("Loading invitations…") }
    } else if (refresh is LoadState.Error) {
        item(key = "invitations_error") {
            PartnerMessage(refresh.error.message ?: "Couldn’t load invitations.", true, "Retry", invitations::retry)
        }
    } else if (invitations.itemCount == 0) {
        item(key = "invitations_empty") {
            PartnerMessage("No invitations yet. When your partner invites you, you’ll see it here.")
        }
    }
    items(count = invitations.itemCount,
        key = { index -> invitations.peek(index)?.id?.let { "invitation_$it" } ?: "invitation_index_$index" },
        contentType = { "partner_invitation" }) { index ->
        val invitation = invitations[index] ?: return@items
        PartnerInvitationCard(invitation, state, onRespond, onProfile)
    }
    when (val append = invitations.loadState.append) {
        is LoadState.Loading -> item(key = "invitations_append_loading") { PartnerProgress("Loading more invitations…") }
        is LoadState.Error -> item(key = "invitations_append_error") {
            PartnerMessage(append.error.message ?: "Couldn’t load more invitations.", true, "Retry", invitations::retry)
        }
        is LoadState.NotLoading -> if (append.endOfPaginationReached && invitations.itemCount > 0) {
            item(key = "invitations_end") {
                Text("You’re all caught up.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartnerInvitationCard(
    invitation: CoupleConnectionRequestResponse.Result,
    state: FindPartnerState,
    onRespond: (CoupleConnectionRequestResponse.Result, String) -> Unit,
    onProfile: (CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
) {
    val partner = invitation.receiver
    val busy = invitation.id in state.updatingRequestIds
    Card(onClick = { onProfile(partner) }, enabled = partner != null, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.compact),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            PartnerIdentity(
                name = partner?.fullName?.takeIf(String::isNotBlank) ?: partner?.username ?: "Your partner",
                detail = partner?.phoneNumber ?: invitation.senderNumber.orEmpty(),
                photoUrl = partner?.profilePhoto,
            )
            if (busy) PartnerProgress("Updating invitation…")
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                val enabled = invitation.id != null && state.updatingRequestIds.isEmpty() &&
                    state.acceptedPartnerName == null && !state.isSendingInvitation && !state.isCancellingInvitation
                SriSuButton("Accept", { onRespond(invitation, Constants.ConnectionStatus.ACCEPTED) },
                    enabled = enabled, size = SriSuButtonSize.Small)
                SriSuButton("Decline", { onRespond(invitation, Constants.ConnectionStatus.REJECTED) },
                    enabled = enabled, variant = SriSuButtonVariant.Outline, size = SriSuButtonSize.Small)
            }
            state.requestErrors[invitation.id]?.let {
                PartnerMessage("$it Try Accept or Decline again.", isError = true)
            }
        }
    }
}
