package com.anitail.music.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow

class CastManager(private val context: Context) {
    val isCasting = MutableStateFlow(false)

    // Memoizar CastContext para evitar múltiples llamadas
    private val castContext by lazy { CastContext.getSharedInstance(context) }

    private val sessionListener = object : SessionManagerListener<Session> {
        override fun onSessionStarted(session: Session, sessionId: String) {
            isCasting.value = true
        }

        override fun onSessionEnded(session: Session, error: Int) {
            isCasting.value = false
        }

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            isCasting.value = true
        }

        override fun onSessionSuspended(session: Session, reason: Int) {}
        override fun onSessionStarting(session: Session) {}
        override fun onSessionEnding(session: Session) {}
        override fun onSessionResuming(session: Session, sessionId: String) {}
        override fun onSessionResumeFailed(session: Session, error: Int) {}
        override fun onSessionStartFailed(session: Session, error: Int) {}
    }

    fun start() {
        castContext.sessionManager.addSessionManagerListener(sessionListener)
        isCasting.value = castContext.sessionManager.currentCastSession != null
    }

    fun stop() {
        castContext.sessionManager.removeSessionManagerListener(sessionListener)
    }
}
