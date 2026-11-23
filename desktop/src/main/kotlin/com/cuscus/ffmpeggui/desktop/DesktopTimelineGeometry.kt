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

/** A clicked lane owns the seek even while two clips overlap in a transition. */
internal fun DesktopProject.mediaForClipOutput(index:Int,outputMs:Long):Long {
    val clip=timeline.clips[index]
    val span=outputClipSpans()[index]
    val played=if(effects.reversed)span.endMs-outputMs else outputMs-span.startMs
    val local=com.cuscus.ffmpeggui.core.preview.PreviewTimeline(listOf(
        com.cuscus.ffmpeggui.core.preview.PreviewTimeline.SegmentTiming(clip.lengthMs,clip.effects.speed,clip.effects.reversed,clip.effects.speedCurve,clip.effects.keepPitch)
    ))
    val offset=local.mediaMsForOutput((played*clock().speed).toLong()).coerceIn(0,(clip.lengthMs-1).coerceAtLeast(0))
    return timeline.startOf(index)+offset
}

/** Insertion in visible output order, converted back to stored clip order on global reverse. */
internal fun DesktopProject.reorderTarget(draggedId:String,outputMs:Long):Int? {
    if(timeline.clips.none {it.id==draggedId})return null
    val remaining=outputClipSpans().filter {timeline.clips[it.index].id!=draggedId}.sortedBy {it.startMs+(it.endMs-it.startMs)/2}
    val visual=remaining.count {outputMs>it.startMs+(it.endMs-it.startMs)/2}
    return if(effects.reversed)remaining.size-visual else visual
}

/** Insertion marker uses the remaining occurrences, not the dragged clip's old slot. */
internal fun DesktopProject.reorderMarker(draggedId:String,target:Int):Long {
    val remaining=outputClipSpans().filter {timeline.clips[it.index].id!=draggedId}.sortedBy {it.startMs}
    val visual=(if(effects.reversed)remaining.size-target else target).coerceIn(0,remaining.size)
    return remaining.getOrNull(visual)?.startMs ?: remaining.lastOrNull()?.endMs ?: 0L
}
