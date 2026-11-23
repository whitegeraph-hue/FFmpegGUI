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
    fun original(file:File):Pair<Raster,GainMapMetadata> {
        require(file.length() in 1..32_000_000) {"Original HDR input is limited to 32 MB"}
        val embedded=UltraHdr.read(file.readBytes()) ?: error("No supported Ultra HDR gain map in this source")
        ImageIO.createImageInputStream(java.io.ByteArrayInputStream(embedded.gainMapJpeg)).use {input->
            val readers=ImageIO.getImageReaders(input);require(readers.hasNext()) {"Cannot decode original gain map"};val reader=readers.next()
            try {
                reader.input=input;val w=reader.getWidth(0);val h=reader.getHeight(0)
                require(w>0 && h>0 && w.toLong()*h<=8_000_000) {"Original HDR gain map is too large"}
                val step=maxOf(1,(maxOf(w,h)+1023)/1024)
                val image=reader.read(0,reader.defaultReadParam.apply {setSourceSubsampling(step,step,0,0)})
                try {
                    val orientation=DesktopStillInputs.orientation(file);val swapped=orientation>=5
                    val width=if(swapped)image.height else image.width;val height=if(swapped)image.width else image.height
                    val pixels=IntArray(width*height)
                    for(y in 0 until image.height)for(x in 0 until image.width) {
                        val (dx,dy)=when(orientation) {
                            2->image.width-1-x to y;3->image.width-1-x to image.height-1-y;4->x to image.height-1-y
                            5->y to x;6->image.height-1-y to x;7->image.height-1-y to image.width-1-x;8->y to image.width-1-x
                            else->x to y
                        }
                        pixels[dy*width+dx]=image.getRGB(x,y)
                    }
                    return Raster(width,height,pixels) to embedded.metadata
                }finally {image.flush()}
            }finally {reader.dispose()}
        }
    }
    private fun rect(project:DesktopProject,builder:GainMapBuilder):FloatArray {
        val source=project.sources.single();val (w,h)=editedSize(project.image,project.effects,source.info.width,source.info.height)
        val border=borderPixels(project.image,w,h);val sx=builder.mapWidth.toFloat()/(w+2*border);val sy=builder.mapHeight.toFloat()/(h+2*border)
        return floatArrayOf(border*sx,border*sy,w*sx,h*sy)
    }
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
                    HdrAreas.share(hdr.area,HdrAreas.aligned(mask,p.image,p.effects,builder.mapWidth,builder.mapHeight,rect(p,builder)))
                }
                val map=when(hdr.mode) {
                    HdrMode.GENERATED->builder.generated(hdr.intensity,area)
                    HdrMode.ORIGINAL->{
                        val p=requireNotNull(project);val (raster,metadata)=original(File(p.sources.single().path))
                        val aligned=HdrAreas.aligned(raster,p.image,p.effects,builder.mapWidth,builder.mapHeight,rect(p,builder),outside=Float.NaN)
                        val stops=FloatArray(aligned.size){i->if(aligned[i].isNaN())Float.NaN else metadata.stopsOf(aligned[i])}
                        builder.original(stops,hdr.intensity.coerceIn(0f,1f)*2f,area)
                    }
                } ?: return false
                val grey=GreyJpeg.encode(map.values,map.width,map.height,90)
                primary.writeBytes(UltraHdr.assemble(primary.readBytes(),grey,map.metadata))
                return true
            } finally {picture.dispose();plain.dispose()}
        }}
    }
}
