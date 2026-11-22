package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.ui.expressive.PanelCard

@Composable internal fun DrawingPanel(c:EditorController,p:DesktopProject) {
    if(!p.sources[0].info.isStill){Text("Drawing currently applies to still pictures. Video brush layers remain in progress.");return}
    var shape by remember {mutableStateOf(StrokeShape.FREE)}
    var color by remember {mutableStateOf(0xffffcc00L)}
    var width by remember {mutableStateOf(.02f)}
    var opacity by remember {mutableStateOf(1f)}
    var eraser by remember {mutableStateOf(false)}
    var points by remember {mutableStateOf<List<Float>>(emptyList())}
    PanelCard {
        Text("Draw on picture",style=MaterialTheme.typography.titleLarge)
        Text("Refresh the background after other edits. Strokes use the edited picture's frame before its border. Render to inspect the final composite. Eraser removes drawing only, never the source picture.")
        OutlinedButton(onClick={c.drawingBackground()},enabled=!c.busy){Text("Refresh drawing background")}
        Row(Modifier.horizontalScroll(rememberScrollState())) {StrokeShape.entries.forEach {v->FilterChip(shape==v,{shape=v},label={Text(v.name)},enabled=!c.busy)}}
        Row {listOf(0xffffffffL,0xff000000L,0xffffcc00L,0xffff4040L,0xff40ccffL).forEach {v->TextButton(onClick={color=v}){Text("■",color=Color(v.toInt()))}}}
        Text("Brush width ${(width*100).toInt()}%")
        Slider(width,{width=it},valueRange=0.002f..0.15f,enabled=!c.busy)
        Text("Opacity ${"%.2f".format(opacity)}")
        Slider(opacity,{opacity=it},valueRange=0f..1f,enabled=!c.busy)
        Row {Checkbox(eraser,{eraser=it},enabled=!c.busy);Text("Eraser")}
        val image=c.drawingFrame
        if(image==null)Text("Refresh drawing background to start.") else {
            val draft=if(points.isNotEmpty())Stroke(points,color,width,opacity,shape,eraser) else null
            val previewScale=minOf(1f,2048f/maxOf(image.width,image.height))
            val painted=remember(p.image.strokes,points,color,width,opacity,shape,eraser,image) {DesktopDrawing.render(p.image.strokes+listOfNotNull(draft),(image.width*previewScale).toInt().coerceAtLeast(1),(image.height*previewScale).toInt().coerceAtLeast(1)).toComposeImageBitmap()}
            Canvas(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height).border(1.dp,Color.Gray).pointerInput(p,color,width,opacity,eraser,c.busy){
                detectTapGestures {o->if(!c.busy)c.mutate(p.copy(image=p.image.copy(strokes=p.image.strokes+Stroke(listOf((o.x/size.width).coerceIn(0f,1f),(o.y/size.height).coerceIn(0f,1f)),color,width,opacity,shape,eraser))))}
            }.pointerInput(p,shape,color,width,opacity,eraser,c.busy){
                fun point(o:Offset)=listOf((o.x/size.width).coerceIn(0f,1f),(o.y/size.height).coerceIn(0f,1f))
                detectDragGestures(onDragStart={if(!c.busy)points=point(it)},onDragCancel={points=emptyList()},onDragEnd={
                    if(points.size>=2 && !c.busy)c.mutate(p.copy(image=p.image.copy(strokes=p.image.strokes+Stroke(points,color,width,opacity,shape,eraser))))
                    points=emptyList()
                }){change,_->if(!c.busy){points=points+point(change.position);change.consume()}}
            }) {
                drawImage(image,dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))
                drawImage(painted,dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))
            }
        }
        Row {
            TextButton(onClick={if(p.image.strokes.isNotEmpty())c.mutate(p.copy(image=p.image.copy(strokes=p.image.strokes.dropLast(1))))},enabled=!c.busy && p.image.strokes.isNotEmpty()){Text("Undo stroke")}
            TextButton(onClick={c.mutate(p.copy(image=p.image.copy(strokes=emptyList())))},enabled=!c.busy && p.image.strokes.isNotEmpty()){Text("Clear drawing")}
        }
        Text("${p.image.strokes.size} saved strokes")
    }
}
