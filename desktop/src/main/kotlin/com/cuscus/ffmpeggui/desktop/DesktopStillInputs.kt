package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.io.RandomAccessFile
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

/** Read JPEG EXIF orientation without decoding pixels. Original files are never changed. */
object DesktopStillInputs {
    fun isUltraHdr(file:File):Boolean = file.extension.lowercase() in setOf("jpg","jpeg") && file.length() in 1..32_000_000 && com.cuscus.ffmpeggui.core.hdr.UltraHdr.read(file.readBytes())!=null

    fun orientation(file:File):Int = runCatching {
        RandomAccessFile(file,"r").use {input->
            if(input.length()<4 || input.readUnsignedShort()!=0xffd8)return@use 1
            var scanned=0L
            while(input.filePointer+4<input.length() && scanned<1_048_576) {
                if(input.readUnsignedByte()!=255)return@use 1
                var marker=input.readUnsignedByte()
                while(marker==255)marker=input.readUnsignedByte()
                if(marker==0xda || marker==0xd9)return@use 1
                val length=input.readUnsignedShort()-2
                if(length<0 || input.filePointer+length>input.length())return@use 1
                if(marker==0xe1 && length>=14) {
                    val bytes=ByteArray(length);input.readFully(bytes)
                    if(bytes.take(6)!=listOf<Byte>(69,120,105,102,0,0))continue
                    val start=6
                    val little=bytes[start]==73.toByte() && bytes[start+1]==73.toByte()
                    if(!little && !(bytes[start]==77.toByte() && bytes[start+1]==77.toByte()))return@use 1
                    fun u16(at:Int):Int {require(at>=0 && at+2<=bytes.size);val a=bytes[at].toInt() and 255;val b=bytes[at+1].toInt() and 255;return if(little)a or (b shl 8) else (a shl 8) or b}
                    fun u32(at:Int):Long {require(at>=0 && at+4<=bytes.size);return if(little)(0..3).fold(0L){v,i->v or ((bytes[at+i].toLong() and 255) shl (i*8))} else (0..3).fold(0L){v,i->(v shl 8) or (bytes[at+i].toLong() and 255)}}
                    if(u16(start+2)!=42)return@use 1
                    val offset=u32(start+4);if(offset>length-8)return@use 1
                    val directory=start+offset.toInt();val count=u16(directory)
                    require(count<=4096)
                    for(i in 0 until count){val at=directory+2+i*12;if(u16(at)==274 && u16(at+2)==3 && u32(at+4)==1L)return@use u16(at+8).takeIf {it in 1..8} ?: 1}
                } else input.seek(input.filePointer+length)
                scanned=input.filePointer
            }
            1
        }
    }.getOrDefault(1)

    /** A scoped upright PNG input, used consistently by preview, inference and export. */
    suspend fun resolve(path:String,directory:File):String=withContext(Dispatchers.IO) {
        val file=File(path)
        val orientation=orientation(file)
        if(orientation==1 && !isUltraHdr(file))return@withContext path
        val stream=ImageIO.createImageInputStream(file)
        try {
            stream.use {input->
                val readers=ImageIO.getImageReaders(input);require(readers.hasNext()) {"Cannot decode oriented picture"}
                val reader=readers.next()
                try {
                    reader.input=input
                    val w=reader.getWidth(0);val h=reader.getHeight(0)
                    require(w.toLong()*h<=32_000_000) {"Oriented pictures are limited to 32 megapixels"}
                    val swap=orientation>=5
                    // Three bytes/pixel plus a bounded source strip, not two full four-byte rasters.
                    val output=BufferedImage(if(swap)h else w,if(swap)w else h,BufferedImage.TYPE_3BYTE_BGR)
                    try {
                        var top=0
                        val stripHeight=maxOf(1,minOf(256,1_000_000/w))
                        while(top<h) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val height=minOf(stripHeight,h-top)
                            val params=reader.defaultReadParam.apply {sourceRegion=java.awt.Rectangle(0,top,w,height)}
                            val strip=reader.read(0,params)
                            try {
                                for(sy in 0 until height)for(x in 0 until w){
                                    val y=top+sy
                                    val (dx,dy)=when(orientation){
                                        2->w-1-x to y;3->w-1-x to h-1-y;4->x to h-1-y
                                        5->y to x;6->h-1-y to x;7->h-1-y to w-1-x;8->y to w-1-x
                                        else->x to y
                                    }
                                    output.setRGB(dx,dy,strip.getRGB(x,sy))
                                }
                            } finally {strip.flush()}
                            top+=height
                        }
                        directory.mkdirs()
                        val target=File.createTempFile("upright-",".png",directory)
                        try {check(ImageIO.write(output,"png",target));target.path} catch(e:Throwable){target.delete();throw e}
                    } finally {output.flush()}
                } finally {reader.dispose()}
            }
        } catch(e:OutOfMemoryError) {
            throw java.io.IOException("Not enough memory to turn this picture upright. Try a smaller copy; the original is unchanged.",e)
        }
    }
}
