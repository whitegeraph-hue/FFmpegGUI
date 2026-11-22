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
    verify("audio mastering and actual 24bit 44100Hz export") {
        val source=media.probe(File(folder,"beat.wav").path)
        val mastering=AudioEffects(equalizer=List(10) { if(it==4) 6f else 0f },compressor=Compressor(),reverb=Reverb(mix=.2f),stereo=StereoImage(width=.8f))
        val edited=DesktopProject.create(source).copy(format=OutputFormat.WAV,audioEffects=mastering,encoding=EncodingSettings(audio=AudioEncoding(192,44100,24)))
        val target=File(folder,"mastered.wav");exporter.export(edited,target)
        val probe=media.process.checked(listOf("ffprobe","-v","error","-show_streams","-of","json",target.path))
        check("pcm_s24le" in probe && "44100" in probe)
        val before=media.samples(source.path);val after=media.samples(target.path)
        check(before.indices.take(16000).any { kotlin.math.abs(before[it]-after[it])>.001f })
        val file=File(folder,"mastering.ffgui.json");DesktopProjects.save(edited,file);check(DesktopProjects.load(file,media).audioEffects==mastering)
    }
    verify("GIF palette frame rate width and persistence") {
        val edited=p.copy(format=OutputFormat.GIF,encoding=EncodingSettings(gif=GifSettings(width=240,fps=10,colors=32,dither=GifDither.BAYER,loops=-1,smaller=true)))
        val output=exporter.export(edited,File(folder,"palette.gif"));check(output.info.width==240 && kotlin.math.abs(output.info.frameRate-10f)<.1f)
        val file=File(folder,"gif.ffgui.json");DesktopProjects.save(edited,file);check(DesktopProjects.load(file,media).encoding.gif==edited.encoding.gif)
    }
    verify("still image crop rotation detail and unchanged source") {
        val file=File(folder,"photo.png")
        media.process.checked(listOf("ffmpeg","-v","error","-y","-i",video.path,"-frames:v","1",file.path))
        val original=file.readBytes();val source=media.probe(file.path)
        val edited=DesktopProject.create(source).copy(format=OutputFormat.PNG,effects=Effects(rotation=Rotation.DEG_90,crop=CropRect(.1f,.2f,.8f,.6f),color=ColorSettings(brightness=.1f,contrast=1.2f,sharpness=.3f)))
        val output=exporter.export(edited,File(folder,"photo-edited.png"))
        check(output.info.width in 286..288 && output.info.height in 382..384 && output.info.isStill)
        check(original.contentEquals(file.readBytes()))
    }
    verify("photo tone perspective corners shared model persistence") {
        val source=media.probe(File(folder,"photo.png").path)
        val edit=ImageEdit(perspectiveH=.12f,straightenDegrees=4f,tone=ImageTone(exposure=.3f,warmth=.2f,shadows=.2f),filter=ImageFilter.VINTAGE,effect=ImageEffect.POSTERIZE,effectAmount=.25f,corners=.15f)
        val photo=DesktopProject.create(source).copy(image=edit)
        val output=exporter.export(photo,File(folder,"photo-tone.png"))
        check(output.info.isStill && output.info.width in 560..640 && output.info.height in 310..360)
        val image=javax.imageio.ImageIO.read(File(output.path));check((image.getRGB(0,0) ushr 24)==0)
        val file=File(folder,"photo.ffgui.json");DesktopProjects.save(photo,file)
        check(DesktopProjects.load(file,media).image==edit)
        check(image.getRGB(320,180)!=javax.imageio.ImageIO.read(File(source.path)).getRGB(320,180))
    }
    verify("actual SVG ASCII ANSI export and original core settings") {
        val source=media.probe(File(folder,"photo.png").path)
        val photo=DesktopProject.create(source).copy(image=ImageEdit(trace=com.cuscus.ffmpeggui.core.vector.TraceSettings(colors=4,maxSide=128),textArt=com.cuscus.ffmpeggui.core.textart.TextArtSettings(columns=40)))
        val svg=File(folder,"traced.svg");exporter.export(photo.copy(format=OutputFormat.SVG),svg)
        val parsed=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg)
        check(parsed.documentElement.tagName=="svg" && parsed.getElementsByTagName("path").length>1)
        val txt=File(folder,"ascii.txt");exporter.export(photo.copy(format=OutputFormat.ASCII),txt)
        check(txt.readLines().size>5 && txt.readLines().all {it.length<=40} && txt.readText().any {!it.isWhitespace()})
        val ansi=File(folder,"colours.ans");exporter.export(photo.copy(format=OutputFormat.ANSI),ansi)
        check(ansi.readText().contains("\u001B["))
        val project=File(folder,"art.ffgui.json");DesktopProjects.save(photo,project);check(DesktopProjects.load(project,media).image==photo.image)
    }
    verify("timed caption real pixels hidden state and persistence") {
        val text=TextOverlay(text="Desktop text",x=.2f,y=.4f,size=.12f,startMs=2000,endMs=4000,color=0xFFFFFF00L,look=TextLook(outline=.1f))
        val project=p.copy(captions=listOf(text))
        val result=exporter.export(project,File(folder,"text-layer.mp4"))
        fun difference(a:java.awt.image.BufferedImage,b:java.awt.image.BufferedImage):Int {
            var n=0;for(y in 100 until 240)for(x in 40 until 580)if(a.getRGB(x,y)!=b.getRGB(x,y))n++;return n
        }
        val before=difference(media.frame(video.path,1000)!!,media.frame(result.path,1000)!!)
        val during=difference(media.frame(video.path,3000)!!,media.frame(result.path,3000)!!)
        check(during>before+500)
        val saved=File(folder,"text.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).captions==listOf(text))
        val hidden=project.copy(captions=listOf(text.copy(hidden=true)))
        check(!exporter.command(hidden,"output.mp4").arguments.joinToString(" ").contains("Desktop text"))
    }
    verify("WebP adapter exact edited size and alpha") {
        val source=media.probe(File(folder,"photo.png").path)
        val photo=DesktopProject.create(source).copy(format=OutputFormat.WEBP,image=ImageEdit(corners=.2f,output=ImageOutput(maxSide=320,quality=.8f)))
        val result=exporter.export(photo,File(folder,"photo.webp"))
        check(result.info.width==320 && result.info.height==180 && result.info.isStill)
        media.process.checked(listOf("ffmpeg","-v","error","-y","-i",result.path,"-frames:v","1",File(folder,"webp-decoded.png").path))
        val image=javax.imageio.ImageIO.read(File(folder,"webp-decoded.png"));check(image.getRGB(0,0) ushr 24==0)
    }
    verify("animated caption original keyframe expression rendered") {
        val keys=Keyframes(listOf(Keyframe(0,mapOf(KeyProperty.X to .1f),KeyEasing.LINEAR),Keyframe(4000,mapOf(KeyProperty.X to .8f))))
        val text=TextOverlay(text="MOVE",y=.7f,size=.14f,color=0xFFFFFFFFL,keyframes=keys)
        val result=exporter.export(p.copy(captions=listOf(text)),File(folder,"animated-text.mp4"))
        suspend fun whiteCenter(at:Long):Double {
            val image=media.frame(result.path,at)!!;var sum=0L;var n=0
            for(y in image.height/2 until image.height-20)for(x in 0 until image.width){val rgb=image.getRGB(x,y);if((rgb shr 16 and 255)>225 && (rgb shr 8 and 255)>225 && (rgb and 255)>225){sum+=x;n++}}
            check(n>50);return sum.toDouble()/n
        }
        check(whiteCenter(3500)>whiteCenter(500)+150)
        val file=File(folder,"keys.ffgui.json");DesktopProjects.save(p.copy(captions=listOf(text)),file);check(DesktopProjects.load(file,media).captions[0].keyframes==keys)
    }
    verify("placed sound actual delayed mix trim repeats and project") {
        val source=media.probe(File(folder,"beat.wav").path)
        val track=DesktopSound(source,PlacedSound(startMs=2000,trimEndMs=2000,repeats=2,volume=.7f,fadeInSeconds=.1f,bassDb=2f))
        val project=p.copy(effects=p.effects.copy(muted=true),sounds=listOf(track))
        val result=exporter.export(project,File(folder,"sound-mix.mp4"));check(result.info.hasAudio && result.info.durationMs in 7900..8200)
        val samples=media.samples(result.path)
        fun peak(a:Int,b:Int)=samples.sliceArray(a*16000 until b*16000).maxOf {kotlin.math.abs(it)}
        check(peak(0,1)<.005f && peak(2,5)>.05f && peak(7,8)<.005f)
        val file=File(folder,"sounds.ffgui.json");DesktopProjects.save(project,file);check(DesktopProjects.load(file,media).sounds==listOf(track))
        check(runCatching {exporter.export(project,File(source.path))}.isFailure)
    }
    verify("cancel real process") {
        val start=System.currentTimeMillis()
        val job=launch { media.process.run(listOf("ffmpeg","-re","-f","lavfi","-i","testsrc2=size=640x360:rate=25","-t","30","-f","null","-")) }
        delay(400);job.cancelAndJoin();check(System.currentTimeMillis()-start<4000)
    }
    println("NATIVE_CHECKS_PASSED=$passed")
}
