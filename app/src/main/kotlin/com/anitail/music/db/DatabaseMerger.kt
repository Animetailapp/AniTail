package com.anitail.music.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber
import java.io.File
import javax.inject.Inject

import dagger.hilt.android.qualifiers.ApplicationContext

class DatabaseMerger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDatabase: MusicDatabase
) {

    /**
     * Merges a remote backup database into the current local database.
     * Strategy: "Smart Merge" - Add missing items, ignore duplicates.
     */
    fun mergeDatabase(remoteDbFile: File) {
        val currentDb = musicDatabase.openHelper.writableDatabase

        try {
            // Attach the remote database
            // Note: We need to use the raw SQLite path for ATTACH DATABASE
            val attachQuery = "ATTACH DATABASE '${remoteDbFile.absolutePath}' AS remote_db"
            currentDb.execSQL(attachQuery)
            Timber.d("Attached remote database for merging")

            currentDb.beginTransaction()
            try {
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
                currentDb.endTransaction()
                // Detach
                currentDb.execSQL("DETACH DATABASE remote_db")
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to merge databases")
            throw e
        }
    }

    private fun mergeFavorites(db: SupportSQLiteDatabase) {
        // 1. Update local songs to be favorite if they are favorite in remote
        // syntax: INSERT OR IGNORE / UPDATE

        // Strategy:
        // A. Insert songs that exist in REMOTE but NOT LOCAL (only if liked)
        // Songs are complex, we should try to copy all columns.
        val insertMissingSongsQuery = """
            INSERT OR IGNORE INTO main.song 
            SELECT * FROM remote_db.song 
            WHERE liked = 1 OR inLibrary IS NOT NULL
        """
        db.execSQL(insertMissingSongsQuery)

        // B. Update existing local songs if remote has them as liked/inLibrary (and local doesn't)
        // We prioritize "True" over "False". combining dates using earlier date if possible or remote date
        // Note: SQLite update with join from another DB is tricky in standard SQL.
        // We use a subquery approach or just `UPDATE song SET liked = 1 WHERE id IN (SELECT id FROM remote_db.song WHERE liked = 1)`
        val updateLikedQuery = """
            UPDATE song 
            SET liked = 1, 
                likedDate = COALESCE(likedDate, (SELECT likedDate FROM remote_db.song WHERE remote_db.song.id = main.song.id))
            WHERE id IN (SELECT id FROM remote_db.song WHERE liked = 1) AND liked = 0
        """
        db.execSQL(updateLikedQuery)

        val updateLibraryQuery = """
            UPDATE song 
            SET inLibrary = COALESCE(inLibrary, (SELECT inLibrary FROM remote_db.song WHERE remote_db.song.id = main.song.id))
            WHERE id IN (SELECT id FROM remote_db.song WHERE inLibrary IS NOT NULL) AND inLibrary IS NULL
        """
        db.execSQL(updateLibraryQuery)
    }

    private fun mergePlaylists(db: SupportSQLiteDatabase) {
        // 1. Insert playlists that don't exist locally
        // We exclude auto-generated or special playlists if necessary, but syncing all is better
        val insertPlaylistsQuery = """
            INSERT OR IGNORE INTO main.playlist 
            SELECT * FROM remote_db.playlist
        """
        db.execSQL(insertPlaylistsQuery)
    }

    private fun mergePlaylistItems(db: SupportSQLiteDatabase) {
        val insertItemsQuery = """
            INSERT OR IGNORE INTO main.playlist_song_map 
            SELECT * FROM remote_db.playlist_song_map
        """
        db.execSQL(insertItemsQuery)
    }

    private fun mergeHistory(db: SupportSQLiteDatabase) {
        // Insert events that don't exist locally.
        // Ensure we only insert events for songs that exist in the main database to avoid foreign key violations.

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
        // Insert artists that exist in remote but not locally
        val insertArtistsQuery = """
            INSERT OR IGNORE INTO main.artist 
            SELECT * FROM remote_db.artist
        """
        db.execSQL(insertArtistsQuery)

        // Update bookmarkedAt for artists that are bookmarked in remote but not local
        val updateBookmarkedQuery = """
            UPDATE artist 
            SET bookmarkedAt = (SELECT bookmarkedAt FROM remote_db.artist WHERE remote_db.artist.id = main.artist.id)
            WHERE id IN (SELECT id FROM remote_db.artist WHERE bookmarkedAt IS NOT NULL) 
            AND bookmarkedAt IS NULL
        """
        db.execSQL(updateBookmarkedQuery)
    }

    private fun mergeAlbums(db: SupportSQLiteDatabase) {
        // Insert albums that exist in remote but not locally
        val insertAlbumsQuery = """
            INSERT OR IGNORE INTO main.album 
            SELECT * FROM remote_db.album
        """
        db.execSQL(insertAlbumsQuery)

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
        val insertSearchHistoryQuery = """
            INSERT OR IGNORE INTO main.search_history 
            SELECT * FROM remote_db.search_history
        """
        db.execSQL(insertSearchHistoryQuery)
    }

    private fun mergeLyrics(db: SupportSQLiteDatabase) {
        // Insert lyrics for songs that don't have lyrics locally but have them in remote
        val insertLyricsQuery = """
            INSERT OR IGNORE INTO main.lyrics 
            SELECT * FROM remote_db.lyrics
        """
        db.execSQL(insertLyricsQuery)
    }

    private fun mergeFormats(db: SupportSQLiteDatabase) {
        // Insert format entries that don't exist locally
        val insertFormatsQuery = """
            INSERT OR IGNORE INTO main.format 
            SELECT * FROM remote_db.format
        """
        db.execSQL(insertFormatsQuery)
    }
}
