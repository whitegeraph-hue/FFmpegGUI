package com.cuscus.ffmpeggui.desktop

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Distro eSpeak NG frontend, direct arguments and a private UTF-8 file, never a shell. */
object DesktopPhonemizer {
    suspend fun phonemes(text:String,language:String,directory:File):Array<IntArray> = withContext(Dispatchers.IO) {
        require(language in listOf("en-us","en","it","es","fr-fr","pt-br","hi"))
        require(text.length in 1..300 && text.none {it=='\u0000'}) {"Voice cue is too long or invalid"}
        require(text.codePoints().allMatch {cp->Character.isLetterOrDigit(cp) || Character.isWhitespace(cp) || Character.getType(cp) in listOf(Character.NON_SPACING_MARK.toInt(),Character.COMBINING_SPACING_MARK.toInt()) || cp.toChar() in "'\".,!?;:-()"}) {"Spell unsupported symbols as spoken words before dubbing"}
        val temporary=Files.createTempDirectory("ffmpeggui-phonemes-").toFile()
        var process:Process?=null
        try {
            val input=File(temporary,"text.txt").apply {writeText(text,Charsets.UTF_8)}
            val output=File(temporary,"ipa.txt");val errors=File(temporary,"errors.txt")
            process=ProcessBuilder(listOf("espeak-ng","-q","--ipa","-v",language,"-b","1","-f",input.path)).redirectOutput(output).redirectError(errors).start()
            process.outputStream.close();val began=System.nanoTime()
            while(!process.waitFor(50,TimeUnit.MILLISECONDS)){currentCoroutineContext().ensureActive();require(output.length()<=16000 && errors.length()<=4000) {"Voice phonemizer output exceeds its limit"};check(System.nanoTime()-began<TimeUnit.SECONDS.toNanos(20)) {"Voice phonemizer timed out"}}
            currentCoroutineContext().ensureActive()
            require(process.exitValue()==0 && errors.length()==0L && output.length() in 1..16000) {"eSpeak NG could not read this cue. No audio was saved."}
            val allowed=File(directory,"tokens.txt").useLines {lines->lines.map {it.substringBeforeLast(' ')}.toSet()}
            val sentences=output.readText(Charsets.UTF_8).lineSequence().map {it.trim()}.filter {it.isNotBlank()}.toList()
            require(sentences.isNotEmpty() && sentences.size<=10)
            sentences.map {sentence->
                val points=sentence.codePoints().toArray()
                require(points.size in 1..380) {"Voice cue has too many sounds. Split it into shorter subtitle cues."}
                val unknown=points.filter {String(Character.toChars(it)) !in allowed}.toSet()
                require(unknown.isEmpty()) {"Voice cannot say every phonetic sound in this cue. No audio was saved."}
                // CLI output omits punctuation; restore a terminal pause without discarding sounds.
                points + intArrayOf('.'.code)
            }.toTypedArray()
        }finally {
            process?.let {if(it.isAlive){it.destroy();if(!it.waitFor(500,TimeUnit.MILLISECONDS))it.destroyForcibly().waitFor()}}
            temporary.deleteRecursively()
        }
    }
}
