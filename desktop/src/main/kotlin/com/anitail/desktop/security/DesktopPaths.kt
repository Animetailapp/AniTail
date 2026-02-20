package com.anitail.desktop.security

import java.nio.file.Path
import java.nio.file.Paths

object DesktopPaths {
    fun userHome(): Path = Paths.get(System.getProperty("user.home") ?: ".")

    fun appDataDir(): Path = userHome().resolve(".anitail")

    fun downloadsMetadataFile(): Path = appDataDir().resolve("downloads.json")

    fun credentialsFile(): Path = appDataDir().resolve("credentials.json")

    fun preferencesFile(): Path = appDataDir().resolve("preferences.json")

    fun androidSettingsPreferencesFile(): Path = appDataDir().resolve("settings.preferences_pb")

    fun lyricsOverridesFile(): Path = appDataDir().resolve("lyrics_overrides.json")

    fun lastFmPendingFile(): Path = appDataDir().resolve("lastfm_pending_scrobbles.json")

    fun androidLastFmOfflineFile(): Path = appDataDir().resolve("lastfm_offline.xml")

    fun databaseDir(): Path = appDataDir().resolve("database")

    fun downloadsDir(): Path = userHome().resolve("Downloads")

    fun autoBackupDir(): Path = downloadsDir().resolve("AniTail").resolve("AutoBackup")
}
