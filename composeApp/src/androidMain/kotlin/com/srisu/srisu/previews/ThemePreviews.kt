package com.srisu.srisu.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.srisu.srisu.components.OutlinedTextFieldCompo
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonVariant
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.theme.spacing

@Preview(name = "SriSu · Light", showBackground = true)
@Composable
private fun SriSuLightPreview() = ThemeSpecimen(false)

@Preview(name = "SriSu · Dark", showBackground = true)
@Composable
private fun SriSuDarkPreview() = ThemeSpecimen(true)

@Preview(name = "SriSu · Serif pairing", showBackground = true)
@Composable
private fun SriSuSerifPreview() = ThemeSpecimen(false, true)

@Preview(name = "SriSu · Large text", showBackground = true, fontScale = 1.5f)
@Composable
private fun SriSuLargeTextPreview() = ThemeSpecimen(false)

@Composable
private fun ThemeSpecimen(dark: Boolean, serif: Boolean = false) {
    SriSuTheme(darkTheme = dark, useSerifHeadings = serif) {
        Surface {
            Column(Modifier.padding(MaterialTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text("Better together.", style = MaterialTheme.typography.headlineLarge)
                Text("Share moments and plans with your partner.", style = MaterialTheme.typography.bodyLarge)
                SriSuButtonVariant.entries.forEach { variant ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                        SriSuButton(variant.name, {}, variant = variant)
                        SriSuButton("Disabled", {}, variant = variant, enabled = false)
                    }
                }
                OutlinedTextFieldCompo(value = "Salil", placeholder = "Your name", onValueChange = {})
                OutlinedTextFieldCompo(value = "salil@", placeholder = "Email address", isError = true, onValueChange = {})
            }
        }
    }
}
