package com.srisu.srisu.features.home.couple.presentation.state

data class CoupleProfileScreenState(
    val profile: CoupleProfileUiState? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isMissing: Boolean = false,
    val errorTitle: String? = null,
    val errorMessage: String? = null,
)
