package com.srisu.srisu.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.srisu.srisu.components.RequestSentDialog
import com.srisu.srisu.theme.SriSuTheme

@Composable
@Preview(showBackground = true)
fun PreviewPrimaryButton() {
    SriSuTheme {
        RequestSentDialog(
            title = "Request sent!",
            successMessage = "Your Crush request has been sent to Amelia. We'll let you know if they accept.",
            onDismiss = {}
        )
    }
}

