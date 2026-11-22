package com.cuscus.ffmpeggui.desktop

import com.cuscus.ffmpeggui.core.command.*
import com.cuscus.ffmpeggui.core.model.*
import java.io.File

fun main(args: Array<String>) {
    require(args.size == 2 && args.all { it.isNotBlank() }) { "Pass input and output paths" }
    val request = ConversionRequest(
        format = OutputFormat.MP4,
        inputFileName = File(args[0]).name,
        sources = listOf(SourceInfo(durationMs = 4000, width = 640, height = 360, hasAudio = true)),
        segments = listOf(Segment(source = 0, startMs = 500, endMs = 3500)),
    )
    val command = CommandBuilder(FontProvider { "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf" })
        .build(request, MediaPaths(inputs = listOf(args[0]), output = args[1]))
    println(command.arguments)
    val process = ProcessBuilder(listOf("ffmpeg", "-y") + command.arguments).inheritIO().start()
    check(process.waitFor() == 0) { "FFmpeg failed" }
    check(File(args[1]).length() > 0)
}
