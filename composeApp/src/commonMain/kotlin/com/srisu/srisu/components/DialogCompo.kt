package com.srisu.srisu.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srisu.srisu.theme.backgroundGray
import com.srisu.srisu.theme.success
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.alertTitle
import srisu.composeapp.generated.resources.check_sticker


@Composable
fun ErrorDialog(
    title: String? = stringResource(Res.string.alertTitle),
    errorMessage: String? = "Error",
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    CustomButtonCompo(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)
                            .padding(top = 22.dp),
                        label = "Close",
                        backgroundColor = MaterialTheme.colorScheme.primary,
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

@Composable
fun SuccessDialog(
    title: String = "SUCCESS",
    successMessage: String,
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
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
                        text = successMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    CustomButtonCompo(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)
                            .padding(top = 22.dp),
                        label = "Close",
                        backgroundColor = success,
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

@Composable
fun RequestSentDialog(
    title: String = "Request Sent",
    successMessage: String,
    onDismiss: () -> Unit
) {
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                Image(
                    painter = painterResource(Res.drawable.check_sticker),
                    modifier = Modifier.size(44.dp),
                    contentDescription = "success_checked_sticker"
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = successMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                CustomButtonCompo(
                    modifier = Modifier.wrapContentWidth().height(IntrinsicSize.Max)
                        .padding(top = 22.dp),
                    label = "Okay",
                    backgroundColor = MaterialTheme.colorScheme.primary,
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