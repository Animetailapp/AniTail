package com.anitail.shared.settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

@OptIn(ExperimentalSettingsImplementation::class)
internal class IosSecureSettings(
    private val delegate: Settings,
    private val secure: Settings = KeychainSettings("com.anitail.ios.settings"),
) : Settings by delegate {
    init {
        migratePlaintextSecrets()
    }

    override val keys: Set<String>
        get() = delegate.keys + runCatching { secure.keys }.getOrDefault(emptySet())

    override val size: Int
        get() = keys.size

    override fun clear() {
        try {
            secure.clear()
        } catch (_: Throwable) {
            // Unsigned simulator builds lack Keychain entitlements; fall back to delegate settings.
        }
        delegate.clear()
    }

    override fun remove(key: String) {
        if (key in secureSettingsKeys) {
            try {
                secure.remove(key)
            } catch (_: Throwable) {
                // Fall back to delegate when Keychain entitlements are unavailable.
            }
        }
        delegate.remove(key)
    }

    override fun hasKey(key: String): Boolean =
        if (key in secureSettingsKeys) {
            try {
                secure.hasKey(key)
            } catch (_: Throwable) {
                delegate.hasKey(key)
            }
        } else {
            delegate.hasKey(key)
        }

    override fun putString(
        key: String,
        value: String,
    ) {
        if (key !in secureSettingsKeys) {
            delegate.putString(key, value)
            return
        }
        val storedInKeychain =
            try {
                secure.putString(key, value)
                true
            } catch (_: Throwable) {
                false
            }
        if (storedInKeychain) {
            delegate.remove(key)
        } else {
            delegate.putString(key, value)
        }
    }

    override fun getString(
        key: String,
        defaultValue: String,
    ): String = getStringOrNull(key) ?: defaultValue

    override fun getStringOrNull(key: String): String? =
        if (key in secureSettingsKeys) {
            try {
                secure.getStringOrNull(key)
            } catch (_: Throwable) {
                delegate.getStringOrNull(key)
            }
        } else {
            delegate.getStringOrNull(key)
        }

    private fun migratePlaintextSecrets() {
        secureSettingsKeys.forEach { key ->
            val plaintext = delegate.getStringOrNull(key) ?: return@forEach
            val migrated =
                try {
                    if (!secure.hasKey(key)) secure.putString(key, plaintext)
                    true
                } catch (_: Throwable) {
                    false
                }
            if (migrated) delegate.remove(key)
        }
    }
}
