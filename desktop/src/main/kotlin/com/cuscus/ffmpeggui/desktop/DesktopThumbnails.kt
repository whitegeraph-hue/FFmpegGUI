package com.cuscus.ffmpeggui.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.LinkedHashMap
import javax.imageio.ImageIO

/** Small source thumbnails only, never a claim that effects are live. Bounded per editor session. */
class DesktopThumbnails(private val media:DesktopMedia) {
    private val gate=Mutex()
    private val cache=object:LinkedHashMap<String,ImageBitmap>(64,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,ImageBitmap>?)=size>64
    }
    suspend fun frame(source:DesktopSource,atMs:Long):ImageBitmap?=withContext(Dispatchers.IO) {
        val file=File(source.path)
        val at=if(source.info.isStill)0 else atMs.coerceIn(0,(source.info.durationMs-1).coerceAtLeast(0))
        val key="${source.path}:${file.length()}:${file.lastModified()}:$at"
        synchronized(cache){cache[key]}?.let {return@withContext it}
        gate.withLock {
        synchronized(cache){cache[key]}?.let {return@withLock it}
        val output=File.createTempFile("ffmpeggui-thumb-",".png")
        val directory=java.nio.file.Files.createTempDirectory("ffmpeggui-thumb-source-").toFile()
        try {
            val input=DesktopStillInputs.resolve(source.path,directory)
            withTimeout(10000) {media.process.checked(listOf("ffmpeg","-nostdin","-v","error","-y","-ss",(at/1000.0).toString(),"-i",input,"-threads","1","-frames:v","1","-vf","scale=160:90:force_original_aspect_ratio=decrease,pad=160:90:(ow-iw)/2:(oh-ih)/2",output.path))}
            ImageIO.read(output)?.let {image->try {image.toComposeImageBitmap().also {synchronized(cache){cache[key]=it}}}finally {image.flush()}}
        } finally {output.delete();directory.deleteRecursively()}
        }
    }
}
