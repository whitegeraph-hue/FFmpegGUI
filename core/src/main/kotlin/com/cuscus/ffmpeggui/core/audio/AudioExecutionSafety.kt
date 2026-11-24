package com.cuscus.ffmpeggui.core.audio

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.command.AudioRuntime
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.ConversionRequest

/** Temporary execution policy, not a project migration or a change to the DSP recipes.
 * Remove only after float/AAC silence, peak and finite audits pass across rates/channels
 * and independent process starts. Kotlin live DSP does not use these FFmpeg filters.
 */
object AudioExecutionSafety {
    const val RETURN_CODE = -7301
    private val blocked = setOf("acrusher", "vibrato")
    const val NOTICE = "FFmpeg render/export blocked: vibrato or lo-fi bitcrushing may produce invalid or unwanted audio. Turn off that effect or choose another preset. Project settings are unchanged. Android live Kotlin preview is separate and does not qualify the export."

    fun stages(effects: AudioEffects): Set<String> = effects.stages().mapNotNull {
        when (it) {
            is AudioStage.Crusher -> "acrusher"
            is AudioStage.Vibrato -> "vibrato"
            is AudioStage.Biquad, is AudioStage.Gate, is AudioStage.Compressor,
            is AudioStage.Chorus, is AudioStage.Flanger, is AudioStage.Phaser,
            is AudioStage.Tremolo, is AudioStage.Echo, AudioStage.StereoLayout,
            is AudioStage.Width, is AudioStage.Balance, AudioStage.Mono -> null
        }
    }.toSet()
    @JvmOverloads fun available(effects: AudioEffects, runtime: AudioRuntime = AudioRuntime.SYSTEM): Boolean =
        stages(effects).none { it == "acrusher" || runtime != AudioRuntime.ANDROID_BUNDLED_6_1_6 }

    @JvmOverloads fun requestIssue(request: ConversionRequest, runtime: AudioRuntime = AudioRuntime.SYSTEM): String? {
        val scopes = buildList {
            if (!available(request.audioEffects, runtime)) add("whole output")
            request.segments.forEachIndexed { i, s -> if (!available(s.effects.audioEffects, runtime)) add("clip ${i + 1}") }
            request.sounds.forEachIndexed { i, s -> if (!available(s.effects, runtime)) add("sound ${i + 1}") }
        }
        return scopes.takeIf { it.isNotEmpty() }?.let { "$NOTICE Affected: ${it.joinToString()}." }
    }

    /** Parses element boundaries, not substrings in filenames, metadata or filter options. */
    fun filterNames(graph: String): Set<String> = filterElements(graph).toSet()

    private fun filterElements(graph: String): List<String> {
        val elements = mutableListOf<String>()
        val part = StringBuilder()
        var quoted = false
        var escaped = false
        for (c in graph) {
            when {
                escaped -> { part.append('\\').append(c); escaped = false }
                !quoted && c == '\\' -> escaped = true
                c == '\'' -> { quoted = !quoted; part.append(c) }
                !quoted && (c == ',' || c == ';') -> { elements += part.toString(); part.clear() }
                else -> part.append(c)
            }
        }
        if (escaped) part.append('\\')
        elements += part.toString()
        return elements.mapNotNull { element ->
            var rest = element.trimStart()
            while (rest.startsWith("[")) {
                val end = rest.indexOf(']')
                if (end < 0) break
                rest = rest.substring(end + 1).trimStart()
            }
            val name = StringBuilder()
            var quote = false
            var escape = false
            for (c in rest) {
                when {
                    escape -> { name.append(c); escape = false }
                    !quote && c == '\\' -> escape = true
                    c == '\'' -> quote = !quote
                    !quote && (c == '=' || c == ':' || c == '@' || c == '[' || c.isWhitespace()) -> break
                    else -> name.append(c)
                }
            }
            name.toString().takeIf { it.isNotEmpty() }
        }
    }

    /** Only canonical builder syntax qualifies. Manual commands never carry provenance. */
    private fun initializedVibratoGraph(graph: String): Boolean {
        val matches=Regex("vibrato=f=([0-9.]+):d=([0-9.]+)").findAll(graph).toList()
        if(matches.isEmpty() || matches.size != filterElements(graph).count{it=="vibrato"})return false
        for(m in matches) {
            val frequency=m.groupValues[1].toDoubleOrNull() ?: return false
            val depth=m.groupValues[2].toDoubleOrNull() ?: return false
            if(!frequency.isFinite() || frequency !in .1..20.0 || !depth.isFinite() || depth !in 0.0..1.0)return false
            val period=Math.rint(48000.0/frequency+.5).toLong()
            val start=maxOf(graph.lastIndexOf(';',m.range.first),graph.lastIndexOf(']',m.range.first))
            val prefix=graph.substring(start+1,m.range.first)
            if(!prefix.contains("aresample=48000,") || !prefix.endsWith("adelay=${period}S:all=1,"))return false
            if(!graph.substring(m.range.last+1).startsWith(",atrim=start_sample=$period,asetpts=PTS-STARTPTS"))return false
        }
        return true
    }

    /** Script/file graph inputs fail closed: reading once here would have a file-change race.
     * Users can paste their graph inline to have its filter names checked instead.
     */
    @JvmOverloads fun commandIssue(command: FfmpegCommand, runtime: AudioRuntime = AudioRuntime.SYSTEM): String? {
        val args = command.arguments
        var lavfiInput = false
        for (i in args.indices) {
            val raw = args[i]
            val option = raw.substringBefore('=')
            val base = option.substringBefore(':')
            if (base in setOf("-graph_file", "-/graph") || (base == "-/i" && lavfiInput) || base in setOf("-filter_complex_script", "-filter_script", "-/filter_complex", "-/filter", "-/af", "-/vf", "-/lavfi"))
                return "Filter graph file inputs are temporarily blocked because their effects cannot be checked safely. Paste the graph inline. Project settings are unchanged."
            val value = if ('=' in raw) raw.substringAfter('=') else args.getOrNull(i + 1)
            if (option == "-f") lavfiInput = value == "lavfi"
            val graph = when {
                base in setOf("-af", "-vf", "-filter", "-filter_complex", "-lavfi", "-graph") -> value
                option == "-i" && lavfiInput -> value
                else -> null
            }
            if (graph != null && filterNames(graph).any {
                    it == "acrusher" || (it == "vibrato" &&
                        (runtime != AudioRuntime.ANDROID_BUNDLED_6_1_6 || !command.hasInitializedAndroidProvenance() || !initializedVibratoGraph(graph)))
                }) return NOTICE
        }
        return null
    }
    fun requireAllowed(command: FfmpegCommand) { commandIssue(command)?.let { throw Blocked(it) } }
    class Blocked(message: String) : IllegalArgumentException(message)
}
