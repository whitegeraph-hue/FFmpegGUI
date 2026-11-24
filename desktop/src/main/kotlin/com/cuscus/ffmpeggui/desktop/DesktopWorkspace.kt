package com.cuscus.ffmpeggui.desktop

import kotlinx.serialization.json.*
import java.util.prefs.Preferences

/** UI preferences only. Project and recovery contents never enter this store. */
data class DesktopWorkspace(
    val inspectorWidth:Int=330,
    val railWidth:Int=164,
    val timelineHeight:Int=204,
    val paneOrder:List<String> = listOf("tools","preview","inspector"),
    val timelineTop:Boolean=false,
    val accent:String="008577",
    val customAccent:Boolean=false,
    val projectToolbar:List<String> = projectActions,
    val timelineToolbar:List<String> = timelineActions,
    val shortcuts:Map<String,String> = shortcutDefaults
) {
    fun normalized()=copy(
        inspectorWidth=inspectorWidth.coerceIn(260,520),railWidth=railWidth.coerceIn(130,250),timelineHeight=timelineHeight.coerceIn(160,360),
        paneOrder=ordered(paneOrder,listOf("tools","preview","inspector"),true),
        accent=accent.uppercase().takeIf {it.matches(Regex("[0-9A-F]{6}"))} ?: "008577",
        projectToolbar=ordered(projectToolbar,projectActions,false),timelineToolbar=ordered(timelineToolbar,timelineActions,false),
        shortcuts=shortcutDefaults.mapValues {(action,default)->shortcuts[action]?.takeIf {it.isEmpty() || validShortcut(it)} ?: default}.let {if(shortcutProblem(it)==null)it else shortcutDefaults}
    )
    companion object {
        val projectActions=listOf("Open media","Open project","Save project","Save as","Recover","Undo","Redo")
        val timelineActions=listOf("Fit","Zoom out","Zoom in","Split","Duplicate","Left","Right","Remove","Add")
        val shortcutDefaults=linkedMapOf("Save project" to "Ctrl+S","Save as" to "Ctrl+Shift+S","Open media" to "Ctrl+O","Open project" to "Ctrl+Shift+O","Undo" to "Ctrl+Z","Redo" to "Ctrl+Shift+Z","Play/pause" to "Space","Step left" to "Left","Step right" to "Right","Start" to "Home","End" to "End","Left" to "Alt+Left","Right" to "Alt+Right","Trim in" to "I","Trim out" to "O","Split" to "S","Duplicate" to "D","Remove" to "Delete")
        private val keys=('A'..'Z').map {it.toString()}+listOf("Space","Left","Right","Home","End","Delete","Backspace")
        fun validShortcut(value:String):Boolean {
            val parts=value.split('+');return parts.lastOrNull() in keys && parts.dropLast(1).all {it in listOf("Ctrl","Shift","Alt")} && parts.distinct().size==parts.size && parts.dropLast(1)==listOf("Ctrl","Shift","Alt").filter {it in parts.dropLast(1)}
        }
        fun shortcutProblem(values:Map<String,String>):String? {
            values.entries.firstOrNull {it.value.contains("Ctrl+") && it.value.contains("Alt+")}?.let {return "Ctrl+Alt is reserved for AltGr text entry."}
            values.entries.firstOrNull {it.key in listOf("Undo","Redo") && it.value!=shortcutDefaults[it.key]}?.let {return "Undo/Redo keep their standard keys to protect text editing."}
            val reserved=setOf("Ctrl+C","Ctrl+V","Ctrl+X","Ctrl+A","Ctrl+Z","Ctrl+Y","Ctrl+Shift+Z","Ctrl+Shift+C","Ctrl+Shift+V","Ctrl+Shift+X","Ctrl+Shift+A")
            values.entries.firstOrNull {it.key !in listOf("Undo","Redo") && it.value in reserved}?.let {return "${it.value} is reserved for text editing."}
            values.entries.firstOrNull {it.value.isNotEmpty() && !validShortcut(it.value)}?.let {return "Invalid shortcut for ${it.key}. Use Ctrl+Shift+S, Alt+Left, or a single key."}
            values.entries.filter {it.value.isNotEmpty()}.groupBy {it.value}.entries.firstOrNull {it.value.size>1}?.let {return "${it.key} is assigned to "+it.value.joinToString {v->v.key}}
            values.entries.firstOrNull {it.key in listOf("Save project","Save as","Open media","Open project","Undo","Redo") && it.value.isNotEmpty() && !it.value.startsWith("Ctrl+")}?.let {return "Project shortcut ${it.key} must include Ctrl to protect text editing."}
            return null
        }
        private fun ordered(value:List<String>,allowed:List<String>,complete:Boolean):List<String> {
            val clean=value.filter {it in allowed}.distinct();return if(complete)clean+allowed.filter {it !in clean} else clean
        }
    }
}

