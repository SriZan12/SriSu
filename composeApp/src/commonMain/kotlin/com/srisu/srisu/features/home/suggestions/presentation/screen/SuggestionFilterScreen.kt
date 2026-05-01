package com.srisu.srisu.features.home.suggestions.presentation.screen

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.CityDropDown
import com.srisu.srisu.components.CountryDropDown
import com.srisu.srisu.components.DropDownIcon
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.ZodiacSignSelectionBottomSheet
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.home.suggestions.presentation.state.SuggestionUIStates
import com.srisu.srisu.features.home.suggestions.presentation.vm.SuggestionViewModel
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FilterSuggestionScreen(
    suggestionViewModel: SuggestionViewModel,
    onNavigateBack: () -> Unit,
    onClearFilter: () -> Unit,
    onFilterApplied: () -> Unit,
) {
    val suggestionUIStates by suggestionViewModel.suggestionUIStates.collectAsStateWithLifecycle()

    FilterSuggestionScreenEffects(
        suggestionViewModel = suggestionViewModel,
    )

    FilterSuggestionFeedbackLayer(
        suggestionVM = suggestionViewModel,
        suggestionUIStates = suggestionUIStates,
    )

    FilterSuggestionScaffold(
        suggestionViewModel = suggestionViewModel,
        suggestionUIStates = suggestionUIStates,
        onNavigateBack = onNavigateBack,
        onClearFilter = onClearFilter,
        onFilterApplied = onFilterApplied,
    )

    BackHandler {
        onNavigateBack()
    }
}

@Composable
private fun FilterSuggestionScreenEffects(
    suggestionViewModel: SuggestionViewModel,
) {
    LaunchedEffect(Unit) {
        suggestionViewModel.getPreferences()
    }
}

