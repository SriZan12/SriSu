package com.srisu.srisu.features.moments.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import com.srisu.srisu.theme.spacing

@Composable internal fun MomentImage(bytes: ByteArray?, description: String?, modifier: Modifier=Modifier,
    scale: ContentScale=ContentScale.Crop, onPresented: ()->Unit = {}, onFailed: ()->Unit = {}) {
    val context=LocalPlatformContext.current
    var loaded by remember(bytes) { mutableStateOf(false) }
    var failed by remember(bytes) { mutableStateOf(false) }
    if(bytes!=null) Box(modifier.semantics {stateDescription=if(loaded) "Photo ready" else if(failed) "Photo unavailable" else "Photo loading"}) {
        AsyncImage(model=ImageRequest.Builder(context).data(bytes)
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build(),
            contentDescription=description, modifier=Modifier.matchParentSize(),contentScale=scale,
            onSuccess={loaded=true;failed=false;onPresented()},onError={failed=true;onFailed()})
        if(failed) Icon(Icons.Outlined.BrokenImage,"Photo could not be decoded",Modifier.align(Alignment.Center))
    }
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer),contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.BrokenImage,description,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun GuardedMomentImage(url: String?, load: suspend (String)->ByteArray?, description: String?,
    modifier: Modifier=Modifier, avatar: Boolean=false, scale: ContentScale=ContentScale.Crop) {
    val bytes by produceState<ByteArray?>(null,url) { value=null; if(url!=null) value=load(url) }
    if(avatar && bytes==null) Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.Person,description,tint=MaterialTheme.colorScheme.onSecondaryContainer)
    } else MomentImage(bytes,description,modifier,scale)
}

@Composable internal fun MomentLifecycle(onActive: (Boolean)->Unit) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val action by rememberUpdatedState(onActive)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_RESUME->action(true)
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP->action(false)
            else->Unit
        } }
        lifecycle.addObserver(observer); action(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); action(false) }
    }
}

@Composable internal fun MomentError(message: String, retry: (() -> Unit)?=null) {
    Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium)) {
            Text(message,color=MaterialTheme.colorScheme.onErrorContainer,style=MaterialTheme.typography.bodyMedium)
            if(retry!=null) TextButton(onClick=retry) { Text("Try again") }
        }
    }
}
