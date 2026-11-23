package com.cuscus.ffmpeggui.core.editing

import com.cuscus.ffmpeggui.core.timeline.*
import com.cuscus.ffmpeggui.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ClipToolScopeTest {
    @Test fun selectedOccurrenceUsesFootageAndLeavesOthersExact() {
        val effects=Effects(speed=2f,reversed=true)
        val a=Clip("a","source",1000,5000,effects)
        val b=a.copy(id="b")
        val c=Clip("c","other",0,3000)
        val t=Timeline(listOf(a,b,c))
        val mapped=ClipToolScope.cuts(t,"b","source",listOf(CutRange(500,2000,"pause")))
        assertEquals(listOf(CutRange(4000,5000,"pause")),mapped)
        val out=ClipToolScope.remove(t,mapped){"new"}
        assertEquals(a,out.clips[0]);assertEquals(c,out.clips.last())
        assertEquals(2000L,out.clips[1].startMs);assertEquals(5000L,out.clips[1].endMs)
        assertEquals(effects,out.clips[1].effects)
        assertEquals(listOf(5000L),ClipToolScope.points(t,"b","source",listOf(1000,2000,5000)))
        assertTrue(ClipToolScope.cuts(t,"c","source",listOf(CutRange(1000,2000))).isEmpty())
    }
    @Test fun edgeCutIsNotSkippedAndCannotTouchNeighbour() {
        val a=Clip("a","s",0,1000);val b=Clip("b","s",0,1000)
        val t=Timeline(listOf(a,b))
        val cuts=ClipToolScope.cuts(t,"b","s",listOf(CutRange(0,50)))
        val out=ClipToolScope.remove(t,cuts){"new"}
        assertEquals(a,out.clips.first());assertEquals(50L,out.clips.last().startMs)
        assertEquals(1950L,out.durationMs)
    }
}
