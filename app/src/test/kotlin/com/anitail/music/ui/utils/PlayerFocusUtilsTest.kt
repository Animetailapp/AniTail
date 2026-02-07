package com.anitail.music.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerFocusUtilsTest {
    @Test
    fun resolvePlayerFocusAction_returnsRequestForTelevisionExpandedWhenNotRequested() {
        val action = resolvePlayerFocusAction(
            isTelevision = true,
            isExpanded = true,
            hasRequestedFocus = false,
        )

        assertEquals(PlayerFocusAction.Request, action)
    }

    @Test
    fun resolvePlayerFocusAction_returnsResetWhenNotTelevision() {
        val action = resolvePlayerFocusAction(
            isTelevision = false,
            isExpanded = true,
            hasRequestedFocus = false,
        )

        assertEquals(PlayerFocusAction.Reset, action)
    }

    @Test
    fun resolvePlayerFocusAction_returnsResetWhenCollapsed() {
        val action = resolvePlayerFocusAction(
            isTelevision = true,
            isExpanded = false,
            hasRequestedFocus = false,
        )

        assertEquals(PlayerFocusAction.Reset, action)
    }

    @Test
    fun resolvePlayerFocusAction_returnsNoneWhenAlreadyRequestedAndExpanded() {
        val action = resolvePlayerFocusAction(
            isTelevision = true,
            isExpanded = true,
            hasRequestedFocus = true,
        )

        assertEquals(PlayerFocusAction.None, action)
    }
}
