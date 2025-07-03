package com.srisu.srisu.features.suggestions.state

import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.response.suggestion.UserPreferenceResponse
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel.Companion.MAX_AGE
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel.Companion.MIN_AGE
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import kotlinx.coroutines.flow.Flow

data class SuggestionUIStates(
    //Used in Suggestion Screen
    var suggestions: Flow<PagingData<UserSuggestionResponse.Result>>? = null,
    val session: Session? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle,

    val suggestionProfileData: UserSuggestionResponse.Result? = null,
    val requestedUsers: HashSet<Int> = hashSetOf(),
    val isRequested: Boolean = false,

    // Used in Suggestion Filter UI
    val cities: List<String?>? = null,
    val selectedCity: String? = "Select a city",
    val minAge: Int = MIN_AGE,
    val maxAge: Int = MAX_AGE,
    val selectedZodiac: ZodiacSign? = null,
    val selectedCountry: CountryModel? = null,
    val userPreferences: UserPreferenceResponse? = null
)