package com.srisu.srisu.features.chat.presentation.findpartner.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonSize
import com.srisu.srisu.features.chat.presentation.findpartner.state.FindPartnerState
import com.srisu.srisu.theme.PartnerLinkTokens
import com.srisu.srisu.theme.SriSuPartnerLinkTypography
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.image_placeholder
import srisu.composeapp.generated.resources.partner_invite_landscape

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun YouAreConnectedScreen(state: FindPartnerState, onViewOurSpace: () -> Unit) {
    BackHandler(onBack = onViewOurSpace)
    MaterialTheme(typography = SriSuPartnerLinkTypography()) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(paddingValues = padding),
                contentAlignment = Alignment.TopCenter
            ) {
                val viewportHeight = maxHeight
                Column(
                    Modifier.widthIn(max = PartnerLinkTokens.contentMaxWidth).fillMaxWidth()
                        .verticalScroll(state = rememberScrollState())
                        .heightIn(min = viewportHeight),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(MaterialTheme.spacing.touchTarget))
                    // Replace this themed placeholder when the Figma celebration asset is available.
                    Box(
                        Modifier.size(PartnerLinkTokens.connectedCelebration),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Invitation accepted",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(all = MaterialTheme.spacing.large)
                                    .size(MaterialTheme.spacing.spacious)
                            )
                        }
                    }
                    PartnerLinkHeading(
                        title = "You’re connected!",
                        subtitle = "Welcome to your shared SriSu space."
                    )
                    Card(
                        Modifier.padding(
                            horizontal = MaterialTheme.spacing.gutter,
                            vertical = MaterialTheme.spacing.extraLarge
                        )
                            .fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            Modifier.padding(all = MaterialTheme.spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                                verticalAlignment = Alignment.Top
                            ) {
                                ConnectedAvatar(
                                    state.senderName,
                                    photo = state.senderPhotoUrl,
                                    Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Favorite, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = MaterialTheme.spacing.large)
                                        .size(MaterialTheme.spacing.icon)
                                )
                                ConnectedAvatar(
                                    name = state.acceptedPartnerName ?: "Your partner",
                                    photo = state.acceptedPartnerPhotoUrl,
                                    Modifier.weight(1f)
                                )
                            }
                            Text(state.connectedSince?.let { "Together since $it" }
                                ?: "Your shared journey starts here.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)


                            SriSuButton(
                                label = "View our space",
                                onClick = onViewOurSpace,
                                Modifier.fillMaxWidth(),
                                size = SriSuButtonSize.Large
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Existing Figma landscape placeholder; replace independently of screen behavior.
                    Image(
                        painterResource(Res.drawable.partner_invite_landscape),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(PartnerLinkTokens.landscapeAspectRatio),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedAvatar(name: String, photo: String?, modifier: Modifier) {
    Column(
        modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        AsyncImage(
            model = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.image_placeholder),
            error = painterResource(Res.drawable.image_placeholder),
            fallback = painterResource(Res.drawable.image_placeholder),
            modifier = Modifier.size(PartnerLinkTokens.connectedAvatar).clip(CircleShape)
        )
        Text(name, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
