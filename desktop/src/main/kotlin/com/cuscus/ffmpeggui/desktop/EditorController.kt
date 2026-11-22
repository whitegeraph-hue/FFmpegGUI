package com.cuscus.ffmpeggui.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.cuscus.ffmpeggui.conversion.*
import com.cuscus.ffmpeggui.core.editing.*
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.preview.mapToTimeline
import com.cuscus.ffmpeggui.core.subtitles.*
import com.cuscus.ffmpeggui.core.timeline.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

class EditorController(val scope: CoroutineScope) {
    var project by mutableStateOf<DesktopProject?>(null)
    var sourceFrame by mutableStateOf<ImageBitmap?>(null)
    var previewFrame by mutableStateOf<ImageBitmap?>(null)
    var position by mutableStateOf(0L)
    var busy by mutableStateOf(false)
    var status by mutableStateOf("Open a video, audio file or picture to begin.")
    var error by mutableStateOf<String?>(null)
    var tab by mutableStateOf("Edit")
    var pauseStatus by mutableStateOf<PauseCutsStatus>(PauseCutsStatus.Idle)
    var sceneStatus by mutableStateOf<SceneSplitsStatus>(SceneSplitsStatus.Idle)
    var beatStatus by mutableStateOf<BeatsStatus>(BeatsStatus.Idle)
    var followStatus by mutableStateOf<FollowCropStatus>(FollowCropStatus.Idle)
    var snap by mutableStateOf(false)
    var modelStatus by mutableStateOf(DesktopModels.present())
    val models=DesktopModelWorker()
    var rendered by mutableStateOf<File?>(null)
    var job: Job? = null
    private var frameJob: Job? = null
    private val history = ArrayDeque<DesktopProject>()
    val media = DesktopMedia()
    val exporter = DesktopExport(media)
    val player=DesktopPlayer(scope,media)
    val id: () -> String = { UUID.randomUUID().toString() }

