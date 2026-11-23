package com.cuscus.ffmpeggui.desktop

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*

/** Runs after the field's own text handler, before the window's project history. */
internal fun Modifier.isolateProjectHistory():Modifier=onKeyEvent {event->
    event.type==KeyEventType.KeyDown && event.isCtrlPressed && (event.key==Key.Z || event.key==Key.Y)
}
