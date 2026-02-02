package com.anitail.music.utils

import androidx.datastore.preferences.core.edit
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.utils.completed
import com.anitail.music.constants.LastCloudSyncKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.ArtistEntity
import com.anitail.music.db.entities.PlaylistEntity
import com.anitail.music.db.entities.PlaylistSongMap
import com.anitail.music.db.entities.SongEntity
import com.anitail.music.models.toMediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils
@Inject
constructor(
    private val database: MusicDatabase,
    private val lastFmService: LastFmService,
    private val googleDriveSyncManager: GoogleDriveSyncManager,
    private val databaseMerger: com.anitail.music.db.DatabaseMerger,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
  private val syncScope = CoroutineScope(Dispatchers.IO)
    private val cloudSyncMutex = Mutex()

  fun likeSong(s: SongEntity) {
    syncScope.launch {
      // Sincronizar con YouTube
      YouTube.likeVideo(s.id, s.liked)

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
      val remoteIds = remoteSongs.map { it.id }
      val localSongs = database.likedSongsByNameAsc().first()

      localSongs
          .filterNot { it.id in remoteIds }
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
    YouTube.playlist(browseId).completed().onSuccess { page ->
      val songs = page.songs.map(SongItem::toMediaMetadata)

      val remoteIds = songs.map { it.id }
      val localIds =
          database.playlistSongs(playlistId).first().sortedBy { it.map.position }.map { it.song.id }

      if (remoteIds == localIds) return@onSuccess

      database.transaction {
        runBlocking {
          database.clearPlaylist(playlistId)
          songs.forEachIndexed { idx, song ->
            if (database.song(song.id).firstOrNull() == null) {
              database.insert(song)
            }
            database.insert(
                PlaylistSongMap(
                    songId = song.id,
                    playlistId = playlistId,
                    position = idx,
                    setVideoId = song.setVideoId))
          }
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
                            com.anitail.music.db.entities.Event(
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

    companion object {
        private const val SYNC_THROTTLE_MS = 30 * 60 * 1000L // 30 minutes
        private const val SYNC_TIMEOUT_MS = 60_000L // 60 seconds
    }

    suspend fun syncCloud(force: Boolean = false): String? = coroutineScope {
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
        val result = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
            syncCloudInternal()
        }

        if (result == null) {
            Timber.e("syncCloud: Timed out after ${SYNC_TIMEOUT_MS / 1000}s")
            return@coroutineScope "Sync timed out"
        }

        // Update last sync timestamp on success
        if (result.startsWith("Sincronización") || result.startsWith("Copia inicial")) {
            context.dataStore.edit { it[LastCloudSyncKey] = System.currentTimeMillis() }
        }

        return@coroutineScope result
    }

    private suspend fun syncCloudInternal(): String? = cloudSyncMutex.withLock {
        coroutineScope {
            // Check if database is still safe to use
            if (!database.isSafeToUse()) {
                Timber.d("syncCloudInternal: Database not available, aborting sync")
                return@coroutineScope null
            }

            // 1. Download latest backup (ZIP file)
            val tempZipFile = java.io.File(context.cacheDir, "temp_sync.zip")
            val downloadResult = googleDriveSyncManager.downloadLatestBackup(tempZipFile)

        if (downloadResult.isSuccess) {
            // 2. Unzip and extract all files
            val tempDbFile = java.io.File(context.cacheDir, "temp_sync_extracted.db")
            val tempSettingsFile = java.io.File(context.cacheDir, "temp_settings.preferences_pb")
            val tempAccountsFile = java.io.File(context.cacheDir, "temp_accounts.json")
            val tempLastFmOfflineFile = java.io.File(context.cacheDir, "temp_lastfm_offline.xml")

            try {
                // Extract all files from ZIP
                java.util.zip.ZipInputStream(java.io.FileInputStream(tempZipFile)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            com.anitail.music.db.InternalDatabase.DB_NAME -> {
                                java.io.FileOutputStream(tempDbFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }

                            "settings.preferences_pb" -> {
                                java.io.FileOutputStream(tempSettingsFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }

                            "accounts.json" -> {
                                java.io.FileOutputStream(tempAccountsFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }

                            "lastfm_offline.xml" -> {
                                java.io.FileOutputStream(tempLastFmOfflineFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }

                if (!tempDbFile.exists() || tempDbFile.length() == 0L) {
                    Timber.e("Could not find database file in backup zip")
                    return@coroutineScope "Backup corrupto o incompleto"
                }

                // 3. Merge database (check if still safe) with retry logic
                if (!database.isSafeToUse()) {
                    Timber.d("syncCloud: Database closed during sync, aborting")
                    return@coroutineScope null
                }

                // Checkpoint WAL to ensure all data is written to main db file
                try {
                    database.checkpoint()
                } catch (e: Exception) {
                    Timber.w(e, "Checkpoint before merge failed, continuing anyway")
                }

                // Retry merge up to 3 times with delays to allow pending transactions to complete
                var mergeSuccess = false
                var lastMergeError: Exception? = null
                for (attempt in 1..3) {
                    try {
                        databaseMerger.mergeDatabase(tempDbFile)
                        mergeSuccess = true
                        break
                    } catch (e: IllegalStateException) {
                        lastMergeError = e
                        if (e.message?.contains("transactions in progress") == true && attempt < 3) {
                            Timber.w("Merge attempt $attempt failed due to pending transactions, retrying...")
                            kotlinx.coroutines.delay(500L * attempt) // Progressive delay
                        } else {
                            throw e
                        }
                    }
                }

                if (!mergeSuccess && lastMergeError != null) {
                    throw lastMergeError
                }

                // 4. Restore settings if present
                if (tempSettingsFile.exists() && tempSettingsFile.length() > 0L) {
                    val targetSettingsDir = java.io.File(context.filesDir, "datastore")
                    if (!targetSettingsDir.exists()) targetSettingsDir.mkdirs()
                    val targetSettingsFile =
                        java.io.File(targetSettingsDir, "settings.preferences_pb")
                    tempSettingsFile.copyTo(targetSettingsFile, overwrite = true)
                    Timber.d("Settings restored from backup")
                }

                // 5. Restore accounts if present
                if (tempAccountsFile.exists() && tempAccountsFile.length() > 0L) {
                    restoreAccounts(tempAccountsFile)
                    Timber.d("Accounts restored from backup")
                }

                // 6. Restore Last.fm offline scrobbles if present
                if (tempLastFmOfflineFile.exists() && tempLastFmOfflineFile.length() > 0L) {
                    val targetLastFmFile = java.io.File(
                        context.applicationInfo.dataDir,
                        "shared_prefs/lastfm_offline.xml"
                    )
                    tempLastFmOfflineFile.copyTo(targetLastFmFile, overwrite = true)
                    Timber.d("Last.fm offline scrobbles restored from backup")
                }

            } catch (e: Exception) {
                Timber.e(e, "Sync Merge failed")
                return@coroutineScope "Fallo al mezclar datos"
            } finally {
                tempZipFile.delete()
                if (tempDbFile.exists()) tempDbFile.delete()
                if (tempSettingsFile.exists()) tempSettingsFile.delete()
                if (tempAccountsFile.exists()) tempAccountsFile.delete()
                if (tempLastFmOfflineFile.exists()) tempLastFmOfflineFile.delete()
            }

            // 7. Upload merged database with all files
            val mergedBackupZip = java.io.File(context.cacheDir, "merged_backup.zip")
            try {
                // Check if database is still available before accessing it
                if (!database.isSafeToUse()) {
                    Timber.d("syncCloud: Database closed before upload, aborting")
                    return@coroutineScope null
                }

                // Get database path before any operations (validate DB is still open)
                val dbPath: String
                try {
                    database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
                        .moveToFirst()
                    dbPath = database.openHelper.writableDatabase.path
                        ?: throw IllegalStateException("Database path is null")
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("already-closed") == true) {
                        Timber.w("syncCloud: Database was closed during upload preparation")
                        return@coroutineScope null
                    }
                    throw e
                }
                
                java.io.FileOutputStream(mergedBackupZip).use { fos ->
                    java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(fos)).use { zos ->
                        // Add DB
                        zos.putNextEntry(java.util.zip.ZipEntry(com.anitail.music.db.InternalDatabase.DB_NAME))
                        java.io.FileInputStream(dbPath)
                            .use { fis -> fis.copyTo(zos) }

                        // Add Settings
                        val settingsFile =
                            java.io.File(context.filesDir, "datastore/settings.preferences_pb")
                        if (settingsFile.exists()) {
                            zos.putNextEntry(java.util.zip.ZipEntry("settings.preferences_pb"))
                            java.io.FileInputStream(settingsFile).use { fis -> fis.copyTo(zos) }
                        }

                        // Add Accounts (encrypted JSON)
                        val accountsJson = createAccountsJson()
                        if (accountsJson.isNotEmpty()) {
                            zos.putNextEntry(java.util.zip.ZipEntry("accounts.json"))
                            zos.write(accountsJson.toByteArray())
                        }

                        // Add Last.fm offline scrobbles
                        val lastFmOfflineFile = java.io.File(
                            context.applicationInfo.dataDir,
                            "shared_prefs/lastfm_offline.xml"
                        )
                        if (lastFmOfflineFile.exists()) {
                            zos.putNextEntry(java.util.zip.ZipEntry("lastfm_offline.xml"))
                            java.io.FileInputStream(lastFmOfflineFile)
                                .use { fis -> fis.copyTo(zos) }
                        }
                    }
                }

                val uploadResult = googleDriveSyncManager.uploadBackup(mergedBackupZip)
                if (uploadResult.isSuccess) {
                    Timber.d("syncCloud: Sync completed successfully")
                    return@coroutineScope "Sincronización completada"
                } else {
                    Timber.e("syncCloud: Failed to upload merged backup")
                    return@coroutineScope "Fallo al subir datos fusionados"
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create merged backup")
                return@coroutineScope "Fallo al crear backup local"
            } finally {
                mergedBackupZip.delete()
            }
        } else {
            // If no remote backup, upload local as ZIP
            Timber.d("syncCloud: No remote backup found, uploading initial backup...")
            val localBackupZip = java.io.File(context.cacheDir, "initial_backup.zip")
            try {
                // Check if database is still available before accessing it
                if (!database.isSafeToUse()) {
                    Timber.d("syncCloud: Database closed before initial upload, aborting")
                    return@coroutineScope null
                }

                // Get database path before any operations (validate DB is still open)
                val dbPath: String
                try {
                    database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
                        .moveToFirst()
                    dbPath = database.openHelper.writableDatabase.path
                        ?: throw IllegalStateException("Database path is null")
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("already-closed") == true) {
                        Timber.w("syncCloud: Database was closed during initial upload preparation")
                        return@coroutineScope null
                    }
                    throw e
                }
                
                java.io.FileOutputStream(localBackupZip).use { fos ->
                    java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(fos)).use { zos ->
                        zos.putNextEntry(java.util.zip.ZipEntry(com.anitail.music.db.InternalDatabase.DB_NAME))
                        java.io.FileInputStream(dbPath)
                            .use { fis -> fis.copyTo(zos) }

                        val settingsFile =
                            java.io.File(context.filesDir, "datastore/settings.preferences_pb")
                        if (settingsFile.exists()) {
                            zos.putNextEntry(java.util.zip.ZipEntry("settings.preferences_pb"))
                            java.io.FileInputStream(settingsFile).use { fis -> fis.copyTo(zos) }
                        }

                        // Add Accounts
                        val accountsJson = createAccountsJson()
                        if (accountsJson.isNotEmpty()) {
                            zos.putNextEntry(java.util.zip.ZipEntry("accounts.json"))
                            zos.write(accountsJson.toByteArray())
                        }

                        // Add Last.fm offline scrobbles
                        val lastFmOfflineFile = java.io.File(
                            context.applicationInfo.dataDir,
                            "shared_prefs/lastfm_offline.xml"
                        )
                        if (lastFmOfflineFile.exists()) {
                            zos.putNextEntry(java.util.zip.ZipEntry("lastfm_offline.xml"))
                            java.io.FileInputStream(lastFmOfflineFile)
                                .use { fis -> fis.copyTo(zos) }
                        }
                    }
                }

                val uploadResult = googleDriveSyncManager.uploadBackup(localBackupZip)
                if (uploadResult.isSuccess) {
                    Timber.d("syncCloud: Initial backup uploaded successfully")
                } else {
                    Timber.e("syncCloud: Failed to upload initial backup")
                }
                return@coroutineScope "Copia inicial subida"
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload initial backup")
                return@coroutineScope "Fallo al subir copia inicial"
            } finally {
                localBackupZip.delete()
            }
        }
            return@coroutineScope null
        }
    }

    private suspend fun createAccountsJson(): String {
        val dataStore = context.dataStore
        val prefs = dataStore.data.first()

        val accounts = mutableMapOf<String, String>()

        // YouTube account
        prefs[com.anitail.music.constants.InnerTubeCookieKey]?.let {
            accounts["innerTubeCookie"] = it
        }
        prefs[com.anitail.music.constants.VisitorDataKey]?.let { accounts["visitorData"] = it }
        prefs[com.anitail.music.constants.DataSyncIdKey]?.let { accounts["dataSyncId"] = it }
        prefs[com.anitail.music.constants.AccountNameKey]?.let { accounts["accountName"] = it }
        prefs[com.anitail.music.constants.AccountEmailKey]?.let { accounts["accountEmail"] = it }
        prefs[com.anitail.music.constants.AccountChannelHandleKey]?.let {
            accounts["accountChannelHandle"] = it
        }
        prefs[com.anitail.music.constants.AccountImageUrlKey]?.let {
            accounts["accountImageUrl"] = it
        }

        // Last.fm account
        prefs[com.anitail.music.constants.LastFmSessionKey]?.let {
            accounts["lastFmSessionKey"] = it
        }
        prefs[com.anitail.music.constants.LastFmUsernameKey]?.let {
            accounts["lastFmUsername"] = it
        }

        // Discord account
        prefs[com.anitail.music.constants.DiscordTokenKey]?.let { accounts["discordToken"] = it }
        prefs[com.anitail.music.constants.DiscordUsernameKey]?.let {
            accounts["discordUsername"] = it
        }
        prefs[com.anitail.music.constants.DiscordNameKey]?.let { accounts["discordName"] = it }
        prefs[com.anitail.music.constants.DiscordAvatarUrlKey]?.let {
            accounts["discordAvatarUrl"] = it
        }

        // Spotify account
        prefs[com.anitail.music.constants.SpotifyAccessTokenKey]?.let {
            accounts["spotifyAccessToken"] = it
        }
        prefs[com.anitail.music.constants.SpotifyRefreshTokenKey]?.let {
            accounts["spotifyRefreshToken"] = it
        }

        // Proxy settings (can contain passwords)
        prefs[com.anitail.music.constants.ProxyUrlKey]?.let { accounts["proxyUrl"] = it }
        prefs[com.anitail.music.constants.ProxyUsernameKey]?.let { accounts["proxyUsername"] = it }
        prefs[com.anitail.music.constants.ProxyPasswordKey]?.let { accounts["proxyPassword"] = it }

        if (accounts.isEmpty()) return ""

        // Encode as simple JSON (base64 encoded for basic obfuscation)
        val jsonBuilder = StringBuilder("{")
        accounts.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) jsonBuilder.append(",")
            jsonBuilder.append(
                "\"$key\":\"${
                    android.util.Base64.encodeToString(
                        value.toByteArray(),
                        android.util.Base64.NO_WRAP
                    )
                }\""
            )
        }
        jsonBuilder.append("}")
        return jsonBuilder.toString()
    }

    private suspend fun restoreAccounts(accountsFile: java.io.File) {
        try {
            val json = accountsFile.readText()
            if (json.isEmpty() || !json.startsWith("{")) return

            val dataStore = context.dataStore

            // Parse simple JSON manually to avoid adding dependencies
            val accountsMap = parseSimpleJson(json)

            dataStore.edit { prefs ->
                accountsMap["innerTubeCookie"]?.let {
                    prefs[com.anitail.music.constants.InnerTubeCookieKey] = decodeBase64(it)
                }
                accountsMap["visitorData"]?.let {
                    prefs[com.anitail.music.constants.VisitorDataKey] = decodeBase64(it)
                }
                accountsMap["dataSyncId"]?.let {
                    prefs[com.anitail.music.constants.DataSyncIdKey] = decodeBase64(it)
                }
                accountsMap["accountName"]?.let {
                    prefs[com.anitail.music.constants.AccountNameKey] = decodeBase64(it)
                }
                accountsMap["accountEmail"]?.let {
                    prefs[com.anitail.music.constants.AccountEmailKey] = decodeBase64(it)
                }
                accountsMap["accountChannelHandle"]?.let {
                    prefs[com.anitail.music.constants.AccountChannelHandleKey] = decodeBase64(it)
                }
                accountsMap["accountImageUrl"]?.let {
                    prefs[com.anitail.music.constants.AccountImageUrlKey] = decodeBase64(it)
                }

                accountsMap["lastFmSessionKey"]?.let {
                    prefs[com.anitail.music.constants.LastFmSessionKey] = decodeBase64(it)
                }
                accountsMap["lastFmUsername"]?.let {
                    prefs[com.anitail.music.constants.LastFmUsernameKey] = decodeBase64(it)
                }

                accountsMap["discordToken"]?.let {
                    prefs[com.anitail.music.constants.DiscordTokenKey] = decodeBase64(it)
                }
                accountsMap["discordUsername"]?.let {
                    prefs[com.anitail.music.constants.DiscordUsernameKey] = decodeBase64(it)
                }
                accountsMap["discordName"]?.let {
                    prefs[com.anitail.music.constants.DiscordNameKey] = decodeBase64(it)
                }
                accountsMap["discordAvatarUrl"]?.let {
                    prefs[com.anitail.music.constants.DiscordAvatarUrlKey] = decodeBase64(it)
                }

                accountsMap["spotifyAccessToken"]?.let {
                    prefs[com.anitail.music.constants.SpotifyAccessTokenKey] = decodeBase64(it)
                }
                accountsMap["spotifyRefreshToken"]?.let {
                    prefs[com.anitail.music.constants.SpotifyRefreshTokenKey] = decodeBase64(it)
                }

                accountsMap["proxyUrl"]?.let {
                    prefs[com.anitail.music.constants.ProxyUrlKey] = decodeBase64(it)
                }
                accountsMap["proxyUsername"]?.let {
                    prefs[com.anitail.music.constants.ProxyUsernameKey] = decodeBase64(it)
                }
                accountsMap["proxyPassword"]?.let {
                    prefs[com.anitail.music.constants.ProxyPasswordKey] = decodeBase64(it)
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
            String(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP))
        } catch (e: Exception) {
            encoded // Return as-is if decoding fails
        }
    }
}
