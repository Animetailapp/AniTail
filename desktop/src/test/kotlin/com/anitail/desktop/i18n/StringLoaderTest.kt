package com.anitail.desktop.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class StringLoaderTest {
    @Test
    fun loadsSpanishStringsFromAndroidResources() {
        val resolver = AndroidStringLoader.load("es")
        assertEquals("Inicio", resolver.get("home"))
        assertEquals("Explorar", resolver.get("explore"))
    }
}

