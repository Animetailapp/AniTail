plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        val commonTest by getting
        val androidMain by getting {
            dependencies {
                implementation(projects.innertube)
            }
        }
        val androidUnitTest by getting
        val desktopMain by getting {
            dependencies {
                implementation(projects.innertube)
            }
        }
        val desktopTest by getting
    }
}

android {
    namespace = "com.anitail.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
    buildFeatures {
        compose = true
    }
}
