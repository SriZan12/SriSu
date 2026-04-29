package com.srisu.srisu.features.chat.presentation.findpartner.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.CountrySelectionBottomSheet
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.ErrorText
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.PhoneNumberCompo
import com.srisu.srisu.components.PrimaryButtonCompo
import com.srisu.srisu.components.RequestSentDialog
import com.srisu.srisu.components.TextIfNotEmpty
import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
import com.srisu.srisu.features.chat.chatroom.couple.findpartner.FindPartnerState
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.suggestions.presentation.screen.InterestChip
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import com.srisu.srisu.utils.isInternetAvailable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.image_placeholder
import srisu.composeapp.generated.resources.leo

@Composable
fun FindYourPartnerScreen(
    findPartnerViewModel: FindPartnerViewModel = koinViewModel<FindPartnerViewModel>()
) {
    val findPartnerUIStates by findPartnerViewModel.findPartnerUIState.collectAsState()

    HandleUiStates(
        findPartnerViewModel = findPartnerViewModel,
        findPartnerUIState = findPartnerUIStates
    )


    FindYourPartnerContent(
        findPartnerViewModel = findPartnerViewModel,
        findPartnerUIStates = findPartnerUIStates
    )

}

@Composable
private fun HandleUiStates(
    findPartnerViewModel: FindPartnerViewModel,
    findPartnerUIState: FindPartnerState
) {

    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = findPartnerUIState.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    findPartnerViewModel.idleScreen()
                },
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = {
                    findPartnerViewModel.idleScreen()
                }
            )
        }

        is BaseUIState.Success<*> -> {
            RequestSentDialog(
                successMessage = baseUIState.message,
                onDismiss = {
                    findPartnerViewModel.idleScreen()
                },
            )
        }

        is BaseUIState.NoInternetConnection -> {
            showBottomSheet = baseUIState.isOffline
        }

        is BaseUIState.Idle -> {
            Unit
        }
    }

    if (showBottomSheet) {
        OfflineBottomSheetCompo(
            show = showBottomSheet,
            onDismiss = {
                findPartnerViewModel.idleScreen()
            }
        )
    }
}

@Composable
private fun FindYourPartnerContent(
    findPartnerViewModel: FindPartnerViewModel,
    findPartnerUIStates: FindPartnerState
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {

            Column {

                var showCountryList by rememberSaveable {
                    mutableStateOf(false)
                }

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Find Your Partner",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center
                )
                Text(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    text = "Enter your partner number to connect",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(36.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                    PhoneNumberCompo(
                        findPartnerUIState = findPartnerUIStates,
                        findPartnerViewModel = findPartnerViewModel,
                        onShowCountryList = {
                            showCountryList = true
                        }
                    )

                    CountrySelectionBottomSheet(
                        modifier = Modifier,
                        countries = findPartnerUIStates.countryList,
                        show = showCountryList,
                        onCountrySelected = { countryModel ->
                            showCountryList = false
                            findPartnerViewModel.updateCountry(
                                code = countryModel.code ?: "",
                                prefix = countryModel.prefix ?: ""
                            )
                        }) {
                        showCountryList = false
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                PrimaryButtonCompo(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    label = "♡  Find Partner  ♡",
                    onClick = {
                        val isValid = findPartnerViewModel.validatePhoneNumber()

                        if (isValid) {
                            findPartnerViewModel.sendFindYourPartnerRequest()
                        }
                    }
                )
            }

            val showPartnerProfile = findPartnerUIStates.showPartnerProfile
            if (showPartnerProfile) {
                val partnerProfile = findPartnerUIStates.partnerResponse
                PartnerProfileDialog(
                    name = partnerProfile?.fullName ?: "",
                    phone = partnerProfile?.phoneNumber ?: "",
                    zodiac = partnerProfile?.zodiacSign ?: "",
                    gender = partnerProfile?.gender ?: "",
                    age = DateTimeUtils.calculateAge(dateString = partnerProfile?.dob).toString(),
                    city = partnerProfile?.city ?: "",
                    country = partnerProfile?.country ?: "",
                    bio = partnerProfile?.bio ?: "",
                    interests = partnerProfile?.userInterests,
                    profilePhotoUrl = partnerProfile?.profilePhoto,
                    onConnectClick = {
                        findPartnerViewModel.sendCoupleConnectionRequest()
                        findPartnerViewModel.updateShowPartnerProfile(showPartnerProfile = false)
                    },
                    onDismiss = {
                        findPartnerViewModel.updateShowPartnerProfile(showPartnerProfile = false)
                    }
                )
            }

        }
    }
}

@Composable
private fun PhoneNumberCompo(
    findPartnerUIState: FindPartnerState,
    findPartnerViewModel: FindPartnerViewModel,
    onShowCountryList: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        PhoneNumberCompo(
            modifier = Modifier.fillMaxWidth(),
            countryCode = findPartnerUIState.countryCode,
            countryPrefix = findPartnerUIState.countryPrefix,
            phoneNumber = findPartnerUIState.phoneNumber,
            updatePhoneNumber = { input ->
                if (input.all { it.isDigit() }) {
                    findPartnerViewModel.updatePhoneNumber(phoneNumber = input)
                }
            },
            onShowCountryList = {
                onShowCountryList()
            },
        )


        if (findPartnerUIState.validationErrorMsg.isNotEmpty()) {
            ErrorText(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                text = "Invalid Phone number format!"
            )
        }
    }

}

