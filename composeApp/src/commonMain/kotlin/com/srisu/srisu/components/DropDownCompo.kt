package com.srisu.srisu.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.getCountryFlagFromAssets
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.country_flag

@Composable
fun DropDownIcon(
    expanded: Boolean = false,
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

        val dropDownIcon =
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown

        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = dropDownIcon,
            contentDescription = "Filter Icon",
        )
    }
}

@Composable
fun CountryCodeDropDown(
    modifier: Modifier = Modifier,
    selectedCountryPrefix: String,
    selectedCountryCode: String,
    onClick: () -> Unit
) {

    val flag by produceState<ImageBitmap?>(initialValue = null, key1 = selectedCountryCode) {
        value = getCountryFlagFromAssets(selectedCountryCode)
    }
    Card(
        modifier = modifier
            .wrapContentWidth()
            .height(54.dp),
        onClick = {
            onClick()

        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = CenterVertically
        ) {

            if (flag == null) {
                Image(
                    painter = painterResource(Res.drawable.country_flag),
                    contentDescription = "country_flag",
                    modifier = Modifier
                        .size(20.dp)
                )
            } else {
                Image(
                    bitmap = flag!!,
                    contentDescription = "flag",
                    modifier = Modifier
                        .size(20.dp)
                )
            }


            Text(
                text = selectedCountryPrefix,
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium
            )


            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
            )
        }
    }
}

@Composable
fun CountryDropDown(
    modifier: Modifier,
    countryList: List<CountryModel>,
    visibleLeadingIcon: Boolean = true,
    option: CountryModel? = null,
    onOptionSelected: (CountryModel) -> Unit,
    showCountryBottomSheet: Boolean,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onShowCountryBottomSheetChange: () -> Unit
) {

    val flag by produceState<ImageBitmap?>(initialValue = null, key1 = option?.code) {
        value = getCountryFlagFromAssets(option?.code ?: "")
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        onClick = {
            onShowCountryBottomSheetChange()
        },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                verticalAlignment = CenterVertically,
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
                            bitmap = flag!!,
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

            DropDownIcon(expanded = showCountryBottomSheet, onClick = {
                onShowCountryBottomSheetChange()
            })


            CountrySelectionBottomSheet(
                show = showCountryBottomSheet,
                countries = countryList,
                onCountrySelected = {
                    onOptionSelected(it)
                    onShowCountryBottomSheetChange()
                },
                onClose = {
                    onShowCountryBottomSheetChange()
                }
            )


        }
    }
}

@Composable
fun CityDropDown(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    selectedCity: String?,
    onCitySelected: (String) -> Unit,
    cityList: List<String?>?,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onExpandedChange: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = {
            onExpandedChange()
        },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, color = Color.Gray)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = CenterVertically
            ) {
                val city = if (selectedCity.isNullOrEmpty()) "Select City" else selectedCity
                Text(
                    text = city,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start
                )

                DropDownIcon(expanded = expanded) {
                    onExpandedChange()
                }
            }

            CitySelectionBottomSheet(
                show = expanded,
                onCitySelected = {
                    onCitySelected(it)
                    onExpandedChange()
                },
                onClose = {
                    onExpandedChange()
                },
                cityList = cityList
            )
        }
    }
}


