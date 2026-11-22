package com.cuscus.ffmpeggui.core.editing

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord

/**
 * Reads the silences FFmpeg's silencedetect filter reports on its log, as
 * (start, end) pairs in milliseconds on the file's clock.
 */
object SilenceDetect {
    private val START = Regex("""silence_start:\s*(-?[0-9]+(?:\.[0-9]+)?)""")
    private val END = Regex("""silence_end:\s*(-?[0-9]+(?:\.[0-9]+)?)""")

    /**
     * The silences of [log]. A silence still open at the end of the log runs
     * to [durationMs] when that is known, else it is dropped.
     */
    fun parse(log: String, durationMs: Long = 0L): List<Pair<Long, Long>> {
        val out = mutableListOf<Pair<Long, Long>>()
        var start = -1L
        for (line in log.lineSequence()) {
            START.find(line)?.let { start = (it.groupValues[1].toDouble() * 1000).toLong() }
            END.find(line)?.let { m ->
                val end = (m.groupValues[1].toDouble() * 1000).toLong()
                if (start >= 0 && end > start) out += start to end
                start = -1
            }
        }
        if (start >= 0 && durationMs > start) out += start to durationMs
        return out
    }

    /** The command that reports the silences of [input] on its log (no output file). */
    fun command(input: String, noiseDb: Int = DEFAULT_NOISE_DB, minSeconds: Double = DEFAULT_MIN_SECONDS) = FfmpegCommand(
        listOf(
            "-hide_banner", "-nostats", "-i", input,
            "-vn", "-af", "silencedetect=noise=${noiseDb}dB:d=$minSeconds",
            "-f", "null", "-",
        ),
    )

    const val DEFAULT_NOISE_DB = -35
    const val DEFAULT_MIN_SECONDS = 0.4
}

/**
 * Suggested cuts of the quiet and the hesitations of a file whose speech was
 * heard ([SpokenWord]s on the file's clock).
 */
object PauseCuts {

    /**
     * Hesitation sounds looked for, compared lowercase without punctuation.
     * Short interjections only: words that can carry meaning ("like", "so")
     * are never suggested away on their own.
     */
    val DEFAULT_FILLERS = setOf("um", "uh", "uhm", "umm", "erm", "er", "ah", "eh", "hmm", "mm", "mmm")

    /** The [text] of a word as fillers are compared: lowercase, letters only. */
    fun normalizedWord(text: String): String = text.lowercase().filter { it.isLetter() }

    /**
     * The quiet stretches between words worth cutting: where the next word
     * starts at least [minPauseMs] after the one before ends. The cut keeps
     * [padMs] of air on both sides so speech is never nibbled, and when the
     * detected [silences] reach further than the words' gap the cut follows
     * them (still inside the gap, still padded).
     */
    fun pauses(
        words: List<SpokenWord>,
        silences: List<Pair<Long, Long>>,
        minPauseMs: Long = 700L,
        padMs: Long = 80L,
    ): List<CutRange> {
        val said = words.sortedBy { it.startMs }
        val cuts = mutableListOf<CutRange>()
        for (i in 1 until said.size) {
            val gapStart = said[i - 1].endMs
            val gapEnd = said[i].startMs
            if (gapEnd - gapStart < minPauseMs) continue
            var from = gapStart + padMs
            var to = gapEnd - padMs
            val silence = silences.firstOrNull { it.first <= gapStart + minPauseMs / 2 && it.second >= gapEnd - minPauseMs / 2 }
                ?: silences.firstOrNull { it.first < gapEnd && it.second > gapStart }
            if (silence != null) {
                from = minOf(from, silence.first + padMs).coerceIn(gapStart, gapEnd)
                to = maxOf(to, silence.second - padMs).coerceIn(gapStart, gapEnd)
            }
            if (to > from) cuts += CutRange(from, to, REASON_PAUSE)
        }
        return cuts
    }

    /**
     * The hesitation words worth cutting: [fillers] said on their own, no
     * longer than [maxWordMs], with [padMs] of air around them (never into a
     * neighbouring word).
     */
    fun fillers(
        words: List<SpokenWord>,
        fillers: Set<String> = DEFAULT_FILLERS,
        maxWordMs: Long = 900L,
        padMs: Long = 60L,
    ): List<CutRange> {
        val said = words.sortedBy { it.startMs }
        return said.mapIndexedNotNull { i, word ->
            if (normalizedWord(word.text) !in fillers) return@mapIndexedNotNull null
            if (word.endMs - word.startMs > maxWordMs) return@mapIndexedNotNull null
            val before = said.getOrNull(i - 1)?.endMs ?: 0L
            val after = said.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
            val from = (word.startMs - padMs).coerceAtLeast(before)
            val to = (word.endMs + padMs).coerceAtMost(after)
            if (to > from) CutRange(from, to, REASON_FILLER) else null
        }
    }

    /** Pauses and fillers together, ready to take out of a timeline of [durationMs]. */
    fun suggest(
        words: List<SpokenWord>,
        silences: List<Pair<Long, Long>>,
        durationMs: Long,
        minPauseMs: Long = 700L,
        padMs: Long = 80L,
        fillers: Set<String> = DEFAULT_FILLERS,
    ): List<CutRange> =
        (pauses(words, silences, minPauseMs, padMs) + fillers(words, fillers)).normalized(durationMs, minGapMs = 120L)

    const val REASON_PAUSE = "pause"
    const val REASON_FILLER = "filler"
}
