package com.srisu.srisu.features.suggestions.state

import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class SuggestionUIStates(
    var suggestions: Flow<PagingData<UserSuggestionResponse.Result>>? = null,
    val cities: MutableStateFlow<List<String?>?> = MutableStateFlow(null),
    val baseUIState: BaseUIState = BaseUIState.Idle,
)