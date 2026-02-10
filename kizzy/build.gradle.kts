plugins {
    alias(libs.plugins.kotlin.serialization)
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    // Dependencia gestionada por root
    implementation(libs.ktor.client.encoding)
    implementation(libs.json)
    testImplementation(libs.junit)
}
