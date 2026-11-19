package com.srisu.srisu.features.moments

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.Scaffold
import com.srisu.srisu.navigation.navhost.AppBottomBar
import com.srisu.srisu.navigation.navhost.BottomDestination
import com.srisu.srisu.features.home.entertainment.screen.HomeMomentsContent
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.*

/** Production composables, synthetic data. No fixture is available to the application. */
@Composable internal fun MomentRenderFixture(name:String) {
    val source: suspend (String)->ByteArray? = {MomentFixtures.image}
    when {
        name.startsWith("navigation")->Scaffold(bottomBar={AppBottomBar({},BottomDestination.HOME,true)}) {padding->
            Box(Modifier.padding(padding)) {MomentRenderFixture("home-active")}
        }
        name=="00-baseline-home"->BaselineHome()
        name.startsWith("home")->{
            var home=MomentFixtures.home
            if(name.contains("empty")) home=home.copy(couple=home.couple!!.copy(members=home.couple!!.members.map {it.copy(activeCount=0,unseenCount=0)}))
            if(name.contains("long")) home=home.copy(user=home.user.copy(name="Alexandria Nguyễn"))
            HomeMomentsContent(HomeMomentsState(home.user.name,home,fresh=!name.contains("offline"),error=if(name.contains("offline")) "Moments could not refresh. Check your connection." else null),source,{},{},{},{},{},{},{},{_,_->},hour=18)
        }
        name.startsWith("create")->{
            val empty=name.contains("select")
            var state by remember { mutableStateOf(MomentComposerState(record=MomentDraftRecord("synthetic",3,
                visibility=if(name.contains("audience")) "public" else "private",
                caption=if(empty) "" else "A quiet morning, and a little thought of you."),
                loading=false,photos=if(empty) emptyList() else listOf(LocalMomentPhoto("one",MomentFixtures.image),LocalMomentPhoto("two",MomentFixtures.image)),
                busy=name.contains("progress"),stage="Uploading photo 1 of 2",progress=if(name.contains("progress")) .35f else null,
                error=if(name.contains("failure")) "Upload interrupted. Your photos and caption are kept. Try again." else null)) }
            MomentComposerContent(state,source,
                onCaption={state=state.copy(record=state.record?.copy(caption=it))},
                onAudience={state=state.copy(record=state.record?.copy(visibility=it))},
                onCrop={id,crop->state=state.copy(record=state.record?.let { it.copy(crops=if(crop==null) it.crops-id else it.crops+(id to crop)) })},
                onRemove={id->state=state.copy(photos=state.photos.filterNot {it.id==id})},
                onReorder={id,offset->val photos=state.photos.toMutableList();val from=photos.indexOfFirst {it.id==id}
                    val to=from+offset;if(from>=0 && to in photos.indices) {photos.add(to,photos.removeAt(from));state=state.copy(photos=photos)}},
                onShare={},onRetry={},onDiscard={},onBack={},
                onPick={if(!name.contains("cancel")) state=state.copy(photos=state.photos+LocalMomentPhoto("picked",MomentFixtures.image))},
                initialCaptionEditing=name.contains("caption"))
        }
        else->{
            val mine=name.contains("creator")
            val public=name.contains("public")
            val moment=(if(mine) MomentFixtures.mine else MomentFixtures.partner).copy(
                visibility=if(public) "public" else "private", canReply=!mine && !public,
                caption=if(name.contains("long")) "One small part of today. ".repeat(12) else "The quiet part of our morning.")
            val state=MomentViewerState(moment=moment,bytes=MomentFixtures.image,loading=false,fresh=true,
                unavailable=if(name.contains("expired")) "expired" else if(name.contains("unavailable")) "unavailable" else null,
                captionDraft=if(name.contains("edit")) moment.caption else null,
                note=if(name.contains("typing")) "Such a lovely place. Thank you for sharing this." else "")
            MomentViewerContent(state,{},{},{},{},{},{},{},{},{},{},{})
        }
    }
}
