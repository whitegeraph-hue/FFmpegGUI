package com.cuscus.ffmpeggui.core.editing

import com.cuscus.ffmpeggui.core.timeline.Timeline

/** A range of the edited sequence to take out, on the timeline clock. */
data class CutRange(val startMs: Long, val endMs: Long, val reason: String = "") {
    val lengthMs: Long get() = endMs - startMs
}

private const val MIN_CUT_MS = 40L

/**
 * Sorted, clamped to [0, durationMs], overlaps and ranges closer than
 * [minGapMs] joined (the joined range keeps both reasons). Ranges shorter
 * than a moment are dropped.
 */
fun List<CutRange>.normalized(durationMs: Long, minGapMs: Long = 0L): List<CutRange> {
    val sorted = map {
        it.copy(startMs = it.startMs.coerceAtLeast(0L), endMs = it.endMs.coerceAtMost(durationMs))
    }.filter { it.endMs - it.startMs >= MIN_CUT_MS }.sortedBy { it.startMs }
    val merged = mutableListOf<CutRange>()
    for (range in sorted) {
        val last = merged.lastOrNull()
        if (last != null && range.startMs - last.endMs <= minGapMs) {
            merged[merged.lastIndex] = last.copy(
                endMs = maxOf(last.endMs, range.endMs),
                reason = listOf(last.reason, range.reason).filter { it.isNotEmpty() }.distinct().joinToString("+"),
            )
        } else {
            merged += range
        }
    }
    return merged
}

/**
 * Splits at every one of [points] that falls inside the footage (each second
 * half gets an id of [nextId]). Points too near a cut or an edge to split are
 * skipped, as [Timeline.splitAt] says.
 */
fun Timeline.splitAtAll(points: List<Long>, nextId: () -> String): Timeline {
    var out = this
    for (point in points.distinct().sorted()) {
        if (out.canSplitAt(point)) out = out.splitAt(point, nextId())
    }
    return out
}

/**
 * The timeline without [ranges] (normalized first). Unchanged when that would
 * leave nothing to play: an edit that removes everything is never made.
 */
fun Timeline.withoutRanges(ranges: List<CutRange>, nextId: () -> String): Timeline {
    val cuts = ranges.normalized(durationMs)
    if (cuts.isEmpty()) return this
    val split = splitAtAll(cuts.flatMap { listOf(it.startMs, it.endMs) }, nextId)
    var start = 0L
    val kept = split.clips.filter { clip ->
        val end = start + clip.lengthMs
        val inside = cuts.any { start >= it.startMs && end <= it.endMs }
        start = end
        !inside
    }
    if (kept.isEmpty() || kept == split.clips) return this
    return split.copy(clips = kept)
}
