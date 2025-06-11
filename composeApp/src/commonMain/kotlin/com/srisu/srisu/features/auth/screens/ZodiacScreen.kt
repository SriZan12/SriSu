package com.srisu.srisu.features.auth.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.features.auth.vm.AuthViewModel
import org.jetbrains.compose.resources.painterResource

@Composable
fun ZodiacScreen(authViewModel: AuthViewModel) {

    Scaffold { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {

            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding),
                contentAlignment = Alignment.Center
            ) {

                val authUIStates by authViewModel.authUiState.collectAsState()
                val zodiacSign = authUIStates.zodiacSign

                zodiacSign?.let { it ->
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

                        Image(
                            painter = painterResource(resource = it.logo),
                            contentDescription = "Zodiac_sign_logo",
                            modifier = Modifier.size(146.dp).align(Alignment.CenterHorizontally)
                        )

                        Text(
                            text = it.title,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Did you know?",
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            ),
                        )

                        Text(
                            text = it.zodiacDescription,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            ),
                        )

                    }
                }

                PrimaryButtonCompo(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
                        .align(Alignment.BottomCenter),
                    label = "Continue",
                    onClick = {
                        authViewModel.navigateNextScreen()
                    }
                )
            }
        }
    }
}
