plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
sourceSets {
    main { kotlin.srcDir("../../core/src/main/kotlin") }
    test { kotlin.srcDir("../../core/src/test/kotlin") }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
