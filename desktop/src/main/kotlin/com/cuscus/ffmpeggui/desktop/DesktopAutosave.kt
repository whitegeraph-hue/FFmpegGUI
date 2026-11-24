package com.cuscus.ffmpeggui.desktop

import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Per-window recovery files never overwrite the user's manual project or its backup. */
class DesktopAutosave(val root:File=defaultRoot()) {
    private val mutex=Mutex()
    private var destination=newFile()
    private fun newFile()=File(root,"edit-${UUID.randomUUID()}.ffgui.json")
    suspend fun save(project:DesktopProject)=withContext(Dispatchers.IO) {
        mutex.withLock {
            require(!Files.isSymbolicLink(root.toPath())) {"Recovery folder cannot be a symbolic link."}
            Files.createDirectories(root.toPath())
            runCatching {Files.setPosixFilePermissions(root.toPath(),java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))}
            DesktopProjects.save(project,destination)
        }
    }
    fun nextProject() {destination=newFile()}
    fun resume(file:File) {require(file.parentFile.canonicalFile==root.canonicalFile && file.isFile && !Files.isSymbolicLink(file.toPath()));destination=if(file.name.endsWith(".previous.ffgui.json"))newFile() else file}
    fun isCurrent(file:File)=file.absoluteFile==destination.absoluteFile
    fun delete(file:File) {
        require(!isCurrent(file)) {"Current recovery is in use. Close this project before deleting it."}
        require(entries().any {it.canonicalFile==file.canonicalFile})
        check(file.delete()) {"Could not delete automatic save."}
        DesktopProjects.previousFile(file).takeIf {it.isFile && !Files.isSymbolicLink(it.toPath())}?.delete()
    }
    fun label(file:File):String=runCatching {
        val p=Json.parseToJsonElement(file.readText()).jsonObject
        val sources=p["sources"]!!.jsonArray.map {it.jsonObject["path"]!!.jsonPrimitive.content}
        val clips=p["timeline"]!!.jsonObject["clips"]!!.jsonArray.size
        sources.joinToString {File(it).name}+" · $clips clips"+if(sources.any {!File(it).isFile})" · missing media" else ""
    }.getOrDefault("Unreadable recovery · ${file.name}")
    fun entries():List<File> = root.listFiles()?.filter {it.isFile && it.name.startsWith("edit-") && it.name.endsWith(".ffgui.json") && !it.name.endsWith(".previous.ffgui.json") && !Files.isSymbolicLink(it.toPath())}?.sortedByDescending {it.lastModified()} ?: emptyList()
    companion object {
        fun defaultRoot()=File(System.getenv("XDG_DATA_HOME") ?: File(System.getProperty("user.home"),".local/share").path,"ffmpeggui-linux/recovery")
    }
}
