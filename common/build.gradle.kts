plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Must match org.jetbrains.compose plugin version from settings.gradle.kts
val composeVersion = "1.11.0-alpha03"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.anitail.shared"
        compileSdk = 36
        minSdk = 23
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
                implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
                implementation("org.jetbrains.compose.material3:material3:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui:$composeVersion")
                api(libs.ktor.serialization.json)
            }
        }
        val commonTest by getting
        val androidMain by getting {
            dependencies {
                implementation(projects.innertube)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(projects.innertube)
            }
        }
        val desktopTest by getting
    }
}
