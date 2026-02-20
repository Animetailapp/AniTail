package com.anitail.desktop.i18n

import java.util.Locale

enum class PluralCategory {
    ZERO,
    ONE,
    TWO,
    FEW,
    MANY,
    OTHER,
}

object PluralRules {
    fun select(locale: Locale, count: Int): PluralCategory {
        val language = locale.language.lowercase()
        return when (language) {
            "ar" -> arabic(count)
            "be", "ru", "uk" -> eastSlavic(count)
            "pl" -> polish(count)
            "cs", "sk" -> czechSlovak(count)
            "fr" -> french(count)
            else -> {
                if (count == 1) PluralCategory.ONE else PluralCategory.OTHER
            }
        }
    }

    private fun arabic(n: Int): PluralCategory {
        if (n == 0) return PluralCategory.ZERO
        if (n == 1) return PluralCategory.ONE
        if (n == 2) return PluralCategory.TWO
        val mod100 = n % 100
        return when {
            mod100 in 3..10 -> PluralCategory.FEW
            mod100 in 11..99 -> PluralCategory.MANY
            else -> PluralCategory.OTHER
        }
    }

    private fun eastSlavic(n: Int): PluralCategory {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> PluralCategory.ONE
            mod10 in 2..4 && mod100 !in 12..14 -> PluralCategory.FEW
            mod10 == 0 || mod10 in 5..9 || mod100 in 11..14 -> PluralCategory.MANY
            else -> PluralCategory.OTHER
        }
    }

    private fun polish(n: Int): PluralCategory {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            n == 1 -> PluralCategory.ONE
            mod10 in 2..4 && mod100 !in 12..14 -> PluralCategory.FEW
            else -> PluralCategory.MANY
        }
    }

    private fun czechSlovak(n: Int): PluralCategory =
        when (n) {
            1 -> PluralCategory.ONE
            2, 3, 4 -> PluralCategory.FEW
            else -> PluralCategory.OTHER
        }

    private fun french(n: Int): PluralCategory =
        if (n == 0 || n == 1) PluralCategory.ONE else PluralCategory.OTHER
}

