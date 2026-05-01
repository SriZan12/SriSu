package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.CommonBottomSheetCompo
import com.srisu.srisu.components.HighlightedTextComponent
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.PrimaryOutlinedButtonCompo
import com.srisu.srisu.features.auth.presentation.components.CommonAuthContainerCompo
import com.srisu.srisu.features.auth.presentation.components.InfoText
import com.srisu.srisu.features.auth.presentation.components.ScreenTopIcon
import com.srisu.srisu.features.auth.presentation.components.TitleText
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.DateTimeUtils.formatLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import network.chaintech.kmp_date_time_picker.ui.datepicker.WheelDatePickerComponent.WheelDatePicker
import network.chaintech.kmp_date_time_picker.utils.WheelPickerDefaults
import network.chaintech.kmp_date_time_picker.utils.now

@Composable
fun AddDOBScreen(
    authViewModel: AuthViewModel,
    onBackClick: () -> Unit = {}
) {
    var showAgeConfirmationBottomSheet by rememberSaveable { mutableStateOf(false) }
    val authUIStates by authViewModel.authUiState.collectAsState()

    val startDate = remember(authUIStates.dob) {
        if (authUIStates.dob.isEmpty()) {
            LocalDate.now().minus(value = 15, unit = DateTimeUnit.YEAR)
        } else {
            LocalDate.parse(authUIStates.dob)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        ScreenTopIcon(
            imageVector = Icons.Outlined.Cake,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "When's your\nbirthday?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "We use this to find your zodiac sign —\nsomething special is coming.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        DOBWheelPickerCard(
            startDate = startDate,
            onDateChanged = { date ->
                authViewModel.updateDOB(dob = formatLocalDate(date = date))
            }
        )


        Text(
            text = "Your zodiac reveal is one tap away ✦",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }

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

@Composable
private fun DOBWheelPickerCard(
    startDate: LocalDate,
    onDateChanged: (LocalDate) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        WheelDatePicker(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
            height = 180.dp,
            title = "Date of Birth",
            hideHeader = true,
            startDate = startDate,
            yearsRange = 1980..(LocalDate.now().year - 15),
            defaultDateTextStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                fontWeight = FontWeight.SemiBold
            ),
            selectedDateTextStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            selectorProperties = WheelPickerDefaults.selectorProperties(
                enabled = true,
                borderColor = MaterialTheme.colorScheme.primary
            ),
            onDateChangeListener = onDateChanged
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
    CommonBottomSheetCompo(
        onDismiss = onDismiss,
        show = showAgeConfirmation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Confirm your age",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            val fullText =
                "You are ${authUIState.age} years old based on your birthdate. This can’t be changed later."
            val highlightedText = "${authUIState.age} years"

            HighlightedTextComponent(
                fullText = fullText,
                highlightedText = highlightedText
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onConfirmed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Yes, looks good",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDeclined,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Text(
                    text = "Edit Date",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}