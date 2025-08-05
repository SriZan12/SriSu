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
import com.srisu.srisu.navigation.Interest
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun InterestScreen(
    interests: List<Interest?>? = emptyList(),
    currentInterests: List<String?>? = emptyList(),
    onInterestSelected: (List<String?>?) -> Unit = {}
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
                interestCategories = interests,
                selectedInterests = selectedInterests,
                onInterestSelected = { selectedInterests = it }
            )
        }
    }
}


@Composable
fun CategorizedInterestListCompo(
    interestCategories: List<Interest?>?,
    selectedInterests: List<String?>?,
    onInterestSelected: (List<String?>?) -> Unit
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        interestCategories?.let { newInterestsCategories ->
            newInterestsCategories.forEach { category ->
                item(span = { GridItemSpan(3) }) {
                    Text(
                        text = category?.category ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(category?.interests ?: emptyList()) { interest ->

                    val backgroundColor = if (isSelected(selectedInterests, interest)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceDim
                    }

                    InterestChip(
                        modifier = Modifier,
                        label = interest,
                        backgroundColor = backgroundColor,
                        onChipClick = {
                            onInterestSelected(
                                selectInterests(
                                    selectedInterests = selectedInterests,
                                    selectedInterest = interest
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun isSelected(selectedInterests: List<String?>?, interest: String?): Boolean {
    val isSelected =
        selectedInterests?.contains(interest)

    return isSelected == true
}

private fun selectInterests(
    selectedInterests: List<String?>?,
    selectedInterest: String?
): List<String> {
    val interestsList = selectedInterests?.filterNotNull() ?: emptyList()

    if (selectedInterest == null) return interestsList

    return if (interestsList.contains(selectedInterest)) {
        interestsList - selectedInterest
    } else {
        if (interestsList.size < 10) interestsList + selectedInterest
        else interestsList
    }
}







