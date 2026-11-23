package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframes

/** Alignment fractions, not pixels: exports use (frame - layer) * fraction. */
internal fun layerAlignmentAt(x:Float,y:Float,width:Float,height:Float):Pair<Float,Float>? {
    if(listOf(x,y,width,height).any {!it.isFinite()} || width<=0 || height<=0 || x !in 0f..width || y !in 0f..height)return null
    return x/width to y/height
}
internal fun layerAlignmentPercent(x:String,y:String):Pair<Float,Float>? {
    fun parse(text:String):Float?=text.trim().takeIf {it.matches(Regex("(?:[0-9]{1,3})(?:[.][0-9]{1,4})?"))}?.toFloatOrNull()?.takeIf {it.isFinite() && it in 0f..100f}?.div(100f)
    return (parse(x) ?: return null) to (parse(y) ?: return null)
}
internal fun Keyframes.hasPositionValues()=frames.any {KeyProperty.X in it.values || KeyProperty.Y in it.values}

@Composable internal fun LayerAlignmentPad(identity:Any,x:Float,y:Float,enabled:Boolean,onCommit:(Float,Float)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    var dragging by remember(identity) {mutableStateOf(false)}
    var draft by remember(identity) {mutableStateOf<Pair<Float,Float>?>(null)}
    val focus=remember {FocusRequester()}
    var xText by remember(identity,x,y,expanded) {mutableStateOf("%.4f".format(java.util.Locale.US,x*100f))}
    var yText by remember(identity,x,y,expanded) {mutableStateOf("%.4f".format(java.util.Locale.US,y*100f))}
    TextButton(onClick={expanded=!expanded},enabled=enabled){Text("Position with alignment pad")}
    if(expanded)AlertDialog(
        onDismissRequest={expanded=false;dragging=false;draft=null},
        title={Text("Layer alignment")},
        confirmButton={TextButton(onClick={expanded=false;dragging=false;draft=null}){Text("Done")}},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Alignment guide, not a preview. Left/top = 0, centre = 0.5, right/bottom = 1 of the free space around this layer. Drag, then release to apply once. Escape or outside release cancels. Render to inspect.",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(xText,{if(it.length<=9)xText=it},label={Text("X (%)")},singleLine=true,enabled=enabled && !dragging,
                isError=layerAlignmentPercent(xText,"0")==null,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f).isolateProjectHistory())
            OutlinedTextField(yText,{if(it.length<=9)yText=it},label={Text("Y (%)")},singleLine=true,enabled=enabled && !dragging,
                isError=layerAlignmentPercent("0",yText)==null,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f).isolateProjectHistory())
            val typed=layerAlignmentPercent(xText,yText)
            TextButton(onClick={typed?.let {onCommit(it.first,it.second)}},enabled=enabled && !dragging && typed!=null && (kotlin.math.abs(typed.first-x)>.0000006f || kotlin.math.abs(typed.second-y)>.0000006f)){Text("Apply")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton(onClick={onCommit(0f,0f)},enabled=enabled && !dragging){Text("Top left")}
            TextButton(onClick={onCommit(.5f,.5f)},enabled=enabled && !dragging){Text("Centre")}
            TextButton(onClick={onCommit(1f,1f)},enabled=enabled && !dragging){Text("Bottom right")}
        }
        val shown=draft ?: (x to y)
        Text("X ${"%.2f".format(java.util.Locale.US,shown.first)} · Y ${"%.2f".format(java.util.Locale.US,shown.second)}",style=MaterialTheme.typography.labelLarge)
        val colour=MaterialTheme.colorScheme.primary
        val grid=MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .focusRequester(focus).onKeyEvent {event->
                if(event.type==KeyEventType.KeyDown && event.key==Key.Escape && dragging){dragging=false;draft=null;true}else false
            }.focusable()
            .pointerInput(identity,enabled) {
                if(enabled)detectDragGestures(
                    onDragStart={at->focus.requestFocus();dragging=true;draft=layerAlignmentAt(at.x,at.y,size.width.toFloat(),size.height.toFloat())},
                    onDragCancel={dragging=false;draft=null},
                    onDragEnd={val target=draft;if(dragging && target!=null)onCommit(target.first,target.second);dragging=false;draft=null},
                    onDrag={change,_->change.consume();if(dragging)draft=layerAlignmentAt(change.position.x,change.position.y,size.width.toFloat(),size.height.toFloat())}
                )
            }) {
            for(f in listOf(0f,.5f,1f)) {
                drawLine(grid,Offset(size.width*f,0f),Offset(size.width*f,size.height),1.dp.toPx())
                drawLine(grid,Offset(0f,size.height*f),Offset(size.width,size.height*f),1.dp.toPx())
            }
            val at=Offset(shown.first*size.width,shown.second*size.height)
            drawCircle(colour,7.dp.toPx(),at,style=Stroke(2.dp.toPx()))
            drawLine(colour,at-Offset(11.dp.toPx(),0f),at+Offset(11.dp.toPx(),0f),2.dp.toPx())
            drawLine(colour,at-Offset(0f,11.dp.toPx()),at+Offset(0f,11.dp.toPx()),2.dp.toPx())
        }
          }})
}
