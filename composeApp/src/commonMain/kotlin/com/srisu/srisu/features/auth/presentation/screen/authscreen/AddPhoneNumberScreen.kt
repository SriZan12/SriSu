package com.srisu.srisu.features.auth.presentation.screen.authscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.CommonBottomSheetCompo
import com.srisu.srisu.components.CountrySelectionBottomSheet
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.HighlightedTextComponent
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.PhoneNumberCompo
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.PrimaryOutlinedButtonCompo
import com.srisu.srisu.components.RoundedPrimaryButtonCompo
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.presentation.components.ScreenTopIcon
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun PhoneNumberScreen(
    authViewModel: AuthViewModel,
    onNavToOTPScreen: () -> Unit
) {
    val authUIState by authViewModel.authUiState.collectAsState()
    var showCountryList by rememberSaveable {
        mutableStateOf(false)
    }
    var showPhoneNumberConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    PhoneNumberScreenContent(
        modifier = Modifier,
        countryCode = authUIState.countryCode,
        phoneNumber = authUIState.phoneNumber,
        countryPrefix = authUIState.countryPrefix,
        onPhoneNumberChange = {
            authViewModel.updatePhoneNumber(phoneNumber = it, showValidationMessage = {})
        },
        onCountryClick = {
            showCountryList = true
        },
        onSendCodeClick = {
            if (authViewModel.isPhoneNumberValid()) {
                showPhoneNumberConfirmation = true
            }
        },
    )

    HandleUiStateDialog(
        authViewModel = authViewModel,
        authUIStates = authUIState
    )


    CountrySelectionBottomSheet(
        modifier = Modifier,
        countries = authUIState.countryList,
        show = showCountryList,
        onCountrySelected = { countryModel ->
            showCountryList = false
            authViewModel.updateCountry(
                code = countryModel.code ?: "",
                prefix = countryModel.prefix ?: ""
            )
        }) {
        showCountryList = false
    }

    PhoneNumberConfirmationBottomSheet(
        authUIState = authUIState,
        showPhoneNumberConfirmation = showPhoneNumberConfirmation,
        onConfirmed = {
            showPhoneNumberConfirmation = false

            if (authViewModel.isPhoneNumberValid()) {
                authViewModel.requestOTP {
                    onNavToOTPScreen()
                }
                onNavToOTPScreen()
            }

        },
        onDeclined = {
            showPhoneNumberConfirmation = false
        },
        onDismiss = {
            showPhoneNumberConfirmation = false
        }
    )

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
private fun PhoneNumberScreenContent(
    countryCode: String = "+977",
    phoneNumber: String,
    countryPrefix: String,
    onPhoneNumberChange: (String) -> Unit,
    onCountryClick: () -> Unit,
    onSendCodeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isButtonEnabled = phoneNumber.length >= 10
    val localFocusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            RoundedPrimaryButtonCompo(
                modifier = Modifier,
                title = "Send Code",
                enabled = isButtonEnabled,
                onClick = onSendCodeClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    indication = null,
                    interactionSource = MutableInteractionSource(),
                    onClick = {
                        localFocusManager.clearFocus()
                    })
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            SriSuHeader()

            ScreenTopIcon(
                imageVector = Icons.Outlined.Call,
                color = MaterialTheme.colorScheme.primary
            )

            PhoneNumberTitle()

            PhoneNumberInputSection(
                countryCode = countryCode,
                phoneNumber = phoneNumber,
                countryPrefix = countryPrefix,
                onPhoneNumberChange = onPhoneNumberChange,
                onCountryClick = onCountryClick,
            )

        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneNumberConfirmationBottomSheet(
    authUIState: AuthUIStates,
    showPhoneNumberConfirmation: Boolean,
    onConfirmed: () -> Unit,
    onDeclined: () -> Unit,
    onDismiss: () -> Unit
) {
    CommonBottomSheetCompo(onDismiss = {
        onDismiss()
    }, show = showPhoneNumberConfirmation) {

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Confirm your Phone Number",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            val fullText =
                "Are you sure ${authUIState.phoneNumber} is your phone number?"
            val highlightedText = authUIState.phoneNumber

            Box(
                modifier = Modifier.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                HighlightedTextComponent(
                    fullText = fullText,
                    highlightedText = highlightedText
                )
            }


            PrimaryButtonCompo(
                modifier = Modifier.fillMaxWidth().height(intrinsicSize = IntrinsicSize.Max)
                    .padding(top = 24.dp, bottom = 12.dp),
                label = "Yes, looks good"
            ) {
                onConfirmed()
            }


            PrimaryOutlinedButtonCompo(
                modifier = Modifier.fillMaxWidth().height(intrinsicSize = IntrinsicSize.Max)
                    .padding(bottom = 24.dp),
                label = "Edit Number"
            ) {
                onDeclined()
            }


        }
    }
}

@Composable
fun SriSuHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "SriSu",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HorizontalDivider(
                modifier = Modifier.width(48.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
            )

            Text(
                text = "D E V O T I O N",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider(
                modifier = Modifier.width(48.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
fun PhoneNumberTitle() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "What's your number?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "We'll send you a quick code — no\npasswords, no hassle.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PhoneNumberInputSection(
    countryCode: String,
    countryPrefix: String,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onCountryClick: () -> Unit
) {
    PhoneNumberCompo(
        modifier = Modifier,
        countryCode = countryCode,
        countryPrefix = countryPrefix,
        phoneNumber = phoneNumber,
        updatePhoneNumber = { input ->
            onPhoneNumberChange(input)
        },
        onShowCountryList = {
            onCountryClick()
        },
    )

}
