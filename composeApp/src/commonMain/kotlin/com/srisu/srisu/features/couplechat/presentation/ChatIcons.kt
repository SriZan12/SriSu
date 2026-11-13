package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import srisu.composeapp.generated.resources.*

/** Small, bundled Material vector subset shared by Android and iOS. No runtime downloads. */
internal object ChatIcons {
    val Mic: ImageVector @Composable get() = vectorResource(Res.drawable.chat_mic)
    val MoreHoriz: ImageVector @Composable get() = vectorResource(Res.drawable.chat_more_horiz)
    val Forum: ImageVector @Composable get() = vectorResource(Res.drawable.chat_forum)
    val Image: ImageVector @Composable get() = vectorResource(Res.drawable.chat_image)
    val Download: ImageVector @Composable get() = vectorResource(Res.drawable.chat_download)
    val Videocam: ImageVector @Composable get() = vectorResource(Res.drawable.chat_videocam)
    val PhotoCamera: ImageVector @Composable get() = vectorResource(Res.drawable.chat_photo_camera)
    val AutoAwesome: ImageVector @Composable get() = vectorResource(Res.drawable.chat_auto_awesome)
    val ChatBubbleOutline: ImageVector @Composable get() = vectorResource(Res.drawable.chat_chat_bubble_outline)
    val Event: ImageVector @Composable get() = vectorResource(Res.drawable.chat_event)
    val Collections: ImageVector @Composable get() = vectorResource(Res.drawable.chat_collections)
    val DeleteOutline: ImageVector @Composable get() = vectorResource(Res.drawable.chat_delete_outline)
    val Stop: ImageVector @Composable get() = vectorResource(Res.drawable.chat_stop)
    val Pause: ImageVector @Composable get() = vectorResource(Res.drawable.chat_pause)
    val Flag: ImageVector @Composable get() = vectorResource(Res.drawable.chat_flag)
    val Block: ImageVector @Composable get() = vectorResource(Res.drawable.chat_block)
    val Schedule: ImageVector @Composable get() = vectorResource(Res.drawable.chat_schedule)
    val ErrorOutline: ImageVector @Composable get() = vectorResource(Res.drawable.chat_error_outline)
    val DoneAll: ImageVector @Composable get() = vectorResource(Res.drawable.chat_done_all)
    val RemoveCircleOutline: ImageVector @Composable get() = vectorResource(Res.drawable.chat_remove_circle_outline)
    val ContentCopy: ImageVector @Composable get() = vectorResource(Res.drawable.chat_content_copy)
    val DeleteSweep: ImageVector @Composable get() = vectorResource(Res.drawable.chat_delete_sweep)
    val Reply: ImageVector @Composable get() = vectorResource(Res.drawable.chat_reply)
}
