package com.srisu.srisu.features.suggestions.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.dto.couple.CoupleConnectionDTO
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.dto.suggestion.UserPreferenceDTO
import com.srisu.srisu.core.data.network.BasePagingSource
import com.srisu.srisu.core.data.repository.suggestion.SuggestionRepository
import com.srisu.srisu.core.data.response.suggestion.UserPreferenceResponse
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.suggestions.state.SuggestionUIStates
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionUtils
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Country.getAllCountriesFromJson
import com.srisu.srisu.utils.Country.getCountryModelFromName
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * This VM will be used by both SuggestionScreen and FilterSuggestionScreen
 * */

class SuggestionViewModel(
    private val suggestionRepository: SuggestionRepository,
    private val connectivityObserver: ConnectivityObserver,

    ) : ViewModel() {

    companion object {
        const val MAX_AGE = 35
        const val MIN_AGE = 16
    }

    private val _suggestionUIStates: MutableStateFlow<SuggestionUIStates> = MutableStateFlow(
        SuggestionUIStates()
    )
    val suggestionUIStates = _suggestionUIStates.asStateFlow()

    init {
        AppLogger.log("SUGGESTION VIEW MODEL INITIALIZED")
        setSession()
        getUserSuggestions()
        loadAllCountries()
    }

    private fun success(message: String = "") {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(baseUIState = BaseUIState.Success(message))
    }

    private fun showLoading() {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(baseUIState = BaseUIState.Loading)
    }

    private fun <T> showSuccessMessage(data: T? = null, message: String) {
        this._suggestionUIStates.value =
            this._suggestionUIStates.value.copy(
                baseUIState = BaseUIState.Success(
                    data = data,
                    message = message
                )
            )
    }

    private fun showErrorMessage(errorType: String?, message: String?) {
        this._suggestionUIStates.value =
            this._suggestionUIStates.value.copy(
                baseUIState = BaseUIState.Error(
                    errorType = errorType,
                    message = message
                )
            )
    }

    fun idleScreen() {
        _suggestionUIStates.value = _suggestionUIStates.value.copy(baseUIState = BaseUIState.Idle)
    }

    private fun showNoInternetConnection(isOffline: Boolean) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(baseUIState = BaseUIState.NoInternetConnection(isOffline = isOffline))
    }

    private fun isInternetAvailable(): Boolean {
        return connectivityObserver.isConnected.value
    }

    private fun updateSession(session: Session?) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(session = session)
    }

    private fun updateCities(cities: List<String?>?) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(cities = cities)
    }

    fun updateMinAge(age: Int) {
        val maxAge = _suggestionUIStates.value.maxAge
        if (age <= maxAge && age in MIN_AGE..maxAge) {
            _suggestionUIStates.value =
                _suggestionUIStates.value.copy(minAge = age)
        }
    }

    fun updateMaxAge(age: Int) {
        val minAge = _suggestionUIStates.value.minAge
        if (age >= minAge && age in minAge..MAX_AGE) {
            _suggestionUIStates.value =
                _suggestionUIStates.value.copy(maxAge = age)
        }
    }

    fun updateSelectedCity(city: String?) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(selectedCity = city)
    }

    fun updateSelectedZodiac(zodiac: ZodiacSign?) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(selectedZodiac = zodiac)
    }

    fun updateSelectedCountry(country: CountryModel?) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(selectedCountry = country)
    }

    private fun updateUserPreferences(userPreferenceResponse: UserPreferenceResponse?) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(userPreferences = userPreferenceResponse)
    }

    private fun addRequestedUsers(userId: Int?) {
        if (userId != null) {
            _suggestionUIStates.value.requestedUsers.add(userId)
        }
    }

    fun isRequested() {
        val userId = _suggestionUIStates.value.suggestionProfileData?.id
        val isUserRequested = _suggestionUIStates.value.requestedUsers.contains(userId)

        _suggestionUIStates.value = _suggestionUIStates.value.copy(
            isRequested = isUserRequested
        )
    }

    private fun loadAllCountries() {
        viewModelScope.launch {
            val countries = getAllCountriesFromJson() ?: emptyList()
            _suggestionUIStates.value = _suggestionUIStates.value.copy(countryList = countries)
        }
    }


    fun clearFilters() {
        updateMinAge(age = MIN_AGE)
        updateMaxAge(age = MAX_AGE)
        updateSelectedCountry(country = null)
        updateSelectedCity(null)
        updateSelectedZodiac(null)
    }

    private fun setSession() {
        val session = SessionUtils().getSession()
        updateSession(session = session)
    }

    private fun setUserPreferencesData() {
        val session = suggestionUIStates.value.session
        val userPreferences = suggestionUIStates.value.userPreferences

        //set min and max age.
        val minAge = userPreferences?.minAge ?: suggestionUIStates.value.minAge
        val maxAge = userPreferences?.maxAge ?: suggestionUIStates.value.maxAge
        updateMinAge(age = minAge)
        updateMaxAge(age = maxAge)

        // set country
        val country = getCountryModelFromName(userPreferences?.country ?: session?.country)
        updateSelectedCountry(country = country)

        //set city
        val city = userPreferences?.city ?: ""
        updateSelectedCity(city = city)

        // set zodiac sign.
        val zodiac =
            ZodiacUtils.getZodiacFromName(userPreferences?.zodiac_sign ?: "")
        updateSelectedZodiac(zodiac = zodiac)
    }

    fun setSuggestionProfileData(suggestionProfileData: String?) {
        val profileData =
            suggestionProfileData?.let {
                Json.decodeFromString<UserSuggestionResponse.Result?>(
                    it
                )
            }
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(suggestionProfileData = profileData)

        isRequested()
    }

    private fun updateSuggestionProfileData(
        suggestionProfileData: UserSuggestionResponse.Result?
    ) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(suggestionProfileData = suggestionProfileData)
    }


    fun getUserSuggestions() {

        showLoading()

        AppLogger.log("INSIDE SUGGESTION API CALL")

        val pagerFlow = Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    val resultHandler =
                        suggestionRepository.getUserSuggestions(pageSize = 20, page = page)

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
            }).flow.cachedIn(viewModelScope)

        _suggestionUIStates.value = _suggestionUIStates.value.copy(suggestions = pagerFlow)
    }

    fun getPreferences() {
        viewModelScope.launch {
            suggestionRepository.getUserPreferences().onSuccess { response, _ ->
                updateUserPreferences(response)
            }.onError { _, _ ->
                idleScreen()
            }

            setUserPreferencesData()
            getCityList()

        }
    }

    fun setUserPreferences(onPreferencesSuccess: () -> Unit) {
        viewModelScope.launch {
            val userPreferenceDTO = UserPreferenceDTO(
                user = suggestionUIStates.value.session?.id,
                city = suggestionUIStates.value.selectedCity,
                country = suggestionUIStates.value.selectedCountry?.name,
                zodiacSign = suggestionUIStates.value.selectedZodiac?.sign?.uppercase(),
                minAge = suggestionUIStates.value.minAge,
                maxAge = suggestionUIStates.value.maxAge
            )

            suggestionRepository.setUserPreferences(userPreferenceDTO = userPreferenceDTO)
                .onSuccess { response, _ ->
                    updateUserPreferences(userPreferenceResponse = response)
                    onPreferencesSuccess()
                }.onError { error, errorType ->
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
            val userPreferenceDTO =
                if (!isClear) {
                    UserPreferenceDTO(
                        user = suggestionUIStates.value.session?.id,
                        city = suggestionUIStates.value.selectedCity,
                        country = suggestionUIStates.value.selectedCountry?.name,
                        zodiacSign = suggestionUIStates.value.selectedZodiac?.sign?.uppercase(),
                        minAge = suggestionUIStates.value.minAge,
                        maxAge = suggestionUIStates.value.maxAge
                    )
                } else {
                    UserPreferenceDTO(
                        user = suggestionUIStates.value.session?.id,
                        minAge = MIN_AGE,
                        maxAge = MAX_AGE,
                        city = null,
                        country = null,
                        zodiacSign = null,
                    )
                }

            val prefId = suggestionUIStates.value.userPreferences?.id
            suggestionRepository.updateUserPreferences(
                userPreferenceDTO = userPreferenceDTO,
                prefId = prefId
            )
                .onSuccess { response, _ ->
                    updateUserPreferences(response)
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

    fun sendSingleConnectionRequest() {

        showLoading()

        viewModelScope.launch {
            val myPhoneNumber = SessionUtils().getPhoneNumber()
            val receiverNumber = suggestionUIStates.value.suggestionProfileData?.phoneNumber

            suggestionRepository.sendSingleConnectionRequest(
                senderNumber = myPhoneNumber,
                receiverNumber = receiverNumber
            ).onSuccess { response, message ->

                addRequestedUsers(userId = suggestionUIStates.value.suggestionProfileData?.id)

                showSuccessMessage(
                    data = response,
                    message = message ?: "Request sent successfully"
                )
                val suggestionProfileData =
                    suggestionUIStates.value.suggestionProfileData.apply {
                        this?.crushed = true
                    }
                updateSuggestionProfileData(suggestionProfileData = suggestionProfileData)
            }.onError { error, errorType ->
                showErrorMessage(
                    errorType = errorType.name,
                    message = error
                )
            }
        }

    }


    fun sendCoupleConnectionRequest() {
        viewModelScope.launch {
            suggestionRepository.sendCoupleConnectionRequest(
                senderNumber = "+9779865103764", receiverNumber = "+919720304050"
            ).onSuccess { response, _ ->

                AppLogger.log("COUPLE CONNECTION SUCCESS = $response")
            }.onError { error, errorType ->
                AppLogger.log("COUPLE CONNECTION ERROR = $error")
                AppLogger.log("COUPLE CONNECTION ERROR TYPE = ${errorType.name}")
            }
        }
    }


    private fun updateCoupleConnectionStatus() {
        viewModelScope.launch {
            suggestionRepository.updateCoupleConnectionRequestStatus(
                connectionId = 21,
                coupleConnectionDTO = CoupleConnectionDTO(
                    senderNumber = "+919720304050",
                    receiverNumber = "+9779863938267",
                    connectionStatus = "ACCEPTED"
                ),
            ).onSuccess { response, _ ->

                AppLogger.log("UPDATE SINGLE CONNECTION SUCCESS = $response")
            }.onError { error, errorType ->
                AppLogger.log("UPDATE SINGLE CONNECTION ERROR = $error")
                AppLogger.log("UPDATE SINGLE CONNECTION ERROR TYPE = ${errorType.name}")
            }
        }
    }

    fun updateSingleConnectionStatus() {
        viewModelScope.launch {
            suggestionRepository.updateSingleConnectionRequestStatus(
                connectionId = 4, singleConnectionDTO = SingleConnectionDTO(
                    senderNumber = "+9779865103764",
                    receiverNumber = "+919720304050",
                    connectionStatus = "ACCEPTED"
                )
            ).onSuccess { response, _ ->

                AppLogger.log("UPDATE SINGLE CONNECTION SUCCESS = $response")
            }.onError { error, errorType ->
                AppLogger.log("UPDATE SINGLE CONNECTION ERROR = $error")
                AppLogger.log("UPDATE SINGLE CONNECTION ERROR TYPE = ${errorType.name}")
            }
        }
    }

    fun getSentLoveRequests() {
        viewModelScope.launch {
            val loveRequests =
                suggestionRepository.getSentLoveRequests().flow.cachedIn(viewModelScope)
        }
    }

    fun getSentRequests() {
        viewModelScope.launch {
            val loveRequests =
                suggestionRepository.getLoveRequests().flow.cachedIn(viewModelScope)
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

        val countryCity = suggestionUIStates.value.userPreferences?.country ?: country
        ?: suggestionUIStates.value.session?.country

        viewModelScope.launch {
            try {
                val cities = suggestionRepository.getCityList(country = countryCity)
                if (cities?.error == false) {
                    updateCities(cities = cities.data)
                }
                idleScreen()
            } catch (exception: UnresolvedAddressException) {
                showNoInternetConnection(isOffline = true)
            }


        }
    }


}