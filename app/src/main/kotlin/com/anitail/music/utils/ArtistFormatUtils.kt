package com.anitail.music.utils

import java.net.URI

private const val DEFAULT_ARTWORK_SIZE_PX = 544
private const val MAX_ARTWORK_SIZE_PX = 2400

private val MONTHLY_LISTENERS_NUMBER_REGEX = Regex("^\\p{Nd}+(?:[\\s\\p{Zs}.,٬٫]\\p{Nd}+)*")

private val MONTHLY_LISTENERS_UNIT_REGEX =
    Regex(
        "^(?:[kmbt]|mln|mio|mil|mrd|тыс|млн|млрд|трлн|ألف|مليون|مليار)(?:\\.)?(?![\\p{L}\\p{M}])|^[万萬亿億兆]",
        RegexOption.IGNORE_CASE,
    )

private val PLAYBACK_COUNT_LABEL_REGEX =
    Regex("[\\s\\u00a0\\u202f]+(?:views?|plays?|reproducciones|visualizaciones|ascolti|visualizzazioni)\\s*$", RegexOption.IGNORE_CASE)

private val GOOGLE_ARTWORK_SIZE_SUFFIX = Regex("=(?:s(\\d+)|w(\\d+)(?:-[^-?#=]+)*-h\\d+)([^?#]*)")
private val YOUTUBE_VIDEO_ARTWORK_PATH =
    Regex("(/vi(?:_webp)?/[^/?#]+/)(mqdefault|hqdefault|sddefault|hq720|maxresdefault)(\\.(?:jpg|webp))(?=[?#]|$)")

private data class MonthlyListenersValue(
    val text: String,
)

fun monthlyListenersCount(raw: String?): String? = parseMonthlyListeners(raw)?.text

fun monthlyAudienceText(
    value: String?,
    audienceLabel: String,
): String? = monthlyListenersCount(value)?.let { "$it $audienceLabel" }

fun formatArtistSubscriberCount(
    raw: String?,
    template: (String) -> String,
): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val countOnly = monthlyListenersCount(trimmed) ?: return trimmed
    val remainder = trimmed.removePrefix(countOnly).trim()
    return if (remainder.isNotEmpty()) trimmed else template(countOnly)
}

private fun parseMonthlyListeners(raw: String?): MonthlyListenersValue? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val numberMatch = MONTHLY_LISTENERS_NUMBER_REGEX.find(text) ?: return null
    if (!numberMatch.value.any(Char::isDigit)) return null
    val numberEnd = numberMatch.range.last + 1
    val rest = text.substring(numberEnd)
    val unit = MONTHLY_LISTENERS_UNIT_REGEX.find(rest.trimStart())?.value
    val countText = numberMatch.value + if (unit != null) rest.takeWhile(Char::isWhitespace) + unit else ""
    return MonthlyListenersValue(countText)
}

fun String.withoutPlaybackCountLabel(): String = replace(PLAYBACK_COUNT_LABEL_REGEX, "").trim()

fun highResolutionArtworkUrl(url: String): String = sizedArtworkUrl(url, MAX_ARTWORK_SIZE_PX)

fun sizedArtworkUrl(
    url: String,
    requestedSizePx: Int? = DEFAULT_ARTWORK_SIZE_PX,
): String {
    val host = url
        .substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/')
        .substringBefore(':')
        .lowercase()
    val isGoogleArtworkHost = host.endsWith(".googleusercontent.com") || host.endsWith(".ggpht.com")
    val isYouTubeArtworkHost = host == "i.ytimg.com" || host.endsWith(".ytimg.com")
    if (!isGoogleArtworkHost && !isYouTubeArtworkHost) return url

    val sourcePath = url.substringBefore('#').substringBefore('?')
    val videoPath = YOUTUBE_VIDEO_ARTWORK_PATH.find(sourcePath).takeIf { isYouTubeArtworkHost }
    if (videoPath != null) {
        val fragment = url.substringAfter('#', missingDelimiterValue = "")
        val source = sourcePath + fragment.takeIf { it.isNotEmpty() }?.let { "#$it" }.orEmpty()
        return if (videoPath.groupValues[2] == "mqdefault" && (requestedSizePx ?: DEFAULT_ARTWORK_SIZE_PX) > 320) {
            source.replaceRange(videoPath.range, "${videoPath.groupValues[1]}maxresdefault${videoPath.groupValues[3]}")
        } else {
            source
        }
    }

    val sizePx = (requestedSizePx ?: DEFAULT_ARTWORK_SIZE_PX).coerceIn(1, MAX_ARTWORK_SIZE_PX)
    if (GOOGLE_ARTWORK_SIZE_SUFFIX.containsMatchIn(url)) {
        return url.replace(GOOGLE_ARTWORK_SIZE_SUFFIX) { match ->
            val suffix = match.groupValues[3]
            val size = if ("s" in suffix.split('-')) "w$sizePx-h$sizePx" else "s$sizePx"
            "=$size$suffix"
        }
    }

    if (!isGoogleArtworkHost) return url
    val suffixIndex = url.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 } ?: url.length
    return url.substring(0, suffixIndex) + "=s$sizePx" + url.substring(suffixIndex)
}

