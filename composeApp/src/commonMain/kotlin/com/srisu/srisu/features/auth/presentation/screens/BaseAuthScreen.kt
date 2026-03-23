package com.srisu.srisu.features.auth.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.srisu.srisu.features.auth.presentation.Components.CustomAuthScreen
import com.srisu.srisu.features.auth.presentation.Components.ProgressIndicator
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.Constants.Auth.TOTAL_PROGRESS
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun BaseAuthScreen(
    navController: NavController,
    authViewModel: AuthViewModel = koinViewModel<AuthViewModel>()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { innerPadding ->

        val authUIState by authViewModel.authUiState.collectAsState()

        Column(modifier = Modifier.fillMaxWidth().padding(paddingValues = innerPadding)) {
            AuthToolBar(
                toolBartTitle = authUIState.currentScreen.title,
                currentProgress = authUIState.currentProgressStep.toString()
            ) {
                authViewModel.navigateBack()
            }

            Spacer(modifier = Modifier.height(10.dp))


            ProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                currentProgress = authUIState.progress
            )

            Spacer(modifier = Modifier.height(56.dp))

            AuthScreenContent(
                navController = navController,
                authViewModel = authViewModel,
                authUIStates = authUIState
            )

        }
        ShowZodiacSignScreen(
            authViewModel = authViewModel,
            authUIStates = authUIState
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
            is CustomAuthScreen.AddPhoneNumberScreen -> {
                AddPhoneNumberCompo(
                    authViewModel = authViewModel
                )
            }

            is CustomAuthScreen.PhoneNumberVerificationScreen -> {
                PhoneNumberVerificationScreen(
                    navController = navController,
                    authViewModel = authViewModel,
                )
            }

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