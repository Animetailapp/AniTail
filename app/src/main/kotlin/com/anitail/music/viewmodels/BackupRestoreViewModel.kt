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
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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
        FileOutputStream(file).use { outputStream ->
            writeBackupArchive(context, outputStream)
        }
    }

    fun restoreFromDrive(context: Context) {
        viewModelScope.launch {
            val tempFile = File(context.cacheDir, "AniTail_Restore_Temp.zip")
            try {
                val result = googleDriveSyncManager.downloadLatestBackup(tempFile)
                result.onSuccess {
                    viewModelScope.launch {
                        restoreFromFile(context, tempFile)
                    }
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

    private suspend fun restoreFromFile(context: Context, file: File) {
        runCatching {
            // Cancel any running sync before closing the database
            SyncWorker.cancel(context)
            MusicDatabase.isRestoring.set(true)
            withContext(Dispatchers.IO) {
                FileInputStream(file).use { backupInput ->
                    restoreFromBackupArchive(context, backupInput)
                }
            }
            restartAppAfterRestore(context)
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun backupNow(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.applicationContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                writeBackupArchive(context, outputStream)
            } ?: throw IllegalStateException("Unable to open backup output stream for URI: $uri")
        }
    }

    fun backup(context: Context, uri: Uri, showToast: Boolean = true) {
        viewModelScope.launch {
            backupNow(context, uri).onSuccess {
                if (showToast) {
                    // Only show the toast if requested (not from background worker)
                    Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT)
                        .show()
                }
            }.onFailure {
                reportException(it)
                if (showToast) {
                    Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    fun restore(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // Cancel any running sync before closing the database
                SyncWorker.cancel(context)
                MusicDatabase.isRestoring.set(true)

                context.applicationContext.contentResolver.openInputStream(uri)?.use { backupInput ->
                    restoreFromBackupArchive(context, backupInput)
                } ?: throw IllegalStateException("Unable to open backup input stream for URI: $uri")
                restartAppAfterRestore(context)
            }.onFailure {
                reportException(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun writeBackupArchive(context: Context, rawOutputStream: OutputStream) {
        rawOutputStream.buffered().zipOutputStream().use { outputStream ->
            (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered()
                .use { inputStream ->
                    outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                    inputStream.copyTo(outputStream)
                    outputStream.closeEntry()
                }

            database.checkpoint()
            val dbPath = database.openHelper.writableDatabase.path
            val databaseFiles = listOf(
                File(dbPath),
                File("$dbPath-wal"),
                File("$dbPath-shm")
            )

            databaseFiles.forEach { dbFile ->
                if (!dbFile.exists()) return@forEach
                FileInputStream(dbFile).use { inputStream ->
                    outputStream.putNextEntry(ZipEntry(dbFile.name))
                    inputStream.copyTo(outputStream)
                    outputStream.closeEntry()
                }
            }
        }
    }

    private fun restoreFromBackupArchive(context: Context, rawInputStream: InputStream) {
        val dbPath = database.openHelper.writableDatabase.path
        val databaseFile = File(dbPath)
        val walFile = File("$dbPath-wal")
        val shmFile = File("$dbPath-shm")

        database.checkpoint()
        database.close()
        walFile.delete()
        shmFile.delete()

        var restoredDatabase = false

        rawInputStream.zipInputStream().use { inputStream ->
            var entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
            while (entry != null) {
                when (entry.name) {
                    SETTINGS_FILENAME -> {
                        val settingsFile = context.filesDir / "datastore" / SETTINGS_FILENAME
                        settingsFile.parentFile?.mkdirs()
                        settingsFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    InternalDatabase.DB_NAME -> {
                        FileOutputStream(databaseFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        restoredDatabase = true
                    }

                    InternalDatabase.DB_WAL_NAME -> {
                        FileOutputStream(walFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    InternalDatabase.DB_SHM_NAME -> {
                        FileOutputStream(shmFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
            }
        }

        if (!restoredDatabase) {
            throw IllegalStateException("Backup archive is missing ${InternalDatabase.DB_NAME}")
        }
    }

    private fun restartAppAfterRestore(context: Context) {
        context.stopService(Intent(context, MusicService::class.java))
        context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
        context.startActivity(Intent(context, MainActivity::class.java))
        exitProcess(0)
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
