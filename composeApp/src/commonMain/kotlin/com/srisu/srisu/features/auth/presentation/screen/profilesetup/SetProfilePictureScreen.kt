package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.toUri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.features.auth.presentation.components.CommonProfileContainerCompo
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.MediaType
import com.srisu.srisu.utils.isInternetAvailable
import com.srisu.srisu.utils.rememberGalleryManager


@Composable
fun SetProfilePictureScreen(
    authViewModel: AuthViewModel
) {
    val authUiState by authViewModel.authUiState.collectAsState()

    HandleUiStateDialog(
        authViewModel = authViewModel,
        authUIStates = authUiState
    )

    CommonProfileContainerCompo(
        modifier = Modifier,
        buttonTitle = if (authUiState.profilePictureUri == null) stringResource(Res.string.auth_skip_photo) else stringResource(Res.string.auth_complete),
        localFocusManager = null,
        currentStep = authUiState.currentProgressStep,
        isPrimaryButtonEnabled = authUiState.baseUIState !is BaseUIState.Loading,
        onNavBack = {
            authViewModel.showNameStep()
        },
        onClickPrimaryButton = {
            authViewModel.sendSetupProfileRequest()
        },
    ) {


        Text(
            text = stringResource(Res.string.auth_photo_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,

            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )


        Text(
            text = stringResource(Res.string.auth_photo_subtitle),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        ProfilePicturePickerSection(
            authViewModel = authViewModel,
            authUIStates = authUiState
        )

    }
}

@Composable
private fun HandleUiStateDialog(
    authViewModel: AuthViewModel,
    authUIStates: AuthUIStates
) {

    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = authUIStates.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    authViewModel.idleScreen()
                },
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim()
        }

        is BaseUIState.Success<*> -> Unit // Root coordinator observes confirmed progress.

        is BaseUIState.NoInternetConnection -> {
            showBottomSheet = baseUIState.isOffline
        }

        is BaseUIState.Idle -> Unit
    }

    if (showBottomSheet) {
        OfflineBottomSheetCompo(
            show = showBottomSheet,
            onDismiss = {
                showBottomSheet = false
                authViewModel.idleScreen()
            }
        )
    }
}


@Composable
private fun ProfilePicturePickerSection(
    authViewModel: AuthViewModel,
    authUIStates: AuthUIStates
) {
    val profilePictureUri = authUIStates.profilePictureUri
    var shouldOpenGallery by remember { mutableStateOf(false) }
    val galleryManager = rememberGalleryManager(
        onResult = { uris ->
            if (!uris.isNullOrEmpty()) {
                authViewModel.updateProfilePictureUri(
                    uri = uris.firstOrNull()?.toUri()
                )
            } else {
                authViewModel.idleScreen()
            }
        },
        mediaType = MediaType.IMAGE_ONLY,
        isMultiple = false
    )

    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary
            ),
            onClick = {
                shouldOpenGallery = true
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (profilePictureUri != null) {
                    AsyncImage(
                        model = profilePictureUri,
                        contentDescription = stringResource(Res.string.auth_photo_preview),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = stringResource(Res.string.auth_add_photo),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        Surface(
            onClick = {
                shouldOpenGallery = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            border = BorderStroke(
                width = 4.dp,
                color = MaterialTheme.colorScheme.background
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(Res.string.auth_add_photo_action),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    LaunchedEffect(shouldOpenGallery) {
        if (shouldOpenGallery) {
            shouldOpenGallery = false
            galleryManager.launch()
        }
    }
}
