package com.anitail.desktop.storage

import com.anitail.desktop.db.DesktopDatabaseSnapshot
import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.PlaylistSongMap
import com.anitail.desktop.db.entities.RelatedSongMap
import com.anitail.desktop.db.entities.SearchHistory
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDateTime
import java.time.ZoneOffset

internal class AndroidBackupDatabaseAdapter(
    private val schemaFile: Path = Path.of(
        "app",
        "schemas",
        "com.anitail.music.db.InternalDatabase",
        "26.json",
    ),
) {
    fun write(snapshot: DesktopDatabaseSnapshot, targetDatabaseFile: Path) {
        targetDatabaseFile.parent?.let { parent ->
            if (!Files.exists(parent)) Files.createDirectories(parent)
        }
        if (Files.exists(targetDatabaseFile)) {
            Files.delete(targetDatabaseFile)
        }

        val connection = DriverManager.getConnection("jdbc:sqlite:${targetDatabaseFile.toAbsolutePath()}")
        connection.use { db ->
            db.autoCommit = false
            createSchema(db)
            insertSnapshot(db, snapshot)
            db.commit()
        }
    }

    fun read(sourceDatabaseFile: Path): DesktopDatabaseSnapshot {
        val connection = DriverManager.getConnection("jdbc:sqlite:${sourceDatabaseFile.toAbsolutePath()}")
        connection.use { db ->
            return DesktopDatabaseSnapshot(
                songs = readSongs(db),
                artists = readArtists(db),
                albums = readAlbums(db),
                playlists = readPlaylists(db),
                playlistSongMaps = readPlaylistSongMaps(db),
                songArtistMaps = readSongArtistMaps(db),
                relatedSongMaps = readRelatedSongMaps(db),
                events = readEvents(db),
                searchHistory = readSearchHistory(db),
            )
        }
    }

    private fun createSchema(db: Connection) {
        val resolvedSchema = resolveSchemaFile()
        require(resolvedSchema != null && Files.exists(resolvedSchema)) {
            "Room schema not found: $schemaFile"
        }

        val schema = JSONObject(Files.readString(resolvedSchema, StandardCharsets.UTF_8))
        val database = schema.getJSONObject("database")

        db.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = OFF")

            val entities = database.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql")
                    .replace("`${'$'}{TABLE_NAME}`", "`$tableName`")
                statement.execute(createSql)

                val indices = entity.optJSONArray("indices") ?: continue
                for (i in 0 until indices.length()) {
                    val indexSql = indices.getJSONObject(i).getString("createSql")
                        .replace("`${'$'}{TABLE_NAME}`", "`$tableName`")
                    statement.execute(indexSql)
                }
            }

            val views = database.optJSONArray("views")
            if (views != null) {
                for (index in 0 until views.length()) {
                    val view = views.getJSONObject(index)
                    val viewName = view.getString("viewName")
                    val viewSql = view.getString("createSql")
                        .replace("`${'$'}{VIEW_NAME}`", "`$viewName`")
                    statement.execute(viewSql)
                }
            }

            val setupQueries = database.getJSONArray("setupQueries")
            for (index in 0 until setupQueries.length()) {
                statement.execute(setupQueries.getString(index))
            }

            statement.execute("PRAGMA user_version = ${database.getInt("version")}")
            statement.execute("PRAGMA foreign_keys = ON")
        }
    }

    private fun resolveSchemaFile(): Path? {
        if (Files.exists(schemaFile)) return schemaFile
        if (schemaFile.isAbsolute) return null

        val parentCandidate = Path.of("..").resolve(schemaFile).normalize()
        if (Files.exists(parentCandidate)) return parentCandidate

        val schemaDirRelative = schemaFile.parent ?: return null
        val schemaDirCandidates = listOf(
            schemaDirRelative,
            Path.of("..").resolve(schemaDirRelative).normalize(),
        )
        schemaDirCandidates.forEach { directory ->
            val latest = latestSchemaInDirectory(directory)
            if (latest != null) return latest
        }
        return null
    }

    private fun latestSchemaInDirectory(directory: Path): Path? {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) return null
        return Files.list(directory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".json", ignoreCase = true) }
                .max(
                    compareBy<Path> {
                        it.fileName.toString().substringBefore('.').toIntOrNull() ?: -1
                    }.thenBy { it.fileName.toString() },
                )
                .orElse(null)
        }
    }

    private fun insertSnapshot(db: Connection, snapshot: DesktopDatabaseSnapshot) {
        insertArtists(db, snapshot.artists)
        insertAlbums(db, snapshot.albums)
        insertPlaylists(db, snapshot.playlists)
        insertSongs(db, snapshot.songs)
        insertSongArtistMaps(db, snapshot.songArtistMaps)
        insertSongAlbumMaps(db, snapshot.songs)
        insertAlbumArtistMaps(db, snapshot.songs, snapshot.songArtistMaps)
        insertPlaylistSongMaps(db, snapshot.playlistSongMaps)
        insertSetVideoIds(db, snapshot.playlistSongMaps)
        insertRelatedSongMaps(db, snapshot.relatedSongMaps)
        insertEvents(db, snapshot.events)
        insertSearchHistory(db, snapshot.searchHistory)
    }

    private fun insertSongs(db: Connection, songs: List<SongEntity>) {
        val sql = """
            INSERT OR REPLACE INTO song(
                id, title, duration, thumbnailUrl, albumId, albumName, artistName, explicit, year,
                date, dateModified, liked, likedDate, totalPlayTime, inLibrary, dateDownload, isLocal,
                romanizeLyrics, mediaStoreUri
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        db.prepareStatement(sql).use { statement ->
            songs.forEach { song ->
                statement.setString(1, song.id)
                statement.setString(2, song.title)
                statement.setInt(3, song.duration)
                statement.setNullableString(4, song.thumbnailUrl)
                statement.setNullableString(5, song.albumId)
                statement.setNullableString(6, song.albumName)
                statement.setNullableString(7, song.artistName)
                statement.setInt(8, song.explicit.toSqlBool())
                statement.setNullableInt(9, song.year)
                statement.setNullableLong(10, song.date.toEpochMillisUtc())
                statement.setNullableLong(11, song.dateModified.toEpochMillisUtc())
                statement.setInt(12, song.liked.toSqlBool())
                statement.setNullableLong(13, song.likedDate.toEpochMillisUtc())
                statement.setLong(14, song.totalPlayTime)
                statement.setNullableLong(15, song.inLibrary.toEpochMillisUtc())
                statement.setNullableLong(16, song.dateDownload.toEpochMillisUtc())
                statement.setInt(17, song.isLocal.toSqlBool())
                statement.setInt(18, song.romanizeLyrics.toSqlBool())
                statement.setNullableString(19, song.mediaStoreUri)
                statement.executeUpdate()
            }
        }
    }

    private fun insertArtists(db: Connection, artists: List<ArtistEntity>) {
        val sql = """
            INSERT OR REPLACE INTO artist(id, name, thumbnailUrl, channelId, lastUpdateTime, bookmarkedAt)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        db.prepareStatement(sql).use { statement ->
            artists.forEach { artist ->
                statement.setString(1, artist.id)
                statement.setString(2, artist.name)
                statement.setNullableString(3, artist.thumbnailUrl)
                statement.setNullableString(4, artist.channelId)
                statement.setLong(5, artist.lastUpdateTime.toEpochMillisUtcRequired())
                statement.setNullableLong(6, artist.bookmarkedAt.toEpochMillisUtc())
                statement.executeUpdate()
            }
        }
    }

    private fun insertAlbums(db: Connection, albums: List<AlbumEntity>) {
        val sql = """
            INSERT OR REPLACE INTO album(
                id, playlistId, title, year, thumbnailUrl, themeColor, songCount, duration,
                lastUpdateTime, bookmarkedAt, likedDate, inLibrary
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        db.prepareStatement(sql).use { statement ->
            albums.forEach { album ->
                statement.setString(1, album.id)
                statement.setNullableString(2, album.playlistId)
                statement.setString(3, album.title)
                statement.setNullableInt(4, album.year)
                statement.setNullableString(5, album.thumbnailUrl)
                statement.setNullableInt(6, album.themeColor)
                statement.setInt(7, album.songCount)
                statement.setInt(8, album.duration)
                statement.setLong(9, album.lastUpdateTime.toEpochMillisUtcRequired())
                statement.setNullableLong(10, album.bookmarkedAt.toEpochMillisUtc())
                statement.setNullableLong(11, album.likedDate.toEpochMillisUtc())
                statement.setNullableLong(12, album.inLibrary.toEpochMillisUtc())
                statement.executeUpdate()
            }
        }
    }

    private fun insertPlaylists(db: Connection, playlists: List<PlaylistEntity>) {
        val sql = """
            INSERT OR REPLACE INTO playlist(
                id, name, browseId, createdAt, lastUpdateTime, isEditable, bookmarkedAt, remoteSongCount,
                playEndpointParams, thumbnailUrl, shuffleEndpointParams, radioEndpointParams, backgroundImageUrl
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        db.prepareStatement(sql).use { statement ->
            playlists.forEach { playlist ->
                statement.setString(1, playlist.id)
                statement.setString(2, playlist.name)
                statement.setNullableString(3, playlist.browseId)
                statement.setNullableLong(4, playlist.createdAt.toEpochMillisUtc())
                statement.setNullableLong(5, playlist.lastUpdateTime.toEpochMillisUtc())
                statement.setInt(6, playlist.isEditable.toSqlBool())
                statement.setNullableLong(7, playlist.bookmarkedAt.toEpochMillisUtc())
                statement.setNullableInt(8, playlist.remoteSongCount)
                statement.setNullableString(9, playlist.playEndpointParams)
                statement.setNullableString(10, playlist.thumbnailUrl)
                statement.setNullableString(11, playlist.shuffleEndpointParams)
                statement.setNullableString(12, playlist.radioEndpointParams)
                statement.setNullableString(13, playlist.backgroundImageUrl)
                statement.executeUpdate()
            }
        }
    }

    private fun insertSongArtistMaps(db: Connection, maps: List<SongArtistMap>) {
        val sql = "INSERT OR REPLACE INTO song_artist_map(songId, artistId, position) VALUES (?, ?, ?)"
        db.prepareStatement(sql).use { statement ->
            maps.forEach { map ->
                statement.setString(1, map.songId)
                statement.setString(2, map.artistId)
                statement.setInt(3, map.position)
                statement.executeUpdate()
            }
        }
    }

    private fun insertSongAlbumMaps(db: Connection, songs: List<SongEntity>) {
        val sql = "INSERT OR REPLACE INTO song_album_map(songId, albumId, `index`) VALUES (?, ?, ?)"
        db.prepareStatement(sql).use { statement ->
            songs.forEach { song ->
                val albumId = song.albumId ?: return@forEach
                if (albumId.isBlank()) return@forEach
                statement.setString(1, song.id)
                statement.setString(2, albumId)
                statement.setInt(3, 0)
                statement.executeUpdate()
            }
        }
    }

    private fun insertAlbumArtistMaps(
        db: Connection,
        songs: List<SongEntity>,
        songArtistMaps: List<SongArtistMap>,
    ) {
        val songAlbumById = songs.associate { it.id to it.albumId }
        val sql = "INSERT OR REPLACE INTO album_artist_map(albumId, artistId, `order`) VALUES (?, ?, ?)"
        db.prepareStatement(sql).use { statement ->
            val inserted = mutableSetOf<Pair<String, String>>()
            songArtistMaps.sortedBy { it.position }.forEach { map ->
                val albumId = songAlbumById[map.songId] ?: return@forEach
                if (albumId.isBlank()) return@forEach
                val key = albumId to map.artistId
                if (!inserted.add(key)) return@forEach
                statement.setString(1, albumId)
                statement.setString(2, map.artistId)
                statement.setInt(3, map.position.coerceAtLeast(0))
                statement.executeUpdate()
            }
        }
    }

    private fun insertPlaylistSongMaps(db: Connection, maps: List<PlaylistSongMap>) {
        val sql = """
            INSERT OR REPLACE INTO playlist_song_map(id, playlistId, songId, position, setVideoId)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        db.prepareStatement(sql).use { statement ->
            maps.forEach { map ->
                statement.setInt(1, map.id)
                statement.setString(2, map.playlistId)
                statement.setString(3, map.songId)
                statement.setInt(4, map.position)
                statement.setNullableString(5, map.setVideoId)
                statement.executeUpdate()
            }
        }
    }

    private fun insertSetVideoIds(db: Connection, maps: List<PlaylistSongMap>) {
        val sql = "INSERT OR REPLACE INTO set_video_id(videoId, setVideoId) VALUES (?, ?)"
        db.prepareStatement(sql).use { statement ->
            val inserted = mutableSetOf<String>()
            maps.forEach { map ->
                val setVideoId = map.setVideoId ?: return@forEach
                if (setVideoId.isBlank()) return@forEach
                if (!inserted.add(map.songId)) return@forEach
                statement.setString(1, map.songId)
                statement.setString(2, setVideoId)
                statement.executeUpdate()
            }
        }
    }

    private fun insertRelatedSongMaps(db: Connection, maps: List<RelatedSongMap>) {
        val sql = """
            INSERT OR REPLACE INTO related_song_map(id, songId, relatedSongId)
            VALUES (?, ?, ?)
        """.trimIndent()
        db.prepareStatement(sql).use { statement ->
            maps.forEach { map ->
                statement.setLong(1, map.id)
                statement.setString(2, map.songId)
                statement.setString(3, map.relatedSongId)
                statement.executeUpdate()
            }
        }
    }

    private fun insertEvents(db: Connection, events: List<EventEntity>) {
        val sql = "INSERT OR REPLACE INTO event(id, songId, timestamp, playTime) VALUES (?, ?, ?, ?)"
        db.prepareStatement(sql).use { statement ->
            events.forEach { event ->
                statement.setLong(1, event.id)
                statement.setString(2, event.songId)
                statement.setLong(3, event.timestamp.toEpochMillisUtcRequired())
                statement.setLong(4, event.playTime)
                statement.executeUpdate()
            }
        }
    }

    private fun insertSearchHistory(db: Connection, history: List<SearchHistory>) {
        val deduplicated = history
            .groupBy { it.query }
            .mapValues { (_, values) -> values.maxByOrNull { it.id } ?: values.first() }
            .values
            .sortedBy { it.id }

        val sql = "INSERT OR REPLACE INTO search_history(id, query) VALUES (?, ?)"
        db.prepareStatement(sql).use { statement ->
            deduplicated.forEach { entry ->
                statement.setLong(1, entry.id)
                statement.setString(2, entry.query)
                statement.executeUpdate()
            }
        }
    }

    private fun readSongs(db: Connection): List<SongEntity> {
        if (!tableExists(db, "song")) return emptyList()
        return db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM song").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            SongEntity(
                                id = rows.getString("id"),
                                title = rows.getString("title"),
                                duration = rows.getInt("duration"),
                                thumbnailUrl = rows.getNullableString("thumbnailUrl"),
                                albumId = rows.getNullableString("albumId"),
                                albumName = rows.getNullableString("albumName"),
                                artistName = rows.getNullableString("artistName"),
                                explicit = rows.getInt("explicit") == 1,
                                year = rows.getNullableInt("year"),
                                date = rows.getNullableLong("date").fromEpochMillisUtc(),
                                dateModified = rows.getNullableLong("dateModified").fromEpochMillisUtc(),
                                liked = rows.getInt("liked") == 1,
                                likedDate = rows.getNullableLong("likedDate").fromEpochMillisUtc(),
                                totalPlayTime = rows.getLong("totalPlayTime"),
                                inLibrary = rows.getNullableLong("inLibrary").fromEpochMillisUtc(),
                                dateDownload = rows.getNullableLong("dateDownload").fromEpochMillisUtc(),
                                isLocal = rows.getInt("isLocal") == 1,
                                romanizeLyrics = rows.getInt("romanizeLyrics") == 1,
                                mediaStoreUri = rows.getNullableString("mediaStoreUri"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readArtists(db: Connection): List<ArtistEntity> {
        if (!tableExists(db, "artist")) return emptyList()
        return db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM artist").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            ArtistEntity(
                                id = rows.getString("id"),
                                name = rows.getString("name"),
                                thumbnailUrl = rows.getNullableString("thumbnailUrl"),
                                channelId = rows.getNullableString("channelId"),
                                lastUpdateTime = rows.getLong("lastUpdateTime").fromEpochMillisUtc()
                                    ?: LocalDateTime.now(),
                                bookmarkedAt = rows.getNullableLong("bookmarkedAt").fromEpochMillisUtc(),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readAlbums(db: Connection): List<AlbumEntity> {
        if (!tableExists(db, "album")) return emptyList()
        return db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM album").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            AlbumEntity(
                                id = rows.getString("id"),
                                playlistId = rows.getNullableString("playlistId"),
                                title = rows.getString("title"),
                                year = rows.getNullableInt("year"),
                                thumbnailUrl = rows.getNullableString("thumbnailUrl"),
                                themeColor = rows.getNullableInt("themeColor"),
                                songCount = rows.getInt("songCount"),
                                duration = rows.getInt("duration"),
                                lastUpdateTime = rows.getLong("lastUpdateTime").fromEpochMillisUtc()
                                    ?: LocalDateTime.now(),
                                bookmarkedAt = rows.getNullableLong("bookmarkedAt").fromEpochMillisUtc(),
                                likedDate = rows.getNullableLong("likedDate").fromEpochMillisUtc(),
                                inLibrary = rows.getNullableLong("inLibrary").fromEpochMillisUtc(),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readPlaylists(db: Connection): List<PlaylistEntity> {
        if (!tableExists(db, "playlist")) return emptyList()
        return db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM playlist").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            PlaylistEntity(
                                id = rows.getString("id"),
                                name = rows.getString("name"),
                                browseId = rows.getNullableString("browseId"),
                                createdAt = rows.getNullableLong("createdAt").fromEpochMillisUtc(),
                                lastUpdateTime = rows.getNullableLong("lastUpdateTime").fromEpochMillisUtc(),
                                isEditable = rows.getInt("isEditable") == 1,
                                bookmarkedAt = rows.getNullableLong("bookmarkedAt").fromEpochMillisUtc(),
                                remoteSongCount = rows.getNullableInt("remoteSongCount"),
                                playEndpointParams = rows.getNullableString("playEndpointParams"),
                                thumbnailUrl = rows.getNullableString("thumbnailUrl"),
                                shuffleEndpointParams = rows.getNullableString("shuffleEndpointParams"),
                                radioEndpointParams = rows.getNullableString("radioEndpointParams"),
                                backgroundImageUrl = rows.getNullableString("backgroundImageUrl"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readSongArtistMaps(db: Connection): List<SongArtistMap> {
        if (!tableExists(db, "song_artist_map")) return emptyList()
        return db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM song_artist_map").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            SongArtistMap(
                                songId = rows.getString("songId"),
                                artistId = rows.getString("artistId"),
                                position = rows.getInt("position"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readPlaylistSongMaps(db: Connection): List<PlaylistSongMap> {
        if (!tableExists(db, "playlist_song_map")) return emptyList()
        return db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM playlist_song_map").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            PlaylistSongMap(
                                id = rows.getInt("id"),
                                playlistId = rows.getString("playlistId"),
                                songId = rows.getString("songId"),
                                position = rows.getInt("position"),
                                setVideoId = rows.getNullableString("setVideoId"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readRelatedSongMaps(db: Connection): List<RelatedSongMap> {
        if (!tableExists(db, "related_song_map")) return emptyList()
        return db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM related_song_map").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            RelatedSongMap(
                                id = rows.getLong("id"),
                                songId = rows.getString("songId"),
                                relatedSongId = rows.getString("relatedSongId"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readEvents(db: Connection): List<EventEntity> {
        if (!tableExists(db, "event")) return emptyList()
        return db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM event").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            EventEntity(
                                id = rows.getLong("id"),
                                songId = rows.getString("songId"),
                                timestamp = rows.getLong("timestamp").fromEpochMillisUtc() ?: LocalDateTime.now(),
                                playTime = rows.getLong("playTime"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readSearchHistory(db: Connection): List<SearchHistory> {
        if (!tableExists(db, "search_history")) return emptyList()
        return db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM search_history").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            SearchHistory(
                                id = rows.getLong("id"),
                                query = rows.getString("query"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun tableExists(db: Connection, tableName: String): Boolean {
        val sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
        db.prepareStatement(sql).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { rows ->
                return rows.next()
            }
        }
    }
}

private fun PreparedStatement.setNullableString(index: Int, value: String?) {
    if (value.isNullOrBlank()) {
        setNull(index, Types.VARCHAR)
    } else {
        setString(index, value)
    }
}

private fun PreparedStatement.setNullableInt(index: Int, value: Int?) {
    if (value == null) {
        setNull(index, Types.INTEGER)
    } else {
        setInt(index, value)
    }
}

private fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
    if (value == null) {
        setNull(index, Types.BIGINT)
    } else {
        setLong(index, value)
    }
}

private fun ResultSet.getNullableString(column: String): String? =
    getString(column)?.takeIf { it.isNotBlank() }

private fun ResultSet.getNullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

private fun ResultSet.getNullableLong(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

private fun Boolean.toSqlBool(): Int = if (this) 1 else 0

private fun LocalDateTime?.toEpochMillisUtc(): Long? =
    this?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

private fun LocalDateTime.toEpochMillisUtcRequired(): Long =
    atZone(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long?.fromEpochMillisUtc(): LocalDateTime? =
    this?.let { millis -> LocalDateTime.ofEpochSecond(millis / 1000L, ((millis % 1000L) * 1_000_000L).toInt(), ZoneOffset.UTC) }
