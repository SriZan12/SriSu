package com.srisu.srisu.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srisu.srisu.theme.AppTheme

@Composable
fun PrimaryButtonCompo(
    modifier: Modifier,
    label: String,
    onClick: () -> Unit
) {
    AppTheme {
        Button(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            onClick = onClick
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PrimaryOutlinedButtonCompo(
    modifier: Modifier,
    label: String,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
    onClick: () -> Unit
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
    modifier: Modifier,
    label: String,
    backgroundColor: Color,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        onClick = onClick
    ) {
        Text(text = label, style = textStyle)
    }
}

@Composable
fun PrimaryTextButton(
    modifier: Modifier,
    label: String,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
    fontWeight: FontWeight = FontWeight.SemiBold,
    onClick: () -> Unit,
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