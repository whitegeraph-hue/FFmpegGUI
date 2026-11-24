package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AndroidVibratoInitializationTest {
    @Test fun initializationIsExplicitRuntimeOnlyAndAllScopes() {
        for(voice in listOf(CreativeVoice.ALIEN,CreativeVoice.GHOST)) for(scope in listOf("output","clip","sound")) {
            val req=ConversionRequest(OutputFormat.WAV,"in.wav",sources=listOf(SourceInfo(1000,0,0,true)),segments=listOf(Segment(0,0,1000,if(scope=="clip")Effects(audioEffects=voice.effects)else Effects())),audioEffects=if(scope=="output")voice.effects else AudioEffects.NONE,sounds=if(scope=="sound")listOf(PlacedSound(trimEndMs=1000,effects=voice.effects))else emptyList())
            val paths=MediaPaths(inputs=listOf("in.wav"),output="out.wav",sounds=if(scope=="sound")listOf("sound.wav")else emptyList())
            val system=CommandBuilder{"font.ttf"}.build(req,paths).arguments.joinToString(" ")
            val android=CommandBuilder(AudioRuntime.ANDROID_BUNDLED_6_1_6){"font.ttf"}.build(req,paths).arguments.joinToString(" ")
            assertTrue(system.contains("vibrato="));assertFalse(system.contains("atrim=start_sample="))
            val match=Regex("adelay=(\\d+)S:all=1,vibrato=f=([0-9.]+):d=[0-9.]+,atrim=start_sample=(\\d+),asetpts=PTS-STARTPTS").find(android)!!
            assertEquals(match.groupValues[1],match.groupValues[3])
            assertEquals(Math.rint(48000.0/match.groupValues[2].toDouble()+.5).toLong(),match.groupValues[1].toLong())
            assertTrue(android.substring(0,match.range.first).contains("aresample=48000"))
            // Implementation is not permission to execute or a saved-setting migration.
            assertNotNull(com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety.requestIssue(req))
            assertNotNull(com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety.commandIssue(FfmpegCommand(listOf("-af",android))))
        }
    }
}
