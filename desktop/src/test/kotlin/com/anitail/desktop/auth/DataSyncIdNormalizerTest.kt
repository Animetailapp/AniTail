package com.anitail.desktop.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataSyncIdNormalizerTest {
    @Test
    fun returnsNullForBlank() {
        assertNull(normalizeDataSyncId(null))
        assertNull(normalizeDataSyncId(""))
        assertNull(normalizeDataSyncId("   "))
    }

    @Test
    fun returnsSameWhenNoSeparator() {
        assertEquals("ABC123", normalizeDataSyncId("ABC123"))
    }

    @Test
    fun keepsPrefixWhenSuffixSeparator() {
        assertEquals("AAA", normalizeDataSyncId("AAA||"))
    }

    @Test
    fun keepsSecondIdWhenContainsSeparator() {
        assertEquals("BBB", normalizeDataSyncId("AAA||BBB"))
    }
}
