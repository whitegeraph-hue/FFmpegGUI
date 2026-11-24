package com.cuscus.ffmpeggui.core.model
fun TransitionKind.creativeFamily():String = when {
    this in setOf(TransitionKind.VENETIAN,TransitionKind.DIAMOND_GRID,TransitionKind.CHECKER_REVEAL,TransitionKind.SAW_WIPE,TransitionKind.IRIS_ELLIPSE,TransitionKind.ANGULAR_SWEEP,TransitionKind.BARS_RANDOM,TransitionKind.CROSS_REVEAL) -> "Shapes"
    name.contains("BLUR") || this==TransitionKind.DEFOCUS -> "Blur"
    name.contains("GLITCH") || name.contains("RGB") || name.contains("NOISE") || this==TransitionKind.PIXELATE || this==TransitionKind.WAVE -> "Glitch"
    name.contains("FLASH") || name.contains("LIGHT") || name.contains("GLOW") || name.contains("FLARE") || name.contains("BURN") -> "Light"
    name.contains("SLIDE") || name.contains("COVER") || name.contains("REVEAL") || name.contains("SQUEEZE") || name.contains("SPLIT") -> "Slide"
    name.contains("WIPE") || name.contains("SMOOTH") || name.startsWith("DIAG_") -> "Wipe"
    name.contains("ZOOM") || name.contains("SPIN") || name.contains("SHAKE") || name.contains("WHIP") || name.contains("PULL") || name.contains("ROLL") || name.contains("SWIRL") || name.contains("BOUNCE") || name.contains("SPRING") || name.contains("REBOUND") || name.contains("PENDULUM") -> "Camera"
    name.contains("CUBE") || name.contains("FLIP") || name.contains("PAGE") -> "3D"
    ordinal>TransitionKind.ZOOM_IN.ordinal && this !in setOf(TransitionKind.DISSOLVE,TransitionKind.FADE_GRAYS,TransitionKind.OVERLAP_CUT) -> "Shapes"
    else -> "Basic"
}
