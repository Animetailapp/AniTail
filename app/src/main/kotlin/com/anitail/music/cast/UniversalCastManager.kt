package com.anitail.music.cast

import android.content.Context
import com.anitail.music.utils.GooglePlayServicesUtils
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import timber.log.Timber

enum class CastingType {
    NONE, CAST, DLNA, AIRPLAY
}

data class CastingState(
    val isActive: Boolean,
    val type: CastingType,
    val deviceName: String? = null
)

class UniversalCastManager(
    private val context: Context,
    private val onCastSessionStarted: (() -> Unit)? = null,
    private val onDlnaSessionStarted: (() -> Unit)? = null,
    private val onAirPlaySessionStarted: (() -> Unit)? = null,
    private val onAirPlayAuthRequired: (() -> Unit)? = null
) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var stateJob: Job? = null
    private val _castingState = MutableStateFlow(CastingState(false, CastingType.NONE))
    val castingState: StateFlow<CastingState> = _castingState.asStateFlow()
    
    // Legacy compatibility
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()
    
    // Cast manager
    private val castManager = if (GooglePlayServicesUtils.isCastAvailable(context)) {
        CastManager(context) {
            onCastSessionStarted?.invoke()
            updateCastingState()
        }
    } else null
    
    // DLNA manager
    private val dlnaManager = DlnaManager(context) {
        onDlnaSessionStarted?.invoke()
        updateCastingState()
    }
    
    // AirPlay manager
    private val airPlayManager = AirPlayManager(
        context,
        onAirPlaySessionStarted = {
            onAirPlaySessionStarted?.invoke()
            updateCastingState()
        },
        onAirPlayAuthRequired = {
            Timber.e("AirPlay auth required callback invoked")
            onAirPlayAuthRequired?.invoke()
        }
    )

    private val castSessionListener = object : SessionManagerListener<Session> {
        override fun onSessionStarted(session: Session, sessionId: String) {
            updateCastingState()
        }

        override fun onSessionEnded(session: Session, error: Int) {
            updateCastingState()
        }

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            updateCastingState()
        }

        override fun onSessionSuspended(session: Session, reason: Int) {
            updateCastingState()
        }
        
        override fun onSessionStarting(session: Session) {}
        override fun onSessionEnding(session: Session) {}
        override fun onSessionResuming(session: Session, sessionId: String) {}
        override fun onSessionResumeFailed(session: Session, error: Int) {}
        override fun onSessionStartFailed(session: Session, error: Int) {}
    }
    
    fun start() {
        try {
            castManager?.start()
            dlnaManager.start()
            airPlayManager.start()
            
            // Add Cast session listener if Cast is available
            if (GooglePlayServicesUtils.isCastAvailable(context)) {
                val castContext = CastContext.getSharedInstance(context)
                castContext.sessionManager.addSessionManagerListener(castSessionListener)
            }
            
            updateCastingState()
            // Observar cambios de estado para actualizar la UI al instante (incluye desconexiones)
            val castFlow: Flow<Boolean> = castManager?.isCasting ?: flowOf(false)
            stateJob?.cancel()
            stateJob = scope.launch {
                try {
                    combine(
                        castFlow,
                        dlnaManager.isConnected,
                        dlnaManager.selectedDevice,
                        airPlayManager.isConnected,
                        airPlayManager.selectedDevice
                    ) { _, _, _, _, _ -> }
                        .collect {
                            updateCastingState()
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error observing casting states")
                }
            }
            Timber.d("Universal Cast Manager started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start Universal Cast Manager")
        }
    }
    
    fun stop() {
        try {
            stateJob?.cancel()
            castManager?.stop()
            dlnaManager.stop()
            airPlayManager.stop()
            
            // Remove Cast session listener if Cast is available
            if (GooglePlayServicesUtils.isCastAvailable(context)) {
                val castContext = CastContext.getSharedInstance(context)
                castContext.sessionManager.removeSessionManagerListener(castSessionListener)
            }
            
            _castingState.value = CastingState(false, CastingType.NONE)
            Timber.d("Universal Cast Manager stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Universal Cast Manager")
        } finally {
            // Cancelar scope para evitar fugas
            scope.coroutineContext.cancel()
        }
    }
    
    fun getDlnaManager(): DlnaManager = dlnaManager
    
    fun getAirPlayManager(): AirPlayManager = airPlayManager
    
    fun isCastAvailable(): Boolean = GooglePlayServicesUtils.isCastAvailable(context)
    
    fun playMedia(mediaUrl: String, title: String, artist: String, albumArt: String? = null, mimeType: String = "audio/mpeg") {
        when (_castingState.value.type) {
            CastingType.CAST -> {
                // Cast playback would be handled by the existing Cast player in MusicService
                Timber.d("Media playback requested for Cast device - handled by MusicService")
            }
            CastingType.DLNA -> {
                dlnaManager.playMedia(mediaUrl, title, artist, albumArt, mimeType)
            }
            CastingType.AIRPLAY -> {
                airPlayManager.playMedia(mediaUrl, title, artist, albumArt, mimeType)
            }
            CastingType.NONE -> {
                Timber.w("No casting device connected")
            }
        }
    }
    
    fun pauseMedia() {
        when (_castingState.value.type) {
            CastingType.CAST -> {
                // Cast pause would be handled by the existing Cast player in MusicService
                Timber.d("Media pause requested for Cast device - handled by MusicService")
            }
            CastingType.DLNA -> {
                dlnaManager.pauseMedia()
            }
            CastingType.AIRPLAY -> {
                airPlayManager.pauseMedia()
            }
            CastingType.NONE -> {
                Timber.w("No casting device connected")
            }
        }
    }
    
    fun stopMedia() {
        when (_castingState.value.type) {
            CastingType.CAST -> {
                // Cast stop would be handled by the existing Cast player in MusicService
                Timber.d("Media stop requested for Cast device - handled by MusicService")
            }
            CastingType.DLNA -> {
                dlnaManager.stopMedia()
            }
            CastingType.AIRPLAY -> {
                airPlayManager.stopMedia()
            }
            CastingType.NONE -> {
                Timber.w("No casting device connected")
            }
        }
    }
    
    private fun updateCastingState() {
        try {
            val isCastActive = castManager?.isCasting?.value == true
            val isDlnaActive = dlnaManager.isConnected.value
            val selectedDlnaDevice = dlnaManager.selectedDevice.value
            val isAirPlayActive = airPlayManager.isConnected.value
            val selectedAirPlayDevice = airPlayManager.selectedDevice.value
            
            val newState = when {
                isCastActive -> {
                    val castContext = if (GooglePlayServicesUtils.isCastAvailable(context)) {
                        CastContext.getSharedInstance(context)
                    } else null
                    val castSession = castContext?.sessionManager?.currentCastSession
                    CastingState(
                        isActive = true,
                        type = CastingType.CAST,
                        deviceName = castSession?.castDevice?.friendlyName
                    )
                }
                isDlnaActive && selectedDlnaDevice != null -> {
                    CastingState(
                        isActive = true,
                        type = CastingType.DLNA,
                        deviceName = selectedDlnaDevice.name
                    )
                }
                isAirPlayActive && selectedAirPlayDevice != null -> {
                    CastingState(
                        isActive = true,
                        type = CastingType.AIRPLAY,
                        deviceName = selectedAirPlayDevice.name
                    )
                }
                else -> {
                    CastingState(
                        isActive = false,
                        type = CastingType.NONE,
                        deviceName = null
                    )
                }
            }
            
            _castingState.value = newState
            _isCasting.value = newState.isActive
            
            Timber.d("Casting state updated: ${newState.type} - ${newState.deviceName}")
        } catch (e: Exception) {
            Timber.e(e, "Error updating casting state")
        }
    }
}