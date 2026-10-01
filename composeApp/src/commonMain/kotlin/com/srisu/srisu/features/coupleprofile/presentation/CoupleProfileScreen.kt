package com.srisu.srisu.features.coupleprofile.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    DisposableEffect(coupleId) { vm.enter(coupleId, initialPage, planId); onDispose { vm.leave() } }
    val back = { if (vm.back()) onBack() }
    val overviewScroll = rememberSaveable(vm.accountId, coupleId, saver = ScrollState.Saver) { ScrollState(0) }
    val accentMotion = rememberProfileAccentMotion(vm.accountId, state.profile?.id ?: coupleId)
    val editorScroll = key(state.page, vm.accountId, coupleId) { rememberScrollState() }
    val overview = state.page in setOf(ProfilePage.PROFILE, ProfilePage.PREVIEW, ProfilePage.ANSWER)
    BackHandler(onBack = back)
    MaterialTheme(typography = SriSuPartnerLinkTypography()) {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(
                expandedHeight = MaterialTheme.spacing.huge * LocalDensity.current.fontScale.coerceAtLeast(1f),
                title = { Text(pageTitle(state.page, state.profile?.canEdit == true), style = MaterialTheme.typography.titleMedium, maxLines = 2) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.cp_back)) } },
                actions = {
                    if (state.draft != null && state.page !in setOf(ProfilePage.SHARING, ProfilePage.ANSWER, ProfilePage.NEW_PLAN)) {
                        SaveButton(state, vm, modifier = Modifier.padding(end = MaterialTheme.spacing.small))
                    }
                    else if (state.page == ProfilePage.PROFILE) {
                        if(state.profile?.canEdit == true) {
                            IconButton(onClick = { vm.open(ProfilePage.PREVIEW) }) { Icon(Icons.Default.Visibility, stringResource(Res.string.cp_view_profile)) }
                            IconButton(onClick = { profileMenu = true }) { Icon(Icons.Default.MoreHoriz, stringResource(Res.string.cp_profile_options)) }
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
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(MaterialTheme.spacing.tiny)) {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(if (overview) overviewScroll else editorScroll)
                    .padding(horizontal = MaterialTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                if (state.page != ProfilePage.ANSWER) ProfileError(state, vm)
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
                    ProfilePage.PREVIEW -> ProfileOverview(state, vm, accentMotion, preview = true)
                    ProfilePage.PROFILE, ProfilePage.ANSWER -> ProfileOverview(state, vm, accentMotion)
                    ProfilePage.STORY -> StoryRead(state, vm)
                    ProfilePage.STORY_EDIT -> StoryEditor(state, vm)
                    ProfilePage.SONG -> SongEditor(state, vm)
                    ProfilePage.INTERESTS -> InterestsEditor(state, vm)
                    ProfilePage.COVER, ProfilePage.POSITION -> CoverEditor(state, vm)
                    ProfilePage.DATE -> {
                        EditorIntro(Res.string.cp_date_hint)
                        ProfileDateFields(state, vm, plan = false)
                    }
                    ProfilePage.SHARING -> SharingEditor(state, vm)
                    ProfilePage.PLANS, ProfilePage.PLAN -> PlansScreen(state, vm)
                    ProfilePage.NEW_PLAN -> {
                        DraftField(state, vm, "title", Res.string.cp_plan_what, 120)
                        ProfileDateFields(state, vm, plan = true)
                        Text(stringResource(Res.string.cp_plan_hint), style = MaterialTheme.typography.bodySmall)
                        SaveButton(state, vm, Modifier.fillMaxWidth(), Res.string.cp_plan_create)
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
                }
            }
        }
        if(state.page == ProfilePage.ANSWER && state.draft != null) ModalBottomSheet(onDismissRequest = { vm.back() }) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) { ProfileError(state, vm); StoryEditor(state,vm) }
        }
        if (state.discardPrompt) AlertDialog(onDismissRequest = vm::keepEditing,
            title = { Text(stringResource(Res.string.cp_discard_title)) }, text = { Text(stringResource(Res.string.cp_discard_body)) },
            confirmButton = { TextButton(onClick = vm::discard) { Text(stringResource(Res.string.cp_discard)) } },
            dismissButton = { TextButton(onClick = vm::keepEditing) { Text(stringResource(Res.string.cp_keep_editing)) } })
    }
}

