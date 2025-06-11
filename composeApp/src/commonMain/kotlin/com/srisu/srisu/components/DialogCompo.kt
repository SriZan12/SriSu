package com.srisu.srisu.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.alertTitle


@Composable
fun ErrorDialog(
    title: String = stringResource(Res.string.alertTitle),
    errorMessage: String,
    show: Boolean,
    onDismiss: () -> Unit,
) {

    if (show) {
        Dialog(
            onDismissRequest = { onDismiss() },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = true
            )
        ) {
            Card(
                modifier = Modifier,
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    CustomButtonCompo(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)
                            .padding(top = 22.dp),
                        label = "Close",
                        backgroundColor = MaterialTheme.colorScheme.onErrorContainer,
                        textStyle =
                            MaterialTheme.typography.titleMedium.copy(color = Color.White),
                        onClick = {
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}