import org.gradle.internal.os.OperatingSystem
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.kotlin.serialization)
}

val appVersion = "1.1.0"

kotlin {
    jvmToolchain(21)
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))
}

tasks.named<ProcessResources>("processResources") {
    from("../app/src/main/res") {
        include("values/**", "values-*/**")
    }
    val packageVersion = appVersion
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

val ffmpegVersion = "8.1.2-1.5.14"
val osArch = System.getProperty("os.arch").lowercase()
val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")
val javacppPlatform = when {
    OperatingSystem.current().isWindows -> if (isArm64) "windows-arm64" else "windows-x86_64"
    OperatingSystem.current().isMacOsX -> if (isArm64) "macosx-arm64" else "macosx-x86_64"
    else -> if (isArm64) "linux-arm64" else "linux-x86_64"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":innertube"))
    implementation(project(":betterlyrics"))
    implementation(project(":simpmusic"))
    implementation(project(":shazamkit"))


    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.13.10-alpha01+snapshot.v-mazunin-webpack-compilation-error-gradle-plugin-tests")
    implementation(libs.materialKolor) {
        exclude(group = "org.jetbrains.compose.material3")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.ui")
    }
    implementation(libs.compose.reorderable)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation(libs.json)
    implementation(libs.okhttp)
    implementation(libs.lastfm.java)
    implementation(libs.commons.codec)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.jmdns)
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("com.google.api-client:google-api-client:2.9.2")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.39.0")
    implementation("com.google.http-client:google-http-client-gson:2.2.0")
    implementation(libs.google.api.services.drive)
    implementation(libs.google.auth.library.oauth2.http)

    // Ktor for lyrics and API calls
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)

    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$javafxPlatform")

    // VLC-based player (fallback) & Bytedeco FFmpeg native engine
    implementation(libs.vlcj)
    implementation("org.bytedeco:ffmpeg:$ffmpegVersion")
    if (javacppPlatform != "windows-arm64") {
        implementation("org.bytedeco:ffmpeg:$ffmpegVersion:$javacppPlatform")
    }

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.anitail.desktop.MainKt"
        jvmArgs(
            "-XX:+UseZGC",
            "-XX:+ZGenerational",
            "-Xms128m",
            "-Xmx1024m",
            "-XX:SoftMaxHeapSize=512m",
            "-XX:+UseStringDeduplication",
            "-Dskiko.vsync.enabled=true",
        )
        val toolchains = project.extensions.getByType<JavaToolchainService>()
        val launcher = toolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        javaHome = launcher.get().metadata.installationPath.asFile.absolutePath
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
            packageVersion = appVersion
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



