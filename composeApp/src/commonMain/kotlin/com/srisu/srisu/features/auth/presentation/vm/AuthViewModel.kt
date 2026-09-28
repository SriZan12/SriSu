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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.data.remote.ApiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val sessionStorage: com.srisu.srisu.core.session.SessionCoordinator,
    private val dataStoreRepo: AuthDataStore,
    private val startup: com.srisu.srisu.features.auth.domain.StartupCoordinator
) : ViewModel() {

    private val _authUiState = MutableStateFlow(AuthUIStates())
    val authUiState = _authUiState.asStateFlow()
    private var operation: Job? = null
    private var busy = false
    private var flowVersion = 0L
    private var requestId: String? = null

    init {
        checkSession()
        initializeAuthNavigationFlow()
        setZodiacSign()
        loadAllCountries()
        getRemainingOTPTimeStamp()
        viewModelScope.launch {
            startup.state.collect { access ->
                if (access is com.srisu.srisu.features.auth.domain.StartupState.Available) {
                    when (access.destination) {
                        com.srisu.srisu.features.auth.domain.AccessDestination.NAME -> updateState { it.copy(currentScreen = CustomProfileSetupScreen.AddFullNameScreen, currentProgressStep = 1) }
                        com.srisu.srisu.features.auth.domain.AccessDestination.PHOTO -> updateState { it.copy(currentScreen = CustomProfileSetupScreen.SetProfilePictureScreen, currentProgressStep = 2) }
                        else -> Unit
                    }
                }
            }
        }
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

    private inline fun launchSafely(
        crossinline onError: (String) -> Unit = { message ->
            showErrorMessage(error = message)
        },
        crossinline block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (exception: Exception) {
                AppLogger.log("AuthViewModel operation failed: ${exception::class.simpleName}")
                onError("Unable to complete this action. Please retry.")
            }
        }
    }

    // Session

    private fun checkSession(): Session? {
        val sessionJson = getSession(sessionKey = SESSION_KEY)

        val session = try {
            sessionJson?.let { Json.decodeFromString<Session>(it) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (exception: Exception) {
            AppLogger.log("Session deserialization failed")
            null
        }

        updateProgress(isIncrease = true)

        updateSession(session)
        updateState { it.copy(fullName = session?.fullName.orEmpty(), username = session?.username.orEmpty()) }

        return session
    }

    private fun saveSession(credentials: String, sessionKey: String) {
        sessionStorage.saveSession(credentials, sessionKey)
    }

    private fun getSession(sessionKey: String): String? {
        return try {
            sessionStorage.getSession(sessionKey)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (exception: Exception) {
            AppLogger.log("Failed to read secure session storage")
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

    fun abandonChallenge() {
        flowVersion++
        operation?.cancel()
        busy = false
        requestId = null
        updateState { it.copy(challengeId = null, optValues = List(6) { "" }, baseUIState = BaseUIState.Idle, resendAt = 0, expiresAt = 0) }
        viewModelScope.launch { dataStoreRepo.deleteOTPTimeStamp() }
    }

    fun updatePhoneNumber(phoneNumber: String, showValidationMessage: () -> Unit) {
        val digits = phoneNumber.trim().removePrefix(currentState.countryPrefix).filter { it in '0'..'9' }.take(14)
        if (digits != currentState.phoneNumber) abandonChallenge()
        updateState { it.copy(phoneNumber = digits) }
    }

    fun updateCountry(code: String, prefix: String) {
        if (prefix != currentState.countryPrefix) abandonChallenge()
        updateState { it.copy(countryCode = code, countryPrefix = prefix) }
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
        if (uri == null) return
        runOperation { _ ->
            val photo = FileManager().createProfilePhotoFromPath(uri.toString())
            if (photo?.fileBytes == null) showErrorMessage("Unable to use this photo. Choose an image under 5 MB and 20 megapixels.")
            else updateState { it.copy(profilePictureUri = uri) }
        }
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

    private fun failure(error: ApiError) {
        val message = when {
            error.fields["username"]?.contains("unique") == true -> "This username is taken. Choose another."
            error.fields["otp_code"]?.contains("expired") == true -> "This code has expired. Request a new code."
            error.fields["otp_code"]?.contains("attempts_exhausted") == true -> "Too many attempts. Request a new code when available."
            error.fields.containsKey("otp_code") -> "This code is invalid or already used. Check it or request a new code."
            error.fields.containsKey("profile_photo") -> "Choose a JPEG, PNG or WebP image under 5 MB and 20 megapixels."
            else -> error.message
        }
        showErrorMessage(message)
    }

    private fun runOperation(block: suspend (Long) -> Unit) {
        if (busy) return
        busy = true
        val version = flowVersion
        showLoading()
        operation = viewModelScope.launch {
            try { block(version)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) { if (version == flowVersion) showErrorMessage("Unable to complete this action. Please retry.")
            } finally {
                if (version == flowVersion) {
                    busy = false
                    if (currentState.baseUIState is BaseUIState.Loading) idleScreen()
                }
            }
        }
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class, ExperimentalTime::class)
    fun requestOTP(onNavToOTPScreen: () -> Unit) {
        if (!isPhoneNumberValid() || currentState.resendAt > kotlin.time.Clock.System.now().toEpochMilliseconds()) return
        runOperation { version ->
            val state = currentState
            val id = requestId ?: kotlin.uuid.Uuid.random().toString().also { requestId = it }
            val response = authRepository.sendOTPRequest(AuthDTO(phoneNumber = state.countryPrefix + state.phoneNumber, requestId = id)).result
            if (version != flowVersion) return@runOperation
            when (response) {
                is NetworkAPIResult.Success -> {
                    val challenge = requireNotNull(response.response)
                    val at = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    val lifetime = (kotlin.time.Instant.parse(challenge.expiresAt) - kotlin.time.Instant.parse(challenge.serverTime)).inWholeMilliseconds.coerceAtLeast(0)
                    dataStoreRepo.saveOTPTimestamp(OTPScreenMetadata(state.countryCode, state.countryPrefix, state.phoneNumber,
                        at, challenge.retryAfterSeconds * 1000, challenge.challengeId, at + lifetime))
                    if (version != flowVersion) return@runOperation
                    updateState { it.copy(challengeId = challenge.challengeId, resendAt = at + challenge.retryAfterSeconds * 1000,
                        expiresAt = at + lifetime, optValues = List(6) { "" }, remainingOTPTimestamp = challenge.retryAfterSeconds * 1000) }
                    requestId = null
                    // AuthGraph observes durable challenge state. No callback navigation race.
                }
                is NetworkAPIResult.Error -> {
                    if (response.failure.status != null && response.failure.status != 409) requestId = null
                    response.failure.retryAfterSeconds?.let { wait -> updateState { it.copy(resendAt = kotlin.time.Clock.System.now().toEpochMilliseconds() + wait * 1000) } }
                    failure(response.failure)
                }
            }
        }
    }

    fun resetOTPTimeStamp() = abandonChallenge()

    @OptIn(ExperimentalTime::class)
    fun getRemainingOTPTimeStamp() {
        val restoringVersion = flowVersion
        viewModelScope.launch {
            val metadata = dataStoreRepo.getOTPTimestamp().first()
            if (restoringVersion == flowVersion && metadata?.challengeId != null && sessionStorage.currentSession()?.access == null && currentState.challengeId == null) {
                val at = kotlin.time.Clock.System.now().toEpochMilliseconds()
                if (metadata.expiresAt > at) updateState { it.copy(challengeId = metadata.challengeId,
                    countryCode = metadata.countryCode, countryPrefix = metadata.countryPrefix, phoneNumber = metadata.phoneNumber,
                    resendAt = metadata.saveTime + metadata.totalTime, expiresAt = metadata.expiresAt) }
                else dataStoreRepo.deleteOTPTimeStamp()
            }
            while (true) {
                val remaining = (currentState.resendAt - kotlin.time.Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0)
                updateState { it.copy(remainingOTPTimestamp = remaining.takeIf { value -> value > 0 }) }
                delay(1000)
            }
        }
    }

    fun verifyOtp(onGoToHomeScreen: () -> Unit, onGoToProfileSetupScreen: () -> Unit) {
        if (!isOtpValid() || currentState.challengeId == null) return
        runOperation { version ->
            val stamp = sessionStorage.stamp()
            val state = currentState
            val response = authRepository.sendVerifyOtpRequest(state.countryPrefix + state.phoneNumber,
                state.optValues.joinToString(""), state.challengeId).result
            if (version != flowVersion) return@runOperation
            sessionStorage.ensureCurrent(stamp)
            when (response) {
                is NetworkAPIResult.Success -> {
                    val result = requireNotNull(response.response)
                    val user = requireNotNull(result.user)
                    require(user.id != null && user.isPhoneVerified == true)
                    val access = requireNotNull(result.tokens?.access)
                    val refresh = requireNotNull(result.tokens?.refresh)
                    dataStoreRepo.deleteOTPTimeStamp()
                    if (version != flowVersion) return@runOperation
                    sessionStorage.ensureCurrent(stamp)
                    updateState { it.copy(optValues = List(6) { "" }, challengeId = null) }
                    sessionStorage.saveIfCurrent(Json.encodeToString(user.toSession(access, refresh, user.id)), stamp)
                    // The root coordinator restores server state for the new account.
                }
                is NetworkAPIResult.Error -> failure(response.failure)
            }
        }
    }

    fun saveName() {
        if (!isFullNameValid() || !isUsernameValid()) return
        runOperation { _ ->
            val stamp = sessionStorage.stamp()
            val result = authRepository.updateName(currentState.fullName.trim(), currentState.username.trim()).result
            sessionStorage.ensureCurrent(stamp)
            when (result) {
                is NetworkAPIResult.Success -> {
                    val profile = requireNotNull(result.response)
                    startup.accept(profile, stamp)
                    if (profile.progress?.nextStep == "photo") updateState { it.copy(currentScreen = CustomProfileSetupScreen.SetProfilePictureScreen, currentProgressStep = 2) }
                }
                is NetworkAPIResult.Error -> failure(result.failure)
            }
        }
    }

    fun sendSetupProfileRequest() {
        runOperation { _ ->
            val stamp = sessionStorage.stamp()
            val path = currentState.profilePictureUri?.toString()
            val media = path?.let { FileManager().createProfilePhotoFromPath(it) }
            if (path != null && media?.fileBytes == null) {
                showErrorMessage("Unable to read this photo. Choose a JPEG, PNG or WebP under 5 MB and 20 megapixels.")
                return@runOperation
            }
            val result = authRepository.sendProfileSetupRequest(ProfileSetupDTO(), media).result
            sessionStorage.ensureCurrent(stamp)
            when (result) {
                is NetworkAPIResult.Success -> {
                    val profile = requireNotNull(result.response)
                    startup.accept(profile, stamp)
                    if (profile.progress?.nextStep == "photo") updateState { it.copy(currentScreen = CustomProfileSetupScreen.SetProfilePictureScreen, currentProgressStep = 2) }
                }
                is NetworkAPIResult.Error -> failure(result.failure)
            }
        }
    }

    fun showNameStep() { updateState { it.copy(currentScreen = CustomProfileSetupScreen.AddFullNameScreen, currentProgressStep = 1) } }

    // Navigation

    private fun initializeAuthNavigationFlow() {
        val screenStack = ArrayDeque<CustomProfileSetupScreen>()
        clearAuthScreenStack()

        screenStack.addAll(
            listOf(
                CustomProfileSetupScreen.AddFullNameScreen,
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

        if (state.screenStack.size >= CustomProfileSetupScreen.screenOrder.size) {
            return
        }

        val currentIndex = getCurrentScreenIndex(currentScreen)
        if (currentIndex <= 0) return

        val previousScreen = CustomProfileSetupScreen.screenOrder[currentIndex - 1]

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
            !com.srisu.srisu.features.auth.domain.isInternationalPhoneValid(currentState.countryPrefix, currentState.phoneNumber) -> {
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
