package com.srisu.srisu.features.profile.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.navigation.Interest
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun InterestScreen(
    interests: List<Interest?>?,
    currentInterests: List<String?>?,
    onInterestSelected: (List<Interest>) -> Unit,
) {
    Scaffold(
        topBar = {
            PrimaryToolBar(
                title = "Interests",
                onNavigate = {
                    onInterestSelected(
                        listOf()
                    )
                }
            )
        },
        bottomBar = {
            PrimaryButtonCompo(
                modifier = Modifier.fillMaxWidth().padding(all = 16.dp).navigationBarsPadding(),
                label = "Save",
                onClick = {

                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding)) {
            CategorizedInterestListCompo(
                interestCategories = interests,
                currentInterests = currentInterests
            )
        }

    }
}

@Composable
fun CategorizedInterestListCompo(
    interestCategories: List<Interest?>?,
    currentInterests: List<String?>?
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        interestCategories?.forEach { category ->
            stickyHeader {
                Text(
                    text = category?.category ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(category?.interests ?: emptyList()) { interest ->

                val backGroundColor = if (currentInterests?.contains(interest) == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceDim
                }

                InterestChip(
                    label = interest,
                    backGroundColor = backGroundColor,
                    onChipClick = {

                    }
                )
            }
        }
    }
}



