package com.cuscus.ffmpeggui.core.editing

import com.cuscus.ffmpeggui.core.subtitles.SpokenWord

/**
 * A sentence-like run of words of a transcript: words follow each other
 * closer than a breath apart. The transcript editor shows and selects these.
 */
data class WordBlock(
    val firstIndex: Int,
    val lastIndex: Int,
    val words: List<SpokenWord>,
) {
    val startMs: Long get() = words.first().startMs
    val endMs: Long get() = words.last().endMs
    val text: String get() = words.joinToString(" ") { it.text }
}

/**
 * The words of a transcript grouped for editing: a gap of [gapMs] or more
 * ends a block. [words] need not be sorted; the blocks and their indices
 * refer to the sorted order.
 */
fun List<SpokenWord>.wordBlocks(gapMs: Long = 1000L): Pair<List<SpokenWord>, List<WordBlock>> {
    val said = sortedBy { it.startMs }
    if (said.isEmpty()) return said to emptyList()
    val blocks = mutableListOf<WordBlock>()
    var first = 0
    for (i in 1..said.size) {
        val endsHere = i == said.size || said[i].startMs - said[i - 1].endMs >= gapMs
        if (endsHere) {
            blocks += WordBlock(first, i - 1, said.subList(first, i))
            first = i
        }
    }
    return said to blocks
}

/** The cut that takes the words [firstIndex]..[lastIndex] out, [padMs] of air around them. */
fun List<SpokenWord>.cutRangeFor(firstIndex: Int, lastIndex: Int, padMs: Long = 60L): CutRange? {
    val said = sortedBy { it.startMs }
    if (firstIndex !in said.indices || lastIndex !in said.indices || lastIndex < firstIndex) return null
    val before = said.getOrNull(firstIndex - 1)?.endMs ?: 0L
    val after = said.getOrNull(lastIndex + 1)?.startMs ?: Long.MAX_VALUE
    val from = (said[firstIndex].startMs - padMs).coerceAtLeast(before)
    val to = (said[lastIndex].endMs + padMs).coerceAtMost(after)
    return if (to > from) CutRange(from, to, TranscriptEdits.REASON_TRANSCRIPT) else null
}

object TranscriptEdits {
    const val REASON_TRANSCRIPT = "transcript"

    /**
     * The cuts that keep only the words [keepFirst]..[keepLast] of [words] in
     * a file [durationMs] long: everything before and after them.
     */
    fun keepOnly(words: List<SpokenWord>, keepFirst: Int, keepLast: Int, durationMs: Long): List<CutRange> {
        val said = words.sortedBy { it.startMs }
        if (keepFirst !in said.indices || keepLast !in said.indices || keepLast < keepFirst) return emptyList()
        val cuts = mutableListOf<CutRange>()
        if (said[keepFirst].startMs > 0L) cuts += CutRange(0L, said[keepFirst].startMs, REASON_TRANSCRIPT)
        if (durationMs > said[keepLast].endMs) cuts += CutRange(said[keepLast].endMs, durationMs, REASON_TRANSCRIPT)
        return cuts
    }
}