data class LinkSegment(
    val text: String,
    val url: String? = null,
)

private val URL_REGEX = Regex("""(?:https?:)?//[^\s<>"]+""")

fun String.toLinkSegments(): List<LinkSegment> {
    if (isBlank()) return emptyList()
    val segments = mutableListOf<LinkSegment>()
    var lastIndex = 0
    URL_REGEX.findAll(this).forEach { match ->
        val matchStart = match.range.first
        val matchEnd = match.range.last + 1
        val rawUrl = substring(matchStart, matchEnd)
        val closingQuote = if (matchStart > 0 && this[matchStart - 1] == '\'' && rawUrl.endsWith("'")) "'" else ""
        val (url, trailingUrlPunctuation) = extractCleanUrlAndTrailing(rawUrl.removeSuffix(closingQuote))
        val trailingPunctuation = trailingUrlPunctuation + closingQuote
        val label = linkLabel(url)
        val plainBeforeEnd =
            if (matchStart > 0 && this[matchStart - 1] == '(' && trailingPunctuation.startsWith(")")) {
                matchStart - 1
            } else {
                matchStart
            }
        val plainText = substring(lastIndex, plainBeforeEnd)

        if (isCreativeCommonsUrl(url)) {
            val occ = findLicensePhraseStart(plainText)
            if (occ >= 0) {
                val prefix = plainText.substring(0, occ).trimEnd(' ', '(')
                if (prefix.isNotBlank()) segments.add(LinkSegment(prefix + if (prefix.endsWith(" ")) "" else " "))
                segments.add(LinkSegment(plainText.substring(occ).trim(' ', '(', ')'), url))
            } else {
                val cleanedPlain = plainText.trimEnd(' ', '(')
                if (cleanedPlain.isNotBlank()) {
                    segments.add(LinkSegment(cleanedPlain + if (cleanedPlain.endsWith(" ")) "" else " "))
                }
                segments.add(LinkSegment(label, url))
            }
        } else {
            val occ = findLastWord(plainText, label)
            if (occ >= 0) {
                val wordEnd = occ + label.length
                val prefix = plainText.substring(0, occ).trimEnd(' ', '(')
                if (prefix.isNotBlank()) segments.add(LinkSegment(prefix + if (prefix.endsWith(" ")) "" else " "))
                segments.add(LinkSegment(plainText.substring(occ, wordEnd), url))
                val suffix = plainText.substring(wordEnd).trim(' ', '(', ')')
                if (suffix.isNotBlank()) segments.add(LinkSegment(" $suffix"))
            } else {
                val cleanedPlain = plainText.trimEnd(' ', '(')
                if (cleanedPlain.isNotBlank()) {
                    segments.add(LinkSegment(cleanedPlain + if (cleanedPlain.endsWith(" ")) "" else " "))
                }
                segments.add(LinkSegment(label, url))
            }
        }
        val consumedUrlLength = matchEnd - matchStart - trailingPunctuation.length
        val afterUrlIndex = matchStart + consumedUrlLength
        lastIndex =
            if (plainBeforeEnd < matchStart && trailingPunctuation.startsWith(")")) {
                afterUrlIndex + 1
            } else {
                afterUrlIndex
            }
    }
    if (lastIndex < length) {
        val remaining = substring(lastIndex).trimStart(' ', ')')
        if (remaining.isNotBlank()) {
            segments.add(LinkSegment(remaining))
        }
    }
    return segments.filterNot { it.text.isBlank() }
}

