package com.cuscus.ffmpeggui

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The real app, opened on the editor with the deterministic test video (see
 * SmartPanelsScreenshotTest for the panel pixels). The video is copied into
 * MediaStore the way the system picker would hand it over, MainActivity is
 * launched with ACTION_VIEW on it (an entry the app supports), the format
 * chooser it lands on is passed with the suggested MP4, and each smart tool
 * is opened and screenshotted in place. Driven through the accessibility
 * tree: the editor never reports Compose-idle, so the Compose rule's waits
 * time out there.
 */
class EditorOpenScreenshotTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun shot(name: String) {
        Thread.sleep(900)
        ShellShot.take(name)
    }

    private fun installTestVideo(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ci-test-video.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FFmpegGUI-CI")
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: error("MediaStore insert failed")
        // The asset lives in the test APK (androidTest/assets), not the app.
        instrumentation.context.assets.open("ci-test-video.mp4").use { input ->
            context.contentResolver.openOutputStream(uri)?.use { input.copyTo(it) } ?: error("no output stream")
        }
        return uri
    }

    @Test
    fun editorOpensAndSmartToolsShow() {
        val uri = installTestVideo()
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use {
            try {
                assertTrue("format chooser not shown", Ui.waitFor("Choose a format", 30_000))
                shot("editor-format-chooser")
                assertTrue("could not pick MP4", Ui.click("MP4"))
                assertTrue("editor toolbar never showed 'Split'", Ui.waitFor("Split", 60_000))
            } catch (e: Throwable) {
                Ui.dump()
                shot("editor-timeout")
                throw e
            }
            shot("editor-open")
            listOf("Pause cuts", "Scenes", "Beats", "Follow crop").forEach { tool ->
                assertTrue("tool not clickable: $tool", Ui.clickScrolling(tool))
                shot("editor-tool-" + tool.lowercase().replace(' ', '-'))
                assertTrue("could not close panel: $tool",Ui.click("Done"))
                Thread.sleep(400)
            }
        }
    }
}
