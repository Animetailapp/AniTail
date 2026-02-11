package com.anitail.desktop.security

import com.sun.jna.platform.win32.Crypt32Util
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import java.util.Comparator
import java.util.EnumSet
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface SecureSecretsStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clearAll()
}

object DesktopSecureStore {
    val instance: SecureSecretsStore by lazy {
        FileBackedSecureStore(
            cipher = selectCipher(),
            storeFile = DesktopPaths.appDataDir().resolve("secure_secrets.json"),
        )
    }

    private fun selectCipher(): SecretCipher {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        return if (osName.contains("win")) {
            DpapiCipher
        } else {
            AesFileCipher(DesktopPaths.appDataDir().resolve("secure_master.key"))
        }
    }
}

object DesktopAppDataCleaner {
    private const val DOWNLOADS_METADATA_FILE = "downloads.json"

    suspend fun clearAppDataPreservingDownloads() = withContext(Dispatchers.IO) {
        val appDataDir = DesktopPaths.appDataDir()
        if (!Files.exists(appDataDir)) return@withContext

        Files.newDirectoryStream(appDataDir).use { entries ->
            for (entry in entries) {
                if (entry.fileName.toString() == DOWNLOADS_METADATA_FILE) {
                    continue
                }
                deleteRecursively(entry)
            }
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { walk ->
            walk.sorted(Comparator.reverseOrder()).forEach { target ->
                Files.deleteIfExists(target)
            }
        }
    }
}

private class FileBackedSecureStore(
    private val cipher: SecretCipher,
    private val storeFile: Path,
) : SecureSecretsStore {
    private val lock = Any()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    override fun get(key: String): String? = synchronized(lock) {
        val entries = readEntriesLocked()
        val encoded = entries[key] ?: return@synchronized null
        val encrypted = runCatching { decoder.decode(encoded) }.getOrNull() ?: return@synchronized null
        val decrypted = runCatching { cipher.decrypt(encrypted) }.getOrNull() ?: run {
            entries.remove(key)
            writeEntriesLocked(entries)
            return@synchronized null
        }
        decrypted.toString(StandardCharsets.UTF_8)
    }

    override fun put(key: String, value: String) = synchronized(lock) {
        val entries = readEntriesLocked()
        if (value.isEmpty()) {
            entries.remove(key)
            writeEntriesLocked(entries)
            return@synchronized
        }
        val encrypted = cipher.encrypt(value.toByteArray(StandardCharsets.UTF_8))
        entries[key] = encoder.encodeToString(encrypted)
        writeEntriesLocked(entries)
    }

    override fun remove(key: String) = synchronized(lock) {
        val entries = readEntriesLocked()
        entries.remove(key)
        writeEntriesLocked(entries)
    }

    override fun clearAll() = synchronized(lock) {
        if (Files.exists(storeFile)) {
            Files.delete(storeFile)
        }
    }

    private fun readEntriesLocked(): MutableMap<String, String> {
        if (!Files.exists(storeFile)) return mutableMapOf()
        val raw = runCatching { Files.readString(storeFile, StandardCharsets.UTF_8) }.getOrDefault("")
        if (raw.isBlank()) return mutableMapOf()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return mutableMapOf()
        val entries = mutableMapOf<String, String>()
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            entries[key] = json.optString(key, "")
        }
        return entries
    }

    private fun writeEntriesLocked(entries: Map<String, String>) {
        ensureParentDirectory(storeFile)
        if (entries.isEmpty()) {
            if (Files.exists(storeFile)) {
                Files.delete(storeFile)
            }
            return
        }
        val json = JSONObject()
        entries.forEach { (key, value) -> json.put(key, value) }
        Files.writeString(storeFile, json.toString(2), StandardCharsets.UTF_8)
        setOwnerOnlyPermissions(storeFile)
    }
}

private interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(cipherText: ByteArray): ByteArray
}

private object DpapiCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = Crypt32Util.cryptProtectData(plain)

    override fun decrypt(cipherText: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(cipherText)
}

private class AesFileCipher(
    private val keyFile: Path,
) : SecretCipher {
    private val secureRandom = SecureRandom()
    private val key: ByteArray by lazy { loadOrCreateKey() }

    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)
        return iv + encrypted
    }

    override fun decrypt(cipherText: ByteArray): ByteArray {
        require(cipherText.size > 12) { "Invalid cipher payload" }
        val iv = cipherText.copyOfRange(0, 12)
        val payload = cipherText.copyOfRange(12, cipherText.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload)
    }

    private fun loadOrCreateKey(): ByteArray {
        ensureParentDirectory(keyFile)
        if (Files.exists(keyFile)) {
            val existing = Files.readAllBytes(keyFile)
            if (existing.size == 32) {
                return existing
            }
        }

        val newKey = ByteArray(32)
        secureRandom.nextBytes(newKey)
        Files.write(keyFile, newKey)
        setOwnerOnlyPermissions(keyFile)
        return newKey
    }
}

private fun ensureParentDirectory(path: Path) {
    val parent = path.parent ?: return
    if (!Files.exists(parent)) {
        Files.createDirectories(parent)
    }
}

private fun setOwnerOnlyPermissions(path: Path) {
    runCatching {
        val file = path.toFile()
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }
    runCatching {
        Files.setPosixFilePermissions(
            path,
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )
    }
}
