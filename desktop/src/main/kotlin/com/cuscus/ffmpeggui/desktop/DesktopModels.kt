package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.speech.SpeechModel
import com.cuscus.ffmpeggui.cutout.CutoutModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DesktopModels {
    val root:File = File(System.getenv("XDG_DATA_HOME") ?: File(System.getProperty("user.home"),".local/share").path,"ffmpeggui-linux/models")
    val pictureMarkers:File get()=File(root,"markers")
    val eraseFile:File get()=File(root,"erase/migan_pipeline_v2.onnx")
    suspend fun importErase(file:File) {
        for(pin in listOf(com.cuscus.ffmpeggui.marking.MarkerModel.ENCODER,com.cuscus.ffmpeggui.marking.MarkerModel.DECODER))DesktopModelFiles.verify(File(pictureMarkers,pin.name),pin.sha256,pin.bytes)
        val pin=com.cuscus.ffmpeggui.erase.EraseModel.MIGAN_FILE
        require(file.name==pin.name);DesktopModelFiles.importFile(file,eraseFile,pin.sha256,28_079_181)
    }
    suspend fun importPictureMarkers(files:List<File>) {
        val pins=listOf(com.cuscus.ffmpeggui.marking.MarkerModel.ENCODER,com.cuscus.ffmpeggui.marking.MarkerModel.DECODER)
        require(files.size==2 && files.map {it.name}.toSet()==pins.map {it.name}.toSet()) {"Choose exactly the TrustMark encoder and decoder"}
        for(pin in pins)DesktopModelFiles.importFile(files.first {it.name==pin.name},File(pictureMarkers,pin.name),pin.sha256,pin.bytes)
    }
    val speechDirectory:File get()=File(root,"parakeet")
    val cutoutFile:File get()=File(root,"cutout/u2netp.onnx")
    val stemsFile:File get()=File(root,"stems/${DesktopStems.NAME}")
    val speechNames:List<String> get()=SpeechModel.PARAKEET.files.map { it.name }
    fun present():String = buildString {
        append("Speech files: ${speechNames.count { File(speechDirectory,it).isFile }} / 4. ")
        append(if(cutoutFile.isFile) "Subject model installed." else "Subject model not installed.")
        append(if(stemsFile.isFile) " Scene-stem model installed." else " Scene-stem model not installed.")
        append(" Every model is hash-checked again before use.")
    }
    suspend fun importSpeech(files:List<File>)=withContext(Dispatchers.IO) {
        val pins=SpeechModel.PARAKEET.files
        require(files.isNotEmpty())
        for(file in files) {
            val pin=pins.firstOrNull { it.name==file.name } ?: error("Not an original speech model file: ${file.name}")
            DesktopModelFiles.importFile(file,File(speechDirectory,pin.name),pin.sha256,pin.bytes)
        }
        // The app ships the exact original token asset, not model weights.
        val tokens=pins.first { it.name=="tokens.txt" }
        if(!File(speechDirectory,"tokens.txt").isFile) {
            val temporary=File.createTempFile("ffmpeggui-tokens-",".txt")
            try {
                DesktopModels::class.java.getResourceAsStream("/speech/tokens.txt")!!.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                DesktopModelFiles.importFile(temporary,File(speechDirectory,"tokens.txt"),tokens.sha256,tokens.bytes)
            } finally { temporary.delete() }
        }
    }
    suspend fun importStems(file:File) {
        require(file.name==DesktopStems.NAME) {"Choose the original Bandit int8 file"}
        DesktopModelFiles.importFile(file,stemsFile,DesktopStems.HASH,DesktopStems.BYTES)
    }
    suspend fun importCutout(file:File) {
        require(file.name==CutoutModel.FAST.fileName) { "Choose u2netp.onnx from the original download link." }
        DesktopModelFiles.importFile(file,cutoutFile,CutoutModel.FAST.sha256,CutoutModel.FAST.bytes)
    }
}
