package com.anitail.desktop.util

import org.json.JSONObject
import java.util.Base64
import java.util.UUID
import java.util.Locale

object SuperProperties {
    // Constants from research for Discord Android 314.13
    private const val CLIENT_VERSION = "314.13 - Stable"
    private const val CLIENT_BUILD_NUMBER = 314013
    private const val RELEASE_CHANNEL = "googleRelease"

    val osName: String by lazy {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> "Windows"
            os.contains("mac") -> "Mac OS X"
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> "Linux"
            else -> "Android" // Default to Android as Kizzy is designed for it
        }
    }

    val deviceName: String by lazy {
        System.getProperty("os.arch") ?: "Generic Device"
    }

    // Lazy loaded properties to avoid re-generating UUIDs
    val superProperties: JSONObject by lazy {
        JSONObject().apply {
            put("os", "Android") // Keeping Android to mimic the mobile app
            put("browser", "Discord Android")
            put("device", "Generic Android Device")
            put("system_locale", Locale.getDefault().toString())
            put("client_version", CLIENT_VERSION)
            put("release_channel", RELEASE_CHANNEL)
            put("device_vendor_id", UUID.randomUUID().toString())
            put("client_uuid", UUID.randomUUID().toString())
            put("client_launch_id", UUID.randomUUID().toString())
            put("os_version", System.getProperty("os.version") ?: "10")
            put("os_sdk_version", "30")
            put("client_build_number", CLIENT_BUILD_NUMBER)
            put("client_event_source", JSONObject.NULL)
            put("design_id", 0)
        }
    }

    val superPropertiesBase64: String by lazy {
        val jsonString = superProperties.toString()
        Base64.getEncoder().encodeToString(jsonString.toByteArray())
    }

    val userAgent: String by lazy {
        "Discord-Android/$CLIENT_BUILD_NUMBER;RNA"
    }
}
