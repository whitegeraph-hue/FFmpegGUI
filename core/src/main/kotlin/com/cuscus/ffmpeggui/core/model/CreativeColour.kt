package com.cuscus.ffmpeggui.core.model

/** Original combinations of existing colour controls; replacing only colour in this scope. */
enum class CreativeColour(val title:String,val color:ColorSettings) {
    RESET("Reset colour",ColorSettings()), CINEMA("Cinema",ColorSettings(contrast=1.18f,saturation=.78f,vignette=.22f)),
    VIVID("Vivid",ColorSettings(contrast=1.12f,saturation=1.4f,sharpness=.15f)), MATTE("Matte",ColorSettings(brightness=.06f,contrast=.8f,saturation=.8f)),
    MONO("Mono drama",ColorSettings(contrast=1.35f,saturation=0f,vignette=.25f)), VINTAGE("Vintage grain",ColorSettings(brightness=.035f,contrast=.9f,saturation=.55f,filmGrain=12f)),
    DREAM("Soft dream",ColorSettings(brightness=.04f,saturation=.85f,gaussianBlur=2f)), CRISP("Crisp",ColorSettings(contrast=1.08f,sharpness=.45f)),
    NOIR("Noir",ColorSettings(brightness=-.04f,contrast=1.55f,saturation=0f,vignette=.45f)), PASTEL("Pastel",ColorSettings(brightness=.08f,contrast=.82f,saturation=.7f)),
    FADED("Faded film",ColorSettings(brightness=.04f,contrast=.72f,saturation=.65f,filmGrain=5f)), NIGHT("Night mood",ColorSettings(brightness=-.08f,contrast=1.2f,saturation=.65f)),
}
