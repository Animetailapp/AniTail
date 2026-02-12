package com.anitail.desktop.storage

import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.security.DesktopPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class AutoBackupExecutionResult(
    val file: Path,
)

object DesktopAutoBackupService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var activeConfig: AutoBackupConfig? = null
    private val filenameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    @Volatile
    var lastErrorMessage: String = ""
        private set

    fun startOrUpdate(
        preferences: DesktopPreferences,
        authService: DesktopAuthService,
    ) {
        val config = AutoBackupConfig.fromPreferences(preferences)
        if (!config.enabled) {
            stop()
            return
        }
        if (activeConfig == config && loopJob?.isActive == true) {
            return
        }

        activeConfig = config
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                delay(config.frequencyMillis())
                runCatching {
                    createBackupNow(preferences = preferences, authService = authService)
                }.onFailure { error ->
                    lastErrorMessage = error.message.orEmpty()
                }
            }
        }
    }

    fun stop() {
        activeConfig = null
        loopJob?.cancel()
        loopJob = null
    }

    suspend fun createBackupNow(
        preferences: DesktopPreferences,
        authService: DesktopAuthService,
    ): AutoBackupExecutionResult = withContext(Dispatchers.IO) {
        val config = AutoBackupConfig.fromPreferences(preferences)
        val backupService = DesktopBackupRestoreService(
            preferences = preferences,
            authService = authService,
        )
        val targetDirectory = resolveBackupDirectory(config)
        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory)
        }
        val filename = "AniTail_AutoBackup_${LocalDateTime.now().format(filenameFormatter)}.backup"
        val targetFile = targetDirectory.resolve(filename)
        backupService.backupTo(targetFile)
        enforceRetention(targetDirectory, config.keepCount)
        lastErrorMessage = ""
        AutoBackupExecutionResult(file = targetFile)
    }

    private fun resolveBackupDirectory(config: AutoBackupConfig): Path {
        if (config.useCustomLocation && config.customLocation.isNotBlank()) {
            val custom = runCatching { Path.of(config.customLocation) }.getOrNull()
            if (custom != null) {
                return custom
            }
        }
        return DesktopPaths.autoBackupDir()
    }

    private fun enforceRetention(directory: Path, keepCount: Int) {
        val files = Files.list(directory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { path ->
                    val name = path.fileName?.toString().orEmpty()
                    name.endsWith(".backup", ignoreCase = true) ||
                        name.endsWith(".zip", ignoreCase = true)
                }
                .toList()
        }.sortedByDescending { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }

        if (files.size <= keepCount) return
        files.drop(keepCount).forEach { oldFile ->
            runCatching { Files.deleteIfExists(oldFile) }
        }
    }
}

private data class AutoBackupConfig(
    val enabled: Boolean,
    val frequencyHours: Int,
    val keepCount: Int,
    val useCustomLocation: Boolean,
    val customLocation: String,
) {
    fun frequencyMillis(): Long = frequencyHours.toLong() * 60L * 60L * 1000L

    companion object {
        fun fromPreferences(preferences: DesktopPreferences): AutoBackupConfig {
            return AutoBackupConfig(
                enabled = preferences.autoBackupEnabled.value,
                frequencyHours = BackupFrequency.fromHours(preferences.autoBackupFrequencyHours.value).hours,
                keepCount = preferences.autoBackupKeepCount.value.coerceIn(1, 20),
                useCustomLocation = preferences.autoBackupUseCustomLocation.value,
                customLocation = preferences.autoBackupCustomLocation.value,
            )
        }
    }
}
