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
    var smartClipId by mutableStateOf<String?>(null)
    fun selectSmartClip(id:String?) {
        if(busy)return
        smartClipId=id
        val p=project ?: return
        p.timeline.clips.firstOrNull {it.id==id}?.let {clip->selectedSource=p.sources.indexOfFirst {it.path==clip.sourceId}.coerceAtLeast(0)}
        resetAnalysis()
    }
    var selectedSource by mutableStateOf(0)
    var project by mutableStateOf<DesktopProject?>(null)
    var sourceFrame by mutableStateOf<ImageBitmap?>(null)
    var previewFrame by mutableStateOf<ImageBitmap?>(null)
    var drawingFrame by mutableStateOf<ImageBitmap?>(null)
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
    val studio=DesktopStudio(media)
    var studioText by mutableStateOf("")
    var studioCatalog by mutableStateOf(com.cuscus.ffmpeggui.core.command.studio.FfmpegCatalog.BUILT_IN)
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
        selectedSource=0;drawingFrame=null;project=DesktopProject.create(source)
        history.clear();smartClipId=null;pruneMasks(); resetAnalysis(); rendered?.delete();rendered=null;previewFrame=null; position=0
        status="${File(path).name} · ${source.info.durationFormatted} · ${source.info.width} × ${source.info.height}"
        seek(0)
    }
    fun selectSource(index:Int) {
        val p=project ?: return
        smartClipId=null;selectedSource=index.coerceIn(p.sources.indices);resetAnalysis()
        p.timeline.clips.indexOfFirst {it.sourceId==p.sources[selectedSource].path}.takeIf {it>=0}?.let {seek(p.timeline.startOf(it))}
    }
    fun append(path:String)=work("Adding source…") {
        val p=project ?: return@work
        require(p.sources.first().info.isVideo && !p.sources.first().info.isStill && p.sources.all {it.info.isVideo}){"Start with a video to combine video and still clips. Audio files can be added as sound tracks."}
        val source=media.probe(path)
        require(source.info.isVideo){"Choose a video or still picture. Add audio-only files under Sound tracks."}
        val sources=if(p.sources.any {it.path==source.path})p.sources else p.sources+source
        mutate(p.copy(sources=sources,timeline=p.timeline.append(Clip(id(),source.path,endMs=if(source.info.isStill)5000 else source.info.durationMs)),followCrop=null))
        selectedSource=sources.indexOfFirst {it.path==source.path};resetAnalysis();status="Source added: ${File(source.path).name}"
    }
    private fun pruneMasks(){DesktopMasks.prune((history.toList()+listOfNotNull(project)).flatMap {listOfNotNull(it.image.key?.mask,it.image.hdr?.mask,it.image.hdr?.brush)}.toSet())}
    fun resetAnalysis() { pauseStatus=PauseCutsStatus.Idle; sceneStatus=SceneSplitsStatus.Idle; beatStatus=BeatsStatus.Idle; snap=false; followStatus=FollowCropStatus.Idle }
    fun mutate(next: DesktopProject) {
        project?.let { history.addLast(it); if (history.size>50) history.removeFirst() }
        project=next
        selectedSource=selectedSource.coerceIn(next.sources.indices)
        player.stop();rendered?.delete(); rendered=null;previewFrame=null
        position=position.coerceAtMost(next.timeline.durationMs)
        resetAnalysis()
        if(next.timeline.clips.none {it.id==smartClipId})smartClipId=null
        seek(position)
        pruneMasks()
    }
    fun undo() { if (history.isNotEmpty()) { project=history.removeLast(); selectedSource=selectedSource.coerceIn(project!!.sources.indices);player.stop();rendered?.delete(); rendered=null;previewFrame=null; resetAnalysis(); seek(position);pruneMasks();status="Undid the last edit." } }
    fun seek(at: Long) {
        val p=project ?: return
        player.stop()
        position=at.coerceIn(0,p.timeline.durationMs)
        val pos=p.timeline.locate(position) ?: return
        val clip=p.timeline.clips[pos.index]
        frameJob?.cancel()
        frameJob=scope.launch {
            delay(120)
            try {
                rendered?.let {player.cue(it,p.outputAt(position));return@launch}
                val info=p.sources.first { it.path==clip.sourceId }.info
                if (!info.isVideo) { sourceFrame=null; return@launch }
                val frame=media.frame(clip.sourceId,clip.startMs+pos.offsetMs)
                sourceFrame=frame?.toComposeImageBitmap()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { sourceFrame=null }
        }
    }
    fun visibleFootagePosition():Long {
        val p=project ?: return position
        return if(player.engaged)p.clock().mediaMsForOutput(player.positionMs) else position
    }
    fun togglePlayback() {
        val p=project ?: return
        if(player.playing)player.pause()
        else rendered?.let {player.play(it,if(player.engaged)player.positionMs else p.outputAt(position))} ?: preview(true)
    }
    fun stepOutput(direction:Int) {
        val p=project ?: return
        val current=if(player.engaged)player.positionMs else p.outputAt(position)
        // The integrated preview is 25 fps, independently of source/export frame rate.
        val output=(current+direction*40L).coerceIn(0,p.outputDurationMs)
        seek(p.clock().mediaMsForOutput(output))
    }
    fun seekWord(word: SpokenWord) {
        val p=project ?: return
        val at=p.footageRanges(word.startMs,word.endMs,selectedSource).firstOrNull()?.first
        if (at==null) status="This word is no longer on the edited timeline." else seek(at)
    }
    fun splitHere() {
        val p=project ?: return
        val beats=(beatStatus as? BeatsStatus.Ready)?.beatsMs.orEmpty()
        val visible=visibleFootagePosition()
        val at=if (snap) BeatTrack.nearest(beats,visible) ?: visible else visible
        if(!p.timeline.canSplitAt(at)){status="Choose a point inside a clip, not an existing boundary.";return}
        position=at
        mutate(p.copy(timeline=p.splitFootageAt(listOf(at)),followCrop=null))
        status="Split at ${time(at)}. Export applies the timeline; source is untouched."
    }
    fun removeClip(index: Int) { val p=project ?: return; mutate(p.copy(timeline=p.timeline.remove(p.timeline.clips[index].id),followCrop=null)) }
    fun moveClip(index: Int, offset: Int) { val p=project ?: return; mutate(p.copy(timeline=p.timeline.move(p.timeline.clips[index].id,offset),followCrop=null)) }
    fun transcriptCut(block: WordBlock, keepOnly: Boolean) {
        val p=project ?: return
        val selected=p.timeline.clips.firstOrNull {it.id==smartClipId}
        if(selected!=null && (selected.sourceId!=p.sources[selectedSource].path || block.endMs<=selected.startMs || block.startMs>=(selected.endMs ?: selected.startMs))) {status="That line is outside the selected clip. No change made.";return}
        val sourceCuts=if (keepOnly) TranscriptEdits.keepOnly(p.sourceWords(selectedSource),block.firstIndex,block.lastIndex,p.sources[selectedSource].info.durationMs)
            else listOfNotNull(p.sourceWords(selectedSource).cutRangeFor(block.firstIndex,block.lastIndex))
        val cuts=ClipToolScope.cuts(p.timeline,smartClipId,p.sources[selectedSource].path,sourceCuts)
        mutate(p.copy(timeline=ClipToolScope.remove(p.timeline,cuts,id),followCrop=null))
        status=if(selected!=null) {if(keepOnly)"Kept this line in the selected clip. Other clips are unchanged."else "Cut this line from the selected clip. Other clips are unchanged."} else if (keepOnly) "Kept this source line. Other sources are unchanged." else "Cut the selected line from the timeline."
    }
    fun findPauses() {
        val p=project ?: return
        if (p.sourceWords(selectedSource).isEmpty()) { pauseStatus=PauseCutsStatus.NeedsWords; return }
        pauseStatus=PauseCutsStatus.Working
        work("Finding pauses and hesitations locally…") {
            try {
                val silence=media.silence(p.sources[selectedSource])
                val cuts=PauseCuts.suggest(p.sourceWords(selectedSource),silence,p.sources[selectedSource].info.durationMs).flatMap { c ->
                    ClipToolScope.cuts(p.timeline,smartClipId,p.sources[selectedSource].path,listOf(c))
                }
                pauseStatus=PauseCutsStatus.Ready(cuts,cuts.indices.toSet()); status=if(cuts.isEmpty())"No pause or hesitation cuts found in this scope."else "Review the suggestions, then choose Cut."
            } catch (e: Exception) { pauseStatus=PauseCutsStatus.Failed; throw e }
        }
    }
    fun findScenes() {
        val p=project ?: return
        sceneStatus=SceneSplitsStatus.Working
        work("Finding scene changes locally…") {
            try {
                val points=ClipToolScope.points(p.timeline,smartClipId,p.sources[selectedSource].path,media.scenes(p.sources[selectedSource])).filter(p.timeline::canSplitAt)
                sceneStatus=SceneSplitsStatus.Ready(points,points.indices.toSet()); status=if(points.isEmpty())"No scene changes found in this scope."else "Review the scene boundaries, then choose Split."
            } catch (e: Exception) { sceneStatus=SceneSplitsStatus.Failed; throw e }
        }
    }
    fun findBeats() {
        val p=project ?: return
        beatStatus=BeatsStatus.Working
        work("Finding musical beats locally…") {
            try {
                val result=withContext(Dispatchers.Default) { BeatTrack.analyze(media.samples(p.sources[selectedSource].path),16000,p.sources[selectedSource].info.durationMs) }
                check(result.tempoBpm!=null && result.beatsMs.isNotEmpty()) { "No steady beat found. Use at least five seconds of rhythmic music." }
                val points=ClipToolScope.points(p.timeline,smartClipId,p.sources[selectedSource].path,result.beatsMs).filter(p.timeline::canSplitAt)
                beatStatus=BeatsStatus.Ready(result.onsetsMs,points,result.tempoBpm); status=if(points.isEmpty())"No splittable beats found in this scope."else "Review the beats. Snapping also applies to Split here."
            } catch (e: Exception) { beatStatus=BeatsStatus.Failed; throw e }
        }
    }
    fun export(output: File) {
        val p=project ?: return
        work("Exporting locally with the shared FFmpeg engine…") {
            val result=exporter.export(p,output){status=it}
            status="Export complete: ${result.path} · ${result.info.durationFormatted}"
        }
    }
    fun preview(playWhenReady:Boolean=false) {
        val current=project ?: return
        if(current.format==OutputFormat.SVG || current.format.isTextArt) {
            status="SVG and text art can be exported. Their in-editor preview is still being ported.";return
        }
        val p=if(current.format.isTranscript) current.copy(format=if(current.sources[0].info.isVideo) OutputFormat.MP4 else OutputFormat.MP3) else current
        player.stop()
        val file=File.createTempFile("ffmpeggui-preview-", ".${p.format.extension}").apply { delete() }
        work("Rendering an exact edited preview. This includes cuts and effects…") {
            try {
                exporter.export(p,file){status=it}
                rendered?.delete(); rendered=file
                if (p.format.hasVideo || p.format.isImage) previewFrame=media.frame(file.path,0)?.toComposeImageBitmap()
                if(playWhenReady && !p.sources[0].info.isStill)player.play(file,p.outputAt(position))
                status=if(p.sources[selectedSource].info.isStill) "Edited picture is ready. Source file is unchanged." else "Edited preview is ready. Play inside the editor to inspect audio and motion."
            } catch (e: Exception) { file.delete(); throw e }
        }
    }
    fun listen() {
        val p=project ?: return
        work("Listening locally. Stop ends the isolated model process…") {
            val warnings=mutableListOf<String>()
            val words=models.transcribe(p.sources[selectedSource].path,DesktopModels.speechDirectory,{warnings+=it}) { step ->
                scope.launch { status="$step locally. Stop ends the isolated model process." }
            }
            check(words.isNotEmpty()) {if(warnings.isEmpty())"No speech recognized. This does not prove the file has no speech." else "No words recognized. "+warnings.joinToString(" ")}
            mutate(p.copy(wordsBySource=p.wordsBySource+(p.sources[selectedSource].path to words))); tab="Transcript";status="Heard ${words.size} words locally. Check the transcript against playback before cutting."
            if(warnings.isNotEmpty())error="Transcript may be incomplete. "+warnings.joinToString(" ")
        }
    }
    fun follow() {
        val p=project ?: return
        if(p.sources.size>1){error="Following crop across multiple sources is still being ported. Use per-clip framing for now.";return}
        if(!DesktopModels.cutoutFile.isFile) { followStatus=FollowCropStatus.NoModel;return }
        followStatus=FollowCropStatus.Working(0f)
        work("Following the subject locally. Stop ends the isolated model process…") {
            try {
                val tracked=models.track(p.sources[selectedSource].path,p.sources[selectedSource].info.durationMs,DesktopModels.cutoutFile,progress={ value -> scope.launch { followStatus=FollowCropStatus.Working(value) } })
                val mapped=tracked.points.flatMap { point -> mapToTimeline(point.atMs,point.atMs+1,p.request().segments,0).map { point.copy(atMs=it.first) } }
                check(mapped.isNotEmpty()) { "No track points on the current timeline." }
                val size=FollowCropTrack.windowSize(tracked.frameWidth,tracked.frameHeight,9,16)
                mutate(p.copy(followCrop=FollowCropTrack(mapped,tracked.frameWidth,tracked.frameHeight,size.first,size.second,p.sources[selectedSource].path)))
                followStatus=FollowCropStatus.Idle;status="Following crop set. Render edited preview to inspect motion before export."
            } catch(e:CancellationException) { followStatus=FollowCropStatus.Idle;throw e }
            catch(e:Exception) { followStatus=FollowCropStatus.Failed;throw e }
        }
    }
    fun refineSubject(points:List<Float>,radius:Float,keep:Boolean)=work("Updating subject mask…") {
        val p=project ?: return@work
        require(points.size>=2 && points.size%2==0 && points.all {it.isFinite()})
        val key=p.image.key ?: error("Cut out a subject first.")
        val file=File(key.mask ?: error("Cut out a subject first."))
        val bitmap=javax.imageio.ImageIO.read(file) ?: error("Cannot read subject mask")
        val target=File(file.parentFile,"${UUID.randomUUID()}.png")
        try {
            val w=bitmap.width;val h=bitmap.height
            val mask=IntArray(w*h) {i->bitmap.getRGB(i%w,i/w) ushr 24}
            com.cuscus.ffmpeggui.core.cutout.MaskEditing.paint(mask,w,h,points.mapIndexed {i,v->v.coerceIn(0f,1f)*if(i%2==0)w else h}.toFloatArray(),radius.coerceIn(.002f,.25f)*w,keep)
            bitmap.setRGB(0,0,w,h,IntArray(w*h) {(mask[it] shl 24) or 0xffffff},0,w)
            check(javax.imageio.ImageIO.write(bitmap,"png",target))
            mutate(p.copy(image=p.image.copy(key=key.copy(mask=target.absolutePath))))
            status="Subject mask refined. Render to inspect. Undo restores the previous mask."
        } catch(e:Throwable) {target.delete();throw e} finally {bitmap.flush()}
    }
    fun cutoutPicture()=work("Finding picture subject offline…") {
        val p=project ?: return@work
        require(p.sources.first().info.isStill) {"Subject cutout currently needs a still picture."}
        val image=models.cutout(p.sources.first().path,DesktopModels.cutoutFile)
        val directory=DesktopMasks.directory()
        val file=File(directory,"${UUID.randomUUID()}.png")
        try {
            check(javax.imageio.ImageIO.write(image,"png",file))
            mutate(p.copy(image=p.image.copy(key=ColorKey(mode=KeyMode.SUBJECT,mask=file.absolutePath))))
            drawingFrame=null
            status="Subject mask ready. Render to inspect its edges. You can undo the cutout."
        } catch(e:Throwable) {file.delete();throw e} finally {image.flush()}
    }
    fun drawingBackground()=work("Rendering drawing background…") {
        val p=project ?: return@work
        val file=File.createTempFile("ffmpeggui-drawing-",".png")
        try {exporter.export(p.copy(format=OutputFormat.PNG,image=p.image.copy(strokes=emptyList(),border=null,corners=0f,output=ImageOutput())),file);drawingFrame=media.frame(file.path,0)?.toComposeImageBitmap();status="Drawing background ready. Render the finished picture to verify."} finally {file.delete()}
    }
    fun smartUi() = com.cuscus.ffmpeggui.SmartEditUi(
        pauseCuts=pauseStatus, sceneSplits=sceneStatus, beats=beatStatus, snapToBeats=snap,
        followCrop=followStatus, followCropSet=project?.followCrop!=null,
        onFindPauseCuts=::findPauses,
        onTogglePauseCut={ index -> (pauseStatus as? PauseCutsStatus.Ready)?.let { s -> pauseStatus=s.copy(chosen=toggle(s.chosen,index)) } },
        onApplyPauseCuts={ val p=project; val s=pauseStatus as? PauseCutsStatus.Ready; if (p!=null && s!=null) mutate(p.copy(timeline=ClipToolScope.remove(p.timeline,s.cuts.filterIndexed { i,_ -> i in s.chosen },id),followCrop=null)) },
        onDismissPauseCuts={ pauseStatus=PauseCutsStatus.Idle }, onListen={ tab="Transcript";listen() },
        onFindSceneSplits=::findScenes,
        onToggleSceneSplit={ index -> (sceneStatus as? SceneSplitsStatus.Ready)?.let { s -> sceneStatus=s.copy(chosen=toggle(s.chosen,index)) } },
        onApplySceneSplits={ val p=project; val s=sceneStatus as? SceneSplitsStatus.Ready; if (p!=null && s!=null) {mutate(p.copy(timeline=p.splitFootageAt(s.points.filterIndexed { i,_ -> i in s.chosen }),followCrop=null));status="Scene splits applied. Undo restores the previous timeline."} },
        onDismissSceneSplits={ sceneStatus=SceneSplitsStatus.Idle;status="Scene suggestions dismissed." }, onFindBeats=::findBeats,
        onToggleBeat={ index -> (beatStatus as? BeatsStatus.Ready)?.let { s -> beatStatus=s.copy(chosen=toggle(s.chosen,index)) } },
        onSnapToBeats={ snap=it },
        onApplyBeatCuts={ val p=project; val s=beatStatus as? BeatsStatus.Ready; if (p!=null && s!=null) mutate(p.copy(timeline=p.splitFootageAt(s.beatsMs.filterIndexed { i,_ -> i in s.chosen }),followCrop=null)) },
        onDismissBeats={ beatStatus=BeatsStatus.Idle }, onTrackSubject=::follow,
        onStopFollowCrop={ job?.cancel(); followStatus=FollowCropStatus.Idle },
        onClearFollowCrop={ project?.let { mutate(it.copy(followCrop=null)) } }, onOpenCutoutModel={ tab="Models" }
    )
    fun close() { player.stop(); job?.cancel(); frameJob?.cancel(); rendered?.delete() }
}
private fun toggle(set: Set<Int>, index: Int) = if (index in set) set-index else set+index
fun time(ms: Long) = java.lang.String.format(java.util.Locale.US,"%d:%02d.%01d",ms/60000,(ms/1000)%60,(ms/100)%10)
