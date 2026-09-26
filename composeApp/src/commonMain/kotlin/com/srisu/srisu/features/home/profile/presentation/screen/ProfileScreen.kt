package com.srisu.srisu.features.home.profile.presentation.screen

import com.srisu.srisu.theme.spacing
import com.srisu.srisu.theme.pill
import com.srisu.srisu.theme.reminder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.ReadMoreText
import com.srisu.srisu.components.RequestSentDialog
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.image_placeholder
import srisu.composeapp.generated.resources.leo

@Composable
fun ProfileScreen(
    profileViewModel: com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel = koinViewModel<com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel>()
) {

    val profileUIState by profileViewModel.profileUIState.collectAsStateWithLifecycle()


    _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.HandleUiStates(
        profileViewModel = profileViewModel,
        profileUIStates = profileUIState
    )

    _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.ProfilePictureContent(
        profileUIState = profileUIState,
    )

}
@Composable
private fun HandleUiStates(
    profileViewModel: com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel,
    profileUIStates: com.srisu.srisu.features.home.profile.presentation.state.ProfileUIState
) {

    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = profileUIStates.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    profileViewModel.idleScreen()
                },
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = {
                    profileViewModel.idleScreen()
                }
            )
        }

        is BaseUIState.Success<*> -> {
            RequestSentDialog(
                successMessage = baseUIState.message,
                onDismiss = {
                    profileViewModel.idleScreen()
                },
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
                profileViewModel.idleScreen()
            }
        )
    }
}

@Composable
private fun ProfilePictureContent(
    profileUIState: com.srisu.srisu.features.home.profile.presentation.state.ProfileUIState,
) {

    val userProfileData = profileUIState.userProfileData

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.ProfilePictureCompo(
            profileUrl = userProfileData?.profilePhoto
        )

        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.UserInfo(
            name = userProfileData?.fullName,
            age = DateTimeUtils.calculateAge(userProfileData?.dob),
            zodiacSign = userProfileData?.zodiacSign,
            city = userProfileData?.city,
            country = userProfileData?.country
        )

        //Interest
        val interests = userProfileData?.userInterests
        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.InterestCompo(
            interests = interests
        )

        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.AboutCompo(
            bio = userProfileData?.bio
        )

        // Gallery
        Text(
            "Gallery",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
        )

        _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.GallerySection(
            photos = userProfileData?.userPhotos
        )

    }
}

@Preview
@Composable
fun ProfilePictureCompo(
    profileUrl: String? = null,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.extraLarge)) {
        if (profileUrl == null) {
            Image(
                painter = painterResource(Res.drawable.image_placeholder),
                contentDescription = "profile placeholder",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            )
        } else {

            AsyncImage(
                model = profileUrl,
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop, // fills and crops extra
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            )

        }
    }
}

@Composable
fun UserInfo(
    name: String?,
    age: Int?,
    zodiacSign: String?,
    city: String?,
    country: String?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)
    ) {
        Text(
            text = name ?: "",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall
        )

        Text("(${age})", style = MaterialTheme.typography.titleMedium)

        val zodiacSignImage = ZodiacUtils.getZodiacSignImage(zodiacSign?.trim() ?: "")
        Image(
            painter = painterResource(resource = zodiacSignImage ?: Res.drawable.leo),
            contentDescription = "zodiac sign",
            modifier = Modifier.size(32.dp)
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = MaterialTheme.spacing.medium)
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = MaterialTheme.colorScheme.onSurface)
        Text(
            "${city ?: "Some City"}, ${country ?: "Some Country"} ",
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
fun InterestCompo(interests: List<User.UserInterest?>?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.medium)
    ) {
        if (!interests.isNullOrEmpty()) {

            Text(
                "Interest",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = MaterialTheme.spacing.medium)
            )

            LazyRow(
                modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                contentPadding = PaddingValues(start = MaterialTheme.spacing.compact, end = MaterialTheme.spacing.compact)
            ) {
                items(interests) { interest ->
                    interest?.let {
                        if (!interest.name.isNullOrEmpty()) {
                            _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.screen.InterestChip(
                                label = interest.name
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InterestChip(label: String, backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = MaterialTheme.shapes.pill,
        modifier = Modifier
            .padding(end = MaterialTheme.spacing.small),
    ) {
        Text(
            text = label,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                .basicMarquee(iterations = 10),
            style = MaterialTheme.typography.labelMedium
        )
    }
}



@Composable
private fun AboutCompo(
    bio: String?
) {
    // About Section
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
    ) {
        Text(
            "About",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
        )

        Spacer(modifier = Modifier.height(8.dp))

        ReadMoreText(
            modifier = Modifier,
            text = bio ?: "No bio",
            style = MaterialTheme.typography.bodyMedium,
            expandableTextStyle = MaterialTheme.typography.titleMedium
        )

    }
}

@Composable
fun GallerySection(
    photos: List<User.UserPhoto?>?
) {

    photos?.let {
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium),
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            items(items = photos) { photoItem ->
                AsyncImage(
                    modifier = Modifier
                        .size(200.dp)
                        .aspectRatio(1f)
                        .clip(shape = MaterialTheme.shapes.small),
                    model = photoItem,
                    contentDescription = "user_photos",
                    contentScale = ContentScale.Crop
                )
            }
        }

        /* Text(
             "See all",
             color = MaterialTheme.colorScheme.reminder,
             textAlign = TextAlign.Center,
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(MaterialTheme.spacing.small)
         )*/
    }
}

