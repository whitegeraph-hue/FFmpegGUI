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
            linux { menuGroup = "AudioVideo"; appCategory = "AudioVideo"; debMaintainer = "FFmpegGUI contributors" }
        }
    }
}
tasks.register<JavaExec>("smokeExport") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.cuscus.ffmpeggui.desktop.SmokeKt")
    args = listOf(project.findProperty("input")?.toString() ?: "", project.findProperty("output")?.toString() ?: "")
}
