package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onFirst
import androidx.test.platform.app.InstrumentationRegistry
import com.cuscus.ffmpeggui.conversion.BeatsStatus
import com.cuscus.ffmpeggui.conversion.FollowCropStatus
import com.cuscus.ffmpeggui.conversion.PauseCutsStatus
import com.cuscus.ffmpeggui.conversion.SceneSplitsStatus
import com.cuscus.ffmpeggui.core.editing.CutRange
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * Real pixels of the smart-editing UI with planted data, for the CI
 * verification (see verify-runtime.yml). The words carry a 2.1s silence and
 * a filler "um", the pause cuts and beats are planted, and the follow-crop
 * samples are solid-color frames: every screenshot is deterministic.
 * Screenshots land in the app's external files dir under ci-shots/ and the
 * workflow pulls them into an artifact.
 */
class SmartPanelsScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The planted speech: a 2.1s silence after "world" and a filler "um". */
    private val plantedWords = listOf(
        SpokenWord(0, 400, "Hello"),
        SpokenWord(500, 900, "world"),
        SpokenWord(3_600, 3_800, "um"),
        SpokenWord(4_000, 4_300, "this"),
        SpokenWord(4_350, 4_550, "is"),
        SpokenWord(4_600, 4_700, "a"),
        SpokenWord(4_800, 5_200, "test"),
    )

    private fun saveShot(name: String, bitmap: Bitmap) {
        val dir = File(context.getExternalFilesDir(null), "ci-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        saveShot(name, rule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun subtitleEditing(seeks: MutableList<Pair<String, Long>>) = SubtitleEditing(
        words = mapOf(EditorSource.MAIN_ID to plantedWords),
        sourceNames = mapOf(EditorSource.MAIN_ID to "ci-test-video.mp4"),
        modelReady = true,
    )

    private fun openTranscript(seeks: MutableList<Pair<String, Long>>): MutableList<Pair<String, Long>> {
        rule.setContent { SubtitleSectionContent(subtitleEditing(seeks), onSeekLine = { s, ms -> seeks += s to ms }) }
        rule.onNodeWithText("Lines").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("world").fetchSemanticsNodes().isNotEmpty() }
        return seeks
    }

    @Test
    fun transcriptRendersWordsAndWordTapSeeks() {
        val seeks = mutableListOf<Pair<String, Long>>()
        openTranscript(seeks)
        shot("transcript-lines")
        // Every word seeks to where it is said: tapping "world" seeks to 500ms on the main file.
        rule.onNodeWithText("world").performClick()
        rule.waitForIdle()
        assertEquals(listOf(EditorSource.MAIN_ID to 500L), seeks)
    }

    @Test
    fun transcriptLineTapOpensKeepCutDialog() {
        val seeks = mutableListOf<Pair<String, Long>>()
        openTranscript(seeks)
        // The row around the words opens the line dialog (the words themselves seek).
        rule.onNode(hasAnyChild(hasText("world")) and hasClickAction())
            .performTouchInput { click(Offset(right - 4f, centerY)) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Cut from video").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Keep only this").assertExists()
        rule.onNodeWithText("Line at 0:00", substring = true).assertExists()
        shot("transcript-keep-cut-dialog")
    }

    @Test
    fun pauseCutsPanelWithPlantedCuts() {
        rule.setContent {
            PauseCutsPanel(
                SmartEditUi(
                    pauseCuts = PauseCutsStatus.Ready(
                        cuts = listOf(CutRange(900, 3_000, "silence"), CutRange(3_600, 3_800, "filler")),
                        chosen = setOf(0, 1),
                    ),
                ),
            )
        }
        rule.waitForIdle()
        shot("panel-pause-cuts")
    }

    @Test
    fun sceneSplitsPanelWithPlantedSplits() {
        rule.setContent {
            SceneSplitsPanel(
                SmartEditUi(
                    sceneSplits = SceneSplitsStatus.Ready(
                        points = listOf(2_000, 4_000, 6_000),
                        chosen = setOf(0, 2),
                    ),
                ),
            )
        }
        rule.waitForIdle()
        shot("panel-scene-splits")
    }

    @Test
    fun beatsPanelWithPlantedBeats() {
        rule.setContent {
            BeatsPanel(
                SmartEditUi(
                    beats = BeatsStatus.Ready(
                        onsetsMs = listOf(450, 950, 1_450, 3_000, 5_000),
                        beatsMs = listOf(0, 500, 1_000, 1_500, 2_000, 2_500),
                        tempoBpm = 120.0,
                    ),
                ),
            )
        }
        rule.waitForIdle()
        shot("panel-beats")
    }

    @Test
    fun followCropPanelSetWithSamples() {
        val frames = listOf(0xFFB00020.toInt(), 0xFF006400.toInt(), 0xFF103070.toInt()).map { color ->
            ImageBitmap(48, 84).apply {
                val bmp = asAndroidBitmap()
                bmp.eraseColor(color)
            }
        }
        rule.setContent {
            FollowCropPanel(
                SmartEditUi(
                    followCrop = FollowCropStatus.Idle,
                    followCropSet = true,
                    followCropSamples = frames,
                ),
            )
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("How it will look").fetchSemanticsNodes().isNotEmpty() }
        shot("panel-follow-crop")
        assertTrue(true)
    }
}
