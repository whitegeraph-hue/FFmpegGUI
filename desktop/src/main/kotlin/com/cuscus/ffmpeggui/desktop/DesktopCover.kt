package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.nio.file.Files
import java.util.Base64

/** Small portable album-art payload; full source decoding stays in FFmpeg. */
object DesktopCover {
    suspend fun prepare(file:File,media:DesktopMedia):String {
        val source=media.probe(file.path)
        require(source.info.isStill) {"Choose a still picture for album art"}
        val directory=Files.createTempDirectory("ffmpeggui-cover-").toFile()
        try {
            val input=DesktopStillInputs.resolve(file.path,directory)
            val png=File(directory,"cover.png")
            media.process.checked(listOf("ffmpeg","-nostdin","-v","error","-y","-i",input,"-vf","scale=600:600:force_original_aspect_ratio=increase,crop=600:600,format=rgb24","-frames:v","1",png.path))
            require(png.length() in 1..1_500_000) {"Album art is too large"}
            val encoded=Base64.getEncoder().encodeToString(png.readBytes())
            decode(encoded)
            return encoded
        } finally {directory.deleteRecursively()}
    }
    fun decode(encoded:String):ByteArray {
        require(encoded.length in 1..2_000_000) {"Album art payload is too large"}
        val bytes=Base64.getDecoder().decode(encoded)
        val size=com.cuscus.ffmpeggui.core.command.CoverTags.pictureSize(bytes)
        require(bytes.size>8 && bytes[0]==0x89.toByte() && size==(600 to 600)) {"Invalid album art picture"}
        return bytes
    }
}
