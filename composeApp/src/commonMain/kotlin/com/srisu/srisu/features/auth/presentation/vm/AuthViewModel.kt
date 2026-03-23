package com.srisu.srisu.features.auth.presentation.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Uri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.auth.data.remote.dto.AuthDTO
import com.srisu.srisu.features.auth.data.remote.dto.ProfileSetupDTO
import com.srisu.srisu.features.auth.data.local.datastore.AuthDataStore
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.presentation.Components.CustomAuthScreen
import com.srisu.srisu.features.auth.presentation.Components.OTPScreenMetadata
import com.srisu.srisu.features.auth.presentation.screens.Gender
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.state.Validation
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.session.setUserWholeCredentials
import com.srisu.srisu.session.toSession
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Constants.Auth.FULL_NAME_PROGRESS
import com.srisu.srisu.utils.Constants.Auth.OTP_WAITING_TIME
import com.srisu.srisu.utils.Constants.Auth.PHONE_NUMBER_VERIFICATION_PROGRESS
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import com.srisu.srisu.utils.Constants.Auth.TOTAL_PROGRESS
import com.srisu.srisu.utils.Country.getAllCountriesFromJson
import com.srisu.srisu.utils.Country.getCountryModelFromPrefix
import com.srisu.srisu.utils.DateTimeUtils.calculateAge
import com.srisu.srisu.utils.DateTimeUtils.getDayAndMonthIndividually
import com.srisu.srisu.utils.FileManager
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val sessionStorage: SessionStorage,
    private val connectivityObserver: ConnectivityObserver,
    private val dataStoreRepo: AuthDataStore
) : ViewModel() {
    private val _authUiState: MutableStateFlow<AuthUIStates> = MutableStateFlow(AuthUIStates())
    val authUiState = this._authUiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5000
        ),
        initialValue = AuthUIStates()
    )


    init {
        checkSession()
        initializeAuthNavigationFlow()
        setZodiacSign()
        loadAllCountries()
    }

    private fun showErrorMessage(errorType: String = "ERROR", error: String) {
        this._authUiState.value = this._authUiState.value.copy(
            baseUIState = BaseUIState.Error(
                errorType = errorType,
                message = error
            )
        )
    }

    private fun showSuccessMessage(message: String) {
        this._authUiState.value =
            this._authUiState.value.copy(baseUIState = BaseUIState.Success(message))
    }

    private fun showLoading() {
        this._authUiState.value = this._authUiState.value.copy(baseUIState = BaseUIState.Loading)
    }

    fun idleScreen() {
        this._authUiState.value = this._authUiState.value.copy(baseUIState = BaseUIState.Idle)
    }

    private fun showNoInternetConnection(isOffline: Boolean) {
        this._authUiState.value =
            this._authUiState.value.copy(baseUIState = BaseUIState.NoInternetConnection(isOffline = isOffline))
    }

    private fun isInternetAvailable(): Boolean {
        return connectivityObserver.isConnected.value
    }

    //    Events
    private fun checkSession(): Session? {
        val sessionJson = getSession(sessionKey = SESSION_KEY)
        var session: Session? = null
        try {
            session = sessionJson?.let { Json.decodeFromString<Session>(it) }

        } catch (exception: Exception) {
            AppLogger.log("SESSION SERIALIZATION EXCEPTION")
        }

        if (session == null) {
            updateProgress(isIncrease = true)
        } else {
            if (session.isPhoneVerified == true) {
                updateProgress(isIncrease = true, step = FULL_NAME_PROGRESS)
            } else if (session.isPhoneVerified == false) {
                updateProgress(isIncrease = true, step = PHONE_NUMBER_VERIFICATION_PROGRESS)
            }

            updateSession(session = session)
//            updateIsOtpVerified(isPhoneNumberVerified = session.isPhoneVerified == true)
            AppLogger.log("SESSIONS = ${Json.encodeToString(session)}")
        }

        return session
    }

    private fun updateProgress(isIncrease: Boolean, step: Int? = null) {
        val totalSteps = TOTAL_PROGRESS
        val currentStep = this._authUiState.value.currentProgressStep

        val newStep = when {
            isIncrease -> {
                step ?: (currentStep + 1).coerceAtMost(totalSteps)
            }

            else -> (currentStep - 1).coerceAtLeast(1)
        }

        val currentProgress = newStep.toFloat() / totalSteps.toFloat()

        this._authUiState.value = this._authUiState.value.copy(
            currentProgressStep = newStep,
            progress = currentProgress
        )
    }

    fun updatePhoneNumber(phoneNumber: String, showValidationMessage: () -> Unit) {
        if (phoneNumber.length <= 10) {
            this._authUiState.value = this._authUiState.value.copy(phoneNumber = phoneNumber)
        } else {
            showValidationMessage()
        }
    }

    fun updateCountry(code: String, prefix: String) {
        this._authUiState.value =
            this._authUiState.value.copy(countryCode = code, countryPrefix = prefix)
    }


    fun updateOtpValues(index: Int, value: String) {
        val newOtpValues = this._authUiState.value.optValues.toMutableList()
        newOtpValues[index] = value
        this._authUiState.value = this._authUiState.value.copy(optValues = newOtpValues)

    }

    private fun loadAllCountries() {
        viewModelScope.launch {
            val countries = getAllCountriesFromJson() ?: emptyList()
            _authUiState.value = _authUiState.value.copy(countryList = countries)
        }
    }

    /* private fun updateIsOtpVerified(isPhoneNumberVerified: Boolean) {
         _authUiState.value = _authUiState.value.copy(isPhoneNumberVerified = isPhoneNumberVerified)
     }*/

    fun updateOTPRemainingTime(remainingOTPTimestamp: Long?) {
        this._authUiState.value =
            this._authUiState.value.copy(remainingOTPTimestamp = remainingOTPTimestamp)
    }

    fun updateFullName(name: String) {
        this._authUiState.value = this._authUiState.value.copy(fullName = name)
    }

    fun updateUserName(username: String) {
        this._authUiState.value = this._authUiState.value.copy(username = username)
    }

    fun updateDOB(dob: String) {
        this._authUiState.value = this._authUiState.value.copy(dob = dob)
        updateZodiacSign()
        updateAge(dob = dob)
    }

    private fun updateAge(dob: String) {
        this._authUiState.value = this._authUiState.value.copy(age = calculateAge(dob).toString())
    }

    fun updateProfilePictureUri(uri: Uri?) {
        this._authUiState.value = this._authUiState.value.copy(profilePictureUri = uri)
    }

    /* private fun updateTokens(tokens: OtpVerificationResponse.Tokens?) {
         _authUiState.value = _authUiState.value.copy(tokens = tokens)
     }*/

    private fun updateSession(session: Session?) {
        this._authUiState.value = this._authUiState.value.copy(session = session)
    }

    private fun updateZodiacSign() {
        val dob = this._authUiState.value.dob
        val dateParts = getDayAndMonthIndividually(dateString = dob)
        val month = dateParts.first
        val day = dateParts.second

        val zodiacSign = findZodiacSign(month, day)
        this._authUiState.value = this._authUiState.value.copy(zodiacSign = zodiacSign)

        AppLogger.log("ZODIAC SIGN = ${this._authUiState.value.zodiacSign}")
    }


    fun updateGender(gender: Gender) {
        this._authUiState.value = this._authUiState.value.copy(gender = gender)
    }

    fun updateValidationError(validation: Validation) {
        this._authUiState.value = this._authUiState.value.copy(validationError = validation)
    }


