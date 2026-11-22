package com.cuscus.ffmpeggui.editing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cuscus.ffmpeggui.core.editing.FollowCropTrack
import com.cuscus.ffmpeggui.core.editing.SubjectPoint
import kotlin.math.roundToInt
import com.cuscus.ffmpeggui.core.engine.StillMaker
import com.cuscus.ffmpeggui.cutout.CutoutEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Follows the subject of a video with the cut-out model: every [everyMs] a
 * frame is captured and cut out, and the middle of its mask is one
 * [SubjectPoint]. The track that comes out is what core's FollowCropTrack
 * smooths and turns into the export's moving crop.
 *
 * This is the slow kind of smart: a look every half second of footage, each a
 * model run on the processor. The panel that starts it says so.
 */
class SubjectTracker(
    private val stillMaker: StillMaker,
    /** The cut-out engine, null when the app has no cut-out model installed. */
    private val engine: () -> CutoutEngine?,
) {

    /** The subject's track and the real frame size it was read from. */
    data class SubjectTrack(
        val points: List<SubjectPoint>,
        val frameWidth: Int,
        val frameHeight: Int,
        /** A few frames already cropped as the export will crop them: the panel shows what the window sees. */
        val samples: List<Bitmap> = emptyList(),
    )

    /**
     * The subject's track through [sourceUri] ([durationMs] long), one point
     * about every [everyMs]; null when no frame could be read. [onProgress]
     * gets 0..1 as the frames are done. Points whose mask found nothing keep
     * the middle of the frame at confidence 0: the smoothing gives them no say.
     *
     * The frames are read as the phone shows them (a turned video's frames lie
     * on their side already), which can differ from the probe's coded size: the
     * result carries the frames' real size, which the crop window is built on.
     */
    suspend fun track(
        sourceUri: String,
        durationMs: Long,
        everyMs: Long = DEFAULT_EVERY_MS,
        onProgress: (Float) -> Unit = {},
    ): SubjectTrack? = withContext(Dispatchers.Default) {
        val cutout = engine() ?: return@withContext null
        if (durationMs <= 0L) return@withContext null
        val points = mutableListOf<SubjectPoint>()
        var frameWidth = 0
        var frameHeight = 0
        var at = 0L
        var done = 0
        val total = (durationMs / everyMs).toInt() + 1
        // Three frames along the footage are kept for the cropped preview below.
        val sampleAt = setOf(total / 6, total / 2, total * 5 / 6)
        val kept = mutableListOf<Pair<Long, Bitmap>>()
        while (at < durationMs) {
            currentCoroutineContext().ensureActive()
            val framePath = stillMaker.captureFrame(sourceUri, at)
            if (framePath != null) {
                val bitmap = BitmapFactory.decodeFile(framePath)
                if (bitmap != null) {
                    if (frameWidth == 0) {
                        frameWidth = bitmap.width
                        frameHeight = bitmap.height
                    }
                    val mask = cutout.mask { side -> pixelsOf(bitmap, side) }
                    points += centroid(mask, at)
                    if (done in sampleAt && kept.size < 3) {
                        kept += at to bitmap
                    } else {
                        bitmap.recycle()
                    }
                }
            }
            done++
            onProgress(done.toFloat() / total)
            at += everyMs
        }
        if (points.isEmpty() || frameWidth == 0) {
            kept.forEach { it.second.recycle() }
            return@withContext null
        }
        // The same window the export slides, cut out of the kept frames (the
        // crop only picks pixels, so a cut-out bitmap IS what it will write).
        val (w, h) = FollowCropTrack.windowSize(frameWidth, frameHeight, 9, 16)
        val track = FollowCropTrack(points, frameWidth, frameHeight, w, h)
        val samples = kept.map { (atMs, bitmap) ->
            val (cx, cy) = track.centerAt(atMs)
            val x = (cx - w / 2f).roundToInt().coerceIn(0, (frameWidth - w).coerceAtLeast(0))
            val y = (cy - h / 2f).roundToInt().coerceIn(0, (frameHeight - h).coerceAtLeast(0))
            val cropped = Bitmap.createBitmap(bitmap, x, y, w.coerceAtMost(frameWidth - x), h.coerceAtMost(frameHeight - y))
            bitmap.recycle()
            if (cropped.height > SAMPLE_HEIGHT) {
                Bitmap.createScaledBitmap(cropped, cropped.width * SAMPLE_HEIGHT / cropped.height, SAMPLE_HEIGHT, true)
                    .also { cropped.recycle() }
            } else {
                cropped
            }
        }
        SubjectTrack(points, frameWidth, frameHeight, samples)
    }

    /** The [bitmap] squeezed to [side] x [side], ARGB row by row. */
    private fun pixelsOf(bitmap: Bitmap, side: Int): IntArray {
        val scaled = if (bitmap.width == side && bitmap.height == side) bitmap else Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== bitmap) scaled.recycle()
        return pixels
    }

    /** The mask's middle (0..1 of the square) and how much of the square it covered. */
    private fun centroid(mask: CutoutEngine.Mask, atMs: Long): SubjectPoint {
        var weight = 0L
        var x = 0L
        var y = 0L
        for (row in 0 until mask.side) {
            for (col in 0 until mask.side) {
                val v = mask.values[row * mask.side + col].toLong()
                weight += v
                x += col * v
                y += row * v
            }
        }
        if (weight <= 0L) return SubjectPoint(atMs, 0.5f, 0.5f, 0f)
        val full = mask.side.toLong() * mask.side * 255L
        return SubjectPoint(
            atMs = atMs,
            x = (x.toDouble() / weight / mask.side).toFloat().coerceIn(0f, 1f),
            y = (y.toDouble() / weight / mask.side).toFloat().coerceIn(0f, 1f),
            confidence = (weight.toDouble() / full).toFloat().coerceIn(0f, 1f),
        )
    }

    companion object {
        const val DEFAULT_EVERY_MS = 500L
        private const val SAMPLE_HEIGHT = 360
    }
}
