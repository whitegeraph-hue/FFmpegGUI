package com.cuscus.ffmpeggui.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.LinkedHashMap
import kotlin.math.abs

/** Original mono source envelope, not an edited mix, DSP meter or true-peak meter. */
internal data class DesktopWaveform(val durationMs:Long,val peaks:FloatArray) {
    fun peak(fromMs:Long,toMs:Long):Float {
        if(peaks.isEmpty() || durationMs<=0)return 0f
        val low=minOf(fromMs,toMs).coerceIn(0,durationMs-1)
        val high=maxOf(fromMs,toMs).coerceIn(0,durationMs-1)
        val first=(low.toDouble()*peaks.size/durationMs).toInt().coerceIn(peaks.indices)
        val last=(high.toDouble()*peaks.size/durationMs).toInt().coerceIn(peaks.indices)
        var result=0f
        for(i in first..last)result=maxOf(result,peaks[i])
        return result
    }
}

/** One decoder, 30 minute input limit, 20 second timeout, 16 small cached envelopes. */
internal class DesktopWaveforms(private val media:DesktopMedia) {
    private val gate=Mutex()
    private val cache=object:LinkedHashMap<String,DesktopWaveform>(16,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,DesktopWaveform>?)=size>16
    }
    suspend fun source(source:DesktopSource):DesktopWaveform=withContext(Dispatchers.IO) {
        require(source.info.hasAudio && source.info.durationMs in 1..1_800_000) {"Source peaks need audio up to 30 minutes."}
        val file=File(source.path)
        val key="${source.path}:${file.length()}:${file.lastModified()}:${source.info.durationMs}"
        synchronized(cache){cache[key]}?.let {return@withContext it}
        gate.withLock {
            synchronized(cache){cache[key]}?.let {return@withLock it}
            val pcm=File.createTempFile("ffmpeggui-peaks-",".pcm")
            try {
                withTimeout(20000) {media.process.checked(listOf("ffmpeg","-nostdin","-v","error","-y","-i",source.path,"-map","0:a:0","-vn","-t","1800","-ac","1","-ar","8000","-threads","1","-f","s16le",pcm.path))}
                require(pcm.length() in 2..28_800_000 && pcm.length()%2==0L) {"Source peaks could not be read within the preview limit."}
                val count=pcm.length()/2
                val peaks=FloatArray(minOf(4096L,count).toInt())
                pcm.inputStream().buffered().use {input->
                    var i=0L
                    while(i<count) {
                        val low=input.read();val high=input.read();check(low>=0 && high>=0)
                        val value=((high shl 8) or low).toShort().toInt()
                        val bin=(i*peaks.size/count).toInt().coerceAtMost(peaks.lastIndex)
                        peaks[bin]=maxOf(peaks[bin],abs(value)/32768f);i++
                    }
                }
                DesktopWaveform(source.info.durationMs,peaks).also {synchronized(cache){cache[key]=it}}
            } finally {pcm.delete()}
        }
    }
}

/** Sample the original envelope at the clip's output-clock positions, including curves/reverse. */
internal fun DesktopProject.waveformLevels(index:Int,span:DesktopClipSpan,data:DesktopWaveform?,bins:Int):FloatArray {
    val clip=timeline.clips[index]
    val local=com.cuscus.ffmpeggui.core.preview.PreviewTimeline(listOf(
        com.cuscus.ffmpeggui.core.preview.PreviewTimeline.SegmentTiming(clip.lengthMs,clip.effects.speed,clip.effects.reversed,clip.effects.speedCurve,clip.effects.keepPitch)
    ))
    val globalSpeed=clock().speed
    fun sourceAt(out:Long):Long {
        val played=if(effects.reversed)span.endMs-out else out-span.startMs
        return clip.startMs+local.mediaMsForOutput((played*globalSpeed).toLong()).coerceIn(0,(clip.lengthMs-1).coerceAtLeast(0))
    }
    return FloatArray(bins) {bin->
        val a=span.startMs+(span.endMs-span.startMs)*bin/bins
        val b=span.startMs+(span.endMs-span.startMs)*(bin+1)/bins
        data?.peak(sourceAt(a),sourceAt(b)) ?: 0f
    }
}
