@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,kotlin.time.ExperimentalTime::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant

/** Fetches the authoritative source; no content or preview is persisted in chat. */
@Composable fun SharedMomentScreen(id: Long,onBack: ()->Unit) {
    ProtectConversation()
    val api=org.koin.compose.koinInject<CoupleChatApi>()
    val profiles=org.koin.compose.koinInject<CoupleProfileRepository>()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var epoch by remember {mutableIntStateOf(0)}
    var moment by remember(id) {mutableStateOf<SharedMoment?>(null)}
    var images by remember(id) {mutableStateOf<Map<Long,ByteArray>>(emptyMap())}
    var loading by remember(id) {mutableStateOf(true)}
    var error by remember(id) {mutableStateOf<String?>(null)}
    LaunchedEffect(id,epoch,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                loading=true;error=null
                when(val result=api.moment(id).result) {
                    is NetworkAPIResult.Error -> error=if(result.failure.status in listOf(403,404))"This moment expired, was deleted, or is no longer shared with you." else "Connect to view this moment."
                    is NetworkAPIResult.Success -> result.response?.let {source ->
                        if(source.id!=id || Instant.parse(source.expiresAt)<=Clock.System.now()) {error="This moment has expired.";return@let}
                        moment=source
                        for(photo in source.photos.take(5)) {
                            if(Instant.parse(source.expiresAt)<=Clock.System.now())break
                            profiles.image(photo.image)?.let {images=images+(photo.id to it)}
                        }
                    }
                };loading=false
                moment?.let {source ->
                    delay((Instant.parse(source.expiresAt)-Clock.System.now()).inWholeMilliseconds.coerceAtLeast(0))
                    error="This moment has expired."
                }
            } catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(_:Exception) {error="This moment could not be displayed. Reconnect and try again."}
            finally {moment=null;images=emptyMap();loading=false}
        }
    }
    Scaffold(topBar={TopAppBar(title={Text("Moment")},navigationIcon={IconButton(onClick=onBack) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back to conversation")}})}) {padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.gutter),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            moment?.let {source ->
                source.title?.let {Text(it,style=MaterialTheme.typography.titleLarge)}
                Text(source.caption)
                source.photos.forEach {photo -> images[photo.id]?.let {PrivatePhoto(it,"Moment photo",Modifier.fillMaxWidth().height(320.dp),fit=true)} }
                Text("This is the original Moment, subject to its audience and expiry. Moment content is stored by SriSu’s Moments feature.",style=MaterialTheme.typography.bodySmall)
            }
            error?.let {Text(it);TextButton(onClick={epoch++}) {Text("Try again")}}
        }
    }
}
