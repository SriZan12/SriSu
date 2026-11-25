package com.srisu.srisu.features.home.dashboard.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.home.dashboard.components.*
import com.srisu.srisu.features.home.dashboard.state.HomeCoupleUi
import com.srisu.srisu.features.home.dashboard.state.HomeStatus
import com.srisu.srisu.features.home.dashboard.state.HomeUiState
import com.srisu.srisu.features.home.dashboard.state.HomeSection
import com.srisu.srisu.core.identity.partnerDisplayName
import com.srisu.srisu.features.home.dashboard.vm.HomeViewModel
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.features.notifications.NotificationRegistrationTools
import com.srisu.srisu.theme.HomeTokens
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.*

/** Navigation callbacks for Home; kept in one stable holder so the content signature stays small. */
@Stable
class HomeActions(
    val onNotifications: () -> Unit = {},
    val onPlanInvitations: () -> Unit = {},
    val onCoupleProfile: () -> Unit = {},
    val onViewMoments: (coupleId: Long, personId: Long) -> Unit = { _, _ -> },
    val onCreateMoment: (coupleId: Long) -> Unit = {},
    val onChat: () -> Unit = {},
    val onSpark: () -> Unit = {},
    val onAsk: () -> Unit = {},
    val onPlanner: () -> Unit = {},
    val onChallenge: (id: String?) -> Unit = {},
    val onPlan: (id: Long?) -> Unit = {},
    val onFindPartner: () -> Unit = {},
    val onInvitations: () -> Unit = {},
    val onQuestion: (String) -> Unit = {},
    val onChatMessage: (String?) -> Unit = {},
    val onDraft: (String) -> Unit = {},
    val onIdeaPlan: (String) -> Unit = {},
    val onEditPlan: (Long) -> Unit = {},
    val onExplore: () -> Unit = {},
    val onSharing: () -> Unit = {},
)

@Composable
fun HomeScreen(actions: HomeActions, modifier: Modifier = Modifier, viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inbox: com.srisu.srisu.features.notifications.NotificationInboxSource = org.koin.compose.koinInject()
    val unread by inbox.unreadCount.collectAsStateWithLifecycle()
    val partnerNames: com.srisu.srisu.core.identity.PartnerNames = org.koin.compose.koinInject()
    MomentLifecycle(viewModel::visible)
    com.srisu.srisu.features.couplechat.presentation.ProtectConversation()
    HomeContent(state.copy(notificationUnreadCount=unread), viewModel::image, {viewModel.refresh();partnerNames.refresh()}, actions, modifier,
        HomeInteractions(viewModel::setStatus, viewModel::suggest, viewModel::ideaAction, viewModel::acceptPlan, viewModel::retryAction, viewModel::reminder),
        onRefresh = {viewModel.refreshFromUser();partnerNames.refresh()})
}

@Composable
fun HomeContent(state: HomeUiState, load: HomeImageLoader, onRetry: () -> Unit, actions: HomeActions,
    modifier: Modifier = Modifier, interactions: HomeInteractions = HomeInteractions(), onRefresh: () -> Unit = onRetry) {
    // Resolve only presentation identities; message text and shared activity content stay original.
    val couple = state.couple?.let { couple ->
        couple.copy(
            partner = couple.partner.copy(name = partnerDisplayName(couple.partner.id, couple.partner.name, couple.id)),
            moments = couple.moments.map { moment -> moment.copy(person = moment.person.copy(
                name = partnerDisplayName(moment.person.id, moment.person.name, couple.id))) },
            challenge = when (val challenge = couple.challenge) {
                is HomeSection.Ready -> HomeSection.Ready(challenge.value.copy(members = challenge.value.members.map { member ->
                    member.copy(person = member.person.copy(name = partnerDisplayName(member.person.id, member.person.name, couple.id)))
                }))
                else -> challenge
            },
        )
    }
    HomeCoupleSpace(state.copy(couple = couple), load, onRetry, actions, interactions, modifier, onRefresh)
}
