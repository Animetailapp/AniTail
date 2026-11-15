package com.anitail.shared.backup

import com.anitail.shared.constants.ArtistNameAliasesJsonKey
import com.anitail.shared.settings.ArtistNameAliases
import com.anitail.shared.settings.PortableSetting
import com.anitail.shared.settings.PortableSettings
import com.anitail.shared.settings.SettingType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun PortableSettings?.withLegacyArtistNameAliases(serialized: String): PortableSettings {
    val aliases = ArtistNameAliases.deserialize(serialized)
    val settings = this ?: PortableSettings(entries = emptyList())
    return settings.copy(
        entries =
            settings.entries.filterNot { it.key == ArtistNameAliasesJsonKey } +
                PortableSetting(ArtistNameAliasesJsonKey, SettingType.STRING, Json.encodeToString(aliases)),
    )
}
