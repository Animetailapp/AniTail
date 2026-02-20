package com.anitail.desktop.i18n

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class PluralRulesTest {
    @Test
    fun englishUsesOneAndOther() {
        val locale = Locale.ENGLISH
        assertEquals(PluralCategory.ONE, PluralRules.select(locale, 1))
        assertEquals(PluralCategory.OTHER, PluralRules.select(locale, 2))
        assertEquals(PluralCategory.OTHER, PluralRules.select(locale, 0))
    }

    @Test
    fun russianUsesFewAndMany() {
        val locale = Locale("ru")
        assertEquals(PluralCategory.ONE, PluralRules.select(locale, 1))
        assertEquals(PluralCategory.FEW, PluralRules.select(locale, 2))
        assertEquals(PluralCategory.MANY, PluralRules.select(locale, 5))
        assertEquals(PluralCategory.MANY, PluralRules.select(locale, 11))
    }
}

