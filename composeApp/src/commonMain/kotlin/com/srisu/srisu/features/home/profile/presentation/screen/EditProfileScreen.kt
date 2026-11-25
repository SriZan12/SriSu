package com.srisu.srisu.features.home.profile.presentation.screen

import com.srisu.srisu.components.NavigationBackButton
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.theme.field
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import com.srisu.srisu.components.DraftOutlinedTextField
import com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState
import com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.backhandler.BackHandler
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.Uri
import coil3.compose.AsyncImage
import coil3.toUri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.CityDropDown
import com.srisu.srisu.components.CountryDropDown
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.FormFieldCompo
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.PrimaryTextButton
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.components.SuccessDialog
import com.srisu.srisu.components.TextAreaCompo
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.core.permissionmanager.PermissionCallback
import com.srisu.srisu.core.permissionmanager.PermissionState
import com.srisu.srisu.core.permissionmanager.PermissionType
import com.srisu.srisu.core.permissionmanager.createPermissionsManager
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.MediaType
import com.srisu.srisu.utils.isInternetAvailable
import com.srisu.srisu.utils.rememberGalleryManager
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

enum class PhotoType { LARGE, SMALL }


@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun EditProfileScreen(
    editedInterest: List<User.UserInterest?>? = null,
    editProfileViewModel: com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel = koinViewModel<com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel>(),
    onNavigateInterestScreen: (List<InterestResponse.Interest?>?, List<User.UserInterest?>?) -> Unit,
    onBack: (() -> Unit)? = null,
    onSystemBack: (() -> Unit)? = onBack,
    onExplore: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
) {

    val editProfileUIState by editProfileViewModel.editProfileUIState.collectAsStateWithLifecycle()
    val initialized by editProfileViewModel.isInitialized.collectAsStateWithLifecycle()
    val initializationError by editProfileViewModel.initializationError.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave: (() -> Unit)? = onSystemBack?.let { callback -> {
        if (editProfileViewModel.hasUnsavedChanges()) confirmDiscard = true else callback()
    } }
    BackHandler(enabled = onSystemBack != null && (onBack != null || editProfileViewModel.hasUnsavedChanges())) { leave?.invoke() }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard profile changes?") },
        text = { Text("Your changes haven’t been saved yet.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onSystemBack?.invoke() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })

    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out?") }, text = { Text("Unsaved profile edits will be discarded. Your shared content stays in your account.") },
        confirmButton = { TextButton(onClick = editProfileViewModel::signOut) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } })
    if (!initialized) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(MaterialTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            onBack?.let { NavigationBackButton(it) }
            Text("Edit Profile", style = MaterialTheme.typography.titleLarge)
            if (initializationError == null) CircularProgressIndicator()
            else { Text(initializationError!!); TextButton(onClick = editProfileViewModel::retryInitialization) { Text("Retry") } }
            TextButton(onClick = { confirmSignOut = true }) { Text("Sign out") }
        }
        return
    }


    Initialization(
        editedInterest = editedInterest,
        editProfileViewModel = editProfileViewModel
    )

    HandleUiStates(
        editProfileViewModel = editProfileViewModel,
        editProfileUIState = editProfileUIState
    )

    EditProfileScreenContent(
        editProfileUIState = editProfileUIState,
        editProfileViewModel = editProfileViewModel,
        onBack = leave.takeIf { onBack != null },
        onExplore = onExplore,
        onNotifications = onNotifications,
        onSignOut = { confirmSignOut = true },
        onNavigateInterestScreen = { currentInterest ->
            onNavigateInterestScreen(
                editProfileUIState.interestList,
                currentInterest
            )
        }
    )

}

@Composable
fun Initialization(
    editedInterest: List<User.UserInterest?>? = null,
    editProfileViewModel: com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel
) {
    LaunchedEffect(editedInterest) {
        if (editedInterest != null) {
            editProfileViewModel.updateCurrentInterests(interests = editedInterest)
        }
    }
}

@Composable
private fun HandleUiStates(
    editProfileViewModel: com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel,
    editProfileUIState: com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState
) {

    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = editProfileUIState.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    editProfileViewModel.idleScreen()
                },
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = {
                    editProfileViewModel.idleScreen()
                }
            )
        }

        is BaseUIState.Success<*> -> {
            SuccessDialog(
                successMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    editProfileViewModel.idleScreen()
                }
            )
        }

        is BaseUIState.NoInternetConnection -> {
            showBottomSheet = baseUIState.isOffline
        }

        is BaseUIState.Idle -> {
            Unit
        }
    }

    if (showBottomSheet) {
        OfflineBottomSheetCompo(
            show = showBottomSheet,
            onDismiss = {
                showBottomSheet = false
                editProfileViewModel.idleScreen()
            }
        )
    }
}

