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
    val packageVersion = run {
        val text = file("build.gradle.kts").readText()
        Regex("packageVersion\\s*=\\s*\"([^\"]+)\"")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?: "0.0.0"
    }
    val generatedDir = layout.buildDirectory.dir("generated/version").get().asFile
    val versionFile = generatedDir.resolve("version.properties")
    doFirst {
        generatedDir.mkdirs()
        versionFile.writeText("version=$packageVersion\n")
    }
    from(generatedDir) {
        include("version.properties")
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
    implementation(projects.betterlyrics)
    implementation(projects.simpmusic)
    implementation(projects.kizzy)

    implementation(compose.desktop.currentOs)
    implementation(libs.material3)
    implementation(libs.materialKolor)
    implementation(libs.compose.reorderable)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation(libs.json)
    implementation(libs.okhttp)
    implementation(libs.lastfm.java)
    implementation(libs.commons.codec)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.kuromoji.ipadic)
    implementation("org.xerial:sqlite-jdbc:3.51.2.0")
    implementation("com.google.api-client:google-api-client:2.8.1")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.39.0")
    implementation("com.google.http-client:google-http-client-gson:2.1.0")
    implementation(libs.google.api.services.drive)
    implementation(libs.google.auth.library.oauth2.http)

    // Ktor for lyrics and API calls
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)

    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$javafxPlatform")

    // VLC-based player
    implementation(libs.vlcj)

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.anitail.desktop.MainKt"
        nativeDistributions {
            includeAllModules = true
            val os = OperatingSystem.current()
            val formats = when {
                os.isMacOsX -> listOf(TargetFormat.Dmg)
                os.isWindows -> listOf(TargetFormat.Msi)
                else -> listOf(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            }
            targetFormats(*formats.toTypedArray())
            packageName = "AniTail"
            packageVersion = "1.0.3"
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
