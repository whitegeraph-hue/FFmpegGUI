package com.cuscus.ffmpeggui.desktop

import ai.onnxruntime.*
import com.cuscus.ffmpeggui.core.marking.*
import com.cuscus.ffmpeggui.marking.MarkerModel
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Original AudioSeal window/resample/join and constant payload. Worker use only. */
class DesktopAudioMarker(private val generator:OrtSession,private val detector:OrtSession) {
    class NotHeld(what:String):IOException("The invisible AI mark does not hold on $what")
    class SpeechVerdict(
        val share: Float?,
        val message: FloatArray,
        val markedSamples: Int = 0,
        val markedMessage: FloatArray = message,
    ) {
        /** Most of the speech carries the mark: a dub on its own. */
        val marked: Boolean get() = share != null && share >= SpeechMarking.MARKED_SHARE

        /**
         * The mark is in some of the sound, as a dub mixed with music and effects
         * is: enough of it found, with the app's own message (SpeechMarking.inMix).
         */
        val markedInMix: Boolean get() = SpeechMarking.inMix(markedSamples, markedMessage)
    }

    suspend fun markSpeech(lines: List<FloatArray>, rate: Int): List<FloatArray> {
        require(rate in 8000..48000 && lines.size in 1..100 && lines.sumOf {it.size.toLong()}<=rate.toLong()*60 && lines.all {line->line.all {it.isFinite()}}) {"Speech marking is limited to 60 seconds of finite mono samples"}
        return run {
            val generator = generator
            val marked = lines.map { line ->
                currentCoroutineContext().ensureActive()
                if (line.isEmpty()) return@map line
                val at16 = Resampler.resample(line, rate, SpeechMarking.RATE)
                val windows = SpeechMarking.markWindows(at16.size)
                val marks = windows.map { window -> watermark(generator, SpeechMarking.padded(at16, window)) }
                val joined = SpeechMarking.join(at16.size, windows, marks)
                val back = Resampler.resample(joined, SpeechMarking.RATE, rate, line.size)
                FloatArray(line.size) { i -> (line[i] + back[i]).coerceIn(-1f, 1f) }
            }
            val verdict = detect(detector, marked, rate)
            // Too little speech to tell (a dub of one short word) is marked all the same.
            if (verdict.share != null && (verdict.share < SpeechMarking.FRESH_SHARE || !Marks.speechMessageIsOurs(verdict.message))) {
                throw NotHeld("this voice")
            }
            marked
        }
    }

    /**
     * AudioSeal's watermark for [audio] (mono, at SpeechMarking.RATE): the
     * mark to add to it, over every part that is not digital silence, nothing
     * where it is. For a whole soundtrack (see SoundSealer), checked by
     * whoever wants to: a mix of music and voices may hold it less than a dub.
     */
    /** How much of [lines] (at [rate]) carries the speech mark. */
    suspend fun speech(lines: List<FloatArray>, rate: Int): SpeechVerdict = withContext(Dispatchers.Default) {
        detect(detector, lines, rate)
    }

    private fun watermark(generator: OrtSession, window: FloatArray): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val audio = OnnxTensor.createTensor(env, FloatBuffer.wrap(window), longArrayOf(1, 1, window.size.toLong()))
        val message = OnnxTensor.createTensor(env, LongBuffer.wrap(Marks.SPEECH_MESSAGE), longArrayOf(1, Marks.SPEECH_BITS.toLong()))
        try {
            generator.run(mapOf(name(generator, 3, "audio") to audio, name(generator, 2, "message") to message)).use { result ->
                val out = (result.get(0) as OnnxTensor).floatBuffer
                require(out.remaining()==window.size) {"Unexpected audio watermark shape"}
                return FloatArray(out.remaining()).also { out.get(it);require(it.all {v->v.isFinite()}) }
            }
        } finally {
            audio.close()
            message.close()
        }
    }

    private suspend fun detect(detector: OrtSession, lines: List<FloatArray>, rate: Int): SpeechVerdict {
        val env = OrtEnvironment.getEnvironment()
        var speech = 0
        var marked = 0
        val messages = ArrayList<FloatArray>()
        val weights = ArrayList<Int>()
        val markedWeights = ArrayList<Int>()
        for (line in lines) {
            if (line.isEmpty()) continue
            val at16 = Resampler.resample(line, rate, SpeechMarking.RATE)
            val voiced = SpeechMarking.voiced(at16)
            for (window in SpeechMarking.checkWindows(at16.size)) {
                currentCoroutineContext().ensureActive()
                val audio = OnnxTensor.createTensor(env, FloatBuffer.wrap(SpeechMarking.padded(at16, window)), longArrayOf(1, 1, SpeechMarking.WINDOW.toLong()))
                try {
                    detector.run(mapOf(detector.inputNames.first() to audio)).use { result ->
                        val detection = (result.get(0) as OnnxTensor).floatBuffer
                        val message = (result.get(1) as OnnxTensor).floatBuffer
                        // [1, 2, WINDOW]: the second row is "marked".
                        var here = 0
                        var hereMarked = 0
                        for (j in 0 until window.length) {
                            if (!voiced[window.start + j]) continue
                            here++
                            if (detection.get(SpeechMarking.WINDOW + j) > SpeechMarking.DETECTED) hereMarked++
                        }
                        speech += here
                        marked += hereMarked
                        messages += FloatArray(Marks.SPEECH_BITS) { message.get(it) }
                        weights += here
                        markedWeights += hereMarked
                    }
                } finally {
                    audio.close()
                }
            }
        }
        val share = if (speech < SpeechMarking.LEAST_VOICED) null else marked.toFloat() / speech
        return SpeechVerdict(
            share = share,
            message = SpeechMarking.message(messages, weights),
            markedSamples = marked,
            markedMessage = SpeechMarking.message(messages, markedWeights),
        )
    }

    private fun name(session:OrtSession,rank:Int,preferred:String):String =
        if(preferred in session.inputNames)preferred else session.inputInfo.entries.firstOrNull {(it.value.info as? TensorInfo)?.shape?.size==rank}?.key ?: error("No mark model input of rank $rank")
    companion object {
        suspend fun <T> use(directory:File,block:suspend (DesktopAudioMarker)->T):T {
            val pins=listOf(MarkerModel.GENERATOR,MarkerModel.DETECTOR)
            pins.forEach {DesktopModelFiles.verify(File(directory,it.name),it.sha256,it.bytes)}
            DesktopSmartModels.checkAvailableMemory(900L*1024*1024)
            OrtSession.SessionOptions().use {opts->
                opts.setIntraOpNumThreads(2);opts.setInterOpNumThreads(1);opts.setCPUArenaAllocator(false);opts.setMemoryPatternOptimization(false)
                val env=OrtEnvironment.getEnvironment()
                env.createSession(File(directory,MarkerModel.GENERATOR.name).path,opts).use {generator->
                    env.createSession(File(directory,MarkerModel.DETECTOR.name).path,opts).use {detector->
                        return block(DesktopAudioMarker(generator,detector))
                    }
                }
            }
        }
    }
}
