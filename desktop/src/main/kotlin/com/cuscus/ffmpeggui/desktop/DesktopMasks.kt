package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.util.UUID

/** Per-process owned mask cache. Saved projects carry their own bytes, never this cache path. */
object DesktopMasks {
    private val session by lazy {
        val root=File(DesktopModels.root.parentFile,"masks").apply {mkdirs()}
        root.listFiles()?.filter {it.isDirectory && it.name.startsWith("session-") }?.forEach {directory->
            val pid=directory.name.removePrefix("session-").substringBefore('-').toLongOrNull()
            if(pid!=null && !ProcessHandle.of(pid).map {it.isAlive}.orElse(false))directory.deleteRecursively()
        }
        File(root,"session-${ProcessHandle.current().pid()}-${UUID.randomUUID()}").apply {
            mkdirs()
            val path=this
            Runtime.getRuntime().addShutdownHook(Thread {path.deleteRecursively()})
        }
    }
    @Synchronized fun directory():File=session.apply {mkdirs()}
    @Synchronized fun prune(keep:Set<String>) {
        val retained=keep.map {File(it).canonicalPath}.toSet()
        session.listFiles()?.filter {it.isFile && it.extension=="png" && it.canonicalPath !in retained}?.forEach {it.delete()}
    }

}
