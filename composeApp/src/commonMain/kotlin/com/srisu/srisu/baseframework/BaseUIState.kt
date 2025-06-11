package com.srisu.srisu.baseframework

sealed class BaseUIState {
    data object Idle : BaseUIState()
    data object Loading : BaseUIState()
    data class Success<T>(val data: T) : BaseUIState()
    data class Error(val errorType: String, val message: String) : BaseUIState()
    data class NoInternetConnection(val isOffline: Boolean) : BaseUIState()

}