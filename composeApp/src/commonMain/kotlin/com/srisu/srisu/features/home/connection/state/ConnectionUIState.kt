package com.srisu.srisu.features.home.connection.state

data class ConnectionUIState(
    val connectionTabList: List<Tab> = emptyList(),
    val currentTab: Tab? = null
) {
    data class Tab(
        val title: String
    )
}
