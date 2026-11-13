@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.srisu.srisu.features.auth.presentation.screen.introduction

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonSize
import com.srisu.srisu.theme.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit, onGuest: () -> Unit, onLogIn: () -> Unit) {
    MaterialTheme(typography = SriSuPartnerLinkTypography()) {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                val topSpace = (maxHeight * 0.09f).coerceIn(24.dp, 72.dp)
                Column(
                    Modifier.widthIn(max = PartnerLinkTokens.contentMaxWidth).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(topSpace))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Srisu", style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.alignByBaseline().semantics { heading() })
                        Spacer(Modifier.width(MaterialTheme.spacing.small))
                        Box(Modifier.size(IntroductionTokens.brandDotSize).alignBy { it.measuredHeight }
                            .background(IntroductionTokens.brandDot, MaterialTheme.shapes.pill))
                    }
                    Text(stringResource(Res.string.intro_tagline), style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
                    Image(
                        painterResource(Res.drawable.onboarding_landscape), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(IntroductionTokens.landscapeAspectRatio),
                        contentScale = ContentScale.FillWidth,
                    )
                    Spacer(Modifier.height(MaterialTheme.spacing.section))
                    Text(stringResource(Res.string.intro_description), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.gutter))
                    Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.gutter),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        IntroductionButton(stringResource(Res.string.intro_start), onGetStarted)
                        TextButton(onClick = onGuest, modifier = Modifier.fillMaxWidth().heightIn(min = MaterialTheme.spacing.touchTarget)) {
                            Text(stringResource(Res.string.intro_guest), style = MaterialTheme.typography.labelLarge)
                        }
                        // The whole sentence is one accessible login action and can wrap at large font sizes.
                        TextButton(onClick = onLogIn, modifier = Modifier.heightIn(min = MaterialTheme.spacing.touchTarget)) {
                            Text(buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append(stringResource(Res.string.intro_existing_account)); append(" ")
                                }
                                withStyle(SpanStyle(fontWeight = MaterialTheme.typography.labelLarge.fontWeight)) {
                                    append(stringResource(Res.string.intro_login))
                                }
                            }, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpaceScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    BackHandler(onBack = onBack)
    MaterialTheme(typography = SriSuPartnerLinkTypography()) {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = PartnerLinkTokens.contentMaxWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                    .padding(horizontal = MaterialTheme.spacing.gutter)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.intro_back))
                        }
                        Spacer(Modifier.weight(1f))
                        val progress = stringResource(Res.string.intro_progress)
                        Row(Modifier.semantics { contentDescription = progress }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(5) { index ->
                                Box(Modifier.size(IntroductionTokens.progressWidth, IntroductionTokens.progressHeight)
                                    .background(if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                        MaterialTheme.shapes.pill))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onContinue, contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.tiny)) {
                            Text(stringResource(Res.string.intro_skip), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(MaterialTheme.spacing.large))
                    Text(stringResource(Res.string.intro_space_title), style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(MaterialTheme.spacing.compact))
                    Text(stringResource(Res.string.intro_space_description), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
                    SpaceFeatureCards()
                    Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
                    Spacer(Modifier.weight(1f))
                    IntroductionButton(stringResource(Res.string.intro_continue), onContinue)
                    Spacer(Modifier.height(MaterialTheme.spacing.large))
                }
            }
        }
    }
}

@Composable
internal fun IntroductionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SriSuButton(label, onClick, modifier.fillMaxWidth(), size = SriSuButtonSize.Large,
        trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(MaterialTheme.spacing.icon)) })
}

/** Shared public product introduction; these cards do not fetch private user data. */
@Composable
internal fun SpaceFeatureCards() {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
        FeatureCard(stringResource(Res.string.intro_moments), stringResource(Res.string.intro_moments_description),
            IntroductionTokens.momentsContainer(colors), IntroductionTokens.moments) {
            Icon(painterResource(Res.drawable.intro_sparkle), null, Modifier.size(IntroductionTokens.featureGlyph), tint = IntroductionTokens.iconInk)
        }
        FeatureCard(stringResource(Res.string.intro_plans), stringResource(Res.string.intro_plans_description),
            IntroductionTokens.plansContainer(colors), IntroductionTokens.plans) {
            Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(IntroductionTokens.featureGlyph), tint = IntroductionTokens.iconInk)
        }
        FeatureCard(stringResource(Res.string.intro_memories), stringResource(Res.string.intro_memories_description),
            IntroductionTokens.memoriesContainer(colors), IntroductionTokens.memories) {
            Icon(Icons.Rounded.Image, null, Modifier.size(IntroductionTokens.featureGlyph), tint = IntroductionTokens.iconInk)
        }
    }
}

@Composable
private fun FeatureCard(title: String, description: String, container: Color, badge: Color, icon: @Composable () -> Unit) {
    Surface(color = container, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(MaterialTheme.spacing.medium).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(IntroductionTokens.featureIconPlate).background(IntroductionTokens.iconPlate, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(IntroductionTokens.featureBadge).background(badge, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.width(MaterialTheme.spacing.medium))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(MaterialTheme.spacing.tiny))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
