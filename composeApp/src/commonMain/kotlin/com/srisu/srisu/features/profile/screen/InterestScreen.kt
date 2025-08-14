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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.core.data.response.auth.InterestResponse
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.profile.state.InterestCategoryUI
import com.srisu.srisu.features.profile.state.InterestUI
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.collections.emptyList

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
                onNavigate = { onInterestSelected(emptyList()) }
            )
        },
        bottomBar = {
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CategorizedInterestListCompo(
                interestList = mapToUIModel(interests = interests),
                selectedInterests = selectedInterests,
                onInterestSelected = { selectedInterests = it }
            )
        }
    }
}

@Composable
fun CategorizedInterestListCompo(
    interestList: List<InterestCategoryUI>,
    selectedInterests: List<User.UserInterest?>?,
    onInterestSelected: (List<User.UserInterest?>?) -> Unit
) {
    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        interestList.forEach { category ->
            item(span = { GridItemSpan(3) }) {
                Text(
                    text = category.category,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(category.interests) { interest ->
                val isSelected = selectedInterests
                    ?.any {
                        AppLogger.log("INTEREST = ${it?.interest}")
                        AppLogger.log("id = ${interest.id}")
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
                        val currentList = selectedInterests?.toMutableList() ?: mutableListOf()

                        val updatedSelection = if (isSelected) {
                            currentList.map { userInterest ->
                                if (userInterest?.interest == interest.id) {
                                    userInterest?.copy(
                                        removed = true
                                    )
                                } else userInterest
                            }
                        } else {
                            currentList + User.UserInterest(
                                interest = interest.id,
                                name = interest.interestName,
                                removed = false
                            )
                        }


                        onInterestSelected(updatedSelection)
                    }
                )
            }
        }
    }
}

fun mapToUIModel(interests: List<InterestResponse.Interest?>?): List<InterestCategoryUI> {
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








