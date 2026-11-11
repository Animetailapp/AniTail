package com.anitail.music.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DatabaseMerger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDatabase: MusicDatabase
) {
    companion object {
        private const val REMOTE_SCHEMA_PREFIX = "remote_db_"
        private const val TEMP_LOCAL_ARTIST_NAMES = "_temp_local_artist_names"
        private const val TEMP_REMOTE_LA_BOOKMARKS = "_temp_remote_la_bookmarks"
        private const val ARTIST_NOT_PRESENT_LOCALLY = """
            remoteArtist.id NOT IN (SELECT id FROM main.artist)
            AND (
                remoteArtist.id NOT LIKE 'LA%'
                OR LOWER(TRIM(remoteArtist.name)) NOT IN (SELECT norm FROM _temp_local_artist_names)
            )
        """
    }

    /**
     * Merges a remote backup database into the current local database.
     * Strategy: "Smart Merge" - Add missing items, ignore duplicates.
     *
     * Uses a standalone [SQLiteDatabase] connection instead of Room's
     * [SupportSQLiteDatabase] to bypass Room's connection pool. Android
     * classifies ATTACH DATABASE as DDL, triggering pool reconfiguration
     * which fails if *any* connection in Room's pool has an active
     * transaction (e.g. InvalidationTracker, DAO queries).
     */
    suspend fun mergeDatabase(remoteDbFile: File) {
        // Clear column cache for each merge session
        columnCache.clear()

        val remoteSchema = buildRemoteSchemaName()
        val db = musicDatabase.openHelper.writableDatabase
        val remotePath = remoteDbFile.absolutePath.replace("'", "''")

        // We MUST start the transaction FIRST.
        // Starting a transaction pins a single connection from Room's connection pool
        // to the current thread. This guarantees that ATTACH, PRAGMA, and INSERTs
        // all happen on the exact same physical SQLite connection.
        db.beginTransaction()
        try {
            // ATTACH the remote database inside the transaction.
            // (SQLite >= 3.7.11 allows ATTACH inside a transaction).
            // CRITICAL: We use compileStatement().execute() instead of execSQL().
            // Android's execSQL() checks for DDL statements (like ATTACH) and triggers
            // a connection pool reconfiguration. Reconfiguration attempts to disable WAL,
            // which crashes if ANY transaction is active in Room's pool.
            // compileStatement() bypasses this DDL check, allowing ATTACH to run safely.
            db.compileStatement("ATTACH DATABASE '$remotePath' AS $remoteSchema").execute()
            Timber.d("Attached remote database for merging as %s", remoteSchema)

            if (!isDatabaseAttached(db, remoteSchema)) {
                throw IllegalStateException("Failed to attach $remoteSchema")
            }

            try {
                // Build indexed lookup tables for normalized artist names. The previous
                // correlated `LOWER(TRIM(name)) = LOWER(TRIM(name))` comparisons cannot use any
                // index and scaled as local × remote artists, holding the write lock for a long
                // time and freezing every other DB access in the app.
                runMergeStep("prepare_lookups") { prepareLookupTables(db, remoteSchema) }

                // 1. Merge Songs (Favorited status mainly)
                runMergeStep("favorites") { mergeFavorites(db, remoteSchema) }

                // 2. Merge Playlists
                runMergeStep("playlists") { mergePlaylists(db, remoteSchema) }

                // 3. Merge Playlist Items
                runMergeStep("playlist_items") { mergePlaylistItems(db, remoteSchema) }

                // 4. Merge Youtube Watch History (Event table)
                runMergeStep("history") { mergeHistory(db, remoteSchema) }

                // 5. Merge Artists (bookmarked)
                runMergeStep("artists") { mergeArtists(db, remoteSchema) }

                // 6. Merge Albums (bookmarked)
                runMergeStep("albums") { mergeAlbums(db, remoteSchema) }

                // 7. Merge Search History
                runMergeStep("search_history") { mergeSearchHistory(db, remoteSchema) }

                // 8. Merge Lyrics
                runMergeStep("lyrics") { mergeLyrics(db, remoteSchema) }

                // 9. Merge Format (audio quality cache)
                runMergeStep("formats") { mergeFormats(db, remoteSchema) }

                db.setTransactionSuccessful()
                Timber.d("Database merge completed successfully")
            } finally {
                // DETACH inside the transaction before ending it,
                // so the connection goes back to the pool clean.
                runCatching { dropLookupTables(db) }
                    .onFailure { Timber.w(it, "Failed to drop merge lookup tables") }
                runCatching {
                    if (isDatabaseAttached(db, remoteSchema)) {
                        db.compileStatement("DETACH DATABASE $remoteSchema").execute()
                        Timber.d("Detached %s", remoteSchema)
                    }
                }.onFailure { detachError ->
                    Timber.w(detachError, "Failed to detach %s after merge", remoteSchema)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to merge databases")
            throw e
        } finally {
            db.endTransaction()
        }
    }

    private fun mergeFavorites(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
    ) {
        val remoteSongTable = "$remoteSchema.song"
        val hasLiked = hasColumn(db, remoteSchema, "song", "liked")
        val hasLikedDate = hasColumn(db, remoteSchema, "song", "likedDate")
        val hasInLibrary = hasColumn(db, remoteSchema, "song", "inLibrary")

        val remoteSongConditions = mutableListOf<String>()
        if (hasLiked) remoteSongConditions += "remoteSong.liked = 1"
        if (hasInLibrary) remoteSongConditions += "remoteSong.inLibrary IS NOT NULL"

        if (remoteSongConditions.isNotEmpty()) {
            val songFilter = remoteSongConditions.joinToString(" OR ")
            insertOrIgnoreBySharedColumns(
                db = db,
                table = "song",
                remoteSchema = remoteSchema,
                sourceAlias = "remoteSong",
                whereClause = songFilter
            )

            // Ensure songs merged from favorites/library preserve artist relations.
            mergeSongArtistDataForSongs(db, remoteSchema, songFilter)
        }

        // B. Update existing local songs if remote has them as liked/inLibrary (and local doesn't)
        // We prioritize "True" over "False". combining dates using earlier date if possible or remote date
        // Note: SQLite update with join from another DB is tricky in standard SQL.
        // We use a subquery approach or just `UPDATE song SET liked = 1 WHERE id IN (SELECT id FROM remote_db.song WHERE liked = 1)`
        if (hasLiked) {
            val updateLikedQuery = if (hasLikedDate) {
                """
                UPDATE song 
                SET liked = 1, 
                    likedDate = COALESCE(likedDate, (SELECT likedDate FROM $remoteSongTable WHERE $remoteSongTable.id = main.song.id))
                WHERE id IN (SELECT id FROM $remoteSongTable WHERE liked = 1) AND liked = 0
            """
            } else {
                """
                UPDATE song 
                SET liked = 1
                WHERE id IN (SELECT id FROM $remoteSongTable WHERE liked = 1) AND liked = 0
            """
            }
            db.execSQL(updateLikedQuery)
        }

        if (hasInLibrary) {
            val updateLibraryQuery = """
                UPDATE song 
                SET inLibrary = COALESCE(inLibrary, (SELECT inLibrary FROM $remoteSongTable WHERE $remoteSongTable.id = main.song.id))
                WHERE id IN (SELECT id FROM $remoteSongTable WHERE inLibrary IS NOT NULL) AND inLibrary IS NULL
            """
            db.execSQL(updateLibraryQuery)
        }
    }

    private fun mergePlaylists(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
    ) {
        insertOrIgnoreBySharedColumns(db, "playlist", remoteSchema = remoteSchema)
    }

    private fun mergePlaylistItems(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
    ) {
        if (!hasTable(db, remoteSchema, "playlist_song_map")) {
            Timber.w("Skipping playlist item merge: remote table %s.playlist_song_map not found", remoteSchema)
            return
        }

        if (!hasTable(db, remoteSchema, "song")) {
            Timber.w("Skipping playlist item merge: remote table %s.song not found", remoteSchema)
            return
        }

        val remotePlaylistSongMapTable = "$remoteSchema.playlist_song_map"
        val playlistSongFilter = """
            remoteSong.id IN (
                SELECT DISTINCT remoteMap.songId
                FROM $remotePlaylistSongMapTable remoteMap
            )
        """.trimIndent()

        // Ensure playlist songs exist locally before inserting child rows in playlist_song_map.
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "song",
            remoteSchema = remoteSchema,
            sourceAlias = "remoteSong",
            whereClause = playlistSongFilter
        )

        // Ensure artist entities and song-artist mappings exist for playlist songs.
        mergeSongArtistDataForSongs(db, remoteSchema, playlistSongFilter)

        // Insert only rows whose parent playlist/song exists locally to avoid FK violations.
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "playlist_song_map",
            remoteSchema = remoteSchema,
            sourceAlias = "remoteMap",
            whereClause = """
                EXISTS (
                    SELECT 1
                    FROM main.playlist localPlaylist
                    WHERE localPlaylist.id = remoteMap.playlistId
                )
                AND EXISTS (
                    SELECT 1
                    FROM main.song localSong
                    WHERE localSong.id = remoteMap.songId
                )
            """.trimIndent()
        )
    }

    private fun mergeHistory(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
    ) {
        val remoteEventTable = "$remoteSchema.event"
        val remoteSongTable = "$remoteSchema.song"
        val historySongFilter = "remoteSong.id IN (SELECT DISTINCT songId FROM $remoteEventTable)"

        // Insert albums that are referenced by songs in remote events
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "album",
            remoteSchema = remoteSchema,
            sourceAlias = "a",
            whereClause = """
                a.id IN (SELECT DISTINCT s.albumId FROM $remoteSongTable s
                         WHERE s.id IN (SELECT DISTINCT songId FROM $remoteEventTable)
                         AND s.albumId IS NOT NULL)
            """.trimIndent()
        )

        // Insert songs that are referenced in remote events but don't exist locally
        // This ensures we can insert the history events without foreign key violations
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "song",
            remoteSchema = remoteSchema,
            sourceAlias = "s",
            whereClause = "s.id IN (SELECT DISTINCT songId FROM $remoteEventTable)"
        )

        // Ensure artist entities and song-artist mappings exist for merged history songs.
        mergeSongArtistDataForSongs(db, remoteSchema, historySongFilter)

        // Insert song-album mappings for those songs
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "song_album_map",
            remoteSchema = remoteSchema,
            sourceAlias = "remoteMap",
            whereClause = """
                remoteMap.songId IN (SELECT DISTINCT songId FROM $remoteEventTable)
                AND EXISTS (
                    SELECT 1
                    FROM main.song localSong
                    WHERE localSong.id = remoteMap.songId
                )
                AND EXISTS (
                    SELECT 1
                    FROM main.album localAlbum
                    WHERE localAlbum.id = remoteMap.albumId
                )
            """.trimIndent()
        )
        
        // Insert events that don't exist locally.
        val insertHistoryQuery = """
             INSERT INTO main.event (songId, timestamp, playTime)
             SELECT songId, timestamp, playTime FROM $remoteEventTable
             WHERE NOT EXISTS (
                SELECT 1 FROM main.event 
                WHERE main.event.songId = $remoteEventTable.songId 
                AND main.event.timestamp = $remoteEventTable.timestamp
             )
             AND EXISTS (
                SELECT 1 FROM main.song
                WHERE main.song.id = $remoteEventTable.songId
             )
         """
        db.execSQL(insertHistoryQuery)

        // Orphan Cleanup: Remove any events that point to non-existent songs.
        // This handles cases where database state was already bad or merge introduced issues.
        val cleanupOrphansQuery = """
             DELETE FROM event 
             WHERE NOT EXISTS (
                 SELECT 1 FROM main.song WHERE main.song.id = main.event.songId
             )
         """
        db.execSQL(cleanupOrphansQuery)
    }

    private fun mergeArtists(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
    ) {
        val remoteArtistTable = "$remoteSchema.artist"
        // Merge artists table while deduplicating generated local IDs (LA*) by name.
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "artist",
            remoteSchema = remoteSchema,
            sourceAlias = "remoteArtist",
            whereClause = ARTIST_NOT_PRESENT_LOCALLY
        )
        refreshLocalArtistNames(db)

        if (!hasColumn(db, remoteSchema, "artist", "bookmarkedAt")) {
            return
        }

        // Preserve bookmarked state from remote artists, including LA* name matches.
        val updateBookmarkedQuery = """
            UPDATE artist
            SET bookmarkedAt = COALESCE(
                (
                    SELECT remoteArtist.bookmarkedAt
                    FROM $remoteArtistTable remoteArtist
                    WHERE remoteArtist.id = artist.id
                      AND remoteArtist.bookmarkedAt IS NOT NULL
                ),
                (
                    SELECT rb.bookmarkedAt
                    FROM $TEMP_REMOTE_LA_BOOKMARKS rb
                    WHERE rb.norm = LOWER(TRIM(artist.name))
                )
            )
            WHERE bookmarkedAt IS NULL
              AND (
                    EXISTS (
                        SELECT 1
                        FROM $remoteArtistTable remoteArtist
                        WHERE remoteArtist.id = artist.id
                          AND remoteArtist.bookmarkedAt IS NOT NULL
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM $TEMP_REMOTE_LA_BOOKMARKS rb
                        WHERE rb.norm = LOWER(TRIM(artist.name))
                    )
              )
        """.trimIndent()
        db.execSQL(updateBookmarkedQuery)
    }

    private fun mergeAlbums(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
    ) {
        val remoteAlbumTable = "$remoteSchema.album"
        // Insert albums that exist in remote but not locally
        insertOrIgnoreBySharedColumns(db, "album", remoteSchema = remoteSchema)

        // Update bookmarkedAt for albums that are bookmarked in remote but not local
        val updateBookmarkedQuery = """
            UPDATE album 
            SET bookmarkedAt = (SELECT bookmarkedAt FROM $remoteAlbumTable WHERE $remoteAlbumTable.id = main.album.id)
            WHERE id IN (SELECT id FROM $remoteAlbumTable WHERE bookmarkedAt IS NOT NULL) 
            AND bookmarkedAt IS NULL
        """
        db.execSQL(updateBookmarkedQuery)
    }

    private fun mergeSearchHistory(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
    ) {
        // Insert search history entries that don't exist locally
        insertOrIgnoreBySharedColumns(db, "search_history", remoteSchema = remoteSchema)
    }

    private fun mergeLyrics(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
    ) {
        // Insert lyrics for songs that don't have lyrics locally but have them in remote
        insertOrIgnoreBySharedColumns(db, "lyrics", remoteSchema = remoteSchema)
    }

    private fun mergeFormats(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
    ) {
        // Insert format entries that don't exist locally
        insertOrIgnoreBySharedColumns(db, "format", remoteSchema = remoteSchema)
    }

    private fun mergeSongArtistDataForSongs(
        db: SupportSQLiteDatabase,
        remoteSchema: String,
        remoteSongWhereClause: String,
    ) {
        if (!hasTable(db, remoteSchema, "song_artist_map")) {
            Timber.w("Skipping song_artist_map merge: remote table %s.song_artist_map not found", remoteSchema)
            return
        }

        if (!hasTable(db, remoteSchema, "artist")) {
            Timber.w("Skipping song_artist_map merge: remote table %s.artist not found", remoteSchema)
            return
        }

        val remoteSongArtistMapTable = "$remoteSchema.song_artist_map"
        val remoteSongTable = "$remoteSchema.song"
        val remoteArtistTable = "$remoteSchema.artist"
        // Insert missing artists referenced by matching songs.
        // For generated IDs (LA*), dedupe by normalized artist name using indexed temp table.
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "artist",
            remoteSchema = remoteSchema,
            sourceAlias = "remoteArtist",
            whereClause = """
                remoteArtist.id IN (
                    SELECT DISTINCT remoteMap.artistId
                    FROM $remoteSongArtistMapTable remoteMap
                    JOIN $remoteSongTable remoteSong ON remoteSong.id = remoteMap.songId
                    WHERE $remoteSongWhereClause
                )
                AND $ARTIST_NOT_PRESENT_LOCALLY
            """.trimIndent()
        )
        refreshLocalArtistNames(db)

        val mergeSongArtistMapsQuery = """
            INSERT OR IGNORE INTO main.song_artist_map (songId, artistId, position)
            SELECT
                remoteMap.songId,
                CASE
                    WHEN remoteArtist.id LIKE 'LA%' THEN COALESCE(
                        (
                            SELECT t.id
                            FROM $TEMP_LOCAL_ARTIST_NAMES t
                            WHERE t.norm = LOWER(TRIM(remoteArtist.name))
                            LIMIT 1
                        ),
                        remoteMap.artistId
                    )
                    ELSE remoteMap.artistId
                END AS resolvedArtistId,
                remoteMap.position
            FROM $remoteSongArtistMapTable remoteMap
            JOIN $remoteSongTable remoteSong ON remoteSong.id = remoteMap.songId
            JOIN $remoteArtistTable remoteArtist ON remoteArtist.id = remoteMap.artistId
            WHERE $remoteSongWhereClause
              AND EXISTS (
                    SELECT 1
                    FROM main.song localSong
                    WHERE localSong.id = remoteMap.songId
              )
              AND (
                    EXISTS (
                        SELECT 1
                        FROM main.artist localArtist
                        WHERE localArtist.id = remoteMap.artistId
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM $TEMP_LOCAL_ARTIST_NAMES t
                        WHERE t.norm = LOWER(TRIM(remoteArtist.name))
                    )
              )
        """.trimIndent()
        db.execSQL(mergeSongArtistMapsQuery)
    }

    private fun insertOrIgnoreBySharedColumns(
        db: SupportSQLiteDatabase,
        table: String,
        remoteSchema: String,
        whereClause: String? = null,
        sourceAlias: String? = null,
    ) {
        val mainColumns = tableColumns(db, "main", table)
        val remoteColumns = tableColumns(db, remoteSchema, table).toSet()
        val sharedColumns = mainColumns.filter { it in remoteColumns }

        if (sharedColumns.isEmpty()) {
            Timber.w("Skipping merge for %s: no shared columns found", table)
            return
        }

        val quotedColumns = sharedColumns.joinToString(", ") { "`$it`" }
        val selectColumns = if (sourceAlias.isNullOrBlank()) {
            sharedColumns.joinToString(", ") { "`$it`" }
        } else {
            sharedColumns.joinToString(", ") { "$sourceAlias.`$it`" }
        }
        val sourceTable = if (sourceAlias.isNullOrBlank()) {
            "$remoteSchema.`$table`"
        } else {
            "$remoteSchema.`$table` $sourceAlias"
        }
        val whereSql = whereClause?.trim()?.takeIf { it.isNotEmpty() }?.let { " WHERE $it" } ?: ""
        val sql = """
            INSERT OR IGNORE INTO main.`$table` ($quotedColumns)
            SELECT $selectColumns FROM $sourceTable$whereSql
        """.trimIndent()
        db.execSQL(sql)
    }

    private fun hasColumn(
        db: SupportSQLiteDatabase,
        schema: String,
        table: String,
        column: String,
    ): Boolean = tableColumns(db, schema, table).any { it == column }

    private fun hasTable(
        db: SupportSQLiteDatabase,
        schema: String,
        table: String,
    ): Boolean = tableColumns(db, schema, table).isNotEmpty()

    private inline fun runMergeStep(
        step: String,
        block: () -> Unit,
    ) {
        Timber.d("Merge step started: %s", step)
        try {
            block()
            Timber.d("Merge step completed: %s", step)
        } catch (error: Exception) {
            Timber.e(error, "Merge step failed: %s", step)
            throw error
        }
    }

    /** Session-scoped cache for PRAGMA table_info results to avoid repeated queries. */
    private val columnCache = mutableMapOf<String, List<String>>()

    private fun tableColumns(
        db: SupportSQLiteDatabase,
        schema: String,
        table: String,
    ): List<String> {
        val cacheKey = "$schema.$table"
        columnCache[cacheKey]?.let { return it }

        val columns = mutableListOf<String>()
        db.query("PRAGMA $schema.table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        columnCache[cacheKey] = columns
        return columns
    }

    private fun prepareLookupTables(db: SupportSQLiteDatabase, remoteSchema: String) {
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS $TEMP_LOCAL_ARTIST_NAMES (id TEXT PRIMARY KEY, norm TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${TEMP_LOCAL_ARTIST_NAMES}_norm ON $TEMP_LOCAL_ARTIST_NAMES(norm)")
        db.execSQL("DELETE FROM $TEMP_LOCAL_ARTIST_NAMES")
        db.execSQL(
            """
            INSERT OR IGNORE INTO $TEMP_LOCAL_ARTIST_NAMES (id, norm)
            SELECT id, LOWER(TRIM(name))
            FROM main.artist
            WHERE name IS NOT NULL
            """.trimIndent()
        )

        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS $TEMP_REMOTE_LA_BOOKMARKS (norm TEXT PRIMARY KEY, bookmarkedAt TEXT)")
        db.execSQL("DELETE FROM $TEMP_REMOTE_LA_BOOKMARKS")
        if (hasTable(db, remoteSchema, "artist") && hasColumn(db, remoteSchema, "artist", "bookmarkedAt")) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO $TEMP_REMOTE_LA_BOOKMARKS (norm, bookmarkedAt)
                SELECT LOWER(TRIM(name)), bookmarkedAt
                FROM $remoteSchema.artist
                WHERE id LIKE 'LA%' AND bookmarkedAt IS NOT NULL AND name IS NOT NULL
                """.trimIndent()
            )
        }
    }

    private fun refreshLocalArtistNames(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO $TEMP_LOCAL_ARTIST_NAMES (id, norm)
            SELECT id, LOWER(TRIM(name))
            FROM main.artist
            WHERE name IS NOT NULL
              AND id NOT IN (SELECT id FROM $TEMP_LOCAL_ARTIST_NAMES)
            """.trimIndent()
        )
    }

    private fun dropLookupTables(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS $TEMP_LOCAL_ARTIST_NAMES")
        db.execSQL("DROP TABLE IF EXISTS $TEMP_REMOTE_LA_BOOKMARKS")
    }

    private fun buildRemoteSchemaName(): String {
        val now = System.currentTimeMillis()
        val threadToken = System.identityHashCode(Thread.currentThread()).toUInt().toString(16)
        val nonce = (System.nanoTime() and 0xFFFF).toString()
        return "${REMOTE_SCHEMA_PREFIX}${threadToken}_${now}_$nonce"
    }

    private fun isDatabaseAttached(
        db: SupportSQLiteDatabase,
        schemaName: String,
    ): Boolean {
        db.query("PRAGMA database_list").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex == -1) return false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == schemaName) {
                    return true
                }
            }
        }
        return false
    }
}
