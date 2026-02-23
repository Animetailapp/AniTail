plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

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
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.material3)
                implementation(libs.compose.ui)
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
