package com.srisu.srisu.notifications

import com.srisu.srisu.features.notifications.*
import com.srisu.srisu.features.moments.MomentFixtures
import srisu.composeapp.generated.resources.Res
import kotlin.time.Instant

/** Real composables with synthetic activity; no sample events ship to production. */
internal object NotificationReferenceFixture {
    val now=Instant.parse("2026-11-25T12:00:00Z")
    private fun row(n:Int,kind:String,time:String,read:Boolean=false,thumbnail:Boolean=false,vararg text:InboxSegment)=InboxItem(
        InboxFixtures.id(n),kind,"fixture",text.joinToString("") {it.text},"",time,
        readAt=if(read) now.toString() else null,available=true,actorName="Aatithi",avatar=true,
        thumbnail=thumbnail,segments=text.toList())
    val state=InboxState(loading=false,loaded=true,unreadCount=2,readToken="synthetic",items=listOf(
        row(11,"spark.submitted","2026-11-25T11:48:00Z",false,false,InboxSegment("Aatithi",true),InboxSegment(" answered your Spark. Answer to see theirs.")),
        row(12,"idea.interested","2026-11-25T11:00:00Z",false,false,InboxSegment("Aatithi",true),InboxSegment(" likes your date idea · A walk together")),
        row(13,"moment.published","2026-11-25T10:00:00Z",true,true,InboxSegment("Aatithi",true),InboxSegment(" shared a photo moment")),
        row(14,"plan.responded","2026-11-25T09:00:00Z",true,false,InboxSegment("Aatithi",true),InboxSegment(" said yes to "),InboxSegment("Sunrise hike",true),InboxSegment(" · Sun 5:30 AM")),
        row(15,"challenge.progress","2026-11-23T09:00:00Z",true,false,InboxSegment("Aatithi",true),InboxSegment(" finished their part of "),InboxSegment("Cook something new",true)),
        row(16,"status.changed","2026-11-23T07:00:00Z",true,false,InboxSegment("Aatithi",true),InboxSegment(" updated their status · Thinking of you")),
        row(17,"moment.published","2026-11-19T09:00:00Z",true,true,InboxSegment("Aatithi",true),InboxSegment(" shared a video moment"))
    ))
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    suspend fun image(id:String,slot:String):ByteArray? = if(slot=="avatar") Res.readBytes("drawable/will.jpg") else MomentFixtures.image
}