internal object DesktopWorkspaceCodec {
    fun encode(value:DesktopWorkspace):String {
        val p=value.normalized()
        return buildJsonObject {
            put("version",1);put("inspectorWidth",p.inspectorWidth);put("railWidth",p.railWidth);put("timelineHeight",p.timelineHeight)
            put("timelineTop",p.timelineTop);put("accent",p.accent);put("customAccent",p.customAccent)
            putJsonArray("paneOrder") {p.paneOrder.forEach {add(it)}}
            putJsonArray("projectToolbar") {p.projectToolbar.forEach {add(it)}}
            putJsonArray("timelineToolbar") {p.timelineToolbar.forEach {add(it)}}
            putJsonObject("shortcuts") {p.shortcuts.forEach {(key,value)->put(key,value)}}
        }.toString()
    }
    fun decode(text:String):DesktopWorkspace=runCatching {
        val j=Json.parseToJsonElement(text).jsonObject;val d=DesktopWorkspace()
        fun int(k:String,f:Int)=j[k]?.jsonPrimitive?.intOrNull ?: f
        fun bool(k:String,f:Boolean)=j[k]?.jsonPrimitive?.booleanOrNull ?: f
        fun list(k:String,f:List<String>)=j[k]?.jsonArray?.map {it.jsonPrimitive.content} ?: f
        val readShortcuts=j["shortcuts"]?.jsonObject?.mapValues {it.value.jsonPrimitive.content} ?: d.shortcuts
        val shortcuts=if(DesktopWorkspace.shortcutProblem(readShortcuts)==null)readShortcuts else d.shortcuts
        DesktopWorkspace(int("inspectorWidth",d.inspectorWidth),int("railWidth",d.railWidth),int("timelineHeight",d.timelineHeight),list("paneOrder",d.paneOrder),bool("timelineTop",false),j["accent"]?.jsonPrimitive?.content ?: d.accent,bool("customAccent",false),list("projectToolbar",d.projectToolbar),list("timelineToolbar",d.timelineToolbar),shortcuts).normalized()
    }.getOrDefault(DesktopWorkspace())
}

internal class DesktopWorkspaceStore(private val preferences:Preferences) {
    fun load()=DesktopWorkspaceCodec.decode(preferences.get("workspace","{}"))
    fun save(value:DesktopWorkspace) {preferences.put("workspace",DesktopWorkspaceCodec.encode(value));preferences.flush()}
    fun names():List<String> = runCatching {Json.parseToJsonElement(preferences.get("workspaceLayouts","{}")).jsonObject.keys.sorted()}.getOrDefault(emptyList())
    fun loadLayout(name:String):DesktopWorkspace?=runCatching {Json.parseToJsonElement(preferences.get("workspaceLayouts","{}")).jsonObject[name]?.let {DesktopWorkspaceCodec.decode(it.toString())}}.getOrNull()
    fun saveLayout(name:String,value:DesktopWorkspace) {
        require(name.trim().length in 1..48) {"Use a layout name from 1 to 48 characters."}
        val current=readLayouts()
        require(name.trim() in current || current.size<6) {"Keep up to 6 layouts. Delete one before saving another."}
        val next=JsonObject(current+mapOf(name.trim() to Json.parseToJsonElement(DesktopWorkspaceCodec.encode(value))))
        require(next.toString().length<Preferences.MAX_VALUE_LENGTH) {"Saved layouts are full. Delete a layout first."}
        preferences.put("workspaceLayouts",next.toString());preferences.flush()
    }
    private fun readLayouts():JsonObject=runCatching {Json.parseToJsonElement(preferences.get("workspaceLayouts","{}")).jsonObject}.getOrElse {throw IllegalStateException("Saved layout data could not be read. It was kept unchanged.")}
    fun deleteLayout(name:String) {
        val current=readLayouts()
        preferences.put("workspaceLayouts",JsonObject(current-name).toString());preferences.flush()
    }
}
