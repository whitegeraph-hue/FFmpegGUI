package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.command.*
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline
import com.cuscus.ffmpeggui.core.time.expectedOutputDurationMs
import com.cuscus.ffmpeggui.core.engine.*
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.editing.*
import com.cuscus.ffmpeggui.core.subtitles.*
import com.cuscus.ffmpeggui.core.timeline.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/** Direct ProcessBuilder adapter. Never sends file names or commands through a shell. */
class DesktopProcess {
    suspend fun run(arguments: List<String>, onLog: (String) -> Unit = {}): Pair<Int, String> = withContext(Dispatchers.IO) {
        require(arguments.isNotEmpty())
        val log = File.createTempFile("ffmpeggui-", ".log")
        var process: Process? = null
        try {
            process = ProcessBuilder(arguments).redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectErrorStream(true).redirectOutput(log).start()
            process.outputStream.close()
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) currentCoroutineContext().ensureActive()
            currentCoroutineContext().ensureActive()
            val text = log.readText().takeLast(100_000)
            onLog(text)
            process.exitValue() to text
        } finally {
            process?.let { if (it.isAlive) { it.destroy(); if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroyForcibly() } }
            log.delete()
        }
    }
    suspend fun checked(arguments: List<String>): String {
        val (code, log) = run(arguments)
        check(code == 0) { log.takeLast(6000).ifBlank { "Process failed ($code)" } }
        return log
    }
}

class DesktopFfmpegExecutor(private val process: DesktopProcess = DesktopProcess()) : FfmpegExecutor {
    override suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult = try {
        val (code, _) = process.run(listOf("ffmpeg", "-nostdin") + command.arguments, onLog)
        if (code == 0) ExecutionResult.Success else ExecutionResult.Failed(code)
    } catch (e: CancellationException) { throw e }
}

data class DesktopSource(val path: String, val info: MediaInfo)
data class DesktopPicture(val source:DesktopSource,val placement:ImagePlacement=ImagePlacement(),val hidden:Boolean=false)
data class DesktopSound(val source:DesktopSource,val placed:PlacedSound=PlacedSound(),val muted:Boolean=false)

