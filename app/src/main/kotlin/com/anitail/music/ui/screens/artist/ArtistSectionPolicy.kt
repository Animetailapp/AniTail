package com.anitail.music.ui.screens.artist

import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ArtistSection
import com.anitail.innertube.pages.LATEST_RELEASE_SECTION_KEY

internal const val LOCAL_LIBRARY_SECTION_KEY = "local_from_your_library"

internal data class ArtistSectionClassification(
    val librarySection: String?,
    val topTracksSection: String?,
    val videoSections: Set<String>,
)

internal fun classifyArtistSections(sections: List<ArtistSection>): ArtistSectionClassification {
    val librarySection =
        sections.firstOrNull { it.moreEndpoint?.browseId?.startsWith("MPLA") == true }?.title
            ?: sections.firstOrNull {
                it.title == LOCAL_LIBRARY_SECTION_KEY || it.title.equals("From your library", ignoreCase = true)
            }?.title

    val topTracksSection =
        sections.firstOrNull { section ->
            section.moreEndpoint?.browseId?.startsWith("VLOL") == true &&
                section.items.isNotEmpty() &&
                section.items.all { it is SongItem && !it.isVideoSong }
        }?.title
            ?: sections.firstOrNull { section ->
                section.title != LATEST_RELEASE_SECTION_KEY &&
                    section.title != librarySection &&
                    section.items.isNotEmpty() &&
                    section.items.all { it is SongItem && !it.isVideoSong } &&
                    section.items.any { (it as SongItem).album != null }
            }?.title
            ?: sections.firstOrNull { section ->
                section.title != LATEST_RELEASE_SECTION_KEY &&
                    section.title != librarySection &&
                    section.items.isNotEmpty() &&
                    section.items.all { it is SongItem && !it.isVideoSong }
            }?.title

    val videoSections =
        sections
            .filter { section ->
                section.title != librarySection &&
                    section.title != topTracksSection &&
                    section.items.isNotEmpty() &&
                    section.items.all { it is SongItem } &&
                    (section.items.any { (it as SongItem).isVideoSong } || section.moreEndpoint?.browseId?.startsWith("VLOL") == true)
            }
            .mapTo(mutableSetOf()) { it.title }

    return ArtistSectionClassification(
        librarySection = librarySection,
        topTracksSection = topTracksSection,
        videoSections = videoSections,
    )
}

internal fun List<YTItem>.isVideoShelf(allowUntypedSongs: Boolean = false): Boolean =
    isNotEmpty() &&
        all { it is SongItem } &&
        (any { (it as SongItem).isVideoSong } || (allowUntypedSongs && all { (it as SongItem).musicVideoType == null }))

internal fun artistCatalogTitles(sections: List<ArtistSection>): List<String> =
    sections
        .asSequence()
        .flatMap { it.items.asSequence() }
        .filter { it is SongItem || it is AlbumItem }
        .map { it.title }
        .toList()

