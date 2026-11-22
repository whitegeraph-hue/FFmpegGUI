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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.cuscus.ffmpeggui.*
import com.cuscus.ffmpeggui.core.editing.wordBlocks
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.theme.TealDarkColorScheme
import kotlinx.coroutines.*
import java.awt.FileDialog
import java.io.File
import java.awt.Desktop

fun main() = application {
    val scope=rememberCoroutineScope()
    val controller=remember { EditorController(scope) }
    Window(onCloseRequest={ controller.close(); exitApplication() }, title="FFmpegGUI · Linux development preview", state=rememberWindowState(width=1280.dp,height=850.dp)) {
        LaunchedEffect(Unit) {
            System.getenv("FFMPEGGUI_OPEN")?.let { controller.open(it) }
            System.getenv("FFMPEGGUI_SCREENSHOT")?.let { path ->
                delay(5000)
                System.getenv("FFMPEGGUI_TAB")?.let { controller.tab=it }; delay(500)
                val screen=java.awt.Robot().createScreenCapture(java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize))
                javax.imageio.ImageIO.write(screen,"png",File(path))
                controller.close(); exitApplication()
            }
        }
        fun pick(save: Boolean = false, name: String? = null): File? {
            val dialog=FileDialog(window,if (save) "Export a new file" else "Open media",if (save) FileDialog.SAVE else FileDialog.LOAD)
            if (name!=null) dialog.file=name
            dialog.isVisible=true
            return dialog.file?.let { File(dialog.directory,it) }
        }
        MaterialTheme(colorScheme=TealDarkColorScheme) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("FFmpegGUI",style=MaterialTheme.typography.headlineMedium)
                            Text("Linux development preview · shared Android editing core",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                        }
                        OutlinedButton(onClick={ pick()?.let { controller.open(it.path) } },enabled=!controller.busy) { Text("Open media") }
                        OutlinedButton(onClick={ pick()?.let { file -> controller.work("Opening project…") { controller.project=DesktopProjects.load(file,controller.media); controller.resetAnalysis(); controller.seek(0); controller.status="Project opened." } } },enabled=!controller.busy) { Text("Open project") }
                        OutlinedButton(onClick={ controller.project?.let { p -> pick(true,"project.ffgui.json")?.let { file -> controller.work("Saving project…") { DesktopProjects.save(p,file); controller.status="Project saved: ${file.path}" } } } },enabled=!controller.busy && controller.project!=null) { Text("Save project") }
                        OutlinedButton(onClick={ controller.undo() },enabled=!controller.busy && controller.project!=null) { Text("Undo") }
                        Button(onClick={ controller.project?.let { p ->
                            pick(true,"${File(p.sources[0].path).nameWithoutExtension}-edited.${p.format.extension}")?.let { file ->
                                if (!file.exists() || javax.swing.JOptionPane.showConfirmDialog(window,"Replace ${file.name}?","Existing output",javax.swing.JOptionPane.YES_NO_OPTION)==0) controller.export(file)
                            }
                        } },enabled=!controller.busy && controller.project!=null) { Text("Export") }
                    }
                    Text("Work in progress: not all Android editors and AI tools have been ported. Originals are never edited.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    val project=controller.project
                    if (project==null) {
                        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                Text("Your files. Your machine.",style=MaterialTheme.typography.headlineMedium)
                                Text("Open a clip to edit its timeline, find scene changes and musical beats, and export locally.")
                                Text("Speech and subject-following adapters are being connected. This is not a finished release.",style=MaterialTheme.typography.bodyMedium)
                                Button(onClick={ pick()?.let { controller.open(it.path) } },enabled=!controller.busy) { Text("Choose a file") }
                            }
                        }
                    } else {
                        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                            Column(Modifier.weight(1.15f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text(File(project.sources[0].path).name,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                                    Text("${project.sources[0].info.width} × ${project.sources[0].info.height}",style=MaterialTheme.typography.labelMedium)
                                }
                                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.Black),contentAlignment=Alignment.Center) {
                                    val frame=controller.sourceFrame
                                    if (frame!=null) Image(frame,"Source frame at the playhead, not an effects preview",modifier=Modifier.fillMaxSize())
                                    else Text(if (project.sources[0].info.isVideo) "Reading frame…" else "Audio file",color=Color.White)
                                }
                                Text("Source frame · ${time(controller.position)} / ${time(project.timeline.durationMs)}. Use Render edited preview to inspect cuts and effects.",style=MaterialTheme.typography.bodySmall)
                                Slider(controller.position.toFloat(),{ controller.seek(it.toLong()) },valueRange=0f..project.timeline.durationMs.coerceAtLeast(1).toFloat(),enabled=!controller.busy)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Button(onClick={ controller.splitHere() },enabled=!controller.busy) { Text("Split here") }
                                    OutlinedButton(onClick={ controller.preview() },enabled=!controller.busy) { Text("Render edited preview") }
                                    controller.rendered?.let { file -> OutlinedButton(onClick={ scope.launch { runCatching { controller.media.process.checked(listOf("ffplay","-autoexit","-window_title","FFmpegGUI edited preview",file.path)) }.onFailure { controller.error=it.message } } }) { Text("Play preview") } }
                                }
                                Text("Timeline · ${project.timeline.clips.size} clips",style=MaterialTheme.typography.titleSmall)
                                LazyColumn(Modifier.heightIn(max=180.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                    itemsIndexed(project.timeline.clips) { index,clip ->
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal=10.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
                                            Text("${index+1}   ${time(clip.startMs)} → ${time(clip.endMs ?: 0)}",modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                                            TextButton(onClick={ controller.seek(project.timeline.startOf(index)) },enabled=!controller.busy) { Text("Seek") }
                                            TextButton(onClick={ controller.moveClip(index,-1) },enabled=!controller.busy && index>0) { Text("↑") }
                                            TextButton(onClick={ controller.moveClip(index,1) },enabled=!controller.busy && index<project.timeline.clips.lastIndex) { Text("↓") }
                                            TextButton(onClick={ controller.removeClip(index) },enabled=!controller.busy && project.timeline.clips.size>1) { Text("Remove") }
                                        }
                                    }
                                }
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                val tabs=listOf("Edit","Transcript","Pause cuts","Scenes","Beats","Follow crop","Models")
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    tabs.forEach { title -> FilterChip(selected=controller.tab==title,onClick={ controller.tab=title },label={ Text(title) }) }
                                }
                                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                    when(controller.tab) {
                                        "Edit" -> EditPanel(controller,project)
                                        "Pause cuts" -> PauseCutsPanel(controller.smartUi())
                                        "Scenes" -> SceneSplitsPanel(controller.smartUi())
                                        "Beats" -> BeatsPanel(controller.smartUi())
                                        "Follow crop" -> FollowCropPanel(controller.smartUi())
                                        "Transcript" -> TranscriptPanel(controller,project)
                                        "Models" -> PanelCard {
                                            Text("Offline models",style=MaterialTheme.typography.titleLarge)
                                            Text("The Linux adapters for the same Parakeet and U2-NetP models are being connected. No cloud processing or automatic uploads.")
                                            Text("Model import, hash checks and runtime tests are not complete yet.")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(controller.status,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                        if (controller.busy) TextButton(onClick={ controller.job?.cancel() }) { Text("Stop") }
                    }
                    controller.error?.let { message -> AlertDialog(onDismissRequest={ controller.error=null },title={ Text("Could not finish") },text={ Box(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())) { Text(message) } },confirmButton={ TextButton(onClick={ controller.error=null }) { Text("Close") } }) }
                }
            }
        }
    }
}

