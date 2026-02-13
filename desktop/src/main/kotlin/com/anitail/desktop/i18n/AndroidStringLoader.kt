package com.anitail.desktop.i18n

import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

data class StringsBundle(
    val strings: Map<String, String>,
    val plurals: Map<String, Map<PluralCategory, String>>,
)

object AndroidStringLoader {
    private val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isCoalescing = true
        isIgnoringComments = true
        isNamespaceAware = false
    }

    fun load(appLanguage: String): StringResolver {
        val classLoader = AndroidStringLoader::class.java.classLoader
        val base = loadBundle("values/strings.xml", classLoader)
        val locale = resolveLocale(appLanguage)
        val localized = resolveCandidatePath(appLanguage, locale, classLoader)
            ?.let { loadBundle(it, classLoader) }
        val merged = mergeBundles(base, localized)
        return StringResolver(locale, merged, base)
    }

    private fun resolveLocale(appLanguage: String): Locale {
        if (appLanguage == SYSTEM_DEFAULT) return Locale.getDefault()
        val normalized = appLanguage.replace('_', '-')
        val parts = normalized.split('-')
        val language = parts.firstOrNull().orEmpty()
        val region = parts.getOrNull(1)
        return if (language.isBlank()) {
            Locale.getDefault()
        } else if (!region.isNullOrBlank()) {
            Locale.Builder().setLanguage(language).setRegion(region).build()
        } else {
            Locale.Builder().setLanguage(language).build()
        }
    }

    private fun resolveCandidatePath(
        appLanguage: String,
        locale: Locale,
        classLoader: ClassLoader?,
    ): String? {
        val candidates = buildCandidates(appLanguage, locale)
        return candidates.firstOrNull { classLoader?.getResource(it) != null }
    }

    private fun buildCandidates(appLanguage: String, locale: Locale): List<String> {
        val normalized = if (appLanguage == SYSTEM_DEFAULT) locale.toLanguageTag() else appLanguage
        val sanitized = normalized.replace('_', '-')
        val parts = sanitized.split('-')
        val language = parts.firstOrNull().orEmpty()
        val region = parts.getOrNull(1)
        val candidates = mutableListOf<String>()

        val explicitFallbacks = explicitQualifierFallbacks[sanitized] ?: emptyList()
        explicitFallbacks.forEach { qualifier ->
            candidates += "values-$qualifier/strings.xml"
        }

        if (!region.isNullOrBlank()) {
            val regionUpper = region.uppercase()
            candidates += "values-$language-r$regionUpper/strings.xml"
            candidates += "values-$language-$regionUpper/strings.xml"
            candidates += "values-$language-r$region/strings.xml"
            candidates += "values-$language-$region/strings.xml"
        }

        if (language.isNotBlank()) {
            candidates += "values-$language/strings.xml"
            candidates += "values-${language.lowercase()}/strings.xml"
            candidates += "values-${language.uppercase()}/strings.xml"
        }

        if (language == "pt" && region.isNullOrBlank()) {
            candidates += "values-pt-rBR/strings.xml"
        }

        return candidates.distinct()
    }

    private val explicitQualifierFallbacks = mapOf(
        "pt-PT" to listOf("pt-rBR"),
        "zh-HK" to listOf("zh-rTW"),
    )

    private fun mergeBundles(base: StringsBundle, overlay: StringsBundle?): StringsBundle {
        if (overlay == null) return base
        val mergedStrings = base.strings + overlay.strings
        val mergedPlurals = base.plurals.toMutableMap()
        overlay.plurals.forEach { (key, value) ->
            val baseItems = mergedPlurals[key].orEmpty()
            mergedPlurals[key] = baseItems + value
        }
        return StringsBundle(mergedStrings, mergedPlurals)
    }

    private fun loadBundle(path: String, classLoader: ClassLoader?): StringsBundle {
        val stream = classLoader?.getResourceAsStream(path)
            ?: return StringsBundle(emptyMap(), emptyMap())
        stream.use {
            return parseXml(it)
        }
    }

    private fun parseXml(stream: InputStream): StringsBundle {
        val builder = documentBuilderFactory.newDocumentBuilder()
        val document = builder.parse(stream)
        val resources = document.documentElement
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, MutableMap<PluralCategory, String>>()

        val stringNodes = resources.getElementsByTagName("string")
        for (index in 0 until stringNodes.length) {
            val element = stringNodes.item(index) as? Element ?: continue
            if (element.getAttribute("translatable") == "false") continue
            val name = element.getAttribute("name").orEmpty()
            if (name.isBlank()) continue
            val value = unescapeAndroidString(element.textContent)
            strings[name] = value
        }

        val pluralNodes = resources.getElementsByTagName("plurals")
        for (index in 0 until pluralNodes.length) {
            val element = pluralNodes.item(index) as? Element ?: continue
            val name = element.getAttribute("name").orEmpty()
            if (name.isBlank()) continue
            val items = mutableMapOf<PluralCategory, String>()
            val childNodes = element.childNodes
            for (childIndex in 0 until childNodes.length) {
                val item = childNodes.item(childIndex)
                if (item.nodeType != Node.ELEMENT_NODE) continue
                val itemElement = item as? Element ?: continue
                if (itemElement.tagName != "item") continue
                val quantity = itemElement.getAttribute("quantity").orEmpty()
                val category = quantityToCategory(quantity) ?: continue
                items[category] = unescapeAndroidString(itemElement.textContent)
            }
            if (items.isNotEmpty()) {
                plurals[name] = items
            }
        }

        return StringsBundle(strings, plurals)
    }

    private fun quantityToCategory(quantity: String): PluralCategory? =
        when (quantity) {
            "zero" -> PluralCategory.ZERO
            "one" -> PluralCategory.ONE
            "two" -> PluralCategory.TWO
            "few" -> PluralCategory.FEW
            "many" -> PluralCategory.MANY
            "other" -> PluralCategory.OTHER
            else -> null
        }

    private fun unescapeAndroidString(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
