package com.srisu.srisu.features.couplechat.presentation

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry

internal actual fun privateClipText(entry: ClipEntry): String? =
    if(entry.clipData.itemCount==1)entry.clipData.getItemAt(0).text?.toString() else null

@Composable
actual fun ProtectConversation() {
    val context = LocalContext.current
    DisposableEffect(context) {
        var current: Context? = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        val window = (current as? Activity)?.window
        val alreadySecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@Composable
actual fun rememberCopyPrivateText(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SriSu private message", text)
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            clipboard.setPrimaryClip(clip)
            Handler(Looper.getMainLooper()).postDelayed({
                val current = clipboard.primaryClip
                if (current?.description?.label == "SriSu private message" && current.itemCount == 1 && current.getItemAt(0).text?.toString() == text) {
                    if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                    else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }, 60000)
        }
    }
}
