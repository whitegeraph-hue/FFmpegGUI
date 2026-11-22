package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.hdr.*
import com.cuscus.ffmpeggui.core.model.*
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Gain map from the original core; decoder reads bands, never two whole pictures. */
object DesktopHdr {
    suspend fun assemble(primary:File,finalPng:File,plainPng:File,hdr:ImageHdr):Boolean {
        require(hdr.mode==HdrMode.GENERATED && hdr.area==HdrArea.EVERYWHERE) {"Original/area HDR still needs a desktop adapter."}
        val finalStream=ImageIO.createImageInputStream(finalPng)
        val plainStream=ImageIO.createImageInputStream(plainPng)
        finalStream.use {pictureInput->plainStream.use {plainInput->
            val picture=ImageIO.getImageReaders(pictureInput).next();val plain=ImageIO.getImageReaders(plainInput).next()
            try {
                picture.input=pictureInput;plain.input=plainInput
                val w=picture.getWidth(0);val h=picture.getHeight(0)
                require(w==plain.getWidth(0) && h==plain.getHeight(0) && w.toLong()*h<=8_000_000) {"HDR output is limited to 8 megapixels to keep memory bounded. Reduce maximum output side."}
                val builder=GainMapBuilder(w,h)
                val band=maxOf(GainMapBuilder.SCALE,(262144/w/GainMapBuilder.SCALE)*GainMapBuilder.SCALE)
                var top=0
                while(top<h) {
                    currentCoroutineContext().ensureActive()
                    val rows=minOf(band,h-top)
                    fun pixels(reader:ImageReader):IntArray {
                        val params=reader.defaultReadParam.apply {sourceRegion=java.awt.Rectangle(0,top,w,rows)}
                        val image=reader.read(0,params)
                        return try {image.getRGB(0,0,w,rows,null,0,w)} finally {image.flush()}
                    }
                    builder.add(top,rows,pixels(picture),pixels(plain));top+=rows
                }
                val map=builder.generated(hdr.intensity) ?: return false
                val grey=GreyJpeg.encode(map.values,map.width,map.height,90)
                primary.writeBytes(UltraHdr.assemble(primary.readBytes(),grey,map.metadata))
                return true
            } finally {picture.dispose();plain.dispose()}
        }}
    }
}
