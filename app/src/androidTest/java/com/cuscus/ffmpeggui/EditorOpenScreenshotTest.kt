package com.cuscus.ffmpeggui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * The real app, opened on the editor with the deterministic test video (see
 * SmartPanelsScreenshotTest for the panel pixels). The video is copied into
 * MediaStore the way the system picker would hand it over, MainActivity is
 * launched with ACTION_VIEW on it (an entry the app supports), and each
 * smart-editing tool is opened and screenshotted in place.
 */
class EditorOpenScreenshotTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun saveShot(name: String) {
        rule.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("takeScreenshot returned null")
        val dir = File(context.getExternalFilesDir(null), "ci-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The bundled deterministic video, copied into MediaStore as the app itself. */
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

    private fun clickTool(label: String) {
        val byText = rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        if (byText) {
            rule.onAllNodesWithText(label).onFirst().performClick()
        } else {
            val byDesc = rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
            assertTrue("toolbar tool not found: $label", byDesc)
            rule.onAllNodesWithContentDescription(label).onFirst().performClick()
        }
    }

    @Test
    fun editorOpensAndSmartToolsShow() {
        val uri = installTestVideo()
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<MainActivity>(intent).use {
            // The editor reached the toolbar: every smart tool is there.
            rule.waitUntil(30_000) {
                rule.onAllNodesWithText("Scenes").fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithContentDescription("Scenes").fetchSemanticsNodes().isNotEmpty()
            }
            saveShot("editor-open")
            listOf("Pause cuts", "Scenes", "Beats", "Follow crop").forEach { tool ->
                clickTool(tool)
                rule.waitForIdle()
                saveShot("editor-tool-" + tool.lowercase().replace(' ', '-'))
            }
        }
    }
}
