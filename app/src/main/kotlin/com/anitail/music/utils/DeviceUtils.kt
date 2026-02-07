package com.anitail.music.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceUtils {
    internal fun isTelevisionUiMode(uiMode: Int): Boolean {
        return (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    internal fun isTelevisionDevice(
        uiMode: Int,
        hasTelevisionFeature: Boolean,
        hasLeanbackFeature: Boolean,
        hasFireTvFeature: Boolean,
    ): Boolean {
        return isTelevisionUiMode(uiMode) ||
                hasTelevisionFeature ||
                hasLeanbackFeature ||
                hasFireTvFeature
    }

    fun isTelevisionDevice(context: Context): Boolean {
        val pm = context.packageManager
        val hasTelevisionFeature = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        val hasLeanbackFeature = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasFireTvFeature = pm.hasSystemFeature("amazon.hardware.fire_tv")
        val uiMode = context.resources.configuration.uiMode
        return isTelevisionDevice(
            uiMode = uiMode,
            hasTelevisionFeature = hasTelevisionFeature,
            hasLeanbackFeature = hasLeanbackFeature,
            hasFireTvFeature = hasFireTvFeature,
        )
    }
}
