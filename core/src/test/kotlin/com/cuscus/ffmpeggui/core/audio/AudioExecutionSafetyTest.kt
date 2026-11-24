package com.cuscus.ffmpeggui.core.audio

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.project.ModelJson
import org.junit.Assert.*
import org.junit.Test

class AudioExecutionSafetyTest {
    private fun issue(vararg args: String) = AudioExecutionSafety.commandIssue(FfmpegCommand(args.toList()))
    @Test fun `all graph aliases instances labels and escaped names blocked`() {
        for (option in listOf("-af", "-filter:a", "-filter:0", "-filter_complex", "-lavfi", "-vf", "-graph")) {
            for (graph in listOf("vibrato=f=3", "[a] acrusher@voice=bits=9[out]", "anull;[in]vibrato[out]", "anull,vib\\rato=f=3", "'vibrato'=f=3")) {
                assertNotNull("$option $graph",issue(option,graph))
                assertNotNull(issue("$option=$graph"))
            }
        }
        assertNotNull(issue("-f","lavfi","-i","sine,acrusher=bits=9"))
    }
    @Test fun `backslash inside quotes is literal and quote still closes`() {
        val slash=92.toChar()
        for(filter in listOf("vibrato","acrusher")) {
            assertNotNull(issue("-filter_complex", "drawtext=text='x${slash}',$filter=f=3"))
            assertNotNull(issue("-filter_complex", "drawtext=text=x${slash}',$filter=f=3"))
            assertNull(issue("-filter_complex", "drawtext=text='x${slash},$filter=f=3'"))
        }
        assertNull(issue("-f","lavfi","-i","anullsrc","-f","wav","-i","vibrato=f=3"))
    }
    @Test fun `options and filenames are not filters`() {
        for(graph in listOf("drawtext=text='a,vibrato=3;acrusher=8'", "drawtext=text=a\\,vibrato\\=3", "amovie='/tmp/vibrato.wav',anull", "[vibrato]anull[acrusher]")) {
            assertNull(graph,issue("-filter_complex",graph))
        }
        assertNull(issue("-i","/tmp/vibrato.wav","-metadata","title=acrusher", "output.wav"))
        assertNull(issue("-af","tremolo=f=3,anull"))
    }
    @Test fun `graph file inputs refused rather than checked then reread`() {
        for(option in listOf("-filter_complex_script", "-filter_script:a", "-/filter_complex", "-/filter:a", "-/af", "-/vf", "-/lavfi", "-graph_file", "-/graph")) {
            assertNotNull(issue(option,"/tmp/graph.txt"))
            assertNotNull(issue("$option=/tmp/graph.txt"))
        }
    }
    @Test fun `detector covers preset filters and preserves every saved recipe`() {
        val blocked=mutableSetOf<CreativeVoice>()
        for(voice in CreativeVoice.entries) {
            val original=ModelJson.write(voice.effects)
            val filters=voice.effects.stages().flatMap {AudioExecutionSafety.filterNames(it.filter)}.toSet()
            assertEquals(voice.name,filters.any{it=="vibrato" || it=="acrusher"},!AudioExecutionSafety.available(voice.effects))
            assertEquals(voice.effects,ModelJson.audioEffects(original))
            assertEquals(original,ModelJson.write(voice.effects))
            if(!AudioExecutionSafety.available(voice.effects))blocked+=voice
        }
        assertEquals(setOf(CreativeVoice.ALIEN,CreativeVoice.GHOST,CreativeVoice.RADIO,CreativeVoice.ROBOT,CreativeVoice.WALKIE,CreativeVoice.LOFI),blocked)
        assertTrue(AudioExecutionSafety.available(CreativeVoice.TELEPHONE.effects))
    }
    @Test fun `loaded clip sound and whole output scopes retained and explained`() {
        val effects=ModelJson.audioEffects(ModelJson.write(CreativeVoice.ALIEN.effects))
        val request=ConversionRequest(OutputFormat.WAV,"in.wav",audioEffects=effects,segments=listOf(Segment(0,effects=Effects(audioEffects=effects))),sounds=listOf(PlacedSound(effects=effects)))
        val snapshot=request.copy()
        val issue=AudioExecutionSafety.requestIssue(request)!!
        assertTrue(issue.contains("whole output") && issue.contains("clip 1") && issue.contains("sound 1"))
        assertEquals(snapshot,request)
        assertNull(AudioExecutionSafety.requestIssue(request.copy(audioEffects=AudioEffects.NONE,segments=listOf(Segment(0)),sounds=emptyList())))
    }
}
