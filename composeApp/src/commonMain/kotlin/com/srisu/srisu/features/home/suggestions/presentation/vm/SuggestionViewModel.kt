package com.srisu.srisu.features.home.suggestions.presentation.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.home.suggestions.data.dto.UserPreferenceDTO
import com.srisu.srisu.core.data.remote.BasePagingSource
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.home.connection.coupleconnection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.suggestions.data.response.UserPreferenceResponse
import com.srisu.srisu.features.home.suggestions.data.response.UserSuggestionResponse
import com.srisu.srisu.features.home.suggestions.domain.repository.SuggestionRepository
import com.srisu.srisu.features.home.suggestions.presentation.state.SuggestionUIStates
import com.srisu.srisu.core.session.SessionUtils
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Country.getAllCountriesFromJson
import com.srisu.srisu.utils.Country.getCountryModelFromName
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This VM will be used by both SuggestionScreen and FilterSuggestionScreen
 * */

class SuggestionViewModel(
    private val suggestionRepository: SuggestionRepository,
    private val connectionRepository: ConnectionRepository,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    companion object {
        const val MAX_AGE = 35
        const val MIN_AGE = 16
        private const val PAGE_SIZE = 20
    }

    private val _suggestionUIStates = MutableStateFlow(SuggestionUIStates())
    val suggestionUIStates = _suggestionUIStates.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        setSession()
        getUserSuggestions()
        viewModelScope.launch {
            loadAllCountries()
        }
    }

    private fun updateState(transform: (SuggestionUIStates) -> SuggestionUIStates) {
        _suggestionUIStates.update(transform)
    }

    private fun setBaseUiState(state: BaseUIState) {
        updateState { it.copy(baseUIState = state) }
    }

    private fun success(message: String = "") {
        setBaseUiState(BaseUIState.Success(message))
    }

    private fun showLoading() {
        setBaseUiState(BaseUIState.Loading)
    }

    private fun <T> showSuccessMessage(data: T? = null, message: String) {
        setBaseUiState(
            BaseUIState.Success(
                data = data,
                message = message
            )
        )
    }

    private fun showErrorMessage(errorType: String?, message: String?) {
        setBaseUiState(
            BaseUIState.Error(
                errorType = errorType,
                message = message
            )
        )
    }

    fun idleScreen() {
        setBaseUiState(BaseUIState.Idle)
    }

    private fun showNoInternetConnection(isOffline: Boolean) {
        setBaseUiState(BaseUIState.NoInternetConnection(isOffline = isOffline))
    }

    private fun isInternetAvailable(): Boolean {
        return connectivityObserver.isConnected.value
    }

    private fun setSession() {
        val session = SessionUtils().getSession()
        AppLogger.log("Setting Session = ${session}")
        updateState { it.copy(session = session) }
    }

    private fun updateCities(cities: List<String?>?) {
        updateState { it.copy(cities = cities) }
    }

    private fun updateUserPreferencesState(userPreferenceResponse: UserPreferenceResponse?) {
        updateState { it.copy(userPreferences = userPreferenceResponse) }
    }

    private fun updateSuggestionProfileData(
        suggestionProfileData: UserSuggestionResponse.Result?
    ) {
        updateState { current ->
            current.copy(
                suggestionProfileData = suggestionProfileData,
                isRequested = suggestionProfileData?.hasActiveConnection == true
            )
        }
    }

    private fun addRequestedUsers(userId: Int?) {
        if (userId == null) return

        updateState { current ->
            val updatedRequestedUsers = current.requestedUsers.toMutableSet().apply {
                add(userId)
            }
            current.copy(
                requestedUsers = updatedRequestedUsers,
                isRequested = current.suggestionProfileData?.id in updatedRequestedUsers
            )
        }
    }

    fun isRequested() {
        val userId = suggestionUIStates.value.suggestionProfileData?.id
        updateState { current ->
            current.copy(
                isRequested = current.requestedUsers.contains(userId)
            )
        }
    }

    fun updateMinAge(age: Int) {
        val current = suggestionUIStates.value
        val maxAge = current.maxAge

        if (age <= maxAge && age in MIN_AGE..maxAge) {
            updateState { it.copy(minAge = age) }
        }
    }

    fun updateMaxAge(age: Int) {
        val current = suggestionUIStates.value
        val minAge = current.minAge

        if (age >= minAge && age in minAge..MAX_AGE) {
            updateState { it.copy(maxAge = age) }
        }
    }

    fun updateSelectedCity(city: String?) {
        updateState { it.copy(selectedCity = city) }
    }

    fun updateSelectedZodiac(zodiac: ZodiacSign?) {
        updateState { it.copy(selectedZodiac = zodiac) }
    }

    fun updateSelectedCountry(country: CountryModel?) {
        updateState { it.copy(selectedCountry = country) }
    }

    fun clearFilters() {
        updateState {
            it.copy(
                minAge = MIN_AGE,
                maxAge = MAX_AGE,
                selectedCountry = null,
                selectedCity = null,
                selectedZodiac = null
            )
        }
    }

    private suspend fun loadAllCountries() {
        val countries = withContext(Dispatchers.IO) {
            getAllCountriesFromJson() ?: emptyList()
        }

        updateState { it.copy(countryList = countries) }
    }

    private fun setUserPreferencesData() {
        val current = suggestionUIStates.value
        val session = current.session
        val userPreferences = current.userPreferences

        val minAge = userPreferences?.minAge ?: current.minAge
        val maxAge = userPreferences?.maxAge ?: current.maxAge
        val country = getCountryModelFromName(userPreferences?.country ?: session?.country)
        val city = userPreferences?.city ?: ""
        val zodiac = ZodiacUtils.getZodiacFromName(userPreferences?.zodiac_sign ?: "")

        updateState {
            it.copy(
                minAge = minAge,
                maxAge = maxAge,
                selectedCountry = country,
                selectedCity = city,
                selectedZodiac = zodiac
            )
        }
    }

    fun setSuggestionProfileData(profileData: UserSuggestionResponse.Result?) {
        updateSuggestionProfileData(profileData)
    }

    fun getUserSuggestions() {
        val pagerFlow = Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    val resultHandler = suggestionRepository.getUserSuggestions(
                        pageSize = PAGE_SIZE,
                        page = page
                    )

                    var items: List<UserSuggestionResponse.Result?>? = emptyList()

                    resultHandler.onSuccess { response, _ ->
                        items = response?.results
                        idleScreen()
                    }.onError { error, errorType ->
                        idleScreen()
                        throw Exception("API Error: $error, Type: $errorType")
                    }

                    items
                }
            }
        ).flow.cachedIn(viewModelScope)

        updateState { it.copy(suggestions = pagerFlow) }
    }

    fun getSuggestionProfile(offlineProfileData: UserSuggestionResponse.Result? = null) {
        viewModelScope.launch {
            suggestionRepository.getSuggestionProfile(userId = offlineProfileData?.id)
                .onSuccess { result, _ ->
                    AppLogger.log("Suggestion Profile fetch success = $result")
                    updateSuggestionProfileData(result)
                    AppLogger.log("Suggestion Profile fetch success Suggestion profile data= ${suggestionUIStates.value.suggestionProfileData}")

                }.onError { error, type ->
                    AppLogger.log("Suggestion Profile fetch error = $error $type")
                }
        }
    }

    fun getPreferences() {
        viewModelScope.launch {
            suggestionRepository.getUserPreferences()
                .onSuccess { response, _ ->
                    updateUserPreferencesState(response)
                }
                .onError { _, _ ->
                    idleScreen()
                }

            setUserPreferencesData()
            getCityList()
        }
    }

    fun setUserPreferences(onPreferencesSuccess: () -> Unit) {
        viewModelScope.launch {
            val userPreferenceDTO = buildUserPreferenceDto(isClear = false)

            suggestionRepository.setUserPreferences(userPreferenceDTO = userPreferenceDTO)
                .onSuccess { response, _ ->
                    updateUserPreferencesState(response)
                    onPreferencesSuccess()
                }
                .onError { error, errorType ->
                    showErrorMessage(
                        errorType = errorType.name.uppercase(),
                        message = error.toString()
                    )
                }
        }
    }

    fun updateUserPreferences(
        isClear: Boolean = false,
        onPreferencesSuccess: () -> Unit
    ) {
        showLoading()

        viewModelScope.launch {
            val userPreferenceDTO = buildUserPreferenceDto(isClear = isClear)
            val prefId = suggestionUIStates.value.userPreferences?.id

            suggestionRepository.updateUserPreferences(
                userPreferenceDTO = userPreferenceDTO,
                prefId = prefId
            ).onSuccess { response, _ ->
                updateUserPreferencesState(response)
                if (isClear) {
                    clearFilters()
                } else {
                    setUserPreferencesData()
                }
                idleScreen()
                onPreferencesSuccess()
            }.onError { error, errorType ->
                showErrorMessage(
                    errorType = errorType.name.uppercase(),
                    message = error.toString()
                )
            }
        }
    }

    private fun buildUserPreferenceDto(isClear: Boolean): UserPreferenceDTO {
        val current = suggestionUIStates.value
        val sessionId = current.session?.id

        return if (isClear) {
            UserPreferenceDTO(
                user = sessionId,
                minAge = MIN_AGE,
                maxAge = MAX_AGE,
                city = null,
                country = null,
                zodiacSign = null,
            )
        } else {
            UserPreferenceDTO(
                user = sessionId,
                city = current.selectedCity,
                country = current.selectedCountry?.name,
                zodiacSign = current.selectedZodiac?.sign?.uppercase(),
                minAge = current.minAge,
                maxAge = current.maxAge
            )
        }
    }

    fun sendSingleConnectionRequest() {
        showLoading()

        viewModelScope.launch {
            val myPhoneNumber = suggestionUIStates.value.session?.phoneNumber
                ?: SessionUtils().getPhoneNumber()
            val receiverNumber = suggestionUIStates.value.suggestionProfileData?.phoneNumber

            connectionRepository.sendSingleConnectionRequest(
                senderNumber = myPhoneNumber,
                receiverNumber = receiverNumber
            ).onSuccess { response, message ->
                addRequestedUsers(userId = suggestionUIStates.value.suggestionProfileData?.id)

                val updatedProfileData = suggestionUIStates.value.suggestionProfileData
                    ?.copy(hasActiveConnection = true)

                updateSuggestionProfileData(updatedProfileData)

                showSuccessMessage(
                    data = response,
                    message = message ?: "Request sent successfully"
                )
            }.onError { error, errorType ->
                showErrorMessage(
                    errorType = errorType.name,
                    message = error
                )
            }
        }
    }

    fun getCityList(country: String? = null, showLoading: Boolean = false) {
        if (!isInternetAvailable()) {
            showNoInternetConnection(isOffline = true)
            return
        }

        if (showLoading) {
            showLoading()
        }

        val selectedCountry = suggestionUIStates.value.userPreferences?.country
            ?: country
            ?: suggestionUIStates.value.session?.country

        viewModelScope.launch {
            try {
                val cities = suggestionRepository.getCityList(country = selectedCountry)
                if (cities?.error == false) {
                    updateCities(cities = cities.data)
                }
                idleScreen()
            } catch (_: UnresolvedAddressException) {
                showNoInternetConnection(isOffline = true)
            }
        }
    }
}
