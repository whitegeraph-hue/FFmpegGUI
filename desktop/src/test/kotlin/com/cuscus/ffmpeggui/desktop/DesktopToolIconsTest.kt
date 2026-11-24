package com.cuscus.ffmpeggui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopToolIconsTest {
    @Test fun everyToolHasExactlyOneExplicitSymbol() {
        val tools=desktopGroups.values.flatten()
        assertEquals(tools.size,tools.toSet().size)
        assertEquals(tools.toSet(),desktopToolSymbols.keys)
    }
    @Test fun differentToolsHaveDifferentSymbolsAndAllVectorsParse() {
        val names=desktopToolSymbols.values
        assertEquals(names.size,names.toSet().size)
        names.forEach {assertNotNull(DesktopSymbols.vector(it))}
    }
    @Test fun audioToolsAreVisuallyDistinct() {
        assertEquals("volume_up",desktopToolSymbols.getValue("Audio"))
        assertEquals("library_music",desktopToolSymbols.getValue("Sound tracks"))
        assertEquals("call_split",desktopToolSymbols.getValue("Scene stems"))
        assertEquals("mic",desktopToolSymbols.getValue("Dubbing"))
    }
}
