package com.srisu.srisu.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.SuccessDialog
import com.srisu.srisu.features.auth.screens.GlowingRedIcon
import com.srisu.srisu.features.profile.screen.RequestSentDialog

@Composable
@Preview(showBackground = true)
fun PreviewPrimaryButton() {
    RequestSentDialog(
        title = "Request sent!",
        successMessage = "Your Crush request has been sent to Amelia. We'll let you know if they accept.",
        onDismiss = {}
    )
}

