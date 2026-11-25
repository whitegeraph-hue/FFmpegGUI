package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.model.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Fixed output-clock notes. Timeline edits do not silently move a review note. */
data class DesktopMarker(val id:String,val atMs:Long,val name:String) {
    init {require(id.length in 1..80 && atMs>=0 && name.isNotBlank() && name.length<=100)}
}
internal object DesktopMarkers {
    fun encode(markers:List<DesktopMarker>):JsonArray {
        require(markers.size<=500 && markers.map {it.id}.distinct().size==markers.size)
        return buildJsonArray {markers.sortedBy {it.atMs}.forEach {m->add(buildJsonObject {put("id",m.id);put("atMs",m.atMs);put("name",m.name)})}}
    }
    fun decode(value:JsonElement?):List<DesktopMarker> {
        if(value==null)return emptyList()
        try {
        val items=value.jsonArray
        require(items.size<=500) {"At most 500 markers per project"}
        return items.map {v->val m=v.jsonObject;DesktopMarker(m["id"]!!.jsonPrimitive.content,m["atMs"]!!.jsonPrimitive.long,m["name"]!!.jsonPrimitive.content)}.also {require(it.map {m->m.id}.distinct().size==it.size)}.sortedBy {it.atMs}
        } catch(e:Exception) {throw IllegalArgumentException("Project markers are invalid. The current project was not changed.",e)}
    }
    fun next(markers:List<DesktopMarker>,atMs:Long,forward:Boolean,duration:Long):DesktopMarker? =
        markers.filter {it.atMs<=duration && if(forward)it.atMs>atMs else it.atMs<atMs}.let {if(forward)it.minByOrNull {m->m.atMs} else it.maxByOrNull {m->m.atMs}}
}

/** Delivery choices only, never edits/effects from another project. */
internal data class DesktopRecentExport(val path:String,val completedAt:Long,val format:OutputFormat,val encoding:EncodingSettings) {
    fun applyTo(project:DesktopProject)=project.copy(format=format,encoding=encoding)
}
internal class DesktopExportHistory(private val file:File=File(System.getenv("XDG_DATA_HOME") ?: File(System.getProperty("user.home"),".local/share").path,"ffmpeggui-linux/recent-exports.json")) {
    fun load():List<DesktopRecentExport> {
        if(!file.exists())return emptyList()
        require(!Files.isSymbolicLink(file.toPath()) && file.length()<=131072) {"Recent-export history is not a safe local file"}
        val root=Json.parseToJsonElement(file.readText()).jsonObject
        require(root["version"]?.jsonPrimitive?.int==1) {"Unsupported recent-export history"}
        val values=root["exports"]!!.jsonArray
        require(values.size<=12)
        return values.map {v->val j=v.jsonObject
            fun s(k:String)=j[k]!!.jsonPrimitive.content
            fun i(k:String)=j[k]!!.jsonPrimitive.int
            val quality=j["quality"]!!.jsonPrimitive.float.also {require(it.isFinite() && it in 0f..1f)}
            val path=s("path").also {require(it.length<=4096 && File(it).isAbsolute)}
            DesktopRecentExport(path,j["completedAt"]!!.jsonPrimitive.long,OutputFormat.valueOf(s("format")),EncodingSettings(
                resolution=Resolution.valueOf(s("resolution")),framerate=Framerate.valueOf(s("framerate")),quality=quality,cpuSpeed=CpuEncoderSpeed.valueOf(s("cpuSpeed")),keepHdr=j["keepHdr"]!!.jsonPrimitive.boolean,
                audio=AudioEncoding(i("audioBitrate"),i("audioSampleRate"),i("audioDepth")),
                gif=GifSettings(i("gifWidth"),i("gifFps"),i("gifColors"),GifDither.valueOf(s("gifDither")),i("gifLoops"),j["gifSmaller"]!!.jsonPrimitive.boolean)))
        }.sortedByDescending {it.completedAt}
    }
    fun record(path:File,format:OutputFormat,encoding:EncodingSettings,completedAt:Long=System.currentTimeMillis()) {
        require(path.isFile) {"Export output is missing"}
        val entry=DesktopRecentExport(path.absolutePath,completedAt,format,encoding)
        val previous=load()
        val entries=(listOf(entry)+previous.filter {it.path!=entry.path}).take(12)
        val data=buildJsonObject {put("version",1);putJsonArray("exports") {entries.forEach {e->add(buildJsonObject {
            val c=e.encoding;val g=c.gif
            put("path",e.path);put("completedAt",e.completedAt);put("format",e.format.name);put("resolution",c.resolution.name);put("framerate",c.framerate.name);put("quality",c.quality);put("cpuSpeed",c.cpuSpeed.name);put("keepHdr",c.keepHdr)
            put("audioBitrate",c.audio.bitrateKbps);put("audioSampleRate",c.audio.sampleRate);put("audioDepth",c.audio.bitDepth)
            put("gifWidth",g.width);put("gifFps",g.fps);put("gifColors",g.colors);put("gifDither",g.dither.name);put("gifLoops",g.loops);put("gifSmaller",g.smaller)
        })}}}.toString()
        require(data.toByteArray().size<=131072)
        val dir=file.absoluteFile.parentFile
        require(!Files.isSymbolicLink(dir.toPath())) {"History folder cannot be a symbolic link"}
        val created=!dir.exists()
        dir.mkdirs();require(dir.isDirectory)
        if(created)runCatching {Files.setPosixFilePermissions(dir.toPath(),java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))}
        val temp=Files.createTempFile(dir.toPath(),"exports-",".tmp")
        try {Files.writeString(temp,data);Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}finally {Files.deleteIfExists(temp)}
    }
}

internal data class DesktopCommand(val label:String,val group:String,val symbol:String,val enabled:Boolean=true,val action:()->Unit)
internal fun filterDesktopCommands(commands:List<DesktopCommand>,query:String):List<DesktopCommand> {
    val terms=query.trim().lowercase().split(Regex("\\s+")).filter {it.isNotBlank()}
    return commands.filter {c->terms.all {term->("${c.label} ${c.group}").lowercase().contains(term)}}
}
