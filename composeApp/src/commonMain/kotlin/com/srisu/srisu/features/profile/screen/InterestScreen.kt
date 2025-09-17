package com.srisu.srisu.features.profile.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.core.data.response.auth.InterestResponse
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.features.profile.state.InterestCategoryUI
import com.srisu.srisu.features.profile.state.InterestUI
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Preview
fun InterestScreen(
    interests: List<InterestResponse.Interest?>? = emptyList(),
    currentInterests: List<User.UserInterest?>? = emptyList(),
    onInterestSelected: (List<User.UserInterest?>?) -> Unit = {}
) {
    val selectedInterests = remember { mutableStateListOf<User.UserInterest>() }
    val latestOnInterestSelected by rememberUpdatedState(onInterestSelected)

    LaunchedEffect(currentInterests) {
        selectedInterests.clear()
        selectedInterests.addAll(currentInterests?.filterNotNull().orEmpty())
    }

    Scaffold(
        topBar = {
            PrimaryToolBar(
                title = "Interests",
                onNavigate = { latestOnInterestSelected(selectedInterests.toList()) }
            )
        },
        bottomBar = {
            if (!interests.isNullOrEmpty()) {
                PrimaryButtonCompo(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    label = "Save",
                    onClick = {
                        latestOnInterestSelected(selectedInterests.toList())
                    }
                )
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val groupedInterests = remember(interests) {
                mapToUIModel(interests = interests)
            }

            CategorizedInterestListCompo(
                interestList = groupedInterests,
                selectedInterests = selectedInterests,
                previousInterest = currentInterests.orEmpty(),
                onInterestSelected = { updated ->
                    selectedInterests.clear()
                    selectedInterests.addAll(updated.filterNotNull())
                }
            )

        }

        BackHandler {
            latestOnInterestSelected(selectedInterests.toList())
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CategorizedInterestListCompo(
    interestList: List<InterestCategoryUI> = emptyList(),
    selectedInterests: List<User.UserInterest?>,
    previousInterest: List<User.UserInterest?>,
    onInterestSelected: (List<User.UserInterest?>) -> Unit
) {
    val lazyGridState = rememberLazyGridState()
    val animatedItems = remember { mutableSetOf<String>() }

    // Track the highest visible item index to determine scroll direction
    var lastFirstVisibleItemIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(lazyGridState.firstVisibleItemIndex) {
        val currentIndex = lazyGridState.firstVisibleItemIndex
        val isScrollingDown = currentIndex >= lastFirstVisibleItemIndex

        if (isScrollingDown) {
            // Only allow animations when scrolling down
            lastFirstVisibleItemIndex = currentIndex
        }
    }

    LazyVerticalGrid(
        state = lazyGridState,
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(all = 16.dp)
    ) {
        if (interestList.isEmpty()) {
            item(span = { GridItemSpan(3) }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "No interests available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            interestList.forEachIndexed { index, category ->
                item(span = { GridItemSpan(3) }, key = "category_${category.category}") {

                        Text(
                            text = category.category,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                        )
                }

                items(
                    items = category.interests,
                    key = { it.id ?: it.hashCode() },
                    contentType = { "interest" }
                ) { interest ->
                    val itemKey = interest.id.toString()
                    var isVisible by remember { mutableStateOf(animatedItems.contains(itemKey)) }
                    val isScrollingDown = lazyGridState.firstVisibleItemIndex >= lastFirstVisibleItemIndex

                    LaunchedEffect(itemKey, isScrollingDown) {
                        if (!animatedItems.contains(itemKey) && isScrollingDown) {
                            animatedItems.add(itemKey)
                            isVisible = true
                        } else if (animatedItems.contains(itemKey)) {
                            isVisible = true
                        }
                    }

                    val isSelected = selectedInterests.any {
                        it?.removed == false && it.interest == interest.id
                    }

                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceDim
                        },
                        animationSpec = tween(durationMillis = 300),
                        label = "ChipBackgroundAnimation_${interest.id}"
                    )

                  /*  val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "ChipScaleAnimation_${interest.id}"
                    )
*/
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    animationSpec = tween(300),
                                    initialOffsetY = { if (index % 2 == 0) -50 else 50 }
                                ),
                        exit = ExitTransition.None
                    ) {
                        InterestChip(
                            modifier = Modifier
//                                .scale(scale)
                                .animateItem(),
                            label = interest.interestName.orEmpty(),
                            backgroundColor = backgroundColor,
                            onChipClick = {
                                val updatedSelection = updateInterestSelection(
                                    currentList = selectedInterests,
                                    previousInterest = previousInterest,
                                    interest = interest,
                                    isSelected = isSelected
                                )
                                onInterestSelected(updatedSelection)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun mapToUIModel(interests: List<InterestResponse.Interest?>?): List<InterestCategoryUI> {
    return interests
        ?.groupBy { it?.category?.name ?: "" }
        ?.map { (categoryName, interestsInCategory) ->
            InterestCategoryUI(
                category = categoryName,
                interests = interestsInCategory.map { interest ->
                    InterestUI(
                        interestName = interest?.name,
                        id = interest?.id
                    )
                }
            )
        } ?: emptyList()
}

private fun updateInterestSelection(
    currentList: List<User.UserInterest?>,
    previousInterest: List<User.UserInterest?>,
    interest: InterestUI,
    isSelected: Boolean
): List<User.UserInterest?> {
    return if (isSelected) {
        val isAvailableInPrevList = previousInterest.any { it?.interest == interest.id }
        if (!isAvailableInPrevList) {
            currentList.filter { it?.interest != interest.id }
        } else {
            currentList.map { userInterest ->
                if (userInterest?.interest == interest.id) {
                    userInterest?.copy(removed = true)
                } else userInterest
            }
        }
    } else {
        val existing = currentList.find { it?.interest == interest.id }
        if (existing != null) {
            currentList.map { userInterest ->
                if (userInterest?.interest == interest.id) {
                    userInterest?.copy(removed = false)
                } else userInterest
            }
        } else {
            currentList + User.UserInterest(
                interest = interest.id,
                name = interest.interestName,
                removed = false
            )
        }
    }
}
