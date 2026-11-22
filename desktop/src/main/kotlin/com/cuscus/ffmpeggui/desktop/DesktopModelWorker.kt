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
    private suspend fun run(kind:String,path:String,model:File,duration:Long=0,progress:(String)->Unit):JsonObject = gate.withLock {
        withContext(Dispatchers.IO) {
            val directory=Files.createTempDirectory("ffmpeggui-model-worker-").toFile()
            var process:Process?=null
            try {
                val request=File(directory,"request.json")
                request.writeText(buildJsonObject { put("kind",kind);put("path",path);put("model",model.path);put("duration",duration);put("parent",ProcessHandle.current().pid()) }.toString())
                val launcher=System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile }
                    ?: ProcessHandle.current().info().command().orElse(null)?.let(::File)?.takeIf { it.name=="ffmpeggui-linux" && it.isFile }
                val command=if(launcher!=null) listOf(launcher.path) else listOf(File(System.getProperty("java.home"),"bin/java").path,"-Xmx256m","-cp",System.getProperty("java.class.path"),"com.cuscus.ffmpeggui.desktop.MainKt")
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
                while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                    File(directory,"progress").takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull()?.let(progress) }
                }
                currentCoroutineContext().ensureActive()
                check(process.exitValue()==0) { "Offline model process stopped (${process.exitValue()}). " + File(directory,"launch.txt").readText()+File(directory,"worker.log").readText().take(2000) }
                val resultFile=File(directory,"result.json")
                check(resultFile.length() in 1..32_000_000) { "Invalid offline model response size" }
                Json.parseToJsonElement(resultFile.readText()).jsonObject
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
    suspend fun transcribe(path:String,model:File,progress:(String)->Unit):List<SpokenWord> =
        run("speech",path,model,progress=progress)["words"]!!.jsonArray.map { it.jsonObject.let { w -> SpokenWord(w["start"]!!.jsonPrimitive.long,w["end"]!!.jsonPrimitive.long,w["text"]!!.jsonPrimitive.content) } }
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
    val result=when(request["kind"]!!.jsonPrimitive.content) {
        "speech" -> {
            val words=runtime.transcribe(path,model,emptyList(),{ step,fraction -> progress("${step.name.lowercase()} ${fraction?.let { "${(it*100).toInt()}%" } ?: ""}") },{})
            buildJsonObject { put("words",buildJsonArray { words.forEach { w -> add(buildJsonObject { put("start",w.startMs);put("end",w.endMs);put("text",w.text) }) } }) }
        }
        "subject" -> {
            val track=runtime.track(path,request["duration"]!!.jsonPrimitive.long,model,progress={ progress(it.toString()) })
            buildJsonObject { put("width",track.frameWidth);put("height",track.frameHeight);put("points",buildJsonArray { track.points.forEach { p -> add(buildJsonObject { put("at",p.atMs);put("x",p.x);put("y",p.y);put("confidence",p.confidence) }) } }) }
        }
        else -> error("Unknown model operation")
    }
    File(directory,"result.json").writeText(result.toString())
}
