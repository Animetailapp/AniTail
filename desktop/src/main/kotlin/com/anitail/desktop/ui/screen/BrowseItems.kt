package com.anitail.desktop.ui.screen

import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.BrowseResult

fun flattenBrowseItems(result: BrowseResult, hideExplicit: Boolean): List<YTItem> {
    val filtered = result.filterExplicit(hideExplicit)
    return filtered.items.flatMap { it.items }
}
