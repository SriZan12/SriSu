package com.srisu.srisu.features.auth.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.country_flag

@Composable
fun PhoneNumberScreen(
    authViewModel: AuthViewModel
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PhoneNumberBottomSection(
                enabled = isButtonEnabled,
                onSendCodeClick = onSendCodeClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            SriSuHeader()

            CallIconBox()

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
fun CallIconBox() {
    Surface(
        modifier = Modifier.size(85.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Call,
                contentDescription = "Phone number verification",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
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

@Composable
private fun CountryCodeSelector(
    countryCode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 18.dp, horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.country_flag),
                contentDescription = "Selected country flag",
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = countryCode,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Change country code",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PhoneNumberTextField(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = { value ->
            onPhoneNumberChange(value.filter { it.isDigit() })
        },
        modifier = modifier.height(64.dp),
        placeholder = {
            Text(
                text = "Phone number",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(32.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun PhoneNumberBottomSection(
    enabled: Boolean,
    onSendCodeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp)
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onSendCodeClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(
                text = "Send the code",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = buildAnnotatedString {
                append("By continuing you agree to our ")

                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("Terms of Service")
                }

                append(" and ")

                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("Privacy")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}