package com.cuscus.ffmpeggui.desktop

/** Every tool has an explicit icon. No generic fallback hiding missing mappings. */
internal val desktopGroups=linkedMapOf(
    "Edit" to listOf("Effects","Clip editor","Scenes","Pause cuts","Beats","Follow crop"),
    "Audio" to listOf("Audio","Sound tracks","Scene stems","Dubbing"),
    "Text" to listOf("Transcript","Subtitles","Text layers","Translation"),
    "Picture" to listOf("Image layers","Drawing","Subject cutout","Magic eraser"),
    "Export" to listOf("Edit","Compression","Tags","Command studio"),
    "Models" to listOf("Models")
)
internal val desktopToolSymbols=mapOf(
    "Effects" to "auto_awesome",
    "Clip editor" to "content_cut",
    "Scenes" to "movie",
    "Pause cuts" to "waves",
    "Beats" to "graphic_eq",
    "Follow crop" to "person_search",
    "Audio" to "volume_up",
    "Sound tracks" to "library_music",
    "Scene stems" to "call_split",
    "Dubbing" to "mic",
    "Transcript" to "speech_to_text",
    "Subtitles" to "subtitles",
    "Text layers" to "title",
    "Translation" to "translate",
    "Image layers" to "layers",
    "Drawing" to "draw",
    "Subject cutout" to "person",
    "Magic eraser" to "ink_eraser",
    "Edit" to "tune",
    "Compression" to "compress",
    "Tags" to "sell",
    "Command studio" to "terminal",
    "Models" to "model_training"
)
