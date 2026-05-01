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
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.presentation.components.CustomAuthScreen
import com.srisu.srisu.features.auth.presentation.screen.ZodiacScreen
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.Constants.Auth.TOTAL_PROGRESS
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.aries
import srisu.composeapp.generated.resources.cancer


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    navController: NavController,
    authViewModel: AuthViewModel = koinViewModel<AuthViewModel>()
) {

    val localFocusManager: FocusManager = LocalFocusManager.current
    val authUIStates by authViewModel.authUiState.collectAsState()

    ZodiacRevealScreen(
        zodiacSign = ZodiacSign(
            key = "CANCER",
            name = "Cancer",
            symbol = "♋",
            logo = Res.drawable.cancer,
            title = "Your zodiac sign",
            dateRange = "June 21 – July 22",
            description = "You're caring, intuitive, and emotionally deep. You love with sincerity and protect what matters most.",
            traits = listOf("Caring", "Intuitive", "Protective"),
            startMonth = 6,
            startDay = 21,
            endMonth = 7,
            endDay = 22
        ),
        onContinueClick = {}
    )



//    Scaffold(
//        modifier = Modifier
//            .fillMaxSize()
//            .clickable(
//                interactionSource = remember { MutableInteractionSource() },
//                indication = null
//            ) {
//                localFocusManager.clearFocus()
//            },
//        topBar = {
//            TopAppBar(
//                title = {},
//                modifier = Modifier.fillMaxWidth(),
//                colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
//                navigationIcon = {
//                    IconButton(onClick = {
//
//                    }) {
//                        Icon(
//                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
//                            contentDescription = "Navigate back",
//                        )
//                    }
//                }
//            )
//        },
//        containerColor = MaterialTheme.colorScheme.background,
//        bottomBar = {
//            RoundedButtonCompo(
//                modifier = Modifier,
//                title = if (authUIStates.currentScreen == CustomAuthScreen.SetProfilePictureScreen) "Complete" else "Next",
//                enabled = true,
//                onClick = {
//                    localFocusManager.clearFocus()
//
//                    if (authViewModel.isFullNameValid() && authViewModel.isUsernameValid()) {
//                        authViewModel.navigateNextScreen()
//                    }
//                }
//            )
//        }
//    ) { innerPadding ->
//
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(innerPadding)
//                .imePadding()
//                .verticalScroll(rememberScrollState())
//                .padding(horizontal = 32.dp),
//            horizontalAlignment = Alignment.CenterHorizontally,
//        ) {
//
//            ProgressIndicator(
//                totalSteps = 6,
//                currentStep = 1,
//                modifier = Modifier.fillMaxWidth()
//            )
//
//            Spacer(modifier = Modifier.height(32.dp))
//
//            AuthScreenContent(
//                navController = navController,
//                authViewModel = authViewModel,
//                authUIStates = authUIStates
//            )
//        }
//
//    }
}


@Composable
private fun AuthScreenContent(
    navController: NavController,
    authViewModel: AuthViewModel,
    authUIStates: AuthUIStates
) {

    AppLogger.log("CURRENT SCREEN = ${authUIStates.currentScreen.title}")


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
//                AddNameScreen(
//                    authViewModel = authViewModel
//                )

                AddDOBScreen(
                    authViewModel = authViewModel,
                    onBackClick = {}
                )
            }

            is CustomAuthScreen.AddDOBScreen -> {
                AddDOBScreen(
                    authViewModel = authViewModel,
                    onBackClick = {}
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