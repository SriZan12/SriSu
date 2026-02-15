package com.srisu.srisu.features.suggestions.screens

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
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.features.suggestions.state.SuggestionUIStates
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.image_placeholder
import srisu.composeapp.generated.resources.leo
import kotlin.random.Random

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SuggestionProfileScreen(
    suggestionViewModel: SuggestionViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {

    val suggestionUIState by suggestionViewModel.suggestionUIStates.collectAsStateWithLifecycle()
    val shouldShowRequestButton by remember {
        mutableStateOf(false)
    }

    HandleUiStates(
        suggestionViewModel = suggestionViewModel,
        suggestionUIState = suggestionUIState
    )

    AnimatedContent(
        targetState = true,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        }
    ) {
        ProfilePictureContent(
            suggestionUIState = suggestionUIState,
            shouldShowRequestButton = shouldShowRequestButton,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
            onSendRequest = {
                suggestionViewModel.sendSingleConnectionRequest()
            }
        )
    }


}

@Composable
private fun HandleUiStates(
    suggestionViewModel: SuggestionViewModel,
    suggestionUIState: SuggestionUIStates
) {

    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = suggestionUIState.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    suggestionViewModel.idleScreen()
                },
            )
        }


        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = {
                    suggestionViewModel.idleScreen()
                }
            )
        }

        is BaseUIState.Success<*> -> {
            RequestSentDialog(
                successMessage = baseUIState.message,
                onDismiss = {
                    suggestionViewModel.idleScreen()
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
                suggestionViewModel.idleScreen()
            }
        )
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ProfilePictureContent(
    suggestionUIState: SuggestionUIStates,
    onSendRequest: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    shouldShowRequestButton: Boolean
) {

    val userProfileData = suggestionUIState.suggestionProfileData

    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .verticalScroll(rememberScrollState())
    ) {
        ProfilePictureCompo(
            id = userProfileData?.id,
            profileUrl = userProfileData?.profilePhoto,
            hasSentRequest = userProfileData?.crushed,
            shouldShowRequestButton = shouldShowRequestButton,
            isRequestSentSuccessfully = suggestionUIState.isRequested,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
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
            country = userProfileData?.country,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
fun ProfilePictureCompo(
    id: Int?,
    profileUrl: String? = null,
    hasSentRequest: Boolean? = false,
    shouldShowRequestButton: Boolean,
    isRequestSentSuccessfully: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onSendRequest: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {

        with(sharedTransitionScope) {
            AsyncImage(
                model = profileUrl,
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                imageLoader = SingletonImageLoader.get(LocalPlatformContext.current),
                placeholder = painterResource(Res.drawable.image_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .sharedElement(
                        sharedTransitionScope.rememberSharedContentState(key = "profile_image-${id}"),
                        animatedVisibilityScope = animatedContentScope,
                        renderInOverlayDuringTransition = false
                    )
            )

        }


        if (hasSentRequest == false && !isRequestSentSuccessfully) {

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
            modifier = Modifier,
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
                items(interests, key = { it?.id ?: Random.nextInt() }) { interest ->
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
fun InterestChip(label: String, backgroundColor: Color = Color.LightGray) {
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

    photos?.let {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = it, key = { it?.id ?: Random.nextInt() }) { photoItem ->
                AsyncImage(
                    modifier = Modifier
                        .size(200.dp)
                        .aspectRatio(1f)
                        .clip(shape = RoundedCornerShape(8.dp)),
                    model = photoItem?.photo,
                    contentDescription = "user_photos",
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
