package com.srisu.srisu.features.suggestions.state

import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.Country
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class SuggestionUIStates(
    var suggestions: Flow<PagingData<UserSuggestionResponse.Result>>? = null,
    val session: MutableStateFlow<Session?> = MutableStateFlow(null),
    val baseUIState: BaseUIState = BaseUIState.Idle,

    // Suggestion Filter UI States
    val cities: MutableStateFlow<List<String?>?> = MutableStateFlow(null),
    val selectedCity: MutableStateFlow<String?> = MutableStateFlow("Select a city"),
    val minAge: MutableStateFlow<Int> = MutableStateFlow(20),
    val maxAge: MutableStateFlow<Int> = MutableStateFlow(25),
    val selectedZodiac: MutableStateFlow<ZodiacSign?> = MutableStateFlow(null),
    val selectedCountry: MutableStateFlow<CountryModel?> = MutableStateFlow(null),
)