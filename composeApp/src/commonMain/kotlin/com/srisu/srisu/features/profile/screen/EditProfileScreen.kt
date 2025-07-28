package com.srisu.srisu.features.profile.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.srisu.srisu.components.CityDropDown
import com.srisu.srisu.components.CountryDropDown
import com.srisu.srisu.components.FormFieldCompo
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.components.TextAreaCompo
import com.srisu.srisu.utils.CountryModel
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun EditProfileScreen() {
    EditProfileScreenContent()
}

@Composable
fun EditProfileScreenContent() {
    Scaffold(
        modifier = Modifier,
        topBar = {
            PrimaryToolBar(
                title = "Edit Profile",
                onNavigate = {

                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(all = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                EditPictureCompo(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                GeneralInfoCompo()

                InterestCompo()
            }
        }
    }
}

@Composable
fun EditPictureCompo(
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceDim)
            .clickable {

            },
        contentAlignment = Alignment.Center
    ) {
        val profilePictureUri = "http://localhost:8000/media/profiles/pexels-athena-1758144.jpg"
        if (profilePictureUri != null) {
            AsyncImage(
                model = profilePictureUri,
                contentDescription = "Selected Profile Picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                imageVector = Icons.Outlined.Person,
                contentDescription = "Image_picker",
                modifier = Modifier.size(80.dp)
            )
        }

    }
}

@Composable
private fun GeneralInfoCompo() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormFieldCompo(
            label = "Full Name",
            value = TextFieldValue("Srijan Khadka"),
            onValueChange = { }
        )

        FormFieldCompo(
            label = "Username",
            value = TextFieldValue("Davide"),
            onValueChange = { }
        )

        var description by remember { mutableStateOf("Hello I am davide") }

        TextAreaCompo(
            label = "Bio",
            placeholder = "Enter your bio...",
            value = description,
            onValueChange = { description = it }
        )

        CountryDropDownCompo(
            selectedCountry = CountryModel(name = "Nepal", "977", "+977"),
            onCountrySelected = {}
        )

        var selectedCity by remember { mutableStateOf<String?>(null) }
        val cityList = listOf("New York", "Los Angeles", "Chicago", "San Francisco", "Miami")

        CityDropDownCompo(
            modifier = Modifier.fillMaxWidth(),
            selectedCity = selectedCity,
            onCitySelected = {
                selectedCity = it
            },
            cityList = cityList,
        )

    }
}

@Composable
private fun CountryDropDownCompo(
    selectedCountry: CountryModel?,
    onCountrySelected: (CountryModel) -> Unit
) {
    var showCountryBottomSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {

        Text(
            text = "Country",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface

        )

        CountryDropDown(
            modifier = Modifier.fillMaxWidth(),
            option = selectedCountry,
            backgroundColor = MaterialTheme.colorScheme.surface,
            onOptionSelected = {
                onCountrySelected(it)
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
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {

        Text(
            text = "City",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,

            )

        CityDropDown(
            modifier = modifier,
            selectedCity = selectedCity,
            onCitySelected = onCitySelected,
            cityList = cityList,
            backgroundColor = MaterialTheme.colorScheme.surface,
            onExpandedChange = {
                expanded = !expanded
            },
            expanded = expanded
        )
    }
}

@Composable
private fun InterestCompo() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {

        Text(
            text = "Interest",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )

        LazyRow(modifier = Modifier.fillMaxWidth()){
            items(10){
                InterestChip(label = "Music")
            }
        }
    }
}

@Composable
@Preview
fun InterestChip(
    label: String,
    backgroundColor: Color = Color.LightGray,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.CenterStart)
        ) {
            Text(
                text = label,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(start = 16.dp, end = 32.dp, top = 8.dp, bottom = 8.dp)
                    .basicMarquee(iterations = 10),
                style = MaterialTheme.typography.labelMedium
            )
        }

        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-8).dp)
                .size(16.dp)
                .background(Color.White, shape = CircleShape)
                .clickable { onRemove() }
                .padding(2.dp),
            tint = Color.Black
        )
    }
}
