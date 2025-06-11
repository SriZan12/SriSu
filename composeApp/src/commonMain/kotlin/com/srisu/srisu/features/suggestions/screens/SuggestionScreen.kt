package com.srisu.srisu.features.suggestions.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import coil3.compose.AsyncImage
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.CountrySelectionBottomSheet
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.features.suggestions.state.SuggestionUIStates
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.getCountryFlagFromAssets
import com.srisu.srisu.utils.isInternetAvailable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.country_flag
import srisu.composeapp.generated.resources.filter_icon

typealias UserProfileData = String

@Preview
@Composable
fun SuggestionScreen(
    suggestionViewModel: SuggestionViewModel = koinViewModel<SuggestionViewModel>(),
    navigateProfileScreen: (UserProfileData) -> Unit
) {
    Scaffold(
        topBar = {

        }
    ) { paddingValues ->

        val suggestionUIState by suggestionViewModel.suggestionUIStates.collectAsStateWithLifecycle()
        var showFilterDialog by rememberSaveable {
            mutableStateOf(false)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {

            SuggestionTopBarCompo(
                showFilterDialog = {
                    showFilterDialog = true
                }
            )

            HandleUiStates(
                authViewModel = suggestionViewModel,
                authUIStates = suggestionUIState
            )

            SuggestionContent(
                suggestionUIState = suggestionUIState,
                onNavigateProfileScreen = {
                    val userProfileData = Json.encodeToString(it)
                    navigateProfileScreen(userProfileData)
                }
            )

            if (showFilterDialog) {
                FilterSuggestionDialog {
                    showFilterDialog = false
                }
            }
        }

    }
}

@Composable
private fun HandleUiStates(
    authViewModel: SuggestionViewModel,
    authUIStates: SuggestionUIStates
) {

    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = authUIStates.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    authViewModel.idleScreen()
                },
            )
        }

        is BaseUIState.Loading -> {
            SuggestionShimmerCompo()
        }

        is BaseUIState.Success<*> -> {
//            val data = baseUIState.data
            // Handle success case based on the expected type
        }

        is BaseUIState.NoInternetConnection -> {
            showBottomSheet = baseUIState.isOffline
        }

        is BaseUIState.Idle -> Unit
    }

    if (showBottomSheet) {
        OfflineBottomSheetCompo(
            show = showBottomSheet,
            onDismiss = {
                showBottomSheet = false
                authViewModel.idleScreen()
            }
        )
    }
}

@Composable
private fun SuggestionTopBarCompo(
    showFilterDialog: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Text(
            text = "Suggestions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )


        IconButton(
            onClick = {
                showFilterDialog()
            }
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(Res.drawable.filter_icon),
                contentDescription = "Filter Icon",
            )
        }

    }

}

@Composable
private fun SuggestionContent(
    suggestionUIState: SuggestionUIStates,
    onNavigateProfileScreen: (UserSuggestionResponse.Result?) -> Unit
) {
    suggestionUIState.suggestions?.let { suggestionsFlow ->
        val suggestions = suggestionsFlow.collectAsLazyPagingItems()

        if (suggestions.itemCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No Suggestions",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                modifier = Modifier.padding(horizontal = 12.dp),
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    count = suggestions.itemCount,
                    key = { it -> suggestions[it]?.id!! },
                    contentType = suggestions.itemContentType { "Suggestion Items" },
                ) { index ->
                    val item = suggestions[index]

                    val height = if (index % 2 == 0) 188.dp else 252.dp

                    SuggestionCardCompo(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(200),
                            fadeOutSpec = tween(200)
                        ),
                        height = height,
                        suggestionItem = item
                    ) { userProfileData ->
                        onNavigateProfileScreen(userProfileData)
                    }

                }
            }
        }
    }
}

@Composable
private fun SuggestionShimmerCompo() {
    LazyVerticalStaggeredGrid(
        modifier = Modifier.padding(horizontal = 12.dp),
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(10) { index ->
            val height = if (index % 2 == 0) 188.dp else 252.dp

            SuggestionCardShimmerCompo(
                modifier = Modifier, height = height
            )
        }
    }
}

@Composable
private fun SuggestionCardCompo(
    modifier: Modifier,
    height: Dp,
    suggestionItem: UserSuggestionResponse.Result?,
    onClick: (UserSuggestionResponse.Result?) -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = {
            onClick(suggestionItem)
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1576828831022-ca41d3905fb7?q=80&w=1923&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                contentDescription = "User Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.BottomStart)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = suggestionItem?.username ?: suggestionItem?.fullName ?: "",
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1

                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val zodiacSignImg =
                        ZodiacUtils.getZodiacSignImage(suggestionItem?.zodiacSign ?: "")

                    zodiacSignImg?.let {
                        Image(
                            modifier = Modifier.size(38.dp),
                            painter = painterResource(zodiacSignImg),
                            contentDescription = "Zodiac Sign",
                        )
                    }

                    suggestionItem?.dob?.let { dob ->
                        Text(
                            text = "${DateTimeUtils.calculateAge(dob)}",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                }


            }


//            Button(
//                onClick = { },
//                modifier = Modifier
//                    .padding(top = 6.dp, bottom = 8.dp)
//                    .align(Alignment.BottomCenter),
//                shape = RoundedCornerShape(18.dp),
//                contentPadding = PaddingValues(horizontal = 28.dp),
//                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
//            ) {
//                Icon(
//                    modifier = Modifier.size(32.dp).align(Alignment.CenterVertically),
//                    painter = painterResource(Res.drawable.love_icon),
//                    contentDescription = "Request Button",
//                    tint = Color.White
//                )
//            }

        }
    }
}

