package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.ui.expressive.PanelCard

@Composable internal fun SubjectPanel(c:EditorController,p:DesktopProject) {
    var keep by remember {mutableStateOf(false)}
    var radius by remember {mutableStateOf(.04f)}
    var points by remember {mutableStateOf<List<Float>>(emptyList())}
    PanelCard {
        Text("Still subject cutout",style=MaterialTheme.typography.titleLarge)
        Text("Uses the imported subject model offline. PNG preserves transparency. The saved project embeds its mask. This is segmentation, not image generation.")
        OutlinedButton(onClick={c.cutoutPicture()},enabled=!c.busy && p.sources.first().info.isStill){Text("Cut out subject")}
        if(p.image.key!=null)OutlinedButton(onClick={c.mutate(p.copy(image=p.image.copy(key=null)))},enabled=!c.busy){Text("Remove cutout")}
        Text(if(p.image.key?.cutsSubject==true) "Subject mask applied. Render to inspect edges." else "No subject mask applied.")
        if(p.image.key?.cutsSubject==true) {
            Text("Refine mask on the upright original below, before crop or rotation. Drag to keep or remove. Render after a stroke to inspect the final alpha. Undo restores the previous mask.")
            Row {FilterChip(keep,{keep=true},label={Text("Keep brush")},enabled=!c.busy);FilterChip(!keep,{keep=false},label={Text("Remove brush")},enabled=!c.busy)}
            Text("Brush radius ${(radius*100).toInt()}% of source width")
            Slider(radius,{radius=it},valueRange=.005f..0.2f,enabled=!c.busy)
            val image=c.sourceFrame
            if(image!=null)Canvas(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height).border(1.dp,Color.Gray).pointerInput(p,keep,radius,c.busy){
                fun normalized(o:Offset)=listOf((o.x/size.width).coerceIn(0f,1f),(o.y/size.height).coerceIn(0f,1f))
                detectDragGestures(onDragStart={if(!c.busy)points=normalized(it)},onDragCancel={points=emptyList()},onDragEnd={
                    if(!c.busy && points.size>=2)c.refineSubject(points,radius,keep)
                    points=emptyList()
                }){change,_->if(!c.busy){points=points+normalized(change.position);change.consume()}}
            }) {
                drawImage(image,dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))
                points.chunked(2).filter {it.size==2}.forEach {drawCircle(if(keep)Color.Green.copy(alpha=.3f) else Color.Red.copy(alpha=.3f),radius*size.width,Offset(it[0]*size.width,it[1]*size.height))}
            }
        }
        Text("Magic eraser and HDR output are still being ported.")
    }
}
