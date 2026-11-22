package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking

object DesktopStillChecks {
    @JvmStatic fun main(args:Array<String>)=runBlocking {
        val folder=File(args[0]).apply {mkdirs()}
        val raw=File(folder,"large-raw.jpg")
        val image=BufferedImage(6000,4000,BufferedImage.TYPE_3BYTE_BGR)
        val g=image.createGraphics()
        try {g.color=java.awt.Color.RED;g.fillRect(0,0,3000,2000);g.color=java.awt.Color.BLUE;g.fillRect(0,2000,3000,2000);g.color=java.awt.Color.GREEN;g.fillRect(3000,0,3000,4000);check(ImageIO.write(image,"jpg",raw))}finally{g.dispose();image.flush()}
        val bytes=raw.readBytes()
        val payload=byteArrayOf(69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0)
        val source=File(folder,"large-exif6.jpg")
        source.writeBytes(bytes.take(2).toByteArray()+byteArrayOf(0xff.toByte(),0xe1.toByte(),0,(payload.size+2).toByte())+payload+bytes.drop(2).toByteArray())
        val before=java.security.MessageDigest.getInstance("SHA-256").digest(source.readBytes())
        // Reserve ordinary editor headroom while testing with the actual launcher heap limit.
        val reserve=ByteArray(32*1024*1024) {7}
        val output=File(DesktopStillInputs.resolve(source.path,File(folder,"upright")))
        val stream=ImageIO.createImageInputStream(output)
        stream.use {input->val reader=ImageIO.getImageReaders(input).next();try {reader.input=input;check(reader.getWidth(0)==4000 && reader.getHeight(0)==6000);val params=reader.defaultReadParam.apply {sourceRegion=java.awt.Rectangle(50,50,10,10)};val corner=reader.read(0,params);check((corner.getRGB(0,0) and 255)>230)}finally{reader.dispose()}}
        check(reserve[reserve.lastIndex]==7.toByte())
        check(before.contentEquals(java.security.MessageDigest.getInstance("SHA-256").digest(source.readBytes())))
        println("PASS 24MP EXIF6 bounded strip normalization with32MiB reserved; maxHeap=${Runtime.getRuntime().maxMemory()}; upright4000x6000; blue corner; original unchanged")
    }
}
