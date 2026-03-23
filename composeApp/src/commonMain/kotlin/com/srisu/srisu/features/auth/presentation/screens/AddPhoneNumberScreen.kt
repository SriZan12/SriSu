package com.srisu.srisu.features.auth.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.CommonBottomSheetCompo
import com.srisu.srisu.components.CountrySelectionBottomSheet
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.ErrorText
import com.srisu.srisu.components.HighlightedTextComponent
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.PhoneNumberCompo
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.PrimaryOutlinedButtonCompo
import com.srisu.srisu.features.auth.presentation.Components.CommonAuthContainerCompo
import com.srisu.srisu.features.auth.presentation.Components.InfoText
import com.srisu.srisu.features.auth.presentation.Components.TitleText
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.state.Validation
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.isInternetAvailable

@Composable
fun AddPhoneNumberCompo(
    authViewModel: AuthViewModel
) {
    val authUIStates by authViewModel.authUiState.collectAsState()
    var showCountryList by rememberSaveable {
        mutableStateOf(false)
    }
    var showPhoneNumberConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    val localFocusManager = LocalFocusManager.current

    CommonAuthContainerCompo(buttonTitle = "Continue", onClickScreenContent = {
        localFocusManager.clearFocus(force = true)
    }, onClickPrimaryButton = {
        if (authViewModel.isPhoneNumberValid()) {
            showPhoneNumberConfirmation = true
        }
    }) {

        HandleUiStateDialog(
            authViewModel = authViewModel,
            authUIStates = authUIStates
        )

        TitleText(
            modifier = Modifier.fillMaxWidth(),
            title = "Please enter the number"
        )

        InfoText(
            modifier = Modifier.fillMaxWidth(),
            info = "We’ll send a one-time code to verify your phone number. No password needed."
        )

        Spacer(modifier = Modifier.height(24.dp))

        PhoneNumberCompo(
            authViewModel = authViewModel,
            authUIStates = authUIStates
        ) {
            showCountryList = true
        }

        CountrySelectionBottomSheet(
            modifier = Modifier,
            countries = authUIStates.countryList,
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
            authUIState = authUIStates,
            showPhoneNumberConfirmation = showPhoneNumberConfirmation,
            onConfirmed = {
                showPhoneNumberConfirmation = false

                if (authViewModel.isPhoneNumberValid()) {
                    authViewModel.requestOTP()
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
private fun PhoneNumberCompo(
    authViewModel: AuthViewModel,
    authUIStates: AuthUIStates,
    onShowCountryList: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PhoneNumberCompo(
            modifier = Modifier.fillMaxWidth(),
            countryCode = authUIStates.countryCode,
            countryPrefix = authUIStates.countryPrefix,
            phoneNumber = authUIStates.phoneNumber,
            isError = authUIStates.validationError.isPhoneNumber,
            updatePhoneNumber = { input ->
                if (input.all { it.isDigit() }) {
                    authViewModel.updatePhoneNumber(phoneNumber = input) { }
                    authViewModel.updateValidationError(Validation(isPhoneNumber = false))
                }
            },
            onShowCountryList = {
                onShowCountryList()
            },
        )

        val validationError = authUIStates.validationError

        if (validationError.isPhoneNumber) {
            ErrorText(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                text = validationError.validationMessage
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