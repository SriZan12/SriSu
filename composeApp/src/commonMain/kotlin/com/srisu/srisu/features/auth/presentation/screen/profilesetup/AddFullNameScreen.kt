package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.srisu.srisu.components.LabeledTextFieldCompo
import com.srisu.srisu.features.auth.presentation.components.CommonProfileContainerCompo
import com.srisu.srisu.features.auth.presentation.components.ScreenTopIcon
import com.srisu.srisu.features.auth.presentation.state.Validation
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel

@Composable
fun AddNameScreen(
    authViewModel: AuthViewModel,
    localFocusManager: FocusManager,
) {

    val authUIState by authViewModel.authUiState.collectAsState()

    CommonProfileContainerCompo(
        modifier = Modifier,
        buttonTitle = "Next",
        localFocusManager = localFocusManager,
        currentStep = authUIState.currentProgressStep,
        isPrimaryButtonEnabled = authViewModel.isFullNameValid() && authViewModel.isUsernameValid(),
        showNavBackIcon = false,
        onNavBack = {},
        onClickPrimaryButton = {
            authViewModel.navigateNextScreen(isIncrease = true)
        },
    ) {

        ScreenTopIcon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            color = MaterialTheme.colorScheme.primary
        )

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

        LabeledTextFieldCompo(
            label = "Full name",
            value = authUIState.fullName,
            placeholder = "e.g. Thomas Shelby",
            isError = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
            onValueChange = {
                authViewModel.updateFullName(name = it)
                authViewModel.updateValidationError(Validation(isFullName = false))
            }
        )

        LabeledTextFieldCompo(
            label = "Username",
            value = authUIState.username,
            placeholder = "@ tommy",
            isError = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
            onValueChange = {
                authViewModel.updateUserName(username = it)
                authViewModel.updateValidationError(Validation(isUserName = false))
            }
        )
    }

}


