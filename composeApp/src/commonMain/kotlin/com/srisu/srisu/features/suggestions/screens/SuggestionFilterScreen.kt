package com.srisu.srisu.features.suggestions.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.CityDropDown
import com.srisu.srisu.components.CitySelectionBottomSheet
import com.srisu.srisu.components.CountryDropDown
import com.srisu.srisu.components.CountrySelectionBottomSheet
import com.srisu.srisu.components.DropDownIcon
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.ZodiacSignSelectionBottomSheet
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.suggestions.state.SuggestionUIStates
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import com.srisu.srisu.utils.getCountryFlagFromAssets
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.country_flag


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FilterSuggestionScreen(
    suggestionViewModel: SuggestionViewModel,
    onNavigateBack: () -> Unit,
    onClearFilter: () -> Unit,
    onFilterApplied: () -> Unit
) {

    val suggestionUIStates by suggestionViewModel.suggestionUIStates.collectAsStateWithLifecycle()

    Initialization(
        suggestionViewModel = suggestionViewModel
    )

    HandleUiStates(
        suggestionVM = suggestionViewModel,
        suggestionUIStates = suggestionUIStates
    )

    SuggestionFilterContent(
        suggestionViewModel = suggestionViewModel,
        suggestionUIStates = suggestionUIStates,
        onNavigateBack = onNavigateBack,
        onClearFilter = onClearFilter,
        onFilterApplied = onFilterApplied
    )

    BackHandler(true) {
        AppLogger.log("FROM BACK HANDLER")
        onNavigateBack()
    }
}

@Composable
private fun Initialization(
    suggestionViewModel: SuggestionViewModel
) {
    LaunchedEffect(Unit) {
        suggestionViewModel.getPreferences()
    }
}

@Composable
private fun HandleUiStates(
    suggestionVM: SuggestionViewModel,
    suggestionUIStates: SuggestionUIStates
) {

    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = suggestionUIStates.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    suggestionVM.idleScreen()
                },
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = {

                }
            )
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
                suggestionVM.idleScreen()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionFilterContent(
    suggestionViewModel: SuggestionViewModel,
    suggestionUIStates: SuggestionUIStates,
    onNavigateBack: () -> Unit,
    onClearFilter: () -> Unit,
    onFilterApplied: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                title = {
                    Text(
                        text = "Filter Suggestions",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onNavigateBack()
                        },
                        modifier = Modifier,
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close Icon",
                        )
                    }
                }
            )
        },
        bottomBar = {
            PrimaryButtonCompo(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(all = 12.dp),
                label = "Apply Filter",
                onClick = {
                    if (suggestionUIStates.userPreferences == null) {
                        suggestionViewModel.setUserPreferences {
                            onFilterApplied()
                        }
                    } else {
                        suggestionViewModel.updateUserPreferences {
                            onFilterApplied()
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues = paddingValues)) {
            FilterSuggestionCompo(
                suggestionViewModel = suggestionViewModel,
                suggestionUIStates = suggestionUIStates
            ) {
                suggestionViewModel.clearFilters()
                suggestionViewModel.updateUserPreferences(isClear = true) {
                    onClearFilter()
                }
            }
        }
    }
}

@Composable
private fun FilterSuggestionCompo(
    suggestionViewModel: SuggestionViewModel,
    suggestionUIStates: SuggestionUIStates,
    onClearFilter: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            val minAge = suggestionUIStates.minAge
            val maxAge = suggestionUIStates.maxAge

            AgeFilterCompo(
                onClearFilter = {
                    onClearFilter()
                },
                minAge = minAge,
                maxAge = maxAge,
                onChangeMinAge = {
                    suggestionViewModel.updateMinAge(it)
                },
                onChangeMaxAge = {
                    suggestionViewModel.updateMaxAge(it)
                }
            )


            CountryFilterCompo(
                selectedCountry = suggestionUIStates.selectedCountry,
                countryList = suggestionUIStates.countryList,
                onClearFilter = {
                },
                onOptionSelected = {
                    suggestionViewModel.updateSelectedCountry(it)
                    suggestionViewModel.updateSelectedCity("")
                    suggestionViewModel.getCityList(
                        country = it.name?.lowercase(),
                        showLoading = true
                    )
                }
            )

            val cities = suggestionUIStates.cities
            val selectedCity = suggestionUIStates.selectedCity

            CityDropDownCompo(
                modifier = Modifier,
                selectedCity = selectedCity,
                onCitySelected = {
                    suggestionViewModel.updateSelectedCity(it)
                },
                cityList = cities ?: emptyList(),
                onClearFilter = {}
            )

            val selectedZodiac = suggestionUIStates.selectedZodiac

            ZodiacSignCompo(
                modifier = Modifier,
                selectedZodiac = selectedZodiac,
                onZodiacSelected = {
                    suggestionViewModel.updateSelectedZodiac(it)
                },
                onClearFilter = {}
            )

        }
    }

}

