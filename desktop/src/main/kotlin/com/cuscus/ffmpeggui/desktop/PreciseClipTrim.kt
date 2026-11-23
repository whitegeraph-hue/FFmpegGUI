package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PreciseClipTrim(c:EditorController,clipId:String,start:Long,end:Long) {
    var from by remember(clipId,start) {mutableStateOf(String.format(Locale.US,"%.3f",start/1000.0))}
    var to by remember(clipId,end) {mutableStateOf(String.format(Locale.US,"%.3f",end/1000.0))}
    val a=from.toDoubleOrNull()?.takeIf {it.isFinite() && it>=0 && it<=Long.MAX_VALUE/1000.0}?.let {(it*1000).toLong()}
    val b=to.toDoubleOrNull()?.takeIf {it.isFinite() && it>=0 && it<=Long.MAX_VALUE/1000.0}?.let {(it*1000).toLong()}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(from,{from=it},label={Text("Source in · sec",maxLines=1,style=MaterialTheme.typography.labelSmall)},singleLine=true,modifier=Modifier.isolateProjectHistory().weight(1f),enabled=!c.busy)
        OutlinedTextField(to,{to=it},label={Text("Source out · sec",maxLines=1,style=MaterialTheme.typography.labelSmall)},singleLine=true,modifier=Modifier.isolateProjectHistory().weight(1f),enabled=!c.busy)
    }
    FilledTonalButton(onClick={c.selectSmartClip(clipId);c.trimSelected(a!!,b!!)},enabled=!c.busy && a!=null && b!=null && b-a>=120 && (a!=start || b!=end)) {Text("Apply trim")}
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick={c.selectSmartClip(clipId);c.trimSelectedAtPlayhead(true)},enabled=!c.busy){Text("In at playhead")}
        OutlinedButton(onClick={c.selectSmartClip(clipId);c.trimSelectedAtPlayhead(false)},enabled=!c.busy){Text("Out at playhead")}
    }
    Text("I / O after focusing the timeline mark source in / out. With reverse, source in may be the visual end. Move inside this clip first. Type exact source times, then Apply. Sliders below commit on release. Undo restores the previous trim. Expanding a recognized clip does not create words for newly included audio; Listen again for that range.",style=MaterialTheme.typography.bodySmall)
}
