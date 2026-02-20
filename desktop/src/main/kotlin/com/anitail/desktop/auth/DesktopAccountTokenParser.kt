package com.anitail.desktop.auth

data class ParsedAccountToken(
    val cookie: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val channelHandle: String? = null,
) {
    fun hasAnyValue(): Boolean {
        return !cookie.isNullOrBlank() ||
            !visitorData.isNullOrBlank() ||
            !dataSyncId.isNullOrBlank() ||
            !accountName.isNullOrBlank() ||
            !accountEmail.isNullOrBlank() ||
            !channelHandle.isNullOrBlank()
    }
}

object DesktopAccountTokenParser {
    private const val COOKIE_PREFIX = "***INNERTUBE COOKIE***"
    private const val VISITOR_PREFIX = "***VISITOR DATA***"
    private const val DATASYNC_PREFIX = "***DATASYNC ID***"
    private const val ACCOUNT_NAME_PREFIX = "***ACCOUNT NAME***"
    private const val ACCOUNT_EMAIL_PREFIX = "***ACCOUNT EMAIL***"
    private const val ACCOUNT_HANDLE_PREFIX = "***ACCOUNT CHANNEL HANDLE***"

    fun parse(raw: String): ParsedAccountToken {
        var parsed = ParsedAccountToken()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            when {
                trimmed.contains(COOKIE_PREFIX) -> {
                    parsed = parsed.copy(cookie = extractValue(trimmed, COOKIE_PREFIX).ifBlank { null })
                }
                trimmed.contains(VISITOR_PREFIX) -> {
                    parsed = parsed.copy(visitorData = extractValue(trimmed, VISITOR_PREFIX).ifBlank { null })
                }
                trimmed.contains(DATASYNC_PREFIX) -> {
                    parsed = parsed.copy(dataSyncId = extractValue(trimmed, DATASYNC_PREFIX).ifBlank { null })
                }
                trimmed.contains(ACCOUNT_NAME_PREFIX) -> {
                    parsed = parsed.copy(accountName = extractValue(trimmed, ACCOUNT_NAME_PREFIX).ifBlank { null })
                }
                trimmed.contains(ACCOUNT_EMAIL_PREFIX) -> {
                    parsed = parsed.copy(accountEmail = extractValue(trimmed, ACCOUNT_EMAIL_PREFIX).ifBlank { null })
                }
                trimmed.contains(ACCOUNT_HANDLE_PREFIX) -> {
                    parsed = parsed.copy(channelHandle = extractValue(trimmed, ACCOUNT_HANDLE_PREFIX).ifBlank { null })
                }
            }
        }

        if (parsed.cookie.isNullOrBlank()) {
            val cookieLine = raw.lineSequence()
                .map { it.trim() }
                .firstOrNull {
                    it.contains("SAPISID=") ||
                        it.contains("__Secure-1PAPISID") ||
                        it.contains("__Secure-3PAPISID")
                }
            if (!cookieLine.isNullOrBlank()) {
                parsed = parsed.copy(cookie = cookieLine)
            }
        }
        return parsed
    }

    fun buildTokenText(credentials: AuthCredentials): String {
        val cookie = credentials.cookie ?: ""
        val visitor = credentials.visitorData ?: ""
        val dataSync = credentials.dataSyncId ?: ""
        val name = credentials.accountName ?: ""
        val email = credentials.accountEmail ?: ""
        val handle = credentials.channelHandle ?: ""

        return """
            $COOKIE_PREFIX =$cookie

            $VISITOR_PREFIX =$visitor

            $DATASYNC_PREFIX =$dataSync

            $ACCOUNT_NAME_PREFIX =$name

            $ACCOUNT_EMAIL_PREFIX =$email

            $ACCOUNT_HANDLE_PREFIX =$handle
        """.trimIndent()
    }

    private fun extractValue(line: String, prefix: String): String {
        val index = line.indexOf(prefix)
        if (index == -1) return ""
        var value = line.substring(index + prefix.length).trimStart()
        if (value.startsWith("=")) {
            value = value.removePrefix("=").trimStart()
        }
        return value
    }
}
