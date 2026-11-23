package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.preview.mapToTimeline
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import java.io.File

/** Inference uses raw selected footage; edits keep the recognizer's source clock. */
object DesktopSpeech {
    fun validateStored(words:Map<String,List<SpokenWord>>) {
        require(words.size<=10000 && words.values.sumOf {it.size.toLong()}<=200000L) {"Too many clip words. Save fewer recognized clip groups in this project."}
    }
    fun words(project:DesktopProject,source:Int,clipId:String?):List<SpokenWord> {
        val clip=project.timeline.clips.firstOrNull {it.id==clipId}
        val words=clip?.let {project.wordsByClip[it.speechKey ?: it.id]} ?: project.sourceWords(source)
        return if(clip==null)words else words.filter {it.endMs>clip.startMs && it.startMs<(clip.endMs ?: Long.MAX_VALUE)}
            .map {it.copy(startMs=maxOf(it.startMs,clip.startMs),endMs=minOf(it.endMs,clip.endMs ?: Long.MAX_VALUE))}
    }
    fun apply(project:DesktopProject,source:Int,clipId:String?,words:List<SpokenWord>):DesktopProject {
        if(clipId==null)return project.copy(wordsBySource=project.wordsBySource+(project.sources[source].path to words))
        check(project.timeline.clips.any {it.id==clipId}) {"The selected clip is no longer on the timeline"}
        val key=java.util.UUID.randomUUID().toString()
        val timeline=project.timeline.copy(clips=project.timeline.clips.map {if(it.id==clipId)it.copy(speechKey=key)else it})
        val used=timeline.clips.map {it.speechKey ?: it.id}.toSet()
        val stored=(project.wordsByClip+(key to words)).filterKeys {it in used}
        validateStored(stored)
        return project.copy(timeline=timeline,wordsByClip=stored)
    }
    fun timedWords(project:DesktopProject):List<SpokenWord> {
        val segments=project.requestSegments()
        return project.timeline.clips.flatMap {clip->
            val source=project.sources.indexOfFirst {it.path==clip.sourceId}
            val scoped=segments.map {if(it.clipId==clip.id)it else it.copy(source=-1)}
            words(project,source,clip.id).flatMap {word->mapToTimeline(word.startMs,word.endMs,scoped,source,project.effects.reversed)
                .map {(a,b)->SpokenWord(a,b,word.text)}}
        }.sortedBy {it.startMs}
    }
    suspend fun <T> useRawClip(project:DesktopProject,clipId:String,media:DesktopMedia,action:suspend (String,Long,Long)->T):T {
        val clip=project.timeline.clips.firstOrNull {it.id==clipId} ?: error("The selected clip is no longer on the timeline")
        val source=project.sources.first {it.path==clip.sourceId}
        require(source.info.hasAudio && !source.info.isStill) {"The selected clip has no audio"}
        val end=clip.endMs ?: source.info.durationMs
        require(end>clip.startMs && end-clip.startMs<=600_000) {"Select up to ten minutes of source audio"}
        val wav=File.createTempFile("ffmpeggui-speech-clip-",".wav")
        try {
            media.process.checked(listOf("ffmpeg","-nostdin","-v","error","-y","-ss",(clip.startMs/1000.0).toString(),"-i",source.path,"-t",((end-clip.startMs)/1000.0).toString(),"-vn","-ac","1","-ar","16000","-c:a","pcm_s16le",wav.path))
            return action(wav.path,clip.startMs,end)
        } finally {wav.delete()}
    }
    fun onSourceClock(words:List<SpokenWord>,start:Long,end:Long)=words.mapNotNull {w->
        val a=(w.startMs+start).coerceIn(start,end);val b=(w.endMs+start).coerceIn(start,end)
        if(b>a)w.copy(startMs=a,endMs=b)else null
    }
}