class DesktopMedia(val process: DesktopProcess = DesktopProcess()) {
    suspend fun probe(path: String): DesktopSource {
        val file = File(path).canonicalFile
        require(file.isFile && file.canRead()) { "Cannot read this file." }
        val data = Json.parseToJsonElement(process.checked(listOf("ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", file.path))).jsonObject
        val streams = data["streams"]!!.jsonArray.map { it.jsonObject }
        val video = streams.firstOrNull { it["codec_type"]?.jsonPrimitive?.content == "video" && it["disposition"]?.jsonObject?.get("attached_pic")?.jsonPrimitive?.intOrNull != 1 }
        val audio = streams.any { it["codec_type"]?.jsonPrimitive?.content == "audio" }
        val duration = ((data["format"]?.jsonObject?.get("duration")?.jsonPrimitive?.doubleOrNull ?: video?.get("duration")?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong()
        val still = duration == 0L && video != null
        var w = video?.get("width")?.jsonPrimitive?.intOrNull ?: 0
        var h = video?.get("height")?.jsonPrimitive?.intOrNull ?: 0
        val rotation = video?.get("side_data_list")?.jsonArray?.mapNotNull { it.jsonObject["rotation"]?.jsonPrimitive?.intOrNull }?.firstOrNull() ?: 0
        if (kotlin.math.abs(rotation) % 180 == 90) { val swap = w; w = h; h = swap }
        val rate = video?.get("avg_frame_rate")?.jsonPrimitive?.content?.split('/')?.let { if (it.size == 2 && it[1].toFloatOrNull() != 0f) (it[0].toFloatOrNull() ?: 0f) / (it[1].toFloatOrNull() ?: 1f) else 0f } ?: 0f
        return DesktopSource(file.path, MediaInfo.of(duration, video != null, w, h, file.length(), audio, still, frameRate = rate,hasAlpha=video?.get("pix_fmt")?.jsonPrimitive?.content?.let {it.startsWith("rgba") || it.startsWith("bgra") || it.startsWith("argb") || it.startsWith("abgr") || it.startsWith("yuva") || it.startsWith("gbrap") || it.startsWith("ya")} ?: false))
    }
    suspend fun frame(path: String, atMs: Long, filters: String? = null): BufferedImage? {
        val target = File.createTempFile("ffmpeggui-frame-", ".png")
        return try {
            val args = mutableListOf("ffmpeg", "-y", "-v", "error", "-ss", (atMs/1000.0).toString(), "-i", path, "-frames:v", "1")
            args += listOf("-vf", listOfNotNull(filters, "scale=960:640:force_original_aspect_ratio=decrease").joinToString(","), target.path)
            process.checked(args)
            ImageIO.read(target)
        } finally { target.delete() }
    }
    suspend fun samples(path: String): FloatArray {
        val target = File.createTempFile("ffmpeggui-audio-", ".pcm")
        return try {
            process.checked(listOf("ffmpeg", "-y", "-v", "error", "-i", path, "-vn", "-ac", "1", "-ar", "16000", "-f", "f32le", target.path))
            require(target.length() <= 16000L * 4 * 60 * 10) { "This preview limits beat analysis to ten minutes to keep memory bounded." }
            val bytes = target.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            FloatArray(buffer.remaining()).also { buffer.get(it) }
        } finally { target.delete() }
    }
    suspend fun silence(source: DesktopSource): List<Pair<Long, Long>> = SilenceDetect.parse(process.checked(listOf("ffmpeg") + SilenceDetect.command(source.path).arguments), source.info.durationMs)
    suspend fun scenes(source: DesktopSource): List<Long> = SceneDetect.splitPoints(SceneDetect.parseShowinfo(process.checked(listOf("ffmpeg") + SceneDetect.command(source.path).arguments)), source.info.durationMs)
}

/** Mutable desktop state wraps the exact same immutable Timeline/Effects/ConversionRequest as Android. */
class DesktopProject(val sources: List<DesktopSource>, val timeline: Timeline,
    val format: OutputFormat = OutputFormat.MP4, val effects: Effects = Effects(),
    val encoding: EncodingSettings = EncodingSettings(), val words: List<SpokenWord> = emptyList(),
    val followCrop: FollowCropTrack? = null, val subtitleStyle: SubtitleStyle? = null, val audioEffects:AudioEffects=AudioEffects.NONE,val image:ImageEdit=ImageEdit(),val captions:List<TextOverlay> = emptyList(),val sounds:List<DesktopSound> = emptyList(),val tags:MetadataTags=MetadataTags(),val canvas:CanvasRatio=CanvasRatio.FIT,val canvasFit:CanvasFit=CanvasFit.FIT,val normalize:Boolean=false,val pictures:List<DesktopPicture> = emptyList(),val wordsBySource:Map<String,List<SpokenWord>> = emptyMap()) {
    fun copy(sources:List<DesktopSource> = this.sources,timeline: Timeline = this.timeline, format: OutputFormat = this.format,
        effects: Effects = this.effects, encoding: EncodingSettings = this.encoding,
        words: List<SpokenWord> = this.words, followCrop: FollowCropTrack? = this.followCrop,
        subtitleStyle: SubtitleStyle? = this.subtitleStyle,audioEffects:AudioEffects=this.audioEffects,image:ImageEdit=this.image,captions:List<TextOverlay> = this.captions,sounds:List<DesktopSound> = this.sounds,tags:MetadataTags=this.tags,canvas:CanvasRatio=this.canvas,canvasFit:CanvasFit=this.canvasFit,normalize:Boolean=this.normalize,pictures:List<DesktopPicture> = this.pictures,wordsBySource:Map<String,List<SpokenWord>> = this.wordsBySource) = DesktopProject(sources, timeline, format, effects, encoding, words, followCrop, subtitleStyle,audioEffects,image,captions,sounds,tags,canvas,canvasFit,normalize,pictures,wordsBySource)
    fun request(): ConversionRequest = ConversionRequest(format, File(sources.first().path).name,
        sources = sources.map { s -> s.info.let { SourceInfo(it.durationMs, it.width, it.height, it.hasAudio, it.isStill, it.hdr, it.frameRate, it.hasAlpha) } },
        segments = timeline.clips.map { c -> Segment(sources.indexOfFirst { it.path == c.sourceId }, c.startMs, c.endMs, c.effects, c.id) },
        effects = effects, encoding = encoding, audioEffects=audioEffects,image=image,metadata=tags,canvas=canvas,canvasFit=canvasFit,normalizeAudio=normalize,imageOverlays=pictures.filter {it.activeOn(timeline)}.map {it.placement},sounds=sounds.filter {!it.muted}.map {it.placed},textOverlays=captions.filter { !it.hidden && (it.clipId==null || timeline.clips.any {c->c.id==it.clipId}) }, followCrop = followCrop, subtitleStyle = subtitleStyle,
        spokenWords = sources.mapIndexedNotNull {i,_->sourceWords(i).takeIf {it.isNotEmpty()}?.let {SourceWords(i,it)} })
    fun clock()=PreviewTimeline(timeline.clips.map {PreviewTimeline.SegmentTiming(it.lengthMs,it.effects.speed,it.effects.reversed,it.effects.curve,it.effects.keepPitch,it.effects.transition)},effects.speed,effects.reversed,effects.keepPitch)
    val outputDurationMs:Long get()=request().expectedOutputDurationMs() ?: timeline.durationMs
    fun outputAt(mediaMs:Long)=clock().outputMs(mediaMs).coerceIn(0,outputDurationMs)
    fun footageRanges(from:Long,to:Long,sourceIndex:Int)=com.cuscus.ffmpeggui.core.preview.mapToTimeline(from,to,request().segments.map {it.copy(effects=Effects.NONE)},sourceIndex)
    fun splitFootageAt(points:List<Long>):Timeline {
        var offset=0L
        return Timeline(timeline.clips.flatMap {clip->
            val bounds=listOf(0L)+points.map {it-offset}.filter {it>=120 && it<=clip.lengthMs-120}.distinct().sorted()+listOf(clip.lengthMs)
            offset+=clip.lengthMs
            val parts=bounds.zipWithNext().mapIndexed {i,(a,b)->clip.copy(id=if(i==0)clip.id else UUID.randomUUID().toString(),startMs=clip.startMs+a,endMs=clip.startMs+b,effects=clip.effects.windowed(a.toDouble()/clip.lengthMs,b.toDouble()/clip.lengthMs).copy(fadeInSeconds=if(a==0L)clip.effects.fadeInSeconds else 0f,fadeOutSeconds=if(b==clip.lengthMs)clip.effects.fadeOutSeconds else 0f,transition=if(b==clip.lengthMs)clip.effects.transition else null))}
            val ordered=if(clip.effects.reversed)parts.reversed() else parts
            ordered.mapIndexed {i,part->part.copy(effects=part.effects.copy(fadeInSeconds=if(i==0)clip.effects.fadeInSeconds else 0f,fadeOutSeconds=if(i==ordered.lastIndex)clip.effects.fadeOutSeconds else 0f,transition=if(i==ordered.lastIndex)clip.effects.transition else null))}
        })
    }
    fun withoutFootageRanges(cuts:List<CutRange>):Timeline {
        var offset=0L
        val clips=timeline.clips.flatMap {clip->
            var spans=listOf(0L to clip.lengthMs)
            cuts.forEach {cut->val a=(cut.startMs-offset).coerceIn(0,clip.lengthMs);val b=(cut.endMs-offset).coerceIn(0,clip.lengthMs)
                if(b>a)spans=spans.flatMap {(x,y)->if(b<=x || a>=y)listOf(x to y) else listOf(x to minOf(a,y),maxOf(b,x) to y).filter {it.second-it.first>=120}}
            }
            offset+=clip.lengthMs
            val parts=spans.mapIndexed {i,(a,b)->clip.copy(id=if(i==0)clip.id else UUID.randomUUID().toString(),startMs=clip.startMs+a,endMs=clip.startMs+b,effects=clip.effects.windowed(a.toDouble()/clip.lengthMs,b.toDouble()/clip.lengthMs))}
            val ordered=if(clip.effects.reversed)parts.reversed() else parts
            ordered.mapIndexed {i,part->part.copy(effects=part.effects.copy(fadeInSeconds=if(i==0)clip.effects.fadeInSeconds else 0f,fadeOutSeconds=if(i==ordered.lastIndex)clip.effects.fadeOutSeconds else 0f,transition=if(i==ordered.lastIndex)clip.effects.transition else null))}
        }
        return if(clips.isEmpty())timeline else Timeline(clips)
    }

    fun sourceWords(index:Int):List<SpokenWord> = sources.getOrNull(index)?.path?.let {wordsBySource[it]} ?: if(index==0)words else emptyList()
    companion object {
        fun create(source: DesktopSource) = DesktopProject(listOf(source), Timeline(listOf(Clip(UUID.randomUUID().toString(), source.path, endMs = if (source.info.isStill) 5000 else source.info.durationMs))),
            format = if (source.info.isStill) OutputFormat.PNG else if (!source.info.isVideo) OutputFormat.MP3 else OutputFormat.MP4)
    }
}

class DesktopExport(private val media: DesktopMedia = DesktopMedia()) {
    val fonts = FontProvider { bold ->
        val name = if (bold) "DejaVuSans-Bold.ttf" else "DejaVuSans.ttf"
        val candidates = listOf(File(System.getProperty("compose.application.resources.dir", "resources"), "fonts/$name"), File("/usr/share/fonts/truetype/dejavu/$name"))
        (candidates.firstOrNull { it.isFile } ?: error("Required font is missing: $name")).path
    }
    fun command(project: DesktopProject, output: String): FfmpegCommand {
        val request=project.request()
        return CommandBuilder(fonts).build(request,MediaPaths.aliasesFor(request).copy(inputs=project.sources.map { it.path },output=output,imageOverlays=project.pictures.filter {it.activeOn(project.timeline)}.map {it.source.path},sounds=project.sounds.filter {!it.muted}.map {it.source.path}))
    }
    fun transcript(project:DesktopProject,format:OutputFormat):String {
        val request=project.request()
        val words=request.timedWords()
        require(words.isNotEmpty()) { "Listen to the file first. No words remain on this edited timeline." }
        return if(format==OutputFormat.SRT) SrtSubtitles.write(SubtitleLayout.ofWords(words,project.subtitleStyle ?: SubtitleStyle(),request.subtitleFrameRatio),project.effects.effectiveSpeed)
        else TranscriptText.write(words,project.effects.effectiveSpeed)
    }
    suspend fun export(project: DesktopProject, output: File): DesktopSource {
        require((project.sources+project.sounds.map {it.source}+project.pictures.map {it.source}).none { File(it.path).canonicalFile == output.canonicalFile }) { "Choose a new output. Source files are never overwritten." }
        require(project.image.key?.mask?.let {File(it).canonicalFile!=output.canonicalFile} ?: true) {"Cannot replace a subject mask with output."}
        require(project.format != OutputFormat.CUSTOM) { "This output needs an adapter that is still being ported." }
        val temporary = File(output.parentFile, ".${output.nameWithoutExtension}-${UUID.randomUUID()}.${project.format.extension}")
        val auxiliaries=Files.createTempDirectory("ffmpeggui-export-").toFile()
        try {
            if(project.format==OutputFormat.WEBP) {
                val png=File(auxiliaries,"webp-source.png")
                export(project.copy(format=OutputFormat.PNG),png)
                media.process.checked(listOf("ffmpeg","-nostdin","-v","error","-y","-i",png.path,"-frames:v","1","-c:v","libwebp","-quality",(project.image.output.quality.coerceIn(0f,1f)*100).toInt().toString(),temporary.path))
                val result=media.probe(temporary.path)
                require(result.info.isStill && result.info.width>0)
                Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING)
                return result.copy(path=output.canonicalPath)
            }
            if(project.format==OutputFormat.SVG || project.format.isTextArt) {
                val png=File(auxiliaries,"art-source.png")
                val image=if(project.format.isTextArt)project.image.forTextArt() else project.image
                export(project.copy(format=OutputFormat.PNG,image=image),png)
                DesktopArt.write(project,png,temporary)
                require(temporary.length()>0) { "Art export was empty." }
                Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING)
                return DesktopSource(output.canonicalPath,project.sources[0].info)
            }
            if(project.format.isTranscript) {
                temporary.writeText(transcript(project,project.format))
                Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING)
                return DesktopSource(output.canonicalPath,project.sources[0].info)
            }
            var request=project.request()
            val drawing=if(project.format.isImage && project.image.strokes.isNotEmpty()) {
                val source=project.sources.first().info
                val (w,h)=com.cuscus.ffmpeggui.core.image.editedSize(project.image,project.effects,source.width,source.height)
                val scale=minOf(1f,4096f/maxOf(w,h))
                File(auxiliaries,"drawing.png").also { file ->
                    val bitmap=DesktopDrawing.render(project.image.strokes,(w*scale).toInt().coerceAtLeast(1),(h*scale).toInt().coerceAtLeast(1))
                    try {check(ImageIO.write(bitmap,"png",file))} finally {bitmap.flush()}
                }
            } else null
            if(drawing!=null)request=request.copy(imageOverlays=request.imageOverlays+ImagePlacement(x=0f,y=0f,widthFraction=1f))
            val words=request.timedWords()
            val style=project.subtitleStyle
            val subtitles=if(request.hasSpokenSubtitles && words.isNotEmpty() && style!=null) {
                val fontFile=File(fonts.fontPath(style.bold))
                val facts=FontFacts.read(fontFile)
                File(auxiliaries,"subtitles.ass").apply {
                    writeText(AssSubtitles.write(SubtitleLayout.ofWords(words,style,request.subtitleFrameRatio,facts),style,request.subtitleFrameRatio,facts?.family ?: "DejaVu Sans"))
                }
            } else null
            val paths=MediaPaths(project.sources.map { it.path },temporary.path,imageOverlays=project.pictures.filter {it.activeOn(project.timeline)}.map {it.source.path}+listOfNotNull(drawing?.path),sounds=project.sounds.filter {!it.muted}.map {it.source.path},subtitles=subtitles?.path,subtitleFonts=subtitles?.let { File(fonts.fontPath(style?.bold ?: false)).parent })
            media.process.checked(listOf("ffmpeg", "-nostdin") + CommandBuilder(fonts).build(request,paths).arguments)
            val result = media.probe(temporary.path)
            require(result.info.isStill || result.info.durationMs > 0) { "Export verification failed." }
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return result.copy(path = output.canonicalPath)
        } finally { temporary.delete();auxiliaries.deleteRecursively() }
    }
}

fun DesktopPicture.activeOn(timeline:Timeline)=!hidden && (placement.clipId==null || timeline.clips.any {it.id==placement.clipId})
