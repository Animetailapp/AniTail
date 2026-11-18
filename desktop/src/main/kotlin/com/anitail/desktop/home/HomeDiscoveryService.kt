package com.anitail.desktop.home

import com.anitail.desktop.YouTube
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.model.CommunityPlaylistItem
import com.anitail.desktop.model.DailyDiscoverItem
import com.anitail.desktop.model.SimilarRecommendation
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.HomePage
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object HomeDiscoveryService {

    suspend fun loadDailyDiscover(
        database: DesktopDatabase,
        hideExplicit: Boolean = false,
    ): List<DailyDiscoverItem> = withContext(Dispatchers.IO) {
        val allSongs = database.songs.first()
        val likedOrPlayed = allSongs.filter { it.liked || it.totalPlayTime > 10_000L }
            .ifEmpty { allSongs }
        if (likedOrPlayed.isEmpty()) return@withContext emptyList()

        val seeds = likedOrPlayed.shuffled().distinctBy { it.id }.take(3)
        val results = coroutineScope {
            seeds.map { seed ->
                async {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                        ?: return@async null
                    val relatedPage = YouTube.related(endpoint).getOrNull() ?: return@async null
                    val recommendation = relatedPage.songs
                        .firstOrNull { song -> (!hideExplicit || !song.explicit) && song.id != seed.id }
                        ?: return@async null

                    DailyDiscoverItem(
                        seed = seed,
                        recommendation = recommendation,
                        relatedEndpoint = endpoint,
                    )
                }
            }.awaitAll().filterNotNull()
        }

        results.distinctBy { it.recommendation.id }
    }

    suspend fun loadCommunityPlaylists(
        database: DesktopDatabase,
    ): List<CommunityPlaylistItem> = withContext(Dispatchers.IO) {
        val allSongs = database.songs.first()
        val songSeeds = allSongs.sortedByDescending { it.totalPlayTime }.take(5).shuffled().take(2)
        val blockedAuthors = setOf("YouTube Music", "YouTube", "Playlist")

        val candidatePlaylists = coroutineScope {
            songSeeds.map { seed ->
                async {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                        ?: return@async emptyList()
                    YouTube.related(endpoint).getOrNull()?.playlists.orEmpty().filter { playlist ->
                        val authorName = playlist.author?.name.orEmpty()
                        authorName.isNotBlank() && authorName !in blockedAuthors
                    }
                }
            }.awaitAll().flatten()
        }

        val results = coroutineScope {
            candidatePlaylists.distinctBy { it.id }.shuffled().take(6).map { playlist ->
                async {
                    val page = YouTube.playlist(playlist.id).getOrNull() ?: return@async null
                    val songs = page.songs.take(10)
                    if (songs.isEmpty()) return@async null
                    CommunityPlaylistItem(
                        playlist = playlist.copy(songCountText = page.playlist.songCountText ?: playlist.songCountText),
                        songs = songs,
                    )
                }
            }.awaitAll().filterNotNull()
        }

        results.shuffled()
    }

    fun buildSpeedDialItems(
        quickPicks: List<LibraryItem>,
        keepListening: List<LibraryItem>,
        homePage: HomePage?,
        similarRecommendations: List<SimilarRecommendation>,
        dailyDiscover: List<DailyDiscoverItem>,
        communityPlaylists: List<CommunityPlaylistItem>,
    ): List<YTItem> {
        val localItems = (quickPicks + keepListening).map { libraryItem ->
            SongItem(
                id = libraryItem.id,
                title = libraryItem.title,
                artists = listOf(com.anitail.innertube.models.Artist(libraryItem.artist, null)),
                thumbnail = libraryItem.artworkUrl.orEmpty(),
            )
        }
        val homeItems = homePage?.sections?.flatMap { it.items }.orEmpty()
        val similarItems = similarRecommendations.flatMap { it.items }
        val dailyItems = dailyDiscover.map { it.recommendation }
        val communityItems = communityPlaylists.map { it.playlist }

        return (localItems + homeItems + similarItems + dailyItems + communityItems)
            .distinctBy { it.id }
            .take(60)
    }
}
