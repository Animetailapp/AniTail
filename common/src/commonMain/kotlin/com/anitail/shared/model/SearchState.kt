package com.anitail.shared.model

data class SearchState(
    val isLoading: Boolean = false,
    val results: List<LibraryItem> = emptyList(),
    val errorMessage: String? = null,
)
