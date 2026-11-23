package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.dub.*
import com.cuscus.ffmpeggui.core.provenance.*
import com.cuscus.ffmpeggui.core.marking.Marks
import kotlinx.coroutines.*
import java.io.File
import java.nio.*

fun runDubChecks(path:String)=runBlocking {
    val folder=File(path).apply {mkdirs()}
    DesktopDub.importMarkers(listOf("audioseal-generator.onnx","audioseal-detector.onnx").map {File(folder,it)})
    if(!DesktopDub.root.exists())DesktopDub.importArchive(File(folder,"kokoro.tar.bz2"))
    DesktopDub.verify(DesktopDub.root);println("PASS exact original archive import and manifest verification")
    check(runCatching {DesktopDub.requireCovered(DesktopDub.root,listOf("The voice carries generated words."))}.isFailure)
    if(System.getenv("FFMPEGGUI_DUB_WORKER_ONLY")==null) {
    val output=File(folder,"dub.wav")
    DesktopDub.generate(DesktopDub.root,DesktopDub.markers,listOf(DubTiming.Line(1000,5000,"This voice is made on this computer."),DubTiming.Line(7000,11000,"Every spoken line has an invisible artificial intelligence mark.")),0,1f,13000,output){println(it)}
    check(output.readBytes().toString(Charsets.ISO_8859_1).contains(AiDisclosure.SPEECH_TEXT))
    val raw=File(folder,"dub.pcm");DesktopProcess().checked(listOf("ffmpeg","-v","error","-y","-i",output.path,"-f","f32le",raw.path))
    val data=ByteBuffer.wrap(raw.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();val samples=FloatArray(data.remaining()).also {data.get(it)}
    check(samples.size in 24000*7..24000*13);check(samples.take(23000).all {it==0f});check(samples.any {kotlin.math.abs(it)>.05})
    DesktopAudioMarker.use(DesktopDub.markers){marker->val verdict=marker.speech(listOf(samples),24000);println("RELOADED_DUB_SHARE=${verdict.share}");check(verdict.marked && Marks.speechMessageIsOurs(verdict.message))}
    }
    val worker=DesktopModelWorker();val isolated=File(folder,"isolated.wav").apply {delete()}
    worker.dub(listOf(DubTiming.Line(0,5000,"This voice is made on this computer.")),0,1f,6000,isolated){println("worker: $it")}
    check(isolated.length()>10000)
    check(runCatching {worker.dub(listOf(DubTiming.Line(0,1000,"Hello")),0,1f,1000,isolated){}}.isFailure)
    val bad=File(folder,"unsupported.wav").apply {delete()};check(runCatching {worker.dub(listOf(DubTiming.Line(0,3000,"Zxyqword")),0,1f,4000,bad){}}.isFailure);check(!bad.exists())
    val cancelled=File(folder,"cancelled.wav").apply {delete()};val job=launch {worker.dub(listOf(DubTiming.Line(0,5000,"This voice is made on this computer.")),0,1f,6000,cancelled){}};delay(700);job.cancelAndJoin();check(!cancelled.exists())
    val retry=File(folder,"retry.wav").apply {delete()};worker.dub(listOf(DubTiming.Line(0,3000,"Hello from this computer.")),0,1f,4000,retry){};check(retry.length()>10000)
    println("PASS isolated dub, create-new refusal, unknown-word failure, cancellation and retry")
    val extra=File(DesktopDub.root,"lexicon-us-en.txt");val original=extra.readBytes();try {extra.appendText("changed");check(runCatching {DesktopDub.verify(DesktopDub.root)}.isFailure)}finally {extra.writeBytes(original)}
    check(runCatching {DesktopDub.generate(DesktopDub.root,DesktopDub.markers,listOf(DubTiming.Line(0,1000,"Hello")),999,1f,1000,File(folder,"invalid.wav")) {}}.isFailure)
    println("PASS pinned Kokoro synthesis, two timed cues, initial silence, saved WAV XMP and invisible mark, corruption, unsupported vocabulary and invalid voice refusal")
}
