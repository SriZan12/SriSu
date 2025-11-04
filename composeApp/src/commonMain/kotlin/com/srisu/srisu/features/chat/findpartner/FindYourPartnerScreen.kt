package com.srisu.srisu.features.chat.findpartner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.CountrySelectionBottomSheet
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.ErrorText
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.PhoneNumberCompo
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.SuccessDialog
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FindYourPartnerScreen(
    findPartnerViewModel: FindPartnerViewModel = koinViewModel<FindPartnerViewModel>()
) {
    val findPartnerUIStates by findPartnerViewModel.findPartnerUIState.collectAsState()

    HandleUiStates(
        findPartnerViewModel = findPartnerViewModel,
        findPartnerUIState = findPartnerUIStates
    )


    FindYourPartnerContent(
        findPartnerViewModel = findPartnerViewModel,
        findPartnerUIStates = findPartnerUIStates
    )
}

@Composable
private fun HandleUiStates(
    findPartnerViewModel: FindPartnerViewModel,
    findPartnerUIState: FindPartnerState
) {

    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = findPartnerUIState.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    findPartnerViewModel.idleScreen()
                },
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = {
                    findPartnerViewModel.idleScreen()
                }
            )
        }

        is BaseUIState.Success<*> -> {
            SuccessDialog(
                successMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    findPartnerViewModel.idleScreen()
                }
            )
        }

        is BaseUIState.NoInternetConnection -> {
            showBottomSheet = baseUIState.isOffline
        }

        is BaseUIState.Idle -> {
            Unit
        }
    }

    if (showBottomSheet) {
        OfflineBottomSheetCompo(
            show = showBottomSheet,
            onDismiss = {
                findPartnerViewModel.idleScreen()
            }
        )
    }
}

@Composable
private fun FindYourPartnerContent(
    findPartnerViewModel: FindPartnerViewModel,
    findPartnerUIStates: FindPartnerState
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {

            Column {

                var showCountryList by rememberSaveable {
                    mutableStateOf(false)
                }

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Find Your Partner",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center
                )
                Text(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    text = "Enter your partner number to connect",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(36.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                    PhoneNumberCompo(
                        findPartnerUIState = findPartnerUIStates,
                        findPartnerViewModel = findPartnerViewModel,
                        onShowCountryList = {
                            showCountryList = true
                        }
                    )

                    CountrySelectionBottomSheet(
                        modifier = Modifier,
                        countries = findPartnerUIStates.countryList,
                        show = showCountryList,
                        onCountrySelected = { countryModel ->
                            showCountryList = false
                            findPartnerViewModel.updateCountry(
                                code = countryModel.code ?: "",
                                prefix = countryModel.prefix ?: ""
                            )
                        }) {
                        showCountryList = false
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                PrimaryButtonCompo(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    label = "♡  Find Partner  ♡",
                    onClick = {
                        findPartnerViewModel.validatePhoneNumber()
                    }
                )
            }

        }
    }
}

@Composable
private fun PhoneNumberCompo(
    findPartnerUIState: FindPartnerState,
    findPartnerViewModel: FindPartnerViewModel,
    onShowCountryList: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        PhoneNumberCompo(
            modifier = Modifier.fillMaxWidth(),
            countryCode = findPartnerUIState.countryCode,
            countryPrefix = findPartnerUIState.countryPrefix,
            phoneNumber = findPartnerUIState.phoneNumber,
            isError = findPartnerUIState.validationErrorMsg.isEmpty(),
            updatePhoneNumber = { input ->
                if (input.all { it.isDigit() }) {
                    findPartnerViewModel.updatePhoneNumber(phoneNumber = input)
                }
            },
            onShowCountryList = {
                onShowCountryList()
            },
        )


        if (findPartnerUIState.validationErrorMsg.isNotEmpty()) {
            ErrorText(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                text = "Invalid Phone number format!"
            )
        }
    }

}

@Preview
@Composable
private fun PreviewFindYourScreen() {
    FindYourPartnerContent(
        findPartnerViewModel = FindPartnerViewModel(),
        findPartnerUIStates = FindPartnerState()
    )
}