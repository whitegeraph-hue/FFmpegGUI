package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.cutout.CutoutModel
import com.cuscus.ffmpeggui.speech.SpeechModel
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files

/** Standalone executable checks. Pass model directory; append --speech for full Parakeet JNI inference. */
fun main(args: Array<String>) = if("--isolated-only" in args) runIsolatedChecks(args[0]) else runModelChecks(args)

fun runModelChecks(args: Array<String>) = runBlocking {
    val models = File(args[0])
    val cache = Files.createTempDirectory("native-adapter-test-").toFile()
    try {
        val zero = DesktopSmartModels.centroid(IntArray(4), 2, 123)
        check(zero.x == .5f && zero.y == .5f && zero.confidence == 0f && zero.atMs == 123L)
        val one = DesktopSmartModels.centroid(intArrayOf(0, 0, 0, 255), 2, 42)
        check(one.x == .5f && one.y == .5f && one.confidence == .25f)
        println("PASS centroid parity")
        val source = File(models, "u2netp.onnx")
        val target = File(cache, "u2netp.onnx")
        DesktopModelFiles.importFile(source, target, CutoutModel.FAST.sha256, CutoutModel.FAST.bytes)
        DesktopModelFiles.verify(target, CutoutModel.FAST.sha256, CutoutModel.FAST.bytes)
        val bad = File(cache, "bad.onnx").apply { writeText("not a model") }
        val before = target.readBytes()
        check(runCatching { DesktopModelFiles.importFile(bad, target, CutoutModel.FAST.sha256, CutoutModel.FAST.bytes) }.isFailure)
        check(target.readBytes().contentEquals(before))
        check(cache.listFiles()!!.none { it.name.startsWith(".model-") })
        println("PASS verified import, corrupt rejection, existing install preserved, stage cleanup")
        val runtime = DesktopSmartModels(cacheDirectory = cache, threads = 2)
        if ("--speech-first" in args) {
            val words = runtime.transcribe(File(models, "en.wav").absolutePath, models)
            check(words.size > 5)
            println("PASS Parakeet first, before ORT Java native load")
        }
        val progress = mutableListOf<Float>()
        val track = runtime.track(File(models, "video.mp4").absolutePath, 1100, source, progress = { progress += it })
        check(track.frameWidth == 320 && track.frameHeight == 240)
        check(track.points.map { it.atMs } == listOf(0L, 500L, 1000L))
        check(track.points.all { it.x in 0f..1f && it.y in 0f..1f && it.confidence in 0f..1f })
        check(progress.last() == 1f && progress.zipWithNext().all { it.first <= it.second })
        println("PASS actual U2-NetP ONNX inference: ${track.points}")
        val job = launch { runtime.track(File(models, "video.mp4").absolutePath, 1100, source, everyMs = 1) }
        delay(100)
        job.cancelAndJoin()
        check(cache.listFiles()!!.none { it.name.startsWith("subject-") || it.name.startsWith("ffmpeg-") })
        println("PASS cancellation cleanup")
        if ("--speech" in args) {
            DesktopModelFiles.speech(models)
            val words = runtime.transcribe(File(models, "en.wav").absolutePath, models)
            check(words.isNotEmpty())
            check(words.all { it.endMs > it.startMs && it.startMs >= 0L })
            check(words.zipWithNext().all { it.first.startMs <= it.second.startMs })
            println("PASS actual pinned Parakeet/JNI inference: ${words.joinToString(" ") { it.text }}")
            println("WORDS $words")
        }
        check(cache.listFiles()!!.none { it.name.startsWith("speech-") || it.name.startsWith("subject-") || it.name.startsWith("ffmpeg-") })
        println("PASS temporary media cleanup")
    } finally { cache.deleteRecursively() }
}

