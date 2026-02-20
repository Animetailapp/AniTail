package com.anitail.shared.platform

import android.os.Build

actual object PlatformInfo {
    actual val name: String = "Android ${Build.VERSION.RELEASE}"
}
