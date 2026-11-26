package com.srisu.srisu.features.explore.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.NavigationBackButton
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.moments.data.MomentPreview
import com.srisu.srisu.features.moments.presentation.GuardedMomentImage
import com.srisu.srisu.features.moments.presentation.MomentError
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.features.moments.presentation.ageLabel
import com.srisu.srisu.features.moments.presentation.minutesBetween
import com.srisu.srisu.theme.*
import org.koin.compose.viewmodel.koinViewModel

@Composable fun ExploreScreen(onMoment: (Long)->Unit,onProfile: (Long)->Unit,onPreferences: ()->Unit,
    onBack: ()->Unit, vm: ExploreViewModel=koinViewModel(), onOpenMoment: (Long,Long)->Unit={couple,_->onMoment(couple)},
    onYouTwo: (() -> Unit)?=null) {
    val state by vm.state.collectAsStateWithLifecycle()
    MomentLifecycle(vm::active)
    LaunchedEffect(vm) { vm.loadInterests() }
    ExploreContent(state,vm::select,vm::refresh,vm::more,vm::broader,vm::manage,vm::library,
        vm::fave,vm::retryFave,onMoment,onProfile,onPreferences,vm::image,onBack=onBack,
        onQuery=vm::setQuery,onInterest=vm::selectInterest,onOpenMoment=onOpenMoment,onYouTwo=onYouTwo,
        onFollowers=vm::showFollowers,onLoadFollowers=vm::loadFollowers,
        onRetryInterests={vm.loadInterests(force=true)},onLoadFollow=vm::loadFollow,onFollow={id,desired->vm.follow(id,desired,shareIdentity=desired)})
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable internal fun ExploreContent(state: ExploreState,onSelect: (String)->Unit,onRefresh: ()->Unit,
    onMore: ()->Unit,onBroader: ()->Unit,onManage: (Boolean)->Unit,onLibrary: (Boolean)->Unit,
    onFave: (Long,Boolean)->Unit,onRetryFave: (Long)->Unit,onMoment: (Long)->Unit,onProfile: (Long)->Unit,
    onPreferences: ()->Unit,loadImage: suspend (String)->ByteArray?,
    globalScroll: LazyListState=rememberLazyListState(),favesScroll: LazyListState=rememberLazyListState(),onBack: (() -> Unit)?=null,
    onQuery: (String)->Unit={},onInterest: (Int?)->Unit={},onOpenMoment: (Long,Long)->Unit={couple,_->onMoment(couple)},
    onYouTwo: (() -> Unit)?=null,onFollowers: (Boolean)->Unit={},onLoadFollowers: (Boolean)->Unit={},
    onRetryInterests: ()->Unit={},onLoadFollow: (Long)->Unit={},onFollow: (Long,Boolean)->Unit={_,_->}) {
    val colors=MaterialTheme.colorScheme; val space=MaterialTheme.spacing; val feed=state.feed
    val rows=feed.cards.filter {state.section!="faves" || state.preferences[it.couple.id]?.confirmed!=false}
    var searching by rememberSaveable {mutableStateOf(false)}
    var publicToday by rememberSaveable {mutableStateOf(false)}
    var selectedMoment by rememberSaveable(state.section) {mutableStateOf<Long?>(null)}
    var optionsFor by remember {mutableStateOf<ExploreCouple?>(null)}
    var confirmFollow by remember {mutableStateOf<ExploreCouple?>(null)}
    val focus=LocalFocusManager.current
    val back: () -> Unit = { when {state.followersOpen->onFollowers(false);state.managing->onManage(false); searching->{searching=false;onQuery("");onInterest(null);focus.clearFocus()}; publicToday->publicToday=false; else->onBack?.invoke()}; Unit }
    BackHandler(state.managing || state.followersOpen || searching || publicToday) {back()}
    val authorized=feed.fresh && state.active
    LaunchedEffect(state.active,feed.fresh,rows.map {it.couple}) {
        if(!state.active || !feed.fresh) {optionsFor=null;confirmFollow=null}
        else {optionsFor=optionsFor?.let {selected->rows.firstOrNull {it.couple.id==selected.id}?.couple};
            confirmFollow=confirmFollow?.takeIf {selected->rows.any {it.couple==selected}}}
    }
    val viewing=state.section=="faves" || publicToday
    val previews=rows.flatMap {card->card.visiblePreviews.map {card to it}}
    val selected=previews.firstOrNull {it.second.id==selectedMoment} ?: previews.firstOrNull()
    Surface(color=ExploreTokens.canvas(colors),modifier=Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) { Column(Modifier.safeDrawingPadding().imePadding().widthIn(max=ExploreTokens.maxWidth).fillMaxSize()) {
            if(state.managing || state.followersOpen) {
                ExploreLibrary(state,back,onManage,onFollowers,onLibrary,onLoadFollowers,onSelect,onProfile,onMoment,onFave,onRetryFave,loadImage)
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal=space.small),verticalAlignment=Alignment.CenterVertically) {
                    if(onBack!=null || searching || publicToday) NavigationBackButton(back)
                    if(searching) ExploreSearch(state.query,onQuery,Modifier.weight(1f),true)
                    else if(publicToday) Text("Public today",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    else {
                        FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.Center) {
                            if(onYouTwo!=null) ExploreTab("You two",false,onYouTwo)
                            ExploreTab("Faves",state.section=="faves",{focus.clearFocus();onSelect("faves")})
                            ExploreTab("Discover",state.section=="global",{focus.clearFocus();onSelect("global")})
                        }
                        IconButton(onClick={onManage(true)}) {Icon(Icons.Outlined.PeopleOutline,"Your Faves and followers")}
                    }
                }
                PullToRefreshBox(isRefreshing=feed.refreshing,onRefresh=onRefresh,modifier=Modifier.weight(1f).fillMaxWidth().testTag("explore-pull-refresh")) {
                    LazyColumn(state=if(state.section=="global") globalScroll else favesScroll,
                        modifier=Modifier.fillMaxSize().testTag("explore-list"),contentPadding=PaddingValues(start=space.gutter,end=space.gutter,bottom=space.large),
                        verticalArrangement=Arrangement.spacedBy(space.small)) {
                        if(!viewing) {
                            if(!searching) item("find") {
                                Column(verticalArrangement=Arrangement.spacedBy(space.compact)) {
                                    Text("Find couples",style=ExploreTokens.editorial(),modifier=Modifier.semantics {heading()})
                                    Surface(onClick={searching=true},shape=MaterialTheme.shapes.pill,color=ExploreTokens.paper(colors),border=BorderStroke(1.dp,colors.outlineVariant)) {
                                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=space.medium),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.compact)) {
                                            Icon(Icons.Outlined.Search,null,Modifier.size(space.icon))
                                            Text("Names, or what they love",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            if(state.interestsLoading && state.interests.isEmpty()) item("interest-loading") {Text("Loading interests…",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                            state.interestsError?.let {item("interest-error") {TextButton(onClick=onRetryInterests) {Text("Retry interests",style=MaterialTheme.typography.bodySmall)}}}
                            if(state.interests.isNotEmpty()) item("interests") {
                                LazyRow(horizontalArrangement=Arrangement.spacedBy(space.small)) {
                                    items(state.interests,key={it.id}) {interest->
                                        FilterChip(selected=state.interestId==interest.id,onClick={onInterest(if(state.interestId==interest.id) null else interest.id)},
                                            label={Text(interest.label,style=MaterialTheme.typography.labelMedium)},shape=MaterialTheme.shapes.pill,colors=sriSuFilterChipColors())
                                    }
                                }
                            }
                        }
                        if(feed.loading && rows.isEmpty() || !feed.fresh && rows.isEmpty() && feed.error==null) item("loading") {ExploreSkeleton()}
                        feed.error?.let {item("error") {MomentError(it,onRefresh)}}
                        if(feed.fresh && !feed.loading && rows.isEmpty() && feed.error==null) item("empty") {
                            ExploreEmpty(state.section,feed.emptyReason,state.query.isNotBlank() || state.interestId!=null,{onSelect("global")},onRefresh,{onManage(true)},onBroader,{onQuery("");onInterest(null)})
                        }
                        if(viewing && selected!=null) {
                            item("moment") {
                                ProtectedExploreContent(authorized) {
                                    key(authorized,state.imageRevision,selected.second.id) {
                                        ExploreMomentPaper(selected.first,selected.second,{if(authorized) onOpenMoment(selected.first.couple.id,selected.second.id)},
                                            {url->if(authorized) loadImage(url) else null},serverTime=feed.serverTime)
                                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                                            TextButton(onClick={onProfile(selected.first.couple.id)},enabled=authorized,modifier=Modifier.weight(1f)) {
                                                Text("Their page",style=MaterialTheme.typography.labelMedium);Spacer(Modifier.width(space.small));Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.size(space.icon))
                                            }
                                            FaveControl(selected.first.couple.id,selected.first.isFaved,state.preferences[selected.first.couple.id],{id,value->if(authorized) onFave(id,value)},{id->if(authorized) onRetryFave(id)})
                                            IconButton(onClick={optionsFor=selected.first.couple;onLoadFollow(selected.first.couple.id)},enabled=authorized) {Icon(Icons.Outlined.MoreHoriz,"More couple actions")}
                                        }
                                    }
                                }
                            }
                            item("film-heading") {ExploreSection(if(state.section=="faves") "FROM YOUR FAVES" else "COUPLES TODAY",if(state.section=="faves") "Your Faves" else null,{onManage(true)})}
                            item("film") {
                                ProtectedExploreContent(authorized) {
                                    LazyRow(modifier=Modifier.testTag("explore-filmstrip"),horizontalArrangement=Arrangement.spacedBy(space.small)) {
                                        itemsIndexed(previews,key={_,item->item.second.id}) {index,(card,moment)->
                                            key(authorized,state.imageRevision) {Surface(onClick={if(authorized) selectedMoment=moment.id},enabled=authorized,shape=MaterialTheme.shapes.small,
                                                modifier=Modifier.semantics {
                                                    this.selected=moment.id==selected.second.id
                                                    contentDescription=momentLabel(card.couple,moment,index,previews.size)
                                                },
                                                border=if(moment.id==selected.second.id) BorderStroke(2.dp,colors.onSurface) else null) {
                                                ExplorePreview(moment,{url->if(authorized) loadImage(url) else null},Modifier.width(ExploreTokens.filmWidth).height(ExploreTokens.filmHeight),null)
                                            }}
                                        }
                                    }
                                }
                            }
                            if(state.section=="faves" && rows.none {it.hasUnopened}) item("seen") {Text("All caught up. Revisit a favourite.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                        } else if(!viewing && rows.isNotEmpty()) {
                            if(!searching && state.query.isBlank() && state.interestId==null) {
                                item("today-heading") {ExploreSection("PUBLIC TODAY","See all") {publicToday=true}}
                                item("today") {ProtectedExploreContent(authorized) {
                                    LazyRow(horizontalArrangement=Arrangement.spacedBy(space.small)) {
                                        items(previews.take(12),key={it.second.id}) {(card,moment)->
                                            key(authorized,state.imageRevision) {Surface(onClick={if(authorized) {selectedMoment=moment.id;publicToday=true}},enabled=authorized,
                                                color=ExploreTokens.paper(colors),shape=MaterialTheme.shapes.field) {
                                                Column(Modifier.width(ExploreTokens.thumbnailWidth).padding(space.tiny)) {
                                                    ExplorePreview(moment,{url->if(authorized) loadImage(url) else null},Modifier.fillMaxWidth().height(ExploreTokens.thumbnailHeight).clip(MaterialTheme.shapes.small),"Moment from ${card.couple.label}")
                                                    Text(moment.caption.ifBlank {card.couple.label},Modifier.padding(vertical=space.tiny),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                                                }
                                            }}
                                        }
                                    }
                                }}
                            }
                            item("couples-heading") {ExploreSection(if(searching || state.query.isNotBlank() || state.interestId!=null) "COUPLES" else "COUPLES FOR YOU")}
                            items(rows,key={"couple-${it.couple.id}"}) {card->
                                ProtectedExploreContent(authorized) {key(authorized,state.imageRevision) {
                                    ExploreCoupleCard(card,state.preferences[card.couple.id],{id,value->if(authorized) onFave(id,value)},{id->if(authorized) onRetryFave(id)},
                                        {id,moment->if(authorized) onOpenMoment(id,moment)}, {id->if(authorized) onProfile(id)},
                                        {url->if(authorized) loadImage(url) else null},compact=searching || state.query.isNotBlank() || state.interestId!=null)
                                }}
                            }
                        }
                        if(rows.isNotEmpty() && feed.fresh) item("continuation") {
                            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                                when {
                                    feed.cursorStale -> TextButton(onClick=onRefresh) {Text("Refresh for the latest couples")}
                                    feed.next!=null -> TextButton(onClick=onMore,enabled=!feed.loading) {Text(if(feed.loading) "Loading…" else "More couples")}
                                    else -> Text(if(feed.paginationAvailable) "You’re up to date" else "More results are temporarily unavailable",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                }
                                if(state.section=="global" && !viewing) TextButton(onClick=onPreferences) {Text("Your city & interests",style=MaterialTheme.typography.bodySmall)}
                            }
                        }
                    }
                }
            }
        }
    }
    }
    optionsFor?.let {couple->
        ModalBottomSheet(onDismissRequest={optionsFor=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            PublicFollowActions(couple,state.following[couple.id],onFollow={desired->if(desired) {optionsFor=null;confirmFollow=couple} else onFollow(couple.id,false)},onRetry={onLoadFollow(couple.id)})
        }
    }
    confirmFollow?.let {couple->PublicFollowConfirmation(couple.label,{confirmFollow=null},{onFollow(couple.id,true);confirmFollow=null;optionsFor=couple})}
}

@Composable internal fun ProtectedExploreContent(authorized:Boolean,content:@Composable ()->Unit) {
    Column(if(authorized) Modifier else Modifier.graphicsLayer {alpha=0f}.clearAndSetSemantics {}) {content()}
}

@Composable internal fun ExploreTab(label:String,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    Box(modifier.heightIn(min=48.dp).semantics {this.selected=selected}.clickable(role=Role.Tab,onClick=onClick),contentAlignment=Alignment.Center) {
        Surface(shape=MaterialTheme.shapes.pill,color=if(selected) colors.primary else ExploreTokens.paper(colors),contentColor=if(selected) colors.onPrimary else colors.onSurface) {
            Row(Modifier.padding(horizontal=MaterialTheme.spacing.compact,vertical=MaterialTheme.spacing.small),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                val icon=when(label) {"Faves"->Icons.Outlined.StarBorder;"Discover"->Icons.Outlined.Public;"You two"->Icons.Outlined.Lock;else->null}
                if(icon!=null) Icon(icon,null,Modifier.size(MaterialTheme.spacing.medium))
                Text(label,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun ExploreSearch(query:String,onQuery:(String)->Unit,modifier:Modifier=Modifier,focused:Boolean=false) {
    val focus=LocalFocusManager.current
    val requester=remember {FocusRequester()}
    LaunchedEffect(focused) {if(focused) requester.requestFocus()}
    OutlinedTextField(value=query,onValueChange=onQuery,modifier=modifier.focusRequester(requester).testTag("explore-search"),placeholder={Text("Names, or what they love",style=MaterialTheme.typography.bodyMedium)},singleLine=true,
        textStyle=MaterialTheme.typography.bodyMedium,shape=MaterialTheme.shapes.pill,
        leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={onQuery("")}) {Icon(Icons.Outlined.Close,"Clear search")}},
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus()}),
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=ExploreTokens.paper(MaterialTheme.colorScheme),focusedContainerColor=ExploreTokens.paper(MaterialTheme.colorScheme)))
}

@Composable internal fun CoupleAvatars(couple:ExploreCouple,loadImage:suspend(String)->ByteArray?,modifier:Modifier=Modifier) {
    val space=MaterialTheme.spacing
    Box(modifier.width(ExploreTokens.pairWidth).height(ExploreTokens.avatar)) {
        repeat(2) {index->
            ExploreAvatar(couple.avatars.getOrNull(index),loadImage,null,
                Modifier.size(ExploreTokens.avatar).align(if(index==0) Alignment.CenterStart else Alignment.CenterEnd)
                    .clip(CircleShape).border(2.dp,ExploreTokens.paper(MaterialTheme.colorScheme),CircleShape))
        }
    }
}

@Composable private fun ExploreCoupleCard(card:ExploreCard,status:FaveStatus?,onFave:(Long,Boolean)->Unit,onRetry:(Long)->Unit,
    onMoment:(Long,Long)->Unit,onProfile:(Long)->Unit,loadImage:suspend(String)->ByteArray?,compact:Boolean) {
    val colors=MaterialTheme.colorScheme;val space=MaterialTheme.spacing
    Surface(shape=MaterialTheme.shapes.large,color=if(compact) ExploreTokens.canvas(colors) else ExploreTokens.paper(colors)) {
        Column(Modifier.padding(if(compact) 0.dp else space.compact),verticalArrangement=Arrangement.spacedBy(space.compact)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                Row(Modifier.weight(1f).clickable(role=Role.Button,onClickLabel="Open couple profile") {onProfile(card.couple.id)},
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                    CoupleAvatars(card.couple,loadImage)
                    Column(Modifier.weight(1f)) {
                        Text(card.couple.label,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(card.couple.sharedInterests.take(2).joinToString(" · ").ifBlank {if(card.hasUnopened) "New public Moments" else "Public Moments"},
                            style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
                FaveControl(card.couple.id,card.isFaved,status,onFave,onRetry)
            }
            if(!compact) Row(horizontalArrangement=Arrangement.spacedBy(space.tiny)) {
                card.visiblePreviews.forEachIndexed {index,moment->
                    ExplorePreview(moment,loadImage,Modifier.weight(1f).aspectRatio(0.8f).clip(MaterialTheme.shapes.small)
                        .semantics {contentDescription=momentLabel(card.couple,moment,index,card.visiblePreviews.size)}
                        .clickable(role=Role.Button,onClickLabel="Open Moment from ${card.couple.label}") {onMoment(card.couple.id,moment.id)},null)
                }
                // Empty slots are not duplicated media. Available previews keep their intended proportions.
                repeat(3-card.visiblePreviews.size) {Spacer(Modifier.weight(1f))}
            }
        }
    }
}

private fun momentLabel(couple:ExploreCouple,moment:MomentPreview,index:Int,count:Int):String =
    "Moment ${index+1} of $count from ${couple.label}" + moment.caption.takeIf {it.isNotBlank()}?.let {": ${it.take(120)}"}.orEmpty()

@Composable internal fun ExplorePreview(moment:MomentPreview,loadImage:suspend(String)->ByteArray?,modifier:Modifier,description:String?) {
    val colors=MaterialTheme.colorScheme
    Box(modifier.background(colors.surfaceContainerHigh)) {
        if(moment.photos.isEmpty() && moment.posterUrl==null) {
            Column(Modifier.align(Alignment.Center).padding(MaterialTheme.spacing.small),horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.FormatQuote,description,Modifier.size(MaterialTheme.spacing.icon))
                Text(moment.caption.ifBlank {"A little moment"},style=MaterialTheme.typography.bodySmall,maxLines=4,overflow=TextOverflow.Ellipsis)
            }
        } else GuardedMomentImage(moment.posterUrl ?: moment.photos.firstOrNull()?.image,loadImage,description,Modifier.fillMaxSize())
        if(moment.mediaKind=="video" || moment.photos.any {it.video!=null}) Surface(color=colors.mediaBackground.copy(alpha=.72f),contentColor=colors.onMedia,
            shape=MaterialTheme.shapes.pill,modifier=Modifier.align(Alignment.BottomEnd).padding(MaterialTheme.spacing.tiny)) {
            Icon(Icons.Outlined.PlayArrow,"Video",Modifier.padding(MaterialTheme.spacing.tiny).size(MaterialTheme.spacing.icon))
        }
    }
}

@Composable internal fun ExploreMomentPaper(card:ExploreCard,moment:MomentPreview,onOpen:()->Unit,loadImage:suspend(String)->ByteArray?,serverTime:String?=null) {
    val colors=MaterialTheme.colorScheme; val space=MaterialTheme.spacing
    Box(Modifier.fillMaxWidth().padding(horizontal=space.compact,vertical=space.medium)) {
        Surface(Modifier.matchParentSize().graphicsLayer {rotationZ=3f},shape=MaterialTheme.shapes.small,color=colors.surfaceContainerHigh,shadowElevation=2.dp) {}
        Surface(Modifier.matchParentSize().graphicsLayer {rotationZ=-2f},shape=MaterialTheme.shapes.small,color=ExploreTokens.paper(colors),shadowElevation=2.dp) {}
        Surface(onClick=onOpen,modifier=Modifier.testTag("explore-selected-moment"),shape=MaterialTheme.shapes.small,color=ExploreTokens.paper(colors),shadowElevation=4.dp) {
            Column(Modifier.padding(space.small),verticalArrangement=Arrangement.spacedBy(space.small)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Public,null,Modifier.size(space.medium));Spacer(Modifier.width(space.tiny))
                    Text("PUBLIC",style=MaterialTheme.typography.bodySmall)
                }
                ExplorePreview(moment,loadImage,Modifier.fillMaxWidth().aspectRatio(.85f),"Open Moment from ${card.couple.label}")
                if(moment.caption.isNotBlank()) Text(moment.caption,style=ExploreTokens.editorial(),maxLines=3,overflow=TextOverflow.Ellipsis)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                    CoupleAvatars(card.couple,loadImage,Modifier.size(36.dp,24.dp))
                    Text(card.couple.label,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    moment.createdAt?.let {created->serverTime?.let {minutesBetween(created,it)}}?.let {minutes->
                        Text(ageLabel(minutes),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.OpenInFull,"Open Moment",Modifier.size(space.medium))
                }
            }
        }
    }
}

@Composable internal fun FaveControl(id:Long,fallback:Boolean,status:FaveStatus?,onFave:(Long,Boolean)->Unit,onRetry:(Long)->Unit) {
    val saved=status?.confirmed ?: fallback;val colors=MaterialTheme.colorScheme;val space=MaterialTheme.spacing
    Column(horizontalAlignment=Alignment.End) {
        FilledTonalButton(onClick={onFave(id,!saved)},enabled=status?.pending!=true,
            contentPadding=PaddingValues(horizontal=space.compact,vertical=space.small),
            colors=ButtonDefaults.filledTonalButtonColors(containerColor=if(saved) ExploreTokens.saved(colors) else colors.primary,contentColor=if(saved) colors.onSurface else colors.onPrimary),
            modifier=Modifier.semantics {contentDescription=if(saved) "Remove couple from your private Faves" else "Save couple to your private Faves"}) {
            Icon(if(saved) Icons.Filled.Star else Icons.Outlined.StarBorder,null,Modifier.size(space.medium),tint=if(saved) colors.reminder else LocalContentColor.current)
            Spacer(Modifier.width(space.tiny));Text(if(status?.pending==true) "Saving…" else if(saved) "Faved" else "Fave",style=MaterialTheme.typography.bodySmall)
        }
        if(status?.error!=null) TextButton(onClick={onRetry(id)}) {Text("Retry save",color=colors.error,style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable internal fun ExploreSection(title:String,action:String?=null,onAction:()->Unit={}) {
    Row(Modifier.fillMaxWidth().heightIn(min=32.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(action!=null) TextButton(onClick=onAction,contentPadding=PaddingValues(horizontal=MaterialTheme.spacing.small)) {
            Text(action,style=MaterialTheme.typography.bodySmall);Spacer(Modifier.width(MaterialTheme.spacing.tiny));Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.size(MaterialTheme.spacing.medium))
        }
    }
}

@Composable private fun ExploreSkeleton() {
    val colors=MaterialTheme.colorScheme;val space=MaterialTheme.spacing
    Column(Modifier.fillMaxWidth().semantics {contentDescription="Loading public couples"},verticalArrangement=Arrangement.spacedBy(space.medium)) {
        Row(horizontalArrangement=Arrangement.spacedBy(space.small)) {repeat(3) {Box(Modifier.weight(1f).height(150.dp).clip(MaterialTheme.shapes.field).background(colors.surfaceContainerHigh))}}
        repeat(2) {Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(ExploreTokens.paper(colors)).padding(space.compact),verticalArrangement=Arrangement.spacedBy(space.compact)) {
            Row(horizontalArrangement=Arrangement.spacedBy(space.compact)) {Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceContainerHigh));Box(Modifier.width(130.dp).height(16.dp).background(colors.surfaceContainerHigh))}
            Box(Modifier.fillMaxWidth().height(118.dp).clip(MaterialTheme.shapes.small).background(colors.surfaceContainerHigh))
        }}
    }
}

@Composable private fun ExploreEmpty(section:String,reason:String?,filtered:Boolean,onGlobal:()->Unit,onRefresh:()->Unit,onManage:()->Unit,onBroader:()->Unit,onClear:()->Unit) {
    val space=MaterialTheme.spacing
    Column(Modifier.fillMaxWidth().padding(vertical=space.section),verticalArrangement=Arrangement.spacedBy(space.compact)) {
        Icon(if(section=="faves") Icons.Outlined.StarBorder else Icons.Outlined.Public,null,Modifier.size(space.large))
        Text(when {filtered->"No couples found";section=="global"->"A quiet moment here";reason=="no_faves"->"Keep your favourites close";else->"Your Faves are still here"},style=ExploreTokens.editorial())
        Text(when {filtered->"Try another name or interest. Only details couples choose to share can be found."
            section=="global"->"There are no public Moments to explore right now."
            reason=="no_faves"->"Save couples you enjoy. Their public Moments will appear here, just for you."
            else->"No active public Moments right now. Your saved couples are still in Your Faves."},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(filtered) TextButton(onClick=onClear) {Text("Clear search")}
        else if(section=="faves") {Button(onClick=onGlobal) {Text("Find couples")};if(reason!="no_faves") TextButton(onClick=onManage) {Text("Your Faves")}}
        else Row {TextButton(onClick=onRefresh) {Text("Try again")};TextButton(onClick=onBroader) {Text("Explore more widely")}}
    }
}

@Composable internal fun ExploreAvatar(url:String?,loadImage:suspend(String)->ByteArray?,description:String?,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    MaterialTheme(colorScheme=colors.copy(secondaryContainer=colors.surfaceContainerHigh,onSecondaryContainer=colors.onSurfaceVariant)) {
        GuardedMomentImage(url,loadImage,description,modifier,avatar=true)
    }
}