private fun extractCleanUrlAndTrailing(rawMatchedText: String): Pair<String, String> {
    var url = rawMatchedText
    var trailing = ""

    while (url.isNotEmpty()) {
        val lastChar = url.last()
        when {
            lastChar in ".,;:!?" -> {
                trailing = lastChar + trailing
                url = url.dropLast(1)
            }
            lastChar == ')' -> {
                val openCount = url.count { it == '(' }
                val closeCount = url.count { it == ')' }
                if (closeCount > openCount) {
                    trailing = lastChar + trailing
                    url = url.dropLast(1)
                } else {
                    break
                }
            }
            lastChar == ']' -> {
                val openCount = url.count { it == '[' }
                val closeCount = url.count { it == ']' }
                if (closeCount > openCount) {
                    trailing = lastChar + trailing
                    url = url.dropLast(1)
                } else {
                    break
                }
            }
            else -> break
        }
    }
    return Pair(normalizeUrl(url), trailing)
}

private fun findLastWord(
    text: String,
    word: String,
): Int {
    var idx = text.lastIndexOf(word, ignoreCase = true)
    while (idx >= 0) {
        val before = if (idx == 0) ' ' else text[idx - 1]
        val after = if (idx + word.length >= text.length) ' ' else text[idx + word.length]
        if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return idx
        idx = text.lastIndexOf(word, idx - 1, ignoreCase = true)
    }
    return -1
}

private fun getUrlHost(url: String): String? =
    runCatching { URI(url).host }.getOrNull()
        ?: runCatching {
            url
                .substringAfter("://")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore(":")
                .takeIf { it.isNotBlank() }
        }.getOrNull()

private fun linkLabel(url: String): String =
    runCatching {
        val host = getUrlHost(url)?.lowercase() ?: return url
        when {
            host.matchesDomain("wikipedia.org") -> "Wikipedia"
            host.matchesDomain("creativecommons.org") -> creativeCommonsLicenseLabel(url) ?: "Creative Commons"
            host.matchesDomain("last.fm") -> "Last.fm"
            host.matchesDomain("musicbrainz.org") -> "MusicBrainz"
            host.matchesDomain("discogs.com") -> "Discogs"
            host.matchesDomain("facebook.com") -> "Facebook"
            host.matchesDomain("instagram.com") -> "Instagram"
            host.matchesDomain("twitter.com") || host.matchesDomain("x.com") -> "X"
            host.matchesDomain("youtube.com") || host.matchesDomain("youtu.be") -> "YouTube"
            host.matchesDomain("tiktok.com") -> "TikTok"
            host.matchesDomain("spotify.com") -> "Spotify"
            else -> host.removePrefix("www.")
        }
    }.getOrDefault(url)

private fun normalizeUrl(url: String): String = if (url.startsWith("//")) "https:$url" else url

private fun String.matchesDomain(domain: String): Boolean = this == domain || endsWith(".$domain")

private fun isCreativeCommonsUrl(url: String): Boolean = getUrlHost(url)?.lowercase()?.matchesDomain("creativecommons.org") == true

private fun findLicensePhraseStart(text: String): Int {
    val match = Regex("(?i)(creative commons|\\bcc\\b)").find(text) ?: return -1
    return match.range.first
}

private fun creativeCommonsLicenseLabel(url: String): String? {
    val match = Regex("""creativecommons\.org/(?:licenses|publicdomain)/([^/\s]+)(?:/([^/\s]+))?""").find(url) ?: return null
    val license = match.groupValues[1]
    val version = match.groupValues[2].takeIf { it.isNotBlank() } ?: return null
    return when (license) {
        "zero" -> "CC0 $version"
        else -> {
            val codes = license.split("-")
            if (codes.isEmpty()) return null
            "CC " + codes.joinToString("-") { it.uppercase() } + " $version"
        }
    }
}

