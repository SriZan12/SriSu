package com.srisu.srisu.features.profile.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.profile.state.ProfileUIState
import com.srisu.srisu.features.profile.vm.ProfileViewModel
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.image_placeholder
import srisu.composeapp.generated.resources.leo
import kotlin.random.Random

@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel = koinViewModel<ProfileViewModel>()
) {

    val profileUIState by profileViewModel.profileUIState.collectAsStateWithLifecycle()


    HandleUiStates(
        profileViewModel = profileViewModel,
        profileUIStates = profileUIState
    )

    ProfilePictureContent(
        profileUIState = profileUIState,
    )

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
) {

    val userProfileData = profileUIState.userProfileData

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        ProfilePictureCompo(
            profileUrl = userProfileData?.profilePhoto
        )

        UserInfo(
            name = userProfileData?.fullName,
            age = DateTimeUtils.calculateAge(userProfileData?.dob),
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
) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
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
fun InterestCompo(interests: List<User.UserInterest?>?) {
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
private fun InterestChip(label: String, backgroundColor: Color = Color.LightGray) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .padding(end = 8.dp),
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

fun getRandomPastelColor(): Color {
    val base = 200 // to ensure soft colors (pastel-ish)
    val red = base + Random.nextInt(0, 56)
    val green = base + Random.nextInt(0, 56)
    val blue = base + Random.nextInt(0, 56)
    return Color(red, green, blue)
}

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

@Composable
fun GallerySection(
    photos: List<User.UserPhoto?>?
) {

    photos?.let {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = photos) { photoItem ->
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

