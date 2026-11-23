package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.hdr.*
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.image.*
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Gain map from the original core; decoder reads bands, never two whole pictures. */
object DesktopHdr {
    fun readMask(file:File,width:Int,height:Int):Raster {
        require(file.length() in 1..24_000_000) {"HDR mask is missing or too large"}
        ImageIO.createImageInputStream(file).use {input->
            val readers=ImageIO.getImageReaders(input);require(readers.hasNext()) {"Invalid HDR mask"};val reader=readers.next()
            try {
                reader.input=input
                require(reader.getWidth(0)==width && reader.getHeight(0)==height && width.toLong()*height<=8_000_000) {"HDR mask must match the upright source, up to 8 megapixels"}
                val step=maxOf(1,(maxOf(width,height)+1023)/1024)
                val picture=reader.read(0,reader.defaultReadParam.apply {setSourceSubsampling(step,step,0,0)})
                try {return HdrAreas.raster(IntArray(picture.width*picture.height){i->picture.getRGB(i%picture.width,i/picture.width) ushr 24},picture.width,picture.height)}finally {picture.flush()}
            }finally {reader.dispose()}
        }
    }

    suspend fun assemble(primary:File,finalPng:File,plainPng:File,hdr:ImageHdr,project:DesktopProject?=null):Boolean {
        require(hdr.mode==HdrMode.GENERATED) {"Original HDR still needs a desktop adapter."}
        require(hdr.hasArea) {"Choose an HDR area mask first"}
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
                val area=if(hdr.area==HdrArea.EVERYWHERE)null else {
                    val p=requireNotNull(project) {"HDR area needs source geometry"};val source=p.sources.single()
                    val mask=readMask(File(requireNotNull(hdr.areaMask)),source.info.width,source.info.height)
                    val (ew,eh)=editedSize(p.image,p.effects,source.info.width,source.info.height)
                    val border=borderPixels(p.image,ew,eh);val sx=builder.mapWidth.toFloat()/(ew+2*border);val sy=builder.mapHeight.toFloat()/(eh+2*border)
                    HdrAreas.share(hdr.area,HdrAreas.aligned(mask,p.image,p.effects,builder.mapWidth,builder.mapHeight,floatArrayOf(border*sx,border*sy,ew*sx,eh*sy)))
                }
                val map=builder.generated(hdr.intensity,area) ?: return false
                val grey=GreyJpeg.encode(map.values,map.width,map.height,90)
                primary.writeBytes(UltraHdr.assemble(primary.readBytes(),grey,map.metadata))
                return true
            } finally {picture.dispose();plain.dispose()}
        }}
    }
}
