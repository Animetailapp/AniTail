package com.anitail.desktop.auth

fun normalizeDataSyncId(raw: String?): String? {
    val value = raw?.takeIf { it.isNotBlank() } ?: return null
    return value.takeIf { !it.contains("||") }
        ?: value.takeIf { it.endsWith("||") }?.substringBefore("||")
        ?: value.substringAfter("||")
}
