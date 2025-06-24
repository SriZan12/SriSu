package com.srisu.srisu.features.suggestions.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.CitySelectionBottomSheet
import com.srisu.srisu.components.CountrySelectionBottomSheet
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.ZodiacSignSelectionBottomSheet
import com.srisu.srisu.features.suggestions.state.SuggestionUIStates
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import com.srisu.srisu.utils.getCountryFlagFromAssets
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.country_flag


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSuggestionScreen(
    suggestionViewModel: SuggestionViewModel = koinViewModel<SuggestionViewModel>(),
    onNavigateBack: () -> Unit
) {

    val filterUIState by suggestionViewModel.suggestionUIStates.collectAsStateWithLifecycle()

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
                onClick = {}
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues = paddingValues)) {
            FilterSuggestionCompo(
                suggestionViewModel = suggestionViewModel,
                filterUIState = filterUIState
            ) {

            }
        }
    }
}

@Composable
private fun FilterSuggestionCompo(
    suggestionViewModel: SuggestionViewModel,
    filterUIState: SuggestionUIStates,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            val minAge by filterUIState.minAge.collectAsState()
            val maxAge by filterUIState.maxAge.collectAsState()

            AgeFilterCompo(
                onReset = {
                    onDismiss()
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
                onReset = {

                },
                onOptionSelected = {
                    suggestionViewModel.getCityList(it.name?.lowercase())
                }
            )

            val cities by filterUIState.cities.collectAsState()
            val selectedCity by filterUIState.selectedCity.collectAsState()

            CityDropDownCompo(
                modifier = Modifier,
                selectedCity = selectedCity,
                onCitySelected = {
                    suggestionViewModel.updateSelectedCity(it)
                },
                cityList = cities ?: emptyList(),
                onReset = {}
            )

            val selectedZodiac by filterUIState.selectedZodiac.collectAsState()

            ZodiacSignCompo(
                modifier = Modifier,
                selectedZodiac = selectedZodiac,
                onZodiacSelected = {
                    suggestionViewModel.updateSelectedZodiac(it)
                },
                onReset = {}
            )

        }
    }

}

@Composable
private fun FilterTitle(
    showReset: Boolean = false,
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

        if (showReset) {
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
}

/*@Composable
private fun AgeFilterCompo(
    onReset: () -> Unit,
    onUpdateMinAge: () -> Unit,
    onUpdateMaxAge: () -> Unit,
    onChangeMinAge: (Int) -> Unit,
    onChangeMaxAge: (Int) -> Unit,
    minAge: Int,
    maxAge: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilterTitle(headerTitle = "Age", showReset = true) {
            onReset()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Min Age",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start
            )
            Text(
                modifier = Modifier.weight(1f),
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
            AgeFilterCardCompo(
                modifier = Modifier.weight(1f),
                age = minAge.toString(),
                onValueChanged = {
                    it.toIntOrNull()?.let { safeInt -> onChangeMinAge(safeInt) }
                },
                onAction = {
                    if (minAge + 1 <= maxAge) onUpdateMinAge()
                },
                canIncrement = minAge + 1 <= maxAge
            )

            AgeFilterCardCompo(
                modifier = Modifier.weight(1f),
                age = maxAge.toString(),
                onValueChanged = {
                    it.toIntOrNull()?.let { safeInt -> onChangeMaxAge(safeInt) }
                },
                onAction = {
                    if (maxAge + 1 <= MAX_AGE) onUpdateMaxAge()
                },
                canIncrement = maxAge + 1 <= MAX_AGE
            )
        }
    }
}*/

@Composable
fun AgeFilterCompo(
    onReset: () -> Unit,
    onChangeMinAge: (Int) -> Unit,
    onChangeMaxAge: (Int) -> Unit,
    minAge: Int,
    maxAge: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilterTitle(headerTitle = "Age", showReset = true) {
            onReset()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Min Age",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start
            )
            Text(
                modifier = Modifier.weight(1f),
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
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {

            AgeFilterDropdownCardCompo(
                modifier = Modifier.weight(1f),
                selectedAge = minAge,
                onAgeSelected = { selected ->
                    if (selected <= maxAge) onChangeMinAge(selected)
                }
            )

            AgeFilterDropdownCardCompo(
                modifier = Modifier.weight(1f),
                selectedAge = maxAge,
                onAgeSelected = { selected ->
                    if (selected >= minAge) onChangeMaxAge(selected)
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeFilterDropdownCardCompo(
    modifier: Modifier,
    selectedAge: Int,
    onAgeSelected: (Int) -> Unit
) {
    val ageOptions = (16..35).toList()
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            onClick = {
                expanded = !expanded
            },
            border = BorderStroke(1.dp, color = Color.Gray)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedAge.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start
                )

                DropDownIcon {
                    expanded = !expanded
                }
            }

        }

        ExposedDropdownMenu(
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
private fun AgeFilterCardCompo(
    modifier: Modifier,
    age: String,
    readOnly: Boolean = false,
    onValueChanged: (String) -> Unit,
    onAction: () -> Unit,
    canIncrement: Boolean
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
                onClick = {
                    if (canIncrement) onAction()
                },
                enabled = canIncrement,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (canIncrement) Color.Black else Color.LightGray
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = "Increase Age",
                    modifier = Modifier.size(24.dp),
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

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        onClick = {
            showCountryBottomSheet = true
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
fun CityDropDownCompo(
    modifier: Modifier = Modifier,
    selectedCity: String?,
    onCitySelected: (String) -> Unit,
    cityList: List<String?>?,
    onReset: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterTitle(headerTitle = "City") {
            onReset()
        }

        Card(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            onClick = {
                expanded = !expanded
            },
            border = BorderStroke(1.dp, color = Color.Gray)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedCity ?: "Select City",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start
                    )

                    DropDownIcon {
                        expanded = !expanded
                    }
                }

                CitySelectionBottomSheet(
                    show = expanded,
                    onCitySelected = {
                        onCitySelected(it)
                        expanded = false
                    },
                    onClose = {
                        expanded = false
                    },
                    cityList = cityList
                )
            }
        }
    }
}

@Composable
private fun ZodiacSignCompo(
    modifier: Modifier = Modifier,
    selectedZodiac: ZodiacSign?,
    onZodiacSelected: (ZodiacSign) -> Unit,
    onReset: () -> Unit
) {
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterTitle(headerTitle = "Zodiac Sign") {
            onReset()
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        modifier = Modifier,
                        text = selectedZodiac?.sign ?: "",
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

                Box(modifier = Modifier.padding(end = 8.dp)) {
                    DropDownIcon(onClick = {
                        expanded = true
                    })
                }

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
