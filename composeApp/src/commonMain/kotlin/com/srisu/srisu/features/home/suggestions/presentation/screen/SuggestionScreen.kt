package com.srisu.srisu.features.home.suggestions.presentation.screen

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import coil3.compose.AsyncImage
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.features.home.suggestions.data.response.UserSuggestionResponse
import com.srisu.srisu.features.home.suggestions.presentation.state.SuggestionUIStates
import com.srisu.srisu.features.home.suggestions.presentation.vm.SuggestionViewModel
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.cross_love
import srisu.composeapp.generated.resources.filter_icon


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SuggestionScreen(
    suggestionViewModel: SuggestionViewModel,
    filterApplied: Boolean,
    filterCleared: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    navigateProfileScreen: (UserSuggestionResponse.Result?) -> Unit,
    navigateFilterScreen: () -> Unit,
) {
    val suggestionUIState by suggestionViewModel.suggestionUIStates.collectAsStateWithLifecycle()

    SuggestionScreenEffects(
        suggestionViewModel = suggestionViewModel,
        filterApplied = filterApplied,
        filterCleared = filterCleared,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SuggestionTopBar(
                    onFilterClick = navigateFilterScreen
                )

                SuggestionFeedbackLayer(
                    suggestionViewModel = suggestionViewModel,
                    suggestionUIStates = suggestionUIState,
                )

                SuggestionContent(
                    suggestionUIState = suggestionUIState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onRetry = suggestionViewModel::getUserSuggestions,
                    onNavigateProfileScreen = navigateProfileScreen,
                )
            }
        }
    }
}

@Composable
private fun SuggestionScreenEffects(
    suggestionViewModel: SuggestionViewModel,
    filterApplied: Boolean,
    filterCleared: Boolean,
) {
    LaunchedEffect(filterApplied, filterCleared) {
        if (filterApplied || filterCleared) {
            suggestionViewModel.getUserSuggestions()
        }
    }
}

@Composable
private fun SuggestionFeedbackLayer(
    suggestionViewModel: SuggestionViewModel,
    suggestionUIStates: SuggestionUIStates,
) {
    val isConnected = isInternetAvailable()
    var showOfflineBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showOfflineBottomSheet = !isConnected
    }

    when (val baseUIState = suggestionUIStates.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = suggestionViewModel::idleScreen,
            )
        }

        is BaseUIState.Loading -> {
            SuggestionShimmerGrid()
        }

        is BaseUIState.NoInternetConnection -> {
            showOfflineBottomSheet = baseUIState.isOffline
        }

        else -> Unit
    }

    if (showOfflineBottomSheet) {
        OfflineBottomSheetCompo(
            show = showOfflineBottomSheet,
            onDismiss = {
                showOfflineBottomSheet = false
                suggestionViewModel.idleScreen()
            }
        )
    }
}

@Composable
private fun SuggestionTopBar(
    onFilterClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Suggestions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        IconButton(onClick = onFilterClick) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(Res.drawable.filter_icon),
                contentDescription = "Filter Icon",
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SuggestionContent(
    suggestionUIState: SuggestionUIStates,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onRetry: () -> Unit,
    onNavigateProfileScreen: (UserSuggestionResponse.Result?) -> Unit,
) {
    val suggestionsFlow = suggestionUIState.suggestions ?: return
    val suggestions = suggestionsFlow.collectAsLazyPagingItems()
    val loadState = suggestions.loadState

    when {
        loadState.refresh is LoadState.Loading -> {
            SuggestionShimmerGrid()
        }

        suggestions.itemCount == 0 -> {
            NoSuggestionComp(onRetry = onRetry)
        }

        else -> {
            LazyVerticalStaggeredGrid(
                modifier = Modifier.padding(horizontal = 12.dp),
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = suggestions.itemCount,
                    key = { index -> suggestions[index]?.id ?: index },
                    contentType = { "SuggestionItem" },
                ) { index ->
                    val item = suggestions[index]
                    val itemHeight = if (index % 2 == 0) 188.dp else 252.dp

                    SuggestionCard(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(200),
                            fadeOutSpec = tween(200),
                        ),
                        height = itemHeight,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        suggestionItem = item,
                        onClick = onNavigateProfileScreen,
                    )
                }

//                if (loadState.append is LoadState.Loading) {
//                    items(2) { index ->
//                        val itemHeight = if (index % 2 == 0) 188.dp else 252.dp
//                        SuggestionCardShimmerCompo(
//                            modifier = Modifier,
//                            height = itemHeight,
//                        )
//                    }
//                }
            }
        }
    }
}

@Composable
private fun SuggestionShimmerGrid() {
    LazyVerticalStaggeredGrid(
        modifier = Modifier.padding(horizontal = 12.dp),
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(10) { index ->
            val itemHeight = if (index % 2 == 0) 188.dp else 252.dp
            SuggestionCardShimmerCompo(
                modifier = Modifier,
                height = itemHeight,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SuggestionCard(
    modifier: Modifier,
    height: Dp,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    suggestionItem: UserSuggestionResponse.Result?,
    onClick: (UserSuggestionResponse.Result?) -> Unit,
) {
    val userId = suggestionItem?.id
    val profileImageKey = "profile_image-$userId"
    val usernameKey = "username_text-$userId"
    val zodiacKey = "zodiac_image-$userId"
    val ageKey = "dob_text-$userId"

    val displayName = suggestionItem?.username ?: suggestionItem?.fullName.orEmpty()
    val zodiacSignImg = remember(suggestionItem?.zodiacSign) {
        ZodiacUtils.getZodiacSignImage(suggestionItem?.zodiacSign ?: "")
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = { onClick(suggestionItem) },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            with(sharedTransitionScope) {
                AsyncImage(
                    model = suggestionItem?.profilePhoto,
                    contentDescription = "User Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .sharedElement(
                            sharedTransitionScope.rememberSharedContentState(key = profileImageKey),
                            animatedVisibilityScope = animatedContentScope,
                        )
                        .fillMaxWidth()
                        .height(height),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = usernameKey),
                                animatedVisibilityScope = animatedContentScope,
                            ),
                        text = displayName,
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        zodiacSignImg?.let { zodiacDrawable ->
                            Image(
                                modifier = Modifier
                                    .size(38.dp)
                                    .sharedElement(
                                        sharedTransitionScope.rememberSharedContentState(key = zodiacKey),
                                        animatedVisibilityScope = animatedContentScope,
                                    ),
                                painter = painterResource(zodiacDrawable),
                                contentDescription = "Zodiac Sign",
                            )
                        }

                        suggestionItem?.age?.let { age ->
                            Text(
                                modifier = Modifier.sharedElement(
                                    sharedTransitionScope.rememberSharedContentState(key = ageKey),
                                    animatedVisibilityScope = animatedContentScope,
                                ),
                                text = age.toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionCardShimmerCompo(
    modifier: Modifier = Modifier,
    height: Dp,
) {
    val brush = rememberSuggestionShimmerBrush()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(brush = brush)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush = brush)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(brush = brush)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush = brush)
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberSuggestionShimmerBrush(): Brush {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.7f),
        Color.LightGray.copy(alpha = 0.7f),
        Color.White.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.7f),
    )

    val transition = rememberInfiniteTransition()
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value),
    )
}

@Composable
private fun NoSuggestionComp(
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "No Suggestions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
            )

            Image(
                painter = painterResource(Res.drawable.cross_love),
                contentDescription = "Love Icon",
                modifier = Modifier.size(60.dp),
            )

            OutlinedButton(
                onClick = onRetry,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 24.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}