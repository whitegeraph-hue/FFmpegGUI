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
            val bytes=file.inputStream().use {it.readNBytes(2_000_001)}
            require(bytes.size<=2_000_000) {"Subtitle file exceeds 2 MB"}
            val charset=when {
                bytes.size>=2 && bytes[0]==0xff.toByte() && bytes[1]==0xfe.toByte()->Charsets.UTF_16LE
                bytes.size>=2 && bytes[0]==0xfe.toByte() && bytes[1]==0xff.toByte()->Charsets.UTF_16BE
                else->Charsets.UTF_8
            }
            val text=try {charset.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")}
                catch(e:java.nio.charset.CharacterCodingException) {throw IllegalArgumentException("Subtitle encoding is not supported. Save as UTF-8 or UTF-16 with a BOM.",e)}
            return DesktopSubtitles(file.name,text,sourceIndex).also {it.validate()}
        }
        fun cuesOnTimeline(value:DesktopSubtitles,request:ConversionRequest):List<SubtitleCue> = SubtitleParser().parse(value.content).onTimeline(request.segments,value.sourceIndex,request.effects.reversed)
    }
    fun validate() {
        require(content.toByteArray(Charsets.UTF_8).size<=2_000_000 && SubtitleParser().parse(content).isNotEmpty()) {"No usable subtitle cues or content exceeds 2 MB"}
    }
}
