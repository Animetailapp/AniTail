plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.anitail.paxsenix"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.timber)
    testImplementation(libs.junit)

    implementation(project(":betterlyrics"))

    coreLibraryDesugaring(libs.desugaring)
}
