package com.cuscus.ffmpeggui.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "FFmpegGUI · Linux development preview") {
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("FFmpegGUI", style = MaterialTheme.typography.displaySmall)
                    Text("Native Linux port · development preview")
                    Text("Kotlin core is shared with Android. Desktop adapters and editor screens are being ported.")
                    Text("This preview is not the completed Linux version.")
                }
            }
        }
    }
}
