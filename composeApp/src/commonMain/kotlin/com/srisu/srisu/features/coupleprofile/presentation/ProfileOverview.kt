package com.srisu.srisu.features.coupleprofile.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonVariant
import com.srisu.srisu.features.coupleprofile.data.*
import com.srisu.srisu.theme.*
import org.jetbrains.compose.resources.*
import srisu.composeapp.generated.resources.*

/** Only already-authorized bytes reach the image loader; keep private disk/memory caches off. */
@Composable internal fun ProtectedImage(
    bytes: ByteArray?, description: String?, modifier: Modifier,
    alignment: Alignment = Alignment.Center, avatar: Boolean = false,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        // Remains behind both loading and failed images, without changing layout bounds.
        Icon(if (avatar) Icons.Default.Person else Icons.Default.Image, description,
            Modifier.size(if (avatar) MaterialTheme.spacing.large else MaterialTheme.spacing.chromeIcon),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (bytes != null) AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current).data(bytes)
                .size(if (avatar) 192 else 1024)
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build(),
            contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = alignment,
        )
    }
}

@Composable internal fun ProfileAvatar(member: CoupleMember, images: Map<String, ByteArray>, modifier: Modifier) {
    ProtectedImage(member.photoUrl?.let(images::get), stringResource(Res.string.cp_named_avatar, member.name),
        modifier.clip(CircleShape).border(MaterialTheme.spacing.tiny, MaterialTheme.colorScheme.background, CircleShape), avatar = true)
}

