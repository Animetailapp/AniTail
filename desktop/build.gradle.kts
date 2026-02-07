import org.gradle.internal.os.OperatingSystem
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)
}

val javafxVersion = "21.0.2"
val javafxPlatform = when {
    OperatingSystem.current().isWindows -> "win"
    OperatingSystem.current().isMacOsX -> "mac"
    else -> "linux"
}

dependencies {
    implementation(projects.common)
    implementation(projects.innertube)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.materialKolor)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation(libs.json)
    implementation(libs.okhttp)

    // Ktor for lyrics and API calls
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$javafxPlatform")

    // New multiplatform player (VLC/Media3/AVPlayer)
    implementation(libs.compose.multiplatform.media.player)
}

configurations.all {
    resolutionStrategy {
        // Force CM version to match plugin to avoid Skiko UnsatisfiedLinkError
        force("org.jetbrains.compose.runtime:runtime:1.7.3")
        force("org.jetbrains.compose.ui:ui:1.7.3")
        force("org.jetbrains.compose.foundation:foundation:1.7.3")
        force("org.jetbrains.compose.material3:material3:1.7.3")
        // Skiko version alignment (matching CM 1.7.3)
        force("org.jetbrains.skiko:skiko:0.8.18")
        force("org.jetbrains.skiko:skiko-jvm:0.8.18")
    }
}

compose.desktop {
    application {
        mainClass = "com.anitail.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.AppImage)
            packageName = "AniTail"
            packageVersion = "1.13.1"
        }
    }
}

tasks.register("runDesktop") {
    group = "application"
    description = "Run the AniTail Desktop app."
    dependsOn("run")
}
