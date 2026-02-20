package com.anitail.desktop.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAccountTokenParserTest {
    @Test
    fun parseAllFieldsFromToken() {
        val raw = """
            ***INNERTUBE COOKIE*** =SAPISID=abc; SID=def
            
            ***VISITOR DATA*** =visitor123
            
            ***DATASYNC ID*** =dataSync456
            
            ***ACCOUNT NAME*** =Tony Colombo
            
            ***ACCOUNT EMAIL*** =tony@example.com
            
            ***ACCOUNT CHANNEL HANDLE*** =@tonycolombo
        """.trimIndent()

        val parsed = DesktopAccountTokenParser.parse(raw)

        assertEquals("SAPISID=abc; SID=def", parsed.cookie)
        assertEquals("visitor123", parsed.visitorData)
        assertEquals("dataSync456", parsed.dataSyncId)
        assertEquals("Tony Colombo", parsed.accountName)
        assertEquals("tony@example.com", parsed.accountEmail)
        assertEquals("@tonycolombo", parsed.channelHandle)
    }

    @Test
    fun parseIgnoresUnknownLinesAndKeepsLastValue() {
        val raw = """
            random line
            ***VISITOR DATA*** =first
            ***VISITOR DATA*** =second
            ***ACCOUNT NAME*** =   Luigi   
        """.trimIndent()

        val parsed = DesktopAccountTokenParser.parse(raw)

        assertEquals("second", parsed.visitorData)
        assertEquals("Luigi", parsed.accountName)
    }

    @Test
    fun parseAllowsPrefixesWithLeadingText() {
        val raw = """
            1) ***INNERTUBE COOKIE*** =SAPISID=abc; SID=def
            - ***VISITOR DATA*** =visitor123
            • ***DATASYNC ID*** =dataSync456
        """.trimIndent()

        val parsed = DesktopAccountTokenParser.parse(raw)

        assertEquals("SAPISID=abc; SID=def", parsed.cookie)
        assertEquals("visitor123", parsed.visitorData)
        assertEquals("dataSync456", parsed.dataSyncId)
    }

    @Test
    fun parseFallsBackToRawCookieLine() {
        val raw = "SAPISID=abc; SID=def; __Secure-1PAPISID=ghi"

        val parsed = DesktopAccountTokenParser.parse(raw)

        assertEquals("SAPISID=abc; SID=def; __Secure-1PAPISID=ghi", parsed.cookie)
    }

    @Test
    fun buildTokenTextContainsAllFields() {
        val credentials = AuthCredentials(
            visitorData = "visitor",
            dataSyncId = "sync",
            cookie = "SAPISID=zzz",
            accountName = "Name",
            accountEmail = "mail@test.com",
            channelHandle = "@handle",
        )

        val text = DesktopAccountTokenParser.buildTokenText(credentials)

        assertTrue(text.contains("***INNERTUBE COOKIE*** =SAPISID=zzz"))
        assertTrue(text.contains("***VISITOR DATA*** =visitor"))
        assertTrue(text.contains("***DATASYNC ID*** =sync"))
        assertTrue(text.contains("***ACCOUNT NAME*** =Name"))
        assertTrue(text.contains("***ACCOUNT EMAIL*** =mail@test.com"))
        assertTrue(text.contains("***ACCOUNT CHANNEL HANDLE*** =@handle"))
    }
}
