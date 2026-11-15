@file:Suppress("UnstableApiUsage")

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.Properties

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
}

compose {
    dependencyCompatibility {
        exclude("io.coil-kt.coil3")
    }
}

abstract class VerifyIosKuromojiPackagingTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val xcodeProject: RegularFileProperty

    @TaskAction
    fun verifyPackaging() {
        val resources = resourceDirectory.get().asFile
        val engine = File(resources, "kuromoji.js")
        val bootstrap = File(resources, "kuromoji-bootstrap.js")

        fun sha256(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }

        check(sha256(engine) == "ab0ac10f1c4a9b5e246e63e57b04ba14d3a6148cd7e8d62cb97a960d4f85a3e6")
        check(sha256(bootstrap) == "3a2f75400ef6f04301408701c2a2dbe23e614561df4aea3ab36504e9cca4ceb9")
        check(File(resources, "kuromoji-LICENSE-2.0.txt").isFile)
        check(File(resources, "kuromoji-NOTICE.md").isFile)
        check(resources.listFiles().orEmpty().none { it.name.endsWith(".dat.gz") })
        val project = xcodeProject.get().asFile.readText()
        check(
            listOf(
                "kuromoji.js in Resources",
                "kuromoji-bootstrap.js in Resources",
                "kuromoji-LICENSE-2.0.txt in Resources",
                "kuromoji-NOTICE.md in Resources",
            ).all { it in project },
        )
    }
}

val verifyIosKuromojiPackaging =
    tasks.register<VerifyIosKuromojiPackagingTask>("verifyIosKuromojiPackaging") {
        resourceDirectory.set(layout.projectDirectory.dir("src/iosMain/resources"))
        xcodeProject.set(layout.projectDirectory.file("../iosApp/iosApp.xcodeproj/project.pbxproj"))
    }

val runPackagingVerification =
    providers.gradleProperty("metrolist.verifyPackaging").map(String::toBooleanStrict).getOrElse(true)
tasks.matching { it.name.startsWith("compileKotlinIos") }.configureEach {
    if (runPackagingVerification) dependsOn(verifyIosKuromojiPackaging)
}

val appVersion = providers.gradleProperty("appVersion").get()
val innerTubeXVersion = providers.gradleProperty("innerTubeXVersion").get()
val bundledLanguageTags =
    file("src/commonMain/composeResources")
        .listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isDirectory && it.name.startsWith("values-") && it.name != "values-xml" }
        .map { directory ->
            val qualifier = directory.name.removePrefix("values-")
            val languageTag =
                if (qualifier.startsWith("b+")) {
                    qualifier.removePrefix("b+").replace('+', '-')
                } else {
                    qualifier.replace(Regex("-r(?=[A-Z]{2}(?:$|-))"), "-")
                }
            Locale.forLanguageTag(languageTag).toLanguageTag()
        }.filter { it.isNotBlank() && it != "und" }
        .distinct()
        .sorted()
        .toList()

kotlin {
    jvmToolchain(21)

    val isMacOs = System.getProperty("os.name").lowercase().contains("mac")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        if (isMacOs) {
            iosTarget.compilations.getByName("main").cinterops.create("playbackObservation") {
                definitionFile.set(project.file("src/iosMain/cinterop/playbackObservation.def"))
            }
        }
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            freeCompilerArgs += "-Xadd-light-debug=enable"
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
        }
        getByName("commonMain") {
            dependencies {
                api("com.github.MetrolistGroup.innertubex:innertubex:$innerTubeXVersion")
                api(libs.cmp.runtime)
                api(libs.cmp.foundation)
                api(libs.cmp.ui)
                api(libs.androidx.navigationevent.compose)
                api(libs.cmp.material3)
                api(libs.cmp.material3.adaptive)
                api(libs.cmp.material3.adaptive.navigation.suite)
                api(libs.cmp.components.resources)
                implementation(libs.cmp.ui.tooling.preview)
                implementation(libs.compose.reorderable)
                implementation(libs.swipe)
                api(libs.markdown.renderer)
                implementation(libs.markdown.renderer.m3)
                implementation(libs.markdown.renderer.coil3)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.encoding)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.serialization.json)

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)

                implementation(libs.datastore)

                implementation(libs.room3.runtime)

                implementation(libs.koin.core)

                api(libs.androidx.navigation3.runtime)

                api(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.datastore)

                implementation(libs.materialKolor)

                api(libs.shimmer)

                implementation("io.coil-kt.coil3:coil-compose:${libs.versions.coil3.get()}") {
                    exclude(group = "org.jetbrains.skiko", module = "skiko")
                }
                implementation(libs.coil3.network.core)
            }
        }
        getByName("iosMain") {
            dependencies {
                implementation("io.coil-kt.coil3:coil-network-ktor3:${libs.versions.coil3.get()}") {
                    exclude(group = "org.jetbrains.skiko", module = "skiko")
                }
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqlite.bundled)
                implementation("no.synth:kmp-zip:2.1.0")
            }
        }
    }

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
}

