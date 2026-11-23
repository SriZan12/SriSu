package com.srisu.srisu.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import com.srisu.srisu.theme.NavigationBackIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.auth_back

@Composable
fun NavigationBackButton(onBack: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, tint: Color = LocalContentColor.current,
    label: String = stringResource(Res.string.auth_back)) {
    IconButton(onClick = onBack, modifier = modifier.size(48.dp), enabled = enabled) {
        Icon(NavigationBackIcon, label, Modifier.size(24.dp), tint = tint)
    }
}
