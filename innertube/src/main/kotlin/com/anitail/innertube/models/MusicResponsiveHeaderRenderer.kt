package com.anitail.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class MusicResponsiveHeaderRenderer(
    val thumbnail: ThumbnailRenderer? = null,
    val buttons: List<Button> = emptyList(),
    val title: Runs,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val straplineTextOne: Runs? = null,
    val description: JsonElement? = null,
) {
    fun extractDescriptionText(): String? {
        val obj = description as? JsonObject ?: return null
        fun runsToText(runsObj: JsonObject?): String? =
            runsObj?.get("runs")
                ?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("")
                ?.takeIf { it.isNotBlank() }

        return runsToText(obj)
            ?: runsToText(
                obj["musicDescriptionShelfRenderer"]
                    ?.jsonObject
                    ?.get("description")
                    ?.jsonObject
            )
    }
    @Serializable
    data class Button(
        val musicPlayButtonRenderer: MusicPlayButtonRenderer?,
        val menuRenderer: Menu.MenuRenderer?,
        val toggleButtonRenderer: ToggleButtonRenderer?,
    ) {
        @Serializable
        data class MusicPlayButtonRenderer(
            val playNavigationEndpoint: NavigationEndpoint?,
        )

        @Serializable
        data class ToggleButtonRenderer(
            val defaultIcon: Icon?,
            val defaultServiceEndpoint: DefaultServiceEndpoint?,
            val toggledServiceEndpoint: ToggledServiceEndpoint?,
        )
    }
}
