package com.anitail.desktop.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

class StringResolver(
    val locale: Locale,
    private val bundle: StringsBundle,
    private val fallback: StringsBundle,
) {
    fun get(id: String, vararg args: Any?): String {
        val template = bundle.strings[id] ?: fallback.strings[id] ?: id
        return format(template, args)
    }

    fun plural(id: String, count: Int, vararg args: Any?): String {
        val table = bundle.plurals[id] ?: fallback.plurals[id]
        if (table.isNullOrEmpty()) return get(id, *args)
        val category = PluralRules.select(locale, count)
        val template = table[category]
            ?: table[PluralCategory.OTHER]
            ?: table.values.firstOrNull()
            ?: id
        return format(template, args)
    }

    private fun format(template: String, args: Array<out Any?>): String {
        if (args.isEmpty()) return template
        return kotlin.runCatching { String.format(locale, template, *args) }
            .getOrDefault(template)
    }
}

val LocalStrings = staticCompositionLocalOf<StringResolver> {
    error("LocalStrings not provided")
}

@Composable
fun stringResource(id: String, vararg args: Any?): String =
    LocalStrings.current.get(id, *args)

@Composable
fun pluralStringResource(id: String, count: Int, vararg args: Any?): String =
    LocalStrings.current.plural(id, count, *args)

