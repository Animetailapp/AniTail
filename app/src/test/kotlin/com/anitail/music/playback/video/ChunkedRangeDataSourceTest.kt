package com.anitail.music.playback.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChunkedRangeDataSourceTest {
    @Test
    fun `parseContentRangeTotalLength extracts total from standard and case-insensitive headers`() {
        assertEquals(
            10_485_760L,
            parseContentRangeTotalLength(mapOf("Content-Range" to listOf("bytes 0-524287/10485760"))),
        )
        assertEquals(
            500L,
            parseContentRangeTotalLength(mapOf("content-range" to listOf("bytes 100-199/500"))),
        )
    }

    @Test
    fun `parseContentRangeTotalLength returns null for wildcard or missing header`() {
        assertNull(parseContentRangeTotalLength(mapOf("Content-Range" to listOf("bytes 0-524287/*"))))
        assertNull(parseContentRangeTotalLength(emptyMap()))
    }
}

