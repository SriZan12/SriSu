package com.srisu.srisu.features.profile.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.srisu.srisu.components.CityDropDown
import com.srisu.srisu.components.CountryDropDown
import com.srisu.srisu.components.FormFieldCompo
import com.srisu.srisu.components.PrimaryTextButton
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.components.TextAreaCompo
import com.srisu.srisu.features.profile.state.EditProfileUIState
import com.srisu.srisu.features.profile.vm.EditProfileViewModel
import com.srisu.srisu.navigation.Interest
import com.srisu.srisu.navigation.interestList
import com.srisu.srisu.utils.CountryModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun EditProfileScreen(
    editProfileViewModel: EditProfileViewModel = koinViewModel<EditProfileViewModel>(),
    onNavigateInterestScreen: (List<Interest>, List<String>) -> Unit
) {

    val editProfileUIState by editProfileViewModel.editProfileUIState.collectAsStateWithLifecycle()

    EditProfileScreenContent(
        editProfileUIState = editProfileUIState,
        editProfileViewModel = editProfileViewModel,
        onNavigateInterestScreen = { interests, currentInterest ->
            onNavigateInterestScreen(
                interests,
                currentInterest
            )
        }
    )
}

@Composable
fun EditProfileScreenContent(
    editProfileUIState: EditProfileUIState,
    editProfileViewModel: EditProfileViewModel,
    onNavigateInterestScreen: (List<Interest>, List<String>) -> Unit,
) {
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
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditPictureCompo(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                GeneralInfoCompo(
                    modifier = Modifier,
                    fullName = editProfileUIState.fullName,
                    userName = editProfileUIState.userName,
                    bio = editProfileUIState.bio,
                    country = editProfileUIState.country,
                    city = editProfileUIState.city,
                    onUpdateFullName = {
                        editProfileViewModel.updateFullName("Srijan Khadka")
                    },
                    onUpdateUserName = {},
                    onUpdateBio = {},
                    onUpdateCountry = {},
                    onUpdateCity = {}
                )

                InterestCompo {
                    onNavigateInterestScreen(
                        interestList,
                        listOf("Football", "Jazz", "Hiking")
                    )
                }

                GalleryCompo(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun EditPictureCompo(
    modifier: Modifier
) {
    Box(
        modifier = modifier.padding(horizontal = 16.dp)
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

typealias fullName = String
typealias userName = String
typealias bio = String
typealias city = String

@Composable
private fun GeneralInfoCompo(
    modifier: Modifier = Modifier,
    fullName: String? = null,
    userName: String? = null,
    bio: String? = null,
    country: String? = null,
    city: String? = null,
    onUpdateFullName: (fullName) -> Unit,
    onUpdateUserName: (userName) -> Unit,
    onUpdateBio: (bio) -> Unit,
    onUpdateCountry: (CountryModel) -> Unit,
    onUpdateCity: (city) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FormFieldCompo(
            label = "Full Name",
            value = TextFieldValue(fullName ?: ""),
            onValueChange = {
                onUpdateFullName(it.text)
            }
        )

        FormFieldCompo(
            label = "Username",
            value = TextFieldValue(userName ?: ""),
            onValueChange = {
                onUpdateUserName(it.text)
            }
        )


        TextAreaCompo(
            label = "Bio",
            placeholder = "Enter your bio...",
            value = bio ?: "",
            onValueChange = { onUpdateBio(it) }
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
private fun InterestCompo(
    onEditInterest: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Interest",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )

            PrimaryTextButton(
                modifier = Modifier,
                label = "Edit",
                textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary),
                fontWeight = FontWeight.SemiBold,
                onClick = {

                }
            )

        }


        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(10) {
                InterestChip(label = "Music")
            }
        }
    }
}

@Composable
fun InterestChip(
    label: String,
    backGroundColor: Color = MaterialTheme.colorScheme.surfaceDim,
    onChipClick: () -> Unit = {}
) {

    Card(
        colors = CardDefaults.cardColors(containerColor = backGroundColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier,
        onClick = {
            onChipClick()
        }
    ) {
        Text(
            text = label,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .basicMarquee(iterations = 10),
            style = MaterialTheme.typography.labelMedium
        )
    }

}


@Composable
@Preview
fun GalleryCompo(
    modifier: Modifier = Modifier,
    onAddImageClicked: () -> Unit = {}
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Text(
            text = "Gallery",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // First row: 2 large cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                GalleryAddCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onAddImageClicked
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                GalleryAddCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onAddImageClicked
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PrimaryTextButton(
            modifier = Modifier.wrapContentWidth().align(Alignment.CenterHorizontally),
            label = "View all",
            textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.primary),
            fontWeight = FontWeight.SemiBold,
            onClick = {

            }
        )
    }
}

@Composable
fun GalleryAddCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier = modifier) {

        Card(
            modifier = Modifier
                .shadow(6.dp, shape = RoundedCornerShape(16.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                // Center Add Circle
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Image",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

            }
        }

        IconButton(
            onClick = { /* Handle close icon click */ },

            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .offset(y = (-6).dp)
                .clip(shape = CircleShape),
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)

        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.padding(all = 4.dp)
            )
        }
    }
}


