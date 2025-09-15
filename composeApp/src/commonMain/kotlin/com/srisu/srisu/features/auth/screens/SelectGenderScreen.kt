package com.srisu.srisu.features.auth.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.ErrorText
import com.srisu.srisu.components.RadioButtonCompo
import com.srisu.srisu.features.auth.common.CommonAuthContainerCompo
import com.srisu.srisu.features.auth.common.InfoText
import com.srisu.srisu.features.auth.common.TitleText
import com.srisu.srisu.features.auth.state.AuthUIStates
import com.srisu.srisu.features.auth.state.Validation
import com.srisu.srisu.features.auth.vm.AuthViewModel

enum class Gender {
    NONE, MALE, FEMALE, OTHERS
}

@Composable
fun SelectGenderCompo(authViewModel: AuthViewModel) {
    CommonAuthContainerCompo(buttonTitle = "Next", onClickPrimaryButton = {
        if (authViewModel.isGenderValid()) {
            authViewModel.navigateNextScreen()
        }
    }) {

        val authUiState by authViewModel.authUiState.collectAsState()

        TitleText(
            modifier = Modifier.fillMaxWidth(),
            title = "Please select your gender"
        )

        InfoText(
            modifier = Modifier.fillMaxWidth(),
            info = "We need this to verify your age and personalize your experience."
        )

        Spacer(modifier = Modifier.height(24.dp))

        GenderCompo(
            authViewModel = authViewModel,
            authUiState = authUiState
        )

        val validationError = authUiState.validationError

        if (validationError.isGender) {
            ErrorText(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                text = validationError.validationMessage
            )
        }
    }
}

@Composable
private fun GenderCompo(
    authViewModel: AuthViewModel,
    authUiState: AuthUIStates
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        RadioButtonCompo(
            modifier = Modifier,
            label = "Male",
            isSelected = authUiState.gender == Gender.MALE,
        ) {
            authViewModel.updateGender(Gender.MALE)
            authViewModel.updateValidationError(validation = Validation(isGender = false))
        }

        RadioButtonCompo(
            modifier = Modifier,
            label = "Female",
            isSelected = authUiState.gender == Gender.FEMALE,
        ) {
            authViewModel.updateGender(Gender.FEMALE)
            authViewModel.updateValidationError(validation = Validation(isGender = false))

        }

        RadioButtonCompo(
            modifier = Modifier,
            label = "Others",
            isSelected = authUiState.gender == Gender.OTHERS,
        ) {
            authViewModel.updateGender(Gender.OTHERS)
            authViewModel.updateValidationError(validation = Validation(isGender = false))
        }
    }
}