    fun work(message: String, block: suspend () -> Unit) {
        if (busy) return
        busy=true; error=null; status=message
        job=scope.launch {
            try { block() }
            catch (e: CancellationException) { status="Stopped. Your original files are untouched."; throw e }
            catch (e: Exception) { error=e.message ?: e.toString(); status="Could not finish. Your original files are untouched." }
            finally { busy=false }
        }
    }
    fun open(path: String) = work("Reading media locally…") {
        val source=media.probe(path)
        player.stop()
        project=DesktopProject.create(source)
        history.clear(); resetAnalysis(); rendered?.delete();rendered=null; position=0
        status="${File(path).name} · ${source.info.durationFormatted} · ${source.info.width} × ${source.info.height}"
        seek(0)
    }
    fun resetAnalysis() { pauseStatus=PauseCutsStatus.Idle; sceneStatus=SceneSplitsStatus.Idle; beatStatus=BeatsStatus.Idle; snap=false; followStatus=FollowCropStatus.Idle }
    fun mutate(next: DesktopProject) {
        project?.let { history.addLast(it); if (history.size>50) history.removeFirst() }
        project=next
        player.stop();rendered?.delete(); rendered=null
        position=position.coerceAtMost(next.timeline.durationMs)
        pauseStatus=PauseCutsStatus.Idle; sceneStatus=SceneSplitsStatus.Idle
        seek(position)
    }
    fun undo() { if (history.isNotEmpty()) { project=history.removeLast(); player.stop();rendered?.delete(); rendered=null; resetAnalysis(); seek(position) } }
    fun seek(at: Long) {
        val p=project ?: return
        position=at.coerceIn(0,p.timeline.durationMs)
        val pos=p.timeline.locate(position) ?: return
        val clip=p.timeline.clips[pos.index]
        frameJob?.cancel()
        frameJob=scope.launch {
            delay(120)
            try {
                val info=p.sources.first { it.path==clip.sourceId }.info
                if (!info.isVideo) { sourceFrame=null; return@launch }
                val frame=media.frame(clip.sourceId,clip.startMs+pos.offsetMs)
                sourceFrame=frame?.toComposeImageBitmap()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { sourceFrame=null }
        }
    }
    fun seekWord(word: SpokenWord) {
        val p=project ?: return
        val at=mapToTimeline(word.startMs,word.endMs,p.request().segments,0).firstOrNull()?.first
        if (at==null) status="This word is no longer on the edited timeline." else seek(at)
    }
    fun splitHere() {
        val p=project ?: return
        val beats=(beatStatus as? BeatsStatus.Ready)?.beatsMs.orEmpty()
        val at=if (snap) BeatTrack.nearest(beats,position) ?: position else position
        mutate(p.copy(timeline=p.timeline.splitAt(at,id()),followCrop=null))
        status="Split at ${time(at)}. Export applies the timeline; source is untouched."
    }
    fun removeClip(index: Int) { val p=project ?: return; mutate(p.copy(timeline=p.timeline.remove(p.timeline.clips[index].id),followCrop=null)) }
    fun moveClip(index: Int, offset: Int) { val p=project ?: return; mutate(p.copy(timeline=p.timeline.move(p.timeline.clips[index].id,offset),followCrop=null)) }
    fun transcriptCut(block: WordBlock, keepOnly: Boolean) {
        val p=project ?: return
        val sourceCuts=if (keepOnly) TranscriptEdits.keepOnly(p.words,block.firstIndex,block.lastIndex,p.sources[0].info.durationMs)
            else listOfNotNull(p.words.cutRangeFor(block.firstIndex,block.lastIndex))
        val cuts=sourceCuts.flatMap { c -> mapToTimeline(c.startMs,c.endMs,p.request().segments,0).map { CutRange(it.first,it.second,c.reason) } }
        mutate(p.copy(timeline=p.timeline.withoutRanges(cuts,id),followCrop=null))
        status=if (keepOnly) "Kept the selected line on the timeline." else "Cut the selected line from the timeline."
    }
    fun findPauses() {
        val p=project ?: return
        if (p.words.isEmpty()) { pauseStatus=PauseCutsStatus.NeedsWords; return }
        pauseStatus=PauseCutsStatus.Working
        work("Finding pauses and hesitations locally…") {
            try {
                val silence=media.silence(p.sources[0])
                val cuts=PauseCuts.suggest(p.words,silence,p.sources[0].info.durationMs).flatMap { c ->
                    mapToTimeline(c.startMs,c.endMs,p.request().segments,0).map { CutRange(it.first,it.second,c.reason) }
                }.normalized(p.timeline.durationMs,120)
                pauseStatus=PauseCutsStatus.Ready(cuts,cuts.indices.toSet()); status="Review the suggestions, then choose Cut."
            } catch (e: Exception) { pauseStatus=PauseCutsStatus.Failed; throw e }
        }
    }
    fun findScenes() {
        val p=project ?: return
        sceneStatus=SceneSplitsStatus.Working
        work("Finding scene changes locally…") {
            try {
                val points=media.scenes(p.sources[0]).flatMap { at -> mapToTimeline(at,at+1,p.request().segments,0).map { it.first } }.distinct()
                sceneStatus=SceneSplitsStatus.Ready(points,points.indices.toSet()); status="Review the scene boundaries, then choose Split."
            } catch (e: Exception) { sceneStatus=SceneSplitsStatus.Failed; throw e }
        }
    }
    fun findBeats() {
        val p=project ?: return
        beatStatus=BeatsStatus.Working
        work("Finding musical beats locally…") {
            try {
                val result=withContext(Dispatchers.Default) { BeatTrack.analyze(media.samples(p.sources[0].path),16000,p.sources[0].info.durationMs) }
                check(result.tempoBpm!=null && result.beatsMs.isNotEmpty()) { "No steady beat found. Use at least five seconds of rhythmic music." }
                val points=result.beatsMs.flatMap { at -> mapToTimeline(at,at+1,p.request().segments,0).map { it.first } }.distinct()
                beatStatus=BeatsStatus.Ready(result.onsetsMs,points,result.tempoBpm); status="Review the beats. Snapping also applies to Split here."
            } catch (e: Exception) { beatStatus=BeatsStatus.Failed; throw e }
        }
    }
    fun export(output: File) {
        val p=project ?: return
        work("Exporting locally with the shared FFmpeg engine…") {
            val result=exporter.export(p,output)
            status="Export complete: ${result.path} · ${result.info.durationFormatted}"
        }
    }
    fun preview() {
        val p=project ?: return
        player.stop()
        val file=File.createTempFile("ffmpeggui-preview-", ".${p.format.extension}").apply { delete() }
        work("Rendering an exact edited preview. This includes cuts and effects…") {
            try {
                exporter.export(p,file)
                rendered?.delete(); rendered=file
                if (p.format.hasVideo || p.format.isImage) previewFrame=media.frame(file.path,0)?.toComposeImageBitmap()
                status="Edited preview is ready. Play inside the editor to inspect audio and motion."
            } catch (e: Exception) { file.delete(); throw e }
        }
    }
    fun listen() {
        val p=project ?: return
        work("Listening locally. Stop ends the isolated model process…") {
            val words=models.transcribe(p.sources[0].path,DesktopModels.speechDirectory) { step ->
                scope.launch { status="$step locally. Stop ends the isolated model process." }
            }
            check(words.isNotEmpty()) { "No speech found in this file." }
            mutate(p.copy(words=words)); tab="Transcript";status="Heard ${words.size} words locally. Tap a word to seek."
        }
    }
    fun follow() {
        val p=project ?: return
        if(!DesktopModels.cutoutFile.isFile) { followStatus=FollowCropStatus.NoModel;return }
        followStatus=FollowCropStatus.Working(0f)
        work("Following the subject locally. Stop ends the isolated model process…") {
            try {
                val tracked=models.track(p.sources[0].path,p.sources[0].info.durationMs,DesktopModels.cutoutFile,progress={ value -> scope.launch { followStatus=FollowCropStatus.Working(value) } })
                val mapped=tracked.points.flatMap { point -> mapToTimeline(point.atMs,point.atMs+1,p.request().segments,0).map { point.copy(atMs=it.first) } }
                check(mapped.isNotEmpty()) { "No track points on the current timeline." }
                val size=FollowCropTrack.windowSize(tracked.frameWidth,tracked.frameHeight,9,16)
                mutate(p.copy(followCrop=FollowCropTrack(mapped,tracked.frameWidth,tracked.frameHeight,size.first,size.second,p.sources[0].path)))
                followStatus=FollowCropStatus.Idle;status="Following crop set. Render edited preview to inspect motion before export."
            } catch(e:CancellationException) { followStatus=FollowCropStatus.Idle;throw e }
            catch(e:Exception) { followStatus=FollowCropStatus.Failed;throw e }
        }
    }
    fun smartUi() = com.cuscus.ffmpeggui.SmartEditUi(
        pauseCuts=pauseStatus, sceneSplits=sceneStatus, beats=beatStatus, snapToBeats=snap,
        followCrop=followStatus, followCropSet=project?.followCrop!=null,
        onFindPauseCuts=::findPauses,
        onTogglePauseCut={ index -> (pauseStatus as? PauseCutsStatus.Ready)?.let { s -> pauseStatus=s.copy(chosen=toggle(s.chosen,index)) } },
        onApplyPauseCuts={ val p=project; val s=pauseStatus as? PauseCutsStatus.Ready; if (p!=null && s!=null) mutate(p.copy(timeline=p.timeline.withoutRanges(s.cuts.filterIndexed { i,_ -> i in s.chosen },id),followCrop=null)) },
        onDismissPauseCuts={ pauseStatus=PauseCutsStatus.Idle }, onListen={ tab="Transcript";listen() },
        onFindSceneSplits=::findScenes,
        onToggleSceneSplit={ index -> (sceneStatus as? SceneSplitsStatus.Ready)?.let { s -> sceneStatus=s.copy(chosen=toggle(s.chosen,index)) } },
        onApplySceneSplits={ val p=project; val s=sceneStatus as? SceneSplitsStatus.Ready; if (p!=null && s!=null) mutate(p.copy(timeline=p.timeline.splitAtAll(s.points.filterIndexed { i,_ -> i in s.chosen },id),followCrop=null)) },
        onDismissSceneSplits={ sceneStatus=SceneSplitsStatus.Idle }, onFindBeats=::findBeats,
        onToggleBeat={ index -> (beatStatus as? BeatsStatus.Ready)?.let { s -> beatStatus=s.copy(chosen=toggle(s.chosen,index)) } },
        onSnapToBeats={ snap=it },
        onApplyBeatCuts={ val p=project; val s=beatStatus as? BeatsStatus.Ready; if (p!=null && s!=null) mutate(p.copy(timeline=p.timeline.splitAtAll(s.beatsMs.filterIndexed { i,_ -> i in s.chosen },id),followCrop=null)) },
        onDismissBeats={ beatStatus=BeatsStatus.Idle }, onTrackSubject=::follow,
        onStopFollowCrop={ job?.cancel(); followStatus=FollowCropStatus.Idle },
        onClearFollowCrop={ project?.let { mutate(it.copy(followCrop=null)) } }, onOpenCutoutModel={ tab="Models" }
    )
    fun close() { player.stop(); job?.cancel(); frameJob?.cancel(); rendered?.delete() }
}
private fun toggle(set: Set<Int>, index: Int) = if (index in set) set-index else set+index
fun time(ms: Long) = java.lang.String.format(java.util.Locale.US,"%d:%02d.%01d",ms/60000,(ms/1000)%60,(ms/100)%10)
