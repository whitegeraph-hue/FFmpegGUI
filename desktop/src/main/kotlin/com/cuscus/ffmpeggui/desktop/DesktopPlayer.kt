package com.cuscus.ffmpeggui.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.LineUnavailableException

/** Integrated preview of an actual rendered edit. Video clock follows played audio samples. */
class DesktopPlayer(private val scope:CoroutineScope,private val media:DesktopMedia,private val openAudio:(AudioFormat)->SourceDataLine={format->AudioSystem.getSourceDataLine(format)}) {
    var frame by mutableStateOf<ImageBitmap?>(null)
    var playing by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    var failure by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
        private set
    private var job:Job?=null
    private var audioLine:SourceDataLine?=null
    private var videoProcess:Process?=null
    private var audioProcess:Process?=null
    private var source by mutableStateOf<File?>(null)
    val engaged:Boolean get()=source!=null
    suspend fun cue(file:File,at:Long) {
        stop()
        source=file
        val info=media.probe(file.path).info
        durationMs=info.durationMs
        positionMs=at.coerceIn(0,(durationMs-1).coerceAtLeast(0))
        if(info.isVideo)frame=media.frame(file.path,positionMs)?.toComposeImageBitmap()
    }
    fun play(file:File,at:Long=positionMs) {
        val previous=job
        stop(false)
        source=file;failure=null;notice=null;playing=true
        job=scope.launch {
            previous?.join()
            playing=true
            try {
                val info=media.probe(file.path).info
                durationMs=info.durationMs
                val start=at.coerceIn(0,(durationMs-1).coerceAtLeast(0))
                positionMs=start
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        val logDirectory=File(System.getProperty("java.io.tmpdir"),"ffmpeggui-preview").apply { mkdirs() }
                        val audioLog=File.createTempFile("audio-",".log",logDirectory)
                        val videoLog=File.createTempFile("video-",".log",logDirectory)
                        var line:SourceDataLine?=null
                        val tailStarted=AtomicLong(0L)
                        var began=0L
                        try {
                            if(info.hasAudio) {
                                val format=AudioFormat(48000f,16,2,true,false)
                                try {
                                    line=openAudio(format)
                                    line!!.open(format,48000);line!!.start()
                                } catch(e:Exception) {
                                    if(e !is LineUnavailableException && e !is IllegalArgumentException && e !is SecurityException)throw e
                                    runCatching {line?.close()};line=null
                                    if(!info.isVideo)throw e
                                    withContext(Dispatchers.Main) {notice="Playing video silently: audio output is unavailable. Exported audio is unchanged."}
                                }
                                audioLine=line
                                if(line!=null) {
                                audioProcess=ProcessBuilder("ffmpeg","-nostdin","-v","error","-ss",(start/1000.0).toString(),"-i",file.path,"-vn","-ac","2","-ar","48000","-f","s16le","-")
                                    .redirectError(audioLog).start()
                                audioProcess!!.outputStream.close()
                                }
                            }
                            val actualLine=line
                            val audioJob=if(actualLine!=null) launch(Dispatchers.IO) {
                                audioProcess!!.inputStream.use { input ->
                                    val bytes=ByteArray(8192)
                                    while(true) {
                                        ensureActive()
                                        val count=input.read(bytes)
                                        if(count<0) break
                                        var offset=0
                                        while(offset<count) { ensureActive();offset+=actualLine.write(bytes,offset,count-offset) }
                                    }
                                }
                                actualLine.drain()
                                tailStarted.set(System.nanoTime())
                                check(audioProcess!!.waitFor()==0) { audioLog.readText().takeLast(2000) }
                            } else null
                            began=System.nanoTime()
                            fun elapsed():Long {
                                val tail=tailStarted.get()
                                return if(actualLine!=null) actualLine.microsecondPosition/1000 + if(tail>0) (System.nanoTime()-tail)/1_000_000 else 0L
                                else (System.nanoTime()-began)/1_000_000
                            }
                            if(info.isVideo) {
                                val width=640
                                val height=(info.height.toDouble()/info.width.coerceAtLeast(1)*width).toInt().coerceIn(2,1920)
                                videoProcess=ProcessBuilder("ffmpeg","-nostdin","-v","error","-ss",(start/1000.0).toString(),"-i",file.path,"-an","-vf","fps=25,scale=$width:$height","-pix_fmt","rgb24","-f","rawvideo","-")
                                    .redirectError(videoLog).start()
                                videoProcess!!.outputStream.close()
                                var index=0L
                                DataInputStream(videoProcess!!.inputStream).use { input ->
                                    val bytes=ByteArray(width*height*3)
                                    while(true) {
                                        ensureActive()
                                        try { input.readFully(bytes) } catch(_:EOFException) { break }
                                        val due=index*40
                                        while(elapsed()<due) { ensureActive();delay((due-elapsed()).coerceAtMost(15).coerceAtLeast(1)) }
                                        if(elapsed()-due<160) {
                                            val pixels=IntArray(width*height) { i -> (0xff shl 24) or ((bytes[i*3].toInt() and 255) shl 16) or ((bytes[i*3+1].toInt() and 255) shl 8) or (bytes[i*3+2].toInt() and 255) }
                                            val bitmap=BufferedImage(width,height,BufferedImage.TYPE_INT_RGB).also { it.setRGB(0,0,width,height,pixels,0,width) }.toComposeImageBitmap()
                                            withContext(Dispatchers.Main) { frame=bitmap;positionMs=(start+elapsed()).coerceAtMost(durationMs) }
                                        }
                                        index++
                                    }
                                }
                                check(videoProcess!!.waitFor()==0) { videoLog.readText().takeLast(2000) }
                            } else {
                                while(audioJob?.isActive==true) { withContext(Dispatchers.Main) { positionMs=(start+elapsed()).coerceAtMost(durationMs) };delay(50) }
                            }
                            audioJob?.join()
                        } finally {
                            line?.stop();line?.flush();line?.close();audioLine=null
                            listOfNotNull(videoProcess,audioProcess).forEach { process -> if(process.isAlive) { process.destroy();if(!process.waitFor(1,TimeUnit.SECONDS))process.destroyForcibly() } }
                            videoProcess=null;audioProcess=null
                            audioLog.delete();videoLog.delete()
                        }
                    }
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { failure="Preview could not play: ${e.message}. Your render is unchanged." }
            finally { playing=false }
        }
    }
    fun pause()=stop(false)
    fun clearFailure() { failure=null }
    fun seek(at:Long) { val file=source ?: return;positionMs=at.coerceIn(0,durationMs);play(file,positionMs) }
    fun stop(reset:Boolean=true) {
        audioLine?.stop();audioLine?.flush();audioLine?.close();audioLine=null
        listOfNotNull(videoProcess,audioProcess).forEach { it.destroy() }
        job?.cancel();job=null;playing=false
        if(reset) { positionMs=0;frame=null;source=null;notice=null }
    }
}
