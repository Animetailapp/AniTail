package com.anitail.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.anitail.music.extensions.toEnum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val dataStoreCaches = ConcurrentHashMap<DataStore<Preferences>, PreferenceCache>()

private data class InFlightPreferenceEdit(
    val version: Long,
    val value: Any?,
    val persisted: Boolean = false,
    val staleEmissionsAfterPersist: Int = 0,
)

@Suppress("UNCHECKED_CAST")
private fun setOrRemoveRawPreference(
    prefs: MutablePreferences,
    rawKey: Preferences.Key<*>,
    value: Any?,
) {
    val typedKey = rawKey as Preferences.Key<Any>
    if (value == null) {
        prefs.remove(typedKey)
    } else {
        prefs[typedKey] = value
    }
}

/**
 * Coordinates snapshot-observed [Preferences] reads with synchronous optimistic writes
 * backed by [DataStore].
 */
internal class PreferenceCache(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val flushLock = Any()
    private val writeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val nextVersion = AtomicLong(0L)
    private val inFlightEdits = LinkedHashMap<Preferences.Key<*>, InFlightPreferenceEdit>()

    private var latestDiskPreferences: Preferences
    private var latestEffectivePreferences: Preferences

    val preferencesState: MutableState<Preferences>

    init {
        val initialPreferences = runCatching {
            runBlocking { dataStore.data.first() }
        }.getOrElse {
            emptyPreferences()
        }
        latestDiskPreferences = initialPreferences
        latestEffectivePreferences = initialPreferences
        preferencesState = mutableStateOf(initialPreferences)

        scope.launch {
            dataStore.data.collect { diskPrefs ->
                synchronized(lock) {
                    latestDiskPreferences = diskPrefs
                    val iterator = inFlightEdits.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        val edit = entry.value
                        if (edit.persisted) {
                            if (diskPrefs[entry.key] == edit.value || edit.staleEmissionsAfterPersist >= 1) {
                                iterator.remove()
                            } else {
                                entry.setValue(
                                    edit.copy(staleEmissionsAfterPersist = edit.staleEmissionsAfterPersist + 1),
                                )
                            }
                        }
                    }
                    latestEffectivePreferences = mergeWithInFlightLocked(diskPrefs)
                }
                flushPreferencesState()
            }
        }
    }

    fun currentSnapshotPreferences(): Preferences =
        Snapshot.withoutReadObservation { preferencesState.value }

    fun <T> set(
        key: Preferences.Key<T>,
        value: T,
    ) {
        val version = nextVersion.incrementAndGet()
        synchronized(lock) {
            inFlightEdits[key] = InFlightPreferenceEdit(version = version, value = value)
            latestEffectivePreferences = mergeWithInFlightLocked(latestDiskPreferences)
        }
        flushPreferencesState()

        scope.launch(writeDispatcher) {
            if (synchronized(lock) { inFlightEdits[key]?.version != version }) {
                return@launch
            }
            try {
                dataStore.edit { prefs ->
                    setOrRemoveRawPreference(prefs, key, value)
                }
                synchronized(lock) {
                    val currentEdit = inFlightEdits[key]
                    if (currentEdit?.version == version) {
                        if (latestDiskPreferences[key] == value) {
                            inFlightEdits.remove(key)
                        } else {
                            inFlightEdits[key] = currentEdit.copy(persisted = true)
                        }
                        latestEffectivePreferences = mergeWithInFlightLocked(latestDiskPreferences)
                    }
                }
                flushPreferencesState()
            } catch (e: CancellationException) {
                clearInFlightOnFailure(key, version)
                throw e
            } catch (_: Throwable) {
                clearInFlightOnFailure(key, version)
            }
        }
    }

    private fun clearInFlightOnFailure(
        key: Preferences.Key<*>,
        version: Long,
    ) {
        synchronized(lock) {
            if (inFlightEdits[key]?.version == version) {
                inFlightEdits.remove(key)
                latestEffectivePreferences = mergeWithInFlightLocked(latestDiskPreferences)
            }
        }
        flushPreferencesState()
    }

    private fun mergeWithInFlightLocked(base: Preferences): Preferences {
        if (inFlightEdits.isEmpty()) return base
        val mutable = base.toMutablePreferences()
        for ((key, edit) in inFlightEdits) {
            setOrRemoveRawPreference(mutable, key, edit.value)
        }
        return mutable.toPreferences()
    }

    private fun flushPreferencesState() {
        synchronized(flushLock) {
            Snapshot.withMutableSnapshot {
                val target = synchronized(lock) { latestEffectivePreferences }
                if (preferencesState.value != target) {
                    preferencesState.value = target
                }
            }
        }
    }
}

private fun DataStore<Preferences>.preferenceCache(): PreferenceCache =
    dataStoreCaches.getOrPut(this) { PreferenceCache(this, dataStoreScope) }

fun DataStore<Preferences>.warmUp(): Preferences =
    preferenceCache().currentSnapshotPreferences()

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    preferenceCache().currentSnapshotPreferences()[key]

operator fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    preferenceCache().currentSnapshotPreferences()[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

fun booleanPreference(
    context: Context,
    key: Preferences.Key<Boolean>,
    defaultValue: Boolean,
) = ReadOnlyProperty<Any?, Boolean> { _, _ -> context.dataStore[key] ?: defaultValue }

fun stringPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: String,
) = ReadOnlyProperty<Any?, String> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val cache = remember(context) { context.dataStore.preferenceCache() }
    val currentDefault = rememberUpdatedState(defaultValue)
    val derivedValue = remember(cache, key) {
        derivedStateOf {
            cache.preferencesState.value[key] ?: currentDefault.value
        }
    }

    return remember(cache, key) {
        object : MutableState<T> {
            override var value: T
                get() = derivedValue.value
                set(value) {
                    cache.set(key, value)
                }

            override fun component1(): T = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@PublishedApi
internal fun <T : Enum<T>> enumPreferenceMutableState(
    rawState: MutableState<String>,
    parse: (String) -> T,
): MutableState<T> =
    object : MutableState<T> {
        override var value: T
            get() = parse(rawState.value)
            set(value) {
                rawState.value = value.name
            }

        override fun component1(): T = value

        override fun component2(): (T) -> Unit = { value = it }
    }

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val rawState = rememberPreference(key = key, defaultValue = defaultValue.name)

    return remember(key, rawState, defaultValue) {
        enumPreferenceMutableState(
            rawState = rawState,
            parse = { raw -> raw.toEnum(defaultValue = defaultValue) },
        )
    }
}
