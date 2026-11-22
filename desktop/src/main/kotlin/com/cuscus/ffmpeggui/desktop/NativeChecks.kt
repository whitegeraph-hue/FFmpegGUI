package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.editing.*
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.timeline.*
import com.cuscus.ffmpeggui.core.subtitles.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Integration checks call real ffmpeg/ffprobe and the unmodified core, never canned detector output. */
fun main(args:Array<String>)=runBlocking {
    val folder=File(args[0]); val media=DesktopMedia(); val exporter=DesktopExport(media)
    var passed=0
    suspend fun verify(name:String,block:suspend ()->Unit) { block(); passed++;println("PASS $name") }
    val video=media.probe(File(folder,"video.mp4").path)
    verify("probe actual streams") { check(video.info.durationMs in 7900..8100 && video.info.width==640 && video.info.hasAudio) }
    verify("seek actual pixels") { val a=media.frame(video.path,500)!!;val b=media.frame(video.path,3500)!!;check(a.width>0 && a.getRGB(100,100)!=b.getRGB(100,100)) }
    verify("real scene detector") { val s=media.probe(File(folder,"scenes.mp4").path); val points=media.scenes(s);println("SCENES $points");check(points.size==2 && kotlin.math.abs(points[0]-2000)<100 && kotlin.math.abs(points[1]-4000)<100) }
    verify("real beat analysis") { val result=BeatTrack.analyze(media.samples(File(folder,"beat.wav").path),16000,8000);println("BEATS ${result.tempoBpm} ${result.beatsMs}");check(result.tempoBpm!=null && kotlin.math.abs(result.tempoBpm!!-120)<5 && result.beatsMs.size>=14) }
    verify("real silence detector") { val s=media.probe(File(folder,"silence.wav").path);val gaps=media.silence(s);println("SILENCE $gaps");check(gaps.any { it.first in 1900..2100 && it.second in 3900..4100 }) }
    val p=DesktopProject.create(video)
    verify("timeline cut exported with audio") {
        val t=p.timeline.withoutRanges(listOf(CutRange(2000,4000))) { UUID.randomUUID().toString() }
        val result=exporter.export(p.copy(timeline=t),File(folder,"cut.mp4"))
        check(result.info.durationMs in 5900..6200 && result.info.hasAudio)
        media.process.checked(listOf("ffmpeg","-v","error","-i",result.path,"-f","null","-"))
    }
    verify("follow crop synthetic track export") {
        val points=listOf(SubjectPoint(0,.25f,.5f,1f),SubjectPoint(7000,.75f,.5f,1f))
        val size=FollowCropTrack.windowSize(640,360,9,16)
        val result=exporter.export(p.copy(followCrop=FollowCropTrack(points,640,360,size.first,size.second,video.path)),File(folder,"crop.mp4"))
        check(result.info.width==202 && result.info.height==360)
    }
    verify("audio-only export") { val result=exporter.export(p.copy(format=OutputFormat.MP3),File(folder,"sound.mp3"));check(!result.info.isVideo && result.info.hasAudio) }
    verify("silent-video export") { val s=media.probe(File(folder,"silent.mp4").path);val result=exporter.export(DesktopProject.create(s),File(folder,"silent-copy.mp4"));check(!result.info.hasAudio && result.info.isVideo) }
    verify("original-overwrite blocked") { check(runCatching { exporter.export(p,File(video.path)) }.isFailure) }
    verify("project save and load shared models") { val file=File(folder,"project.ffgui.json");DesktopProjects.save(p,file);val restored=DesktopProjects.load(file,media);check(restored.timeline==p.timeline && restored.effects==p.effects && restored.sources==p.sources) }
    val captioned=p.copy(words=listOf(SpokenWord(500,1800,"Hello Linux"),SpokenWord(2500,3800,"Timed subtitles")),subtitleStyle=SubtitleStyle())
    verify("real subtitle burn-in and retimed text") {
        val result=exporter.export(captioned,File(folder,"subtitled.mp4"))
        val plain=media.frame(video.path,1000)!!;val lit=media.frame(result.path,1000)!!
        var differs=0
        for(y in 230 until 350)for(x in 50 until 590)if(plain.getRGB(x,y)!=lit.getRGB(x,y))differs++
        check(differs>1000)
        val edited=captioned.copy(timeline=captioned.timeline.withoutRanges(listOf(CutRange(0,2000))) { UUID.randomUUID().toString() },effects=Effects(speed=2f))
        val text=exporter.transcript(edited,OutputFormat.SRT)
        check("Hello Linux" !in text && "Timed subtitles" in text && "00:00:00,250" in text)
        exporter.export(edited.copy(format=OutputFormat.SRT),File(folder,"subtitles.srt"))
        exporter.export(edited.copy(format=OutputFormat.TXT),File(folder,"transcript.txt"))
        check(File(folder,"subtitles.srt").readText()==text)
    }
    verify("encoding controls persisted and applied") {
        val edited=p.copy(encoding=EncodingSettings(resolution=Resolution.P240,framerate=Framerate.FPS15,cpuSpeed=CpuEncoderSpeed.FAST,quality=.5f,audio=AudioEncoding(128,44100,24)),subtitleStyle=SubtitlePreset.BOXED.apply(SubtitleStyle()))
        val file=File(folder,"encoding.ffgui.json");DesktopProjects.save(edited,file)
        val restored=DesktopProjects.load(file,media);check(restored.encoding==edited.encoding && restored.subtitleStyle==edited.subtitleStyle)
        val output=exporter.export(edited,File(folder,"encoded.mp4"));check(output.info.height==240 && kotlin.math.abs(output.info.frameRate-15f)<.1f)
    }
    verify("cancel real process") {
        val start=System.currentTimeMillis()
        val job=launch { media.process.run(listOf("ffmpeg","-re","-f","lavfi","-i","testsrc2=size=640x360:rate=25","-t","30","-f","null","-")) }
        delay(400);job.cancelAndJoin();check(System.currentTimeMillis()-start<4000)
    }
    println("NATIVE_CHECKS_PASSED=$passed")
}
