@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
package com.srisu.srisu.features.explore

import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.core.MemorySession
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.PublicFollowViewModel
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.MomentViewerViewModel
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URI
import java.time.Instant
import kotlin.test.*

/** Actual Ktor repositories and ViewModels against the disposable loopback server.
 * Run tools/core_integration.py --explore; normal offline unit runs skip this class. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ExploreTransportIntegrationTest {
    private class Account(fixture:JsonObject,id:String,token:String,environment:ApiEnvironment) {
        val sessions=SessionCoordinator(MemorySession()).apply {
            saveSession(ApiJson.encodeToString(Session(id=fixture.getValue(id).jsonPrimitive.long,
                access=fixture.getValue(token).jsonPrimitive.content)),SESSION_KEY)
        }
        val http:HttpClient=HttpClientFactory.create(sessions,environment,OkHttp.create {config {retryOnConnectionFailure(false)}})
        val profile=CoupleProfileRepository(http,environment,sessions)
        val explore=ExploreRepository(http,environment,profile)
        val moments=MomentRepository(http,environment,profile)
    }
    private fun <T> NetworkAPIResult<T?>.value():T=assertNotNull(assertIs<NetworkAPIResult.Success<T?>>(this).response)

    @Test fun realDiscoveryPrivateFavesPublicFollowingSelectedMomentAndWithdrawal() {
        val path=System.getenv("SRISU_CORE_INTEGRATION_FILE")
        assumeTrue("Requires a disposable loopback backend",path!=null)
        val fixture=ApiJson.parseToJsonElement(File(path!!).readText()).jsonObject
        assumeTrue("Requires --explore fixture",fixture.containsKey("explore_text_moment_id"))
        val origin=fixture.getValue("base_url").jsonPrimitive.content
        require(URI(origin).host=="127.0.0.1")
        val environment=ApiEnvironment(origin,development=true)
        val dispatcher=newSingleThreadContext("explore-http-main")
        Dispatchers.setMain(dispatcher)
        val mine=Account(fixture,"account_id","access",environment)
        val myPartner=Account(fixture,"partner_id","partner_access",environment)
        val owner=Account(fixture,"visitor_id","visitor_access",environment)
        val ownersPartner=Account(fixture,"visitor_partner_id","visitor_partner_access",environment)
        val store=ViewModelStore()
        try {runBlocking(dispatcher) {withTimeout(60_000) {
            val target=fixture.getValue("visitor_couple_id").jsonPrimitive.long
            val ownCouple=fixture.getValue("couple_id").jsonPrimitive.long
            val interest=fixture.getValue("explore_interest_id").jsonPrimitive.int
            val interestLabel=fixture.getValue("explore_interest_label").jsonPrimitive.content
            val creator=fixture.getValue("explore_creator_name").jsonPrimitive.content
            val photoId=fixture.getValue("explore_photo_moment_id").jsonPrimitive.long
            val selectedId=fixture.getValue("explore_text_moment_id").jsonPrimitive.long
            val privateId=fixture.getValue("explore_private_moment_id").jsonPrimitive.long
            val orderedIds=fixture.getValue("explore_moment_ids").jsonArray.map {it.jsonPrimitive.long}

            // Search/filter, explicit identity projection and the additive serializer
            // fields must work over real HTTP, not just a hand-written JSON fixture.
            val page=mine.explore.feed("global","broad",null,"  ${creator.uppercase()}  ",interest).value()
            assertEquals(creator.lowercase(),page.q);assertEquals(interest,page.interestId)
            val card=page.results.single {it.couple.id==target}
            assertEquals(fixture.getValue("explore_target_name").jsonPrimitive.content,card.couple.displayName)
            assertEquals(setOf(fixture.getValue("visitor_id").jsonPrimitive.long,
                fixture.getValue("visitor_partner_id").jsonPrimitive.long),card.couple.members.map {it.id}.toSet())
            assertTrue(interestLabel in card.couple.sharedInterests)
            assertEquals(3,card.previews.size);assertEquals(3,card.previews.map {it.id}.distinct().size)
            assertEquals(card.preview,card.previews.first());assertTrue(card.activeCount>=12)
            card.previews.forEach {preview->
                assertTrue(preview.mediaKind in setOf("text","photo","video","mixed"))
                assertTrue(Instant.parse(assertNotNull(preview.createdAt))<=Instant.parse(page.serverTime))
                assertTrue(Instant.parse(preview.expiresAt)>Instant.parse(page.serverTime))
            }
            val checked=mine.explore.validate(listOf(target),"global",creator,interest).value()
            assertEquals(target,checked.results.single().couple.id)
            val none=mine.explore.feed("global","broad",null,"unmatched synthetic phrase",interest).value()
            assertTrue(none.results.isEmpty());assertEquals("no_results",none.emptyReason)

            // A private save cannot manufacture a follower, or change the partner's saves.
            val beforeSave=mine.explore.faveState(target).value()
            assertFalse(beforeSave.isFaved)
            assertIs<NetworkAPIResult.Success<*>>(mine.explore.setFave(target,true,beforeSave.version))
            assertFalse(myPartner.explore.faveState(target).value().isFaved)
            assertTrue(owner.explore.followers(null).value().results.isEmpty())
            val library=mine.explore.saved(null).value()
            assertEquals(1,library.totalCount);assertEquals(target,library.results.single().coupleId)
            assertTrue(library.results.single().hasActiveContent)
            assertEquals(target,mine.explore.feed("faves","local",null).value().results.single().couple.id)

            // Exercise the profile-scoped public-follow controller against the real
            // current-version endpoint, including explicit disclosure and stale writes.
            val follows=PublicFollowViewModel(mine.explore,mine.sessions).also {store.put("follow",it)}
            follows.enter(target);follows.active(true)
            val initialState=follows.state.first {!it.loading}
            assertNull(initialState.error)
            val initial=assertNotNull(initialState.snapshot)
            assertTrue(initial.canFollow);assertFalse(initial.isFollowing)
            follows.follow(true)
            assertNotNull(follows.state.value.error);assertFalse(mine.explore.followState(target).value().isFollowing)
            follows.follow(true,shareIdentity=true)
            val followedState=follows.state.first {!it.saving}
            assertNull(followedState.error)
            val followed=assertNotNull(followedState.snapshot)
            assertTrue(followed.isFollowing)
            assertTrue(followed.version>initial.version)
            val incoming=owner.explore.followers(null).value()
            assertEquals(target,incoming.coupleId)
            assertEquals(fixture.getValue("account_id").jsonPrimitive.long,incoming.results.single().userId)
            assertEquals(ownCouple,incoming.results.single().coupleId)
            assertEquals(incoming.results,ownersPartner.explore.followers(null).value().results)
            assertTrue(myPartner.explore.followers(null).value().results.isEmpty())
            assertEquals(409,assertIs<NetworkAPIResult.Error<*>>(
                mine.explore.setFollow(target,false,initial.version,false)).failure.status)
            follows.follow(false)
            val unfollowedState=follows.state.first {!it.saving}
            assertNull(unfollowedState.error)
            val unfollowed=assertNotNull(unfollowedState.snapshot)
            assertFalse(unfollowed.isFollowing)
            assertTrue(unfollowed.version>followed.version);assertTrue(mine.explore.faveState(target).value().isFaved)
            assertTrue(owner.explore.followers(null).value().results.isEmpty())
            follows.follow(true,shareIdentity=true)
            val refollowed=follows.state.first {!it.saving}
            assertNull(refollowed.error);assertTrue(assertNotNull(refollowed.snapshot).isFollowing)

            // The selected item is beyond page one. Real sequence continuation and
            // direct loading must preserve that selection without marking it viewed.
            val first=mine.moments.sequence(target,null,null).value()
            assertNotNull(first.next);assertFalse(first.results.any {it.id==selectedId || it.id==privateId})
            val second=mine.moments.sequence(target,null,momentCursor(first.next)).value()
            assertTrue(second.results.any {it.id==selectedId})
            assertTrue((first.results+second.results).map {it.id}.containsAll(orderedIds))
            assertFalse((first.results+second.results).any {it.id==privateId})
            assertEquals(404,assertIs<NetworkAPIResult.Error<*>>(mine.moments.get(privateId)).failure.status)
            val photo=mine.moments.get(photoId).value()
            assertEquals(creator,photo.creator.name);assertFalse(photo.canReply);assertFalse(photo.canEdit)
            val photoUrl=photo.photos.first().image
            assertTrue(assertNotNull(mine.explore.image(photoUrl)).isNotEmpty())
            val viewer=MomentViewerViewModel(mine.moments,mine.sessions).also {store.put("viewer",it)}
            viewer.open(selectedId,target,null);viewer.active(true)
            val selected=viewer.state.first {it.fresh && it.moment?.id==selectedId && it.hasPrevious}
            assertFalse(selected.moment!!.hasOpened)
            assertFalse(mine.moments.get(selectedId).value().hasOpened)
            viewer.move(-1)
            val previousId=orderedIds[orderedIds.indexOf(selectedId)-1]
            viewer.state.first {it.fresh && it.moment?.id==previousId}
            viewer.move(1)
            viewer.state.first {it.fresh && it.moment?.id==selectedId}

            val appreciation="A thoughtful little moment. Thank you for sharing."
            viewer.note(appreciation);viewer.sendNote()
            val sent=viewer.state.first {!it.busy}
            assertNull(sent.error);assertTrue(sent.noteSent)
            assertEquals("",viewer.state.value.note)
            val received=owner.moments.notes(selectedId,1).value().results.single()
            assertEquals(appreciation,received.message)
            assertEquals(received.id,ownersPartner.moments.notes(selectedId,1).value().results.single().id)
            assertTrue(myPartner.moments.notes(selectedId,1).value().results.isEmpty())
            assertEquals(404,assertIs<NetworkAPIResult.Error<*>>(myPartner.moments.noteDetail(received.id)).failure.status)

            // Withdrawal goes through the existing profile API. Previously delivered
            // media URLs fail authorization; private saves and the owner's data remain.
            val profile=owner.profile.load(target).value()
            owner.profile.save(target,"sharing",buildJsonObject {
                put("expected_revision",profile.revisions.getValue("sharing"));put("action","revoke")
            }).value()
            assertEquals(404,assertIs<NetworkAPIResult.Error<*>>(mine.moments.get(photoId)).failure.status)
            assertNull(mine.explore.image(photoUrl))
            assertTrue(mine.moments.sequence(target,null,null).value().results.isEmpty())
            assertTrue(mine.explore.validate(listOf(target),"global",creator,interest).value().results.isEmpty())
            assertTrue(mine.explore.feed("faves","local",null).value().results.isEmpty())
            val withdrawnFollow=mine.explore.followState(target).value()
            assertFalse(withdrawnFollow.canFollow);assertFalse(withdrawnFollow.isFollowing)
            assertTrue(owner.explore.followers(null).value().results.isEmpty())
            assertTrue(mine.explore.faveState(target).value().isFaved)
            assertEquals(1,mine.explore.saved(null).value().totalCount)
            assertEquals("private",owner.moments.get(photoId).value().visibility)
            assertNotNull(owner.moments.image(photoUrl))
            viewer.refresh()
            val withdrawn=viewer.state.first {!it.loading && it.unavailable!=null}
            assertFalse(withdrawn.fresh);assertNull(withdrawn.moment);assertNull(withdrawn.bytes)
            follows.refresh()
            assertFalse(follows.state.first {!it.loading && it.snapshot!=null}.snapshot!!.canFollow)
        }}} finally {
            runBlocking(dispatcher) {store.clear()}
            mine.http.close();myPartner.http.close();owner.http.close();ownersPartner.http.close()
            Dispatchers.resetMain();dispatcher.close()
        }
    }
}
