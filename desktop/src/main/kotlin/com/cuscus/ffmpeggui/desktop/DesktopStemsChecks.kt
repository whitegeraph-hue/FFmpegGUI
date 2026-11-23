package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*

/** Opt-in actual pinned Bandit inference and cross-process output ownership check. */
fun runStemsChecks(root:String)=runBlocking {
    val models=File(root)
    val directory=Files.createTempDirectory("stems-parent-proof-").toFile()
    try {
        val worker=DesktopModelWorker()
        val destination=File(directory,"outputs")
        val files=worker.stems(File(models,"input.wav").path,File(models,DesktopStems.NAME),destination) {println(it)}
        check(files.size==3 && files.all {it.isFile && it.length()>44})
        val media=DesktopMedia()
        val duration=media.probe(File(models,"input.wav").path).info.durationMs
        files.forEach { val info=media.probe(it.path).info;check(info.hasAudio && kotlin.math.abs(info.durationMs-duration)<10) }
        check(listOf("LICENSE.txt","NOTICE.txt","PROVENANCE.txt").all {File(destination,it).length()>100})
        check(runCatching {worker.stems(File(models,"input.wav").path,File(models,DesktopStems.NAME),destination) {}}.isFailure)
        check(files.all {it.isFile})
        println("PASS real pinned Bandit worker, three durable duration-matched outputs, notices, existing-folder rejection")
        val cancelled=File(directory,"cancelled")
        val job=launch {worker.stems(File(models,"input.wav").path,File(models,DesktopStems.NAME),cancelled) {}}
        delay(150);job.cancelAndJoin();check(!cancelled.exists())
        println("PASS stem worker cancellation removes partial destination")
        val saved=File(models,"proof-output");check(!saved.exists());check(destination.renameTo(saved))
        println("STEMS_CHECKS_PASSED output=${saved.absolutePath}")
    } finally {directory.deleteRecursively()}
}
