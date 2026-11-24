package com.cuscus.ffmpeggui.desktop

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.graphics.luminance
import java.util.UUID
import java.util.prefs.Preferences

class DesktopWorkspaceTest {
    @Test fun roundTripKeepsLayoutToolbarAndShortcuts() {
        val p=DesktopWorkspace(inspectorWidth=440,paneOrder=listOf("inspector","preview","tools"),timelineTop=true,accent="AB12EF",customAccent=true,projectToolbar=listOf("Undo","Save project"),shortcuts=DesktopWorkspace.shortcutDefaults+mapOf("Split" to "K"))
        assertEquals(p,DesktopWorkspaceCodec.decode(DesktopWorkspaceCodec.encode(p)))
    }
    @Test fun corruptedDataUsesSafeDefaults() {assertEquals(DesktopWorkspace(),DesktopWorkspaceCodec.decode("broken"))}
    @Test fun clampsSizesAndRepairsPaneOrder() {
        val p=DesktopWorkspace(inspectorWidth=999,railWidth=-3,timelineHeight=-1,paneOrder=listOf("preview","preview","unknown"),accent="oops").normalized()
        assertEquals(520,p.inspectorWidth);assertEquals(130,p.railWidth);assertEquals(160,p.timelineHeight);assertEquals(listOf("preview","tools","inspector"),p.paneOrder);assertEquals("008577",p.accent)
    }
    @Test fun rejectsConflictingAndMalformedShortcuts() {
        assertNull(DesktopWorkspace.shortcutProblem(DesktopWorkspace.shortcutDefaults))
        assertNotNull(DesktopWorkspace.shortcutProblem(mapOf("Split" to "S","Save" to "S")))
        assertNotNull(DesktopWorkspace.shortcutProblem(mapOf("Split" to "Meta+S")))
        assertFalse(DesktopWorkspace.validShortcut("Escape"));assertTrue(DesktopWorkspace.validShortcut("Ctrl+Shift+Alt+S"))
    }
    @Test fun loadedPartialMapsCannotConflictWithDefaults() {
        val p=DesktopWorkspaceCodec.decode("""{"shortcuts":{"Split":"D"}}""")
        assertNull(DesktopWorkspace.shortcutProblem(p.shortcuts));assertEquals("S",p.shortcuts["Split"])
        val q=DesktopWorkspaceCodec.decode("""{"shortcuts":{"Save project":"S"}}""")
        assertEquals("Ctrl+S",q.shortcuts["Save project"])
    }
    @Test fun disabledShortcutsAndHiddenToolbarsRoundTrip() {
        val p=DesktopWorkspace(projectToolbar=emptyList(),timelineToolbar=emptyList(),shortcuts=DesktopWorkspace.shortcutDefaults.mapValues {if(it.key in listOf("Undo","Redo"))it.value else ""})
        assertEquals(p,DesktopWorkspaceCodec.decode(DesktopWorkspaceCodec.encode(p)))
    }
    @Test fun layoutOverwriteAndCapacityAreBounded() {
        val node=Preferences.userRoot().node("workspace-test-"+UUID.randomUUID());val store=DesktopWorkspaceStore(node)
        try {
            for(i in 1..4)store.saveLayout("Layout $i",DesktopWorkspace())
            store.saveLayout("Layout 1",DesktopWorkspace(inspectorWidth=450));assertEquals(450,store.loadLayout("Layout 1")!!.inspectorWidth)
            assertEquals(4,store.names().size)
            assertThrows(IllegalArgumentException::class.java) {store.saveLayout(" ",DesktopWorkspace())}
            assertThrows(IllegalArgumentException::class.java) {store.saveLayout("x".repeat(49),DesktopWorkspace())}
        }finally {node.removeNode()}
    }
    @Test fun reservesTextEditingAndAltGr() {
        for(key in listOf("Ctrl+C","Ctrl+V","Ctrl+X","Ctrl+A","Ctrl+Z","Ctrl+Y","Ctrl+Shift+Z","Ctrl+Alt+K"))assertNotNull(key,DesktopWorkspace.shortcutProblem(DesktopWorkspace.shortcutDefaults+mapOf("Save project" to key)))
        assertNotNull(DesktopWorkspace.shortcutProblem(DesktopWorkspace.shortcutDefaults+mapOf("Undo" to "Ctrl+U")))
    }
    @Test fun sixMaximalLayoutsFitAndCorruptStoreIsNeverOverwritten() {
        val node=Preferences.userRoot().node("workspace-test-"+UUID.randomUUID());val store=DesktopWorkspaceStore(node)
        try {
            for(i in 1..6)store.saveLayout(i.toString()+"x".repeat(47),DesktopWorkspace())
            assertEquals(6,store.names().size)
            assertThrows(IllegalArgumentException::class.java) {store.saveLayout("seventh",DesktopWorkspace())}
            node.put("workspaceLayouts","broken");assertThrows(IllegalStateException::class.java) {store.saveLayout("new",DesktopWorkspace())}
            assertThrows(IllegalStateException::class.java) {store.deleteLayout("old")}
            assertEquals("broken",node.get("workspaceLayouts",null))
        }finally {node.removeNode()}
    }
    @Test fun extremeAccentsRemainReadable() {
        for(base in listOf(com.cuscus.ffmpeggui.ui.theme.TealLightColorScheme,com.cuscus.ffmpeggui.ui.theme.TealDarkColorScheme))for(hex in listOf("FFFFFF","000000","FFFF00","FF00FF","008577")) {
            val c=workspaceColors(base,DesktopWorkspace(customAccent=true,accent=hex))
            fun ratio(a:androidx.compose.ui.graphics.Color,b:androidx.compose.ui.graphics.Color):Float {val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
            for(bg in listOf(c.surface,c.surfaceContainer,c.surfaceContainerLow,c.surfaceContainerHigh,c.surfaceContainerHighest,c.surfaceContainerLowest,c.surfaceVariant,c.background))assertTrue(hex,ratio(c.primary,bg)>=4.5f)
            assertTrue(hex,ratio(c.primary,c.onPrimary)>=4.5f)
        }
    }
    @Test fun namedLayoutsAndResetDoNotTouchOtherPreferences() {
        val node=Preferences.userRoot().node("workspace-test-"+UUID.randomUUID());val store=DesktopWorkspaceStore(node)
        try {
            node.put("unrelated","safe");val custom=DesktopWorkspace(inspectorWidth=450)
            store.save(custom);store.saveLayout("My layout",custom)
            assertEquals(custom,store.load());assertEquals(custom,store.loadLayout("My layout"));assertEquals(listOf("My layout"),store.names())
            store.save(DesktopWorkspace());assertEquals(DesktopWorkspace(),store.load());assertEquals("safe",node.get("unrelated",null));assertEquals(custom,store.loadLayout("My layout"))
            store.deleteLayout("My layout");assertTrue(store.names().isEmpty())
        } finally {node.removeNode()}
    }
}
