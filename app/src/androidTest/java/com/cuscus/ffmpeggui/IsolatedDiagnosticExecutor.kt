package com.cuscus.ffmpeggui

import androidx.test.platform.app.InstrumentationRegistry
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor

/** Instrumentation APK only. Never linked from application production code.
 * A deliberate runner flag is required as well as explicitly naming the diagnostic test.
 * Reflection avoids changing the app's production dependency exposure for a diagnostic.
 */
internal class IsolatedDiagnosticExecutor : FfmpegExecutor {
    override suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult {
        check(InstrumentationRegistry.getArguments().getString("unsafeDspDiagnostics") == "true" || InstrumentationRegistry.getArguments().getString("candidateDspValidation") == "true")
        val type=Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
        val session=type.getMethod("executeWithArguments",Array<String>::class.java).invoke(null,command.arguments.toTypedArray())
        val log=session.javaClass.getMethod("getAllLogsAsString").invoke(session)?.toString().orEmpty()
        onLog(log)
        val code=session.javaClass.getMethod("getReturnCode").invoke(session)
        val value=code?.javaClass?.getMethod("getValue")?.invoke(code) as? Int ?: -1
        return if(value==0)ExecutionResult.Success else ExecutionResult.Failed(value)
    }
}
