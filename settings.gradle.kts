@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { setUrl("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
    plugins {
        id("org.jetbrains.compose") version "1.12.0-SNAPSHOT+v-mazunin-enable-lifecycle-and-nav3-for-1-12-0-alpha02"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        maven { setUrl("https://jitpack.io") }
        maven {
            setUrl("https://jogamp.org/deployment/maven/")
            content {
                includeGroupAndSubgroups("org.jogamp")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

rootProject.name = "AniTail"

val requestedTasks = gradle.startParameter.taskNames
val desktopOnlyByTask =
    requestedTasks.isNotEmpty() && requestedTasks.all { task ->
        task.contains("desktop", ignoreCase = true)
    }
val desktopOnlyByEnv =
    System.getenv("ANITAIL_DESKTOP_ONLY")?.equals("true", ignoreCase = true) == true
val includeAndroidProject = !(desktopOnlyByTask || desktopOnlyByEnv)

if (includeAndroidProject) {
    include(":android")
}
include(":common")
include(":desktop")
include(":innertube")
include(":kugou")
include(":lrclib")
include(":betterlyrics")
include(":simpmusic")
include(":shazamkit")
include(":paxsenix")

if (includeAndroidProject) {
    project(":android").projectDir = file("app")
}

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that AniTail and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in innertube/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.teamnewpipe:NewPipeExtractor")
//includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
//    }
//}
