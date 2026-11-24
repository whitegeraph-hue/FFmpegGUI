package com.cuscus.ffmpeggui.desktop

import androidx.compose.ui.text.input.TextFieldValue
import com.cuscus.ffmpeggui.core.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TitleTextTest {
 private fun project():DesktopProject=DesktopProject.create(DesktopSource("video.mp4",MediaInfo.of(5000,true,320,180,0,false))).copy(captions=listOf(TextOverlay(text="Original",x=.2f,y=.7f,size=.08f,startMs=120,endMs=2000,isBold=true,opacity=.6f),TextOverlay(text="Other",layer=1)))
 private fun controller(test:(EditorController)->Unit){val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val c=EditorController(scope);try{test(c)}finally{c.close();scope.cancel()}}
 @Test fun longDraftIsOneUndoAndPreservesEarlierClipEdit()=controller {c->
  val original=project();c.project=original
  c.mutate(original.copy(timeline=original.timeline.updateEffects(original.timeline.clips[0].id){it.copy(volume=.5f)}))
  val prior=c.project!!;val d=TitleTextDraft(prior,0,prior.captions[0])
  val title="An opening title that is deliberately longer than fifty characters, with punctuation."
  title.indices.forEach {d.edit(TextFieldValue(title.take(it+1)))}
  assertSame(prior,c.project);assertTrue(c.applyTitleText(d));val applied=c.project!!
  assertEquals(prior.captions[0].copy(text=title),applied.captions[0]);assertSame(prior.captions[1],applied.captions[1]);assertEquals(prior.timeline,applied.timeline)
  c.undo();assertSame(prior,c.project);assertTrue(c.canUndo)
  c.undo();assertSame(original,c.project);assertFalse(c.canUndo)
  c.redo();assertSame(prior,c.project);c.redo();assertSame(applied,c.project);assertFalse(c.canRedo)
 }
 @Test fun cancelIsOnlyDiscardingDraftAndDoesNotTouchHistory()=controller {c->
  val p=project();c.project=p;val d=TitleTextDraft(p,0,p.captions[0]);d.edit(TextFieldValue("Discard this"));assertSame(p,c.project);assertFalse(c.canUndo);assertFalse(c.canRedo)
 }
 @Test fun unchangedApplyKeepsHistoryRedoAndProjectIdentity()=controller {c->
  val p=project();c.project=p;assertTrue(c.applyTitleText(TitleTextDraft(p,0,p.captions[0])));assertFalse(c.canUndo)
  c.mutate(p.copy(normalize=true));c.undo();assertTrue(c.canRedo)
  assertTrue(c.applyTitleText(TitleTextDraft(p,0,p.captions[0])));assertSame(p,c.project);assertFalse(c.canUndo);assertTrue(c.canRedo)
 }
 @Test fun draftUndoRedoAreLocalIncludingEmptyHistory()=controller {c->
  val p=project();c.project=p;c.mutate(p.copy(normalize=true));val prior=c.project!!;val d=TitleTextDraft(prior,0,prior.captions[0])
  d.undo();d.redo();assertSame(prior,c.project)
  d.edit(TextFieldValue("First"));d.edit(TextFieldValue("Second"));d.undo();assertEquals("First",d.value.text);d.undo();assertEquals("Original",d.value.text);d.undo();assertEquals("Original",d.value.text)
  d.redo();assertEquals("First",d.value.text);d.edit(TextFieldValue("Branch"));d.redo();assertEquals("Branch",d.value.text);assertSame(prior,c.project);assertTrue(c.canUndo);assertFalse(c.canRedo)
 }
 @Test fun busyStaleWrongRemovedAndReorderedRejected()=controller {c->
  val p=project();c.project=p;val d=TitleTextDraft(p,0,p.captions[0]);d.edit(TextFieldValue("New"))
  c.busy=true;assertFalse(c.applyTitleText(d));c.busy=false
  assertFalse(c.applyTitleText(TitleTextDraft(p,1,p.captions[0])))
  for(next in listOf(p.copy(),p.copy(captions=emptyList()),p.copy(captions=p.captions.reversed()))) {
   c.project=next;assertFalse(c.applyTitleText(d));assertSame(next,c.project);assertFalse(c.canUndo)
  }
 }
 @Test fun selectionChangesDoNotCreateDraftHistory() {
  val p=project();val d=TitleTextDraft(p,0,p.captions[0]);val selected=TextFieldValue("Original",androidx.compose.ui.text.TextRange(0,4));d.edit(selected);d.undo();assertEquals(selected,d.value)
 }
 @Test fun draftAndUnchangedApplyPreservePreviewAndSelection()=controller {c->
  val p=project();c.project=p;c.position=1234L;c.smartClipId=p.timeline.clips[0].id
  val rendered=java.io.File.createTempFile("title-preview-test-",".mp4");c.rendered=rendered
  try {
   val d=TitleTextDraft(p,0,p.captions[0]);d.edit(TextFieldValue("Draft only"));d.undo()
   assertSame(p,c.project);assertSame(rendered,c.rendered);assertTrue(rendered.exists());assertEquals(1234L,c.position);assertEquals(p.timeline.clips[0].id,c.smartClipId)
   assertTrue(c.applyTitleText(d));assertSame(rendered,c.rendered);assertTrue(rendered.exists());assertFalse(c.canUndo)
   d.edit(TextFieldValue("Changed"));assertTrue(c.applyTitleText(d));assertNull(c.rendered);assertFalse(rendered.exists());assertTrue(c.canUndo)
  } finally {rendered.delete()}
 }

 @Test fun windowFallbackRoutesExhaustedDraftHistoryWithoutProjectUndo()=controller {c->
  val p=project();c.project=p;c.mutate(p.copy(normalize=true));val prior=c.project!!
  val d=TitleTextDraft(prior,0,prior.captions[0]);c.titleTextDraft=d;d.edit(TextFieldValue("Draft"))
  repeat(20){assertTrue(c.routeTitleTextHistory(false))};assertEquals("Original",d.value.text);assertSame(prior,c.project);assertFalse(c.canRedo);assertTrue(c.canUndo)
  repeat(20){assertTrue(c.routeTitleTextHistory(true))};assertEquals("Draft",d.value.text);assertSame(prior,c.project)
  c.titleTextDraft=null;assertFalse(c.routeTitleTextHistory(false));c.undo();assertSame(p,c.project)
 }

}
