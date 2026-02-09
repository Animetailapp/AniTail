import org.gradle.internal.os.OperatingSystem
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

tasks.named<ProcessResources>("processResources") {
    from("../app/src/main/res") {
        include("values/**", "values-*/**")
    }
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
    implementation(libs.materialKolor)
    implementation(libs.compose.reorderable)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation(libs.json)
    implementation(libs.okhttp)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    // Ktor for lyrics and API calls
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$javafxPlatform")

    // VLC-based player
    implementation(libs.vlcj)

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

configurations.all {
    resolutionStrategy {
        // Force CM version to match plugin to avoid Skiko UnsatisfiedLinkError
        force("org.jetbrains.compose.runtime:runtime:1.9.3")
        force("org.jetbrains.compose.ui:ui:1.9.3")
        force("org.jetbrains.compose.foundation:foundation:1.9.3")
        force("org.jetbrains.compose.material3:material3:1.9.0")
    }
}

compose.desktop {
    application {
        mainClass = "com.anitail.desktop.MainKt"
        nativeDistributions {
            val os = OperatingSystem.current()
            val formats = when {
                os.isMacOsX -> listOf(TargetFormat.Dmg)
                os.isWindows -> listOf(TargetFormat.Msi)
                else -> listOf(TargetFormat.Deb, TargetFormat.Rpm)
            }
            targetFormats(*formats.toTypedArray())
            packageName = "AniTail"
            packageVersion = "1.13.1"
            macOS {
                iconFile.set(project.file("src/main/resources/drawable/ic_anitail.icns"))
                bundleID = "com.anitail.desktop"
            }
            linux {
                iconFile.set(project.file("src/main/resources/drawable/ic_anitail.png"))
                shortcut = true
                menuGroup = "AniTail"
            }
            windows {
                iconFile.set(project.file("src/main/resources/drawable/ic_anitail.ico"))
                menuGroup = "AniTail"
                shortcut = true
                menu = true
                perUserInstall = false
            }
        }
        buildTypes {
            release {
                proguard {
                    version.set("7.4.2")
                    configurationFiles.from(project.file("proguard-rules.pro"))
                    obfuscate.set(false)
                    optimize.set(false)
                }
            }
        }
    }
}

tasks.register("runDesktop") {
    group = "application"
    description = "Run the AniTail Desktop app."
    dependsOn("run")
}
