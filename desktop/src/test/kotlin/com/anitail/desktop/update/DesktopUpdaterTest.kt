package com.anitail.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUpdaterTest {
    @Test
    fun `isVersionNewer returns true when latest is newer`() {
        assertTrue(DesktopUpdater.isVersionNewer("v1.2.3", "1.2.2"))
        assertTrue(DesktopUpdater.isVersionNewer("1.10.0", "1.9.9"))
        assertTrue(DesktopUpdater.isVersionNewer("2.0", "1.99.99"))
    }

    @Test
    fun `isVersionNewer returns false when equal or older`() {
        assertFalse(DesktopUpdater.isVersionNewer("1.2.3", "1.2.3"))
        assertFalse(DesktopUpdater.isVersionNewer("1.2.2", "1.2.3"))
        assertFalse(DesktopUpdater.isVersionNewer("v1.0.0", "1.0"))
    }

    @Test
    fun `selectDownloadUrlForPlatform picks windows arm64 first`() {
        val assets = listOf(
            "AniTail-Desktop-v1.0.0-windows-x64.msi" to "x64",
            "AniTail-Desktop-v1.0.0-windows-arm64.msi" to "arm64",
        )

        val selected = DesktopUpdater.selectDownloadUrlForPlatform(
            assets = assets,
            osName = "windows 11",
            archName = "aarch64",
        )

        assertEquals("arm64", selected)
    }

    @Test
    fun `selectDownloadUrlForPlatform falls back to os token match`() {
        val assets = listOf(
            "AniTail-Desktop-v1.0.0-linux-x64.deb" to "linux",
            "AniTail-Desktop-v1.0.0-macos-x64.dmg" to "mac",
        )

        val selected = DesktopUpdater.selectDownloadUrlForPlatform(
            assets = assets,
            osName = "Linux",
            archName = "x86_64",
        )

        assertEquals("linux", selected)
    }
}
