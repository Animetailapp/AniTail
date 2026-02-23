@file:Suppress("DEPRECATION")

package com.anitail.music.utils

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val LEGACY_INITIAL_BACKUP_NAME = "initial_backup.zip"
        private const val LEGACY_MERGED_BACKUP_NAME = "merged_backup.zip"
    }

    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    private var driveService: Drive? = null

    private val googleSignInClient: GoogleSignInClient by lazy {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        GoogleSignIn.getClient(context, signInOptions)
    }

    init {
        // Try to restore session from previous sign-in
        // Note: silentSignIn should be called on a coroutine, so we check synchronously first
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            Timber.d("Found existing Google account: ${account.email}")
            _signedInAccount.value = account
            initializeDriveService(account)
        }
    }

    /**
     * Try to restore session silently. Call this on app startup from a coroutine.
     */
    suspend fun trySilentSignIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            val account = googleSignInClient.silentSignIn().await()
            Timber.d("Silent sign-in successful: ${account.email}")
            _signedInAccount.value = account
            initializeDriveService(account)
            true
        } catch (e: Exception) {
            Timber.d("Silent sign-in failed or no previous session: ${e.message}")
            false
        }
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    suspend fun handleSignInResult(result: ActivityResult): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.await()
                _signedInAccount.value = account
                initializeDriveService(account)
                true
            } catch (e: Exception) {
                Timber.e(e, "Google Sign-In failed")
                false
            }
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                googleSignInClient.signOut().await()
                _signedInAccount.value = null
                driveService = null
            } catch (e: Exception) {
                Timber.e(e, "Sign out failed")
            }
        }
    }

    private fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("AniTail")
            .build()
    }

    suspend fun uploadBackup(backupFile: File): Result<String> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext Result.failure(Exception("Not signed in"))

        try {
            // Find or create AniTail Backups folder
            val folderId = getOrCreateBackupFolder()

            // Create file metadata
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = backupFile.name
                parents = listOf(folderId)
            }

            val mediaContent = FileContent("application/octet-stream", backupFile)

            val file = service.files().create(fileMetadata, mediaContent)
                .setFields("id, name")
                .execute()

            Timber.d("Backup uploaded: ${file.name} (${file.id})")
            Result.success(file.id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload backup")
            Result.failure(e)
        }
    }

    suspend fun uploadBackupReplacingByName(
        backupFile: File,
        remoteName: String = backupFile.name,
    ): Result<String> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext Result.failure(Exception("Not signed in"))

        try {
            val folderId = getOrCreateBackupFolder()
            val mediaContent = FileContent("application/octet-stream", backupFile)
            val existingFileId = findFileIdInFolder(folderId, remoteName)

            val file = if (existingFileId != null) {
                service.files().update(existingFileId, null, mediaContent)
                    .setFields("id, name")
                    .execute()
            } else {
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = remoteName
                    parents = listOf(folderId)
                }
                service.files().create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()
            }

            Timber.d("Backup uploaded (replace-by-name): ${file.name} (${file.id})")
            Result.success(file.id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload backup with replace-by-name")
            Result.failure(e)
        }
    }

    suspend fun uploadBackup(uri: android.net.Uri, name: String): Result<String> =
        withContext(Dispatchers.IO) {
            val service =
                driveService ?: return@withContext Result.failure(Exception("Not signed in"))

            try {
                val folderId = getOrCreateBackupFolder()

                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    this.name = name
                    parents = listOf(folderId)
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Could not open stream for uri: $uri"))

                val mediaContent = com.google.api.client.http.InputStreamContent(
                    "application/octet-stream",
                    inputStream
                )

                val file = service.files().create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()

                Timber.d("Backup uploaded from URI: ${file.name} (${file.id})")
                Result.success(file.id)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload backup from URI")
                Result.failure(e)
            }
        }

    suspend fun uploadBackupReplacingByName(uri: android.net.Uri, name: String): Result<String> =
        withContext(Dispatchers.IO) {
            val service =
                driveService ?: return@withContext Result.failure(Exception("Not signed in"))

            try {
                val folderId = getOrCreateBackupFolder()
                val existingFileId = findFileIdInFolder(folderId, name)

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Could not open stream for uri: $uri"))

                val mediaContent = com.google.api.client.http.InputStreamContent(
                    "application/octet-stream",
                    inputStream
                )

                val file = if (existingFileId != null) {
                    service.files().update(existingFileId, null, mediaContent)
                        .setFields("id, name")
                        .execute()
                } else {
                    val fileMetadata = com.google.api.services.drive.model.File().apply {
                        this.name = name
                        parents = listOf(folderId)
                    }
                    service.files().create(fileMetadata, mediaContent)
                        .setFields("id, name")
                        .execute()
                }

                Timber.d("Backup uploaded from URI (replace-by-name): ${file.name} (${file.id})")
                Result.success(file.id)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload backup from URI with replace-by-name")
                Result.failure(e)
            }
        }

    suspend fun downloadLatestBackup(destinationFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            val service =
                driveService ?: return@withContext Result.failure(Exception("Not signed in"))

            try {
                val folderId = getOrCreateBackupFolder()

                // Find latest backup file
                val result = service.files().list()
                    .setQ(
                        "'$folderId' in parents and (" +
                            "name contains 'AniTail' or " +
                            "name = '$LEGACY_INITIAL_BACKUP_NAME' or " +
                            "name = '$LEGACY_MERGED_BACKUP_NAME'" +
                            ") and trashed = false"
                    )
                    .setOrderBy("modifiedTime desc")
                    .setPageSize(1)
                    .setFields("files(id, name, modifiedTime, createdTime)")
                    .execute()

                val files = result.files
                if (files.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("No backups found"))
                }

                val latestFile = files.first()

                // Download file
                val outputStream: OutputStream = FileOutputStream(destinationFile)
                service.files().get(latestFile.id).executeMediaAndDownloadTo(outputStream)
                outputStream.close()

                Timber.d("Downloaded backup: ${latestFile.name}")
                Result.success(destinationFile)
            } catch (e: Exception) {
                Timber.e(e, "Failed to download backup")
                Result.failure(e)
            }
        }

    suspend fun listBackups(): Result<List<DriveBackupInfo>> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext Result.failure(Exception("Not signed in"))

        try {
            val folderId = getOrCreateBackupFolder()

            val result = service.files().list()
                .setQ(
                    "'$folderId' in parents and (" +
                        "name contains 'AniTail' or " +
                        "name = '$LEGACY_INITIAL_BACKUP_NAME' or " +
                        "name = '$LEGACY_MERGED_BACKUP_NAME'" +
                        ") and trashed = false"
                )
                .setOrderBy("modifiedTime desc")
                .setPageSize(20)
                .setFields("files(id, name, modifiedTime, createdTime, size)")
                .execute()

            val backups = result.files?.map { file ->
                DriveBackupInfo(
                    id = file.id,
                    name = file.name,
                    createdTime = file.modifiedTime?.value ?: file.createdTime?.value ?: 0L,
                    size = file.size.toLong()
                )
            } ?: emptyList()

            Result.success(backups)
        } catch (e: Exception) {
            Timber.e(e, "Failed to list backups")
            Result.failure(e)
        }
    }

    private suspend fun getOrCreateBackupFolder(): String = withContext(Dispatchers.IO) {
        val service = driveService ?: throw Exception("Not signed in")
        val folderName = "AniTail Backups"

        // Search for existing folder
        val result = service.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        if (result.files.isNotEmpty()) {
            return@withContext result.files.first().id
        }

        // Create folder if not exists
        val folderMetadata = com.google.api.services.drive.model.File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
        }

        val folder = service.files().create(folderMetadata)
            .setFields("id")
            .execute()

        Timber.d("Created backup folder: ${folder.id}")
        folder.id
    }

    private suspend fun findFileIdInFolder(
        folderId: String,
        fileName: String,
    ): String? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        val escapedFileName = fileName.replace("'", "\\'")
        val result = service.files().list()
            .setQ("'$folderId' in parents and name = '$escapedFileName' and trashed = false")
            .setPageSize(1)
            .setFields("files(id)")
            .execute()

        result.files?.firstOrNull()?.id
    }

    fun isSignedIn(): Boolean = _signedInAccount.value != null
}

data class DriveBackupInfo(
    val id: String,
    val name: String,
    val createdTime: Long,
    val size: Long
)
