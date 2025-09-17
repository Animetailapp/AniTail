package com.anitail.music.cast

import android.content.Context
import com.anitail.music.utils.GooglePlayServicesUtils
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import timber.log.Timber

enum class CastingType {
    NONE, CAST, DLNA
}

data class CastingState(
    val isActive: Boolean,
    val type: CastingType,
    val deviceName: String? = null
)

class UniversalCastManager(
    private val context: Context,
    private val onCastSessionStarted: (() -> Unit)? = null,
    private val onDlnaSessionStarted: (() -> Unit)? = null
) {
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
            
            // Add Cast session listener if Cast is available
            if (GooglePlayServicesUtils.isCastAvailable(context)) {
                val castContext = CastContext.getSharedInstance(context)
                castContext.sessionManager.addSessionManagerListener(castSessionListener)
            }
            
            updateCastingState()
            Timber.d("Universal Cast Manager started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start Universal Cast Manager")
        }
    }
    
    fun stop() {
        try {
            castManager?.stop()
            dlnaManager.stop()
            
            // Remove Cast session listener if Cast is available
            if (GooglePlayServicesUtils.isCastAvailable(context)) {
                val castContext = CastContext.getSharedInstance(context)
                castContext.sessionManager.removeSessionManagerListener(castSessionListener)
            }
            
            _castingState.value = CastingState(false, CastingType.NONE)
            Timber.d("Universal Cast Manager stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Universal Cast Manager")
        }
    }
    
    fun getDlnaManager(): DlnaManager = dlnaManager
    
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