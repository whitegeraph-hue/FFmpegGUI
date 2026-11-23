package com.cuscus.ffmpeggui.desktop

import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.*

fun runMarkChecks(path:String)=runBlocking {
    val directory=File(path)
    val source=File(directory,"source.png")
    DesktopPictureMarker.use(directory) {marker->
        val picture=ImageIO.read(source)
        try {
            check(!marker.pictureMarked(picture)) {"Test source already marked"}
            marker.markPicture(picture)
            check(marker.pictureMarked(picture))
            val before=picture.getRGB(0,0,picture.width,picture.height,null,0,picture.width)
            marker.markPicture(picture)
            check(before.contentEquals(picture.getRGB(0,0,picture.width,picture.height,null,0,picture.width)))
            val output=File(directory,"marked.png");check(ImageIO.write(picture,"png",output))
            val decoded=ImageIO.read(output)
            try {check(marker.pictureMarked(decoded))} finally {decoded.flush()}
        } finally {picture.flush()}
    }
    println("PASS original pinned TrustMark real encode, immediate readback, no duplicate marking and PNG reload")
}

fun runEraseChecks(path:String)=runBlocking {
    val dir=File(path);val markers=File(dir,"../ffmpeggui-markers")
    val result=DesktopErase.erase(File(dir,"source.png").path,File(dir,"mask.png").path,File(dir,"migan_pipeline_v2.onnx"),markers,dir){println(it)}
    check(result.isFile && DesktopProvenance.aiPicture(result))
    val actual=ImageIO.read(result);val source=ImageIO.read(File(dir,"source.png"))
    try {
        var changed=0;for(y in 150 until 280)for(x in 240 until 380)if(actual.getRGB(x,y)!=source.getRGB(x,y))changed++
        check(changed>1000);check(actual.width==source.width && actual.height==source.height)
    }finally {actual.flush();source.flush()}
    val empty=File(dir,"empty.png");val im=java.awt.image.BufferedImage(640,360,java.awt.image.BufferedImage.TYPE_INT_ARGB);ImageIO.write(im,"png",empty);im.flush()
    check(runCatching {DesktopErase.erase(File(dir,"source.png").path,empty.path,File(dir,"migan_pipeline_v2.onnx"),markers,dir){}}.isFailure)
    DesktopModels.importPictureMarkers(listOf(File(markers,"trustmark-encoder-q.onnx"),File(markers,"trustmark-decoder-q.onnx")))
    DesktopModels.importErase(File(dir,"migan_pipeline_v2.onnx"))
    val worker=DesktopModelWorker();val target=File(dir,"worker-erased.png");target.delete()
    worker.erase(File(dir,"source.png").path,File(dir,"mask.png"),target){println(it)}
    check(target.isFile && DesktopProvenance.aiPicture(target))
    val bytes=target.readBytes();check(runCatching {worker.erase(File(dir,"source.png").path,File(dir,"mask.png"),target){}}.isFailure);check(bytes.contentEquals(target.readBytes()))
    val cancelled=File(dir,"cancelled.png");cancelled.delete()
    val job=launch {worker.erase(File(dir,"source.png").path,File(dir,"mask.png"),cancelled){}};delay(150);job.cancelAndJoin();check(!cancelled.exists())
    println("PASS actual MI-GAN fill, changed selected pixels, pinned TrustMark and PNG reload, XMP, empty mask rejection, isolated transfer, existing output preservation and cancellation")
}

fun runLamaChecks(path:String)=runBlocking {
    val dir=File(path);val markers=File(dir,"../ffmpeggui-markers")
    DesktopModels.importPictureMarkers(listOf(File(markers,"trustmark-encoder-q.onnx"),File(markers,"trustmark-decoder-q.onnx")))
    DesktopModels.importLama(File(dir,"lama_fp32.onnx"))
    val worker=DesktopModelWorker();val target=File(dir,"worker-lama.png").apply {delete()}
    worker.erase(File(dir,"source.png").path,File(dir,"mask.png"),target,true){println(it)}
    check(target.isFile && DesktopProvenance.aiPicture(target))
    val actual=ImageIO.read(target);val source=ImageIO.read(File(dir,"source.png"))
    try {var changed=0;for(y in 150 until 280)for(x in 240 until 380)if(actual.getRGB(x,y)!=source.getRGB(x,y))changed++;check(changed>1000);check(actual.width==source.width && actual.height==source.height)}finally {actual.flush();source.flush()}
    val bytes=target.readBytes();check(runCatching {worker.erase(File(dir,"source.png").path,File(dir,"mask.png"),target,true){}}.isFailure);check(bytes.contentEquals(target.readBytes()))
    val cancelled=File(dir,"cancelled-lama.png").apply {delete()};val job=launch {worker.erase(File(dir,"source.png").path,File(dir,"mask.png"),cancelled,true){}};delay(250);job.cancelAndJoin();check(!cancelled.exists())
    println("PASS actual known-hash LaMa fill, saved TrustMark and PNG disclosure, selected pixels changed, new-only destination and cancellation")
}
