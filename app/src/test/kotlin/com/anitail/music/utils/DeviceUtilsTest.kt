package com.anitail.music.utils

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUtilsTest {
    @Test
    fun isTelevisionUiMode_returnsTrueForTelevision() {
        val uiMode = Configuration.UI_MODE_TYPE_TELEVISION
        assertTrue(DeviceUtils.isTelevisionUiMode(uiMode))
    }

    @Test
    fun isTelevisionDevice_returnsTrueForUiModeTelevision() {
        assertTrue(
            DeviceUtils.isTelevisionDevice(
                uiMode = Configuration.UI_MODE_TYPE_TELEVISION,
                hasTelevisionFeature = false,
                hasLeanbackFeature = false,
                hasFireTvFeature = false,
            )
        )
    }

    @Test
    fun isTelevisionDevice_returnsTrueForTelevisionFeature() {
        assertTrue(
            DeviceUtils.isTelevisionDevice(
                uiMode = Configuration.UI_MODE_TYPE_NORMAL,
                hasTelevisionFeature = true,
                hasLeanbackFeature = false,
                hasFireTvFeature = false,
            )
        )
    }

    @Test
    fun isTelevisionDevice_returnsTrueForLeanbackFeature() {
        assertTrue(
            DeviceUtils.isTelevisionDevice(
                uiMode = Configuration.UI_MODE_TYPE_NORMAL,
                hasTelevisionFeature = false,
                hasLeanbackFeature = true,
                hasFireTvFeature = false,
            )
        )
    }

    @Test
    fun isTelevisionDevice_returnsTrueForFireTvFeature() {
        assertTrue(
            DeviceUtils.isTelevisionDevice(
                uiMode = Configuration.UI_MODE_TYPE_NORMAL,
                hasTelevisionFeature = false,
                hasLeanbackFeature = false,
                hasFireTvFeature = true,
            )
        )
    }

    @Test
    fun isTelevisionDevice_returnsFalseWhenNoSignals() {
        assertFalse(
            DeviceUtils.isTelevisionDevice(
                uiMode = Configuration.UI_MODE_TYPE_NORMAL,
                hasTelevisionFeature = false,
                hasLeanbackFeature = false,
                hasFireTvFeature = false,
            )
        )
    }
}
