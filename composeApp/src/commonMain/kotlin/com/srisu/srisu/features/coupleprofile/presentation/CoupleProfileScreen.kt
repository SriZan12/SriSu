package com.srisu.srisu.features.coupleprofile.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonVariant
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.coupleprofile.data.*
import com.srisu.srisu.theme.*
import com.srisu.srisu.utils.*
import org.jetbrains.compose.resources.*
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CoupleProfileScreen(coupleId: Long?, onBack: () -> Unit, vm: CoupleProfileViewModel = koinViewModel(), initialPage: String? = null, planId: Long? = null, onOpenCouple: ((Long) -> Unit)? = null) {
    val state by vm.state.collectAsState()
    var profileMenu by remember { mutableStateOf(false) }
    DisposableEffect(coupleId, initialPage, planId) { vm.enter(coupleId, initialPage, planId); onDispose { vm.leave() } }
    val back = { if (vm.back()) onBack() }
    BackHandler(onBack = back)
    MaterialTheme(typography = SriSuPartnerLinkTypography()) {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(
                title = { Text(pageTitle(state.page, state.profile?.canEdit == true), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.cp_back)) } },
                actions = {
                    if (state.draft != null && state.page != ProfilePage.SHARING) TextButton(onClick = { vm.save() }, enabled = !state.saving && state.draft?.dirty==true) { Text(stringResource(if (state.saving) Res.string.cp_saving else Res.string.cp_save)) }
                    else if (state.page == ProfilePage.PROFILE) {
                        if(state.profile?.canEdit == true) {
                            TextButton(onClick = { vm.open(ProfilePage.PREVIEW) }) { Text(stringResource(Res.string.cp_view_profile)) }
                            IconButton(onClick = { profileMenu = true }) { Icon(Icons.Default.Edit, stringResource(Res.string.cp_edit)) }
                            DropdownMenu(expanded=profileMenu,onDismissRequest={profileMenu=false}) {
                                listOf(ProfilePage.COVER to Res.string.cp_cover,ProfilePage.DATE to Res.string.cp_date,ProfilePage.SHARING to Res.string.cp_sharing).forEach { (page,label) -> DropdownMenuItem(text={Text(stringResource(label))},onClick={profileMenu=false;vm.open(page)}) }
                                DropdownMenuItem(text={Text(stringResource(Res.string.cp_refresh))},onClick={profileMenu=false;vm.refresh()})
                            }
                        } else IconButton(onClick = { vm.refresh() }, enabled = !state.loading) { Icon(Icons.Default.Refresh, stringResource(Res.string.cp_refresh)) }
                    }
                }
            ) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(MaterialTheme.spacing.medium)) {
                            Text(if (error.kind == NetworkAPIResult.ErrorType.CONFLICT) stringResource(Res.string.cp_conflict) else error.message)
                            if (error.fields.isNotEmpty()) Text(stringResource(Res.string.cp_field_error) + " " + error.fields.keys.joinToString())
                            TextButton(onClick = { vm.refresh() }) { Text(stringResource(Res.string.cp_retry)) }
                            if (error.kind == NetworkAPIResult.ErrorType.CONFLICT) TextButton(onClick = vm::reloadDraft, enabled = !state.loading && !state.saving) { Text(stringResource(Res.string.cp_reload_draft)) }
                        }
                    }
                }
                val profile = state.profile
                if (profile == null && !state.loading && state.page != ProfilePage.DISCOVER) Text(stringResource(Res.string.cp_no_profile))
                if(state.page == ProfilePage.DISCOVER) {
                    if(state.discovery.isEmpty() && !state.loading && state.error == null) Text(stringResource(Res.string.cp_empty))
                    state.discovery.forEach { card ->
                        OutlinedCard(onClick={onOpenCouple?.invoke(card.couple.id)},enabled=onOpenCouple!=null,modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(MaterialTheme.spacing.medium)) {
                                Text(card.preview.title?.takeIf(String::isNotBlank) ?: stringResource(Res.string.cp_shared_moment),style=MaterialTheme.typography.titleMedium)
                                card.preview.caption?.takeIf(String::isNotBlank)?.let { Text(it,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                                Text(stringResource(Res.string.cp_view_profile),style=MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if(state.discoveryCursor!=null) TextButton(onClick={vm.discover(true)},enabled=!state.loading){Text(stringResource(Res.string.cp_more))}
                }
                if (profile != null) when (state.page) {
                    ProfilePage.DISCOVER -> Unit
                    ProfilePage.PREVIEW -> ProfileOverview(state, vm, preview = true)
                    ProfilePage.PROFILE, ProfilePage.ANSWER -> ProfileOverview(state, vm)
                    ProfilePage.STORY -> StoryRead(state, vm)
                    ProfilePage.STORY_EDIT -> StoryEditor(state, vm)
                    ProfilePage.SONG -> SongEditor(state, vm)
                    ProfilePage.INTERESTS -> InterestsEditor(state, vm)
                    ProfilePage.COVER, ProfilePage.POSITION -> CoverEditor(state, vm)
                    ProfilePage.DATE -> {
                        Text(stringResource(Res.string.cp_date_hint))
                        ProfileDateFields(state, vm, plan = false)
                        SaveButton(state, vm)
                    }
                    ProfilePage.SHARING -> SharingEditor(state, vm)
                    ProfilePage.PLANS, ProfilePage.PLAN -> PlansScreen(state, vm)
                    ProfilePage.NEW_PLAN -> {
                        DraftField(state, vm, "title", Res.string.cp_plan_what, 120)
                        ProfileDateFields(state, vm, plan = true)
                        Text(stringResource(Res.string.cp_plan_hint), style = MaterialTheme.typography.bodySmall)
                        SaveButton(state, vm, Res.string.cp_plan_create)
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
            }
        }
        if(state.page == ProfilePage.ANSWER && state.draft != null) ModalBottomSheet(onDismissRequest = { vm.back() }) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) { StoryEditor(state,vm) }
        }
        if (state.discardPrompt) AlertDialog(onDismissRequest = vm::keepEditing,
            title = { Text(stringResource(Res.string.cp_discard_title)) }, text = { Text(stringResource(Res.string.cp_discard_body)) },
            confirmButton = { TextButton(onClick = vm::discard) { Text(stringResource(Res.string.cp_discard)) } },
            dismissButton = { TextButton(onClick = vm::keepEditing) { Text(stringResource(Res.string.cp_keep_editing)) } })
    }
}

@Composable private fun pageTitle(page: ProfilePage, owner: Boolean): String = stringResource(when(page) {
    ProfilePage.PREVIEW -> Res.string.cp_view_profile
    ProfilePage.DISCOVER -> Res.string.cp_explore
    ProfilePage.PROFILE -> if (owner) Res.string.cp_profile else Res.string.cp_visitor
    ProfilePage.STORY, ProfilePage.STORY_EDIT, ProfilePage.ANSWER -> Res.string.cp_story
    ProfilePage.SONG -> Res.string.cp_song
    ProfilePage.INTERESTS -> Res.string.cp_interests_edit
    ProfilePage.COVER -> Res.string.cp_cover
    ProfilePage.POSITION -> Res.string.cp_position
    ProfilePage.DATE -> Res.string.cp_date
    ProfilePage.SHARING -> Res.string.cp_sharing
    ProfilePage.PLANS, ProfilePage.PLAN -> Res.string.cp_plans
    ProfilePage.NEW_PLAN -> Res.string.cp_new_plan
})

@Composable private fun ProtectedImage(bytes: ByteArray?, description: String, modifier: Modifier, alignment: Alignment = Alignment.Center) {
    if (bytes != null) AsyncImage(model = ImageRequest.Builder(LocalPlatformContext.current).data(bytes).size(1024).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build(), contentDescription = description, modifier = modifier, contentScale = ContentScale.Crop, alignment = alignment)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, description, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable private fun ProfileHero(state: CoupleProfileState, vm: CoupleProfileViewModel, editable: Boolean) {
    val profile = state.profile ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.9f).clip(MaterialTheme.shapes.large)) {
            if (profile.cover?.url != null) ProtectedImage(state.images[profile.cover.url], stringResource(Res.string.cp_cover_description), Modifier.fillMaxSize(), BiasAlignment(0f, profile.cover.focalY * 2 - 1))
            else Image(painterResource(Res.drawable.onboarding_landscape), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth, alignment = Alignment.BottomCenter)
            if (editable) Surface(shape=MaterialTheme.shapes.pill,color=MaterialTheme.colorScheme.background.copy(alpha=.9f),modifier=Modifier.align(Alignment.TopCenter).padding(top=MaterialTheme.spacing.medium)) {
                TextButton(onClick={vm.open(ProfilePage.COVER)}) { Icon(Icons.Default.AddPhotoAlternate,null,Modifier.size(MaterialTheme.spacing.icon));Spacer(Modifier.width(MaterialTheme.spacing.tiny));Text(stringResource(if(profile.cover?.url==null)Res.string.cp_add_cover else Res.string.cp_change),style=MaterialTheme.typography.bodySmall) }
            }
        }
        Box(Modifier.fillMaxWidth().height(MaterialTheme.spacing.spacious)) {
            Row(horizontalArrangement = Arrangement.spacedBy(-MaterialTheme.spacing.compact), modifier = Modifier.align(Alignment.TopCenter).offset(y = -MaterialTheme.spacing.large).requiredHeight(MaterialTheme.spacing.huge)) {
                profile.members.orEmpty().forEach { member -> ProtectedImage(member.photoUrl?.let(state.images::get), stringResource(Res.string.cp_avatar_description), Modifier.size(MaterialTheme.spacing.huge).clip(CircleShape).border(MaterialTheme.spacing.tiny,MaterialTheme.colorScheme.background,CircleShape)) }
            }
        }
        profile.members?.let { Text(it.joinToString(" & ") { m -> m.name }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) }
        profile.anniversaryDate?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (editable) TextButton(onClick = { vm.open(ProfilePage.DATE) }) { Text(stringResource(Res.string.cp_date),style=MaterialTheme.typography.bodySmall) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ProfileOverview(state: CoupleProfileState, vm: CoupleProfileViewModel, preview: Boolean = false) {
    val profile = state.profile ?: return
    val editable = profile.canEdit && !preview
    if (profile.canEdit || profile.members != null || profile.cover != null) ProfileHero(state, vm, editable)
    if (!profile.canEdit && profile.publishedSections.isEmpty()) Text(stringResource(Res.string.cp_private))
    if (profile.canFave) SriSuButton(stringResource(if (profile.isFaved) Res.string.cp_unfave else Res.string.cp_fave), vm::fave, enabled = !state.saving, variant = SriSuButtonVariant.Outline)
    Surface(shape = MaterialTheme.shapes.medium, color = CoupleProfileTokens.sectionSurface(MaterialTheme.colorScheme)) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), horizontalArrangement = Arrangement.SpaceEvenly) {
            profile.daysTogether?.let { Metric(it.toString(), Res.string.cp_days, Icons.Default.Favorite) }
            Metric(profile.visibleMomentCount.toString(), Res.string.cp_moments, Icons.Default.AutoAwesome)
            profile.daysToAnniversary?.let { Metric(it.toString(), Res.string.cp_anniversary_countdown, Icons.Default.CalendarMonth) } ?: profile.plansDone?.let { Metric(it.toString(), Res.string.cp_done, Icons.Default.CalendarMonth) }
        }
    }
    if (profile.story != null || profile.canEdit) {
        SectionHeader(Res.string.cp_story, if(preview) null else if (profile.story.isNullOrEmpty()) Res.string.cp_write else Res.string.cp_read) { vm.open(if (profile.story.isNullOrEmpty() && profile.canEdit) ProfilePage.STORY_EDIT else ProfilePage.STORY) }
        if (profile.story.isNullOrEmpty()) SectionCard { Text(stringResource(Res.string.cp_story_hint)); if (editable) TextButton(onClick = { vm.open(ProfilePage.STORY_EDIT) }) { Text(stringResource(Res.string.cp_write)) } }
        else SectionCard { StoryAnswers(profile, vm.accountId, vm::openAnswer, editable) }
    }
    if (profile.canEdit || "song" in profile.publishedSections) {
        SectionHeader(Res.string.cp_song, if (editable) if (profile.song == null) Res.string.cp_pick else Res.string.cp_change else null) { vm.open(ProfilePage.SONG) }
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                Icon(Icons.Default.MusicNote, null)
                Column {
                    Text(profile.song?.title ?: stringResource(Res.string.cp_song_empty), style = MaterialTheme.typography.titleSmall)
                    Text(profile.song?.artist?.takeIf(String::isNotBlank) ?: stringResource(Res.string.cp_song_hint), style = MaterialTheme.typography.bodySmall)
                    profile.song?.note?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (profile.interests != null) {
        SectionHeader(Res.string.cp_interests, if (editable) Res.string.cp_edit else null) { vm.open(ProfilePage.INTERESTS) }
        if (profile.interests.shared.isEmpty()) SectionCard { Text(stringResource(Res.string.cp_interests_empty)); Text(stringResource(Res.string.cp_interests_hint),style = MaterialTheme.typography.bodySmall) }
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) { profile.interests.shared.forEach { SuggestionChip(onClick = { if (editable) vm.open(ProfilePage.INTERESTS) }, label = { Text(it) }, enabled = editable) } }
        if(profile.canEdit) {
            InterestSelections(Res.string.cp_your_interests, profile.interests.mine.orEmpty())
            InterestSelections(Res.string.cp_partner_likes, profile.interests.partner.orEmpty())
        }
    }
    if (profile.canEdit) {
        SectionHeader(Res.string.cp_together)
        if(preview) SectionCard { Text(stringResource(Res.string.cp_plans)); Text(stringResource(Res.string.cp_plans_summary, profile.plansDone ?: 0, profile.plansUpcoming ?: 0)) }
        else OutlinedButton(onClick = { vm.open(ProfilePage.PLANS) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth,null); Spacer(Modifier.width(MaterialTheme.spacing.small)); Column { Text(stringResource(Res.string.cp_plans)); Text(stringResource(Res.string.cp_plans_summary, profile.plansDone ?: 0, profile.plansUpcoming ?: 0),style=MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null) }
        if(editable) TextButton(onClick = { vm.open(ProfilePage.SHARING) }) { Icon(Icons.Default.Lock,null); Spacer(Modifier.width(MaterialTheme.spacing.small)); Text(stringResource(Res.string.cp_sharing)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun InterestSelections(label: StringResource, names: List<String>) {
    Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
    if(names.isEmpty()) Text(stringResource(Res.string.cp_no_selections), style = MaterialTheme.typography.bodySmall)
    else FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        names.forEach { name -> Surface(shape = MaterialTheme.shapes.small, color = CoupleProfileTokens.sectionSurface(MaterialTheme.colorScheme)) {
            Text(name, Modifier.padding(MaterialTheme.spacing.small), style = MaterialTheme.typography.bodyMedium)
        } }
    }
}
@Composable private fun Metric(value: String, label: StringResource, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 110.dp)) { Icon(icon,null,Modifier.size(MaterialTheme.spacing.icon),tint = if(icon==Icons.Default.CalendarMonth) IntroductionTokens.plans else if(icon==Icons.Default.AutoAwesome) IntroductionTokens.moments else IntroductionTokens.memories); Text(value,style=MaterialTheme.typography.titleMedium); Text(stringResource(label),style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center) }
}
@Composable private fun SectionHeader(title: StringResource, action: StringResource? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(title),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f)); if(action!=null) TextButton(onClick=onClick) { Text(stringResource(action),style=MaterialTheme.typography.bodySmall) } }
}
@Composable private fun SectionCard(content: @Composable ColumnScope.() -> Unit) { Surface(shape=MaterialTheme.shapes.large,color=CoupleProfileTokens.sectionSurface(MaterialTheme.colorScheme)) { Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small),content=content) } }
@Composable private fun promptTitle(prompt: String) = stringResource(when(prompt) { "how_met" -> Res.string.cp_how_met; "first_move" -> Res.string.cp_first_move; else -> Res.string.cp_first_impression })
@Composable private fun StoryAnswers(profile: CoupleProfile, account: Long?, onEdit: (String) -> Unit, editable: Boolean = profile.canEdit) {
    CoupleProfileViewModel.PROMPTS.forEach { prompt ->
        Text(promptTitle(prompt),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val answers=profile.story.orEmpty().filter { it.prompt==prompt }
        if(answers.isEmpty()) Text(stringResource(Res.string.cp_partner_side),style=MaterialTheme.typography.bodySmall)
        answers.forEach { answer -> Row(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) { Icon(Icons.Default.Person,null,Modifier.size(MaterialTheme.spacing.icon)); Column { Text(answer.answer,style=MaterialTheme.typography.bodyMedium);if(editable && answer.authorId==account) TextButton(onClick={onEdit(prompt)}) { Text(stringResource(Res.string.cp_edit)) }; profile.members?.firstOrNull { it.id==answer.authorId }?.let { Text(it.name,style=MaterialTheme.typography.bodySmall) } } } }
    }
}
@Composable private fun sectionLabel(section: String) = stringResource(when(section) {
    "story" -> Res.string.cp_story
    "song" -> Res.string.cp_song
    "interests" -> Res.string.cp_interests
    "cover" -> Res.string.cp_cover
    "date" -> Res.string.cp_date
    "sharing" -> Res.string.cp_sharing
    "plans" -> Res.string.cp_plans
    else -> Res.string.cp_profile
})
@Composable private fun StoryRead(state: CoupleProfileState, vm: CoupleProfileViewModel) {
    state.profile?.let { profile ->
        if(profile.canEdit) SriSuButton(stringResource(Res.string.cp_edit),{vm.open(ProfilePage.STORY_EDIT)},variant=SriSuButtonVariant.Outline)
        SectionCard { StoryAnswers(profile,vm.accountId,vm::openAnswer) }
        if(profile.canEdit) {
            SectionHeader(Res.string.cp_history)
            state.history.forEach { change -> SectionCard { Text(profile.members.orEmpty().firstOrNull { it.id==change.actorId }?.name.orEmpty(),style=MaterialTheme.typography.titleSmall); Text(stringResource(Res.string.cp_history_updated, sectionLabel(change.section)),style=MaterialTheme.typography.bodySmall); Text(localPlanTime(change.createdAt),style=MaterialTheme.typography.bodySmall) } }
            if(state.historyBefore!=null) TextButton(onClick={vm.history(true)},enabled=!state.listLoading) { Text(stringResource(Res.string.cp_more)) }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StoryEditor(state: CoupleProfileState, vm: CoupleProfileViewModel) {
    var removal by remember { mutableStateOf<String?>(null) }
    CoupleProfileViewModel.PROMPTS.filter { it in state.draft?.values.orEmpty() }.forEach { prompt ->
        Text(promptTitle(prompt),style=MaterialTheme.typography.titleMedium)
        if(prompt=="how_met") FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) { listOf(Res.string.cp_at_party,Res.string.cp_through_friends,Res.string.cp_at_work,Res.string.cp_online).forEach { resource -> val text=stringResource(resource); SuggestionChip(onClick={vm.edit(prompt,text)},label={Text(text)},enabled=!state.saving) } }
        if(prompt=="first_move") FlowRow { (state.profile?.members.orEmpty().map { it.name }+stringResource(Res.string.cp_both)).forEach { text -> SuggestionChip(onClick={vm.edit(prompt,text)},label={Text(text)},enabled=!state.saving) } }
        val partnerAnswer=state.profile?.story?.firstOrNull { it.prompt==prompt && it.authorId!=vm.accountId }
        partnerAnswer?.let { SectionCard { Text(it.answer); TextButton(onClick={vm.edit(prompt,it.answer)},enabled=!state.saving) { Text(stringResource(Res.string.cp_agree)) } } }
        DraftField(state,vm,prompt,Res.string.cp_in_words,240)
        TextButton(onClick={vm.invite(prompt)},enabled=!state.saving && prompt !in state.inviteSent) { Text(stringResource(if(prompt in state.inviteSent) Res.string.cp_invited else Res.string.cp_ask_partner)) }
        if(!state.draft?.values?.get(prompt).isNullOrBlank()) TextButton(onClick={removal=prompt},enabled=!state.saving) { Text(stringResource(Res.string.cp_answer_off),color=MaterialTheme.colorScheme.error) }
    }
    SaveButton(state,vm)
    removal?.let { prompt -> AlertDialog(onDismissRequest={removal=null},title={Text(stringResource(Res.string.cp_answer_off))},text={Text(stringResource(Res.string.cp_answer_confirm))},confirmButton={TextButton(onClick={vm.edit(prompt,""); removal=null; if(state.page==ProfilePage.ANSWER) vm.save()}) { Text(stringResource(Res.string.cp_remove)) }},dismissButton={TextButton(onClick={removal=null}) { Text(stringResource(Res.string.cp_keep)) }}) }
}
@Composable private fun SongEditor(state: CoupleProfileState, vm: CoupleProfileViewModel) {
    var remove by remember { mutableStateOf(false) }
    Text(stringResource(Res.string.cp_song_hint))
    DraftField(state,vm,"title",Res.string.cp_song_title,120)
    DraftField(state,vm,"artist",Res.string.cp_artist,120)
    DraftField(state,vm,"band",Res.string.cp_band,120)
    DraftField(state,vm,"note",Res.string.cp_note,240)
    SaveButton(state,vm)
    if(state.profile?.song!=null) TextButton(onClick={remove=true},enabled=!state.saving) { Text(stringResource(Res.string.cp_song_remove),color=MaterialTheme.colorScheme.error) }
    if(remove) AlertDialog(onDismissRequest={remove=false},text={Text(stringResource(Res.string.cp_remove_song_confirm))},confirmButton={TextButton(onClick={remove=false;vm.save(remove=true)}){Text(stringResource(Res.string.cp_remove))}},dismissButton={TextButton(onClick={remove=false}){Text(stringResource(Res.string.cp_cancel))}})
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun InterestsEditor(state: CoupleProfileState, vm: CoupleProfileViewModel) {
    var query by remember { mutableStateOf("") }
    val selected=state.draft?.values?.get("names").orEmpty().split('\n').filter(String::isNotBlank)
    OutlinedTextField(query,{query=it},label={Text(stringResource(Res.string.cp_search))},modifier=Modifier.fillMaxWidth(),enabled=!state.saving)
    if(query.isNotBlank()) TextButton(onClick={vm.toggle("names",query.trim());query=""},enabled=!state.saving && selected.size<20 && unicodeLength(query.trim())<=100) { Icon(Icons.Default.Add,null);Text(stringResource(Res.string.cp_add)) }
    Text("${stringResource(Res.string.cp_selected)}: ${selected.size} / 20",style=MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) { selected.forEach { FilterChip(selected=true,onClick={vm.toggle("names",it)},label={Text(it)},enabled=!state.saving) } }
    SectionHeader(Res.string.cp_partner_likes)
    state.profile?.interests?.partner.orEmpty().filter { it !in selected }.forEach { name -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(name,Modifier.weight(1f));TextButton(onClick={vm.toggle("names",name)},enabled=!state.saving && selected.size<20) { Text(stringResource(Res.string.cp_me_too)) } } }
    state.catalogue.forEach { (category, choices) ->
        val visible = choices.filter { it.contains(query,true) }
        if(visible.isNotEmpty()) {
            if(category.isNotBlank()) Text(category,style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) { visible.forEach { name -> FilterChip(selected=name in selected,onClick={vm.toggle("names",name)},label={Text(name)},enabled=!state.saving && (name in selected || selected.size<20)) } }
        }
    }
    SaveButton(state,vm)
}
@Composable private fun DraftField(state: CoupleProfileState, vm: CoupleProfileViewModel, key: String, label: StringResource, limit: Int) {
    val value=state.draft?.values?.get(key).orEmpty()
    val invalid=unicodeLength(value)>limit || state.error?.fields?.keys?.any { it==key || it.endsWith(".$key") }==true
    OutlinedTextField(value,{vm.edit(key,it)},label={Text(stringResource(label))},enabled=!state.saving,modifier=Modifier.fillMaxWidth(),isError=invalid,supportingText=if(invalid || unicodeLength(value)>limit*0.8) { {if(invalid) Text(stringResource(Res.string.cp_invalid)) else Text("${unicodeLength(value)} / $limit")} } else null)
}
@Composable private fun SaveButton(state: CoupleProfileState,vm: CoupleProfileViewModel,label: StringResource=Res.string.cp_save) { SriSuButton(stringResource(if(state.saving) Res.string.cp_saving else label),{vm.save()},enabled=!state.saving && state.draft?.dirty==true,modifier=Modifier.fillMaxWidth()) }

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CoverEditor(state: CoupleProfileState,vm: CoupleProfileViewModel) {
    val gallery=rememberGalleryManager(onResult={it?.firstOrNull()?.let(vm::choosePhoto)},mediaType=MediaType.IMAGE_ONLY,isMultiple=false)
    val camera=rememberCameraManager(vm::cameraPhoto,vm::cameraError)
    val draft=state.draft ?: return
    val bytes=draft.photo?.fileBytes ?: (draft.sourceUrl ?: state.profile?.cover?.url)?.let(state.images::get)
    ProtectedImage(bytes,stringResource(Res.string.cp_cover_description),Modifier.fillMaxWidth().aspectRatio(1.6f).clip(MaterialTheme.shapes.medium).pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); vm.position((vm.state.value.draft?.focalY ?: .5f) - amount.y / size.height.coerceAtLeast(1)) } },BiasAlignment(0f,draft.focalY*2-1))
    if(state.page==ProfilePage.POSITION) {
        Text(stringResource(Res.string.cp_position_hint))
        Slider(value=draft.focalY,onValueChange=vm::position,enabled=!state.saving)
        TextButton(onClick=vm::pickDifferentPhoto,enabled=!state.saving) { Text(stringResource(Res.string.cp_different_photo)) }
        SaveButton(state,vm)
    } else {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) { state.profile?.members.orEmpty().forEach { member -> ProtectedImage(member.photoUrl?.let(state.images::get),stringResource(Res.string.cp_avatar_description),Modifier.size(MaterialTheme.spacing.touchTarget).clip(CircleShape)) } }
        Text(state.profile?.members.orEmpty().joinToString(" & "){it.name},style=MaterialTheme.typography.headlineSmall,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
        SriSuButton(stringResource(Res.string.cp_camera),camera.launch,enabled=!state.saving && camera.available,variant=SriSuButtonVariant.Outline)
        SriSuButton(stringResource(Res.string.cp_library),gallery::launch,enabled=!state.saving,variant=SriSuButtonVariant.Outline)
        SectionHeader(Res.string.cp_moment_photos)
        Text(stringResource(Res.string.cp_moment_hint),style=MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) { state.covers.forEach { photo -> ProtectedImage(state.images[photo.url],stringResource(Res.string.cp_cover_description),Modifier.size(96.dp).clip(MaterialTheme.shapes.small).clickable(enabled=!state.saving){vm.chooseCover(photo)}) } }
        if(state.coversBefore!=null) TextButton(onClick={vm.covers(true)},enabled=!state.listLoading) {Text(stringResource(Res.string.cp_more))}
    }
}
@Composable private fun SharingEditor(state: CoupleProfileState,vm: CoupleProfileViewModel) {
    val selected=state.draft?.values?.get("sections").orEmpty().split('\n')
    Text(stringResource(Res.string.cp_sharing_hint))
    listOf("identity" to Res.string.cp_identity,"story" to Res.string.cp_story,"song" to Res.string.cp_song,"interests" to Res.string.cp_shared_interests,"cover" to Res.string.cp_cover,"date" to Res.string.cp_date).forEach { (key,label) -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Checkbox(key in selected,{vm.toggle("sections",key)},enabled=!state.saving);Text(stringResource(label),Modifier.weight(1f));if(key in state.profile?.publishedSections.orEmpty()) Text(stringResource(Res.string.cp_published),style=MaterialTheme.typography.bodySmall) } }
    val sharing=state.profile?.sharing
    Text(stringResource(when { sharing?.validProposal==true && sharing.approvedBy.size==2 -> Res.string.cp_published; sharing?.validProposal==true -> Res.string.cp_awaiting; sharing?.proposal.isNullOrEmpty() -> Res.string.cp_private; else -> Res.string.cp_proposal_changed }),style=MaterialTheme.typography.bodySmall)
    SriSuButton(stringResource(Res.string.cp_propose),{vm.save(sharingAction="propose")},enabled=!state.saving)
    if(sharing?.validProposal==true && sharing.proposal.isNotEmpty() && vm.accountId !in sharing.approvedBy) SriSuButton(stringResource(Res.string.cp_approve),{vm.save(sharingAction="approve")},enabled=!state.saving && state.draft?.dirty!=true)
    TextButton(onClick={vm.save(sharingAction="revoke")},enabled=!state.saving) { Text(stringResource(Res.string.cp_revoke),color=MaterialTheme.colorScheme.error) }
}
@Composable private fun PlansScreen(state: CoupleProfileState,vm: CoupleProfileViewModel) {
    SriSuButton(stringResource(Res.string.cp_new_plan),{vm.open(ProfilePage.NEW_PLAN)},variant=SriSuButtonVariant.Outline)
    Row(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) { FilterChip(!state.past,{vm.plans(false)},label={Text(stringResource(Res.string.cp_upcoming))});FilterChip(state.past,{vm.plans(true)},label={Text(stringResource(Res.string.cp_past))}) }
    if(state.listLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(state.plans.isEmpty() && !state.listLoading) Text(stringResource(Res.string.cp_empty))
    state.plans.forEach { plan -> SectionCard {
        Text(plan.title,style=MaterialTheme.typography.titleMedium)
        Text(localPlanTime(plan.startsAt),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(when {plan.completed -> Res.string.cp_done;plan.response=="yes" -> Res.string.cp_agreed;plan.response=="no" -> Res.string.cp_declined;plan.response=="another_time" -> Res.string.cp_reschedule;else -> Res.string.cp_pending}),style=MaterialTheme.typography.bodySmall)
        if(plan.responseNote.isNotBlank()) Text(plan.responseNote)
        if(!state.past && plan.createdBy!=vm.accountId) {
            Row { TextButton(onClick={vm.respond(plan,"yes")},enabled=!state.saving){Text(stringResource(Res.string.cp_yes))};TextButton(onClick={vm.respond(plan,"no")},enabled=!state.saving){Text(stringResource(Res.string.cp_no))};TextButton(onClick={vm.respond(plan,"another_time")},enabled=!state.saving){Text(stringResource(Res.string.cp_another_time))} }
        }
        if(state.past && plan.response=="yes" && !plan.completed) TextButton(onClick={vm.respond(plan,completed=true)},enabled=!state.saving) {Text(stringResource(Res.string.cp_mark_done))}
    } }
    if(state.plansBefore!=null) TextButton(onClick={vm.plans(more=true)},enabled=!state.listLoading){Text(stringResource(Res.string.cp_more))}
}
