package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DesktopAutosaveTest {
 private fun project()=DesktopProject.create(DesktopSource("/tmp/missing-test-source.mp4",com.cuscus.ffmpeggui.core.model.MediaInfo.of(5000,true,320,180,0,false)))
 @Test fun unnamedEditsSurviveImmediateFlushAndPreserveLastVersion()=runBlocking {
  val root=Files.createTempDirectory("autosave-test").toFile()
  try {
   val store=DesktopAutosave(root);val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val c=EditorController(scope,store)
   try {
    c.project=project();c.mutate(c.project!!.copy(normalize=true));assertTrue(c.flushAutosave())
    val saved=store.entries().single();assertTrue(saved.readText().contains("\"normalize\": true"));assertNull(c.projectFile)
    c.undo();assertTrue(c.flushAutosave());assertFalse(saved.readText().contains("\"normalize\": true"))
    assertTrue(DesktopProjects.previousFile(saved).readText().contains("\"normalize\": true"))
    assertEquals(1,store.entries().size)
   }finally {c.close();scope.cancel()}
  }finally {root.deleteRecursively()}
 }
 @Test fun sessionsNeverOverwriteEachOther()=runBlocking {
  val root=Files.createTempDirectory("autosave-test").toFile()
  try {val a=DesktopAutosave(root);val b=DesktopAutosave(root);a.save(project());b.save(project());assertEquals(2,a.entries().size);a.nextProject();a.save(project());assertEquals(3,a.entries().size)}finally {root.deleteRecursively()}
 }
 @Test fun writeFailureIsVisibleAndCloseIsNotApproved()=runBlocking {
  val root=Files.createTempFile("autosave-test","file").toFile();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val c=EditorController(scope,DesktopAutosave(root))
  try {c.project=project();assertFalse(c.flushAutosave());assertTrue(c.autosaveStatus.startsWith("Recovery save failed"));assertNotNull(c.error)}finally {c.close();scope.cancel();root.delete()}
 }
 @Test fun recoveryRefusesSymlinkFolder()=runBlocking {
  val root=Files.createTempDirectory("autosave-test").toFile();val link=File(root,"link");Files.createSymbolicLink(link.toPath(),root.toPath())
  try {try {DesktopAutosave(link).save(project());fail("Accepted symbolic link")}catch(expected:IllegalArgumentException){assertTrue(expected.message!!.contains("symbolic"))}}finally {link.delete();root.deleteRecursively()}
 }
 @Test fun rapidEditsAreDebounced()=runBlocking {
  val root=Files.createTempDirectory("autosave-test").toFile();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val c=EditorController(scope,DesktopAutosave(root))
  try {c.project=project();repeat(50){c.mutate(c.project!!.copy(normalize=it%2==0))};assertTrue(c.autosave.entries().isEmpty());delay(900);assertEquals(1,c.autosave.entries().size);assertFalse(DesktopProjects.previousFile(c.autosave.entries().single()).exists())}finally {c.close();scope.cancel();root.deleteRecursively()}
 }
 @Test fun failureKeepsLastGoodSnapshot()=runBlocking {
  val root=Files.createTempDirectory("autosave-test").toFile();val store=DesktopAutosave(root)
  try {store.save(project());val saved=store.entries().single();val original=saved.readBytes();val backup=DesktopProjects.previousFile(saved);backup.mkdir();try {store.save(project().copy(normalize=true));fail("Accepted blocked backup")}catch(expected:IllegalArgumentException){};assertArrayEquals(original,saved.readBytes())}finally {root.deleteRecursively()}
 }
 @Test fun deleteIsExplicitAndLeavesOtherProjects()=runBlocking {
  val root=Files.createTempDirectory("autosave-test").toFile();val store=DesktopAutosave(root)
  try {store.save(project());val first=store.entries().single();store.save(project().copy(normalize=true));store.nextProject();store.save(project());store.delete(first);assertEquals(1,store.entries().size);assertFalse(DesktopProjects.previousFile(first).exists())}finally {root.deleteRecursively()}
 }
 @Test fun resumeDoesNotMultiplyRecoveryFiles()=runBlocking {
  val root=Files.createTempDirectory("autosave-test").toFile();val store=DesktopAutosave(root)
  try {store.save(project());val first=store.entries().single();val reopened=DesktopAutosave(root);reopened.resume(first);reopened.save(project().copy(normalize=true));assertEquals(1,store.entries().size);assertTrue(first.readText().contains("\"normalize\": true"))}finally {root.deleteRecursively()}
 }

 @Test fun cannotDeleteActiveRecovery()=runBlocking {
  val root=Files.createTempDirectory("autosave-test").toFile();val store=DesktopAutosave(root)
  try {store.save(project());val first=store.entries().single();try {store.delete(first);fail("Deleted active save")}catch(expected:IllegalArgumentException){};assertTrue(first.isFile)}finally {root.deleteRecursively()}
 }

}
