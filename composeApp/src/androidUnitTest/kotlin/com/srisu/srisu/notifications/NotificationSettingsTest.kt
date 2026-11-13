package com.srisu.srisu.notifications

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.srisu.srisu.core.environment
import com.srisu.srisu.core.session
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.notifications.*
import com.srisu.srisu.theme.SriSuTheme
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationSettingsTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun render(dark: Boolean, large: Boolean, matrixChat: Boolean = false) {
        val sessions=session()
        val lifetime=ApplicationLifetime()
        val client=HttpClientFactory.create(sessions,environment,MockEngine { request ->
            if(request.method==HttpMethod.Put) respond("{}",HttpStatusCode.Conflict,headersOf(HttpHeaders.ContentType,"application/json"))
            else respond("""{"data":{"contract":"notifications-1","push":true,"chat":true,"activity":true,"revision":1,"capabilities":[${if(matrixChat) "\"matrix-chat-push-1\"" else ""}]}}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        })
        val repository=NotificationRepository(NotificationApi(client,environment),sessions,lifetime)
        NativeNotifications.update(true,"denied",false,true)
        try {
            compose.setContent {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                    org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
                }
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.6f else 1f)) {
                    SriSuTheme(darkTheme=dark) {
                        androidx.compose.material3.Surface { NotificationSettings(onBack={},repository=repository) }
                    }
                }
            }
            compose.waitUntil(10000) { ShadowLooper.idleMainLooper();repository.state.value.preferences != null }
            if (matrixChat) {
                compose.onNodeWithContentDescription("Chat messages").performScrollTo().assertIsOn()
                compose.onNodeWithText("Mute 1 hour").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Chat push notifications are not available yet.").assertDoesNotExist()
            } else {
                compose.onNodeWithContentDescription("Chat messages").assertDoesNotExist()
                compose.onNodeWithText("Chat push notifications are not available yet.").performScrollTo().assertIsDisplayed()
            }
            compose.onNodeWithText("Open system settings").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Push notifications").performScrollTo().performClick()
            compose.waitUntil(10000) { ShadowLooper.idleMainLooper();repository.state.value.error != null }
            compose.onNodeWithContentDescription("Push notifications").assertIsOn()
            compose.onNodeWithText("Your change wasn’t saved. Reload and try again.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Reload settings").performClick()
            compose.waitUntil(10000) { ShadowLooper.idleMainLooper();!repository.state.value.busy && repository.state.value.error == null }
            compose.onNodeWithContentDescription("Share crash diagnostics").performScrollTo().assertIsOff()
            compose.onNodeWithText("Back").performScrollTo()
            compose.runOnIdle {
                val view=compose.activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val file=File("build/reports/notifications/settings-${if(dark)"dark-large" else "light"}.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        } finally {
            compose.runOnIdle {repository.close();lifetime.close();client.close();NativeNotifications.update(false,"unavailable",false,false)}
        }
    }
    @Test fun deniedPermissionAndFailedSaveKeepConfirmedSettings()=render(false,false)
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun darkLargeTextSettingsRemainReachable()=render(true,true,matrixChat=true)
}
