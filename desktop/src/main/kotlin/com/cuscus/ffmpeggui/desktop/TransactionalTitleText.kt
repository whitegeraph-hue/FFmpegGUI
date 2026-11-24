package com.cuscus.ffmpeggui.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.TextOverlay

/** A title draft owns text history. It never writes to the project until Apply. */
internal class TitleTextDraft(val expected:DesktopProject,val index:Int,val layer:TextOverlay) {
    var value by mutableStateOf(TextFieldValue(layer.text))
        private set
    private val past=ArrayDeque<TextFieldValue>()
    private val future=ArrayDeque<TextFieldValue>()
    fun edit(next:TextFieldValue) {
        if(next.text!=value.text){past.addLast(value);future.clear()}
        value=next
    }
    fun undo(){if(past.isNotEmpty()){future.addLast(value);value=past.removeLast()}}
    fun redo(){if(future.isNotEmpty()){past.addLast(value);value=future.removeLast()}}
    fun isCurrent(c:EditorController)=!c.busy && c.project===expected && expected.captions.getOrNull(index)===layer
}

@Composable internal fun TransactionalTitleText(c:EditorController,p:DesktopProject,index:Int) {
    val layer=p.captions.getOrNull(index) ?: return
    var draft by remember(layer.id) {mutableStateOf<TitleTextDraft?>(null)}
    Text(layer.text.ifEmpty {"(Empty text)"},style=MaterialTheme.typography.bodyLarge)
    OutlinedButton(onClick={draft=TitleTextDraft(p,index,layer)},enabled=!c.busy){Text("Edit text…")}
    draft?.let {d->
        DisposableEffect(d) {
            c.titleTextDraft=d
            onDispose {if(c.titleTextDraft===d)c.titleTextDraft=null}
        }
        val current=d.isCurrent(c)
        val focus=remember(d) {FocusRequester()}
        LaunchedEffect(d) {focus.requestFocus()}
        AlertDialog(onDismissRequest={draft=null},properties=DialogProperties(dismissOnClickOutside=false),modifier=Modifier.onPreviewKeyEvent {event->
            if(event.type!=KeyEventType.KeyDown)false
            else when {
                event.key==Key.Escape->{draft=null;true}
                event.isCtrlPressed && event.key==Key.Z->{if(event.isShiftPressed)d.redo() else d.undo();true}
                event.isCtrlPressed && event.key==Key.Y->{d.redo();true}
                else->false
            }
        },title={Text("Edit text layer")},text={
            Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Changes stay in this draft until Apply. Cancel or Escape keeps the original text. Apply is one project Undo step; style, timing and clip binding stay unchanged.")
                OutlinedTextField(d.value,{d.edit(it)},label={Text("Text")},enabled=current,modifier=Modifier.fillMaxWidth().heightIn(min=100.dp,max=220.dp).focusRequester(focus).isolateProjectHistory())
                if(!current)Text("The project changed or is busy. Cancel and reopen text editing.",color=MaterialTheme.colorScheme.error)
            }
        },confirmButton={Button(onClick={if(c.applyTitleText(d))draft=null},enabled=current && d.value.text!=d.layer.text){Text("Apply")}},dismissButton={TextButton(onClick={draft=null}){Text("Cancel")}})
    }
}
