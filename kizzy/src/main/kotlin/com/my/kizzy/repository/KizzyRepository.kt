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

/**
 * Modified by Zion Huang
 */
class KizzyRepository(
    userAgent: String,
    superProperties: String?
) {
    private val api = ApiService(userAgent, superProperties)

    suspend fun getImages(urls: List<String>): ImageProxyResponse? {
        val response = api.getImage(urls).getOrNull()
        return response?.body<ImageProxyResponse>()
    }

    suspend fun getImage(url: String): String? {
        val images = getImages(listOf(url))
        return images?.assets?.get(url)
    }
}
