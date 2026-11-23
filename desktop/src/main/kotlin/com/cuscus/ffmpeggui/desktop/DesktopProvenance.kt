package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import com.cuscus.ffmpeggui.core.provenance.*

/** Preserve existing machine-readable AI picture disclosure, not a claim to detect unmarked AI. */
object DesktopProvenance {
    fun aiPicture(file:File):Boolean {
        file.inputStream().buffered().use {input->
            val xmp=AiMarksReader.xmp(input)
            if(xmp!=null && AiMarksReader.changedWithAi(xmp))return true
        }
        return RandomAccessFile(file,"r").use {input->
            val length=input.length();val window=2_000_000
            val head=ByteArray(minOf(length,window.toLong()).toInt());input.readFully(head)
            if(AiMarksReader.scan(head).ai)return@use true
            if(length>window) {
                input.seek(maxOf(window.toLong(),length-window))
                val tail=ByteArray((length-input.filePointer).toInt());input.readFully(tail)
                AiMarksReader.scan(tail).ai
            } else false
        }
    }
    fun needed(project:DesktopProject)=project.sources.any {it.aiPicture} || project.pictures.any {it.activeOn(project.timeline) && it.source.aiPicture}
    suspend fun seal(project:DesktopProject,file:File) {
        if(!needed(project))return
        val disclosure=AiSeal.disclosureFor(file.path,AiDisclosure(pictures=true))
        if(disclosure.isEmpty)return
        val way=AiSeal.wayFor(file.path)
        require(way!=AiSeal.Way.NONE) {"This format cannot preserve the source's AI picture disclosure. Choose PNG, JPG, WebP, TIFF, SVG or MP4."}
        // Several original picture writers use whole-file byte arrays. Bound that path explicitly.
        require(way !in setOf(AiSeal.Way.PNG,AiSeal.Way.JPEG,AiSeal.Way.WEBP,AiSeal.Way.GIF,AiSeal.Way.TIFF,AiSeal.Way.SVG) || file.length()<=32_000_000) {"AI-disclosed picture output exceeds the safe metadata-writing size (32 MB). Reduce output dimensions or quality."}
        if(way==AiSeal.Way.TAGS) {
            val tagged=File(file.parentFile,".disclosed-${UUID.randomUUID()}.${file.extension}")
            try {
                DesktopProcess().checked(listOf("ffmpeg","-nostdin","-v","error")+AiSeal.remux(file.path,tagged.path,disclosure).arguments)
                require(aiPicture(tagged)) {"AI picture disclosure was not retained in the container tags"}
                Files.move(tagged.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING)
            } finally {tagged.delete()}
            return
        }
        check(AiSeal.mark(file,disclosure) in setOf(AiSeal.Marked.WRITTEN,AiSeal.Marked.TAGS_ONLY)) {"Could not preserve AI picture disclosure. No output was replaced."}
    }
}
