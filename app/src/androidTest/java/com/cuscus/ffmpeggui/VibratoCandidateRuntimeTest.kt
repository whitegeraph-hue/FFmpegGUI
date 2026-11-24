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
        val normalExecutor=com.cuscus.ffmpeggui.media.FfmpegKitExecutor()
        val diagnosticExecutor=IsolatedDiagnosticExecutor()
        val failures=mutableListOf<String>();var cases=0
        val evidence=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"vibrato-evidence").apply{mkdirs()}
        var executions=0
        var versionWritten=false
        suspend fun execute(command:FfmpegCommand):Boolean {
            val id=++executions
            File(evidence,"command-$id.txt").writeText(command.toDisplayString())
            val display=command.toDisplayString()
            val chunks=display.chunked(1200)
            chunks.forEachIndexed {part,text->android.util.Log.i("VIBRATO-CANDIDATE","COMMAND id=$id part=${part+1}/${chunks.size} $text")}
            val log=StringBuilder()
            // Only unproven hand-built isolated references use the diagnostic path.
            // All 215 builder commands, source fixtures and decodes use the real executor.
            val hasNativeVibrato=command.arguments.any{it.contains("vibrato=")}
            val isBuilder=command.arguments.any{it.contains("vibrato-candidate-speech.wav") || it.contains("vibrato-candidate-input.wav") || it.contains("vibrato-candidate-video.mp4")}
            val executor=if(hasNativeVibrato && !isBuilder)diagnosticExecutor else normalExecutor
            android.util.Log.i("VIBRATO-CANDIDATE","EXECUTOR id=$id normal=${executor===normalExecutor}")
            val result=executor.execute(command){log.append(it)}
            if(!versionWritten && log.contains("ffmpeg version")) {
                File(evidence,"runtime-version.txt").writeText(log.toString());versionWritten=true
                android.util.Log.i("VIBRATO-CANDIDATE","RUNTIME "+log.lineSequence().first{it.contains("ffmpeg version")})
            }
            if(result!=ExecutionResult.Success) {failures+="execute=$result ${log.takeLast(1200)}";return false}
            return true
        }
        fun read(file:File):FloatArray {
            val b=ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(b.remaining()/4){b.float}
        }
        // The production AudioFilters inserts aresample=48000 before these DSP stages.
        // Scope checks below require that exact prefix; the transform never uses the input rate.
        fun auditProduction(command:FfmpegCommand):FfmpegCommand {
            var count=0
            for(arg in command.arguments) for(m in Regex("vibrato=f=([0-9.]+):d=([0-9.]+)").findAll(arg)) {
                val period=Math.rint(48000.0/m.groupValues[1].toDouble()+.5).toLong()
                val branchStart=maxOf(arg.lastIndexOf(';',m.range.first),arg.lastIndexOf(']',m.range.first))
                val prefix=arg.substring(branchStart+1,m.range.first)
                check(prefix.contains("aresample=48000")) {"No production 48k resample before vibrato"}
                check(prefix.endsWith("adelay=${period}S:all=1,")) {"Missing production initialization"}
                check(arg.substring(m.range.last+1).startsWith(",atrim=start_sample=$period,asetpts=PTS-STARTPTS")) {"Missing production trim/reset"}
                count++
            }
            android.util.Log.i("VIBRATO-CANDIDATE","PRODUCTION-AUDIT count=$count nextCommand=${executions+1}")
            return command // Audits only. Never edits the production-built graph.
        }
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
        for(frequency in listOf(.1,.7,3.0)) {
            val out=File(dir,"vibrato-padded-silence.f32")
            val period=Math.rint(48000.0/frequency+.5).toLong()
            cases++
            if(execute(FfmpegCommand(listOf("-y","-f","lavfi","-i","anullsrc=r=48000:cl=mono","-af","adelay=${period}S:all=1,vibrato=f=$frequency:d=0.55,atrim=start_sample=$period,asetpts=PTS-STARTPTS","-t","1","-f","f32le",out.path)))) {
                val values=read(out);val bad=values.count{!it.isFinite()};val nonzero=values.count{abs(it)>.00001f}
                android.util.Log.i("VIBRATO-CANDIDATE","PADDED-SILENCE f$frequency samples=${values.size} bad=$bad nonzero=$nonzero")
                if(values.size!=48000 || bad>0 || nonzero>0)failures+="padded silence f$frequency bad$bad nonzero$nonzero samples${values.size}"
            }
        }
        val speech=File(dir,"vibrato-candidate-speech.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("creative-speech16.wav").use{src->speech.outputStream().use{src.copyTo(it)}}
        val recipes=listOf("Alien" to CreativeVoice.ALIEN.effects,"Ghost" to CreativeVoice.GHOST.effects,"Tape-old-no-crusher" to AudioEffects(highCutHz=6500,modulation=Modulation(ModulationKind.VIBRATO,.7f,.12f),limiter=Limiter(-2f)))
        suspend fun inspect(label:String,command:FfmpegCommand,out:File,ms:Long,signal:Boolean,aac:Boolean=false,quietBefore:Long=0,quietAfter:Long?=null) {
            cases++;out.delete();val start=System.nanoTime()
            if(!execute(auditProduction(command)))return
            val raw=File(dir,"vibrato-candidate-decode.f32");raw.delete()
            if(!execute(FfmpegCommand(listOf("-y","-i",out.path,"-vn","-ac","1","-ar","48000","-f","f32le",raw.path))))return
            // Keep channels separate for amplitude/saturation. The historical mono
            // measurement below is for timing/silence, not a channel peak claim.
            val channelsRaw=File(dir,"vibrato-channel-decode.f32")
            if(!execute(FfmpegCommand(listOf("-y","-i",out.path,"-vn","-ar","48000","-f","f32le",channelsRaw.path))))return
            val separate=read(channelsRaw)
            val perChannelPeak=separate.filter{it.isFinite()}.maxOfOrNull{abs(it)} ?: 0f
            val saturation=separate.count{it>=32767f/32768f || it<=-1f}
            android.util.Log.i("VIBRATO-CANDIDATE","CHANNEL-METRIC $label decodedPeak=$perChannelPeak boundarySamples=$saturation samples=${separate.size}")
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
                val req=ConversionRequest(OutputFormat.WAV,input.name,sources=listOf(SourceInfo(1000,0,0,true)),segments=listOf(Segment(0,0,1000,if(scope=="clip")Effects(audioEffects=fx)else Effects())),effects=if(scope=="sound")Effects(muted=true,volume=0f)else Effects(),audioEffects=if(scope=="output")fx else AudioEffects.NONE,sounds=if(scope=="sound")listOf(PlacedSound(trimEndMs=1000,effects=fx))else emptyList())
                val out=File(dir,"vibrato-candidate.wav")
                val built=CommandBuilder(AudioRuntime.ANDROID_BUNDLED_6_1_6){"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(input.path),output=out.path,sounds=if(scope=="sound")listOf(input.path)else emptyList()))
                inspect("PCM16 $name/$rate/level$level/ch$ch/$scope",built,out,1000,level>0)
                if(level==0 && scope=="output") {
                    val aac=File(dir,"vibrato-candidate.m4a")
                    inspect("SILENT-AAC $name/$rate/ch$ch",CommandBuilder(AudioRuntime.ANDROID_BUNDLED_6_1_6){"/fonts/Regular.ttf"}.build(req.copy(format=OutputFormat.M4A),MediaPaths(inputs=listOf(input.path),output=aac.path)),aac,1000,false,true)
                }
            }
        }
        for((name,fx) in recipes) {
            val req=ConversionRequest(OutputFormat.WAV,speech.name,sources=listOf(SourceInfo(2000,0,0,true)),segments=listOf(Segment(0,0,1500)),effects=Effects(muted=true,volume=0f),sounds=listOf(PlacedSound(startMs=300,trimStartMs=100,trimEndMs=900,effects=fx)))
            val out=File(dir,"vibrato-candidate-placement.wav")
            val cmd=CommandBuilder(AudioRuntime.ANDROID_BUNDLED_6_1_6){"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(speech.path),output=out.path,sounds=listOf(speech.path)))
            inspect("PLACEMENT $name",cmd,out,1500,true,quietBefore=290,quietAfter=1120)
        }
        // Neutral effects isolate placement from DSP. A deliberately mixed-source control
        // records the old fixture's leak without treating expected source speech as a defect.
        for(zeroSource in listOf(false,true)) {
            val req=ConversionRequest(OutputFormat.WAV,speech.name,sources=listOf(SourceInfo(2000,0,0,true)),segments=listOf(Segment(0,0,1500)),effects=Effects(muted=true,volume=if(zeroSource)0f else 1f),sounds=listOf(PlacedSound(startMs=300,trimStartMs=100,trimEndMs=900)))
            val out=File(dir,"vibrato-neutral.wav")
            val cmd=CommandBuilder(AudioRuntime.ANDROID_BUNDLED_6_1_6){"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(speech.path),output=out.path,sounds=listOf(speech.path)))
            inspect("NEUTRAL-PLACEMENT sourceZero=$zeroSource",cmd,out,1500,true,quietBefore=if(zeroSource)290 else 0,quietAfter=if(zeroSource)1120 else null)
        }
        // Verify explicit final output rates, including silent AAC and retained speech.
        for((name,fx) in recipes) for(rate in listOf(16000,44100)) for(format in listOf(OutputFormat.WAV,OutputFormat.M4A)) for(silent in listOf(false,true)) {
            val req=ConversionRequest(format,speech.name,sources=listOf(SourceInfo(2000,0,0,true)),segments=listOf(Segment(0,0,1000)),effects=Effects(volume=if(silent)0f else 1f),audioEffects=fx,encoding=EncodingSettings(audio=AudioEncoding(sampleRate=rate)))
            val out=File(dir,if(format==OutputFormat.M4A)"vibrato-rate.m4a"else "vibrato-rate.wav")
            val cmd=CommandBuilder(AudioRuntime.ANDROID_BUNDLED_6_1_6){"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(speech.path),output=out.path))
            inspect("OUTPUT-RATE $name/$rate/$format/silent$silent",cmd,out,1000,!silent,aac=format==OutputFormat.M4A)
        }
        val video=File(dir,"vibrato-candidate-video.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("ci-test-video.mp4").use{src->video.outputStream().use{src.copyTo(it)}}
        for((name,fx) in recipes) for(silent in listOf(false,true)) {
            val req=ConversionRequest(OutputFormat.MP4,video.name,sources=listOf(SourceInfo(8000,640,360,true)),segments=listOf(Segment(0,0,1000)),effects=Effects(volume=if(silent)0f else 1f),audioEffects=fx)
            val out=File(dir,"vibrato-candidate.mp4")
            inspect("MP4 $name/silent$silent",CommandBuilder(AudioRuntime.ANDROID_BUNDLED_6_1_6){"/fonts/Regular.ttf"}.build(req,MediaPaths(inputs=listOf(video.path),output=out.path)),out,1000,!silent,aac=true)
        }
        // Full generated conversion path, including staging, policy, native executor,
        // output planning and cleanup. No diagnostic executor or command edits here.
        val engineOut=File(dir,"vibrato-engine-output").apply{mkdirs()}
        val stager=com.cuscus.ffmpeggui.core.engine.SourceStager {
            val work=File(dir,"vibrato-engine-staging").apply{mkdirs()}
            object:com.cuscus.ffmpeggui.core.engine.StagingSession {
                override fun stage(sourceUri:String,preferredName:String):String?=File(work,preferredName).also{File(sourceUri).copyTo(it,overwrite=true)}.path
                override fun scratchFile(name:String)=File(work,name).path
                override fun close(){work.deleteRecursively()}
            }
        }
        val engine=com.cuscus.ffmpeggui.core.engine.ConversionEngine(
            commandBuilder=CommandBuilder(AudioRuntime.ANDROID_BUNDLED_6_1_6){"/fonts/Regular.ttf"},
            manualResolver=ManualCommandResolver(),outputPlanner=com.cuscus.ffmpeggui.core.output.OutputPlanner(),
            progressParser=com.cuscus.ffmpeggui.core.engine.ProgressParser(),executor=normalExecutor,
            stager=stager,outputDirectory={engineOut.path})
        for((name,fx) in recipes) for(scope in listOf("output","clip","sound")) {
            val req=ConversionRequest(OutputFormat.WAV,speech.name,sources=listOf(SourceInfo(2000,0,0,true)),segments=listOf(Segment(0,0,1000,if(scope=="clip")Effects(audioEffects=fx)else Effects())),audioEffects=if(scope=="output")fx else AudioEffects.NONE,effects=if(scope=="sound")Effects(volume=0f)else Effects(),sounds=if(scope=="sound")listOf(PlacedSound(trimEndMs=1000,effects=fx))else emptyList())
            val sources=com.cuscus.ffmpeggui.core.engine.MediaSources(listOf(com.cuscus.ffmpeggui.core.engine.NamedSource(speech.path,speech.name)),sounds=if(scope=="sound")listOf(com.cuscus.ffmpeggui.core.engine.NamedSource(speech.path,"stem.wav"))else emptyList())
            val result=engine.run(com.cuscus.ffmpeggui.core.engine.ConversionJob(req,sources))
            cases++
            android.util.Log.i("VIBRATO-CANDIDATE","ENGINE $name/$scope result=$result")
            if(result !is com.cuscus.ffmpeggui.core.engine.ConversionOutcome.Success || !File(result.outputPath).isFile)failures+="engine $name/$scope $result"
        }
        // Direct manual native calls remain blocked, even when a safe-looking pad is pasted.
        for(graph in listOf("vibrato=f=3","aresample=48000,adelay=16000S:all=1,vibrato=f=3:d=.3,atrim=start_sample=16000,asetpts=PTS-STARTPTS","acrusher=bits=9")) {
            val refusalLog=StringBuilder()
            val result=normalExecutor.execute(FfmpegCommand(listOf("-y","-f","lavfi","-i","anullsrc","-t","1","-af",graph,File(dir,"must-not-export.wav").path))){refusalLog.append(it)}
            android.util.Log.i("VIBRATO-CANDIDATE","GUARD-REFUSAL graph=$graph result=$result reason=$refusalLog")
            check(result==ExecutionResult.Failed(com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety.RETURN_CODE))
        }
        android.util.Log.i("VIBRATO-CANDIDATE","SUMMARY cases=$cases failures=${failures.size}")
        failures.forEach{android.util.Log.e("VIBRATO-CANDIDATE",it)}
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
    /** Unpadded negative control gets its own instrumentation process, before the candidate trials. */
    @Test fun unpaddedSilenceNegativeControl(): Unit = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("candidateDspValidation")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val evidence=File(context.getExternalFilesDir(null),"vibrato-negative-control").apply{mkdirs()}
        val executor=IsolatedDiagnosticExecutor()
        for(frequency in listOf(.1,.7,3.0)) {
            val out=File(context.cacheDir,"vibrato-negative.f32")
            val command=FfmpegCommand(listOf("-y","-f","lavfi","-i","anullsrc=r=48000:cl=mono","-t","1","-af","vibrato=f=$frequency:d=0.55","-f","f32le",out.path))
            File(evidence,"command-$frequency.txt").writeText(command.toDisplayString())
            android.util.Log.i("VIBRATO-CANDIDATE","NEGATIVE-COMMAND f$frequency "+command.toDisplayString())
            val log=StringBuilder();val result=executor.execute(command){log.append(it)}
            android.util.Log.i("VIBRATO-CANDIDATE","NEGATIVE-RUNTIME "+log.lineSequence().firstOrNull{it.contains("ffmpeg version")})
            File(evidence,"runtime-$frequency.txt").writeText(log.toString())
            assertEquals(ExecutionResult.Success,result)
            val b=ByteBuffer.wrap(out.readBytes()).order(ByteOrder.LITTLE_ENDIAN);val values=FloatArray(b.remaining()/4){b.float}
            val row="UNPADDED f$frequency samples=${values.size} bad=${values.count{!it.isFinite()}} peak=${values.filter{it.isFinite()}.maxOfOrNull{abs(it)}} nonzero=${values.count{abs(it)>.00001f}}"
            android.util.Log.i("VIBRATO-CANDIDATE",row);File(evidence,"samples-$frequency.txt").writeText(row)
        }
    }

}
