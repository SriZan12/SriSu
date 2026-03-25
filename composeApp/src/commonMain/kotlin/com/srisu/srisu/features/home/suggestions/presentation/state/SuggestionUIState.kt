package com.srisu.srisu.features.home.suggestions.presentation.state

import androidx.compose.runtime.Stable
import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.home.suggestions.data.response.UserPreferenceResponse
import com.srisu.srisu.features.home.suggestions.data.response.UserSuggestionResponse
import com.srisu.srisu.features.home.suggestions.presentation.vm.SuggestionViewModel.Companion.MAX_AGE
import com.srisu.srisu.features.home.suggestions.presentation.vm.SuggestionViewModel.Companion.MIN_AGE
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import kotlinx.coroutines.flow.Flow

@Stable
data class SuggestionUIStates(
    //Used in Suggestion Screen
    val suggestions: Flow<PagingData<UserSuggestionResponse.Result>>? = null,
    val session: Session? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle,

    val suggestionProfileData: UserSuggestionResponse.Result? = null,
    val requestedUsers: Set<Int> = emptySet(),
    val isRequested: Boolean = false,

    // Used in Suggestion Filter UI
    val cities: List<String?>? = null,
    val selectedCity: String? = "Select a city",
    val minAge: Int = MIN_AGE,
    val maxAge: Int = MAX_AGE,
    val selectedZodiac: ZodiacSign? = null,
    val selectedCountry: CountryModel? = null,
    val userPreferences: UserPreferenceResponse? = null,
    val countryList: List<CountryModel> = emptyList()
)
