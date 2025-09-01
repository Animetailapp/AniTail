package com.anitail.music.utils

import android.content.Context
import com.anitail.music.constants.EnableCastKey
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.runBlocking

/**
 * Utilidades para verificar la disponibilidad de Google Play Services
 */
object GooglePlayServicesUtils {

    /**
     * Verifica si Google Play Services está disponible en el dispositivo
     * @param context El contexto de la aplicación
     * @return true si Google Play Services está disponible, false en caso contrario
     */
    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }

    /**
     * Verifica si Google Play Services está disponible y si es posible recuperarlo
     * @param context El contexto de la aplicación
     * @return true si está disponible o se puede recuperar, false en caso contrario
     */
    fun isGooglePlayServicesAvailableOrRecoverable(context: Context): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return when (resultCode) {
            ConnectionResult.SUCCESS -> true
            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            ConnectionResult.SERVICE_DISABLED -> googleApiAvailability.isUserResolvableError(
                resultCode
            )

            else -> false
        }
    }

    /**
     * Obtiene el código de error de Google Play Services
     * @param context El contexto de la aplicación
     * @return El código de error de ConnectionResult
     */
    fun getGooglePlayServicesErrorCode(context: Context): Int {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        return googleApiAvailability.isGooglePlayServicesAvailable(context)
    }

    /**
     * Verifica si Cast está habilitado por el usuario y si Google Play Services está disponible
     * @param context El contexto de la aplicación
     * @return true si Cast está disponible y habilitado, false en caso contrario
     */
    fun isCastAvailable(context: Context): Boolean {
        return try {
            val isGooglePlayAvailable = isGooglePlayServicesAvailable(context)
            val isCastEnabled = runBlocking {
                context.dataStore.get(EnableCastKey, true)
            }
            isGooglePlayAvailable && isCastEnabled
        } catch (e: Exception) {
            false
        }
    }
}