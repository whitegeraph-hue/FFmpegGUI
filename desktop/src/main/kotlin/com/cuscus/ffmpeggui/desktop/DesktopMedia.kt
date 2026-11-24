package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.audio.AudioExecutionSafety

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

internal fun desktopProcessFailure(code:Int,log:String):String {
    val killed=if(code==137 || code==-9) " The process was killed. Memory pressure or an external stop may be responsible; this does not prove the cause. Try fewer high-resolution clips at once or make smaller source copies. Lowering only export resolution may not reduce source-decoder memory." else ""
    return "Process failed (exit $code).$killed\n"+log.takeLast(6000)
}

/** Direct ProcessBuilder adapter. Never sends file names or commands through a shell. */
class DesktopProcess {
    suspend fun run(arguments: List<String>, onLog: (String) -> Unit = {}): Pair<Int, String> = withContext(Dispatchers.IO) {
        require(arguments.isNotEmpty())
        if (File(arguments.first()).name == "ffmpeg") {
            AudioExecutionSafety.commandIssue(FfmpegCommand(arguments.drop(1)))?.let { issue ->
                onLog(issue)
                return@withContext AudioExecutionSafety.RETURN_CODE to issue
            }
        }
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
        check(code == 0) { desktopProcessFailure(code,log) }
        return log
    }
}

class DesktopFfmpegExecutor(private val process: DesktopProcess = DesktopProcess()) : FfmpegExecutor {
    override suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult = try {
        val (code, _) = process.run(listOf("ffmpeg", "-nostdin") + command.arguments, onLog)
        if (code == 0) ExecutionResult.Success else ExecutionResult.Failed(code)
    } catch (e: CancellationException) { throw e }
}

