package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.command.*
import com.cuscus.ffmpeggui.core.command.studio.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Local single-output studio. Never runs a shell or allows extra file destinations. */
class DesktopStudio(private val media:DesktopMedia) {
    suspend fun catalog():FfmpegCatalog {
        suspend fun read(vararg args:String)=media.process.checked(listOf("ffmpeg","-hide_banner")+args)
        return FfmpegHelpParser.catalog(read("-h","full"),read("-filters"),read("-encoders"),read("-muxers"),read("-pix_fmts"))
    }
    fun paths(p:DesktopProject)=MediaPaths.aliasesFor(p.request()).copy(subtitles=null,subtitleFonts=null,concatList=null)
    fun aliases(p:DesktopProject):Map<String,String> {
        val a=paths(p)
        return a.inputAliasesTo(a.copy(inputs=p.sources.map {it.path},sounds=p.sounds.filter {!it.muted}.map {it.source.path},imageOverlays=p.pictures.filter {it.activeOn(p.timeline)}.map {it.source.path}))
    }
    fun validate(p:DesktopProject,text:String):List<String> {
        val catalog=FfmpegCatalog.BUILT_IN
        val reading=CommandReading(text,catalog)
        require(reading.spans.none {it.openQuote!=null}){"Close all command quotes."}
        require(reading.spans.firstOrNull()?.value=="ffmpeg"){"Start with ffmpeg."}
        val files=aliases(p).keys
        var outputs=0
        reading.spans.forEachIndexed {i,span->
            when(val role=reading.roles[i]) {
                ArgRole.Program->Unit
                ArgRole.Option->require(span.value.substringBefore(':') in allowed){"Execution of ${span.value} is not ported. Edit/lint is available; local single-output commands only."}
                is ArgRole.Value->{
                    val option=reading.spans[role.option].value.substringBefore(':')
                    if(option=="-i")require(span.value in files){"Inputs must use the listed local aliases."}
                    if(option in listOf("-vf","-af","-filter_complex","-filter","-lavfi")) {
                        require('\\' !in span.value){"Escaped filter graphs are not supported by this local adapter."}
                        val names=Regex("(?:^|[;,])(?:\\[[^]]+\\])*([A-Za-z][A-Za-z0-9_]*)(?==|[;,]|$)").findAll(span.value).map {it.groupValues[1]}.toList()
                        require(names.isNotEmpty() && names.all {it in safeFilters}){"This filter graph needs an adapter that is not yet ported."}

                        require(!span.value.contains(Regex("(?i)(movie|amovie|subtitles|ass|sendcmd|zmq|concat|filename|file=|textfile|lut3d)"))){"This filter needs an external file adapter that is not ported."}
                    }
                    require(!span.value.contains(Regex("(?i)(https?|tcp|udp|rtmp|srt|ftp):"))){"Network commands are not supported."}
                }
                ArgRole.Bare->{require(span.value.matches(Regex("output\\.[A-Za-z0-9]+"))){"Use one output.ext alias, not a path or numbered output."};require(span.value.substringAfterLast('.').lowercase() in setOf("mp4","mkv","webm","mov","mp3","wav","flac","ogg","opus","png","jpg","jpeg","gif","webp")){"This output may create extra files and is not supported."};outputs++}
            }
        }
        require(outputs==1){"Use exactly one output.ext alias."}
        require(reading.inputCount>0){"Use at least one local input alias."}
        return reading.spans.map {it.value}
    }
    suspend fun export(p:DesktopProject,text:String,target:File):DesktopSource {
        validate(p,text)
        require(p.image.key?.mask?.let {File(it).canonicalFile!=target.canonicalFile} ?: true){"Subject masks cannot be overwritten."}
        require((p.sources+p.sounds.map {it.source}+p.pictures.map {it.source}).none {File(it.path).canonicalFile==target.canonicalFile}){"Source files cannot be overwritten."}
        val output=tokenizeCommandLine(text).lastOrNull {it.matches(Regex("output\\.[A-Za-z0-9]+"))}!!
        val ext=output.substringAfterLast('.')
        require(target.extension.equals(ext,true)){"Choose an output ending in .$ext, matching the command."}
        val temporary=File(target.parentFile,".studio-${UUID.randomUUID()}.$ext")
        val upright=Files.createTempDirectory("ffmpeggui-studio-inputs-").toFile()
        try {
            val inputs=aliases(p).mapValues {(_,path)->DesktopStillInputs.resolve(path,upright)}
            val resolved=ManualCommandResolver().resolve(text,inputs,temporary.path.removeSuffix(".$ext")) as? ManualCommandResolver.Result.Resolved ?: error("Missing output alias")
            val args=resolved.command.arguments.toMutableList()
            val outputIndex=args.indexOf(resolved.outputPath)
            val durations=args.indices.filter {args[it]=="-t"}.mapNotNull {i->args.getOrNull(i+1)?.split(':')?.fold(0.0){acc,part->acc*60+(part.toDoubleOrNull() ?: error("Use a numeric time limit."))}}
            args.addAll(outputIndex,listOf("-t",minOf(1800.0,durations.minOrNull() ?: 1800.0).toString(),"-fs","1073741824"))
            media.process.checked(listOf("ffmpeg","-nostdin","-y","-protocol_whitelist","file,pipe")+args)
            val result=media.probe(temporary.path)
            require(result.info.isStill || result.info.durationMs>0){"Command did not produce usable media."}
            Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
            return result.copy(path=target.canonicalPath)
        } finally {temporary.delete();upright.deleteRecursively()}
    }
    companion object {
        private val safeFilters=setOf("scale","crop","transpose","hflip","vflip","fps","format","setsar","setpts","asetpts","atempo","reverse","areverse","fade","afade","volume","loudnorm","split","asplit","palettegen","paletteuse","overlay","amix","showwavespic","eq","trim","atrim","null","anull","pad","aformat","aresample")
        private val allowed=setOf("-i","-y","-hide_banner","-c","-codec","-vcodec","-acodec","-vf","-af","-filter","-filter_complex","-lavfi","-map","-ss","-to","-t","-b","-crf","-preset","-r","-s","-pix_fmt","-ar","-ac","-an","-vn","-sn","-frames","-q","-movflags","-shortest","-loop","-threads","-tag","-profile","-tune","-aspect","-sseof","-itsoffset","-copyts","-stream_loop")
    }
}
