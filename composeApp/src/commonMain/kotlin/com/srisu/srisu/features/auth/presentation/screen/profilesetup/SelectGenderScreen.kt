package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.auth.presentation.components.CommonProfileContainerCompo
import com.srisu.srisu.features.auth.presentation.state.Validation
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel

enum class Gender {
    NONE, MALE, FEMALE, OTHERS
}

@Composable
fun SelectGenderScreen(
    authViewModel: AuthViewModel
) {
    val authUiState by authViewModel.authUiState.collectAsState()

    CommonProfileContainerCompo(
        modifier = Modifier,
        buttonTitle = "Next",
        localFocusManager = null,
        currentStep = authUiState.currentProgressStep,
        isPrimaryButtonEnabled = authViewModel.isGenderValid(),
        onNavBack = {
            authViewModel.navigateBack()
        },
        onClickPrimaryButton = {
            authViewModel.navigateNextScreen(isIncrease = true)
        },
    ) {

        Text(
            text = "How do you identify?",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "No boxes here — just helping us\npersonalise things for you.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))
        GenderPrimaryOptions(
            selectedGender = authUiState.gender,
            onGenderSelected = { gender ->
                authViewModel.updateGender(gender)
                authViewModel.updateValidationError(
                    validation = Validation(isGender = false)
                )
            }
        )
    }

}

@Composable
private fun GenderPrimaryOptions(
    selectedGender: Gender?,
    onGenderSelected: (Gender) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GenderCard(
            title = "Woman",
            isSelected = selectedGender == Gender.FEMALE,
            onClick = { onGenderSelected(Gender.FEMALE) },
            modifier = Modifier.weight(1f)
        )

        GenderCard(
            title = "Man",
            isSelected = selectedGender == Gender.MALE,
            onClick = { onGenderSelected(Gender.MALE) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GenderCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(28.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GenderLineIcon(
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GenderLineIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.07f
        val centerX = size.width / 2f

        drawCircle(
            color = color,
            radius = size.minDimension * 0.18f,
            center = Offset(centerX, size.height * 0.28f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.23f, size.height * 0.55f),
            size = Size(size.width * 0.54f, size.height * 0.48f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        drawLine(
            color = color,
            start = Offset(size.width * 0.23f, size.height * 0.78f),
            end = Offset(size.width * 0.23f, size.height * 0.95f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = Offset(size.width * 0.77f, size.height * 0.78f),
            end = Offset(size.width * 0.77f, size.height * 0.95f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}