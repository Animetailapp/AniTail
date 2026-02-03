package com.anitail.music.betterlyrics

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

object TTMLParser {
    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val words: List<ParsedWord>,
    )

    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double,
    )

    private data class SpanInfo(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val hasTrailingSpace: Boolean,
    )

    fun parseTTML(ttml: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ttml.byteInputStream())
            val pElements = doc.getElementsByTagName("p")

            for (i in 0 until pElements.length) {
                val pElement = pElements.item(i) as? Element ?: continue
                val begin = pElement.getAttribute("begin")
                if (begin.isNullOrEmpty()) continue

                val startTime = parseTime(begin)
                val spanInfos = mutableListOf<SpanInfo>()

                val childNodes = pElement.childNodes
                for (j in 0 until childNodes.length) {
                    val node = childNodes.item(j)
                    when (node.nodeType) {
                        Node.ELEMENT_NODE -> {
                            val span = node as? Element
                            if (span?.tagName?.lowercase() == "span") {
                                val wordBegin = span.getAttribute("begin")
                                val wordEnd = span.getAttribute("end")
                                val wordText = span.textContent

                                if (wordText.isNotEmpty() && wordBegin.isNotEmpty() && wordEnd.isNotEmpty()) {
                                    val nextSibling = node.nextSibling
                                    val hasTrailingSpace =
                                        nextSibling?.nodeType == Node.TEXT_NODE &&
                                            nextSibling.textContent?.contains(Regex("\\s")) == true

                                    spanInfos.add(
                                        SpanInfo(
                                            text = wordText,
                                            startTime = parseTime(wordBegin),
                                            endTime = parseTime(wordEnd),
                                            hasTrailingSpace = hasTrailingSpace,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                val words = mergeSpansIntoWords(spanInfos)
                val lineText = words.joinToString(" ") { it.text }
                val finalText = if (lineText.isEmpty()) {
                    pElement.textContent.trim()
                } else {
                    lineText
                }

                if (finalText.isNotEmpty()) {
                    lines.add(
                        ParsedLine(
                            text = finalText,
                            startTime = startTime,
                            words = words,
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return lines
    }

    private fun mergeSpansIntoWords(spanInfos: List<SpanInfo>): List<ParsedWord> {
        if (spanInfos.isEmpty()) return emptyList()

        val words = mutableListOf<ParsedWord>()
        var currentText = StringBuilder()
        var currentStartTime = spanInfos[0].startTime
        var currentEndTime = spanInfos[0].endTime

        for ((index, span) in spanInfos.withIndex()) {
            if (index == 0) {
                currentText.append(span.text)
                currentStartTime = span.startTime
                currentEndTime = span.endTime
            } else {
                val prevSpan = spanInfos[index - 1]
                if (prevSpan.hasTrailingSpace) {
                    if (currentText.isNotEmpty()) {
                        words.add(
                            ParsedWord(
                                text = currentText.toString().trim(),
                                startTime = currentStartTime,
                                endTime = currentEndTime,
                            )
                        )
                    }

                    currentText = StringBuilder(span.text)
                    currentStartTime = span.startTime
                    currentEndTime = span.endTime
                } else {
                    currentText.append(span.text)
                    currentEndTime = span.endTime
                }
            }
        }

        if (currentText.isNotEmpty()) {
            words.add(
                ParsedWord(
                    text = currentText.toString().trim(),
                    startTime = currentStartTime,
                    endTime = currentEndTime,
                )
            )
        }

        return words
    }

    fun toLRC(lines: List<ParsedLine>): String {
        return buildString {
            lines.forEach { line ->
                val timeMs = (line.startTime * 1000).toLong()
                val minutes = timeMs / 60000
                val seconds = (timeMs % 60000) / 1000
                val centiseconds = (timeMs % 1000) / 10

                appendLine(
                    String.format(
                        "[%02d:%02d.%02d]%s",
                        minutes,
                        seconds,
                        centiseconds,
                        line.text,
                    )
                )

                if (line.words.isNotEmpty()) {
                    val wordsData = line.words.joinToString("|") { word ->
                        "${word.text}:${word.startTime}:${word.endTime}"
                    }
                    appendLine("<$wordsData>")
                }
            }
        }
    }

    private fun parseTime(timeStr: String): Double {
        return try {
            when {
                timeStr.contains(":") -> {
                    val parts = timeStr.split(":")
                    when (parts.size) {
                        2 -> {
                            val minutes = parts[0].toDouble()
                            val seconds = parts[1].toDouble()
                            minutes * 60 + seconds
                        }
                        3 -> {
                            val hours = parts[0].toDouble()
                            val minutes = parts[1].toDouble()
                            val seconds = parts[2].toDouble()
                            hours * 3600 + minutes * 60 + seconds
                        }
                        else -> timeStr.toDoubleOrNull() ?: 0.0
                    }
                }
                else -> timeStr.toDoubleOrNull() ?: 0.0
            }
        } catch (_: Exception) {
            0.0
        }
    }
}
