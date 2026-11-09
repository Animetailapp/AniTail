/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anitail.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DeepLService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val MAX_RETRIES = 3

    // Mapping from full language name (used by OpenRouter) to DeepL language code
    private val languageNameToCode = mapOf(
        "Arabic" to "AR",
        "Bulgarian" to "BG",
        "Chinese (Simplified)" to "ZH-HANS",
        "Chinese (Traditional)" to "ZH-HANT",
        "Chinese" to "ZH",
        "Czech" to "CS",
        "Danish" to "DA",
        "Dutch" to "NL",
        "English" to "EN-US",
        "English (US)" to "EN-US",
        "English (UK)" to "EN-GB",
        "Estonian" to "ET",
        "Finnish" to "FI",
        "French" to "FR",
        "German" to "DE",
        "Greek" to "EL",
        "Hungarian" to "HU",
        "Indonesian" to "ID",
        "Italian" to "IT",
        "Japanese" to "JA",
        "Korean" to "KO",
        "Latvian" to "LV",
        "Lithuanian" to "LT",
        "Norwegian" to "NB",
        "Polish" to "PL",
        "Portuguese" to "PT-PT",
        "Portuguese (Brazilian)" to "PT-BR",
        "Romanian" to "RO",
        "Russian" to "RU",
        "Slovak" to "SK",
        "Slovenian" to "SL",
        "Spanish" to "ES",
        "Swedish" to "SV",
        "Turkish" to "TR",
        "Ukrainian" to "UK"
    )

    // Languages that support formality parameter in DeepL
    private val formalitySupported = setOf(
        "DE", "FR", "IT", "ES", "NL", "PL", "PT-BR", "PT-PT", "JA", "RU"
    )

    suspend fun translate(
        text: String,
        targetLanguage: String,
        apiKey: String,
        formality: String = "default",
        maxRetries: Int = MAX_RETRIES
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        var currentAttempt = 0

        val lines = text.lines()
        if (lines.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(Exception("DeepL API key is required"))
        }

        // Determine endpoint based on API key type (free keys end with ":fx")
        val baseUrl = if (trimmedKey.endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }

        // Resolve language code
        val targetLangCode = languageNameToCode[targetLanguage]
            ?: targetLanguage.uppercase().take(2)

        // Build JSON request body - DeepL accepts up to 50 texts per request
        // Split into batches if needed
        val batchSize = 50
        val batches = lines.chunked(batchSize)
        val allTranslated = mutableListOf<String>()

        for (batch in batches) {
            val textArray = JSONArray().apply {
                batch.forEach { line -> put(line) }
            }

            val jsonBody = JSONObject().apply {
                put("text", textArray)
                put("target_lang", targetLangCode)
                put("preserve_formatting", true)

                // Add formality only if supported for target language and not default
                if (formality != "default" && targetLangCode in formalitySupported) {
                    put("formality", formality)
                }
            }

            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "DeepL-Auth-Key $trimmedKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Metrolist/1.0")
                .post(jsonBody.toString().toRequestBody(JSON))
                .build()

            var batchSuccess = false
            currentAttempt = 0

            while (currentAttempt < maxRetries && !batchSuccess) {
                try {
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        if (response.code in listOf(429, 500, 502, 503, 504)) {
                            currentAttempt++
                            if (currentAttempt < maxRetries) {
                                kotlinx.coroutines.delay(2000L * currentAttempt)
                                continue
                            }
                        }

                        val errorMsg = when (response.code) {
                            403 -> "Invalid DeepL API key (403 Forbidden)"
                            456 -> "DeepL quota exceeded (456)"
                            else -> try {
                                JSONObject(responseBody ?: "").optString("message")
                                    .ifBlank { "HTTP ${response.code}: ${response.message}" }
                            } catch (e: Exception) {
                                "HTTP ${response.code}: ${responseBody ?: response.message}"
                            }
                        }
                        return@withContext Result.failure(Exception("DeepL error: $errorMsg"))
                    }

                    if (responseBody == null) {
                        currentAttempt++
                        continue
                    }

                    val jsonResponse = JSONObject(responseBody)
                    val translations = jsonResponse.optJSONArray("translations")

                    if (translations != null && translations.length() == batch.size) {
                        for (i in 0 until translations.length()) {
                            val translatedText = translations.getJSONObject(i).optString("text", "")
                            allTranslated.add(translatedText)
                        }
                        batchSuccess = true
                    } else {
                        currentAttempt++
                    }
                } catch (e: Exception) {
                    currentAttempt++
                    if (currentAttempt >= maxRetries) {
                        return@withContext Result.failure(Exception("DeepL network error: ${e.message}"))
                    }
                    kotlinx.coroutines.delay(1000L * currentAttempt)
                }
            }

            if (!batchSuccess) {
                return@withContext Result.failure(Exception("DeepL translation failed after $maxRetries attempts"))
            }
        }

        return@withContext Result.success(allTranslated)
    }
}