//Api calls and local savings.

    fun requestOTP(isNavigateScreen: Boolean = true) {

        if (!isInternetAvailable()) {
            showNoInternetConnection(isOffline = true)
            return
        }

        viewModelScope.launch {

            showLoading()

            val authDTO = AuthDTO(
                fullName = this@AuthViewModel._authUiState.value.fullName,
                dob = this@AuthViewModel._authUiState.value.dob,
                gender = this@AuthViewModel._authUiState.value.gender.name,
                phoneNumber = "${this@AuthViewModel._authUiState.value.countryPrefix}${this@AuthViewModel._authUiState.value.phoneNumber}",

                )

            val session = Session(isPhoneVerified = false)

            val credentials = Json.encodeToString(session)

            saveSession(credentials = credentials, sessionKey = SESSION_KEY)
            if (isNavigateScreen) {
                navigateNextScreen()
            }
            idleScreen()

            /* authRepository.sendOTPRequest(authDTO = authDTO)
                 .onSuccess { _, _ ->
                     val credentials = setCredentials(
                         tokens = null,
                         isPhoneVerified = false
                     )
                     saveSession(credentials = credentials, sessionKey = SESSION_KEY)
                     if(isNavigateScreen){
                     navigateNextScreen()
                     }
                     idleScreen()
                 }
                 .onError { error, errorType ->
                     AppLogger.log("ERROR API CALL = ${errorType.name}")
                     AppLogger.log("ERROR API CALL = ${error.toString()}")

                     *//*  showErrorMessage(
                          errorType = "${errorType.name} ERROR",
                          error = error.toString()
                      )*//*
                }*/
        }
    }

    @OptIn(ExperimentalTime::class)
    fun saveOTPTimeStamp() {
        viewModelScope.launch {
            if (this@AuthViewModel._authUiState.value.remainingOTPTimestamp == null) {
                dataStoreRepo.saveOTPTimestamp(
                    otpScreenMetadata = OTPScreenMetadata(
                        countryCode = this@AuthViewModel._authUiState.value.countryCode,
                        countryPrefix = this@AuthViewModel._authUiState.value.countryPrefix,
                        phoneNumber = this@AuthViewModel._authUiState.value.phoneNumber,
                        saveTime = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                        totalTime = OTP_WAITING_TIME
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun getRemainingOTPTimeStamp() {
        viewModelScope.launch {
            dataStoreRepo.getOTPTimestamp().collectLatest { otpTimestamp ->
                val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
                val timeRemaining = otpTimestamp?.let {
                    val elapsedTime = currentTime - it.saveTime
                    val remainingTime = (it.totalTime - elapsedTime).coerceAtLeast(0L)
                    remainingTime
                }

                updateOTPRemainingTime(remainingOTPTimestamp = timeRemaining)

                otpTimestamp?.countryCode?.let {
                    updateCountry(
                        code = otpTimestamp.countryCode,
                        prefix = otpTimestamp.countryPrefix
                    )
                }

                otpTimestamp?.phoneNumber?.let {
                    updatePhoneNumber(
                        phoneNumber = otpTimestamp.phoneNumber,
                        showValidationMessage = {})
                }

                if (timeRemaining == null || timeRemaining == 0L) {
                    saveOTPTimeStamp()
                }
            }
        }
    }


    fun verifyOtp(onGoToHomeScreen: () -> Unit) {

        if (!isInternetAvailable()) {
            showNoInternetConnection(isOffline = true)
            return
        }

        viewModelScope.launch {

            showLoading()

            val otpCode = this@AuthViewModel._authUiState.value.optValues.joinToString("")
            val phoneNumber =
                "${this@AuthViewModel._authUiState.value.countryPrefix}${this@AuthViewModel._authUiState.value.phoneNumber}"

            authRepository.sendVerifyOtpRequest(phoneNumber = phoneNumber, otp = otpCode)
                .onSuccess { response, _ ->

                    val session = response?.user?.toSession(
                        access = response.tokens?.access,
                        refresh = response.tokens?.refresh,
                        id = response.user.id
                    )

                    val credentials = Json.encodeToString(session)

                    saveSession(credentials = credentials, sessionKey = SESSION_KEY)
                    dataStoreRepo.deleteOTPTimeStamp()

                    onOtpVerifiedSuccess(
                        isPhoneNumberVerified = response?.user?.isPhoneVerified == true,
                        isProfileCompleted = response?.user?.isProfileComplete == true
                    ) {
                        onGoToHomeScreen()
                    }
//                    updateProgress(isIncrease = true)
                    idleScreen()
                }
                .onError { error, _ ->
                    showErrorMessage(error = error.toString())
                }
        }
    }

    private fun onOtpVerifiedSuccess(
        isPhoneNumberVerified: Boolean,
        isProfileCompleted: Boolean,
        onGoToHomeScreen: () -> Unit
    ) {

        if (isPhoneNumberVerified && isProfileCompleted) {
            onGoToHomeScreen()
        } else {
            if (isPhoneNumberVerified) {
                removeScreen(screen = CustomAuthScreen.AddPhoneNumberScreen)
                removeScreen(screen = CustomAuthScreen.PhoneNumberVerificationScreen)
                checkSession()
                updateCurrentScreen()
            }
        }
    }

    fun sendSetupProfileRequest() {

        if (!isInternetAvailable()) {
            showNoInternetConnection(isOffline = true)
            return
        }

        viewModelScope.launch {

            showLoading()

            val session = _authUiState.value.session
            val phoneNumber = session?.phoneNumber
                ?: "${this@AuthViewModel._authUiState.value.countryPrefix}${this@AuthViewModel._authUiState.value.phoneNumber}"
            val country = getCountryModelFromPrefix(prefix = _authUiState.value.countryPrefix)
            val countryPrefix = this@AuthViewModel._authUiState.value.countryPrefix

            val profileDTO = ProfileSetupDTO(
                phoneNumber = phoneNumber,
                dob = this@AuthViewModel._authUiState.value.dob,
                fullName = this@AuthViewModel._authUiState.value.fullName,
                username = this@AuthViewModel._authUiState.value.username,
                gender = this@AuthViewModel._authUiState.value.gender.name.uppercase(),
                mood = "HAPPY",
                country = country?.name,
                profilePhoto = this@AuthViewModel._authUiState.value.profilePictureUri.toString(),
                zodiacSign = this@AuthViewModel._authUiState.value.zodiacSign?.sign?.uppercase(),
            )

            val fileManager = FileManager()
            val profilePictureUri =
                if (this@AuthViewModel._authUiState.value.profilePictureUri == null) {
                    null
                } else {
                    this@AuthViewModel._authUiState.value.profilePictureUri.toString()
                }
            val mediaFile =
                fileManager.createMediaFileFromPath(
                    path = profilePictureUri,
                    id = null,
                    removed = null
                )

            authRepository.sendProfileSetupRequest(
                profileSetupDTO = profileDTO,
                mediaFile = mediaFile
            )
                .onSuccess { response, _ ->

                    val credentials = setUserWholeCredentials(
                        access = this@AuthViewModel._authUiState.value.session?.access,
                        refresh = this@AuthViewModel._authUiState.value.session?.refresh,
                        userInfo = response?.user
                    )

                    saveSession(credentials = credentials, sessionKey = SESSION_KEY)
                    idleScreen()
                    showSuccessMessage(message = "")
                }
                .onError { error, errorType ->
                    showErrorMessage(
                        error = error.toString(),
                        errorType = "${errorType.name} ERROR"
                    )
                }
        }
    }

    //Sessions
    private fun saveSession(credentials: String, sessionKey: String) {
        sessionStorage.saveSession(credentials, sessionKey)
    }

    private fun getSession(sessionKey: String): String? {
        return try {
            sessionStorage.getSession(sessionKey)
        } catch (exception: Exception) {
            AppLogger.log("EXCEPTION ON GETTING SESSION = ${exception.message}")
            null
        }

    }


    //    NAVIGATION
    private fun initializeAuthNavigationFlow() {

        val session = this._authUiState.value.session
        val isPhoneNumberVerified = session?.isPhoneVerified
        val screenStack: ArrayDeque<CustomAuthScreen> = ArrayDeque()

        clearAuthScreenStack()

        if (isPhoneNumberVerified == true) {
            screenStack.addAll(
                listOf(
                    CustomAuthScreen.AddFullNameScreen,
                    CustomAuthScreen.AddDOBScreen,
                    CustomAuthScreen.ZodiacScreen,
                    CustomAuthScreen.SelectGenderScreen,
                    CustomAuthScreen.SetProfilePictureScreen
                )
            )
        } else if (session != null && isPhoneNumberVerified == false) {
            screenStack.addAll(
                listOf(
                    CustomAuthScreen.PhoneNumberVerificationScreen,
                    CustomAuthScreen.AddFullNameScreen,
                    CustomAuthScreen.AddDOBScreen,
                    CustomAuthScreen.ZodiacScreen,
                    CustomAuthScreen.SelectGenderScreen,
                    CustomAuthScreen.SetProfilePictureScreen
                )
            )
        } else {

            screenStack.addAll(
                listOf(
                    CustomAuthScreen.AddPhoneNumberScreen,
                    CustomAuthScreen.PhoneNumberVerificationScreen,
                    CustomAuthScreen.AddFullNameScreen,
                    CustomAuthScreen.AddDOBScreen,
                    CustomAuthScreen.ZodiacScreen,
                    CustomAuthScreen.SelectGenderScreen,
                    CustomAuthScreen.SetProfilePictureScreen
                )
            )
        }

        this._authUiState.value = this._authUiState.value.copy(
            screenStack = screenStack
        )

        updateCurrentScreen()
    }

    private fun clearAuthScreenStack() {
        this._authUiState.value = this._authUiState.value.copy(screenStack = ArrayDeque())
    }

    private fun updateCurrentScreen() {


        this._authUiState.value = this._authUiState.value.copy(
            currentScreen = this._authUiState.value.screenStack.firstOrNull()
                ?: CustomAuthScreen.SelectGenderScreen
        )


    }

    private fun removeCurrentScreen() {
        this._authUiState.value.screenStack.removeFirst()
    }

    private fun removeScreen(screen: CustomAuthScreen) {
        this._authUiState.value.screenStack.remove(screen)
    }

    fun navigateNextScreen(isIncrease: Boolean = true) {
        val stack = this._authUiState.value.screenStack
        if (stack.isEmpty()) return

        if (stack.isNotEmpty()) {
            removeCurrentScreen()
            updateCurrentScreen()
            updateProgress(isIncrease = isIncrease)
        }

    }

    fun navigateBack() {
        val currentScreen = this._authUiState.value.currentScreen
        val session = this._authUiState.value.session
        val isPhoneNumberVerified = session?.isPhoneVerified

        if (currentScreen == CustomAuthScreen.PhoneNumberVerificationScreen) {
            return
        }

        if (this._authUiState.value.screenStack.size < CustomAuthScreen.screenOrder.size) {

            val currentIndex = getCurrentScreenIndex(currentScreen = currentScreen)

            if (currentIndex > 0) {

                val previousScreen = CustomAuthScreen.screenOrder[currentIndex - 1]

                val shouldSkipAddingPreviousScreen =
                    (isPhoneNumberVerified == true && (previousScreen == CustomAuthScreen.PhoneNumberVerificationScreen || previousScreen == CustomAuthScreen.AddPhoneNumberScreen))

                if (!shouldSkipAddingPreviousScreen) {
                    if (previousScreen == CustomAuthScreen.AddDOBScreen) {
                        this._authUiState.value.screenStack.addFirst(CustomAuthScreen.ZodiacScreen)
                        this._authUiState.value.screenStack.addFirst(previousScreen)
                        updateProgress(isIncrease = false)
                        updateProgress(isIncrease = false)

                    } else {
                        this._authUiState.value.screenStack.addFirst(previousScreen)
                        updateProgress(isIncrease = false)
                    }

                }


                updateCurrentScreen()
            }
        }
    }

    private fun getCurrentScreenIndex(
        currentScreen: CustomAuthScreen
    ): Int {
        val isCurrentScreenGender =
            this._authUiState.value.currentScreen == CustomAuthScreen.SelectGenderScreen

        return if (!isCurrentScreenGender) {
            CustomAuthScreen.screenOrder.indexOf(currentScreen)
        } else {

            CustomAuthScreen.screenOrder.indexOf(CustomAuthScreen.ZodiacScreen)
        }

    }

    private fun setZodiacSign() {

        val zodiacSignList = ZodiacUtils.getZodiacSignList()

        this._authUiState.value = this._authUiState.value.copy(zodiacSignList = zodiacSignList)
    }


    private fun findZodiacSign(month: Int, day: Int): ZodiacSign? {
        val zodiacSigns = this._authUiState.value.zodiacSignList

        return zodiacSigns.find { sign ->
            if (sign.startMonth == sign.endMonth) {
                // Same month range
                month == sign.startMonth && day in sign.startDay..sign.endDay
            } else if (sign.startMonth < sign.endMonth) {
                // Normal range (e.g., Aries: March 21 - April 19)
                (month == sign.startMonth && day >= sign.startDay) ||
                        (month == sign.endMonth && day <= sign.endDay)
            } else {
                // Wrap-around range (e.g., Capricorn: Dec 22 - Jan 19)
                (month == sign.startMonth && day >= sign.startDay) ||
                        (month == sign.endMonth && day <= sign.endDay)
            }
        }
    }

// Validations

    fun isPhoneNumberValid(): Boolean {
        if (this._authUiState.value.phoneNumber.length < 10 || this._authUiState.value.phoneNumber.isEmpty()) {
            updateValidationError(
                validation = Validation(
                    validationMessage = "Invalid phone number format!",
                    isPhoneNumber = true
                )
            )
            return false
        } else if (this._authUiState.value.countryPrefix.isEmpty()) {
            updateValidationError(
                validation = Validation(
                    validationMessage = "Country code is required!",
                    isPhoneNumber = true
                )
            )
            return false
        }

        return true
    }

    fun isFullNameValid(): Boolean {
        if (this._authUiState.value.fullName.isEmpty()) {
            updateValidationError(
                validation = Validation(
                    validationMessage = "Full name is required!",
                    isFullName = true
                )
            )
            return false
        }
        return true
    }

    fun isUsernameValid(): Boolean {
        if (this._authUiState.value.username.isEmpty()) {
            updateValidationError(
                validation = Validation(
                    validationMessage = "Username is required!",
                    isUserName = true
                )
            )
            return false
        }
        return true
    }

    fun isOtpValid(): Boolean {
        if (this._authUiState.value.optValues.any { it.isEmpty() }) {
            updateValidationError(
                validation = Validation(
                    validationMessage = "Otp is required!",
                    isOtp = true
                )
            )
            return false
        }
        return true
    }

    fun isDOBValid(): Boolean {
        if (this._authUiState.value.dob.isEmpty()) {
            updateValidationError(
                validation = Validation(
                    validationMessage = "Date of birth is required!",
                    isDOB = true
                )
            )
            return false
        }

        return true
    }

    fun isGenderValid(): Boolean {
        if (this._authUiState.value.gender == Gender.NONE) {
            updateValidationError(
                validation = Validation(
                    validationMessage = "Please choose your gender!",
                    isGender = true
                )
            )
            return false
        }
        return true
    }


}