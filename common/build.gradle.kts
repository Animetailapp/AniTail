plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)

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
        getByName("commonMain") {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                api(libs.ktor.serialization.json)
            }
        }
        getByName("androidMain") {
            dependencies {
                implementation(project(":innertube"))
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.material3)
                implementation(libs.compose.ui)
            }
        }
        getByName("desktopMain") {
            dependencies {
                implementation(project(":innertube"))
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.material3)
                implementation(libs.compose.ui)
            }
        }
    }
}