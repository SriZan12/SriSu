package com.srisu.srisu.features.auth.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.srisu.srisu.components.PhoneNumberCompo
import com.srisu.srisu.components.RoundedButtonCompo
import com.srisu.srisu.features.auth.presentation.components.CustomAuthScreen
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.Constants.Auth.TOTAL_PROGRESS
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun BaseAuthScreen(
    navController: NavController,
    authViewModel: AuthViewModel = koinViewModel<AuthViewModel>()
) {

    val localFocusManager: FocusManager = LocalFocusManager.current
    val authUIStates by authViewModel.authUiState.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                localFocusManager.clearFocus()
            },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            RoundedButtonCompo(
                modifier = Modifier,
                title = "Looks good, let's go",
                enabled = true,
                onClick = {
                    localFocusManager.clearFocus()

                    if (authViewModel.isFullNameValid() && authViewModel.isUsernameValid()) {
                        authViewModel.navigateNextScreen()
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .imePadding() // only content moves/scrolls above keyboard
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            SriSuProgressIndicator(
                totalSteps = 6,
                currentStep = 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            AuthScreenContent(
                navController = navController,
                authViewModel = authViewModel,
                authUIStates = authUIStates
            )
        }

        ShowZodiacSignScreen(
            authViewModel = authViewModel,
            authUIStates = authUIStates
        )
    }
}


@Composable
private fun AuthScreenContent(
    navController: NavController,
    authViewModel: AuthViewModel,
    authUIStates: AuthUIStates
) {
    AnimatedContent(
        targetState = authUIStates.currentScreen,
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

            is CustomAuthScreen.AddFullNameScreen -> {
                AddFullNameCompo(
                    authViewModel = authViewModel
                )
            }

            is CustomAuthScreen.AddDOBScreen -> {
                AddDOBCompo(
                    authViewModel = authViewModel
                )
            }

            is CustomAuthScreen.ZodiacScreen -> {
                // This will be handled automatically.
            }

            is CustomAuthScreen.SelectGenderScreen -> {
                SelectGenderCompo(
                    authViewModel = authViewModel
                )
            }

            CustomAuthScreen.SetProfilePictureScreen -> {
                SetProfilePictureScreen(
                    navController = navController,
                    authViewModel = authViewModel
                )
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
    authUIStates: AuthUIStates
) {
    AnimatedContent(
        targetState = authUIStates.currentScreen,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
            } else {
                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
            }
        },

        label = "AuthScreenTransition"
    ) { currentScreen ->
        if (currentScreen is CustomAuthScreen.ZodiacScreen) {
            ZodiacScreen(authViewModel = authViewModel)
        }
    }
}