@Composable
private fun FilterTitle(
    showReset: Boolean = false,
    headerTitle: String,
    onClearFilter: () -> Unit
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

        if (showReset) {
            TextButton(
                onClick = {
                    onClearFilter()
                },
            ) {
                Text(
                    text = "Clear Filter",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun AgeFilterCompo(
    onClearFilter: () -> Unit,
    onChangeMinAge: (Int) -> Unit,
    onChangeMaxAge: (Int) -> Unit,
    minAge: Int?,
    maxAge: Int?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilterTitle(headerTitle = "Age", showReset = true) {
            onClearFilter()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {

            AgeFilterDropdownCardCompo(
                modifier = Modifier.weight(1f),
                selectedAge = minAge,
                headerTitle = "Min Age",
                onAgeSelected = { selected ->
                    onChangeMinAge(selected)
                }
            )

            AgeFilterDropdownCardCompo(
                modifier = Modifier.weight(1f),
                selectedAge = maxAge,
                headerTitle = "Max Age",
                onAgeSelected = { selected ->
                    onChangeMaxAge(selected)
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeFilterDropdownCardCompo(
    modifier: Modifier,
    selectedAge: Int?,
    headerTitle: String,
    onAgeSelected: (Int) -> Unit
) {
    val ageOptions = (16..35).toList()
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        val age = if (selectedAge == 0) "" else selectedAge.toString()
        OutlinedTextField(
            readOnly = true,
            value = age,
            onValueChange = {},
            label = { Text(headerTitle) },
            trailingIcon = {
                DropDownIcon(expanded) {
                    expanded != expanded
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.Black
            )
        )


        ExposedDropdownMenu(
            modifier = Modifier.height(300.dp),
            expanded = expanded,
            shape = RoundedCornerShape(12.dp),
            onDismissRequest = { expanded = false }
        ) {
            ageOptions.forEach { age ->
                DropdownMenuItem(
                    text = {
                        Text(age.toString())
                    },
                    onClick = {
                        onAgeSelected(age)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}


@Composable
private fun CountryFilterCompo(
    selectedCountry: CountryModel?,
    countryList: List<CountryModel>,
    onClearFilter: () -> Unit,
    onOptionSelected: (CountryModel) -> Unit
) {

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var showCountryBottomSheet by remember { mutableStateOf(false) }
        FilterTitle(headerTitle = "Country") {
            onClearFilter()
        }

        CountryDropDown(
            modifier = Modifier.fillMaxWidth(),
            countryList = countryList,
            option = selectedCountry,
            onOptionSelected = {
                onOptionSelected(it)
            },
            onShowCountryBottomSheetChange = {
                showCountryBottomSheet = !showCountryBottomSheet
            },
            showCountryBottomSheet = showCountryBottomSheet
        )
    }
}


@Composable
private fun CityDropDownCompo(
    modifier: Modifier = Modifier,
    selectedCity: String?,
    onCitySelected: (String) -> Unit,
    cityList: List<String?>?,
    onClearFilter: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterTitle(headerTitle = "City") {
            onClearFilter()
        }

        CityDropDown(
            modifier = modifier,
            selectedCity = selectedCity,
            onCitySelected = onCitySelected,
            cityList = cityList,
            onExpandedChange = {
                expanded = !expanded
            },
            expanded = expanded
        )
    }
}

@Composable
private fun ZodiacSignCompo(
    modifier: Modifier = Modifier,
    selectedZodiac: ZodiacSign?,
    onZodiacSelected: (ZodiacSign) -> Unit,
    onClearFilter: () -> Unit
) {
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterTitle(headerTitle = "Zodiac Sign") {
            onClearFilter()
        }

        Card(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            onClick = {
                expanded = true
            },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(
                1.dp, color = Color.Gray
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        modifier = Modifier,
                        text = selectedZodiac?.sign ?: "Select Zodiac Sign",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start
                    )

                    val zodiacSignImg =
                        ZodiacUtils.getZodiacSignImage(selectedZodiac?.sign ?: "")

                    if (zodiacSignImg != null) {
                        Image(
                            painter = painterResource(zodiacSignImg),
                            contentDescription = "zodiac_sign",
                            modifier = Modifier
                                .size(24.dp)
                        )
                    }
                }

                DropDownIcon(expanded = expanded, onClick = {
                    expanded = true
                })


                ZodiacSignSelectionBottomSheet(
                    show = expanded,
                    onZodiacSelected = {
                        onZodiacSelected(it)
                        expanded = false
                    },
                    onClose = {
                        expanded = false
                    }
                )
            }

        }
    }
}



