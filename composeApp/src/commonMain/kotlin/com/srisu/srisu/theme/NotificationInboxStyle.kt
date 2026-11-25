package com.srisu.srisu.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Measured from the user-supplied Notifications board, 25 November 2026.
 * Only inbox typography/density changes; global theme and chat remain intact. */
internal object NotificationInboxStyle {
    val unread @Composable get() = MaterialTheme.colorScheme.let {
        if (it.surface.luminance() > .5f) lerp(it.surface, it.tertiaryContainer, .42f)
        else it.surfaceContainerLow
    }
    val message: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium.copy(
        fontSize=14.sp, lineHeight=20.sp, letterSpacing=(-.14).sp)
    val time: TextStyle @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize=12.sp,lineHeight=18.sp)
    val heading: TextStyle @Composable get() = MaterialTheme.typography.titleLarge.copy(fontSize=20.sp,lineHeight=27.sp)
    val section: TextStyle @Composable get() = MaterialTheme.typography.labelSmall.copy(
        fontSize=12.sp,lineHeight=18.sp,letterSpacing=1.44.sp,fontWeight=FontWeight.Bold)
}
