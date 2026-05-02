package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.srisu.srisu.features.auth.presentation.components.CustomProfileSetupScreen
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.navigation.graph.HomeNavigation
import com.srisu.srisu.utils.Constants.Auth.TOTAL_PROGRESS
import org.koin.compose.viewmodel.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    navController: NavController,
    authViewModel: AuthViewModel = koinViewModel<AuthViewModel>()
) {

    val localFocusManager: FocusManager = LocalFocusManager.current
    val authUIState by authViewModel.authUiState.collectAsState()

    ProfileScreenContent(
        navController = navController,
        authViewModel = authViewModel,
        authUIState = authUIState,
        localFocusManager = localFocusManager
    )

    ShowZodiacSignScreen(
        authViewModel = authViewModel,
        authUIState = authUIState
    )

}


@Composable
private fun ProfileScreenContent(
    navController: NavController,
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

            is CustomProfileSetupScreen.AddFullNameScreen -> {
                AddNameScreen(
                    authViewModel = authViewModel,
                    localFocusManager = localFocusManager
                )
            }

            is CustomProfileSetupScreen.AddDOBScreen -> {
                AddDOBScreen(
                    authViewModel = authViewModel
                )
            }

            is CustomProfileSetupScreen.ZodiacScreen -> {
                // This will be handled automatically.
            }

            is CustomProfileSetupScreen.SelectGenderScreen -> {
                SelectGenderScreen(
                    authViewModel = authViewModel
                )
            }

            CustomProfileSetupScreen.SetProfilePictureScreen -> {
                SetProfilePictureScreen(
                    navController = navController,
                    authViewModel = authViewModel
                ) {
                    navController.navigate(HomeNavigation.Home)
                }
            }

            else -> {}
        }
    }
}

@Composable
fun AuthToolBar(
    toolBartTitle: String,
    currentProgress: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(all = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        IconButton(
            modifier = Modifier.size(24.dp),
            onClick = {
                onClick()
            },
            colors = IconButtonDefaults.iconButtonColors().copy(contentColor = Color.Black),
            content = {
                Icon(
                    modifier = Modifier,
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "navigate_back",
                )
            },
        )

        Text(
            text = toolBartTitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black
        )

        Text(
            text = "$currentProgress of $TOTAL_PROGRESS",
            style = MaterialTheme.typography.titleSmall,
            color = Color.Black
        )
    }
}

@Composable
private fun ShowZodiacSignScreen(
    authViewModel: AuthViewModel,
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
        if (currentScreen is CustomProfileSetupScreen.ZodiacScreen) {
            authUIState.zodiacSign?.let {
                ZodiacRevealScreen(
                    zodiacSign = authUIState.zodiacSign,
                    onContinueClick = {
                        authViewModel.navigateNextScreen(isIncrease = true)
                    },
                    modifier = Modifier
                )
            }
        }
    }
}