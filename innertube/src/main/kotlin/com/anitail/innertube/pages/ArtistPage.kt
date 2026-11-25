package com.anitail.innertube.pages

import com.anitail.innertube.models.Album
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.BrowseEndpoint
import com.anitail.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.anitail.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_USER_CHANNEL
import com.anitail.innertube.models.MusicCarouselShelfRenderer
import com.anitail.innertube.models.MusicMultiRowListItemRenderer
import com.anitail.innertube.models.MusicResponsiveListItemRenderer
import com.anitail.innertube.models.MusicShelfRenderer
import com.anitail.innertube.models.MusicTwoRowItemRenderer
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.PodcastItem
import com.anitail.innertube.models.Run
import com.anitail.innertube.models.SectionListRenderer
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.models.oddElements
import com.anitail.innertube.models.splitBySeparator
import com.anitail.innertube.utils.parseTime

const val LATEST_RELEASE_SECTION_KEY = "latest_release"

private val RELEASE_YEAR_REGEX = Regex("^\\d{4}$")
private val ALBUM_TYPE_LABELS = setOf(
    "album",
    "single",
    "ep",
    "audiobook",
    "álbum",
    "sencillo",
    "audiolibro",
    "singolo",
)
private val PLAY_COUNT_REGEX = Regex(
    "^\\d+(?:[.,\\s\\u00A0]\\d+)*\\s*(?:[KMBkmb]|mln|mio|mil|mrd|тыс|млн|млрд|трлн|ألف|مليون|مليار|[万萬亿億兆])?(?:\\s+.*)?$",
    RegexOption.IGNORE_CASE,
)

data class ArtistSection(
    val title: String,
    val items: List<YTItem>,
    val moreEndpoint: BrowseEndpoint?,
)

