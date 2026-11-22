package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.project.Json as CoreJson
import com.cuscus.ffmpeggui.core.project.JsonText
import com.cuscus.ffmpeggui.core.project.ModelJson
import com.cuscus.ffmpeggui.core.subtitles.*
import com.cuscus.ffmpeggui.core.timeline.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Desktop envelope around the shared Android model JSON. Source files stay external. */
object DesktopProjects {
    private fun core(value: CoreJson): JsonElement = Json.parseToJsonElement(JsonText.write(value))
    private fun read(value: JsonElement): CoreJson.Obj = JsonText.parse(value.toString()) as? CoreJson.Obj ?: error("Expected model object")
    fun save(project: DesktopProject, target: File) {
        val data=buildJsonObject {
            put("type","ffmpeggui-linux-project"); put("version",1)
            put("sources",buildJsonArray { project.sources.forEach { add(buildJsonObject { put("path",it.path); put("size",File(it.path).length()); put("durationMs",it.info.durationMs) }) } })
            put("format",project.format.name)
            put("effects",core(ModelJson.write(project.effects)))
            put("audioEffects",core(ModelJson.write(project.audioEffects)))
            put("gif",buildJsonObject { val g=project.encoding.gif;put("width",g.width);put("fps",g.fps);put("colors",g.colors);put("dither",g.dither.name);put("loops",g.loops);put("smaller",g.smaller) })
            project.followCrop?.let { put("followCrop",core(ModelJson.writeFollowCrop(it))) }
            project.subtitleStyle?.let { put("subtitleStyle",core(ModelJson.write(it))) }
            put("resolution",project.encoding.resolution.name)
            put("quality",project.encoding.quality)
            put("framerate",project.encoding.framerate.name);put("cpuSpeed",project.encoding.cpuSpeed.name)
            put("keepHdr",project.encoding.keepHdr)
            put("audioBitrate",project.encoding.audio.bitrateKbps);put("audioSampleRate",project.encoding.audio.sampleRate);put("audioDepth",project.encoding.audio.bitDepth)
            put("timeline",core(ModelJson.write(project.timeline)))
            put("words",buildJsonArray { project.words.forEach { w -> add(buildJsonObject { put("startMs",w.startMs);put("endMs",w.endMs);put("text",w.text) }) } })
        }
        require(project.sources.none { File(it.path).canonicalFile==target.canonicalFile }) { "Cannot replace a source with a project." }
        val temporary=File(target.parentFile,".${target.name}-${UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(Json { prettyPrint=true }.encodeToString(JsonObject.serializer(),data))
            Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
    suspend fun load(file:File,media:DesktopMedia):DesktopProject {
        val data=Json.parseToJsonElement(file.readText()).jsonObject
        require(data["type"]?.jsonPrimitive?.content=="ffmpeggui-linux-project" && data["version"]?.jsonPrimitive?.int==1) { "Unsupported project file." }
        val sources=data["sources"]!!.jsonArray.map { item ->
            val value=item.jsonObject
            val source=media.probe(value["path"]!!.jsonPrimitive.content)
            require(File(source.path).length()==value["size"]!!.jsonPrimitive.long && source.info.durationMs==value["durationMs"]!!.jsonPrimitive.long) { "A source file changed. Open it as a new project instead." }
            source
        }
        require(sources.size==1) { "This preview accepts one source per project. Multi-source analysis is not ready yet." }
        val timeline=ModelJson.timeline(read(data["timeline"]!!))
        require(timeline.clips.isNotEmpty() && timeline.clips.all { c ->
            val s=sources.firstOrNull { it.path==c.sourceId }
            val end=c.endMs
            s!=null && c.startMs>=0 && end!=null && end>c.startMs && (s.info.isStill || end<=s.info.durationMs+50)
        }) { "Project timeline refers to missing or invalid footage." }
        val effects=ModelJson.effects(read(data["effects"]!!))
        val gif=data["gif"]?.jsonObject
        val gifSettings=gif?.let { GifSettings(it["width"]!!.jsonPrimitive.int,it["fps"]!!.jsonPrimitive.int,it["colors"]!!.jsonPrimitive.int,GifDither.valueOf(it["dither"]!!.jsonPrimitive.content),it["loops"]!!.jsonPrimitive.int,it["smaller"]!!.jsonPrimitive.boolean) } ?: GifSettings()
        val words=data["words"]!!.jsonArray.map { w -> w.jsonObject.let { SpokenWord(it["startMs"]!!.jsonPrimitive.long,it["endMs"]!!.jsonPrimitive.long,it["text"]!!.jsonPrimitive.content) } }
        return DesktopProject(sources,timeline,OutputFormat.valueOf(data["format"]!!.jsonPrimitive.content),effects=effects,words=words,
            encoding=EncodingSettings(resolution=data["resolution"]?.jsonPrimitive?.content?.let { Resolution.valueOf(it) } ?: Resolution.ORIGINAL, quality=data["quality"]?.jsonPrimitive?.float ?: .7f,
                framerate=data["framerate"]?.jsonPrimitive?.content?.let { Framerate.valueOf(it) } ?: Framerate.ORIGINAL,
                cpuSpeed=data["cpuSpeed"]?.jsonPrimitive?.content?.let { CpuEncoderSpeed.valueOf(it) } ?: CpuEncoderSpeed.BALANCED,
                keepHdr=data["keepHdr"]?.jsonPrimitive?.boolean ?: true,gif=gifSettings,
                audio=AudioEncoding(data["audioBitrate"]?.jsonPrimitive?.int ?: 192,data["audioSampleRate"]?.jsonPrimitive?.int ?: 0,data["audioDepth"]?.jsonPrimitive?.int ?: 16)),
            followCrop=data["followCrop"]?.let { ModelJson.followCrop(read(it)) },subtitleStyle=data["subtitleStyle"]?.let { ModelJson.subtitleStyle(read(it)) },audioEffects=data["audioEffects"]?.let {ModelJson.audioEffects(read(it))} ?: AudioEffects.NONE)
    }
}
