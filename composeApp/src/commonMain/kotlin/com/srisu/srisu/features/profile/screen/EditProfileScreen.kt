package com.srisu.srisu.features.profile.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.srisu.srisu.components.CityDropdownCompo
import com.srisu.srisu.components.CountrySelectionBottomSheet
import com.srisu.srisu.components.FormFieldCompo
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.components.TextAreaCompo
import com.srisu.srisu.features.suggestions.screens.CountryDropDown
import com.srisu.srisu.theme.backgroundGray
import com.srisu.srisu.utils.CountryModel
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun EditProfileScreen() {

}

@Composable
@Preview
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding)){
            Column(modifier = Modifier.fillMaxWidth().padding(all = 16.dp)) {
                EditPictureCompo(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                GeneralInfoCompo()
            }
        }
    }
}

@Composable
fun EditPictureCompo(
    modifier: Modifier
){
    Box(
        modifier = modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(backgroundGray)
            .clickable {
//                showPermissionDialog = true

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
private fun GeneralInfoCompo(){
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormFieldCompo(
            label = "Full Name",
            value = TextFieldValue("Srijan Khadka"),
            onValueChange = {  }
        )

        FormFieldCompo(
            label = "Username",
            value = TextFieldValue("Davide"),
            onValueChange = {  }
        )

        var description by remember { mutableStateOf("Hello I am davide") }

        TextAreaCompo(
            label = "Bio",
            placeholder = "Enter your bio...",
            value = description,
            onValueChange = { description = it }
        )

        var selectedCity by remember { mutableStateOf<String?>(null) }

        val cityList = listOf("New York", "Los Angeles", "Chicago", "San Francisco", "Miami")

        CityDropdownCompo(
            selectedCity = selectedCity,
            onCitySelected = { selectedCity = it },
            cityList = cityList
        )

    }
}