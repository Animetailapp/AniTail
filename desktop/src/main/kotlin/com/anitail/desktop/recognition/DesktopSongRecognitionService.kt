package com.anitail.desktop.recognition

import com.anitail.shazamkit.Shazam
import com.anitail.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Servicio de reconocimiento de canciones para Desktop usando ShazamKit.
 */
object DesktopSongRecognitionService {

    private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()

    fun reset() {
        _recognitionStatus.value = RecognitionStatus.Ready
    }

    suspend fun recognize(durationMs: Long = 7000L): RecognitionStatus = withContext(Dispatchers.IO) {
        if (!DesktopAudioRecorder.isMicrophoneAvailable()) {
            val error = RecognitionStatus.Error("No se detectó un micrófono compatible disponible.")
            _recognitionStatus.value = error
            return@withContext error
        }

        _recognitionStatus.value = RecognitionStatus.Listening

        try {
            val pcmData = DesktopAudioRecorder.recordAudio(durationMs)
            if (pcmData.isEmpty()) {
                val error = RecognitionStatus.Error("No se capturó audio suficiente.")
                _recognitionStatus.value = error
                return@withContext error
            }

            _recognitionStatus.value = RecognitionStatus.Processing

            val signature = ShazamSignatureGenerator.fromI16(pcmData)
            val result = Shazam.recognize(signature, durationMs)

            val status: RecognitionStatus = result.fold(
                onSuccess = { RecognitionStatus.Success(it) },
                onFailure = { error ->
                    if (error.message?.contains("no match", ignoreCase = true) == true) {
                        RecognitionStatus.NoMatch()
                    } else {
                        RecognitionStatus.Error(error.message ?: "Error al reconocer música")
                    }
                }
            )

            _recognitionStatus.value = status
            status
        } catch (e: Exception) {
            val error = RecognitionStatus.Error(e.message ?: "Error al reconocer música")
            _recognitionStatus.value = error
            error
        }
    }
}
