package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import com.srisu.srisu.theme.spacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.features.auth.presentation.components.CommonProfileContainerCompo
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

enum class Gender {
    NONE, MALE, FEMALE,
}

@Composable
fun SelectGenderScreen(
    authViewModel: AuthViewModel
) {
    val authUiState by authViewModel.authUiState.collectAsState()
    val loading = authUiState.baseUIState is BaseUIState.Loading

    when (val state = authUiState.baseUIState) {
        is BaseUIState.Error -> ErrorDialog(title = state.errorType, errorMessage = state.message,
            show = true, onDismiss = authViewModel::idleScreen)
        is BaseUIState.Loading -> LoadingScrim()
        else -> Unit
    }

    CommonProfileContainerCompo(
        modifier = Modifier,
        buttonTitle = stringResource(Res.string.auth_next),
        localFocusManager = null,
        currentStep = authUiState.currentProgressStep,
        isPrimaryButtonEnabled = authUiState.gender != Gender.NONE && !loading,
        onNavBack = {
            authViewModel.navigateProfileBack()
        },
        onClickPrimaryButton = {
            authViewModel.saveGender()
        },
    ) {

        Text(
            text = stringResource(Res.string.auth_gender_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,

            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(Res.string.auth_gender_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))
        GenderPrimaryOptions(
            selectedGender = authUiState.gender,
            enabled = !loading,
            onGenderSelected = { gender ->
                authViewModel.updateGender(gender)
            }
        )
    }

}

@Composable
private fun GenderPrimaryOptions(
    selectedGender: Gender?,
    enabled: Boolean,
    onGenderSelected: (Gender) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GenderCard(
            title = stringResource(Res.string.auth_gender_woman),
            enabled = enabled,
            isSelected = selectedGender == Gender.FEMALE,
            onClick = { onGenderSelected(Gender.FEMALE) },
            modifier = Modifier.weight(1f)
        )

        GenderCard(
            title = stringResource(Res.string.auth_gender_man),
            enabled = enabled,
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
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(180.dp).semantics {
            selected = isSelected
            role = Role.RadioButton
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
                .padding(horizontal = MaterialTheme.spacing.medium),
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
