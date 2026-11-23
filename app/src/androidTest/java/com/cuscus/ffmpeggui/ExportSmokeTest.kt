package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.Transition
import com.cuscus.ffmpeggui.core.model.TransitionKind
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.editing.FollowCropTrack
import com.cuscus.ffmpeggui.core.editing.SubjectPoint
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.model.SourceInfo
import com.cuscus.ffmpeggui.media.FfmpegKitExecutor
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * A real export through the real FFmpeg runtime on the device: the core
 * CommandBuilder builds the full command for synthetic footage with a known
 * follow-crop track and the pause cut applied (two segments around the
 * planted 2-3.5s silence), the pinned FFmpegKit build runs it, and the
 * output is probed. The test video is deterministic: left half red, right
 * half blue, sine audio with the silence gap, so the assertions are exact:
 * the output is the 9:16 window, 6.5s long, red at the start and blue at
 * the end as the window travels right.
 */
class ExportSmokeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets get() = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun followCropAndPauseCutExport() = runBlocking {
        val input = File(context.cacheDir, "ci-export-input.mp4")
        // The asset lives in the test APK (androidTest/assets), not the app.
        testAssets.open("ci-test-video.mp4").use { src -> input.outputStream().use { src.copyTo(it) } }
        val output = File(context.cacheDir, "ci-export-output.mp4")
        output.delete()

        // The known track: the 9:16 window of the 640x360 frame, travelling
        // from the red half to the blue half across the (cut) sequence.
        val track = FollowCropTrack(
            points = listOf(
                SubjectPoint(0, 0.25f, 0.5f, 1f),
                SubjectPoint(6_500, 0.75f, 0.5f, 1f),
            ),
            inWidth = 640, inHeight = 360, outWidth = 202, outHeight = 360,
        )
        val request = ConversionRequest(
            format = OutputFormat.MP4,
            inputFileName = "ci-export-input.mp4",
            sources = listOf(SourceInfo(durationMs = 8_000, width = 640, height = 360, hasAudio = true)),
            // The planted pause cut, applied: 2.0-3.5s of silence is out.
            segments = listOf(
                Segment(source = 0, startMs = 0, endMs = 2_000),
                Segment(source = 0, startMs = 3_500, endMs = 8_000),
            ),
            followCrop = track,
        )
        val paths = MediaPaths(inputs = listOf(input.absolutePath), output = output.absolutePath)
        val command = CommandBuilder { _ -> "/fonts/Regular.ttf" }.build(request, paths)

        val result = FfmpegKitExecutor().execute(command) { }
        assertEquals(ExecutionResult.Success, result)
        assertTrue("output file missing", output.isFile && output.length() > 0)

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(output.absolutePath)
            assertEquals("202", mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
            assertEquals("360", mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1
            assertTrue("duration $durationMs not near 6500ms", durationMs in 6_100..6_900)

            // Representative content: red half at the start, blue half at the end.
            // OPTION_CLOSEST decodes the real frame at that time; CLOSEST_SYNC
            // would return the stream's only keyframe (t=0) for every probe.
            val (startRed, startBlue) = channelAverages(mmr.getFrameAtTime(100_000, MediaMetadataRetriever.OPTION_CLOSEST))
            val (endRed, endBlue) = channelAverages(mmr.getFrameAtTime(6_300_000, MediaMetadataRetriever.OPTION_CLOSEST))
            assertTrue("start frame not red (r=$startRed b=$startBlue)", startRed > startBlue + 60)
            assertTrue("end frame not blue (r=$endRed b=$endBlue)", endBlue > endRed + 60)
        } finally {
            mmr.release()
        }
    }

    @Test
    fun mixedStillTransitionUsesBundledRuntime() = runBlocking {
        val input=File(context.cacheDir,"ci-mixed-input.mp4")
        testAssets.open("ci-test-video.mp4").use {src->input.outputStream().use {src.copyTo(it)}}
        val picture=File(context.cacheDir,"ci-mixed-white.png")
        val bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        picture.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        val output=File(context.cacheDir,"ci-mixed-transition.mp4");output.delete()
        val request=ConversionRequest(format=OutputFormat.MP4,inputFileName=input.name,
            sources=listOf(SourceInfo(durationMs=8000,width=640,height=360,hasAudio=true,frameRate=25f),SourceInfo(durationMs=0,width=320,height=180,hasAudio=false,isStill=true)),
            segments=listOf(Segment(0,0,1000,Effects(transition=Transition(TransitionKind.FADE,400))),Segment(1,0,1500,Effects(transition=Transition(TransitionKind.FADE,400))),Segment(0,1000,2000)))
        val command=CommandBuilder {_ -> "/fonts/Regular.ttf"}.build(request,MediaPaths(inputs=listOf(input.path,picture.path),output=output.path))
        assertEquals(ExecutionResult.Success,FfmpegKitExecutor().execute(command){})
        val mmr=MediaMetadataRetriever()
        try {
            mmr.setDataSource(output.path)
            val duration=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1
            assertTrue("mixed duration $duration",duration in 2600..2850)
            val frame=requireNotNull(mmr.getFrameAtTime(1350000,MediaMetadataRetriever.OPTION_CLOSEST))
            val pixel=frame.getPixel(frame.width/2,frame.height/2)
            assertTrue("middle of still is not white",android.graphics.Color.red(pixel)>235 && android.graphics.Color.green(pixel)>235 && android.graphics.Color.blue(pixel)>235)
            frame.recycle()
        }finally {mmr.release()}
    }

    private fun channelAverages(frame: Bitmap?): Pair<Float, Float> {
        requireNotNull(frame) { "no frame at the requested time" }
        val pixels = IntArray(frame.width * frame.height)
        frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        var red = 0L
        var blue = 0L
        for (p in pixels) {
            red += (p shr 16) and 0xFF
            blue += p and 0xFF
        }
        return red.toFloat() / pixels.size to blue.toFloat() / pixels.size
    }
}
