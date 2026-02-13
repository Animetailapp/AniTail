package com.anitail.desktop.ui.screen

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerControlSizingTest {
    @Test
    fun `caps sizes at phone width`() {
        val sizes = calculatePlayerControlSizes(420.dp)

        assertDpEquals(60.dp, sizes.playButtonHeight)
        assertDpEquals(66.dp, sizes.playButtonWidth)
        assertDpEquals(42.dp, sizes.sideButtonHeight)
        assertDpEquals(54.dp, sizes.sideButtonWidth)
        assertDpEquals(42.dp, sizes.playIconSize)
        assertDpEquals(32.dp, sizes.sideIconSize)
    }

    @Test
    fun `scales down under cap`() {
        val sizes = calculatePlayerControlSizes(300.dp)

        assertDpEquals(50.dp, sizes.playButtonHeight)
        assertDpEquals(55.dp, sizes.playButtonWidth)
        assertDpEquals(35.dp, sizes.sideButtonHeight)
        assertDpEquals(45.5f.dp, sizes.sideButtonWidth)
        assertDpEquals(35.dp, sizes.playIconSize)
        assertDpEquals(26.67f.dp, sizes.sideIconSize, delta = 0.05f)
    }
}

private fun assertDpEquals(expected: androidx.compose.ui.unit.Dp, actual: androidx.compose.ui.unit.Dp, delta: Float = 0.01f) {
    assertEquals(expected.value, actual.value, delta)
}
