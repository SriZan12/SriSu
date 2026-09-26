package com.srisu.srisu.components

import com.srisu.srisu.theme.spacing
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.theme.sheetScrim
import com.srisu.srisu.utils.ZodiacUtils
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.offline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonBottomSheetCompo(
    show: Boolean = false,
    bottomSheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    ), onDismiss: () -> Unit, content: @Composable () -> Unit
) {

    if (show) {
        ModalBottomSheet(
            scrimColor = MaterialTheme.colorScheme.sheetScrim,
            modifier = Modifier.fillMaxWidth(),
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = {
                onDismiss()
            }) {
            content()
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectionBottomSheet(
    modifier: Modifier = Modifier,
    countries: List<CountryModel>,
    show: Boolean,
    onCountrySelected: (CountryModel) -> Unit,
    onClose: () -> Unit
) {
    if (!show) return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    var query by remember { mutableStateOf("") }

    val filteredCountries by remember(countries, query) {
        derivedStateOf {
            if (query.isBlank()) {
                countries
            } else {
                countries.filter {
                    it.name?.contains(query, ignoreCase = true) == true
                }
            }
        }
    }

    ModalBottomSheet(
            scrimColor = MaterialTheme.colorScheme.sheetScrim,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onClose
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.compact)
        ) {
            SearchBar(
                modifier = Modifier.fillMaxWidth(),
                hint = "Search Country",
                onTextChange = { query = it }
            )

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                contentPadding = PaddingValues(vertical = MaterialTheme.spacing.small)
            ) {
                items(
                    items = filteredCountries,
                    key = { it.code ?: it.name.orEmpty() }
                ) { item ->
                    CountryCodeSelectionItem(
                        countryModel = item,
                        onCountrySelected = onCountrySelected
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryCodeSelectionItem(
    countryModel: CountryModel,
    onCountrySelected: (CountryModel) -> Unit,
) {
    val countryCode = countryModel.code.orEmpty()

//    var flag by remember(countryCode) { mutableStateOf<ImageBitmap?>(null) }
//
//    LaunchedEffect(countryCode) {
//        flag = if (countryCode.isNotBlank()) {
//            getCountryFlagFromAssets(countryCode)
//        } else {
//            null
//        }
//    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCountrySelected(countryModel) }
                .padding(vertical = 10.dp, horizontal = MaterialTheme.spacing.compact),
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
//                if (flag != null) {
//                    Image(
//                        bitmap = flag!!,
//                        contentDescription = "flag",
//                        modifier = Modifier.size(22.dp)
//                    )
//                } else {
//                    Image(
//                        painter = painterResource(Res.drawable.country_flag),
//                        contentDescription = "country_flag",
//                        modifier = Modifier.size(22.dp)
//                    )
//                }

                Text(
                    text = countryModel.name.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Text(
                text = countryModel.prefix.orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall
            )
        }

        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineBottomSheetCompo(
    show: Boolean,
    onDismiss: () -> Unit
) {

    CommonBottomSheetCompo(
        show = show,
        onDismiss = {
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.medium),
                text = "Oops! You're Offline",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )

            Image(
                painter = painterResource(Res.drawable.offline),
                contentDescription = "Offline_icon",
                modifier = Modifier.size(44.dp).align(Alignment.CenterHorizontally)
            )

            Text(
                modifier = Modifier.fillMaxWidth().padding(start = MaterialTheme.spacing.compact, top = MaterialTheme.spacing.compact, end = MaterialTheme.spacing.compact),
                text = "Looks like you’re not connected to the internet. Check your network and try again.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            PrimaryButtonCompo(
                modifier = Modifier.fillMaxWidth().height(intrinsicSize = IntrinsicSize.Max)
                    .padding(vertical = MaterialTheme.spacing.large),
                label = "Close"
            ) {
                onDismiss()
            }
        }
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessBottomSheet(
    show: Boolean,
    onFirstButton: () -> Unit,
    onSecondButton: () -> Unit,
    onDismiss: () -> Unit
) {
    CommonBottomSheetCompo(show = show, onDismiss = {
        onDismiss()
    }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "You're All Set 🎉",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.medium),
                textAlign = TextAlign.Center
            )

            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "Done_icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp).padding(bottom = MaterialTheme.spacing.compact)
            )

            Text(
                text = "Your profile is now ready! Time to explore and Find ❤.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            PrimaryButtonCompo(
                modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.large).height(IntrinsicSize.Max),
                label = "Explore now",
                onClick = {
                    onFirstButton()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            CustomButtonCompo(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                label = "View Profile",
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                onClick = {
                    onSecondButton()
                }
            )
            Spacer(modifier = Modifier.height(24.dp))

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySelectionBottomSheet(
    show: Boolean,
    cityList: List<String?>?,
    onClose: () -> Unit,
    onCitySelected: (String) -> Unit
) {
    if (show) {

        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

        var isSearchOn by remember {
            mutableStateOf(false)
        }
        var filterCityList by remember {
            mutableStateOf(cityList)
        }

        ModalBottomSheet(
            scrimColor = MaterialTheme.colorScheme.sheetScrim,
            modifier = Modifier.fillMaxSize(),
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = {
                onClose()
            }) {

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.compact)) {
                SearchBar(
                    modifier = Modifier.fillMaxWidth(),
                    hint = "Search City",
                    onTextChange = { query ->

                        isSearchOn = true

                        if (query.isEmpty() || query.isBlank()) {
                            isSearchOn = false
                        }

                        filterCityList = cityList?.filter {
                            it?.contains(query, ignoreCase = true) == true
                        }
                    }
                )

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {

                    val citiesList = if (isSearchOn) filterCityList else cityList

                    items(citiesList ?: emptyList()) { item ->
                        if (!item.isNullOrEmpty()) {
                            CitySelectionItem(
                                city = item,
                                onCitySelected = {
                                    onCitySelected(it)
                                }
                            )
                        }
                    }
                }

            }
        }
    }

}

@Composable
private fun CitySelectionItem(
    city: String,
    onCitySelected: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = MaterialTheme.spacing.compact)
                .clickable {
                    onCitySelected(
                        city
                    )
                },
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier,
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                Text(
                    text = city,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZodiacSignSelectionBottomSheet(
    show: Boolean,
    onZodiacSelected: (ZodiacUtils.ZodiacSign) -> Unit,
    onClose: () -> Unit
) {

    if (show) {
        val zodiacList = ZodiacUtils.getZodiacSignList()

        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

        var isSearchOn by remember {
            mutableStateOf(false)
        }

        var filterZodiacList by remember {
            mutableStateOf(listOf<ZodiacUtils.ZodiacSign>())
        }

        ModalBottomSheet(
            scrimColor = MaterialTheme.colorScheme.sheetScrim,
            modifier = Modifier.fillMaxSize(),
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = {
                onClose()
            }) {

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.compact)) {
                SearchBar(
                    modifier = Modifier.fillMaxWidth(),
                    hint = "Search Zodiac Sign",
                    onTextChange = { query ->

                        isSearchOn = true

                        if (query.isEmpty() || query.isBlank()) {
                            isSearchOn = false
                        }

                        filterZodiacList = zodiacList.filter {
                            it.name.contains(query, ignoreCase = true)
                        }
                    }
                )

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {

                    val zodiacs = if (isSearchOn) filterZodiacList else zodiacList

                    items(zodiacs) { item ->
                        ZodiacSignSelectionItem(
                            zodiacSign = item,
                            onZodiacSignSelected = {
                                onZodiacSelected(it)
                            }
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun ZodiacSignSelectionItem(
    zodiacSign: ZodiacUtils.ZodiacSign,
    onZodiacSignSelected: (ZodiacUtils.ZodiacSign) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = MaterialTheme.spacing.compact)
                .clickable {
                    onZodiacSignSelected(
                        zodiacSign
                    )
                },
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {


            val zodiacLogo = ZodiacUtils.getZodiacSignImage(
                name = zodiacSign.name
            )

            Row(
                modifier = Modifier,
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                if (zodiacLogo != null) {
                    Image(
                        painter = painterResource(zodiacLogo),
                        contentDescription = "flag",
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = zodiacSign.name ?: "",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
            }

        }

        HorizontalDivider()
    }
}

