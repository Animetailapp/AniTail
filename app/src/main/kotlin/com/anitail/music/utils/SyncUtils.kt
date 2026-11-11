package com.anitail.music.utils

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.utils.completed
import com.anitail.music.constants.GoogleDriveFolderIdKey
import com.anitail.music.constants.LastCloudSyncKey
import com.anitail.music.constants.LastCloudSyncLocalFingerprintKey
import com.anitail.music.constants.LastCloudSyncRemoteMd5Key
import com.anitail.music.constants.LastCloudSyncRemoteModifiedTimeKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.ArtistEntity
import com.anitail.music.db.entities.PlaylistEntity
import com.anitail.music.db.entities.PlaylistSongMap
import com.anitail.music.db.entities.SongEntity
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.R
import com.anitail.music.constants.AccountChannelHandleKey
import com.anitail.music.constants.AccountEmailKey
import com.anitail.music.constants.AccountImageUrlKey
import com.anitail.music.constants.AccountNameKey
import com.anitail.music.constants.DataSyncIdKey
import com.anitail.music.constants.DiscordAvatarUrlKey
import com.anitail.music.constants.DiscordStateTemplateKey
import com.anitail.music.constants.DiscordUserStatusKey
import com.anitail.music.constants.DiscordNameKey
import com.anitail.music.discord.DiscordTokenStore
import com.anitail.music.constants.DiscordUsernameKey
import com.anitail.music.constants.InnerTubeCookieKey
import com.anitail.music.constants.LastFmSessionKey
import com.anitail.music.constants.LastFmUsernameKey
import com.anitail.music.constants.ProxyPasswordKey
import com.anitail.music.constants.ProxyUrlKey
import com.anitail.music.constants.ProxyUsernameKey
import com.anitail.music.constants.SpotifyAccessTokenKey
import com.anitail.music.constants.SpotifyRefreshTokenKey
import com.anitail.music.constants.VisitorDataKey
import com.anitail.music.db.DatabaseMerger
import com.anitail.music.db.InternalDatabase
import com.anitail.music.db.entities.Event
import com.anitail.music.models.MediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class SyncUtils
@Inject
constructor(
    private val database: MusicDatabase,
    private val lastFmService: LastFmService,
    private val googleDriveSyncManager: GoogleDriveSyncManager,
    private val databaseMerger: DatabaseMerger,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CLOUD_SYNC_REMOTE_BACKUP_NAME = "AniTail_CloudSync_Backup.zip"
        const val SYNC_THROTTLE_MS = 30 * 60 * 1000L // 30 minutes
        const val SYNC_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes for Drive download/merge/upload

        /** Preference keys written by the sync itself; excluded from the local fingerprint. */
        private val SYNC_BOOKKEEPING_PREF_NAMES = setOf(
            LastCloudSyncKey.name,
            LastCloudSyncRemoteModifiedTimeKey.name,
            LastCloudSyncRemoteMd5Key.name,
            LastCloudSyncLocalFingerprintKey.name,
            GoogleDriveFolderIdKey.name,
        )

        /** Cheap aggregate query over the synced tables (all served by PK / rowid). */
        private val LOCAL_FINGERPRINT_QUERY = """
            SELECT
                (SELECT COUNT(*) || '-' || IFNULL(MAX(rowid), 0) FROM event),
                (SELECT COUNT(*) || '-' || IFNULL(MAX(likedDate), '') FROM song WHERE liked = 1),
                (SELECT COUNT(*) || '-' || IFNULL(MAX(inLibrary), '') FROM song WHERE inLibrary IS NOT NULL),
                (SELECT COUNT(*) || '-' || IFNULL(MAX(rowid), 0) || '-' || IFNULL(MAX(lastUpdateTime), '') FROM playlist),
                (SELECT COUNT(*) || '-' || IFNULL(MAX(rowid), 0) FROM playlist_song_map),
                (SELECT COUNT(*) || '-' || IFNULL(MAX(bookmarkedAt), '') FROM artist WHERE bookmarkedAt IS NOT NULL),
                (SELECT COUNT(*) || '-' || IFNULL(MAX(bookmarkedAt), '') FROM album WHERE bookmarkedAt IS NOT NULL),
                (SELECT COUNT(*) || '-' || IFNULL(MAX(rowid), 0) FROM search_history),
                (SELECT COUNT(*) FROM lyrics)
        """.trimIndent()
    }

    private val syncScope = CoroutineScope(Dispatchers.IO)
    val isSyncing = MutableStateFlow(false)
    val syncStatus = MutableStateFlow<String?>(null)
    private val cloudSyncMutex = Mutex()
    private var clearSyncStatusJob: Job? = null

    private fun scheduleSyncStatusClear(status: String) {
        clearSyncStatusJob?.cancel()
        clearSyncStatusJob = syncScope.launch {
            delay(3000.milliseconds)
            if (!isSyncing.value && syncStatus.value == status) {
                syncStatus.value = null
            }
        }
    }

    private fun publishSyncStatus(status: String?) {
        clearSyncStatusJob?.cancel()
        syncStatus.value = status
        if (!status.isNullOrBlank()) {
            scheduleSyncStatusClear(status)
        }
    }

  fun likeSong(s: SongEntity) {
    syncScope.launch {
      // Sincronizar con Last.fm
      try {
        val song = database.song(s.id).firstOrNull()
        if (song != null) {
          if (s.liked) {
            lastFmService.loveTrack(song)
          } else {
            lastFmService.unloveTrack(song)
          }
        }
      } catch (e: Exception) {
        Timber.e(e, "Error syncing like to Last.fm for song: ${s.title}")
      }
    }
  }

  suspend fun syncLikedSongs() = coroutineScope {
    YouTube.playlist("LM").completed().onSuccess { page ->
      val remoteSongs = page.songs
      val remoteIds = remoteSongs.map { it.id }.toSet()
      val localSongs = database.likedSongsByNameAsc().first()
      val recentlyLikedThreshold = LocalDateTime.now().minusMinutes(5)

      localSongs
          .filter { localSong ->
            val entity = localSong.song
            val isLocalOnlySong = entity.isLocal || localSong.id.startsWith("LOCAL_")
            val wasRecentlyLiked = entity.likedDate?.isAfter(recentlyLikedThreshold) == true
            !isLocalOnlySong && !wasRecentlyLiked && localSong.id !in remoteIds
          }
          .forEach { database.update(it.song.localToggleLike()) }

        LocalDateTime.now()
      remoteSongs.forEachIndexed { index, song ->
        launch {
          val dbSong = database.song(song.id).firstOrNull()
          val timestamp = LocalDateTime.now().minusSeconds(index.toLong())
          if (dbSong == null) {
            database.insert(
                song.toMediaMetadata().toSongEntity().copy(liked = true, likedDate = timestamp))
          } else if (!dbSong.song.liked || dbSong.song.likedDate != timestamp) {
            database.update(dbSong.song.copy(liked = true, likedDate = timestamp))
          }
        }
      }
    }
  }

  suspend fun syncLibrarySongs() = coroutineScope {
    YouTube.library("FEmusic_liked_videos").completed().onSuccess { page ->
      val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
      val remoteIds = remoteSongs.map { it.id }.toSet()
      val localSongs = database.songsByNameAsc().first()

      localSongs
          .filterNot { it.id in remoteIds }
          .forEach { database.update(it.song.toggleLibrary()) }

      remoteSongs.forEach { song ->
        launch {
          val dbSong = database.song(song.id).firstOrNull()
          if (dbSong == null) {
            database.insert(song.toMediaMetadata(), SongEntity::toggleLibrary)
          } else if (dbSong.song.inLibrary == null) {
            database.update(dbSong.song.toggleLibrary())
          }
        }
      }
    }
  }

  suspend fun syncLikedAlbums() = coroutineScope {
    YouTube.library("FEmusic_liked_albums").completed().onSuccess { page ->
      val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
      val remoteIds = remoteAlbums.map { it.id }.toSet()
      val localAlbums = database.albumsLikedByNameAsc().first()

      localAlbums
          .filterNot { it.id in remoteIds }
          .forEach { database.update(it.album.localToggleLike()) }

      remoteAlbums.forEach { album ->
        launch {
          val dbAlbum = database.album(album.id).firstOrNull()
          YouTube.album(album.browseId).onSuccess { albumPage ->
            if (dbAlbum == null) {
              database.insert(albumPage)
              database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                database.update(newDbAlbum.album.localToggleLike())
              }
            } else if (dbAlbum.album.bookmarkedAt == null) {
              database.update(dbAlbum.album.localToggleLike())
            }
          }
        }
      }
    }
  }

  suspend fun syncArtistsSubscriptions() = coroutineScope {
    YouTube.library("FEmusic_library_corpus_artists").completed().onSuccess { page ->
      val remoteArtists = page.items.filterIsInstance<ArtistItem>()
      val remoteIds = remoteArtists.map { it.id }.toSet()
      val localArtists = database.artistsBookmarkedByNameAsc().first()

      localArtists
          .filterNot { it.id in remoteIds }
          .forEach { database.update(it.artist.localToggleLike()) }

      remoteArtists.forEach { artist ->
        launch {
          val dbArtist = database.artist(artist.id).firstOrNull()
          if (dbArtist == null) {
            database.insert(
                ArtistEntity(
                    id = artist.id,
                    name = artist.title,
                    thumbnailUrl = artist.thumbnail,
                    channelId = artist.channelId,
                    bookmarkedAt = LocalDateTime.now()))
          } else if (dbArtist.artist.bookmarkedAt == null) {
            database.update(dbArtist.artist.localToggleLike())
          }
        }
      }
    }
  }

  suspend fun syncSavedPlaylists() = coroutineScope {
    YouTube.library("FEmusic_liked_playlists").completed().onSuccess { page ->
      val remotePlaylists =
          page.items
              .filterIsInstance<PlaylistItem>()
              .filterNot { it.id == "LM" || it.id == "SE" }
              .reversed()
      val remoteIds = remotePlaylists.map { it.id }.toSet()
      val localPlaylists = database.playlistsByNameAsc().first()

      localPlaylists
          .filterNot { it.playlist.browseId in remoteIds }
          .filterNot { it.playlist.browseId == null }
          .forEach { database.update(it.playlist.localToggleLike()) }

      remotePlaylists.forEach { playlist ->
        launch {
          var playlistEntity = localPlaylists.find { it.playlist.browseId == playlist.id }?.playlist
          if (playlistEntity == null) {
            playlistEntity =
                PlaylistEntity(
                    name = playlist.title,
                    browseId = playlist.id,
                    thumbnailUrl = playlist.thumbnail,
                    isEditable = playlist.isEditable,
                    bookmarkedAt = LocalDateTime.now(),
                    remoteSongCount =
                        playlist.songCountText?.let {
                          Regex("""\\d+""").find(it)?.value?.toIntOrNull()
                        },
                    playEndpointParams = playlist.playEndpoint?.params,
                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                    radioEndpointParams = playlist.radioEndpoint?.params)
            database.insert(playlistEntity)
          }
          syncPlaylist(playlist.id, playlistEntity.id)
        }
      }
    }
  }

  private suspend fun syncPlaylist(browseId: String, playlistId: String) = coroutineScope {
    val isRdpn = browseId == "RDPN" || browseId == "VLRDPN" || browseId == "FEmusic_new_episodes"
    val result = if (isRdpn) {
        YouTube.newEpisodes().map { songs ->
            com.anitail.innertube.pages.PlaylistPage(
                playlist = com.anitail.innertube.models.PlaylistItem(
                    id = "RDPN",
                    title = "New Episodes",
                    author = null,
                    songCountText = "${songs.size} episodes",
                    thumbnail = songs.firstOrNull()?.thumbnail,
                    playEndpoint = null,
                    shuffleEndpoint = null,
                    radioEndpoint = null,
                ),
                songs = songs,
                songsContinuation = null,
                continuation = null
            )
        }
    } else {
        YouTube.playlist(browseId)
    }

    result.completed().onSuccess { page ->
      val songs = page.songs.map(SongItem::toMediaMetadata)

      val remoteIds = songs.map { it.id }
      val localIds =
          database.playlistSongs(playlistId).first().sortedBy { it.map.position }.map { it.song.id }

      if (remoteIds == localIds) return@onSuccess

        val songsToInsert = mutableListOf<MediaMetadata>()
        withContext(Dispatchers.IO) {
            songs.forEach { song ->
                if (database.getSongById(song.id) == null) {
                    songsToInsert.add(song)
                }
            }
        }
      database.transaction {
          database.clearPlaylist(playlistId)
          songsToInsert.forEach { song -> database.insert(song) }
          songs.forEachIndexed { idx, song ->
              database.insert(
                  PlaylistSongMap(
                      songId = song.id,
                      playlistId = playlistId,
                      position = idx,
                      setVideoId = song.setVideoId
                  )
              )
        }
      }
    }
  }

    suspend fun syncWatchHistory() = coroutineScope {
        YouTube.musicHistory().onSuccess { page ->
            val historySections = page.sections ?: return@onSuccess
            val songs = historySections.flatMap { it.songs }
            val songsToSync = songs.take(20)

            val recentEvents = database.events().first().take(50)

            songsToSync.reversed().forEachIndexed { index, song ->
                val isRecentlyPlayed = recentEvents.any { it.song?.id == song.id }

                if (!isRecentlyPlayed) {
                    launch {
                        if (database.song(song.id).firstOrNull() == null) {
                            database.insert(song.toMediaMetadata())
                        }

                        database.insert(
                            Event(
                                songId = song.id,
                                timestamp = LocalDateTime.now()
                                    .minusSeconds((songsToSync.size - index).toLong()),
                                playTime = 0
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun syncCloud(force: Boolean = false): String? = cloudSyncMutex.withLock {
        coroutineScope {
        // Skip if database is being restored
        if (!database.isSafeToUse()) {
            Timber.d("syncCloud: Skipping - database is being restored")
            return@coroutineScope null
        }

        // Throttling: skip if synced recently (unless forced)
        if (!force) {
            val lastSync = context.dataStore.data.first()[LastCloudSyncKey] ?: 0L
            val now = System.currentTimeMillis()
            if (now - lastSync < SYNC_THROTTLE_MS) {
                val minutesAgo = (now - lastSync) / 60000
                Timber.d("syncCloud: Skipping - last sync was $minutesAgo min ago")
                return@coroutineScope null
            }
        }

        // First, try to restore sign-in session
        if (!googleDriveSyncManager.isSignedIn()) {
            Timber.d("syncCloud: Not signed in, attempting silent sign-in...")
            val silentSignInSuccess = googleDriveSyncManager.trySilentSignIn()
            if (!silentSignInSuccess) {
                Timber.d("syncCloud: Silent sign-in failed, user not authenticated")
                return@coroutineScope null // User not signed in, skip sync silently
            }
            Timber.d("syncCloud: Silent sign-in successful")
        }

        Timber.d("syncCloud: Starting cloud sync...")

        // Wrap entire sync in timeout to prevent hangs
        return@coroutineScope try {
            val result = withTimeoutOrNull(SYNC_TIMEOUT_MS.milliseconds) {
                syncCloudInternal()
            } ?: context.getString(R.string.sync_timeout)

            if (result == context.getString(R.string.sync_timeout)) {
                Timber.e("syncCloud: Timed out after ${SYNC_TIMEOUT_MS / 1000}s")
            }

            // Update last sync timestamp on success
            if (result == context.getString(R.string.sync_completed) || result == context.getString(R.string.sync_initial_uploaded)) {
                context.dataStore.edit { it[LastCloudSyncKey] = System.currentTimeMillis() }
            }

            publishSyncStatus(result)
            result
        } catch (e: Exception) {
            publishSyncStatus(context.getString(R.string.sync_error))
            throw e
        }
        }
    }

    /**
     * Content-based fingerprint of the data that cloud sync actually carries.
     *
     * Previously this used file size/mtime of the DB, WAL, SHM and settings files, which change on
     * virtually every playback event or preference write (including the sync bookkeeping keys
     * themselves), so the "unchanged" fast path almost never hit and every sync did a full
     * download + merge + upload.
     */
    private suspend fun buildLocalCloudSyncFingerprint(): String? = withContext(Dispatchers.IO) {
        if (!database.isSafeToUse()) return@withContext null

        val dbFingerprint = try {
            database.openHelper.readableDatabase.query(LOCAL_FINGERPRINT_QUERY).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                (0 until cursor.columnCount).joinToString(":") { cursor.getString(it) ?: "" }
            }
        } catch (e: IllegalStateException) {
            if (e.message?.contains("already-closed") == true) {
                Timber.w("syncCloud: Database was closed while computing local fingerprint")
                return@withContext null
            }
            throw e
        } catch (e: Exception) {
            Timber.w(e, "syncCloud: Failed to compute DB fingerprint")
            return@withContext null
        } ?: return@withContext null

        val prefs = context.dataStore.data.first()
        val settingsFingerprint = prefs.asMap()
            .filterKeys { it.name !in SYNC_BOOKKEEPING_PREF_NAMES }
            .entries
            .sortedBy { it.key.name }
            .joinToString("|") { "${it.key.name}=${it.value}" }
            .hashCode()

        val lastFmOfflineFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/lastfm_offline.xml"
        )
        val lastFmFingerprint =
            if (lastFmOfflineFile.exists()) lastFmOfflineFile.length().toString() else "missing"

        listOf(dbFingerprint, settingsFingerprint.toString(), lastFmFingerprint).joinToString("|")
    }

    private fun matchesRemoteSnapshot(
        remoteMetadata: DriveBackupMetadata,
        storedModifiedTime: Long,
        storedMd5: String?,
    ): Boolean {
        if (storedModifiedTime == 0L || remoteMetadata.modifiedTime != storedModifiedTime) {
            return false
        }

        return remoteMetadata.md5 == null || storedMd5 == null || remoteMetadata.md5 == storedMd5
    }

    private suspend fun persistCloudSyncSnapshot(
        remoteMetadata: DriveBackupMetadata?,
        localFingerprint: String?,
    ) {
        context.dataStore.edit { prefs ->
            if (remoteMetadata != null) {
                prefs[LastCloudSyncRemoteModifiedTimeKey] = remoteMetadata.modifiedTime
                if (!remoteMetadata.md5.isNullOrBlank()) {
                    prefs[LastCloudSyncRemoteMd5Key] = remoteMetadata.md5
                } else {
                    prefs.remove(LastCloudSyncRemoteMd5Key)
                }
            } else {
                prefs.remove(LastCloudSyncRemoteModifiedTimeKey)
                prefs.remove(LastCloudSyncRemoteMd5Key)
            }

            if (!localFingerprint.isNullOrBlank()) {
                prefs[LastCloudSyncLocalFingerprintKey] = localFingerprint
            } else {
                prefs.remove(LastCloudSyncLocalFingerprintKey)
            }
        }
    }

    /**
     * Produces a consistent, self-contained copy of the database without blocking writers.
     *
     * On API 30+ (SQLite >= 3.28) uses `VACUUM INTO`, which reads a snapshot under a normal read
     * transaction (WAL mode lets writers continue). On older devices falls back to the previous
     * `wal_checkpoint(FULL)` + file copy.
     */
    private fun snapshotDatabase(snapshotFile: File): File {
        if (snapshotFile.exists()) snapshotFile.delete()
        val db = database.openHelper.writableDatabase
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val target = snapshotFile.absolutePath.replace("'", "''")
                db.query("VACUUM INTO '$target'").use { it.moveToFirst() }
                if (snapshotFile.exists() && snapshotFile.length() > 0L) return snapshotFile
            } catch (e: Exception) {
                Timber.w(e, "syncCloud: VACUUM INTO failed, falling back to checkpoint + copy")
                snapshotFile.delete()
            }
        }
        db.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbPath = db.path ?: throw IllegalStateException("Database path is null")
        return File(dbPath)
    }

    private suspend fun createCurrentBackupZip(outputZipFile: File) = withContext(Dispatchers.IO) {
        if (!database.isSafeToUse()) {
            throw IllegalStateException("Database closed before backup creation")
        }

        val snapshotFile = File(context.cacheDir, "cloud_sync_snapshot.db")
        val dbFile = try {
            snapshotDatabase(snapshotFile)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("already-closed") == true) {
                throw IllegalStateException("Database closed during backup creation", e)
            }
            throw e
        }

        try {
            FileOutputStream(outputZipFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                    zos.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                    FileInputStream(dbFile).use { fis ->
                        fis.copyTo(zos, bufferSize = 16384)
                    }

                val settingsFile = File(context.filesDir, "datastore/settings.preferences_pb")
                if (settingsFile.exists()) {
                    zos.putNextEntry(ZipEntry("settings.preferences_pb"))
                    FileInputStream(settingsFile).use { fis -> fis.copyTo(zos) }
                }

                val accountsJson = createAccountsJson()
                if (accountsJson.isNotEmpty()) {
                    zos.putNextEntry(ZipEntry("accounts.json"))
                    zos.write(accountsJson.toByteArray())
                }

                val lastFmOfflineFile = File(
                    context.applicationInfo.dataDir,
                    "shared_prefs/lastfm_offline.xml"
                )
                if (lastFmOfflineFile.exists()) {
                    zos.putNextEntry(ZipEntry("lastfm_offline.xml"))
                    FileInputStream(lastFmOfflineFile).use { fis -> fis.copyTo(zos) }
                }
            }
            }
        } finally {
            if (snapshotFile.exists()) snapshotFile.delete()
        }
    }

    private suspend fun uploadCurrentStateBackup(
        successMessage: String,
        failureMessage: String,
        existingFileId: String? = null,
    ): String {
        val backupZip = File(context.cacheDir, "cloud_sync_upload.zip")
        try {
            // Capture the fingerprint *before* snapshotting so changes made during upload
            // are detected on the next sync instead of being silently marked as synced.
            val localFingerprint = buildLocalCloudSyncFingerprint()
            createCurrentBackupZip(backupZip)

            val uploadResult = googleDriveSyncManager.uploadBackupReplacingByNameWithMetadata(
                backupFile = backupZip,
                remoteName = CLOUD_SYNC_REMOTE_BACKUP_NAME,
                existingFileId = existingFileId,
            )
            val remoteMetadata = uploadResult.getOrElse {
                Timber.e(it, "syncCloud: Failed to upload current backup state")
                return failureMessage
            }

            persistCloudSyncSnapshot(remoteMetadata, localFingerprint)
            return successMessage
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload current backup state")
            return failureMessage
        } finally {
            backupZip.delete()
        }
    }

    private suspend fun syncCloudInternal(): String? {
        clearSyncStatusJob?.cancel()
        isSyncing.value = true
        syncStatus.value = context.getString(R.string.syncing)
        return try {
            coroutineScope {
            // Check if database is still safe to use
            if (!database.isSafeToUse()) {
                Timber.d("syncCloudInternal: Database not available, aborting sync")
                return@coroutineScope null
            }

            val prefs = context.dataStore.data.first()
            val currentLocalFingerprint = buildLocalCloudSyncFingerprint()
            val storedRemoteModifiedTime = prefs[LastCloudSyncRemoteModifiedTimeKey] ?: 0L
            val storedRemoteMd5 = prefs[LastCloudSyncRemoteMd5Key]
            val storedLocalFingerprint = prefs[LastCloudSyncLocalFingerprintKey]
            // Only look at the dedicated cloud-sync file so manual/auto backups uploaded to the
            // same folder don't make the remote look "changed" and force a full download+merge.
            // Fall back to the newest legacy backup only if the sync file doesn't exist yet.
            val latestRemoteMetadata = googleDriveSyncManager
                .getLatestBackupMetadata(exactName = CLOUD_SYNC_REMOTE_BACKUP_NAME)
                .mapCatching { it ?: googleDriveSyncManager.getLatestBackupMetadata().getOrThrow() }
                .getOrElse {
                Timber.e(it, "syncCloud: Failed to query remote backup metadata")
                return@coroutineScope context.getString(R.string.sync_failed_download)
            }

            if (latestRemoteMetadata != null) {
                val remoteUnchanged = matchesRemoteSnapshot(
                    remoteMetadata = latestRemoteMetadata,
                    storedModifiedTime = storedRemoteModifiedTime,
                    storedMd5 = storedRemoteMd5,
                )
                val localUnchanged =
                    currentLocalFingerprint != null && currentLocalFingerprint == storedLocalFingerprint

                if (remoteUnchanged && localUnchanged) {
                    Timber.d("syncCloud: Remote and local snapshots unchanged, skipping sync")
                    persistCloudSyncSnapshot(latestRemoteMetadata, currentLocalFingerprint)
                    return@coroutineScope context.getString(R.string.sync_completed)
                }

                if (remoteUnchanged && currentLocalFingerprint != null) {
                    Timber.d("syncCloud: Remote unchanged, uploading local changes without merge")
                    return@coroutineScope uploadCurrentStateBackup(
                        successMessage = context.getString(R.string.sync_completed),
                        failureMessage = context.getString(R.string.sync_failed_upload),
                        existingFileId = latestRemoteMetadata.id,
                    )
                }
            }

            val remoteBackupMetadata = latestRemoteMetadata ?: run {
                Timber.d("syncCloud: No remote backup found, uploading initial backup...")
                return@coroutineScope uploadCurrentStateBackup(
                    successMessage = context.getString(R.string.sync_initial_uploaded),
                    failureMessage = context.getString(R.string.sync_failed_initial_upload),
                    existingFileId = null,
                )
            }

            // 1. Download latest backup (ZIP file)
            val tempZipFile = File(context.cacheDir, "temp_sync.zip")
            val downloadResult = googleDriveSyncManager.downloadBackup(remoteBackupMetadata, tempZipFile)
            if (!downloadResult.isSuccess) {
                tempZipFile.delete()
                Timber.e(downloadResult.exceptionOrNull(), "syncCloud: Failed to download remote backup")
                return@coroutineScope context.getString(R.string.sync_failed_download)
            }

            // 2. Unzip and extract all files
            val tempDbFile = File(context.cacheDir, "temp_sync_extracted.db")
            val tempSettingsFile = File(context.cacheDir, "temp_settings.preferences_pb")
            val tempAccountsFile = File(context.cacheDir, "temp_accounts.json")
            val tempLastFmOfflineFile = File(context.cacheDir, "temp_lastfm_offline.xml")

            var mergeProducedChanges: Boolean  // assume changes unless proven otherwise
            try {
                // Extract all files from ZIP (buffered for performance)
                BufferedInputStream(FileInputStream(tempZipFile)).use { bis ->
                    ZipInputStream(bis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            when (entry.name) {
                                InternalDatabase.DB_NAME -> {
                                    BufferedOutputStream(FileOutputStream(tempDbFile)).use { fos ->
                                        zis.copyTo(fos, bufferSize = 16384)
                                    }
                                }

                                "settings.preferences_pb" -> {
                                    FileOutputStream(tempSettingsFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }

                                "accounts.json" -> {
                                    FileOutputStream(tempAccountsFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }

                                "lastfm_offline.xml" -> {
                                    FileOutputStream(tempLastFmOfflineFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }
                }

                if (!tempDbFile.exists() || tempDbFile.length() == 0L) {
                    Timber.e("Could not find database file in backup zip")
                    return@coroutineScope context.getString(R.string.sync_corrupt_backup)
                }

                // 3. Merge database (check if still safe)
                if (!database.isSafeToUse()) {
                    Timber.d("syncCloud: Database closed during sync, aborting")
                    return@coroutineScope null
                }

                databaseMerger.mergeDatabase(tempDbFile)

                // 4. Restore settings if present (through DataStore, never by overwriting the
                // live preferences file underneath an active DataStore instance)
                if (tempSettingsFile.exists() && tempSettingsFile.length() > 0L) {
                    applyRemoteSettings(tempSettingsFile)
                    Timber.d("Settings restored from backup")
                }

                // 5. Restore accounts if present
                if (tempAccountsFile.exists() && tempAccountsFile.length() > 0L) {
                    restoreAccounts(tempAccountsFile)
                    Timber.d("Accounts restored from backup")
                }

                // 6. Restore Last.fm offline scrobbles if present
                if (tempLastFmOfflineFile.exists() && tempLastFmOfflineFile.length() > 0L) {
                    val targetLastFmFile = File(
                        context.applicationInfo.dataDir,
                        "shared_prefs/lastfm_offline.xml"
                    )
                    tempLastFmOfflineFile.copyTo(targetLastFmFile, overwrite = true)
                    Timber.d("Last.fm offline scrobbles restored from backup")
                }

                val mergedLocalFingerprint = buildLocalCloudSyncFingerprint()
                mergeProducedChanges =
                    currentLocalFingerprint == null || mergedLocalFingerprint == null ||
                        currentLocalFingerprint != mergedLocalFingerprint

            } catch (e: Exception) {
                Timber.e(e, "Sync Merge failed")
                return@coroutineScope context.getString(R.string.sync_failed_merge)
            } finally {
                tempZipFile.delete()
                if (tempDbFile.exists()) tempDbFile.delete()
                if (tempSettingsFile.exists()) tempSettingsFile.delete()
                if (tempAccountsFile.exists()) tempAccountsFile.delete()
                if (tempLastFmOfflineFile.exists()) tempLastFmOfflineFile.delete()
            }

            // 7. Upload merged database with all files
            // Skip re-upload if merge produced no changes — the remote already has the latest data.
            if (!mergeProducedChanges) {
                Timber.d("syncCloud: Merge produced no changes, skipping re-upload")
                persistCloudSyncSnapshot(remoteBackupMetadata, buildLocalCloudSyncFingerprint())
                return@coroutineScope context.getString(R.string.sync_completed)
            }

            try {
                return@coroutineScope uploadCurrentStateBackup(
                    successMessage = context.getString(R.string.sync_completed),
                    failureMessage = context.getString(R.string.sync_failed_upload),
                    existingFileId = remoteBackupMetadata.id,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to create merged backup")
                return@coroutineScope context.getString(R.string.sync_failed_local_backup)
            }
            }
        } finally {
            isSyncing.value = false
        }
    }


    /**
     * Reads a remote `.preferences_pb` file with a short-lived DataStore and copies its entries
     * into the app DataStore (remote wins for shared keys; local-only keys are kept and the
     * sync bookkeeping keys are never overwritten).
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun applyRemoteSettings(remoteSettingsFile: File) {
        // Unique file name: DataStore forbids two instances on the same path in one process.
        val readFile = File(
            context.cacheDir,
            "remote_settings_${System.nanoTime()}.preferences_pb"
        )
        val readScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            remoteSettingsFile.copyTo(readFile, overwrite = true)
            val remotePrefs = PreferenceDataStoreFactory.create(scope = readScope) { readFile }
                .data.first()

            context.dataStore.edit { local ->
                remotePrefs.asMap().forEach { (key, value) ->
                    if (key.name !in SYNC_BOOKKEEPING_PREF_NAMES) {
                        local[key as Preferences.Key<Any>] = value
                    }
                }
            }
        } finally {
            readScope.cancel()
            readFile.delete()
        }
    }

    private suspend fun createAccountsJson(): String {
        val dataStore = context.dataStore
        val prefs = dataStore.data.first()

        val accounts = mutableMapOf<String, String>()

        // YouTube account
        prefs[InnerTubeCookieKey]?.let {
            accounts["innerTubeCookie"] = it
        }
        prefs[VisitorDataKey]?.let { accounts["visitorData"] = it }
        prefs[DataSyncIdKey]?.let { accounts["dataSyncId"] = it }
        prefs[AccountNameKey]?.let { accounts["accountName"] = it }
        prefs[AccountEmailKey]?.let { accounts["accountEmail"] = it }
        prefs[AccountChannelHandleKey]?.let {
            accounts["accountChannelHandle"] = it
        }
        prefs[AccountImageUrlKey]?.let {
            accounts["accountImageUrl"] = it
        }

        // Last.fm account
        prefs[LastFmSessionKey]?.let {
            accounts["lastFmSessionKey"] = it
        }
        prefs[LastFmUsernameKey]?.let {
            accounts["lastFmUsername"] = it
        }

        // Discord account
        DiscordTokenStore.init(context)
        DiscordTokenStore.retrieve()?.let { accounts["discordToken"] = it }
        prefs[DiscordUsernameKey]?.let {
            accounts["discordUsername"] = it
        }
        prefs[DiscordNameKey]?.let { accounts["discordName"] = it }
        prefs[DiscordAvatarUrlKey]?.let {
            accounts["discordAvatarUrl"] = it
        }

        // Spotify account
        prefs[SpotifyAccessTokenKey]?.let {
            accounts["spotifyAccessToken"] = it
        }
        prefs[SpotifyRefreshTokenKey]?.let {
            accounts["spotifyRefreshToken"] = it
        }

        // Proxy settings (can contain passwords)
        prefs[ProxyUrlKey]?.let { accounts["proxyUrl"] = it }
        prefs[ProxyUsernameKey]?.let { accounts["proxyUsername"] = it }
        prefs[ProxyPasswordKey]?.let { accounts["proxyPassword"] = it }

        if (accounts.isEmpty()) return ""

        // Encode as simple JSON (base64 encoded for basic obfuscation)
        val jsonBuilder = StringBuilder("{")
        accounts.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) jsonBuilder.append(",")
            jsonBuilder.append(
                "\"$key\":\"${
                    Base64.encodeToString(
                        value.toByteArray(),
                        Base64.NO_WRAP
                    )
                }\""
            )
        }
        jsonBuilder.append("}")
        return jsonBuilder.toString()
    }

    private suspend fun restoreAccounts(accountsFile: File) {
        try {
            val json = accountsFile.readText()
            if (json.isEmpty() || !json.startsWith("{")) return

            val dataStore = context.dataStore

            // Parse simple JSON manually to avoid adding dependencies
            val accountsMap = parseSimpleJson(json)

            dataStore.edit { prefs ->
                accountsMap["innerTubeCookie"]?.let {
                    prefs[InnerTubeCookieKey] = decodeBase64(it)
                }
                accountsMap["visitorData"]?.let {
                    prefs[VisitorDataKey] = decodeBase64(it)
                }
                accountsMap["dataSyncId"]?.let {
                    prefs[DataSyncIdKey] = decodeBase64(it)
                }
                accountsMap["accountName"]?.let {
                    prefs[AccountNameKey] = decodeBase64(it)
                }
                accountsMap["accountEmail"]?.let {
                    prefs[AccountEmailKey] = decodeBase64(it)
                }
                accountsMap["accountChannelHandle"]?.let {
                    prefs[AccountChannelHandleKey] = decodeBase64(it)
                }
                accountsMap["accountImageUrl"]?.let {
                    prefs[AccountImageUrlKey] = decodeBase64(it)
                }

                accountsMap["lastFmSessionKey"]?.let {
                    prefs[LastFmSessionKey] = decodeBase64(it)
                }
                accountsMap["lastFmUsername"]?.let {
                    prefs[LastFmUsernameKey] = decodeBase64(it)
                }

                accountsMap["discordToken"]?.let {
                    DiscordTokenStore.init(context)
                    DiscordTokenStore.store(decodeBase64(it))
                }
                accountsMap["discordUsername"]?.let {
                    prefs[DiscordUsernameKey] = decodeBase64(it)
                }
                accountsMap["discordName"]?.let {
                    prefs[DiscordNameKey] = decodeBase64(it)
                }
                accountsMap["discordAvatarUrl"]?.let {
                    prefs[DiscordAvatarUrlKey] = decodeBase64(it)
                }

                accountsMap["spotifyAccessToken"]?.let {
                    prefs[SpotifyAccessTokenKey] = decodeBase64(it)
                }
                accountsMap["spotifyRefreshToken"]?.let {
                    prefs[SpotifyRefreshTokenKey] = decodeBase64(it)
                }

                accountsMap["proxyUrl"]?.let {
                    prefs[ProxyUrlKey] = decodeBase64(it)
                }
                accountsMap["proxyUsername"]?.let {
                    prefs[ProxyUsernameKey] = decodeBase64(it)
                }
                accountsMap["proxyPassword"]?.let {
                    prefs[ProxyPasswordKey] = decodeBase64(it)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore accounts")
        }
    }

    private fun parseSimpleJson(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val content = json.trim().removeSurrounding("{", "}")
        if (content.isEmpty()) return result

        // Simple regex-based parsing for our known format
        val pattern = "\"([^\"]+)\":\"([^\"]*)\""
        val regex = Regex(pattern)
        regex.findAll(content).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            result[key] = value
        }
        return result
    }

    private fun decodeBase64(encoded: String): String {
        return try {
            String(Base64.decode(encoded, Base64.NO_WRAP))
        } catch (_: Exception) {
            encoded // Return as-is if decoding fails
        }
    }

    fun savePodcast(podcastId: String, save: Boolean) {
        syncScope.launch {
            if (YouTube.cookie == null) {
                Timber.d("[PODCAST_TOGGLE] Skipping savePodcast - user not logged in")
                return@launch
            }
            YouTube.savePodcast(podcastId, save).onSuccess {
                Timber.d("[PODCAST_TOGGLE] Successfully saved/unsaved podcast: $podcastId")
            }.onFailure { e ->
                Timber.e(e, "[PODCAST_TOGGLE] Failed to save/unsave podcast: $podcastId")
            }
        }
    }
}
