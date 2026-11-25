package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun DesktopHint(label:String,modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    if(label.isBlank()) {Box(modifier) {content()};return}
    TooltipArea(tooltip={
        Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.inverseSurface,shadowElevation=4.dp) {
            Text(label,Modifier.padding(horizontal=12.dp,vertical=8.dp),color=MaterialTheme.colorScheme.inverseOnSurface,style=MaterialTheme.typography.labelMedium)
        }
    },modifier=modifier,delayMillis=500,content=content)
}

@Composable internal fun DesktopTimeline(c:EditorController,p:DesktopProject,expandedWorkspace:Boolean=false,availableHeight:Int=360,onAdd:()->Unit,onMarkers:()->Unit={}) {
    val workspace=LocalDesktopWorkspace.current
    val focus=remember {FocusRequester()}
    var zoom by remember {mutableStateOf(1f)}
    val scroll=rememberScrollState()
    var dragging by remember(p) {mutableStateOf<String?>(null)}
    var dragX by remember(p) {mutableStateOf(0f)}
    var dragY by remember(p) {mutableStateOf(0f)}
    var dropIndex by remember(p) {mutableStateOf<Int?>(null)}
    val spans=p.outputClipSpans()
    val duration=p.outputDurationMs.coerceAtLeast(1)
    val index=p.timeline.clips.indexOfFirst {it.id==c.smartClipId}
    val density=LocalDensity.current.density
    val visualIndex=if(index<0)-1 else if(p.effects.reversed)p.timeline.clips.lastIndex-index else index
    BoxWithConstraints(Modifier.height(if(expandedWorkspace)0.dp else workspace.timelineHeight.coerceAtMost(availableHeight).dp).clipToBounds()) {
    val compact=maxWidth<1200.dp
    val smallToolbar=maxWidth<900.dp
    val toolbarWidthDp=maxWidth.value.toInt()
    Surface(Modifier.fillMaxWidth().height(workspace.timelineHeight.coerceAtMost(availableHeight).dp).focusRequester(focus).onKeyEvent {event->
        if(expandedWorkspace || event.type!=KeyEventType.KeyDown || c.busy || event.isMetaPressed)false
        else when(workspace.action(event)) {
            "Left"->{c.moveSelectedVisual(-1);true}
            "Right"->{c.moveSelectedVisual(1);true}
            "Start"->{c.seekOutputEdge(false);true}
            "End"->{c.seekOutputEdge(true);true}
            "Play/pause"->{c.togglePlayback();true}
            "Step left"->{c.stepOutput(-1);true}
            "Step right"->{c.stepOutput(1);true}
            "Remove"->{if(index>=0 && p.timeline.clips.size>1)c.removeClip(index);true}
            "Trim in"->{c.trimSelectedAtPlayhead(true);true}
            "Trim out"->{c.trimSelectedAtPlayhead(false);true}
            "Split"->{c.splitHere();true}
            "Duplicate"->{c.duplicateSelected();true}
            else->if(event.key==Key.Escape && dragging!=null){dragging=null;dropIndex=null;true}else false
        }
    }.focusable(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal=14.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            val actionScroll=rememberScrollState()
            var showMore by remember(compact,smallToolbar,workspace.timelineToolbar) {mutableStateOf(false)}
            val (primaryActions,overflowActions)=workspace.timelineToolbarGroups(toolbarWidthDp)
            fun actionEnabled(action:String)=when(action) {
                "Fit","Zoom in"->true
                "Zoom out"->zoom>1f
                "Split"->!c.busy
                "Duplicate"->!c.busy && index>=0
                "Left"->!c.busy && visualIndex>0
                "Right"->!c.busy && visualIndex>=0 && visualIndex<p.timeline.clips.lastIndex
                "Remove"->!c.busy && index>=0 && p.timeline.clips.size>1
                "Add"->!c.busy && !p.sources[0].info.isStill
                else->false
            }
            fun runAction(action:String) {if(!actionEnabled(action))return;when(action) {
                "Fit"->zoom=1f
                "Zoom in"->zoom=(zoom*1.5f).coerceAtMost(32f)
                "Zoom out"->zoom=(zoom/1.5f).coerceAtLeast(1f)
                "Split"->c.splitHere()
                "Duplicate"->c.duplicateSelected()
                "Left"->c.moveSelectedVisual(-1)
                "Right"->c.moveSelectedVisual(1)
                "Remove"->c.removeClip(index)
                "Add"->onAdd()
            }}
            Row(Modifier.fillMaxWidth().horizontalScroll(actionScroll),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                var chooseClip by remember {mutableStateOf(false)}
                Box {TextButton(onClick={chooseClip=true},enabled=!c.busy) {Text(if(index>=0)"Clip ${index+1} ▾" else "Timeline ▾")}
                    DropdownMenu(chooseClip,{chooseClip=false}) {
                        p.timeline.clips.forEachIndexed {i,clip->DropdownMenuItem(text={Text("${i+1} · ${File(clip.sourceId).name} · ${clip.lengthMs} ms")},onClick={c.selectSmartClip(clip.id);c.seek(p.mediaForClipOutput(i,spans[i].startMs));chooseClip=false})}
                    }}
                if(!compact)Text("${p.timeline.clips.size} clips · ${time(duration)}",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,modifier=if(compact)Modifier.width(100.dp) else Modifier.weight(1f))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick=onMarkers,contentPadding=PaddingValues(horizontal=8.dp)){Text("Markers ${p.markers.size}")}
                val playhead=if(c.player.engaged)c.player.positionMs else p.outputAt(c.position)
                TextButton(onClick={DesktopMarkers.next(p.markers,playhead,false,duration)?.let {c.seekOutput(it.atMs)}},enabled=DesktopMarkers.next(p.markers,playhead,false,duration)!=null,contentPadding=PaddingValues(horizontal=6.dp)){Text("‹")}
                TextButton(onClick={DesktopMarkers.next(p.markers,playhead,true,duration)?.let {c.seekOutput(it.atMs)}},enabled=DesktopMarkers.next(p.markers,playhead,true,duration)!=null,contentPadding=PaddingValues(horizontal=6.dp)){Text("›")}
            }
                primaryActions.forEach {action->when(action) {
                    "Split" -> {TextButton(onClick={runAction(action)},enabled=actionEnabled(action)){SymbolLabel("content_cut","Split")}}
                    "Duplicate" -> {DesktopHint("Duplicate selected clip ("+workspace.shortcuts["Duplicate"]+")") {TextButton(contentPadding=PaddingValues(horizontal=12.dp),onClick={runAction(action)},enabled=actionEnabled(action)){if(compact)Symbol("content_copy",description="Duplicate selected clip") else SymbolLabel("content_copy","Duplicate")}}}
                    "Left" -> {DesktopHint("Move selected clip left ("+workspace.shortcuts["Left"]+")") {TextButton(contentPadding=PaddingValues(horizontal=12.dp),onClick={runAction(action)},enabled=actionEnabled(action)){if(compact)Symbol("arrow_back",description="Move selected clip left") else SymbolLabel("arrow_back","Left")}}}
                    "Right" -> {DesktopHint("Move selected clip right ("+workspace.shortcuts["Right"]+")") {TextButton(contentPadding=PaddingValues(horizontal=12.dp),onClick={runAction(action)},enabled=actionEnabled(action)){if(compact)Symbol("arrow_forward",description="Move selected clip right") else SymbolLabel("arrow_forward","Right")}}}
                    "Remove" -> {DesktopHint("Remove selected clip ("+workspace.shortcuts["Remove"]+")") {TextButton(contentPadding=PaddingValues(horizontal=12.dp),onClick={runAction(action)},enabled=actionEnabled(action)){if(compact)Symbol("delete",description="Remove selected clip") else SymbolLabel("delete","Remove")}}}
                    "Add" -> {FilledTonalButton(onClick={runAction(action)},enabled=actionEnabled(action)){SymbolLabel("add","Add")}}
                    "Fit" -> {TextButton(contentPadding=PaddingValues(horizontal=10.dp),onClick={runAction(action)},enabled=actionEnabled(action)) {SymbolLabel("fit_screen","Fit")}}
                    "Zoom out" -> {DesktopHint("Zoom out") {TextButton(contentPadding=PaddingValues(horizontal=10.dp),onClick={runAction(action)},enabled=actionEnabled(action)){if(compact)Symbol("zoom_out",description="Zoom out") else SymbolLabel("zoom_out","Out")}}}
                    "Zoom in" -> {DesktopHint("Zoom in") {TextButton(contentPadding=PaddingValues(horizontal=10.dp),onClick={runAction(action)},enabled=actionEnabled(action)){if(compact)Symbol("zoom_in",description="Zoom in") else SymbolLabel("zoom_in","In")}}}
                }}
                if(overflowActions.isNotEmpty())Box {
                    TextButton(onClick={showMore=true}) {Text("More ▾")}
                    DropdownMenu(showMore,{showMore=false}) {
                        overflowActions.forEach {action->DropdownMenuItem(text={Text(action)},enabled=actionEnabled(action),onClick={showMore=false;runAction(action)})}
                    }
                }
            }
            if(actionScroll.maxValue>0)HorizontalScrollbar(rememberScrollbarAdapter(actionScroll),Modifier.fillMaxWidth().height(7.dp))

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val width=maxWidth.value*zoom
                val viewportWidth=maxWidth.value*density
                LaunchedEffect(c.smartClipId,p.timeline.clips,zoom) {
                    if(index>=0) {
                        val x=(width*spans[index].startMs/duration*density).toInt()
                        if(x<scroll.value || x>scroll.value+scroll.viewportSize-60)scroll.animateScrollTo((x-40).coerceIn(0,scroll.maxValue))
                    }
                }
                LaunchedEffect(c.player.playing) {
                    while(c.player.playing) {
                        val x=(width*c.player.positionMs/duration*density).toInt()
                        if(x<scroll.value || x>scroll.value+scroll.viewportSize-30)scroll.scrollTo((x-60).coerceIn(0,scroll.maxValue))
                        delay(250)
                    }
                }
                // Keep the pointer fixed in viewport space while the content moves beneath it.
                LaunchedEffect(dragging,c.busy,width) {
                    while(dragging!=null && !c.busy) {
                        val delta=timelineDragEdgeStep(dragX-scroll.value,dragY,viewportWidth,density)
                        if(delta!=0f) {
                            val id=dragging
                            val moved=scroll.scrollBy(delta)
                            if(id!=null && dragging==id) {
                                dragX+=moved
                                dropIndex=p.reorderTarget(id,(dragX/(width*density)*duration).toLong())
                            }
                        }
                        delay(40)
                    }
                }
                Column(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                    Box(Modifier.width(width.dp).height(22.dp)
                        .pointerInput(p,width,c.busy){detectTapGestures {at->if(!c.busy){focus.requestFocus();c.seekOutput((at.x/size.width*duration).toLong())}}}
                        .pointerInput(p,width,c.busy){if(!c.busy)detectDragGestures(
                            onDragStart={at->focus.requestFocus();c.seekOutput((at.x/size.width*duration).toLong())},
                            onDrag={change,_->change.consume();c.seekOutput((change.position.x/size.width*duration).toLong())}
                        )}) {
                        // Bucket dense notes into 12dp targets, keeping ruler labels readable.
                        val visibleMarkers=p.markers.filter {it.atMs<=duration && width*it.atMs/duration in scroll.value/density..(scroll.value+scroll.viewportSize)/density}
                        visibleMarkers.groupBy {(width*it.atMs/duration/12).toInt()}.values.forEach {bucket->
                            val marker=bucket.minBy {it.atMs}
                            DesktopHint(if(bucket.size==1)marker.name else "${bucket.size} markers. Open Markers to choose a note.",Modifier.offset(x=(width*marker.atMs/duration).dp,y=11.dp).width(12.dp).height(11.dp)) {
                                Text("◆",color=MaterialTheme.colorScheme.tertiary,modifier=Modifier.clickable {c.seekOutput(marker.atMs)},style=MaterialTheme.typography.labelSmall)
                            }
                        }
                        val step=maxOf(100L,(duration/(maxOf(1,(width/120).toInt()))/100)*100)
                        var t=0L
                        while(t<=duration) {Text(time(t),Modifier.offset(x=(width*t/duration).dp),style=MaterialTheme.typography.labelSmall);t+=step}
                    }
                    BoxWithConstraints(Modifier.width(width.dp).weight(1f)) {
                        val viewportLeft=scroll.value/density
                        val viewportRight=viewportLeft+scroll.viewportSize/density
                        val overlapping=spans.sortedBy {it.startMs}.zipWithNext().any {(a,b)->a.endMs>b.startMs}
                        spans.forEach {span->
                            val clip=p.timeline.clips[span.index]
                            val clipLeft=width*span.startMs/duration
                            val clipWidth=(width*(span.endMs-span.startMs)/duration).coerceAtLeast(2f)
                            // Keep one small prefetch margin, not a task per offscreen clip.
                            if(dragging!=clip.id && (clipLeft+clipWidth<viewportLeft-160 || clipLeft>viewportRight+160))return@forEach
                            val source=p.sources.first {it.path==clip.sourceId}
                            val selected=clip.id==c.smartClipId
                            val lane=if(overlapping)span.index%2 else 0
                            val clipHeight=if(overlapping)minOf(46.dp,((maxHeight-3.dp)/2).coerceAtLeast(24.dp)) else minOf(88.dp,maxHeight)
                            val smallClip=clipHeight<80.dp
                            val color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                            val ink=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            key(clip.id) {
                            Surface(shape=RoundedCornerShape(9.dp),color=color,contentColor=ink,border=BorderStroke(if(selected)3.dp else 1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),modifier=Modifier.offset(x=(width*span.startMs/duration).dp,y=(lane*(clipHeight.value+3)).dp).width(clipWidth.dp).height(clipHeight).padding(end=2.dp).pointerInput(p,span,c.busy){detectTapGestures(onDoubleTap={if(!c.busy){focus.requestFocus();c.selectSmartClip(clip.id);c.tab="Clip editor"}},onTap={at->if(!c.busy){focus.requestFocus();c.selectSmartClip(clip.id);val out=span.startMs+(at.x/size.width*(span.endMs-span.startMs)).toLong();c.seek(p.mediaForClipOutput(span.index,out))}})}) {
                                var thumb by remember(source.path,clip.startMs,clip.endMs) {mutableStateOf<ImageBitmap?>(null)}
                                val showThumb=clipWidth>if(overlapping)110f else 65f
                                val showPeaks=clipWidth>140f && source.info.hasAudio
                                var waveform by remember(source.path) {mutableStateOf<DesktopWaveform?>(null)}
                                var peakState by remember(source.path) {mutableStateOf("Loading source peaks")}
                                LaunchedEffect(source.path,showPeaks) {
                                    if(showPeaks)try {waveform=c.waveforms.source(source);peakState="Source peaks"}
                                    catch(e:CancellationException){throw e}
                                    catch(_:Exception){peakState=if(source.info.durationMs>1_800_000)"Peaks: 30 min limit" else "Source peaks unavailable"}
                                }
                                @Composable fun Peaks(modifier:Modifier) {
                                    val data=waveform
                                    val bins=(clipWidth/3).toInt().coerceIn(1,256)
                                    val levels=remember(p,data,bins,span.index) {
                                        p.waveformLevels(span.index,span,data,bins)
                                    }
                                    Box(modifier) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            val middle=size.height/2
                                            drawLine(ink.copy(alpha=.2f),Offset(0f,middle),Offset(size.width,middle),1.dp.toPx())
                                            if(data!=null)levels.forEachIndexed {i,peak->
                                                val x=(i+.5f)*size.width/bins
                                                val h=peak*middle
                                                drawLine(ink.copy(alpha=.75f),Offset(x,middle-h),Offset(x,middle+h),1.dp.toPx())
                                            }
                                        }
                                        if(data==null)Text(peakState,maxLines=1,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=5.dp))
                                    }
                                }
                                LaunchedEffect(source.path,clip.startMs,clip.endMs,showThumb) {
                                    if(showThumb && source.info.isVideo)try {
                                        thumb=c.thumbnails.frame(source,clip.startMs+clip.lengthMs/2)
                                    } catch(e:CancellationException) {throw e} catch(_:Exception) {thumb=null}
                                }
                                @Composable fun Thumbnail(modifier:Modifier) {
                                    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),contentAlignment=Alignment.Center) {
                                        if(thumb!=null)Image(thumb!!,"Source thumbnail",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                                        else Text(if(source.info.isVideo)"Source" else "Audio",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                @Composable fun ClipLabels(modifier:Modifier=Modifier) {
                                    Column(modifier) {
                                        Text("${span.index+1}${if(span.backwards)" ←" else ""} · ${File(clip.sourceId).name}",Modifier.padding(horizontal=7.dp,vertical=2.dp),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
                                        if(clipWidth>95f && (clipHeight>=60.dp || overlapping && clipHeight>=42.dp))Text("${time(span.endMs-span.startMs)}${if(clip.effects.speed!=1f)" · ${clip.effects.speed}×" else ""}",Modifier.padding(horizontal=7.dp),maxLines=1,style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Box(Modifier.fillMaxSize()) {
                                if(overlapping)Row(verticalAlignment=Alignment.CenterVertically) {
                                    if(showThumb)Thumbnail(Modifier.width(64.dp).fillMaxHeight())
                                    Column(Modifier.weight(1f)) {
                                        ClipLabels()
                                        if(showPeaks)Peaks(Modifier.fillMaxWidth().height(13.dp))
                                    }
                                } else Column {
                                    if(showThumb)Thumbnail(Modifier.fillMaxWidth().height(if(smallClip)18.dp else if(showPeaks)34.dp else 48.dp))
                                    ClipLabels()
                                    if(showPeaks && !smallClip)Peaks(Modifier.fillMaxWidth().height(14.dp))
                                }
                                if(clipWidth>45f && p.timeline.clips.size>1)Box(Modifier.align(Alignment.TopEnd).size(25.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha=.9f),RoundedCornerShape(6.dp)).pointerInput(p,clip.id,width,c.busy) {
                                    if(!c.busy)detectDragGestures(
                                        onDragStart={at->focus.requestFocus();dragging=clip.id;dragX=(clipLeft+clipWidth-27)*density+at.x;dragY=lane*(clipHeight.value+3)*density+at.y;dropIndex=span.index},
                                        onDragCancel={dragging=null;dropIndex=null},
                                        onDragEnd={val target=dropIndex;if(dragging==clip.id && target!=null && dragY in -20*density..115*density && dragX in scroll.value.toFloat()..(scroll.value+viewportWidth))c.reorderClip(p,clip.id,target);dragging=null;dropIndex=null},
                                        onDrag={change,_->change.consume();if(dragging==clip.id){dragX=(clipLeft+clipWidth-27)*density+change.position.x;dragY=lane*(clipHeight.value+3)*density+change.position.y;dropIndex=if(dragging!=clip.id)null else if(dragY in -20*density..115*density && dragX in scroll.value.toFloat()..(scroll.value+viewportWidth))p.reorderTarget(clip.id,(dragX/(width*density)*duration).toLong()) else null}}
                                    )
                                },contentAlignment=Alignment.Center) {Row {Symbol("arrow_back",size=10);Symbol("arrow_forward",size=10)}}
                                }
                            }
                            }
                        }
                        if(dragging!=null && dropIndex!=null) {
                            val x=p.reorderMarker(dragging!!,dropIndex!!)
                            Box(Modifier.offset(x=(width*x/duration).dp).width(3.dp).height(95.dp).background(MaterialTheme.colorScheme.tertiary))
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            val at=if(c.player.engaged)c.player.positionMs else p.outputAt(c.position)
                            val x=size.width*at/duration
                            // A continuous two-tone guide remains legible across both lanes.
                            val bottom=minOf(size.height,if(overlapping)95.dp.toPx() else 88.dp.toPx())
                            drawLine(Color.White.copy(alpha=.9f),Offset(x,0f),Offset(x,bottom),4.dp.toPx())
                            drawLine(Color(0xff006b5e),Offset(x,0f),Offset(x,bottom),1.5.dp.toPx())
                            drawCircle(Color.White,5.dp.toPx(),Offset(x,4.dp.toPx()))
                            drawCircle(Color(0xff006b5e),3.dp.toPx(),Offset(x,4.dp.toPx()))
                        }
                    }
                }
            }
            HorizontalScrollbar(rememberScrollbarAdapter(scroll),Modifier.fillMaxWidth().height(7.dp))
            Text(if(dragging!=null)dropIndex?.let {"Release to move to clip ${it+1}. Hold near an edge to scroll; outside cancels."} ?: "Outside drop area. Release to cancel." else if(compact)"${p.timeline.clips.size} clips · ${time(duration)} · Originals unchanged · ${if(index>=0)"Clip ${index+1} selected · " else ""}Drag ruler to seek · shortcuts in Settings" else if(index>=0)"Clip ${index+1} selected · click within a clip to seek · double-click for controls · drag ↔ to reorder · shortcuts in Settings · original source thumbnails & peaks" else "Click within a clip to select & seek · double-click for controls · drag ruler to seek · output time · thumbnails & peaks are original sources",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
}
