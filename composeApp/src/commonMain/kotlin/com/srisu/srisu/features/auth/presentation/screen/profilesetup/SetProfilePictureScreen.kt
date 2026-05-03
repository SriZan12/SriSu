package com.srisu.srisu.features.auth.presentation.screen.profilesetup

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.toUri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.SuccessBottomSheet
import com.srisu.srisu.core.permissionmanager.PermissionCallback
import com.srisu.srisu.core.permissionmanager.PermissionState
import com.srisu.srisu.core.permissionmanager.PermissionType
import com.srisu.srisu.core.permissionmanager.createPermissionsManager
import com.srisu.srisu.features.auth.presentation.components.CommonProfileContainerCompo
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.navigation.graph.HomeNavigation
import com.srisu.srisu.utils.MediaType
import com.srisu.srisu.utils.isInternetAvailable
import com.srisu.srisu.utils.rememberGalleryManager


@Composable
fun SetProfilePictureScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    onSetupComplete: () -> Unit
) {
    val authUiState by authViewModel.authUiState.collectAsState()

    HandleUiStateDialog(
        navController = navController,
        authViewModel = authViewModel,
        authUIStates = authUiState
    )

    CommonProfileContainerCompo(
        modifier = Modifier,
        buttonTitle = "Complete",
        localFocusManager = null,
        currentStep = authUiState.currentProgressStep,
        isPrimaryButtonEnabled = true,
        onNavBack = {
            authViewModel.navigateBack()
        },
        onClickPrimaryButton = {
            authViewModel.sendSetupProfileRequest()
        },
    ) {


        Text(
            text = "Put a face to the\nname",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )


        Text(
            text = "A photo helps people feel closer before\nthey even say hello.",
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
    navController: NavController,
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

        is BaseUIState.Success<*> -> {

            SuccessBottomSheet(
                show = true,
                onDismiss = {
                    authViewModel.idleScreen()
                },
                onSecondButton = {
                    authViewModel.idleScreen()

                },
                onFirstButton = {
                    authViewModel.idleScreen()
                    navController.navigate(HomeNavigation.Home)
                }
            )
        }

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
    var permissionState by remember { mutableStateOf(PermissionState.NOT_ASKED_YET) }

    val permissionManager = createPermissionsManager(
        object : PermissionCallback {
            override fun onPermissionStatus(
                permissionType: PermissionType,
                status: PermissionState
            ) {
                permissionState = status
            }
        }
    )

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
                        contentDescription = "Selected profile picture",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = "Add profile picture",
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
                    contentDescription = "Add photo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    if (shouldOpenGallery) {
        if (!permissionManager.isPermissionGranted(PermissionType.STORAGE)) {
            permissionManager.askPermission(PermissionType.STORAGE)
        } else {
            galleryManager.launch()
        }

        shouldOpenGallery = false
    }

    LaunchedEffect(permissionState) {
        if (permissionState == PermissionState.GRANTED) {
            galleryManager.launch()
        }
    }
}


