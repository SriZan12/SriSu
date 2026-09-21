package com.srisu.srisu.components

import com.srisu.srisu.theme.SriSuFieldTypography
import com.srisu.srisu.theme.SriSuComponentTokens
import com.srisu.srisu.theme.disabledOutline
import androidx.compose.foundation.layout.heightIn
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.theme.field
import com.srisu.srisu.theme.transparent
import com.srisu.srisu.theme.caret

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srisu.srisu.core.logger.AppLogger
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res


@Composable
fun OutlinedTextFieldCompo(
    modifier: Modifier = Modifier,
    value: String,
    isError: Boolean = false,
    placeholder: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle = SriSuFieldTypography(MaterialTheme.typography).bodyLarge,
    shape: Shape = MaterialTheme.shapes.field,
    imeAction: ImeAction = ImeAction.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    keyboardType: KeyboardType = KeyboardType.Text,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        errorContainerColor = MaterialTheme.colorScheme.surface,
        disabledBorderColor = MaterialTheme.colorScheme.disabledOutline,
        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = SriSuComponentTokens.disabledAlpha),
        cursorColor = MaterialTheme.colorScheme.caret,
        errorBorderColor = MaterialTheme.colorScheme.error
    ).copy(focusedTextColor = MaterialTheme.colorScheme.onSurface),
    singleLine: Boolean = true,
    trailingImage: DrawableResource? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingIconDescription: String = "More options",
    onClickTrailingIcon: () -> Unit = {}
) {

    MaterialTheme(typography = SriSuFieldTypography(MaterialTheme.typography)) {
        OutlinedTextField(
            enabled = enabled,
            leadingIcon = leadingContent,
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            label = { Text(text = placeholder) },
            modifier = modifier.heightIn(min = MaterialTheme.spacing.fieldHeight),
            textStyle = textStyle,
            shape = shape,
            singleLine = singleLine,
            keyboardActions = keyboardActions,
            trailingIcon = if (trailingIcon != null || trailingImage != null) {
                {
                    IconButton(onClick = onClickTrailingIcon, enabled = enabled) {
                        if (trailingIcon != null) Icon(trailingIcon, trailingIconDescription,
                            modifier = Modifier.size(MaterialTheme.spacing.chromeIcon))
                        else if (trailingImage != null) Icon(painterResource(trailingImage), trailingIconDescription,
                            modifier = Modifier.size(MaterialTheme.spacing.chromeIcon))
                    }
                }
            } else null,

            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            colors = colors


        )
    }
}

@Composable
private fun PhoneNumberTextField(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = { value ->
            onPhoneNumberChange(value.filter { it.isDigit() })
        },
        modifier = modifier.height(64.dp),
        placeholder = {
            Text(
                text = "Phone number",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        singleLine = true,
        shape = MaterialTheme.shapes.field,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            cursorColor = MaterialTheme.colorScheme.caret
        )
    )
}

