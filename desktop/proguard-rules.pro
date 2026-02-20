# Desktop Proguard rules.
# We don't obfuscate or optimize, and we suppress unresolved references from
# optional runtime-only libraries to keep the release build working.
-ignorewarnings
-dontwarn **
-dontshrink
-dontpreverify
-dontwarn javafx.**
-dontwarn com.sun.javafx.**
-dontwarn com.sun.webkit.**

# Keep Ktor serialization ServiceLoader provider class
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }
