package com.my.kizzy.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    @SerialName("id")
    val id: String,
)

@Serializable
data class ImageProxyResponse(
    @SerialName("assets")
    val assets: Map<String, String>,
)
