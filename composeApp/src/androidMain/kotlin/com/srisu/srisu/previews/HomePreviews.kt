package com.srisu.srisu.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.srisu.srisu.features.home.dashboard.screen.HomeActions
import com.srisu.srisu.features.home.dashboard.screen.HomeContent
import com.srisu.srisu.features.home.dashboard.state.*
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.datetime.LocalDateTime

/** Synthetic preview fixtures. Conversation, presence and photo thumbnails have no live source yet. */
object HomePreviewData {
    private val me = HomePersonUi(1, "Salil", null)
    private val partner = HomePersonUi(2, "Aayush", null)
    val couple = HomeCoupleUi(
        id = 10,
        partner = partner,
        daysTogether = 248,
        moments = listOf(HomeMomentUi(me, own = true, activeCount = 1, unseenCount = 0),
            HomeMomentUi(partner, own = false, activeCount = 2, unseenCount = 2)),
        momentsFresh = true,
        conversation = HomeConversationUi("found the trail we talked about 👀 sunday??", "12:40", unread = 3,
            partnerOnline = true, photoUrls = listOf("preview://wood", "preview://sunrise")),
        challenge = HomeSection.Ready(HomeChallengeUi("c1", "Cook something new together", HomeChallengeStatus.ONE_CONFIRMED,
            listOf(HomeChallengeMemberUi(me, confirmed = true), HomeChallengeMemberUi(partner, confirmed = false)))),
        plan = HomeSection.Ready(HomePlanUi(7, "Sunrise hike to Nagarkot", LocalDateTime(2026, 9, 20, 5, 30), HomePlanStatus.BOTH_GOING)),
        pendingInvitations = 1,
    )
    val content = HomeUiState(HomeStatus.READY, HomeDaypart.EVENING, "Salil", couple)
    val loading = HomeUiState(HomeStatus.LOADING, HomeDaypart.EVENING, "Salil")
    val empty = HomeUiState(HomeStatus.READY, HomeDaypart.MORNING, "Salil", couple = null)
    val failed = HomeUiState(HomeStatus.FAILED, HomeDaypart.AFTERNOON, "Salil")
    val sectionsEmpty = content.copy(couple = couple.copy(conversation = null, challenge = HomeSection.Empty,
        plan = HomeSection.Failed, pendingInvitations = 0))
}

@Composable
private fun HomePreview(state: HomeUiState, dark: Boolean = false) {
    SriSuTheme(darkTheme = dark) { HomeContent(state, { null }, {}, HomeActions()) }
}

@Preview(name = "Home · Content · Light", showBackground = true, heightDp = 1100)
@Composable
private fun HomeContentLightPreview() = HomePreview(HomePreviewData.content)

@Preview(name = "Home · Content · Dark", showBackground = true, heightDp = 1100)
@Composable
private fun HomeContentDarkPreview() = HomePreview(HomePreviewData.content, dark = true)

@Preview(name = "Home · Loading · Light", showBackground = true)
@Composable
private fun HomeLoadingLightPreview() = HomePreview(HomePreviewData.loading)

@Preview(name = "Home · Loading · Dark", showBackground = true)
@Composable
private fun HomeLoadingDarkPreview() = HomePreview(HomePreviewData.loading, dark = true)

@Preview(name = "Home · Empty (no partner) · Light", showBackground = true)
@Composable
private fun HomeEmptyLightPreview() = HomePreview(HomePreviewData.empty)

@Preview(name = "Home · Empty (no partner) · Dark", showBackground = true)
@Composable
private fun HomeEmptyDarkPreview() = HomePreview(HomePreviewData.empty, dark = true)

@Preview(name = "Home · Empty sections · Light", showBackground = true, heightDp = 1100)
@Composable
private fun HomeSectionsEmptyPreview() = HomePreview(HomePreviewData.sectionsEmpty)

@Preview(name = "Home · Error · Light", showBackground = true)
@Composable
private fun HomeErrorPreview() = HomePreview(HomePreviewData.failed)

@Preview(name = "Home · Large text", showBackground = true, fontScale = 1.5f, heightDp = 1400)
@Composable
private fun HomeLargeTextPreview() = HomePreview(HomePreviewData.content)
