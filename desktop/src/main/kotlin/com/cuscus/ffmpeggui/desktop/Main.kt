package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyRow
import com.cuscus.ffmpeggui.ui.theme.TealLightColorScheme
import androidx.compose.ui.window.*
import com.cuscus.ffmpeggui.*
import com.cuscus.ffmpeggui.core.editing.wordBlocks
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.subtitles.*
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.theme.TealDarkColorScheme
import kotlinx.coroutines.*
import java.awt.FileDialog
import java.io.File
import java.awt.Desktop

fun main() = if (System.getenv("FFMPEGGUI_MODEL_WORKER") != null) {
    runModelWorker(System.getenv("FFMPEGGUI_MODEL_WORKER"))
} else if (System.getenv("FFMPEGGUI_NATIVE_CHECKS") != null) {
    runNativeChecks(arrayOf(System.getenv("FFMPEGGUI_NATIVE_CHECKS")))
} else if (System.getenv("FFMPEGGUI_DUB_CHECKS") != null) {
    runDubChecks(System.getenv("FFMPEGGUI_DUB_CHECKS"))
} else if (System.getenv("FFMPEGGUI_AUDIO_MARK_CHECKS") != null) {
    runAudioMarkChecks(System.getenv("FFMPEGGUI_AUDIO_MARK_CHECKS"))
} else if (System.getenv("FFMPEGGUI_LAMA_CHECKS") != null) {
    runLamaChecks(System.getenv("FFMPEGGUI_LAMA_CHECKS"))
} else if (System.getenv("FFMPEGGUI_ERASE_CHECKS") != null) {
    runEraseChecks(System.getenv("FFMPEGGUI_ERASE_CHECKS"))
} else if (System.getenv("FFMPEGGUI_MARK_CHECKS") != null) {
    runMarkChecks(System.getenv("FFMPEGGUI_MARK_CHECKS"))
} else if (System.getenv("FFMPEGGUI_TRANSLATION_CHECKS") != null) {
    runTranslationChecks(System.getenv("FFMPEGGUI_TRANSLATION_CHECKS"))
} else if (System.getenv("FFMPEGGUI_STEMS_CHECKS") != null) {
    runStemsChecks(System.getenv("FFMPEGGUI_STEMS_CHECKS"))
} else if (System.getenv("FFMPEGGUI_ISOLATED_CHECKS") != null) {
    runIsolatedChecks(System.getenv("FFMPEGGUI_ISOLATED_CHECKS"))
} else if (System.getenv("FFMPEGGUI_MODEL_CHECKS") != null) {
    runModelChecks(arrayOf(System.getenv("FFMPEGGUI_MODEL_CHECKS"),"--speech-first","--speech"))
} else application {
    val scope=rememberCoroutineScope()
    val controller=remember { EditorController(scope) }
    val preferences=remember {java.util.prefs.Preferences.userRoot().node("ffmpeggui-desktop")}
    var lightTheme by remember {mutableStateOf(preferences.getBoolean("lightTheme",false))}
    var showSettings by remember {mutableStateOf(false)}
    var showExport by remember {mutableStateOf(false)}
    var pendingDiscard by remember {mutableStateOf<(() -> Unit)?>(null)}
    fun protectEdits(action:()->Unit) {if(controller.hasUnsavedChanges || controller.busy)pendingDiscard=action else action()}
    Window(onCloseRequest={ protectEdits {controller.close();exitApplication()} }, title="FFmpegGUI · Linux development preview", state=rememberWindowState(width=1280.dp,height=850.dp),onKeyEvent={event->
        if(event.type==KeyEventType.KeyDown && !showSettings && !showExport && pendingDiscard==null && controller.error==null) {
            when {
                event.isCtrlPressed && event.key==Key.Z -> {if(!controller.busy){if(event.isShiftPressed)controller.redo() else controller.undo()};true}
                event.isCtrlPressed && event.key==Key.Y -> {if(!controller.busy)controller.redo();true}
                event.key==Key.Escape -> {controller.selectSmartClip(null);controller.status="Clip selection cleared. Tools now apply to the whole output or selected source, as shown in Apply to.";true}
                else -> false
            }
        }else false
    }) {
        LaunchedEffect(Unit) {
            System.getenv("FFMPEGGUI_OPEN")?.let { controller.open(it) }
            System.getenv("FFMPEGGUI_PROJECT")?.let {path->controller.openProject(File(path))}
            System.getenv("FFMPEGGUI_TAB")?.let { controller.tab=it }
            System.getenv("FFMPEGGUI_SCREENSHOT")?.let { path ->
                delay(System.getenv("FFMPEGGUI_CAPTURE_DELAY")?.toLongOrNull() ?: 5000)
                System.getenv("FFMPEGGUI_TAB")?.let { controller.tab=it }; delay(500)
                val screen=java.awt.Robot().createScreenCapture(java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize))
                javax.imageio.ImageIO.write(screen,"png",File(path))
                controller.close(); exitApplication()
            }
        }
        fun pick(save: Boolean = false, name: String? = null, title:String?=null): File? {
            val dialog=FileDialog(window,title ?: if (save) "Export a new file" else "Open media",if (save) FileDialog.SAVE else FileDialog.LOAD)
            if (name!=null) dialog.file=name
            dialog.isVisible=true
            return dialog.file?.let { File(dialog.directory,it) }
        }
        fun openMedia() {if(!controller.busy)protectEdits {pick()?.let {controller.open(it.path)}}}
        fun openProject() {if(!controller.busy)protectEdits {pick(title="Open project")?.let {controller.openProject(it)}}}
        fun saveProject(saveAs:Boolean=false) {
            if(controller.busy || controller.project==null)return
            val target=if(saveAs)null else controller.projectFile
            (target ?: pick(true,controller.projectFile?.path ?: "project.ffgui.json","Save project"))?.let {controller.saveProject(it)}
        }
        MaterialTheme(colorScheme=if(lightTheme)TealLightColorScheme else TealDarkColorScheme) {
            Surface(Modifier.fillMaxSize().onPreviewKeyEvent {event->
                if(event.type!=KeyEventType.KeyDown || !event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || showSettings || showExport || pendingDiscard!=null || controller.error!=null)false
                else when(event.key) {
                    Key.S->{saveProject(event.isShiftPressed);true}
                    Key.O->{if(event.isShiftPressed)openProject() else openMedia();true}
                    else->false
                }
            }) {
                BoxWithConstraints {
                val compact=maxWidth<1490.dp
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    @Composable fun RowScope.ProjectActions() {
                        OutlinedButton(onClick=::openMedia,enabled=!controller.busy) { SymbolLabel("folder_open","Open media") }
                        OutlinedButton(onClick=::openProject,enabled=!controller.busy) { SymbolLabel("folder","Open project") }
                        OutlinedButton(onClick={saveProject()},enabled=!controller.busy && controller.project!=null) { SymbolLabel("save","Save project") }
                        TextButton(onClick={saveProject(true)},enabled=!controller.busy && controller.project!=null) { Text("Save as…") }
                        OutlinedButton(onClick={ controller.undo() },enabled=!controller.busy && controller.canUndo) { SymbolLabel("undo","Undo") }
                        OutlinedButton(onClick={ controller.redo() },enabled=!controller.busy && controller.canRedo) { Icon(DesktopSymbols.vector("undo"),null,Modifier.size(20.dp).graphicsLayer(scaleX=-1f));Spacer(Modifier.width(7.dp));Text("Redo") }
                    }
                    @Composable fun RowScope.WindowActions() {
                        FilledTonalButton(onClick={showSettings=true}) {SymbolLabel("settings","Settings")}
                        Button(onClick={showExport=true},enabled=!controller.busy && controller.project!=null){SymbolLabel("file_upload","Export ${controller.project?.format?.name ?: ""}")}
                    }
                    @Composable fun RowScope.ProjectTitle() {
                        Column(Modifier.weight(1f).widthIn(min=145.dp)) {
                            Text("FFmpegGUI",style=MaterialTheme.typography.titleLarge,maxLines=1)
                            Text(if(controller.hasUnsavedChanges)"Unsaved changes" else controller.projectFile?.name ?: "Desktop preview",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        ProjectTitle()
                        if(!compact)ProjectActions()
                        WindowActions()
                    }
                    if(compact)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {ProjectActions()}
                    Text("Work in progress: not all Android editors and AI tools have been ported. Originals are never edited.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    val project=controller.project
                    if (project==null) {
                        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                Text("Your files. Your machine.",style=MaterialTheme.typography.headlineMedium)
                                Text("Open a clip to edit its timeline, find scene changes and musical beats, and export locally.")
                                Text("Offline speech and subject models are imported once from their original downloads. This is not a finished release.",style=MaterialTheme.typography.bodyMedium)
                                Button(onClick={ protectEdits {pick()?.let {file->controller.open(file.path)}} },enabled=!controller.busy) { Text("Choose a file") }
                            }
                        }
                    } else {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                DesktopToolRail(controller)
                                Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Text(File(project.sources[controller.selectedSource].path).name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                                        Text("${project.sources[controller.selectedSource].info.width} × ${project.sources[controller.selectedSource].info.height}",style=MaterialTheme.typography.labelSmall)
                                    }
                                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp)).background(Color.Black),contentAlignment=Alignment.Center) {
                                        val frame=controller.player.frame ?: controller.previewFrame ?: controller.sourceFrame
                                        if(frame!=null)Image(frame,if(controller.player.frame!=null || controller.previewFrame!=null)"Rendered edit" else "Source frame, render to inspect effects",Modifier.fillMaxSize())
                                        else Text(if(project.sources[controller.selectedSource].info.isVideo)"Reading frame…" else "Audio clip",color=Color.White)
                                    }
                                    Text(if(controller.player.frame!=null)"Rendered preview · ${time(controller.player.positionMs)} / ${time(controller.player.durationMs)}" else if(controller.previewFrame!=null)"Rendered edit. Original unchanged." else "Source preview · Play edit renders first",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(onClick={controller.stepOutput(-1)},enabled=!controller.busy){Text("−1f")}
                                        Button(onClick={controller.togglePlayback()},enabled=!controller.busy){SymbolLabel(if(controller.player.playing)"pause" else "play_arrow",if(controller.player.playing)"Pause" else "Play edit")}
                                        FilledTonalButton(onClick={controller.stepOutput(1)},enabled=!controller.busy){Text("+1f")}
                                        if(controller.rendered!=null)TextButton(onClick={controller.preview()},enabled=!controller.busy){Text("Re-render")}
                                        TextButton(onClick={controller.splitHere()},enabled=!controller.busy && !project.sources[0].info.isStill){Text("Split")}
                                        if(!compact)Spacer(Modifier.weight(1f))
                                        if(!compact)Text("%.3f s output · 25fps".format(java.util.Locale.US,(if(controller.player.engaged)controller.player.positionMs else project.outputAt(controller.position))/1000.0),style=MaterialTheme.typography.labelSmall)
                                    }
                                    if(compact)Text("%.3f s output · 25fps".format(java.util.Locale.US,(if(controller.player.engaged)controller.player.positionMs else project.outputAt(controller.position))/1000.0),style=MaterialTheme.typography.labelSmall,maxLines=1)
                                    controller.player.failure?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)}
                                }
                                Surface(Modifier.width(if(compact)320.dp else 360.dp).fillMaxHeight(),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                        Text(controller.tab,style=MaterialTheme.typography.titleLarge)
                                        ScrollArea(Modifier.weight(1f)) {
                                            if(controller.tab in listOf("Pause cuts","Scenes","Beats","Transcript","Scene stems","Audio"))DesktopScopePicker(controller,project)
                                    when(controller.tab) {
                                        "Edit" -> EditPanel(controller,project,window)
                                        "Subject cutout" -> SubjectPanel(controller,project)
                                        "Drawing" -> DrawingPanel(controller,project)
                                        "Dubbing" -> DubPanel(controller,window){name->pick(true,name)}
                                        "Magic eraser" -> ErasePanel(controller,project,window){name->pick(true,name)}
                                        "Command studio" -> StudioPanel(controller,project){name->pick(true,name)}
                                        "Clip editor" -> ClipEditorPanel(controller,project)
                                        "Subtitles" -> SubtitlesPanel(controller,project){pick()}
                                        "Image layers" -> PictureLayersPanel(controller,project){pick()?.path}
                                        "Tags" -> TagsPanel(controller,project){pick()}
                                        "Sound tracks" -> SoundTracksPanel(controller,project) {pick()?.path}
                                        "Audio" -> AudioMasterPanel(controller,project)
                                        "Text layers" -> TextLayersPanel(controller,project)
                                        "Pause cuts" -> PauseCutsPanel(controller.smartUi())
                                        "Scenes" -> SceneSplitsPanel(controller.smartUi())
                                        "Beats" -> BeatsPanel(controller.smartUi())
                                        "Follow crop" -> FollowCropPanel(controller.smartUi())
                                        "Transcript" -> TranscriptPanel(controller,project)
                                        "Translation" -> TranslationPanel(controller,project,window)
                                        "Compression" -> CompressionPanel(controller,project)
                                        "Scene stems" -> PanelCard {
                                            Text("Dialogue, music and effects",style=MaterialTheme.typography.titleLarge)
                                            Text(if(controller.smartClipId==null)"Separate the selected original file offline with the pinned Bandit int8 model. Choose Clip N to separate only an existing clip." else "Separate only the selected clip's played sound, including its speed, reverse, volume and fades. Whole-project mastering and added tracks are excluded. Exports start at zero; the timeline stays unchanged.")
                                            Text("Files over 10 minutes are rejected. A 10-minute synthetic resource test passed, not a general audio-quality test. At least 2 GiB free RAM is required. Results can contain bleed or missing sound. Review them before using them. Instrument splitting is not yet ported.")
                                            Text("Import the 40 MB model in Models first. Licence and attribution travel with the exported folder.")
                                            Button(onClick={
                                                val dialog=FileDialog(window,"Choose a new folder name for scene stems",FileDialog.SAVE)
                                                dialog.file="scene-stems-${System.currentTimeMillis()}";dialog.isVisible=true
                                                dialog.file?.let {name->
                                                    val destination=File(dialog.directory,name)
                                                    controller.work("Starting isolated scene separation…") {
                                                        val selected=controller.smartClipId
                                                        if(selected==null)controller.models.stems(project.sources[controller.selectedSource].path,DesktopModels.stemsFile,destination) {controller.status=it}
                                                        else DesktopClipAudio.use(project,selected,controller.media) {path->controller.models.stems(path,DesktopModels.stemsFile,destination) {controller.status=it}}
                                                        controller.status="Three stems saved with licences: ${destination.absolutePath}. Review the audio before use."
                                                    }
                                                }
                                            },enabled=!controller.busy && project.sources[controller.selectedSource].info.hasAudio) {Text("Export scene stems to a new folder")}
                                        }
                                        "Models" -> PanelCard {
                                            Text("Offline models",style=MaterialTheme.typography.titleLarge)
                                            Text(controller.modelStatus)
                                            Text("No cloud processing or automatic downloads. Import the original pinned files. Other weights are rejected.")
                                            OutlinedButton(onClick={ Desktop.getDesktop().browse(java.net.URI(com.cuscus.ffmpeggui.speech.SpeechModel.FILES_URL)) },enabled=!controller.busy) { Text("Open original speech downloads") }
                                            Button(onClick={
                                                val dialog=FileDialog(window,"Import speech model files",FileDialog.LOAD)
                                                dialog.isMultipleMode=true;dialog.isVisible=true
                                                if(dialog.files.isNotEmpty()) controller.work("Checking and importing speech model files…") { DesktopModels.importSpeech(dialog.files.toList());controller.modelStatus=DesktopModels.present();controller.status=controller.modelStatus }
                                            },enabled=!controller.busy) { Text("Import speech files") }
                                            Text("Select encoder.int8.onnx, decoder.int8.onnx and joiner.int8.onnx together. The original token list is included. About 670 MB on disk; speech needs substantial free RAM.",style=MaterialTheme.typography.bodySmall)
                                            OutlinedButton(onClick={ Desktop.getDesktop().browse(java.net.URI(com.cuscus.ffmpeggui.cutout.CutoutModel.FAST.downloadUrl)) },enabled=!controller.busy) { Text("Open U2-NetP download") }
                                            Button(onClick={ pick()?.let { file -> controller.work("Checking and importing subject model…") { DesktopModels.importCutout(file);controller.modelStatus=DesktopModels.present();controller.status=controller.modelStatus } } },enabled=!controller.busy) { Text("Import u2netp.onnx") }
                                            OutlinedButton(onClick={Desktop.getDesktop().browse(java.net.URI(DesktopStems.URL))},enabled=!controller.busy) {Text("Open original Bandit int8 download")}
                                            Button(onClick={pick()?.let {file->controller.work("Checking and importing scene-stem model…") {DesktopModels.importStems(file);controller.modelStatus=DesktopModels.present();controller.status=controller.modelStatus}}},enabled=!controller.busy) {Text("Import Bandit scene-stem model")}
                                            Text("Bandit v2 by Karn N. Watcharasupat, Chih-Wei Wu and Iroro Orife. Model: CC BY-SA 4.0. Code: Apache 2.0. Exported WAVs include the original licence and provenance notices.",style=MaterialTheme.typography.bodySmall)
                                            Text("Models and licences: NVIDIA Parakeet, CC BY 4.0; U2-NetP by Xuebin Qin et al., Apache 2.0. See the source project model record for attributions.",style=MaterialTheme.typography.bodySmall)
                                            OutlinedButton(onClick={ Desktop.getDesktop().browse(java.net.URI("https://github.com/whitegeraph-hue/FFmpegGUI/blob/linux-desktop/docs/MODELS.md")) }) { Text("Model licences and sources") }
                                        }
                                    }
                                        }
                                    }
                                }
                            }
                            DesktopTimeline(controller,project,onAdd={pick()?.let {controller.append(it.path)}})
                        }
                    }
                    if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(controller.status,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                        if (controller.busy) TextButton(onClick={ controller.status="Stopping the local process…";controller.job?.cancel() }) { Text("Stop") }
                    }
                    if(showExport && project!=null) {
                        val draftController=remember {EditorController(scope).apply {this.project=project}}
                        DisposableEffect(Unit){onDispose {draftController.close()}}
                        val draft=draftController.project ?: project
                        AlertDialog(onDismissRequest={showExport=false},title={Text("Export · ${draft.format.name}")},text={
                            ScrollArea(Modifier.heightIn(max=400.dp)) {ExportOptions(draftController,draft)}
                        },confirmButton={Button(onClick={
                            val file=pick(true,"${File(project.sources[0].path).nameWithoutExtension}-edited.${draft.format.extension}")
                            if(file!=null && (!file.exists() || javax.swing.JOptionPane.showConfirmDialog(window,"Replace ${file.name}?","Existing output",javax.swing.JOptionPane.YES_NO_OPTION)==0)) {
                                showExport=false;controller.export(file,draft)
                            }
                        }){Text("Choose file & export")}},dismissButton={TextButton(onClick={showExport=false}){Text("Cancel")}})
                    }
                    if(showSettings)AlertDialog(onDismissRequest={showSettings=false},title={Row(verticalAlignment=Alignment.CenterVertically){SymbolLabel("settings","Settings")}},text={
                        ScrollArea(Modifier.heightIn(max=470.dp)) {
                            Text("Appearance",style=MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment=Alignment.CenterVertically){Switch(lightTheme,{lightTheme=it;preferences.putBoolean("lightTheme",it)});Spacer(Modifier.width(10.dp));Text("Light theme")}
                            Text("Offline models",style=MaterialTheme.typography.titleMedium)
                            Text("Import and manage speech, subject and sound models locally. Models are never downloaded automatically.")
                            FilledTonalButton(onClick={controller.tab="Models";showSettings=false}){Text("Manage models")}
                            Text("Export settings",style=MaterialTheme.typography.titleMedium)
                            TextButton(onClick={showExport=true;showSettings=false}){Text("Open format & quality")}

                            Text("Preview",style=MaterialTheme.typography.titleMedium)
                            Text("Play edit renders the edited result first, then plays it with sound. Edits invalidate that render. Source view does not show effects live.")
                            Text("Shortcuts: Ctrl+S saves to the current project file (asks on first save); Ctrl+Shift+S saves a copy to a new destination. Ctrl+O opens media; Ctrl+Shift+O opens a project. Ctrl+Z undoes the last edit; Ctrl+Shift+Z or Ctrl+Y redoes. A new edit clears Redo. Undo and Redo restore clip selection and position. Esc clears clip selection. After clicking the timeline: Space plays, arrows step one output frame, S splits, D duplicates, Delete removes. Project Undo/Redo do not run while typing in fields. These timeline keys do not run while typing in fields. Click inside a clip to select and seek; double-click to open its controls. Use timeline arrows to reorder. Drag reordering is not available.",style=MaterialTheme.typography.bodySmall)
                            Text("Development preview. Not every mobile feature is available yet. Original files are never edited.",style=MaterialTheme.typography.bodySmall)
                        }
                    },confirmButton={TextButton(onClick={showSettings=false}){Text("Done")}})
                    pendingDiscard?.let { action -> AlertDialog(onDismissRequest={pendingDiscard=null},title={Text(if(controller.busy)"Local work is still running" else "Unsaved project changes")},text={Text(if(controller.busy)"Closing now stops the current local task. Any unsaved project changes will be lost. Keep editing to wait or save first." else "Save your project first to keep these edits. Discarding changes cannot be undone after opening another file or closing the app.")},confirmButton={TextButton(onClick={pendingDiscard=null;action()}){Text(if(controller.busy)"Stop & close" else "Discard changes")}},dismissButton={TextButton(onClick={pendingDiscard=null}){Text("Keep editing")}}) }
                    controller.error?.let { message -> AlertDialog(onDismissRequest={ controller.error=null },title={ Text("Could not finish") },text={ Box(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())) { Text(message) } },confirmButton={ TextButton(onClick={ controller.error=null }) { Text("Close") } }) }
                }
            }
        }
    }
    }
}

