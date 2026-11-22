package com.cuscus.ffmpeggui.core.editing

import com.cuscus.ffmpeggui.core.command.FfmpegCommand

/**
 * Reads where a file's scenes change from FFmpeg's log: the frames the
 * `select=gt(scene,...)` filter lets through are printed by `showinfo`,
 * each with its `pts_time`.
 */
object SceneDetect {
    private val PTS_TIME = Regex("""pts_time:\s*(-?[0-9]+(?:\.[0-9]+)?)""")

    /** Every `pts_time` of [log], in seconds, in the order printed. */
    fun parseShowinfo(log: String): List<Double> =
        log.lineSequence().mapNotNull { PTS_TIME.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }.toList()

    /**
     * The scene changes worth splitting at, in milliseconds: consecutive
     * changes nearer than [minGapMs] collapse into the stronger side's
     * boundary (the later one), and nothing lands nearer than [edgeMs] to the
     * ends of a [durationMs]-long file (0: unknown ends).
     */
    fun splitPoints(ptsSeconds: List<Double>, durationMs: Long = 0L, minGapMs: Long = 1500L, edgeMs: Long = 300L): List<Long> {
        val times = ptsSeconds.map { (it * 1000).toLong() }.filter { it > edgeMs && (durationMs <= 0 || it < durationMs - edgeMs) }.sorted()
        val kept = mutableListOf<Long>()
        for (time in times) {
            val last = kept.lastOrNull()
            if (last != null && time - last < minGapMs) kept[kept.lastIndex] = time else kept += time
        }
        return kept
    }

    /**
     * The command that logs the frames where the picture changes by more than
     * [threshold] (0..1; lower finds more changes). No output file is made.
     */
    fun command(input: String, threshold: Double = DEFAULT_THRESHOLD) = FfmpegCommand(
        listOf(
            "-hide_banner", "-nostats", "-i", input,
            // The comma inside gt() is escaped for the filter graph; the whole
            // graph is one argument, no shell is involved.
            "-vf", "select=gt(scene\\,$threshold),showinfo",
            "-an", "-f", "null", "-",
        ),
    )

    const val DEFAULT_THRESHOLD = 0.35
}
