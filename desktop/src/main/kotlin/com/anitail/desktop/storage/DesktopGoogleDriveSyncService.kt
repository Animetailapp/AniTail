package com.anitail.desktop.storage

import com.anitail.desktop.security.DesktopPaths
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.User
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

data class DesktopDriveBackupInfo(
    val id: String,
    val name: String,
    val createdTimeMillis: Long,
    val sizeBytes: Long,
)

object DesktopGoogleDriveSyncService {
    private const val APP_NAME = "AniTail Desktop"
    private const val BACKUPS_FOLDER_NAME = "AniTail Backups"
    private const val TOKEN_STORE_DIR = "google_tokens"
    private const val OAUTH_USER_ID = "desktop_user"
    private const val BACKUP_MIME_TYPE = "application/octet-stream"
    private const val CLIENT_SECRET_FILE_NAME = "client_secret.json"
    private const val CLIENT_SECRET_RESOURCE_PATH = "/oauth/client_secret.json"
    const val CLOUD_SYNC_REMOTE_BACKUP_NAME = "AniTail_Desktop_CloudSync.backup"
    const val MANUAL_REMOTE_BACKUP_NAME = "AniTail_Desktop_Backup.backup"

    private val _signedInEmail = MutableStateFlow<String?>(null)
    val signedInEmail: StateFlow<String?> = _signedInEmail.asStateFlow()

    @Volatile
    private var driveService: Drive? = null

    @Volatile
    private var oauthFlow: GoogleAuthorizationCodeFlow? = null

    @Volatile
    private var extractedBundledSecretPath: Path? = null

    suspend fun isConfigured(): Boolean = withContext(Dispatchers.IO) {
        resolveClientSecretPath() != null
    }

