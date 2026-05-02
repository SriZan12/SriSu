package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import com.srisu.srisu.features.auth.presentation.components.CommonProfileContainerCompo
import com.srisu.srisu.features.auth.presentation.state.RelationshipSituation
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel

enum class SituationIconType {
    Solo,
    Couple
}

@Composable
fun SelectRelationshipScreen(
    authViewModel: AuthViewModel
) {
    val authUiState by authViewModel.authUiState.collectAsState()
    val selectedSituation = authUiState.relationshipSituation

    CommonProfileContainerCompo(
        modifier = Modifier,
        buttonTitle = "Looks good, let's go",
        localFocusManager = null,
        currentStep = authUiState.currentProgressStep,
        isPrimaryButtonEnabled = authViewModel.isRelationshipValid(),
        onNavBack = {
            authViewModel.navigateBack()
        },
        onClickPrimaryButton = {
            authViewModel.navigateNextScreen(isIncrease = true)
        },
    ) {


        Text(
            text = "What's your\nsituation?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )


        Text(
            text = "SriSu is built for everyone — whether\nyou're flying solo or partnered up.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )


        SituationOptionCard(
            title = "Single",
            subtitle = "Looking for meaningful connections",
            isSelected = selectedSituation == RelationshipSituation.SINGLE,
            iconType = SituationIconType.Solo,
            onClick = {
                authViewModel.updateRelationshipSituation(RelationshipSituation.SINGLE)
            }
        )


        SituationOptionCard(
            title = "Mingled",
            subtitle = "Want to grow closer with my partner",
            isSelected = selectedSituation == RelationshipSituation.COUPLE,
            iconType = SituationIconType.Couple,
            onClick = {
                authViewModel.updateRelationshipSituation(RelationshipSituation.COUPLE)
            }
        )
    }
}

@Composable
private fun SituationOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    iconType: SituationIconType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        shape = RoundedCornerShape(28.dp),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SituationIconBox(
                iconType = iconType
            )

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 28.sp
                )
            }
        }
    }
}

@Composable
private fun SoloLineIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.07f
        val centerX = size.width / 2f

        drawCircle(
            color = color,
            radius = size.minDimension * 0.18f,
            center = Offset(centerX * 0.85f, size.height * 0.35f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, size.height * 0.58f),
            size = Size(size.width * 0.54f, size.height * 0.46f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        drawCircle(
            color = color,
            radius = size.minDimension * 0.045f,
            center = Offset(size.width * 0.82f, size.height * 0.20f)
        )

        drawLine(
            color = color,
            start = Offset(size.width * 0.90f, size.height * 0.10f),
            end = Offset(size.width * 0.90f, size.height * 0.27f),
            strokeWidth = strokeWidth * 0.65f,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = Offset(size.width * 0.82f, size.height * 0.185f),
            end = Offset(size.width * 0.98f, size.height * 0.185f),
            strokeWidth = strokeWidth * 0.65f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SituationIconBox(
    iconType: SituationIconType
) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (iconType) {
                SituationIconType.Solo -> SoloLineIcon(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                SituationIconType.Couple -> CoupleLineIcon(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CoupleLineIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.07f

        drawCircle(
            color = color,
            radius = size.minDimension * 0.16f,
            center = Offset(size.width * 0.36f, size.height * 0.36f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        drawCircle(
            color = color,
            radius = size.minDimension * 0.16f,
            center = Offset(size.width * 0.64f, size.height * 0.36f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.08f, size.height * 0.60f),
            size = Size(size.width * 0.48f, size.height * 0.42f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.44f, size.height * 0.60f),
            size = Size(size.width * 0.48f, size.height * 0.42f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}