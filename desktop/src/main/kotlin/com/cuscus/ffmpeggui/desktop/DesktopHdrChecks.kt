package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.core.hdr.UltraHdr

/** Real upper-limit output, measured in a separate launcher-sized heap with editor reserve. */
object DesktopHdrChecks {
    @JvmStatic fun main(args:Array<String>)=runBlocking {
        val folder=File(args[0]).apply {mkdirs()}
        val start=System.nanoTime()
        val image=BufferedImage(4000,2000,BufferedImage.TYPE_3BYTE_BGR)
        val g=image.createGraphics()
        try {
            g.paint=java.awt.GradientPaint(0f,0f,java.awt.Color.DARK_GRAY,4000f,2000f,java.awt.Color.WHITE)
            g.fillRect(0,0,4000,2000)
            check(ImageIO.write(image,"png",File(folder,"final.png")))
            check(ImageIO.write(image,"jpg",File(folder,"primary.jpg")))
        } finally {g.dispose();image.flush()}
        File(folder,"final.png").copyTo(File(folder,"plain.png"),overwrite=true)
        val reserve=ByteArray(32*1024*1024) {3}
        check(DesktopHdr.assemble(File(folder,"primary.jpg"),File(folder,"final.png"),File(folder,"plain.png"),ImageHdr()))
        check(UltraHdr.read(File(folder,"primary.jpg").readBytes())!=null)
        check(reserve[reserve.lastIndex]==3.toByte())
        println("PASS 8MP HDR assembly with32MiB editor reserve; maxHeap=${Runtime.getRuntime().maxMemory()}; elapsedSeconds=${(System.nanoTime()-start)/1e9}")
    }
}
