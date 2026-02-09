package com.anitail.desktop.sync

fun shouldStartSync(ytmSync: Boolean, cookie: String?): Boolean {
    if (!ytmSync) return false
    return !cookie.isNullOrBlank()
}
