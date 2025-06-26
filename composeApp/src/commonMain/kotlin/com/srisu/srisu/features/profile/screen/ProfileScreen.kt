package com.srisu.srisu.features.profile.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.CustomButtonCompo
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.ReadMoreText
import com.srisu.srisu.components.RequestSentDialog
import com.srisu.srisu.components.SuccessDialog
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.profile.state.ProfileUIState
import com.srisu.srisu.features.profile.vm.ProfileViewModel
import com.srisu.srisu.theme.success
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.check_sticker
import srisu.composeapp.generated.resources.image_placeholder
import srisu.composeapp.generated.resources.leo

@Composable
fun ProfileScreen(
    userProfileData: String?,
    profileViewModel: ProfileViewModel = koinViewModel<ProfileViewModel>()
) {

    val profileUIState by profileViewModel.profileUIState.collectAsStateWithLifecycle()
    val shouldShowRequestButton by remember {
        mutableStateOf(false)
    }

    Init(profileViewModel = profileViewModel, userProfileData = userProfileData)

    HandleUiStates(
        profileViewModel = profileViewModel,
        profileUIStates = profileUIState
    )

    ProfilePictureContent(
        profileUIState = profileUIState,
        shouldShowRequestButton = shouldShowRequestButton,
        onSendRequest = {
            AppLogger.log("ON SEND REQUEST")
            profileViewModel.sendSingleConnectionRequest()
        }
    )

}

@Composable
private fun Init(
    profileViewModel: ProfileViewModel,
    userProfileData: String?
) {
    LaunchedEffect(
        key1 = Unit
    ) {
        profileViewModel.updateUserProfileData(userProfileData = userProfileData)
    }
}

@Composable
private fun HandleUiStates(
    profileViewModel: ProfileViewModel,
    profileUIStates: ProfileUIState
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
    profileUIState: ProfileUIState,
    onSendRequest: () -> Unit,
    shouldShowRequestButton: Boolean
) {

    val userProfileData = profileUIState.userProfileData

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .verticalScroll(rememberScrollState())
    ) {
        ProfilePictureCompo(
            profileUrl = userProfileData?.profilePhoto,
            hasSentRequest = userProfileData?.crushed,
            shouldShowRequestButton = shouldShowRequestButton,
            isRequestSentSuccessfully = profileUIState.isRequestSentSuccessfully,
            onSendRequest = {
                onSendRequest()
            }
        )

        // Name and Age
        val age = DateTimeUtils.calculateAge(userProfileData?.dob)

        UserInfo(
            name = userProfileData?.fullName,
            age = age,
            zodiacSign = userProfileData?.zodiacSign,
            city = userProfileData?.city,
            country = userProfileData?.country
        )

        //Interest
        val interests = userProfileData?.userInterests
        InterestCompo(interests = interests)

        AboutCompo(
            bio = userProfileData?.bio
        )

        // Gallery
        Text(
            "Gallery",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        GallerySection(
            photos = userProfileData?.userPhotos
        )

    }
}

@Preview
@Composable
fun ProfilePictureCompo(
    profileUrl: String? = null,
    hasSentRequest: Boolean? = false,
    shouldShowRequestButton: Boolean,
    isRequestSentSuccessfully: Boolean?,
    onSendRequest: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        if (profileUrl == null) {
            Image(
                painter = painterResource(Res.drawable.image_placeholder),
                contentDescription = "profile placeholder",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        } else {
            AsyncImage(
                model = profileUrl,
//                model = "https://images.unsplash.com/photo-1576828831022-ca41d3905fb7?q=80&w=1923&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }

        if (hasSentRequest == false && !isRequestSentSuccessfully!!) {

            IconButton(
                onClick = {
                    onSendRequest()
                },
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = 24.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    modifier = Modifier.size(48.dp).padding(all = 8.dp),
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Like",
                    tint = Color.White
                )
            }
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
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = Color.Black)
        Text(
            "${city ?: "Some City"}, ${country ?: "Some Country"} ",
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
fun InterestCompo(interests: List<UserSuggestionResponse.Result.UserInterest?>?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)
    ) {
        if (!interests.isNullOrEmpty()) {

            Text(
                "Interest",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp)
            )

            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp)
            ) {
                items(interests) { interest ->
                    interest?.let {
                        if (!interest.name.isNullOrEmpty()) {
                            InterestChip(label = interest.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InterestChip(label: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.LightGray),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .padding(end = 8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Preview
@Composable
private fun AboutCompo(
    bio: String?
) {
    // About Section
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

@Preview
@Composable
fun GallerySection(
    photos: List<UserSuggestionResponse.Result.UserPhoto?>?
) {

    val listPhotos = listOf(
        "https://media.istockphoto.com/id/1197578214/photo/beautiful-young-woman.jpg?s=1024x1024&w=is&k=20&c=au0eZV8dc7lE2VC8ghRF8igL19OxPBXbKvKzcmyjeQE=",
        "https://media.istockphoto.com/id/184888055/photo/beautiful-young-woman-smiling.jpg?s=1024x1024&w=is&k=20&c=Veh-hAfi6G3HSkdRPHyoFdjFWdGfYB9S6kd4LkihkkM=",
        "https://media.istockphoto.com/id/185123021/photo/portrait-of-a-beautiful-brunette-woman.jpg?s=1024x1024&w=is&k=20&c=tj3AIS7iGpwkr1QgY0w90prerVhUSFA-QrAOMby9x1E=",
        "https://media.istockphoto.com/id/1197578214/photo/beautiful-young-woman.jpg?s=1024x1024&w=is&k=20&c=au0eZV8dc7lE2VC8ghRF8igL19OxPBXbKvKzcmyjeQE=",
        "https://media.istockphoto.com/id/184888055/photo/beautiful-young-woman-smiling.jpg?s=1024x1024&w=is&k=20&c=Veh-hAfi6G3HSkdRPHyoFdjFWdGfYB9S6kd4LkihkkM=",
        "https://media.istockphoto.com/id/185123021/photo/portrait-of-a-beautiful-brunette-woman.jpg?s=1024x1024&w=is&k=20&c=tj3AIS7iGpwkr1QgY0w90prerVhUSFA-QrAOMby9x1E="
    )

    photos?.let {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = listPhotos) { photoItem ->
                AsyncImage(
                    modifier = Modifier
                        .size(200.dp)
                        .aspectRatio(1f)
                        .clip(shape = RoundedCornerShape(8.dp)),
                    model = photoItem,
                    contentDescription = "user_photos",
                    contentScale = ContentScale.Crop
                )
            }
        }

        /* Text(
             "See all",
             color = Color(0xFFFFA500),
             textAlign = TextAlign.Center,
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(8.dp)
         )*/
    }
}

