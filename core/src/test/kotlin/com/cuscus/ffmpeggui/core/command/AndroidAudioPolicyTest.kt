package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety as Safety
import com.cuscus.ffmpeggui.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AndroidAudioPolicyTest {
    private val runtime=AudioRuntime.ANDROID_BUNDLED_6_1_6
    private fun built(fx:AudioEffects):FfmpegCommand {
        val req=ConversionRequest(OutputFormat.WAV,"in.wav",sources=listOf(SourceInfo(1000,0,0,true)),segments=listOf(Segment(0,0,1000)),audioEffects=fx)
        return CommandBuilder(runtime){"font.ttf"}.build(req,MediaPaths(inputs=listOf("in.wav"),output="out.wav"))
    }
    @Test fun onlyUnchangedAndroidBuilderCommandPassesAndroidBoundary() {
        val cmd=built(CreativeVoice.ALIEN.effects)
        assertNull(Safety.commandIssue(cmd,runtime))
        assertNotNull(Safety.commandIssue(cmd))
        assertNotNull(Safety.commandIssue(FfmpegCommand(cmd.arguments),runtime))
        assertNotNull(Safety.commandIssue(cmd.copy(),runtime))
        assertNotNull(Safety.commandIssue(cmd.copy(arguments=cmd.arguments+listOf("-af","vibrato=f=3")),runtime))
        assertNotNull(Safety.commandIssue(built(CreativeVoice.LOFI.effects),runtime))
    }
    @Test fun argumentMutationAndBadBuilderPatternsFailClosed() {
        val cmd=built(CreativeVoice.ALIEN.effects)
        val args=cmd.arguments as MutableList<String>
        args.addAll(listOf("-af","vibrato=f=3"))
        assertNotNull(Safety.commandIssue(cmd,runtime))
        for(graph in listOf("vibrato=f=3:d=.3","aresample=48000,adelay=20S:all=1,vibrato=f=3:d=.3,atrim=start_sample=20,asetpts=PTS-STARTPTS","aresample=48000,adelay=16000S:all=1,vibrato=f=3:d=.3,atrim=start_sample=16000,asetpts=PTS-STARTPTS,acrusher=bits=9")) {
            // Simulate a builder regression. Even issued commands need the graph check.
            val malformed=FfmpegCommand(listOf("-af",graph)).builtForInitializedAndroid()
            assertNotNull(Safety.commandIssue(malformed,runtime))
        }
        val good=built(CreativeVoice.GHOST.effects)
        assertNotNull(Safety.commandIssue(good,AudioRuntime.SYSTEM))
    }
    @Test fun nativeManualAliasesAndScriptsStillFailClosed() {
        for(option in listOf("-af","-filter:a","-filter_complex","-lavfi","-vf","-graph"))
            for(graph in listOf("vibrato=f=3","anull,vib\\rato=f=3","'vibrato'=f=3","[in]vibrato@voice=f=3[out]","aresample=48000,adelay=16000S:all=1,vibrato=f=3:d=.3,atrim=start_sample=16000,asetpts=PTS-STARTPTS")) {
                assertNotNull(Safety.commandIssue(FfmpegCommand(listOf(option,graph)),runtime))
                assertNotNull(Safety.commandIssue(FfmpegCommand(listOf("$option=$graph")),runtime))
            }
        for(option in listOf("-filter_complex_script","-filter_script:a","-/af","-/filter_complex","-graph_file"))
            assertNotNull(Safety.commandIssue(FfmpegCommand(listOf(option,"graph.txt")),runtime))
    }
}
