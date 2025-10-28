package com.srisu.srisu.features.home.home.state

import org.jetbrains.compose.resources.DrawableResource

data class HomeState(
    val navigationList: List<NavigationItem> = listOf<NavigationItem>()
) {
    data class NavigationItem(
        val route: String,
        val icon: DrawableResource,
        val title: String
    )
}