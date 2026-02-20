package com.anitail.desktop.download

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDownloadServiceTest {
    @Test
    fun resolvesMusicDownloadDirectory() {
        val home = File("C:\\Users\\TestUser")
        val expected = File(home, "Music${File.separator}Anitail")

        val resolved = DesktopDownloadService.resolveMusicDownloadDir(home.path)

        assertEquals(expected.path, resolved.path)
    }
}
