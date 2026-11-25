package com.cuscus.ffmpeggui.desktop

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import com.cuscus.ffmpeggui.core.model.*

class DesktopEditingToolsTest {
 private fun project()=DesktopProject.create(DesktopSource("/tmp/test.mp4",MediaInfo.of(5000,true,320,180,0,false)))
 @Test fun markersRoundTripAndSorted() {
  val markers=listOf(DesktopMarker("b",4000,"Music"),DesktopMarker("a",100,"Cut"))
  assertEquals(markers.reversed(),DesktopMarkers.decode(DesktopMarkers.encode(markers)))
  assertEquals("b",DesktopMarkers.next(markers,100,true,5000)?.id)
  assertNull(DesktopMarkers.next(markers,100,true,3000))
  assertEquals(markers,project().copy(markers=markers).copy(normalize=true).markers)
 }
 @Test fun badMarkersRejected() {
  for(value in listOf(-1L,Long.MIN_VALUE))try {DesktopMarker("a",value,"Bad");fail()}catch(expected:IllegalArgumentException){}
  try {DesktopMarkers.encode(List(501){DesktopMarker("$it",it.toLong(),"m")});fail()}catch(expected:IllegalArgumentException){}
  try {DesktopMarkers.encode(List(2){DesktopMarker("same",0,"m")});fail()}catch(expected:IllegalArgumentException){}
 }
 @Test fun invalidMarkerLoadKeepsCurrentProjectAndHistory()=kotlinx.coroutines.runBlocking {
  val dir=Files.createTempDirectory("bad-markers").toFile()
  val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Unconfined)
  val c=EditorController(scope,DesktopAutosave(File(dir,"recovery")))
  try {
   val media=File(dir,"media.wav")
   assertEquals(0,ProcessBuilder("ffmpeg","-v","error","-f","lavfi","-i","anullsrc","-t","2",media.path).start().waitFor())
   val original=DesktopProject.create(DesktopMedia().probe(media.path));c.project=original;c.mutate(original.copy(normalize=true));val current=c.project
   val file=File(dir,"invalid.ffgui.json");DesktopProjects.save(original,file)
   val root=kotlinx.serialization.json.Json.parseToJsonElement(file.readText()) as kotlinx.serialization.json.JsonObject
   val fields=root.toMutableMap();fields["markers"]=kotlinx.serialization.json.Json.parseToJsonElement("[{\"id\":\"bad\",\"atMs\":-1,\"name\":\"Invalid\"}]")
   file.writeText(kotlinx.serialization.json.JsonObject(fields).toString())
   c.openProject(file);while(c.busy)kotlinx.coroutines.delay(10);assertSame(current,c.project);assertTrue(c.canUndo);assertTrue(c.error!!.contains("markers are invalid"))
  }finally {c.close();scope.coroutineContext[kotlinx.coroutines.Job]?.cancel();dir.deleteRecursively()}
 }
 @Test fun commandFilterMatchesAllTermsWithoutRunningActions() {
  var ran=false
  val values=listOf(DesktopCommand("Sound tracks","Audio","mic"){ran=true},DesktopCommand("Save project","Project","save"){})
  assertEquals("Sound tracks",filterDesktopCommands(values," AUDIO sound ").single().label)
  assertTrue(filterDesktopCommands(values,"missing").isEmpty());assertFalse(ran)
 }
 @Test fun exportHistoryBoundedAndSettingsDoNotCopyEdits() {
  val dir=Files.createTempDirectory("export-history").toFile()
  try {
   val history=DesktopExportHistory(File(dir,"history.json"))
   repeat(15){val f=File(dir,"$it.mp4");f.writeText("output");history.record(f,OutputFormat.MP4,EncodingSettings(quality=.5f),it.toLong())}
   assertEquals(12,history.load().size);assertEquals("14.mp4",File(history.load().first().path).name)
   val p=project().copy(markers=listOf(DesktopMarker("a",100,"Keep")),normalize=true)
   val changed=history.load().first().applyTo(p)
   assertSame(p.timeline,changed.timeline);assertTrue(changed.normalize);assertEquals(p.markers,changed.markers);assertEquals(.5f,changed.encoding.quality)
   history.record(File(dir,"14.mp4"),OutputFormat.WAV,EncodingSettings(),20)
   assertEquals(12,history.load().size);assertEquals(OutputFormat.WAV,history.load().first().format)
  }finally {dir.deleteRecursively()}
 }
 @Test fun markersPersistThroughProjectAndRecoveryAndUndo()=kotlinx.coroutines.runBlocking {
  val dir=Files.createTempDirectory("marker-persistence").toFile()
  val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Unconfined)
  val c=EditorController(scope,DesktopAutosave(File(dir,"recovery")))
  try {
   val media=File(dir,"media.wav")
   val process=ProcessBuilder("ffmpeg","-v","error","-f","lavfi","-i","anullsrc","-t","2",media.path).start();assertEquals(0,process.waitFor())
   val p=DesktopProject.create(DesktopMedia().probe(media.path))
   c.project=p;c.mutate(p.copy(markers=listOf(DesktopMarker("one",1500,"Beat"))))
   c.undo();assertTrue(c.project!!.markers.isEmpty());c.redo();assertEquals(1,c.project!!.markers.size)
   val file=File(dir,"manual.ffgui.json");DesktopProjects.save(c.project!!,file)
   assertEquals(c.project!!.markers,DesktopProjects.load(file,DesktopMedia()).markers)
   DesktopProjects.save(p,file);assertEquals(1,DesktopProjects.load(DesktopProjects.previousFile(file),DesktopMedia()).markers.size)
   assertTrue(c.flushAutosave());assertEquals(c.project!!.markers,DesktopProjects.load(c.autosave.entries().single(),DesktopMedia()).markers)
  }finally {c.close();scope.coroutineContext[kotlinx.coroutines.Job]?.cancel();dir.deleteRecursively()}
 }
 @Test fun malformedHistoryNotOverwritten() {
  val dir=Files.createTempDirectory("export-history").toFile()
  try {val file=File(dir,"history.json");file.writeText("bad");val output=File(dir,"out.mp4");output.writeText("out")
   try {DesktopExportHistory(file).record(output,OutputFormat.MP4,EncodingSettings());fail()}catch(expected:Exception){}
   assertEquals("bad",file.readText())
  }finally {dir.deleteRecursively()}
 }
}