@Composable private fun ProfileError(state: CoupleProfileState, vm: CoupleProfileViewModel) {
                state.error?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        Column(Modifier.padding(MaterialTheme.spacing.medium)) {
                            Text(if (error.kind == NetworkAPIResult.ErrorType.CONFLICT) stringResource(Res.string.cp_conflict) else error.message)
                            if (error.fields.isNotEmpty()) Text(stringResource(Res.string.cp_field_error) + " " + error.fields.keys.joinToString())
                            TextButton(onClick = { vm.refresh() }) { Text(stringResource(Res.string.cp_retry)) }
                            if (error.kind == NetworkAPIResult.ErrorType.CONFLICT) TextButton(onClick = vm::reloadDraft, enabled = !state.loading && !state.saving) { Text(stringResource(Res.string.cp_reload_draft)) }
                        }
                    }
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
        Column(Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.small), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
            Text(promptTitle(prompt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val choices = when (prompt) {
                "how_met" -> listOf(Res.string.cp_at_party, Res.string.cp_through_friends, Res.string.cp_at_work, Res.string.cp_online).map { stringResource(it) }
                "first_move" -> state.profile?.members.orEmpty().map { it.name } + stringResource(Res.string.cp_both)
                else -> emptyList()
            }
            if (choices.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                choices.forEach { text -> FilterChip(selected = state.draft?.values?.get(prompt) == text,
                    onClick = { vm.edit(prompt, text) }, label = { Text(text, style = MaterialTheme.typography.bodyMedium) },
                    shape = MaterialTheme.shapes.pill, enabled = !state.saving) }
            }
            state.profile?.story?.firstOrNull { it.prompt == prompt && it.authorId != vm.accountId }?.let {
                SectionCard { Text(it.answer, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.edit(prompt, it.answer) }, enabled = !state.saving) { Text(stringResource(Res.string.cp_agree)) } }
            }
            DraftField(state, vm, prompt, Res.string.cp_in_words, 240)
            TextButton(onClick = { vm.invite(prompt) }, enabled = !state.saving && prompt !in state.inviteSent) {
                Text(stringResource(if (prompt in state.inviteSent) Res.string.cp_invited else Res.string.cp_ask_partner), style = MaterialTheme.typography.labelMedium)
            }
            if (!state.draft?.values?.get(prompt).isNullOrBlank()) TextButton(onClick = { removal = prompt }, enabled = !state.saving) {
                Text(stringResource(Res.string.cp_answer_off), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (state.page == ProfilePage.ANSWER) SaveButton(state, vm, modifier = Modifier.fillMaxWidth())
    removal?.let { prompt -> AlertDialog(onDismissRequest = { removal = null }, title = { Text(stringResource(Res.string.cp_answer_off)) },
        text = { Text(stringResource(Res.string.cp_answer_confirm)) },
        confirmButton = { TextButton(onClick = { vm.edit(prompt, ""); removal = null; if (state.page == ProfilePage.ANSWER) vm.save() }) { Text(stringResource(Res.string.cp_remove)) } },
        dismissButton = { TextButton(onClick = { removal = null }) { Text(stringResource(Res.string.cp_keep)) } }) }
}
@Composable private fun SongEditor(state: CoupleProfileState, vm: CoupleProfileViewModel) {
    var remove by remember { mutableStateOf(false) }
    EditorIntro(Res.string.cp_song_hint)
    SectionHeader(Res.string.cp_song_details)
    DraftField(state,vm,"title",Res.string.cp_song_title,120)
    DraftField(state,vm,"artist",Res.string.cp_artist,120)
    DraftField(state,vm,"band",Res.string.cp_band,120)
    SectionHeader(Res.string.cp_note)
    DraftField(state,vm,"note",Res.string.cp_note_hint,240)
    if(state.profile?.song!=null) TextButton(onClick={remove=true},enabled=!state.saving) { Text(stringResource(Res.string.cp_song_remove),color=MaterialTheme.colorScheme.error) }
    if(remove) AlertDialog(onDismissRequest={remove=false},text={Text(stringResource(Res.string.cp_remove_song_confirm))},confirmButton={TextButton(onClick={remove=false;vm.save(remove=true)}){Text(stringResource(Res.string.cp_remove))}},dismissButton={TextButton(onClick={remove=false}){Text(stringResource(Res.string.cp_cancel))}})
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun InterestsEditor(state: CoupleProfileState, vm: CoupleProfileViewModel) {
    var query by remember { mutableStateOf("") }
    val selected=state.draft?.values?.get("names").orEmpty().split('\n').filter(String::isNotBlank)
    OutlinedTextField(query,{query=it},label={Text(stringResource(Res.string.cp_search))},leadingIcon={Icon(Icons.Default.Search,null)},shape=MaterialTheme.shapes.field,singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!state.saving)
    if(query.isNotBlank()) TextButton(onClick={vm.toggle("names",query.trim());query=""},enabled=!state.saving && query.isNotBlank() && selected.size<20 && unicodeLength(query.trim())<=100) { Icon(Icons.Default.Add,null);Text(stringResource(Res.string.cp_add)) }
    Text("${stringResource(Res.string.cp_selected)}: ${selected.size} / 20",style=MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small), verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.tiny)) { selected.forEach { FilterChip(selected=true,onClick={vm.toggle("names",it)},label={Text(it,style=MaterialTheme.typography.bodyMedium)},shape=MaterialTheme.shapes.pill,enabled=!state.saving) } }
    if (state.profile?.interests?.partner.orEmpty().any { it !in selected }) SectionHeader(Res.string.cp_partner_likes)
    state.profile?.interests?.partner.orEmpty().filter { it !in selected }.forEach { name -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(name,Modifier.weight(1f));TextButton(onClick={vm.toggle("names",name)},enabled=!state.saving && selected.size<20) { Text(stringResource(Res.string.cp_me_too)) } } }
    state.catalogue.forEach { (category, choices) ->
        val visible = choices.filter { it.contains(query,true) }
        if(visible.isNotEmpty()) {
            if(category.isNotBlank()) Text(category,style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small), verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.tiny)) { visible.forEach { name -> FilterChip(selected=name in selected,onClick={vm.toggle("names",name)},label={Text(name,style=MaterialTheme.typography.bodyMedium)},shape=MaterialTheme.shapes.pill,enabled=!state.saving && (name in selected || selected.size<20)) } }
        }
    }
}
@Composable private fun DraftField(state: CoupleProfileState, vm: CoupleProfileViewModel, key: String, label: StringResource, limit: Int) {
    val value = state.draft?.values?.get(key).orEmpty()
    val invalid = unicodeLength(value) > limit || state.error?.fields?.keys?.any { it == key || it.endsWith(".$key") } == true
    val longForm = limit > 120
    OutlinedTextField(
        value, { vm.edit(key, it) }, label = { Text(stringResource(label)) },
        enabled = !state.saving, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.field,
        textStyle = MaterialTheme.typography.bodyLarge, singleLine = !longForm, minLines = if (longForm) 3 else 1,
        isError = invalid,
        supportingText = if (invalid || unicodeLength(value) > limit * .8) { {
            Text(if (invalid) stringResource(Res.string.cp_invalid) else "${unicodeLength(value)} / $limit",
                Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } } else null,
    )
}
@Composable private fun SaveButton(state: CoupleProfileState, vm: CoupleProfileViewModel, modifier: Modifier = Modifier, label: StringResource = Res.string.cp_save) {
    SriSuButton(stringResource(if (state.saving) Res.string.cp_saving else label), { vm.save() },
        enabled = !state.saving && state.draft?.dirty == true, modifier = modifier,
        trailingIcon = if (state.saving) { { CircularProgressIndicator(Modifier.size(MaterialTheme.spacing.medium), strokeWidth = MaterialTheme.spacing.hairline) } } else null)
}
@Composable private fun EditorIntro(text: StringResource) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = MaterialTheme.spacing.small))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CoverEditor(state: CoupleProfileState,vm: CoupleProfileViewModel) {
    val gallery=rememberGalleryManager(onResult={it?.firstOrNull()?.let(vm::choosePhoto)},mediaType=MediaType.IMAGE_ONLY,isMultiple=false)
    val camera=rememberCameraManager(vm::cameraPhoto,vm::cameraError)
    val draft=state.draft ?: return
    val bytes=draft.photo?.fileBytes ?: (draft.sourceUrl ?: state.profile?.cover?.url)?.let(state.images::get)
    ProtectedImage(bytes,stringResource(Res.string.cp_cover_description),Modifier.fillMaxWidth().aspectRatio(2.2f).clip(MaterialTheme.shapes.large).pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); vm.position((vm.state.value.draft?.focalY ?: .5f) - amount.y / size.height.coerceAtLeast(1)) } },BiasAlignment(0f,draft.focalY*2-1))
    if(state.page==ProfilePage.POSITION) {
        EditorIntro(Res.string.cp_position_hint)
        val positionLabel = stringResource(Res.string.cp_position)
        Slider(value=draft.focalY,onValueChange=vm::position,enabled=!state.saving,modifier=Modifier.semantics { contentDescription = positionLabel })
        TextButton(onClick=vm::pickDifferentPhoto,enabled=!state.saving) { Text(stringResource(Res.string.cp_different_photo)) }
    } else {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) { state.profile?.members.orEmpty().forEach { member -> ProfileAvatar(member, state.images, Modifier.size(MaterialTheme.spacing.touchTarget)) } }
        Text(state.profile?.members.orEmpty().joinToString(" & "){it.name},style=MaterialTheme.typography.headlineSmall,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        SriSuButton(stringResource(Res.string.cp_camera),camera.launch,enabled=!state.saving && camera.available,variant=SriSuButtonVariant.Outline)
        SriSuButton(stringResource(Res.string.cp_library),gallery::launch,enabled=!state.saving,variant=SriSuButtonVariant.Outline)
        }
        SectionHeader(Res.string.cp_moment_photos)
        Text(stringResource(Res.string.cp_moment_hint),style=MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) { state.covers.forEach { photo -> ProtectedImage(state.images[photo.url],stringResource(Res.string.cp_cover_description),Modifier.size(96.dp).clip(MaterialTheme.shapes.small).clickable(enabled=!state.saving){vm.chooseCover(photo)}) } }
        if(state.covers.isEmpty() && !state.listLoading) Text(stringResource(Res.string.cp_no_cover_choices),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.coversBefore!=null) TextButton(onClick={vm.covers(true)},enabled=!state.listLoading) {Text(stringResource(Res.string.cp_more))}
    }
}
@Composable private fun SharingEditor(state: CoupleProfileState,vm: CoupleProfileViewModel) {
    val selected=state.draft?.values?.get("sections").orEmpty().split('\n')
    EditorIntro(Res.string.cp_sharing_hint)
    Column(Modifier.fillMaxWidth()) {
        listOf("identity" to Res.string.cp_identity, "story" to Res.string.cp_story, "song" to Res.string.cp_song,
            "interests" to Res.string.cp_shared_interests, "cover" to Res.string.cp_cover, "date" to Res.string.cp_date).forEach { (key, label) ->
            Row(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.spacing.touchTarget)
                .toggleable(value = key in selected, enabled = !state.saving, role = Role.Checkbox) { vm.toggle("sections", key) }
                .padding(vertical = MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
                Checkbox(key in selected, onCheckedChange = null, enabled = !state.saving)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    if (key in state.profile?.publishedSections.orEmpty()) Text(stringResource(Res.string.cp_published),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val sharing=state.profile?.sharing
    Text(stringResource(when { sharing?.validProposal==true && sharing.approvedBy.size==2 -> Res.string.cp_published; sharing?.validProposal==true -> Res.string.cp_awaiting; sharing?.proposal.isNullOrEmpty() -> Res.string.cp_private_owner; else -> Res.string.cp_proposal_changed }),style=MaterialTheme.typography.bodySmall)
    SriSuButton(stringResource(Res.string.cp_propose),{vm.save(sharingAction="propose")},enabled=!state.saving,modifier=Modifier.fillMaxWidth())
    if(sharing?.validProposal==true && sharing.proposal.isNotEmpty() && vm.accountId !in sharing.approvedBy) SriSuButton(stringResource(Res.string.cp_approve),{vm.save(sharingAction="approve")},enabled=!state.saving && state.draft?.dirty!=true,modifier=Modifier.fillMaxWidth())
    TextButton(onClick={vm.save(sharingAction="revoke")},enabled=!state.saving) { Text(stringResource(Res.string.cp_revoke),color=MaterialTheme.colorScheme.error) }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PlansScreen(state: CoupleProfileState,vm: CoupleProfileViewModel) {
    SriSuButton(stringResource(Res.string.cp_new_plan),{vm.open(ProfilePage.NEW_PLAN)},variant=SriSuButtonVariant.Outline)
    Row(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) { FilterChip(!state.past,{vm.plans(false)},label={Text(stringResource(Res.string.cp_upcoming))});FilterChip(state.past,{vm.plans(true)},label={Text(stringResource(Res.string.cp_past))}) }
    if(state.listLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(state.plans.isEmpty() && !state.listLoading) SectionCard { Icon(Icons.Default.CalendarMonth,null); Text(stringResource(Res.string.cp_empty),style=MaterialTheme.typography.bodyMedium) }
    state.plans.forEach { plan -> SectionCard {
        Text(plan.title,style=MaterialTheme.typography.titleMedium)
        Text(localPlanTime(plan.startsAt),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(when {plan.completed -> Res.string.cp_done;plan.response=="yes" -> Res.string.cp_agreed;plan.response=="no" -> Res.string.cp_declined;plan.response=="another_time" -> Res.string.cp_reschedule;else -> Res.string.cp_pending}),style=MaterialTheme.typography.bodySmall)
        if(plan.responseNote.isNotBlank()) Text(plan.responseNote)
        if(!state.past && plan.createdBy!=vm.accountId) {
            FlowRow { TextButton(onClick={vm.respond(plan,"yes")},enabled=!state.saving){Text(stringResource(Res.string.cp_yes))};TextButton(onClick={vm.respond(plan,"no")},enabled=!state.saving){Text(stringResource(Res.string.cp_no))};TextButton(onClick={vm.respond(plan,"another_time")},enabled=!state.saving){Text(stringResource(Res.string.cp_another_time))} }
        }
        if(state.past && plan.response=="yes" && !plan.completed) TextButton(onClick={vm.respond(plan,completed=true)},enabled=!state.saving) {Text(stringResource(Res.string.cp_mark_done))}
    } }
    if(state.plansBefore!=null) TextButton(onClick={vm.plans(more=true)},enabled=!state.listLoading){Text(stringResource(Res.string.cp_more))}
}