data class ArtistPage(
    val artist: ArtistItem,
    val sections: List<ArtistSection>,
    val description: String?,
    val subscriberCountText: String?,
    val monthlyListenerCount: String? = null,
    val descriptionRuns: List<Run>? = null,
    val viewCount: String? = null,
    val banner: String? = null,
    val isSubscribed: Boolean = false,
) {
    companion object {
        fun artistSectionsWithLatestRelease(
            artist: ArtistItem,
            sections: List<ArtistSection>,
        ): List<ArtistSection> {
            val cleanedSections = sections.filterNot { it.title == LATEST_RELEASE_SECTION_KEY }
            val latest = cleanedSections
                .asSequence()
                .flatMap { it.items.asSequence() }
                .filterIsInstance<AlbumItem>()
                .filter { album ->
                    val albumArtists = album.artists
                    albumArtists.isNullOrEmpty() ||
                        albumArtists.any { it.id == artist.id || it.name == artist.title }
                }
                .maxWithOrNull(compareBy { it.year })
                ?: return cleanedSections

            val remaining = cleanedSections.filterNot { section ->
                val soleAlbum = section.items.singleOrNull() as? AlbumItem
                soleAlbum != null && soleAlbum.browseId == latest.browseId
            }

            return listOf(
                ArtistSection(
                    title = LATEST_RELEASE_SECTION_KEY,
                    items = listOf(latest),
                    moreEndpoint = null,
                )
            ) + remaining
        }

        fun fromSectionListRendererContent(content: SectionListRenderer.Content): ArtistSection? {
            return when {
                content.musicShelfRenderer != null -> fromMusicShelfRenderer(content.musicShelfRenderer)
                content.musicCarouselShelfRenderer != null -> fromMusicCarouselShelfRenderer(content.musicCarouselShelfRenderer)
                else -> null
            }
        }

        private fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): ArtistSection? {
            val items = renderer.contents?.mapNotNull { content ->
                content.musicResponsiveListItemRenderer?.let { fromMusicResponsiveListItemRenderer(it) }
                    ?: content.musicMultiRowListItemRenderer?.let { fromMusicMultiRowListItemRenderer(it) }
            }?.ifEmpty { null } ?: return null
            return ArtistSection(
                title = renderer.title?.runs?.firstOrNull()?.text ?: "",
                items = items,
                moreEndpoint = renderer.title?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint
                    ?: renderer.bottomEndpoint?.browseEndpoint
                    ?: renderer.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint
            )
        }

        private fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): ArtistSection? {
            return ArtistSection(
                title = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: return null,
                items = renderer.contents.mapNotNull { content ->
                    content.musicTwoRowItemRenderer?.let { twoRowRenderer ->
                        fromMusicTwoRowItemRenderer(twoRowRenderer)
                    } ?: content.musicResponsiveListItemRenderer?.let { listItemRenderer ->
                        fromMusicResponsiveListItemRenderer(listItemRenderer)
                    } ?: content.musicMultiRowListItemRenderer?.let { multiRowRenderer ->
                        fromMusicMultiRowListItemRenderer(multiRowRenderer)
                    }
                }.ifEmpty { null } ?: return null,
                moreEndpoint = renderer.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint
            )
        }

        private fun fromMusicMultiRowListItemRenderer(renderer: MusicMultiRowListItemRenderer): SongItem? {
            val videoId = renderer.onTap?.watchEndpoint?.videoId
                ?: renderer.title?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
                ?: renderer.menu?.menuRenderer?.items?.firstNotNullOfOrNull {
                    it.menuNavigationItemRenderer?.navigationEndpoint?.watchEndpoint?.videoId
                }
                ?: return null
            val title = renderer.title?.runs?.firstOrNull()?.text ?: return null
            return SongItem(
                id = videoId,
                title = title,
                artists = renderer.subtitle?.runs?.mapNotNull { run ->
                    run.navigationEndpoint?.browseEndpoint?.let { endpoint ->
                        Artist(name = run.text, id = endpoint.browseId)
                    }
                } ?: emptyList(),
                album = null,
                duration = null,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: "",
                isEpisode = true,
            )
        }

        private fun isPlayCountText(text: String): Boolean {
            val lower = text.lowercase()
            if (lower.contains("plays") ||
                lower.contains("views") ||
                lower.contains("listeners") ||
                lower.contains("audience") ||
                lower.contains("reproducciones") ||
                lower.contains("visualizaciones") ||
                lower.contains("ascolti") ||
                lower.contains("visualizzazioni") ||
                lower.contains("wiedergaben") ||
                lower.contains("aufrufe") ||
                lower.contains("vues") ||
                lower.contains("lectures")
            ) {
                return true
            }
            if (RELEASE_YEAR_REGEX.matches(text)) return false
            return text.firstOrNull()?.isDigit() == true && PLAY_COUNT_REGEX.matches(text)
        }

        private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val metadataColumns = renderer.flexColumns.drop(1)
            val linkedArtists = mutableListOf<Artist>()
            var album: Album? = null
            var playCountText: String? = null
            var duration: Int? = renderer.fixedColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                ?.text?.parseTime()

            metadataColumns.forEach { column ->
                val runs = column.musicResponsiveListItemFlexColumnRenderer.text?.runs.orEmpty()
                runs.forEach { run ->
                    val browseEndpoint = run.navigationEndpoint?.browseEndpoint
                    val browseId = browseEndpoint?.browseId
                    when {
                        browseId != null && (browseEndpoint.isAlbumEndpoint || browseId.startsWith("MPREb") || browseId.contains("release_detail")) -> {
                            if (album == null) album = Album(name = run.text, id = browseId)
                        }

                        browseId != null && (browseEndpoint.isArtistEndpoint || browseEndpoint.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_USER_CHANNEL || browseId.startsWith("UC")) -> {
                            linkedArtists += Artist(name = run.text, id = browseId)
                        }

                        browseId != null -> {
                            // Ambiguous browse link in secondary columns: treat trailing album column as album if linkedArtists already present
                            if (column === renderer.flexColumns.lastOrNull() && linkedArtists.isNotEmpty() && album == null) {
                                album = Album(name = run.text, id = browseId)
                            }
                        }

                        else -> {
                            run.text.split(" • ").forEach { rawPart ->
                                val part = rawPart.trim()
                                if (part.isNotEmpty() && part !in setOf("•", "&", ",")) {
                                    val parsedDuration = if (':' in part) part.parseTime() else null
                                    when {
                                        parsedDuration != null -> if (duration == null) duration = parsedDuration
                                        isPlayCountText(part) -> if (playCountText == null) playCountText = part
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fallback to first segment of flexColumn(1) if no linked artist runs were found
            val artists = linkedArtists.ifEmpty {
                val firstSegment = renderer.flexColumns
                    .getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.splitBySeparator()
                    ?.firstOrNull()
                    ?.oddElements()
                    .orEmpty()
                firstSegment
                    .filterNot { run ->
                        val t = run.text.trim()
                        t.isBlank() || isPlayCountText(t) || (':' in t && t.parseTime() != null)
                    }
                    .map { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) }
            }

            // Extract library tokens using the new method that properly handles multiple toggle items
            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

            return SongItem(
                id = renderer.playlistItemData?.videoId ?: return null,
                title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text ?: return null,
                artists = artists,
                album = album,
                duration = duration,
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
                playCountText = playCountText,
            )
        }

        private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            return when {
                renderer.isSong -> {
                    val subtitleRuns = renderer.subtitle?.runs?.oddElements() ?: return null
                    SongItem(
                        id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = subtitleRuns.filter {
                            it.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("UC") == true ||
                                it.navigationEndpoint?.browseEndpoint != null
                        }.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId
                            )
                        }.ifEmpty {
                            subtitleRuns.firstOrNull()?.let {
                                if (!isPlayCountText(it.text.trim())) {
                                    listOf(Artist(name = it.text, id = null))
                                } else {
                                    emptyList()
                                }
                            } ?: emptyList()
                        },
                        album = null,
                        duration = null,
                        musicVideoType = renderer.musicVideoType,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                        playCountText = renderer.subtitle?.runs
                            ?.flatMap { it.text.split(" • ") }
                            ?.map { it.trim() }
                            ?.firstOrNull { isPlayCountText(it) },
                    )
                }

                renderer.isAlbum -> {
                    val subtitleRuns = renderer.subtitle?.runs.orEmpty()
                    val splitTokens = subtitleRuns.flatMap { run ->
                        if (run.navigationEndpoint?.browseEndpoint == null && " • " in run.text) {
                            run.text.split(" • ").map { part -> Run(part.trim(), null) }
                        } else {
                            listOf(run)
                        }
                    }
                    val year = splitTokens.firstNotNullOfOrNull { run ->
                        run.text.trim().takeIf { RELEASE_YEAR_REGEX.matches(it) }?.toIntOrNull()
                    } ?: subtitleRuns.lastOrNull()?.text?.trim()?.toIntOrNull()
                    val albumType = splitTokens.firstNotNullOfOrNull { run ->
                        val text = run.text.trim()
                        if (run.navigationEndpoint?.browseEndpoint == null && text.lowercase() in ALBUM_TYPE_LABELS) {
                            text
                        } else {
                            null
                        }
                    }
                    val artists = splitTokens.mapNotNull { run ->
                        val browseId = run.navigationEndpoint?.browseEndpoint?.browseId
                        val pageType = run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
                        if (browseId != null && (pageType == MUSIC_PAGE_TYPE_ARTIST || pageType == MUSIC_PAGE_TYPE_USER_CHANNEL || browseId.startsWith("UC"))) {
                            Artist(name = run.text, id = browseId)
                        } else null
                    }.takeIf { it.isNotEmpty() }

                    AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.anyWatchEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = artists,
                        year = year,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                        type = albumType,
                    )
                }

                renderer.isPlaylist -> {
                    // Playlist from YouTube Music
                    PlaylistItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL") ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        author = renderer.subtitle?.runs?.firstOrNull()?.let {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId
                            )
                        },
                        songCountText = renderer.subtitle?.runs?.lastOrNull()?.text
                            ?.takeIf { it != renderer.subtitle.runs.firstOrNull()?.text },
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        playEndpoint = renderer.thumbnailOverlay
                            ?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.anyWatchEndpoint,
                        shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.anyWatchEndpoint,
                        radioEndpoint = renderer.menu?.menuRenderer?.items?.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.anyWatchEndpoint
                    )
                }

                renderer.isArtist -> {
                    ArtistItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        title = renderer.title.runs?.lastOrNull()?.text ?: return null,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        channelId = renderer.menu?.menuRenderer?.items?.find {
                            it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType == "SUBSCRIBE"
                        }?.toggleMenuServiceItemRenderer?.defaultServiceEndpoint?.subscribeEndpoint?.channelIds?.firstOrNull(),
                        shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.anyWatchEndpoint,
                        radioEndpoint = renderer.menu?.menuRenderer?.items?.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.anyWatchEndpoint,
                    )
                }

                renderer.isPodcast -> {
                    val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)
                    PodcastItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        author = renderer.subtitle?.runs?.firstOrNull()?.let {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId
                            )
                        },
                        episodeCountText = renderer.subtitle?.runs?.lastOrNull()?.text,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl(),
                        playEndpoint = renderer.thumbnailOverlay
                            ?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint,
                        shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                        libraryAddToken = libraryTokens.addToken,
                        libraryRemoveToken = libraryTokens.removeToken,
                    )
                }

                else -> null
            }
        }
    }
}
