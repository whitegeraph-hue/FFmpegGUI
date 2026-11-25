package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import java.io.File

@Composable internal fun DesktopCommandSearch(commands:List<DesktopCommand>,onClose:()->Unit) {
    var query by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf(0)}
    val results=filterDesktopCommands(commands,query)
    LaunchedEffect(query){selected=0}
    val focus=remember {FocusRequester()}
    LaunchedEffect(Unit){focus.requestFocus()}
    AlertDialog(onDismissRequest=onClose,title={Text("Find a command")},text={Column {
        OutlinedTextField(query,{query=it},label={Text("Search tools and actions")},singleLine=true,modifier=Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent {event->
            if(event.type!=KeyEventType.KeyDown)false else when(event.key) {
                Key.DirectionDown->{selected=(selected+1).coerceAtMost((results.size-1).coerceAtLeast(0));true}
                Key.DirectionUp->{selected=(selected-1).coerceAtLeast(0);true}
                Key.Enter->{results.getOrNull(selected)?.takeIf {it.enabled}?.let {onClose();it.action()};true}
                Key.Escape->{onClose();true}
                else->false
            }
        })
        Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())) {
            if(results.isEmpty())Text("No matching commands",Modifier.padding(16.dp))
            results.forEachIndexed {i,command->TextButton(onClick={onClose();command.action()},enabled=command.enabled,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(containerColor=if(i==selected)MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)) {
                Symbol(command.symbol);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(command.label);Text(command.group,style=MaterialTheme.typography.labelSmall)}
            }}
        }
    }},confirmButton={TextButton(onClick=onClose){Text("Close")}})
}

@Composable internal fun DesktopMarkerEditor(c:EditorController,p:DesktopProject,onClose:()->Unit) {
    var name by remember {mutableStateOf("")}
    var editing by remember {mutableStateOf<String?>(null)}
    val at=if(c.player.engaged)c.player.positionMs else p.outputAt(c.position)
    AlertDialog(onDismissRequest=onClose,title={Text("Timeline markers")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Output-time notes. They stay at fixed output times after edits. Markers past the new end are kept but cannot be jumped to.",style=MaterialTheme.typography.bodySmall)
        Text("Playhead · ${time(at)}")
        OutlinedTextField(name,{name=it.take(100)},singleLine=true,label={Text("Marker name")},modifier=Modifier.fillMaxWidth())
        Button(onClick={c.mutate(p.copy(markers=if(editing==null)p.markers+DesktopMarker(java.util.UUID.randomUUID().toString(),if(c.player.engaged)c.player.positionMs else p.outputAt(c.position),name.trim()) else p.markers.map {if(it.id==editing)it.copy(name=name.trim())else it}));name="";editing=null},enabled=!c.busy && name.isNotBlank() && (editing!=null || p.markers.size<500)){SymbolLabel("add",if(editing==null)"Add marker" else "Rename marker")}
        Column(Modifier.heightIn(max=230.dp).verticalScroll(rememberScrollState())) {
            if(p.markers.isEmpty())Text("No markers yet",Modifier.padding(12.dp))
            p.markers.sortedBy {it.atMs}.forEach {m->Row {
                TextButton(onClick={c.seekOutput(m.atMs)},enabled=m.atMs<=p.outputDurationMs && !c.busy,modifier=Modifier.weight(1f)){Text("${time(m.atMs)} · ${m.name}"+(if(m.atMs>p.outputDurationMs)" · past end" else ""))}
                TextButton(onClick={editing=m.id;name=m.name},enabled=!c.busy){Text("Rename")}
                TextButton(onClick={c.mutate(p.copy(markers=p.markers.filter {it.id!=m.id}))},enabled=!c.busy){Text("Delete")}
            }}
        }
    }},confirmButton={TextButton(onClick=onClose){Text("Done")}})
}

@Composable internal fun DesktopRecentExports(c:EditorController,p:DesktopProject?,onUse:(DesktopProject)->Unit,onClose:()->Unit) {
    var failure by remember {mutableStateOf<String?>(null)}
    val entries=remember {runCatching {c.exportHistory.load()}.onFailure {failure="Recent-export history could not be read. The saved file was left unchanged. New exports still work."}.getOrDefault(emptyList())}
    AlertDialog(onDismissRequest=onClose,title={Text("Recent exports")},text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        failure?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(entries.isEmpty() && failure==null)Text("Completed exports will appear here. Failed or cancelled exports are not added.")
        entries.forEach {e->Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh) {Column(Modifier.padding(12.dp)) {
            Text(File(e.path).name+(if(!File(e.path).exists())" · missing" else ""),style=MaterialTheme.typography.titleSmall)
            Text(e.path,style=MaterialTheme.typography.bodySmall)
            Text("${e.format} · ${e.encoding.resolution} · quality ${"%.2f".format(java.util.Locale.ROOT,e.encoding.quality)}",style=MaterialTheme.typography.labelSmall)
            Row {
                TextButton(onClick={runCatching {val dir=File(e.path).parentFile;require(dir.isDirectory){"Output folder is missing"};java.awt.Desktop.getDesktop().open(dir)}.onFailure {failure="Could not open the folder. Open it manually from the path above. ${it.message ?: "Desktop file manager unavailable"}"}}){Text("Open folder")}
                TextButton(onClick={p?.let {onUse(e.applyTo(it))}},enabled=p!=null && !c.busy){Text("Use settings")}
            }
        }}}
    }},confirmButton={TextButton(onClick=onClose){Text("Done")}})
}