@Composable
fun OTPInputTextFields(
    otpLength: Int,
    onUpdateOtpValuesByIndex: (Int, String) -> Unit,
    onOtpInputComplete: () -> Unit,
    modifier: Modifier = Modifier,
    otpValues: List<String> = List(otpLength) { "" }, // Pass this as default for future reference
    isError: Boolean = false,
) {
    val focusRequesters = List(otpLength) { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        otpValues.forEachIndexed { index, value ->
            OutlinedTextField(
                modifier = Modifier.size(52.dp)
                    .focusRequester(focusRequesters[index])
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Backspace) {
                            if (otpValues[index].isEmpty() && index > 0) {
                                onUpdateOtpValuesByIndex(index, "")
                                focusRequesters[index - 1].requestFocus()
                            } else {
                                onUpdateOtpValuesByIndex(index, "")
                            }
                            true
                        } else {
                            false
                        }
                    },
                value = value,
                onValueChange = { newValue ->
                    // To use OTP code copied from keyboard
                    if (newValue.all { it.isDigit() }) {
                        if (newValue.length == otpLength) {
                            for (i in otpValues.indices) {
                                onUpdateOtpValuesByIndex(
                                    i,
                                    if (i < newValue.length && newValue[i].isDigit()) newValue[i].toString() else ""
                                )
                            }

                            keyboardController?.hide()
//                            onOtpInputComplete() // you should validate the otp values first for, if it is only digits or isNotEmpty
                        } else if (newValue.length <= 1) {
                            onUpdateOtpValuesByIndex(index, newValue)
                            if (newValue.isNotEmpty()) {
                                if (index < otpLength - 1) {
                                    focusRequesters[index + 1].requestFocus()
                                } else {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    AppLogger.log("TRIGGERED FROM ELSE IF CASE.")
//                                    onOtpInputComplete()
                                }
                            }
                        } else {
                            if (index < otpLength - 1) focusRequesters[index + 1].requestFocus()
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (index == otpLength - 1) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (index < otpLength - 1) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    },
                    onDone = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onOtpInputComplete()
                    }
                ),
                shape = MaterialTheme.shapes.field,
                isError = isError,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    errorBorderColor = MaterialTheme.colorScheme.error
                ).copy(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
            )

            LaunchedEffect(value) {
                if (otpValues.all { it.isNotEmpty() }) {
                    focusManager.clearFocus()
                    AppLogger.log("TRIGGERED FROM LAUNCHED EFFECT.")
                    onOtpInputComplete()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequesters.first().requestFocus()
    }
}


@Composable
fun OtpVerificationBox(
    modifier: Modifier = Modifier,
    onOtpEntered: (String) -> Unit
) {
    val otpLength = 6
    val otpValues = remember { mutableStateListOf("", "", "", "", "", "") }
    val focusNodes = remember { List(otpLength) { FocusRequester() } }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(otpLength) { index ->
            OutlinedTextField(
                value = otpValues[index],
                onValueChange = { newValue ->
                    if (newValue.length <= 1 && newValue.all { it.isDigit() }) {
                        otpValues[index] = newValue

                        // Move focus to the next field if a digit is entered
                        if (newValue.isNotEmpty() && index < otpLength - 1) {
                            focusNodes[index + 1].requestFocus()
                        }

                        // Trigger callback if all digits are entered
                        if (otpValues.all { it.isNotEmpty() }) {
                            onOtpEntered(otpValues.joinToString(""))
                        }
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .focusRequester(focusNodes[index]), // Use Modifier.focusRequester
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (index == otpLength - 1) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (index < otpLength - 1) {
                            focusNodes[index + 1].requestFocus()
                        }
                    },
                    onDone = {
                        if (otpValues.all { it.isNotEmpty() }) {
                            onOtpEntered(otpValues.joinToString(""))
                        }
                    }
                ),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }

    // Automatically focus the first field when the composable is first launched
    LaunchedEffect(Unit) {
        focusNodes[0].requestFocus()
    }
}

@Composable
fun SearchBar(
    hint: String,
    modifier: Modifier = Modifier,
    isEnabled: (Boolean) = true,
    height: Dp = 48.dp,
    elevation: Dp = 4.dp,
    cornerShape: Shape = MaterialTheme.shapes.small,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    onSearchClicked: () -> Unit = {},
    onTextChange: (String) -> Unit = {},
) {
    var text by remember { mutableStateOf(TextFieldValue()) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .height(height)
                .fillMaxWidth()
                .clickable { onSearchClicked() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = modifier
                    .weight(1f)
                    .size(36.dp)
                    .background(color = MaterialTheme.colorScheme.transparent, shape = CircleShape)
                    .clickable {
                        if (text.text.isNotEmpty()) {
                            text = TextFieldValue(text = "")
                            onTextChange("")
                        }
                    },
            ) {
                Icon(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(10.dp).size(24.dp),
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search_icon",
                    tint = if (text.text.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            BasicTextField(
                modifier = modifier
                    .weight(8f)
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.small),
                value = text,
                onValueChange = {
                    text = it
                    onTextChange(it.text)
                },
                enabled = isEnabled,
                textStyle = MaterialTheme.typography.titleSmall,
                decorationBox = { innerTextField ->
                    if (text.text.isEmpty()) {
                        Text(
                            text = hint,
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    innerTextField()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearchClicked() }),
                singleLine = true
            )
        }
    }
}

@Composable
fun PhoneNumberCompo(
    modifier: Modifier,
    countryCode: String,
    countryPrefix: String,
    phoneNumber: String,
    backgroundColor: Color = MaterialTheme.colorScheme.transparent,
    updatePhoneNumber: (String) -> Unit,
    onShowCountryList: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {

        CountryCodeDropDown(
            modifier = Modifier,
            selectedCountryPrefix = countryPrefix,
            selectedCountryCode = countryCode,
            backgroundColor = backgroundColor,
            onClick = {
                onShowCountryList()
            },
        )

        PhoneNumberTextField(
            phoneNumber = phoneNumber,
            onPhoneNumberChange = {
                updatePhoneNumber(it)
            },
            modifier = Modifier.weight(0.62f)
        )


    }

}

@Composable
fun LabeledTextFieldCompo(
    label: String,
    value: String,
    placeholder: String,
    isError: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
            },
            singleLine = true,
            isError = isError,
            shape = MaterialTheme.shapes.field,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                errorBorderColor = MaterialTheme.colorScheme.error,
                cursorColor = MaterialTheme.colorScheme.caret
            )
        )
    }
}


