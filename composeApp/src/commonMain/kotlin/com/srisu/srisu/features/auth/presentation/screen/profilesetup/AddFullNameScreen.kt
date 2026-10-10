package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
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

    when (val state = authUIState.baseUIState) {
        is com.srisu.srisu.baseframework.BaseUIState.Error -> com.srisu.srisu.components.ErrorDialog(title = state.errorType, errorMessage = state.message, show = true, onDismiss = authViewModel::idleScreen)
        is com.srisu.srisu.baseframework.BaseUIState.Loading -> com.srisu.srisu.components.LoadingScrim()
        else -> Unit
    }
    CommonProfileContainerCompo(
        modifier = Modifier,
        buttonTitle = stringResource(Res.string.auth_next),
        localFocusManager = localFocusManager,
        currentStep = authUIState.currentProgressStep,
        isPrimaryButtonEnabled = authUIState.fullName.isNotBlank() && authUIState.username.isNotBlank() && authUIState.baseUIState !is com.srisu.srisu.baseframework.BaseUIState.Loading,
        showNavBackIcon = false,
        onNavBack = {},
        onClickPrimaryButton = {
            authViewModel.saveName()
        },
    ) {

        ScreenTopIcon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(Res.string.auth_name_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,

            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(Res.string.auth_name_subtitle),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.titleLarge.lineHeight
        )

        LabeledTextFieldCompo(
            label = stringResource(Res.string.auth_full_name),
            value = authUIState.fullName,
            placeholder = stringResource(Res.string.auth_name_placeholder),
            isError = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
            onValueChange = {
                authViewModel.updateFullName(name = it)
                authViewModel.updateValidationError(Validation(isFullName = false))
            }
        )

        LabeledTextFieldCompo(
            label = stringResource(Res.string.auth_username),
            value = authUIState.username,
            placeholder = stringResource(Res.string.auth_username_placeholder),
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