    suspend fun signIn(): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            ensureDriveService(forceAuthPrompt = true)
            val email = fetchCurrentUserEmail()
            _signedInEmail.value = email
            email
        }
    }

    suspend fun trySilentRestore(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ensureDriveService(
                forceAuthPrompt = false,
                allowInteractiveAuth = false,
            )
            _signedInEmail.value = fetchCurrentUserEmail()
            true
        }.getOrDefault(false)
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            driveService = null
            oauthFlow = null
            _signedInEmail.value = null
            val tokenDir = tokenStorePath()
            if (Files.exists(tokenDir)) {
                Files.walk(tokenDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    suspend fun uploadBackup(backupFile: Path): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(Files.exists(backupFile)) { "Backup file not found." }
            val service = ensureDriveService(
                forceAuthPrompt = false,
                allowInteractiveAuth = true,
            )
            val folderId = getOrCreateBackupsFolder(service)

            val metadata = File().apply {
                name = backupFile.fileName.toString()
                parents = listOf(folderId)
            }
            val media = FileContent(BACKUP_MIME_TYPE, backupFile.toFile())
            val uploaded = service.files().create(metadata, media)
                .setFields("id,name")
                .execute()
            uploaded.id ?: error("Upload succeeded without file id.")
        }
    }

    suspend fun uploadBackupReplacingByName(
        backupFile: Path,
        remoteName: String = backupFile.fileName.toString(),
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(Files.exists(backupFile)) { "Backup file not found." }
            val service = ensureDriveService(
                forceAuthPrompt = false,
                allowInteractiveAuth = true,
            )
            val folderId = getOrCreateBackupsFolder(service)
            val existingFileId = findFileIdInFolderByName(service, folderId, remoteName)
            val media = FileContent(BACKUP_MIME_TYPE, backupFile.toFile())

            val uploaded = if (existingFileId != null) {
                service.files().update(existingFileId, null, media)
                    .setFields("id,name")
                    .execute()
            } else {
                val metadata = File().apply {
                    name = remoteName
                    parents = listOf(folderId)
                }
                service.files().create(metadata, media)
                    .setFields("id,name")
                    .execute()
            }

            uploaded.id ?: error("Upload succeeded without file id.")
        }
    }

    suspend fun downloadLatestBackup(destinationFile: Path): Result<Path> = withContext(Dispatchers.IO) {
        runCatching {
            val service = ensureDriveService(
                forceAuthPrompt = false,
                allowInteractiveAuth = true,
            )
            val latest = findLatestBackup(service) ?: error("No backups found in Google Drive.")

            destinationFile.parent?.let { parent ->
                if (!Files.exists(parent)) Files.createDirectories(parent)
            }
            Files.newOutputStream(destinationFile).use { output ->
                service.files().get(latest.id).executeMediaAndDownloadTo(output)
            }
            destinationFile
        }
    }

    suspend fun listBackups(limit: Int = 20): Result<List<DesktopDriveBackupInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val service = ensureDriveService(
                forceAuthPrompt = false,
                allowInteractiveAuth = true,
            )
            val folderId = getOrCreateBackupsFolder(service)
            val response: FileList = service.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setOrderBy("modifiedTime desc")
                .setPageSize(limit)
                .setFields("files(id,name,modifiedTime,createdTime,size)")
                .execute()

            response.files.orEmpty().map { file ->
                DesktopDriveBackupInfo(
                    id = file.id.orEmpty(),
                    name = file.name.orEmpty(),
                    createdTimeMillis = file.modifiedTime?.value ?: file.createdTime?.value ?: 0L,
                    sizeBytes = file.size.toLong(),
                )
            }.filter { it.id.isNotBlank() }
        }
    }

    private fun ensureDriveService(
        forceAuthPrompt: Boolean,
        allowInteractiveAuth: Boolean = true,
    ): Drive {
        driveService?.let { return it }
        val flow = oauthFlow ?: buildFlow().also { oauthFlow = it }
        val receiver = LocalServerReceiver.Builder()
            .setHost("localhost")
            .setPort(8888)
            .build()
        val credential = if (forceAuthPrompt) {
            AuthorizationCodeInstalledApp(flow, receiver).authorize(OAUTH_USER_ID)
        } else {
            flow.loadCredential(OAUTH_USER_ID)
                ?: if (allowInteractiveAuth) {
                    AuthorizationCodeInstalledApp(flow, receiver).authorize(OAUTH_USER_ID)
                } else {
                    error("No cached Google Drive credential available.")
                }
        }

        val createdService = Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        )
            .setApplicationName(APP_NAME)
            .build()
        driveService = createdService
        return createdService
    }

    private fun buildFlow(): GoogleAuthorizationCodeFlow {
        val secretPath = resolveClientSecretPath()
            ?: error("Google OAuth client secret file not found.")

        val clientSecrets = Files.newInputStream(secretPath).use { input ->
            InputStreamReader(input).use { reader ->
                GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), reader)
            }
        }
        val tokenDir = tokenStorePath()
        if (!Files.exists(tokenDir)) Files.createDirectories(tokenDir)

        return GoogleAuthorizationCodeFlow.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            clientSecrets,
            listOf(DriveScopes.DRIVE_FILE),
        )
            .setDataStoreFactory(FileDataStoreFactory(tokenDir.toFile()))
            .setAccessType("offline")
            .build()
    }

    private fun fetchCurrentUserEmail(): String? {
        val service = driveService ?: return null
        val user: User? = service.about().get()
            .setFields("user(emailAddress)")
            .execute()
            .user
        return user?.emailAddress?.takeIf { it.isNotBlank() }
    }

    private fun getOrCreateBackupsFolder(service: Drive): String {
        val existing = service.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '$BACKUPS_FOLDER_NAME' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id,name)")
            .execute()
            .files
            ?.firstOrNull()
        if (existing?.id?.isNotBlank() == true) return existing.id

        val folderMetadata = File().apply {
            name = BACKUPS_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        val created = service.files().create(folderMetadata)
            .setFields("id")
            .execute()
        return created.id ?: error("Failed to create Google Drive backups folder.")
    }

    private fun findLatestBackup(service: Drive): DesktopDriveBackupInfo? {
        val folderId = getOrCreateBackupsFolder(service)
        val query = "'$folderId' in parents and trashed = false and (name contains '.backup' or name contains '.zip')"
        val response = service.files().list()
            .setQ(query)
            .setOrderBy("modifiedTime desc")
            .setPageSize(1)
            .setFields("files(id,name,modifiedTime,createdTime,size)")
            .execute()
        val file = response.files?.firstOrNull() ?: return null
        val id = file.id ?: return null
        return DesktopDriveBackupInfo(
            id = id,
            name = file.name.orEmpty(),
            createdTimeMillis = file.modifiedTime?.value ?: file.createdTime?.value ?: 0L,
            sizeBytes = file.size.toLong(),
        )
    }

    private fun findFileIdInFolderByName(
        service: Drive,
        folderId: String,
        fileName: String,
    ): String? {
        val escaped = fileName.replace("'", "\\'")
        val response = service.files().list()
            .setQ("'$folderId' in parents and name = '$escaped' and trashed = false")
            .setPageSize(1)
            .setFields("files(id)")
            .execute()
        return response.files?.firstOrNull()?.id
    }

    private fun tokenStorePath(): Path = DesktopPaths.appDataDir().resolve(TOKEN_STORE_DIR)

    private fun resolveClientSecretPath(): Path? {
        val envPath = System.getenv("ANITAIL_GOOGLE_CLIENT_SECRET")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }
        if (envPath != null && Files.exists(envPath)) return envPath

        val appDataSecret = DesktopPaths.appDataDir().resolve(CLIENT_SECRET_FILE_NAME)
        if (Files.exists(appDataSecret)) return appDataSecret

        val defaultName = Path.of(CLIENT_SECRET_FILE_NAME)
        if (Files.exists(defaultName)) return defaultName

        val wildcardFromCwd = Files.list(Path.of(".")).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { path ->
                    val name = path.fileName.toString()
                    name.startsWith("client_secret_") && name.endsWith(".json")
                }
                .findFirst()
                .orElse(null)
        }
        if (wildcardFromCwd != null) return wildcardFromCwd

        return resolveBundledClientSecretPath()
    }

    private fun resolveBundledClientSecretPath(): Path? {
        val cached = extractedBundledSecretPath
        if (cached != null && Files.exists(cached)) return cached

        val resourceStream = DesktopGoogleDriveSyncService::class.java
            .getResourceAsStream(CLIENT_SECRET_RESOURCE_PATH)
            ?: return null

        val targetFile = DesktopPaths.appDataDir()
            .resolve("oauth")
            .resolve("client_secret.bundled.json")
        targetFile.parent?.let { parent ->
            if (!Files.exists(parent)) Files.createDirectories(parent)
        }

        resourceStream.use { input ->
            Files.newOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        extractedBundledSecretPath = targetFile
        return targetFile
    }
}
