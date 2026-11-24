package com.cuscus.ffmpeggui.desktop
import org.junit.Assert.*
import org.junit.Test
class SoundTimingTest {
 @Test fun millisecondPrecision(){assertEquals(SoundTiming(1234,17,97),soundTiming("1234","17","97",1000,5000))}
 @Test fun nonIntegerAndOverflowRefused(){for(s in listOf("NaN","Infinity","-1","1.0","1e3","","99999999999999999999"))assertNull(s,soundTiming(s,"0","80",1000,5000))}
 @Test fun boundsAndExclusiveEnd(){assertNull(soundTiming("5000","0","80",1000,5000));assertNull(soundTiming("1","80","80",1000,5000));assertNull(soundTiming("1","0","1001",1000,5000));assertEquals(SoundTiming(4999,0,1000),soundTiming("4999","0","1000",1000,5000))}
 @Test fun surroundingWhitespaceAllowed(){assertEquals(SoundTiming(1234,17,97),soundTiming(" 1234 ","17 "," 97",1000,5000))}
}

class SoundTimingControllerTest {
 private fun project(end:Long?=null)=DesktopProject.create(DesktopSource("video.mp4",com.cuscus.ffmpeggui.core.model.MediaInfo.of(5000,true,320,180,0,false))).copy(sounds=listOf(
  DesktopSound(DesktopSource("cue.wav",com.cuscus.ffmpeggui.core.model.MediaInfo.of(1000,false,0,0,0,true)),com.cuscus.ffmpeggui.core.model.PlacedSound(trimEndMs=end,repeats=3,speed=1.25f,fadeInSeconds=.01f,fadeOutSeconds=.02f)),
  DesktopSound(DesktopSource("other.wav",com.cuscus.ffmpeggui.core.model.MediaInfo.of(1000,false,0,0,0,true)))))
 private fun controller(test:(EditorController)->Unit){val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Unconfined);val c=EditorController(scope);try{test(c)}finally{c.close();scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()}}
 @Test fun nullEndRetainedForStartAndInEdits()=controller {c->
  for(t in listOf(SoundTiming(1234,0,1000),SoundTiming(1234,17,1000))) {
   val p=project();c.project=p;assertTrue(c.applySoundTiming(p,0,p.sounds[0],t));val result=c.project!!.sounds[0].placed
   assertEquals(p.sounds[0].placed.copy(startMs=t.startMs,trimStartMs=t.inMs),result);assertNull(result.trimEndMs);assertNull(result.lengthMs)
   assertFalse(DesktopExport().command(c.project!!,"output.mp4").arguments.joinToString(" ").contains("aloop"))
  }
 }
 @Test fun explicitEndPreservedOrEdited()=controller {c->
  for(end in listOf(97L,1000L)){val p=project(1000);c.project=p;assertTrue(c.applySoundTiming(p,0,p.sounds[0],SoundTiming(1234,17,end)));assertEquals(p.sounds[0].placed.copy(startMs=1234,trimStartMs=17,trimEndMs=end),c.project!!.sounds[0].placed)}
  val p=project();c.project=p;assertTrue(c.applySoundTiming(p,0,p.sounds[0],SoundTiming(1234,17,97)));assertEquals(97L,c.project!!.sounds[0].placed.trimEndMs)
 }
 @Test fun oneUndoRedoAndNoOpHistory()=controller {c->
  val p=project();c.project=p;assertTrue(c.applySoundTiming(p,0,p.sounds[0],SoundTiming(0,0,1000)));assertFalse(c.canUndo);assertSame(p,c.project)
  assertTrue(c.applySoundTiming(p,0,p.sounds[0],SoundTiming(1234,17,97)));val applied=c.project!!;assertSame(p.sounds[1],applied.sounds[1]);assertTrue(c.canUndo)
  c.undo();assertSame(p,c.project);assertFalse(c.canUndo);assertTrue(c.canRedo)
  assertTrue(c.applySoundTiming(p,0,p.sounds[0],SoundTiming(0,0,1000)));assertTrue(c.canRedo)
  c.redo();assertSame(applied,c.project);assertFalse(c.canRedo)
 }
 @Test fun staleBusyWrongTrackAndInvalidRejected()=controller {c->
  val p=project();c.project=p;val t=SoundTiming(1234,17,97)
  c.busy=true;assertFalse(c.applySoundTiming(p,0,p.sounds[0],t));c.busy=false
  assertFalse(c.applySoundTiming(p.copy(),0,p.sounds[0],t));assertFalse(c.applySoundTiming(p,1,p.sounds[0],t));assertFalse(c.applySoundTiming(p,0,p.sounds[0],SoundTiming(5000,0,80)))
  assertSame(p,c.project);assertFalse(c.canUndo)
 }
}
