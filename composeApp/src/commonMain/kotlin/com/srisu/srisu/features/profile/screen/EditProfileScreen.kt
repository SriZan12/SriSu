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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.Uri
import coil3.compose.AsyncImage
import coil3.toUri
import com.srisu.srisu.components.CityDropDown
import com.srisu.srisu.components.CountryDropDown
import com.srisu.srisu.components.FormFieldCompo
import com.srisu.srisu.components.PrimaryTextButton
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.components.TextAreaCompo
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.profile.state.EditProfileUIState
import com.srisu.srisu.features.profile.state.GalleyPhotoModel
import com.srisu.srisu.features.profile.vm.EditProfileViewModel
import com.srisu.srisu.navigation.Interest
import com.srisu.srisu.navigation.interestList
import com.srisu.srisu.permissionmanager.PermissionCallback
import com.srisu.srisu.permissionmanager.PermissionState
import com.srisu.srisu.permissionmanager.PermissionType
import com.srisu.srisu.permissionmanager.createPermissionsManager
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.MediaType
import com.srisu.srisu.utils.rememberGalleryManager
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

enum class PhotoType { LARGE, SMALL }


@Composable
fun EditProfileScreen(
    editedInterest: List<String?>? = null,
    session: Session?,
    editProfileViewModel: EditProfileViewModel = koinViewModel<EditProfileViewModel>(),
    onNavigateInterestScreen: (List<Interest>, List<String?>?) -> Unit
) {

    val editProfileUIState by editProfileViewModel.editProfileUIState.collectAsStateWithLifecycle()

    Initialization(
        session = session,
        editedInterest = editedInterest,
        editProfileViewModel = editProfileViewModel
    )

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
fun Initialization(
    session: Session? = null,
    editedInterest: List<String?>? = null,
    editProfileViewModel: EditProfileViewModel
) {
    LaunchedEffect(Unit) {
        editProfileViewModel.updateSession(session = session)
        if (!editedInterest.isNullOrEmpty()) {
            editProfileViewModel.updateInterests(interests = editedInterest)
        }
    }
}

@Composable
fun EditProfileScreenContent(
    editProfileUIState: EditProfileUIState,
    editProfileViewModel: EditProfileViewModel,
    onNavigateInterestScreen: (List<Interest>, List<String?>?) -> Unit,
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
                ProfilePictureCompo(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    editProfileViewModel = editProfileViewModel,
                    editProfileUIState = editProfileUIState
                )

                GeneralInfoCompo(
                    modifier = Modifier,
                    fullName = editProfileUIState.fullName,
                    userName = editProfileUIState.userName,
                    bio = editProfileUIState.bio,
                    country = editProfileUIState.country,
                    city = editProfileUIState.city,
                    cities = editProfileUIState.cities,
                    onUpdateFullName = { fullName ->
                        editProfileViewModel.updateFullName(fullName)
                    },
                    onUpdateUserName = { userName ->
                        editProfileViewModel.updateUserName(userName)
                    },
                    onUpdateBio = { bio ->
                        editProfileViewModel.updateBio(bio)

                    },
                    onUpdateCountry = { countryModel ->
                        editProfileViewModel.updateCountry(countryModel)
                    },
                    onUpdateCity = {}
                )

                InterestCompo(allInterests = editProfileUIState.interests) {
                    onNavigateInterestScreen(
                        interestList,
                        editProfileUIState.interests
                    )
                }

                val openGallery = remember { mutableStateOf(false) }
                val photoType = remember { mutableStateOf(PhotoType.LARGE) }
                val photoIndex = remember { mutableStateOf(0) }

                GalleryCompo(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { index, type ->
                    openGallery.value = true
                    photoIndex.value = index
                    photoType.value = type
                }

                if (openGallery.value) {
                    OpenGallery(showPermissionDialog = openGallery.value) { photoUri ->
                        if (photoType.value == PhotoType.LARGE) {
                            editProfileViewModel.updateLargePhoto(
                                photo = GalleyPhotoModel(
                                    photoUri = photoUri,
                                    index = photoIndex.value
                                )
                            )

                        } else {

                        }
                        openGallery.value = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePictureCompo(
    modifier: Modifier = Modifier,
    editProfileViewModel: EditProfileViewModel,
    editProfileUIState: EditProfileUIState,
) {
    val profilePictureUri = editProfileUIState.profilePictureUri
    var showPermissionDialog by remember { mutableStateOf(false) }
//    var permissionState by remember { mutableStateOf(PermissionState.NOT_ASKED_YET) }


    val permissionManager = createPermissionsManager(object : PermissionCallback {
        override fun onPermissionStatus(permissionType: PermissionType, status: PermissionState) {
            AppLogger.log("INSIDE CALLBACK = $status")
            when (status) {
                PermissionState.GRANTED -> {
//                    permissionState = PermissionState.GRANTED
                }

                PermissionState.SHOW_RATIONALE -> {
//                    permissionState = PermissionState.SHOW_RATIONALE
                }

                PermissionState.DENIED -> {
//                    permissionState = PermissionState.DENIED
                }

                PermissionState.NOT_ASKED_YET -> {
                }

                PermissionState.REQUEST_LAUNCHED -> {
//                    permissionState = PermissionState.REQUEST_LAUNCHED
                }
            }
        }
    })

    val galleryManager = rememberGalleryManager(
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                editProfileViewModel.updateProfilePictureUri(uri = uris.firstOrNull()?.toUri())
            }
        },
        mediaType = MediaType.IMAGE_ONLY
    )

    Box(
        modifier = modifier.padding(horizontal = 16.dp)
            .size(180.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceDim)
            .clickable {
                showPermissionDialog = true

            },
        contentAlignment = Alignment.Center
    ) {
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

        if (showPermissionDialog) {
            if (!permissionManager.isPermissionGranted(permission = PermissionType.STORAGE)) {
                permissionManager.askPermission(permission = PermissionType.STORAGE)
            } else {
                galleryManager.launch()
            }
            showPermissionDialog = false
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
    country: CountryModel? = null,
    city: String? = null,
    cities: List<String?>? = null,
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
            value = fullName ?: "",
            onValueChange = {
                onUpdateFullName(it)
            }
        )

        FormFieldCompo(
            label = "Username",
            value = userName ?: "",
            onValueChange = {
                onUpdateUserName(it)
            }
        )


        TextAreaCompo(
            label = "Bio",
            placeholder = "Enter your bio...",
            value = bio ?: "",
            onValueChange = { onUpdateBio(it) }
        )

        CountryDropDownCompo(
            selectedCountry = country,
            onCountrySelected = {
                onUpdateCountry(it)
            }
        )

        CityDropDownCompo(
            modifier = Modifier.fillMaxWidth(),
            selectedCity = city,
            onCitySelected = {
                onUpdateCity(it)
            },
            cityList = cities,
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
    allInterests: List<String?>? = null,
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
                label = if (allInterests.isNullOrEmpty()) "Add" else "Edit",
                textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary),
                fontWeight = FontWeight.SemiBold,
                onClick = {
                    onEditInterest()
                }
            )

        }


        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(items = allInterests ?: emptyList()) { interest ->
                if (!interest.isNullOrEmpty()) {
                    InterestChip(label = interest)
                }
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

typealias index = Int

@Composable
@Preview
fun GalleryCompo(
    modifier: Modifier = Modifier,
    largePhotos: List<GalleyPhotoModel?>? = emptyList(),
    smallPhotos: List<GalleyPhotoModel?>? = emptyList(),
    onAddImageClicked: (index, PhotoType) -> Unit
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
            val photos = largePhotos?.sortedBy { it?.index }
            photos?.forEachIndexed { index, photo ->
                GalleryAddCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    photoUri = photo?.photoUri,
                    onClick = { onAddImageClicked(index, PhotoType.LARGE) }
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
                    onClick = { onAddImageClicked(0, PhotoType.SMALL) }
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
    photoUri: Uri? = null,
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
                    if (photoUri == null) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Image",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Selected Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop

                        )
                    }
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

@Composable
private fun OpenGallery(
    showPermissionDialog: Boolean = false,
    photoPicked: (Uri?) -> Unit
) {
    var showPermissionDialog by remember { mutableStateOf(showPermissionDialog) }
//    var permissionState by remember { mutableStateOf(PermissionState.NOT_ASKED_YET) }


    val permissionManager = createPermissionsManager(object : PermissionCallback {
        override fun onPermissionStatus(permissionType: PermissionType, status: PermissionState) {
            AppLogger.log("INSIDE CALLBACK = $status")
            when (status) {
                PermissionState.GRANTED -> {
//                    permissionState = PermissionState.GRANTED
                }

                PermissionState.SHOW_RATIONALE -> {
//                    permissionState = PermissionState.SHOW_RATIONALE
                }

                PermissionState.DENIED -> {
//                    permissionState = PermissionState.DENIED
                }

                PermissionState.NOT_ASKED_YET -> {
                }

                PermissionState.REQUEST_LAUNCHED -> {
//                    permissionState = PermissionState.REQUEST_LAUNCHED
                }
            }
        }
    })

    val galleryManager = rememberGalleryManager(
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                photoPicked(uris.firstOrNull()?.toUri())
            }
        },
        mediaType = MediaType.IMAGE_ONLY
    )



    if (showPermissionDialog) {
        if (!permissionManager.isPermissionGranted(permission = PermissionType.STORAGE)) {
            permissionManager.askPermission(permission = PermissionType.STORAGE)
        } else {
            galleryManager.launch()
        }
    }

}

