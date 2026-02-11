package com.anitail.desktop.security

import java.nio.file.Path
import java.nio.file.Paths

object DesktopPaths {
    fun userHome(): Path = Paths.get(System.getProperty("user.home") ?: ".")

    fun appDataDir(): Path = userHome().resolve(".anitail")

    fun downloadsMetadataFile(): Path = appDataDir().resolve("downloads.json")

    fun credentialsFile(): Path = appDataDir().resolve("credentials.json")

    fun preferencesFile(): Path = appDataDir().resolve("preferences.json")

    fun lyricsOverridesFile(): Path = appDataDir().resolve("lyrics_overrides.json")

    fun lastFmPendingFile(): Path = appDataDir().resolve("lastfm_pending_scrobbles.json")

    fun databaseDir(): Path = appDataDir().resolve("database")
}
