package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.cutout.CutoutModel
import com.cuscus.ffmpeggui.speech.SpeechModel
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files

/** Standalone executable checks. Pass model directory; append --speech for full Parakeet JNI inference. */
fun main(args: Array<String>) = runModelChecks(args)

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
