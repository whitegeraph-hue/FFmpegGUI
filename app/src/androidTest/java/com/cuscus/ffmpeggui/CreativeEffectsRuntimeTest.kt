package com.cuscus.ffmpeggui

import android.graphics.RuntimeShader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.command.*
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.media.FfmpegKitExecutor
import com.cuscus.ffmpeggui.core.sound.OriginalSound
import com.cuscus.ffmpeggui.core.look.lookShader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlinx.coroutines.runBlocking

/** Actual Android controls, shader compiler and bundled FFmpeg, not source-only parity claims. */
class CreativeEffectsRuntimeTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun telephoneAndOriginalCueControls() {
        var fx by mutableStateOf(AudioEffects.NONE)
        var cue:OriginalSound?=null
        rule.setContent { Column(Modifier.verticalScroll(rememberScrollState())) {
            AudioEffectsContent(fx) {_,change->fx=change(fx)}
            OriginalSoundPicker {cue=it}
        }}
        rule.onNodeWithText("Old telephone").performScrollTo().performClick()
        rule.runOnIdle {assertEquals(CreativeVoice.TELEPHONE.effects,fx)}
        ShellShot.take("creative-telephone-selected")
        rule.onNodeWithText("Original sound cues").performScrollTo().performClick()
        rule.onNodeWithText("Air whoosh").performScrollTo().performClick()
        rule.runOnIdle {assertEquals(OriginalSound.WHOOSH,cue)}
        ShellShot.take("creative-original-sounds")
    }
    @Test fun telephonePreservesSixteenKhzSpeech() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        for(rate in listOf(8000,16000)) for(fx in CreativeVoice.entries.map{it.effects}+AudioEffects(equalizer=List(10){12f})) {
        val input=File(context.cacheDir,"creative-speech${rate/1000}.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open(input.name).use {src->input.outputStream().use{src.copyTo(it)}}
        val reader=android.media.MediaMetadataRetriever();reader.setDataSource(input.path)
        val duration=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong();reader.release()
        val output=File(context.cacheDir,"creative-telephone-speech.wav")
        val req=ConversionRequest(OutputFormat.WAV,input.name,sources=listOf(SourceInfo(duration,0,0,true)),segments=listOf(Segment(0,0,duration)),audioEffects=fx)
        val command=CommandBuilder{ "/fonts/Regular.ttf" }.build(req,MediaPaths(inputs=listOf(input.path),output=output.path))
        assertEquals(ExecutionResult.Success,FfmpegKitExecutor().execute(command){})
        val bytes=output.readBytes();val buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var offset=12;var channels=0;var nonzero=false;var samples=0
        while(offset+8<=bytes.size) {
            val size=buffer.getInt(offset+4);require(size>=0 && offset+8L+size<=bytes.size)
            when(String(bytes,offset,4)) {
                "fmt " -> channels=buffer.getShort(offset+10).toInt()
                "data" -> { for(i in offset+8 until offset+8+size step 2) {if(buffer.getShort(i).toInt()!=0)nonzero=true;samples++} }
            }
            offset+=8+size+(size and 1)
        }
        if(fx.stereo.mono) assertEquals("telephone mono",1,channels)
        assertTrue("$rate Hz voice preset must retain speech samples: $fx",nonzero && samples>10000)
        }
    }

    @Test fun grittyVoicesFiniteAtRelevantRatesAndLevels() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val speech=File(context.cacheDir,"gritty-speech.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("creative-speech16.wav").use{src->speech.outputStream().use{src.copyTo(it)}}
        val executor=FfmpegKitExecutor()
        for(rate in listOf(8000,16000,48000)) for(level in listOf(0,1,8)) {
            val input=File(context.cacheDir,"gritty-input.wav")
            assertEquals(ExecutionResult.Success,executor.execute(FfmpegCommand(listOf("-y","-i",speech.path,"-t","1","-af","volume=$level","-ar",rate.toString(),input.path))) {})
            for(voice in CreativeVoice.entries.filter{it.effects.lofiBits>0}+CreativeVoice.TELEPHONE) {
                val output=File(context.cacheDir,"gritty-output.wav")
                val req=ConversionRequest(OutputFormat.WAV,input.name,sources=listOf(SourceInfo(1000,0,0,true)),segments=listOf(Segment(0,0,1000)),audioEffects=voice.effects)
                val command=CommandBuilder{"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(input.path),output=output.path))
                // Float WAV preserves NaN/Inf, unlike integer PCM which can hide it.
                val args=command.arguments.map{if(it=="pcm_s16le")"pcm_f32le" else it}
                assertTrue("Float diagnostic must replace int16 encoder",args.contains("pcm_f32le") && !args.contains("pcm_s16le"))
                val logs=StringBuilder();val result=executor.execute(FfmpegCommand(args)){logs.append(it)}
                assertEquals("$voice $rate level$level ${logs.takeLast(1000)}",ExecutionResult.Success,result)
                val raw=File(context.cacheDir,"gritty.f32")
                assertEquals(ExecutionResult.Success,executor.execute(FfmpegCommand(listOf("-y","-i",output.path,"-ac","1","-f","f32le",raw.path))) {})
                val data=java.nio.ByteBuffer.wrap(raw.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN);var nonzero=false
                while(data.remaining()>=4) {val sample=data.float;assertTrue("$voice $rate level$level non-finite",sample.isFinite());if(kotlin.math.abs(sample)>.00001f)nonzero=true}
                if(level>0)assertTrue("$voice $rate level$level erased speech",nonzero)
                raw.delete();output.delete()
            }
        }
    }

    @Test fun allNewShadersCompileAndRealPhoneRenders() = runBlocking {
        PictureLook.entries.filter{it.ordinal>=PictureLook.SEPIA.ordinal}.forEach {RuntimeShader(lookShader(it))}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val input=File(context.cacheDir,"creative-source.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("ci-test-video.mp4").use{src->input.outputStream().use{src.copyTo(it)}}
        val executor=FfmpegKitExecutor()
        fun request()=ConversionRequest(OutputFormat.MP4,input.name,sources=listOf(SourceInfo(8000,640,360,true)),segments=listOf(Segment(0,0,1000)))
        val cases=PictureLook.entries.filter{it.ordinal>=PictureLook.SEPIA.ordinal}.map{request().copy(looks=listOf(LookSpan(it,0,900)))}+
            CreativeVoice.entries.map{request().copy(audioEffects=it.effects)}+
            TransitionKind.entries.filter{it.ordinal in TransitionKind.VENETIAN.ordinal..TransitionKind.CROSS_REVEAL.ordinal}.map{kind->request().copy(segments=listOf(Segment(0,0,1000,Effects(transition=Transition(kind,300))),Segment(0,4000,5000)))}
        val failures=mutableListOf<String>()
        for((index,req) in cases.withIndex()) {
            val output=File(context.cacheDir,"creative-export-$index.mp4")
            val command=CommandBuilder{ "/fonts/Regular.ttf" }.build(req,MediaPaths(inputs=listOf(input.path),output=output.path))
            val logs=StringBuilder()
            val result=executor.execute(command){logs.append(it)}
            if(result!=ExecutionResult.Success || output.length()<=1000) failures+="case $index looks=${req.looks} voice=${req.audioEffects} result=$result command=${command.arguments} logs=${logs.takeLast(10000)}"
            if(result==ExecutionResult.Success && req.audioEffects!=AudioEffects.NONE) {
                val raw=File(context.cacheDir,"telephone-final.f32")
                val decoded=executor.execute(FfmpegCommand(listOf("-y","-i",output.path,"-vn","-ac","1","-ar","48000","-f","f32le",raw.path))){}
                assertEquals(ExecutionResult.Success,decoded)
                val data=java.nio.ByteBuffer.wrap(raw.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                var nonzero=false;while(data.remaining()>=4) {val sample=data.float;assertTrue("Voice AAC decoded non-finite sample",sample.isFinite());if(kotlin.math.abs(sample)>0.00001f)nonzero=true}
                assertTrue("Voice AAC must retain content",nonzero);raw.delete()
            }
            output.delete()
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
        for(cue in OriginalSound.entries) {
            val file=File(context.cacheDir,"cue-${cue.name}.wav");file.outputStream().use{cue.writeWav(it)}
            val reader=android.media.MediaMetadataRetriever();reader.setDataSource(file.path)
            assertNotNull(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));reader.release();file.delete()
        }
    }
}
