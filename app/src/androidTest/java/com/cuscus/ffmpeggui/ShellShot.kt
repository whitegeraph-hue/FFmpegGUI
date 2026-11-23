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

    private fun find(text: String): android.view.accessibility.AccessibilityNodeInfo? {
        val nodes=all()
        return nodes.firstOrNull { it.text?.toString()?.equals(text,ignoreCase=true)==true || it.contentDescription?.toString()?.equals(text,ignoreCase=true)==true }
            ?: nodes.firstOrNull { label(it).contains(text,ignoreCase=true) }
    }

    fun waitFor(text: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (find(text) != null) return true
            Thread.sleep(300)
        }
        return false
    }

    /**
     * Clicks the node showing [text]: through its nearest clickable ancestor
     * when it has one, else with a real tap at the middle of its bounds (the
     * CI tree showed Compose cards whose labels have no clickable ancestor in
     * the accessibility tree; the click is merged elsewhere).
     */
    fun click(text: String): Boolean {
        val label = find(text) ?: return false
        var node: android.view.accessibility.AccessibilityNodeInfo? = label
        while (node != null && !node.isClickable) node = node.parent
        if (node != null && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = android.graphics.Rect().also { label.getBoundsInScreen(it) }
        if (r.isEmpty) return false
        ui.executeShellCommand("input tap ${r.centerX()} ${r.centerY()}").use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        }
        return true
    }

    /** Clicks [text], swiping the tool strip left a few times to bring it in view. */
    fun clickScrolling(text: String): Boolean {
        repeat(15) {
            if (click(text)) return true
            val strip=all().firstOrNull {n->
                val r=android.graphics.Rect().also {n.getBoundsInScreen(it)}
                n.isScrollable && r.width()>r.height()*2 && r.height()>20
            }
            if(strip!=null)strip.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(400)
        }
        return false
    }
}
