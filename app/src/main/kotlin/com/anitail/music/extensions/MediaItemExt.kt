package com.anitail.music.extensions

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import com.anitail.innertube.models.SongItem
import com.anitail.music.db.entities.Song
import com.anitail.music.models.MediaMetadata
import com.anitail.music.models.toMediaMetadata
import timber.log.Timber

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

fun Song.toMediaItem(): MediaItem {
    val resolvedUri =
        when {
            song.isLocal && !song.downloadUri.isNullOrEmpty() -> song.downloadUri
            !song.mediaStoreUri.isNullOrEmpty() -> song.mediaStoreUri
            else -> song.id
        }

    Timber.d(
        "MediaItemExt: Song.toMediaItem() id=%s isLocal=%s uri=%s",
        song.id,
        song.isLocal,
        resolvedUri,
    )

    return MediaItem
        .Builder()
        .setMediaId(song.id)
        .setUri(resolvedUri)
        .setCustomCacheKey(song.id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(song.title)
                .setSubtitle(song.artistName ?: artists.joinToString { it.name })
                .setArtist(song.artistName ?: artists.joinToString { it.name })
                .setArtworkUri(song.thumbnailUrl?.toUri())
                .setAlbumTitle(song.albumName)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putString("artwork_uri", song.thumbnailUrl)
                        putBoolean("is_local", song.isLocal)
                    },
                )
                .build(),
        ).build()
}

fun SongItem.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCustomCacheKey(id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(thumbnail.toUri())
                .setAlbumTitle(album?.name)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .build(),
        ).build()

fun MediaMetadata.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCustomCacheKey(id)
        .setTag(this)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(this.artistName ?: artists.joinToString { it.name })
                .setArtist(this.artistName ?: artists.joinToString { it.name })
                .setArtworkUri(thumbnailUrl?.toUri())
                .setAlbumTitle(album?.title)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .build(),
        ).build()
