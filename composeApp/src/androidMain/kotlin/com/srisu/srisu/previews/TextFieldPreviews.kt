package com.srisu.srisu.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.srisu.srisu.features.auth.presentation.screen.profilesetup.SetProfilePictureScreen
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import org.koin.compose.viewmodel.koinViewModel

/*@Composable
@Preview(showBackground = true)
fun PreviewOutlinedTextField() {
    Box(modifier = Modifier.fillMaxWidth()) {
        var text by remember {
            (mutableStateOf(""))
        }

        OutlinedTextFieldCompo(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            value = text,
            placeholder = "Placeholder",
            onValueChange = {
                text = it
            }
        )
    }
}*/

/*@Composable
@Preview(showBackground = true)
fun OtpVerificationScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter OTP",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OtpVerificationBox(
            modifier = Modifier.fillMaxWidth(),
            onOtpEntered = { otp ->
                // Handle the entered OTP
                println("Entered OTP: $otp")
            }
        )
    }
}*/

@Composable
@Preview(showBackground = true)
fun ShowCountryCodeDropDown() {
    /*CountryCodeDropDown(
        selectedCountryCode = "IN",
        selectedCountryPrefix = "+92"
    ) { }*/

    SetProfilePictureScreen(
        authViewModel = koinViewModel<AuthViewModel>(),
        navController = rememberNavController()
    )
}