@Composable
fun PartnerProfileDialog(
    name: String,
    phone: String,
    zodiac: String,
    gender: String,
    age: String,
    city: String,
    country: String,
    bio: String,
    interests: List<FindYourPartnerResponse.UserInterest?>?,
    profilePhotoUrl: String?,
    onConnectClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = 24.dp,
                        bottom = 12.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    if (
                        profilePhotoUrl.isNullOrEmpty()
                    ) {
                        Image(
                            painter = painterResource(resource = Res.drawable.image_placeholder),
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        AsyncImage(
                            model = profilePhotoUrl,
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextIfNotEmpty(
                        text = name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    TextIfNotEmpty(
                        text = phone,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Black)
                    )


                    Spacer(modifier = Modifier.height(8.dp))


                    TextIfNotEmpty(
                        text = age,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )

                    TextIfNotEmpty(
                        text = "$city, $country",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )

                    Row(
                        modifier = Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val zodiacSignImage = ZodiacUtils.getZodiacSignImage(name = zodiac.trim())
                        Image(
                            painter = painterResource(
                                resource = zodiacSignImage ?: Res.drawable.leo
                            ),
                            contentDescription = "zodiac sign",
                            modifier = Modifier.size(32.dp)
                        )

                        Text(
                            text = gender,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )

                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val trimmedBio = bio
                        .split("\\s+".toRegex())        // split by whitespace
                        .take(30)                       // take only first 40 words
                        .joinToString(" ")              // join them back
                        .let { if (it.length < bio.length) "$it..." else it } // add ellipsis if trimmed

                    if (trimmedBio.isNotBlank()) {
                        Text(
                            text = "\"$trimmedBio\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                color = Color.Black
                            ),
                            textAlign = TextAlign.Center
                        )
                    }


                }

                if (!interests.isNullOrEmpty()) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "Interests",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )

                    LazyRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(
                            items = interests,
                            key = { it?.id ?: "" }
                        ) { interest ->
                            interest?.let {
                                if (!interest.name.isNullOrEmpty()) {
                                    InterestChip(
                                        label = interest.name
                                    )
                                }
                            }
                        }
                    }
                }


                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(vertical = 22.dp)
                        .height(48.dp)
                ) {
                    Text("Connect Now", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Outlined.Favorite,
                        contentDescription = "Connect",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PartnerProfileDialog() {
    val age = DateTimeUtils.calculateAge(dateString = "2000-01-01").toString()

    PartnerProfileDialog(
        name = "John Doe",
        phone = "+91 9863938267",
        zodiac = "Leo",
        gender = "Male",
        age = age,
        city = "New Delhi",
        country = "India",
        bio = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
        interests = listOf(
            FindYourPartnerResponse.UserInterest(name = "Reading"),
            FindYourPartnerResponse.UserInterest(name = "Traveling"),
            FindYourPartnerResponse.UserInterest(name = "Music"),
            FindYourPartnerResponse.UserInterest(name = "Sports")
        ),
        profilePhotoUrl = "https://images.unsplash.com/photo-1599475735868-a8924c458792?ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D&auto=format&fit=crop&q=80&w=926",
        onConnectClick = {},
        onDismiss = {}

    )
}


