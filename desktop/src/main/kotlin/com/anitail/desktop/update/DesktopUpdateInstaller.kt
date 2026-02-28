package com.anitail.desktop.update

import com.anitail.desktop.security.DesktopPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

data class DesktopUpdateInstallResult(
    val installerPath: Path,
)

object DesktopUpdateInstaller {
    suspend fun downloadAndInstall(downloadUrl: String): Result<DesktopUpdateInstallResult> = runCatching {
        require(downloadUrl.isNotBlank()) { "downloadUrl is blank" }
        withContext(Dispatchers.IO) {
            val updatesDir = DesktopPaths.appDataDir().resolve("updates")
            if (!Files.exists(updatesDir)) {
                Files.createDirectories(updatesDir)
            }

            val installerName = resolveInstallerFileName(downloadUrl)
            val destination = updatesDir.resolve(installerName)
            downloadFile(downloadUrl, destination)
            pruneOldInstallers(updatesDir, keepCount = 3)
            launchInstaller(destination)
            DesktopUpdateInstallResult(installerPath = destination)
        }
    }

    private fun resolveInstallerFileName(downloadUrl: String): String {
        val fallback = "AniTail-Desktop-Update.bin"
        val fromPath = runCatching {
            val path = URI(downloadUrl).path.orEmpty().trim()
            path.substringAfterLast('/').trim()
        }.getOrDefault("")

        val candidate = fromPath.ifBlank { fallback }
        return candidate
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .ifBlank { fallback }
    }

    private fun downloadFile(downloadUrl: String, destination: Path) {
        val connection = (URI(downloadUrl).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "AniTail-Desktop-Updater")
        }

        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                error("Failed downloading update. HTTP $code")
            }

            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(
                    Files.newOutputStream(
                        destination,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE,
                    ),
                ).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun launchInstaller(installerPath: Path) {
        if (!Files.exists(installerPath)) error("Installer file not found: $installerPath")
        val file = installerPath.toFile()
        if (!Desktop.isDesktopSupported()) error("Desktop integration not supported")
        val desktop = Desktop.getDesktop()
        when {
            desktop.isSupported(Desktop.Action.OPEN) -> desktop.open(file)
            desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(file.toURI())
            else -> error("No supported desktop action to launch installer")
        }
    }

    private fun pruneOldInstallers(directory: Path, keepCount: Int) {
        val files = Files.list(directory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .toList()
        }.sortedByDescending { path ->
            runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
        }

        if (files.size <= keepCount) return
        files.drop(keepCount).forEach { oldFile ->
            runCatching { Files.deleteIfExists(oldFile) }
        }
    }
}
