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

    private fun flags() {
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    private fun walk(node: android.view.accessibility.AccessibilityNodeInfo?, out: MutableList<android.view.accessibility.AccessibilityNodeInfo>) {
        node ?: return
        out += node
        for (i in 0 until node.childCount) walk(node.getChild(i), out)
    }

    private fun all(): List<android.view.accessibility.AccessibilityNodeInfo> {
        flags()
        val out = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        // Every window, not only the active one: the app window may not be reported as active.
        ui.windows.forEach { walk(it.root, out) }
        walk(ui.rootInActiveWindow, out)
        return out
    }

    private fun label(n: android.view.accessibility.AccessibilityNodeInfo): String =
        listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" | ")

    /** What the accessibility tree holds, for the CI log when a lookup fails. */
    fun dump() {
        val nodes = all()
        android.util.Log.i("CI-A11Y", "nodes=${nodes.size}")
        nodes.forEach { android.util.Log.i("CI-A11Y", "${it.className} click=${it.isClickable} '${label(it)}'") }
    }

    private fun find(text: String): android.view.accessibility.AccessibilityNodeInfo? =
        all().firstOrNull { label(it).contains(text, ignoreCase = true) }

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