@Composable private fun ScrollArea(modifier:Modifier=Modifier,spacing:Int=12,content:@Composable ColumnScope.()->Unit) {
    val scroll=rememberScrollState()
    Box(modifier) {
        Column(Modifier.fillMaxWidth().padding(end=10.dp).verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(spacing.dp),content=content)
        VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(7.dp))
    }
}
private val desktopGroups=linkedMapOf(
    "Edit" to listOf("Clip editor","Scenes","Pause cuts","Beats","Follow crop"),
    "Audio" to listOf("Audio","Sound tracks","Scene stems","Dubbing"),
    "Text" to listOf("Transcript","Subtitles","Text layers","Translation"),
    "Picture" to listOf("Image layers","Drawing","Subject cutout","Magic eraser"),
    "Export" to listOf("Edit","Compression","Tags","Command studio"),
    "Models" to listOf("Models")
)
@Composable private fun DesktopToolRail(c:EditorController) {
    val group=desktopGroups.entries.firstOrNull {c.tab in it.value}?.key ?: "Edit"
    BoxWithConstraints(Modifier.width(154.dp).fillMaxHeight()) {
    val short=maxHeight<430.dp
    ScrollArea(Modifier.fillMaxSize(),spacing=if(short)2 else 12) {
        if(short) {
            var groups by remember {mutableStateOf(false)}
            Box {
                FilledTonalButton(onClick={groups=true},modifier=Modifier.fillMaxWidth()) {Text("$group ▾")}
                DropdownMenu(groups,{groups=false}) {desktopGroups.forEach {(name,tools)->DropdownMenuItem(text={Text(name)},onClick={c.tab=tools.first();groups=false})}}
            }
        }
        if(!short) {
        Text("TOOLS",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(8.dp))
        desktopGroups.entries.toList().chunked(2).forEach {pair->
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                pair.forEach {(name,tools)->
                    val selected=group==name
                    Surface(onClick={c.tab=tools.first()},shape=RoundedCornerShape(14.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.weight(1f)) {
                        Column(Modifier.padding(vertical=8.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            val symbol=mapOf("Edit" to "edit","Audio" to "graphic_eq","Text" to "title","Picture" to "image","Export" to "file_upload","Models" to "model_training").getValue(name)
                            Symbol(symbol+if(selected)"_filled" else "",size=22)
                            Text(name,style=MaterialTheme.typography.labelLarge,maxLines=1)
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical=6.dp))
        }
        desktopGroups[group].orEmpty().forEach {tool->
            Surface(onClick={c.tab=tool},shape=RoundedCornerShape(14.dp),color=if(c.tab==tool)MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal=10.dp,vertical=if(short)7.dp else 9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Symbol(when(tool){"Clip editor"->"content_cut";"Scenes"->"movie";"Pause cuts"->"waves";"Beats"->"graphic_eq";"Follow crop"->"person_search";"Transcript"->"subtitles";"Drawing"->"draw";"Dubbing"->"mic";else->"tune"},size=18)
                    Text(if(tool=="Edit")"Format & quality" else tool,style=MaterialTheme.typography.labelLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
    }
}
@Composable private fun DesktopScopePicker(c:EditorController,p:DesktopProject) {
    var expanded by remember {mutableStateOf(false)}
    val number=p.timeline.clips.indexOfFirst {it.id==c.smartClipId}
    Text("Apply to · select a timeline clip to change scope",style=MaterialTheme.typography.labelSmall)
    Box {
        FilledTonalButton(onClick={expanded=true},enabled=!c.busy,modifier=Modifier.fillMaxWidth()) {Text(if(number>=0)"Clip ${number+1}" else if(c.tab=="Audio")"Whole output" else "Selected source · all clips")}
        DropdownMenu(expanded,{expanded=false}) {
            DropdownMenuItem(text={Text(if(c.tab=="Audio")"Whole output" else "Selected source · all clips")},onClick={c.selectSmartClip(null);expanded=false})
            p.timeline.clips.forEachIndexed {i,clip->DropdownMenuItem(text={Text("Clip ${i+1} · ${time(clip.lengthMs)}")},onClick={c.selectSmartClip(clip.id);expanded=false})}
        }
    }
    Text(if(number>=0)"Only this clip occurrence. Other clips stay unchanged." else if(c.tab=="Audio")"Final mix, including every source and added sound." else "All occurrences of the selected source. Choose a clip to limit changes.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(p.sources.size>1) {
        var sources by remember {mutableStateOf(false)}
        Box {TextButton(onClick={sources=true}){Text("Source: "+File(p.sources[c.selectedSource].path).name.take(24))};DropdownMenu(sources,{sources=false}) {p.sources.forEachIndexed {i,source->DropdownMenuItem(text={Text(File(source.path).name)},onClick={c.selectSource(i);sources=false})}}}
    }
}

@Composable
private fun EditPanel(c: EditorController,p: DesktopProject,window:java.awt.Frame) {
    StabilizationPanel(c,p)
    ExportOptions(c,p)
    if(p.sources[0].info.isVideo && !p.sources[0].info.isStill)PanelCard {
        Text("Canvas",style=MaterialTheme.typography.titleMedium)
        FlowRowCompat {CanvasRatio.entries.forEach {v->FilterChip(p.canvas==v,{c.mutate(p.copy(canvas=v))},label={Text(v.name)},enabled=!c.busy)}}
        FlowRowCompat {CanvasFit.entries.forEach {v->FilterChip(p.canvasFit==v,{c.mutate(p.copy(canvasFit=v))},label={Text(v.name)},enabled=!c.busy)}}
    }
    PanelCard {
        Text("Picture and sound",style=MaterialTheme.typography.titleMedium)
        ValueSlider("Speed",p.effects.speed,.25f..4f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(speed=it))) }
        ValueSlider("Volume",p.effects.volume,0f..2f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(volume=it))) }
        if (p.sources[0].info.isVideo || p.sources[0].info.isStill) {
            ValueSlider("Brightness",p.effects.color.brightness,-1f..1f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(color=p.effects.color.copy(brightness=it)))) }
            ValueSlider("Contrast",p.effects.color.contrast,0f..2f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(color=p.effects.color.copy(contrast=it)))) }
            ValueSlider("Saturation",p.effects.color.saturation,0f..2f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(color=p.effects.color.copy(saturation=it)))) }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ c.mutate(p.copy(effects=p.effects.copy(rotation=Rotation.entries[(p.effects.rotation.ordinal+1)%4]))) },enabled=!c.busy) { Text("Rotate ${p.effects.rotation.degrees}°") }
                OutlinedButton(onClick={ c.mutate(p.copy(effects=p.effects.copy(flipHorizontal=!p.effects.flipHorizontal))) },enabled=!c.busy) { Text(if(p.effects.flipHorizontal) "Unflip" else "Flip horizontal") }
            }
        }
    }
    GeometryPanel(c,p)
    PhotoPanel(c,p,window)
    ArtPanel(c,p)
    PanelCard {
        Text("Command preview",style=MaterialTheme.typography.titleMedium)
        Text("Generated by the unchanged Kotlin CommandBuilder used on Android.",style=MaterialTheme.typography.bodySmall)
        val command=remember(p) { runCatching { c.exporter.command(p,"output.${p.format.extension}").arguments.joinToString(" ") }.getOrElse { it.message.orEmpty() } }
        SelectionContainerCompat(command)
    }
}
@Composable private fun ExportOptions(c:EditorController,p:DesktopProject) {
    PanelCard {
        Text("Export format",style=MaterialTheme.typography.titleMedium)
        val formats=when { p.sources[0].info.isStill -> listOf(OutputFormat.JPG,OutputFormat.PNG,OutputFormat.WEBP,OutputFormat.BMP,OutputFormat.TIFF,OutputFormat.SVG,OutputFormat.ASCII,OutputFormat.ANSI)
            !p.sources[0].info.isVideo -> listOf(OutputFormat.MP3,OutputFormat.M4A,OutputFormat.OGG,OutputFormat.OPUS,OutputFormat.FLAC,OutputFormat.WAV,OutputFormat.SRT,OutputFormat.TXT)
            else -> listOf(OutputFormat.MP4,OutputFormat.MKV,OutputFormat.AVI,OutputFormat.GIF,OutputFormat.MP3,OutputFormat.WAV,OutputFormat.SRT,OutputFormat.TXT) }
        FlowRowCompat {
            formats.forEach { format -> FilterChip(p.format==format,{ c.mutate(p.copy(format=format)) },label={ Text(format.name) },enabled=!c.busy) }
        }
    }
    if(!p.format.isTranscript)EncodingPanel(c,p)
}
@Composable private fun SelectionContainerCompat(text: String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(text,style=MaterialTheme.typography.bodySmall) } }
@Composable private fun ValueSlider(label:String,value:Float,range:ClosedFloatingPointRange<Float>,enabled:Boolean,onChange:(Float)->Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    Text("$label · ${"%.2f".format(java.util.Locale.US,draft)}",style=MaterialTheme.typography.labelLarge)
    Slider(draft,{ draft=it },valueRange=range,onValueChangeFinished={ onChange(draft) },enabled=enabled)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TranscriptPanel(c:EditorController,p:DesktopProject) {
    Text("Automatic transcripts can miss speech, sometimes an entire section. Play and review before using word cuts. A missing-audio warning only detects some empty non-silent chunks, not all omissions.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    PanelCard {
        Text("Transcript",style=MaterialTheme.typography.titleLarge)
        Text("Word taps seek the source on the current timeline. Line edits use the same cut/keep logic as Android.")
        if(DesktopSpeech.words(p,c.selectedSource,c.smartClipId).isEmpty()) Text("No lines yet. Import the speech model in Models, then listen to this file.")
        Button(onClick={ c.listen() },enabled=!c.busy && p.sources[c.selectedSource].info.hasAudio) { Text(if(DesktopSpeech.words(p,c.selectedSource,c.smartClipId).isEmpty()) if(c.smartClipId==null) "Listen to this source" else "Listen to selected clip" else "Listen again in this scope") }
        Text("Listen uses raw audio before speed, reverse and effects. Clip words override source words only on that occurrence. With no clip selected, this view shows source words only. Source Listen does not replace clip words. Reselect a clip to see its words. After expanding a trim, Listen again to recognize newly included audio. Stop ends the isolated local model process.",style=MaterialTheme.typography.bodySmall)
        val blocks=remember(DesktopSpeech.words(p,c.selectedSource,c.smartClipId)) { DesktopSpeech.words(p,c.selectedSource,c.smartClipId).wordBlocks().second }
        blocks.forEach { block ->
            HorizontalDivider()
            Text("Line at ${time(block.startMs)}",style=MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                block.words.forEach { word -> TextButton(onClick={ c.seekWord(word) },contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) { Text(word.text) } }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ c.transcriptCut(block,false) },enabled=!c.busy) { Text("Cut from video") }
                OutlinedButton(onClick={ c.transcriptCut(block,true) },enabled=!c.busy) { Text(if(p.sources.size>1)"Keep line in this source" else "Keep only this") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FlowRowCompat(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) { content() }
}

@Composable private fun EncodingPanel(c:EditorController,p:DesktopProject) {
    PanelCard {
        Text("Encoding",style=MaterialTheme.typography.titleMedium)
        Text("Export runs locally. Higher quality makes larger files. Lossless formats preserve quality without a quality slider.",style=MaterialTheme.typography.bodySmall)
        if(p.format in listOf(OutputFormat.MP4,OutputFormat.MKV,OutputFormat.AVI))ValueSlider("Quality",p.encoding.quality,0f..1f,!c.busy) { c.mutate(p.copy(encoding=p.encoding.copy(quality=it))) }
        if(p.format==OutputFormat.GIF) {
            val g=p.encoding.gif
            ValueSlider("GIF width",g.width.toFloat(),120f..960f,!c.busy) {c.mutate(p.copy(encoding=p.encoding.copy(gif=g.copy(width=it.toInt()))))}
            ValueSlider("GIF fps",g.fps.toFloat(),5f..30f,!c.busy) {c.mutate(p.copy(encoding=p.encoding.copy(gif=g.copy(fps=it.toInt()))))}
            ValueSlider("Palette colours",g.colors.toFloat(),2f..256f,!c.busy) {c.mutate(p.copy(encoding=p.encoding.copy(gif=g.copy(colors=it.toInt()))))}
            FlowRowCompat { GifDither.entries.forEach { d -> FilterChip(g.dither==d,{c.mutate(p.copy(encoding=p.encoding.copy(gif=g.copy(dither=d))))},label={Text(d.name.lowercase())},enabled=!c.busy) } }
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(g.loops==0,{c.mutate(p.copy(encoding=p.encoding.copy(gif=g.copy(loops=if(it)0 else -1))))},enabled=!c.busy);Text("Loop forever");Checkbox(g.smaller,{c.mutate(p.copy(encoding=p.encoding.copy(gif=g.copy(smaller=it))))},enabled=!c.busy);Text("Smaller file")}
        } else if(p.format.hasVideo) {
            Text("Output height")
            FlowRowCompat { Resolution.entries.forEach { r -> FilterChip(p.encoding.resolution==r,{ c.mutate(p.copy(encoding=p.encoding.copy(resolution=r))) },label={ Text(if(r==Resolution.ORIGINAL) "Original" else "${r.height}p") },enabled=!c.busy) } }
            Text("Frame rate")
            FlowRowCompat { Framerate.entries.forEach { r -> FilterChip(p.encoding.framerate==r,{ c.mutate(p.copy(encoding=p.encoding.copy(framerate=r))) },label={ Text(if(r==Framerate.ORIGINAL) "Original" else "${r.value} fps") },enabled=!c.busy) } }
            Text("CPU speed / file size")
            FlowRowCompat { CpuEncoderSpeed.entries.forEach { r -> FilterChip(p.encoding.cpuSpeed==r,{ c.mutate(p.copy(encoding=p.encoding.copy(cpuSpeed=r))) },label={ Text(r.name.lowercase()) },enabled=!c.busy) } }
        }
        if(p.format.isLossyAudio) ValueSlider("Audio bitrate (kbps)",p.encoding.audio.bitrateKbps.toFloat(),64f..320f,!c.busy) { c.mutate(p.copy(encoding=p.encoding.copy(audio=p.encoding.audio.copy(bitrateKbps=it.toInt())))) }
        if(p.format.isLosslessAudio) Row { listOf(16,24).forEach { depth -> FilterChip(p.encoding.audio.bitDepth==depth,{ c.mutate(p.copy(encoding=p.encoding.copy(audio=p.encoding.audio.copy(bitDepth=depth)))) },label={Text("$depth bit")},enabled=!c.busy) } }
    }
}
@Composable private fun SubtitlesPanel(c:EditorController,p:DesktopProject,pick:()->File?) {
    val style=p.subtitleStyle ?: SubtitleStyle()
    PanelCard {
        Text("Subtitles follow the selected source through cuts, speed and reverse.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={pick()?.let {file->c.work("Reading subtitle file…"){val imported=DesktopSubtitles.import(file,c.selectedSource);c.mutate(p.copy(externalSubtitles=imported));c.status="Subtitle file imported. Render preview to inspect timing."}}},enabled=!c.busy){Text("Import SRT / ASS / SSA")}
        p.externalSubtitles?.let {v->
            Text("${v.name}, source ${v.sourceIndex+1}")
            if(v.enabled && DesktopSubtitles.cuesOnTimeline(v,p.request()).isEmpty())Text("No imported cues remain on this edit. Export will contain no imported subtitles.",color=MaterialTheme.colorScheme.error)
            Row(verticalAlignment=Alignment.CenterVertically) {Switch(v.enabled,{c.mutate(p.copy(externalSubtitles=v.copy(enabled=it)))},enabled=!c.busy);Text("Use imported subtitles",Modifier.weight(1f))}
            TextButton(onClick={c.mutate(p.copy(externalSubtitles=null))},enabled=!c.busy){Text("Remove imported subtitles")}
        }
        val canStyle=p.externalSubtitles?.enabled==true || p.wordsByClip.values.any {it.isNotEmpty()} || p.sources.indices.any {p.sourceWords(it).isNotEmpty()}
        Row(verticalAlignment=Alignment.CenterVertically) {
            Switch(p.subtitleStyle!=null,{c.mutate(p.copy(subtitleStyle=if(it)style else null))},enabled=!c.busy && canStyle)
            Text(if(p.externalSubtitles?.enabled==true)"Style SRT (ASS keeps its own look)" else "Burn spoken subtitles")
        }
        Text("${p.sources.indices.sumOf {p.sourceWords(it).size}} source words and ${p.wordsByClip.values.sumOf {it.size}} clip-bound words. Imported subtitles take priority while enabled. SRT/TXT transcript export still uses spoken words.",style=MaterialTheme.typography.bodySmall)
        Text("Style presets")
        FlowRowCompat { SubtitleStyle.PRESETS.forEach { preset -> FilterChip(SubtitlePreset.of(style)==preset,{c.mutate(p.copy(subtitleStyle=preset.apply(style)))},label={Text(preset.name.lowercase())},enabled=!c.busy && canStyle) } }
        if(p.subtitleStyle!=null) {
            ValueSlider("Text size",style.size,SubtitleStyle.MIN_SIZE..SubtitleStyle.MAX_SIZE,!c.busy) {c.mutate(p.copy(subtitleStyle=style.copy(size=it)))}
            ValueSlider("Horizontal position",style.x,0f..1f,!c.busy) {c.mutate(p.copy(subtitleStyle=style.copy(x=it)))}
            ValueSlider("Vertical position",style.y,0f..1f,!c.busy) {c.mutate(p.copy(subtitleStyle=style.copy(y=it)))}
            FlowRowCompat { WordHighlight.entries.forEach { highlight -> FilterChip(style.highlight==highlight,{c.mutate(p.copy(subtitleStyle=style.copy(highlight=highlight)))},label={Text(highlight.name.lowercase())},enabled=!c.busy) } }
            FlowRowCompat { SubtitleLook.entries.forEach { look -> FilterChip(style.look==look,{c.mutate(p.copy(subtitleStyle=style.copy(look=look)))},label={Text(look.name.lowercase())},enabled=!c.busy) } }
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(style.bold,{c.mutate(p.copy(subtitleStyle=style.copy(bold=it)))},enabled=!c.busy);Text("Bold");Checkbox(style.uppercase,{c.mutate(p.copy(subtitleStyle=style.copy(uppercase=it)))},enabled=!c.busy);Text("Uppercase") }
            Text("Font: "+(style.fontFile?.let {File(it).name} ?: "DejaVu Sans"),style=MaterialTheme.typography.bodySmall)
            if(style.fontFile?.let { !File(it).isFile || !File(it).canRead() }==true)Text("Font missing or unreadable. Rendering uses DejaVu Sans until you choose the font again.",color=MaterialTheme.colorScheme.error)
            Row {
                OutlinedButton(onClick={pick()?.let {file->c.work("Checking font…"){DesktopFonts.validate(file);c.mutate(p.copy(subtitleStyle=style.copy(fontFile=file.absolutePath)));c.status="Font selected. Render preview to see the result."}}},enabled=!c.busy) {Text("Choose TTF / OTF")}
                TextButton(onClick={c.mutate(p.copy(subtitleStyle=style.copy(fontFile=null)))},enabled=!c.busy) {Text("Default font")}
            }
            Text("Custom fonts stay at their original paths. Missing fonts use DejaVu Sans. No fonts are embedded in project files.",style=MaterialTheme.typography.bodySmall)
            ArgbField("Text ARGB",style.textColor,!c.busy){c.mutate(p.copy(subtitleStyle=style.copy(textColor=it)))}
            ArgbField("Highlight ARGB",style.highlightColor,!c.busy){c.mutate(p.copy(subtitleStyle=style.copy(highlightColor=it)))}
            ArgbField("Shadow ARGB",style.shadowColor,!c.busy){c.mutate(p.copy(subtitleStyle=style.copy(shadowColor=it)))}
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(style.dropShadow,{c.mutate(p.copy(subtitleStyle=style.copy(dropShadow=it)))},enabled=!c.busy);Text("Drop shadow");Checkbox(style.maxLines==2,{c.mutate(p.copy(subtitleStyle=style.copy(maxLines=if(it)2 else 1)))},enabled=!c.busy);Text("Two lines")}

        }
    }
}

@Composable private fun ArgbField(label:String,value:Long,enabled:Boolean,update:(Long)->Unit) {
    var text by remember(value) {mutableStateOf("%08X".format(value and 0xffffffffL))}
    val valid=text.matches(Regex("[0-9a-fA-F]{8}"))
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(24.dp).background(Color(value.toInt())))
        OutlinedTextField(text,{text=it.take(8);if(text.matches(Regex("[0-9a-fA-F]{8}")))update(text.toLong(16))},label={Text(label+" (AARRGGBB)")},modifier=Modifier.isolateProjectHistory(),enabled=enabled,isError=!valid,singleLine=true)
    }
}

