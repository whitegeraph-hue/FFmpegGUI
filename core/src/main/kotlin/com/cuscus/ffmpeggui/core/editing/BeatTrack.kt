package com.cuscus.ffmpeggui.core.editing

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Finds the beats of a piece of music from its sound: no model, no network,
 * the samples alone. Onsets are the moments the energy jumps; the tempo is
 * the spacing the onsets agree on most; the beats are a grid at that spacing
 * placed where the onsets are strongest.
 *
 * The input is what the app's audio extractor writes for speech: mono 32-bit
 * floats at 16 kHz. Any other rate works the same.
 */
object BeatTrack {

    /** What [analyze] found. [beatsMs] is the grid to snap edits to. */
    data class Result(
        /** The energy jumps, in milliseconds. */
        val onsetsMs: List<Long>,
        /** The agreed spacing, or null when the onsets do not settle on one. */
        val tempoBpm: Double?,
        /** The beat grid, in milliseconds (the onsets themselves when no tempo is found). */
        val beatsMs: List<Long>,
    )

    /**
     * The beats of [samples] ([sampleRate] Hz mono). [durationMs] (0: the
     * samples' own length) caps the grid.
     */
    fun analyze(samples: FloatArray, sampleRate: Int, durationMs: Long = 0L): Result {
        if (samples.size < sampleRate) return Result(emptyList(), null, emptyList())
        val hop = (sampleRate * HOP_MS / 1000).coerceAtLeast(1)
        val energy = frameEnergy(samples, hop)
        val flux = FloatArray(energy.size) { i -> if (i == 0) 0f else max(0f, energy[i] - energy[i - 1]) }
        val onsets = peakPicking(flux, hopMs = HOP_MS)
        val total = if (durationMs > 0) durationMs else samples.size * 1000L / sampleRate
        val tempo = estimateTempo(flux, hopMs = HOP_MS)
        val beats = if (tempo != null) grid(flux, tempo, HOP_MS, total) else onsets
        return Result(onsets, tempo, beats.filter { it in 1 until total })
    }

    /** Each [timesMs] moved to the nearest beat at most [maxDistanceMs] away; the others stay. */
    fun snap(timesMs: List<Long>, beatsMs: List<Long>, maxDistanceMs: Long = 150L): List<Long> {
        if (beatsMs.isEmpty()) return timesMs
        return timesMs.map { time ->
            val nearest = beatsMs.minByOrNull { abs(it - time) } ?: return@map time
            if (abs(nearest - time) <= maxDistanceMs) nearest else time
        }
    }

    /** The nearest beat to [timeMs] at most [maxDistanceMs] away, or null. */
    fun nearest(beatsMs: List<Long>, timeMs: Long, maxDistanceMs: Long = 150L): Long? =
        beatsMs.minByOrNull { abs(it - timeMs) }?.takeIf { abs(it - timeMs) <= maxDistanceMs }

    // ---------------------------------------------------------------------

    /** Log energy of every [hop]-sample frame (the log keeps quiet detail audible). */
    private fun frameEnergy(samples: FloatArray, hop: Int): FloatArray {
        val frames = samples.size / hop
        val out = FloatArray(frames)
        var i = 0
        var frame = 0
        while (frame < frames) {
            var sum = 0.0
            for (j in 0 until hop) {
                val s = samples[i++]
                sum += s * s
            }
            out[frame] = (ln(sum / hop + 1e-9) * 0.5).toFloat()
            frame++
        }
        return out
    }

    /** Local maxima of [flux] above a moving average of their neighbourhood. */
    private fun peakPicking(flux: FloatArray, hopMs: Int): List<Long> {
        val window = (1000 / hopMs).coerceAtLeast(1) // one second to each side
        val minGap = (MIN_ONSET_MS / hopMs).toInt().coerceAtLeast(1)
        val onsets = mutableListOf<Long>()
        var lastPeak = -minGap
        for (i in 1 until flux.size - 1) {
            if (flux[i] <= 0f) continue
            var sum = 0f
            var count = 0
            for (j in (i - window).coerceAtLeast(0)..(i + window).coerceAtMost(flux.size - 1)) {
                sum += flux[j]
                count++
            }
            if (flux[i] < (sum / count) * THRESHOLD + FLOOR) continue
            if (flux[i] < flux[i - 1] || flux[i] < flux[i + 1]) continue
            if (i - lastPeak < minGap) {
                // Two peaks inside one beat's length: the stronger one stands.
                if (onsets.isNotEmpty() && flux[i] > flux[(onsets.last() / hopMs).toInt()]) {
                    onsets[onsets.lastIndex] = i.toLong() * hopMs
                    lastPeak = i
                }
                continue
            }
            onsets += i.toLong() * hopMs
            lastPeak = i
        }
        return onsets
    }

    /**
     * The tempo the onsets of [flux] agree on: the lag (between [MIN_BPM] and
     * [MAX_BPM]) the flux's autocorrelation likes most; null when nothing
     * stands out (speech-like sound, silence).
     */
    private fun estimateTempo(flux: FloatArray, hopMs: Int): Double? {
        // The slowest beat's lag has to recur a few times for the
        // autocorrelation to mean anything (about five seconds at MIN_BPM).
        val slowestLag = (60000.0 / MIN_BPM / hopMs).roundToInt()
        if (flux.size < slowestLag * 4) return null
        val minLag = (60000.0 / MAX_BPM / hopMs).roundToInt().coerceAtLeast(1)
        val maxLag = (60000.0 / MIN_BPM / hopMs).roundToInt().coerceAtMost(flux.size / 2)
        if (maxLag <= minLag) return null
        var mean = 0.0
        for (v in flux) mean += v
        mean /= flux.size
        var bestLag = -1
        var bestScore = 0.0
        for (lag in minLag..maxLag) {
            var score = 0.0
            for (i in lag until flux.size) score += (flux[i] - mean) * (flux[i - lag] - mean)
            // A true tempo's multiples agree too: a little of twice the lag joins the score.
            if (lag * 2 < flux.size) {
                var twice = 0.0
                for (i in lag * 2 until flux.size) twice += (flux[i] - mean) * (flux[i - lag * 2] - mean)
                score += twice * 0.5
            }
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0 || bestScore <= 0.0) return null
        return 60000.0 / (bestLag * hopMs)
    }

    /**
     * A beat grid [tempoBpm] fast over [totalMs]: the phase (within one beat)
     * under which the grid points land on the strongest flux wins.
     */
    private fun grid(flux: FloatArray, tempoBpm: Double, hopMs: Int, totalMs: Long): List<Long> {
        val intervalMs = 60000.0 / tempoBpm
        val intervalHops = intervalMs / hopMs
        var bestPhase = 0.0
        var bestEnergy = -1.0
        var phase = 0.0
        while (phase < intervalMs) {
            var energy = 0.0
            var t = phase
            while ((t / hopMs).roundToInt() < flux.size) {
                energy += flux[(t / hopMs).roundToInt()]
                t += intervalMs
            }
            if (energy > bestEnergy) {
                bestEnergy = energy
                bestPhase = phase
            }
            phase += intervalHops / 8.0
        }
        val beats = mutableListOf<Long>()
        var t = bestPhase
        while (t < totalMs) {
            beats += t.roundToLong()
            t += intervalMs
        }
        return beats
    }

    private const val HOP_MS = 10
    private const val MIN_ONSET_MS = 240L
    private const val THRESHOLD = 1.4f
    private const val FLOOR = 1e-4f
    private const val MIN_BPM = 50
    private const val MAX_BPM = 220
}
