package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.SpeedCurve
import com.cuscus.ffmpeggui.core.model.SpeedPoint
import kotlin.math.*

@Composable internal fun RampCanvas(curve:SpeedCurve,enabled:Boolean,onChange:(SpeedCurve)->Unit) {
    var selected by remember {mutableStateOf(0)}
    var draft by remember(curve) {mutableStateOf(curve.nodes)}
    selected=selected.coerceIn(draft.indices)
    Text("Tap empty chart to add a node (up to 12). Drag a node to move it. First and last positions stay at the ends. Horizontal axis: footage 0–100%. Vertical: 0.25–4x, logarithmic.")
    fun speed(y:Float)=exp(ln(.25)+y.coerceIn(0f,1f)*ln(16.0)).toFloat()
    fun height(v:Float)=(ln(v.toDouble()/.25)/ln(16.0)).toFloat()
    Canvas(Modifier.fillMaxWidth().height(180.dp).border(1.dp,Color.Gray).pointerInput(curve,enabled){
        fun nearest(o:Offset)=draft.indices.minByOrNull {i->hypot(draft[i].at*size.width-o.x,(1-height(draft[i].speed))*size.height-o.y)} ?: 0
        detectTapGestures {o->if(enabled){
            val nearest=nearest(o);val n=draft[nearest]
            val close=hypot(n.at*size.width-o.x,(1-height(n.speed))*size.height-o.y)<24
            if(close)selected=nearest else if(draft.size<12){
                val at=(o.x/size.width).coerceIn(.01f,.99f)
                if(draft.none {abs(it.at-at)<.01f})onChange(SpeedCurve(draft+SpeedPoint(at,speed(1-o.y/size.height))))
            }
        }}
    }.pointerInput(curve,enabled){
        detectDragGestures(onDragStart={o->if(enabled)selected=draft.indices.minBy {i->hypot(draft[i].at*size.width-o.x,(1-height(draft[i].speed))*size.height-o.y)}},onDragCancel={draft=curve.nodes},onDragEnd={if(enabled)onChange(SpeedCurve(draft))}){change,_->if(enabled){
            val at=when(selected){0->0f;draft.lastIndex->1f;else->(change.position.x/size.width).coerceIn(draft[selected-1].at+minOf(.005f,(draft[selected+1].at-draft[selected-1].at)/3),draft[selected+1].at-minOf(.005f,(draft[selected+1].at-draft[selected-1].at)/3))}
            draft=draft.mapIndexed {i,p->if(i==selected)SpeedPoint(at,speed(1-change.position.y/size.height))else p};change.consume()
        }}
    }) {
        val active=SpeedCurve(draft)
        val neutral=(1-height(1f))*size.height
        drawLine(Color.Gray,Offset(0f,neutral),Offset(size.width,neutral),1f)
        val path=Path()
        for(i in 0..200){val x=i/200f;val y=(1-height(active.speedAt(x.toDouble())))*size.height;if(i==0)path.moveTo(0f,y)else path.lineTo(x*size.width,y)}
        drawPath(path,Color(0xff65d0c0),style=Stroke(2f))
        draft.forEachIndexed {i,p->drawCircle(if(i==selected)Color.Yellow else Color.White,6f,Offset(p.at*size.width,(1-height(p.speed))*size.height))}
    }
    Text("Selected node ${selected+1}: ${(draft[selected].at*100).toInt()}%, ${"%.2f".format(draft[selected].speed)}x")
    OutlinedButton(onClick={onChange(SpeedCurve(curve.nodes.filterIndexed {i,_->i!=selected}));selected=0},enabled=enabled && selected>0 && selected<curve.nodes.lastIndex){Text("Remove selected node")}
}
