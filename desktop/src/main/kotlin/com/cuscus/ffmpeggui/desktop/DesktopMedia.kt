package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.command.*
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
        return DesktopSource(file.path, MediaInfo.of(duration, video != null, w, h, file.length(), audio, still, frameRate = rate))
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
    val followCrop: FollowCropTrack? = null, val subtitleStyle: SubtitleStyle? = null) {
    fun copy(timeline: Timeline = this.timeline, format: OutputFormat = this.format,
        effects: Effects = this.effects, encoding: EncodingSettings = this.encoding,
        words: List<SpokenWord> = this.words, followCrop: FollowCropTrack? = this.followCrop,
        subtitleStyle: SubtitleStyle? = this.subtitleStyle) = DesktopProject(sources, timeline, format, effects, encoding, words, followCrop, subtitleStyle)
    fun request(): ConversionRequest = ConversionRequest(format, File(sources.first().path).name,
        sources = sources.map { s -> s.info.let { SourceInfo(it.durationMs, it.width, it.height, it.hasAudio, it.isStill, it.hdr, it.frameRate, it.hasAlpha) } },
        segments = timeline.clips.map { c -> Segment(sources.indexOfFirst { it.path == c.sourceId }, c.startMs, c.endMs, c.effects, c.id) },
        effects = effects, encoding = encoding, followCrop = followCrop, subtitleStyle = subtitleStyle,
        spokenWords = if (words.isNotEmpty()) listOf(SourceWords(0, words)) else emptyList())
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
        return CommandBuilder(fonts).build(request,MediaPaths.aliasesFor(request).copy(inputs=project.sources.map { it.path },output=output))
    }
    fun transcript(project:DesktopProject,format:OutputFormat):String {
        val request=project.request()
        val words=request.timedWords()
        require(words.isNotEmpty()) { "Listen to the file first. No words remain on this edited timeline." }
        return if(format==OutputFormat.SRT) SrtSubtitles.write(SubtitleLayout.ofWords(words,project.subtitleStyle ?: SubtitleStyle(),request.subtitleFrameRatio),project.effects.effectiveSpeed)
        else TranscriptText.write(words,project.effects.effectiveSpeed)
    }
    suspend fun export(project: DesktopProject, output: File): DesktopSource {
        require(project.sources.none { File(it.path).canonicalFile == output.canonicalFile }) { "Choose a new output. Source files are never overwritten." }
        require(!project.format.needsPlatformEncoder && project.format != OutputFormat.CUSTOM) { "This output needs an adapter that is still being ported." }
        val temporary = File(output.parentFile, ".${output.nameWithoutExtension}-${UUID.randomUUID()}.${project.format.extension}")
        val auxiliaries=Files.createTempDirectory("ffmpeggui-export-").toFile()
        try {
            if(project.format.isTranscript) {
                temporary.writeText(transcript(project,project.format))
                Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING)
                return DesktopSource(output.canonicalPath,project.sources[0].info)
            }
            val request=project.request()
            val words=request.timedWords()
            val style=project.subtitleStyle
            val subtitles=if(request.hasSpokenSubtitles && words.isNotEmpty() && style!=null) {
                val fontFile=File(fonts.fontPath(style.bold))
                val facts=FontFacts.read(fontFile)
                File(auxiliaries,"subtitles.ass").apply {
                    writeText(AssSubtitles.write(SubtitleLayout.ofWords(words,style,request.subtitleFrameRatio,facts),style,request.subtitleFrameRatio,facts?.family ?: "DejaVu Sans"))
                }
            } else null
            val paths=MediaPaths(project.sources.map { it.path },temporary.path,subtitles=subtitles?.path,subtitleFonts=subtitles?.let { File(fonts.fontPath(style?.bold ?: false)).parent })
            media.process.checked(listOf("ffmpeg", "-nostdin") + CommandBuilder(fonts).build(request,paths).arguments)
            val result = media.probe(temporary.path)
            require(result.info.isStill || result.info.durationMs > 0) { "Export verification failed." }
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return result.copy(path = output.canonicalPath)
        } finally { temporary.delete();auxiliaries.deleteRecursively() }
    }
}
