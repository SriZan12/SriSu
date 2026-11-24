package com.srisu.srisu.features.moments

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.srisu.srisu.features.moments.data.LocalMomentVideo
import com.srisu.srisu.features.moments.data.readMomentVideoChunk
import com.srisu.srisu.features.moments.data.removeMomentVideoFile
import com.srisu.srisu.utils.AppContext
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MomentVideoFilesTest {
    @Test fun readsAndDeletesPreparedVideosWithoutGlobalKoin() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppContext.setUp(app)
        // Production uses KoinIsolatedContext, not the global Koin registry.
        assertNull(GlobalContext.getOrNull())
        val root = File(app.cacheDir, "srisu-moment-videos").apply { mkdirs() }
        val file = File.createTempFile("synthetic-", ".mp4", root)
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        file.writeBytes(bytes)
        val video = LocalMomentVideo(file.path, bytes.size.toLong(), 1000, 160, 240)
        try {
            assertContentEquals(byteArrayOf(2, 3, 4), readMomentVideoChunk(video, 1, 3))
            removeMomentVideoFile(video)
            assertFalse(file.exists())
        } finally { file.delete() }
    }

    @Test fun refusesReadsAndDeletionOutsideOwnedVideoDirectory() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppContext.setUp(app)
        val file = File.createTempFile("unrelated-", ".mp4", app.cacheDir)
        file.writeBytes(byteArrayOf(1, 2, 3))
        val video = LocalMomentVideo(file.path, 3, 1000, 160, 240)
        try {
            assertFailsWith<IllegalArgumentException> { readMomentVideoChunk(video, 0, 3) }
            removeMomentVideoFile(video)
            assertTrue(file.exists())
        } finally { file.delete() }
    }
}
