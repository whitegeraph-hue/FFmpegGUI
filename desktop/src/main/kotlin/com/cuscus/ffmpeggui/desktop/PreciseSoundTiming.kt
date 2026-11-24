package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal data class SoundTiming(val startMs:Long,val inMs:Long,val outMs:Long)
/** Integer milliseconds avoid floating-point rounding and reject exponents/non-finite values. */
internal fun soundTiming(start:String,from:String,to:String,sourceEnd:Long,outputEnd:Long):SoundTiming? {
    fun value(s:String)=s.trim().takeIf{it.matches(Regex("[0-9]{1,15}"))}?.toLongOrNull()
    val a=value(start) ?: return null;val b=value(from) ?: return null;val d=value(to) ?: return null
    return SoundTiming(a,b,d).takeIf{a<outputEnd && b<d && d<=sourceEnd}
}

@Composable internal fun PreciseSoundTiming(c:EditorController,p:DesktopProject,index:Int) {
    val sound=p.sounds.getOrNull(index) ?: return
    var open by remember(sound) {mutableStateOf(false)}
    OutlinedButton(onClick={open=true},enabled=!c.busy){Text("Precise timing…")}
    if(open) {
        val s=sound.placed
        var start by remember(sound) {mutableStateOf(s.startMs.toString())}
        var from by remember(sound) {mutableStateOf(s.trimStartMs.toString())}
        var to by remember(sound) {mutableStateOf((s.trimEndMs ?: sound.source.info.durationMs).toString())}
        val timing=soundTiming(start,from,to,sound.source.info.durationMs,p.outputDurationMs)
        val changed=timing!=null && (timing.startMs!=s.startMs || timing.inMs!=s.trimStartMs || timing.outMs!=(s.trimEndMs ?: sound.source.info.durationMs))
        val current=c.project===p && !c.busy
        AlertDialog(onDismissRequest={open=false},title={Text("Sound track timing")},text={
            Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()).onPreviewKeyEvent {event->if(event.type==KeyEventType.KeyDown && event.key==Key.Escape){open=false;true}else false},verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Output-timed track, not bound to the selected clip. Times stay fixed after clip reordering. Values are whole milliseconds (1000 ms = 1 second).")
                OutlinedTextField(start,{start=it},label={Text("Start on output · ms")},singleLine=true,enabled=current,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().isolateProjectHistory())
                OutlinedButton(onClick={start=(if(c.player.engaged)c.player.positionMs else p.outputAt(c.position)).toString()},enabled=current){Text("Use output playhead")}
                OutlinedTextField(from,{from=it},label={Text("Source in · ms")},singleLine=true,enabled=current,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().isolateProjectHistory())
                OutlinedTextField(to,{to=it},label={Text("Source out · ms (exclusive)")},singleLine=true,enabled=current,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().isolateProjectHistory())
                Text("Source: 0–${sound.source.info.durationMs} ms. Output start: 0–${(p.outputDurationMs-1).coerceAtLeast(0)} ms. Keep source in before source out. The mix stops at the output end; speed/repeats/fades are unchanged.",style=MaterialTheme.typography.bodySmall)
                if(timing==null)Text("Enter whole milliseconds within these ranges.",color=MaterialTheme.colorScheme.error)
                if(!current)Text("The project changed. Cancel and reopen timing.",color=MaterialTheme.colorScheme.error)
            }
        },confirmButton={Button(onClick={if(timing!=null && c.applySoundTiming(p,index,sound,timing))open=false},enabled=current && changed){Text("Apply")}},dismissButton={TextButton(onClick={open=false}){Text("Cancel")}})
    }
}
