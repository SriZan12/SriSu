package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString

@Composable
expect fun ProtectConversation()

@Composable
expect fun rememberCopyPrivateText(): (String) -> Unit

internal expect fun privateClipText(entry: ClipEntry): String?

/** Applies to selection toolbars, keyboard copy/cut, and composer text as well
 * as the explicit message action. Pasting remains owned by the platform. */
@Suppress("DEPRECATION")
@Composable
internal fun PrivateChatClipboard(content: @Composable () -> Unit) {
    val copy=rememberCopyPrivateText()
    val original=LocalClipboard.current
    val legacy=LocalClipboardManager.current
    val protected=remember(original,copy) {object: Clipboard by original {
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            if(clipEntry==null)original.setClipEntry(null)
            else privateClipText(clipEntry)?.let(copy)
        }
    }}
    val protectedLegacy=remember(legacy,copy) {object: ClipboardManager by legacy {
        override fun setText(annotatedString: AnnotatedString)=copy(annotatedString.text)
        override fun setClip(clipEntry: ClipEntry?) {
            if(clipEntry==null)legacy.setClip(null) else privateClipText(clipEntry)?.let(copy)
        }
    }}
    CompositionLocalProvider(LocalClipboard provides protected,LocalClipboardManager provides protectedLegacy,content=content)
}
