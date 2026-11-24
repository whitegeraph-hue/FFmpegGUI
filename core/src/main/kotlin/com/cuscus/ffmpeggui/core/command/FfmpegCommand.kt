package com.cuscus.ffmpeggui.core.command

/** An FFmpeg invocation as an argument vector, without the leading program name. */
data class FfmpegCommand(val arguments: List<String>) {
    // Not serialized, accepted from a command line, or preserved by data-class copy.
    // The snapshot also fails closed if a caller mutates a backing argument list.
    private var initializedAndroidArguments: List<String>? = null
    internal fun builtForInitializedAndroid(): FfmpegCommand = apply { initializedAndroidArguments = arguments.toList() }
    internal fun hasInitializedAndroidProvenance(): Boolean = initializedAndroidArguments == arguments


    /** Shell-style rendering ("ffmpeg -y -i ..."), suitable for display and editing. */
    fun toDisplayString(): String = renderCommandLine(listOf(PROGRAM_NAME) + arguments)

    companion object {
        const val PROGRAM_NAME = "ffmpeg"
    }
}
