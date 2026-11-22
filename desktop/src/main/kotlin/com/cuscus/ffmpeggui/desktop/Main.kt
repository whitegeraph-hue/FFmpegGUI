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
import com.cuscus.ffmpeggui.core.subtitles.*
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.theme.TealDarkColorScheme
import kotlinx.coroutines.*
import java.awt.FileDialog
import java.io.File
import java.awt.Desktop

fun main() = if (System.getenv("FFMPEGGUI_MODEL_WORKER") != null) {
    runModelWorker(System.getenv("FFMPEGGUI_MODEL_WORKER"))
} else if (System.getenv("FFMPEGGUI_ISOLATED_CHECKS") != null) {
    runIsolatedChecks(System.getenv("FFMPEGGUI_ISOLATED_CHECKS"))
} else if (System.getenv("FFMPEGGUI_MODEL_CHECKS") != null) {
    runModelChecks(arrayOf(System.getenv("FFMPEGGUI_MODEL_CHECKS"),"--speech-first","--speech"))
} else application {
    val scope=rememberCoroutineScope()
    val controller=remember { EditorController(scope) }
    Window(onCloseRequest={ controller.close(); exitApplication() }, title="FFmpegGUI · Linux development preview", state=rememberWindowState(width=1280.dp,height=850.dp)) {
        LaunchedEffect(Unit) {
            System.getenv("FFMPEGGUI_OPEN")?.let { controller.open(it) }
            System.getenv("FFMPEGGUI_SCREENSHOT")?.let { path ->
                delay(System.getenv("FFMPEGGUI_CAPTURE_DELAY")?.toLongOrNull() ?: 5000)
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
                        OutlinedButton(onClick={ pick()?.let { file -> controller.work("Opening project…") { controller.player.stop();controller.rendered?.delete();controller.rendered=null;controller.previewFrame=null;controller.project=DesktopProjects.load(file,controller.media); controller.resetAnalysis(); controller.seek(0); controller.status="Project opened." } } },enabled=!controller.busy) { Text("Open project") }
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
                                Text("Offline speech and subject models are imported once from their original downloads. This is not a finished release.",style=MaterialTheme.typography.bodyMedium)
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
                                    val frame=if(controller.player.playing || controller.player.frame!=null) controller.player.frame else controller.previewFrame ?: controller.sourceFrame
                                    if (frame!=null) Image(frame,if(controller.player.frame!=null || controller.previewFrame!=null) "Rendered edit playback" else "Source frame at the playhead, not an effects preview",modifier=Modifier.fillMaxSize())
                                    else Text(if (project.sources[0].info.isVideo) "Reading frame…" else "Audio file",color=Color.White)
                                }
                                Text(if(controller.previewFrame!=null && project.sources[0].info.isStill) "Rendered edited picture. Source file is unchanged." else if(controller.previewFrame!=null && controller.player.frame==null) "Rendered edited frame. Play preview to inspect motion and sound." else if(controller.player.frame!=null) "Rendered edit · ${time(controller.player.positionMs)} / ${time(controller.player.durationMs)} · synchronized audio/video" else "Source frame · ${time(controller.position)} / ${time(project.timeline.durationMs)}. Render edited preview to inspect cuts and effects.",style=MaterialTheme.typography.bodySmall)
                                controller.player.failure?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                                if(controller.player.frame!=null) {
                                    var scrub by remember { mutableStateOf<Float?>(null) }
                                    Slider(scrub ?: controller.player.positionMs.toFloat(),{ scrub=it },onValueChangeFinished={scrub?.let { controller.player.seek(it.toLong()) };scrub=null},valueRange=0f..controller.player.durationMs.coerceAtLeast(1).toFloat())
                                }
                                Slider(controller.position.toFloat(),{ controller.player.stop();controller.seek(it.toLong()) },valueRange=0f..project.timeline.durationMs.coerceAtLeast(1).toFloat(),enabled=!controller.busy)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Button(onClick={ controller.splitHere() },enabled=!controller.busy && !project.sources[0].info.isStill) { Text("Split here") }
                                    OutlinedButton(onClick={ controller.preview() },enabled=!controller.busy) { Text("Render edited preview") }
                                    controller.rendered?.takeIf { !project.sources[0].info.isStill }?.let { file ->
                                        OutlinedButton(onClick={ if(controller.player.playing) controller.player.pause() else controller.player.play(file) }) { Text(if(controller.player.playing) "Pause preview" else "Play preview") }
                                    }
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
                                val tabs=listOf("Edit","Transcript","Pause cuts","Scenes","Beats","Follow crop","Models","Subtitles","Audio","Text layers","Sound tracks")
                                FlowRowCompat {
                                    tabs.forEach { title -> FilterChip(selected=controller.tab==title,onClick={ controller.tab=title },label={ Text(title) }) }
                                }
                                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                    when(controller.tab) {
                                        "Edit" -> EditPanel(controller,project)
                                        "Subtitles" -> SubtitlesPanel(controller,project)
                                        "Sound tracks" -> SoundTracksPanel(controller,project) {pick()?.path}
                                        "Audio" -> AudioMasterPanel(controller,project)
                                        "Text layers" -> TextLayersPanel(controller,project)
                                        "Pause cuts" -> PauseCutsPanel(controller.smartUi())
                                        "Scenes" -> SceneSplitsPanel(controller.smartUi())
                                        "Beats" -> BeatsPanel(controller.smartUi())
                                        "Follow crop" -> FollowCropPanel(controller.smartUi())
                                        "Transcript" -> TranscriptPanel(controller,project)
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
                                            Text("Models and licences: NVIDIA Parakeet, CC BY 4.0; U2-NetP by Xuebin Qin et al., Apache 2.0. See the source project model record for attributions.",style=MaterialTheme.typography.bodySmall)
                                            OutlinedButton(onClick={ Desktop.getDesktop().browse(java.net.URI("https://github.com/whitegeraph-hue/FFmpegGUI/blob/linux-desktop/docs/MODELS.md")) }) { Text("Model licences and sources") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(controller.status,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                        if (controller.busy) TextButton(onClick={ controller.status="Stopping the local process…";controller.job?.cancel() }) { Text("Stop") }
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
        val formats=when { p.sources[0].info.isStill -> listOf(OutputFormat.JPG,OutputFormat.PNG,OutputFormat.WEBP,OutputFormat.BMP,OutputFormat.TIFF,OutputFormat.SVG,OutputFormat.ASCII,OutputFormat.ANSI)
            !p.sources[0].info.isVideo -> listOf(OutputFormat.MP3,OutputFormat.M4A,OutputFormat.OGG,OutputFormat.OPUS,OutputFormat.FLAC,OutputFormat.WAV,OutputFormat.SRT,OutputFormat.TXT)
            else -> listOf(OutputFormat.MP4,OutputFormat.MKV,OutputFormat.AVI,OutputFormat.GIF,OutputFormat.MP3,OutputFormat.WAV,OutputFormat.SRT,OutputFormat.TXT) }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            formats.forEach { format -> FilterChip(p.format==format,{ c.mutate(p.copy(format=format)) },label={ Text(format.name) },enabled=!c.busy) }
        }
    }
    EncodingPanel(c,p)
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
    PhotoPanel(c,p)
    ArtPanel(c,p)
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
        if(p.words.isEmpty()) Text("No lines yet. Import the speech model in Models, then listen to this file.")
        Button(onClick={ c.listen() },enabled=!c.busy && p.sources[0].info.hasAudio) { Text(if(p.words.isEmpty()) "Listen to the video" else "Listen again") }
        Text("Native inference runs in an isolated local process. Stop ends that process.",style=MaterialTheme.typography.bodySmall)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FlowRowCompat(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) { content() }
}

@Composable private fun EncodingPanel(c:EditorController,p:DesktopProject) {
    PanelCard {
        Text("Encoding",style=MaterialTheme.typography.titleMedium)
        Text("Quality and size use the original Android command settings. CPU encoding only.",style=MaterialTheme.typography.bodySmall)
        ValueSlider("Quality",p.encoding.quality,0f..1f,!c.busy) { c.mutate(p.copy(encoding=p.encoding.copy(quality=it))) }
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
@Composable private fun SubtitlesPanel(c:EditorController,p:DesktopProject) {
    val style=p.subtitleStyle ?: SubtitleStyle()
    PanelCard {
        Text("Spoken subtitles",style=MaterialTheme.typography.titleLarge)
        Text("Listen in Transcript first. Subtitles follow cuts and speed through the original core. Render edited preview to see the actual result.")
        Text("${p.words.size} source words. Export SRT or TXT from Edit without burning text into video.",style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Switch(p.subtitleStyle!=null,{ c.mutate(p.copy(subtitleStyle=if(it) style else null)) },enabled=!c.busy && p.words.isNotEmpty())
            Text("Burn subtitles into video")
        }
        if(p.words.isEmpty()) Button(onClick={c.tab="Transcript"}) {Text("Open Transcript")}
        Text("Style presets")
        FlowRowCompat { SubtitleStyle.PRESETS.forEach { preset -> FilterChip(SubtitlePreset.of(style)==preset,{c.mutate(p.copy(subtitleStyle=preset.apply(style)))},label={Text(preset.name.lowercase())},enabled=!c.busy && p.words.isNotEmpty()) } }
        if(p.subtitleStyle!=null) {
            ValueSlider("Text size",style.size,SubtitleStyle.MIN_SIZE..SubtitleStyle.MAX_SIZE,!c.busy) {c.mutate(p.copy(subtitleStyle=style.copy(size=it)))}
            ValueSlider("Horizontal position",style.x,0f..1f,!c.busy) {c.mutate(p.copy(subtitleStyle=style.copy(x=it)))}
            ValueSlider("Vertical position",style.y,0f..1f,!c.busy) {c.mutate(p.copy(subtitleStyle=style.copy(y=it)))}
            FlowRowCompat { WordHighlight.entries.forEach { highlight -> FilterChip(style.highlight==highlight,{c.mutate(p.copy(subtitleStyle=style.copy(highlight=highlight)))},label={Text(highlight.name.lowercase())},enabled=!c.busy) } }
            FlowRowCompat { SubtitleLook.entries.forEach { look -> FilterChip(style.look==look,{c.mutate(p.copy(subtitleStyle=style.copy(look=look)))},label={Text(look.name.lowercase())},enabled=!c.busy) } }
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(style.bold,{c.mutate(p.copy(subtitleStyle=style.copy(bold=it)))},enabled=!c.busy);Text("Bold");Checkbox(style.uppercase,{c.mutate(p.copy(subtitleStyle=style.copy(uppercase=it)))},enabled=!c.busy);Text("Uppercase") }
            Text("Linux currently uses DejaVu Sans. Custom font and colour pickers remain unported.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun AudioMasterPanel(c:EditorController,p:DesktopProject) {
    val a=p.audioEffects
    fun change(value:AudioEffects) {c.mutate(p.copy(audioEffects=value))}
    PanelCard {
        Text("Audio mastering",style=MaterialTheme.typography.titleLarge)
        Text("The original ten-band EQ and mastering filter chain. Render edited preview to hear the actual result. Live waveform and multi-track editing remain unported.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={change(AudioEffects.NONE)},enabled=!c.busy) {Text("Reset mastering")}
        AudioEffects.EQ_BANDS_HZ.forEachIndexed { index,hz -> ValueSlider("$hz Hz (dB)",a.equalizerGains[index],-12f..12f,!c.busy) { value ->change(a.copy(equalizer=a.equalizerGains.toMutableList().also {it[index]=value}))} }
        ValueSlider("Low cut Hz (0 = off)",a.lowCutHz.toFloat(),0f..500f,!c.busy) {change(a.copy(lowCutHz=it.toInt()))}
        ValueSlider("High cut Hz (0 = off)",a.highCutHz.toFloat(),0f..20000f,!c.busy) {change(a.copy(highCutHz=it.toInt()))}
        ValueSlider("Pitch semitones",a.pitchSemitones,-12f..12f,!c.busy) {change(a.copy(pitchSemitones=it))}
    }
    PanelCard {
        Text("Dynamics and space",style=MaterialTheme.typography.titleMedium)
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.gate!=null,{change(a.copy(gate=if(it)NoiseGate() else null))},enabled=!c.busy);Text("Noise gate")}
        a.gate?.let { g ->ValueSlider("Gate threshold dB",g.thresholdDb,-70f..-10f,!c.busy) {change(a.copy(gate=g.copy(thresholdDb=it)))} }
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.compressor!=null,{change(a.copy(compressor=if(it)Compressor() else null))},enabled=!c.busy);Text("Compressor")}
        a.compressor?.let { comp ->ValueSlider("Threshold dB",comp.thresholdDb,-40f..0f,!c.busy) {change(a.copy(compressor=comp.copy(thresholdDb=it)))};ValueSlider("Ratio",comp.ratio,1f..12f,!c.busy) {change(a.copy(compressor=comp.copy(ratio=it)))} }
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.limiter!=null,{change(a.copy(limiter=if(it)Limiter() else null))},enabled=!c.busy);Text("Limiter")}
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.echo!=null,{change(a.copy(echo=if(it)Echo() else null))},enabled=!c.busy);Text("Echo")}
        a.echo?.let { echo ->ValueSlider("Echo delay ms",echo.delayMs.toFloat(),20f..1000f,!c.busy) {change(a.copy(echo=echo.copy(delayMs=it.toInt())))};ValueSlider("Echo feedback",echo.feedback,0f..0.8f,!c.busy) {change(a.copy(echo=echo.copy(feedback=it)))} }
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(a.reverb!=null,{change(a.copy(reverb=if(it)Reverb() else null))},enabled=!c.busy);Text("Reverb")}
        a.reverb?.let { r ->FlowRowCompat {RoomSize.entries.forEach { size ->FilterChip(r.size==size,{change(a.copy(reverb=r.copy(size=size)))},label={Text(size.name.lowercase())},enabled=!c.busy)}};ValueSlider("Reverb mix",r.mix,0f..1f,!c.busy) {change(a.copy(reverb=r.copy(mix=it)))} }
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

@Composable private fun PhotoPanel(c:EditorController,p:DesktopProject) {
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
        Text("Photo filter")
        Row(Modifier.horizontalScroll(rememberScrollState())) {ImageFilter.entries.forEach {f->FilterChip(e.filter==f,{edit(e.copy(filter=f))},label={Text(f.name)},enabled=!c.busy)}}
        ValueSlider("Filter strength",e.filterAmount,0f..1f,!c.busy){edit(e.copy(filterAmount=it))}
        Text("Photo effect")
        Row(Modifier.horizontalScroll(rememberScrollState())) {ImageEffect.entries.forEach {f->FilterChip(e.effect==f,{edit(e.copy(effect=f))},label={Text(f.name)},enabled=!c.busy)}}
        ValueSlider("Effect strength",e.effectAmount,0f..1f,!c.busy){edit(e.copy(effectAmount=it))}
        ValueSlider("Maximum output side (0 keeps original)",e.output.maxSide.toFloat(),0f..3840f,!c.busy){edit(e.copy(output=e.output.copy(maxSide=if(it<64)0 else it.toInt())))}
        ValueSlider("Photo quality",e.output.quality,0f..1f,!c.busy){edit(e.copy(output=e.output.copy(quality=it)))}
        ValueSlider("Round corners",e.corners,0f..1f,!c.busy){edit(e.copy(corners=it))}
        OutlinedButton(onClick={edit(ImageEdit())},enabled=!c.busy){Text("Reset photo settings")}
        Text("Drawing, masks, layer canvas and HDR output remain in progress.",style=MaterialTheme.typography.bodySmall)
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
            if(t.mode==com.cuscus.ffmpeggui.core.textart.ArtMode.TEXT_FILL)OutlinedTextField(t.phrase,{change(t.copy(phrase=it))},label={Text("Phrase")},enabled=!c.busy)
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
        Text("Whole-output layers. Render to inspect placement. Higher layer number is on top. Clip binding and custom animation curves remain in progress.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={c.mutate(p.copy(captions=p.captions+TextOverlay(text="Your text",layer=p.captions.size)))},enabled=!c.busy){Text("Add text layer")}
    }
    p.captions.forEachIndexed {index,t->
        fun update(v:TextOverlay)=c.mutate(p.copy(captions=p.captions.mapIndexed {i,old->if(i==index)v else old}))
        PanelCard {
            Text("Layer ${index+1}",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(t.text,{update(t.copy(text=it))},label={Text("Text")},enabled=!c.busy,modifier=Modifier.fillMaxWidth())
            ValueSlider("X",t.x,0f..1f,!c.busy){update(t.copy(x=it))}
            ValueSlider("Y",t.y,0f..1f,!c.busy){update(t.copy(y=it))}
            ValueSlider("Size",t.size,.01f.. .3f,!c.busy){update(t.copy(size=it))}
            ValueSlider("Opacity",t.opacity,0f..1f,!c.busy){update(t.copy(opacity=it))}
            Row {Checkbox(t.isBold,{update(t.copy(isBold=it))},enabled=!c.busy);Text("Bold");Checkbox(t.hidden,{update(t.copy(hidden=it))},enabled=!c.busy);Text("Hidden");Checkbox(t.look.box,{update(t.copy(look=t.look.copy(box=it)))},enabled=!c.busy);Text("Box")}
            ValueSlider("Outline",t.look.outline,0f.. .3f,!c.busy){update(t.copy(look=t.look.copy(outline=it)))}
            Row {listOf(0xFFFFFFFFL,0xFF000000L,0xFFFFCC00L,0xFFFF4040L,0xFF40CCFFL).forEach {color-> TextButton(onClick={update(t.copy(color=color))},enabled=!c.busy){Text("■",color=Color(color.toInt()))}}}
            if(!p.sources[0].info.isStill) {
                val duration=p.timeline.durationMs/1000f
                ValueSlider("Start seconds",t.startMs/1000f,0f..duration,!c.busy){update(t.copy(startMs=(it*1000).toLong().coerceAtMost((t.endMs ?: p.timeline.durationMs)-1)))}
                ValueSlider("End seconds",(t.endMs ?: p.timeline.durationMs)/1000f,0f..duration,!c.busy){update(t.copy(endMs=(it*1000).toLong().coerceAtLeast(t.startMs+1)))}
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
    val at=(c.position-t.startMs).coerceAtLeast(0)
    Text("Animation keys",style=MaterialTheme.typography.titleSmall)
    Text("Seek on the output timeline, set the controls above, then capture. Times below are relative to this text's start.",style=MaterialTheme.typography.bodySmall)
    Button(onClick={update(t.copy(keyframes=t.keyframes.set(at,mapOf(KeyProperty.X to t.x,KeyProperty.Y to t.y,KeyProperty.SIZE to t.size,KeyProperty.OPACITY to t.opacity))))},enabled=!c.busy && c.position>=t.startMs && c.position<=(t.endMs ?: p.timeline.durationMs)){Text("Capture at ${time(at)}")}
    t.keyframes.sorted.forEach {key->
        Column {
            Text("${time(key.atMs)} · X ${key.values[KeyProperty.X]} · Y ${key.values[KeyProperty.Y]}",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {KeyEasing.entries.filter {it!=KeyEasing.CUSTOM}.forEach {e->FilterChip(key.easing==e,{update(t.copy(keyframes=t.keyframes.withEasing(key.atMs,e)))},label={Text(e.name)},enabled=!c.busy)}}
            TextButton(onClick={update(t.copy(keyframes=t.keyframes.remove(key.atMs)))},enabled=!c.busy){Text("Remove key")}
        }
    }
}

@Composable private fun SoundTracksPanel(c:EditorController,p:DesktopProject,pick:()->String?) {
    if(p.sources[0].info.isStill){Text("Sound tracks need a video or audio timeline.");return}
    PanelCard {
        Text("Sound tracks",style=MaterialTheme.typography.titleMedium)
        Text("Add music, voice or effects from local files. Tracks mix on the output clock and stop at its end. Render to hear the mix.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={pick()?.let {path->c.work("Reading sound track…") {val source=c.media.probe(path);require(source.info.hasAudio){"File has no audio."};c.mutate(p.copy(sounds=p.sounds+DesktopSound(source,PlacedSound(trimEndMs=source.info.durationMs))))}}},enabled=!c.busy){Text("Add sound file")}
    }
    p.sounds.forEachIndexed {index,sound->
        val s=sound.placed
        fun update(v:PlacedSound)=c.mutate(p.copy(sounds=p.sounds.mapIndexed {i,old->if(i==index)old.copy(placed=v)else old}))
        PanelCard {
            Text("${index+1}. ${File(sound.source.path).name}",style=MaterialTheme.typography.titleSmall)
            Row {Checkbox(sound.muted,{mute->c.mutate(p.copy(sounds=p.sounds.mapIndexed {i,old->if(i==index)old.copy(muted=mute)else old}))},enabled=!c.busy);Text("Muted")}
            ValueSlider("Start on output, seconds",s.startMs/1000f,0f..p.timeline.durationMs/1000f,!c.busy){update(s.copy(startMs=(it*1000).toLong()))}
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
            TextButton(onClick={c.mutate(p.copy(sounds=p.sounds.filterIndexed {i,_->i!=index}))},enabled=!c.busy){Text("Remove track")}
        }
    }
}
