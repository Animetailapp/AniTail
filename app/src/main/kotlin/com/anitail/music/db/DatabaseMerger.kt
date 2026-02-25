package com.anitail.music.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DatabaseMerger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDatabase: MusicDatabase
) {
    companion object {
        private const val REMOTE_SCHEMA = "remote_db"
    }

    /**
     * Merges a remote backup database into the current local database.
     * Strategy: "Smart Merge" - Add missing items, ignore duplicates.
     */
    suspend fun mergeDatabase(remoteDbFile: File) {
        val currentDb = musicDatabase.openHelper.writableDatabase

        try {
            // Ensure WAL checkpoint is done before merge to avoid conflicts
            try {
                currentDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            } catch (e: Exception) {
                Timber.w(e, "WAL checkpoint failed, continuing anyway")
            }

            // Small delay to allow any pending transactions to complete
            delay(100)

            // Defensive cleanup: a previous failed merge may have left remote_db attached.
            runCatching {
                detachDatabaseIfAttached(currentDb, REMOTE_SCHEMA)
            }.onFailure { detachError ->
                Timber.w(detachError, "Failed to pre-clean attached %s", REMOTE_SCHEMA)
            }

            currentDb.beginTransaction()
            try {
                // ATTACH must happen on the same connection that runs merge queries.
                val remotePath = remoteDbFile.absolutePath.replace("'", "''")
                attachRemoteDatabase(currentDb, remotePath, REMOTE_SCHEMA)
                Timber.d("Attached remote database for merging")

                if (!isDatabaseAttached(currentDb, REMOTE_SCHEMA)) {
                    throw IllegalStateException("Failed to attach $REMOTE_SCHEMA on active transaction connection")
                }

                // 1. Merge Songs (Favorited status mainly)
                // If song exists in both but only remote is favorite, update local
                // If song doesn't exist locally but exists in remote (and is favorite), insert it
                mergeFavorites(currentDb)

                // 2. Merge Playlists
                // Insert playlists that don't exist locally
                mergePlaylists(currentDb)

                // 3. Merge Playlist Items
                mergePlaylistItems(currentDb)

                // 4. Merge Youtube Watch History (Event table)
                mergeHistory(currentDb)

                // 5. Merge Artists (bookmarked)
                mergeArtists(currentDb)

                // 6. Merge Albums (bookmarked)
                mergeAlbums(currentDb)

                // 7. Merge Search History
                mergeSearchHistory(currentDb)

                // 8. Merge Lyrics
                mergeLyrics(currentDb)

                // 9. Merge Format (audio quality cache)
                mergeFormats(currentDb)

                currentDb.setTransactionSuccessful()
                Timber.d("Database merge completed successfully")
            } finally {
                runCatching {
                    detachDatabaseIfAttached(currentDb, REMOTE_SCHEMA)
                }.onFailure { detachError ->
                    Timber.w(detachError, "Failed to detach %s before endTransaction", REMOTE_SCHEMA)
                }
                currentDb.endTransaction()
                runCatching {
                    detachDatabaseIfAttached(currentDb, REMOTE_SCHEMA)
                }.onFailure { detachError ->
                    Timber.w(detachError, "Failed to detach %s after endTransaction", REMOTE_SCHEMA)
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to merge databases")
            throw e
        }
    }

    private fun mergeFavorites(db: SupportSQLiteDatabase) {
        val hasLiked = hasColumn(db, "remote_db", "song", "liked")
        val hasLikedDate = hasColumn(db, "remote_db", "song", "likedDate")
        val hasInLibrary = hasColumn(db, "remote_db", "song", "inLibrary")

        val remoteSongConditions = mutableListOf<String>()
        if (hasLiked) remoteSongConditions += "remoteSong.liked = 1"
        if (hasInLibrary) remoteSongConditions += "remoteSong.inLibrary IS NOT NULL"

        if (remoteSongConditions.isNotEmpty()) {
            val songFilter = remoteSongConditions.joinToString(" OR ")
            insertOrIgnoreBySharedColumns(
                db = db,
                table = "song",
                sourceAlias = "remoteSong",
                whereClause = songFilter
            )

            // Ensure songs merged from favorites/library preserve artist relations.
            mergeSongArtistDataForSongs(db, songFilter)
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
                    likedDate = COALESCE(likedDate, (SELECT likedDate FROM remote_db.song WHERE remote_db.song.id = main.song.id))
                WHERE id IN (SELECT id FROM remote_db.song WHERE liked = 1) AND liked = 0
            """
            } else {
                """
                UPDATE song 
                SET liked = 1
                WHERE id IN (SELECT id FROM remote_db.song WHERE liked = 1) AND liked = 0
            """
            }
            db.execSQL(updateLikedQuery)
        }

        if (hasInLibrary) {
            val updateLibraryQuery = """
                UPDATE song 
                SET inLibrary = COALESCE(inLibrary, (SELECT inLibrary FROM remote_db.song WHERE remote_db.song.id = main.song.id))
                WHERE id IN (SELECT id FROM remote_db.song WHERE inLibrary IS NOT NULL) AND inLibrary IS NULL
            """
            db.execSQL(updateLibraryQuery)
        }
    }

    private fun mergePlaylists(db: SupportSQLiteDatabase) {
        insertOrIgnoreBySharedColumns(db, "playlist")
    }

    private fun mergePlaylistItems(db: SupportSQLiteDatabase) {
        insertOrIgnoreBySharedColumns(db, "playlist_song_map")
    }

    private fun mergeHistory(db: SupportSQLiteDatabase) {
        val historySongFilter = "remoteSong.id IN (SELECT DISTINCT songId FROM remote_db.event)"

        // Insert albums that are referenced by songs in remote events
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "album",
            sourceAlias = "a",
            whereClause = """
                a.id IN (
                    SELECT DISTINCT s.albumId FROM remote_db.song s
                    WHERE s.id IN (SELECT DISTINCT songId FROM remote_db.event)
                    AND s.albumId IS NOT NULL
                )
            """.trimIndent()
        )

        // Insert songs that are referenced in remote events but don't exist locally
        // This ensures we can insert the history events without foreign key violations
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "song",
            sourceAlias = "s",
            whereClause = """
                s.id IN (SELECT DISTINCT songId FROM remote_db.event)
                AND s.id NOT IN (SELECT id FROM main.song)
            """.trimIndent()
        )

        // Ensure artist entities and song-artist mappings exist for merged history songs.
        mergeSongArtistDataForSongs(db, historySongFilter)

        // Insert song-album mappings for those songs
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "song_album_map",
            whereClause = "songId IN (SELECT DISTINCT songId FROM remote_db.event)"
        )
        
        // Insert events that don't exist locally.
        val insertHistoryQuery = """
             INSERT INTO main.event (songId, timestamp, playTime)
             SELECT songId, timestamp, playTime FROM remote_db.event
             WHERE NOT EXISTS (
                SELECT 1 FROM main.event 
                WHERE main.event.songId = remote_db.event.songId 
                AND main.event.timestamp = remote_db.event.timestamp
             )
             AND EXISTS (
                SELECT 1 FROM main.song
                WHERE main.song.id = remote_db.event.songId
             )
         """
        db.execSQL(insertHistoryQuery)

        // Orphan Cleanup: Remove any events that point to non-existent songs.
        // This handles cases where database state was already bad or merge introduced issues.
        val cleanupOrphansQuery = """
             DELETE FROM event 
             WHERE songId NOT IN (SELECT id FROM song)
         """
        db.execSQL(cleanupOrphansQuery)
    }

    private fun mergeArtists(db: SupportSQLiteDatabase) {
        // Merge artists table while deduplicating generated local IDs (LA*) by name.
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "artist",
            sourceAlias = "remoteArtist",
            whereClause = """
                NOT EXISTS (
                    SELECT 1
                    FROM main.artist localArtist
                    WHERE localArtist.id = remoteArtist.id
                       OR (
                            remoteArtist.id LIKE 'LA%'
                            AND LOWER(TRIM(localArtist.name)) = LOWER(TRIM(remoteArtist.name))
                       )
                )
            """.trimIndent()
        )

        if (!hasColumn(db, "remote_db", "artist", "bookmarkedAt")) {
            return
        }

        // Preserve bookmarked state from remote artists, including LA* name matches.
        val updateBookmarkedQuery = """
            UPDATE artist
            SET bookmarkedAt = (
                SELECT remoteArtist.bookmarkedAt
                FROM remote_db.artist remoteArtist
                WHERE remoteArtist.bookmarkedAt IS NOT NULL
                  AND (
                        remoteArtist.id = main.artist.id
                        OR (
                            remoteArtist.id LIKE 'LA%'
                            AND LOWER(TRIM(remoteArtist.name)) = LOWER(TRIM(main.artist.name))
                        )
                  )
                ORDER BY CASE WHEN remoteArtist.id = main.artist.id THEN 0 ELSE 1 END
                LIMIT 1
            )
            WHERE bookmarkedAt IS NULL
              AND EXISTS (
                    SELECT 1
                    FROM remote_db.artist remoteArtist
                    WHERE remoteArtist.bookmarkedAt IS NOT NULL
                      AND (
                            remoteArtist.id = main.artist.id
                            OR (
                                remoteArtist.id LIKE 'LA%'
                                AND LOWER(TRIM(remoteArtist.name)) = LOWER(TRIM(main.artist.name))
                            )
                      )
              )
        """.trimIndent()
        db.execSQL(updateBookmarkedQuery)
    }

    private fun mergeAlbums(db: SupportSQLiteDatabase) {
        // Insert albums that exist in remote but not locally
        insertOrIgnoreBySharedColumns(db, "album")

        // Update bookmarkedAt for albums that are bookmarked in remote but not local
        val updateBookmarkedQuery = """
            UPDATE album 
            SET bookmarkedAt = (SELECT bookmarkedAt FROM remote_db.album WHERE remote_db.album.id = main.album.id)
            WHERE id IN (SELECT id FROM remote_db.album WHERE bookmarkedAt IS NOT NULL) 
            AND bookmarkedAt IS NULL
        """
        db.execSQL(updateBookmarkedQuery)
    }

    private fun mergeSearchHistory(db: SupportSQLiteDatabase) {
        // Insert search history entries that don't exist locally
        insertOrIgnoreBySharedColumns(db, "search_history")
    }

    private fun mergeLyrics(db: SupportSQLiteDatabase) {
        // Insert lyrics for songs that don't have lyrics locally but have them in remote
        insertOrIgnoreBySharedColumns(db, "lyrics")
    }

    private fun mergeFormats(db: SupportSQLiteDatabase) {
        // Insert format entries that don't exist locally
        insertOrIgnoreBySharedColumns(db, "format")
    }

    private fun mergeSongArtistDataForSongs(
        db: SupportSQLiteDatabase,
        remoteSongWhereClause: String,
    ) {
        // Insert missing artists referenced by matching songs.
        // For generated IDs (LA*), dedupe by normalized artist name.
        insertOrIgnoreBySharedColumns(
            db = db,
            table = "artist",
            sourceAlias = "remoteArtist",
            whereClause = """
                remoteArtist.id IN (
                    SELECT DISTINCT remoteMap.artistId
                    FROM remote_db.song_artist_map remoteMap
                    JOIN remote_db.song remoteSong ON remoteSong.id = remoteMap.songId
                    WHERE $remoteSongWhereClause
                )
                AND NOT EXISTS (
                    SELECT 1
                    FROM main.artist localArtist
                    WHERE localArtist.id = remoteArtist.id
                       OR (
                            remoteArtist.id LIKE 'LA%'
                            AND LOWER(TRIM(localArtist.name)) = LOWER(TRIM(remoteArtist.name))
                       )
                )
            """.trimIndent()
        )

        val mergeSongArtistMapsQuery = """
            INSERT OR IGNORE INTO main.song_artist_map (songId, artistId, position)
            SELECT
                remoteMap.songId,
                CASE
                    WHEN remoteArtist.id LIKE 'LA%' THEN COALESCE(
                        (
                            SELECT localByName.id
                            FROM main.artist localByName
                            WHERE LOWER(TRIM(localByName.name)) = LOWER(TRIM(remoteArtist.name))
                            ORDER BY localByName.rowid
                            LIMIT 1
                        ),
                        (
                            SELECT localById.id
                            FROM main.artist localById
                            WHERE localById.id = remoteMap.artistId
                            LIMIT 1
                        ),
                        remoteMap.artistId
                    )
                    ELSE COALESCE(
                        (
                            SELECT localById.id
                            FROM main.artist localById
                            WHERE localById.id = remoteMap.artistId
                            LIMIT 1
                        ),
                        remoteMap.artistId
                    )
                END AS resolvedArtistId,
                remoteMap.position
            FROM remote_db.song_artist_map remoteMap
            JOIN remote_db.song remoteSong ON remoteSong.id = remoteMap.songId
            JOIN remote_db.artist remoteArtist ON remoteArtist.id = remoteMap.artistId
            WHERE $remoteSongWhereClause
              AND EXISTS (
                    SELECT 1
                    FROM main.song localSong
                    WHERE localSong.id = remoteMap.songId
              )
        """.trimIndent()
        db.execSQL(mergeSongArtistMapsQuery)
    }

    private fun insertOrIgnoreBySharedColumns(
        db: SupportSQLiteDatabase,
        table: String,
        whereClause: String? = null,
        sourceAlias: String? = null,
    ) {
        val mainColumns = tableColumns(db, "main", table)
        val remoteColumns = tableColumns(db, "remote_db", table).toSet()
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
            "remote_db.`$table`"
        } else {
            "remote_db.`$table` $sourceAlias"
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

    private fun tableColumns(
        db: SupportSQLiteDatabase,
        schema: String,
        table: String,
    ): List<String> {
        val columns = mutableListOf<String>()
        db.query("PRAGMA $schema.table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        return columns
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

    private fun detachDatabaseIfAttached(
        db: SupportSQLiteDatabase,
        schemaName: String,
    ) {
        if (!isDatabaseAttached(db, schemaName)) return
        db.execSQL("DETACH DATABASE $schemaName")
        Timber.d("Detached %s", schemaName)
    }

    private fun attachRemoteDatabase(
        db: SupportSQLiteDatabase,
        escapedRemotePath: String,
        schemaName: String,
    ) {
        val attachSql = "ATTACH DATABASE '$escapedRemotePath' AS $schemaName"
        runCatching {
            db.execSQL(attachSql)
        }.onFailure { attachError ->
            if (!isAlreadyInUseAttachError(attachError)) {
                throw attachError
            }

            Timber.w(
                attachError,
                "Schema %s already in use, retrying ATTACH after DETACH",
                schemaName
            )

            runCatching {
                db.execSQL("DETACH DATABASE $schemaName")
            }.onFailure { detachError ->
                Timber.w(detachError, "Retry DETACH failed for %s", schemaName)
            }

            // Retry ATTACH on the same active connection.
            db.execSQL(attachSql)
        }
    }

    private fun isAlreadyInUseAttachError(error: Throwable): Boolean {
        val message = error.message ?: return false
        return message.contains("already in use", ignoreCase = true)
    }
}
