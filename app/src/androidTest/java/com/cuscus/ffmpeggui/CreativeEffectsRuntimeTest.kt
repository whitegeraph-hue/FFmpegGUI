package com.cuscus.ffmpeggui

import com.cuscus.ffmpeggui.core.audio.stages
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
    @Test fun suspectGraphsRejectedBeforeNativeOutputAndSavedEffectCanBeRemoved(): Unit = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val output=File(context.cacheDir,"blocked-native-output.wav");output.delete()
        for(option in listOf("-af","-filter:a","-filter_complex","-lavfi","-filter_complex_script","-/filter_complex")) {
            val graph=if(option.contains("script") || option.startsWith("-/")) "/missing/graph.txt" else "vibrato=f=3"
            val logs=StringBuilder()
            val result=FfmpegKitExecutor().execute(FfmpegCommand(listOf("-f","lavfi","-i","anullsrc",option,graph,output.path))){logs.append(it)}
            assertEquals(ExecutionResult.Failed(com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety.RETURN_CODE),result)
            assertTrue(logs.isNotBlank());assertFalse(output.exists())
        }
        var fx by mutableStateOf(CreativeVoice.ALIEN.effects)
        rule.setContent {Column(Modifier.verticalScroll(rememberScrollState())) {AudioEffectsContent(fx){_,change->fx=change(fx)}}}
        rule.onNodeWithText("This scope retains a blocked effect. Use Off, turn off lo-fi, or choose Clean / reset. Saving keeps your settings.").assertExists()
        rule.runOnIdle {assertEquals(CreativeVoice.ALIEN.effects,fx)}
        ShellShot.take("blocked-effect-retained-warning")
        rule.onNodeWithText("Clean / reset").performScrollTo().performClick()
        rule.runOnIdle {assertEquals(AudioEffects.NONE,fx)}
    }
    @Test fun allVoiceRecipesPreserveEightAndSixteenKhzSpeech() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        for(rate in listOf(8000,16000)) for(fx in CreativeVoice.entries.filter{com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety.available(it.effects)}.map{it.effects}+AudioEffects(equalizer=List(10){12f})) {
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
        val failures=mutableListOf<String>()
        for(rate in listOf(8000,16000,48000)) for(level in listOf(0,1,8)) for(channels in listOf(1,2)) {
            val input=File(context.cacheDir,"gritty-input.wav")
            assertEquals(ExecutionResult.Success,executor.execute(FfmpegCommand(listOf("-y","-i",speech.path,"-t","1","-af","volume=$level","-ar",rate.toString(),"-ac",channels.toString(),input.path))) {})
            for((name,fx) in CreativeVoice.entries.filter{it!=CreativeVoice.CLEAN && com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety.available(it.effects)}.map{it.name to it.effects}) {
                val output=File(context.cacheDir,"gritty-output.wav")
                val req=ConversionRequest(OutputFormat.WAV,input.name,sources=listOf(SourceInfo(1000,0,0,true)),segments=listOf(Segment(0,0,1000)),audioEffects=fx)
                val command=CommandBuilder{"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(input.path),output=output.path))
                // Float WAV preserves NaN/Inf, unlike integer PCM which can hide it.
                val args=command.arguments.map{if(it=="pcm_s16le")"pcm_f32le" else it}
                assertTrue("Float diagnostic must replace int16 encoder",args.contains("pcm_f32le") && !args.contains("pcm_s16le"))
                val logs=StringBuilder();val result=executor.execute(FfmpegCommand(args)){logs.append(it)}
                assertEquals("$name $rate level$level ${logs.takeLast(1000)}",ExecutionResult.Success,result)
                val raw=File(context.cacheDir,"gritty.f32")
                assertEquals(ExecutionResult.Success,executor.execute(FfmpegCommand(listOf("-y","-i",output.path,"-ac","1","-f","f32le",raw.path))) {})
                val data=java.nio.ByteBuffer.wrap(raw.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN);var nonzero=false
                var finite=true;var peak=0f
                while(data.remaining()>=4) {val sample=data.float;if(!sample.isFinite())finite=false else peak=maxOf(peak,kotlin.math.abs(sample));if(kotlin.math.abs(sample)>.00001f)nonzero=true}
                android.util.Log.i("CATALOG-PEAK","$name $rate level$level ch$channels finite=$finite nonzero=$nonzero peak=$peak")
                if(!finite || peak>4f || (level>0 && !nonzero) || (level==0 && nonzero))failures+="$name $rate level$level ch$channels finite=$finite nonzero=$nonzero peak=$peak"
                raw.delete();output.delete()
            }
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }


    /** Diagnostic only: preserve the delivered recipes and collect every stage-prefix result. */
    @Test fun manualDspStagePrefixDiagnostics(): Unit = runBlocking {
        org.junit.Assume.assumeTrue("Explicit isolated native diagnostic only", InstrumentationRegistry.getArguments().getString("unsafeDspDiagnostics") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val executor=IsolatedDiagnosticExecutor()
        val speech=File(context.cacheDir,"manual-dsp-speech.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("creative-speech16.wav").use{src->speech.outputStream().use{src.copyTo(it)}}
        val recipes=listOf(
            "telephone-old" to CreativeVoice.TELEPHONE.effects.copy(lofiBits=10),
            "tape-old" to AudioEffects(highCutHz=6500,lofiBits=12,modulation=Modulation(ModulationKind.VIBRATO,.7f,.12f),limiter=Limiter(-2f)),
            "tape-no-crusher" to AudioEffects(highCutHz=6500,modulation=Modulation(ModulationKind.VIBRATO,.7f,.12f),limiter=Limiter(-2f)),
            "cyborg-old" to CreativeVoice.CYBORG.effects.copy(lofiBits=9)
        )
        var cases=0;var invalid=0;var commandFailures=0
        suspend fun inspect(label:String,input:File,filters:List<String>,channels:Int):Boolean {
            val out=File(context.cacheDir,"manual-dsp.f32");out.delete()
            val logs=StringBuilder()
            val result=executor.execute(FfmpegCommand(listOf("-y","-i",input.path,"-af",filters.joinToString(","),"-ac",channels.toString(),"-f","f32le",out.path))){logs.append(it)}
            if(result!=ExecutionResult.Success || !out.isFile) {
                cases++;commandFailures++
                android.util.Log.e("MANUAL-DSP","$label COMMAND_FAILED result=$result ${logs.takeLast(1200)}")
                out.delete();return false
            }
            val data=java.nio.ByteBuffer.wrap(out.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            var nonfinite=0;var firstBad=-1;var nonzero=0;var samples=0;var peak=0f
            while(data.remaining()>=4) {
                val v=data.float
                if(!v.isFinite()){nonfinite++;if(firstBad<0)firstBad=samples}
                else {peak=maxOf(peak,kotlin.math.abs(v));if(kotlin.math.abs(v)>.00001f)nonzero++}
                samples++
            }
            cases++;if(nonfinite>0)invalid++
            android.util.Log.i("MANUAL-DSP","$label n=$samples bad=$nonfinite first=$firstBad nonzero=$nonzero peak=$peak filters=${filters.joinToString(",")}")
            assertTrue("$label output must contain samples",samples>0)
            return nonfinite==0
        }
        for(rate in listOf(8000,16000,48000)) for(level in listOf(0,1,8)) for(channels in listOf(1,2)) {
            val input=File(context.cacheDir,"manual-dsp-input.wav")
            assertEquals(ExecutionResult.Success,executor.execute(FfmpegCommand(listOf("-y","-i",speech.path,"-t","1","-af","volume=$level","-ar",rate.toString(),"-ac",channels.toString(),input.path))) {})
            for((name,fx) in recipes) {
                val stages=fx.stages().map{it.filter}
                for(count in 0..stages.size) inspect("$name/$rate/$level/ch$channels/prefix$count",input,listOf("aresample=48000","aformat=sample_fmts=fltp")+stages.take(count),channels)
                if(fx.pitchSemitones!=0f) {
                    val output=File(context.cacheDir,"manual-dsp-built.wav")
                    val req=ConversionRequest(OutputFormat.WAV,input.name,sources=listOf(SourceInfo(1000,0,0,true)),segments=listOf(Segment(0,0,1000)),audioEffects=fx)
                    val built=CommandBuilder{"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(input.path),output=output.path))
                    val args=built.arguments.map{if(it=="pcm_s16le")"pcm_f32le" else it}
                    val result=executor.execute(FfmpegCommand(args)){}
                    android.util.Log.i("MANUAL-DSP","$name/$rate/$level/ch$channels/actual-builder result=$result command=${args.joinToString(" ")}")
                    if(result==ExecutionResult.Success)inspect("$name/$rate/$level/ch$channels/actual-builder-decode",output,listOf("anull"),channels)
                    else {cases++;commandFailures++}
                    output.delete()
                }
                // Change one mechanism at a time. These controls are not production changes.
                if(stages.any{it.startsWith("acrusher=")}) {
                    inspect("$name/$rate/$level/ch$channels/aa-positive",input,listOf("aresample=48000","aformat=sample_fmts=fltp")+stages.map{if(it.startsWith("acrusher="))it.replace(":aa=0:",":aa=0.000001:")else it},channels)
                }
                if(stages.any{it.startsWith("vibrato=")}) {
                    // A prefix of silence replaces stale delay values before the real signal begins.
                    inspect("$name/$rate/$level/ch$channels/primed-vibrato",input,listOf("aresample=48000","aformat=sample_fmts=fltp","adelay=20:all=1")+stages+listOf("atrim=start=0.02","asetpts=PTS-STARTPTS"),channels)
                }
            }
        }
        // A precision boundary that is only 0.5000001 of a quantization step below an integer.
        // roundf can choose the upper integer while the double comparison enters aa=0 division.
        for(bits in listOf(5,8,9,12,16)) for(aa in listOf("0","0.000001")) {
            val input=File(context.cacheDir,"manual-dsp-boundary.wav")
            val value=(32.4999999)/(Math.pow(2.0,bits.toDouble())-1)
            assertEquals(ExecutionResult.Success,executor.execute(FfmpegCommand(listOf("-y","-f","lavfi","-i","aevalsrc=$value:s=48000:d=0.02","-c:a","pcm_f64le",input.path))) {})
            inspect("boundary/bits$bits/aa$aa",input,listOf("acrusher=bits=$bits:mode=lin:aa=$aa:mix=0:samples=1"),1)
        }
        android.util.Log.i("MANUAL-DSP","SUMMARY cases=$cases invalid=$invalid commandFailures=$commandFailures. Diagnostic collection, invalid results intentionally recorded, not a production pass.")
        speech.delete()
    }



    @Test fun deliveredCatalogSilentAacPeakCollection(): Unit = runBlocking {
        org.junit.Assume.assumeTrue("Explicit isolated native diagnostic only", InstrumentationRegistry.getArguments().getString("unsafeDspDiagnostics") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val executor=IsolatedDiagnosticExecutor()
        val input=File(context.cacheDir,"catalog-silence.wav");val failures=mutableListOf<String>();var cases=0
        for(rate in listOf(8000,16000,48000)) for(channels in listOf(1,2)) {
            assertEquals(ExecutionResult.Success,executor.execute(FfmpegCommand(listOf("-y","-f","lavfi","-i","anullsrc=r=$rate:cl=${if(channels==1)"mono"else "stereo"}","-t","1",input.path))) {})
            for(voice in CreativeVoice.entries.filter{it!=CreativeVoice.CLEAN}) {
                cases++;val output=File(context.cacheDir,"catalog-silence.m4a");val raw=File(context.cacheDir,"catalog-silence.f32")
                val req=ConversionRequest(OutputFormat.M4A,input.name,sources=listOf(SourceInfo(1000,0,0,true)),segments=listOf(Segment(0,0,1000)),audioEffects=voice.effects)
                val cmd=CommandBuilder{"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(input.path),output=output.path))
                val result=executor.execute(cmd){}
                if(result!=ExecutionResult.Success){failures+="${voice.name}/$rate/ch$channels execute=$result";continue}
                val decoded=executor.execute(FfmpegCommand(listOf("-y","-i",output.path,"-ac","1","-f","f32le",raw.path))){}
                if(decoded!=ExecutionResult.Success){failures+="${voice.name}/$rate/ch$channels decode=$decoded";continue}
                val data=java.nio.ByteBuffer.wrap(raw.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN);var peak=0f;var bad=0
                while(data.remaining()>=4){val v=data.float;if(!v.isFinite())bad++ else peak=maxOf(peak,kotlin.math.abs(v))}
                android.util.Log.i("CATALOG-PEAK","SILENT-AAC ${voice.name}/$rate/ch$channels bad=$bad peak=$peak")
                if(bad>0 || peak>0.00001f)failures+="${voice.name}/$rate/ch$channels bad=$bad peak=$peak"
                output.delete();raw.delete()
            }
        }
        android.util.Log.i("CATALOG-PEAK","SILENT-AAC SUMMARY cases=$cases failures=${failures.size}")
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }

    @Test fun availableCatalogSilentAacPeakAcceptance(): Unit = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val executor=FfmpegKitExecutor()
        val input=File(context.cacheDir,"catalog-silence.wav");val failures=mutableListOf<String>();var cases=0
        for(rate in listOf(8000,16000,48000)) for(channels in listOf(1,2)) {
            assertEquals(ExecutionResult.Success,executor.execute(FfmpegCommand(listOf("-y","-f","lavfi","-i","anullsrc=r=$rate:cl=${if(channels==1)"mono"else "stereo"}","-t","1",input.path))) {})
            for(voice in CreativeVoice.entries.filter{it!=CreativeVoice.CLEAN && com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety.available(it.effects)}) {
                cases++;val output=File(context.cacheDir,"catalog-silence.m4a");val raw=File(context.cacheDir,"catalog-silence.f32")
                val req=ConversionRequest(OutputFormat.M4A,input.name,sources=listOf(SourceInfo(1000,0,0,true)),segments=listOf(Segment(0,0,1000)),audioEffects=voice.effects)
                val cmd=CommandBuilder{"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(input.path),output=output.path))
                val result=executor.execute(cmd){}
                if(result!=ExecutionResult.Success){failures+="${voice.name}/$rate/ch$channels execute=$result";continue}
                val decoded=executor.execute(FfmpegCommand(listOf("-y","-i",output.path,"-ac","1","-f","f32le",raw.path))){}
                if(decoded!=ExecutionResult.Success){failures+="${voice.name}/$rate/ch$channels decode=$decoded";continue}
                val data=java.nio.ByteBuffer.wrap(raw.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN);var peak=0f;var bad=0
                while(data.remaining()>=4){val v=data.float;if(!v.isFinite())bad++ else peak=maxOf(peak,kotlin.math.abs(v))}
                android.util.Log.i("CATALOG-PEAK","SILENT-AAC ${voice.name}/$rate/ch$channels bad=$bad peak=$peak")
                if(bad>0 || peak>0.00001f)failures+="${voice.name}/$rate/ch$channels bad=$bad peak=$peak"
                output.delete();raw.delete()
            }
        }
        android.util.Log.i("CATALOG-PEAK","SILENT-AAC SUMMARY cases=$cases failures=${failures.size}")
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }

    @Test fun allNewShadersCompileAndRealPhoneRenders() = runBlocking {
        PictureLook.entries.filter{it.ordinal>=PictureLook.SEPIA.ordinal}.forEach {RuntimeShader(lookShader(it))}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val input=File(context.cacheDir,"creative-source.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("ci-test-video.mp4").use{src->input.outputStream().use{src.copyTo(it)}}
        val executor=FfmpegKitExecutor()
        fun request()=ConversionRequest(OutputFormat.MP4,input.name,sources=listOf(SourceInfo(8000,640,360,true)),segments=listOf(Segment(0,0,1000)))
        val cases=PictureLook.entries.filter{it.ordinal>=PictureLook.SEPIA.ordinal}.map{request().copy(looks=listOf(LookSpan(it,0,900)))}+
            CreativeVoice.entries.filter{com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety.available(it.effects)}.map{request().copy(audioEffects=it.effects)}+
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
                var nonzero=false;var peak=0f;while(data.remaining()>=4) {val sample=data.float;assertTrue("Voice AAC decoded non-finite sample",sample.isFinite());peak=maxOf(peak,kotlin.math.abs(sample));if(kotlin.math.abs(sample)>0.00001f)nonzero=true}
                android.util.Log.i("CATALOG-PEAK","MP4 case$index peak=$peak nonzero=$nonzero")
                assertTrue("Voice AAC extreme peak=$peak",peak<=4f)
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
