package com.anitail.shared.platform

actual object PlatformInfo {
    actual val name: String = System.getProperty("os.name") ?: "Desktop"
}
