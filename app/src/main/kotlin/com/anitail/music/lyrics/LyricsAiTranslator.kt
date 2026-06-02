package com.anitail.music.lyrics

import com.anitail.music.api.DeepLService
import com.anitail.music.api.MistralService
import com.anitail.music.api.OpenRouterService

object LyricsAiTranslator {
    suspend fun translate(
        lines: List<String>,
        targetLanguage: String,
        provider: String,
        mode: String,
        openRouterApiKey: String,
        openRouterBaseUrl: String,
        openRouterModel: String,
        deeplApiKey: String,
        deeplFormality: String,
        mistralApiKey: String,
        mistralModel: String,
        systemPrompt: String = "",
    ): Result<List<String>> {
        val text = lines.joinToString("\n")
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("Lyrics text is empty"))
        }

        return when (provider) {
            "DeepL" ->
                DeepLService.translate(
                    text = text,
                    targetLanguage = targetLanguage,
                    apiKey = deeplApiKey,
                    formality = deeplFormality,
                )

            "Mistral" ->
                MistralService.translate(
                    text = text,
                    targetLanguage = targetLanguage,
                    apiKey = mistralApiKey,
                    model = mistralModel,
                    mode = mode,
                    customSystemPrompt = systemPrompt,
                )

            else ->
                OpenRouterService.translate(
                    text = text,
                    targetLanguage = targetLanguage,
                    apiKey = openRouterApiKey,
                    baseUrl = openRouterBaseUrl,
                    model = openRouterModel,
                    mode = mode,
                    customSystemPrompt = systemPrompt,
                )
        }
    }
}
