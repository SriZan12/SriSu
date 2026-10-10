package com.srisu.srisu.features.couplechat

import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.couplechat.crypto.*
import com.srisu.srisu.features.couplechat.data.*
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Observational Debug/emulator benchmark, not a release performance gate. */
class ChatHistoryBenchmarkTest {
    @Test fun tenThousandEncryptedRowsRemainPaged() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val account=810000000000000010L
        val native=AndroidChatDriver(context)
        val store=ProtectedChatStore(native,account)
        val room="00000000-0000-4000-8000-000000000010"
        val db=LocalConversation(store,room)
        val reset="{\"confirm_history_loss\":true}"
        assertTrue(JSONObject(native.execute(account.toString(),"reset",reset)).getBoolean("ok"))
        try {
            for(batch in 0 until 100) {
                val entries=mutableListOf<ProtectedEntry>()
                for(index in batch*100+1..batch*100+100) {
                    val id="synthetic-$index"
                    val timeline=db.timelinePrefix+"c:"+index.toString().padStart(20,'0')+":"+id
                    val row=LocalChatMessage(id,room,account,"2026-10-10T10:00:00Z",ChatContent.Text("Synthetic history $index"),
                        transport=ChatTransport.SENT,sequence=index.toLong(),timelineKey=timeline)
                    entries+=ProtectedEntry(timeline,ChatJson.encodeToString(id))
                    entries+=ProtectedEntry(db.messageKey(id),ChatJson.encodeToString(row))
                }
                store.write(entries)
            }
            native.close(account.toString())
            val began=SystemClock.elapsedRealtimeNanos()
            val first=db.page()
            val cold=(SystemClock.elapsedRealtimeNanos()-began)/1_000_000.0
            assertEquals(50,first.first.size);assertTrue(first.second)
            assertEquals("synthetic-10000",first.first.last().id)
            val measurements=mutableListOf<Double>()
            var before=first.first.first().timelineKey
            repeat(20) {
                val start=SystemClock.elapsedRealtimeNanos();val page=db.page(before)
                measurements+=(SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
                assertEquals(50,page.first.size);before=page.first.first().timelineKey
            }
            val pending=SystemClock.elapsedRealtimeNanos()
            val operation=db.enqueue("message",ChatContent.Text("Synthetic queued send"))
            val enqueue=(SystemClock.elapsedRealtimeNanos()-pending)/1_000_000.0
            assertEquals(operation.id,db.queue().single().id)
            val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
            val result=JSONObject().put("history_rows",10000).put("page_size",50).put("cold_native_page_ms",cold)
                .put("warm_page_median_ms",measurements.sorted()[10]).put("warm_page_p95_ms",measurements.sorted()[18])
                .put("protected_enqueue_ms",enqueue).put("process_pss_kib",memory.totalPss)
                .put("environment","Debug APK; API35 emulator-5580, x86_64, 2 CPU/2 GiB; not UI frame or physical release metrics")
            File(context.filesDir,"chat-history-benchmark.json").writeText(result.toString(2))
        } finally {native.execute(account.toString(),"reset",reset);native.close(account.toString())}
    }
}
