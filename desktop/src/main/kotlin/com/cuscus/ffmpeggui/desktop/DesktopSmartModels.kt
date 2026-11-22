package com.cuscus.ffmpeggui.desktop

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.cuscus.ffmpeggui.core.cutout.SubjectModelIo
import com.cuscus.ffmpeggui.core.editing.SubjectPoint
import com.cuscus.ffmpeggui.core.subtitles.RecognizedWords
import com.cuscus.ffmpeggui.core.subtitles.ListenedSound
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.cutout.CutoutModel
import com.cuscus.ffmpeggui.speech.SpeechModel
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/** No networking. Uses the original app's unchanged model pin definitions. */
object DesktopModelFiles {
    suspend fun verify(file: File, sha256: String, bytes: Long? = null) = withContext(Dispatchers.IO) {
        if (!file.isFile || (bytes != null && file.length() != bytes))
            throw IOException("Missing or wrong-size model file: ${file.name}")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (actual != sha256) throw IOException("SHA-256 mismatch: ${file.name}")
    }

    /** Stage and verify a private copy before atomic install. Never alters the source. */
    suspend fun importFile(source: File, target: File, sha256: String, bytes: Long? = null) =
        withContext(Dispatchers.IO) {
            target.absoluteFile.parentFile.mkdirs()
            val stage = Files.createTempFile(target.absoluteFile.parentFile.toPath(), ".model-", ".tmp").toFile()
            try {
                source.inputStream().buffered().use { input ->
                    stage.outputStream().buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                    }
                }
                verify(stage, sha256, bytes)
                currentCoroutineContext().ensureActive()
                Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { stage.delete() }
        }

    suspend fun speech(directory: File) {
        for (pin in SpeechModel.PARAKEET.files) verify(File(directory, pin.name), pin.sha256, pin.bytes)
    }

    fun cutout(file: File): CutoutModel = listOf(CutoutModel.FAST, CutoutModel.PRECISE, CutoutModel.FINEST)
        .firstOrNull { it.fileName == file.name }
        ?: throw IOException("Choose an original supported cutout model filename")
}

/** Shared across these adapters: never keep speech and segmentation loaded together. */
private val desktopNativeModelGate = Mutex()

/**
 * Native Linux CPU adapters. Runtime binaries and model files are supplied by the app,
 * not downloaded here. Keep model directories private and immutable during inference.
 */
