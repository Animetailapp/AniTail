package com.anitail.music.ui.component

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import com.anitail.music.R

class MusicOrbWidgetProvider : MusicWidgetProvider() {
    override fun widgetComponentClass(): Class<out AppWidgetProvider> = MusicOrbWidgetProvider::class.java

    override fun resolveLayoutRes(appWidgetManager: AppWidgetManager, appWidgetId: Int): Int {
        return R.layout.widget_music_orb
    }
}
