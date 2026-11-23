package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.dub.DubModel
import com.cuscus.ffmpeggui.marking.MarkerModel
import com.cuscus.ffmpeggui.core.archive.*
import com.cuscus.ffmpeggui.core.dub.*
import com.cuscus.ffmpeggui.core.provenance.*
import com.cuscus.ffmpeggui.core.voice.WavWriter
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.json.*

object DesktopDub {
    val root:File get()=File(DesktopModels.root,"kokoro")
    val markers:File get()=File(DesktopModels.root,"audio-markers")
    suspend fun importMarkers(files:List<File>) {
        val pins=listOf(MarkerModel.GENERATOR,MarkerModel.DETECTOR)
        require(files.size==2 && files.map {it.name}.toSet()==pins.map {it.name}.toSet()) {"Choose exactly AudioSeal generator and detector"}
        for(pin in pins)DesktopModelFiles.importFile(files.first {it.name==pin.name},File(markers,pin.name),pin.sha256,pin.bytes)
    }
    private suspend fun markerPins() {for(pin in listOf(MarkerModel.GENERATOR,MarkerModel.DETECTOR))DesktopModelFiles.verify(File(markers,pin.name),pin.sha256,pin.bytes)}
    private suspend fun hash(file:File):String {val d=MessageDigest.getInstance("SHA-256");file.inputStream().use {i->val b=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=i.read(b);if(n<0)break;d.update(b,0,n)}};return d.digest().joinToString(""){"%02x".format(it)}}
    suspend fun importArchive(source:File)=withContext(Dispatchers.IO) {
        markerPins();require(!root.exists()) {"Kokoro is already installed"}
        DesktopModelFiles.verify(source,DubModel.ARCHIVE_SHA256,DubModel.ARCHIVE_BYTES)
        root.parentFile.mkdirs();require(root.parentFile.usableSpace>900_000_000) {"Not enough free space to import Kokoro"}
        val stage=Files.createTempDirectory(root.parentFile.toPath(),".kokoro-").toFile()
        try {
            // A verified private archive copy prevents a changed selected file after hashing.
            val archive=File(stage,"model.tar.bz2");DesktopModelFiles.importFile(source,archive,DubModel.ARCHIVE_SHA256,DubModel.ARCHIVE_BYTES)
            val content=File(stage,"content").apply {mkdir()};val hashes=linkedMapOf<String,String>();var total=0L
            archive.inputStream().buffered().use {raw->Bzip2InputStream(raw,2).use {input->
                val tar=TarReader(input)
                while(true){currentCoroutineContext().ensureActive();val entry=tar.next() ?: break
                    require(entry.path.startsWith(DubModel.ARCHIVE_FOLDER+"/")) {"Unexpected voice archive root"}
                    val rel=entry.path.removePrefix(DubModel.ARCHIVE_FOLDER+"/")
                    require(rel.isNotEmpty() && rel.split('/').none {it in listOf("",".","..")} && '\\' !in rel && !rel.startsWith('/')) {"Unsafe voice archive path"}
                    require(hashes.size<3000 && !hashes.containsKey(rel) && entry.size in 0..350_000_000) {"Invalid or duplicate voice archive file"}
                    total+=entry.size;require(total<=DubModel.INSTALLED_BYTES) {"Voice archive exceeds its size limit"}
                    val target=File(content,rel);target.parentFile.mkdirs();var copied=0L
                    target.outputStream().use {o->val b=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=tar.content.read(b);if(n<0)break;copied+=n;require(copied<=entry.size);o.write(b,0,n)}}
                    require(copied==entry.size);hashes[rel]=hash(target)
                }
            }}
            require(total==DubModel.INSTALLED_BYTES) {"Unexpected voice archive expanded size"}
            for((name,pin) in DubModel.PINNED)require(hashes[name]==pin) {"Kokoro pinned file mismatch: $name"}
            File(content,"desktop-manifest.json").writeText(buildJsonObject {put("archive",DubModel.ARCHIVE_SHA256);put("files",buildJsonObject {hashes.forEach {(k,v)->put(k,v)}})}.toString())
            Files.move(content.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE)
        }finally {stage.deleteRecursively()}
    }
    suspend fun verify(directory:File)=withContext(Dispatchers.IO) {
        val manifest=File(directory,"desktop-manifest.json");require(manifest.length() in 1..1_000_000) {"Missing Kokoro import manifest"}
        val data=Json.parseToJsonElement(manifest.readText()).jsonObject;require(data["archive"]!!.jsonPrimitive.content==DubModel.ARCHIVE_SHA256)
        val files=data["files"]!!.jsonObject;require(files.size in 10..3000)
        for((rel,value) in files){currentCoroutineContext().ensureActive();require(!rel.startsWith('/') && '\\' !in rel && rel.split('/').none {it in listOf("",".","..")});val f=File(directory,rel);require(f.isFile && !Files.isSymbolicLink(f.toPath()) && f.length()<=350_000_000 && hash(f)==value.jsonPrimitive.content) {"Kokoro file changed: $rel. Import the original archive again."}}
        for((name,pin) in DubModel.PINNED)require(files[name]?.jsonPrimitive?.content==pin) {"Kokoro pinned identity missing"}
    }
    /** Snapshot on the final output clock, not the selected source clock. */
    fun projectLines(project:DesktopProject):List<DubTiming.Line> {
        val request=project.request()
        val srt=project.externalSubtitles?.takeIf {it.enabled}?.let {subtitles->
            val cues=DesktopSubtitles.cuesOnTimeline(subtitles,request)
            com.cuscus.ffmpeggui.core.subtitles.SrtSubtitles.write(com.cuscus.ffmpeggui.core.subtitles.SubtitleLayout.ofCues(cues,project.subtitleStyle ?: com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle(),request.subtitleFrameRatio),project.effects.effectiveSpeed)
        } ?: DesktopExport().transcript(project,com.cuscus.ffmpeggui.core.model.OutputFormat.SRT)
        val lines=com.cuscus.ffmpeggui.core.preview.SubtitleParser().parse(srt).map {DubTiming.Line(it.startMs,it.endMs,it.text)}
        require(lines.isNotEmpty() && lines.size<=30 && project.outputDurationMs<=60000) {"Project dubbing is limited to30cues and one minute"}
        return lines
    }
    suspend fun generate(directory:File,markerDirectory:File,lines:List<DubTiming.Line>,voice:Int,speed:Float,endMs:Long?,target:File,progress:(String)->Unit) {
        require(lines.size in 1..30 && lines.sumOf {it.text.length}<=3000 && lines.all {it.text.length<=300 && it.startMs>=0 && it.endMs>it.startMs && it.endMs<=60_000} && lines.zipWithNext().all {it.first.startMs<it.second.startMs} && voice in DubLanguage.EN_US.voices.map {it.id} && speed in .75f..1.5f && (endMs==null || endMs in 1..60_000)) {"Dub up to30short US-English cues within one minute"}
        for(pin in listOf(MarkerModel.GENERATOR,MarkerModel.DETECTOR))DesktopModelFiles.verify(File(markerDirectory,pin.name),pin.sha256,pin.bytes)
        verify(directory);DesktopSmartModels.checkAvailableMemory(1_200L*1024*1024)
        val spoken=lines.mapNotNull {line->DubTiming.oneBreath(DubTiming.spoken(line.text)).takeIf {it.isNotBlank()}?.let {line.copy(text=it)}}
        require(spoken.isNotEmpty()) {"No spoken subtitle text"}
        val phonemes=spoken.map {DesktopPhonemizer.phonemes(it.text,"en-us",directory)}
        val config=OfflineTtsConfig.builder().setModel(OfflineTtsModelConfig.builder().setKokoro(OfflineTtsKokoroModelConfig.builder()
            .setModel(File(directory,DubModel.MODEL).path).setVoices(File(directory,DubModel.VOICES).path).setTokens(File(directory,DubModel.TOKENS).path).build())
            .setNumThreads(2).setProvider("cpu").setDebug(false).build()).setMaxNumSentences(1).setSilenceScale(.15f).build()
        val clips=ArrayList<FloatArray>();val rate:Int
        val tts=OfflineTts(config)
        try {
            rate=tts.sampleRate;require(rate==24000);val slots=DubTiming.slots(spoken,endMs);val pace=DubPace()
            fun say(index:Int,at:Float):FloatArray {val config=GenerationConfig().apply {setSid(voice);setSpeed(at);setPhonemeCodepoints(phonemes[index])};val audio=tts.generateWithConfig("",config);require(audio.sampleRate==rate && audio.samples.isNotEmpty() && audio.samples.size<=rate*60 && audio.samples.all {it.isFinite()});return DubAudio.trimmed(audio.samples,rate).also {require(it.isNotEmpty()) {"The voice returned silence"};DubAudio.fade(it,rate)}}
            spoken.forEachIndexed {index,line->currentCoroutineContext().ensureActive();progress("Saying cue ${index+1} of ${spoken.size}");val used=pace.speedFor(line.text,slots[index],speed);var samples=say(index,used);pace.learn(line.text,samples.size*1000L/rate,used);DubTiming.retrySpeed(samples.size*1000L/rate,slots[index],used,speed)?.let {retry->val again=say(index,retry);if(again.isNotEmpty())samples=again};clips+=DubFit.fit(samples,rate,slots[index]);require(clips.sumOf {it.size.toLong()}<=rate*60L) {"Generated voice exceeds one minute"}}
        }finally {tts.release()}
        progress("Adding and checking invisible AudioSeal marks")
        DesktopAudioMarker.use(markerDirectory){marker->
        val marked=marker.markSpeech(clips,rate)
        val starts=DubTiming.place(spoken,marked.map {it.size*1000L/rate})
        require(starts.last()+marked.last().size*1000L/rate<=60_000 && (endMs==null || starts.last()+marked.last().size*1000L/rate<=endMs)) {"Fitted voice extends beyond the allowed end"}
        WavWriter(target,1,rate,comment=AiDisclosure.SPEECH_TEXT).use {writer->
            var written=0L;val silence=FloatArray(rate/10)
            marked.forEachIndexed {i,clip->var gap=starts[i]*rate/1000-written;while(gap>0){currentCoroutineContext().ensureActive();val n=minOf(gap,silence.size.toLong()).toInt();writer.write(silence,n);written+=n;gap-=n};writer.write(clip,clip.size);written+=clip.size}
        }
        check(AiSeal.mark(target,AiDisclosure(speech=true),AiDisclosure.TRAINED)==AiSeal.Marked.WRITTEN) {"Could not mark generated speech disclosure"}
        require(target.length() in 45..3_000_000) {"Invalid saved speech size"}
        val saved=com.cuscus.ffmpeggui.core.marking.PcmWav.read(target)
        require(saved.rate==rate && saved.samples.size<=rate*60) {"Invalid saved speech format"}
        val verdict=marker.speech(listOf(saved.samples),rate)
        require(verdict.share!=null && verdict.share>=com.cuscus.ffmpeggui.core.marking.SpeechMarking.FRESH_SHARE && com.cuscus.ffmpeggui.core.marking.Marks.speechMessageIsOurs(verdict.message)) {"Invisible AI mark could not be verified in the saved WAV. No output will be delivered."}
        }
    }
}
