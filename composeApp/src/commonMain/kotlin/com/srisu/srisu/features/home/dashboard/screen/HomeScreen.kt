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
)

@Composable
fun HomeScreen(actions: HomeActions, modifier: Modifier = Modifier, viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MomentLifecycle(viewModel::visible)
    HomeContent(state, viewModel::image, viewModel::refresh, actions, modifier)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    load: HomeImageLoader,
    onRetry: () -> Unit,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val space = MaterialTheme.spacing
            LazyColumn(Modifier.widthIn(max = HomeTokens.contentMaxWidth).fillMaxSize().statusBarsPadding(),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(start = space.gutter, end = space.gutter, top = space.large, bottom = space.extraLarge),
                verticalArrangement = Arrangement.spacedBy(space.section)) {
                val couple = state.couple
                item(key = "header", contentType = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(space.section)) {
                        HomeHeader(state.daypart, state.name, couple?.partner?.name, couple?.daysTogether,
                            hasAlerts = (couple?.pendingInvitations ?: 0) > 0, onCoupleProfile = actions.onCoupleProfile,
                            onNotifications = if (couple == null) actions.onInvitations else actions.onNotifications)
                        NotificationRegistrationTools()
                    }
                }
                if (state.refreshing) item(key = "refreshing", contentType = "progress") {
                    val label = stringResource(Res.string.home_refreshing)
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = label })
                }
                if (state.stale) item(key = "stale", contentType = "error") {
                    HomeErrorCard(stringResource(Res.string.home_stale), onRetry)
                }
                when {
                    state.status == HomeStatus.LOADING -> homeSkeleton()
                    state.status == HomeStatus.FAILED -> item(key = "failed", contentType = "error") {
                        HomeErrorCard(stringResource(Res.string.home_failed), onRetry)
                    }
                    couple == null -> item(key = "link", contentType = "link") {
                        HomeLinkPartnerCard(actions.onFindPartner, actions.onInvitations)
                    }
                    else -> coupleSections(couple, load, onRetry, actions)
                }
            }
        }
    }
}

private fun LazyListScope.coupleSections(couple: HomeCoupleUi, load: HomeImageLoader, onRetry: () -> Unit, actions: HomeActions) {
    item(key = "moments", contentType = "moments") {
        HomeMomentsRow(couple.moments, couple.momentsFresh, load,
            onOpen = { person -> actions.onViewMoments(couple.id, person) }, onCreate = { actions.onCreateMoment(couple.id) })
    }
    item(key = "conversation", contentType = "conversation") {
        val partner = couple.partner.name.ifBlank { stringResource(Res.string.home_partner_fallback_title) }
        HomeSection(stringResource(Res.string.home_from, partner), stringResource(Res.string.home_open_chat), actions.onChat) {
            HomeConversationPanel(couple.partner, couple.conversation, load, actions.onChat, actions.onSpark, actions.onAsk, actions.onPlanner)
        }
    }
    item(key = "challenge", contentType = "challenge") {
        HomeSection(stringResource(Res.string.home_todays_challenge)) {
            HomeChallengeSection(couple.challenge, load, actions.onChallenge, onRetry)
        }
    }
    item(key = "plan", contentType = "plan") {
        HomeSection(stringResource(Res.string.home_coming_up), stringResource(Res.string.home_view_all), { actions.onPlan(null) }) {
            HomePlanSection(couple.plan, actions.onPlan, onRetry)
        }
    }
}

private fun LazyListScope.homeSkeleton() {
    items(count = SKELETON_ROWS, key = { "skeleton-$it" }, contentType = { "skeleton" }) { HomePlaceholderCard() }
}

@Composable
private fun HomeSection(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
        HomeSectionHeader(title, actionLabel = actionLabel, onAction = onAction)
        content()
    }
}

private const val SKELETON_ROWS = 3
