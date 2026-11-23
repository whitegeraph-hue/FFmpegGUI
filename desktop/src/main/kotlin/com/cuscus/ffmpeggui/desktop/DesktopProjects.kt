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
            project.compression?.let {v->put("compression",buildJsonObject {put("level",v.level.name);put("target",v.targetBytes);put("palette",v.pngPalette)})}
            project.coverPng?.let {DesktopCover.decode(it);put("coverPng",it)}
            project.stabilization?.let {v->put("stabilization",buildJsonObject {put("shakiness",v.shakiness);put("smoothing",v.smoothing)})}
            project.externalSubtitles?.let {v->put("externalSubtitles",buildJsonObject {put("name",v.name);put("content",v.content);put("source",v.sourceIndex);put("enabled",v.enabled)})}
            project.photoPlacement?.let {v->put("photoPlacement",buildJsonObject {put("x",v.x);put("y",v.y);put("width",v.widthFraction);put("opacity",v.opacity);put("rotation",v.rotationDegrees);put("layer",v.layer);put("stretch",v.stretch);put("blend",v.blend.name);put("aspect",v.aspect)})}
            put("tags",core(ModelJson.write(project.tags)));put("canvas",project.canvas.name);put("canvasFit",project.canvasFit.name);put("normalize",project.normalize)
            put("effects",core(ModelJson.write(project.effects)))
            put("pictures",buildJsonArray {project.pictures.forEach {picture->add(buildJsonObject {
                put("path",picture.source.path);put("size",File(picture.source.path).length());put("hidden",picture.hidden)
                val v=picture.placement
                v.video?.let {vid->put("video",buildJsonObject {put("trimStartMs",vid.trimStartMs);vid.trimEndMs?.let {put("trimEndMs",it)};put("loop",vid.loop)})}
                v.clipId?.let {put("clipId",it)};put("keys",core(ModelJson.write(Effects(keyframes=v.keyframes))))
                put("x",v.x);put("y",v.y);put("width",v.widthFraction);put("opacity",v.opacity);put("rotation",v.rotationDegrees);put("layer",v.layer)
                put("startMs",v.startMs);v.endMs?.let {put("endMs",it)};put("fadeIn",v.fadeInSeconds);put("fadeOut",v.fadeOutSeconds)
                put("flipH",v.flipHorizontal);put("flipV",v.flipVertical);put("brightness",v.brightness);put("contrast",v.contrast);put("saturation",v.saturation)
                put("blend",v.blend.name);put("stretch",v.stretch);put("look",core(ModelJson.write(v.look)))
            })}})
            put("sounds",buildJsonArray {project.sounds.forEach { sound -> add(buildJsonObject {
                put("path",sound.source.path);put("size",File(sound.source.path).length());put("durationMs",sound.source.info.durationMs);put("muted",sound.muted)
                val s=sound.placed
                put("keys",core(ModelJson.write(Effects(keyframes=s.keyframes))))
                put("startMs",s.startMs);put("trimStartMs",s.trimStartMs);s.trimEndMs?.let {put("trimEndMs",it)}
                put("volume",s.volume);put("speed",s.speed);put("keepPitch",s.keepPitch);put("pitch",s.pitchSemitones);put("repeats",s.repeats)
                put("fadeIn",s.fadeInSeconds);put("fadeOut",s.fadeOutSeconds);put("normalize",s.normalize)
                put("bass",s.bassDb);put("mid",s.midDb);put("treble",s.trebleDb);put("effects",core(ModelJson.write(s.effects)))
            }) }})
            put("captions",buildJsonArray { project.captions.forEach {add(core(ModelJson.write(it)))} })
            put("image",core(ModelJson.write(project.image)))
            project.image.key?.takeIf {it.cutsSubject}?.mask?.let {path->
                val file=File(path);require(file.length() in 1..24_000_000) {"Subject mask is missing or too large"}
                put("subjectMask",java.util.Base64.getEncoder().encodeToString(file.readBytes()))
            }
            project.image.hdr?.let {hdr->
                listOf("hdrSubjectMask" to hdr.mask,"hdrBrushMask" to hdr.brush).forEach {(key,path)->path?.let {
                    val file=File(it);DesktopHdr.readMask(file,project.sources.single().info.width,project.sources.single().info.height)
                    put(key,java.util.Base64.getEncoder().encodeToString(file.readBytes()))
                }}
            }
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
            put("wordsBySource",buildJsonObject {project.sources.indices.forEach {i->put(project.sources[i].path,buildJsonArray {project.sourceWords(i).forEach {w->add(buildJsonObject {put("startMs",w.startMs);put("endMs",w.endMs);put("text",w.text)})}})}})
            put("wordsByClip",buildJsonObject {project.wordsByClip.forEach {(id,words)->put(id,buildJsonArray {words.forEach {w->add(buildJsonObject {put("startMs",w.startMs);put("endMs",w.endMs);put("text",w.text)})}})}})
            put("words",buildJsonArray { project.words.forEach { w -> add(buildJsonObject { put("startMs",w.startMs);put("endMs",w.endMs);put("text",w.text) }) } })
        }
        require((project.sources+project.sounds.map {it.source}+project.pictures.map {it.source}).none { File(it.path).canonicalFile==target.canonicalFile }) { "Cannot replace a source with a project." }
        require(listOfNotNull(project.image.hdr?.mask,project.image.hdr?.brush).none {File(it).canonicalFile==target.canonicalFile}) {"Cannot replace an HDR mask with a project."}
        require(project.image.key?.mask?.let {File(it).canonicalFile!=target.canonicalFile} ?: true) {"Cannot replace a subject mask with a project."}
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
        require(sources.isNotEmpty() && sources.map {it.path}.distinct().size==sources.size){"Invalid or duplicate source list."}
        require(sources.size==1 || (sources.first().info.isVideo && !sources.first().info.isStill && sources.all {it.info.isVideo})){"Multi-source timelines must start with video and contain only video or still pictures. Audio-only files use Sound tracks."}
        val timeline=ModelJson.timeline(read(data["timeline"]!!))
        require(timeline.clips.isNotEmpty() && timeline.clips.all { c ->
            val s=sources.firstOrNull { it.path==c.sourceId }
            val end=c.endMs
            s!=null && c.startMs>=0 && end!=null && end>c.startMs && (s.info.isStill || end<=s.info.durationMs+50)
        }) { "Project timeline refers to missing or invalid footage." }
        val pictures=data["pictures"]?.jsonArray?.map {item->
            val v=item.jsonObject;val source=media.probe(v["path"]!!.jsonPrimitive.content)
            require(source.info.isVideo && File(source.path).length()==v["size"]!!.jsonPrimitive.long){"An image layer changed or is not picture/video."}
            fun f(k:String)=v[k]!!.jsonPrimitive.float
            val vid=v["video"]?.jsonObject?.let {OverlayVideo(it["trimStartMs"]!!.jsonPrimitive.long,it["trimEndMs"]?.jsonPrimitive?.long,it["loop"]!!.jsonPrimitive.boolean)}
            require(source.info.isStill || (vid!=null && v["clipId"]==null)){"Video layers require whole-output timing."}
            val placement=ImagePlacement(video=vid,clipId=v["clipId"]?.jsonPrimitive?.content,keyframes=v["keys"]?.let {ModelJson.effects(read(it)).keyframes} ?: Keyframes.NONE,x=f("x"),y=f("y"),widthFraction=f("width"),opacity=f("opacity"),rotationDegrees=f("rotation"),layer=v["layer"]!!.jsonPrimitive.int,
                startMs=v["startMs"]!!.jsonPrimitive.long,endMs=v["endMs"]?.jsonPrimitive?.long,fadeInSeconds=f("fadeIn"),fadeOutSeconds=f("fadeOut"),
                flipHorizontal=v["flipH"]!!.jsonPrimitive.boolean,flipVertical=v["flipV"]!!.jsonPrimitive.boolean,brightness=f("brightness"),contrast=f("contrast"),saturation=f("saturation"),
                blend=LayerBlend.valueOf(v["blend"]!!.jsonPrimitive.content),stretch=f("stretch"),look=ModelJson.overlayLook(read(v["look"]!!)),aspect=source.info.width.toFloat()/source.info.height)
            DesktopPicture(source,placement,v["hidden"]!!.jsonPrimitive.boolean)
        } ?: emptyList()
        val sounds=data["sounds"]?.jsonArray?.map {item ->
            val v=item.jsonObject;val source=media.probe(v["path"]!!.jsonPrimitive.content)
            require(source.info.hasAudio && source.info.durationMs==v["durationMs"]!!.jsonPrimitive.long && File(source.path).length()==v["size"]!!.jsonPrimitive.long){"A sound file changed or has no audio."}
            fun f(k:String)=v[k]!!.jsonPrimitive.float
            fun l(k:String)=v[k]!!.jsonPrimitive.long
            val placed=PlacedSound(keyframes=v["keys"]?.let {ModelJson.effects(read(it)).keyframes} ?: Keyframes.NONE,startMs=l("startMs"),trimStartMs=l("trimStartMs"),trimEndMs=v["trimEndMs"]?.jsonPrimitive?.long,
                volume=f("volume"),speed=f("speed"),keepPitch=v["keepPitch"]!!.jsonPrimitive.boolean,pitchSemitones=f("pitch"),repeats=v["repeats"]!!.jsonPrimitive.int,
                fadeInSeconds=f("fadeIn"),fadeOutSeconds=f("fadeOut"),normalize=v["normalize"]!!.jsonPrimitive.boolean,bassDb=f("bass"),midDb=f("mid"),trebleDb=f("treble"),effects=ModelJson.audioEffects(read(v["effects"]!!)))
            require(placed.startMs>=0 && placed.trimStartMs>=0 && (placed.trimEndMs ?: source.info.durationMs) in (placed.trimStartMs+1)..source.info.durationMs){"Invalid sound trim."}
            DesktopSound(source,placed,v["muted"]!!.jsonPrimitive.boolean)
        } ?: emptyList()
        val effects=ModelJson.effects(read(data["effects"]!!))
        val gif=data["gif"]?.jsonObject
        val gifSettings=gif?.let { GifSettings(it["width"]!!.jsonPrimitive.int,it["fps"]!!.jsonPrimitive.int,it["colors"]!!.jsonPrimitive.int,GifDither.valueOf(it["dither"]!!.jsonPrimitive.content),it["loops"]!!.jsonPrimitive.int,it["smaller"]!!.jsonPrimitive.boolean) } ?: GifSettings()
        val words=data["words"]!!.jsonArray.map { w -> w.jsonObject.let { SpokenWord(it["startMs"]!!.jsonPrimitive.long,it["endMs"]!!.jsonPrimitive.long,it["text"]!!.jsonPrimitive.content) } }
        val perSource=data["wordsBySource"]?.jsonObject?.mapValues {(_,v)->v.jsonArray.map {w->w.jsonObject.let {SpokenWord(it["startMs"]!!.jsonPrimitive.long,it["endMs"]!!.jsonPrimitive.long,it["text"]!!.jsonPrimitive.content)}}} ?: emptyMap()
        val perClip=data["wordsByClip"]?.jsonObject?.mapValues {(_,v)->v.jsonArray.map {w->w.jsonObject.let {SpokenWord(it["startMs"]!!.jsonPrimitive.long,it["endMs"]!!.jsonPrimitive.long,it["text"]!!.jsonPrimitive.content)}}} ?: emptyMap()
        require(perClip.size<=10000 && perClip.values.sumOf {it.size}<=200000) {"Too many clip words"}
        require(perSource.keys.all {key->sources.any {it.path==key}}){"Words refer to missing source."}
        var image=data["image"]?.let {ModelJson.imageEdit(read(it))} ?: ImageEdit()
        if(image.key?.cutsSubject==true) {
            val encoded=data["subjectMask"]?.jsonPrimitive?.content ?: error("Saved subject mask is missing")
            require(encoded.length<=32_000_000) {"Subject mask is too large"}
            val bytes=java.util.Base64.getDecoder().decode(encoded)
            val input=javax.imageio.ImageIO.createImageInputStream(java.io.ByteArrayInputStream(bytes))
            input.use {stream->
                val readers=javax.imageio.ImageIO.getImageReaders(stream)
                require(readers.hasNext()) {"Invalid subject mask"}
                val reader=readers.next()
                try {reader.input=stream;require(reader.getWidth(0).toLong()*reader.getHeight(0)<=32_000_000) {"Subject mask is too large"}} finally {reader.dispose()}
            }
            val mask=javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) ?: error("Invalid subject mask")
            try {require(mask.width==sources.first().info.width && mask.height==sources.first().info.height) {"Subject mask geometry does not match source"}} finally {mask.flush()}
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
            val directory=DesktopMasks.directory()
            val target=File(directory,"$hash.png")
            if(!target.isFile || !target.readBytes().contentEquals(bytes)) {
                val tmp=File.createTempFile("subject-",".png",directory)
                try {tmp.writeBytes(bytes);Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)} finally {tmp.delete()}
            }
            image=image.copy(key=image.key!!.copy(mask=target.absolutePath))
        }
        image.hdr?.let {hdr->
            fun restore(key:String,path:String?):String? {
                if(path==null)return null
                val encoded=data[key]?.jsonPrimitive?.content ?: error("Saved HDR mask is missing")
                require(encoded.length<=32_000_000) {"HDR mask is too large"}
                val bytes=java.util.Base64.getDecoder().decode(encoded)
                val temp=File.createTempFile("hdr-restore-",".png",DesktopMasks.directory())
                try {
                    temp.writeBytes(bytes);DesktopHdr.readMask(temp,sources.single().info.width,sources.single().info.height)
                    val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
                    val target=File(DesktopMasks.directory(),"$hash.png")
                    Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);return target.absolutePath
                }finally {temp.delete()}
            }
            image=image.copy(hdr=hdr.copy(mask=restore("hdrSubjectMask",hdr.mask),brush=restore("hdrBrushMask",hdr.brush)))
        }
        return DesktopProject(sources,timeline,OutputFormat.valueOf(data["format"]!!.jsonPrimitive.content),compression=data["compression"]?.jsonObject?.let {v->Compression(CompressionLevel.valueOf(v["level"]!!.jsonPrimitive.content),v["target"]!!.jsonPrimitive.long.also {require(it in 0..100_000_000_000)},v["palette"]!!.jsonPrimitive.boolean)},coverPng=data["coverPng"]?.jsonPrimitive?.content?.also {DesktopCover.decode(it)},photoPlacement=data["photoPlacement"]?.jsonObject?.let {v->ImagePlacement(x=v["x"]!!.jsonPrimitive.float,y=v["y"]!!.jsonPrimitive.float,widthFraction=v["width"]!!.jsonPrimitive.float,opacity=v["opacity"]!!.jsonPrimitive.float,rotationDegrees=v["rotation"]!!.jsonPrimitive.float,layer=v["layer"]!!.jsonPrimitive.int,stretch=v["stretch"]!!.jsonPrimitive.float,blend=LayerBlend.valueOf(v["blend"]!!.jsonPrimitive.content),aspect=v["aspect"]?.jsonPrimitive?.float ?: 0f)},effects=effects,words=words,stabilization=data["stabilization"]?.jsonObject?.let {v->Stabilization(v["shakiness"]!!.jsonPrimitive.int.coerceIn(1,10),v["smoothing"]!!.jsonPrimitive.int.coerceIn(0,100))},externalSubtitles=data["externalSubtitles"]?.jsonObject?.let {v->DesktopSubtitles(v["name"]!!.jsonPrimitive.content,v["content"]!!.jsonPrimitive.content,v["source"]!!.jsonPrimitive.int,v["enabled"]!!.jsonPrimitive.boolean).also {it.validate();require(it.sourceIndex in sources.indices){"Subtitle source missing"}}},
            encoding=EncodingSettings(resolution=data["resolution"]?.jsonPrimitive?.content?.let { Resolution.valueOf(it) } ?: Resolution.ORIGINAL, quality=data["quality"]?.jsonPrimitive?.float ?: .7f,
                framerate=data["framerate"]?.jsonPrimitive?.content?.let { Framerate.valueOf(it) } ?: Framerate.ORIGINAL,
                cpuSpeed=data["cpuSpeed"]?.jsonPrimitive?.content?.let { CpuEncoderSpeed.valueOf(it) } ?: CpuEncoderSpeed.BALANCED,
                keepHdr=data["keepHdr"]?.jsonPrimitive?.boolean ?: true,gif=gifSettings,
                audio=AudioEncoding(data["audioBitrate"]?.jsonPrimitive?.int ?: 192,data["audioSampleRate"]?.jsonPrimitive?.int ?: 0,data["audioDepth"]?.jsonPrimitive?.int ?: 16)),
            followCrop=data["followCrop"]?.let { ModelJson.followCrop(read(it)) },subtitleStyle=data["subtitleStyle"]?.let { ModelJson.subtitleStyle(read(it)) },wordsBySource=perSource,wordsByClip=perClip,pictures=pictures,tags=data["tags"]?.let {ModelJson.metadataTags(read(it))} ?: MetadataTags(),canvas=data["canvas"]?.jsonPrimitive?.content?.let {CanvasRatio.valueOf(it)} ?: CanvasRatio.FIT,canvasFit=data["canvasFit"]?.jsonPrimitive?.content?.let {CanvasFit.valueOf(it)} ?: CanvasFit.FIT,normalize=data["normalize"]?.jsonPrimitive?.boolean ?: false,sounds=sounds,captions=data["captions"]?.jsonArray?.map { ModelJson.textOverlay(read(it)) ?: error("Invalid caption") } ?: emptyList(),image=image,audioEffects=data["audioEffects"]?.let {ModelJson.audioEffects(read(it))} ?: AudioEffects.NONE)
    }
}
