package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.*
import com.cuscus.ffmpeggui.core.project.*
import org.junit.Assert.*
import org.junit.Test

class ClipAudioFiltersTest {
    @Test fun clipEffectsRoundTripAndOldDefaults() {
        val fx=Effects(audioEffects=AudioEffects(echo=Echo(),pitchSemitones=4f),normalizeAudio=true)
        assertEquals(fx,ModelJson.effects(ModelJson.write(fx)))
        assertEquals(Effects.NONE,ModelJson.effects(Json.Obj(emptyMap())))
    }
    @Test fun processedClipIsBoundedBeforeFades() {
        val filters=AudioFilters().clipChain(Effects(audioEffects=AudioEffects(echo=Echo(),pitchSemitones=4f),normalizeAudio=true,fadeOutSeconds=.1f),2.0)
        assertTrue(filters.any {it.startsWith("aecho")})
        assertTrue(filters.contains("loudnorm"))
        assertTrue(filters.indexOf("apad=whole_dur=2.0000")>filters.indexOf("loudnorm"))
        assertTrue(filters.last().startsWith("afade"))
    }
    @Test fun unknownDurationIsRefused() {
        assertTrue(runCatching {AudioFilters().clipChain(Effects(normalizeAudio=true),null)}.isFailure)
        assertTrue(runCatching {AudioFilters().clipChain(Effects(audioEffects=AudioEffects(echo=Echo())),null)}.isFailure)
    }
    @Test fun neutralAndMutedDoNotProcess() {
        assertTrue(AudioFilters().clipChain(Effects.NONE,2.0).none {it.startsWith("apad")||it=="loudnorm"})
        val muted=Effects(muted=true,audioEffects=AudioEffects(echo=Echo()),normalizeAudio=true)
        assertTrue(AudioFilters().clipChain(muted,2.0).none {it.startsWith("aecho")||it=="loudnorm"})
    }
}
