package com.cuscus.ffmpeggui

import androidx.test.platform.app.InstrumentationRegistry
import com.cuscus.ffmpeggui.core.audio.AudioStage
import com.cuscus.ffmpeggui.core.command.*
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.model.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlin.math.*

/** Candidate-only execution in the test APK. No production recipe or restriction changes. */
class VibratoCandidateRuntimeTest {
    @Test fun fullPeriodInitializationRealBuilderScopesAndKotlinParity(): Unit = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("candidateDspValidation") == "true")
        val dir=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val executor=IsolatedDiagnosticExecutor()
        val failures=mutableListOf<String>();var cases=0
        suspend fun execute(command:FfmpegCommand):Boolean {
            val log=StringBuilder();val result=executor.execute(command){log.append(it)}
            if(result!=ExecutionResult.Success) {failures+="execute=$result ${log.takeLast(1200)}";return false}
            return true
        }
        fun read(file:File):FloatArray {
            val b=ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(b.remaining()/4){b.float}
        }
        // The production AudioFilters inserts aresample=48000 before these DSP stages.
        // Scope checks below require that exact prefix; the transform never uses the input rate.
        fun candidate(command:FfmpegCommand):FfmpegCommand=FfmpegCommand(command.arguments.map {arg ->
            if(!arg.contains("vibrato="))arg else {
                check(arg.contains("aresample=48000")) {"Candidate requires proven 48k processed rate: $arg"}
                arg.replace(Regex("vibrato=f=([0-9.]+):d=([0-9.]+)")) {m ->
                    val period=Math.rint(48000.0/m.groupValues[1].toDouble()+0.5).toLong()
                    "adelay=${period}S:all=1,${m.value},atrim=start_sample=$period,asetpts=PTS-STARTPTS"
                }
            }
        })
        // Independent actual Kotlin reference, encoded as f32 on both sides. Exact comparison
        // is only claimed at that encoding precision, not at internal double precision.
        for(ch in listOf(1,2)) for((frequency,depth) in listOf(.1 to 1.0,.7 to .12,3.0 to .3,7.0 to .55,20.0 to 1.0)) {
            val frames=12000;val input=FloatArray(frames*ch){i -> val n=i/ch;val c=i%ch;if(n==0).75f else (.4*sin(2*PI*(437+c*79)*n/48000)).toFloat()}
            val src=File(dir,"vibrato-candidate-reference-in.f32")
            src.writeBytes(ByteBuffer.allocate(input.size*4).order(ByteOrder.LITTLE_ENDIAN).also{b->input.forEach{b.putFloat(it)}}.array())
            val expected=input.copyOf();AudioStage.Vibrato(frequency,depth).processor(48000,ch).process(expected,frames)
            val out=File(dir,"vibrato-candidate-reference-out.f32");out.delete()
            val period=Math.rint(48000.0/frequency+.5).toLong()
            val start=System.nanoTime()
            val ok=execute(FfmpegCommand(listOf("-y","-f","f32le","-ar","48000","-ac",ch.toString(),"-i",src.path,"-af","adelay=${period}S:all=1,vibrato=f=$frequency:d=$depth,atrim=start_sample=$period,asetpts=PTS-STARTPTS","-f","f32le",out.path)))
            cases++
            if(ok) {
                val actual=read(out);val error=if(actual.size==expected.size)actual.indices.maxOf{abs(actual[it]-expected[it])}else Float.POSITIVE_INFINITY
                val bad=actual.count{!it.isFinite()};val peak=actual.maxOf{abs(it)}
                android.util.Log.i("VIBRATO-CANDIDATE","PARITY ch$ch f$frequency d$depth pad$period samples=${actual.size} error=$error bad=$bad peak=$peak elapsedMs=${(System.nanoTime()-start)/1000000}")
                if(error>0.000002f || bad>0 || peak>1f)failures+="parity ch$ch f$frequency error=$error bad=$bad peak=$peak"
            }
        }
        val speech=File(dir,"vibrato-candidate-speech.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("creative-speech16.wav").use{src->speech.outputStream().use{src.copyTo(it)}}
        val recipes=listOf("Alien" to CreativeVoice.ALIEN.effects,"Ghost" to CreativeVoice.GHOST.effects,"Tape-old-no-crusher" to AudioEffects(highCutHz=6500,modulation=Modulation(ModulationKind.VIBRATO,.7f,.12f),limiter=Limiter(-2f)))
        suspend fun inspect(label:String,command:FfmpegCommand,out:File,ms:Long,signal:Boolean,aac:Boolean=false,quietBefore:Long=0,quietAfter:Long?=null) {
            cases++;out.delete();val start=System.nanoTime()
            if(!execute(candidate(command)))return
            val raw=File(dir,"vibrato-candidate-decode.f32");raw.delete()
            if(!execute(FfmpegCommand(listOf("-y","-i",out.path,"-vn","-ac","1","-ar","48000","-f","f32le",raw.path))))return
            val actual=read(raw);val bad=actual.count{!it.isFinite()};val peak=actual.filter{it.isFinite()}.maxOfOrNull{abs(it)} ?: 0f
            val nonzero=actual.any{abs(it)>.00001f};val duration=actual.size*1000L/48000
            val leak=actual.indices.count {i->abs(actual[i])>.00001f && (i*1000L/48000<quietBefore || (quietAfter!=null && i*1000L/48000>quietAfter))}
            android.util.Log.i("VIBRATO-CANDIDATE","$label samples=${actual.size} duration=$duration bad=$bad peak=$peak signal=$nonzero leak=$leak elapsedMs=${(System.nanoTime()-start)/1000000}")
            if(bad>0 || peak>4f || signal!=nonzero || abs(duration-ms)>(if(aac)65 else 2) || leak>0)failures+="$label duration=$duration expected$ms bad=$bad peak=$peak signal=$nonzero wanted$signal leak=$leak"
        }
        for(rate in listOf(8000,16000,48000)) for(level in listOf(0,1,8)) for(ch in listOf(1,2)) {
            val input=File(dir,"vibrato-candidate-input.wav")
            if(!execute(FfmpegCommand(listOf("-y","-i",speech.path,"-t","1","-af","volume=$level","-ar",rate.toString(),"-ac",ch.toString(),input.path))))continue
            for((name,fx) in recipes) for(scope in listOf("output","clip","sound")) {
                val req=ConversionRequest(OutputFormat.WAV,input.name,sources=listOf(SourceInfo(1000,0,0,true)),segments=listOf(Segment(0,0,1000,if(scope=="clip")Effects(audioEffects=fx)else Effects())),effects=if(scope=="sound")Effects(muted=true)else Effects(),audioEffects=if(scope=="output")fx else AudioEffects.NONE,sounds=if(scope=="sound")listOf(PlacedSound(trimEndMs=1000,effects=fx))else emptyList())
                val out=File(dir,"vibrato-candidate.wav")
                val built=CommandBuilder{"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(input.path),output=out.path,sounds=if(scope=="sound")listOf(input.path)else emptyList()))
                inspect("FLOAT $name/$rate/level$level/ch$ch/$scope",FfmpegCommand(built.arguments.map{if(it=="pcm_s16le")"pcm_f32le"else it}),out,1000,level>0)
                if(level==0 && scope=="output") {
                    val aac=File(dir,"vibrato-candidate.m4a")
                    inspect("SILENT-AAC $name/$rate/ch$ch",CommandBuilder{"/fonts/Regular.ttf"}.build(req.copy(format=OutputFormat.M4A),MediaPaths(inputs=listOf(input.path),output=aac.path)),aac,1000,false,true)
                }
            }
        }
        for((name,fx) in recipes) {
            val req=ConversionRequest(OutputFormat.WAV,speech.name,sources=listOf(SourceInfo(2000,0,0,true)),segments=listOf(Segment(0,0,1500)),effects=Effects(muted=true),sounds=listOf(PlacedSound(startMs=300,trimStartMs=100,trimEndMs=900,effects=fx)))
            val out=File(dir,"vibrato-candidate-placement.wav")
            val cmd=CommandBuilder{"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(speech.path),output=out.path,sounds=listOf(speech.path)))
            inspect("PLACEMENT $name",FfmpegCommand(cmd.arguments.map{if(it=="pcm_s16le")"pcm_f32le"else it}),out,1500,true,quietBefore=290,quietAfter=1120)
        }
        android.util.Log.i("VIBRATO-CANDIDATE","SUMMARY cases=$cases failures=${failures.size}")
        failures.forEach{android.util.Log.e("VIBRATO-CANDIDATE",it)}
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
