package com.srisu.srisu.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.utils.Country.getAllCountriesFromJson
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.getCountryFlagFromAssets
import com.srisu.srisu.theme.backgroundGraySecondary
import com.srisu.srisu.utils.ZodiacUtils
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.aries
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
            modifier = Modifier.fillMaxWidth(),
            sheetState = bottomSheetState,
            containerColor = Color.White,
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
    show: Boolean,
    onCountrySelected: (CountryModel) -> Unit,
    onClose: () -> Unit
) {

    if (show) {
        val countryList = getAllCountriesFromJson()

        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

        var isSearchOn by remember {
            mutableStateOf(false)
        }
        var filterCountryList by remember {
            mutableStateOf(listOf<CountryModel>())
        }

        ModalBottomSheet(
            modifier = Modifier.fillMaxSize(),
            sheetState = sheetState,
            containerColor = Color.White,
            onDismissRequest = {
                onClose()
            }) {

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                SearchBar(
                    modifier = Modifier.fillMaxWidth(),
                    hint = "Search Country",
                    onTextChange = { query ->

                        isSearchOn = true

                        if (query.isEmpty() || query.isBlank()) {
                            isSearchOn = false
                        }

                        countryList?.let { countryList ->
                            filterCountryList = countryList.filter {
                                it.name?.contains(query, ignoreCase = true) == true
                            }
                        }
                    }
                )

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    val countriesList = if (isSearchOn) filterCountryList else countryList

                    items(countriesList ?: emptyList()) { item ->
                        CountryCodeSelectionItem(
                            countryModel = item,
                            onCountrySelected = {
                                onCountrySelected(it)
                            }
                        )
                    }
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp)
                .clickable {
                    onCountrySelected(
                        countryModel
                    )
                },
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {


            val flag = getCountryFlagFromAssets(
                countryCode = countryModel.code ?: ""
            )

            Row(
                modifier = Modifier,
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (flag == null) {
                    Image(
                        painter = painterResource(Res.drawable.aries),
                        contentDescription = "country_flag",
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Image(
                        bitmap = flag,
                        contentDescription = "flag",
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = countryModel.name ?: "",
                    color = Color.Black,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Text(
                text = countryModel.prefix ?: "",
                color = Color.Black,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 12.dp),
                text = "Looks like you’re not connected to the internet. Check your network and try again.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            PrimaryButtonCompo(
                modifier = Modifier.fillMaxWidth().height(intrinsicSize = IntrinsicSize.Max)
                    .padding(vertical = 24.dp),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "You're All Set 🎉",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "Done_icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp).padding(bottom = 12.dp)
            )

            Text(
                text = "Your profile is now ready! Time to explore and Find ❤.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            PrimaryButtonCompo(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(IntrinsicSize.Max),
                label = "Explore now",
                onClick = {
                    onFirstButton()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            CustomButtonCompo(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                label = "View Profile",
                backgroundColor = backgroundGraySecondary,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.Black),
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
    cityList: List<String>,
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
            modifier = Modifier.fillMaxSize(),
            sheetState = sheetState,
            containerColor = Color.White,
            onDismissRequest = {
                onClose()
            }) {

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                SearchBar(
                    modifier = Modifier.fillMaxWidth(),
                    hint = "Search City",
                    onTextChange = { query ->

                        isSearchOn = true

                        if (query.isEmpty() || query.isBlank()) {
                            isSearchOn = false
                        }

                        filterCityList = cityList.filter {
                            it.contains(query, ignoreCase = true)
                        }
                    }
                )

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    val citiesList = if (isSearchOn) filterCityList else cityList

                    items(citiesList) { item ->
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

@Composable
private fun CitySelectionItem(
    city: String,
    onCitySelected: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = city,
                    color = Color.Black,
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
            modifier = Modifier.fillMaxSize(),
            sheetState = sheetState,
            containerColor = Color.White,
            onDismissRequest = {
                onClose()
            }) {

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                SearchBar(
                    modifier = Modifier.fillMaxWidth(),
                    hint = "Search Zodiac Sign",
                    onTextChange = { query ->

                        isSearchOn = true

                        if (query.isEmpty() || query.isBlank()) {
                            isSearchOn = false
                        }

                        filterZodiacList = zodiacList.filter {
                            it.sign.contains(query, ignoreCase = true)
                        }
                    }
                )

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                .padding(vertical = 10.dp, horizontal = 12.dp)
                .clickable {
                    onZodiacSignSelected(
                        zodiacSign
                    )
                },
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {


            val zodiacLogo = ZodiacUtils.getZodiacSignImage(
                name = zodiacSign.sign
            )

            Row(
                modifier = Modifier,
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (zodiacLogo != null) {
                    Image(
                        painter = painterResource(zodiacLogo),
                        contentDescription = "flag",
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = zodiacSign.sign ?: "",
                    color = Color.Black,
                    style = MaterialTheme.typography.titleSmall
                )
            }

        }

        HorizontalDivider()
    }
}

