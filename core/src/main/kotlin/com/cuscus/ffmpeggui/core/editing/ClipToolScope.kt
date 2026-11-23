package com.cuscus.ffmpeggui.core.editing

import com.cuscus.ffmpeggui.core.timeline.Timeline

/** Source detections mapped to unsped, unreversed editor footage, one clip occurrence only. */
object ClipToolScope {
    fun remove(timeline:Timeline,cuts:List<CutRange>,nextId:()->String):Timeline {
        var offset=0L
        val clips=timeline.clips.flatMap {clip->
            val base=offset;offset+=clip.lengthMs
            val local=cuts.mapNotNull {cut->val a=maxOf(0,cut.startMs-base);val b=minOf(clip.lengthMs,cut.endMs-base);if(b>a)a to b else null}.sortedBy {it.first}
            if(local.isEmpty())listOf(clip) else {
                var spans=listOf(0L to clip.lengthMs)
                for((a,b) in local)spans=spans.flatMap {(x,y)->if(a>=y || b<=x)listOf(x to y)else listOf(x to minOf(a,y),maxOf(b,x) to y).filter {it.second>it.first}}
                val parts=spans.mapIndexed {i,(a,b)->clip.copy(id=if(i==0)clip.id else nextId(),startMs=clip.startMs+a,endMs=clip.startMs+b,effects=clip.effects.windowed(a.toDouble()/clip.lengthMs,b.toDouble()/clip.lengthMs))}
                val ordered=if(clip.effects.reversed)parts.reversed()else parts
                ordered.mapIndexed {i,part->part.copy(effects=part.effects.copy(fadeInSeconds=if(i==0)clip.effects.fadeInSeconds else 0f,fadeOutSeconds=if(i==ordered.lastIndex)clip.effects.fadeOutSeconds else 0f,transition=if(i==ordered.lastIndex)clip.effects.transition else null))}
            }
        }
        return if(clips.isEmpty())timeline else Timeline(clips)
    }

    fun cuts(timeline:Timeline,clipId:String?,sourceId:String,cuts:List<CutRange>):List<CutRange> {
        var offset=0L
        return timeline.clips.flatMap {clip->
            val start=offset;offset+=clip.lengthMs
            if(clip.sourceId!=sourceId || (clipId!=null && clip.id!=clipId))emptyList() else cuts.mapNotNull {cut->
                val a=maxOf(cut.startMs,clip.startMs);val b=minOf(cut.endMs,clip.endMs ?: clip.startMs)
                if(b>a)CutRange(start+a-clip.startMs,start+b-clip.startMs,cut.reason)else null
            }
        }
    }
    fun points(timeline:Timeline,clipId:String?,sourceId:String,points:List<Long>):List<Long> =
        cuts(timeline,clipId,sourceId,points.map {CutRange(it,it+1,"point")}).map {it.startMs}.filter {at->
            val pos=timeline.locate(at) ?: return@filter false
            pos.offsetMs>0 && pos.offsetMs<timeline.clips[pos.index].lengthMs
        }.distinct().sorted()
}
