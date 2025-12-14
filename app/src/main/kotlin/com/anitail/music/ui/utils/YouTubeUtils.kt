package com.anitail.music.ui.utils

private val GOOGLE_IMAGE_REGEX = Regex("https://lh3\\.googleusercontent\\.com/.*=w(\\d+)-h(\\d+).*")
private val YT_AVATAR_REGEX = Regex("https://yt3\\.ggpht\\.com/.*=s(\\d+)")

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this
    GOOGLE_IMAGE_REGEX.matchEntire(this)?.groupValues?.let { group ->
        val (W, H) = group.drop(1).map { it.toInt() }
        var w = width
        var h = height
        if (w != null && h == null) h = (w / W) * H
        if (w == null && h != null) w = (h / H) * W
        return "${split("=w")[0]}=w$w-h$h-p-l90-rj"
    }
    if (this matches YT_AVATAR_REGEX) {
        return "$this-s${width ?: height}"
    }
    return this
}
