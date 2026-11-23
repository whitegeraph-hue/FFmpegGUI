package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.timeline.Timeline
import java.io.File

/** A selected occurrence's sound, not every occurrence of the same file. */
object DesktopClipAudio {
    fun snapshot(project:DesktopProject,clipId:String):DesktopProject {
        val clip=project.timeline.clips.firstOrNull {it.id==clipId}
            ?: error("The selected clip is no longer on the timeline")
        val source=project.sources.firstOrNull {it.path==clip.sourceId}
            ?: error("The selected clip source is unavailable")
        require(source.info.hasAudio && !source.info.isStill) {"The selected clip has no audio"}
        // No neighbour follows this isolated clip, so it has no outgoing transition.
        val one=clip.copy(effects=clip.effects.copy(transition=null))
        val selected=DesktopProject(listOf(source),Timeline(listOf(one)),format=OutputFormat.WAV)
        require(selected.outputDurationMs in 1..600_000) {"Select a clip with up to ten minutes of played audio"}
        return selected
    }
    suspend fun <T> use(project:DesktopProject,clipId:String,media:DesktopMedia,action:suspend (String)->T):T {
        val selected=snapshot(project,clipId)
        val wav=File.createTempFile("ffmpeggui-clip-audio-",".wav").apply {delete()}
        try {
            DesktopExport(media).export(selected,wav)
            return action(wav.path)
        } finally {wav.delete()}
    }
}
