package com.srisu.srisu.features.home.home.state

import org.jetbrains.compose.resources.DrawableResource

data class HomeUiState(
    val navigationList: List<NavigationItem> = listOf<NavigationItem>(),
    val isEngaged: Boolean = false
) {
    data class NavigationItem(
        val route: String,
        val icon: DrawableResource,
        val title: String
    )
}
