package com.anitail.music.cast

import android.content.Context
import com.anitail.music.utils.GooglePlayServicesUtils
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow

class CastManager(
    private val context: Context,
    private val onCastSessionStarted: (() -> Unit)? = null
) {
    val isCasting = MutableStateFlow(false)

    // Memoizar CastContext para evitar múltiples llamadas
    private val castContext by lazy {
        if (GooglePlayServicesUtils.isCastAvailable(context)) {
            CastContext.getSharedInstance(context)
        } else {
            null
        }
    }

    private val sessionListener = object : SessionManagerListener<Session> {
        override fun onSessionStarted(session: Session, sessionId: String) {
            isCasting.value = true
            // Sincronizar automáticamente la cola cuando se inicia una sesión de Cast
            onCastSessionStarted?.invoke()
        }

        override fun onSessionEnded(session: Session, error: Int) {
            isCasting.value = false
        }

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            isCasting.value = true
            // También sincronizar cuando se reanuda una sesión
            onCastSessionStarted?.invoke()
        }

        override fun onSessionSuspended(session: Session, reason: Int) {}
        override fun onSessionStarting(session: Session) {}
        override fun onSessionEnding(session: Session) {}
        override fun onSessionResuming(session: Session, sessionId: String) {}
        override fun onSessionResumeFailed(session: Session, error: Int) {}
        override fun onSessionStartFailed(session: Session, error: Int) {}
    }

    fun start() {
        val context = castContext ?: return
        context.sessionManager.addSessionManagerListener(sessionListener)
        isCasting.value = context.sessionManager.currentCastSession != null
    }

    fun stop() {
        val context = castContext ?: return
        context.sessionManager.removeSessionManagerListener(sessionListener)
    }
}
