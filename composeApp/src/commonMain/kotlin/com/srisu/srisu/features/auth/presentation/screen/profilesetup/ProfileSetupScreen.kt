package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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

    androidx.compose.ui.backhandler.BackHandler {
        authViewModel.navigateProfileBack()
    }
    ProfileScreenContent(
        authViewModel = authViewModel,
        authUIState = authUIState,
        localFocusManager = localFocusManager
    )


}


@Composable
private fun ProfileScreenContent(
    authViewModel: AuthViewModel,
    localFocusManager: FocusManager,
    authUIState: AuthUIStates
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
            else -> AddNameScreen(authViewModel, localFocusManager)
        }
    }
}
