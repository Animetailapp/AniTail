package com.anitail.desktop.update

import com.anitail.desktop.storage.DesktopPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Properties

data class DesktopReleaseInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val releasePageUrl: String,
)

data class DesktopUpdateCheckResult(
    val releaseInfo: DesktopReleaseInfo,
    val isUpdateAvailable: Boolean,
)

object DesktopUpdater {
    private const val RepoOwner = "Animetailapp"
    private const val RepoName = "Anitail-Desktop"
    private const val ApiLatestReleaseUrl = "https://api.github.com/repos/$RepoOwner/$RepoName/releases/latest"
    private const val ApiReleasesUrl = "https://api.github.com/repos/$RepoOwner/$RepoName/releases"
    private const val ApiTagsUrl = "https://api.github.com/repos/$RepoOwner/$RepoName/tags"
    private const val DefaultReleasePage = "https://github.com/$RepoOwner/$RepoName/releases/latest"

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000L
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 20_000L
        }
    }

    suspend fun maybeCheckForUpdates(
        preferences: DesktopPreferences,
    ): Result<DesktopUpdateCheckResult?> = runCatching {
        if (!shouldCheckForUpdates(preferences)) {
            return@runCatching null
        }
        checkForUpdates(preferences = preferences, force = true).getOrThrow()
    }

    suspend fun checkForUpdates(
        preferences: DesktopPreferences,
        force: Boolean = false,
    ): Result<DesktopUpdateCheckResult> = runCatching {
        if (!force && !shouldCheckForUpdates(preferences)) {
            val cachedVersion = preferences.latestVersionName.value
            val cachedUrl = preferences.latestVersionDownloadUrl.value
            if (cachedVersion.isNotBlank() && cachedUrl.isNotBlank()) {
                val releaseInfo = DesktopReleaseInfo(
                    versionName = cachedVersion,
                    downloadUrl = cachedUrl,
                    releaseNotes = preferences.latestVersionReleaseNotes.value,
                    releasePageUrl = preferences.latestVersionReleasePageUrl.value.ifBlank { DefaultReleasePage },
                )
                return@runCatching DesktopUpdateCheckResult(
                    releaseInfo = releaseInfo,
                    isUpdateAvailable = isVersionNewer(cachedVersion, currentVersionName()),
                )
            }
        }

        val checkedAt = System.currentTimeMillis()
        preferences.setUpdateLastCheckTime(checkedAt)
        val releaseInfo = getLatestReleaseInfo().getOrThrow()
        preferences.updateLatestReleaseCheck(
            checkTimeMs = checkedAt,
            versionName = releaseInfo.versionName,
            downloadUrl = releaseInfo.downloadUrl,
            releaseNotes = releaseInfo.releaseNotes,
            releasePageUrl = releaseInfo.releasePageUrl,
        )
        DesktopUpdateCheckResult(
            releaseInfo = releaseInfo,
            isUpdateAvailable = isVersionNewer(releaseInfo.versionName, currentVersionName()),
        )
    }

    suspend fun getLatestReleaseInfo(): Result<DesktopReleaseInfo> = runCatching {
        val payload = fetchReleasePayload()
        if (payload == null) {
            val version = currentVersionName()
            return@runCatching DesktopReleaseInfo(
                versionName = version,
                downloadUrl = DefaultReleasePage,
                releaseNotes = "",
                releasePageUrl = DefaultReleasePage,
            )
        }

        val versionName = payload.optString("name")
            .ifBlank { payload.optString("tag_name") }
            .trim()
        if (versionName.isBlank()) {
            error("Release version is empty")
        }

        val releasePageUrl = payload.optString("html_url").ifBlank { DefaultReleasePage }
        val notes = payload.optString("body", "")
        val assets = payload.optJSONArray("assets") ?: JSONArray()
        val downloadUrl = resolveAssetDownloadUrl(assets, releasePageUrl)

        DesktopReleaseInfo(
            versionName = versionName,
            downloadUrl = downloadUrl,
            releaseNotes = notes,
            releasePageUrl = releasePageUrl,
        )
    }

    private suspend fun fetchReleasePayload(): JSONObject? {
        val latestResponse = githubGet(ApiLatestReleaseUrl)
        if (latestResponse.status.isSuccess()) {
            return JSONObject(latestResponse.bodyAsText())
        }
        if (latestResponse.status.value != 404) {
            error("GitHub API error ${latestResponse.status.value}")
        }

        val releasesResponse = githubGet("$ApiReleasesUrl?per_page=1")
        if (releasesResponse.status.isSuccess()) {
            val releases = JSONArray(releasesResponse.bodyAsText())
            if (releases.length() > 0) {
                return releases.optJSONObject(0)
            }
        } else if (releasesResponse.status.value != 404) {
            error("GitHub API error ${releasesResponse.status.value}")
        }

        val tagsResponse = githubGet("$ApiTagsUrl?per_page=1")
        if (tagsResponse.status.isSuccess()) {
            val tags = JSONArray(tagsResponse.bodyAsText())
            val firstTag = tags.optJSONObject(0)?.optString("name").orEmpty().trim()
            if (firstTag.isNotBlank()) {
                return JSONObject(
                    mapOf(
                        "tag_name" to firstTag,
                        "html_url" to "https://github.com/$RepoOwner/$RepoName/releases/tag/$firstTag",
                        "body" to "",
                    ),
                )
            }
            return null
        }
        if (tagsResponse.status.value == 404) {
            return null
        }
        error("GitHub API error ${tagsResponse.status.value}")
    }

    private suspend fun githubGet(url: String) = client.get(url) {
        header(HttpHeaders.UserAgent, "AniTail-Desktop-Updater")
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    fun shouldCheckForUpdates(
        preferences: DesktopPreferences,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!preferences.autoUpdateEnabled.value) return false
        val frequency = preferences.autoUpdateCheckFrequency.value
        if (frequency == com.anitail.desktop.storage.UpdateCheckFrequency.NEVER) return false

        val lastCheck = preferences.lastUpdateCheckTimeMs.value
        return lastCheck < 0L || nowMs - lastCheck >= frequency.toMillis()
    }

    fun isVersionNewer(latestVersion: String, currentVersion: String): Boolean {
        val latestParts = numericVersionParts(latestVersion)
        val currentParts = numericVersionParts(currentVersion)
        val maxSize = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until maxSize) {
            val latest = latestParts.getOrElse(index) { 0 }
            val current = currentParts.getOrElse(index) { 0 }
            if (latest > current) return true
            if (latest < current) return false
        }
        return false
    }

    fun currentVersionName(): String {
        val systemProperties = listOf(
            "anitail.version",
            "app.version",
            "jpackage.app-version",
        )
        for (key in systemProperties) {
            val value = System.getProperty(key).orEmpty().trim()
            if (value.isNotBlank()) return value
        }

        val implementationVersion = DesktopUpdater::class.java.`package`?.implementationVersion.orEmpty().trim()
        if (implementationVersion.isNotBlank()) return implementationVersion

        val resourceVersion = runCatching {
            DesktopUpdater::class.java.getResourceAsStream("/version.properties")?.use { stream ->
                val props = Properties().apply { load(stream) }
                props.getProperty("version", "").trim()
            }.orEmpty()
        }.getOrDefault("")
        if (resourceVersion.isNotBlank()) return resourceVersion

        val devVersion = runCatching {
            val path = Path.of("desktop", "build.gradle.kts")
            if (!Files.exists(path)) return@runCatching ""
            val content = Files.readString(path, StandardCharsets.UTF_8)
            Regex("packageVersion\\s*=\\s*\"([^\"]+)\"")
                .find(content)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
                .trim()
        }.getOrDefault("")
        if (devVersion.isNotBlank()) return devVersion

        return "0.0.0"
    }

    fun openDownloadUrl(url: String): Result<Unit> = runCatching {
        val target = url.trim().ifBlank { DefaultReleasePage }
        Desktop.getDesktop().browse(URI(target))
    }

    private fun resolveAssetDownloadUrl(
        assetsJson: JSONArray,
        fallbackReleasePage: String,
    ): String {
        val assets = buildList {
            for (index in 0 until assetsJson.length()) {
                val item = assetsJson.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val url = item.optString("browser_download_url").trim()
                if (name.isNotBlank() && url.isNotBlank()) {
                    add(name to url)
                }
            }
        }

        if (assets.isEmpty()) return fallbackReleasePage

        val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        val archName = System.getProperty("os.arch").orEmpty().lowercase(Locale.ROOT)
        return selectDownloadUrlForPlatform(
            assets = assets,
            osName = osName,
            archName = archName,
        ) ?: assets.first().second
    }

    internal fun selectDownloadUrlForPlatform(
        assets: List<Pair<String, String>>,
        osName: String,
        archName: String,
    ): String? {
        val suffixPriority = when {
            osName.contains("win") && archName.contains("aarch64") ->
                listOf("windows-arm64.msi", "windows-x64.msi")
            osName.contains("win") ->
                listOf("windows-x64.msi", "windows-arm64.msi")
            osName.contains("mac") && archName.contains("aarch64") ->
                listOf("macos-arm64.dmg", "macos-x64.dmg")
            osName.contains("mac") ->
                listOf("macos-x64.dmg", "macos-arm64.dmg")
            osName.contains("linux") && archName.contains("aarch64") ->
                listOf("linux-arm64.deb", "linux-arm64.rpm", "linux-x64.deb", "linux-x64.rpm")
            osName.contains("linux") ->
                listOf("linux-x64.deb", "linux-x64.rpm", "linux-arm64.deb", "linux-arm64.rpm")
            else -> emptyList()
        }

        val normalized = assets.map { it.first.lowercase(Locale.ROOT) to it.second }
        for (suffix in suffixPriority) {
            val match = normalized.firstOrNull { it.first.endsWith(suffix) }
            if (match != null) return match.second
        }

        val osToken = when {
            osName.contains("win") -> "windows"
            osName.contains("mac") -> "macos"
            osName.contains("linux") -> "linux"
            else -> ""
        }
        if (osToken.isNotEmpty()) {
            val match = normalized.firstOrNull { it.first.contains(osToken) }
            if (match != null) return match.second
        }

        return null
    }

    private fun numericVersionParts(value: String): List<Int> {
        return Regex("""\d+""")
            .findAll(value)
            .mapNotNull { it.value.toIntOrNull() }
            .take(6)
            .toList()
            .ifEmpty { listOf(0) }
    }
}
