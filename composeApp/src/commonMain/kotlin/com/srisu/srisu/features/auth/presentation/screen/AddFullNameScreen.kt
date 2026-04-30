package com.srisu.srisu.features.auth.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.ErrorText
import com.srisu.srisu.components.RoundedButtonCompo
import com.srisu.srisu.features.auth.presentation.state.Validation
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel

@Composable
fun AddFullNameCompo(
    authViewModel: AuthViewModel,
    onBackClick: () -> Unit = {}
) {
    val localFocusManager: FocusManager = LocalFocusManager.current
    val authUIStates by authViewModel.authUiState.collectAsState()
    val validationError = authUIStates.validationError

    val isButtonEnabled =
        authUIStates.fullName.isNotBlank() &&
                authUIStates.username.isNotBlank() &&
                !validationError.isFullName &&
                !validationError.isUserName

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                localFocusManager.clearFocus()
            },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            RoundedButtonCompo(
                modifier = Modifier,
                title = "Looks good, let's go",
                enabled = isButtonEnabled,
                onClick = {
                    localFocusManager.clearFocus()

                    if (authViewModel.isFullNameValid() && authViewModel.isUsernameValid()) {
                        authViewModel.navigateNextScreen()
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .imePadding() // only content moves/scrolls above keyboard
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            SriSuProgressIndicator(
                totalSteps = 6,
                currentStep = 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))


            ProfileIdentityIcon()


            Text(
                text = "What do we call you?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )


            Text(
                text = "Your name is yours — your username is\nyour vibe.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight
            )


            SriSuLabeledTextField(
                label = "Full name",
                value = authUIStates.fullName,
                placeholder = "e.g. Thomas Shelby",
                isError = validationError.isFullName,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                onValueChange = {
                    authViewModel.updateFullName(name = it)
                    authViewModel.updateValidationError(Validation(isFullName = false))
                }
            )

            if (validationError.isFullName) {
                ErrorText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    text = validationError.validationMessage
                )
            }


            SriSuLabeledTextField(
                label = "Username",
                value = authUIStates.username,
                placeholder = "@ tommy",
                isError = validationError.isUserName,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
                onValueChange = {
                    authViewModel.updateUserName(username = it)
                    authViewModel.updateValidationError(Validation(isUserName = false))
                }
            )

            if (validationError.isUserName) {
                ErrorText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    text = validationError.validationMessage
                )
            }

        }
    }
}

@Composable
fun SriSuProgressIndicator(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isSelected = index < currentStep

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
            )
        }
    }
}


@Composable
private fun ProfileIdentityIcon() {
    Surface(
        modifier = Modifier.size(85.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "OTP message",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun SriSuLabeledTextField(
    label: String,
    value: String,
    placeholder: String,
    isError: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
            },
            singleLine = true,
            isError = isError,
            shape = RoundedCornerShape(28.dp),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                errorBorderColor = MaterialTheme.colorScheme.error,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
