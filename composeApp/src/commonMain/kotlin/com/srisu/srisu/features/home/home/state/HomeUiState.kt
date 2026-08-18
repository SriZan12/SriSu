package com.srisu.srisu.features.home.home.state

import com.srisu.srisu.core.session.Session
import org.jetbrains.compose.resources.DrawableResource

data class HomeUiState(
    val navigationList: List<NavigationItem> = listOf<NavigationItem>(),
    val session: Session? = null,
    val isEngaged: Boolean = false
) {
    data class NavigationItem(
        val route: String,
        val icon: DrawableResource,
        val title: String
    )
}