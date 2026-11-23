package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable internal fun DesktopTimeline(c:EditorController,p:DesktopProject,onAdd:()->Unit) {
    val focus=remember {FocusRequester()}
    var zoom by remember {mutableStateOf(1f)}
    val scroll=rememberScrollState()
    val spans=p.outputClipSpans()
    val duration=p.outputDurationMs.coerceAtLeast(1)
    val index=p.timeline.clips.indexOfFirst {it.id==c.smartClipId}
    val density=LocalDensity.current.density
    BoxWithConstraints {
    val compact=maxWidth<1200.dp
    Surface(Modifier.fillMaxWidth().height(if(compact)244.dp else 204.dp).focusRequester(focus).onKeyEvent {event->
        if(event.type!=KeyEventType.KeyDown || c.busy || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)false else when(event.key) {
            Key.Spacebar->{c.togglePlayback();true}
            Key.DirectionLeft->{c.stepOutput(-1);true}
            Key.DirectionRight->{c.stepOutput(1);true}
            Key.Delete,Key.Backspace->{if(index>=0 && p.timeline.clips.size>1)c.removeClip(index);true}
            Key.S->{c.splitHere();true}
            Key.D->{c.duplicateSelected();true}
            else->false
        }
    }.focusable(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal=14.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            @Composable fun RowScope.ClipActions() {
                TextButton(onClick={c.splitHere()},enabled=!c.busy){SymbolLabel("content_cut","Split")}
                TextButton(onClick={c.duplicateSelected()},enabled=!c.busy && index>=0){SymbolLabel("content_copy","Duplicate")}
                TextButton(onClick={c.moveClip(index,-1)},enabled=!c.busy && index>0){SymbolLabel("arrow_back","Left")}
                TextButton(onClick={c.moveClip(index,1)},enabled=!c.busy && index>=0 && index<p.timeline.clips.lastIndex){SymbolLabel("arrow_forward","Right")}
                TextButton(onClick={c.removeClip(index)},enabled=!c.busy && index>=0 && p.timeline.clips.size>1){SymbolLabel("delete","Remove")}
                FilledTonalButton(onClick=onAdd,enabled=!c.busy && !p.sources[0].info.isStill){SymbolLabel("add","Add")}
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                var chooseClip by remember {mutableStateOf(false)}
                Box {TextButton(onClick={chooseClip=true},enabled=!c.busy) {Text(if(index>=0)"Clip ${index+1} ▾" else "Timeline ▾")}
                    DropdownMenu(chooseClip,{chooseClip=false}) {
                        p.timeline.clips.forEachIndexed {i,clip->DropdownMenuItem(text={Text("${i+1} · ${File(clip.sourceId).name} · ${clip.lengthMs} ms")},onClick={c.selectSmartClip(clip.id);c.seek(p.mediaForClipOutput(i,spans[i].startMs));chooseClip=false})}
                    }}
                Text("${p.timeline.clips.size} clips · ${time(duration)}",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f))
                TextButton(onClick={zoom=1f}) {SymbolLabel("fit_screen","Fit")}
                TextButton(onClick={zoom=(zoom/1.5f).coerceAtLeast(1f)},enabled=zoom>1f){SymbolLabel("zoom_out","Out")}
                TextButton(onClick={zoom=(zoom*1.5f).coerceAtMost(32f)}){SymbolLabel("zoom_in","In")}
                if(!compact)ClipActions()
            }
            if(compact)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {ClipActions()}

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val width=maxWidth.value*zoom
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
                Column(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                    Box(Modifier.width(width.dp).height(22.dp).pointerInput(p,width){detectTapGestures {at->if(!c.busy)c.seek(p.clock().mediaMsForOutput((at.x/size.width*duration).toLong()))}}) {
                        val step=maxOf(100L,(duration/(maxOf(1,(width/120).toInt()))/100)*100)
                        var t=0L
                        while(t<=duration) {Text(time(t),Modifier.offset(x=(width*t/duration).dp),style=MaterialTheme.typography.labelSmall);t+=step}
                    }
                    Box(Modifier.width(width.dp).weight(1f)) {
                        val viewportLeft=scroll.value/density
                        val viewportRight=viewportLeft+scroll.viewportSize/density
                        val overlapping=spans.sortedBy {it.startMs}.zipWithNext().any {(a,b)->a.endMs>b.startMs}
                        spans.forEach {span->
                            val clip=p.timeline.clips[span.index]
                            val clipLeft=width*span.startMs/duration
                            val clipWidth=(width*(span.endMs-span.startMs)/duration).coerceAtLeast(2f)
                            // Keep one small prefetch margin, not a task per offscreen clip.
                            if(clipLeft+clipWidth<viewportLeft-160 || clipLeft>viewportRight+160)return@forEach
                            val source=p.sources.first {it.path==clip.sourceId}
                            val selected=clip.id==c.smartClipId
                            val lane=if(overlapping)span.index%2 else 0
                            val clipHeight=if(overlapping)46.dp else 88.dp
                            val color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                            val ink=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            key(clip.id) {
                            Surface(shape=RoundedCornerShape(9.dp),color=color,contentColor=ink,border=BorderStroke(if(selected)3.dp else 1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),modifier=Modifier.offset(x=(width*span.startMs/duration).dp,y=(lane*49).dp).width(clipWidth.dp).height(clipHeight).padding(end=2.dp).pointerInput(p,span,c.busy){detectTapGestures(onDoubleTap={if(!c.busy){focus.requestFocus();c.selectSmartClip(clip.id);c.tab="Clip editor"}},onTap={at->if(!c.busy){focus.requestFocus();c.selectSmartClip(clip.id);val out=span.startMs+(at.x/size.width*(span.endMs-span.startMs)).toLong();c.seek(p.mediaForClipOutput(span.index,out))}})}) {
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
                                        if(clipWidth>95f)Text("${time(span.endMs-span.startMs)}${if(clip.effects.speed!=1f)" · ${clip.effects.speed}×" else ""}",Modifier.padding(horizontal=7.dp),maxLines=1,style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if(overlapping)Row(verticalAlignment=Alignment.CenterVertically) {
                                    if(showThumb)Thumbnail(Modifier.width(64.dp).fillMaxHeight())
                                    Column(Modifier.weight(1f)) {
                                        ClipLabels()
                                        if(showPeaks)Peaks(Modifier.fillMaxWidth().height(13.dp))
                                    }
                                } else Column {
                                    if(showThumb)Thumbnail(Modifier.fillMaxWidth().height(if(showPeaks)34.dp else 48.dp))
                                    ClipLabels()
                                    if(showPeaks)Peaks(Modifier.fillMaxWidth().height(14.dp))
                                }
                            }
                            }
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
            Text(if(index>=0)"Clip ${index+1} selected · click within a clip to seek · double-click for controls · S split · D duplicate · arrows step · thumbnails & peaks are original sources" else "Click within a clip to select & seek · double-click for controls · output time · thumbnails & peaks are original sources",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
}
