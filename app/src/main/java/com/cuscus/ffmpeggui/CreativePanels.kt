@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.cuscus.ffmpeggui
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.sound.OriginalSound

@Composable internal fun CreativeColourPresets(current:ColorSettings,change:(ColorSettings)->Unit) {
    Text("Colour recipes",style=MaterialTheme.typography.titleMedium)
    Text("Replaces colour settings in this scope. Undo restores them. Render to check blur, grain and finishing touches.",style=MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {CreativeColour.entries.forEach {p->FilterChip(selected=current==p.color,onClick={change(p.color)},label={Text(p.title)})}}
}
@Composable internal fun OriginalSoundPicker(add:(OriginalSound)->Unit) {
    var open by remember {mutableStateOf(false)}
    var family by remember {mutableStateOf("Transitions")}
    FilledTonalButton(onClick={open=!open}) {Text(if(open)"Hide original sound cues" else "Original sound cues")}
    if(open) {
        Text("Synthesized original cues, not recordings from another app. Adds a track at the playhead; use its volume, trim and fades to adjust it. Keep the generated local sound files with the project.",style=MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {OriginalSound.entries.map{it.family}.distinct().forEach {f->FilterChip(family==f,{family=f},label={Text(f)})}}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {OriginalSound.entries.filter{it.family==family}.forEach {p->OutlinedButton(onClick={add(p)}){Text(p.title)}}}
    }
}
