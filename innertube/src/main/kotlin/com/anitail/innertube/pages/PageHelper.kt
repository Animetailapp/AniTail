package com.anitail.innertube.pages

import com.anitail.innertube.models.Menu
import com.anitail.innertube.models.MusicResponsiveListItemRenderer.FlexColumn
import com.anitail.innertube.models.Run

object PageHelper {
    fun extractRuns(columns: List<FlexColumn>, typeLike: String): List<Run> {
        val filteredRuns = mutableListOf<Run>()
        for (column in columns) {
            val runs = column.musicResponsiveListItemFlexColumnRenderer.text?.runs
                ?: continue

            for (run in runs) {
                val typeStr = run.navigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    ?: run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
                    ?: continue

                if (typeLike in typeStr) {
                    filteredRuns.add(run)
                }
            }
        }
        return filteredRuns
    }

    fun extractFeedbackToken(
        menu: Menu.MenuRenderer.Item.ToggleMenuServiceRenderer?,
        type: String
    ): String? {
        if (menu == null) return null
        val defaultToken = menu.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
        val toggledToken = menu.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken

        return if (menu.defaultIcon.iconType == type) {
            defaultToken
        } else {
            toggledToken
        }
    }
}