package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.LabeledTextFieldCompo
import com.srisu.srisu.features.auth.presentation.components.ScreenTopIcon
import com.srisu.srisu.features.auth.presentation.state.Validation
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel

@Composable
fun AddNameScreen(
    authViewModel: AuthViewModel,
    onBackClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        val authUIStates by authViewModel.authUiState.collectAsState()
        val validationError = authUIStates.validationError

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

        LabeledTextFieldCompo(
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

    }
}


