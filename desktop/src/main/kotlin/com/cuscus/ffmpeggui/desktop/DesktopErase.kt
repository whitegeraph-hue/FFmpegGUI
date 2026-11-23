package com.cuscus.ffmpeggui.desktop

import ai.onnxruntime.*
import com.cuscus.ffmpeggui.core.erase.InpaintArea
import com.cuscus.ffmpeggui.core.provenance.*
import com.cuscus.ffmpeggui.erase.EraseModel
import kotlinx.coroutines.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.imageio.ImageIO

/** Original MI-GAN/LaMa crop/grow/blend; alpha mask above half selects erased pixels. */
object DesktopErase {
    fun scaled(picture:BufferedImage,w:Int,h:Int):BufferedImage {
        val out=BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);val g=out.createGraphics()
        try {g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(picture,0,0,w,h,null)}finally {g.dispose()}
        return out
    }
    fun decode(path:String):BufferedImage {
        val stream=ImageIO.createImageInputStream(File(path)) ?: error("Cannot read picture")
        return stream.use {input->
            val readers=ImageIO.getImageReaders(input);require(readers.hasNext()) {"Choose a supported PNG/JPG picture"};val reader=readers.next()
            try {reader.input=input;val w=reader.getWidth(0);val h=reader.getHeight(0);require(w>0 && h>0 && w.toLong()*h<=2_000_000) {"Magic eraser is limited to 2 megapixels for memory safety. Resize a copy first."};reader.read(0).let {decoded->if(decoded.type==BufferedImage.TYPE_INT_ARGB)decoded else try {val rgba=BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);val g=rgba.createGraphics();try {g.composite=java.awt.AlphaComposite.Src;g.drawImage(decoded,0,0,null)}finally {g.dispose()};rgba}finally {decoded.flush()}}} finally {reader.dispose()}
        }
    }
    suspend fun erase(path:String,maskPath:String,model:File,markers:File,work:File,precise:Boolean=false,progress:(String)->Unit):File {
        // Validate marks before even opening the generative model. Pin checks run again at marking.
        for(pin in listOf(com.cuscus.ffmpeggui.marking.MarkerModel.ENCODER,com.cuscus.ffmpeggui.marking.MarkerModel.DECODER))DesktopModelFiles.verify(File(markers,pin.name),pin.sha256,pin.bytes)
        if(precise)DesktopModelFiles.verify(model,DesktopModels.LAMA_SHA256,DesktopModels.LAMA_BYTES) else DesktopModelFiles.verify(model,EraseModel.MIGAN_FILE.sha256,28_079_181)
        DesktopSmartModels.checkAvailableMemory((if(precise)2_000L else 1_200L)*1024*1024)
        val upright=DesktopStillInputs.resolve(path,work)
        val picture=decode(upright)
        try {
            val width=picture.width;val height=picture.height;val side=maxOf(width,height)
            val mask=decode(maskPath)
            val selected=try {require(mask.width==width && mask.height==height) {"Erase mask dimensions must match the upright source"};BooleanArray(width*height){i->(mask.getRGB(i%width,i/width) ushr 24)>127}}finally {mask.flush()}
            val marked=InpaintArea.bounds(selected,width,height) ?: error("Nothing selected. Mask alpha above 50% is erased; transparent pixels are kept.")
            val growBy=maxOf(2,side/150)
            val crop=InpaintArea.crop(InpaintArea.Box(maxOf(0,marked.left-growBy),maxOf(0,marked.top-growBy),minOf(width,marked.right+growBy),minOf(height,marked.bottom+growBy)),width,height)
            val cw=crop.width;val ch=crop.height;val n=InpaintArea.SIDE
            val local=BooleanArray(cw*ch){i->selected[(crop.top+i/cw)*width+crop.left+i%cw]}
            val erased=InpaintArea.grow(local,cw,ch,growBy)
            val square=scaled(picture.getSubimage(crop.left,crop.top,cw,ch),n,n)
            val pixels=try {square.getRGB(0,0,n,n,null,0,n)}finally {square.flush()}
            val hole=BooleanArray(n*n){i->erased[(i/n)*ch/n*cw+(i%n)*cw/n]}
            progress("Filling selected area with ${if(precise)"LaMa" else "MI-GAN"}")
            val filled=run(model,pixels,hole,precise)
            val made=BufferedImage(n,n,BufferedImage.TYPE_INT_ARGB).also {it.setRGB(0,0,n,n,filled,0,n)}
            val back=try {scaled(made,cw,ch)}finally {made.flush()}
            try {
                val feather=maxOf(1,side/220);val inside=InpaintArea.bounds(erased,cw,ch)!!;val reach=3*feather
                val bx=maxOf(0,inside.left-reach);val by=maxOf(0,inside.top-reach);val bw=minOf(cw,inside.right+reach)-bx;val bh=minOf(ch,inside.bottom+reach)-by
                val part=BooleanArray(bw*bh){i->erased[(by+i/bw)*cw+bx+i%bw]};val weights=InpaintArea.weights(part,bw,bh,feather)
                val row=IntArray(bw);val painted=IntArray(bw)
                for(y in 0 until bh){currentCoroutineContext().ensureActive();picture.getRGB(crop.left+bx,crop.top+by+y,bw,1,row,0,bw);back.getRGB(bx,by+y,bw,1,painted,0,bw);InpaintArea.blend(row,painted,weights,y*bw);picture.setRGB(crop.left+bx,crop.top+by+y,bw,1,row,0,bw)}
            }finally {back.flush()}
            progress("Adding and checking the invisible AI mark")
            DesktopPictureMarker.use(markers){it.markPicture(picture)}
            val target=File(work,"erased.png")
            check(ImageIO.write(picture,"png",target))
            check(AiSeal.mark(target,AiDisclosure(pictures=true))==AiSeal.Marked.WRITTEN) {"Could not write AI picture disclosure"}
            // Read the saved PNG, not just the in-memory pixels.
            val reload=decode(target.path)
            try {DesktopPictureMarker.use(markers){check(it.pictureMarked(reload)) {"Invisible AI mark did not survive PNG writing"}}} finally {reload.flush()}
            return target
        }finally {picture.flush()}
    }
    private suspend fun run(file:File,argb:IntArray,hole:BooleanArray,precise:Boolean):IntArray {
        currentCoroutineContext().ensureActive()
        OrtSession.SessionOptions().use {opts->
            opts.setIntraOpNumThreads(2);opts.setInterOpNumThreads(1);opts.setCPUArenaAllocator(false);opts.setMemoryPatternOptimization(false)
            val env=OrtEnvironment.getEnvironment();val n=InpaintArea.SIDE;val plane=n*n
            env.createSession(file.path,opts).use {session->
                val names=session.inputNames.toList();require(names.size==2);val maskName=names.firstOrNull {it.contains("mask",true)} ?: names.last();val imageName=names.first {it!=maskName}
                val image:OnnxTensor
                val mask:OnnxTensor
                if(precise) {
                    val pixels=FloatArray(3*plane){index->((argb[index%plane] shr (16-8*(index/plane))) and 255)/255f}
                    image=OnnxTensor.createTensor(env,FloatBuffer.wrap(pixels),longArrayOf(1,3,n.toLong(),n.toLong()))
                    try {mask=OnnxTensor.createTensor(env,FloatBuffer.wrap(FloatArray(plane){if(hole[it])1f else 0f}),longArrayOf(1,1,n.toLong(),n.toLong()))}catch(t:Throwable){image.close();throw t}
                } else {
                    val bytes=ByteBuffer.allocateDirect(3*plane).order(ByteOrder.nativeOrder());for(c in 0..2)for(i in 0 until plane)bytes.put(((argb[i] shr (16-8*c)) and 255).toByte());bytes.rewind()
                    val known=ByteBuffer.allocateDirect(plane).order(ByteOrder.nativeOrder());for(i in 0 until plane)known.put((if(hole[i])0 else 255).toByte());known.rewind()
                    image=OnnxTensor.createTensor(env,bytes,longArrayOf(1,3,n.toLong(),n.toLong()),OnnxJavaType.UINT8)
                    try {mask=OnnxTensor.createTensor(env,known,longArrayOf(1,1,n.toLong(),n.toLong()),OnnxJavaType.UINT8)}catch(t:Throwable){image.close();throw t}
                }
                image.use {mask.use {
                        session.run(mapOf(imageName to image,maskName to mask)).use {result->
                            val tensor=result[0] as OnnxTensor
                            val channels=if(tensor.info.type==OnnxJavaType.UINT8){val b=tensor.byteBuffer;FloatArray(b.remaining()){(b.get(it).toInt() and 255).toFloat()}} else {val b=tensor.floatBuffer;FloatArray(b.remaining()).also {b.get(it);if((it.maxOrNull() ?: 0f)<=1.5f)for(i in it.indices)it[i]*=255}}
                            require(channels.size==3*plane && channels.all {it.isFinite()}) {"Unexpected inpainting output shape"}
                            return IntArray(plane){i->(255 shl 24) or (channels[i].toInt().coerceIn(0,255) shl 16) or (channels[plane+i].toInt().coerceIn(0,255) shl 8) or channels[2*plane+i].toInt().coerceIn(0,255)}
                        }
                    }
                }
            }
        }
    }
}