@Composable private fun StabilizationPanel(c:EditorController,p:DesktopProject) {
    PanelCard {
        Text("Video stabilization",style=MaterialTheme.typography.titleLarge)
        Text("Two local passes use the original vid.stab command builder. First analyses the edited joined footage, second smooths it. Requires vidstabdetect/vidstabtransform in installed FFmpeg. Auto zoom may crop edges. Render preview to inspect.",style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically){Switch(p.stabilization!=null,{c.mutate(p.copy(stabilization=if(it)Stabilization() else null))},enabled=!c.busy && p.sources.all {it.info.isVideo && !it.info.isStill});Text("Stabilize video")}
        p.stabilization?.let {v->
            ValueSlider("Shakiness",v.shakiness.toFloat(),1f..10f,!c.busy){c.mutate(p.copy(stabilization=v.copy(shakiness=it.toInt())))}
            ValueSlider("Smoothing frames",v.smoothing.toFloat(),0f..100f,!c.busy){c.mutate(p.copy(stabilization=v.copy(smoothing=it.toInt())))}
        }
    }
}

@Composable private fun AudioMasterPanel(c:EditorController,p:DesktopProject) {
    val selected=p.timeline.clips.firstOrNull {it.id==c.smartClipId}
    val a=selected?.effects?.audioEffects ?: p.audioEffects
    fun change(value:AudioEffects) {c.mutate(if(selected==null)p.copy(audioEffects=value)else p.copy(timeline=p.timeline.updateEffects(selected.id){it.copy(audioEffects=value)}))}
    PanelCard {
        Text(if(selected==null)"Audio mastering" else "Selected clip audio",style=MaterialTheme.typography.titleLarge)
        if(selected!=null) {
            Text("This clip only, before the join and whole-project mastering. Export or render to hear it. Tails stop at the clip boundary. Splitting a normalized clip normalizes each new clip separately and can change their levels.")
            Row {Checkbox(selected.effects.normalizeAudio,{value->c.mutate(p.copy(timeline=p.timeline.updateEffects(selected.id){it.copy(normalizeAudio=value)}))},enabled=!c.busy);Text("Normalize selected clip")}
        }
        Text("The original ten-band EQ and mastering filter chain. Render edited preview to hear the actual result. Live DSP preview and waveform editing remain unported; sound tracks are edited separately.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={change(AudioEffects.NONE)},enabled=!c.busy) {Text(if(selected==null)"Reset mastering" else "Reset clip effects")}
        var showEq by remember {mutableStateOf(false)}
        TextButton(onClick={showEq=!showEq}){Text(if(showEq)"Hide 10-band equalizer" else "Show 10-band equalizer")}
        if(showEq)AudioEffects.EQ_BANDS_HZ.forEachIndexed { index,hz -> ValueSlider("$hz Hz (dB)",a.equalizerGains[index],-12f..12f,!c.busy) { value ->change(a.copy(equalizer=a.equalizerGains.toMutableList().also {it[index]=value}))} }
        ValueSlider("Low cut Hz (0 = off)",a.lowCutHz.toFloat(),0f..500f,!c.busy) {change(a.copy(lowCutHz=it.toInt()))}
        ValueSlider("High cut Hz (0 = off)",a.highCutHz.toFloat(),0f..20000f,!c.busy) {change(a.copy(highCutHz=it.toInt()))}
        ValueSlider("Pitch semitones",a.pitchSemitones,-12f..12f,!c.busy) {change(a.copy(pitchSemitones=it))}
    }
    PanelCard {
        Text("Dynamics and space",style=MaterialTheme.typography.titleMedium)
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.gate!=null,{change(a.copy(gate=if(it)NoiseGate() else null))},enabled=!c.busy);Text("Noise gate")}
        a.gate?.let { g ->
            ValueSlider("Gate threshold dB",g.thresholdDb,-70f..-10f,!c.busy) {change(a.copy(gate=g.copy(thresholdDb=it)))}
            ValueSlider("Gate reduction dB",g.reductionDb,-60f..0f,!c.busy) {change(a.copy(gate=g.copy(reductionDb=it)))}
        }
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.compressor!=null,{change(a.copy(compressor=if(it)Compressor() else null))},enabled=!c.busy);Text("Compressor")}
        a.compressor?.let { comp ->ValueSlider("Threshold dB",comp.thresholdDb,-40f..0f,!c.busy) {change(a.copy(compressor=comp.copy(thresholdDb=it)))};ValueSlider("Ratio",comp.ratio,1f..12f,!c.busy) {change(a.copy(compressor=comp.copy(ratio=it)))} }
        a.compressor?.let {comp->
            ValueSlider("Compressor attack ms",comp.attackMs,1f..200f,!c.busy) {change(a.copy(compressor=comp.copy(attackMs=it)))}
            ValueSlider("Compressor release ms",comp.releaseMs,10f..1000f,!c.busy) {change(a.copy(compressor=comp.copy(releaseMs=it)))}
            ValueSlider("Compressor makeup dB",comp.makeupDb,0f..24f,!c.busy) {change(a.copy(compressor=comp.copy(makeupDb=it)))}
        }
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.limiter!=null,{change(a.copy(limiter=if(it)Limiter() else null))},enabled=!c.busy);Text("Limiter")}
        a.limiter?.let {limit->ValueSlider("Limiter ceiling dB",limit.ceilingDb,-12f..0f,!c.busy) {change(a.copy(limiter=limit.copy(ceilingDb=it)))}}
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.echo!=null,{change(a.copy(echo=if(it)Echo() else null))},enabled=!c.busy);Text("Echo")}
        a.echo?.let { echo ->ValueSlider("Echo delay ms",echo.delayMs.toFloat(),20f..1000f,!c.busy) {change(a.copy(echo=echo.copy(delayMs=it.toInt())))};ValueSlider("Echo feedback",echo.feedback,0f..0.8f,!c.busy) {change(a.copy(echo=echo.copy(feedback=it)))} }
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.reverb!=null,{change(a.copy(reverb=if(it)Reverb() else null))},enabled=!c.busy);Text("Reverb")}
        a.reverb?.let { r ->FlowRowCompat {RoomSize.entries.forEach { size ->FilterChip(r.size==size,{change(a.copy(reverb=r.copy(size=size)))},label={Text(size.name.lowercase())},enabled=!c.busy)}};ValueSlider("Reverb mix",r.mix,0f..1f,!c.busy) {change(a.copy(reverb=r.copy(mix=it)))} }
        ValueSlider("Lo-fi bits (0 = off)",a.lofiBits.toFloat(),0f..16f,!c.busy) {change(a.copy(lofiBits=it.toInt().let {n->if(n<2)0 else n}))}
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.modulation!=null,{change(a.copy(modulation=if(it)Modulation(ModulationKind.CHORUS) else null))},enabled=!c.busy);Text("Modulation")}
        a.modulation?.let {m->
            FlowRowCompat {ModulationKind.entries.forEach {kind->FilterChip(m.kind==kind,{change(a.copy(modulation=m.copy(kind=kind)))},label={Text(kind.name.lowercase())},enabled=!c.busy)}}
            ValueSlider("Modulation rate Hz",m.rateHz,.1f..10f,!c.busy) {change(a.copy(modulation=m.copy(rateHz=it)))}
            ValueSlider("Modulation depth",m.depth,0f..1f,!c.busy) {change(a.copy(modulation=m.copy(depth=it)))}
        }
        ValueSlider("Stereo width",a.stereo.width,0f..2f,!c.busy) {change(a.copy(stereo=a.stereo.copy(width=it)))}
        ValueSlider("Stereo balance",a.stereo.balance,-1f..1f,!c.busy) {change(a.copy(stereo=a.stereo.copy(balance=it)))}
        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(a.stereo.mono,{change(a.copy(stereo=a.stereo.copy(mono=it)))},enabled=!c.busy);Text("Mono");Checkbox(a.stereo.swapSides,{change(a.copy(stereo=a.stereo.copy(swapSides=it)))},enabled=!c.busy);Text("Swap sides")}
    }
}