@Composable
private fun EditPanel(c: EditorController,p: DesktopProject) {
    PanelCard {
        Text("Export format",style=MaterialTheme.typography.titleMedium)
        val formats=when { p.sources[0].info.isStill -> listOf(OutputFormat.JPG,OutputFormat.PNG,OutputFormat.BMP,OutputFormat.TIFF)
            !p.sources[0].info.isVideo -> listOf(OutputFormat.MP3,OutputFormat.M4A,OutputFormat.OGG,OutputFormat.OPUS,OutputFormat.FLAC,OutputFormat.WAV)
            else -> listOf(OutputFormat.MP4,OutputFormat.MKV,OutputFormat.AVI,OutputFormat.GIF,OutputFormat.MP3,OutputFormat.WAV) }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            formats.forEach { format -> FilterChip(p.format==format,{ c.mutate(p.copy(format=format)) },label={ Text(format.name) },enabled=!c.busy) }
        }
    }
    PanelCard {
        Text("Picture and sound",style=MaterialTheme.typography.titleMedium)
        ValueSlider("Speed",p.effects.speed,.25f..4f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(speed=it))) }
        ValueSlider("Volume",p.effects.volume,0f..2f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(volume=it))) }
        if (p.sources[0].info.isVideo) {
            ValueSlider("Brightness",p.effects.color.brightness,-1f..1f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(color=p.effects.color.copy(brightness=it)))) }
            ValueSlider("Contrast",p.effects.color.contrast,0f..2f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(color=p.effects.color.copy(contrast=it)))) }
            ValueSlider("Saturation",p.effects.color.saturation,0f..2f,!c.busy) { c.mutate(p.copy(effects=p.effects.copy(color=p.effects.color.copy(saturation=it)))) }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ c.mutate(p.copy(effects=p.effects.copy(rotation=Rotation.entries[(p.effects.rotation.ordinal+1)%4]))) },enabled=!c.busy) { Text("Rotate ${p.effects.rotation.degrees}°") }
                OutlinedButton(onClick={ c.mutate(p.copy(effects=p.effects.copy(flipHorizontal=!p.effects.flipHorizontal))) },enabled=!c.busy) { Text(if(p.effects.flipHorizontal) "Unflip" else "Flip horizontal") }
            }
        }
    }
    PanelCard {
        Text("Command preview",style=MaterialTheme.typography.titleMedium)
        Text("Generated by the unchanged Kotlin CommandBuilder used on Android.",style=MaterialTheme.typography.bodySmall)
        val command=remember(p) { runCatching { c.exporter.command(p,"output.${p.format.extension}").arguments.joinToString(" ") }.getOrElse { it.message.orEmpty() } }
        SelectionContainerCompat(command)
    }
}
@Composable private fun SelectionContainerCompat(text: String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(text,style=MaterialTheme.typography.bodySmall) } }
@Composable private fun ValueSlider(label:String,value:Float,range:ClosedFloatingPointRange<Float>,enabled:Boolean,onChange:(Float)->Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    Text("$label · ${"%.2f".format(java.util.Locale.US,draft)}",style=MaterialTheme.typography.labelLarge)
    Slider(draft,{ draft=it },valueRange=range,onValueChangeFinished={ onChange(draft) },enabled=enabled)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TranscriptPanel(c:EditorController,p:DesktopProject) {
    PanelCard {
        Text("Transcript",style=MaterialTheme.typography.titleLarge)
        Text("Word taps seek the source on the current timeline. Line edits use the same cut/keep logic as Android.")
        if(p.words.isEmpty()) Text("No lines yet. The offline speech adapter is being connected.")
        val blocks=remember(p.words) { p.words.wordBlocks().second }
        blocks.forEach { block ->
            HorizontalDivider()
            Text("Line at ${time(block.startMs)}",style=MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                block.words.forEach { word -> TextButton(onClick={ c.seekWord(word) },contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) { Text(word.text) } }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ c.transcriptCut(block,false) },enabled=!c.busy) { Text("Cut from video") }
                OutlinedButton(onClick={ c.transcriptCut(block,true) },enabled=!c.busy) { Text("Keep only this") }
            }
        }
    }
}