data class DesktopSource(val path: String, val info: MediaInfo,val aiPicture:Boolean=false,val aiSpeech:Boolean=false)
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
        val measuredDuration = ((data["format"]?.jsonObject?.get("duration")?.jsonPrimitive?.doubleOrNull ?: video?.get("duration")?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong()
        val demuxer=data["format"]?.jsonObject?.get("format_name")?.jsonPrimitive?.content.orEmpty()
        val still = video!=null && (measuredDuration==0L || demuxer in setOf("jpeg_pipe","png_pipe","bmp_pipe","tiff_pipe","webp_pipe","image2"))
        val duration=if(still)0L else measuredDuration
        var w = video?.get("width")?.jsonPrimitive?.intOrNull ?: 0
        var h = video?.get("height")?.jsonPrimitive?.intOrNull ?: 0
        if(still && DesktopStillInputs.isUltraHdr(file)) {
            ImageIO.createImageInputStream(file).use {input->val reader=ImageIO.getImageReaders(input).next();try {reader.input=input;w=reader.getWidth(0);h=reader.getHeight(0)}finally {reader.dispose()}}
        }
        val rotation = video?.get("side_data_list")?.jsonArray?.mapNotNull { it.jsonObject["rotation"]?.jsonPrimitive?.intOrNull }?.firstOrNull() ?: 0
        if (kotlin.math.abs(rotation) % 180 == 90) { val swap = w; w = h; h = swap }
        if(still && DesktopStillInputs.orientation(file)>=5 && kotlin.math.abs(rotation)%180!=90){val swap=w;w=h;h=swap}
        val rate = video?.get("avg_frame_rate")?.jsonPrimitive?.content?.split('/')?.let { if (it.size == 2 && it[1].toFloatOrNull() != 0f) (it[0].toFloatOrNull() ?: 0f) / (it[1].toFloatOrNull() ?: 1f) else 0f } ?: 0f
        return DesktopSource(file.path, MediaInfo.of(duration, video != null, w, h, file.length(), audio, still, frameRate = rate,hasAlpha=video?.get("pix_fmt")?.jsonPrimitive?.content?.let {it.startsWith("rgba") || it.startsWith("bgra") || it.startsWith("argb") || it.startsWith("abgr") || it.startsWith("yuva") || it.startsWith("gbrap") || it.startsWith("ya")} ?: false),aiPicture=video!=null && DesktopProvenance.aiPicture(file),aiSpeech=audio && DesktopProvenance.aiSpeech(file))
    }
    suspend fun frame(path: String, atMs: Long, filters: String? = null): BufferedImage? {
        val target = File.createTempFile("ffmpeggui-frame-", ".png")
        val directory=Files.createTempDirectory("ffmpeggui-upright-").toFile()
        return try {
            val input=DesktopStillInputs.resolve(path,directory)
            val args = mutableListOf("ffmpeg", "-y", "-v", "error", "-ss", (atMs/1000.0).toString(), "-i", input, "-frames:v", "1")
            args += listOf("-vf", listOfNotNull(filters, "scale=960:640:force_original_aspect_ratio=decrease").joinToString(","), target.path)
            process.checked(args)
            ImageIO.read(target)
        } finally { target.delete();directory.deleteRecursively() }
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
data class DesktopLook(val span:LookSpan,val clipId:String?=null)

class DesktopProject(val sources: List<DesktopSource>, val timeline: Timeline,
    val format: OutputFormat = OutputFormat.MP4, val effects: Effects = Effects(),
    val encoding: EncodingSettings = EncodingSettings(), val words: List<SpokenWord> = emptyList(),
    val followCrop: FollowCropTrack? = null, val subtitleStyle: SubtitleStyle? = null, val audioEffects:AudioEffects=AudioEffects.NONE,val image:ImageEdit=ImageEdit(),val captions:List<TextOverlay> = emptyList(),val sounds:List<DesktopSound> = emptyList(),val tags:MetadataTags=MetadataTags(),val canvas:CanvasRatio=CanvasRatio.FIT,val canvasFit:CanvasFit=CanvasFit.FIT,val normalize:Boolean=false,val pictures:List<DesktopPicture> = emptyList(),val wordsBySource:Map<String,List<SpokenWord>> = emptyMap(),val wordsByClip:Map<String,List<SpokenWord>> = emptyMap(),val photoPlacement:ImagePlacement?=null,val externalSubtitles:DesktopSubtitles?=null,val stabilization:Stabilization?=null,val coverPng:String?=null,val compression:Compression?=null,val looks:List<DesktopLook> = emptyList()) {
    fun copy(sources:List<DesktopSource> = this.sources,timeline: Timeline = this.timeline, format: OutputFormat = this.format,
        effects: Effects = this.effects, encoding: EncodingSettings = this.encoding,
        words: List<SpokenWord> = this.words, followCrop: FollowCropTrack? = this.followCrop,
        subtitleStyle: SubtitleStyle? = this.subtitleStyle,audioEffects:AudioEffects=this.audioEffects,image:ImageEdit=this.image,captions:List<TextOverlay> = this.captions,sounds:List<DesktopSound> = this.sounds,tags:MetadataTags=this.tags,canvas:CanvasRatio=this.canvas,canvasFit:CanvasFit=this.canvasFit,normalize:Boolean=this.normalize,pictures:List<DesktopPicture> = this.pictures,wordsBySource:Map<String,List<SpokenWord>> = this.wordsBySource,wordsByClip:Map<String,List<SpokenWord>> = this.wordsByClip,photoPlacement:ImagePlacement?=this.photoPlacement,externalSubtitles:DesktopSubtitles?=this.externalSubtitles,stabilization:Stabilization?=this.stabilization,coverPng:String?=this.coverPng,compression:Compression?=this.compression,looks:List<DesktopLook> = this.looks) = DesktopProject(sources, timeline, format, effects, encoding, words, followCrop, subtitleStyle,audioEffects,image,captions,sounds,tags,canvas,canvasFit,normalize,pictures,wordsBySource,wordsByClip,photoPlacement,externalSubtitles,stabilization,coverPng,compression,looks)
    fun requestSegments()=timeline.clips.map { c -> Segment(sources.indexOfFirst {it.path==c.sourceId},c.startMs,c.endMs,c.effects,c.id) }
    fun request(): ConversionRequest = ConversionRequest(format, File(sources.first().path).name,
        sources = sources.map { s -> s.info.let { SourceInfo(it.durationMs, it.width, it.height, it.hasAudio, it.isStill, it.hdr, it.frameRate, it.hasAlpha) } },
        segments = timeline.clips.map { c -> Segment(sources.indexOfFirst { it.path == c.sourceId }, c.startMs, c.endMs, c.effects, c.id) },
        photoPlacement=photoPlacement,effects = effects, encoding = encoding, audioEffects=audioEffects,image=image,metadata=tags,canvas=canvas,canvasFit=canvasFit,normalizeAudio=normalize,imageOverlays=pictures.filter {it.activeOn(timeline)}.map {it.placement},sounds=sounds.filter {!it.muted}.map {it.placed.copy(aiVoice=if(it.source.aiSpeech)"AI-generated voice" else it.placed.aiVoice)},textOverlays=captions.filter { !it.hidden && (it.clipId==null || timeline.clips.any {c->c.id==it.clipId}) }, followCrop = followCrop, subtitleStyle = subtitleStyle,
        looks=resolvedLooks(),aiEditedPictures=DesktopProvenance.needed(this),compression=compression,cover=coverPng!=null,stabilization=stabilization,subtitleSource=externalSubtitles?.takeIf {it.enabled}?.sourceIndex,
        placedWords = if(externalSubtitles?.enabled==true)emptyList() else DesktopSpeech.timedWords(this))
    fun resolvedLooks():List<LookSpan> {
        val clock=clock()
        return looks.mapNotNull { item ->
            val span=item.span
            if(item.clipId==null) span.takeIf {it.startMs<clock.outputDurationMs}?.copy(endMs=span.endMs.coerceAtMost(clock.outputDurationMs))
            else {
                val index=timeline.clips.indexOfFirst {it.id==item.clipId}
                if(index<0)null else {
                    val begin=clock.joinedStartMs(index)
                    val finish=begin+timeline.clips[index].effects.playedMs(timeline.clips[index].lengthMs)
                    val start=((if(effects.reversed)clock.joinedDurationMs-finish else begin)/clock.speed).toLong().coerceAtLeast(0)
                    val end=((if(effects.reversed)clock.joinedDurationMs-begin else finish)/clock.speed).toLong().coerceAtMost(clock.outputDurationMs)
                    val from=(start+span.startMs).coerceAtMost(end);val to=(start+span.endMs).coerceAtMost(end)
                    if(to>from)span.copy(startMs=from,endMs=to)else null
                }
            }
        }
    }
    /** Preserve bound effect coverage when one occurrence becomes several non-overlapping split children. */
    fun rebindSplitLooks(previous:DesktopProject):DesktopProject {
        if(timeline===previous.timeline || previous.looks.isEmpty())return this
        val oldIds=previous.timeline.clips.map{it.id}.toSet()
        var rebound=looks
        previous.timeline.clips.forEach {old->
            val children=timeline.clips.filter {it.sourceId==old.sourceId && it.startMs>=old.startMs && (it.endMs ?: Long.MAX_VALUE)<=(old.endMs ?: Long.MAX_VALUE) && (it.id==old.id || it.id !in oldIds)}
            if(children.size<2 || children.sumOf{it.lengthMs}!=old.lengthMs)return@forEach
            val bound=looks.filter{it.clipId==old.id}
            if(bound.isEmpty())return@forEach
            val speed=previous.clock().speed
            rebound=rebound.filter{it.clipId!=old.id}+bound.flatMap {item->children.mapNotNull {child->
                val consumed=if(old.effects.reversed)(old.endMs ?: old.startMs+old.lengthMs)-(child.endMs ?: child.startMs+child.lengthMs) else child.startMs-old.startMs
                val begin=(old.effects.playedOffsetMs(old.lengthMs.toDouble(),consumed.toDouble())/speed).toLong()
                val end=(old.effects.playedOffsetMs(old.lengthMs.toDouble(),(consumed+child.lengthMs).toDouble())/speed).toLong()
                val a=maxOf(item.span.startMs,begin);val b=minOf(item.span.endMs,end)
                if(b>a)item.copy(span=item.span.copy(startMs=a-begin,endMs=b-begin),clipId=child.id)else null
            }}
        }
        return copy(looks=rebound)
    }
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
    val fonts = object : FontProvider {
      override fun customFontPath(file:String):String? = File(file).takeIf {it.isFile && it.canRead()}?.path
      override fun fontPath(bold:Boolean):String {
        val name = if (bold) "DejaVuSans-Bold.ttf" else "DejaVuSans.ttf"
        val candidates = listOf(File(System.getProperty("compose.application.resources.dir", "resources"), "fonts/$name"), File("/usr/share/fonts/truetype/dejavu/$name"), File("/usr/share/fonts/TTF/$name"))
        return (candidates.firstOrNull { it.isFile } ?: error("Required font is missing: $name")).path
      }
    }
    fun command(project: DesktopProject, output: String): FfmpegCommand {
        val request=project.request()
        return CommandBuilder(fonts).build(request,MediaPaths.aliasesFor(request).copy(inputs=project.sources.map { it.path },output=output,imageOverlays=project.pictures.filter {it.activeOn(project.timeline)}.map {it.source.path},sounds=project.sounds.filter {!it.muted}.map {it.source.path}))
    }
    fun transcript(project:DesktopProject,format:OutputFormat):String {
        val request=project.copy(externalSubtitles=null).request()
        val words=request.timedWords()
        require(words.isNotEmpty()) { "Listen to the file first. No words remain on this edited timeline." }
        return if(format==OutputFormat.SRT) SrtSubtitles.write(SubtitleLayout.ofWords(words,project.subtitleStyle ?: SubtitleStyle(),request.subtitleFrameRatio),project.effects.effectiveSpeed)
        else TranscriptText.write(words,project.effects.effectiveSpeed)
    }
    /** Uses the full edited graph and output clock, without encoding the rest of the movie.
     * This is a requested still inspection, not real-time playback or a DSP preview. */
    internal suspend fun editedFrame(project:DesktopProject,atMs:Long):BufferedImage {
        require(project.sources.first().info.isVideo && !project.sources.first().info.isStill) { "Use the edited picture preview for a still." }
        val at=atMs.coerceIn(0,(project.outputDurationMs-40).coerceAtLeast(0))
        val preview=project.copy(format=OutputFormat.MP4,compression=null,coverPng=null,
            encoding=project.encoding.copy(streamCopy=false))
        val file=File.createTempFile("ffmpeggui-edited-frame-",".mp4").apply {delete()}
        try {
            exportInternal(preview,file,{},at)
            return media.frame(file.path,0) ?: error("Edited frame was empty.")
        } finally {file.delete()}
    }
    suspend fun export(project:DesktopProject,output:File,onPhase:(String)->Unit={}):DesktopSource =
        exportInternal(project,output,onPhase,null)
    private suspend fun exportInternal(project: DesktopProject, output: File,onPhase:(String)->Unit,frameAtMs:Long?): DesktopSource {
        require((project.sources+project.sounds.map {it.source}+project.pictures.map {it.source}).none { File(it.path).canonicalFile == output.canonicalFile }) { "Choose a new output. Source files are never overwritten." }
        require(listOfNotNull(project.image.hdr?.mask,project.image.hdr?.brush).none {File(it).canonicalFile==output.canonicalFile}) {"Cannot replace an HDR mask with output."}
        require(project.image.key?.mask?.let {File(it).canonicalFile!=output.canonicalFile} ?: true) {"Cannot replace a subject mask with output."}
        require(project.compression==null || project.format in listOf(OutputFormat.MP4,OutputFormat.MKV,OutputFormat.JPG,OutputFormat.PNG,OutputFormat.MP3,OutputFormat.M4A,OutputFormat.OGG,OutputFormat.OPUS)) {"Compression is not supported for this output format. Turn it off or choose another format."}
        require(project.format != OutputFormat.CUSTOM) { "This output needs an adapter that is still being ported." }
        if (frameAtMs == null && project.format.hasAudio) AudioExecutionSafety.requestIssue(project.request())?.let { throw AudioExecutionSafety.Blocked(it) }
        val temporary = File(output.parentFile, ".${output.nameWithoutExtension}-${UUID.randomUUID()}.${project.format.extension}")
        val auxiliaries=Files.createTempDirectory("ffmpeggui-export-").toFile()
        try {
            if(project.format==OutputFormat.WEBP) {
                val png=File(auxiliaries,"webp-source.png")
                export(project.copy(format=OutputFormat.PNG),png)
                media.process.checked(listOf("ffmpeg","-nostdin","-v","error","-y","-i",png.path,"-frames:v","1","-c:v","libwebp","-quality",(project.image.output.quality.coerceIn(0f,1f)*100).toInt().toString(),temporary.path))
                val result=media.probe(temporary.path)
                require(result.info.isStill && result.info.width>0)
                DesktopProvenance.seal(project,temporary)
                Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING)
                return result.copy(path=output.canonicalPath,aiPicture=DesktopProvenance.needed(project),aiSpeech=DesktopProvenance.speechNeeded(project))
            }
            if(project.format==OutputFormat.SVG || project.format.isTextArt) {
                val png=File(auxiliaries,"art-source.png")
                val image=if(project.format.isTextArt)project.image.forTextArt() else project.image
                export(project.copy(format=OutputFormat.PNG,image=image),png)
                DesktopArt.write(project,png,temporary)
                require(temporary.length()>0) { "Art export was empty." }
                DesktopProvenance.seal(project,temporary)
                Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING)
                return DesktopSource(output.canonicalPath,project.sources[0].info)
            }
            if(project.format.isTranscript) {
                temporary.writeText(transcript(project,project.format))
                DesktopProvenance.seal(project,temporary)
                Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING)
                return DesktopSource(output.canonicalPath,project.sources[0].info)
            }
            val compressed=project.compression?.let {c->
                require(project.format in listOf(OutputFormat.MP4,OutputFormat.MKV,OutputFormat.JPG,OutputFormat.PNG,OutputFormat.MP3,OutputFormat.M4A,OutputFormat.OGG,OutputFormat.OPUS)) {"Compression is not supported for this output format. Turn it off or choose another format."}
                require(project.image.hdr==null) {"Turn HDR off before compression"}
                require(!project.encoding.streamCopy) {"Compression needs encoding, not stream copy"}
                require(!c.hasTarget || project.format!=OutputFormat.PNG) {"PNG size fitting is not supported. Turn off the target size."}
                if(project.format.isLossyAudio) project.copy(encoding=project.encoding.copy(audio=project.encoding.audio.copy(bitrateKbps=Compression.audioKbps(project.format,c,project.outputDurationMs))))
                else if(project.format==OutputFormat.JPG) project.copy(image=project.image.copy(output=project.image.output.copy(quality=if(c.hasTarget).95f else c.level.jpegQuality)))
                else project
            } ?: project
            var request=compressed.request()
            val drawing=if(project.format.isImage && project.image.strokes.isNotEmpty()) {
                val source=project.sources.first().info
                val (w,h)=project.image.canvas?.sane()?.let {it.width to it.height} ?: com.cuscus.ffmpeggui.core.image.editedSize(project.image,project.effects,source.width,source.height)
                val scale=minOf(1f,4096f/maxOf(w,h))
                File(auxiliaries,"drawing.png").also { file ->
                    val bitmap=DesktopDrawing.render(project.image.strokes,(w*scale).toInt().coerceAtLeast(1),(h*scale).toInt().coerceAtLeast(1))
                    try {check(ImageIO.write(bitmap,"png",file))} finally {bitmap.flush()}
                }
            } else null
            if(drawing!=null)request=request.copy(imageOverlays=request.imageOverlays+ImagePlacement(x=0f,y=0f,widthFraction=1f))
            val words=request.timedWords()
            val style=project.subtitleStyle
            var subtitleFontDirectory:File?=null
            val external=project.externalSubtitles?.takeIf {it.enabled}
            val styledCues=external?.takeIf {style!=null && !com.cuscus.ffmpeggui.core.preview.SubtitleRetimer.isAss(it.content)}?.let {DesktopSubtitles.cuesOnTimeline(it,request)}
            val subtitles=if(style!=null && ((request.hasSpokenSubtitles && words.isNotEmpty()) || styledCues!=null)) {
                val selected=style.fontFile?.let {file->File(file).takeIf {it.isFile && it.canRead()}}
                val original=selected ?: File(fonts.fontPath(style.bold))
                if(selected!=null)DesktopFonts.validate(selected)
                subtitleFontDirectory=File(auxiliaries,"subtitle-fonts").apply {mkdirs()}
                val fontFile=File(subtitleFontDirectory,"subtitle."+original.extension.lowercase()).also {original.copyTo(it)}
                val facts=FontFacts.read(fontFile)
                File(auxiliaries,"subtitles.ass").apply {
                    writeText(AssSubtitles.write(if(styledCues!=null)SubtitleLayout.ofCues(styledCues,style,request.subtitleFrameRatio,facts) else SubtitleLayout.ofWords(words,style,request.subtitleFrameRatio,facts),style,request.subtitleFrameRatio,DesktopFonts.assFamily(facts?.family ?: "DejaVu Sans")))
                }
            } else if(external!=null) {
                com.cuscus.ffmpeggui.core.preview.SubtitleRetimer().retime(external.content,request.segments,external.sourceIndex,request.effects.reversed)?.let {retimed->File(auxiliaries,"external."+retimed.extension).apply {writeText(retimed.content)}}
            } else null
            require(project.image.hdr==null || project.format==OutputFormat.JPG) {"HDR is enabled but this output is not JPG. Choose JPG or turn HDR off."}
            val hdr=project.image.hdr
            require(hdr==null || (hdr.hasArea && project.image.canvas==null)) {"This HDR mode needs an adapter still being ported."}
            if(hdr?.mode==HdrMode.ORIGINAL)DesktopHdr.original(File(project.sources.single().path))
            val hdrPictures=hdr?.let {HdrPictures(File(auxiliaries,"hdr-final.png").path,File(auxiliaries,"hdr-plain.png").path)}
            val cover=project.coverPng?.let {encoded->
                require(project.format.coverWriting!=null) {"This format cannot hold album art. Choose MP3, M4A, FLAC, OGG or OPUS, or remove the cover."}
                File(auxiliaries,"cover.png").apply {writeBytes(DesktopCover.decode(encoded))}
            }
            val coverTags=cover?.takeIf {project.format.coverWriting==CoverWriting.PICTURE_TAG}?.let {picture->File(auxiliaries,"cover-tags.txt").apply {writeText(CoverTags.ffmetadata(picture.readBytes()))}}
            val paths=MediaPaths(project.sources.map { DesktopStillInputs.resolve(it.path,auxiliaries) },temporary.path,cover=cover?.path,coverTags=coverTags?.path,imageOverlays=project.pictures.filter {it.activeOn(project.timeline)}.map {DesktopStillInputs.resolve(it.source.path,auxiliaries)}+listOfNotNull(drawing?.path),sounds=project.sounds.filter {!it.muted}.map {it.source.path},hdrPictures=hdrPictures,subtitles=subtitles?.path,subtitleFonts=subtitleFontDirectory?.path,stabilizationTransforms=project.stabilization?.let {File(auxiliaries,"transforms.trf").path},passLog=project.compression?.takeIf {it.hasTarget}?.let {File(auxiliaries,"x265-stats").path})
            val builder=CommandBuilder(fonts)
            if(builder.needsStabilizationAnalysis(request)) {
                val filters=media.process.checked(listOf("ffmpeg","-hide_banner","-filters"))
                require(filters.contains("vidstabdetect") && filters.contains("vidstabtransform")) {"Installed FFmpeg lacks vid.stab. Install an FFmpeg build with vidstabdetect and vidstabtransform, or turn stabilization off."}
                onPhase("Stabilization pass 1 of 2: analysing edited footage…")
                media.process.checked(listOf("ffmpeg","-nostdin")+builder.buildStabilizationAnalysis(request,paths).arguments)
                require(File(paths.stabilizationTransforms!!).length()>0){"Stabilization analysis produced no transforms"}
            }
            suspend fun encode(arguments:List<String>) {
                // Bound x265's native thread pools on high-core-count desktops and CI hosts.
                val args=arguments.toMutableList()
                if(args.contains("libx265")) {
                    val at=args.indexOf("-x265-params")
                    if(at>=0)args[at+1]+=":pools=2:frame-threads=2" else args.addAll(args.lastIndex,listOf("-x265-params","pools=2:frame-threads=2"))
                }
                media.process.checked(listOf("ffmpeg","-nostdin")+args)
            }
            if(builder.needsFirstPass(request)) {
                val encoders=media.process.checked(listOf("ffmpeg","-hide_banner","-encoders"))
                require(encoders.contains("libx265")) {"Installed FFmpeg lacks the libx265 encoder needed for compression"}
                onPhase("Compression pass 1 of 2: measuring edited video…")
                encode(builder.buildFirstPass(request,paths).arguments)
            }
            onPhase(if(builder.needsFirstPass(request))"Compression pass 2 of 2: writing output…" else if(request.stabilization!=null)"Stabilization pass 2 of 2: rendering smoothed output…" else "Rendering edited output…")
            try {
                val arguments=builder.build(request,paths).arguments
                val limited=if(frameAtMs==null)arguments else {
                    require(arguments.lastOrNull()==paths.output && paths.output==temporary.path) {"Edited-frame output path invariant failed."}
                    arguments.dropLast(1)+listOf("-ss",(frameAtMs/1000.0).toString(),"-frames:v","1","-t","0.080",paths.output)
                }
                encode(limited)
            } catch(e:IllegalStateException) {
                if(project.format==OutputFormat.OGG && e.message.orEmpty().contains("encoder setup failed"))
                    throw IllegalArgumentException("Vorbis rejected this sample-rate/channel/bitrate combination. Try 48000 Hz and 96 kbps in Encoding, or choose FLAC. No encoding settings were changed automatically.",e)
                throw e
            }
            val target=project.compression?.targetBytes?.takeIf {it>0}
            if(target!=null && project.format==OutputFormat.JPG && temporary.length()>target) {
                val best=File(auxiliaries,"best-fit.jpg")
                var low=0f;var high=request.image.output.quality
                repeat(6) {step->
                    currentCoroutineContext().ensureActive()
                    val quality=(low+high)/2f
                    onPhase("Fitting JPG size: attempt ${step+1} of 6…")
                    encode(builder.build(request.copy(image=request.image.copy(output=request.image.output.copy(quality=quality))),paths).arguments)
                    if(temporary.length()<=target){temporary.copyTo(best,true);low=quality}else high=quality
                }
                require(best.isFile) {"This target size is too small at the selected dimensions. Reduce output dimensions or increase the target. No output was replaced."}
                best.copyTo(temporary,true)
            }
            require(target==null || temporary.length()<=target) {"Encoded output exceeds the target size. Increase the target or reduce resolution. No output was replaced."}
            if(hdr!=null && hdrPictures!=null)DesktopHdr.assemble(temporary,File(hdrPictures.finalPng),File(hdrPictures.plainPng),hdr,project)
            val result = media.probe(temporary.path)
            require(result.info.isStill || result.info.durationMs > 0) { "Export verification failed." }
            DesktopProvenance.seal(project,temporary)
            require(target==null || temporary.length()<=target) {"AI disclosure makes output exceed the target size. Increase the target. No output was replaced."}
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return result.copy(path = output.canonicalPath,aiPicture=DesktopProvenance.needed(project),aiSpeech=DesktopProvenance.speechNeeded(project))
        } finally { temporary.delete();auxiliaries.deleteRecursively() }
    }
}

fun DesktopPicture.activeOn(timeline:Timeline)=!hidden && (placement.clipId==null || timeline.clips.any {it.id==placement.clipId})
