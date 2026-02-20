package com.anitail.desktop.storage

import com.anitail.desktop.auth.AuthCredentials
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.DesktopDatabaseSnapshot
import com.anitail.desktop.security.DesktopPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

data class DesktopBackupReport(
    val backupFile: Path,
    val includesSongDatabase: Boolean,
    val includesAccounts: Boolean,
)

data class DesktopRestoreReport(
    val restoredSongDatabase: Boolean,
    val restoredAccounts: Boolean,
    val restoredDesktopPreferences: Boolean,
    val restoredLyricsOverrides: Boolean,
    val restoredLastFmPending: Boolean,
    val warnings: List<String>,
)

data class DesktopCloudSyncReport(
    val hadRemoteBackup: Boolean,
    val uploadedFileId: String,
    val warnings: List<String>,
)

internal class DesktopBackupRestoreService(
    private val database: DesktopDatabase = DesktopDatabase.getInstance(),
    private val preferences: DesktopPreferences = DesktopPreferences.getInstance(),
    private val authService: DesktopAuthService = DesktopAuthService(),
    private val androidDatabaseAdapter: AndroidBackupDatabaseAdapter = AndroidBackupDatabaseAdapter(),
) {
    suspend fun backupTo(targetFile: Path): DesktopBackupReport = withContext(Dispatchers.IO) {
        targetFile.parent?.let { parent ->
            if (!Files.exists(parent)) Files.createDirectories(parent)
        }

        val snapshot = database.snapshot()
        val tempDir = Files.createTempDirectory("anitail_backup_")
        val tempSongDb = tempDir.resolve(ANDROID_DB_FILENAME)
        var includesAccounts = false

        try {
            androidDatabaseAdapter.write(snapshot = snapshot, targetDatabaseFile = tempSongDb)
            if (Files.exists(targetFile)) {
                Files.delete(targetFile)
            }

            ZipOutputStream(Files.newOutputStream(targetFile)).use { zip ->
                addFileToZip(zip, tempSongDb, ANDROID_DB_FILENAME)

                val accountsJson = buildAccountsJson()
                if (accountsJson != null) {
                    includesAccounts = true
                    addStringToZip(zip, ACCOUNTS_FILENAME, accountsJson.toString())
                }

                addFileToZipIfExists(
                    zip = zip,
                    file = DesktopPaths.preferencesFile(),
                    entryName = "$DESKTOP_DIR/preferences.json",
                )
                addFileToZipIfExists(
                    zip = zip,
                    file = DesktopPaths.androidSettingsPreferencesFile(),
                    entryName = SETTINGS_FILENAME,
                )
                addFileToZipIfExists(
                    zip = zip,
                    file = DesktopPaths.credentialsFile(),
                    entryName = "$DESKTOP_DIR/credentials.json",
                )
                addFileToZipIfExists(
                    zip = zip,
                    file = DesktopPaths.lyricsOverridesFile(),
                    entryName = "$DESKTOP_DIR/lyrics_overrides.json",
                )
                addFileToZipIfExists(
                    zip = zip,
                    file = DesktopPaths.lastFmPendingFile(),
                    entryName = "$DESKTOP_DIR/lastfm_pending_scrobbles.json",
                )
                val androidLastFmOfflineFile = DesktopPaths.androidLastFmOfflineFile()
                if (Files.exists(androidLastFmOfflineFile)) {
                    addFileToZip(
                        zip = zip,
                        source = androidLastFmOfflineFile,
                        entryName = LASTFM_OFFLINE_FILENAME,
                    )
                } else {
                    buildLastFmOfflineXmlFromPending(DesktopPaths.lastFmPendingFile())?.let { xml ->
                        addStringToZip(
                            zip = zip,
                            entryName = LASTFM_OFFLINE_FILENAME,
                            payload = xml,
                        )
                    }
                }
                addDirectoryToZipIfExists(
                    zip = zip,
                    directory = DesktopPaths.databaseDir(),
                    rootEntry = "$DESKTOP_DIR/database",
                )
            }

            DesktopBackupReport(
                backupFile = targetFile,
                includesSongDatabase = true,
                includesAccounts = includesAccounts,
            )
        } finally {
            deleteDirectoryRecursively(tempDir)
        }
    }

    suspend fun restoreFrom(backupFile: Path): DesktopRestoreReport = withContext(Dispatchers.IO) {
        require(Files.exists(backupFile)) { "Backup file not found: $backupFile" }

        val tempDir = Files.createTempDirectory("anitail_restore_")
        val warnings = mutableListOf<String>()
        var restoredSongDatabase = false
        var restoredAccounts = false
        var restoredDesktopPreferences = false
        var restoredLyricsOverrides = false
        var restoredLastFmPending = false

        val previousSnapshot = database.snapshot()

        try {
            unzip(backupFile, tempDir)

            val songDb = tempDir.resolve(ANDROID_DB_FILENAME)
            if (Files.exists(songDb)) {
                val snapshot = androidDatabaseAdapter.read(songDb)
                database.replaceAll(snapshot)
                restoredSongDatabase = true
            } else {
                val desktopDbDir = tempDir.resolve("$DESKTOP_DIR/database")
                if (Files.exists(desktopDbDir)) {
                    replaceDirectory(desktopDbDir, DesktopPaths.databaseDir())
                    database.initialize()
                    restoredSongDatabase = true
                    warnings += "song.db not found; restored desktop database files instead."
                } else {
                    warnings += "No database payload found in backup."
                }
            }

            val desktopPreferences = tempDir.resolve("$DESKTOP_DIR/preferences.json")
            if (Files.exists(desktopPreferences)) {
                copyFile(desktopPreferences, DesktopPaths.preferencesFile())
                restoredDesktopPreferences = true
            }

            val androidSettingsPreferences = tempDir.resolve(SETTINGS_FILENAME)
            if (Files.exists(androidSettingsPreferences)) {
                copyFile(androidSettingsPreferences, DesktopPaths.androidSettingsPreferencesFile())
                warnings += "settings.preferences_pb restored as passthrough (not applied to desktop preferences)."
            }

            val desktopCredentials = tempDir.resolve("$DESKTOP_DIR/credentials.json")
            if (Files.exists(desktopCredentials)) {
                copyFile(desktopCredentials, DesktopPaths.credentialsFile())
            }

            val desktopLyricsOverrides = tempDir.resolve("$DESKTOP_DIR/lyrics_overrides.json")
            if (Files.exists(desktopLyricsOverrides)) {
                copyFile(desktopLyricsOverrides, DesktopPaths.lyricsOverridesFile())
                restoredLyricsOverrides = true
            }

            val desktopLastFmPending = tempDir.resolve("$DESKTOP_DIR/lastfm_pending_scrobbles.json")
            if (Files.exists(desktopLastFmPending)) {
                copyFile(desktopLastFmPending, DesktopPaths.lastFmPendingFile())
                restoredLastFmPending = true
            }

            val androidLastFmOffline = tempDir.resolve(LASTFM_OFFLINE_FILENAME)
            if (Files.exists(androidLastFmOffline)) {
                copyFile(androidLastFmOffline, DesktopPaths.androidLastFmOfflineFile())
                val jsonFromXml = extractPendingScrobblesJsonFromLastFmOfflineXml(androidLastFmOffline)
                if (!jsonFromXml.isNullOrBlank()) {
                    DesktopPaths.lastFmPendingFile().parent?.let { parent ->
                        if (!Files.exists(parent)) Files.createDirectories(parent)
                    }
                    Files.writeString(
                        DesktopPaths.lastFmPendingFile(),
                        jsonFromXml,
                        StandardCharsets.UTF_8,
                    )
                    restoredLastFmPending = true
                }
            }

            if (restoredDesktopPreferences) {
                preferences.load()
            }
            authService.loadCredentials()

            val accountsFile = tempDir.resolve(ACCOUNTS_FILENAME)
            if (Files.exists(accountsFile)) {
                restoredAccounts = applyAccountsJson(accountsFile)
            }

            DesktopRestoreReport(
                restoredSongDatabase = restoredSongDatabase,
                restoredAccounts = restoredAccounts,
                restoredDesktopPreferences = restoredDesktopPreferences,
                restoredLyricsOverrides = restoredLyricsOverrides,
                restoredLastFmPending = restoredLastFmPending,
                warnings = warnings.toList(),
            )
        } catch (error: Throwable) {
            database.replaceAll(previousSnapshot)
            throw error
        } finally {
            deleteDirectoryRecursively(tempDir)
        }
    }

    suspend fun syncWithDriveSmartMerge(
        downloadLatestBackup: suspend (Path) -> Result<Path>,
        uploadBackup: suspend (Path) -> Result<String>,
    ): DesktopCloudSyncReport = withContext(Dispatchers.IO) {
        val localSnapshot = database.snapshot()
        val downloadFile = Files.createTempFile("anitail_drive_sync_remote_", ".backup")
        var hadRemoteBackup = false
        var warnings = emptyList<String>()

        try {
            val remoteBackupPath = downloadLatestBackup(downloadFile)
            if (remoteBackupPath.isSuccess) {
                hadRemoteBackup = true
                val restoreReport = restoreFrom(remoteBackupPath.getOrThrow())
                warnings = restoreReport.warnings

                val remoteSnapshot = database.snapshot()
                val mergedSnapshot = mergeSnapshots(
                    local = localSnapshot,
                    remote = remoteSnapshot,
                )
                database.replaceAll(mergedSnapshot)
            } else {
                val error = remoteBackupPath.exceptionOrNull()
                if (!isNoRemoteBackupError(error)) {
                    throw (error ?: IllegalStateException("Failed to download backup from Drive."))
                }
            }

            val uploadFile = Files.createTempFile("anitail_drive_sync_local_", ".backup")
            try {
                backupTo(uploadFile)
                val uploadedFileId = uploadBackup(uploadFile).getOrThrow()
                DesktopCloudSyncReport(
                    hadRemoteBackup = hadRemoteBackup,
                    uploadedFileId = uploadedFileId,
                    warnings = warnings,
                )
            } finally {
                Files.deleteIfExists(uploadFile)
            }
        } catch (error: Throwable) {
            database.replaceAll(localSnapshot)
            throw error
        } finally {
            Files.deleteIfExists(downloadFile)
        }
    }

    private fun buildAccountsJson(): JSONObject? {
        val accounts = mutableMapOf<String, String>()
        val credentials = authService.credentials

        credentials?.cookie?.takeIf { it.isNotBlank() }?.let { accounts["innerTubeCookie"] = it }
        credentials?.visitorData?.takeIf { it.isNotBlank() }?.let { accounts["visitorData"] = it }
        credentials?.dataSyncId?.takeIf { it.isNotBlank() }?.let { accounts["dataSyncId"] = it }
        credentials?.accountName?.takeIf { it.isNotBlank() }?.let { accounts["accountName"] = it }
        credentials?.accountEmail?.takeIf { it.isNotBlank() }?.let { accounts["accountEmail"] = it }
        credentials?.channelHandle?.takeIf { it.isNotBlank() }
            ?.let { accounts["accountChannelHandle"] = it }
        credentials?.accountImageUrl?.takeIf { it.isNotBlank() }
            ?.let { accounts["accountImageUrl"] = it }

        preferences.lastFmSessionKey.value.takeIf { it.isNotBlank() }
            ?.let { accounts["lastFmSessionKey"] = it }
        preferences.lastFmUsername.value.takeIf { it.isNotBlank() }
            ?.let { accounts["lastFmUsername"] = it }

        preferences.discordToken.value.takeIf { it.isNotBlank() }
            ?.let { accounts["discordToken"] = it }
        preferences.discordUsername.value.takeIf { it.isNotBlank() }
            ?.let { accounts["discordUsername"] = it }
        preferences.discordName.value.takeIf { it.isNotBlank() }
            ?.let { accounts["discordName"] = it }
        preferences.discordAvatarUrl.value.takeIf { it.isNotBlank() }
            ?.let { accounts["discordAvatarUrl"] = it }

        preferences.spotifyAccessToken.value.takeIf { it.isNotBlank() }
            ?.let { accounts["spotifyAccessToken"] = it }
        preferences.spotifyRefreshToken.value.takeIf { it.isNotBlank() }
            ?.let { accounts["spotifyRefreshToken"] = it }

        preferences.proxyUrl.value.takeIf { it.isNotBlank() && it != "host:port" }
            ?.let { accounts["proxyUrl"] = it }
        preferences.proxyUsername.value.takeIf { it.isNotBlank() }
            ?.let { accounts["proxyUsername"] = it }
        preferences.proxyPassword.value.takeIf { it.isNotBlank() }
            ?.let { accounts["proxyPassword"] = it }

        if (accounts.isEmpty()) return null

        val json = JSONObject()
        val encoder = Base64.getEncoder()
        accounts.forEach { (key, value) ->
            val encoded = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
            json.put(key, encoded)
        }
        return json
    }

    private suspend fun applyAccountsJson(accountsFile: Path): Boolean {
        val raw = Files.readString(accountsFile, StandardCharsets.UTF_8)
        if (raw.isBlank()) return false

        val json = JSONObject(raw)
        if (!json.keys().hasNext()) return false

        val decoded = mutableMapOf<String, String>()
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val encoded = json.optString(key, "").trim()
            if (encoded.isBlank()) continue
            decoded[key] = decodeBase64Safely(encoded)
        }

        val current = authService.credentials ?: AuthCredentials()
        val updatedCredentials = current.copy(
            visitorData = decoded["visitorData"] ?: current.visitorData,
            dataSyncId = decoded["dataSyncId"] ?: current.dataSyncId,
            cookie = decoded["innerTubeCookie"] ?: current.cookie,
            accountName = decoded["accountName"] ?: current.accountName,
            accountEmail = decoded["accountEmail"] ?: current.accountEmail,
            channelHandle = decoded["accountChannelHandle"] ?: current.channelHandle,
            accountImageUrl = decoded["accountImageUrl"] ?: current.accountImageUrl,
        )
        authService.saveCredentials(updatedCredentials)

        decoded["innerTubeCookie"]?.let { preferences.setYoutubeCookie(it) }

        decoded["lastFmSessionKey"]?.let {
            preferences.setLastFmSessionKey(it)
            preferences.setLastFmEnabled(it.isNotBlank())
        }
        decoded["lastFmUsername"]?.let { preferences.setLastFmUsername(it) }

        decoded["discordToken"]?.let { preferences.setDiscordToken(it) }
        decoded["discordUsername"]?.let { preferences.setDiscordUsername(it) }
        decoded["discordName"]?.let { preferences.setDiscordName(it) }
        decoded["discordAvatarUrl"]?.let { preferences.setDiscordAvatarUrl(it) }

        decoded["spotifyAccessToken"]?.let { preferences.setSpotifyAccessToken(it) }
        decoded["spotifyRefreshToken"]?.let { preferences.setSpotifyRefreshToken(it) }

        decoded["proxyUrl"]?.let {
            preferences.setProxyUrl(it)
            preferences.setProxyEnabled(it.isNotBlank())
        }
        decoded["proxyUsername"]?.let { preferences.setProxyUsername(it) }
        decoded["proxyPassword"]?.let { preferences.setProxyPassword(it) }

        return decoded.isNotEmpty()
    }

    private fun decodeBase64Safely(encoded: String): String {
        return runCatching {
            String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
        }.getOrElse { encoded }
    }

    private fun mergeSnapshots(
        local: DesktopDatabaseSnapshot,
        remote: DesktopDatabaseSnapshot,
    ): DesktopDatabaseSnapshot {
        val mergedSongs = mergeSongs(local.songs, remote.songs)
        val mergedArtists = mergeArtists(local.artists, remote.artists)
        val mergedAlbums = mergeAlbums(local.albums, remote.albums)
        val mergedPlaylists = mergePlaylists(local.playlists, remote.playlists)
        val mergedPlaylistSongMaps = mergePlaylistSongMaps(
            localMaps = local.playlistSongMaps,
            remoteMaps = remote.playlistSongMaps,
        )
        val mergedSongArtistMaps = mergeSongArtistMaps(local.songArtistMaps, remote.songArtistMaps)
        val mergedRelatedSongMaps = mergeRelatedSongMaps(local.relatedSongMaps, remote.relatedSongMaps)
        val mergedEvents = mergeEvents(local.events, remote.events)
        val mergedSearchHistory = mergeSearchHistory(local.searchHistory, remote.searchHistory)

        return DesktopDatabaseSnapshot(
            songs = mergedSongs,
            artists = mergedArtists,
            albums = mergedAlbums,
            playlists = mergedPlaylists,
            playlistSongMaps = mergedPlaylistSongMaps,
            songArtistMaps = mergedSongArtistMaps,
            relatedSongMaps = mergedRelatedSongMaps,
            events = mergedEvents,
            searchHistory = mergedSearchHistory,
        )
    }

    private fun mergeSongs(
        localSongs: List<com.anitail.desktop.db.entities.SongEntity>,
        remoteSongs: List<com.anitail.desktop.db.entities.SongEntity>,
    ): List<com.anitail.desktop.db.entities.SongEntity> {
        val localById = localSongs.associateBy { it.id }
        val remoteById = remoteSongs.associateBy { it.id }
        return (localById.keys + remoteById.keys).mapNotNull { id ->
            val local = localById[id]
            val remote = remoteById[id]
            when {
                local != null && remote != null -> database.mergeSong(local, remote)
                remote != null -> remote
                else -> local
            }
        }
    }

    private fun mergeArtists(
        localArtists: List<com.anitail.desktop.db.entities.ArtistEntity>,
        remoteArtists: List<com.anitail.desktop.db.entities.ArtistEntity>,
    ): List<com.anitail.desktop.db.entities.ArtistEntity> {
        val localById = localArtists.associateBy { it.id }
        val remoteById = remoteArtists.associateBy { it.id }
        return (localById.keys + remoteById.keys).mapNotNull { id ->
            val local = localById[id]
            val remote = remoteById[id]
            when {
                local != null && remote != null -> {
                    val newer = if (remote.lastUpdateTime.isAfter(local.lastUpdateTime)) remote else local
                    newer.copy(
                        name = if (newer.name.isNotBlank()) newer.name else local.name,
                        thumbnailUrl = remote.thumbnailUrl ?: local.thumbnailUrl,
                        channelId = remote.channelId ?: local.channelId,
                        lastUpdateTime = maxOf(local.lastUpdateTime, remote.lastUpdateTime),
                        bookmarkedAt = maxDateTime(local.bookmarkedAt, remote.bookmarkedAt),
                    )
                }
                remote != null -> remote
                else -> local
            }
        }
    }

    private fun mergeAlbums(
        localAlbums: List<com.anitail.desktop.db.entities.AlbumEntity>,
        remoteAlbums: List<com.anitail.desktop.db.entities.AlbumEntity>,
    ): List<com.anitail.desktop.db.entities.AlbumEntity> {
        val localById = localAlbums.associateBy { it.id }
        val remoteById = remoteAlbums.associateBy { it.id }
        return (localById.keys + remoteById.keys).mapNotNull { id ->
            val local = localById[id]
            val remote = remoteById[id]
            when {
                local != null && remote != null -> {
                    val newer = if (remote.lastUpdateTime.isAfter(local.lastUpdateTime)) remote else local
                    newer.copy(
                        playlistId = remote.playlistId ?: local.playlistId,
                        year = remote.year ?: local.year,
                        thumbnailUrl = remote.thumbnailUrl ?: local.thumbnailUrl,
                        themeColor = remote.themeColor ?: local.themeColor,
                        songCount = maxOf(local.songCount, remote.songCount),
                        duration = maxOf(local.duration, remote.duration),
                        lastUpdateTime = maxOf(local.lastUpdateTime, remote.lastUpdateTime),
                        bookmarkedAt = maxDateTime(local.bookmarkedAt, remote.bookmarkedAt),
                        likedDate = maxDateTime(local.likedDate, remote.likedDate),
                        inLibrary = maxDateTime(local.inLibrary, remote.inLibrary),
                    )
                }
                remote != null -> remote
                else -> local
            }
        }
    }

    private fun mergePlaylists(
        localPlaylists: List<com.anitail.desktop.db.entities.PlaylistEntity>,
        remotePlaylists: List<com.anitail.desktop.db.entities.PlaylistEntity>,
    ): List<com.anitail.desktop.db.entities.PlaylistEntity> {
        val localById = localPlaylists.associateBy { it.id }
        val remoteById = remotePlaylists.associateBy { it.id }
        return (localById.keys + remoteById.keys).mapNotNull { id ->
            val local = localById[id]
            val remote = remoteById[id]
            when {
                local != null && remote != null -> database.mergePlaylist(local, remote)
                remote != null -> remote
                else -> local
            }
        }
    }

    private fun mergePlaylistSongMaps(
        localMaps: List<com.anitail.desktop.db.entities.PlaylistSongMap>,
        remoteMaps: List<com.anitail.desktop.db.entities.PlaylistSongMap>,
    ): List<com.anitail.desktop.db.entities.PlaylistSongMap> {
        val byPlaylist = linkedMapOf<String, MutableList<com.anitail.desktop.db.entities.PlaylistSongMap>>()
        val playlists = (localMaps.map { it.playlistId } + remoteMaps.map { it.playlistId }).distinct()

        playlists.forEach { playlistId ->
            val remoteList = remoteMaps.filter { it.playlistId == playlistId }.sortedBy { it.position }
            val localList = localMaps.filter { it.playlistId == playlistId }.sortedBy { it.position }
            val merged = mutableListOf<com.anitail.desktop.db.entities.PlaylistSongMap>()
            val seenSongIds = mutableSetOf<String>()

            remoteList.forEach { map ->
                if (seenSongIds.add(map.songId)) merged += map
            }
            localList.forEach { map ->
                if (seenSongIds.add(map.songId)) merged += map
            }

            byPlaylist[playlistId] = merged
        }

        var nextId = 1
        val normalized = mutableListOf<com.anitail.desktop.db.entities.PlaylistSongMap>()
        byPlaylist.forEach { (playlistId, maps) ->
            maps.forEachIndexed { index, map ->
                normalized += map.copy(
                    id = nextId++,
                    playlistId = playlistId,
                    position = index,
                )
            }
        }
        return normalized
    }

    private fun mergeSongArtistMaps(
        localMaps: List<com.anitail.desktop.db.entities.SongArtistMap>,
        remoteMaps: List<com.anitail.desktop.db.entities.SongArtistMap>,
    ): List<com.anitail.desktop.db.entities.SongArtistMap> {
        val byKey = linkedMapOf<Pair<String, String>, com.anitail.desktop.db.entities.SongArtistMap>()
        (remoteMaps + localMaps).forEach { map ->
            val key = map.songId to map.artistId
            val existing = byKey[key]
            if (existing == null || map.position < existing.position) {
                byKey[key] = map
            }
        }
        return byKey.values.toList()
    }

    private fun mergeRelatedSongMaps(
        localMaps: List<com.anitail.desktop.db.entities.RelatedSongMap>,
        remoteMaps: List<com.anitail.desktop.db.entities.RelatedSongMap>,
    ): List<com.anitail.desktop.db.entities.RelatedSongMap> {
        val keys = linkedSetOf<Pair<String, String>>()
        val merged = mutableListOf<com.anitail.desktop.db.entities.RelatedSongMap>()
        (remoteMaps + localMaps).forEach { map ->
            val key = map.songId to map.relatedSongId
            if (keys.add(key)) {
                merged += map
            }
        }
        return merged.mapIndexed { index, map -> map.copy(id = index.toLong() + 1L) }
    }

    private fun mergeEvents(
        localEvents: List<com.anitail.desktop.db.entities.EventEntity>,
        remoteEvents: List<com.anitail.desktop.db.entities.EventEntity>,
    ): List<com.anitail.desktop.db.entities.EventEntity> {
        val keys = linkedSetOf<Triple<String, java.time.LocalDateTime, Long>>()
        val merged = mutableListOf<com.anitail.desktop.db.entities.EventEntity>()
        (remoteEvents + localEvents).forEach { event ->
            val key = Triple(event.songId, event.timestamp, event.playTime)
            if (keys.add(key)) {
                merged += event
            }
        }
        return merged
            .sortedByDescending { it.timestamp }
            .mapIndexed { index, event -> event.copy(id = index.toLong() + 1L) }
    }

    private fun mergeSearchHistory(
        localHistory: List<com.anitail.desktop.db.entities.SearchHistory>,
        remoteHistory: List<com.anitail.desktop.db.entities.SearchHistory>,
    ): List<com.anitail.desktop.db.entities.SearchHistory> {
        val byQuery = linkedMapOf<String, com.anitail.desktop.db.entities.SearchHistory>()
        (localHistory + remoteHistory).forEach { entry ->
            val existing = byQuery[entry.query]
            if (existing == null || entry.id > existing.id) {
                byQuery[entry.query] = entry
            }
        }
        return byQuery.values
            .sortedBy { it.id }
            .mapIndexed { index, entry -> entry.copy(id = index.toLong() + 1L) }
    }

    private fun isNoRemoteBackupError(error: Throwable?): Boolean {
        if (error == null) return false
        val message = error.message.orEmpty().lowercase()
        return message.contains("no backups found")
    }

    private fun maxDateTime(
        left: java.time.LocalDateTime?,
        right: java.time.LocalDateTime?,
    ): java.time.LocalDateTime? {
        return when {
            left == null -> right
            right == null -> left
            right.isAfter(left) -> right
            else -> left
        }
    }

    private fun buildLastFmOfflineXmlFromPending(pendingJsonFile: Path): String? {
        if (!Files.exists(pendingJsonFile)) return null
        val pendingJson = Files.readString(pendingJsonFile, StandardCharsets.UTF_8).trim()
        if (pendingJson.isBlank() || pendingJson == "[]") return null
        val escaped = pendingJson
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        return buildString {
            append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
            append("<map>")
            append("<string name=\"pending_scrobbles\">")
            append(escaped)
            append("</string>")
            append("</map>")
        }
    }

    private fun extractPendingScrobblesJsonFromLastFmOfflineXml(xmlFile: Path): String? {
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder()
            val document = Files.newInputStream(xmlFile).use { input -> builder.parse(input) }
            val nodes = document.getElementsByTagName("string")
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val attributes = node.attributes ?: continue
                val name = attributes.getNamedItem("name")?.nodeValue ?: continue
                if (name == "pending_scrobbles") {
                    val value = node.textContent.orEmpty().trim()
                    if (value.isNotBlank()) {
                        return@runCatching value
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun addFileToZipIfExists(zip: ZipOutputStream, file: Path, entryName: String) {
        if (!Files.exists(file)) return
        addFileToZip(zip, file, entryName)
    }

    private fun addDirectoryToZipIfExists(zip: ZipOutputStream, directory: Path, rootEntry: String) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { walk ->
            walk.filter { Files.isRegularFile(it) }.forEach { file ->
                val relative = directory.relativize(file).toString().replace("\\", "/")
                addFileToZip(zip, file, "$rootEntry/$relative")
            }
        }
    }

    private fun addFileToZip(zip: ZipOutputStream, source: Path, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        Files.newInputStream(source).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addStringToZip(zip: ZipOutputStream, entryName: String, payload: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(payload.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun unzip(sourceZip: Path, destinationDir: Path) {
        ZipInputStream(Files.newInputStream(sourceZip)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = safeResolveZipEntry(destinationDir, entry)
                if (entry.isDirectory) {
                    if (!Files.exists(target)) Files.createDirectories(target)
                } else {
                    target.parent?.let { parent ->
                        if (!Files.exists(parent)) Files.createDirectories(parent)
                    }
                    Files.newOutputStream(target).use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun safeResolveZipEntry(destinationDir: Path, entry: ZipEntry): Path {
        val normalizedDestination = destinationDir.toAbsolutePath().normalize()
        val resolved = normalizedDestination.resolve(entry.name).normalize()
        require(resolved.startsWith(normalizedDestination)) {
            "Invalid zip entry path: ${entry.name}"
        }
        return resolved
    }

    private fun copyFile(source: Path, target: Path) {
        target.parent?.let { parent ->
            if (!Files.exists(parent)) Files.createDirectories(parent)
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun replaceDirectory(sourceDir: Path, targetDir: Path) {
        deleteDirectoryRecursively(targetDir)
        Files.createDirectories(targetDir)
        Files.walk(sourceDir).use { walk ->
            walk.forEach { source ->
                val relative = sourceDir.relativize(source)
                val target = targetDir.resolve(relative.toString())
                if (Files.isDirectory(source)) {
                    if (!Files.exists(target)) Files.createDirectories(target)
                } else {
                    target.parent?.let { parent ->
                        if (!Files.exists(parent)) Files.createDirectories(parent)
                    }
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun deleteDirectoryRecursively(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { walk ->
            walk.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    companion object {
        private const val ANDROID_DB_FILENAME = "song.db"
        private const val ACCOUNTS_FILENAME = "accounts.json"
        private const val SETTINGS_FILENAME = "settings.preferences_pb"
        private const val LASTFM_OFFLINE_FILENAME = "lastfm_offline.xml"
        private const val DESKTOP_DIR = "desktop"
    }
}