class DesktopSmartModels(
    private val ffmpeg: String = "ffmpeg",
    private val cacheDirectory: File = File(System.getProperty("java.io.tmpdir"), "ffmpeggui-native"),
    private val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4),
) {
    enum class SpeechStep { EXTRACTING, LOADING, LISTENING }

    suspend fun transcribe(path: String, modelDirectory: File): List<SpokenWord> =
        transcribe(path, modelDirectory, emptyList(), { _, _ -> }, {})

    /** Voice stems, when supplied, are mixed first, preserving the Android voice-first path. */
    suspend fun transcribe(
        path: String,
        modelDirectory: File,
        voiceStems: List<File>,
        progress: (SpeechStep, Float?) -> Unit,
        onWords: (List<SpokenWord>) -> Unit,
    ): List<SpokenWord> = withContext(Dispatchers.IO) {
        desktopNativeModelGate.withLock {
            DesktopModelFiles.speech(modelDirectory)
            checkAvailableMemory(700L * 1024 * 1024)
            cacheDirectory.mkdirs()
            val pcm = Files.createTempFile(cacheDirectory.toPath(), "speech-", ".f32").toFile()
            try {
                progress(SpeechStep.EXTRACTING, null)
                val inputs = if (voiceStems.isEmpty()) listOf(File(path).absolutePath) else voiceStems.map { it.absolutePath }
                runFfmpeg(ListenedSound.command(inputs, pcm.absolutePath).arguments)
                val total = pcm.length() / 4
                if (total <= 0) throw IOException("No sound to listen to")
                progress(SpeechStep.LOADING, null)
                val config = OfflineRecognizerConfig.builder()
                    .setFeatureConfig(FeatureConfig.builder().setSampleRate(16000).setFeatureDim(80).build())
                    .setOfflineModelConfig(OfflineModelConfig.builder()
                        .setTransducer(OfflineTransducerModelConfig.builder()
                            .setEncoder(File(modelDirectory, "encoder.int8.onnx").absolutePath)
                            .setDecoder(File(modelDirectory, "decoder.int8.onnx").absolutePath)
                            .setJoiner(File(modelDirectory, "joiner.int8.onnx").absolutePath).build())
                        .setTokens(File(modelDirectory, "tokens.txt").absolutePath)
                        .setNumThreads(threads).setDebug(false).setProvider("cpu").setModelType("nemo_transducer").build())
                    .setDecodingMethod("greedy_search").build()
                val model = OfflineRecognizer(config)
                try {
                    val words = mutableListOf<SpokenWord>()
                    RandomAccessFile(pcm, "r").use { file ->
                        val chunk = 30 * 16000
                        val bytes = ByteArray(chunk * 4)
                        var start = 0L
                        while (start < total) {
                            currentCoroutineContext().ensureActive()
                            val count = minOf(chunk.toLong(), total - start).toInt()
                            file.seek(start * 4)
                            file.readFully(bytes, 0, count * 4)
                            val samples = FloatArray(count)
                            ByteBuffer.wrap(bytes, 0, count * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples)
                            val length = if (start + count >= total) count else
                                RecognizedWords.quietestCut(samples, count - 2 * 16000, count, 320).coerceAtLeast(16000)
                            val stream = model.createStream()
                            val found = try {
                                stream.acceptWaveform(if (length == count) samples else samples.copyOf(length), 16000)
                                model.decode(stream)
                                currentCoroutineContext().ensureActive()
                                val result = model.getResult(stream)
                                RecognizedWords.fromTokens(result.tokens.toList(), result.timestamps, result.durations,
                                    start * 1000 / 16000, (start + length) * 1000 / 16000)
                            } finally { stream.release() }
                            words += found
                            if (found.isNotEmpty()) onWords(found)
                            start += length
                            progress(SpeechStep.LISTENING, start.toFloat() / total)
                        }
                    }
                    words
                } finally { model.release() }
            } finally { pcm.delete() }
        }
    }

    data class SubjectTrack(val points: List<SubjectPoint>, val frameWidth: Int, val frameHeight: Int)

    suspend fun trackSubject(path: String, durationMs: Long, modelFile: File, progress: (Float) -> Unit): List<SubjectPoint> =
        track(path, durationMs, modelFile, progress = progress).points

    /** Rich form retains the rotated display frame size needed by FollowCropTrack. */
    suspend fun track(
        path: String,
        durationMs: Long,
        modelFile: File,
        everyMs: Long = 500L,
        progress: (Float) -> Unit = {},
    ): SubjectTrack = withContext(Dispatchers.IO) {
        require(durationMs > 0 && everyMs > 0)
        desktopNativeModelGate.withLock {
            val contract = DesktopModelFiles.cutout(modelFile)
            DesktopModelFiles.verify(modelFile, contract.sha256, contract.bytes)
            checkAvailableMemory(contract.minFreeBytes)
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            try {
                options.setIntraOpNumThreads(threads)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                if (contract.lean) options.setCPUArenaAllocator(false)
                env.createSession(modelFile.absolutePath, options).use { session ->
                    val inputName = session.inputNames.firstOrNull() ?: throw IOException("The model has no input")
                    val points = mutableListOf<SubjectPoint>()
                    var width = 0
                    var height = 0
                    val total = (durationMs - 1) / everyMs + 1
                    var at = 0L
                    var done = 0L
                    while (at < durationMs) {
                        currentCoroutineContext().ensureActive()
                        val frame = frame(path, at)
                        if (width == 0) { width = frame.width; height = frame.height }
                        val side = contract.net.side
                        val scaled = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
                        val graphics = scaled.createGraphics()
                        try {
                            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                            graphics.drawImage(frame, 0, 0, side, side, null)
                        } finally { graphics.dispose(); frame.flush() }
                        val input = SubjectModelIo.input(scaled.getRGB(0, 0, side, side, null, 0, side), contract.net)
                        scaled.flush()
                        val mask = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, side.toLong(), side.toLong())).use { tensor ->
                            session.run(mapOf(inputName to tensor)).use { result ->
                                val values = (result.get(0) as OnnxTensor).floatBuffer
                                if (values.remaining() != side * side) throw IOException("Unexpected cutout output shape")
                                SubjectModelIo.mask(FloatArray(values.remaining()).also { values.get(it) }, contract.net)
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        points += centroid(mask, side, at)
                        done++
                        progress(done.toFloat() / total)
                        at += minOf(everyMs, durationMs - at)
                    }
                    SubjectTrack(points, width, height)
                }
            } finally { options.close() }
        }
    }

    /** Still-picture subject alpha in original upright pixel geometry, not a generated picture. */
    suspend fun cutout(path:String,modelFile:File):BufferedImage = withContext(Dispatchers.IO) {
        desktopNativeModelGate.withLock {
            val contract=DesktopModelFiles.cutout(modelFile)
            DesktopModelFiles.verify(modelFile,contract.sha256,contract.bytes)
            checkAvailableMemory(contract.minFreeBytes)
            val picture=frame(path,0)
            try {
                require(picture.width.toLong()*picture.height<=32_000_000) {"Cutout supports pictures up to 32 megapixels."}
                val side=contract.net.side
                val scaled=BufferedImage(side,side,BufferedImage.TYPE_INT_RGB)
                val g=scaled.createGraphics()
                try {g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(picture,0,0,side,side,null)} finally {g.dispose()}
                val input=SubjectModelIo.input(scaled.getRGB(0,0,side,side,null,0,side),contract.net);scaled.flush()
                val options=OrtSession.SessionOptions()
                try {
                    options.setIntraOpNumThreads(threads);options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    if(contract.lean)options.setCPUArenaAllocator(false)
                    val env=OrtEnvironment.getEnvironment()
                    val mask=env.createSession(modelFile.absolutePath,options).use {session->
                        OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,3,side.toLong(),side.toLong())).use {tensor->
                            session.run(mapOf(session.inputNames.first() to tensor)).use {result->
                                val values=(result.get(0) as OnnxTensor).floatBuffer
                                require(values.remaining()==side*side) {"Unexpected subject output shape"}
                                SubjectModelIo.mask(FloatArray(values.remaining()).also {values.get(it)},contract.net)
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    val small=BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB)
                    small.setRGB(0,0,side,side,IntArray(mask.size) {(mask[it] shl 24) or 0xffffff},0,side)
                    val output=BufferedImage(picture.width,picture.height,BufferedImage.TYPE_INT_ARGB)
                    val graphics=output.createGraphics()
                    try {graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(small,0,0,output.width,output.height,null)} finally {graphics.dispose();small.flush()}
                    output
                } finally {options.close()}
            } finally {picture.flush()}
        }
    }

    private suspend fun frame(path: String, atMs: Long): BufferedImage {
        cacheDirectory.mkdirs()
        val image = Files.createTempFile(cacheDirectory.toPath(), "subject-", ".png").toFile()
        try {
            runFfmpeg(listOf("-y", "-ss", "%.3f".format(java.util.Locale.ROOT, atMs / 1000.0),
                "-i", File(path).absolutePath, "-frames:v", "1", image.absolutePath))
            return ImageIO.read(image) ?: throw IOException("No frame at $atMs ms")
        } finally { image.delete() }
    }

    private suspend fun runFfmpeg(args: List<String>) {
        val log = Files.createTempFile(cacheDirectory.toPath(), "ffmpeg-", ".log").toFile()
        try {
            val process = ProcessBuilder(listOf(ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error") + args)
                .redirectErrorStream(true).redirectOutput(log).start()
            try {
                while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                    delay(10)
                }
                if (process.exitValue() != 0) throw IOException("FFmpeg failed (${process.exitValue()}): ${log.readText().takeLast(2000)}")
            } finally {
                if (process.isAlive) {
                    process.destroy()
                    if (!process.waitFor(200, TimeUnit.MILLISECONDS)) process.destroyForcibly().waitFor()
                }
            }
        } finally { log.delete() }
    }

    companion object {
        internal fun centroid(mask: IntArray, side: Int, atMs: Long): SubjectPoint {
            require(side > 0 && mask.size == side * side)
            var weight = 0L
            var x = 0L
            var y = 0L
            for (i in mask.indices) {
                val v = mask[i].toLong()
                weight += v
                x += (i % side) * v
                y += (i / side) * v
            }
            if (weight <= 0) return SubjectPoint(atMs, .5f, .5f, 0f)
            return SubjectPoint(atMs, (x.toDouble() / weight / side).toFloat().coerceIn(0f, 1f),
                (y.toDouble() / weight / side).toFloat().coerceIn(0f, 1f),
                (weight.toDouble() / (side.toLong() * side * 255)).toFloat().coerceIn(0f, 1f))
        }

        private fun checkAvailableMemory(minimum: Long) {
            // Native models allocate outside the Java heap. Respect the host and cgroup limit.
            val host = runCatching {
                File("/proc/meminfo").useLines { lines ->
                    lines.first { it.startsWith("MemAvailable:") }.split(Regex("\\s+"))[1].toLong() * 1024
                }
            }.getOrNull()
            val cgroup = runCatching {
                File("/sys/fs/cgroup/memory.max").readText().trim().toLong() -
                    File("/sys/fs/cgroup/memory.current").readText().trim().toLong()
            }.getOrNull()
            val available = listOfNotNull(host, cgroup).minOrNull()
            if (available != null && available < minimum) throw IOException("Not enough free memory for model: $available bytes available")
        }
    }
}
