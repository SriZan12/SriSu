package com.srisu.srisu.features.home.couple.presentation.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.features.home.couple.presentation.state.CoupleProfileUiState
import com.srisu.srisu.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.image_placeholder

@Composable
fun CoupleProfileScreen(
    onNavigateBack: () -> Unit,
    onSendMessage: () -> Unit,
    onPlanDate: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: CoupleProfileUiState = CoupleProfileUiState(),
    isLoading: Boolean = false,
    isMissing: Boolean = false,
    errorTitle: String? = null,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onDismissError: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        topBar = {
            CoupleProfileTopBar(
                onNavigateBack = onNavigateBack,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { contentPadding ->
        if (isMissing) {
            MissingCoupleProfile(
                onNavigateBack = onNavigateBack,
                onRetry = onRetry,
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CoupleHero(uiState = uiState)
                CoupleIdentity(uiState = uiState)
                if (!uiState.profileComplete) {
                    CompleteProfileCard(onCompleteProfile = onOpenSettings)
                }
                CoupleActions(onPlanDate = onPlanDate, onSendMessage = onSendMessage)
                RelationshipHighlights(uiState = uiState)
                if (uiState.sharedInterests.isNotEmpty()) {
                    SharedInterests(interests = uiState.sharedInterests)
                }
                RelationshipStrength(value = uiState.relationshipStrength)
                if (uiState.journeyStory.isNotBlank()) {
                    JourneyStory(story = uiState.journeyStory)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (isLoading) {
        LoadingScrim()
    }

    ErrorDialog(
        title = errorTitle,
        errorMessage = errorMessage,
        show = !errorMessage.isNullOrBlank(),
        onDismiss = onDismissError,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CoupleProfileTopBar(
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "SriSu",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Couple profile settings",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun CoupleHero(uiState: CoupleProfileUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ProfileImage(
            imageUrl = uiState.coverPhotoUrl,
            contentDescription = "Couple cover photo",
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)),
        )

        Row(
            modifier = Modifier.offset(y = 48.dp),
            horizontalArrangement = Arrangement.spacedBy((-14).dp),
        ) {
            PartnerPortrait(
                imageUrl = uiState.firstPartnerPhotoUrl,
                contentDescription = "First partner profile photo",
            )
            PartnerPortrait(
                imageUrl = uiState.secondPartnerPhotoUrl,
                contentDescription = "Second partner profile photo",
            )
        }
    }
}

@Composable
private fun PartnerPortrait(
    imageUrl: String?,
    contentDescription: String
) {
    Card(
        modifier = Modifier.size(104.dp),
        shape = CircleShape,
        border = BorderStroke(
            width = 5.dp,
            color = MaterialTheme.colorScheme.background,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        ProfileImage(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    }
}

@Composable
private fun ProfileImage(
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
private fun CoupleIdentity(uiState: CoupleProfileUiState) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = uiState.coupleTitle.ifBlank { "Our Story" },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = uiState.partnerNames.ifBlank { "You & your partner" },
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "“${uiState.tagline.ifBlank { "Make this space yours." }}”",
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CompleteProfileCard(onCompleteProfile: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Complete your couple profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Add your anniversary, shared interests, cover photo, and story.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedButton(
                onClick = onCompleteProfile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Set up profile")
            }
        }
    }
}

@Composable
private fun MissingCoupleProfile(
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp),
        )
        Text(
            text = "No couple profile yet",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Connect with your partner first, then return here to create your shared space.",
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            Text("Try again")
        }
        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Go back")
        }
    }
}

@Composable
private fun CoupleActions(onPlanDate: () -> Unit, onSendMessage: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPlanDate,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Plan a Date",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onSendMessage,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Message",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RelationshipHighlights(uiState: CoupleProfileUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HighlightCard(
            label = "DAYS TOGETHER",
            value = uiState.daysTogether.toString(),
            icon = { Icon(imageVector = Icons.Default.DateRange, contentDescription = null) },
            modifier = Modifier.weight(1f),
        )
        HighlightCard(
            label = "ANNIVERSARY",
            value = uiState.anniversary,
            icon = { Icon(imageVector = Icons.Default.Favorite, contentDescription = null) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HighlightCard(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { icon() }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SharedInterests(interests: List<String>) {
    SectionTitle(text = "SHARED INTERESTS")
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 96.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        userScrollEnabled = false,
    ) {
        items(interests) { interest ->
            Card(
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    text = interest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RelationshipStrength(value: Int) {
    val safeValue = value.coerceIn(0, 100)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Relationship Strength",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$safeValue%",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { safeValue / 100f },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@Composable
private fun JourneyStory(story: String) {
    SectionTitle(text = "Our Journey Together", prominent = true)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(24.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).size(54.dp),
                tint = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
            Text(
                text = story,
                modifier = Modifier.padding(end = 20.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, prominent: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        textAlign = TextAlign.Center,
        color = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        style = if (prominent) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Preview
@Composable
private fun CoupleProfileScreenPreview() {
    AppTheme {
        CoupleProfileScreen(
            onNavigateBack = {},
            onSendMessage = {},
            onPlanDate = {},
            onOpenSettings = {},
            uiState = CoupleProfileUiState.preview(),
        )
    }
}
