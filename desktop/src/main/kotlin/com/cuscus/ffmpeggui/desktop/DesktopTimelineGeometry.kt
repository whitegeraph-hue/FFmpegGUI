package com.cuscus.ffmpeggui.desktop

/** Output-clock clip spans. Transitions overlap rather than stretching the ruler. */
internal data class DesktopClipSpan(val index:Int,val startMs:Long,val endMs:Long,val backwards:Boolean)
internal fun DesktopProject.outputClipSpans():List<DesktopClipSpan> {
    val clock=clock()
    return timeline.clips.mapIndexed {i,_->
        val begin=clock.joinedStartMs(i)
        val end=begin+clock.clipPosition(timeline.startOf(i)).playedLengthMs
        val start=if(effects.reversed)clock.joinedDurationMs-end else begin
        val finish=if(effects.reversed)clock.joinedDurationMs-begin else end
        DesktopClipSpan(i,(start/clock.speed).toLong().coerceAtLeast(0),(finish/clock.speed).toLong().coerceAtMost(outputDurationMs),clock.playsBackwards(i))
    }
}