val prepareComposeResourceAliases =
    tasks.register<Sync>("prepareComposeResourceAliases") {
        val catalog = layout.projectDirectory.dir("src/commonMain/composeResources")
        from(catalog)
        mapOf("in" to "id", "iw" to "he").forEach { (legacy, modern) ->
            from(catalog.dir("values-$legacy")) { into("values-$modern") }
        }
        into(layout.buildDirectory.dir("generated/composeResourceAliases"))
    }

compose.resources {
    packageOfResClass = "com.anitail.shared.generated.resources"
    publicResClass = true
    generateResClass = always
    customDirectory("commonMain", layout.dir(prepareComposeResourceAliases.map { it.destinationDir }))
}

tasks.matching { it.name == "generateResourceAccessorsForCommonMain" }.configureEach {
    outputs.cacheIf { true }
}

val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val generateBuildConfig =
    tasks.register("generateBuildConfig") {
        val outputDir =
            layout.buildDirectory
                .dir("generated/source/config/main/kotlin")
                .get()
                .asFile

        inputs.files(localPropertiesFile)

        val lastFmKey =
            providers
                .gradleProperty("LASTFM_API_KEY")
                .filter { it.isNotEmpty() }
                .orElse(providers.environmentVariable("LASTFM_API_KEY").filter { it.isNotEmpty() })
                .orElse(providers.provider { localProperties.getProperty("LASTFM_API_KEY").orEmpty() }.filter { it.isNotEmpty() })
                .orElse(providers.provider { "" })
                .get()
                .trim()
        val lastFmSecret =
            providers
                .gradleProperty("LASTFM_SECRET")
                .filter { it.isNotEmpty() }
                .orElse(providers.environmentVariable("LASTFM_SECRET").filter { it.isNotEmpty() })
                .orElse(providers.provider { localProperties.getProperty("LASTFM_SECRET").orEmpty() }.filter { it.isNotEmpty() })
                .orElse(providers.provider { "" })
                .get()
                .trim()
        val isReleaseTask =
            gradle.startParameter.taskNames.any { name ->
                name.contains("Release", ignoreCase = true) ||
                    name.contains("Benchmark", ignoreCase = true) ||
                    (name.contains("package", ignoreCase = true) && !name.contains("Debug", ignoreCase = true)) ||
                    name.contains("Distributable", ignoreCase = true)
            }
        val metrolistDebugProp =
            providers
                .gradleProperty("metrolist.debug")
                .orNull
                ?.toBooleanStrictOrNull()
        val xcodeDebug =
            providers
                .environmentVariable("CONFIGURATION")
                .orNull
                ?.let { !it.equals("Release", ignoreCase = true) }
        val debugValue: Boolean = metrolistDebugProp ?: xcodeDebug ?: !isReleaseTask
        val lyprioBaseUrl =
            providers
                .gradleProperty("LYPRIO_BASE_URL")
                .filter { it.isNotEmpty() }
                .orElse(providers.environmentVariable("LYPRIO_BASE_URL").filter { it.isNotEmpty() })
                .orElse(providers.provider { localProperties.getProperty("LYPRIO_BASE_URL").orEmpty() }.filter { it.isNotEmpty() })
                .orElse(providers.provider { "" })
                .get()
                .trim()
        val lyprioHmacKey =
            providers
                .gradleProperty("LYPRIO_HMAC_KEY")
                .filter { it.isNotEmpty() }
                .orElse(providers.environmentVariable("LYPRIO_HMAC_KEY").filter { it.isNotEmpty() })
                .orElse(providers.provider { localProperties.getProperty("LYPRIO_HMAC_KEY").orEmpty() }.filter { it.isNotEmpty() })
                .orElse(providers.provider { "" })
                .get()
                .trim()
        require(lyprioBaseUrl.isBlank() == lyprioHmacKey.isBlank()) {
            "LYPRIO_BASE_URL and LYPRIO_HMAC_KEY must be configured together"
        }
        if (lyprioBaseUrl.isNotBlank()) {
            val uri = URI(lyprioBaseUrl)
            require(
                !uri.host.isNullOrBlank() &&
                    uri.rawUserInfo == null &&
                    uri.rawQuery == null &&
                    uri.rawFragment == null &&
                    (
                        (uri.scheme == "https" && (uri.port == -1 || uri.port == 443)) ||
                            (debugValue && uri.scheme == "http")
                    ),
            ) { "LYPRIO_BASE_URL must use HTTPS on port 443; HTTP is allowed only in debug builds" }
            require(Base64.getDecoder().decode(lyprioHmacKey).size == 32) {
                "LYPRIO_HMAC_KEY must be a base64-encoded 32-byte key"
            }
        }
        inputs.property("lastFmKey", lastFmKey)
        inputs.property("lastFmSecret", lastFmSecret)
        inputs.property("lyprioBaseUrl", lyprioBaseUrl)
        inputs.property("lyprioHmacKey", lyprioHmacKey)
        val sourceRevision =
            providers
                .gradleProperty("metrolist.sourceRevision")
                .orElse(providers.environmentVariable("GITHUB_SHA"))
                .map(String::trim)
                .filter { it.matches(Regex("[0-9a-fA-F]{40}")) }
                .orElse("")
                .get()
        inputs.property("appVersion", appVersion)
        inputs.property("sourceRevision", sourceRevision)
        inputs.property("bundledLanguageTags", bundledLanguageTags.joinToString(","))

        inputs.property("debug", debugValue)

        outputs.dir(outputDir)
        outputs.cacheIf { false }

        doLast {
            val configFile = outputDir.resolve("com/anitail/shared/BuildConfig.kt")
            configFile.parentFile.mkdirs()
            configFile.writeText(
                """
                package com.anitail.shared

                import kotlin.experimental.ExperimentalObjCRefinement
                import kotlin.native.HiddenFromObjC

                @OptIn(ExperimentalObjCRefinement::class)
                @HiddenFromObjC
                object BuildConfig {
                    const val LASTFM_API_KEY = "${inputs.properties["lastFmKey"]}"
                    const val LASTFM_SECRET = "${inputs.properties["lastFmSecret"]}"
                    const val LYPRIO_BASE_URL = "${inputs.properties["lyprioBaseUrl"]}"
                    const val LYPRIO_HMAC_KEY = "${inputs.properties["lyprioHmacKey"]}"
                    const val APP_VERSION = "${inputs.properties["appVersion"]}"
                    const val SOURCE_REVISION = "${inputs.properties["sourceRevision"]}"
                    const val BUNDLED_LANGUAGE_TAGS = "${inputs.properties["bundledLanguageTags"]}"
                    const val DEBUG = ${inputs.properties["debug"]}
                }
                """.trimIndent(),
            )
        }
    }

kotlin {
    sourceSets {
        getByName("commonMain") {
            kotlin.srcDir(generateBuildConfig)
        }
    }
}

dependencies {
    add("kspIosArm64", libs.room3.compiler)
    add("kspIosSimulatorArm64", libs.room3.compiler)
}

class RoomSchemaArgProvider(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDirectory: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("room.schemaLocation=${schemaDirectory.path}")
}

ksp {
    arg(RoomSchemaArgProvider(layout.projectDirectory.dir("schemas").asFile))
}

