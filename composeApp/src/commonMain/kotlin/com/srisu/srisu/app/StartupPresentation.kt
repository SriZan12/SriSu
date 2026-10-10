package com.srisu.srisu.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

/** Replaceable runtime presentation. It never chooses routes or performs I/O. */
@Composable
fun StartupPresentation(error: String?, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(MaterialTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SriSu", style = MaterialTheme.typography.headlineLarge)
            if (error == null) CircularProgressIndicator()
            else {
                Text(error, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onRetry) { Text(stringResource(Res.string.auth_retry)) }
            }
        }
    }
}