internal data class ProfileFormActions(
    val name: (String) -> Unit = {}, val username: (String) -> Unit = {},
    val bio: (String) -> Unit = {}, val country: (CountryModel) -> Unit = {},
    val city: (String) -> Unit = {}, val photo: (Uri?) -> Unit = {},
    val gallery: (GalleyPhotoModel, PhotoType) -> Unit = { _, _ -> },
    val interests: () -> Unit = {}, val save: () -> Unit = {}, val signOut: () -> Unit = {},
    val retryInterests: () -> Unit = {},
    val sharePartnerDetails: (Boolean) -> Unit = {},
)

@Composable
fun EditProfileScreenContent(
    editProfileUIState: EditProfileUIState,
    editProfileViewModel: com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel,
    onNavigateInterestScreen: (List<User.UserInterest?>?) -> Unit,
    onBack: (() -> Unit)? = null,
    onSignOut: () -> Unit = {},
    onExplore: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
) {
    val catalogue by editProfileViewModel.catalogueState.collectAsStateWithLifecycle()
    val notice = when {
        catalogue.loading -> "Loading interests…"
        catalogue.offline -> "Showing saved interests while offline."
        catalogue.error != null -> catalogue.error?.message
        catalogue.loaded && catalogue.items.isEmpty() -> "No interests available yet."
        else -> null
    }
    EditProfileForm(editProfileUIState, ProfileFormActions(
        name=editProfileViewModel::updateFullName, username=editProfileViewModel::updateUserName,
        bio=editProfileViewModel::updateBio, country={ editProfileViewModel.updateCountry(it);editProfileViewModel.updateCity("") },
        city=editProfileViewModel::updateCity, photo=editProfileViewModel::updateProfilePictureUri,
        gallery={photo,type -> if(type==PhotoType.LARGE) editProfileViewModel.updateLargePhoto(photo) else editProfileViewModel.updateSmallPhotos(photo)},
        interests={onNavigateInterestScreen(editProfileUIState.currentInterests)}, save=editProfileViewModel::updateProfile,
        signOut=onSignOut, retryInterests=editProfileViewModel::refreshCatalogue,
        sharePartnerDetails=editProfileViewModel::updatePartnerDetailsSharing),
        onBack,onExplore,onNotifications,notice,catalogue.error!=null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileForm(
    state: EditProfileUIState, actions: ProfileFormActions,
    onBack: (() -> Unit)? = null, onExplore: (() -> Unit)? = null, onNotifications: (() -> Unit)? = null,
    interestsNotice: String? = null, retryInterests: Boolean = false,
) {
    val space=MaterialTheme.spacing
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var galleryTarget by remember { mutableStateOf<Triple<Int,PhotoType,Int?>?>(null) }
    Scaffold(modifier=Modifier.imePadding(),containerColor=MaterialTheme.colorScheme.background,
        topBar={TopAppBar(title={Text("Edit Profile",style=MaterialTheme.typography.titleLarge)},
            navigationIcon={onBack?.let {NavigationBackButton(it)}},
            actions={
                onExplore?.let {IconButton(onClick=it) {Icon(Icons.Outlined.Explore,"Explore SriSu")}}
                onNotifications?.let {IconButton(onClick=it) {Icon(Icons.Outlined.Settings,"Settings")}}
            }, colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))}
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal=space.gutter,vertical=space.small).testTag("profile-form"),
                verticalArrangement=Arrangement.spacedBy(space.medium)) {
                ProfilePictureCompo(Modifier.align(Alignment.CenterHorizontally),state.profilePictureUri,actions.photo)
                GeneralInfoCompo(countryList=state.countryList,fullName=state.fullName,userName=state.userName,
                    bio=state.bio,country=state.country,city=state.city,cities=state.cities,
                    onUpdateFullName=actions.name,onUpdateUserName=actions.username,onUpdateBio=actions.bio,
                    onUpdateCountry=actions.country,onUpdateCity=actions.city)
                interestsNotice?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
                if(retryInterests) TextButton(onClick=actions.retryInterests) {Text("Retry interests")}
                InterestCompo(state.currentInterests.orEmpty().filterNotNull().filter {it.removed!=true}.map {it.name},actions.interests)
                Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(space.tiny)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(space.compact)) {
                        Text("Share bio and interests in Find Partner", style=MaterialTheme.typography.titleSmall,
                            modifier=Modifier.weight(1f))
                        Switch(checked=state.sharePartnerDetails == true, onCheckedChange=actions.sharePartnerDetails,
                            enabled=state.sharePartnerDetails != null,
                            modifier=Modifier.semantics { contentDescription = "Share bio and interests in Find Partner" })
                    }
                    Text(if (state.sharePartnerDetails == null) "Reload your profile to change this preference."
                        else "People who find you can see these details. Extra photos stay private. Save to apply.",
                        style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GalleryCompo(largePhotos=state.largePhotos,smallPhotos=state.smallPhotos,
                    onAddImageClicked={index,type,id -> galleryTarget=Triple(index,type,id)},
                    onRemoveImage={index,type,id -> actions.gallery(GalleyPhotoModel(id=id,photoUri=null,removed=true,index=index),type)})
                PrimaryButtonCompo(modifier=Modifier.fillMaxWidth(),label="Update Profile",onClick={
                    focus.clearFocus();keyboard?.hide();actions.save()
                })
                TextButton(onClick=actions.signOut,modifier=Modifier.align(Alignment.CenterHorizontally)) {Text("Sign out")}
            }
        }
    }
    galleryTarget?.let {target -> OpenGallery { uri ->
        if(uri!=null) actions.gallery(GalleyPhotoModel(id=target.third,photoUri=uri,index=target.first),target.second)
        galleryTarget=null
    } }
}

