package com.my.kizzy.rpc

open class KizzyRPC(val token: String) {
    enum class Type {
        LISTENING,
        PLAYING,
        WATCHING,
        COMPETING
    }

    fun setActivity(
        name: String,
        details: String?,
        state: String?,
        largeImage: RpcImage?,
        smallImage: RpcImage?,
        largeText: String?,
        smallText: String?,
        buttons: List<Pair<String, String>>?,
        type: Type,
        startTime: Long,
        endTime: Long,
        applicationId: String
    ) {}

    fun closeRPC() {}
}

sealed class RpcImage {
    class ExternalImage(val url: String, val fallback: String) : RpcImage()
    class DiscordImage(val name: String) : RpcImage()
}
