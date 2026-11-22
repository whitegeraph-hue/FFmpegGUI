package com.cuscus.ffmpeggui.media

import android.content.Context
import android.net.Uri
import com.cuscus.ffmpeggui.core.editing.BeatTrack
import com.cuscus.ffmpeggui.core.editing.SceneDetect
import com.cuscus.ffmpeggui.core.editing.SilenceDetect
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * The FFmpeg runs behind the editor's smart editing tools: silences, scene
 * changes and beats of a file, read from FFmpeg's log or from the sound it
 * writes. The reading itself lives in core (SilenceDetect, SceneDetect,
 * BeatTrack), tested there; this is the Android adapter that runs FFmpeg.
 */
class FfmpegEditAnalysis(private val context: Context, private val executor: FfmpegExecutor) {

    private val extractor by lazy { FfmpegAudioExtractor(context, executor) }

    /**
     * The quiet stretches of [sourceUri] ([durationMs] long, 0 when unknown),
     * as (start, end) in milliseconds on the file's clock; null when the file
     * cannot be read. [noiseDb] and [minSeconds] say what counts as quiet.
     */
    suspend fun silencesOf(
        sourceUri: String,
        durationMs: Long,
        noiseDb: Int = SilenceDetect.DEFAULT_NOISE_DB,
        minSeconds: Double = SilenceDetect.DEFAULT_MIN_SECONDS,
    ): List<Pair<Long, Long>>? {
        val input = ffmpegReadablePath(context, Uri.parse(sourceUri)) ?: return null
        val log = StringBuilder()
        val result = executor.execute(SilenceDetect.command(input, noiseDb, minSeconds)) { log.append(it).append('\n') }
        return if (result is ExecutionResult.Failed) null else SilenceDetect.parse(log.toString(), durationMs)
    }

    /**
     * Where the scenes of [sourceUri] change ([threshold] 0..1, lower finds
     * more), in milliseconds on the file's clock; null when unreadable.
     */
    suspend fun sceneChangesOf(sourceUri: String, threshold: Double = SceneDetect.DEFAULT_THRESHOLD): List<Long>? {
        val input = ffmpegReadablePath(context, Uri.parse(sourceUri)) ?: return null
        val log = StringBuilder()
        val result = executor.execute(SceneDetect.command(input, threshold)) { log.append(it).append('\n') }
        return if (result is ExecutionResult.Failed) null else SceneDetect.splitPoints(SceneDetect.parseShowinfo(log.toString()))
    }

    /**
     * The beats of [sourceUri]'s sound ([durationMs] long, 0 when unknown),
     * found from the sound itself (see core's BeatTrack); null when the file
     * has no readable sound. [onProgress] follows the sound's extraction.
     */
    suspend fun beatsOf(sourceUri: String, durationMs: Long, onProgress: (Float) -> Unit = {}): BeatTrack.Result? {
        val pcm = File(context.cacheDir, "beats-" + UUID.randomUUID().toString() + ".pcm")
        return try {
            when (extractor.extract(sourceUri, pcm, durationMs, onProgress = onProgress)) {
                ExecutionResult.Success -> {
                    val bytes = pcm.readBytes()
                    val count = bytes.size / FfmpegAudioExtractor.BYTES_PER_SAMPLE
                    if (count <= 0) return null
                    val samples = FloatArray(count)
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples)
                    BeatTrack.analyze(samples, FfmpegAudioExtractor.SAMPLE_RATE, durationMs)
                }
                else -> null
            }
        } finally {
            pcm.delete()
        }
    }
}
