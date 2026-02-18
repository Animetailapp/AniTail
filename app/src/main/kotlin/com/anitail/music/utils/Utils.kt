package com.anitail.music.utils

fun reportException(throwable: Throwable) {
    if (throwable.hasCancellationCause()) return
    throwable.printStackTrace()
}

private fun Throwable.hasCancellationCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is java.util.concurrent.CancellationException) {
            return true
        }
        current = current.cause
    }
    return false
}
