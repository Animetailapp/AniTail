package com.anitail.music.utils

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anitail.music.constants.ChipSortTypeKey
import com.anitail.music.constants.LibraryFilter
import com.anitail.music.constants.LibrarySortingMapKey
import com.anitail.music.extensions.toEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DataStorePreferenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testScope: CoroutineScope
    private lateinit var prefsFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cache: PreferenceCache

    private val testFileSystem = object : ForwardingFileSystem(FileSystem.SYSTEM) {
        // On Windows JVM tests, Okio's JvmSystemFileSystem uses File.renameTo, which fails if target exists.
        override fun atomicMove(
            source: Path,
            target: Path,
        ) {
            Files.move(
                source.toFile().toPath(),
                target.toFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    @Before
    fun setUp() {
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File(tempFolder.root, "settings_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = testFileSystem,
                serializer = PreferencesSerializer,
                producePath = { prefsFile.toOkioPath() },
            ),
            corruptionHandler = null,
            migrations = emptyList(),
            scope = testScope,
        )
        cache = PreferenceCache(dataStore = dataStore, scope = testScope)
    }

    @After
    fun tearDown() {
        runBlocking {
            testScope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    private fun createRawMutableState(
        key: Preferences.Key<String>,
        defaultValue: String,
    ): MutableState<String> {
        val derived = derivedStateOf { cache.preferencesState.value[key] ?: defaultValue }
        return object : MutableState<String> {
            override var value: String
                get() = derived.value
                set(value) {
                    cache.set(key, value)
                }

            override fun component1(): String = value

            override fun component2(): (String) -> Unit = { value = it }
        }
    }

    private fun <T> awaitDiskValue(
        key: Preferences.Key<T>,
        expected: T,
    ) {
        val deadline = System.currentTimeMillis() + 2_000L
        var lastActual: T? = null
        while (System.currentTimeMillis() < deadline) {
            if (prefsFile.exists() && prefsFile.length() > 0L) {
                val diskPrefs = runBlocking {
                    runCatching {
                        testFileSystem.read(prefsFile.toOkioPath()) {
                            PreferencesSerializer.readFrom(this)
                        }
                    }.getOrNull()
                }
                if (diskPrefs != null) {
                    lastActual = diskPrefs[key]
                    if (lastActual == expected) return
                }
            }
            Thread.sleep(10)
        }
        assertEquals(expected, lastActual)
    }

    @Test
    fun `enumPreferenceMutableState updates immediately on same MutableState instance and persists`() {
        val rawState = createRawMutableState(
            key = ChipSortTypeKey,
            defaultValue = LibraryFilter.LIBRARY.name,
        )
        val enumState = enumPreferenceMutableState(
            rawState = rawState,
            parse = { raw -> raw.toEnum(LibraryFilter.LIBRARY) },
        )

        var delegatedFilter by enumState
        assertEquals(LibraryFilter.LIBRARY, delegatedFilter)
        assertEquals(LibraryFilter.LIBRARY, enumState.component1())

        delegatedFilter = LibraryFilter.SONGS
        assertEquals(LibraryFilter.SONGS, delegatedFilter)
        assertEquals(LibraryFilter.SONGS, enumState.component1())

        val setFilter = enumState.component2()
        setFilter(LibraryFilter.ALBUMS)
        assertEquals(LibraryFilter.ALBUMS, delegatedFilter)
        assertEquals(LibraryFilter.ALBUMS, enumState.value)

        delegatedFilter = LibraryFilter.LIBRARY
        assertEquals(LibraryFilter.LIBRARY, enumState.value)
        assertEquals(LibraryFilter.LIBRARY, enumState.component1())

        runBlocking {
            val persisted = dataStore.data.first { prefs ->
                prefs[ChipSortTypeKey] == LibraryFilter.LIBRARY.name
            }
            assertEquals(LibraryFilter.LIBRARY.name, persisted[ChipSortTypeKey])
        }
        awaitDiskValue(ChipSortTypeKey, LibraryFilter.LIBRARY.name)
        assertEquals(LibraryFilter.LIBRARY, enumState.value)
    }

    @Test
    fun `PreferenceCache set updates target key immediately without reverting or touching other keys`() {
        val otherKey = stringPreferencesKey("otherPreference")
        val targetDerived = derivedStateOf { cache.preferencesState.value[LibrarySortingMapKey] ?: "" }
        val otherDerived = derivedStateOf { cache.preferencesState.value[otherKey] ?: "default_other" }

        assertEquals("", targetDerived.value)
        assertEquals("default_other", otherDerived.value)

        cache.set(LibrarySortingMapKey, "TRACKS=NAME:ASC")
        assertEquals("TRACKS=NAME:ASC", targetDerived.value)
        assertEquals("default_other", otherDerived.value)
        awaitDiskValue(LibrarySortingMapKey, "TRACKS=NAME:ASC")

        cache.set(LibrarySortingMapKey, "TRACKS=YEAR:ASC")
        assertEquals("TRACKS=YEAR:ASC", targetDerived.value)

        cache.set(LibrarySortingMapKey, "TRACKS=NAME:DESC")
        assertEquals("TRACKS=NAME:DESC", targetDerived.value)

        runBlocking {
            val persisted = dataStore.data.first { prefs ->
                prefs[LibrarySortingMapKey] == "TRACKS=NAME:DESC"
            }
            assertEquals("TRACKS=NAME:DESC", persisted[LibrarySortingMapKey])
        }

        awaitDiskValue(LibrarySortingMapKey, "TRACKS=NAME:DESC")
        assertEquals("TRACKS=NAME:DESC", targetDerived.value)
        assertEquals("default_other", otherDerived.value)
    }

    @Test
    fun `external DataStore edit propagates to PreferenceCache state`() {
        val observed = derivedStateOf {
            cache.preferencesState.value[ChipSortTypeKey] ?: LibraryFilter.LIBRARY.name
        }
        assertEquals(LibraryFilter.LIBRARY.name, observed.value)

        runBlocking {
            dataStore.edit { prefs ->
                prefs[ChipSortTypeKey] = LibraryFilter.PLAYLISTS.name
            }
            val start = System.currentTimeMillis()
            while (observed.value != LibraryFilter.PLAYLISTS.name && System.currentTimeMillis() - start < 2_000L) {
                Thread.sleep(10)
            }
        }

        awaitDiskValue(ChipSortTypeKey, LibraryFilter.PLAYLISTS.name)
        assertEquals(LibraryFilter.PLAYLISTS.name, observed.value)
    }
}
