package com.anitail.desktop.util

import javafx.application.Platform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton para gestionar la inicialización de JavaFX.
 * Evita el error "Toolkit already initialized" cuando múltiples
 * componentes intentan inicializar JavaFX.
 */
object JavaFxManager {
    private val initialized = AtomicBoolean(false)
    
    /**
     * Inicializa JavaFX si aún no ha sido inicializado.
     * Es seguro llamar múltiples veces - solo la primera llamada inicializa.
     */
    fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            try {
                Platform.startup {}
            } catch (e: IllegalStateException) {
                // Ya inicializado por otra parte del código
            }
            Platform.setImplicitExit(false)
        }
    }
    
    /**
     * Ejecuta código en el hilo de JavaFX.
     */
    fun runLater(action: () -> Unit) {
        ensureInitialized()
        Platform.runLater(action)
    }
}
