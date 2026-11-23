package com.cuscus.ffmpeggui.desktop

import java.io.File
import com.cuscus.ffmpeggui.core.preview.*
import com.cuscus.ffmpeggui.core.model.ConversionRequest

/** Embedded source-clock subtitle content. No external file is executed or retained. */
data class DesktopSubtitles(val name:String,val content:String,val sourceIndex:Int,val enabled:Boolean=true) {
    companion object {
        fun import(file:File,sourceIndex:Int):DesktopSubtitles {
            require(file.isFile && file.length() in 1..2_000_000) {"Choose an SRT, ASS or SSA file up to 2 MB"}
            require(file.extension.lowercase() in listOf("srt","ass","ssa")) {"Choose SRT, ASS or SSA"}
            return DesktopSubtitles(file.name,file.readText(Charsets.UTF_8),sourceIndex).also {it.validate()}
        }
        fun cuesOnTimeline(value:DesktopSubtitles,request:ConversionRequest):List<SubtitleCue> = SubtitleParser().parse(value.content).onTimeline(request.segments,value.sourceIndex,request.effects.reversed)
    }
    fun validate() {
        require(content.toByteArray(Charsets.UTF_8).size<=2_000_000 && SubtitleParser().parse(content).isNotEmpty()) {"No usable subtitle cues or content exceeds 2 MB"}
    }
}
