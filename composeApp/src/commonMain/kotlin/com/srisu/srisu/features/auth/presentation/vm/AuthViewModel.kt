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
import com.srisu.srisu.features.auth.presentation.components.CustomProfileSetupScreen
import com.srisu.srisu.features.auth.presentation.components.OTPScreenMetadata
import com.srisu.srisu.features.auth.presentation.screen.profilesetup.Gender
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.state.Validation
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.core.session.setUserWholeCredentials
import com.srisu.srisu.core.session.toSession
import com.srisu.srisu.features.auth.presentation.state.RelationshipSituation
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

    private val _authUiState = MutableStateFlow(AuthUIStates())
    val authUiState = _authUiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = AuthUIStates()
    )

    init {
        checkSession()
        initializeAuthNavigationFlow()
        setZodiacSign()
        loadAllCountries()
    }

    private val currentState: AuthUIStates
        get() = _authUiState.value

    private fun updateState(transform: (AuthUIStates) -> AuthUIStates) {
        _authUiState.value = transform(_authUiState.value)
    }

    private fun setBaseUiState(state: BaseUIState) {
        updateState { it.copy(baseUIState = state) }
    }

    private fun showErrorMessage(
        error: String,
        errorType: String = "ERROR"
    ) {
        setBaseUiState(
            BaseUIState.Error(
                errorType = errorType,
                message = error
            )
        )
    }

    private fun showSuccessMessage(message: String) {
        setBaseUiState(BaseUIState.Success(message))
    }

    private fun showLoading() {
        setBaseUiState(BaseUIState.Loading)
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

    private inline fun launchSafely(
        crossinline onError: (String) -> Unit = { message ->
            showErrorMessage(error = message)
        },
        crossinline block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (exception: Exception) {
                AppLogger.log("AuthViewModel exception: ${exception.message}")
                onError(exception.message ?: "Something went wrong.")
            }
        }
    }

    // Session

    private fun checkSession(): Session? {
        val sessionJson = getSession(sessionKey = SESSION_KEY)

        val session = try {
            sessionJson?.let { Json.decodeFromString<Session>(it) }
        } catch (exception: Exception) {
            AppLogger.log("Session deserialization failed: ${exception.message}")
            null
        }

        if (session == null) {
            updateProgress(isIncrease = true)
            return null
        }

        when (session.isPhoneVerified) {
            true -> updateProgress(isIncrease = true, step = FULL_NAME_PROGRESS)
            false -> updateProgress(
                isIncrease = true,
                step = PHONE_NUMBER_VERIFICATION_PROGRESS
            )

            null -> updateProgress(isIncrease = true)
        }

        updateSession(session)
        AppLogger.log("SESSION = ${runCatching { Json.encodeToString(session) }.getOrNull()}")

        return session
    }

    private fun saveSession(credentials: String, sessionKey: String) {
        runCatching {
            sessionStorage.saveSession(credentials, sessionKey)
        }.onFailure {
            AppLogger.log("Failed to save session: ${it.message}")
        }
    }

    private fun getSession(sessionKey: String): String? {
        return try {
            sessionStorage.getSession(sessionKey)
        } catch (exception: Exception) {
            AppLogger.log("Failed to get session: ${exception.message}")
            null
        }
    }

    private fun updateSession(session: Session?) {
        updateState { it.copy(session = session) }
    }

    // Progress

    private fun updateProgress(
        isIncrease: Boolean,
        step: Int? = null
    ) {
        val totalSteps = TOTAL_PROGRESS
        val currentStep = currentState.currentProgressStep

        val newStep = when {
            isIncrease -> step ?: (currentStep + 1).coerceAtMost(totalSteps)
            else -> (currentStep - 1).coerceAtLeast(1)
        }

        updateState {
            it.copy(
                currentProgressStep = newStep,
                progress = newStep.toFloat() / totalSteps.toFloat()
            )
        }
    }

    // UI updates

    fun updatePhoneNumber(
        phoneNumber: String,
        showValidationMessage: () -> Unit
    ) {
        if (phoneNumber.length <= 10) {
            updateState { it.copy(phoneNumber = phoneNumber) }
        } else {
            showValidationMessage()
        }
    }

    fun updateCountry(code: String, prefix: String) {
        updateState {
            it.copy(
                countryCode = code,
                countryPrefix = prefix
            )
        }
    }

    fun updateOtpValues(index: Int, value: String) {
        val otpValues = currentState.optValues.toMutableList()
        if (index !in otpValues.indices) return

        otpValues[index] = value
        updateState { it.copy(optValues = otpValues) }
    }

    private fun loadAllCountries() {
        launchSafely {
            val countries = getAllCountriesFromJson().orEmpty()
            updateState { it.copy(countryList = countries) }
        }
    }

    fun updateOTPRemainingTime(remainingOTPTimestamp: Long?) {
        updateState { it.copy(remainingOTPTimestamp = remainingOTPTimestamp) }
    }

    fun updateFullName(name: String) {
        updateState { it.copy(fullName = name) }
    }

    fun updateUserName(username: String) {
        updateState { it.copy(username = username) }
    }

    fun updateDOB(dob: String) {
        updateState { it.copy(dob = dob) }
        updateZodiacSign()
        updateAge(dob)
    }

    private fun updateAge(dob: String) {
        updateState { it.copy(age = calculateAge(dob).toString()) }
    }

    fun updateProfilePictureUri(uri: Uri?) {
        updateState { it.copy(profilePictureUri = uri) }
    }

    fun updateGender(gender: Gender) {
        updateState { it.copy(gender = gender) }
    }

    fun updateRelationshipSituation(situation: RelationshipSituation) {
        updateState { it.copy(relationshipSituation = situation) }
    }

    fun updateValidationError(validation: Validation) {
        updateState { it.copy(validationError = validation) }
    }

    // API calls and local persistence

    fun requestOTP(isNavigateScreen: Boolean = true) {
        if (!isInternetAvailable()) {
            showNoInternetConnection(isOffline = true)
            return
        }

        launchSafely {
            showLoading()

            val state = currentState
            val authDTO = AuthDTO(
                fullName = state.fullName,
                dob = state.dob,
                gender = state.gender.name,
                phoneNumber = "${state.countryPrefix}${state.phoneNumber}"
            )

            // Preserved current behavior:
            // local session is saved and navigation proceeds even though API call is currently disabled.
            val session = Session(isPhoneVerified = false)
            val credentials = Json.encodeToString(session)

            saveSession(credentials = credentials, sessionKey = SESSION_KEY)

            if (isNavigateScreen) {
                navigateNextScreen()
            }

            idleScreen()

            /*
            authRepository.sendOTPRequest(authDTO = authDTO)
                .onSuccess { _, _ ->
                    val credentials = setCredentials(
                        tokens = null,
                        isPhoneVerified = false
                    )
                    saveSession(credentials = credentials, sessionKey = SESSION_KEY)

                    if (isNavigateScreen) {
                        navigateNextScreen()
                    }

                    idleScreen()
                }
                .onError { error, errorType ->
                    AppLogger.log("OTP request errorType = ${errorType.name}")
                    AppLogger.log("OTP request error = ${error.toString()}")

                    showErrorMessage(
                        errorType = "${errorType.name} ERROR",
                        error = error.toString()
                    )
                }
            */
        }
    }

    @OptIn(ExperimentalTime::class)
    fun saveOTPTimeStamp() {
        launchSafely {
            if (currentState.remainingOTPTimestamp != null) return@launchSafely

            dataStoreRepo.saveOTPTimestamp(
                otpScreenMetadata = OTPScreenMetadata(
                    countryCode = currentState.countryCode,
                    countryPrefix = currentState.countryPrefix,
                    phoneNumber = currentState.phoneNumber,
                    saveTime = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                    totalTime = OTP_WAITING_TIME
                )
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    fun getRemainingOTPTimeStamp() {
        launchSafely {
            dataStoreRepo.getOTPTimestamp().collectLatest { otpTimestamp ->
                val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds()

                val timeRemaining = otpTimestamp?.let {
                    val elapsedTime = currentTime - it.saveTime
                    (it.totalTime - elapsedTime).coerceAtLeast(0L)
                }

                updateOTPRemainingTime(timeRemaining)

                otpTimestamp?.countryCode?.let { code ->
                    updateCountry(
                        code = code,
                        prefix = otpTimestamp.countryPrefix
                    )
                }

                otpTimestamp?.phoneNumber?.let { phoneNumber ->
                    updatePhoneNumber(
                        phoneNumber = phoneNumber,
                        showValidationMessage = {}
                    )
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

        launchSafely {
            showLoading()

            val state = currentState
            val otpCode = state.optValues.joinToString("")
            val phoneNumber = "${state.countryPrefix}${state.phoneNumber}"

            authRepository.sendVerifyOtpRequest(
                phoneNumber = phoneNumber,
                otp = otpCode
            ).onSuccess { response, _ ->
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
                    isProfileCompleted = response?.user?.isProfileComplete == true,
                    onGoToHomeScreen = onGoToHomeScreen
                )

                idleScreen()
            }.onError { error, _ ->
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
            return
        }

    }

    fun sendSetupProfileRequest() {
        if (!isInternetAvailable()) {
            showNoInternetConnection(isOffline = true)
            return
        }

        launchSafely {
            showLoading()

            val state = currentState
            val session = state.session

            val phoneNumber = session?.phoneNumber
                ?: "${state.countryPrefix}${state.phoneNumber}"

            val country = getCountryModelFromPrefix(prefix = state.countryPrefix)
            val profilePicturePath = state.profilePictureUri?.toString()

            val profileDTO = ProfileSetupDTO(
                phoneNumber = phoneNumber,
                dob = state.dob,
                fullName = state.fullName,
                username = state.username,
                gender = state.gender.name.uppercase(),
                mood = "HAPPY",
                country = country?.name,
                profilePhoto = profilePicturePath,
                zodiacSign = state.zodiacSign?.name?.uppercase()
            )

            val fileManager = FileManager()
            val mediaFile = profilePicturePath?.let { path ->
                fileManager.createMediaFileFromPath(
                    path = path,
                    id = null,
                    removed = null
                )
            }

            authRepository.sendProfileSetupRequest(
                profileSetupDTO = profileDTO,
                mediaFile = mediaFile
            ).onSuccess { response, _ ->
                val credentials = setUserWholeCredentials(
                    access = currentState.session?.access,
                    refresh = currentState.session?.refresh,
                    userInfo = response?.user
                )

                saveSession(credentials = credentials, sessionKey = SESSION_KEY)
                idleScreen()
                showSuccessMessage(message = "")
            }.onError { error, errorType ->
                showErrorMessage(
                    error = error.toString(),
                    errorType = "${errorType.name} ERROR"
                )
            }
        }
    }

    // Navigation

    private fun initializeAuthNavigationFlow() {
        val session = currentState.session
        val isPhoneNumberVerified = session?.isPhoneVerified
        val screenStack = ArrayDeque<CustomProfileSetupScreen>()

        clearAuthScreenStack()

        screenStack.addAll(
            listOf(
                CustomProfileSetupScreen.AddFullNameScreen,
                CustomProfileSetupScreen.AddDOBScreen,
                CustomProfileSetupScreen.ZodiacScreen,
                CustomProfileSetupScreen.SelectGenderScreen,
                CustomProfileSetupScreen.SetProfilePictureScreen
            )
        )

        updateState { it.copy(screenStack = screenStack) }
        updateCurrentScreen()
    }

    private fun clearAuthScreenStack() {
        updateState { it.copy(screenStack = ArrayDeque()) }
    }

    private fun updateCurrentScreen() {
        updateState {
            it.copy(
                currentScreen = it.screenStack.firstOrNull()
                    ?: CustomProfileSetupScreen.SelectGenderScreen
            )
        }
    }

    private fun removeCurrentScreen() {
        currentState.screenStack.removeFirstOrNull()
    }

    private fun removeScreen(screen: CustomProfileSetupScreen) {
        currentState.screenStack.remove(screen)
    }

    fun navigateNextScreen(isIncrease: Boolean = true) {
        val stack = currentState.screenStack
        if (stack.isEmpty()) return

        removeCurrentScreen()
        updateCurrentScreen()
        updateProgress(isIncrease = isIncrease)
    }

    fun navigateBack() {
        val state = currentState
        val currentScreen = state.currentScreen
        val isPhoneNumberVerified = state.session?.isPhoneVerified


        if (state.screenStack.size >= CustomProfileSetupScreen.screenOrder.size) {
            return
        }

        val currentIndex = getCurrentScreenIndex(currentScreen)
        if (currentIndex <= 0) return

        val previousScreen = CustomProfileSetupScreen.screenOrder[currentIndex - 1]

        val shouldSkipAddingPreviousScreen = isPhoneNumberVerified == true

        if (shouldSkipAddingPreviousScreen) {
            updateCurrentScreen()
            return
        }

        if (previousScreen == CustomProfileSetupScreen.AddDOBScreen) {
            currentState.screenStack.addFirst(CustomProfileSetupScreen.ZodiacScreen)
            currentState.screenStack.addFirst(previousScreen)
            updateProgress(isIncrease = false)
            updateProgress(isIncrease = false)
        } else {
            currentState.screenStack.addFirst(previousScreen)
            updateProgress(isIncrease = false)
        }

        updateCurrentScreen()
    }

    private fun getCurrentScreenIndex(currentScreen: CustomProfileSetupScreen): Int {
        val isCurrentScreenGender =
            currentState.currentScreen == CustomProfileSetupScreen.SelectGenderScreen

        return if (!isCurrentScreenGender) {
            CustomProfileSetupScreen.screenOrder.indexOf(currentScreen)
        } else {
            CustomProfileSetupScreen.screenOrder.indexOf(CustomProfileSetupScreen.ZodiacScreen)
        }
    }

    // Zodiac

    private fun setZodiacSign() {
        val zodiacSignList = ZodiacUtils.getZodiacSignList()
        updateState { it.copy(zodiacSignList = zodiacSignList) }
    }

    private fun updateZodiacSign() {
        val dob = currentState.dob
        val dateParts = getDayAndMonthIndividually(dateString = dob)
        val month = dateParts.first
        val day = dateParts.second

        val zodiacSign = findZodiacSign(month, day)
        updateState { it.copy(zodiacSign = zodiacSign) }

        AppLogger.log("ZODIAC SIGN = ${currentState.zodiacSign}")
    }

    private fun findZodiacSign(month: Int, day: Int): ZodiacSign? {
        return currentState.zodiacSignList.find { sign ->
            when {
                sign.startMonth == sign.endMonth -> {
                    month == sign.startMonth && day in sign.startDay..sign.endDay
                }

                sign.startMonth < sign.endMonth -> {
                    (month == sign.startMonth && day >= sign.startDay) ||
                            (month == sign.endMonth && day <= sign.endDay)
                }

                else -> {
                    (month == sign.startMonth && day >= sign.startDay) ||
                            (month == sign.endMonth && day <= sign.endDay)
                }
            }
        }
    }

    // Validations

    fun isPhoneNumberValid(): Boolean {
        return when {
            currentState.phoneNumber.isBlank() || currentState.phoneNumber.length < 10 -> {
                updateValidationError(
                    Validation(
                        validationMessage = "Invalid phone number format!",
                        isPhoneNumber = true
                    )
                )
                false
            }

            currentState.countryPrefix.isBlank() -> {
                updateValidationError(
                    Validation(
                        validationMessage = "Country code is required!",
                        isPhoneNumber = true
                    )
                )
                false
            }

            else -> true
        }
    }

    fun isFullNameValid(): Boolean {
        return if (currentState.fullName.isBlank()) {
            updateValidationError(
                Validation(
                    validationMessage = "Full name is required!",
                    isFullName = true
                )
            )
            false
        } else {
            true
        }
    }

    fun isUsernameValid(): Boolean {
        return if (currentState.username.isBlank()) {
            updateValidationError(
                Validation(
                    validationMessage = "Username is required!",
                    isUserName = true
                )
            )
            false
        } else {
            true
        }
    }

    fun isOtpValid(): Boolean {
        return if (currentState.optValues.any { it.isBlank() }) {
            updateValidationError(
                Validation(
                    validationMessage = "Otp is required!",
                    isOtp = true
                )
            )
            false
        } else {
            true
        }
    }

    fun isDOBValid(): Boolean {
        return if (currentState.dob.isBlank()) {
            updateValidationError(
                Validation(
                    validationMessage = "Date of birth is required!",
                    isDOB = true
                )
            )
            false
        } else {
            true
        }
    }

    fun isGenderValid(): Boolean {
        return if (currentState.gender == Gender.NONE) {
            updateValidationError(
                Validation(
                    validationMessage = "Please choose your gender!",
                    isGender = true
                )
            )
            false
        } else {
            true
        }
    }

    fun isRelationshipValid(): Boolean {
        return if (currentState.relationshipSituation == RelationshipSituation.NOTHING) {
            updateValidationError(
                Validation(
                    validationMessage = "Please choose your relationship status!",
                    isRelationship = true
                )
            )
            false
        } else {
            true
        }
    }
}