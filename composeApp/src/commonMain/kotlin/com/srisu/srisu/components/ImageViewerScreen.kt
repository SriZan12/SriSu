package com.srisu.srisu.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    images: List<String?>,
    startIndex: Int,
    onDismiss: () -> Unit
) {

    AppLogger.log("START INDEX = $startIndex")

    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { images.size }
    )

    var isPagerEnabled by remember { mutableStateOf(value = true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fill,
            userScrollEnabled = isPagerEnabled,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(
                image = images[page],
                onTap = onDismiss,
                onZoomChange = { isZoomed ->
                    isPagerEnabled = !isZoomed
                }
            )
        }

        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${images.size}",
                    color = Color.White,
                    modifier = Modifier,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }


    }
}


@Composable
private fun ZoomableImage(
    image: String?,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)

        if (newScale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }

        scale = newScale
        onZoomChange(newScale > 1f)
    }

    AsyncImage(
        model = image,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (scale > 1f) {
                                // Reset zoom on double tap when zoomed
                                scale = 1f
                                offset = Offset.Zero
                                onZoomChange(false)
                            } else {
                                // Zoom in to 2x on double tap
                                scale = 2f
                                // Calculate offset to zoom into the tap position
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                offset = Offset(
                                    x = (centerX - tapOffset.x) * scale,
                                    y = (centerY - tapOffset.y) * scale
                                )
                                onZoomChange(true)
                            }
                        }
                    },
                    onTap = {
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(
                state = transformableState,
                canPan = { scale > 1f }
            )
    )
}



