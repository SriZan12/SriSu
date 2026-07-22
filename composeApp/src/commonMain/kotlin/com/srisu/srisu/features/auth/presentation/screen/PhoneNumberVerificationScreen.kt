package com.srisu.srisu.features.auth.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.state.Validation
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.navigation.graph.HomeNavigation
import com.srisu.srisu.utils.DateTimeUtils.CountdownTimer
import com.srisu.srisu.utils.formatTime
import com.srisu.srisu.utils.isInternetAvailable
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.ErrorText
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OTPInputTextFields
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.StyledAnnotatedText
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.presentation.components.CommonAuthContainerCompo
import com.srisu.srisu.features.auth.presentation.components.TitleText
import com.srisu.srisu.utils.Constants.Auth.OTP_LENGTH
import kotlinx.coroutines.delay

@Composable
fun PhoneNumberVerificationScreen(
    navController: NavController,
    authViewModel: AuthViewModel
) {
    val localFocusManager: FocusManager = LocalFocusManager.current

    CommonAuthContainerCompo(
        buttonTitle = "Verify",
        onClickScreenContent = {
            localFocusManager.clearFocus(force = true)
        },
        onClickPrimaryButton = {
            if (authViewModel.isOtpValid()) {
                authViewModel.verifyOtp() {
                    navController.navigate(HomeNavigation.Home)
                }

//            authViewModel.navigateNextScreen()
            }

        }) {

        val authUIStates by authViewModel.authUiState.collectAsStateWithLifecycle()

        Init(authViewModel = authViewModel)

        HandleUiStateDialog(
            authViewModel = authViewModel,
            authUIStates = authUIStates
        )

        PhoneNumberVerificationCompo(
            navController = navController,
            authUIStates = authUIStates,
            authViewModel = authViewModel
        )

    }
}

@Composable
private fun PhoneNumberVerificationCompo(
    navController: NavController,
    authUIStates: AuthUIStates,
    authViewModel: AuthViewModel
) {
    Column {
        TitleText(
            modifier = Modifier.fillMaxWidth(),
            title = "Verify your number"
        )

        StyledAnnotatedText(
            title = "Please enter the 6 digit OTP sent to",
            subTitle = "${authUIStates.countryPrefix}${authUIStates.phoneNumber}",
            titleStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
            subTitleStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            ),

            )

        Spacer(modifier = Modifier.height(24.dp))

        val validationError = authUIStates.validationError


        OTPInputTextFields(
            modifier = Modifier.fillMaxWidth(),
            otpValues = authUIStates.optValues,
            otpLength = OTP_LENGTH,
            isError = validationError.isOtp,
            onOtpInputComplete = {
                if (authViewModel.isOtpValid()) {
                    authViewModel.verifyOtp {
                        navController.navigate(HomeNavigation.Home)
                    }
                }

                AppLogger.log("OTP COMPLETED")
            },
            onUpdateOtpValuesByIndex = { index, value ->
                authViewModel.updateOtpValues(index = index, value = value)
                authViewModel.updateValidationError(Validation(isOtp = false))
            }
        )


        if (validationError.isOtp) {
            ErrorText(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                text = validationError.validationMessage
            )
        }

        ResendCompo(
            authUIStates = authUIStates,
            authViewModel = authViewModel
        )
    }
}

@Composable
private fun Init(
    authViewModel: AuthViewModel
) {
    LaunchedEffect(Unit) {
        authViewModel.getRemainingOTPTimeStamp()
    }
}

@Composable
private fun HandleUiStateDialog(
    authViewModel: AuthViewModel,
    authUIStates: AuthUIStates
) {

    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = authUIStates.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    authViewModel.idleScreen()
                },
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim()
        }

        is BaseUIState.Success<*> -> {
//            val data = baseUIState.data
            // Handle success case based on the expected type
        }

        is BaseUIState.NoInternetConnection -> {
            showBottomSheet = baseUIState.isOffline
        }

        is BaseUIState.Idle -> Unit
    }

    if (showBottomSheet) {
        OfflineBottomSheetCompo(
            show = showBottomSheet,
            onDismiss = {
                showBottomSheet = false
                authViewModel.idleScreen()
            }
        )
    }
}

@Composable
private fun ResendCompo(
    authUIStates: AuthUIStates,
    authViewModel: AuthViewModel
) {
    var isVisible by remember { mutableStateOf(false) }
    val remainingOTPTimestamp = authUIStates.remainingOTPTimestamp

    LaunchedEffect(Unit) {
        delay(1000L) // Delay for 1 second
        isVisible = true
    }

    if (isVisible) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (remainingOTPTimestamp != null) {
                CountDownTimerCompo(authUIStates = authUIStates) {
                    authViewModel.updateOTPRemainingTime(remainingOTPTimestamp = null)
                }
            } else {
                StyledAnnotatedText(
                    modifier = Modifier.clickable(
                        interactionSource = null,
                        indication = null
                    ) {
                        authViewModel.requestOTP(isNavigateScreen = false)
                        authViewModel.saveOTPTimeStamp()
                    },
                    title = "Didn't receive the code?",
                    subTitle = "Resend",
                    titleStyle = MaterialTheme.typography.bodySmall.copy(color = Color.Black),
                    subTitleStyle = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                )
            }
        }
    }
}


@Composable
private fun CountDownTimerCompo(
    authUIStates: AuthUIStates,
    onTimerFinished: () -> Unit
) {

    val totalSeconds = (authUIStates.remainingOTPTimestamp ?: 0L) / 1000 // converting ms to sec

    CountdownTimer(
        totalSeconds = totalSeconds,
        onFinish = {
            onTimerFinished()
        }
    ) { timeLeft ->
        StyledAnnotatedText(
            modifier = Modifier,
            title = "Try again in ",
            subTitle = formatTime(seconds = timeLeft),
            titleStyle = MaterialTheme.typography.bodySmall.copy(color = Color.Black),
            subTitleStyle = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            ),
        )

    }


}

