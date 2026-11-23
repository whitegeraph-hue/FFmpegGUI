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
    verify("external SRT ASS source clocks styling embedded project and stripped attachments") {
        val file=File(folder,"source subtitles.srt").apply {writeText("1\n00:00:00,500 --> 00:00:01,500\nExternal caption\n\n2\n00:00:02,500 --> 00:00:03,500\nSecond caption\n")}
        val utf16=File(folder,"utf16.srt").apply {writeBytes(byteArrayOf(0xff.toByte(),0xfe.toByte())+file.readText().toByteArray(Charsets.UTF_16LE))}
        check(DesktopSubtitles.import(utf16,0).content.contains("External caption"))
        val badEncoding=File(folder,"cp1252.srt").apply {writeBytes(byteArrayOf(0x96.toByte()))}
        check(runCatching {DesktopSubtitles.import(badEncoding,0)}.exceptionOrNull()?.message?.contains("encoding")==true)
        val huge=File(folder,"huge.srt");java.io.RandomAccessFile(huge,"rw").use {it.setLength(2_000_001)}
        check(runCatching {DesktopSubtitles.import(huge,0)}.isFailure)
        val imported=DesktopSubtitles.import(file,0)
        check(exporter.transcript(captioned.copy(externalSubtitles=imported),OutputFormat.TXT).contains("Hello Linux"))
        val edited=p.copy(timeline=Timeline(listOf(Clip("external",video.path,startMs=2000,endMs=4000,effects=Effects(speed=2f)))),externalSubtitles=imported,subtitleStyle=SubtitleStyle(size=.12f,highlight=WordHighlight.NONE))
        val cues=DesktopSubtitles.cuesOnTimeline(imported,edited.request());check(cues.size==1 && cues[0].startMs==250L && cues[0].endMs==750L)
        val output=exporter.export(edited,File(folder,"external-styled.mp4"))
        val no=exporter.export(edited.copy(externalSubtitles=null,subtitleStyle=null),File(folder,"external-plain.mp4"))
        val lit=media.frame(output.path,500)!!;val plain=media.frame(no.path,500)!!
        var changed=0;for(y in 230 until lit.height)for(x in 50 until 590)if(lit.getRGB(x,y)!=plain.getRGB(x,y))changed++
        check(changed>500)
        val joined=p.copy(timeline=Timeline(listOf(Clip("sub-a",video.path,endMs=2000,effects=Effects(transition=Transition(TransitionKind.FADE,600))),Clip("sub-b",video.path,startMs=2000,endMs=4000))),effects=Effects(speed=2f),externalSubtitles=imported,subtitleStyle=SubtitleStyle(size=.12f,highlight=WordHighlight.NONE))
        val joinedCues=DesktopSubtitles.cuesOnTimeline(imported,joined.request());check(joinedCues.size==2)
        val joinedOutput=exporter.export(joined,File(folder,"external-global-transition.mp4"));check(kotlin.math.abs(joinedOutput.info.durationMs-joined.outputDurationMs)<180)
        val joinedPlain=exporter.export(joined.copy(externalSubtitles=null,subtitleStyle=null),File(folder,"external-global-transition-plain.mp4"))
        val secondAt=(joinedCues[1].startMs+joinedCues[1].endMs)/4
        val joinedLit=media.frame(joinedOutput.path,secondAt)!!;val joinedNo=media.frame(joinedPlain.path,secondAt)!!
        var diff=0;for(y in 230 until joinedLit.height)for(x in 50 until 590)if(joinedLit.getRGB(x,y)!=joinedNo.getRGB(x,y))diff++
        check(diff>500)
        val saved=File(folder,"external-subtitles.ffgui.json");DesktopProjects.save(edited,saved);file.delete();val loaded=DesktopProjects.load(saved,media);check(loaded.externalSubtitles==imported)
        val plainSrt=exporter.export(edited.copy(subtitleStyle=null),File(folder,"external-default.mp4"));check(plainSrt.info.durationMs==output.info.durationMs)
        val reversed=edited.copy(effects=Effects(reversed=true));val reversedCues=DesktopSubtitles.cuesOnTimeline(imported,reversed.request());check(reversedCues.size==1)
        val ass=AssSubtitles.write(SubtitleLayout.ofWords(listOf(SpokenWord(2500,3500,"ASS caption")),SubtitleStyle(),16f/9f),SubtitleStyle(),16f/9f,"DejaVu Sans")+"\n[Fonts]\nfontname: must-not-reach-libass.ttf\nAAAA\n"
        val retimed=com.cuscus.ffmpeggui.core.preview.SubtitleRetimer().retime(ass,edited.request().segments,0)!!
        check("[Fonts]" !in retimed.content && "must-not-reach" !in retimed.content)
        val assFile=File(folder,"external.ass").apply {writeText(ass)}
        val assProject=edited.copy(externalSubtitles=DesktopSubtitles.import(assFile,0));val assResult=exporter.export(assProject,File(folder,"external-ass.mp4"));check(assResult.info.durationMs==output.info.durationMs)
        check(runCatching {DesktopSubtitles.import(File(folder,"not-a-font.ttf"),0)}.isFailure)
        javax.imageio.ImageIO.write(lit,"png",File(folder,"external-subtitle-proof.png"))
    }
    verify("custom subtitle font colour shadow limits and missing-font fallback") {
        val hostile="Font,Name\r\n[Events]\u0000"
        val safe=DesktopFonts.assFamily(hostile)
        check(safe.none {it==',' || it.isISOControl()})
        val ass=AssSubtitles.write(emptyList(),SubtitleStyle(),16f/9f,safe)
        check(ass.lines().count {it=="[Events]"}==1)
        val custom=File(folder,"custom font's.ttf")
        File("/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf").copyTo(custom,overwrite=true)
        DesktopFonts.validate(custom)
        val invalid=File(folder,"not-a-font.ttf").apply {writeText("not a font")}
        check(runCatching {DesktopFonts.validate(invalid)}.isFailure)
        val edited=captioned.copy(subtitleStyle=SubtitleStyle(fontFile=custom.path,bold=false,highlight=WordHighlight.NONE,textColor=0xff00ff00L,dropShadow=true,shadowColor=0xccff0000L,maxLines=1,size=.14f))
        val output=exporter.export(edited,File(folder,"subtitle-custom.mp4"))
        val frame=media.frame(output.path,1000)!!
        var green=0
        for(y in 200 until frame.height)for(x in 0 until frame.width){val rgb=frame.getRGB(x,y);val r=(rgb shr 16) and 255;val g=(rgb shr 8) and 255;val b=rgb and 255;if(g>140 && r<90 && b<90)green++}
        check(green>100)
        val file=File(folder,"subtitle-custom.ffgui.json");DesktopProjects.save(edited,file);check(DesktopProjects.load(file,media).subtitleStyle==edited.subtitleStyle)
        val missing=edited.copy(subtitleStyle=edited.subtitleStyle!!.copy(fontFile=File(folder,"missing.ttf").path))
        val missingOutput=exporter.export(missing,File(folder,"subtitle-fallback.mp4"))
        check(missingOutput.info.durationMs==output.info.durationMs)
        val fallbackFrame=media.frame(missingOutput.path,1000)!!
        var changed=0
        for(y in 200 until frame.height)for(x in 0 until frame.width)if(frame.getRGB(x,y)!=fallbackFrame.getRGB(x,y))changed++
        check(changed>200) {"Custom serif family must differ from sans fallback"}
        check(File(video.path).isFile && custom.isFile)
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
    verify("drawing shapes alpha eraser pixel export and save") {
        val source=media.probe(File(folder,"sticker.png").path)
        val base=DesktopProject.create(source).copy(format=OutputFormat.PNG)
        val line=Stroke(listOf(.1f,.5f,.9f,.5f),0xffff0000L,.1f,shape=StrokeShape.LINE)
        val erase=Stroke(listOf(.5f,.25f,.5f,.75f),width=.15f,eraser=true)
        val project=base.copy(image=base.image.copy(strokes=listOf(line,erase)))
        val target=File(folder,"drawing-proof.png");exporter.export(project,target)
        val before=media.frame(source.path,0)!!;val after=media.frame(target.path,0)!!
        val w=after.width;val h=after.height
        val red=after.getRGB(w/4,h/2);check(((red shr 16) and 255)>245 && ((red shr 8) and 255)<8 && (red and 255)<8)
        check(after.getRGB(w/2,h/2)==before.getRGB(w/2,h/2))
        val alpha=DesktopDrawing.render(listOf(line.copy(opacity=.5f)),200,200)
        check((alpha.getRGB(50,100) ushr 24) in 125..130)
        val shapes=DesktopDrawing.render(StrokeShape.entries.mapIndexed {i,v->Stroke(listOf(.1f,.1f+i*.15f,.8f,.2f+i*.15f),shape=v)},300,300)
        check((0 until 300).any {x->(0 until 300).any {y->(shapes.getRGB(x,y) ushr 24)>0}})
        val saved=File(folder,"drawing.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).image==project.image)
    }
    verify("manual mask keep remove geometry portable export") {
        val source=media.probe(File(folder,"sticker.png").path)
        val w=source.info.width;val h=source.info.height
        val mask=IntArray(w*h) {255}
        com.cuscus.ffmpeggui.core.cutout.MaskEditing.paint(mask,w,h,floatArrayOf(w*.5f,h*.1f,w*.5f,h*.9f),w*.15f,false)
        check(mask[(h/2)*w+w/2]==0 && mask[(h/2)*w+w/10]==255)
        com.cuscus.ffmpeggui.core.cutout.MaskEditing.paint(mask,w,h,floatArrayOf(w*.5f,h*.5f),w*.04f,true)
        check(mask[(h/2)*w+w/2]==255)
        val image=java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0,0,w,h,IntArray(w*h) {(mask[it] shl 24) or 0xffffff},0,w)
        val maskFile=File(folder,"manual-mask.png");javax.imageio.ImageIO.write(image,"png",maskFile)
        val project=DesktopProject.create(source).copy(image=ImageEdit(key=ColorKey(mode=KeyMode.SUBJECT,mask=maskFile.path)))
        val target=File(folder,"manual-mask-proof.png");exporter.export(project,target)
        val result=javax.imageio.ImageIO.read(target)
        check((result.getRGB(w/2,h/4) ushr 24)<10 && (result.getRGB(w/2,h/2) ushr 24)>245)
        val saved=File(folder,"manual-mask.ffgui.json");DesktopProjects.save(project,saved);maskFile.delete()
        val loaded=DesktopProjects.load(saved,media);check(File(loaded.image.key!!.mask!!).isFile)
        check(runCatching {exporter.export(loaded,File(loaded.image.key!!.mask!!))}.isFailure)
    }
    verify("custom four-node ramp saved real export duration") {
        val curve=SpeedCurve(listOf(SpeedPoint(0f,.5f),SpeedPoint(.26f,.67f),SpeedPoint(.56f,1.94f),SpeedPoint(1f,.5f)))
        val edited=p.copy(timeline=Timeline(listOf(Clip("custom-ramp",video.path,endMs=4000,effects=Effects(speedCurve=curve)))))
        val saved=File(folder,"custom-ramp.ffgui.json");DesktopProjects.save(edited,saved)
        val restored=DesktopProjects.load(saved,media);check(restored.timeline==edited.timeline)
        val result=exporter.export(restored,File(folder,"custom-ramp.mp4"))
        check(kotlin.math.abs(result.info.durationMs-edited.outputDurationMs)<180)
        media.process.checked(listOf("ffmpeg","-v","error","-i",result.path,"-f","null","-"))
    }
    verify("all EXIF orientations upright preview export reload and layer") {
        val raw=java.awt.image.BufferedImage(240,120,java.awt.image.BufferedImage.TYPE_INT_RGB)
        for(y in 0 until 120)for(x in 0 until 240)raw.setRGB(x,y,when {x<120 && y<60->0xff0000;x>=120 && y<60->0x00ff00;x<120->0x0000ff;else->0xffff00})
        val bytes=java.io.ByteArrayOutputStream().also {javax.imageio.ImageIO.write(raw,"jpg",it)}.toByteArray()
        val sourceHash=java.security.MessageDigest.getInstance("SHA-256")
        for(orientation in 1..8) {
            val payload=byteArrayOf(69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,orientation.toByte(),0,0,0,0,0,0,0)
            val file=File(folder,"exif-$orientation.jpg")
            file.writeBytes(bytes.take(2).toByteArray()+byteArrayOf(0xff.toByte(),0xe1.toByte(),0,(payload.size+2).toByte())+payload+bytes.drop(2).toByteArray())
            val before=sourceHash.digest(file.readBytes())
            check(DesktopStillInputs.orientation(file)==orientation)
            val source=media.probe(file.path);check(source.info.width==if(orientation>=5)120 else 240);check(source.info.height==if(orientation>=5)240 else 120)
            val frame=media.frame(file.path,0)!!
            check((frame.width>frame.height)==(orientation<5))
            val expected=when(orientation){1->0xff0000;2->0x00ff00;3->0xffff00;4->0x0000ff;5->0xff0000;6->0x0000ff;7->0xffff00;else->0x00ff00}
            fun close(actual:Int):Boolean=(0..2).all {k->kotlin.math.abs(((actual shr (8*k)) and 255)-((expected shr (8*k)) and 255))<18}
            check(close(frame.getRGB(frame.width/8,frame.height/8))) {"Preview orientation $orientation"}
            val project=DesktopProject.create(source)
            val saved=File(folder,"exif-$orientation.ffgui.json");DesktopProjects.save(project,saved)
            val loaded=DesktopProjects.load(saved,media)
            val output=File(folder,"exif-$orientation.png");val result=exporter.export(loaded,output)
            check(result.info.width==source.info.width && result.info.height==source.info.height)
            val exported=javax.imageio.ImageIO.read(output);check(close(exported.getRGB(exported.width/8,exported.height/8))) {"Export orientation $orientation"}
            check(sourceHash.digest(file.readBytes()).contentEquals(before))
        }
        val picture=media.probe(File(folder,"exif-6.jpg").path)
        val layered=p.copy(pictures=listOf(DesktopPicture(picture,ImagePlacement(x=.1f,y=.1f,widthFraction=.3f,aspect=.5f))))
        val target=exporter.export(layered,File(folder,"exif-layer.mp4"))
        check(target.info.durationMs>0)
        javax.imageio.ImageIO.write(media.frame(target.path,0),"png",File(folder,"exif-layer-proof.png"))
    }
    verify("owned mask pruning retains history set and embedded recovery") {
        val directory=DesktopMasks.directory()
        val kept=File(directory,"kept.png");val discarded=File(directory,"discarded.png")
        val bitmap=java.awt.image.BufferedImage(100,100,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        javax.imageio.ImageIO.write(bitmap,"png",kept);javax.imageio.ImageIO.write(bitmap,"png",discarded)
        val source=media.probe(File(folder,"sticker.png").path)
        val project=DesktopProject.create(source).copy(image=ImageEdit(key=ColorKey(mode=KeyMode.SUBJECT,mask=kept.path)))
        val saved=File(folder,"pruned-mask.ffgui.json");DesktopProjects.save(project,saved)
        DesktopMasks.prune(setOf(kept.path));check(kept.isFile && !discarded.exists())
        DesktopMasks.prune(emptySet());check(!kept.exists())
        val loaded=DesktopProjects.load(saved,media);check(File(loaded.image.key!!.mask!!).isFile)
    }
    verify("generated Ultra HDR JPG embedded map SDR and protected drawing") {
        val source=media.probe(File(folder,"sticker.png").path)
        val project=DesktopProject.create(source).copy(format=OutputFormat.JPG,image=ImageEdit(hdr=ImageHdr(intensity=.7f),strokes=listOf(Stroke(listOf(.1f,.5f,.9f,.5f),0xffff0000L,.15f,shape=StrokeShape.LINE))))
        val target=File(folder,"ultra-hdr.jpg");exporter.export(project,target)
        val embedded=com.cuscus.ffmpeggui.core.hdr.UltraHdr.read(target.readBytes()) ?: error("Missing HDR map")
        check(embedded.metadata.maxStops>0)
        val map=javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(embedded.gainMapJpeg))
        check(map.width==50 && map.height==50)
        // Drawing is protected from HDR amplification by the unmodified core's plain comparison.
        val middle=map.getRGB(map.width/2,map.height/2) and 255
        val corner=map.getRGB(map.width/4,map.height/8) and 255
        check(middle<corner) {"Drawing gain $middle not below white photo $corner"}
        val sdr=javax.imageio.ImageIO.read(target)
        check(((sdr.getRGB(25,50) shr 16) and 255)>200)
        val saved=File(folder,"ultra-hdr.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).image==project.image)
        javax.imageio.ImageIO.write(sdr,"png",File(folder,"ultra-hdr-sdr-proof.png"))
        javax.imageio.ImageIO.write(map,"png",File(folder,"ultra-hdr-map-proof.png"))
    }
    verify("HDR alpha area rotated geometry inverse background portable masks and overwrite guards") {
        val source=media.probe(File(folder,"sticker.png").path)
        val maskFile=File(folder,"hdr-half-mask.png");val bitmap=java.awt.image.BufferedImage(100,100,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for(y in 0 until 100)for(x in 0 until 100)bitmap.setRGB(x,y,if(x<50)0xffffffff.toInt()else 0x00ffffff)
        javax.imageio.ImageIO.write(bitmap,"png",maskFile);bitmap.flush()
        val project=DesktopProject.create(source).copy(format=OutputFormat.JPG,effects=Effects(rotation=Rotation.entries.first {it.degrees==90}),image=ImageEdit(hdr=ImageHdr(intensity=.7f,area=HdrArea.SUBJECT,mask=maskFile.path)))
        fun gain(file:File):Pair<Int,Int> {
            val embedded=com.cuscus.ffmpeggui.core.hdr.UltraHdr.read(file.readBytes()) ?: error("Missing HDR area map")
            val image=javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(embedded.gainMapJpeg))
            try {return (image.getRGB(image.width/2,image.height/4) and 255) to (image.getRGB(image.width/2,3*image.height/4) and 255)}finally {image.flush()}
        }
        val out=File(folder,"hdr-area-subject.jpg");exporter.export(project,out);val (a,b)=gain(out);check(kotlin.math.abs(a-b)>80) {"Area map not separated: $a $b"}
        val inverse=File(folder,"hdr-area-background.jpg");exporter.export(project.copy(image=project.image.copy(hdr=project.image.hdr!!.copy(area=HdrArea.BACKGROUND))),inverse);val (c,d)=gain(inverse);check((a-b)*(c-d)<0) {"Background must invert subject"}
        val bytes=maskFile.readBytes();check(runCatching {exporter.export(project,maskFile)}.isFailure);check(bytes.contentEquals(maskFile.readBytes()))
        check(runCatching {DesktopProjects.save(project,maskFile)}.isFailure)
        val saved=File(folder,"hdr-area.ffgui.json");DesktopProjects.save(project,saved);maskFile.delete()
        val restored=DesktopProjects.load(saved,media);check(restored.image.hdr!!.area==HdrArea.SUBJECT && File(restored.image.hdr!!.mask!!).isFile)
        val reexport=File(folder,"hdr-area-restored.jpg");exporter.export(restored,reexport);check(gain(reexport)==(a to b))
        val noMask=restored.copy(image=restored.image.copy(hdr=ImageHdr(area=HdrArea.BRUSH)))
        check(runCatching {exporter.export(noMask,File(folder,"hdr-no-mask.jpg"))}.isFailure)
        check(runCatching {DesktopHdr.readMask(File(folder,"video.mp4"),100,100)}.isFailure)
    }
    verify("original Ultra HDR map survives rotate export and rejects plain input") {
        val source=media.probe(File(folder,"hdr-area-subject.jpg").path)
        check(source.info.width==100 && source.info.height==100) {"Ultra HDR probe must use primary picture"}
        val frame=media.frame(source.path,0)!!;try {check((frame.getRGB(frame.width/2,frame.height/2) and 255)>200) {"Preview selected gain map instead of primary"}}finally {frame.flush()}
        val project=DesktopProject.create(source).copy(format=OutputFormat.JPG,effects=Effects(rotation=Rotation.entries.first {it.degrees==90}),image=ImageEdit(hdr=ImageHdr(mode=HdrMode.ORIGINAL,intensity=.5f)))
        val out=File(folder,"hdr-original-rotated.jpg");exporter.export(project,out)
        val primary=javax.imageio.ImageIO.read(out);try {check(primary.width==100 && (primary.getRGB(25,25) and 255)>200) {"Output must retain primary white pixels"}}finally {primary.flush()}
        val original=DesktopHdr.original(File(source.path));val restored=DesktopHdr.original(out)
        check(kotlin.math.abs(original.second.maxStops-restored.second.maxStops)<.1f)
        val map=restored.first;val left=map.pixels[map.height/2*map.width+map.width/4] and 255;val right=map.pixels[map.height/2*map.width+3*map.width/4] and 255
        check(kotlin.math.abs(left-right)>100) {"Original rotated gain map lost geometry: $left $right"}
        val plain=media.probe(File(folder,"sticker.png").path);val failed=File(folder,"hdr-original-rejected.jpg");failed.delete()
        check(runCatching {exporter.export(DesktopProject.create(plain).copy(format=OutputFormat.JPG,image=ImageEdit(hdr=ImageHdr(mode=HdrMode.ORIGINAL))),failed)}.isFailure);check(!failed.exists())
        val saved=File(folder,"hdr-original.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).image.hdr==project.image.hdr)
    }
    verify("HDR non-JPG output explicitly rejected") {
        val still=media.probe(File(folder,"exif-6.jpg").path)
        val error=runCatching {exporter.export(DesktopProject.create(still).copy(format=OutputFormat.PNG,image=ImageEdit(hdr=ImageHdr())),File(folder,"hdr-rejected.png"))}.exceptionOrNull()
        check(error?.message?.contains("not JPG")==true)
    }
    verify("picture canvas placement transparency and save") {
        val source=media.probe(File(folder,"sticker.png").path)
        val project=DesktopProject.create(source).copy(image=ImageEdit(canvas=PictureCanvas(320,180,0xff0000ffL)),photoPlacement=ImagePlacement(x=.25f,y=.1f,widthFraction=.3f,rotationDegrees=15f,aspect=1f))
        val target=File(folder,"photo-canvas.png");exporter.export(project,target)
        val image=javax.imageio.ImageIO.read(target)
        check(image.width==320 && image.height==180)
        check((image.getRGB(10,10) and 255)>245)
        check((image.getRGB(120,60) and 0xffffff)>0xeeeeee)
        val transparent=project.copy(image=project.image.copy(canvas=PictureCanvas(320,180,null)))
        val alpha=File(folder,"photo-canvas-alpha.png");exporter.export(transparent,alpha)
        check((javax.imageio.ImageIO.read(alpha).getRGB(10,10) ushr 24)<10)
        val saved=File(folder,"photo-canvas.ffgui.json");DesktopProjects.save(project,saved);val loaded=DesktopProjects.load(saved,media)
        check(loaded.photoPlacement==project.photoPlacement && loaded.image==project.image)
    }
    verify("lyrics from edited transcript output clock actual metadata and saved timing") {
        val edited=captioned.copy(effects=Effects(speed=2f))
        val words=edited.request().timedWords()
        val lrc=LyricsText.synced(words,edited.effects.effectiveSpeed)
        check("[00:00.25]" in lrc)
        val parsed=LrcLines.parse(lrc)
        val stamped=LrcLines.format(parsed.copy(lines=parsed.lines.mapIndexed {i,v->if(i==0)v.copy(timeMs=1230)else v}))
        check(LrcLines.parse(stamped).lines.first().timeMs==1230L)
        val project=edited.copy(format=OutputFormat.MP3,tags=MetadataTags(lyrics=stamped))
        val result=exporter.export(project,File(folder,"lyrics.mp3"))
        val info=media.process.checked(listOf("ffprobe","-v","error","-show_entries","format_tags","-of","json",result.path))
        check(info.contains("Hello Linux") && info.contains("[00:01.23]"))
        val file=File(folder,"lyrics.ffgui.json");DesktopProjects.save(project,file);check(DesktopProjects.load(file,media).tags.lyrics==stamped)
    }
    verify("real two-pass stabilization joined clip effects and project persistence") {
        val edited=p.copy(timeline=Timeline(listOf(Clip("stable-a",video.path,endMs=1800,effects=Effects(speed=1.5f)),Clip("stable-b",video.path,startMs=3000,endMs=4500,effects=Effects(reversed=true)))),stabilization=Stabilization(7,12))
        val phases=mutableListOf<String>()
        val output=exporter.export(edited,File(folder,"stabilized.mp4")){phases+=it}
        check(phases.size==2 && phases[0].contains("1 of 2") && phases[1].contains("2 of 2"))
        check(kotlin.math.abs(output.info.durationMs-edited.outputDurationMs)<180)
        check(media.frame(output.path,500)!=null)
        val file=File(folder,"stabilized.ffgui.json");DesktopProjects.save(edited,file);check(DesktopProjects.load(file,media).stabilization==edited.stabilization)
    }
    verify("custom transition families actual motion duration and saved state") {
        val left=media.probe(File(folder,"scenes.mp4").path)
        val first=Clip("family-left",left.path,endMs=2000)
        val last=Clip("family-right",left.path,startMs=2000,endMs=4000)
        for(kind in listOf(TransitionKind.FLASH,TransitionKind.CUBE_LEFT,TransitionKind.RGB_SPLIT,TransitionKind.HEARTS,TransitionKind.ZOOM_BLUR,TransitionKind.PAGE_TURN,TransitionKind.WIPE_UP)) {
            val project=DesktopProject.create(left).copy(timeline=Timeline(listOf(first.copy(effects=Effects(transition=Transition(kind,700))),last)))
            val result=exporter.export(project,File(folder,"transition-${kind.name}.mp4"))
            check(kotlin.math.abs(result.info.durationMs-project.outputDurationMs)<180)
            val mid=media.frame(result.path,1650)!!
            var mixed=0
            for(y in 0 until mid.height step 10)for(x in 0 until mid.width step 10){val rgb=mid.getRGB(x,y);val r=(rgb shr 16) and 255;val b=rgb and 255;if(r>20 && b>20)mixed++}
            check(mixed>0 || kind in listOf(TransitionKind.CUBE_LEFT,TransitionKind.PAGE_TURN,TransitionKind.WIPE_UP,TransitionKind.HEARTS)) {"No transition pixels for $kind"}
            val saved=File(folder,"transition-${kind.name}.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).timeline==project.timeline)
        }
    }
    verify("all modulation families lo-fi and dynamics actual PCM and persistence") {
        val audio=media.probe(File(folder,"beat.wav").path)
        val base=DesktopProject.create(audio).copy(format=OutputFormat.WAV)
        val plain=media.process.checked(listOf("ffmpeg","-v","error","-i",audio.path,"-f","md5","-"))
        for(kind in ModulationKind.entries) {
            val fx=AudioEffects(lofiBits=8,modulation=Modulation(kind=kind,rateHz=2f,depth=.6f),gate=NoiseGate(-60f,-20f),compressor=Compressor(-20f,2f,10f,100f,2f),limiter=Limiter(-2f))
            val project=base.copy(audioEffects=fx)
            val result=exporter.export(project,File(folder,"modulation-${kind.name}.wav"))
            check(kotlin.math.abs(result.info.durationMs-audio.info.durationMs)<50)
            val hash=media.process.checked(listOf("ffmpeg","-v","error","-i",result.path,"-f","md5","-"));check(hash!=plain)
            val saved=File(folder,"modulation-${kind.name}.ffgui.json");DesktopProjects.save(project,saved);check(DesktopProjects.load(saved,media).audioEffects==fx)
        }
        val failed=runCatching {exporter.export(base.copy(format=OutputFormat.OGG),File(folder,"invalid-vorbis.ogg"))}
        check(failed.isFailure && failed.exceptionOrNull()!!.message.orEmpty().contains("Vorbis rejected"))
    }
    verify("portable album art attached picture and Ogg metadata exports") {
        val art=File(folder,"album-art-source.png")
        val bitmap=java.awt.image.BufferedImage(900,600,java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics=bitmap.createGraphics();graphics.color=java.awt.Color.RED;graphics.fillRect(0,0,900,600);graphics.color=java.awt.Color.BLUE;graphics.fillRect(250,150,400,300);graphics.dispose()
        javax.imageio.ImageIO.write(bitmap,"png",art);bitmap.flush()
        val encoded=DesktopCover.prepare(art,media)
        check(com.cuscus.ffmpeggui.core.command.CoverTags.pictureSize(DesktopCover.decode(encoded))==(600 to 600))
        val audio=media.probe(File(folder,"beat.wav").path)
        val project=DesktopProject.create(audio).copy(coverPng=encoded)
        val saved=File(folder,"album.ffgui.json");DesktopProjects.save(project,saved);art.delete()
        val restored=DesktopProjects.load(saved,media);check(restored.coverPng==encoded)
        for(format in listOf(OutputFormat.MP3,OutputFormat.M4A,OutputFormat.FLAC,OutputFormat.OGG,OutputFormat.OPUS)) {
            val output=exporter.export(restored.copy(format=format,encoding=restored.encoding.copy(audio=restored.encoding.audio.copy(bitrateKbps=96,sampleRate=48000))),File(folder,"album.${format.name.lowercase()}"))
            val probe=media.process.checked(listOf("ffprobe","-v","error","-show_streams","-of","json",output.path))
            check(probe.contains("attached_pic") && probe.contains("600")) {"No album art in $format"}
            val decoded=File(folder,"album-${format.name}.png")
            media.process.checked(listOf("ffmpeg","-nostdin","-v","error","-y","-i",output.path,"-map","0:v:0","-frames:v","1",decoded.path))
            val image=javax.imageio.ImageIO.read(decoded);check(image.width==600 && image.height==600);check((image.getRGB(300,300) and 255)>180);image.flush()
        }
        check(runCatching {exporter.export(restored.copy(format=OutputFormat.WAV),File(folder,"album-invalid.wav"))}.isFailure)
        check(runCatching {DesktopCover.decode("bad")}.isFailure)
    }
    verify("compression two-pass HEVC JPG fit PNG palette and strict target failure") {
        val phases=mutableListOf<String>()
        val compressed=p.copy(compression=Compression(CompressionLevel.BALANCED,300_000))
        val output=File(folder,"compressed-hevc.mp4")
        val result=exporter.export(compressed,output){phases+=it}
        check(output.length()<=300_000 && kotlin.math.abs(result.info.durationMs-p.outputDurationMs)<100)
        check(phases.any {it.contains("Compression pass 1")} && phases.any {it.contains("Compression pass 2")})
        val codec=media.process.checked(listOf("ffprobe","-v","error","-select_streams","v:0","-show_entries","stream=codec_name","-of","csv=p=0",output.path));check(codec.trim()=="hevc")
        media.process.checked(listOf("ffmpeg","-v","error","-i",output.path,"-f","null","-"))
        val saved=File(folder,"compressed.ffgui.json");DesktopProjects.save(compressed,saved);check(DesktopProjects.load(saved,media).compression==compressed.compression)
        val image=java.awt.image.BufferedImage(480,320,java.awt.image.BufferedImage.TYPE_INT_RGB)
        val random=java.util.Random(17)
        for(y in 0 until image.height)for(x in 0 until image.width)image.setRGB(x,y,random.nextInt())
        val photo=File(folder,"compression-source.png");javax.imageio.ImageIO.write(image,"png",photo);image.flush()
        val still=DesktopProject.create(media.probe(photo.path))
        val jpeg=File(folder,"fitted.jpg");exporter.export(still.copy(format=OutputFormat.JPG,compression=Compression(targetBytes=100_000)),jpeg);check(jpeg.length()<=100_000)
        val png=File(folder,"palette.png");exporter.export(still.copy(compression=Compression(pngPalette=true)),png)
        val audio=DesktopProject.create(media.probe(File(folder,"beat.wav").path)).copy(format=OutputFormat.MP3,compression=Compression(CompressionLevel.SMALLEST))
        val sound=File(folder,"compressed-audio.mp3");exporter.export(audio,sound);check(sound.length()>100)
        check(runCatching {exporter.export(still.copy(format=OutputFormat.SVG,compression=Compression()),File(folder,"bad-compressed.svg"))}.isFailure)
        check(media.probe(png.path).info.width==480)
        val protected=File(folder,"tiny-compressed.mp4").apply {writeText("keep existing output")}
        check(runCatching {exporter.export(p.copy(compression=Compression(targetBytes=100)),protected)}.isFailure)
        check(protected.readText()=="keep existing output")
        check(folder.listFiles()!!.none {it.name.startsWith(".tiny-compressed-")})
    }
    verify("subtitle translation preserves ASS SRT clocks and project content") {
        val srt="1\n00:00:01,000 --> 00:00:02,000\nHello world\n"
        val content=com.cuscus.ffmpeggui.core.translate.SubtitleTranslation.rewrite(srt,listOf("Bonjour le monde"))
        val project=p.copy(externalSubtitles=DesktopSubtitles("fr.srt",content,0))
        val saved=File(folder,"translated.ffgui.json");DesktopProjects.save(project,saved)
        val restored=DesktopProjects.load(saved,media);check(restored.externalSubtitles!!.content==content)
        check(content.contains("00:00:01,000 --> 00:00:02,000"))
        val ass="[Script Info]\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\an8}Hello"
        val result=com.cuscus.ffmpeggui.core.translate.SubtitleTranslation.rewrite(ass,listOf("Bonjour"))
        check(result.contains("0:00:01.00,0:00:02.00") && result.contains("{\\an8}Bonjour"))
        check(DesktopTranslation.validRepo("Xenova/opus-mt-en-fr"))
        check(!DesktopTranslation.validRepo("../../opus-mt-en-fr") && !DesktopTranslation.validRepo("Xenova/opus-mt-en-mul"))
    }
    verify("ONNX external data nested graph and unsupported containers rejected") {
        fun field(n:Int,data:ByteArray):ByteArray {
            fun varint(v:Int):ByteArray {var x=v;val bytes=mutableListOf<Byte>();do {val b=x and 127;x=x ushr 7;bytes+=(b or if(x>0)128 else 0).toByte()}while(x>0);return bytes.toByteArray()}
            return varint((n shl 3) or 2)+varint(data.size)+data
        }
        val file=File(folder,"external-data-test.onnx")
        val tensor=field(13,field(1,"location".toByteArray())+field(2,"outside.bin".toByteArray()))
        file.writeBytes(field(7,field(5,tensor)))
        check(runCatching {DesktopOnnx.singleFile(file)}.exceptionOrNull()!!.message!!.contains("External-data"))
        for(n in listOf(5,10)) {file.writeBytes(field(7,field(1,field(5,field(n,tensor)))));check(runCatching {DesktopOnnx.singleFile(file)}.isFailure)}
        for(n in listOf(6,11)) {file.writeBytes(field(7,field(1,field(5,field(n,field(5,tensor))))));check(runCatching {DesktopOnnx.singleFile(file)}.isFailure)}
        file.writeBytes(field(7,field(15,byteArrayOf())));check(runCatching {DesktopOnnx.singleFile(file)}.isFailure)
        for(n in listOf(22,23)) {file.writeBytes(field(7,field(1,field(5,field(n,byteArrayOf())))));check(runCatching {DesktopOnnx.singleFile(file)}.isFailure)}
        val nested=field(7,field(1,field(5,field(6,field(5,tensor)))))
        file.writeBytes(nested);check(runCatching {DesktopOnnx.singleFile(file)}.isFailure)
        file.writeBytes(field(7,field(5,byteArrayOf(112,1))));check(runCatching {DesktopOnnx.singleFile(file)}.isFailure)
        for(n in listOf(20,25)){file.writeBytes(field(n,byteArrayOf()));check(runCatching {DesktopOnnx.singleFile(file)}.isFailure)}
        file.writeBytes(field(7,field(5,field(9,byteArrayOf(1,2,3)))));DesktopOnnx.singleFile(file)
        file.writeBytes(byteArrayOf(58,127));check(runCatching {DesktopOnnx.singleFile(file)}.isFailure)
    }
    verify("AI picture disclosures survive stripped exports layers studio and recovery") {
        val marked=File(folder,"marked-source.png");File(folder,"photo.png").copyTo(marked,true)
        check(com.cuscus.ffmpeggui.core.provenance.AiSeal.mark(marked,com.cuscus.ffmpeggui.core.provenance.AiDisclosure(pictures=true))==com.cuscus.ffmpeggui.core.provenance.AiSeal.Marked.WRITTEN)
        val source=media.probe(marked.path);check(source.aiPicture)
        val markedProject=DesktopProject.create(source).copy(tags=MetadataTags(stripAll=true))
        for(format in listOf(OutputFormat.PNG,OutputFormat.JPG,OutputFormat.WEBP)) {
            val output=File(folder,"disclosed.${format.name.lowercase()}")
            exporter.export(markedProject.copy(format=format),output);check(DesktopProvenance.aiPicture(output))
            media.process.checked(listOf("ffmpeg","-v","error","-i",output.path,"-f","null","-"))
        }
        val layered=p.copy(pictures=listOf(DesktopPicture(source)),tags=MetadataTags(stripAll=true),timeline=Timeline(listOf(Clip("marked",video.path,endMs=1000))))
        for(format in listOf(OutputFormat.MP4,OutputFormat.MKV)) {
            val output=File(folder,"disclosed.${format.name.lowercase()}");exporter.export(layered.copy(format=format),output)
            check(DesktopProvenance.aiPicture(output));media.process.checked(listOf("ffmpeg","-v","error","-i",output.path,"-f","null","-"))
        }
        val saved=File(folder,"disclosed.ffgui.json");DesktopProjects.save(layered,saved);check(DesktopProjects.load(saved,media).pictures.single().source.aiPicture)
        check(!DesktopProvenance.needed(layered.copy(pictures=layered.pictures.map {it.copy(hidden=true)})))
        val forbidden=File(folder,"disclosed.bmp").apply {writeText("keep")}
        check(runCatching {exporter.export(markedProject.copy(format=OutputFormat.BMP),forbidden)}.isFailure);check(forbidden.readText()=="keep")
        val hdrOutput=File(folder,"disclosed-hdr.jpg")
        exporter.export(markedProject.copy(format=OutputFormat.JPG,image=ImageEdit(hdr=ImageHdr(intensity=.7f))),hdrOutput)
        check(DesktopProvenance.aiPicture(hdrOutput))
        check(com.cuscus.ffmpeggui.core.hdr.UltraHdr.read(hdrOutput.readBytes())!=null)
        val studio=DesktopStudio(media);val output=File(folder,"disclosed-studio.png")
        studio.export(markedProject,"ffmpeg -i input.png -frames:v 1 output.png",output);check(DesktopProvenance.aiPicture(output))
    }
    verify("AI voice disclosures survive sound import re-encode strip and recovery") {
        val voice=File(folder,"disclosed-voice.wav")
        media.process.checked(listOf("ffmpeg","-nostdin","-v","error","-y","-f","lavfi","-i","sine=frequency=330:sample_rate=24000","-t","2",voice.path))
        check(com.cuscus.ffmpeggui.core.provenance.AiSeal.mark(voice,com.cuscus.ffmpeggui.core.provenance.AiDisclosure(speech=true),com.cuscus.ffmpeggui.core.provenance.AiDisclosure.TRAINED)==com.cuscus.ffmpeggui.core.provenance.AiSeal.Marked.WRITTEN)
        val source=media.probe(voice.path);check(source.aiSpeech && !source.aiPicture)
        val sound=DesktopSound(source,PlacedSound(startMs=0,trimEndMs=2000))
        val layered=p.copy(sounds=listOf(sound),tags=MetadataTags(stripAll=true))
        check(DesktopProvenance.speechNeeded(layered));check(layered.request().sounds.single().aiVoice!=null)
        for(format in listOf(OutputFormat.MP4,OutputFormat.MKV,OutputFormat.MP3,OutputFormat.FLAC,OutputFormat.WAV)) {
            val output=File(folder,"voice-disclosed.${format.name.lowercase()}");exporter.export(layered.copy(format=format),output)
            check(DesktopProvenance.aiSpeech(output));check(!media.probe(output.path).aiPicture);media.process.checked(listOf("ffmpeg","-v","error","-i",output.path,"-f","null","-"))
        }
        val saved=File(folder,"voice-disclosed.ffgui.json");DesktopProjects.save(layered,saved);check(DesktopProjects.load(saved,media).sounds.single().source.aiSpeech)
        check(!DesktopProvenance.speechNeeded(layered.copy(sounds=listOf(sound.copy(muted=true)))))
        check(!DesktopProvenance.speechNeeded(layered.copy(sounds=listOf(sound.copy(placed=sound.placed.copy(volume=0f))))))
        val studioOut=File(folder,"voice-studio.wav");val studio=DesktopStudio(media);val alias=studio.aliases(layered).entries.first {it.value==voice.canonicalPath}.key;studio.export(layered,"ffmpeg -i $alias output.wav",studioOut);check(DesktopProvenance.aiSpeech(studioOut))
        val generic=File(folder,"generic-external.png");java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB).let {javax.imageio.ImageIO.write(it,"png",generic);it.flush()}
        check(com.cuscus.ffmpeggui.core.provenance.AiSeal.mark(generic,com.cuscus.ffmpeggui.core.provenance.AiDisclosure(pictures=true))==com.cuscus.ffmpeggui.core.provenance.AiSeal.Marked.WRITTEN)
        check(DesktopProvenance.aiPicture(generic))
        val combined=File(folder,"combined-disclosure.mp4");File(folder,"voice-disclosed.mp4").copyTo(combined,overwrite=true)
        check(com.cuscus.ffmpeggui.core.provenance.AiSeal.mark(combined,com.cuscus.ffmpeggui.core.provenance.AiDisclosure(pictures=true,speech=true))==com.cuscus.ffmpeggui.core.provenance.AiSeal.Marked.WRITTEN)
        val both=media.probe(combined.path);check(both.aiSpeech && both.aiPicture)
        val genericType=File(folder,"generic-external.svg").apply {writeText("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"2\" height=\"2\"><metadata>"+com.cuscus.ffmpeggui.core.provenance.Xmp.packet(com.cuscus.ffmpeggui.core.provenance.AiDisclosure()).replace("<rdf:li xml:lang=\"x-default\"></rdf:li>","<rdf:li xml:lang=\"x-default\">External picture</rdf:li>")+"</metadata></svg>")}
        check(DesktopProvenance.aiPicture(genericType))
        val main=DesktopProject(listOf(source),Timeline(listOf(Clip("voice",source.path,endMs=source.info.durationMs))));val mainOut=File(folder,"voice-main.mp3");exporter.export(main.copy(format=OutputFormat.MP3,tags=MetadataTags(stripAll=true)),mainOut);check(DesktopProvenance.aiSpeech(mainOut))
        val image=File(folder,"voice-no-speech.png");exporter.export(layered.copy(format=OutputFormat.PNG),image);check(!DesktopProvenance.aiSpeech(image))
    }
    verify("dub cue snapshots use edited output clock including global speed") {
        val original="1\n00:00:01,000 --> 00:00:03,000\nHello Linux.\n\n2\n00:00:05,000 --> 00:00:07,000\nSecond cue.\n"
        val edit=p.copy(timeline=Timeline(listOf(Clip("dub-clock",video.path,startMs=2000,endMs=8000,effects=Effects(speed=2f)))),effects=Effects(speed=2f),externalSubtitles=DesktopSubtitles("clock.srt",original,0))
        val lines=DesktopDub.projectLines(edit)
        check(lines.size==2);check(lines[0].startMs==0L && lines[0].endMs==250L);check(lines[1].startMs==750L && lines[1].endMs==1250L)
        check(lines.all {it.endMs<=edit.outputDurationMs})
        val transcript=p.copy(words=listOf(SpokenWord(1000,2000,"Hello Linux.")),effects=Effects(speed=2f))
        val spoken=DesktopDub.projectLines(transcript);check(spoken.single().startMs==500L)
    }
    verify("cancel real process") {
        val start=System.currentTimeMillis()
        val job=launch { media.process.run(listOf("ffmpeg","-re","-f","lavfi","-i","testsrc2=size=640x360:rate=25","-t","30","-f","null","-")) }
        delay(400);job.cancelAndJoin();check(System.currentTimeMillis()-start<4000)
    }
    println("NATIVE_CHECKS_PASSED=$passed")
}