@Composable
fun SuggestionCardShimmerCompo(
    modifier: Modifier = Modifier,
    height: Dp
) {
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
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        )
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Shimmer image placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(brush = brush)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.BottomStart)
            ) {
                // Username shimmer
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zodiac icon shimmer
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(brush = brush)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Age shimmer
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
private fun FilterSuggestionDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            onDismiss()
        },
        content = {
            FilterSuggestionCompo {

            }
        }
    )
}

@Composable
private fun FilterSuggestionCompo(
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {

            IconButton(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    onDismiss()
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Icon",
                )
            }

            AgeFilterCompo(onReset = {
                onDismiss()
            })

            Spacer(modifier = Modifier.height(12.dp))

            CountryFilterCompo(
                onReset = {

                },
                onOptionSelected = {

                }
            )

        }
    }

}

@Composable
private fun FilterTitle(
    headerTitle: String,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = headerTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        TextButton(
            onClick = {
                onReset()
            },
        ) {
            Text(
                text = "Reset",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AgeFilterCompo(
    onReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilterTitle(headerTitle = "Age") {
            onReset()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth().weight(1f),
                text = "Min Age",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start
            )
            Text(
                modifier = Modifier.fillMaxWidth().weight(1f),
                text = "Max Age",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            var minAge by rememberSaveable {
                mutableStateOf("20")
            }

            var maxAge by rememberSaveable {
                mutableStateOf("25")
            }

            AgeFilterCardCompo(
                modifier = Modifier.weight(1f),
                age = minAge,
                onValueChanged = {
                    minAge = it
                }
            )

            AgeFilterCardCompo(
                modifier = Modifier.weight(1f),
                age = maxAge,
                onValueChanged = {
                    maxAge = it
                }
            )
        }
    }


}

@Composable
private fun AgeFilterCardCompo(
    modifier: Modifier,
    age: String,
    readOnly: Boolean = false,
    onValueChanged: (String) -> Unit
) {

    OutlinedTextField(
        modifier = modifier,
        value = age,
        onValueChange = {
            onValueChanged(it)
        },
        readOnly = readOnly,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Gray,
        ).copy(focusedTextColor = Color.Black),
        trailingIcon = {
            IconButton(
                modifier = Modifier.size(24.dp).clip(shape = RoundedCornerShape(8.dp)),
                onClick = {

                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceDim,
                    contentColor = Color.Black
                ),
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Filter Icon",
                )
            }
        }
    )


}

@Composable
private fun CountryFilterCompo(
    onReset: () -> Unit,
    onOptionSelected: (CountryModel) -> Unit
) {
    //TODO get the country from session
    var countryModel by remember {
        mutableStateOf(
            CountryModel(
                name = "Nepal",
                prefix = "+977",
                code = "NP"
            )
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        FilterTitle(headerTitle = "Country") {
            onReset()
        }

        CountryDropDown(
            modifier = Modifier.fillMaxWidth(),
            option = countryModel,
            onOptionSelected = {
                onOptionSelected(it)
                countryModel = it
            }
        )
    }
}

@Composable
private fun CountryDropDown(
    modifier: Modifier,
    visibleLeadingIcon: Boolean = true,
    option: CountryModel? = null,
    onOptionSelected: (CountryModel) -> Unit

) {
    var showCountryBottomSheet by rememberSaveable {
        mutableStateOf(false)
    }

    val flag = getCountryFlagFromAssets(
        countryCode = option?.code ?: ""
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(
            1.dp, color = Color.Gray
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visibleLeadingIcon) {
                    if (flag == null) {
                        Image(
                            painter = painterResource(Res.drawable.country_flag),
                            contentDescription = "country_flag",
                            modifier = Modifier
                                .size(24.dp)
                        )
                    } else {
                        Image(
                            bitmap = flag,
                            contentDescription = "flag",
                            modifier = Modifier
                                .size(24.dp)
                        )
                    }
                }

                Text(
                    modifier = Modifier,
                    text = option?.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start
                )
            }

            Box(modifier = Modifier.padding(end = 8.dp)) {
                DropDownIcon(onClick = {
                    showCountryBottomSheet = true
                })
            }

            CountrySelectionBottomSheet(
                show = showCountryBottomSheet,
                onCountrySelected = {
                    onOptionSelected(it)
                    showCountryBottomSheet = false
                },
                onClose = {
                    showCountryBottomSheet = false
                }
            )


        }
    }
}

@Composable
private fun CityDropDownCompo(){

}

@Composable
private fun DropDownIcon(
    onClick: () -> Unit
) {
    IconButton(
        modifier = Modifier.size(24.dp).clip(shape = RoundedCornerShape(8.dp)),
        onClick = {
            onClick()
        },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceDim,
            contentColor = Color.Black
        ),
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Filter Icon",
        )
    }
}