@Composable
private fun FilterSuggestionFeedbackLayer(
    suggestionVM: SuggestionViewModel,
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
                onDismiss = suggestionVM::idleScreen,
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = {},
            )
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
                suggestionVM.idleScreen()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSuggestionScaffold(
    suggestionViewModel: SuggestionViewModel,
    suggestionUIStates: SuggestionUIStates,
    onNavigateBack: () -> Unit,
    onClearFilter: () -> Unit,
    onFilterApplied: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        topBar = {
            FilterSuggestionTopBar(
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            PrimaryButtonCompo(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                label = "Apply Filter",
                onClick = {
                    if (suggestionUIStates.userPreferences == null) {
                        suggestionViewModel.setUserPreferences(onFilterApplied)
                    } else {
                        suggestionViewModel.updateUserPreferences(
                            onPreferencesSuccess = onFilterApplied,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            FilterSuggestionContent(
                minAge = suggestionUIStates.minAge,
                maxAge = suggestionUIStates.maxAge,
                selectedCountry = suggestionUIStates.selectedCountry,
                countryList = suggestionUIStates.countryList,
                cities = suggestionUIStates.cities,
                selectedCity = suggestionUIStates.selectedCity,
                selectedZodiac = suggestionUIStates.selectedZodiac,
                onClearFilterClicked = {
                    suggestionViewModel.clearFilters()
                    suggestionViewModel.updateUserPreferences(
                        isClear = true,
                        onPreferencesSuccess = onClearFilter,
                    )
                },
                onMinAgeChanged = suggestionViewModel::updateMinAge,
                onMaxAgeChanged = suggestionViewModel::updateMaxAge,
                onCountrySelected = { country ->
                    suggestionViewModel.updateSelectedCountry(country)
                    suggestionViewModel.updateSelectedCity("")
                    suggestionViewModel.getCityList(
                        country = country.name?.lowercase(),
                        showLoading = true,
                    )
                },
                onCitySelected = suggestionViewModel::updateSelectedCity,
                onZodiacSelected = suggestionViewModel::updateSelectedZodiac,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSuggestionTopBar(
    onNavigateBack: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        title = {
            Text(
                text = "Filter Suggestions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close Icon",
                )
            }
        },
    )
}

@Composable
private fun FilterSuggestionContent(
    minAge: Int,
    maxAge: Int,
    selectedCountry: CountryModel?,
    countryList: List<CountryModel>,
    cities: List<String?>?,
    selectedCity: String?,
    selectedZodiac: ZodiacSign?,
    onClearFilterClicked: () -> Unit,
    onMinAgeChanged: (Int) -> Unit,
    onMaxAgeChanged: (Int) -> Unit,
    onCountrySelected: (CountryModel) -> Unit,
    onCitySelected: (String) -> Unit,
    onZodiacSelected: (ZodiacSign) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AgeFilterSection(
                onClearFilter = onClearFilterClicked,
                minAge = minAge,
                maxAge = maxAge,
                onChangeMinAge = onMinAgeChanged,
                onChangeMaxAge = onMaxAgeChanged,
            )

            CountryFilterSection(
                selectedCountry = selectedCountry,
                countryList = countryList,
                onOptionSelected = onCountrySelected,
            )

            CityFilterSection(
                selectedCity = selectedCity,
                onCitySelected = onCitySelected,
                cityList = cities ?: emptyList(),
            )

            ZodiacFilterSection(
                selectedZodiac = selectedZodiac,
                onZodiacSelected = onZodiacSelected,
            )
        }
    }
}

@Composable
private fun FilterTitle(
    headerTitle: String,
    showReset: Boolean = false,
    onClearFilter: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = headerTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (showReset) {
            TextButton(onClick = onClearFilter) {
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
fun AgeFilterSection(
    onClearFilter: () -> Unit,
    onChangeMinAge: (Int) -> Unit,
    onChangeMaxAge: (Int) -> Unit,
    minAge: Int,
    maxAge: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilterTitle(
            headerTitle = "Age",
            showReset = true,
            onClearFilter = onClearFilter,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            AgeFilterDropdownCard(
                modifier = Modifier.weight(1f),
                selectedAge = minAge,
                headerTitle = "Min Age",
                onAgeSelected = onChangeMinAge,
            )

            AgeFilterDropdownCard(
                modifier = Modifier.weight(1f),
                selectedAge = maxAge,
                headerTitle = "Max Age",
                onAgeSelected = onChangeMaxAge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeFilterDropdownCard(
    modifier: Modifier,
    selectedAge: Int,
    headerTitle: String,
    onAgeSelected: (Int) -> Unit,
) {
    val ageOptions = remember { (16..35).toList() }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        val selectedAgeText = if (selectedAge == 0) "" else selectedAge.toString()

        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            readOnly = true,
            value = selectedAgeText,
            onValueChange = {},
            label = { Text(headerTitle) },
            trailingIcon = {
                DropDownIcon(expanded = expanded) {
                    expanded = !expanded
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.Black,
            ),
        )

        ExposedDropdownMenu(
            modifier = Modifier.height(300.dp),
            expanded = expanded,
            shape = RoundedCornerShape(12.dp),
            onDismissRequest = { expanded = false },
        ) {
            ageOptions.forEach { age ->
                DropdownMenuItem(
                    text = { Text(age.toString()) },
                    onClick = {
                        onAgeSelected(age)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun CountryFilterSection(
    selectedCountry: CountryModel?,
    countryList: List<CountryModel>,
    onOptionSelected: (CountryModel) -> Unit,
) {
    var showCountryBottomSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterTitle(headerTitle = "Country")

        CountryDropDown(
            modifier = Modifier.fillMaxWidth(),
            countryList = countryList,
            option = selectedCountry,
            onOptionSelected = onOptionSelected,
            onShowCountryBottomSheetChange = {
                showCountryBottomSheet = !showCountryBottomSheet
            },
            showCountryBottomSheet = showCountryBottomSheet,
        )
    }
}

@Composable
private fun CityFilterSection(
    selectedCity: String?,
    onCitySelected: (String) -> Unit,
    cityList: List<String?>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterTitle(headerTitle = "City")

        CityDropDown(
            modifier = modifier,
            selectedCity = selectedCity,
            onCitySelected = onCitySelected,
            cityList = cityList,
            onExpandedChange = {
                expanded = !expanded
            },
            expanded = expanded,
        )
    }
}

@Composable
private fun ZodiacFilterSection(
    selectedZodiac: ZodiacSign?,
    onZodiacSelected: (ZodiacSign) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val zodiacSignImg = remember(selectedZodiac?.name) {
        ZodiacUtils.getZodiacSignImage(selectedZodiac?.name ?: "")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterTitle(headerTitle = "Zodiac Sign")

        Card(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            onClick = { expanded = true },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(1.dp, Color.Gray),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = selectedZodiac?.name ?: "Select Zodiac Sign",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                    )

                    zodiacSignImg?.let {
                        Image(
                            painter = painterResource(it),
                            contentDescription = "zodiac_sign",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                DropDownIcon(
                    expanded = expanded,
                    onClick = { expanded = true },
                )
            }
        }

        ZodiacSignSelectionBottomSheet(
            show = expanded,
            onZodiacSelected = {
                onZodiacSelected(it)
                expanded = false
            },
            onClose = {
                expanded = false
            },
        )
    }
}
