package com.srisu.srisu.features.moments.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.features.couplechat.presentation.rememberNativeChatMedia
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable fun MomentComposerScreen(couple: Long,onBack: ()->Unit,onPublished: (Long)->Unit,
    vm: MomentComposerViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val native=rememberNativeChatMedia()
    val scope=rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(couple) { vm.open(couple) }
    DisposableEffect(native) { onDispose { native.close() } }
    LaunchedEffect(state.publishedId,state.discarded) { state.publishedId?.let(onPublished); if(state.discarded) onBack() }
    MomentComposerContent(state,vm::image,vm::caption,vm::audience,vm::remove,vm::reorder,vm::share,vm::restore,vm::discard,onBack,
        picking=picking,onPick={source->
            if(!picking) {
                picking=true
                val stamp=vm.pickerStamp()
                if(source=="photo") native.pickPhotos(vm.remainingPhotoSlots()) { batch ->
                    scope.launch { picking=false; vm.pickedPhotos(batch,stamp) }
                } else native.pick(source) { raw,bytes -> scope.launch { picking=false; vm.picked(raw,bytes,stamp) } }
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable internal fun MomentComposerContent(state: MomentComposerState, image: suspend (String)->ByteArray?,
    onCaption: (String)->Unit,onAudience: (String)->Unit,onRemove: (String)->Unit,onReorder: (String,Int)->Unit,
    onShare: ()->Unit,onRetry: ()->Unit,onDiscard: ()->Unit,onBack: ()->Unit,onPick: (String)->Unit,
    picking: Boolean=false, initialAudienceSheet: Boolean=false) {
    val space=MaterialTheme.spacing
    val frozen=state.busy || picking || state.record?.publication!=null
    var audience by rememberSaveable { mutableStateOf(initialAudienceSheet) }
    var leave by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val back={ if(state.photos.isNotEmpty() || state.record?.caption?.isNotBlank()==true || state.busy) leave=true else onBack() }
    BackHandler(onBack=back)
    Scaffold(modifier=Modifier.fillMaxSize().imePadding(),topBar={
        TopAppBar(title={Text("New Moment")},navigationIcon={ IconButton(onClick=back) { Icon(Icons.Outlined.Close,"Close Moment") } })
    },bottomBar={
        Surface(tonalElevation=0.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(space.gutter),verticalArrangement=Arrangement.spacedBy(space.small)) {
                if(state.busy) {
                    Text(state.stage,style=MaterialTheme.typography.bodyMedium)
                    if(state.progress==null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth())
                }
                SriSuButton(if(state.record?.publication!=null && !state.busy) "Check publication" else "Continue to sharing",
                    onClick={if(state.record?.publication!=null) onShare() else audience=true},modifier=Modifier.fillMaxWidth(),
                    enabled=!state.loading && !state.busy && !picking && state.photos.isNotEmpty())
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=space.gutter),
            verticalArrangement=Arrangement.spacedBy(space.medium)) {
            if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { MomentError(it,if(!state.busy) { {if(state.record?.publication!=null) onShare() else onRetry()} } else null) }
            if(state.error!=null && !state.busy) TextButton(onClick=onDiscard) {Text("Discard this draft")}
            if(state.photos.isEmpty()) {
                Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(space.extraLarge),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(space.medium)) {
                        Icon(Icons.Outlined.AddPhotoAlternate,null,Modifier.size(space.huge),tint=MaterialTheme.colorScheme.secondary)
                        Text("What would you like to share?",style=MaterialTheme.typography.titleLarge)
                        Text("Choose up to five photos. Add a few words if you like.",style=MaterialTheme.typography.bodyLarge)
                        SriSuButton("Choose photos",{onPick("photo")},enabled=!picking && !state.loading)
                        OutlinedButton(onClick={onPick("camera")},enabled=!picking && !state.loading) { Icon(Icons.Outlined.PhotoCamera,null); Spacer(Modifier.width(space.small)); Text("Take a photo") }
                    }
                }
            } else {
                val photo=state.photos[selected.coerceIn(state.photos.indices)]
                Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    if(photo.bytes!=null) MomentImage(photo.bytes,"Selected photo ${selected+1}",Modifier.fillMaxSize(),ContentScale.Fit)
                    else GuardedMomentImage(photo.remote?.image,image,"Selected photo ${selected+1}",Modifier.fillMaxSize(),scale=ContentScale.Fit)
                }
                LazyRow(horizontalArrangement=Arrangement.spacedBy(space.small)) {
                    itemsIndexed(state.photos,key={_,p->p.id}) { index,p ->
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Box(Modifier.size(64.dp).border(if(index==selected) 2.dp else 0.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.medium)
                                .padding(3.dp).clip(MaterialTheme.shapes.medium).clickable { selected=index }) {
                                if(p.bytes!=null) MomentImage(p.bytes,"Select photo ${index+1}",Modifier.fillMaxSize())
                                else GuardedMomentImage(p.remote?.image,image,"Select photo ${index+1}",Modifier.fillMaxSize())
                            }

                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={onReorder(photo.id,-1); selected=(selected-1).coerceAtLeast(0)},enabled=!frozen && selected>0) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Move photo earlier") }
                    Text("${selected+1} of ${state.photos.size}",style=MaterialTheme.typography.bodyMedium)
                    IconButton(onClick={onRemove(photo.id);selected=0},enabled=!frozen) {Icon(Icons.Outlined.DeleteOutline,"Remove selected photo")}
                    IconButton(onClick={onReorder(photo.id,1); selected=(selected+1).coerceAtMost(state.photos.lastIndex)},enabled=!frozen && selected<state.photos.lastIndex) { Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Move photo later") }
                }
                if(state.photos.size<5) FlowRow(horizontalArrangement=Arrangement.spacedBy(space.small),verticalArrangement=Arrangement.spacedBy(space.small)) {
                    OutlinedButton(onClick={onPick("photo")},enabled=!frozen && !picking) { Icon(Icons.Outlined.AddPhotoAlternate,null); Spacer(Modifier.width(space.small)); Text("Add photos") }
                    OutlinedButton(onClick={onPick("camera")},enabled=!frozen && !picking) { Icon(Icons.Outlined.PhotoCamera,null); Spacer(Modifier.width(space.small)); Text("Camera") }
                }
            }
            if(picking) Text("Opening your photos…",style=MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value=state.record?.caption.orEmpty(),onValueChange=onCaption,
                modifier=Modifier.fillMaxWidth(),label={Text("Add a caption")},placeholder={Text("A little thought to go with your day…")},
                enabled=!frozen && !state.loading,minLines=2,maxLines=5,supportingText={Text("${state.record?.caption?.length ?: 0}/1000")})
            Text("Your photos stay private until you share. An interrupted upload can be retried here.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(space.medium))
        }
    }
    if(audience) ModalBottomSheet(onDismissRequest={if(!state.busy) audience=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=space.gutter).navigationBarsPadding().padding(bottom=space.large),verticalArrangement=Arrangement.spacedBy(space.medium)) {
            Text("Share to",style=MaterialTheme.typography.titleLarge)
            AudienceRow("Just you two","Only you and your current partner",Icons.Outlined.People,state.record?.visibility!="public",!frozen) {onAudience("private")}
            AudienceRow("Public","People signed in to SriSu can see this Moment",Icons.Outlined.Public,state.record?.visibility=="public",!frozen) {onAudience("public")}
            Row(horizontalArrangement=Arrangement.spacedBy(space.small)) { Icon(Icons.Outlined.Schedule,null,Modifier.size(space.icon)); Text("Visible for 24 hours after sharing",style=MaterialTheme.typography.bodyMedium) }
            if(state.record?.visibility=="public") Text("This photo and its caption will appear in discovery. Replies from other people remain private appreciation notes.",style=MaterialTheme.typography.bodyMedium)
            SriSuButton("Share Moment",{audience=false;onShare()},Modifier.fillMaxWidth(),enabled=!state.busy && !state.loading)
        }
    }
    if(leave) AlertDialog(onDismissRequest={leave=false},title={Text("Keep your Moment?")},
        text={Text(if(state.busy) "Uploading or publishing is still in progress. Stay here until it finishes." else "Uploaded photos and your caption can be recovered. Photos that have not uploaded stay only while this screen is open.")},
        confirmButton={TextButton(onClick={leave=false;if(!state.busy) onBack()}) { Text(if(state.busy) "Stay here" else "Keep draft and leave") }},
        dismissButton={if(!state.busy) TextButton(onClick={leave=false;onDiscard()}) {Text("Discard draft")}})
}

@Composable private fun AudienceRow(title: String,detail: String,icon: androidx.compose.ui.graphics.vector.ImageVector,selected: Boolean,enabled: Boolean,onClick: ()->Unit) {
    Surface(onClick=onClick,enabled=enabled,shape=MaterialTheme.shapes.medium,
        color=if(selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
            Icon(icon,null); Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium); Text(detail,style=MaterialTheme.typography.bodyMedium) }
            RadioButton(selected,onClick=null,enabled=enabled)
        }
    }
}
