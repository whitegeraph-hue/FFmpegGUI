package com.cuscus.ffmpeggui

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.cuscus.ffmpeggui.conversion.ConversionViewModel
import com.cuscus.ffmpeggui.conversion.SceneSplitsStatus
import org.junit.Assert.*
import org.junit.Test

/** Real Android model, content URI, FFmpeg scene analysis, apply and Undo. */
class SelectedClipRuntimeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(message: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 60000
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(100)
        }
        error(message)
    }
    @Test fun selectedOccurrenceScenesApplyUndoAndStaleScope() {
        val context = instrumentation.targetContext
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ci-selected-scenes.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FFmpegGUI-CI")
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        instrumentation.context.assets.open("ci-selected-scenes.mp4").use { src ->
            context.contentResolver.openOutputStream(uri)!!.use { src.copyTo(it) }
        }
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            assertTrue(Ui.waitFor("Choose a format", 30000))
            assertTrue(Ui.click("MP4"))
            assertTrue(Ui.waitFor("Split", 60000))
            lateinit var vm: ConversionViewModel
            scenario.onActivity { vm = ViewModelProvider(it, ConversionViewModel.Factory)[ConversionViewModel::class.java] }
            waitFor("source probe did not finish") { vm.mediaInfo.value != null }
            instrumentation.runOnMainSync { vm.splitClipAt(3000) }
            val before = vm.settings.value.timeline
            assertEquals(2, before.clips.size)
            val second = before.clips[1].id
            val dsp = com.cuscus.ffmpeggui.core.model.AudioEffects(highCutHz=150,echo=com.cuscus.ffmpeggui.core.model.Echo(),pitchSemitones=3f)
            instrumentation.runOnMainSync { vm.updateEffects(second) { it.copy(audioEffects=dsp,normalizeAudio=true) } }
            assertEquals(before.clips[0],vm.settings.value.timeline.clips[0])
            assertEquals(dsp,vm.settings.value.timeline.clips[1].effects.audioEffects)
            val encoded=com.cuscus.ffmpeggui.core.project.ModelJson.write(vm.settings.value.timeline)
            assertEquals(vm.settings.value.timeline,com.cuscus.ffmpeggui.core.project.ModelJson.timeline(encoded))
            instrumentation.runOnMainSync {vm.undo()}
            assertEquals(before,vm.settings.value.timeline)

            assertTrue(Ui.clickScrolling("Scenes"))
            assertTrue(Ui.waitFor("Clip 2",15000))
            assertTrue(Ui.click("Clip 2"))
            Thread.sleep(1000)
            ShellShot.take("selected-clip2-scenes-before")
            instrumentation.runOnMainSync { vm.findSceneSplits() }
            waitFor("selected scene analysis did not finish") { vm.sceneSplits.value is SceneSplitsStatus.Ready || vm.sceneSplits.value is SceneSplitsStatus.Failed }
            val ready = vm.sceneSplits.value as? SceneSplitsStatus.Ready ?: error("scene analysis failed")
            assertEquals(listOf(4000L), ready.points)
            ShellShot.take("selected-clip2-scenes-detected")
            instrumentation.runOnMainSync { vm.applySceneSplits() }
            val after = vm.settings.value.timeline
            assertEquals(3, after.clips.size)
            assertEquals(before.clips[0], after.clips[0])
            assertEquals(3000L, after.clips[1].startMs)
            assertEquals(4000L, after.clips[1].endMs)
            assertEquals(4000L, after.clips[2].startMs)
            instrumentation.runOnMainSync { vm.undo() }
            assertEquals(before, vm.settings.value.timeline)
            instrumentation.runOnMainSync { vm.setSmartClipScope(second); vm.findSceneSplits(); vm.setSmartClipScope(before.clips[0].id) }
            Thread.sleep(3000)
            assertTrue("stale results survived scope switch", vm.sceneSplits.value is SceneSplitsStatus.Idle)
            instrumentation.runOnMainSync { vm.applySceneSplits() }
            assertEquals(before, vm.settings.value.timeline)
            instrumentation.runOnMainSync { vm.setSmartClipScope(second);vm.keepSpokenRangeOnly(before.clips[1].sourceId,1000,2000) }
            assertEquals(before,vm.settings.value.timeline)
            instrumentation.runOnMainSync { vm.setSmartClipScope(second);vm.cutSpokenRange(before.clips[1].sourceId,4000,4500) }
            assertEquals(before.clips[0],vm.settings.value.timeline.clips[0])
            assertEquals(5500L,vm.settings.value.timeline.durationMs)
            instrumentation.runOnMainSync { vm.undo();vm.keepSpokenRangeOnly(before.clips[1].sourceId,4000,4500) }
            assertEquals(before.clips[0],vm.settings.value.timeline.clips[0])
            assertEquals(3500L,vm.settings.value.timeline.durationMs)
            instrumentation.runOnMainSync { vm.undo() }
            assertEquals(before,vm.settings.value.timeline)
            assertTrue(Ui.click("Clip 2"))
            assertTrue(Ui.click("Done"))
            assertTrue(Ui.clickScrolling("Equalizer"))
            assertTrue(Ui.waitFor("Bass boost",15000))
            assertTrue(Ui.click("Bass boost"))
            waitFor("selected clip EQ did not change") {vm.settings.value.timeline.clips[1].effects.audioEffects.shapesTone}
            assertEquals(before.clips[0],vm.settings.value.timeline.clips[0])
            assertEquals(com.cuscus.ffmpeggui.core.model.AudioEffects.NONE,vm.settings.value.audioEffects)
            ShellShot.take("selected-clip2-equalizer-bass")
            instrumentation.runOnMainSync {vm.undo()}
            assertEquals(before,vm.settings.value.timeline)
        }
        context.contentResolver.delete(uri, null, null)
    }
}
