package com.srisu.srisu.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srisu.srisu.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun PrimaryButtonCompo(
    modifier: Modifier = Modifier,
    label: String = "Button",
    onClick: () -> Unit = {}
) {
    AppTheme {
        Button(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            onClick = onClick,
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
@Preview
fun RoundedPrimaryButtonCompo(
    modifier: Modifier,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(34.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PrimaryOutlinedButtonCompo(
    modifier: Modifier = Modifier,
    label: String = "Button",
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
    onClick: () -> Unit = {}
) {
    AppTheme {
        OutlinedButton(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary),
            onClick = onClick
        ) {
            Text(text = label, style = textStyle)
        }
    }
}

@Composable
fun CustomButtonCompo(
    modifier: Modifier = Modifier,
    label: String = "Button",
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
    onClick: () -> Unit = {}
) {
    Button(
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        onClick = onClick
    ) {
        Text(text = label, style = textStyle)
    }
}

@Composable
fun PrimaryTextButton(
    modifier: Modifier = Modifier,
    label: String = "Button",
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
    fontWeight: FontWeight = FontWeight.SemiBold,
    onClick: () -> Unit = {},
) {
    TextButton(
        modifier = modifier,
        onClick = {
            onClick()
        }
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}