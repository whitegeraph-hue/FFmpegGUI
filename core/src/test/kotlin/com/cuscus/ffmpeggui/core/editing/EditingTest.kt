package com.cuscus.ffmpeggui.core.editing

import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EditingTest {

    // ------------------------------------------------------------------
    // CutPlan
    // ------------------------------------------------------------------

    @Test
    fun `normalized joins overlaps and drops slivers`() {
        val ranges = listOf(
            CutRange(5000, 6000),
            CutRange(1000, 2000),
            CutRange(1900, 2500),
            CutRange(8000, 8010), // too short to keep
            CutRange(-500, 900),  // clamped away: shorter than 40 ms once clamped? no: 0..900 stays
        )
        val out = ranges.normalized(10_000)
        assertEquals(listOf(CutRange(0, 900), CutRange(1000, 2500), CutRange(5000, 6000)), out)
    }

    @Test
    fun `withoutRanges takes the middle out and keeps the rest`() {
        val timeline = Timeline(listOf(Clip("a", "main", 0, 10_000)))
        var n = 0
        val out = timeline.withoutRanges(listOf(CutRange(2000, 3000))) { "new${n++}" }
        assertEquals(9_000L, out.durationMs)
        assertEquals(2, out.clips.size)
        assertEquals(0L to 2_000L, out.clips[0].startMs to out.clips[0].endMs)
        assertEquals(3_000L to 10_000L, out.clips[1].startMs to out.clips[1].endMs)
    }

    @Test
    fun `withoutRanges never empties the timeline`() {
        val timeline = Timeline(listOf(Clip("a", "main", 0, 10_000)))
        val out = timeline.withoutRanges(listOf(CutRange(0, 10_000))) { "x" }
        assertEquals(timeline, out)
    }

    @Test
    fun `splitAtAll splits at every inside point`() {
        val timeline = Timeline(listOf(Clip("a", "main", 0, 10_000)))
        var n = 0
        val out = timeline.splitAtAll(listOf(4_000, 7_000, 50)) { "new${n++}" }
        // 50 ms is too near the edge to split; the other two land.
        assertEquals(3, out.clips.size)
        assertEquals(10_000L, out.durationMs)
    }

    // ------------------------------------------------------------------
    // SilenceDetect
    // ------------------------------------------------------------------

    @Test
    fun `silencedetect log is read into spans`() {
        val log = """
            [silencedetect @ 0x123] silence_start: 2.5
            [silencedetect @ 0x123] silence_end: 4.0 | silence_duration: 1.5
            other noise
            [silencedetect @ 0x123] silence_start: 8
        """.trimIndent()
        assertEquals(listOf(2_500L to 4_000L, 8_000L to 9_000L), SilenceDetect.parse(log, durationMs = 9_000))
        assertEquals(listOf(2_500L to 4_000L), SilenceDetect.parse(log))
    }

    // ------------------------------------------------------------------
    // PauseCuts
    // ------------------------------------------------------------------

    private fun said(vararg words: Pair<Long, String>) =
        words.map { (start, text) -> SpokenWord(start, start + 300, text) }

    @Test
    fun `long gaps become padded cuts`() {
        val words = said(0L to "hello", 2_000L to "world")
        val cuts = PauseCuts.pauses(words, silences = emptyList(), minPauseMs = 700, padMs = 80)
        assertEquals(listOf(CutRange(380, 1_920, PauseCuts.REASON_PAUSE)), cuts)
    }

    @Test
    fun `detected silence widens the cut inside the gap`() {
        val words = said(0L to "hello", 3_000L to "world")
        val silences = listOf(200L to 2_950L)
        val cuts = PauseCuts.pauses(words, silences, minPauseMs = 700, padMs = 80)
        // The silence starts before the first word ends, so the cut starts where the word ends.
        assertEquals(listOf(CutRange(300, 2_920, PauseCuts.REASON_PAUSE)), cuts)
    }

    @Test
    fun `fillers are found, real words are not`() {
        val words = said(0L to "I", 500L to "um,", 1_000L to "think")
        val cuts = PauseCuts.fillers(words, padMs = 60)
        assertEquals(listOf(CutRange(440, 860, PauseCuts.REASON_FILLER)), cuts)
        assertEquals("um", PauseCuts.normalizedWord("Um,"))
    }

    @Test
    fun `suggest merges a filler inside a pause`() {
        val words = said(0L to "well", 700L to "uh", 3_000L to "yes")
        val cuts = PauseCuts.suggest(words, emptyList(), durationMs = 4_000, minPauseMs = 700)
        assertEquals(1, cuts.size)
        assertTrue(cuts[0].startMs >= 300L)
        assertTrue(cuts[0].endMs <= 2_920L)
    }

    // ------------------------------------------------------------------
    // SceneDetect
    // ------------------------------------------------------------------

    @Test
    fun `showinfo pts times become split points`() {
        val log = "[Parsed_showinfo_1] n: 10 pts_time:5.25\n[Parsed_showinfo_1] n: 11 pts_time:5.5\n[Parsed_showinfo_1] n: 40 pts_time:20\n"
        val pts = SceneDetect.parseShowinfo(log)
        assertEquals(listOf(5.25, 5.5, 20.0), pts)
        // 5.5 collapses into 5.25 (nearer than the 1.5 s gap); 20 stands.
        assertEquals(listOf(5_500L, 20_000L), SceneDetect.splitPoints(pts))
    }

    @Test
    fun `scene command escapes the comma`() {
        val command = SceneDetect.command("in.mp4")
        val vf = command.arguments[command.arguments.indexOf("-vf") + 1]
        assertEquals("select=gt(scene\\,0.35),showinfo", vf)
    }

    // ------------------------------------------------------------------
    // BeatTrack
    // ------------------------------------------------------------------

    /** A 16 kHz click track: 30 ms noise bursts every [beatMs]. */
    private fun clicks(seconds: Int, beatMs: Long, seed: Int = 42): FloatArray {
        val rate = 16_000
        val out = FloatArray(seconds * rate)
        var state = seed
        fun next(): Float {
            state = (state * 1103515245 + 12345) and 0x7fffffff
            return state / 0x3fffffff.toFloat() - 1f
        }
        var beat = 0L
        while (beat < seconds * 1000L) {
            val start = (beat * rate / 1000).toInt()
            for (i in 0 until 30 * rate / 1000) out[start + i] = next() * 0.9f
            beat += beatMs
        }
        return out
    }

    @Test
    fun `a 120 bpm click track is read as 120 bpm`() {
        val result = BeatTrack.analyze(clicks(seconds = 10, beatMs = 500), 16_000)
        // The very first click has no rise (nothing before it): 19 onsets of 20 clicks.
        assertEquals(19, result.onsetsMs.size)
        assertNotNull(result.tempoBpm)
        assertTrue("tempo ${result.tempoBpm}", abs(result.tempoBpm!! - 120.0) < 2.0)
        assertTrue("beats ${result.beatsMs.size}", result.beatsMs.size in 19..21)
    }

    @Test
    fun `silence has no tempo`() {
        val result = BeatTrack.analyze(FloatArray(16_000 * 5), 16_000)
        assertTrue(result.onsetsMs.isEmpty())
        assertNull(result.tempoBpm)
        assertTrue(result.beatsMs.isEmpty())
    }

    @Test
    fun `snap moves near times onto beats and leaves far ones`() {
        val beats = listOf(0L, 500L, 1_000L)
        assertEquals(listOf(0L, 500L, 5_000L), BeatTrack.snap(listOf(40L, 480L, 5_000L), beats, maxDistanceMs = 150))
        assertEquals(500L, BeatTrack.nearest(beats, 480))
        assertNull(BeatTrack.nearest(beats, 300, maxDistanceMs = 100))
    }

    // ------------------------------------------------------------------
    // FollowCrop
    // ------------------------------------------------------------------

    private fun track(): FollowCropTrack {
        val (w, h) = FollowCropTrack.windowSize(1920, 1080, 9, 16)
        return FollowCropTrack(
            points = (0..20).map { SubjectPoint(it * 1_000L, 0.2f + it * 0.03f, 0.5f, 0.5f) },
            inWidth = 1920, inHeight = 1080, outWidth = w, outHeight = h,
        )
    }

    @Test
    fun `the vertical window fits the frame`() {
        assertEquals(606 to 1080, FollowCropTrack.windowSize(1920, 1080, 9, 16))
        assertEquals(1080 to 1080, FollowCropTrack.windowSize(1080, 1920, 1, 1))
    }

    @Test
    fun `the window never leaves the frame`() {
        val edge = FollowCropTrack(
            points = listOf(SubjectPoint(0, 0.0f, 1.0f, 1f)),
            inWidth = 1920, inHeight = 1080, outWidth = 606, outHeight = 1080,
        )
        val (cx, cy) = edge.centerAt(0)
        assertEquals(303f, cx, 0.01f)
        assertEquals(540f, cy, 0.01f)
    }

    @Test
    fun `the smoothed centre follows the subject`() {
        val t = track()
        val (cx, _) = t.centerAt(10_000)
        // Subject at 10 s: x = 0.5 of 1920 = 960, smoothed nearby.
        assertEquals(960f, cx, 60f)
    }

    @Test
    fun `the filter is a crop with a balanced escaped expression`() {
        val filter = track().cropFilter()
        assertTrue(filter.startsWith("crop=w=606:h=1080:x='"))
        assertTrue(filter.endsWith("'"))
        assertFalse(filter.contains(", ")) // no filter-graph comma leaks
        val inner = filter.removePrefix("crop=w=606:h=1080:x='")
        var depth = 0
        inner.forEach { c -> if (c == '(') depth++; if (c == ')') depth--; assertTrue(depth >= 0) }
        assertEquals(0, depth)
        assertTrue(filter.contains("clip("))
    }

    @Test
    fun `follow crop joins the export chain and sets the frame`() {
        val builder = com.cuscus.ffmpeggui.core.command.CommandBuilder { _ -> "/fonts/Regular.ttf" }
        val track = FollowCropTrack(
            points = listOf(SubjectPoint(0, 0.5f, 0.5f, 1f), SubjectPoint(2_000, 0.6f, 0.5f, 1f)),
            inWidth = 1920, inHeight = 1080, outWidth = 606, outHeight = 1080,
        )
        val request = com.cuscus.ffmpeggui.core.model.ConversionRequest(
            format = com.cuscus.ffmpeggui.core.model.OutputFormat.MP4,
            inputFileName = "clip.mov",
            sources = listOf(com.cuscus.ffmpeggui.core.model.SourceInfo(durationMs = 20_000, width = 1920, height = 1080)),
            followCrop = track,
        )
        val args = builder.build(request, com.cuscus.ffmpeggui.core.command.MediaPaths.aliasesFor(request)).arguments.joinToString(" ")
        assertTrue("crop in command", args.contains("crop=w=606:h=1080"))
    }

    @Test
    fun `positionAt walks the keys linearly`() {
        val t = track()
        val keys = listOf(Triple(0L, 100f, 0f), Triple(1_000L, 200f, 0f), Triple(2_000L, 100f, 0f))
        assertEquals(150f, t.positionAt(keys, 0, 0.5), 0.01f)
        assertEquals(150f, t.positionAt(keys, 0, 1.5), 0.01f)
        assertEquals(100f, t.positionAt(keys, 0, 0.0), 0.01f)
        assertEquals(100f, t.positionAt(keys, 0, 5.0), 0.01f)
    }

    @Test
    fun `points pack and unpack`() {
        val points = listOf(SubjectPoint(1234L, 0.25f, 0.5f, 0.75f))
        assertEquals(points, points.packed().unpackedPoints())
    }

    // ------------------------------------------------------------------
    // TranscriptEdit
    // ------------------------------------------------------------------

    @Test
    fun `words group into blocks at breaths`() {
        val words = said(0L to "one", 400L to "two", 3_000L to "three")
        val (sorted, blocks) = words.wordBlocks()
        assertEquals(2, blocks.size)
        assertEquals("one two", blocks[0].text)
        assertEquals(0 to 1, blocks[0].firstIndex to blocks[0].lastIndex)
        assertEquals(2 to 2, blocks[1].firstIndex to blocks[1].lastIndex)
        assertEquals(sorted, words.sortedBy { it.startMs })
    }

    @Test
    fun `cutting a selection keeps air around the neighbours`() {
        val words = said(0L to "a", 1_000L to "b", 2_000L to "c")
        val cut = words.cutRangeFor(1, 1, padMs = 60)!!
        assertEquals(CutRange(940, 1_300 + 60L, TranscriptEdits.REASON_TRANSCRIPT), cut)
    }

    @Test
    fun `keeping a selection cuts before and after it`() {
        val words = said(1_000L to "a", 2_000L to "b", 3_000L to "c")
        val cuts = TranscriptEdits.keepOnly(words, 1, 1, durationMs = 10_000)
        assertEquals(listOf(CutRange(0, 2_000, TranscriptEdits.REASON_TRANSCRIPT), CutRange(2_300, 10_000, TranscriptEdits.REASON_TRANSCRIPT)), cuts)
    }
}