@Composable
private fun ProfilePictureCompo(modifier: Modifier=Modifier,profilePictureUri: Uri?,onUpdateProfilePictureUri:(Uri?)->Unit) {
    // Android's Photo Picker and iOS's picker grant access only to the selected image.
    var picking by remember { mutableStateOf(false) }
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel="Change profile photo",role=Role.Button) {picking=true}.testTag("profile-avatar"),contentAlignment=Alignment.Center) {
            if(profilePictureUri!=null) AsyncImage(profilePictureUri,"Profile photo",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Icon(Icons.Outlined.Person,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick={picking=true}) {Text("Change photo",style=MaterialTheme.typography.labelMedium)}
    }
    if(picking) OpenGallery {uri -> uri?.let(onUpdateProfilePictureUri);picking=false}
}


typealias fullName = String
typealias userName = String
typealias bio = String
typealias city = String

@Composable
private fun GeneralInfoCompo(
    modifier: Modifier = Modifier,
    countryList: List<CountryModel>,
    fullName: String? = null,
    userName: String? = null,
    bio: String? = null,
    country: CountryModel? = null,
    city: String? = null,
    cities: List<String?>? = null,
    onUpdateFullName: (com.srisu.srisu.features.home.profile.presentation.screen.fullName) -> Unit,
    onUpdateUserName: (com.srisu.srisu.features.home.profile.presentation.screen.userName) -> Unit,
    onUpdateBio: (com.srisu.srisu.features.home.profile.presentation.screen.bio) -> Unit,
    onUpdateCountry: (CountryModel) -> Unit,
    onUpdateCity: (com.srisu.srisu.features.home.profile.presentation.screen.city) -> Unit,
) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val next=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Down)},onDone={focus.clearFocus();keyboard?.hide()})
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
        DraftOutlinedTextField(value=fullName.orEmpty(),onValueChange=onUpdateFullName,modifier=Modifier.fillMaxWidth().testTag("profile-name"),
            label={Text("Full Name")},shape=MaterialTheme.shapes.field,maxLines=1,maxLength=100,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),keyboardActions=next)
        DraftOutlinedTextField(value=userName.orEmpty(),onValueChange=onUpdateUserName,modifier=Modifier.fillMaxWidth().testTag("profile-username"),
            label={Text("Username")},shape=MaterialTheme.shapes.field,maxLines=1,maxLength=150,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next,autoCorrectEnabled=false),keyboardActions=next)
        DraftOutlinedTextField(value=bio.orEmpty(),onValueChange=onUpdateBio,modifier=Modifier.fillMaxWidth().heightIn(min=96.dp).testTag("profile-bio"),
            label={Text("Bio")},shape=MaterialTheme.shapes.field,maxLines=4,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=next)
        CountryDropDownCompo(country,countryList,onUpdateCountry)
        CityDropDownCompo(Modifier.fillMaxWidth(),city,onUpdateCity,cities)
    }
}

