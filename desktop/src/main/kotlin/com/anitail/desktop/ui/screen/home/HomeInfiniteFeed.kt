package com.anitail.desktop.ui.screen.home

import com.anitail.innertube.pages.HomePage

/**
 * Estado de paginación encadenada por chips para la Home (estilo Metrolist).
 * Cuando la continuación principal de FEmusic_home se agota (`continuation == null`),
 * continúa cargando automáticamente los chips disponibles (Energía, Relajarse, etc.)
 * y sus propias continuaciones, deduplicando secciones e ítems.
 */
data class HomeInfinitePaginationState(
    val usedChipKeys: Set<String> = emptySet(),
    val activeChipContinuation: String? = null,
    val requestedContinuations: Set<String> = emptySet(),
    val exhausted: Boolean = false,
)

internal fun HomePage.Chip.paginationKey(): String? {
    val params = endpoint?.params?.takeIf { it.isNotBlank() } ?: return null
    val browseId = endpoint?.browseId?.takeIf { it.isNotBlank() } ?: "FEmusic_home"
    return "$browseId:$params"
}

internal fun mergeUniqueHomeSections(
    existing: List<HomePage.Section>,
    incoming: List<HomePage.Section>,
): List<HomePage.Section> {
    if (incoming.isEmpty()) return existing
    val seenSectionKeys = existing.map { sectionSignature(it) }.toMutableSet()
    val result = existing.toMutableList()
    for (section in incoming) {
        val uniqueItems = section.items.distinctBy { it.id }
        if (uniqueItems.isEmpty()) continue
        val candidate = section.copy(items = uniqueItems)
        val sig = sectionSignature(candidate)
        if (seenSectionKeys.add(sig)) {
            result.add(candidate)
        }
    }
    return result
}

private fun sectionSignature(section: HomePage.Section): String {
    val ids = section.items.joinToString(",") { it.id }
    return "${section.title.trim().lowercase()}|$ids"
}

internal suspend fun loadNextHomePage(
    currentHomePage: HomePage,
    selectedChip: HomePage.Chip?,
    paginationState: HomeInfinitePaginationState,
    fetchHome: suspend (continuation: String?, params: String?) -> HomePage?,
): Pair<HomePage, HomeInfinitePaginationState>? {
    // 1. Continuación directa (feed principal o chip seleccionado manualmente)
    val directContinuation = currentHomePage.continuation
        ?.takeIf { it.isNotBlank() && it !in paginationState.requestedContinuations }

    if (directContinuation != null) {
        val next = fetchHome(directContinuation, null) ?: return null
        val updatedRequested = paginationState.requestedContinuations + directContinuation
        val nextToken = next.continuation?.takeUnless { it in updatedRequested }
        val mergedSections = mergeUniqueHomeSections(currentHomePage.sections, next.sections)
        return currentHomePage.copy(
            sections = mergedSections,
            continuation = nextToken,
        ) to paginationState.copy(requestedContinuations = updatedRequested)
    }

    // 2. Si el usuario filtró por un chip manualmente y ya no tiene continuación, no mezclamos otros chips
    if (selectedChip != null || paginationState.exhausted) {
        return null
    }

    // 3. Feed principal sin continuación: encadenar chips restantes y sus continuaciones
    var state = paginationState
    var accumulatedSections = currentHomePage.sections

    repeat(3) {
        val chipContinuation = state.activeChipContinuation
            ?.takeIf { it.isNotBlank() && it !in state.requestedContinuations }

        if (chipContinuation != null) {
            val next = fetchHome(chipContinuation, null)
            val updatedRequested = state.requestedContinuations + chipContinuation
            if (next == null) {
                state = state.copy(
                    activeChipContinuation = null,
                    requestedContinuations = updatedRequested,
                )
                return@repeat
            }
            val merged = mergeUniqueHomeSections(accumulatedSections, next.sections)
            val nextChipToken = next.continuation?.takeUnless { it in updatedRequested }
            val addedNew = merged.size > accumulatedSections.size
            accumulatedSections = merged
            state = state.copy(
                activeChipContinuation = nextChipToken,
                requestedContinuations = updatedRequested,
            )
            if (addedNew) {
                return currentHomePage.copy(
                    sections = accumulatedSections,
                    continuation = null,
                ) to state
            }
            return@repeat
        }

        val remainingChips = currentHomePage.chips.orEmpty().filter { chip ->
            val key = chip.paginationKey()
            key != null && key !in state.usedChipKeys
        }
        val nextChip = remainingChips.randomOrNull()
        if (nextChip == null) {
            state = state.copy(exhausted = true)
            return if (accumulatedSections.size > currentHomePage.sections.size) {
                currentHomePage.copy(sections = accumulatedSections, continuation = null) to state
            } else {
                null
            }
        }

        val chipKey = nextChip.paginationKey()!!
        val chipParams = nextChip.endpoint?.params
        state = state.copy(usedChipKeys = state.usedChipKeys + chipKey)

        val chipPage = fetchHome(null, chipParams) ?: return@repeat
        val merged = mergeUniqueHomeSections(accumulatedSections, chipPage.sections)
        val nextChipToken = chipPage.continuation?.takeUnless { it in state.requestedContinuations }
        val addedNew = merged.size > accumulatedSections.size
        accumulatedSections = merged
        state = state.copy(activeChipContinuation = nextChipToken)

        if (addedNew) {
            return currentHomePage.copy(
                sections = accumulatedSections,
                continuation = null,
            ) to state
        }
    }

    return if (accumulatedSections.size > currentHomePage.sections.size) {
        currentHomePage.copy(sections = accumulatedSections, continuation = null) to state
    } else {
        null
    }
}

