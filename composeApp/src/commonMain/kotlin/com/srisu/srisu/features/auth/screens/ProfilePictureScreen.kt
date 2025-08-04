package com.srisu.srisu.features.auth.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.toUri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.SuccessBottomSheet
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.common.CommonAuthContainerCompo
import com.srisu.srisu.features.auth.common.InfoText
import com.srisu.srisu.features.auth.common.TitleText
import com.srisu.srisu.features.auth.state.AuthUIStates
import com.srisu.srisu.features.auth.vm.AuthViewModel
import com.srisu.srisu.navigation.HomeNavigation
import com.srisu.srisu.permissionmanager.PermissionCallback
import com.srisu.srisu.permissionmanager.PermissionState
import com.srisu.srisu.permissionmanager.PermissionType
import com.srisu.srisu.permissionmanager.createPermissionsManager
import com.srisu.srisu.utils.MediaType
import com.srisu.srisu.utils.isInternetAvailable
import com.srisu.srisu.utils.rememberGalleryManager
import com.srisu.srisu.theme.backgroundGray


@Composable
fun ProfilePictureScreen(navController: NavController, authViewModel: AuthViewModel) {
    CommonAuthContainerCompo(buttonTitle = "Complete", onClick = {
        authViewModel.sendSetupProfileRequest()
    }) {

        val authUiState by authViewModel.authUiState.collectAsState()

        HandleUiStateDialog(
            navController = navController,
            authViewModel = authViewModel,
            authUIStates = authUiState
        )

        TitleText(
            modifier = Modifier.fillMaxWidth(),
            title = "What's your best look?"
        )

        InfoText(
            modifier = Modifier.fillMaxWidth(),
            info = "Upload a photo that shows the real you and helps others get to know you better."
        )

        Spacer(modifier = Modifier.height(48.dp))

        ProfilePictureCompo(
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
private fun ProfilePictureCompo(
    authViewModel: AuthViewModel,
    authUIStates: AuthUIStates
) {
    val profilePictureUri = authUIStates.profilePictureUri
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionState by remember { mutableStateOf(PermissionState.NOT_ASKED_YET) }


    val permissionManager = createPermissionsManager(object : PermissionCallback {
        override fun onPermissionStatus(permissionType: PermissionType, status: PermissionState) {
            AppLogger.log("INSIDE CALLBACK = $status")
            when (status) {
                PermissionState.GRANTED -> {
                    permissionState = PermissionState.GRANTED
                }

                PermissionState.SHOW_RATIONALE -> permissionState = PermissionState.SHOW_RATIONALE

                PermissionState.DENIED -> {
                    permissionState = PermissionState.DENIED
                }

                PermissionState.NOT_ASKED_YET -> {
                }

                PermissionState.REQUEST_LAUNCHED -> {
                    permissionState = PermissionState.REQUEST_LAUNCHED
                }
            }
        }
    })

    val galleryManager = rememberGalleryManager(
        onResult = { uris ->
            if (!uris.isNullOrEmpty()) {
                authViewModel.updateProfilePictureUri(uri = uris.firstOrNull()?.toUri())
            } else {
                authViewModel.idleScreen()
            }
        },
        mediaType = MediaType.IMAGE_ONLY
    )


    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(211.dp)
                .clip(CircleShape)
                .background(backgroundGray)
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


/*@Composable
private fun ProfilePictureCompo(
    authViewModel: AuthViewModel,
    authUIStates: AuthViewModel.AuthUIStates
) {
    val profilePictureUri = authUIStates.profilePictureUri
//    var showPermissionDialog by remember { mutableStateOf(true) }
//    var permissionState by remember { mutableStateOf(PermissionState.NOT_ASKED_YET) }
//    var hasRequestedPermission by remember { mutableStateOf(true) }
//    var permissionRequestCompleted by remember { mutableStateOf(false) }

    val galleryManager = rememberGalleryManager(
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                authViewModel.updateProfilePictureUri(uri = uris.firstOrNull()?.toUri())
            }
        },
        mediaType = MediaType.IMAGE_ONLY
    )

    *//*
        val permissionManager = createPermissionsManager(object : PermissionCallback {
            override fun onPermissionStatus(permissionType: PermissionType, status: PermissionState) {
                AppLogger.log("INSIDE CALLBACK = $status")
                when (status) {
                    PermissionState.GRANTED -> {
                        permissionState = PermissionState.GRANTED
    //                    galleryManager.launch()
                    }

                    PermissionState.SHOW_RATIONALE -> permissionState = PermissionState.SHOW_RATIONALE

                    PermissionState.DENIED -> {
                        permissionState = PermissionState.DENIED
                    }

                    PermissionState.NOT_ASKED_YET -> {
                    }

                    PermissionState.REQUEST_LAUNCHED -> {
                        permissionState = PermissionState.REQUEST_LAUNCHED
                    }
                }
            }
        })
    *//*
    *//*
        LaunchedEffect(permissionState) {
            AppLogger.log("INSIDE PERMISSION STATE LAUNCHED EFFECT")
            if (hasRequestedPermission) {
                permissionRequestCompleted = true
            }
        }*//*

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(211.dp)
                .clip(CircleShape)
                .background(backgroundGray)
                .clickable {
                    *//*   showPermissionDialog = true
                       hasRequestedPermission = true*//*

                    galleryManager.launch()

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
                    painter = painterResource(Res.drawable.image_placeholder),
                    contentDescription = "Image_picker",
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        *//*
                if (showPermissionDialog) {
                    if (!permissionManager.isPermissionGranted(permission = PermissionType.STORAGE)) {
                        permissionManager.askPermission(
                            permission = PermissionType.STORAGE,
                            permissionRequestCompleted = permissionRequestCompleted
                        )

                    } else {
                           galleryManager.launch()
                    }
                    showPermissionDialog = false
                }
        *//*

    }
}*/


