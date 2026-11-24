package com.cuscus.ffmpeggui.desktop

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.sound.OriginalSound
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CreativeVoicePicker(current:AudioEffects,enabled:Boolean,change:(AudioEffects)->Unit) {
    var family by remember {mutableStateOf("Retro")}
    Text("Voice presets",style=MaterialTheme.typography.titleMedium)
    Text("Replaces DSP in this scope. Render/export to hear. Character presets are original DSP, not AI voices.",style=MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {CreativeVoice.entries.map{it.family}.distinct().forEach {f->FilterChip(family==f,{family=f},label={Text(f)},enabled=enabled)}}
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {CreativeVoice.entries.filter{it.family==family || it==CreativeVoice.CLEAN}.forEach {v->FilterChip(current==v.effects,{change(v.effects)},label={Text(v.title)},enabled=enabled)}}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun OriginalCues(c:EditorController,p:DesktopProject) {
    var open by remember {mutableStateOf(false)}
    var family by remember {mutableStateOf("Transitions")}
    FilledTonalButton(onClick={open=!open},enabled=!c.busy){Text(if(open)"Hide original cues" else "Original sound cues")}
    if(open) {
        Text("Synthesized original cues, not recordings from another app. Added at output playhead; adjust the track below. Projects reference these local WAV files, so keep them with your media.",style=MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {OriginalSound.entries.map{it.family}.distinct().forEach {f->FilterChip(family==f,{family=f},label={Text(f)},enabled=!c.busy)}}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {OriginalSound.entries.filter{it.family==family}.forEach {cue->OutlinedButton(onClick={
            val at=p.outputAt(c.position)
            c.work("Generating ${cue.title}…") {
                val dir=File(System.getProperty("user.home"),".local/share/ffmpeggui/original-sounds").apply{mkdirs()}
                val file=cue.ensureFile(dir)
                val source=c.media.probe(file.path)
                c.mutate(p.copy(sounds=p.sounds+DesktopSound(source,PlacedSound(startMs=at,trimEndMs=source.info.durationMs,volume=.7f))))
                c.status="${cue.title} added at output playhead"
            }
        },enabled=!c.busy){Text(cue.title)}}}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CreativeLooksPanel(c:EditorController,p:DesktopProject) {
    val selected=p.timeline.clips.firstOrNull{it.id==c.smartClipId}
    var search by remember {mutableStateOf("")}
    Text("Timed visual effects",style=MaterialTheme.typography.titleMedium)
    Text(if(selected==null)"Whole-output clock. Ranges stay at these output times after timeline edits." else "Attached to this clip occurrence, but affects the composite while it plays. Local played-time ranges move with it; removed clips hide them. Review after trim/speed edits.",style=MaterialTheme.typography.bodySmall)
    Text("Render-first preview. Desktop does not show live effects. Flashing looks can affect photosensitive viewers; use with care.",style=MaterialTheme.typography.bodySmall)
    OutlinedTextField(search,{search=it},label={Text("Search looks")},enabled=!c.busy)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {PictureLook.entries.filter{it.id.contains(search.replace(' ','_'),true)}.forEach {kind->
        OutlinedButton(onClick={
            val duration=if(selected==null)p.outputDurationMs else p.clock().let { selected.effects.playedMs(selected.lengthMs)/it.speed }.toLong()
            val start=if(selected==null)p.outputAt(c.position).coerceAtMost((duration-100).coerceAtLeast(0)) else 0L
            val span=LookSpan(kind,start,(start+2000).coerceAtMost(duration))
            if(span.endMs>span.startMs)c.mutate(p.copy(looks=p.looks+DesktopLook(span,selected?.id)))
        },enabled=!c.busy){Text(kind.id.replace('_',' '))}
    }}
    p.looks.forEachIndexed {index,item->
        val v=item.span
        fun update(span:LookSpan)=c.mutate(p.copy(looks=p.looks.mapIndexed{i,old->if(i==index)old.copy(span=span)else old}))
        val clip=item.clipId?.let{id->p.timeline.clips.firstOrNull{it.id==id}}
        val duration=if(item.clipId==null)p.clock().outputDurationMs else clip?.let{(it.effects.playedMs(it.lengthMs)/p.clock().speed).toLong()} ?: v.endMs
        Text("${index+1}. ${v.look.id.replace('_',' ')} · ${if(item.clipId==null)"output" else if(clip==null)"removed clip, inactive" else "clip ${p.timeline.clips.indexOf(clip)+1}"}",style=MaterialTheme.typography.titleSmall)
        ValueSlider("Start seconds",v.startMs/1000f,0f..(duration/1000f).coerceAtLeast(.1f),!c.busy){update(v.copy(startMs=(it*1000).toLong().coerceAtMost(v.endMs-1)))}
        ValueSlider("End seconds",v.endMs/1000f,.001f..(duration/1000f).coerceAtLeast(.1f),!c.busy){update(v.copy(endMs=(it*1000).toLong().coerceAtLeast(v.startMs+1)))}
        ValueSlider("Strength",v.strength,0f..1f,!c.busy){update(v.copy(strength=it))}
        if(v.look.hasAmount)ValueSlider("Amount",v.amount,.25f..2f,!c.busy){update(v.copy(amount=it))}
        if(v.look.moves)ValueSlider("Speed",v.speed,.25f..4f,!c.busy){update(v.copy(speed=it))}
        TextButton(onClick={c.mutate(p.copy(looks=p.looks.filterIndexed{i,_->i!=index}))},enabled=!c.busy){Text("Remove effect")}
    }
}
