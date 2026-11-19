package com.srisu.srisu.features.moments

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.chat.ChatRecoveryFixtures
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*

object MomentFixtures {
    const val NOW="2026-10-22T06:00:00Z"
    val photo=MomentPhoto(21,"https://api.example.invalid/photo",0,960,640)
    val mine=Moment(7,3,1,MomentPerson(1,"Alex"),"The quiet part of our morning.",photos=listOf(photo),publishedAt=NOW,expiresAt="2026-10-23T06:00:00Z",serverTime=NOW,canEdit=true,canDelete=true)
    val partner=mine.copy(id=8,createdBy=2,creator=MomentPerson(2,"Sam"),canEdit=false,canDelete=false,canReply=true)
    val home=MomentHome(MomentPerson(1,"Alex"),MomentCouple(3,null,listOf(MomentPerson(1,"Alex",activeCount=1),MomentPerson(2,"Sam",activeCount=1,unseenCount=1))),NOW)
    val image get()=ChatRecoveryFixtures.image
    fun sessions(values: MutableMap<String,String> = mutableMapOf()): SessionCoordinator {
        val storage=object:SessionStorage {
            override fun saveSession(credentials:String,sessionKey:String) {values[sessionKey]=credentials}
            override fun getSession(sessionKey:String)=values[sessionKey]
            override fun clearSession():Boolean {values.clear();return true}
            override fun clearOnReinstall(key:String)=Unit
        }
        return SessionCoordinator(storage).also {it.saveSession(ApiJson.encodeToString(Session(id=1,access="synthetic",fullName="Alex")),SESSION_KEY)}
    }
    fun <T> failed(status:Int?=null):MomentResult<T> = NetworkAPIResult.Error(ApiError(NetworkAPIResult.ErrorType.NETWORK,"synthetic","Connection unavailable",status=status))
}

open class FakeMoments : MomentSource {
    override val changes=MutableStateFlow(0L)
    override fun changed() {changes.value++}
    var homeCalls=0
    var views=0
    var gets=0
    var uploads=0
    var publications=0
    var failUpload=false
    var lostPublication=false
    var homeResult:MomentResult<MomentHome> = NetworkAPIResult.Success(MomentFixtures.home)
    var moment:MomentResult<Moment> = NetworkAPIResult.Success(MomentFixtures.partner)
    var staged:MomentDraft?=null
    var published:Long?=null
    var sentIntent:PublishMoment?=null
    override suspend fun home():MomentResult<MomentHome> {homeCalls++;return homeResult}
    override suspend fun sequence(couple:Long,creator:Long?,cursor:String?):MomentResult<MomentPage> = NetworkAPIResult.Success(MomentPage(listOf(MomentFixtures.partner)))
    override suspend fun get(id:Long):MomentResult<Moment> {gets++;return moment}
    override suspend fun image(url:String):ByteArray?=MomentFixtures.image
    override suspend fun viewed(id:Long):MomentResult<Moment> {views++;return moment}
    override suspend fun react(id:Long,value:Boolean)=moment
    override suspend fun edit(id:Long,revision:Long,caption:String)=moment
    override suspend fun delete(id:Long)=NetworkAPIResult.Success<JsonObject?>(null)
    override suspend fun note(id:Long,text:String):MomentResult<MomentNote> = NetworkAPIResult.Success(MomentNote(1,text))
    override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> = NetworkAPIResult.Success(MomentNotePage())
    override suspend fun noteReply(id:Long,text:String):MomentResult<MomentNoteReply> = NetworkAPIResult.Success(MomentNoteReply(1,text))
    override suspend fun createDraft(id:String,couple:Long):MomentResult<MomentDraft> {
        val result=(staged ?: MomentDraft(id,couple,1)).copy(momentId=published)
        staged=result; return NetworkAPIResult.Success(result)
    }
    override suspend fun draft(id:String)=NetworkAPIResult.Success(staged)
    override suspend fun upload(id:String,photo:String,bytes:ByteArray,progress:(Float)->Unit):MomentResult<MomentDraft> {
        uploads++; if(failUpload) return MomentFixtures.failed()
        val current=staged!!; progress(.5f)
        staged=current.copy(revision=current.revision+1,photos=current.photos+StagedPhoto(photo,"https://api.example.invalid/staged",960,640))
        return NetworkAPIResult.Success(staged)
    }
    override suspend fun removePhoto(id:String,photo:String)=NetworkAPIResult.Success(staged)
    override suspend fun discard(id:String)=NetworkAPIResult.Success<JsonObject?>(null)
    override suspend fun publish(id:String,body:PublishMoment):MomentResult<Moment> {
        publications++;sentIntent=body;published=7
        return if(lostPublication) MomentFixtures.failed() else NetworkAPIResult.Success(MomentFixtures.mine)
    }
    override suspend fun discover(section:String,cursor:String?):MomentResult<MomentDiscoveryPage> = NetworkAPIResult.Success(MomentDiscoveryPage(MomentFixtures.NOW))
    override suspend fun fave(couple:Long,value:Boolean)=NetworkAPIResult.Success<JsonObject?>(null)
}
