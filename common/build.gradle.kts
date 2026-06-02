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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                api(libs.ktor.serialization.json)
            }
        }
        val commonTest by getting
        val androidMain by getting {
            dependencies {
                implementation(project(":innertube"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(project(":innertube"))
            }
        }
        val desktopTest by getting
    }
}
