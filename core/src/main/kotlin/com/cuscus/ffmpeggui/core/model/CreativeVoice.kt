package com.cuscus.ffmpeggui.core.model

/** Original DSP recipes, not recordings or AI impersonations. Selecting one replaces this scope's DSP. */
enum class CreativeVoice(val title: String, val family: String, val effects: AudioEffects) {
    CLEAN("Clean / reset", "Natural", AudioEffects.NONE),
    TELEPHONE("Old telephone", "Retro", AudioEffects(lowCutHz=400, highCutHz=3200, equalizer=listOf(-12f,-12f,-9f,-4f,3f,6f,4f,-6f,0f,0f), compressor=Compressor(-23f,5f,4f,80f,4f),stereo=StereoImage(mono=true),limiter=Limiter(-2f))),
    RADIO("AM radio", "Retro", AudioEffects(lowCutHz=260,highCutHz=4200,lofiBits=9,compressor=Compressor(-22f,4f,5f,100f,3f),stereo=StereoImage(mono=true),limiter=Limiter(-2f))),
    WALKIE("Walkie-talkie", "Retro", AudioEffects(lowCutHz=650,highCutHz=2600,lofiBits=7,compressor=Compressor(-25f,6f,2f,70f,4f),gate=NoiseGate(-42f,-35f),stereo=StereoImage(mono=true),limiter=Limiter(-3f))),
    MEGAPHONE("Megaphone", "Retro", AudioEffects(lowCutHz=500,highCutHz=4200,compressor=Compressor(-26f,8f,3f,100f,5f),echo=Echo(75,.18f),limiter=Limiter(-3f))),
    TAPE("Worn tape", "Retro", AudioEffects(highCutHz=6500,lofiBits=12,modulation=Modulation(ModulationKind.VIBRATO,.7f,.12f),limiter=Limiter(-2f))),
    TINY("Tiny voice", "Character", AudioEffects(pitchSemitones=9f,highCutHz=10000,limiter=Limiter(-2f))),
    DEEP("Deep voice", "Character", AudioEffects(pitchSemitones=-6f,limiter=Limiter(-2f))),
    GIANT("Giant", "Character", AudioEffects(pitchSemitones=-10f,reverb=Reverb(RoomSize.HALL,.24f),limiter=Limiter(-3f))),
    ALIEN("Alien wobble", "Character", AudioEffects(pitchSemitones=4f,modulation=Modulation(ModulationKind.VIBRATO,7f,.55f),echo=Echo(110,.22f),limiter=Limiter(-3f))),
    ROBOT("Robot flutter", "Character", AudioEffects(lofiBits=7,modulation=Modulation(ModulationKind.TREMOLO,20f,.95f),stereo=StereoImage(mono=true),limiter=Limiter(-3f))),
    CYBORG("Cyborg", "Character", AudioEffects(pitchSemitones=-3f,lofiBits=9,modulation=Modulation(ModulationKind.FLANGER,.5f,.8f),limiter=Limiter(-3f))),
    DOUBLE("Double voice", "Space", AudioEffects(modulation=Modulation(ModulationKind.CHORUS,1.2f,.6f),limiter=Limiter(-3f))),
    DREAM("Dreamy", "Space", AudioEffects(modulation=Modulation(ModulationKind.CHORUS,.5f,.35f),reverb=Reverb(RoomSize.HALL,.5f),limiter=Limiter(-3f))),
    CAVE("Cave", "Space", AudioEffects(echo=Echo(240,.45f),reverb=Reverb(RoomSize.CATHEDRAL,.65f),limiter=Limiter(-3f))),
    STADIUM("Stadium", "Space", AudioEffects(echo=Echo(380,.38f),reverb=Reverb(RoomSize.HALL,.65f),limiter=Limiter(-3f))),
    UNDERWATER("Underwater", "Texture", AudioEffects(highCutHz=800,modulation=Modulation(ModulationKind.PHASER,.4f,.65f),limiter=Limiter(-3f))),
    LOFI("8-bit voice", "Texture", AudioEffects(lofiBits=5,highCutHz=6000,limiter=Limiter(-3f))),
    GHOST("Ghost", "Texture", AudioEffects(pitchSemitones=3f,modulation=Modulation(ModulationKind.VIBRATO,3f,.3f),reverb=Reverb(RoomSize.CATHEDRAL,.6f),limiter=Limiter(-3f))),
    WARM("Warm narration", "Natural", AudioEffects(lowCutHz=80,highCutHz=14000,equalizer=listOf(0f,0f,2f,2f,0f,0f,2f,1f,0f,0f),compressor=Compressor(-20f,2.5f,15f,160f,2f),limiter=Limiter(-2f))),
}
