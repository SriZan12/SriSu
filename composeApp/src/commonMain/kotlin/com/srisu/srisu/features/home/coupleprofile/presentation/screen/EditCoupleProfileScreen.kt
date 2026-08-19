package com.srisu.srisu.features.home.coupleprofile.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.features.home.coupleprofile.presentation.state.CoupleProfileUiState
import com.srisu.srisu.features.home.coupleprofile.presentation.state.EditCoupleProfileUiState
import com.srisu.srisu.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.image_placeholder

@Composable
fun EditCoupleProfileScreen(
    initialProfile: CoupleProfileUiState,
    onNavigateBack: () -> Unit,
    onSave: (CoupleProfileUiState) -> Unit,
    onChangeCoverPhoto: () -> Unit,
    onChangePartnerPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var uiState by remember(initialProfile) {
        mutableStateOf(EditCoupleProfileUiState.from(initialProfile))
    }

    EditCoupleProfileContent(
        uiState = uiState,
        onUiStateChange = { uiState = it },
        onNavigateBack = onNavigateBack,
        onSave = { onSave(uiState.toCoupleProfile(initialProfile)) },
        onChangeCoverPhoto = onChangeCoverPhoto,
        onChangePartnerPhotos = onChangePartnerPhotos,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCoupleProfileContent(
    uiState: EditCoupleProfileUiState,
    onUiStateChange: (EditCoupleProfileUiState) -> Unit,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    onChangeCoverPhoto: () -> Unit,
    onChangePartnerPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PrimaryToolBar(
                title = "Edit Couple Profile",
                showNavButton = true,
                onNavigate = {

                }
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            EditablePhotos(
                uiState = uiState,
                onChangeCoverPhoto = onChangeCoverPhoto,
                onChangePartnerPhotos = onChangePartnerPhotos,
            )

            EditSectionTitle("Couple details")
            EditField(
                value = uiState.coupleTitle,
                onValueChange = { onUiStateChange(uiState.copy(coupleTitle = it)) },
                label = "Couple title",
                placeholder = "The Soulmates",
            )
            EditField(
                value = uiState.tagline,
                onValueChange = { onUiStateChange(uiState.copy(tagline = it)) },
                label = "Tagline",
                placeholder = "Two hearts, one journey.",
            )
            EditField(
                value = uiState.anniversary,
                onValueChange = { onUiStateChange(uiState.copy(anniversary = it)) },
                label = "Anniversary",
                placeholder = "Oct 12",
            )

            EditSectionTitle("Shared interests")
            EditField(
                value = uiState.sharedInterests,
                onValueChange = { onUiStateChange(uiState.copy(sharedInterests = it)) },
                label = "Interests",
                placeholder = "Travel, Coffee, Photography",
                supportingText = "Separate each interest with a comma.",
            )

//            EditSectionTitle("Relationship strength")
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically,
//            ) {
//                Text("Current value", style = MaterialTheme.typography.bodyMedium)
//                Text(
//                    "${uiState.relationshipStrength.toInt()}%",
//                    color = MaterialTheme.colorScheme.primary,
//                    style = MaterialTheme.typography.titleMedium,
//                    fontWeight = FontWeight.Bold,
//                )
//            }
//            Slider(
//                value = uiState.relationshipStrength,
//                onValueChange = { onUiStateChange(uiState.copy(relationshipStrength = it)) },
//                valueRange = 0f..100f,
//                steps = 19,
//            )

            EditSectionTitle("Our journey")
            EditField(
                value = uiState.journeyStory,
                onValueChange = { onUiStateChange(uiState.copy(journeyStory = it)) },
                label = "Our story",
                placeholder = "Tell your story together...",
                singleLine = false,
                minLines = 6,
            )

            Button(
                onClick = onSave,
                enabled = uiState.coupleTitle.isNotBlank() && uiState.tagline.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Save Changes", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EditablePhotos(
    uiState: EditCoupleProfileUiState,
    onChangeCoverPhoto: () -> Unit,
    onChangePartnerPhotos: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            EditableProfileImage(
                imageUrl = uiState.coverPhotoUrl,
                contentDescription = "Couple cover photo",
                modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp)),
            )
            IconButton(
                onClick = onChangeCoverPhoto,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Change cover photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditableProfileImage(
                imageUrl = uiState.firstPartnerPhotoUrl,
                contentDescription = "First partner photo",
                modifier = Modifier.size(72.dp).clip(CircleShape),
            )
            EditableProfileImage(
                imageUrl = uiState.secondPartnerPhotoUrl,
                contentDescription = "Second partner photo",
                modifier = Modifier.padding(start = 8.dp).size(72.dp).clip(CircleShape),
            )
            IconButton(onClick = onChangePartnerPhotos, modifier = Modifier.padding(start = 6.dp)) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Change partner photos")
            }
        }
    }
}

@Composable
private fun EditableProfileImage(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier,
) {
    if (imageUrl.isNullOrBlank()) {
        Image(
            painter = painterResource(Res.drawable.image_placeholder),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
    )
}

@Composable
private fun EditSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Preview
@Composable
private fun EditCoupleProfileScreenPreview() {
    AppTheme {
        EditCoupleProfileScreen(
            initialProfile = CoupleProfileUiState(),
            onNavigateBack = {},
            onSave = {},
            onChangeCoverPhoto = {},
            onChangePartnerPhotos = {},
        )
    }
}