@Composable
private fun CountryDropDownCompo(
    selectedCountry: CountryModel?,
    countryList: List<CountryModel>,
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
            countryList = countryList,
            option = selectedCountry,
            placeholder = "Select country",
            elevation = 0.dp,
            backgroundColor = MaterialTheme.colorScheme.surface,
            onOptionSelected = onCountrySelected,
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
            elevation = 0.dp,
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
            modifier = Modifier.fillMaxWidth(),
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
                onClick = onEditInterest
            )

        }


        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            contentPadding = PaddingValues(0.dp)
        ) {
            items(items = allInterests ?: emptyList(), key = { it ?: "" }) { interest ->
                if (!interest.isNullOrEmpty()) {
                    _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.InterestChip(
                        modifier = Modifier,
                        label = interest
                    )
                }
            }
        }
    }
}


@Composable
fun InterestChip(
    modifier: Modifier,
    label: String,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceDim,
    iterations: Int = 10,
    onChipClick: () -> Unit = {}
) {

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = MaterialTheme.shapes.large,
        onClick = onChipClick
    ) {
        Text(
            text = label,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                .basicMarquee(iterations = iterations),
            style = MaterialTheme.typography.labelMedium
        )
    }

}

typealias index = Int

@Composable
fun GalleryCompo(
    modifier: Modifier = Modifier,
    largePhotos: List<com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel?>? = emptyList(),
    smallPhotos: List<com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel?>? = emptyList(),
    onAddImageClicked: (com.srisu.srisu.features.home.profile.presentation.screen.index, com.srisu.srisu.features.home.profile.presentation.screen.PhotoType, Int?) -> Unit,
    onRemoveImage: (com.srisu.srisu.features.home.profile.presentation.screen.index, com.srisu.srisu.features.home.profile.presentation.screen.PhotoType, Int?) -> Unit
) {

    val photos=largePhotos.orEmpty().mapIndexed {index,photo -> Triple(photo,PhotoType.LARGE,index)} +
        smallPhotos.orEmpty().mapIndexed {index,photo -> Triple(photo,PhotoType.SMALL,index)}
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text("Gallery",style=MaterialTheme.typography.titleSmall)
        photos.chunked(3).forEach {row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                row.forEach {(photo,type,index) -> GalleryAddCard(Modifier.weight(1f).aspectRatio(1f),photo?.photoUri,
                    {onAddImageClicked(index,type,photo?.id)},{onRemoveImage(index,type,photo?.id)})}
                repeat(3-row.size) {Spacer(Modifier.weight(1f))}
            }
        }
    }
}

@Composable
fun GalleryAddCard(
    modifier: Modifier = Modifier,
    photoUri: Uri? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(modifier = modifier) {

        Card(
            modifier = Modifier
                .clickable { onClick() },
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (photoUri == null) {
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
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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

        if (photoUri != null) {
            IconButton(
                onClick = onRemove,

                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(MaterialTheme.spacing.touchTarget)
                    .clip(shape = CircleShape),
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)

            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove photo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(all = MaterialTheme.spacing.tiny)
                )
            }
        }
    }
}

@Composable
private fun OpenGallery(
    photoPicked: (Uri?) -> Unit
) {

    val galleryManager = rememberGalleryManager(
        onResult = { uris ->
            if (uris.isNullOrEmpty()) {
                photoPicked(null)
            } else {
                photoPicked(uris.firstOrNull()?.toUri())

            }
        },
        mediaType = MediaType.IMAGE_ONLY,
        isMultiple = false
    )

    LaunchedEffect(Unit) {
        galleryManager.launch()
    }
}


@Preview()
@Composable
fun GalleryCompoPreview() {
    val fakeLargePhotos = listOf(
        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
            photoUri = null,
            index = 0
        ),
        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
            photoUri = null,
            index = 1
        )
    )
    val fakeSmallPhotos = listOf(
        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
            photoUri = null,
            index = 0
        ),
        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
            photoUri = null,
            index = 1
        ),
        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
            photoUri = null,
            index = 2
        )
    )
    _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.GalleryCompo(
        largePhotos = fakeLargePhotos,
        smallPhotos = fakeSmallPhotos,
        onAddImageClicked = { _, _, _ -> },
        onRemoveImage = { _, _, _ -> }
    )
}
