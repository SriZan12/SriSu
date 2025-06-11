package com.srisu.srisu.features.auth.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.ErrorText
import com.srisu.srisu.components.OutlinedTextFieldCompo
import com.srisu.srisu.features.auth.common.CommonAuthContainerCompo
import com.srisu.srisu.features.auth.common.InfoText
import com.srisu.srisu.features.auth.common.TitleText
import com.srisu.srisu.features.auth.state.Validation
import com.srisu.srisu.features.auth.vm.AuthViewModel

@Composable
fun AddFullNameCompo(authViewModel: AuthViewModel) {
    CommonAuthContainerCompo(
        buttonTitle = "Next",
        onClick = {
            if (authViewModel.isFullNameValid() && authViewModel.isUsernameValid()) {
                authViewModel.navigateNextScreen()
            }
        }
    ) {

        val authUIStates by authViewModel.authUiState.collectAsState()

        TitleText(
            modifier = Modifier.fillMaxWidth(),
            title = "What's your name?"
        )

        InfoText(
            modifier = Modifier.fillMaxWidth(),
            info = "Please enter your name. You won’t be able to change it later."
        )

        Spacer(modifier = Modifier.height(12.dp))

        val validationError = authUIStates.validationError

        OutlinedTextFieldCompo(
            modifier = Modifier.fillMaxWidth(),
            value = authUIStates.fullName,
            isError = validationError.isFullName,
            textStyle = MaterialTheme.typography.titleMedium,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions.Default,
            keyboardType = KeyboardType.Text,
            placeholder = "Enter your full name",
            onValueChange = {
                authViewModel.updateFullName(name = it)
                authViewModel.updateValidationError(Validation(isFullName = false))
            }
        )

        if (validationError.isFullName) {
            ErrorText(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                text = validationError.validationMessage
            )
        }

        Spacer(modifier = Modifier.height(24.dp))


        TitleText(
            modifier = Modifier.fillMaxWidth(),
            title = "What's your Username?"
        )

        InfoText(
            modifier = Modifier.fillMaxWidth(),
            info = "How would you like to be called?"
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextFieldCompo(
            modifier = Modifier.fillMaxWidth(),
            value = authUIStates.username,
            isError = validationError.isUserName,
            textStyle = MaterialTheme.typography.titleMedium,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions.Default,
            keyboardType = KeyboardType.Text,
            placeholder = "Enter your username",
            onValueChange = {
                authViewModel.updateUserName(username = it)
                authViewModel.updateValidationError(Validation(isUserName = false))
            }
        )

        if (validationError.isUserName) {
            ErrorText(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                text = validationError.validationMessage
            )
        }

    }
}

