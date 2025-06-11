package com.srisu.srisu.features.auth.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.CommonBottomSheetCompo
import com.srisu.srisu.components.HighlightedTextComponent
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.PrimaryOutlinedButtonCompo
import com.srisu.srisu.features.auth.common.CommonAuthContainerCompo
import com.srisu.srisu.features.auth.common.InfoText
import com.srisu.srisu.features.auth.common.TitleText
import com.srisu.srisu.features.auth.state.AuthUIStates
import com.srisu.srisu.features.auth.vm.AuthViewModel
import com.srisu.srisu.utils.DateTimeUtils.formatLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import network.chaintech.kmp_date_time_picker.ui.datepicker.WheelDatePickerComponent.WheelDatePicker
import network.chaintech.kmp_date_time_picker.utils.WheelPickerDefaults
import network.chaintech.kmp_date_time_picker.utils.now


@Composable
fun AddDOBCompo(authViewModel: AuthViewModel) {

    var showAgeConfirmationBottomSheet by rememberSaveable { mutableStateOf(false) }

    CommonAuthContainerCompo(buttonTitle = "Next", onClick = {
        showAgeConfirmationBottomSheet = true
    }) {

        val authUIStates by authViewModel.authUiState.collectAsState()

        TitleText(
            modifier = Modifier.fillMaxWidth(),
            title = "What's your date of birth?"
        )

        InfoText(
            modifier = Modifier.fillMaxWidth(),
            info = "We need this to verify your age and personalize your experience."
        )

        Spacer(modifier = Modifier.height(24.dp))

        val starDate = if (authUIStates.dob.isEmpty()) {
            LocalDate.now().minus(value = 15, unit = DateTimeUnit.YEAR)
        } else {
            LocalDate.parse(authUIStates.dob)
        }

        WheelDatePicker(
            modifier = Modifier,
            height = 250.dp,
            title = "Date of Birth",
            hideHeader = true,
            startDate = starDate,
            yearsRange = (1950..(LocalDate.now().year - 15)),
            dateTextStyle = MaterialTheme.typography.titleSmall,
            dateTextColor = MaterialTheme.colorScheme.primary,
            selectorProperties = WheelPickerDefaults.selectorProperties(borderColor = Color.White),
            onDateChangeListener = { date ->
                val formattedDate = formatLocalDate(date = date)
                authViewModel.updateDOB(dob = formattedDate)
            },
        )


        AgeConfirmationBottomSheet(
            onDismiss = {
                showAgeConfirmationBottomSheet = false
            },
            authUIState = authUIStates,
            showAgeConfirmation = showAgeConfirmationBottomSheet,
            onConfirmed = {
                showAgeConfirmationBottomSheet = false
                authViewModel.navigateNextScreen()
            },
            onDeclined = {
                showAgeConfirmationBottomSheet = false
            }
        )


    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgeConfirmationBottomSheet(
    onDismiss: () -> Unit,
    authUIState: AuthUIStates,
    showAgeConfirmation: Boolean,
    onConfirmed: () -> Unit,
    onDeclined: () -> Unit
) {
    CommonBottomSheetCompo(onDismiss = {
        onDismiss()
    }, show = showAgeConfirmation) {

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Confirm your age",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            val fullText =
                "You are ${authUIState.age} years old based on your birthdate. This can’t be changed later."
            val highlightedText = "${authUIState.age} years"

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
                label = "Edit Date"
            ) {
                onDeclined()
            }


        }
    }
}