@Composable private fun GeometryPanel(c:EditorController,p:DesktopProject) {
    if(!p.sources[0].info.isVideo && !p.sources[0].info.isStill)return
    PanelCard {
        Text("Framing and detail",style=MaterialTheme.typography.titleMedium)
        Text("These apply to the rendered output. The source-frame view stays unedited.",style=MaterialTheme.typography.bodySmall)
        val crop=p.effects.crop ?: CropRect()
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(p.effects.crop!=null,{c.mutate(p.copy(effects=p.effects.copy(crop=if(it)CropRect(.1f,.1f,.8f,.8f) else null),followCrop=null))},enabled=!c.busy);Text("Crop")}
        if(p.effects.crop!=null) {
            ValueSlider("Crop left",crop.left,0f..0.9f,!c.busy) {val left=it;c.mutate(p.copy(effects=p.effects.copy(crop=crop.copy(left=left,width=crop.width.coerceAtMost(1-left))),followCrop=null))}
            ValueSlider("Crop top",crop.top,0f..0.9f,!c.busy) {val top=it;c.mutate(p.copy(effects=p.effects.copy(crop=crop.copy(top=top,height=crop.height.coerceAtMost(1-top))),followCrop=null))}
            ValueSlider("Crop width",crop.width,.1f..(1-crop.left).coerceAtLeast(.1f),!c.busy) {c.mutate(p.copy(effects=p.effects.copy(crop=crop.copy(width=it)),followCrop=null))}
            ValueSlider("Crop height",crop.height,.1f..(1-crop.top).coerceAtLeast(.1f),!c.busy) {c.mutate(p.copy(effects=p.effects.copy(crop=crop.copy(height=it)),followCrop=null))}
        }
        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(p.effects.flipVertical,{c.mutate(p.copy(effects=p.effects.copy(flipVertical=it)))},enabled=!c.busy);Text("Flip vertical");Checkbox(p.effects.reversed,{c.mutate(p.copy(effects=p.effects.copy(reversed=it),followCrop=null))},enabled=!c.busy && !p.sources[0].info.isStill);Text("Reverse")}
        val color=p.effects.color
        ValueSlider("Sharpness",color.sharpness,-1f..1f,!c.busy) {c.mutate(p.copy(effects=p.effects.copy(color=color.copy(sharpness=it))))}
        ValueSlider("Gaussian blur",color.gaussianBlur,0f..ColorSettings.BLUR_MAX,!c.busy) {c.mutate(p.copy(effects=p.effects.copy(color=color.copy(gaussianBlur=it))))}
        ValueSlider("Vignette",color.vignette,0f..1f,!c.busy) {c.mutate(p.copy(effects=p.effects.copy(color=color.copy(vignette=it))))}
        ValueSlider("Film grain",color.filmGrain,0f..ColorSettings.GRAIN_MAX,!c.busy) {c.mutate(p.copy(effects=p.effects.copy(color=color.copy(filmGrain=it))))}
        ValueSlider("Denoise",color.denoise,0f..ColorSettings.DENOISE_MAX,!c.busy) {c.mutate(p.copy(effects=p.effects.copy(color=color.copy(denoise=it))))}
        if(!p.sources[0].info.isStill) {
            ValueSlider("Fade in seconds",p.effects.fadeInSeconds,0f..5f,!c.busy) {c.mutate(p.copy(effects=p.effects.copy(fadeInSeconds=it)))}
            ValueSlider("Fade out seconds",p.effects.fadeOutSeconds,0f..5f,!c.busy) {c.mutate(p.copy(effects=p.effects.copy(fadeOutSeconds=it)))}
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(p.effects.muted,{c.mutate(p.copy(effects=p.effects.copy(muted=it)))},enabled=!c.busy);Text("Mute source");Checkbox(p.effects.keepPitch,{c.mutate(p.copy(effects=p.effects.copy(keepPitch=it)))},enabled=!c.busy);Text("Keep pitch")}
        }
        Text("Layers, brushes, masks, HDR painting and keyframes are still being ported.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun PhotoPanel(c:EditorController,p:DesktopProject,window:java.awt.Frame) {
    if(!p.sources[0].info.isStill)return
    val e=p.image
    fun edit(v:ImageEdit)=c.mutate(p.copy(image=v))
    PanelCard {
        Text("Photo geometry and tone",style=MaterialTheme.typography.titleMedium)
        Text("Render or export to see the edited picture. Source view is unchanged.",style=MaterialTheme.typography.bodySmall)
        ValueSlider("Horizontal perspective",e.perspectiveH,-1f..1f,!c.busy){edit(e.copy(perspectiveH=it))}
        ValueSlider("Vertical perspective",e.perspectiveV,-1f..1f,!c.busy){edit(e.copy(perspectiveV=it))}
        ValueSlider("Straighten degrees",e.straightenDegrees,-30f..30f,!c.busy){edit(e.copy(straightenDegrees=it))}
        val t=e.tone
        ValueSlider("Exposure stops",t.exposure,-2f..2f,!c.busy){edit(e.copy(tone=t.copy(exposure=it)))}
        ValueSlider("Highlights",t.highlights,-1f..1f,!c.busy){edit(e.copy(tone=t.copy(highlights=it)))}
        ValueSlider("Shadows",t.shadows,-1f..1f,!c.busy){edit(e.copy(tone=t.copy(shadows=it)))}
        ValueSlider("Warmth",t.warmth,-1f..1f,!c.busy){edit(e.copy(tone=t.copy(warmth=it)))}
        ValueSlider("Tint",t.tint,-1f..1f,!c.busy){edit(e.copy(tone=t.copy(tint=it)))}
        ValueSlider("Hue",t.hue,-180f..180f,!c.busy){edit(e.copy(tone=t.copy(hue=it)))}
        ValueSlider("Fade",t.fade,0f..1f,!c.busy){edit(e.copy(tone=t.copy(fade=it)))}
        Text("Subject cutout runs offline with the imported subject model. PNG keeps removed background transparent; other formats may flatten it.")
        OutlinedButton(onClick={c.cutoutPicture()},enabled=!c.busy){Text("Cut out subject")}
        if(e.key!=null)OutlinedButton(onClick={edit(e.copy(key=null))},enabled=!c.busy){Text("Remove cutout")}
        Row {Switch(e.canvas!=null,{on->edit(e.copy(canvas=if(on)PictureCanvas()else null))},enabled=!c.busy);Text("Picture canvas")}
        e.canvas?.let {canvas->
            ValueSlider("Canvas width",canvas.width.toFloat(),64f..3840f,!c.busy){edit(e.copy(canvas=canvas.copy(width=it.toInt())))}
            ValueSlider("Canvas height",canvas.height.toFloat(),64f..3840f,!c.busy){edit(e.copy(canvas=canvas.copy(height=it.toInt())))}
            FlowRowCompat {listOf<Long?>(null,0xffffffffL,0xff000000L,0xff8090a0L).forEach {color->OutlinedButton(onClick={edit(e.copy(canvas=canvas.copy(background=color)))},enabled=!c.busy){Text(if(color==null)"Transparent"else "#%06X".format(color and 0xffffff))}}}
            val v=p.photoPlacement ?: com.cuscus.ffmpeggui.core.image.fittedPhoto(canvas,p.sources.first().info.width,p.sources.first().info.height)
            fun placed(value:ImagePlacement)=c.mutate(p.copy(photoPlacement=value))
            ValueSlider("Photo X",v.x,-1f..1f,!c.busy){placed(v.copy(x=it))}
            ValueSlider("Photo Y",v.y,-1f..1f,!c.busy){placed(v.copy(y=it))}
            ValueSlider("Photo width",v.widthFraction,.05f..2f,!c.busy){placed(v.copy(widthFraction=it))}
            ValueSlider("Photo opacity",v.opacity,0f..1f,!c.busy){placed(v.copy(opacity=it))}
            ValueSlider("Photo rotation",v.rotationDegrees,-180f..180f,!c.busy){placed(v.copy(rotationDegrees=it))}
            ValueSlider("Photo stack",v.layer.toFloat(),-10f..10f,!c.busy){placed(v.copy(layer=it.toInt()))}
            OutlinedButton(onClick={c.mutate(p.copy(photoPlacement=null))},enabled=!c.busy){Text("Fit photo automatically")}
        }
        Text("Photo filter")
        Row(Modifier.horizontalScroll(rememberScrollState())) {ImageFilter.entries.forEach {f->FilterChip(e.filter==f,{edit(e.copy(filter=f))},label={Text(f.name)},enabled=!c.busy)}}
        ValueSlider("Filter strength",e.filterAmount,0f..1f,!c.busy){edit(e.copy(filterAmount=it))}
        Text("Photo effect")
        Row(Modifier.horizontalScroll(rememberScrollState())) {ImageEffect.entries.forEach {f->FilterChip(e.effect==f,{edit(e.copy(effect=f))},label={Text(f.name)},enabled=!c.busy)}}
        ValueSlider("Effect strength",e.effectAmount,0f..1f,!c.busy){edit(e.copy(effectAmount=it))}
        ValueSlider("Maximum output side (0 keeps original)",e.output.maxSide.toFloat(),0f..3840f,!c.busy){edit(e.copy(output=e.output.copy(maxSide=if(it<64)0 else it.toInt())))}
        ValueSlider("Photo quality",e.output.quality,0f..1f,!c.busy){edit(e.copy(output=e.output.copy(quality=it)))}
        ValueSlider("Round corners",e.corners,0f..1f,!c.busy){edit(e.copy(corners=it))}
        Row {Switch(e.hdr!=null,{on->edit(e.copy(hdr=if(on)ImageHdr()else null))},enabled=!c.busy);Text("Ultra HDR JPG")}
        e.hdr?.let {hdr->
            if(p.format!=OutputFormat.JPG)Text("HDR is on, but export requires JPG. Choose JPG or turn HDR off.",color=MaterialTheme.colorScheme.error)
            FlowRowCompat {HdrMode.entries.forEach {mode->FilterChip(hdr.mode==mode,{edit(e.copy(hdr=hdr.copy(mode=mode)))},label={Text(mode.name)},enabled=!c.busy)}}
            ValueSlider("HDR intensity",hdr.intensity,0f..1f,!c.busy){edit(e.copy(hdr=hdr.copy(intensity=it)))}
            Text("HDR area")
            FlowRowCompat {HdrArea.entries.forEach {area->FilterChip(hdr.area==area,{edit(e.copy(hdr=hdr.copy(area=area)))},label={Text(area.name)},enabled=!c.busy)}}
            if(hdr.area!=HdrArea.EVERYWHERE) {
                Text(if(hdr.areaMask==null)"Choose a matching upright alpha mask. White/opaque selects HDR; transparent excludes it. BACKGROUND inverts a subject mask." else "HDR mask: ${File(hdr.areaMask!!).name}")
                OutlinedButton(onClick={val d=FileDialog(window,"Choose HDR alpha mask PNG",FileDialog.LOAD);d.isVisible=true;d.file?.let {name->val file=File(d.directory,name);c.work("Checking HDR mask…"){
                    DesktopHdr.readMask(file,p.sources.single().info.width,p.sources.single().info.height)
                    val target=File.createTempFile("hdr-",".png",DesktopMasks.directory());file.copyTo(target,true)
                    edit(e.copy(hdr=if(hdr.area==HdrArea.BRUSH)hdr.copy(brush=target.path)else hdr.copy(mask=target.path)));c.status="HDR mask copied. Export JPG to inspect the gain map."
                }}},enabled=!c.busy){Text("Choose HDR alpha mask")}
                e.key?.mask?.let {path->OutlinedButton(onClick={edit(e.copy(hdr=if(hdr.area==HdrArea.BRUSH)hdr.copy(brush=path)else hdr.copy(mask=path)))},enabled=!c.busy){Text("Use current subject mask")}}
            }
            Text("JPG only, up to 8 megapixels, no picture canvas. GENERATED uses edited light, not AI. ORIGINAL requires a supported embedded Ultra HDR source up to32MB, midpoint keeps its original strength. SDR screens show the ordinary JPG. HDR hardware display is not verified. Direct HDR brush painting remains in progress.")
        }
        OutlinedButton(onClick={c.mutate(p.copy(image=ImageEdit(),photoPlacement=null))},enabled=!c.busy){Text("Reset photo settings")}
        Text("Drawing and subject-mask brushes have separate tabs. Direct HDR brush painting and richer layer gestures remain in progress.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ArtPanel(c:EditorController,p:DesktopProject) {
    if(!p.sources[0].info.isStill || (p.format!=OutputFormat.SVG && !p.format.isTextArt))return
    PanelCard {
        Text(if(p.format==OutputFormat.SVG)"SVG tracing" else "Text art",style=MaterialTheme.typography.titleMedium)
        Text("Export to inspect the final SVG or text file. In-editor art preview is not ready.",style=MaterialTheme.typography.bodySmall)
        if(p.format==OutputFormat.SVG) {
            val t=p.image.trace
            fun change(v:com.cuscus.ffmpeggui.core.vector.TraceSettings)=c.mutate(p.copy(image=p.image.copy(trace=v)))
            ValueSlider("Colours",t.colors.toFloat(),2f..64f,!c.busy){change(t.copy(colors=it.toInt()))}
            ValueSlider("Detail",t.detail,0f..1f,!c.busy){change(t.copy(detail=it))}
            ValueSlider("Speckle pixels",t.speckle.toFloat(),0f..100f,!c.busy){change(t.copy(speckle=it.toInt()))}
            Row {Checkbox(t.mono,{change(t.copy(mono=it))},enabled=!c.busy);Text("Black/white");Checkbox(t.smooth,{change(t.copy(smooth=it))},enabled=!c.busy);Text("Curves")}
            Row {Checkbox(t.gradients,{change(t.copy(gradients=it))},enabled=!c.busy);Text("Gradients")}
        } else {
            val t=p.image.textArt
            fun change(v:com.cuscus.ffmpeggui.core.textart.TextArtSettings)=c.mutate(p.copy(image=p.image.copy(textArt=v)))
            ValueSlider("Columns",t.columns.toFloat(),8f..240f,!c.busy){change(t.copy(columns=it.toInt()))}
            Text("Mode")
            Row(Modifier.horizontalScroll(rememberScrollState())) {com.cuscus.ffmpeggui.core.textart.ArtMode.entries.forEach {v->FilterChip(t.mode==v,{change(t.copy(mode=v))},label={Text(v.name)},enabled=!c.busy)}}
            if(t.mode==com.cuscus.ffmpeggui.core.textart.ArtMode.TEXT_FILL)OutlinedTextField(t.phrase,{change(t.copy(phrase=it))},label={Text("Phrase")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
            Row {com.cuscus.ffmpeggui.core.textart.Paper.entries.forEach {v->FilterChip(t.paper==v,{change(t.copy(paper=v))},label={Text(v.name)},enabled=!c.busy)}}
            if(p.format==OutputFormat.ANSI)Row(Modifier.horizontalScroll(rememberScrollState())) {com.cuscus.ffmpeggui.core.textart.ArtColor.entries.forEach {v->FilterChip(t.palette.depth==v,{change(t.copy(palette=t.palette.copy(depth=v)))},label={Text(v.name)},enabled=!c.busy)}}
            Text("Dither")
            Row(Modifier.horizontalScroll(rememberScrollState())) {com.cuscus.ffmpeggui.core.textart.Dither.entries.forEach {v->FilterChip(t.dither==v,{change(t.copy(dither=v))},label={Text(v.name)},enabled=!c.busy)}}
        }
    }
}

@Composable private fun TextLayersPanel(c:EditorController,p:DesktopProject) {
    if(!p.sources[0].info.isVideo && !p.sources[0].info.isStill){Text("Text layers need a picture or video.");return}
    PanelCard {
        Text("Text layers",style=MaterialTheme.typography.titleMedium)
        Text("Whole-output or clip-bound layers. Render to inspect placement. Higher layer number is on top. Custom curves and direct dragging remain in progress.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={c.mutate(p.copy(captions=p.captions+TextOverlay(text="Your text",layer=p.captions.size)))},enabled=!c.busy){Text("Add text layer")}
    }
    p.captions.forEachIndexed {index,t->
        fun update(v:TextOverlay)=c.mutate(p.copy(captions=p.captions.mapIndexed {i,old->if(i==index)v else old}))
        PanelCard {
            Text("Layer ${index+1}",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(t.text,{update(t.copy(text=it))},label={Text("Text")},enabled=!c.busy,modifier=Modifier.isolateProjectHistory().fillMaxWidth())
            ValueSlider("X",t.x,0f..1f,!c.busy){update(t.copy(x=it))}
            ValueSlider("Y",t.y,0f..1f,!c.busy){update(t.copy(y=it))}
            ValueSlider("Size",t.size,.01f.. .3f,!c.busy){update(t.copy(size=it))}
            ValueSlider("Opacity",t.opacity,0f..1f,!c.busy){update(t.copy(opacity=it))}
            ValueSlider("Rotation",t.rotationDegrees,-180f..180f,!c.busy){update(t.copy(rotationDegrees=it))}
            ValueSlider("Stretch",t.stretch,.25f..4f,!c.busy){update(t.copy(stretch=it))}
            Text("Blend")
            FlowRowCompat {LayerBlend.entries.forEach {b->FilterChip(t.blend==b,{update(t.copy(blend=b))},label={Text(b.name)},enabled=!c.busy)}}
            LayerBinding(c,p,t.clipId){update(t.copy(clipId=it,keyframes=Keyframes.NONE))}

            Row {Checkbox(t.isBold,{update(t.copy(isBold=it))},enabled=!c.busy);Text("Bold");Checkbox(t.hidden,{update(t.copy(hidden=it))},enabled=!c.busy);Text("Hidden");Checkbox(t.look.box,{update(t.copy(look=t.look.copy(box=it)))},enabled=!c.busy);Text("Box")}
            ValueSlider("Outline",t.look.outline,0f.. .3f,!c.busy){update(t.copy(look=t.look.copy(outline=it)))}
            Row {listOf(0xFFFFFFFFL,0xFF000000L,0xFFFFCC00L,0xFFFF4040L,0xFF40CCFFL).forEach {color-> TextButton(onClick={update(t.copy(color=color))},enabled=!c.busy){Text("■",color=Color(color.toInt()))}}}
            if(!p.sources[0].info.isStill && t.clipId==null) {
                val duration=p.outputDurationMs/1000f
                ValueSlider("Start seconds",t.startMs/1000f,0f..duration,!c.busy){update(t.copy(startMs=(it*1000).toLong().coerceAtMost((t.endMs ?: p.outputDurationMs)-1)))}
                ValueSlider("End seconds",(t.endMs ?: p.outputDurationMs)/1000f,0f..duration,!c.busy){update(t.copy(endMs=(it*1000).toLong().coerceAtLeast(t.startMs+1)))}
                ValueSlider("Fade in seconds",t.fadeInSeconds,0f..3f,!c.busy){update(t.copy(fadeInSeconds=it))}
                ValueSlider("Fade out seconds",t.fadeOutSeconds,0f..3f,!c.busy){update(t.copy(fadeOutSeconds=it))}
            }
            if(!p.sources[0].info.isStill) CaptionKeys(c,p,t,::update)
            Row {
                TextButton(onClick={update(t.copy(layer=t.layer+1))},enabled=!c.busy){Text("Raise (${t.layer})")}
                TextButton(onClick={update(t.copy(layer=t.layer-1))},enabled=!c.busy){Text("Lower")}
                TextButton(onClick={c.mutate(p.copy(captions=p.captions.filterIndexed {i,_->i!=index}))},enabled=!c.busy){Text("Remove")}
            }
        }
    }
}

@Composable private fun CaptionKeys(c:EditorController,p:DesktopProject,t:TextOverlay,update:(TextOverlay)->Unit) {
    val clipIndex=p.timeline.clips.indexOfFirst {it.id==t.clipId}
    val start=if(clipIndex>=0)p.clock().joinedStartMs(clipIndex) else t.startMs
    val finish=if(clipIndex>=0)start+p.timeline.clips[clipIndex].effects.playedMs(p.timeline.clips[clipIndex].lengthMs) else t.endMs ?: p.outputDurationMs
    val cursor=if(clipIndex>=0)start+p.clock().clipPosition(c.position).playedOffsetMs else p.outputAt(c.position)
    val at=(cursor-start).coerceAtLeast(0)
    Text("Animation keys",style=MaterialTheme.typography.titleSmall)
    Text("Seek on the output timeline, set the controls above, then capture. Times below are relative to this text's start.",style=MaterialTheme.typography.bodySmall)
    Button(onClick={update(t.copy(keyframes=t.keyframes.set(at,mapOf(KeyProperty.X to t.x,KeyProperty.Y to t.y,KeyProperty.SIZE to t.size,KeyProperty.OPACITY to t.opacity))))},enabled=!c.busy && cursor>=start && cursor<=finish){Text("Capture at ${time(at)}")}
    t.keyframes.sorted.forEach {key->
        Column {
            Text("${time(key.atMs)} · X ${key.values[KeyProperty.X]} · Y ${key.values[KeyProperty.Y]}",style=MaterialTheme.typography.bodySmall)
            FlowRowCompat {KeyEasing.entries.filter {it!=KeyEasing.CUSTOM}.forEach {e->FilterChip(key.easing==e,{update(t.copy(keyframes=t.keyframes.withEasing(key.atMs,e)))},label={Text(e.name)},enabled=!c.busy)}}
            TextButton(onClick={update(t.copy(keyframes=t.keyframes.remove(key.atMs)))},enabled=!c.busy){Text("Remove key")}
        }
    }
}

@Composable private fun SoundTracksPanel(c:EditorController,p:DesktopProject,pick:()->String?) {
    if(p.sources[0].info.isStill){Text("Sound tracks need a video or audio timeline.");return}
    PanelCard {
        Text("Sound tracks",style=MaterialTheme.typography.titleMedium)
        Text("Add music, voice or effects from local files. Tracks mix on the output clock and stop at its end. Render to hear the mix.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={pick()?.let {path->c.work("Reading sound track…") {val source=c.media.probe(path);require(source.info.hasAudio){"File has no audio."};c.mutate(p.copy(sounds=p.sounds+DesktopSound(source,PlacedSound(trimEndMs=source.info.durationMs))));c.status="Sound track added: ${File(source.path).name}"}}},enabled=!c.busy){Text("Add sound file")}
    }
    p.sounds.forEachIndexed {index,sound->
        val s=sound.placed
        fun update(v:PlacedSound)=c.mutate(p.copy(sounds=p.sounds.mapIndexed {i,old->if(i==index)old.copy(placed=v)else old}))
        PanelCard {
            Text("${index+1}. ${File(sound.source.path).name}",style=MaterialTheme.typography.titleSmall)
            Row {Checkbox(sound.muted,{mute->c.mutate(p.copy(sounds=p.sounds.mapIndexed {i,old->if(i==index)old.copy(muted=mute)else old}))},enabled=!c.busy);Text("Muted")}
            ValueSlider("Start on output, seconds",s.startMs/1000f,0f..p.outputDurationMs/1000f,!c.busy){update(s.copy(startMs=(it*1000).toLong()))}
            ValueSlider("Trim start, seconds",s.trimStartMs/1000f,0f..sound.source.info.durationMs/1000f,!c.busy){update(s.copy(trimStartMs=(it*1000).toLong().coerceAtMost((s.trimEndMs ?: sound.source.info.durationMs)-1)))}
            ValueSlider("Trim end, seconds",(s.trimEndMs ?: sound.source.info.durationMs)/1000f,0f..sound.source.info.durationMs/1000f,!c.busy){update(s.copy(trimEndMs=(it*1000).toLong().coerceAtLeast(s.trimStartMs+1)))}
            ValueSlider("Volume",s.volume,0f..2f,!c.busy){update(s.copy(volume=it))}
            ValueSlider("Speed",s.speed,.25f..4f,!c.busy){update(s.copy(speed=it))}
            ValueSlider("Pitch semitones",s.pitchSemitones,-12f..12f,!c.busy){update(s.copy(pitchSemitones=it))}
            ValueSlider("Repeats",s.repeats.toFloat(),1f..10f,!c.busy){update(s.copy(repeats=it.toInt()))}
            ValueSlider("Fade in, seconds",s.fadeInSeconds,0f..5f,!c.busy){update(s.copy(fadeInSeconds=it))}
            ValueSlider("Fade out, seconds",s.fadeOutSeconds,0f..5f,!c.busy){update(s.copy(fadeOutSeconds=it))}
            ValueSlider("Bass dB",s.bassDb,-12f..12f,!c.busy){update(s.copy(bassDb=it))}
            ValueSlider("Mid dB",s.midDb,-12f..12f,!c.busy){update(s.copy(midDb=it))}
            ValueSlider("Treble dB",s.trebleDb,-12f..12f,!c.busy){update(s.copy(trebleDb=it))}
            Row {Checkbox(s.keepPitch,{update(s.copy(keepPitch=it))},enabled=!c.busy);Text("Keep pitch");Checkbox(s.normalize,{update(s.copy(normalize=it))},enabled=!c.busy);Text("Normalize")}
            val cursor=p.outputAt(c.position)
            val at=(cursor-s.startMs).coerceAtLeast(0)
            LayerKeys(c,s.keyframes,at,cursor>=s.startMs && cursor<=s.startMs+(s.lengthMs ?: p.outputDurationMs),mapOf(KeyProperty.VOLUME to s.volume)){update(s.copy(keyframes=it))}
            TextButton(onClick={c.mutate(p.copy(sounds=p.sounds.filterIndexed {i,_->i!=index}))},enabled=!c.busy){Text("Remove track")}
        }
    }
}

@Composable private fun TagsPanel(c:EditorController,p:DesktopProject,pick:()->File?) {
    val t=p.tags
    fun update(v:MetadataTags)=c.mutate(p.copy(tags=v))
    PanelCard {
        Text("Output tags",style=MaterialTheme.typography.titleMedium)
        Text("Album art: ${if(p.coverPng!=null) "embedded 600 x 600 cover" else "none selected"}. Select a local picture; it is centre-cropped square, embedded in the project, and written to MP3/M4A/FLAC/OGG/OPUS exports. WAV/video cannot hold this cover. Existing source art is not automatically imported.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={pick()?.let {file->c.work("Preparing album art…") {c.mutate(p.copy(coverPng=DesktopCover.prepare(file,c.media)));c.status="Album art prepared. Export to write it."}}},enabled=!c.busy) {Text("Choose album art")}
        if(p.coverPng!=null) TextButton(onClick={c.mutate(p.copy(coverPng=null))},enabled=!c.busy) {Text("Remove album art")}
        Text("Container support varies. Render or export to write these tags. AI provenance marks are never removed by these controls.",style=MaterialTheme.typography.bodySmall)
        Row {Checkbox(t.stripAll,{update(t.copy(stripAll=it))},enabled=!c.busy);Text("Strip source tags")}
        Row {Checkbox(t.stripSensitive,{update(t.copy(stripSensitive=it))},enabled=!c.busy);Text("Strip location and device tags")}
        OutlinedTextField(t.title,{update(t.copy(title=it))},label={Text("Title")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
        OutlinedTextField(t.artist,{update(t.copy(artist=it))},label={Text("Artist")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
        OutlinedTextField(t.album,{update(t.copy(album=it))},label={Text("Album")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
        OutlinedTextField(t.albumArtist,{update(t.copy(albumArtist=it))},label={Text("Album artist")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
        OutlinedTextField(t.year,{update(t.copy(year=it))},label={Text("Year")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
        OutlinedTextField(t.genre,{update(t.copy(genre=it))},label={Text("Genre")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
        OutlinedTextField(t.comment,{update(t.copy(comment=it))},label={Text("Comment")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
        OutlinedTextField(t.copyright,{update(t.copy(copyright=it))},label={Text("Copyright")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
        OutlinedTextField(t.lyrics,{update(t.copy(lyrics=it))},label={Text("Lyrics, plain or LRC")},enabled=!c.busy,modifier=Modifier.isolateProjectHistory().fillMaxWidth())
        Text("Lyrics use output time. Later timeline edits do not retime this metadata automatically. Regenerate from reviewed spoken words after changing the edit.",style=MaterialTheme.typography.bodySmall)
        val words=p.copy(externalSubtitles=null).request().timedWords()
        FlowRowCompat {
            OutlinedButton(onClick={update(t.copy(lyrics=LyricsText.plain(words)))},enabled=!c.busy && words.isNotEmpty()){Text("Plain from transcript")}
            OutlinedButton(onClick={update(t.copy(lyrics=LyricsText.synced(words,p.effects.effectiveSpeed)))},enabled=!c.busy && words.isNotEmpty()){Text("LRC from transcript")}
            TextButton(onClick={val parsed=LrcLines.parse(t.lyrics);update(t.copy(lyrics=LrcLines.plain(parsed.lines)))},enabled=!c.busy){Text("Remove timing")}
        }
        val lyrics=LrcLines.parse(t.lyrics)
        val at=p.outputAt(c.position)
        val active=LrcLines.lineAt(lyrics.lines,at)
        Text("Lyrics clock ${time(at)}. Seek output, then stamp a line. Export writes metadata, not burned text.",style=MaterialTheme.typography.bodySmall)
        lyrics.lines.take(250).forEachIndexed {index,line->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Text((line.timeMs?.let {LyricsText.stamp(it)} ?: "[untimed]")+" "+line.text,color=if(index==active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f))
                TextButton(onClick={update(t.copy(lyrics=LrcLines.format(lyrics.copy(lines=lyrics.lines.mapIndexed {i,v->if(i==index)v.copy(timeMs=at)else v}))))},enabled=!c.busy){Text("Stamp")}
                TextButton(onClick={update(t.copy(lyrics=LrcLines.format(lyrics.copy(lines=lyrics.lines.mapIndexed {i,v->if(i==index)v.copy(timeMs=null)else v}))))},enabled=!c.busy && line.timeMs!=null){Text("Clear")}
            }
        }
        if(lyrics.lines.size>250)Text("Line timing view shows the first 250 lines. All lines remain in the text field and export.")
    }
}

@Composable private fun PictureLayersPanel(c:EditorController,p:DesktopProject,pick:()->String?) {
    if(!p.sources[0].info.isVideo){Text("Image layers need a video or picture.");return}
    PanelCard {
        Text("Image layers",style=MaterialTheme.typography.titleMedium)
        Text("Add local pictures or video. Video layer sound is not included; add it separately as a sound track. Render to inspect the composite. Direct dragging remains in progress.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={pick()?.let {path->c.work("Reading image layer…"){val source=c.media.probe(path);require(source.info.isVideo){"Choose a picture or video."};c.mutate(p.copy(pictures=p.pictures+DesktopPicture(source,ImagePlacement(aspect=source.info.width.toFloat()/source.info.height,layer=p.pictures.size+p.captions.size,video=if(source.info.isStill)null else OverlayVideo(trimEndMs=source.info.durationMs)))));c.status="Image layer added."}}},enabled=!c.busy){Text("Add picture or video")}
    }
    p.pictures.forEachIndexed {index,picture->
        val v=picture.placement
        fun update(next:ImagePlacement)=c.mutate(p.copy(pictures=p.pictures.mapIndexed {i,old->if(i==index)old.copy(placement=next)else old}))
        PanelCard {
            Text("${index+1}. ${File(picture.source.path).name}",style=MaterialTheme.typography.titleSmall)
            Row {Checkbox(picture.hidden,{hidden->c.mutate(p.copy(pictures=p.pictures.mapIndexed {i,old->if(i==index)old.copy(hidden=hidden)else old}))},enabled=!c.busy);Text("Hidden")}
            ValueSlider("X",v.x,0f..1f,!c.busy){update(v.copy(x=it))}
            ValueSlider("Y",v.y,0f..1f,!c.busy){update(v.copy(y=it))}
            ValueSlider("Width",v.widthFraction,.02f..2f,!c.busy){update(v.copy(widthFraction=it))}
            ValueSlider("Opacity",v.opacity,0f..1f,!c.busy){update(v.copy(opacity=it))}
            ValueSlider("Rotation",v.rotationDegrees,-180f..180f,!c.busy){update(v.copy(rotationDegrees=it))}
            ValueSlider("Stretch",v.stretch,.25f..4f,!c.busy){update(v.copy(stretch=it))}
            Row {Checkbox(v.flipHorizontal,{update(v.copy(flipHorizontal=it))},enabled=!c.busy);Text("Flip H");Checkbox(v.flipVertical,{update(v.copy(flipVertical=it))},enabled=!c.busy);Text("Flip V")}
            ValueSlider("Brightness",v.brightness,-1f..1f,!c.busy){update(v.copy(brightness=it))}
            ValueSlider("Contrast",v.contrast,0f..2f,!c.busy){update(v.copy(contrast=it))}
            ValueSlider("Saturation",v.saturation,0f..2f,!c.busy){update(v.copy(saturation=it))}
            if(v.video==null)LayerBinding(c,p,v.clipId){update(v.copy(clipId=it,keyframes=Keyframes.NONE))} else Text("Video layers use the whole-output clock. Clip binding is not yet supported.")
            v.video?.let {vid->
                ValueSlider("Video trim start seconds",vid.trimStartMs/1000f,0f..picture.source.info.durationMs/1000f,!c.busy){update(v.copy(video=vid.copy(trimStartMs=(it*1000).toLong().coerceAtMost((vid.trimEndMs ?: picture.source.info.durationMs)-1))))}
                ValueSlider("Video trim end seconds",(vid.trimEndMs ?: picture.source.info.durationMs)/1000f,0f..picture.source.info.durationMs/1000f,!c.busy){update(v.copy(video=vid.copy(trimEndMs=(it*1000).toLong().coerceAtLeast(vid.trimStartMs+1))))}
                Row {Checkbox(vid.loop,{update(v.copy(video=vid.copy(loop=it)))},enabled=!c.busy);Text("Loop video layer")}
            }
            Text("Picture shape")
            FlowRowCompat {OverlayShape.entries.forEach {shape->FilterChip(v.look.shape==shape,{update(v.copy(look=v.look.copy(shape=shape)))},label={Text(shape.name)},enabled=!c.busy)}}
            ValueSlider("Round corners",v.look.cornerRadius,0f..1f,!c.busy){update(v.copy(look=v.look.copy(cornerRadius=it)))}
            ValueSlider("Blur",v.look.blur,0f.. .2f,!c.busy){update(v.copy(look=v.look.copy(blur=it)))}
            ValueSlider("White outline",v.look.borderWidth,0f.. .1f,!c.busy){update(v.copy(look=v.look.copy(borderWidth=it,borderColor=0xffffff)))}
            ValueSlider("Shadow opacity",v.look.shadowOpacity,0f..1f,!c.busy){update(v.copy(look=v.look.copy(shadowOpacity=it)))}
            Text("Blend")
            FlowRowCompat {LayerBlend.entries.forEach {b->FilterChip(v.blend==b,{update(v.copy(blend=b))},label={Text(b.name)},enabled=!c.busy)}}
            if(!p.sources[0].info.isStill && v.clipId==null){
                ValueSlider("Start seconds",v.startMs/1000f,0f..p.outputDurationMs/1000f,!c.busy){update(v.copy(startMs=(it*1000).toLong().coerceAtMost((v.endMs ?: p.outputDurationMs)-1)))}
                ValueSlider("End seconds",(v.endMs ?: p.outputDurationMs)/1000f,0f..p.outputDurationMs/1000f,!c.busy){update(v.copy(endMs=(it*1000).toLong().coerceAtLeast(v.startMs+1)))}
                ValueSlider("Fade in seconds",v.fadeInSeconds,0f..3f,!c.busy){update(v.copy(fadeInSeconds=it))}
                ValueSlider("Fade out seconds",v.fadeOutSeconds,0f..3f,!c.busy){update(v.copy(fadeOutSeconds=it))}
            }
            if(!p.sources[0].info.isStill) {
                val ci=p.timeline.clips.indexOfFirst {it.id==v.clipId}
                val start=if(ci>=0)p.clock().joinedStartMs(ci) else v.startMs
                val end=if(ci>=0)start+p.timeline.clips[ci].effects.playedMs(p.timeline.clips[ci].lengthMs) else v.endMs ?: p.outputDurationMs
                val cursor=if(ci>=0)start+p.clock().clipPosition(c.position).playedOffsetMs else p.outputAt(c.position)
                LayerKeys(c,v.keyframes,(cursor-start).coerceAtLeast(0),cursor>=start && cursor<=end,mapOf(KeyProperty.X to v.x,KeyProperty.Y to v.y,KeyProperty.SIZE to v.widthFraction,KeyProperty.OPACITY to v.opacity,KeyProperty.ROTATION to v.rotationDegrees)){update(v.copy(keyframes=it))}
            }
            Row {TextButton(onClick={update(v.copy(layer=v.layer+1))},enabled=!c.busy){Text("Raise (${v.layer})")};TextButton(onClick={update(v.copy(layer=v.layer-1))},enabled=!c.busy){Text("Lower")};TextButton(onClick={c.mutate(p.copy(pictures=p.pictures.filterIndexed {i,_->i!=index}))},enabled=!c.busy){Text("Remove")}}
        }
    }
}

@Composable private fun ClipEditorPanel(c:EditorController,p:DesktopProject) {
    val index=p.timeline.clips.indexOfFirst {it.id==c.smartClipId}.takeIf {it>=0} ?: (p.timeline.locate(c.position)?.index ?: 0).coerceIn(p.timeline.clips.indices)
    val clip=p.timeline.clips[index]
    val source=p.sources.first {it.path==clip.sourceId}
    val f=clip.effects
    fun change(update:(Effects)->Effects){c.mutate(p.copy(timeline=p.timeline.updateEffects(clip.id,update),followCrop=null))}
    var section by remember {mutableStateOf("Trim")}
    PanelCard {
        Text("Clip ${index+1}: ${File(clip.sourceId).name}",style=MaterialTheme.typography.titleLarge)
        Text("Only this clip. Original files stay unchanged.",style=MaterialTheme.typography.bodySmall)
        var expanded by remember {mutableStateOf(false)}
        Box {FilledTonalButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){SymbolLabel(when(section){"Trim"->"content_cut";"Motion"->"crop";"Colour"->"palette";"Sound"->"volume_up";else->"transition_fade"},section+"  ▾")}
            DropdownMenu(expanded,{expanded=false}) {listOf("Trim","Motion","Colour","Sound","Transition").forEach {name->DropdownMenuItem(text={Row(verticalAlignment=Alignment.CenterVertically){SymbolLabel(when(name){"Trim"->"content_cut";"Motion"->"crop";"Colour"->"palette";"Sound"->"volume_up";else->"transition_fade"},name)}},onClick={section=name;expanded=false})}}}
        if(section=="Trim") {
        if(!source.info.isStill) {
            PreciseClipTrim(c,clip.id,clip.startMs,clip.endMs ?: source.info.durationMs)
            ValueSlider("Source in (seconds)",clip.startMs/1000f,0f..((clip.endMs ?: source.info.durationMs)-120).coerceAtLeast(1)/1000f,!c.busy){c.mutate(p.copy(timeline=p.timeline.trim(clip.id,(it*1000).toLong(),clip.endMs ?: source.info.durationMs,source.info.durationMs),followCrop=null))}
            ValueSlider("Source out (seconds)",(clip.endMs ?: source.info.durationMs)/1000f,(clip.startMs+120)/1000f..source.info.durationMs/1000f,!c.busy){c.mutate(p.copy(timeline=p.timeline.trim(clip.id,clip.startMs,(it*1000).toLong(),source.info.durationMs),followCrop=null))}
        }
        if(source.info.isStill) {
            Text("Still picture shown as a clip. Duration is footage time, before clip speed. The original picture is unchanged.")
            ValueSlider("Still duration seconds",clip.lengthMs/1000f,.2f..60f,!c.busy){value->c.mutate(p.copy(timeline=p.timeline.trim(clip.id,0,(value*1000).toLong(),0),followCrop=null))}
        }
        FlowRowCompat {
            OutlinedButton(onClick={c.mutate(p.copy(timeline=p.timeline.duplicate(clip.id,java.util.UUID.randomUUID().toString()),followCrop=null))},enabled=!c.busy){Text("Duplicate clip")}
            OutlinedButton(onClick={change {Effects.NONE}},enabled=!c.busy){Text("Reset clip effects")}
        }
        }
        if(section=="Motion") {
        ValueSlider("Clip speed",f.speed,.25f..4f,!c.busy){v->change {it.copy(speed=v)}}
        Text("Speed ramp",style=MaterialTheme.typography.titleMedium)
        Row {Switch(f.speedCurve!=null,{v->change {it.copy(speedCurve=if(v)SpeedCurve(listOf(SpeedPoint(0f,1f),SpeedPoint(.5f,2f),SpeedPoint(1f,1f))) else null)}},enabled=!c.busy);Text("Vary clip speed")}
        f.speedCurve?.let {curve->
            Text("Positions are fractions of the footage in playing order. The constant-speed slider is ignored while the ramp is on.")
            RampCanvas(curve,!c.busy){updated->change {it.copy(speedCurve=updated)}}
            curve.nodes.forEachIndexed {i,node->ValueSlider("Ramp ${i+1} at ${(node.at*100).toInt()}%",node.speed,.25f..4f,!c.busy){v->change {it.copy(speedCurve=SpeedCurve(curve.nodes.mapIndexed {j,n->if(i==j)n.copy(speed=v)else n}))}}}
            FlowRowCompat {
                OutlinedButton(onClick={change {it.copy(speedCurve=SpeedCurve(listOf(SpeedPoint(0f,.5f),SpeedPoint(.5f,2f),SpeedPoint(1f,.5f))))}},enabled=!c.busy){Text("Slow-fast-slow")}
                OutlinedButton(onClick={change {it.copy(speedCurve=SpeedCurve(listOf(SpeedPoint(0f,2f),SpeedPoint(.5f,.5f),SpeedPoint(1f,2f))))}},enabled=!c.busy){Text("Fast-slow-fast")}
            }
        }
        Row { Switch(f.reversed,{v->change {it.copy(reversed=v)}},enabled=!c.busy);Text("Reverse");Switch(f.keepPitch,{v->change {it.copy(keepPitch=v)}},enabled=!c.busy);Text("Keep pitch") }
        }
        if(section=="Sound") {
        Row {Switch(f.muted,{v->change {it.copy(muted=v)}},enabled=!c.busy);Text("Mute clip")}
        ValueSlider("Clip volume",f.volume,0f..2f,!c.busy){v->change {it.copy(volume=v)}}
        ValueSlider("Fade in (seconds)",f.fadeInSeconds,0f..3f,!c.busy){v->change {it.copy(fadeInSeconds=v)}}
        ValueSlider("Fade out (seconds)",f.fadeOutSeconds,0f..3f,!c.busy){v->change {it.copy(fadeOutSeconds=v)}}
        }
        if(section=="Motion") {
        ValueSlider("Clip zoom",f.zoom,1f..4f,!c.busy){v->change {it.copy(zoom=v)}}
        ValueSlider("Clip pan X",f.panX,0f..1f,!c.busy){v->change {it.copy(panX=v)}}
        ValueSlider("Clip pan Y",f.panY,0f..1f,!c.busy){v->change {it.copy(panY=v)}}
        }
        if(section=="Colour") {
        ValueSlider("Clip brightness",f.color.brightness,-1f..1f,!c.busy){v->change {it.copy(color=it.color.copy(brightness=v))}}
        ValueSlider("Clip contrast",f.color.contrast,0f..2f,!c.busy){v->change {it.copy(color=it.color.copy(contrast=v))}}
        ValueSlider("Clip saturation",f.color.saturation,0f..2f,!c.busy){v->change {it.copy(color=it.color.copy(saturation=v))}}
        }
        if(section=="Motion" || section=="Colour") {
        Text("Clip animation keys",style=MaterialTheme.typography.titleMedium)
        val sourceAt=clip.startMs+(c.position-p.timeline.startOf(index)).coerceIn(0,clip.lengthMs)
        Text("Keys use this source file's clock. Seek in the clip, set zoom, pan, colour and volume above, then capture.")
        Button(onClick={change {it.copy(keyframes=it.keyframes.set(sourceAt,mapOf(KeyProperty.ZOOM to f.zoom,KeyProperty.PAN_X to f.panX,KeyProperty.PAN_Y to f.panY,KeyProperty.BRIGHTNESS to f.color.brightness,KeyProperty.CONTRAST to f.color.contrast,KeyProperty.SATURATION to f.color.saturation,KeyProperty.VOLUME to f.volume)))}},enabled=!c.busy){Text("Capture source key ${time(sourceAt)}")}
        f.keyframes.sorted.forEach {key->
            Text("Source ${time(key.atMs)}")
            FlowRowCompat {KeyEasing.entries.filter {it!=KeyEasing.CUSTOM}.forEach {e->FilterChip(key.easing==e,{change {it.copy(keyframes=it.keyframes.withEasing(key.atMs,e))}},label={Text(e.name)},enabled=!c.busy)}}
            TextButton(onClick={change {it.copy(keyframes=it.keyframes.remove(key.atMs))}},enabled=!c.busy){Text("Remove clip key")}
        }
        }
        if(section=="Transition" && index<p.timeline.clips.lastIndex) {
            Text("Transition into next clip",style=MaterialTheme.typography.titleMedium)
            Text("Clips overlap by the transition length, limited to half either clip. Native and original custom-expression transitions are available. Punch and technical-difficulties companion assets are still being ported. Render to inspect the final cut.")
            OutlinedButton(onClick={change {it.copy(transition=null)}},enabled=!c.busy){Text("No transition")}
            FlowRowCompat {TransitionKind.entries.filter {it.companionEffect==null && !it.tvBreak}.forEach {kind->FilterChip(f.transition?.kind==kind,{change {it.copy(transition=Transition(kind,f.transition?.durationMs ?: 500L))}},label={Text(kind.name)},enabled=!c.busy)}}
            f.transition?.let {tr->ValueSlider("Transition seconds",tr.durationMs/1000f,.1f..3f,!c.busy){v->change {it.copy(transition=tr.copy(durationMs=(v*1000).toLong()))}}}
        }
    }
}

@Composable private fun LayerBinding(c:EditorController,p:DesktopProject,bound:String?,update:(String?)->Unit) {
    Text("Layer clock. Changing binding clears animation keys.")
    FlowRowCompat {
        FilterChip(bound==null,{update(null)},label={Text("Whole output")},enabled=!c.busy)
        p.timeline.clips.forEachIndexed {i,clip->FilterChip(bound==clip.id,{update(clip.id)},label={Text("Clip ${i+1}")},enabled=!c.busy)}
    }
    if(bound!=null)Text("Clip-bound layers show for that clip. Whole-output time and fade controls do not apply.")
}
@Composable private fun LayerKeys(c:EditorController,keys:Keyframes,at:Long,inRange:Boolean,values:Map<KeyProperty,Float>,update:(Keyframes)->Unit) {
    Text("Animation keys",style=MaterialTheme.typography.titleSmall)
    Text("Seek, set layer controls, then capture. Key times are relative to the layer or bound clip start.")
    Button(onClick={update(keys.set(at,values))},enabled=!c.busy && inRange){Text("Capture layer key ${time(at)}")}
    keys.sorted.forEach {key->
        Text("Key ${time(key.atMs)}")
        FlowRowCompat {KeyEasing.entries.filter {it!=KeyEasing.CUSTOM}.forEach {e->FilterChip(key.easing==e,{update(keys.withEasing(key.atMs,e))},label={Text(e.name)},enabled=!c.busy)}}
        TextButton(onClick={update(keys.remove(key.atMs))},enabled=!c.busy){Text("Remove layer key")}
    }
}

@Composable private fun StudioPanel(c:EditorController,p:DesktopProject,pick:(String)->File?) {
    val aliases=c.studio.paths(p).aliasEntries()
    val lint=remember(c.studioText,c.studioCatalog,p){com.cuscus.ffmpeggui.core.command.studio.CommandLint(c.studioCatalog,aliases).check(c.studioText)}
    PanelCard {
        Text("Command studio",style=MaterialTheme.typography.titleLarge)
        Text("Commands run locally as FFmpeg arguments, never through a shell. This single-output adapter permits listed input aliases and selected filters only. Generated subtitles/AI marks, arbitrary file paths, network and numbered outputs are not yet supported. Manual commands do not automatically apply editor effects. Execution stops at 30 minutes or 1 GiB; Stop cancels a running job.")
        Text("Inputs: "+c.studio.aliases(p).keys.joinToString(", "))
        FlowRowCompat {
            OutlinedButton(onClick={c.studioText=com.cuscus.ffmpeggui.core.command.FfmpegCommand(c.exporter.command(p,"output.${p.format.extension}").arguments.map {arg->c.studio.aliases(p).entries.fold(arg){v,e->v.replace(e.value,e.key)}}).toDisplayString()},enabled=!c.busy){Text("Copy generated edit")}
            OutlinedButton(onClick={c.work("Reading installed FFmpeg help…"){c.studioCatalog=c.studio.catalog();c.status="Installed FFmpeg catalogue loaded."}},enabled=!c.busy){Text("Read installed catalogue")}
            OutlinedButton(onClick={c.studioText=com.cuscus.ffmpeggui.core.command.studio.tidyCommand(c.studioText,c.studioCatalog)},enabled=!c.busy){Text("Tidy command")}
        }
        OutlinedTextField(c.studioText,{c.studioText=it},label={Text("FFmpeg command")},modifier=Modifier.isolateProjectHistory().fillMaxWidth().heightIn(min=160.dp,max=280.dp),enabled=!c.busy)
        Text("${c.studioCatalog.filters.size} filters, ${c.studioCatalog.encoders.size} encoders read from this system.")
        lint.take(12).forEach {Text("${it.severity}: ${it.code} ${it.subject}",color=if(it.severity==com.cuscus.ffmpeggui.core.command.studio.LintSeverity.ERROR)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
        val validation=runCatching {c.studio.validate(p,c.studioText)}.exceptionOrNull()?.message
        if(validation!=null)Text(validation,style=MaterialTheme.typography.bodySmall)
        Button(onClick={
            val ext=com.cuscus.ffmpeggui.core.command.tokenizeCommandLine(c.studioText).lastOrNull {it.matches(Regex("output\\.[A-Za-z0-9]+"))}?.substringAfterLast('.') ?: "mp4"
            pick("manual-export.$ext")?.let {file->
                if(!file.exists() || javax.swing.JOptionPane.showConfirmDialog(null,"Replace ${file.name}?","Existing output",javax.swing.JOptionPane.YES_NO_OPTION)==0)c.work("Running local manual command…"){val out=c.studio.export(p,c.studioText,file);c.status="Manual output ready: ${out.path}"}
            }
        },enabled=!c.busy && validation==null){Text("Run to new file")}
    }
    PanelCard {
        Text("Original recipes",style=MaterialTheme.typography.titleMedium)
        com.cuscus.ffmpeggui.core.command.studio.CommandRecipe.ALL.forEach {recipe->
            val text=recipe.fill(aliases)
            TextButton(onClick={c.studioText=text.orEmpty()},enabled=!c.busy && text!=null){Text(recipe.title)}
            Text(recipe.summary,style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun CompressionPanel(c:EditorController,p:DesktopProject) {
    val v=p.compression ?: Compression()
    fun update(value:Compression)=c.mutate(p.copy(compression=value))
    PanelCard {
        Text("File compression",style=MaterialTheme.typography.titleLarge)
        Text("Export the edited result with the original compression settings. MP4/MKV use CPU HEVC; target video sizes use two passes. JPG quality can be fitted, PNG uses strong deflate with an optional palette. Lossy audio uses a smaller bitrate. Original files are unchanged.")
        Row {Switch(p.compression!=null,{c.mutate(p.copy(compression=if(it)v else null))},enabled=!c.busy);Text("Use compression")}
        if(p.compression!=null) {
            FlowRowCompat {CompressionLevel.entries.forEach {level->FilterChip(v.level==level,{update(v.copy(level=level))},label={Text(level.name.lowercase())},enabled=!c.busy)}}
            Text("A target is a hard maximum here: if the encoded file is larger, export fails without replacing your output. Exact size may be impossible. HDR must be off. Unsupported output formats are rejected.",style=MaterialTheme.typography.bodySmall)
            var target by remember(v.targetBytes) {mutableStateOf(if(v.targetBytes==0L)""else v.targetBytes.toString())}
            OutlinedTextField(target,{text->if(text.length<13 && text.all {it.isDigit()})target=text},label={Text("Target bytes (empty = preset)")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
            Button(onClick={val n=target.toLongOrNull() ?: 0;update(v.copy(targetBytes=n.coerceIn(0,100_000_000_000)))},enabled=!c.busy){Text("Apply target")}
            Row {Checkbox(v.pngPalette,{update(v.copy(pngPalette=it))},enabled=!c.busy);Text("PNG: reduce to 256 colours")}
            Text("PNG target fitting is not supported. Lossy audio target fitting is bitrate-based and can fail for tiny files. HEVC support varies between players. Render/export to check the result.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun TranslationPanel(c:EditorController,p:DesktopProject,window:java.awt.Frame) {
    var repo by remember {mutableStateOf("Xenova/opus-mt-en-fr")}
    var models by remember {mutableStateOf(DesktopTranslation.installed())}
    var selected by remember {mutableStateOf(models.firstOrNull()?.repo)}
    PanelCard {
        Text("Offline subtitle translation",style=MaterialTheme.typography.titleLarge)
        Text("Import a direct Marian/Opus-MT language pair. Translation runs locally in an isolated process. Model files are not automatically downloaded. Hashes are recorded at import and checked again, but are not publisher-authenticated pins. Use only trusted model files.")
        Text("Up to500short cues,1000characters each. Long token sequences are rejected, not silently truncated. Output keeps cue times and source binding, but inline styling can change. Translation may be wrong: review every result before export.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(repo,{repo=it},label={Text("Model repository, e.g. Xenova/opus-mt-en-fr")},modifier=Modifier.isolateProjectHistory(),enabled=!c.busy)
        OutlinedButton(onClick={Desktop.getDesktop().browse(java.net.URI("https://huggingface.co/Xenova/opus-mt-en-fr"))},enabled=!c.busy) {Text("Open example model files")}
        Text("Select config.json, vocab.json, source.spm, encoder_model_quantized.onnx and decoder_model_quantized.onnx. Quantized ONNX files are in the repository's onnx folder. Multilingual/pivot routes are still being ported.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={
            val dialog=FileDialog(window,"Import five translation model files",FileDialog.LOAD);dialog.isMultipleMode=true;dialog.isVisible=true
            if(dialog.files.isNotEmpty())c.work("Checking offline translation model…") {DesktopTranslation.import(repo.trim(),dialog.files.toList(),c.models);models=DesktopTranslation.installed();selected=repo.trim();c.status="Translation model imported. Hashes recorded; no publisher pin is claimed."}
        },enabled=!c.busy) {Text("Import translation files")}
        Text("Opus-MT: University of Helsinki, Jorg Tiedemann and contributors. ONNX conversions by Xenova. Licences vary by model: the linked en-fr base model card lists Apache 2.0. Review your selected model card and licence; no model weights are bundled.",style=MaterialTheme.typography.bodySmall)
        models.forEach {model->
            Row(verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected==model.repo,{selected=model.repo},enabled=!c.busy)
                Text("${model.repo}: ${model.pair.first} to ${model.pair.second}",modifier=Modifier.weight(1f))
                TextButton(onClick={c.work("Removing translation model…") {require(model.directory.canonicalFile.parentFile==DesktopTranslation.root.canonicalFile);model.directory.deleteRecursively();models=DesktopTranslation.installed();selected=models.firstOrNull()?.repo}},enabled=!c.busy){Text("Remove")}
            }
        }
        val subtitles=p.externalSubtitles
        Text("Import SRT/ASS/SSA in Subtitles first. Spoken words are kept separately and are never overwritten.")
        Button(onClick={
            val model=models.first {it.repo==selected};val original=requireNotNull(subtitles)
            c.work("Translating subtitles locally…") {
                val texts=com.cuscus.ffmpeggui.core.translate.SubtitleTranslation.texts(original.content)
                require(texts.isNotEmpty()) {"No subtitle text to translate"}
                val translated=c.models.translate(model.directory,texts){c.status=it}
                val rewritten=com.cuscus.ffmpeggui.core.translate.SubtitleTranslation.rewrite(original.content,translated)
                val next=original.copy(name="${model.pair.second}-${original.name}",content=rewritten);next.validate()
                c.mutate(p.copy(externalSubtitles=next));c.status="Translated ${texts.size} cues. Review all wording before export. Undo restores the original subtitles."
            }
        },enabled=!c.busy && selected!=null && subtitles!=null) {Text("Translate imported subtitles (Undo available)")}
    }
}

@Composable private fun ErasePanel(c:EditorController,p:DesktopProject,window:java.awt.Frame,save:(String)->File?) {
    var mask by remember {mutableStateOf<File?>(null)}
    var precise by remember {mutableStateOf(false)}
    PanelCard {
        Text("Magic eraser: new PNG",style=MaterialTheme.typography.titleLarge)
        Text("Works on the original still picture, not the edited timeline. Select a matching upright PNG mask: opaque pixels are filled, transparent pixels are kept. Up to2megapixels. The original is unchanged. MI-GAN and LaMa invent details: inspect the saved PNG. Integrated edit replacement is still being ported.")
        Text("The PNG must pass invisible TrustMark readback and carry AI picture disclosure. This does not guarantee the invisible mark survives later edits or recompression. Export a new PNG only; it is not silently added to your project.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={Desktop.getDesktop().browse(java.net.URI(com.cuscus.ffmpeggui.marking.MarkerModel.FILES_URL))},enabled=!c.busy){Text("Open original AI mark downloads")}
        Button(onClick={
            val d=FileDialog(window,"Select TrustMark encoder and decoder",FileDialog.LOAD);d.isMultipleMode=true;d.isVisible=true
            if(d.files.isNotEmpty())c.work("Checking pinned picture marking models…"){DesktopModels.importPictureMarkers(d.files.toList());c.status="Picture marking models imported and hash checked."}
        },enabled=!c.busy){Text("Import two TrustMark files")}
        OutlinedButton(onClick={Desktop.getDesktop().browse(java.net.URI("https://huggingface.co/andraniksargsyan/migan"))},enabled=!c.busy){Text("Open MI-GAN model page")}
        Button(onClick={val d=FileDialog(window,"Choose migan_pipeline_v2.onnx",FileDialog.LOAD);d.isVisible=true;d.file?.let {name->val f=File(d.directory,name);c.work("Checking pinned MI-GAN model…"){DesktopModels.importErase(f);c.status="MI-GAN imported and hash checked."}}},enabled=!c.busy){Text("Import MI-GAN model")}
        OutlinedButton(onClick={Desktop.getDesktop().browse(java.net.URI("https://huggingface.co/Carve/LaMa-ONNX"))},enabled=!c.busy){Text("Open pinned LaMa conversion page")}
        Button(onClick={val d=FileDialog(window,"Choose Carve lama_fp32.onnx",FileDialog.LOAD);d.isVisible=true;d.file?.let {name->val file=File(d.directory,name);c.work("Checking pinned LaMa conversion…"){DesktopModels.importLama(file);c.status="LaMa imported and hash checked."}}},enabled=!c.busy){Text("Import LaMa model")}
        FlowRowCompat {FilterChip(!precise,{precise=false},label={Text("MI-GAN")},enabled=!c.busy);FilterChip(precise,{precise=true},label={Text("LaMa")},enabled=!c.busy)}
        Text("MI-GAN needs1,200MiB free RAM; LaMa needs2,000MiB free RAM. Up to2megapixels. LaMa is the fixed512Carve conversion, not arbitrary ONNX.")
        Text("MI-GAN by Picsart AI Research, MIT. LaMa/Carve conversion, Apache2.0. TrustMark by Adobe, MIT. Models are separate imports, not bundled. See model pages and bundled notices.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={val d=FileDialog(window,"Select alpha erase mask PNG",FileDialog.LOAD);d.isVisible=true;d.file?.let {mask=File(d.directory,it)}},enabled=!c.busy){Text("Choose alpha mask")}
        Button(onClick={runCatching {DesktopEraseMask.paint(File(p.sources.first().path),window)}.onSuccess {if(it!=null)mask=it}.onFailure {c.error=it.message}},enabled=!c.busy && p.sources.size==1 && p.sources.first().info.isStill){Text("Paint erase mask")}
        Text(mask?.name ?: "No mask selected. Paint a mask here or choose an external alpha mask.")
        Button(onClick={val chosen=mask ?: return@Button;save("erased.png")?.let {target->c.work("Preparing magic eraser…"){c.models.erase(p.sources.first().path,chosen,target,precise){c.status=it};c.status="New AI-marked PNG saved: ${target.path}. Original and project are unchanged."}}},enabled=!c.busy && mask!=null && p.sources.size==1 && p.sources.first().info.isStill){Text("Erase to new PNG")}
    }
}

@Composable private fun DubPanel(c:EditorController,window:java.awt.Frame,save:(String)->File?) {
    var text by remember {mutableStateOf("This voice carries generated words on Linux.")}
    var voice by remember {mutableStateOf(0)}
    var cues by remember {mutableStateOf<List<com.cuscus.ffmpeggui.core.dub.DubTiming.Line>?>(null)}
    var cueOrigin by remember {mutableStateOf("Single text cue")}
    var addTrack by remember {mutableStateOf(false)}
    PanelCard {
        Text("Offline dubbing: new WAV",style=MaterialTheme.typography.titleLarge)
        Text("US-English Kokoro voices. Up to one minute,30cues,300characters per cue. Uses local eSpeak NG for pronunciation, then adds and verifies invisible AudioSeal marks in the saved WAV. A very short or silent result is refused if its mark cannot be checked.")
        Text("Saves a new WAV; optionally adds it as a separate Sound track, without replacing original audio. its voice disclosure is kept at export, but invisible mark strength after edits is not guaranteed. An SRT uses its own times starting at zero, not your edited timeline. Later edits or recompression can weaken the invisible mark. Review all speech before use.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={Desktop.getDesktop().browse(java.net.URI("https://github.com/marcomorosi06/FFmpegGUI/releases/tag/ai-markers-1"))},enabled=!c.busy){Text("Open original AudioSeal model files")}
        Button(onClick={val d=FileDialog(window,"Choose AudioSeal generator and detector",FileDialog.LOAD);d.isMultipleMode=true;d.isVisible=true;if(d.files.isNotEmpty())c.work("Checking AudioSeal models…"){DesktopDub.importMarkers(d.files.toList());c.status="AudioSeal pins verified and imported."}},enabled=!c.busy){Text("Import both AudioSeal models")}
        OutlinedButton(onClick={Desktop.getDesktop().browse(java.net.URI("https://github.com/modelship-ai/model-bundles/releases/tag/tts/kokoro"))},enabled=!c.busy){Text("Open exact-hash Kokoro archive mirror")}
        Text("Choose kokoro-multi-lang-v1_0-c5f7e2d2.tar.bz2 (about350MB). Only the original Android full-archive hash is accepted. Current upstream repacks are refused even if main files look the same. No weights are bundled.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={val d=FileDialog(window,"Import exact original Kokoro tar.bz2",FileDialog.LOAD);d.isVisible=true;d.file?.let {name->val f=File(d.directory,name);c.work("Verifying and unpacking pinned Kokoro archive…"){DesktopDub.importArchive(f);c.status="Kokoro archive imported and original pins verified."}}},enabled=!c.busy){Text("Import pinned Kokoro archive")}
        Text("Kokoro: hexgrad, Apache2.0. AudioSeal: Meta, MIT. eSpeak NG: GPL, separately installed by your package manager. Imported archive also contains eSpeak data. At least1.2GiB free RAM is required. Other languages and Piper are still being ported.",style=MaterialTheme.typography.bodySmall)
        FlowRowCompat {com.cuscus.ffmpeggui.core.dub.DubLanguage.EN_US.voices.forEach {v->FilterChip(voice==v.id,{voice=v.id},label={Text(v.label)},enabled=!c.busy)}}
        OutlinedTextField(text,{if(it.length<=300)text=it;cues=null;cueOrigin="Single text cue"},label={Text("Single cue, starts at0seconds, up to20seconds")},enabled=!c.busy,modifier=Modifier.isolateProjectHistory().fillMaxWidth())
        Button(onClick={val d=FileDialog(window,"Read standalone SRT cue times (not project timeline)",FileDialog.LOAD);d.isVisible=true;d.file?.let {name->c.work("Reading dubbing cues…"){val f=File(d.directory,name);require(f.extension.equals("srt",true));val s=DesktopSubtitles.import(f,0);val parsed=com.cuscus.ffmpeggui.core.preview.SubtitleParser().parse(s.content).map {com.cuscus.ffmpeggui.core.dub.DubTiming.Line(it.startMs,it.endMs,it.text)};require(parsed.size in 1..30);cues=parsed;cueOrigin="Standalone SRT";c.status="Read ${parsed.size} standalone cues. Times are not remapped by project edits."}}},enabled=!c.busy){Text("Use standalone timed SRT")}
        Button(onClick={c.project?.let {project->c.work("Snapshotting edited output-clock cues…"){cues=DesktopDub.projectLines(project);cueOrigin="Edited project snapshot";c.status="Cues frozen on the current output clock. Later timeline edits will not retime them."}}},enabled=!c.busy){Text("Snapshot project subtitle/transcript cues")}
        Text("$cueOrigin: ${cues?.size ?: 1}cue(s). Snapshot again after timeline changes.")
        Row {Checkbox(addTrack,{addTrack=it},enabled=!c.busy);Text("Add saved WAV as a new Sound track (Undo available)")}
        Text("Adds without muting original sound. The saved track stays on this output clock; later timeline changes will not retime it. Undo removes the track, not the saved WAV.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={save("dub-ai.wav")?.let {output->val lines=cues ?: listOf(com.cuscus.ffmpeggui.core.dub.DubTiming.Line(0,20000,text));c.work("Generating and verifying marked speech…"){c.models.dub(lines,voice,1f,60000,output){c.status=it};if(addTrack){val original=requireNotNull(c.project);val source=c.media.probe(output.path);c.mutate(original.copy(sounds=original.sounds+DesktopSound(source,com.cuscus.ffmpeggui.core.model.PlacedSound(trimEndMs=source.info.durationMs))));c.status="Saved WAV and added marked Sound track. Original audio remains. Review before export; Undo removes track only."}else c.status="Saved marked standalone WAV: ${output.path}. Review speech before use; project audio is unchanged."}}},enabled=!c.busy && DesktopDub.root.isDirectory && DesktopDub.markers.isDirectory){Text("Generate marked new WAV")}
    }
}
