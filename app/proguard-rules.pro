# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

## Kotlin Serialization
# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclasseswithmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclasseswithmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclasseswithmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn javax.servlet.ServletContainerInitializer
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.slf4j.impl.StaticLoggerBinder

## Rules for NewPipeExtractor
-keep class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

## Logging (does not affect Timber)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    ## Leave in release builds
    #public static int i(...);
    #public static int w(...);
    #public static int e(...);
}

# Generated automatically by the Android Gradle plugin.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Keep all classes within the kuromoji package
-keep class com.atilika.kuromoji.** { *; }

## Queue Persistence Rules
# Keep queue-related classes to prevent serialization issues in release builds
-keep class com.anitail.music.models.PersistQueue { *; }
-keep class com.anitail.music.playback.queues.** { *; }

# Keep serialization methods for queue persistence
-keepclassmembers class * implements java.io.Serializable {
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

# --- Google Cast / MediaRouter (release) ---
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-keep class androidx.mediarouter.** { *; }


## Media3 Protection Rules
# Protect Guava from conflicts with system versions
-keep class com.google.common.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
-keep class com.google.common.collect.** { *; }
-dontwarn com.google.common.**

# Protect Media3 from obfuscation
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Suppress warnings for com.google.re2j reported by R8
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

## Google API Client Libraries (Drive API, OAuth)
# Keep all Google API client model classes for JSON parsing
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
-keep class com.google.auth.** { *; }

# Keep GenericJson implementations used by Drive API
-keepclassmembers class * extends com.google.api.client.json.GenericJson {
    *;
}

# Keep Key annotation used for JSON field mapping
-keepattributes *Annotation*
-keep class com.google.api.client.util.Key

# Prevent obfuscation of classes that use @Key annotation
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}

# Google HTTP Client
-dontwarn com.google.api.client.http.**
-keep class com.google.api.client.http.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.util.** { *; }

# Google OAuth2
-keep class com.google.api.client.googleapis.** { *; }
-dontwarn com.google.api.client.googleapis.**

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
# ============================================
# Kotlin Core
# ============================================
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep interface kotlin.coroutines.** { *; }
-dontwarn kotlin.**

# ============================================
# Kotlin Coroutines
# ============================================
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ============================================
# Evitar que R8 convierta interfaces en clases
# (Esta es la causa principal del IncompatibleClassChangeError)
# ============================================
-keep,allowshrinking interface * {
    <methods>;
}

# ============================================
# Kotlin Metadata (necesario para reflexión)
# ============================================
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault

# ============================================
# Kotlin Serialization
# ============================================
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.anitail.music.**$$serializer { *; }
-keepclassmembers class com.anitail.music.** {
    *** Companion;
}
-keepclasseswithmembers class com.anitail.music.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================
# Media3 / ExoPlayer
# ============================================
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================
# Ktor
# ============================================
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-dontwarn io.ktor.**

# Vibra fingerprint library
-keep class com.metrolist.music.recognition.VibraSignature { *; }
-keepclassmembers class com.metrolist.music.recognition.VibraSignature {
    native <methods>;
}