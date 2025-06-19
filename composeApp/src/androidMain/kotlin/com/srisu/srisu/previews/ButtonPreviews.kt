package com.srisu.srisu.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.SuccessDialog
import com.srisu.srisu.features.auth.screens.GlowingRedIcon

@Composable
@Preview(showBackground = true)
fun PreviewPrimaryButton() {
   SuccessDialog (
       show = true,
       title = "Success",
        successMessage = "Something went wrong",
   ) { }
}