@Composable private fun ProfileHero(state: CoupleProfileState, vm: CoupleProfileViewModel, editable: Boolean) {
    val profile = state.profile ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.2f).clip(MaterialTheme.shapes.large)) {
            if (profile.cover?.url != null) ProtectedImage(state.images[profile.cover.url], stringResource(Res.string.cp_cover_description),
                Modifier.fillMaxSize(), BiasAlignment(0f, profile.cover.focalY * 2 - 1))
            else Image(painterResource(Res.drawable.onboarding_landscape), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter)
            if (editable) Surface(shape = MaterialTheme.shapes.pill, color = MaterialTheme.colorScheme.background,
                modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacing.small)) {
                if (profile.cover?.url == null) TextButton(onClick = { vm.open(ProfilePage.COVER) }) {
                    Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(MaterialTheme.spacing.icon))
                    Spacer(Modifier.width(MaterialTheme.spacing.small))
                    Text(stringResource(Res.string.cp_add_cover), style = MaterialTheme.typography.bodySmall)
                } else IconButton(onClick = { vm.open(ProfilePage.COVER) }) {
                    Icon(Icons.Default.PhotoCamera, stringResource(Res.string.cp_change_cover))
                }
            }
        }
        if (!profile.members.isNullOrEmpty()) {
            // The row occupies its actual height; overlap does not leave a second avatar-sized gap.
            Box(Modifier.fillMaxWidth().height(MaterialTheme.spacing.huge - MaterialTheme.spacing.large)) {
                Row(Modifier.align(Alignment.TopCenter).offset(y = -MaterialTheme.spacing.large).requiredHeight(MaterialTheme.spacing.huge), horizontalArrangement = Arrangement.spacedBy(-MaterialTheme.spacing.compact)) {
                    profile.members.forEach { ProfileAvatar(it, state.images, Modifier.size(MaterialTheme.spacing.huge)) }
                }
            }
            Text(profile.members.joinToString(" & ") { it.name }, Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }
        if (editable) TextButton(onClick = { vm.open(ProfilePage.DATE) }) {
            Icon(Icons.Default.CalendarMonth, null, Modifier.size(MaterialTheme.spacing.medium))
            Spacer(Modifier.width(MaterialTheme.spacing.small))
            Text(profile.anniversaryDate?.let { stringResource(Res.string.cp_since, it) } ?: stringResource(Res.string.cp_date),
                style = MaterialTheme.typography.bodySmall)
        } else profile.anniversaryDate?.let {
            Text(stringResource(Res.string.cp_since, it), Modifier.padding(top = MaterialTheme.spacing.small),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun ProfileOverview(state: CoupleProfileState, vm: CoupleProfileViewModel, accentMotion: ProfileAccentMotion, preview: Boolean = false) {
    val profile = state.profile ?: return
    val editable = profile.canEdit && !preview
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            if (profile.canEdit || profile.members != null || profile.cover != null) ProfileHero(state, vm, editable)
            if (!profile.canEdit && profile.publishedSections.isEmpty()) SectionCard {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.cp_private_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.cp_private), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (profile.canFave) SriSuButton(stringResource(if (profile.isFaved) Res.string.cp_unfave else Res.string.cp_fave), vm::fave,
                enabled = !state.saving, modifier = Modifier.fillMaxWidth(), variant = SriSuButtonVariant.Outline)
            ProfileMetrics(profile, accentMotion, motionEnabled = state.page != ProfilePage.ANSWER)
        }
        if (profile.story != null || profile.canEdit) ProfileSection(Res.string.cp_story,
            if (preview) null else if (profile.story.isNullOrEmpty() && profile.canEdit) Res.string.cp_write else Res.string.cp_read,
            { vm.open(if (profile.story.isNullOrEmpty() && profile.canEdit) ProfilePage.STORY_EDIT else ProfilePage.STORY) }) {
            SectionCard {
                if (profile.story.isNullOrEmpty()) Text(stringResource(Res.string.cp_story_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else StoryAnswers(profile, vm.accountId, vm::openAnswer, editable)
            }
        }
        if (profile.canEdit || "song" in profile.publishedSections) ProfileSection(Res.string.cp_song,
            if (editable) if (profile.song == null) Res.string.cp_pick else Res.string.cp_change else null, { vm.open(ProfilePage.SONG) }) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
                    Surface(shape = MaterialTheme.shapes.field, color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.size(MaterialTheme.spacing.touchTarget), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                        Text(profile.song?.title ?: stringResource(Res.string.cp_song_empty), style = MaterialTheme.typography.titleMedium)
                        Text(profile.song?.artist?.takeIf(String::isNotBlank) ?: stringResource(Res.string.cp_song_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                profile.song?.note?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        profile.interests?.let { interests ->
            ProfileSection(Res.string.cp_interests, if (editable) Res.string.cp_edit else null, { vm.open(ProfilePage.INTERESTS) }) {
                if (interests.shared.isEmpty()) SectionCard {
                    Text(stringResource(Res.string.cp_interests_empty), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.cp_interests_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else InterestLabels(interests.shared, shared = true)
                if (profile.canEdit) {
                    InterestSelections(Res.string.cp_your_interests, interests.mine.orEmpty())
                    InterestSelections(Res.string.cp_partner_likes, interests.partner.orEmpty())
                }
            }
        }
        if (profile.canEdit) ProfileSection(Res.string.cp_together) {
            if (preview) SectionCard { PlanSummary(profile) }
            else Surface(onClick = { vm.open(ProfilePage.PLANS) }, shape = MaterialTheme.shapes.large,
                color = CoupleProfileTokens.sectionSurface(MaterialTheme.colorScheme)) {
                Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
                    Icon(Icons.Default.CalendarMonth, null)
                    Column(Modifier.weight(1f)) { PlanSummary(profile) }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
            if (editable) TextButton(onClick = { vm.open(ProfilePage.SHARING) }) {
                Icon(Icons.Default.Lock, null, Modifier.size(MaterialTheme.spacing.icon))
                Spacer(Modifier.width(MaterialTheme.spacing.small))
                Text(stringResource(Res.string.cp_sharing), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable private fun PlanSummary(profile: CoupleProfile) {
    Text(stringResource(Res.string.cp_plans), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.cp_plans_summary, profile.plansDone ?: 0, profile.plansUpcoming ?: 0),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun ProfileSection(title: StringResource, action: StringResource? = null, onClick: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        SectionHeader(title, action, onClick)
        content()
    }
}

@Composable private fun InterestSelections(label: StringResource, names: List<String>) {
    Column(Modifier.padding(top = MaterialTheme.spacing.small), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (names.isEmpty()) Text(stringResource(Res.string.cp_no_selections), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else InterestLabels(names)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun InterestLabels(names: List<String>, shared: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        names.forEach { name ->
            Surface(shape = MaterialTheme.shapes.pill,
                color = if (shared) MaterialTheme.colorScheme.secondaryContainer else CoupleProfileTokens.sectionSurface(MaterialTheme.colorScheme),
                contentColor = if (shared) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface) {
                Text(name, Modifier.padding(horizontal = MaterialTheme.spacing.compact, vertical = MaterialTheme.spacing.small), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable internal fun ProfileMetrics(profile: CoupleProfile, motion: ProfileAccentMotion, motionEnabled: Boolean = true) {
    Surface(modifier = profileAccentEntrance(motion, motionEnabled), shape = MaterialTheme.shapes.large, color = CoupleProfileTokens.sectionSurface(MaterialTheme.colorScheme)) {
        Row(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.medium, horizontal = MaterialTheme.spacing.small), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            profile.daysTogether?.let { Metric(it.toString(), Res.string.cp_days, Icons.Default.Favorite, Modifier.weight(1f),
                Modifier.graphicsLayer { scaleX = motion.heartScale.value; scaleY = motion.heartScale.value }) }
            Metric(profile.visibleMomentCount.toString(), Res.string.cp_moments, Icons.Default.AutoAwesome, Modifier.weight(1f),
                Modifier.graphicsLayer { scaleX = motion.momentsScale.value; scaleY = motion.momentsScale.value; rotationZ = motion.momentsRotation.value })
            profile.daysToAnniversary?.let { Metric(it.toString(), Res.string.cp_anniversary_countdown, Icons.Default.CalendarMonth, Modifier.weight(1f)) }
                ?: profile.plansDone?.let { Metric(it.toString(), Res.string.cp_done, Icons.Default.CalendarMonth, Modifier.weight(1f)) }
        }
    }
}
@Composable private fun Metric(value: String, label: StringResource, icon: ImageVector, modifier: Modifier, iconMotion: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
        Icon(icon, null, Modifier.size(MaterialTheme.spacing.icon).then(iconMotion), tint = when (icon) {
            Icons.Default.CalendarMonth -> MaterialTheme.colorScheme.planner
            Icons.Default.AutoAwesome -> MaterialTheme.colorScheme.moments
            else -> MaterialTheme.colorScheme.primary
        })
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun SectionHeader(title: StringResource, action: StringResource? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null) TextButton(onClick = onClick) {
            if (action != Res.string.cp_read) { Icon(Icons.Default.Edit, null, Modifier.size(MaterialTheme.spacing.medium)); Spacer(Modifier.width(MaterialTheme.spacing.tiny)) }
            Text(stringResource(action), style = MaterialTheme.typography.labelMedium)
        }
    }
}
@Composable internal fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = CoupleProfileTokens.sectionSurface(MaterialTheme.colorScheme)) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact), content = content)
    }
}
@Composable internal fun promptTitle(prompt: String) = stringResource(when (prompt) {
    "how_met" -> Res.string.cp_how_met
    "first_move" -> Res.string.cp_first_move
    else -> Res.string.cp_first_impression
})
@Composable internal fun StoryAnswers(profile: CoupleProfile, account: Long?, onEdit: (String) -> Unit, editable: Boolean = profile.canEdit) {
    CoupleProfileViewModel.PROMPTS.forEachIndexed { index, prompt ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Text(promptTitle(prompt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val answers = profile.story.orEmpty().filter { it.prompt == prompt }
            if (answers.isEmpty()) Text(stringResource(if (profile.canEdit) Res.string.cp_partner_side else Res.string.cp_no_answer_shared), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            answers.forEach { answer ->
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Person, null, Modifier.padding(top = MaterialTheme.spacing.tiny).size(MaterialTheme.spacing.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                        Text(answer.answer, style = MaterialTheme.typography.bodyMedium)
                        profile.members?.firstOrNull { it.id == answer.authorId }?.let {
                            Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (editable && answer.authorId == account) IconButton(onClick = { onEdit(prompt) }) {
                        Icon(Icons.Default.Edit, stringResource(Res.string.cp_edit_answer, promptTitle(prompt)), Modifier.size(MaterialTheme.spacing.icon))
                    }
                }
            }
        }
    }
}
