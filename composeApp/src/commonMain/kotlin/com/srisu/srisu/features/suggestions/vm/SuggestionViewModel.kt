package com.srisu.srisu.features.suggestions.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.dto.couple.CoupleConnectionDTO
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.network.BasePagingSource
import com.srisu.srisu.core.data.repository.suggestion.SuggestionRepository
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.suggestions.state.SuggestionUIStates
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionUtils
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Country
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.get

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
        val session = SessionUtils().getSession()
        updateSession(session = session)
        updateSelectedCountry(
            country = CountryModel(
                name = session?.country,
                prefix = null,
                code = null
            )
        )
        getCityList(
            session?.country?.lowercase()
        )
        updateSelectedZodiac(
            zodiac = ZodiacUtils.getZodiacFromName(session?.zodiacSign)
        )
    }

    private fun success(message: String = "") {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(baseUIState = BaseUIState.Success(message))
    }

    private fun showLoading() {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(baseUIState = BaseUIState.Loading)
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

    fun updateSession(session: Session?) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(session = MutableStateFlow(session))
    }

    fun updateCities(cities: List<String?>?) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(cities = MutableStateFlow(cities))
    }

    fun updateMinAge(age: Int) {
        if (age >= MIN_AGE) {
            _suggestionUIStates.value =
                _suggestionUIStates.value.copy(minAge = MutableStateFlow(age))
        }
    }

    fun updateMaxAge(age: Int) {
//        if (age <= MAX_AGE) {
            _suggestionUIStates.value =
                _suggestionUIStates.value.copy(maxAge = MutableStateFlow(age))
//        }
    }

    fun updateSelectedCity(city: String) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(selectedCity = MutableStateFlow(city))
    }

    fun updateSelectedZodiac(zodiac: ZodiacSign?) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(selectedZodiac = MutableStateFlow(zodiac))
    }

    fun updateSelectedCountry(country: CountryModel) {
        _suggestionUIStates.value =
            _suggestionUIStates.value.copy(selectedCountry = MutableStateFlow(country))
    }


    fun getUserSuggestions() {

        showLoading()

        val pagerFlow = Pager(
            config = PagingConfig(pageSize = 20, prefetchDistance = 15, enablePlaceholders = false),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    val resultHandler =
                        suggestionRepository.getUserSuggestions(pageSize = 20, page = page)

                    var items: List<UserSuggestionResponse.Result?>? = emptyList()

                    resultHandler.onSuccess { response, _ ->
                        items = response?.results
                        success()
                    }.onError { error, errorType ->
                        idleScreen()
                        throw Exception("API Error: $error, Type: $errorType")
                    }

                    items
                }
            }).flow.cachedIn(viewModelScope)

        _suggestionUIStates.value = _suggestionUIStates.value.copy(suggestions = pagerFlow)
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
            val loveRequests = suggestionRepository.getLoveRequests().flow.cachedIn(viewModelScope)
        }
    }

    fun getCityList(country: String?) {
        viewModelScope.launch {
            val cities = suggestionRepository.getCityList(country)

            if (cities?.error == false) {
                updateCities(cities = cities.data)
            }
        }
    }


}