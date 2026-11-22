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
    verify("canvas portrait and actual metadata tags") {
        val project=p.copy(canvas=CanvasRatio.PORTRAIT_9_16,canvasFit=CanvasFit.FIT,tags=MetadataTags(title="Linux export",artist="Offline editor",stripAll=true))
        val result=exporter.export(project,File(folder,"portrait-tags.mp4"))
        check(kotlin.math.abs(result.info.width.toDouble()/result.info.height-9.0/16)<.01)
        val tags=media.process.checked(listOf("ffprobe","-v","error","-show_entries","format_tags=title,artist","-of","json",result.path))
        check(tags.contains("Linux export") && tags.contains("Offline editor"))
        val file=File(folder,"canvas-tags.ffgui.json");DesktopProjects.save(project,file);val restored=DesktopProjects.load(file,media);check(restored.tags==project.tags && restored.canvas==project.canvas && restored.canvasFit==project.canvasFit)
    }
    verify("timed image layer actual composite and saved state") {
        val file=File(folder,"sticker.png")
        media.process.checked(listOf("ffmpeg","-v","error","-y","-f","lavfi","-i","color=white:s=100x100","-frames:v","1",file.path))
        val source=media.probe(file.path);val picture=DesktopPicture(source,ImagePlacement(x=.5f,y=.5f,widthFraction=.3f,startMs=2000,endMs=4000,aspect=1f))
        val project=p.copy(pictures=listOf(picture));val result=exporter.export(project,File(folder,"picture-layer.mp4"))
        val before=media.frame(result.path,1000)!!;val during=media.frame(result.path,3000)!!
        check((during.getRGB(during.width/2,during.height/2) and 0xffffff)>0xeeeeee)
        check(before.getRGB(before.width/2,before.height/2)!=during.getRGB(during.width/2,during.height/2))
        val saved=File(folder,"pictures.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).pictures==listOf(picture))
        check(runCatching {exporter.export(project,file)}.isFailure)
    }
    verify("multi-source real sequence and per-source retimed words") {
        val second=media.probe(File(folder,"silent.mp4").path)
        val timeline=com.cuscus.ffmpeggui.core.timeline.Timeline(listOf(com.cuscus.ffmpeggui.core.timeline.Clip("a",video.path,endMs=2000),com.cuscus.ffmpeggui.core.timeline.Clip("b",second.path,startMs=1000,endMs=3000)))
        val project=p.copy(sources=listOf(video,second),timeline=timeline,wordsBySource=mapOf(video.path to listOf(SpokenWord(200,800,"First source")),second.path to listOf(SpokenWord(1200,1800,"Second source"))))
        val output=exporter.export(project,File(folder,"multi.mp4"));check(output.info.durationMs in 3900..4200 && output.info.hasAudio)
        val transcript=exporter.transcript(project,OutputFormat.SRT);check(transcript.contains("First source") && transcript.contains("Second source") && transcript.contains("00:00:02,200"))
        val saved=File(folder,"multi.ffgui.json");DesktopProjects.save(project,saved);val restored=DesktopProjects.load(saved,media);check(restored.sources==project.sources && restored.wordsBySource==project.wordsBySource && restored.timeline==timeline)
        media.process.checked(listOf("ffmpeg","-v","error","-i",output.path,"-f","null","-"))
    }
    verify("clip trim speed reverse and real transition export") {
        val timeline=Timeline(listOf(Clip("one",video.path,endMs=4000,effects=Effects(speed=2f,reversed=true,volume=.6f,transition=Transition(TransitionKind.FADE,500))),Clip("two",video.path,startMs=4000,endMs=6000)))
        val project=p.copy(timeline=timeline)
        val result=exporter.export(project,File(folder,"transition.mp4"))
        check(result.info.durationMs in 3400..3700 && result.info.hasAudio)
        media.process.checked(listOf("ffmpeg","-v","error","-i",result.path,"-f","null","-"))
        val first=media.frame(result.path,200)!!;val original=media.frame(video.path,200)!!
        check(first.getRGB(100,100)!=original.getRGB(100,100))
        val saved=File(folder,"transition.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).timeline==timeline)
        check(timeline.trim("one",500,3500,video.info.durationMs).clips[0].startMs==500L)
    }
    verify("clip volume keys actually change PCM over time") {
        val key=Keyframes().set(0,mapOf(KeyProperty.VOLUME to 0f)).set(2500,mapOf(KeyProperty.VOLUME to 0f)).set(3500,mapOf(KeyProperty.VOLUME to 1f)).set(7000,mapOf(KeyProperty.VOLUME to 1f))
        val project=p.copy(timeline=Timeline(listOf(Clip("keys",video.path,endMs=7000,effects=Effects(keyframes=key)))))
        val output=exporter.export(project,File(folder,"clip-keys.mp4"));val pcm=media.samples(output.path)
        fun power(a:Int,b:Int)=pcm.sliceArray(a*16000 until b*16000).maxOf {kotlin.math.abs(it)}
        check(power(0,2)<.005f && power(4,6)>.02f)
    }
    verify("mixed saved timeline types rejected") {
        val audio=media.probe(File(folder,"beat.wav").path)
        val mixed=p.copy(sources=listOf(video,audio),timeline=Timeline(listOf(Clip("v",video.path,endMs=1000),Clip("a",audio.path,endMs=1000))))
        val saved=File(folder,"mixed.ffgui.json");DesktopProjects.save(mixed,saved)
        check(runCatching {DesktopProjects.load(saved,media)}.isFailure)
    }
    verify("output clock combined speed reverse transition timed content") {
        val timeline=Timeline(listOf(Clip("fast",video.path,endMs=4000,effects=Effects(speed=2f,reversed=true,muted=true,transition=Transition(TransitionKind.FADE,500))),Clip("last",video.path,startMs=4000,endMs=6000,effects=Effects(muted=true))))
        val sticker=media.probe(File(folder,"sticker.png").path)
        val tone=media.probe(File(folder,"beat.wav").path)
        val project=p.copy(timeline=timeline,wordsBySource=mapOf(video.path to listOf(SpokenWord(2000,3000,"Reverse word"),SpokenWord(5000,5500,"Last word"))),captions=listOf(TextOverlay(text="CLOCK",x=.5f,y=.8f,startMs=2700,endMs=3300,size=.12f)),pictures=listOf(DesktopPicture(sticker,ImagePlacement(x=.5f,y=.5f,widthFraction=.3f,startMs=2700,endMs=3300,aspect=1f))),sounds=listOf(DesktopSound(tone,PlacedSound(startMs=2700,trimEndMs=500))))
        check(project.outputDurationMs==3500L && project.outputAt(2000)==1000L)
        check(project.footageRanges(2000,3000,0).first()==(2000L to 3000L))
        val cut=project.copy(timeline=project.withoutFootageRanges(project.footageRanges(2000,3000,0).map {CutRange(it.first,it.second)}))
        check(!exporter.transcript(cut,OutputFormat.SRT).contains("Reverse word"))
        val srt=exporter.transcript(project,OutputFormat.SRT)
        check("00:00:00,500" in srt && "00:00:02,500" in srt)
        val result=exporter.export(project,File(folder,"output-clock.mp4"));check(result.info.durationMs in 3400..3700)
        val before=media.frame(result.path,1000)!!;val during=media.frame(result.path,3000)!!
        check((during.getRGB(during.width/2,during.height/2) and 0xffffff)>0xeeeeee)
        check(before.getRGB(before.width/2,before.height/2)!=during.getRGB(during.width/2,during.height/2))
        val clean=exporter.export(project.copy(captions=emptyList()),File(folder,"output-clock-no-caption.mp4"))
        val plain=media.frame(clean.path,3000)!!
        var captionPixels=0
        for(y in 250 until 350)for(x in 100 until 540)if(during.getRGB(x,y)!=plain.getRGB(x,y))captionPixels++
        check(captionPixels>500)
        javax.imageio.ImageIO.write(during,"png",File(folder,"output-clock-during.png"))
        val pcm=media.samples(result.path)
        fun peak(a:Int,b:Int)=pcm.sliceArray(a until b).maxOf {kotlin.math.abs(it)}
        check(peak(0,24000)<.005f && peak(43200,51000)>.02f)
        DesktopProjects.save(project,File(folder,"output-clock.ffgui.json"))
        val split=project.splitFootageAt(listOf(1000,3000));check(split.clips.take(3).map {it.startMs}==listOf(3000L,1000L,0L))
        val slower=project.copy(effects=Effects(speed=.5f));check(slower.outputDurationMs==7000L && slower.outputAt(2000)==2000L)
    }
    verify("retimed reverse follow crop uses joined clock actual pixels") {
        val file=File(folder,"follow-clock-source.mp4")
        media.process.checked(listOf("ffmpeg","-v","error","-y","-f","lavfi","-i","color=black:s=640x360:r=25:d=4","-vf","drawbox=x=100:y=150:w=40:h=60:color=white:t=fill:enable='lt(t,2)',drawbox=x=500:y=150:w=40:h=60:color=white:t=fill:enable='gte(t,2)'","-c:v","libx264","-pix_fmt","yuv420p",file.path))
        val source=media.probe(file.path)
        val project=DesktopProject.create(source).copy(timeline=Timeline(listOf(Clip("follow",source.path,endMs=4000,effects=Effects(speed=2f,reversed=true)))))
        val original=listOf(SubjectPoint(0,120f/640,.5f,1f),SubjectPoint(1800,120f/640,.5f,1f),SubjectPoint(2200,520f/640,.5f,1f),SubjectPoint(3960,520f/640,.5f,1f))
        val mapped=original.flatMap {point->com.cuscus.ffmpeggui.core.preview.mapToTimeline(point.atMs,point.atMs+1,project.request().segments,0).map {point.copy(atMs=it.first)}}
        val track=FollowCropTrack(mapped,640,360,202,360,source.path)
        val output=exporter.export(project.copy(followCrop=track),File(folder,"follow-clock.mp4"))
        check(output.info.durationMs in 1900..2200)
        for(at in listOf(200L,1600L)) {
            val image=media.frame(output.path,at)!!
            val center=image.getRGB(image.width/2,image.height/2) and 0xffffff
            check(center>0xdddddd){"Subject not centered at $at: $center"}
        }
    }
    verify("clip-bound text rotation stretch blend and image animation") {
        val timeline=Timeline(listOf(Clip("before",video.path,endMs=2000),Clip("bound",video.path,startMs=2000,endMs=4000)))
        val sticker=media.probe(File(folder,"sticker.png").path)
        val text=TextOverlay(text="BOUND",clipId="bound",rotationDegrees=15f,stretch=1.3f,blend=LayerBlend.SCREEN,size=.15f,y=.8f)
        val keys=Keyframes().set(0,mapOf(KeyProperty.X to 0f,KeyProperty.Y to .2f,KeyProperty.SIZE to .2f)).set(1600,mapOf(KeyProperty.X to 1f,KeyProperty.Y to .2f,KeyProperty.SIZE to .2f))
        val picture=DesktopPicture(sticker,ImagePlacement(clipId="bound",widthFraction=.2f,aspect=1f,keyframes=keys))
        val project=p.copy(timeline=timeline,captions=listOf(text),pictures=listOf(picture))
        val output=exporter.export(project,File(folder,"bound-layers.mp4"))
        val plain=exporter.export(project.copy(captions=emptyList(),pictures=emptyList()),File(folder,"bound-plain.mp4"))
        suspend fun changed(at:Long,x0:Int,x1:Int,y0:Int,y1:Int):Int {
            val image=media.frame(output.path,at)!!;val base=media.frame(plain.path,at)!!;var n=0
            for(y in y0 until y1)for(x in x0 until x1)if(image.getRGB(x,y)!=base.getRGB(x,y))n++
            return n
        }
        check(changed(500,0,640,0,360)<4000)
        check(changed(2500,50,590,220,350)>2000)
        val early=media.frame(output.path,2040)!!;val late=media.frame(output.path,3600)!!
        check((early.getRGB(15,90) and 0xffffff)>0xeeeeee && (late.getRGB(late.width-15,90) and 0xffffff)>0xeeeeee)
        val saved=File(folder,"bound-layers.ffgui.json");DesktopProjects.save(project,saved);val read=DesktopProjects.load(saved,media)
        check(read.captions==listOf(text) && read.pictures==listOf(picture))
        javax.imageio.ImageIO.write(late,"png",File(folder,"bound-layers-late.png"))
    }
    verify("placed sound keyframes actual PCM clock and persistence") {
        val source=media.probe(File(folder,"silence.wav").path)
        val silent=media.probe(File(folder,"silent.mp4").path)
        val keys=Keyframes().set(0,mapOf(KeyProperty.VOLUME to 0f)).set(500,mapOf(KeyProperty.VOLUME to 0f)).set(1000,mapOf(KeyProperty.VOLUME to 1f)).set(1800,mapOf(KeyProperty.VOLUME to 1f))
        val sound=DesktopSound(source,PlacedSound(startMs=1000,trimEndMs=1800,keyframes=keys))
        val project=DesktopProject.create(silent).copy(sounds=listOf(sound))
        val output=exporter.export(project,File(folder,"sound-keys.mp4"));val pcm=media.samples(output.path)
        check(pcm.sliceArray(0 until 20000).maxOf {kotlin.math.abs(it)}<.005f)
        check(pcm.sliceArray(35000 until 42000).maxOf {kotlin.math.abs(it)}>.1f)
        val saved=File(folder,"sound-keys.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).sounds==listOf(sound))
    }
    verify("video layer actual source frames trim time and decoration") {
        val source=media.probe(File(folder,"scenes.mp4").path)
        val picture=DesktopPicture(source,ImagePlacement(x=.5f,y=.5f,widthFraction=.4f,startMs=2000,endMs=5000,video=OverlayVideo(trimStartMs=2000,trimEndMs=4000),aspect=640f/360,look=OverlayLook(shape=OverlayShape.ROUNDED,borderWidth=.02f,shadowOpacity=.4f)))
        val project=p.copy(pictures=listOf(picture))
        val output=exporter.export(project,File(folder,"video-layer.mp4"))
        val during=media.frame(output.path,3000)!!;val rgb=during.getRGB(during.width/2,during.height/2)
        check((rgb and 255)>180 && ((rgb shr 16) and 255)<60)
        val before=media.frame(output.path,1000)!!;check(before.getRGB(before.width/2,before.height/2)!=rgb)
        val saved=File(folder,"video-layer.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).pictures==listOf(picture))
        javax.imageio.ImageIO.write(during,"png",File(folder,"video-layer-proof.png"))
    }
    verify("manual studio real catalogue local render and destination guards") {
        val studio=DesktopStudio(media);val catalogue=studio.catalog()
        check(catalogue.filters.any {it.name=="scale"} && catalogue.encoders.any {it.name=="libx264"})
        val text="ffmpeg -i input.mp4 -t 1 -vf scale=320:180 -c:v libx264 -c:a aac output.mp4"
        val output=studio.export(p,text,File(folder,"manual-studio.mp4"))
        check(output.info.width==320 && output.info.height==180 && output.info.durationMs in 900..1200)
        check(runCatching {studio.export(p,text,File(video.path))}.isFailure)
        check(runCatching {studio.validate(p,"ffmpeg -i https://example.invalid/file.mp4 output.mp4")}.isFailure)
        check(runCatching {studio.validate(p,"ffmpeg -i input.mp4 /tmp/not-output.mp4")}.isFailure)
        check(runCatching {studio.validate(p,"ffmpeg -i input.mp4 -vf movie=/tmp/private output.mp4")}.isFailure)
        check(runCatching {studio.validate(p,"ffmpeg -i input.mp4 -filter_script /tmp/private output.mp4")}.isFailure)
        check(runCatching {studio.validate(p,"ffmpeg -i input.mp4 output_%03d.png")}.isFailure)
        check(com.cuscus.ffmpeggui.core.command.studio.CommandLint(catalogue,studio.paths(p).aliasEntries()).check("ffmpeg -i input.mp4").any {it.code==com.cuscus.ffmpeggui.core.command.studio.LintCode.NO_OUTPUT})
    }
    verify("speed ramp actual duration clock persistence and decode") {
        val curve=SpeedCurve(listOf(SpeedPoint(0f,.5f),SpeedPoint(.5f,2f),SpeedPoint(1f,.5f)))
        val project=p.copy(timeline=Timeline(listOf(Clip("ramp",video.path,endMs=4000,effects=Effects(speedCurve=curve)))))
        val result=exporter.export(project,File(folder,"speed-ramp.mp4"))
        check(kotlin.math.abs(result.info.durationMs-project.outputDurationMs)<180)
        check(project.outputAt(1000)>1000L)
        media.process.checked(listOf("ffmpeg","-v","error","-i",result.path,"-f","null","-"))
        val saved=File(folder,"speed-ramp.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).timeline==project.timeline)
    }
    verify("ramp transition timed layer and loop command cancellation") {
        val curve=SpeedCurve(listOf(SpeedPoint(0f,.5f),SpeedPoint(.5f,2f),SpeedPoint(1f,1f)))
        val timeline=Timeline(listOf(Clip("ramp-a",video.path,endMs=4000,effects=Effects(speedCurve=curve,transition=Transition(TransitionKind.FADE,500))),Clip("ramp-b",video.path,startMs=4000,endMs=6000)))
        val sticker=media.probe(File(folder,"sticker.png").path)
        val project=p.copy(timeline=timeline,pictures=listOf(DesktopPicture(sticker,ImagePlacement(x=.5f,y=.5f,widthFraction=.3f,startMs=1000,endMs=2000,aspect=1f))))
        val output=exporter.export(project,File(folder,"ramp-transition-layer.mp4"))
        check(kotlin.math.abs(output.info.durationMs-project.outputDurationMs)<180)
        val active=media.frame(output.path,1400)!!;check((active.getRGB(active.width/2,active.height/2) and 0xffffff)>0xeeeeee)
        val studio=DesktopStudio(media)
        val target=File(folder,"cancelled-loop.mp4");target.delete()
        val job=launch {studio.export(p,"ffmpeg -stream_loop -1 -i input.mp4 -c:v libx264 -preset veryslow -c:a aac output.mp4",target)}
        delay(400);job.cancelAndJoin();check(!target.exists())
        check(folder.listFiles()!!.none {it.name.startsWith(".studio-")})
    }
    verify("cancel real process") {
        val start=System.currentTimeMillis()
        val job=launch { media.process.run(listOf("ffmpeg","-re","-f","lavfi","-i","testsrc2=size=640x360:rate=25","-t","30","-f","null","-")) }
        delay(400);job.cancelAndJoin();check(System.currentTimeMillis()-start<4000)
    }
    println("NATIVE_CHECKS_PASSED=$passed")
}
