package com.cuscus.ffmpeggui.desktop

import java.io.File
import kotlinx.coroutines.*
import com.cuscus.ffmpeggui.core.translate.SubtitleTranslation

fun runTranslationChecks(path:String)=runBlocking {
    val directory=File(path)
    val worker=DesktopModelWorker()
    val source="1\n00:00:01,000 --> 00:00:03,000\nHello, how are you?\n\n2\n00:00:04,000 --> 00:00:06,000\nThe cat is sleeping on the table.\n"
    DesktopTranslation.import("Xenova/opus-mt-en-fr",DesktopTranslation.files.map {File(directory,it)},worker)
    val model=DesktopTranslation.installed().first {it.repo=="Xenova/opus-mt-en-fr"}
    val texts=SubtitleTranslation.texts(source)
    val translated=worker.translate(model.directory,texts){println(it)}
    check(translated.size==2 && translated.zip(texts).all {it.first!=it.second && it.first.isNotBlank()})
    println("ACTUAL_TRANSLATION=$translated")
    val rewritten=SubtitleTranslation.rewrite(source,translated)
    check(rewritten.contains("00:00:01,000 --> 00:00:03,000") && rewritten.contains("00:00:04,000 --> 00:00:06,000"))
    val original=File(model.directory,"source.spm");val bytes=original.readBytes()
    try {original.appendText("damage");check(runCatching {worker.translate(model.directory,texts){}}.isFailure)} finally {original.writeBytes(bytes)}
    val job=launch {worker.translate(model.directory,List(500){texts[0]}){}}
    delay(150);job.cancelAndJoin()
    check(worker.translate(model.directory,listOf(texts[0])){}.isNotEmpty())
    check(runCatching {worker.translate(model.directory,listOf("a".repeat(1001))) {}}.isFailure)
    File(directory,"translated.srt").writeText(rewritten)
    println("PASS translation import, actual isolated inference, time retention, changed-file rejection, cancel/retry and oversized cue rejection")
}
