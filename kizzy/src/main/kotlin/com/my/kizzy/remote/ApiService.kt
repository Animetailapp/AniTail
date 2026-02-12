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
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.logging.Logger

/**
 * Modified by Zion Huang
 */
class ApiService {
    private val logger = Logger.getLogger(ApiService::class.java.name)
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            })
        }
    }

    private val proxies = listOf(
        "https://kizzy-helper.vercel.app/images",
        "https://kizzy-helper-images.vercel.app/images",
        "https://kizzy.dead8309.xyz/api/images"
    )

    suspend fun getImage(urls: List<String>, proxyIndex: Int = 0): Result<HttpResponse> {
        val proxyUrl = proxies.getOrNull(proxyIndex) ?: return Result.failure(Exception("No more proxies"))

        return runCatching {
            client.get {
                url(proxyUrl)
                parameter("urls", urls.joinToString(","))
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
        }
    }

    fun getProxyCount() = proxies.size
}
