package com.anitail.music.lyrics

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object TranslationUtils {
    private val cache = ConcurrentHashMap<String, String>() // key: "$src|$tgt|$text"
    private val translatorPool = ConcurrentHashMap<String, Translator>() // key: "$src|$tgt"

    private fun getOrCreateTranslator(sourceLang: String, targetLang: String): Translator {
        val key = "$sourceLang|$targetLang"
        return translatorPool.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
            Translation.getClient(options)
        }
    }

    suspend fun ensureModelDownloaded(sourceLang: String, targetLang: String) {
        withContext(Dispatchers.IO) {
            val client = getOrCreateTranslator(sourceLang, targetLang)
            client.downloadModelIfNeeded().await()
        }
    }

    suspend fun translateOrNull(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        val key = "$sourceLang|$targetLang|$text"
        cache[key]?.let { return@withContext it }
        runCatching {
            val client = getOrCreateTranslator(sourceLang, targetLang)
            val result = client.translate(text).await()
            cache[key] = result
            result
        }.getOrNull()
    }

    fun languageTagToMlKit(langTag: String): String? {
        return when (langTag.lowercase()) {
            "auto", "" -> null
            "en", "en-us", "en-gb" -> TranslateLanguage.ENGLISH
            "es", "es-419", "es-es" -> TranslateLanguage.SPANISH
            "pt", "pt-br", "pt-pt" -> TranslateLanguage.PORTUGUESE
            "fr", "fr-ca" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "it" -> TranslateLanguage.ITALIAN
            "ru" -> TranslateLanguage.RUSSIAN
            "uk" -> TranslateLanguage.UKRAINIAN
            "pl" -> TranslateLanguage.POLISH
            "tr" -> TranslateLanguage.TURKISH
            "vi" -> TranslateLanguage.VIETNAMESE
            "id" -> TranslateLanguage.INDONESIAN
            "ms" -> TranslateLanguage.MALAY
            "hi" -> TranslateLanguage.HINDI
            "bn" -> TranslateLanguage.BENGALI
            "ar" -> TranslateLanguage.ARABIC
            "fa" -> TranslateLanguage.PERSIAN
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "zh", "zh-cn" -> TranslateLanguage.CHINESE
            "zh-tw", "zh-hk" -> TranslateLanguage.CHINESE
            else -> null
        }
    }

    suspend fun close() {
        withContext(Dispatchers.IO) {
            translatorPool.values.forEach { runCatching { it.close() } }
            translatorPool.clear()
            cache.clear()
        }
    }
}
