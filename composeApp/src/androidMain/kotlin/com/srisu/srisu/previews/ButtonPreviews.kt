package com.srisu.srisu.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.srisu.srisu.components.RequestSentDialog

@Composable
@Preview(showBackground = true)
fun PreviewPrimaryButton() {
    RequestSentDialog(
        title = "Request sent!",
        successMessage = "Your Crush request has been sent to Amelia. We'll let you know if they accept.",
        onDismiss = {}
    )
}

