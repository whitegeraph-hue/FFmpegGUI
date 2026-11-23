package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.editing.SubjectPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** A native crash must not take the unsaved editor down. No network or shell. */
class DesktopModelWorker {
    private val gate=Mutex()
    private suspend fun run(kind:String,path:String,model:File,duration:Long=0,output:File?=null,payload:JsonObject?=null,progress:(String)->Unit):JsonObject = gate.withLock {
        withContext(Dispatchers.IO) {
            val directory=Files.createTempDirectory("ffmpeggui-model-worker-").toFile()
            var process:Process?=null
            try {
                val request=File(directory,"request.json")
                request.writeText(buildJsonObject { put("kind",kind);put("path",File(path).absolutePath);put("model",model.absolutePath);put("duration",duration);put("parent",ProcessHandle.current().pid());payload?.forEach {(key,value)->put(key,value)} }.toString())
                val launcher=System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile }
                    ?: ProcessHandle.current().info().command().orElse(null)?.let(::File)?.takeIf { it.name=="ffmpeggui-linux" && it.isFile }
                val classpath=System.getProperty("java.class.path").split(File.pathSeparator).joinToString(File.pathSeparator) { File(it).absolutePath }
                val command=if(launcher!=null) listOf(launcher.path) else listOf(File(System.getProperty("java.home"),"bin/java").path,"-Xmx256m","-cp",classpath,"com.cuscus.ffmpeggui.desktop.MainKt")
                val builder=ProcessBuilder(command).directory(directory)
                    .redirectErrorStream(true).redirectOutput(File(directory,"worker.log"))
                builder.environment().apply {
                    keys.filter { it.startsWith("FFMPEGGUI_") || it.startsWith("_JPACKAGE_") }.toList().forEach { remove(it) }
                    put("FFMPEGGUI_MODEL_WORKER",directory.path)
                }
                File(directory,"launch.txt").writeText("command=$command\njava.home=${System.getProperty("java.home")}\njpackage.app-path=${System.getProperty("jpackage.app-path")}\ncurrent.command=${ProcessHandle.current().info().command()}")
                process=builder.start();process.outputStream.close()
                val child=process
                val shutdown=Thread { child.descendants().forEach { it.destroyForcibly() };child.destroyForcibly() }
                Runtime.getRuntime().addShutdownHook(shutdown)
                try {
                val started=System.nanoTime()
                var lastProgress:String?=null
                while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                    check(System.nanoTime()-started<TimeUnit.MINUTES.toNanos(30)) {"Offline model processing exceeded 30 minutes. Split the source or subtitle file into smaller parts."}
                    currentCoroutineContext().ensureActive()
                    File(directory,"progress").takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull()?.takeIf {it!=lastProgress}?.let {lastProgress=it;progress(it)} }
                }
                currentCoroutineContext().ensureActive()
                check(process.exitValue()==0) {
                    File(directory,"error.txt").takeIf {it.isFile && it.length() in 1..4000}?.readText()
                        ?: "Offline model process stopped (${process.exitValue()}). Try closing other apps and retrying."
                }
                val resultFile=File(directory,"result.json")
                check(resultFile.length() in 1..32_000_000) { "Invalid offline model response size" }
                val response=Json.parseToJsonElement(resultFile.readText()).jsonObject
                if(kind=="stems") {
                    val target=requireNotNull(output)
                    val total=DesktopStems.names.sumOf {File(directory,it).length()}
                    check(target.usableSpace>total+10_000_000) {"Not enough free disk space in the output folder"}
                    check(response["files"]!!.jsonArray.map {it.jsonPrimitive.content}==DesktopStems.names) {"Unexpected stem names"}
                    DesktopStems.names.forEach {name->
                        val source=File(directory,name)
                        check(source.isFile && !Files.isSymbolicLink(source.toPath()) && source.length() in 45..250_000_000) {"Invalid stem output"}
                        source.inputStream().use {input->Files.newOutputStream(File(target,name).toPath(),java.nio.file.StandardOpenOption.CREATE_NEW).use {input.copyTo(it)}}
                    }
                }
                response
                } finally { Runtime.getRuntime().removeShutdownHook(shutdown) }
            } finally {
                process?.let { p ->
                    val children=p.descendants().toList()
                    children.forEach { it.destroy() };p.destroy()
                    if(!p.waitFor(1,TimeUnit.SECONDS))p.destroyForcibly().waitFor()
                    children.filter { it.isAlive }.forEach { it.destroyForcibly() }
                }
                directory.deleteRecursively()
            }
        }
    }
    suspend fun erase(path:String,mask:File,destination:File,progress:(String)->Unit) {
        require(!destination.exists()) {"Choose a new output PNG; existing files are not replaced"}
        require(destination.extension.equals("png",true)) {"Magic eraser output must be PNG"}
        val response=run("erase",path,DesktopModels.eraseFile,payload=buildJsonObject {put("mask",mask.absolutePath);put("markers",DesktopModels.pictureMarkers.absolutePath)},progress=progress)
        val bytes=java.util.Base64.getDecoder().decode(response["png"]!!.jsonPrimitive.content)
        require(bytes.size<=16_000_000) {"Invalid erased picture response"}
        var created=false
        try {
            java.nio.file.Files.newOutputStream(destination.toPath(),java.nio.file.StandardOpenOption.CREATE_NEW).use {created=true;it.write(bytes)}
            require(DesktopProvenance.aiPicture(destination)) {"Missing AI picture disclosure"}
        }catch(t:Throwable){if(created)destination.delete();throw t}
    }
    suspend fun checkTranslation(directory:File) {run("translation-check","",directory,progress={})}
    suspend fun translate(directory:File,texts:List<String>,progress:(String)->Unit):List<String> {
        val payload=buildJsonObject {put("texts",buildJsonArray {texts.forEach {add(JsonPrimitive(it))}})}
        return run("translation","",directory,payload=payload,progress=progress)["texts"]!!.jsonArray.map {it.jsonPrimitive.content}
    }
    /** Destination must not exist. A failed/cancelled run leaves no partial stem directory. */
    suspend fun stems(path:String,model:File,destination:File,progress:(String)->Unit):List<File> {
        require(destination.mkdir()) {"Choose a new, writable output folder"}
        try {
            run("stems",path,model,output=destination,progress=progress)
            listOf("LICENSE.txt","NOTICE.txt","PROVENANCE.txt").forEach {name->
                DesktopModelWorker::class.java.getResourceAsStream("/bandit-int8/$name")!!.use {input->File(destination,name).outputStream().use {input.copyTo(it)}}
            }
            return DesktopStems.names.map {File(destination,it)}
        } catch(t:Throwable) {destination.deleteRecursively();throw t}
    }
    suspend fun transcribe(path:String,model:File,onWarning:(String)->Unit={},progress:(String)->Unit):List<SpokenWord> {
        val result=run("speech",path,model,progress=progress)
        result["warnings"]?.jsonArray?.forEach {onWarning(it.jsonPrimitive.content)}
        return result["words"]!!.jsonArray.map { it.jsonObject.let { w -> SpokenWord(w["start"]!!.jsonPrimitive.long,w["end"]!!.jsonPrimitive.long,w["text"]!!.jsonPrimitive.content) } }
    }
    suspend fun cutout(path:String,model:File):java.awt.image.BufferedImage {
        val result=run("cutout",path,model,progress={})
        val bytes=java.util.Base64.getDecoder().decode(result["png"]!!.jsonPrimitive.content)
        return javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) ?: error("Invalid subject mask")
    }
    suspend fun track(path:String,duration:Long,model:File,progress:(Float)->Unit):DesktopSmartModels.SubjectTrack {
        val result=run("subject",path,model,duration) { it.toFloatOrNull()?.let(progress) }
        return DesktopSmartModels.SubjectTrack(result["points"]!!.jsonArray.map { it.jsonObject.let { p -> SubjectPoint(p["at"]!!.jsonPrimitive.long,p["x"]!!.jsonPrimitive.float,p["y"]!!.jsonPrimitive.float,p["confidence"]!!.jsonPrimitive.float) } },result["width"]!!.jsonPrimitive.int,result["height"]!!.jsonPrimitive.int)
    }
}

