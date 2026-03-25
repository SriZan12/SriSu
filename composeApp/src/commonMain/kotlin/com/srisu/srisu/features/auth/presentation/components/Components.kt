package com.srisu.srisu.features.auth.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.PrimaryButtonCompo

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
fun CommonAuthContainerCompo(
    modifier: Modifier = Modifier,
    buttonTitle: String = "Next",
    onClickPrimaryButton: () -> Unit,
    onClickScreenContent: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().clickable(
            onClick = onClickScreenContent,
            indication = null,
            interactionSource = MutableInteractionSource()
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }

        PrimaryButtonCompo(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 24.dp)
                .align(Alignment.BottomCenter),
            label = buttonTitle
        ) {
            onClickPrimaryButton()
        }
    }
}

@Composable
fun TitleText(
    modifier: Modifier,
    title: String
) {
    Text(
        modifier = modifier,
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
        color = Color.Black
    )
}

@Composable
fun InfoText(
    modifier: Modifier,
    info: String,
) {
    Text(
        modifier = modifier,
        text = info,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Black
    )
}
