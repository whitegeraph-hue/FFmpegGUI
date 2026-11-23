package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.nio.FloatBuffer
import ai.onnxruntime.*
import kotlinx.coroutines.*
import com.cuscus.ffmpeggui.core.stems.*
import com.cuscus.ffmpeggui.core.voice.*

/** Original pinned scene separation, run only inside the disposable native worker. */
object DesktopStems {
    const val NAME="bandit-v2-multi-int8.onnx"
    const val HASH="3a4a054b8e07c553d5f19f9f72ad52a1d5049c6355e0512f33b9e2f5ae7579a9"
    const val BYTES=40_260_047L
    const val URL="https://github.com/marcomorosi06/FFmpegGUI/releases/tag/bandit-int8-onnx-1"
    val names=listOf("dialogue.wav","music.wav","effects.wav")
    suspend fun separate(path:String,model:File,directory:File,progress:(String)->Unit):List<File> = withContext(Dispatchers.IO) {
        DesktopModelFiles.verify(model,HASH,BYTES)
        DesktopSmartModels.checkAvailableMemory(1536L*1024*1024)
        val media=DesktopMedia()
        val source=media.probe(path)
        require(source.info.hasAudio && source.info.durationMs in 1..600_000) {"Scene separation needs audio up to ten minutes"}
        require(directory.usableSpace>source.info.durationMs*1400+100_000_000) {"Not enough temporary disk space for scene stems"}
        val pcm=File(directory,"stems-input.pcm")
        progress("Extracting stereo audio")
        media.process.checked(listOf("ffmpeg","-nostdin","-v","error","-y","-i",path,"-map","0:a:0","-vn","-ac","2","-ar","48000","-f","s16le",pcm.path))
        val outputs=names.map {File(directory,it)}
        try {
            val env=OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use {options->
                options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1)
                options.setCPUArenaAllocator(false);options.setMemoryPatternOptimization(false)
                progress("Loading pinned Bandit int8 model")
                env.createSession(model.absolutePath,options).use {session->
                    val network=BanditMasks {spectrum,frames->
                        val shape=longArrayOf(1,1025,frames.toLong(),2)
                        OnnxTensor.createTensor(env,FloatBuffer.wrap(spectrum),shape).use {input->
                            session.run(mapOf(session.inputNames.first() to input)).use {result->
                                val tensor=result[0] as OnnxTensor
                                if(tensor.info.type==OnnxJavaType.DOUBLE) {
                                    val buffer=tensor.doubleBuffer;FloatArray(buffer.remaining()) {buffer.get().toFloat()}
                                } else {
                                    val buffer=tensor.floatBuffer;FloatArray(buffer.remaining()).also {buffer.get(it)}
                                }
                            }
                        }
                    }
                    val context=currentCoroutineContext()
                    PcmStereoFile(pcm).use {audio->
                        val writers=outputs.map {WavWriter(it,2,48000)}
                        try {StemSeparation(BanditMasks.LAYOUT,network).separate(audio,{stems,count->stems.indices.forEach {writers[it].write(stems[it],count)}},{progress("Separating scene ${(it*100).toInt()}%")},{context.ensureActive()})}
                        finally {writers.forEach {it.close()}}
                    }
                }
            }
            require(outputs.all {it.length()>44}) {"No stem audio was produced"}
            outputs
        } finally {pcm.delete()}
    }
}
