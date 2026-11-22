package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
import com.srisu.srisu.utils.*
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

@Composable
fun SetProfilePictureScreen(authViewModel: AuthViewModel) {
    val state by authViewModel.authUiState.collectAsState()
    HandleUiStateDialog(authViewModel, state)
    val gallery = rememberGalleryManager(onResult = { paths ->
        paths?.firstOrNull()?.let { authViewModel.updateProfilePictureUri(it.toUri()) }
    }, mediaType = MediaType.IMAGE_ONLY, isMultiple = false, onError = authViewModel::photoSelectionFailed)
    val camera = rememberCameraManager(authViewModel::updateCapturedProfilePhoto, authViewModel::photoSelectionFailed)
    OnboardingPhotoContent(state, authViewModel::navigateProfileBack, authViewModel::sendSetupProfileRequest,
        gallery::launch, authViewModel::removeProfilePhoto, camera.launch.takeIf { camera.available })
}

@Composable
internal fun OnboardingPhotoContent(state: AuthUIStates, onBack: () -> Unit, onSave: () -> Unit,
    onChoosePhoto: () -> Unit, onRemovePhoto: () -> Unit, onTakePhoto: (() -> Unit)? = null) {
    val photo = state.preparedProfilePhoto
    val busy = state.baseUIState is BaseUIState.Loading
    CommonProfileContainerCompo(buttonTitle = stringResource(if (photo == null) Res.string.auth_skip_photo else Res.string.auth_complete),
        localFocusManager = null, currentStep = state.currentProgressStep, isPrimaryButtonEnabled = !busy,
        onNavBack = onBack, onClickPrimaryButton = onSave) {
        Text(stringResource(Res.string.auth_photo_title), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.auth_photo_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Surface(onClick = onChoosePhoto, enabled = !busy, modifier = Modifier.size(144.dp).testTag("onboarding-photo"),
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Box(contentAlignment = Alignment.Center) {
                if (photo != null) AsyncImage(model = photo.fileBytes, contentDescription = stringResource(Res.string.auth_photo_preview),
                    modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                else Icon(Icons.Outlined.AddAPhoto, stringResource(Res.string.auth_add_photo), Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.Center) {
            TextButton(onChoosePhoto, enabled = !busy) { Text(if (photo == null) "Choose photo" else "Change photo") }
            onTakePhoto?.let { TextButton(it, enabled = !busy) { Text("Take photo") } }
            if (photo != null) TextButton(onRemovePhoto, enabled = !busy) { Text("Remove") }
        }
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
