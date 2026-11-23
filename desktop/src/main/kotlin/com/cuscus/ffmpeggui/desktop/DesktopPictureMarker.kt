package com.cuscus.ffmpeggui.desktop

import ai.onnxruntime.*
import com.cuscus.ffmpeggui.core.marking.*
import com.cuscus.ffmpeggui.marking.MarkerModel
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer

/** Original TrustMark computation, plain CPU kernels only. Use in disposable worker. */
class DesktopPictureMarker(private val encoder:OrtSession,private val decoder:OrtSession) {
    class NotHeld(what:String):IOException("The invisible AI mark does not hold on $what")
    suspend fun markPicture(picture: BufferedImage) {
            // Already marked and still reading well (a picture erased again: the first
            // erase marked it, and a small part changed since): laid again, the marks
            // would add up and start to show as a pattern. Left as it is.
            val before = Marks.pictureErrors(decode(picture))
            if (held(before)) {
                return
            }
            val region = PictureMarking.region(picture.width, picture.height)
            val cover = squeeze(picture, region, PictureMarking.SIZE)
            val residual = PictureMarking.residual(encode(encoder, cover), cover)
            require(residual.all {it.isFinite()}) {"Invalid mark output"}
            // Checked before the picture is touched: a mark far stronger than TrustMark's
            // (a wrong computation) would show as burnt colours, and one that does not read
            // on the 256 square will not read on the picture either.
            val strength = PictureMarking.meanMagnitude(residual)
            if (strength > PictureMarking.MOST_MEAN_MAGNITUDE) {
                throw NotHeld("this picture")
            }
            val square = decodeSquare(PictureMarking.marked(cover, residual))
            if (square != null && !held(Marks.pictureErrors(square))) {
                throw NotHeld("this picture")
            }
            val feather = PictureMarking.feather(region)
            val row = IntArray(region.width)
            val mark = FloatArray(3 * region.width)
            for (y in 0 until region.height) {
                if (y % 64 == 0) currentCoroutineContext().ensureActive()
                picture.getRGB(region.left,region.top+y,region.width,1,row,0,region.width)
                PictureMarking.residualRow(residual, y, region.width, region.height, mark)
                for (x in 0 until region.width) {
                    val p = row[x]
                    val w = PictureMarking.weight(x, y, region, feather)
                    val r = PictureMarking.apply((p shr 16) and 0xFF, mark[x * 3], w)
                    val g = PictureMarking.apply((p shr 8) and 0xFF, mark[x * 3 + 1], w)
                    val b = PictureMarking.apply(p and 0xFF, mark[x * 3 + 2], w)
                    row[x] = (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
                picture.setRGB(region.left,region.top+y,region.width,1,row,0,region.width)
            }
            val errors = Marks.pictureErrors(decode(picture))
            if (!held(errors)) {
                throw NotHeld("this picture")
            }

    }

    /** True when [errors] wrong bits (null: not found) is a mark that holds. */
    private fun held(errors: Int?): Boolean = errors != null && errors <= Marks.PICTURE_FRESH_ERRORS

    /** The decoder's bits for a square already at its size ([3, SIZE, SIZE], -1..1); null when it takes another size. */
    private fun decodeSquare(square: FloatArray): FloatArray? {
        val decoder = decoder
        val size = (decoder.inputInfo.values.first().info as? TensorInfo)?.shape?.lastOrNull()?.toInt() ?: PictureMarking.SIZE
        if (size != PictureMarking.SIZE) return null
        val env = OrtEnvironment.getEnvironment()
        val n = PictureMarking.SIZE.toLong()
        val image = OnnxTensor.createTensor(env, FloatBuffer.wrap(square), longArrayOf(1, 3, n, n))
        try {
            decoder.run(mapOf(decoder.inputNames.first() to image)).use { result ->
                val out = (result.get(0) as OnnxTensor).floatBuffer
                return FloatArray(out.remaining()).also { out.get(it) }
            }
        } finally {
            image.close()
        }
    }

    /** True when [picture] carries TrustMark's mark (Marks.PICTURE_CODEWORD). */
    suspend fun pictureMarked(picture: BufferedImage): Boolean = withContext(Dispatchers.Default) {
        Marks.pictureMarked(decode(picture))
    }

    private fun squeeze(picture: BufferedImage, region: PictureMarking.Region, size: Int): FloatArray {
        val squeezer = PictureMarking.Squeezer(region.width, region.height, size)
        val row = IntArray(region.width)
        for (y in 0 until region.height) {
            picture.getRGB(region.left,region.top+y,region.width,1,row,0,region.width)
            squeezer.add(y, row)
        }
        return squeezer.result()
    }

    private fun encode(encoder: OrtSession, cover: FloatArray): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val n = PictureMarking.SIZE.toLong()
        val image = OnnxTensor.createTensor(env, FloatBuffer.wrap(cover), longArrayOf(1, 3, n, n))
        val bits = OnnxTensor.createTensor(env, FloatBuffer.wrap(Marks.PICTURE_PAYLOAD), longArrayOf(1, Marks.PICTURE_BITS.toLong()))
        try {
            encoder.run(mapOf(name(encoder, 4, "onnx::Concat_0") to image, name(encoder, 2, "onnx::Gemm_1") to bits)).use { result ->
                val out = (result.get(0) as OnnxTensor).floatBuffer
                if (out.remaining() != cover.size) throw IOException("Unexpected output of ${out.remaining()} values")
                return FloatArray(out.remaining()).also { out.get(it) }
            }
        } finally {
            image.close()
            bits.close()
        }
    }

    private fun decode(picture: BufferedImage): FloatArray {
        val decoder = decoder
        val shape = (decoder.inputInfo.values.first().info as? TensorInfo)?.shape
        val size = shape?.lastOrNull()?.takeIf { it > 0 }?.toInt() ?: PictureMarking.SIZE
        val region = PictureMarking.region(picture.width, picture.height)
        val squeezed = squeeze(picture, region, size)
        val env = OrtEnvironment.getEnvironment()
        val image = OnnxTensor.createTensor(env, FloatBuffer.wrap(squeezed), longArrayOf(1, 3, size.toLong(), size.toLong()))
        try {
            decoder.run(mapOf(decoder.inputNames.first() to image)).use { result ->
                val out = (result.get(0) as OnnxTensor).floatBuffer
                return FloatArray(out.remaining()).also { out.get(it) }
            }
        } finally {
            image.close()
        }
    }

    private fun name(session:OrtSession,rank:Int,preferred:String):String =
        if(preferred in session.inputNames)preferred else session.inputInfo.entries.firstOrNull {(it.value.info as? TensorInfo)?.shape?.size==rank}?.key ?: error("No mark model input of rank $rank")
    companion object {
        suspend fun <T> use(directory:File,block:suspend (DesktopPictureMarker)->T):T {
            val pins=listOf(MarkerModel.ENCODER,MarkerModel.DECODER)
            pins.forEach {DesktopModelFiles.verify(File(directory,it.name),it.sha256,it.bytes)}
            DesktopSmartModels.checkAvailableMemory(700L*1024*1024)
            OrtSession.SessionOptions().use {opts->
                opts.setIntraOpNumThreads(2);opts.setInterOpNumThreads(1);opts.setCPUArenaAllocator(false);opts.setMemoryPatternOptimization(false)
                val env=OrtEnvironment.getEnvironment()
                env.createSession(File(directory,MarkerModel.ENCODER.name).path,opts).use {encoder->
                    env.createSession(File(directory,MarkerModel.DECODER.name).path,opts).use {decoder->
                        return block(DesktopPictureMarker(encoder,decoder))
                    }
                }
            }
        }
    }
}
