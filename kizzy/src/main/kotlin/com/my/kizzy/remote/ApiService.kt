/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ApiService.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.logging.Logger

/**
 * Modified by Zion Huang
 */
class ApiService(
    private val userAgent: String,
    private val superProperties: String?
) {
    private val logger = Logger.getLogger(ApiService::class.java.name)
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            })
        }
        install(HttpCache)
        install(DefaultRequest) {
            header(HttpHeaders.UserAgent, userAgent)
            if (superProperties != null) {
                header("X-Super-Properties", superProperties)
            }
            val locale = java.util.Locale.getDefault().toLanguageTag()
            header(HttpHeaders.AcceptLanguage, locale)
            header("X-Discord-Locale", locale)
            header("X-Discord-Timezone", java.util.TimeZone.getDefault().id)
        }
    }

    suspend fun getImage(urls: List<String>) = runCatching {
        client.get {
            url("https://kizzy-helper.vercel.app/images")
            parameter("urls", urls.joinToString(","))
        }
    }
}