fun runIsolatedChecks(path:String)=runBlocking {
val models=File(path)
val source=File(models,"u2netp.onnx")
        val isolated=DesktopModelWorker()
        check(isolated.transcribe(File(models,"en.wav").path,models){}.size>5)
        val isolatedTrack=isolated.track(File(models,"video.mp4").path,1100,source){}
        check(isolatedTrack.points.size==3)
        println("PASS isolated speech and tracking workers")
        val image=DesktopMedia().frame(File(models,"video.mp4").path,0)!!
        val expectedWidth=image.width;val expectedHeight=image.height
        val photo=File(models,"cutout-test-input.png")
        javax.imageio.ImageIO.write(image,"png",photo);image.flush()
        val mask=isolated.cutout(photo.path,source)
        check(mask.width==expectedWidth && mask.height==expectedHeight)
        val alpha=IntArray(mask.width*mask.height) {i->mask.getRGB(i%mask.width,i/mask.width) ushr 24}
        check(alpha.max()>220 && alpha.min()<30) {"Model returned a flat mask"}
        val maskFile=File(models,"cutout-test-mask.png");javax.imageio.ImageIO.write(mask,"png",maskFile);mask.flush()
        val media=DesktopMedia();val project=DesktopProject.create(media.probe(photo.path)).copy(image=com.cuscus.ffmpeggui.core.model.ImageEdit(key=com.cuscus.ffmpeggui.core.model.ColorKey(mode=com.cuscus.ffmpeggui.core.model.KeyMode.SUBJECT,mask=maskFile.path)))
        val target=File(models,"cutout-test-output.png");DesktopExport(media).export(project,target)
        val output=javax.imageio.ImageIO.read(target)
        check(output.colorModel.hasAlpha())
        val values=IntArray(output.width*output.height) {i->output.getRGB(i%output.width,i/output.width) ushr 24}
        check(values.min()<30 && values.max()>220)
        val saved=File(models,"cutout.ffgui.json");DesktopProjects.save(project,saved);maskFile.delete()
        val restored=DesktopProjects.load(saved,media)
        check(File(restored.image.key!!.mask!!).isFile)
        DesktopExport(media).export(restored,File(models,"cutout-restored.png"))
        val jpeg=java.io.ByteArrayOutputStream().also {javax.imageio.ImageIO.write(javax.imageio.ImageIO.read(photo),"jpg",it)}.toByteArray()
        val payload=byteArrayOf(69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0)
        val oriented=File(models,"cutout-oriented.jpg")
        oriented.writeBytes(jpeg.take(2).toByteArray()+byteArrayOf(0xff.toByte(),0xe1.toByte(),0,(payload.size+2).toByte())+payload+jpeg.drop(2).toByteArray())
        val orientedSource=media.probe(oriented.path)
        val orientedMask=isolated.cutout(oriented.path,source)
        check(orientedMask.width==expectedHeight && orientedMask.height==expectedWidth)
        val orientedMaskFile=File(models,"cutout-oriented-mask.png");javax.imageio.ImageIO.write(orientedMask,"png",orientedMaskFile);orientedMask.flush()
        val orientedProject=DesktopProject.create(orientedSource).copy(image=com.cuscus.ffmpeggui.core.model.ImageEdit(key=com.cuscus.ffmpeggui.core.model.ColorKey(mode=com.cuscus.ffmpeggui.core.model.KeyMode.SUBJECT,mask=orientedMaskFile.path)))
        val orientedSaved=File(models,"cutout-oriented.ffgui.json");DesktopProjects.save(orientedProject,orientedSaved)
        val orientedResult=DesktopExport(media).export(DesktopProjects.load(orientedSaved,media),File(models,"cutout-oriented.png"))
        check(orientedResult.info.width==expectedHeight && orientedResult.info.height==expectedWidth)
        println("PASS isolated real subject cutout alpha export portable mask and EXIF geometry")
        val known=ProcessHandle.current().descendants().map { it.pid() }.toList().toSet()
        val cancelled=launch { isolated.transcribe(File(models,"en.wav").path,models){} }
        var child:ProcessHandle?=null
        repeat(100) {
            if(child==null)child=ProcessHandle.current().descendants().filter { it.pid() !in known }.findFirst().orElse(null)
            if(child==null)delay(20)
        }
        check(child!=null) { "No worker launched" }
        cancelled.cancelAndJoin()
        delay(200)
        check(!child!!.isAlive) { "Cancelled worker survived" }
        println("PASS cancelled worker exits")
        val crashed=async { runCatching { isolated.transcribe(File(models,"en.wav").path,models){} } }
        var failedChild:ProcessHandle?=null
        repeat(100) {
            if(failedChild==null)failedChild=ProcessHandle.current().descendants().filter { it.pid() !in known }.findFirst().orElse(null)
            if(failedChild==null)delay(20)
        }
        check(failedChild!=null)
        failedChild!!.destroyForcibly()
        check(crashed.await().isFailure) { "Killed worker was reported as success" }
        check(isolated.transcribe(File(models,"en.wav").path,models){}.isNotEmpty())
        println("PASS crashed worker reports failure and retry succeeds")
}
