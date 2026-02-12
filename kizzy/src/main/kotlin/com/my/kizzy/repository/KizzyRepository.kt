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
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Modified by Zion Huang
 */
class KizzyRepository {
    private val api = ApiService()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getImages(urls: List<String>): Map<String, String>? {
        val response = api.getImage(urls).getOrNull() ?: return null
        return try {
            val bodyString = response.bodyAsText()
            val element = json.parseToJsonElement(bodyString)
            if (element.jsonObject.containsKey("assets")) {
                json.decodeFromJsonElement<ImageProxyResponse>(element).assets
            } else {
                json.decodeFromJsonElement<Map<String, String>>(element)
            }
        } catch (e: Exception) {
            null
        }
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
