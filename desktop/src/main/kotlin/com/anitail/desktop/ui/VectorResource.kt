package com.anitail.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

internal object VectorResource {
    private val cache = mutableMapOf<String, ImageVector>()

    fun load(resourceName: String): ImageVector {
        return cache.getOrPut(resourceName) {
            val stream = openResource(resourceName)
                ?: error("Vector resource not found: $resourceName")
            stream.use { parseVector(it, resourceName) }
        }
    }

    private fun openResource(resourceName: String): InputStream? {
        val path = "drawable/$resourceName"
        return Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: VectorResource::class.java.classLoader.getResourceAsStream(path)
    }

    private fun parseVector(stream: InputStream, resourceName: String): ImageVector {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        val vector = document.documentElement

        val defaultWidth = parseDp(vector.getAttribute("android:width"))
        val defaultHeight = parseDp(vector.getAttribute("android:height"))
        val viewportWidth =
            vector.getAttribute("android:viewportWidth").toFloatOrNull() ?: defaultWidth.value
        val viewportHeight =
            vector.getAttribute("android:viewportHeight").toFloatOrNull() ?: defaultHeight.value
        val tintColor = parseColor(vector.getAttribute("android:tint"))
        val autoMirror = vector.getAttribute("android:autoMirrored").equals("true", ignoreCase = true)

        val builder = ImageVector.Builder(
            name = resourceName,
            defaultWidth = defaultWidth,
            defaultHeight = defaultHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            tintColor = tintColor ?: Color.Unspecified,
            autoMirror = autoMirror,
        )

        parseVectorChildren(vector, builder)

        return builder.build()
    }

    private fun parseVectorChildren(element: Element, builder: ImageVector.Builder) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            when (child.tagName) {
                "group" -> parseGroup(child, builder)
                "path" -> parsePath(child, builder)
                "clip-path" -> Unit
            }
        }
    }

    private fun parseGroup(element: Element, builder: ImageVector.Builder) {
        val rotate = element.getAttribute("android:rotation").toFloatOrNull() ?: 0f
        val pivotX = element.getAttribute("android:pivotX").toFloatOrNull() ?: 0f
        val pivotY = element.getAttribute("android:pivotY").toFloatOrNull() ?: 0f
        val scaleX = element.getAttribute("android:scaleX").toFloatOrNull() ?: 1f
        val scaleY = element.getAttribute("android:scaleY").toFloatOrNull() ?: 1f
        val translateX = element.getAttribute("android:translateX").toFloatOrNull() ?: 0f
        val translateY = element.getAttribute("android:translateY").toFloatOrNull() ?: 0f
        val clipPathData = parseClipPath(element)

        builder.addGroup(
            rotate = rotate,
            pivotX = pivotX,
            pivotY = pivotY,
            scaleX = scaleX,
            scaleY = scaleY,
            translationX = translateX,
            translationY = translateY,
            clipPathData = clipPathData,
        )
        parseVectorChildren(element, builder)
        builder.clearGroup()
    }

    private fun parseClipPath(element: Element): List<PathNode> {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            if (child.tagName == "clip-path") {
                val pathData = child.getAttribute("android:pathData")
                if (pathData.isNotBlank()) {
                    return parsePathData(pathData)
                }
            }
        }
        return emptyList()
    }

    private fun parsePath(element: Element, builder: ImageVector.Builder) {
        val pathData = element.getAttribute("android:pathData")
        if (pathData.isBlank()) return

        val fillColor = parseColor(element.getAttribute("android:fillColor"))
        val strokeColor = parseColor(element.getAttribute("android:strokeColor"))
        val strokeWidth = element.getAttribute("android:strokeWidth").toFloatOrNull() ?: 0f
        val fillAlpha = element.getAttribute("android:fillAlpha").toFloatOrNull() ?: 1f
        val strokeAlpha = element.getAttribute("android:strokeAlpha").toFloatOrNull() ?: 1f
        val fillType = when (element.getAttribute("android:fillType")) {
            "evenOdd" -> PathFillType.EvenOdd
            else -> PathFillType.NonZero
        }
        val strokeCap = when (element.getAttribute("android:strokeLineCap")) {
            "round" -> StrokeCap.Round
            "square" -> StrokeCap.Square
            else -> StrokeCap.Butt
        }
        val strokeJoin = when (element.getAttribute("android:strokeLineJoin")) {
            "round" -> StrokeJoin.Round
            "bevel" -> StrokeJoin.Bevel
            else -> StrokeJoin.Miter
        }

        builder.addPath(
            pathData = parsePathData(pathData),
            pathFillType = fillType,
            fill = fillColor?.let { SolidColor(it) },
            fillAlpha = fillAlpha,
            stroke = strokeColor?.let { SolidColor(it) },
            strokeAlpha = strokeAlpha,
            strokeLineWidth = strokeWidth,
            strokeLineCap = strokeCap,
            strokeLineJoin = strokeJoin,
        )
    }

    private fun parseDp(raw: String): Dp {
        val normalized = raw.trim().lowercase()
        val numeric = normalized
            .removeSuffix("dp")
            .removeSuffix("dip")
            .removeSuffix("px")
        return numeric.toFloatOrNull()?.dp ?: 24.dp
    }

    private fun parseColor(raw: String?): Color? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        if (value.startsWith("@android:color/") || value.startsWith("@color/")) {
            return when (value.substringAfterLast("/").lowercase()) {
                "white" -> Color.White
                "black" -> Color.Black
                "transparent" -> Color.Transparent
                else -> null
            }
        }
        if (!value.startsWith("#")) return null
        return when (value.length) {
            7 -> Color(androidColorToArgb(value))
            9 -> Color(androidColorToArgb(value))
            else -> null
        }
    }

    private fun androidColorToArgb(value: String): Int {
        val hex = value.removePrefix("#")
        return when (hex.length) {
            6 -> {
                val rgb = hex.toLong(16).toInt()
                (0xFF shl 24) or rgb
            }
            8 -> {
                val argb = hex.toLong(16).toInt()
                argb
            }
            else -> 0xFFFFFFFF.toInt()
        }
    }

    private fun parsePathData(raw: String): List<PathNode> {
        return PathParser().parsePathString(raw).toNodes()
    }
}
