package com.srisu.srisu.features.profile.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.core.data.response.auth.InterestResponse
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.features.profile.state.InterestCategoryUI
import com.srisu.srisu.features.profile.state.InterestUI
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.collections.emptyList

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Preview
fun InterestScreen(
    interests: List<InterestResponse.Interest?>? = emptyList(),
    currentInterests: List<User.UserInterest?>? = emptyList(),
    onInterestSelected: (List<User.UserInterest?>?) -> Unit = {}
) {

    var selectedInterests by remember {
        mutableStateOf(currentInterests)
    }

    Scaffold(
        topBar = {
            PrimaryToolBar(
                title = "Interests",
                onNavigate = { onInterestSelected(selectedInterests) }
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
                        onInterestSelected(selectedInterests)
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
            CategorizedInterestListCompo(
                interestList = mapToUIModel(interests = interests),
                selectedInterests = selectedInterests,
                previousInterest = currentInterests,
                onInterestSelected = { selectedInterests = it }
            )
        }

        BackHandler {
            onInterestSelected(selectedInterests)
        }
    }
}

@Composable
fun CategorizedInterestListCompo(
    interestList: List<InterestCategoryUI> = emptyList(),
    selectedInterests: List<User.UserInterest?>?,
    previousInterest: List<User.UserInterest?>?,
    onInterestSelected: (List<User.UserInterest?>?) -> Unit
) {
    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (interestList.isEmpty()) {
            // Empty state
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
            interestList.forEach { category ->
                item(span = { GridItemSpan(3) }, key = {
                    category.category
                }) {
                    Text(
                        text = category.category,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(category.interests) { interest ->
                    val isSelected = selectedInterests
                        ?.any {
                            it?.removed == false && it.interest == interest.id
                        } == true

                    val backgroundColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceDim
                    }

                    InterestChip(
                        modifier = Modifier,
                        label = interest.interestName ?: "",
                        backgroundColor = backgroundColor,
                        onChipClick = {

                            val updatedSelection = updateInterestSelection(
                                currentList = selectedInterests ?: emptyList(),
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
    previousInterest: List<User.UserInterest?>?,
    interest: InterestUI,
    isSelected: Boolean
): List<User.UserInterest?> {

    return if (isSelected) {
        val isAvailableInPrevList = previousInterest?.any { it?.interest == interest.id }
        if (isAvailableInPrevList == false) {
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









