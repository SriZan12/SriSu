package com.srisu.srisu.features.explore.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.NavigationBackButton
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.moments.presentation.GuardedMomentImage
import com.srisu.srisu.features.moments.presentation.MomentError
import com.srisu.srisu.theme.*

@Composable internal fun ExploreLibrary(state:ExploreState,onBack:()->Unit,onManage:(Boolean)->Unit,onFollowers:(Boolean)->Unit,
    onLibrary:(Boolean)->Unit,onLoadFollowers:(Boolean)->Unit,onSelect:(String)->Unit,onProfile:(Long)->Unit,onMoment:(Long)->Unit,
    onFave:(Long,Boolean)->Unit,onRetry:(Long)->Unit,loadImage:suspend(String)->ByteArray?,
    libraryScroll:LazyListState=rememberLazyListState(),followersScroll:LazyListState=rememberLazyListState()) {
    val space=MaterialTheme.spacing;val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        NavigationBackButton(onBack)
        Text("Faves",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        IconButton(onClick={onFollowers(false);onManage(false);onSelect("global")}) {Icon(Icons.Outlined.Search,"Find couples")}
    }
    Row(Modifier.padding(horizontal=space.gutter)) {
        ExploreTab("Your Faves"+(state.savedTotal?.let {" · $it"} ?: ""),!state.followersOpen,{onFollowers(false);onManage(true)},Modifier.weight(1f))
        ExploreTab("Followers",state.followersOpen,{onFollowers(true)},Modifier.weight(1f))
    }
    LazyColumn(Modifier.fillMaxSize(),state=if(state.followersOpen) followersScroll else libraryScroll,
        contentPadding=PaddingValues(space.gutter),verticalArrangement=Arrangement.spacedBy(space.medium)) {
        item("privacy") {
            Row(horizontalArrangement=Arrangement.spacedBy(space.small),verticalAlignment=Alignment.Top) {
                Icon(if(state.followersOpen) Icons.Outlined.PeopleOutline else Icons.Outlined.Lock,null,Modifier.size(space.medium))
                Text(if(state.followersOpen) "People who chose to follow your couple. Only you two can see this list."
                    else "Saved just for you. Faving a couple doesn’t notify them or follow them publicly.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            }
        }
        if(state.followersOpen) {
            val followers=state.followers
            if(followers.loading) item("loading") {LinearProgressIndicator(Modifier.fillMaxWidth())}
            followers.error?.let {item("error") {MomentError(it,{onLoadFollowers(false)})}}
            if(followers.loaded && followers.rows.isEmpty()) item("empty") {
                Text("No followers yet",style=ExploreTokens.editorial())
                Text("Public follows appear here when someone chooses to share their identity with you.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
            }
            val visible=state.active && followers.loaded
            items(followers.rows,key={it.userId}) {person->
                LibraryIdentity(visible) {key(visible,state.imageRevision) {
                Row(Modifier.fillMaxWidth().clickable(enabled=visible,role=Role.Button,onClickLabel="Open follower’s couple profile") {onProfile(person.coupleId)}.padding(vertical=space.small),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.compact)) {
                    ExploreAvatar(person.avatarUrl,{url->if(visible) loadImage(url) else null},null,Modifier.size(ExploreTokens.avatar).clip(CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(person.displayName,style=MaterialTheme.typography.titleSmall)
                        Text("Following your couple",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward,null)
                }
                }}
            }
            if(followers.next!=null) item("more") {TextButton(onClick={onLoadFollowers(true)},enabled=!followers.loading) {Text("More followers")}}
        } else {
            val library=state.library
            val saved=library.rows.filter {state.preferences[it.coupleId]?.confirmed!=false}
            if(library.loading) item("loading") {LinearProgressIndicator(Modifier.fillMaxWidth())}
            library.error?.let {item("error") {MomentError(it,{onLibrary(false)})}}
            if(library.loaded && saved.isEmpty()) item("empty") {Text("No couples saved yet",style=ExploreTokens.editorial())}
            val visible=state.active && library.loaded
            items(saved,key={it.coupleId}) {row->
                LibraryIdentity(visible) {key(visible,state.imageRevision) {
                Column {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                        Row(Modifier.weight(1f).clickable(enabled=visible && row.available,role=Role.Button,onClickLabel="Open saved couple profile") {onProfile(row.coupleId)},
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                            CoupleAvatars(row.couple ?: ExploreCouple(row.coupleId),{url->if(visible) loadImage(url) else null})
                            Column(Modifier.weight(1f)) {
                                Text(if(row.available) row.couple?.label ?: "A SriSu couple" else "Couple unavailable",style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(if(!row.available) "You can remove this Fave" else if(row.hasActiveContent) "Public Moments available" else "No active Moments",
                                    style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            }
                        }
                        FaveControl(row.coupleId,true,state.preferences[row.coupleId],{id,value->if(visible) onFave(id,value)},{id->if(visible) onRetry(id)})
                    }
                    if(row.available && row.hasActiveContent) TextButton(onClick={onMoment(row.coupleId)},enabled=visible) {Text("View Moments",style=MaterialTheme.typography.bodySmall)}
                }
                }}
            }
            if(library.next!=null) item("more") {TextButton(onClick={onLibrary(true)},enabled=!library.loading) {Text("More saved couples")}}
        }
        item("discover") {
            Surface(onClick={onFollowers(false);onManage(false);onSelect("global")},shape=MaterialTheme.shapes.large,color=ExploreTokens.saved(colors)) {
                Row(Modifier.fillMaxWidth().padding(space.medium),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.compact)) {
                    Icon(Icons.Outlined.Public,null)
                    Column(Modifier.weight(1f)) {
                        Text("Find couples in Discover",style=MaterialTheme.typography.titleSmall)
                        Text("Couples to fave and their public Moments",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward,null)
                }
            }
        }
    }
}

/** Retain measured row heights and stable keys during reauthorization without
 * exposing cached identity text, pixels, accessibility actions or image requests. */
@Composable private fun LibraryIdentity(visible:Boolean,content:@Composable ()->Unit) {
    Column(if(visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}) {content()}
}

@Composable internal fun PublicFollowActions(couple:ExploreCouple,status:PublicFollowState?,onFollow:(Boolean)->Unit,onRetry:()->Unit) {
    val space=MaterialTheme.spacing;val snapshot=status?.snapshot
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(space.gutter),verticalArrangement=Arrangement.spacedBy(space.compact)) {
        Text(couple.label,style=MaterialTheme.typography.titleMedium)
        Text("Following is separate from your private Faves.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            status==null || status.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            status.error!=null -> MomentError(status.error,onRetry)
            snapshot?.isFollowing==true -> OutlinedButton(onClick={onFollow(false)},enabled=!status.saving) {Text(if(status.saving) "Updating…" else "Unfollow")}
            snapshot?.canFollow==true -> Button(onClick={onFollow(true)},enabled=!status.saving) {Text(if(status.saving) "Updating…" else "Follow publicly")}
            else -> Text(when(snapshot?.reason) {
                "own_couple"->"This is your couple."
                "connect_partner","not_connected","connection_required"->"Connect with your partner before following publicly."
                "approve_public_identity","identity_not_public","public_identity_required","identity_required"->"Both partners must approve sharing your couple’s identity before you can follow publicly."
                else->"Public following isn’t available for this couple right now. You can still keep private Faves."
            },style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun PublicFollowConfirmation(name:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Follow $name?")},
        text={Text("They’ll be able to see your name and profile photo in their followers list. This is your individual choice and doesn’t follow on your partner’s behalf. Your existing Faves stay private.",modifier=Modifier.verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(onClick=onConfirm) {Text("Share identity & follow")}},dismissButton={TextButton(onClick=onDismiss) {Text("Cancel")}})
}
