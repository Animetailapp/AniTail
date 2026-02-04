package com.anitail.music.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.anitail.innertube.utils.parseCookieString
import com.anitail.music.constants.InnerTubeCookieKey
import com.anitail.music.constants.YtmSyncKey
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get

fun Context.isSyncEnabled(): Boolean =
    dataStore.get(YtmSyncKey, true) && isUserLoggedIn()

fun Context.isUserLoggedIn(): Boolean {
    val cookie = dataStore[InnerTubeCookieKey] ?: ""
    return "SAPISID" in parseCookieString(cookie) && isInternetConnected()
}

fun Context.isInternetConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}
