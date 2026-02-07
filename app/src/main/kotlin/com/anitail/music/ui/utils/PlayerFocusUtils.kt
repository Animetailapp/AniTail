package com.anitail.music.ui.utils

enum class PlayerFocusAction {
    Request,
    Reset,
    None,
}

fun resolvePlayerFocusAction(
    isTelevision: Boolean,
    isExpanded: Boolean,
    hasRequestedFocus: Boolean,
): PlayerFocusAction {
    if (!isTelevision || !isExpanded) {
        return PlayerFocusAction.Reset
    }
    return if (hasRequestedFocus) {
        PlayerFocusAction.None
    } else {
        PlayerFocusAction.Request
    }
}
