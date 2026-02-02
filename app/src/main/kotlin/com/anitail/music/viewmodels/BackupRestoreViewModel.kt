package com.anitail.music.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.MainActivity
import com.anitail.music.R
import com.anitail.music.db.InternalDatabase
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.ArtistEntity
import com.anitail.music.db.entities.Song
import com.anitail.music.db.entities.SongEntity
import com.anitail.music.extensions.div
import com.anitail.music.extensions.tryOrNull
import com.anitail.music.extensions.zipInputStream
import com.anitail.music.extensions.zipOutputStream
import com.anitail.music.playback.MusicService
import com.anitail.music.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.anitail.music.services.SyncWorker
import com.anitail.music.utils.GoogleDriveSyncManager
import com.anitail.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
    private val googleDriveSyncManager: GoogleDriveSyncManager,
    private val databaseMerger: com.anitail.music.db.DatabaseMerger,
    private val syncUtils: com.anitail.music.utils.SyncUtils,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val signedInAccount = googleDriveSyncManager.signedInAccount

    // Sync state
    private val _isSyncing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    init {
        // Try to restore Google Sign-In session on ViewModel creation
        viewModelScope.launch {
            googleDriveSyncManager.trySilentSignIn()
        }
    }

    fun getSignInIntent() = googleDriveSyncManager.getSignInIntent()

    fun syncWithDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true
            _syncMessage.value = "Iniciando..."

            try {
                val resultMessage = syncUtils.syncCloud(force = true)
                _syncMessage.value = resultMessage ?: "Sincronización completada"
            } catch (e: Exception) {
                Timber.e(e, "Sync failed")
                _syncMessage.value = "Falló: ${e.localizedMessage}"
            } finally {
                _isSyncing.value = false
                // Clear message after delay
                kotlinx.coroutines.delay(3000)
                _syncMessage.value = null
            }
        }
    }

    fun handleSignInResult(result: ActivityResult) {
        viewModelScope.launch {
            googleDriveSyncManager.handleSignInResult(result)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            googleDriveSyncManager.signOut()
        }
    }

    fun backupToDrive(context: Context) {
        viewModelScope.launch {
            val tempFile =
                File(context.cacheDir, "AniTail_Backup_${System.currentTimeMillis()}.zip")
            try {
                // Perform backup to temporary file
                backupToFile(context, tempFile)

                // Upload to Drive
                val result = googleDriveSyncManager.uploadBackup(tempFile)
                result.onSuccess {
                    Toast.makeText(context, "Backup uploaded to Drive", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Timber.e(it, "Failed to upload to Drive")
                    Toast.makeText(context, "Failed to upload to Drive", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Backup failed")
                Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
            } finally {
                tempFile.delete()
            }
        }
    }

    private suspend fun backupToFile(context: Context, file: File) = withContext(Dispatchers.IO) {
        FileOutputStream(file).use { fos ->
            fos.buffered().zipOutputStream().use { outputStream ->
                (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered()
                    .use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                        inputStream.copyTo(outputStream)
                    }
                database.checkpoint()
                FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                    outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    fun restoreFromDrive(context: Context) {
        viewModelScope.launch {
            val tempFile = File(context.cacheDir, "AniTail_Restore_Temp.zip")
            try {
                val result = googleDriveSyncManager.downloadLatestBackup(tempFile)
                result.onSuccess {
                    restoreFromFile(context, tempFile)
                }.onFailure {
                    Toast.makeText(context, "No backups found on Drive", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Restore failed")
                Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
            } finally {
                tempFile.delete()
            }
        }
    }

    private fun restoreFromFile(context: Context, file: File) {
        runCatching {
            // Cancel any running sync before closing the database
            SyncWorker.cancel(context)
            MusicDatabase.isRestoring.set(true)
            
            FileInputStream(file).use { fis ->
                fis.zipInputStream().use { inputStream ->
                    var entry = tryOrNull { inputStream.nextEntry }
                    while (entry != null) {
                        when (entry.name) {
                            SETTINGS_FILENAME -> {
                                (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream()
                                    .use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                            }

                            InternalDatabase.DB_NAME -> {
                                runBlocking(Dispatchers.IO) {
                                    database.checkpoint()
                                }
                                database.close()
                                FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                        entry = tryOrNull { inputStream.nextEntry }
                    }
                }
            }
            context.stopService(Intent(context, MusicService::class.java))
            context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            context.startActivity(Intent(context, MainActivity::class.java))
            exitProcess(0)
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
        }
    }
    fun backup(context: Context, uri: Uri, showToast: Boolean = true) {
        runCatching {
            context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                it.buffered().zipOutputStream().use { outputStream ->
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered()
                        .use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                            inputStream.copyTo(outputStream)
                        }
                    runBlocking(Dispatchers.IO) {
                        database.checkpoint()
                    }
                    FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }.onSuccess {
            if (showToast) {
                // Only show the toast if requested (not from background worker)
                Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            reportException(it)
            if (showToast) {
                Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restore(context: Context, uri: Uri) {
        runCatching {
            // Cancel any running sync before closing the database
            SyncWorker.cancel(context)
            MusicDatabase.isRestoring.set(true)
            
            context.applicationContext.contentResolver.openInputStream(uri)?.use {
                it.zipInputStream().use { inputStream ->
                    var entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
                    while (entry != null) {
                        when (entry.name) {
                            SETTINGS_FILENAME -> {
                                (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream()
                                    .use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                            }

                            InternalDatabase.DB_NAME -> {
                                runBlocking(Dispatchers.IO) {
                                    database.checkpoint()
                                }
                                database.close()
                                FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                        entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
                    }
                }
            }
            context.stopService(Intent(context, MusicService::class.java))
            context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            context.startActivity(Intent(context, MainActivity::class.java))
            exitProcess(0)
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun importPlaylistFromCsv(context: Context, uri: Uri): ArrayList<Song> {
        val songs = arrayListOf<Song>()
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                lines.forEachIndexed { _, line ->
                    val parts = line.split(",").map { it.trim() }
                    val title = parts[0]
                    val artistStr = parts[1]

                    val artists = artistStr.split(";").map { it.trim() }.map {
                   ArtistEntity(
                            id = "",
                            name = it,
                        )
                    }
                    val mockSong = Song(
                        song = SongEntity(
                            id = "",
                            title = title,
                        ),
                        artists = artists,
                    )
                    songs.add(mockSong)
                }
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return songs
    }

    fun loadM3UOnline(
        context: Context,
        uri: Uri,
    ): ArrayList<Song> {
        val songs = ArrayList<Song>()

        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                if (lines.first().startsWith("#EXTM3U")) {
                    lines.forEachIndexed { _, rawLine ->
                        if (rawLine.startsWith("#EXTINF:")) {
                            // maybe later write this to be more efficient
                            val artists =
                                rawLine.substringAfter("#EXTINF:").substringAfter(',').substringBefore(" - ").split(';')
                            val title = rawLine.substringAfter("#EXTINF:").substringAfter(',').substringAfter(" - ")

                            val mockSong = Song(
                                song = SongEntity(
                                    id = "",
                                    title = title,
                                ),
                                artists = artists.map { ArtistEntity("", it) },
                            )
                            songs.add(mockSong)

                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return songs
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}
