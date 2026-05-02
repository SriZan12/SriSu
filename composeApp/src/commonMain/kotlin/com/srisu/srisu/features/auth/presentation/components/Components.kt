package com.srisu.srisu.features.auth.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.RoundedPrimaryButtonCompo

@Composable
fun ProgressIndicator(
    modifier: Modifier,
    currentProgress: Float
) {

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = {
                currentProgress
            },
            gapSize = (-8).dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(8.dp)),
            trackColor = MaterialTheme.colorScheme.onSurface,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun ProgressIndicator(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isSelected = index < currentStep

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonProfileContainerCompo(
    modifier: Modifier = Modifier,
    buttonTitle: String = "Next",
    localFocusManager: FocusManager?,
    currentStep: Int,
    isPrimaryButtonEnabled: Boolean,
    onNavBack: () -> Unit,
    showNavBackIcon: Boolean = true,
    onClickPrimaryButton: () -> Unit,
    content: @Composable () -> Unit
) {

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                localFocusManager?.clearFocus()
            },
        topBar = {
            TopAppBar(
                title = {},
                modifier = Modifier.fillMaxWidth(),
                colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                navigationIcon = {
                    if (showNavBackIcon) {
                        IconButton(onClick = onNavBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate back",
                            )
                        }

                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            RoundedPrimaryButtonCompo(
                modifier = Modifier,
                title = buttonTitle,
                enabled = isPrimaryButtonEnabled,
                onClick = {
                    localFocusManager?.clearFocus()
                    onClickPrimaryButton()
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            ProgressIndicator(
                totalSteps = 6,
                currentStep = currentStep,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            content()

        }

    }
}

@Composable
fun ScreenTopIcon(
    color: Color,
    imageVector: ImageVector
) {
    Surface(
        modifier = Modifier.size(85.dp),
        shape = RoundedCornerShape(28.dp),
        color = color
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = "OTP message",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
