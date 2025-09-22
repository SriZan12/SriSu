package com.srisu.srisu.features.home.connection.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.home.connection.state.ConnectionUIState
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun MyCrushScreen(connectionUiState: ConnectionUIState) {
    MyCrushScreenContent(
        connectionUiState = connectionUiState
    )
}

@Composable
fun MyCrushScreenContent(
    modifier: Modifier = Modifier,
    connectionUiState: ConnectionUIState,
) {

    connectionUiState.myCrushList?.let { myCrushListFlow ->
        val myCrushList = myCrushListFlow.collectAsLazyPagingItems()


        if (myCrushList.itemCount == 0 && connectionUiState.baseUIState != BaseUIState.Loading) {
            NoConnectionsFound(
                modifier = Modifier,
                title = "You don't have any crush"
            )
        } else if (connectionUiState.baseUIState == BaseUIState.Loading) {
            ConnectionShimmerCompo()
        } else {

            NoConnectionsFound(
                modifier = Modifier,
                title = "You don't have any crush"
            )

            /*LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(all = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    count = myCrushList.itemCount,
                    key = { index -> myCrushList[index]?.id ?: index },
                    contentType = myCrushList.itemContentType { "myCrush" }
                ) { index ->
                    val myCrushItem = myCrushList[index]
                    myCrushItem?.let { crush ->
                        ConnectionItem(
                            userName = crush.receiver?.username ?: "",
                            userImage = crush.receiver?.profilePhoto ?: "",
                            dob = crush.receiver?.dob,
                            zodiacSign = crush.receiver?.zodiacSign
                        )

                    }
                }
            }*/
        }
    }

}

@Composable
@Preview
fun PreviewMyCrushScreen() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MyCrushScreen(
            connectionUiState = ConnectionUIState()
        )
    }
}