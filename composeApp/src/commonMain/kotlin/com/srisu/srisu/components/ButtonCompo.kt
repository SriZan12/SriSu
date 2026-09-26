package com.srisu.srisu.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.srisu.srisu.theme.SriSuComponentTokens
import com.srisu.srisu.theme.pill
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.theme.transparent

/** The seven variants and three sizes documented in Figma's Buttons section. */
enum class SriSuButtonVariant { Primary, Secondary, Tertiary, Outline, Ghost, Destructive, SoftDestructive }
enum class SriSuButtonSize { Small, Medium, Large }

@Composable
fun SriSuButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: SriSuButtonVariant = SriSuButtonVariant.Primary,
    size: SriSuButtonSize = SriSuButtonSize.Medium,
    textStyle: TextStyle? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = when (variant) {
        SriSuButtonVariant.Primary -> colors.primary
        SriSuButtonVariant.Secondary -> colors.surface
        SriSuButtonVariant.Tertiary -> colors.surfaceVariant
        SriSuButtonVariant.Outline, SriSuButtonVariant.Ghost -> colors.transparent
        SriSuButtonVariant.Destructive -> colors.error
        SriSuButtonVariant.SoftDestructive -> colors.error.copy(alpha = SriSuComponentTokens.softDestructiveAlpha)
    }
    val foreground = when (variant) {
        SriSuButtonVariant.Primary -> colors.onPrimary
        SriSuButtonVariant.Destructive -> colors.onError
        SriSuButtonVariant.SoftDestructive -> colors.error
        else -> colors.onSurface
    }
    val height = when (size) {
        SriSuButtonSize.Small -> MaterialTheme.spacing.buttonSmall
        SriSuButtonSize.Medium -> MaterialTheme.spacing.buttonMedium
        SriSuButtonSize.Large -> MaterialTheme.spacing.buttonLarge
    }
    val horizontal = if (size == SriSuButtonSize.Small) MaterialTheme.spacing.compact else MaterialTheme.spacing.medium
    val alpha = if (enabled) 1f else SriSuComponentTokens.disabledAlpha
    Button(
        modifier = modifier.heightIn(min = height),
        enabled = enabled,
        onClick = onClick,
        shape = MaterialTheme.shapes.pill,
        border = if (variant == SriSuButtonVariant.Secondary || variant == SriSuButtonVariant.Outline)
            BorderStroke(MaterialTheme.spacing.hairline, colors.outlineVariant.copy(alpha = alpha)) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = foreground,
            disabledContainerColor = container.copy(alpha = container.alpha * SriSuComponentTokens.disabledAlpha),
            disabledContentColor = foreground.copy(alpha = SriSuComponentTokens.disabledAlpha),
        ),
        contentPadding = PaddingValues(horizontal = horizontal, vertical = MaterialTheme.spacing.tiny),
    ) {
        Text(label, style = textStyle ?: if (size == SriSuButtonSize.Large)
            MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PrimaryButtonCompo(modifier: Modifier = Modifier, label: String = "Button", onClick: () -> Unit = {}) =
    SriSuButton(label, onClick, modifier)

@Composable
fun RoundedPrimaryButtonCompo(modifier: Modifier, title: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = MaterialTheme.spacing.gutter, vertical = MaterialTheme.spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SriSuButton(title, onClick, Modifier.fillMaxWidth(), enabled, size = SriSuButtonSize.Large)
    }
}

@Composable
fun PrimaryOutlinedButtonCompo(
    modifier: Modifier = Modifier,
    label: String = "Button",
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    onClick: () -> Unit = {},
) = SriSuButton(label, onClick, modifier, variant = SriSuButtonVariant.Secondary, textStyle = textStyle)

@Composable
fun CustomButtonCompo(
    modifier: Modifier = Modifier,
    label: String = "Button",
    shape: Shape = MaterialTheme.shapes.pill,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    onClick: () -> Unit = {},
) {
    Button(
        modifier = modifier, shape = shape, onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
    ) { Text(label, style = textStyle) }
}

@Composable
fun PrimaryTextButton(
    modifier: Modifier = Modifier,
    label: String = "Button",
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    fontWeight: FontWeight = textStyle.fontWeight ?: FontWeight.Bold,
    onClick: () -> Unit = {},
) {
    TextButton(modifier = modifier, onClick = onClick, shape = MaterialTheme.shapes.pill) {
        Text(label, style = textStyle, fontWeight = fontWeight)
    }
}
