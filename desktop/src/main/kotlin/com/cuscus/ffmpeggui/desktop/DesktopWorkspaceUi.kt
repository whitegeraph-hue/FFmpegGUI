package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

internal val LocalDesktopWorkspace=staticCompositionLocalOf {DesktopWorkspace()}
internal fun KeyEvent.workspaceStroke():String? {
    val name=when(key) {
        Key.Spacebar->"Space";Key.DirectionLeft->"Left";Key.DirectionRight->"Right";Key.Home->"Home";Key.MoveEnd->"End";Key.Delete->"Delete";Key.Backspace->"Backspace"
        else->listOf(Key.A,Key.B,Key.C,Key.D,Key.E,Key.F,Key.G,Key.H,Key.I,Key.J,Key.K,Key.L,Key.M,Key.N,Key.O,Key.P,Key.Q,Key.R,Key.S,Key.T,Key.U,Key.V,Key.W,Key.X,Key.Y,Key.Z).indexOf(key).takeIf {it>=0}?.let {('A'.code+it).toChar().toString()}
    } ?: return null
    if(isMetaPressed || (isCtrlPressed && isAltPressed))return null
    return listOfNotNull(if(isCtrlPressed)"Ctrl" else null,if(isShiftPressed)"Shift" else null,if(isAltPressed)"Alt" else null,name).joinToString("+")
}
internal fun DesktopWorkspace.action(event:KeyEvent):String?=event.workspaceStroke()?.let {stroke->
    shortcuts.entries.firstOrNull {it.value==stroke}?.key ?: when {
        stroke=="Shift+Left" && shortcuts["Step left"]=="Left"->"Step left"
        stroke=="Shift+Right" && shortcuts["Step right"]=="Right"->"Step right"
        else->null
    }
}
internal fun workspaceColors(base:ColorScheme,p:DesktopWorkspace):ColorScheme {
    if(!p.customAccent)return base
    val requested=Color(0xff000000 or p.accent.toLong(16))
    val backgrounds=listOf(base.surface,base.surfaceContainer,base.surfaceContainerLow,base.surfaceContainerHigh,base.surfaceContainerHighest,base.surfaceContainerLowest,base.surfaceVariant,base.background)
    val surface=base.surface.luminance()
    fun contrast(c:Color):Float = backgrounds.minOf {background->val l=c.luminance();val b=background.luminance();(maxOf(l,b)+.05f)/(minOf(l,b)+.05f)}
    var accent=requested
    val target=if(surface>.5f)Color.Black else Color.White
    for(i in 1..20) {if(contrast(accent)>=4.5f)break;accent=lerp(requested,target,i/20f)}
    val ink=if(accent.luminance()>.179f)Color.Black else Color.White
    return base.copy(primary=accent,onPrimary=ink,secondary=accent,onSecondary=ink,tertiary=accent,onTertiary=ink)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DesktopWorkspaceEditor(current:DesktopWorkspace,store:DesktopWorkspaceStore,onApply:(DesktopWorkspace)->Unit,onClose:()->Unit) {
    var draft by remember {mutableStateOf(current)}
    var section by remember {mutableStateOf("Layout")}
    var layoutName by remember {mutableStateOf("")}
    var names by remember {mutableStateOf(store.names())}
    var notice by remember {mutableStateOf("")}
    var confirmReset by remember {mutableStateOf(false)}
    val problem=DesktopWorkspace.shortcutProblem(draft.shortcuts)
    val editorScroll=rememberScrollState()
    LaunchedEffect(section) {editorScroll.scrollTo(0)}
    AlertDialog(onDismissRequest=onClose,modifier=Modifier.widthIn(max=820.dp),title={Text("Customize workspace")},text={
        Column(Modifier.fillMaxWidth()) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("Layout","Colours","Toolbars","Shortcuts","Saved layouts").forEach {name->FilterChip(section==name,{section=name},label={Text(name)})}
            }
            Box(Modifier.heightIn(min=160.dp,max=300.dp)) {
            Column(Modifier.padding(end=12.dp).verticalScroll(editorScroll),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                when(section) {
                    "Layout"->{
                        Text("Size and arrange panels. Compact windows clamp sizes to keep the preview usable. Apply changes to preview them; Reset defaults is always available.")
                        Text("Inspector width: ${draft.inspectorWidth} dp");Slider(draft.inspectorWidth.toFloat(),{draft=draft.copy(inspectorWidth=it.toInt())},valueRange=260f..520f)
                        Text("Tools width: ${draft.railWidth} dp");Slider(draft.railWidth.toFloat(),{draft=draft.copy(railWidth=it.toInt())},valueRange=130f..250f)
                        Text("Timeline height: ${draft.timelineHeight} dp");Slider(draft.timelineHeight.toFloat(),{draft=draft.copy(timelineHeight=it.toInt())},valueRange=160f..360f)
                        Row(verticalAlignment=Alignment.CenterVertically) {Switch(draft.timelineTop,{draft=draft.copy(timelineTop=it)});Text("Timeline above workspace")}
                        Text("Panels, left to right")
                        draft.paneOrder.forEachIndexed {i,name->Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(name.replaceFirstChar {it.uppercase()},Modifier.weight(1f))
                            TextButton(onClick={val a=draft.paneOrder.toMutableList();java.util.Collections.swap(a,i,i-1);draft=draft.copy(paneOrder=a)},enabled=i>0){Text("Left")}
                            TextButton(onClick={val a=draft.paneOrder.toMutableList();java.util.Collections.swap(a,i,i+1);draft=draft.copy(paneOrder=a)},enabled=i<2){Text("Right")}
                        }}
                    }
                    "Colours"->{
                        Text("Light/dark mode stays in Settings. Customize the action accent here; neutral surfaces keep their readable theme colours. Very light or dark accents are adjusted for readable controls.")
                        Row(verticalAlignment=Alignment.CenterVertically) {Switch(draft.customAccent,{draft=draft.copy(customAccent=it)});Text("Custom accent")}
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("008577","2463EB","7C3AED","B42318","AF5200","467516").forEach {hex->Button(onClick={draft=draft.copy(accent=hex,customAccent=true)},colors=ButtonDefaults.buttonColors(containerColor=Color(0xff000000 or hex.toLong(16)))){Text(hex)}}}
                        OutlinedTextField(draft.accent,{draft=draft.copy(accent=it.uppercase().take(6))},label={Text("Accent hex (6 digits, no #)")},singleLine=true,isError=!draft.accent.matches(Regex("[0-9A-Fa-f]{6}")))
                    }
                    "Toolbars"->{
                        Text("Show, hide and reorder actions. Settings and Export remain available so you cannot lose access. Hidden actions with assigned shortcuts still work. To restore Add or any other hidden action, return here or use Reset defaults, then Apply.")
                        @Composable fun Toolbar(title:String,all:List<String>,selected:List<String>,update:(List<String>)->Unit) {
                            Text(title,style=MaterialTheme.typography.titleMedium)
                            (selected+all.filter {it !in selected}).forEach {action->Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(action in selected,{checked->update(if(checked)selected+action else selected-action)})
                                Text(action,Modifier.weight(1f))
                                val at=selected.indexOf(action)
                                TextButton(onClick={val a=selected.toMutableList();java.util.Collections.swap(a,at,at-1);update(a)},enabled=at>0){Text("Up")}
                                TextButton(onClick={val a=selected.toMutableList();java.util.Collections.swap(a,at,at+1);update(a)},enabled=at>=0 && at<selected.lastIndex){Text("Down")}
                            }}
                        }
                        Toolbar("Project",DesktopWorkspace.projectActions,draft.projectToolbar){draft=draft.copy(projectToolbar=it)}
                        Toolbar("Timeline",DesktopWorkspace.timelineActions,draft.timelineToolbar){draft=draft.copy(timelineToolbar=it)}
                    }
                    "Shortcuts"->{
                        Text("Use Ctrl+Shift+S, Alt+Left, or a single letter. Modifier order is Ctrl, Shift, Alt. Leave blank to disable. Escape is reserved. Timeline shortcuts run only with timeline focus; project shortcuts need Ctrl. Copy/paste/select-all and AltGr are reserved. Undo/Redo keep standard keys, including Ctrl+Y for Redo.")
                        draft.shortcuts.forEach {(action,value)->OutlinedTextField(value,{draft=draft.copy(shortcuts=draft.shortcuts+mapOf(action to it))},label={Text(action)},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=action !in listOf("Undo","Redo"))}
                        problem?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                    }
                    "Saved layouts"->{
                        Text("Up to 6 saved layouts include panel order and sizes, accent, toolbars and shortcuts. They do not contain media or edits. Save uses the settings currently shown in this editor. Saving or deleting a named layout takes effect immediately; Cancel only discards changes to the active workspace.")
                        OutlinedTextField(layoutName,{layoutName=it.take(48)},label={Text("Layout name")},singleLine=true)
                        Button(onClick={runCatching {store.saveLayout(layoutName,draft);names=store.names();notice="Saved $layoutName"}.onFailure {notice=it.message ?: "Could not save layout"}},enabled=layoutName.isNotBlank() && problem==null && draft.accent.matches(Regex("[0-9A-Fa-f]{6}"))){Text(if(layoutName.trim() in names)"Replace named layout" else "Save named layout")}
                        names.forEach {name->Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(name,Modifier.weight(1f))
                            TextButton(onClick={store.loadLayout(name)?.let {draft=it;notice="Loaded $name. Apply to use it."}}){Text("Load")}
                            TextButton(onClick={runCatching {store.deleteLayout(name);names=store.names();notice="Deleted $name"}.onFailure {notice=it.message ?: "Could not delete layout"}}){Text("Delete")}
                        }}
                    }
                }
                if(notice.isNotBlank())Text(notice,style=MaterialTheme.typography.bodySmall)
            }
            VerticalScrollbar(rememberScrollbarAdapter(editorScroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(8.dp))
            }
            TextButton(onClick={confirmReset=true}){Text("Reset defaults")}
        }
    },confirmButton={Button(onClick={runCatching {onApply(draft.normalized());onClose()}.onFailure {notice="Could not save workspace: ${it.message}"}},enabled=problem==null && draft.accent.matches(Regex("[0-9A-Fa-f]{6}"))){Text("Apply")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
    if(confirmReset)AlertDialog(onDismissRequest={confirmReset=false},title={Text("Reset workspace defaults?")},text={Text("Panel arrangement, sizes, accent, toolbars and shortcuts return to defaults. Saved layouts, projects and automatic recovery are kept.")},confirmButton={TextButton(onClick={draft=DesktopWorkspace();confirmReset=false;notice="Defaults ready. Apply to use them."}){Text("Reset")}},dismissButton={TextButton(onClick={confirmReset=false}){Text("Cancel")}})
}
