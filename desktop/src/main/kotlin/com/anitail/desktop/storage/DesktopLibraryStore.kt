package com.anitail.desktop.storage

import com.anitail.shared.model.LibraryItem
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DesktopLibraryStore(
    private val filePath: Path = defaultLibraryPath(),
) {
    fun load(): List<LibraryItem> {
        if (!Files.exists(filePath)) return emptyList()
        return runCatching {
            val raw = Files.readString(filePath, StandardCharsets.UTF_8)
            if (raw.isBlank()) return emptyList()
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    add(
                        LibraryItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            artist = obj.getString("artist"),
                            artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                            playbackUrl = obj.getString("playbackUrl"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(items: List<LibraryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("artworkUrl", item.artworkUrl ?: "")
                    .put("playbackUrl", item.playbackUrl)
            )
        }
        ensureParent()
        Files.writeString(filePath, array.toString(), StandardCharsets.UTF_8)
    }

    private fun ensureParent() {
        filePath.parent?.let { parent ->
            if (!Files.exists(parent)) {
                Files.createDirectories(parent)
            }
        }
    }

    companion object {
        private fun defaultLibraryPath(): Path {
            val home = System.getProperty("user.home") ?: "."
            return Paths.get(home, ".anitail", "library.json")
        }
    }
}
