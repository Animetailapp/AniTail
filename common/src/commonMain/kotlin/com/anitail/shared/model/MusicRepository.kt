package com.anitail.shared.model

interface MusicRepository {
    suspend fun search(query: String): List<LibraryItem>

    fun initialLibrary(): List<LibraryItem>
}
