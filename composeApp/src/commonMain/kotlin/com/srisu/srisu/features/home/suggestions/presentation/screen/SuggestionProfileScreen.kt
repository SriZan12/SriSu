package com.srisu.srisu.features.home.suggestions.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.ReadMoreText
import com.srisu.srisu.components.RequestSentDialog
import com.srisu.srisu.features.home.suggestions.data.response.UserSuggestionResponse
import com.srisu.srisu.features.home.suggestions.presentation.state.SuggestionUIStates
import com.srisu.srisu.features.home.suggestions.presentation.vm.SuggestionViewModel import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.leo

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SuggestionProfileScreen(
    suggestionViewModel: SuggestionViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val suggestionUIState by suggestionViewModel.suggestionUIStates.collectAsStateWithLifecycle()

    SuggestionProfileFeedbackLayer(
        suggestionViewModel = suggestionViewModel,
        suggestionUIState = suggestionUIState,
    )

    AnimatedContent(
        targetState = true,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        }
    ) {
        SuggestionProfileContent(
            suggestionUIState = suggestionUIState,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
            onSendRequest = suggestionViewModel::sendSingleConnectionRequest,
        )
    }
}

@Composable
private fun SuggestionProfileFeedbackLayer(
    suggestionViewModel: SuggestionViewModel,
    suggestionUIState: SuggestionUIStates,
) {
    val isConnected = isInternetAvailable()
    var showOfflineBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showOfflineBottomSheet = !isConnected
    }

    when (val baseUIState = suggestionUIState.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = suggestionViewModel::idleScreen,
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = suggestionViewModel::idleScreen,
            )
        }

        is BaseUIState.Success<*> -> {
            RequestSentDialog(
                successMessage = baseUIState.message,
                onDismiss = suggestionViewModel::idleScreen,
            )
        }

        is BaseUIState.NoInternetConnection -> {
            showOfflineBottomSheet = baseUIState.isOffline
        }

        is BaseUIState.Idle -> Unit
    }

    if (showOfflineBottomSheet) {
        OfflineBottomSheetCompo(
            show = showOfflineBottomSheet,
            onDismiss = {
                showOfflineBottomSheet = false
                suggestionViewModel.idleScreen()
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SuggestionProfileContent(
    suggestionUIState: SuggestionUIStates,
    onSendRequest: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val userProfileData = suggestionUIState.suggestionProfileData

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .verticalScroll(rememberScrollState())
            .animateContentSize(),
    ) {
        ProfilePictureCompo(
            id = userProfileData?.id,
            profileUrl = userProfileData?.profilePhoto,
            hasSentRequest = userProfileData?.hasActiveConnection,
            isRequestSentSuccessfully = suggestionUIState.isRequested,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
            onSendRequest = onSendRequest,
        )

        UserInfoSection(
            name = userProfileData?.fullName,
            age = userProfileData?.age,
            zodiacSign = userProfileData?.zodiacSign,
            city = userProfileData?.city,
            country = userProfileData?.country,
        )

        InterestSection(
            interests = userProfileData?.userInterests,
        )

        AboutSection(
            bio = userProfileData?.bio,
        )

        Text(
            text = "Gallery",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        GallerySection(
            photos = userProfileData?.userPhotos,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProfilePictureCompo(
    id: Int?,
    profileUrl: String? = null,
    hasSentRequest: Boolean? = false,
    isRequestSentSuccessfully: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onSendRequest: () -> Unit,
) {
    val shouldShowRequestButton = hasSentRequest == false && !isRequestSentSuccessfully
    val profileImageKey = "profile_image-$id"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        with(sharedTransitionScope) {
            AsyncImage(
                model = profileUrl,
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                imageLoader = SingletonImageLoader.get(LocalPlatformContext.current),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .sharedElement(
                        sharedTransitionScope.rememberSharedContentState(key = profileImageKey),
                        animatedVisibilityScope = animatedContentScope,
                        renderInOverlayDuringTransition = false,
                    ),
            )
        }

        if (shouldShowRequestButton) {
            IconButton(
                onClick = onSendRequest,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = 24.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(8.dp),
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Like",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
fun UserInfoSection(
    name: String?,
    age: Int?,
    zodiacSign: String?,
    city: String?,
    country: String?,
) {
    val zodiacSignImage = remember(zodiacSign) {
        ZodiacUtils.getZodiacSignImage(zodiacSign?.trim().orEmpty()) ?: Res.drawable.leo
    }

    val locationText = remember(city, country) {
        "${city ?: "Some City"}, ${country ?: "Some Country"}"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = name.orEmpty(),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )

            if (age != null) {
                Text(
                    text = "($age)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Image(
                painter = painterResource(resource = zodiacSignImage),
                contentDescription = "zodiac sign",
                modifier = Modifier.size(32.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location",
                tint = Color.Black,
            )
            Text(
                text = locationText,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
fun InterestSection(
    interests: List<UserSuggestionResponse.Result.UserInterest?>?,
) {
    if (interests.isNullOrEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 16.dp),
    ) {
        Text(
            text = "Interest",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp),
        )

        LazyRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
        ) {
            items(
                items = interests,
                key = { interest -> interest?.id ?: interest?.name.orEmpty() },
            ) { interest ->
                val label = interest?.name
                if (!label.isNullOrEmpty()) {
                    InterestChip(label = label)
                }
            }
        }
    }
}

@Composable
fun InterestChip(
    label: String,
    backgroundColor: Color = Color.LightGray,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Text(
            text = label,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .basicMarquee(iterations = 10),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AboutSection(
    bio: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "About",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ReadMoreText(
            modifier = Modifier,
            text = bio ?: "No bio",
            style = MaterialTheme.typography.bodyMedium,
            expandableTextStyle = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun GallerySection(
    photos: List<UserSuggestionResponse.Result.UserPhoto?>?,
) {
    if (photos.isNullOrEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = photos,
            key = { photo -> photo?.id ?: photo?.photo.orEmpty() },
        ) { photoItem ->
            AsyncImage(
                model = photoItem?.photo,
                contentDescription = "user_photos",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(200.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}