fun runModelWorker(folder:String)=runBlocking {
    val directory=File(folder)
    val request=Json.parseToJsonElement(File(directory,"request.json").readText()).jsonObject
    val parent=request["parent"]?.jsonPrimitive?.long
    if(parent!=null) Thread {
        while(ProcessHandle.of(parent).map { it.isAlive }.orElse(false)) Thread.sleep(250)
        ProcessHandle.current().descendants().forEach { it.destroyForcibly() }
        directory.deleteRecursively()
        kotlin.system.exitProcess(125)
    }.apply { isDaemon=true;start() }
    val runtime=DesktopSmartModels(cacheDirectory=File(directory,"media"))
    fun progress(text:String) { File(directory,"progress").writeText(text) }
    val path=request["path"]!!.jsonPrimitive.content
    val model=File(request["model"]!!.jsonPrimitive.content)
    try {
    val result=when(request["kind"]!!.jsonPrimitive.content) {
        "erase" -> {
            val output=DesktopErase.erase(path,request["mask"]!!.jsonPrimitive.content,model,File(request["markers"]!!.jsonPrimitive.content),directory,::progress)
            require(output.length()<=16_000_000)
            buildJsonObject {put("png",java.util.Base64.getEncoder().encodeToString(output.readBytes()))}
        }
        "translation-check","translation" -> {
            val texts=request["texts"]?.jsonArray?.map {it.jsonPrimitive.content} ?: emptyList()
            val translated=DesktopTranslation.execute(model,texts,request["kind"]!!.jsonPrimitive.content=="translation-check",::progress)
            buildJsonObject {put("texts",buildJsonArray {translated.forEach {add(JsonPrimitive(it))}})}
        }
        "speech" -> {
            val warnings=mutableListOf<String>()
            val words=runtime.transcribe(path,model,emptyList(),{ step,fraction -> progress("${step.name.lowercase()} ${fraction?.let { "${(it*100).toInt()}%" } ?: ""}") },{},{warnings+=it})
            buildJsonObject { put("warnings",buildJsonArray {warnings.forEach {add(JsonPrimitive(it))}});put("words",buildJsonArray { words.forEach { w -> add(buildJsonObject { put("start",w.startMs);put("end",w.endMs);put("text",w.text) }) } }) }
        }
        "cutout" -> {
            val image=runtime.cutout(path,model)
            try {
                val bytes=java.io.ByteArrayOutputStream()
                check(javax.imageio.ImageIO.write(image,"png",bytes))
                buildJsonObject {put("png",java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()))}
            } finally {image.flush()}
        }
        "stems" -> {
            val files=DesktopStems.separate(path,model,directory,::progress)
            buildJsonObject {put("files",buildJsonArray {files.forEach {add(JsonPrimitive(it.name))}})}
        }
        "subject" -> {
            val track=runtime.track(path,request["duration"]!!.jsonPrimitive.long,model,progress={ progress(it.toString()) })
            buildJsonObject { put("width",track.frameWidth);put("height",track.frameHeight);put("points",buildJsonArray { track.points.forEach { p -> add(buildJsonObject { put("at",p.atMs);put("x",p.x);put("y",p.y);put("confidence",p.confidence) }) } }) }
        }
        else -> error("Unknown model operation")
    }
    File(directory,"result.json").writeText(result.toString())
    } catch(e:Exception) {
        File(directory,"error.txt").writeText((e.message ?: "Offline model could not finish").take(3000))
        throw e
    }
}
