package com.anitail.desktop.ui.screen.library

data class ItemWrapper<T>(
    val item: T,
    var isSelected: Boolean = false,
)
