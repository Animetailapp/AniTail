/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KizzyRepository.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.repository

import com.my.kizzy.remote.ApiService
import com.my.kizzy.remote.ImageProxyResponse
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.logging.Logger

/**
 * Modified by Zion Huang
 */
class KizzyRepository {
    private val logger = Logger.getLogger("Kizzy")
    private val api = ApiService()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getImages(urls: List<String>): Map<String, String>? {
        for (i in 0 until api.getProxyCount()) {
            val result = api.getImage(urls, i)
            val response: HttpResponse? = result.getOrNull()

            if (response != null && response.status.value == 200) {
                try {
                    val bodyString = response.bodyAsText()
                    val element = json.parseToJsonElement(bodyString)
                    if (element is kotlinx.serialization.json.JsonObject) {
                        return if (element.containsKey("assets")) {
                            json.decodeFromJsonElement<ImageProxyResponse>(element).assets
                        } else if (element.containsKey("error")) {
                            logger.warning("Kizzy: Proxy $i returned error: $bodyString")
                            null
                        } else {
                            json.decodeFromJsonElement<Map<String, String>>(element)
                        }
                    }
                } catch (e: Exception) {
                    logger.warning("Kizzy: Failed to parse proxy $i response: ${e.message}")
                }
            } else {
                val status = response?.status?.value ?: result.exceptionOrNull()?.message ?: "Unknown Error"
                if (status is String && !status.contains("composition")) {
                    logger.warning("Kizzy: Proxy $i failed with status $status")
                }
            }
        }
        return null
    }

    suspend fun getImage(url: String): String? {
        val assets = getImages(listOf(url))
        // Try exact match first
        assets?.get(url)?.let { return it }
        // Try any match if there is only one asset returned
        if (assets?.size == 1) {
            return assets.values.first()
        }
        return null
    }
}
