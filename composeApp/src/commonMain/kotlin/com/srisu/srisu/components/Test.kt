package com.srisu.srisu.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val OtpLength = 6

@Composable
fun OtpVerificationScreen(
    otpCode: String,
    phoneNumber: String,
    countryCode: String = "+977",
    resendTimeText: String = "0:09",
    onOtpChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onVerifyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVerifyEnabled = otpCode.length == OtpLength

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            VerifyBottomButton(
                enabled = isVerifyEnabled,
                onVerifyClick = onVerifyClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Go back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MessageIconBox()

                Spacer(modifier = Modifier.height(72.dp))

                OtpHeader(
                    countryCode = countryCode,
                    phoneNumber = phoneNumber
                )

                Spacer(modifier = Modifier.height(56.dp))

                OtpInputField(
                    otpCode = otpCode,
                    onOtpChange = onOtpChange
                )

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = "Resend code in $resendTimeText",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MessageIconBox() {
    Surface(
        modifier = Modifier.size(112.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "OTP message",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 26.dp, end = 26.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.tertiaryContainer
            )
        }
    }
}

@Composable
private fun OtpHeader(
    countryCode: String,
    phoneNumber: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Text(
            text = "Check your messages",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "We sent a 6-digit code to $countryCode\n${phoneNumber.maskPhoneNumber()}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OtpInputField(
    otpCode: String,
    onOtpChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = FocusRequester()

    BasicTextField(
        value = otpCode,
        onValueChange = { value ->
            if (value.length <= OtpLength) {
                onOtpChange(value.filter { it.isDigit() })
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(OtpLength) { index ->
                    val digit = otpCode.getOrNull(index)?.toString().orEmpty()
                    val isFocused = index == otpCode.length

                    OtpDigitBox(
                        digit = digit,
                        isFocused = isFocused,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
private fun OtpDigitBox(
    digit: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .aspectRatio(0.82f)
            .heightIn(min = 64.dp, max = 84.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (digit.isNotEmpty()) {
                Text(
                    text = digit,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else if (isFocused) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .align(Alignment.Center)
                ) {
                    VerticalDivider(
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun VerifyBottomButton(
    enabled: Boolean,
    onVerifyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onVerifyClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(
                text = "Verify",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun String.maskPhoneNumber(): String {
    if (length <= 5) return this

    val start = take(2)
    val end = takeLast(3)
    return "$start•••••$end"
}

@Preview
@Composable
private fun OtpVerificationScreenPreview() {
    MaterialTheme {
        OtpVerificationScreen(
            otpCode = "",
            phoneNumber = "9812345123",
            onOtpChange = {},
            onBackClick = {},
            onVerifyClick = {}
        )
    }
}