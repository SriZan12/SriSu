package com.srisu.srisu.features.auth.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.ErrorText
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OTPInputTextFields
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.RoundedButtonCompo
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.state.Validation
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.navigation.graph.HomeNavigation
import com.srisu.srisu.utils.Constants.Auth.OTP_LENGTH
import com.srisu.srisu.utils.DateTimeUtils.CountdownTimer
import com.srisu.srisu.utils.formatTime
import com.srisu.srisu.utils.isInternetAvailable
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun PhoneNumberVerificationScreen(
    navController: NavController,
    authViewModel: AuthViewModel
) {
    val authUIStates by authViewModel.authUiState.collectAsState()
    val localFocusManager = LocalFocusManager.current

    Init(authViewModel = authViewModel)

    HandleUiStateDialog(
        authViewModel = authViewModel,
        authUIStates = authUIStates
    )

    PhoneNumberVerificationContent(
        authUIStates = authUIStates,
        onBackClick = {
            navController.popBackStack()
        },
        onScreenClick = {
            localFocusManager.clearFocus(force = true)
        },
        onOtpChange = { index, value ->
            authViewModel.updateOtpValues(index = index, value = value)
            authViewModel.updateValidationError(Validation(isOtp = false))
        },
        onOtpComplete = {
            if (authViewModel.isOtpValid()) {
                authViewModel.verifyOtp {
                    navController.navigate(HomeNavigation.Home)
                }
            }
        },
        onVerifyClick = {
            localFocusManager.clearFocus(force = true)

            if (authViewModel.isOtpValid()) {
                authViewModel.verifyOtp {
                    navController.navigate(HomeNavigation.Home)
                }
            }
        },
        onResendClick = {
            authViewModel.requestOTP(isNavigateScreen = false)
            authViewModel.saveOTPTimeStamp()
        },
        onTimerFinished = {
            authViewModel.updateOTPRemainingTime(remainingOTPTimestamp = null)
        }
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneNumberVerificationContent(
    authUIStates: AuthUIStates,
    onBackClick: () -> Unit,
    onScreenClick: () -> Unit,
    onOtpChange: (index: Int, value: String) -> Unit,
    onOtpComplete: () -> Unit,
    onVerifyClick: () -> Unit,
    onResendClick: () -> Unit,
    onTimerFinished: () -> Unit
) {
    val otpCode = authUIStates.optValues.joinToString("")
    val isVerifyEnabled = otpCode.length == OTP_LENGTH && !authUIStates.validationError.isOtp

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onScreenClick
            ),
        topBar = {
            TopAppBar(
                title = {},
                modifier = Modifier,
                navigationIcon = {
                    IconButton(onClick = {}, modifier = Modifier) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back icon",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            RoundedButtonCompo(
                modifier = Modifier,
                title = "Verify",
                enabled = isVerifyEnabled,
                onClick = onVerifyClick
            )
        }
    ) { innerPadding ->


        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = innerPadding)
                .statusBarsPadding()
        ) {

            Column(
                modifier = Modifier
                    .padding( start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                MessageIconBox()

                OtpHeader(
                    countryCode = authUIStates.countryPrefix,
//                    phoneNumber = authUIStates.phoneNumber
                )

                OTPInputTextFields(
                    modifier = Modifier.fillMaxWidth(),
                    otpValues = authUIStates.optValues,
                    otpLength = OTP_LENGTH,
                    isError = authUIStates.validationError.isOtp,
                    onOtpInputComplete = onOtpComplete,
                    onUpdateOtpValuesByIndex = onOtpChange
                )

                if (authUIStates.validationError.isOtp) {
                    ErrorText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        text = authUIStates.validationError.validationMessage
                    )
                }


                ResendCodeSection(
                    authUIStates = authUIStates,
                    onResendClick = onResendClick,
                    onTimerFinished = onTimerFinished
                )
            }
        }
    }
}

@Composable
private fun BackButton(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        IconButton(
            onClick = onBackClick,
            modifier = modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Go back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }

}

@Composable
private fun MessageIconBox() {
    Surface(
        modifier = Modifier.size(85.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "OTP message",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )

        }
    }
}


@Composable
private fun OtpHeader(
    countryCode: String,
    phoneNumber: String = "9863938267"
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Check your messages",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "We sent a 6-digit code to $countryCode\n${phoneNumber.maskPhoneNumber()}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResendCodeSection(
    authUIStates: AuthUIStates,
    onResendClick: () -> Unit,
    onTimerFinished: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1000L)
        isVisible = true
    }

    if (!isVisible) return

    val remainingOTPTimestamp = authUIStates.remainingOTPTimestamp

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (remainingOTPTimestamp != null) {
            val totalSeconds = remainingOTPTimestamp / 1000

            CountdownTimer(
                totalSeconds = totalSeconds,
                onFinish = onTimerFinished
            ) { timeLeft ->
                Text(
                    text = "Resend code in ${formatTime(timeLeft)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onResendClick
                ),
                text = buildAnnotatedString {
                    append("Didn't receive the code? ")

                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("Resend")
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun String.maskPhoneNumber(): String {
    if (length <= 5) return this

    val start = take(2)
    val end = takeLast(3)

    AppLogger.log("MASKED PHONE NUMER = ${start}•••••${end}")

    return "$start•••••$end"
}

@Preview
@Composable
private fun OtpVerificationScreenPreview() {
    MaterialTheme {
        PhoneNumberVerificationContent(
            authUIStates = AuthUIStates(),
            onBackClick = {},
            onScreenClick = {},
            onOtpChange = { _, _ ->

            },
            onOtpComplete = {},
            onVerifyClick = {},
            onResendClick = {},
            onTimerFinished = {}
        )
    }
}
