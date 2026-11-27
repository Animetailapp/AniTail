package com.anitail.shared.db

fun interface OfflineSongAvailability {
    suspend fun songIds(): Set<String>
}
