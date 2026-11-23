package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.voice.WavWriter
import com.cuscus.ffmpeggui.core.marking.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun runAudioMarkChecks(path:String)=runBlocking {
    val dir=File(path);val raw=File(dir,"source.pcm")
    DesktopProcess().checked(listOf("ffmpeg","-v","error","-y","-i",File(dir,"source.wav").path,"-ac","1","-ar","16000","-f","f32le",raw.path))
    val buffer=ByteBuffer.wrap(raw.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    val source=FloatArray(buffer.remaining()).also {buffer.get(it)}
    require(source.size<=16000*30)
    DesktopAudioMarker.use(dir){marker->
        val marked=marker.markSpeech(listOf(source),16000).single()
        val verdict=marker.speech(listOf(marked),16000)
        println("FRESH_SHARE=${verdict.share} MESSAGE_MATCH=${Marks.speechMessageIsOurs(verdict.message)}")
        check(verdict.share!=null && verdict.share>=SpeechMarking.FRESH_SHARE && Marks.speechMessageIsOurs(verdict.message))
        val wav=File(dir,"marked.wav");WavWriter(wav,1,16000).use {it.write(marked,marked.size)}
        DesktopProcess().checked(listOf("ffmpeg","-v","error","-y","-i",wav.path,"-f","f32le",raw.path))
        val saved=ByteBuffer.wrap(raw.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();val samples=FloatArray(saved.remaining()).also {saved.get(it)}
        val reloaded=marker.speech(listOf(samples),16000)
        check(reloaded.marked && Marks.speechMessageIsOurs(reloaded.message))
        val clean=marker.speech(listOf(source),16000)
        println("SOURCE_SHARE=${clean.share} RELOADED_SHARE=${reloaded.share}")
    }
    raw.delete();println("PASS actual pinned AudioSeal generation, constant-message readback and saved WAV reload")
}
