package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import com.srisu.srisu.features.auth.presentation.components.CustomProfileSetupScreen
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.navigation.graph.HomeNavigation
import org.koin.compose.viewmodel.koinViewModel


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ProfileSetupScreen(
    authViewModel: AuthViewModel = koinViewModel<AuthViewModel>()
) {

    val localFocusManager: FocusManager = LocalFocusManager.current
    val authUIState by authViewModel.authUiState.collectAsState()

    var confirmLeave by remember { mutableStateOf(false) }
    val back: () -> Unit = {
        if (authUIState.currentScreen == CustomProfileSetupScreen.AddFullNameScreen) confirmLeave = true
        else authViewModel.navigateProfileBack()
    }
    androidx.compose.ui.backhandler.BackHandler(onBack = back)
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false },
        title = { Text("Use another account?") },
        text = { Text("Sign out to return to phone verification. Saved setup progress stays with this account.") },
        confirmButton = { TextButton(onClick = authViewModel::signOut) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep setting up") } })
    ProfileScreenContent(
        authViewModel = authViewModel,
        authUIState = authUIState,
        localFocusManager = localFocusManager,
        onBack = back
    )


}


@Composable
private fun ProfileScreenContent(
    authViewModel: AuthViewModel,
    localFocusManager: FocusManager,
    authUIState: AuthUIStates,
    onBack: () -> Unit
) {

    AnimatedContent(
        targetState = authUIState.currentScreen,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
            } else {
                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
            }
        },

        label = "AuthScreenTransition"
    ) { currentScreen ->
        when (currentScreen) {
            CustomProfileSetupScreen.SetProfilePictureScreen -> SetProfilePictureScreen(authViewModel)
            CustomProfileSetupScreen.SelectGenderScreen -> SelectGenderScreen(authViewModel)
            else -> AddNameScreen(authViewModel, localFocusManager, onBack)
        }
    }
}
