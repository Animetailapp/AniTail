package com.anitail.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.anitail.music.extensions.toEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val dataStoreScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val dataStoreStateFlows =
    ConcurrentHashMap<DataStore<Preferences>, StateFlow<Preferences>>()
private val optimisticPreferenceOverrides =
    ConcurrentHashMap<Preferences.Key<*>, MutableState<Any?>>()

private object UnsetPreferenceSentinel

@Suppress("UNCHECKED_CAST")
private fun <T> optimisticPreferenceState(key: Preferences.Key<T>): MutableState<Any?> =
    optimisticPreferenceOverrides.getOrPut(key) { mutableStateOf<Any?>(UnsetPreferenceSentinel) }

private fun DataStore<Preferences>.cachedPreferences(): StateFlow<Preferences> =
    dataStoreStateFlows.getOrPut(this) {
        val initialPreferences = runCatching {
            runBlocking {
                data.first()
            }
        }.getOrElse {
            emptyPreferences()
        }

        data.stateIn(
            dataStoreScope,
            SharingStarted.Eagerly,
            initialPreferences,
        )
    }

fun DataStore<Preferences>.warmUp(): Preferences = cachedPreferences().value

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    cachedPreferences().value[key]

operator fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    cachedPreferences().value[key] ?: defaultValue

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
    val coroutineScope = rememberCoroutineScope()
    val optimisticState = remember(key) { optimisticPreferenceState(key) }

    val state =
        remember(key) {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsState(context.dataStore[key] ?: defaultValue)

    return remember(key) {
        object : MutableState<T> {
            @Suppress("UNCHECKED_CAST")
            override var value: T
                get() {
                    val overrideVal = optimisticState.value
                    return if (overrideVal !== UnsetPreferenceSentinel) overrideVal as T else state.value
                }
                set(value) {
                    optimisticState.value = value
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value
                        }
                        if (optimisticState.value == value) {
                            optimisticState.value = UnsetPreferenceSentinel
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val (rawValue, setRawValue) = rememberPreference(key = key, defaultValue = defaultValue.name)
    val enumValue = remember(rawValue) { rawValue.toEnum(defaultValue = defaultValue) }

    return remember(key) {
        object : MutableState<T> {
            override var value: T
                get() = enumValue
                set(value) {
                    setRawValue(value.name)
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
