package com.anitail.music.models

import androidx.compose.runtime.Immutable
import com.anitail.innertube.models.Album as InnerTubeAlbum
import com.anitail.innertube.models.Artist as InnerTubeArtist
import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.SongItem
import com.anitail.music.db.entities.Song
import com.anitail.music.db.entities.SongEntity
import com.anitail.music.playback.video.isLiveType
import com.anitail.music.playback.video.isOfficialVideoType
import com.anitail.music.playback.video.isUploadedType
import com.anitail.music.playback.video.isVideoType
import com.anitail.music.ui.utils.resize

import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val artistName: String? = null,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val isEpisode: Boolean = false,
    val musicVideoType: String? = null,
) {
    val isLocalTrack: Boolean
        get() = id.startsWith("LOCAL_") || id.startsWith("local_")

    val isVideoSong: Boolean
        get() = !isUploadedType(musicVideoType) && isVideoType(musicVideoType)

    val isOfficialMusicVideo: Boolean
        get() = isOfficialVideoType(musicVideoType)

    val isLive: Boolean
        get() = isLiveType(musicVideoType) && duration <= 0

    val canSwitchToMusicVideo: Boolean
        get() = !isVideoSong && !isUploadedType(musicVideoType) && !isLocalTrack && !isEpisode

    val canSwitchToAudioTrack: Boolean
        get() = isVideoSong && !isUploadedType(musicVideoType) && !isLocalTrack && !isEpisode

    @Serializable
    data class Artist(
        val id: String?,
        val name: String,
    )

    @Serializable
    data class Album(
        val id: String,
        val title: String,
    )

    fun toSongEntity() =
        SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = album?.id,
            albumName = album?.title,
            artistName = artistName,
            explicit = explicit,
            isEpisode = isEpisode,
        )

    fun toSongItem() =
        SongItem(
            id = id,
            title = title,
            artists =
                artists.map { InnerTubeArtist(name = it.name, id = it.id) }.ifEmpty {
                    listOfNotNull(artistName?.takeIf { it.isNotBlank() }?.let { InnerTubeArtist(name = it, id = null) })
                },
            album = album?.let { InnerTubeAlbum(name = it.title, id = it.id) },
            duration = duration.takeIf { it > 0 },
            musicVideoType = musicVideoType,
            thumbnail = thumbnailUrl.orEmpty(),
            explicit = explicit,
            setVideoId = setVideoId,
            isEpisode = isEpisode,
        )
}

fun Song.toMediaMetadata() =
    MediaMetadata(
        id = song.id,
        title = song.title,
        artists =
        (artists ?: emptyList()).map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name ?: "Unknown",
            )
        },
        artistName = song.artistName,
        duration = song.duration,
        thumbnailUrl = song.thumbnailUrl,
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.title ?: "Unknown",
            )
        } ?: song.albumId?.let { albumId ->
            MediaMetadata.Album(
                id = albumId,
                title = song.albumName.orEmpty(),
            )
        },
        isEpisode = song.isEpisode,
    )

fun SongItem.toMediaMetadata() =
    MediaMetadata(
        id = id ?: "",
        title = title ?: "Unknown",
        artists =
        (artists ?: emptyList()).map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name ?: "Unknown",
            )
        },
        artistName = null,
        duration = duration ?: -1,
        thumbnailUrl = thumbnail?.resize(544, 544),
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.name ?: "Unknown",
            )
        },
        explicit = explicit,
        setVideoId = setVideoId,
        isEpisode = isEpisode,
        musicVideoType = musicVideoType,
    )

fun EpisodeItem.toMediaMetadata() = asSongItem().toMediaMetadata()
