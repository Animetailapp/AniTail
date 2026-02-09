package com.anitail.desktop.model

import com.anitail.innertube.models.YTItem
import com.anitail.shared.model.LibraryItem

/**
 * Modelo para recomendaciones similares en Desktop.
 * Similar a SimilarRecommendation de Android pero adaptado para Desktop.
 */
data class SimilarRecommendation(
    val title: String,
    val thumbnailUrl: String?,
    val isArtist: Boolean,
    val items: List<YTItem>,
    val sourceItem: LibraryItem? = null,
)

/**
 * Modelo para una sección dinámica de la página de inicio.
 */
data class HomeSection(
    val title: String,
    val label: String? = null,
    val thumbnailUrl: String? = null,
    val isArtist: Boolean = false,
    val items: List<LibraryItem>,
)
