package com.anitail.shared.viewmodels

import com.anitail.shared.model.AgentInfo
import com.anitail.shared.model.LyricsEntry
import com.anitail.shared.repository.LyricsProviderSearch

data class LyricsUiState(
    val songId: String? = null,
    val lyrics: List<LyricsEntry> = emptyList(),
    val rawLyrics: String? = null,
    val songwriters: List<String> = emptyList(),
    val agents: Map<String, AgentInfo> = emptyMap(),
    val provider: String? = null,
    val queryTitle: String? = null,
    val queryArtist: String? = null,
    val timingOffsetMs: Int = 0,
    val isLoading: Boolean = false,
    val providerSearch: LyricsProviderSearch? = null,
    val manualAiTranslationEnabled: Boolean = false,
)
