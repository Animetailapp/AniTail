package com.anitail.desktop.ui.screen.home

import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.BrowseEndpoint
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.HomePage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeInfiniteFeedTest {
    private fun fakeSong(id: String) = SongItem(
        id = id,
        title = "Song $id",
        artists = listOf(Artist("Artist", null)),
        thumbnail = "https://example.com/$id.jpg",
        explicit = false,
    )

    private fun fakeSection(title: String, songIds: List<String>) = HomePage.Section(
        title = title,
        label = null,
        thumbnail = null,
        endpoint = null,
        items = songIds.map(::fakeSong),
    )

    private fun fakeChip(title: String, params: String) = HomePage.Chip(
        title = title,
        endpoint = BrowseEndpoint(browseId = "FEmusic_home", params = params),
        deselectEndPoint = null,
    )

    @Test
    fun loadsDirectContinuationAndDeduplicatesSections() = runBlocking {
        val initial = HomePage(
            chips = listOf(fakeChip("Relax", "p_relax")),
            sections = listOf(fakeSection("Quick picks", listOf("s1", "s2"))),
            continuation = "cont_1",
        )

        val result = loadNextHomePage(
            currentHomePage = initial,
            selectedChip = null,
            paginationState = HomeInfinitePaginationState(),
        ) { cont, _ ->
            assertEquals("cont_1", cont)
            HomePage(
                chips = null,
                sections = listOf(
                    fakeSection("Quick picks", listOf("s1", "s2")), // duplicate
                    fakeSection("Recommended", listOf("s3", "s4")),
                ),
                continuation = "cont_2",
            )
        }

        assertNotNull(result)
        val (updatedPage, updatedState) = result
        assertEquals(2, updatedPage.sections.size)
        assertEquals("Recommended", updatedPage.sections[1].title)
        assertEquals("cont_2", updatedPage.continuation)
        assertTrue("cont_1" in updatedState.requestedContinuations)
    }

    @Test
    fun chainsRemainingChipsAndTheirContinuationsAfterMainFeedEnds() = runBlocking {
        val chip1 = fakeChip("Energize", "p_energize")
        val initial = HomePage(
            chips = listOf(chip1),
            sections = listOf(fakeSection("Main", listOf("s1"))),
            continuation = null,
        )

        val firstChipLoad = loadNextHomePage(
            currentHomePage = initial,
            selectedChip = null,
            paginationState = HomeInfinitePaginationState(),
        ) { cont, params ->
            assertNull(cont)
            assertEquals("p_energize", params)
            HomePage(
                chips = null,
                sections = listOf(fakeSection("Workout Mix", listOf("s2"))),
                continuation = "chip_cont_1",
            )
        }

        assertNotNull(firstChipLoad)
        val (pageAfterChip, stateAfterChip) = firstChipLoad
        assertEquals(2, pageAfterChip.sections.size)
        assertEquals("chip_cont_1", stateAfterChip.activeChipContinuation)

        val chipContinuationLoad = loadNextHomePage(
            currentHomePage = pageAfterChip,
            selectedChip = null,
            paginationState = stateAfterChip,
        ) { cont, params ->
            assertEquals("chip_cont_1", cont)
            assertNull(params)
            HomePage(
                chips = null,
                sections = listOf(fakeSection("More Energy", listOf("s3"))),
                continuation = null,
            )
        }

        assertNotNull(chipContinuationLoad)
        val (pageAfterCont, stateAfterCont) = chipContinuationLoad
        assertEquals(3, pageAfterCont.sections.size)
        assertNull(stateAfterCont.activeChipContinuation)
    }

    @Test
    fun doesNotChainOtherChipsWhenChipIsManuallySelected() = runBlocking {
        val selected = fakeChip("Focus", "p_focus")
        val initial = HomePage(
            chips = listOf(selected, fakeChip("Party", "p_party")),
            sections = listOf(fakeSection("Focus Mix", listOf("s1"))),
            continuation = null,
        )

        val result = loadNextHomePage(
            currentHomePage = initial,
            selectedChip = selected,
            paginationState = HomeInfinitePaginationState(),
        ) { _, _ -> error("Should not fetch other chips when a chip is selected") }

        assertNull(result)
    }
}

