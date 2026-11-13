@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDate
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIPasteboardOptionLocalOnly
import androidx.compose.ui.platform.ClipEntry

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal actual fun privateClipText(entry: ClipEntry): String? = entry.getPlainText()

/** The Swift root hides Compose content and accessibility while the scene is inactive. */
@Composable
actual fun ProtectConversation() = Unit

@Composable
actual fun rememberCopyPrivateText(): (String) -> Unit = remember {
    { text -> UIPasteboard.generalPasteboard.setItems(
        listOf(mapOf("public.utf8-plain-text" to text)),
        options = mapOf(UIPasteboardOptionLocalOnly to true, UIPasteboardOptionExpirationDate to NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + 60.0)),
    ) }
}
