package com.cuscus.ffmpeggui.core.editing

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * One look at where the subject is: the middle of its mask ([x], [y], 0..1 of
 * the frame) at [atMs], and [confidence] (0..1: how much of the frame the
 * mask covered, so empty looks pull less).
 */
data class SubjectPoint(val atMs: Long, val x: Float, val y: Float, val confidence: Float)

/**
 * Where a vertical reframe of a landscape (or any) video looks, moment by
 * moment: a crop window of [outWidth] x [outHeight] slides inside the
 * [inWidth] x [inHeight] frame following the subject's [points].
 */
data class FollowCropTrack(
    val points: List<SubjectPoint>,
    val inWidth: Int,
    val inHeight: Int,
    val outWidth: Int,
    val outHeight: Int,
    /** The file the subject was tracked in (its uri); empty when unknown. */
    val sourceKey: String = "",
) {
    init {
        require(points.isNotEmpty()) { "A follow crop needs at least one look at the subject" }
        require(inWidth > 0 && inHeight > 0) { "A follow crop needs the frame's size" }
        // A window wider than the frame would break the smoothing's clamping.
        require(outWidth in 2..inWidth && outHeight in 2..inHeight) { "The crop window must fit inside the frame" }
    }

    /**
     * The crop window's centre in frame pixels at [atMs]: the [windowMs]-wide
     * confidence-weighted moving average of the points around it, clamped so
     * the window never leaves the frame.
     */
    fun centerAt(atMs: Long, windowMs: Long = SMOOTH_WINDOW_MS): Pair<Float, Float> {
        val near = points.filter { kotlin.math.abs(it.atMs - atMs) <= windowMs / 2 }
        val usable = if (near.isEmpty()) listOf(points.minByOrNull { kotlin.math.abs(it.atMs - atMs) }!!) else near
        var weightSum = 0f
        var x = 0f
        var y = 0f
        for (point in usable) {
            val w = point.confidence.coerceIn(0.05f, 1f)
            x += point.x * w
            y += point.y * w
            weightSum += w
        }
        // The clamp's ends stay ordered even for a window as wide as the frame.
        val cx = (x / weightSum * inWidth).coerceIn(outWidth / 2f, (inWidth - outWidth / 2f).coerceAtLeast(outWidth / 2f))
        val cy = (y / weightSum * inHeight).coerceIn(outHeight / 2f, (inHeight - outHeight / 2f).coerceAtLeast(outHeight / 2f))
        return cx to cy
    }

    /**
     * The points sampled onto an even grid of at most [maxKeys] keys, already
     * smoothed: what the filter expression is built from.
     */
    fun keyframes(maxKeys: Int = MAX_KEYS): List<Triple<Long, Float, Float>> {
        val first = points.minOf { it.atMs }
        val last = points.maxOf { it.atMs }
        val keys = maxKeys.coerceAtLeast(2)
        val step = if (last <= first) 1L else (last - first) / (keys - 1).toLong().coerceAtLeast(1L)
        return (0 until keys).map { k ->
            val at = (first + k * step).coerceAtMost(last)
            val (cx, cy) = centerAt(at)
            Triple(at, (cx - outWidth / 2f), (cy - outHeight / 2f))
        }
    }

    /**
     * The crop window's left (or top) at [tSeconds], as the filter expression
     * computes it: a piecewise-linear walk of [keys] (time seconds, position
     * pixels). Exposed so the tests can check the expression against it.
     */
    fun positionAt(keys: List<Triple<Long, Float, Float>>, axis: Int, tSeconds: Double): Float {
        val times = keys.map { it.first / 1000.0 }
        val values = keys.map { if (axis == 0) it.second else it.third }
        if (tSeconds <= times.first()) return values.first()
        if (tSeconds >= times.last()) return values.last()
        var position = values.first()
        for (k in 1 until values.size) {
            val span = times[k] - times[k - 1]
            if (span <= 0.0) continue
            val ramp = ((tSeconds - times[k - 1]) / span).coerceIn(0.0, 1.0)
            position += ((values[k] - values[k - 1]) * ramp).toFloat()
        }
        return position
    }

    /**
     * The FFmpeg filter that reframes the video following the subject:
     * `crop=w:h:x=expr:y=expr`, x and y piecewise-linear in `t` (written as a
     * sum of ramps, the form FFmpeg's expression parser handles well). Commas
     * inside the expressions are escaped for the filter graph.
     */
    fun cropFilter(maxKeys: Int = MAX_KEYS): String {
        val keys = keyframes(maxKeys)
        val x = rampExpression(keys, axis = 0)
        val y = rampExpression(keys, axis = 1)
        return "crop=w=$outWidth:h=$outHeight:x='$x':y='$y'"
    }

    private fun rampExpression(keys: List<Triple<Long, Float, Float>>, axis: Int): String {
        val times = keys.map { it.first / 1000.0 }
        val values = keys.map { if (axis == 0) it.second else it.third }
        val expression = StringBuilder(num(values.first()))
        for (k in 1 until values.size) {
            val span = times[k] - times[k - 1]
            if (span <= 0.0) continue
            val delta = values[k] - values[k - 1]
            if (delta == 0f) continue
            expression.append(if (delta > 0) '+' else '-')
            expression.append(num(kotlin.math.abs(delta)))
            // Commas stay raw: the expression is a single-quoted option value
            // (as the other filters of the app quote theirs).
            expression.append("*clip((t-")
            expression.append(num(times[k - 1].toFloat()))
            expression.append(")/")
            expression.append(num(span.toFloat()))
            expression.append(",0,1)")
        }
        return expression.toString()
    }

    companion object {
        private const val SMOOTH_WINDOW_MS = 800L

        /** The most keys written into one expression (longer strings parse slowly). */
        const val MAX_KEYS = 48

        /** A number as FFmpeg reads it: plain ASCII, never scientific. */
        private fun num(v: Float): String =
            if (v == v.toLong().toFloat()) v.toLong().toString() else String.format(Locale.US, "%.3f", v)

        /**
         * The largest window of [ratioW]:[ratioH] that fits [inWidth] x
         * [inHeight], both sides even (encoders ask for that).
         */
        fun windowSize(inWidth: Int, inHeight: Int, ratioW: Int, ratioH: Int): Pair<Int, Int> {
            var w = inWidth
            var h = (w.toLong() * ratioH / ratioW).toInt()
            if (h > inHeight) {
                h = inHeight
                w = (h.toLong() * ratioW / ratioH).toInt()
            }
            return (w / 2 * 2).coerceAtLeast(2) to (h / 2 * 2).coerceAtLeast(2)
        }
    }
}

/** Packs a track's points for keeping in a project (see ModelJson). */
fun List<SubjectPoint>.packed(): List<Float> = flatMap { listOf(it.atMs.toFloat(), it.x, it.y, it.confidence) }

/** Back from [packed]; [atMs] is rounded back to the millisecond. */
fun List<Float>.unpackedPoints(): List<SubjectPoint> =
    chunked(4).mapNotNull { q ->
        if (q.size < 4) null else SubjectPoint(q[0].roundToLong(), q[1], q[2], q[3])
    }
