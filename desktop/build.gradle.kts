import java.security.MessageDigest
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.2"
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":shared-core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
compose.desktop {
    application {
        mainClass = "com.cuscus.ffmpeggui.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "ffmpeggui-linux"
            packageVersion = "0.1.0"
            description = "FFmpegGUI native Linux port (development preview)"
            vendor = "FFmpegGUI contributors"
            modules("java.desktop", "java.logging", "java.net.http", "jdk.unsupported")
            linux { menuGroup = "AudioVideo"; appCategory = "AudioVideo"; debMaintainer = "noreply@localhost" }
        }
    }
}
tasks.register<JavaExec>("smokeExport") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.cuscus.ffmpeggui.desktop.SmokeKt")
    args = listOf(project.findProperty("input")?.toString() ?: "", project.findProperty("output")?.toString() ?: "")
}

// Compile the real Android smart panels on Desktop, changing only resource lookup.
// The originals remain the single source; Android files are never edited here.
val prepareSharedUi by tasks.registering {
    val source = file("../app/src/main/java/com/cuscus/ffmpeggui")
    val strings = file("../app/src/main/res/values/strings.xml")
    val out = layout.buildDirectory.dir("generated/sharedUi")
    inputs.files(source.resolve("SmartEditPanels.kt"), source.resolve("conversion/SmartEditStatus.kt"), source.resolve("ui/theme/Color.kt"), strings)
    outputs.dir(out)
    doLast {
        val folder = out.get().asFile.apply { mkdirs() }
        val panels = source.resolve("SmartEditPanels.kt").readText()
        folder.resolve("SmartEditPanels.kt").writeText(panels.replace("import androidx.compose.ui.res.stringResource", "import com.cuscus.ffmpeggui.desktop.stringResource") + "\ninternal fun decimal(value: Float, places: Int): String = java.lang.String.format(java.util.Locale.US, \"%.\${places}f\", value)\n")
        source.resolve("conversion/SmartEditStatus.kt").copyTo(folder.resolve("SmartEditStatus.kt"), overwrite = true)
        source.resolve("ui/theme/Color.kt").copyTo(folder.resolve("Color.kt"), overwrite = true)
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(strings)
        val nodes = document.getElementsByTagName("string")
        val values = (0 until nodes.length).associate { i ->
            val node = nodes.item(i) as org.w3c.dom.Element
            node.getAttribute("name") to node.textContent.replace("\\'", "'").replace("\\n", "\n")
        }
        val used = Regex("R\\.string\\.([a-zA-Z0-9_]+)").findAll(panels).map { it.groupValues[1] }.toSet()
        fun quoted(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("$", "\\$") + "\""
        folder.resolve("R.kt").writeText("package com.cuscus.ffmpeggui\nobject R { object string {\n" + used.joinToString("\n") { "const val $it = ${quoted(values[it] ?: error("Missing string $it"))}" } + "\n}}\n")
    }
}
sourceSets.main { kotlin.srcDir(layout.buildDirectory.dir("generated/sharedUi")) }
tasks.named("compileKotlin") { dependsOn(prepareSharedUi) }
tasks.register<JavaExec>("nativeChecks") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.cuscus.ffmpeggui.desktop.NativeChecksKt")
    args = listOf(project.findProperty("fixtureDir")?.toString() ?: "/tmp/ffmpeggui-fixtures")
}

dependencies {
    implementation(files("libs/sherpa-onnx-jvm-2.0.3.jar", "libs/sherpa-onnx-native-lib-linux-x64-2.0.3.jar"))
    implementation("com.microsoft.onnxruntime:onnxruntime:1.28.0")
}
val prepareModelDefinitions by tasks.registering(Copy::class) {
    from("../app/src/main/java/com/cuscus/ffmpeggui/speech/SpeechModel.kt")
    from("../app/src/main/java/com/cuscus/ffmpeggui/cutout/CutoutModel.kt")
    into(layout.buildDirectory.dir("generated/modelDefinitions"))
}
sourceSets.main { kotlin.srcDir(layout.buildDirectory.dir("generated/modelDefinitions")); resources.srcDir("resources") }
tasks.named("compileKotlin") { dependsOn(prepareModelDefinitions) }
tasks.register<JavaExec>("modelChecks") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.cuscus.ffmpeggui.desktop.DesktopSmartModelsChecksKt")
    args = listOf(project.findProperty("modelDir")?.toString() ?: "/tmp/ffmpeggui-models", "--speech", "--speech-first")
}

val verifyNativeJars by tasks.registering {
    inputs.files(fileTree("libs") { include("*.jar") })
    doLast {
        file("libs/SHA256SUMS").readLines().filter { it.isNotBlank() }.forEach { line ->
            val pair = line.split(Regex("\\s+"), limit = 2)
            val digest = MessageDigest.getInstance("SHA-256").digest(file("libs/${pair[1]}").readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
            check(digest == pair[0]) { "Native runtime checksum mismatch: ${pair[1]}" }
        }
    }
}
tasks.named("compileKotlin") { dependsOn(verifyNativeJars) }
tasks.register("writeRuntimeClasspath") {
    doLast { layout.buildDirectory.file("runtime-classpath.txt").get().asFile.writeText(sourceSets.main.get().runtimeClasspath.asPath) }
}
