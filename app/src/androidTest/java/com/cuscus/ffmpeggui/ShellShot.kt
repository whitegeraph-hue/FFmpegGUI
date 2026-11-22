package com.cuscus.ffmpeggui

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Screenshots through the shell's screencap into /sdcard/ci-shots. connected
 * tests uninstall the app afterwards (wiping its own files dir), so the
 * evidence must live outside the app's storage for the CI pull to find it.
 */
object ShellShot {
    fun take(name: String) {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        run(ui, "mkdir -p /sdcard/ci-shots")
        run(ui, "screencap -p /sdcard/ci-shots/$name.png")
    }

    private fun run(ui: android.app.UiAutomation, command: String) {
        ui.executeShellCommand(command).use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        }
    }
}
