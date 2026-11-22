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

/**
 * Drives the real app through the accessibility tree, with no Compose idle
 * sync: the editor never reports idle (video surface, animations), so the
 * Compose test rule's waits time out there.
 */
object Ui {
    private val ui get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun find(text: String): android.view.accessibility.AccessibilityNodeInfo? {
        val root = ui.rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByText(text)
            .firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
    }

    fun waitFor(text: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (find(text) != null) return true
            Thread.sleep(300)
        }
        return false
    }

    /** Clicks the node showing [text] (or its nearest clickable parent). */
    fun click(text: String): Boolean {
        var node = find(text) ?: return false
        while (!node.isClickable) node = node.parent ?: return false
        return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Clicks [text], swiping the tool strip left a few times to bring it in view. */
    fun clickScrolling(text: String): Boolean {
        repeat(4) {
            if (click(text)) return true
            ui.executeShellCommand("input swipe 1000 2175 200 2175 250").use { it.fileDescriptor }
            Thread.sleep(600)
        }
        return false
    }
}
