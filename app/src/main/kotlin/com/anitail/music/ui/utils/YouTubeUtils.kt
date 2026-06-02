package com.anitail.music.ui.utils

private val GOOGLE_IMAGE_REGEX = Regex("https://lh3\\.googleusercontent\\.com/.*=w(\\d+)-h(\\d+).*")
private val YT_AVATAR_REGEX = Regex("https://yt3\\.ggpht\\.com/.*=s(\\d+)")

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String = try {
    if (width == null && height == null) {
        this
    } else if (this.contains("googleusercontent.com") || this.contains("ggpht.com")) {
        val w = width ?: height ?: 544
        val h = height ?: width ?: 544
        
        var result = this
        // Replace =wX-hX
        if (result.contains("=w")) {
            result = result.replace(Regex("=w\\d+-h\\d+"), "=w$w-h$h")
        }
        // Replace =sX (usually for avatars or square thumbnails)
        result = result.replace(Regex("=s\\d+"), "=s$w")
        
        // Replace -sX at the end of ggpht.com urls
        if (this.contains("ggpht.com")) {
            result = result.replace(Regex("-s\\d+"), "-s$w")
        }
        result
    } else {
        this
            .replace("hqdefault", "maxresdefault")
            .replace("mqdefault", "maxresdefault")
            .replace("sddefault", "maxresdefault")
    }
} catch (e: Throwable) {
    this
}
