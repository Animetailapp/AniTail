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
    private var available = true
    
    /**
     * Inicializa JavaFX si aún no ha sido inicializado.
     * Es seguro llamar múltiples veces - solo la primera llamada inicializa.
     * Retorna true si JavaFX está disponible, false si no se pudo inicializar.
     */
    fun ensureInitialized(): Boolean {
        if (!available) return false
        if (initialized.compareAndSet(false, true)) {
            try {
                Platform.startup {}
            } catch (e: IllegalStateException) {
                // Ya inicializado por otra parte del código
            } catch (e: RuntimeException) {
                // JavaFX toolkit no encontrado (libs nativas no disponibles)
                println("JavaFxManager: JavaFX no disponible - ${e.message}")
                available = false
                return false
            }
            Platform.setImplicitExit(false)
        }
        return true
    }

    val isAvailable: Boolean get() = available
    
    /**
     * Ejecuta código en el hilo de JavaFX.
     */
    fun runLater(action: () -> Unit) {
        if (!ensureInitialized()) return
        Platform.runLater(action)
    }
}
