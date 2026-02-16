package com.anitail.music.ui.screens.settings

import android.content.Context
import androidx.annotation.StringRes
import com.anitail.music.R

object SettingsSearchOptions {
    private val providers: Map<String, (Context) -> List<SettingOptionData>> = mapOf(
        "appearance" to ::appearance,
        "account" to ::account,
        "lastfm" to ::lastfm,
        "content" to ::content,
        "player" to ::player,
        "jam" to ::jam,
        "privacy" to ::privacy,
        "storage" to ::storage,
        "backup" to ::backup,
        "update" to ::update,
        "about" to ::about,
    )

    fun forItem(id: String, context: Context): List<SettingOptionData> =
        providers[id]?.invoke(context).orEmpty()

    private fun appearance(context: Context): List<SettingOptionData> = listOf(
        option(
            context,
            "dynamic_icon",
            R.string.enable_dynamic_icon,
            R.string.enable_dynamic_icon_desc,
            sectionRes = R.string.theme
        ),
        option(
            context,
            "high_refresh_rate",
            R.string.enable_high_refresh_rate,
            R.string.enable_high_refresh_rate_desc,
            sectionRes = R.string.theme
        ),
        option(
            context,
            "theme_and_colors",
            R.string.theme,
            R.string.customize_app_theme,
            sectionRes = R.string.theme
        ),
        option(
            context,
            "palette_customization",
            R.string.palette_customization,
            R.string.design_your_own_palette,
            sectionRes = R.string.theme
        ),
        option(context, "dark_theme", R.string.dark_theme, sectionRes = R.string.theme),
        option(
            context,
            "display_density",
            R.string.display_density_title,
            R.string.display_density_summary,
            sectionRes = R.string.theme
        ),
        option(
            context,
            "new_player_design",
            R.string.new_player_design,
            sectionRes = R.string.player
        ),
        option(
            context,
            "new_mini_player_design",
            R.string.new_mini_player_design,
            sectionRes = R.string.player
        ),
        option(
            context,
            "player_background_style",
            R.string.player_background_style,
            sectionRes = R.string.player
        ),
        option(
            context,
            "player_buttons_style",
            R.string.player_buttons_style,
            sectionRes = R.string.player
        ),
        option(
            context,
            "player_slider_style",
            R.string.player_slider_style,
            sectionRes = R.string.player
        ),
        option(
            context,
            "enable_swipe_thumbnail",
            R.string.enable_swipe_thumbnail,
            sectionRes = R.string.player
        ),
        option(
            context,
            "swipe_sensitivity",
            R.string.swipe_sensitivity,
            sectionRes = R.string.player
        ),
        option(
            context,
            "lyrics_text_position",
            R.string.lyrics_text_position,
            sectionRes = R.string.lyrics
        ),
        option(context, "lyrics_click", R.string.lyrics_click_change, sectionRes = R.string.lyrics),
        option(context, "lyrics_scroll", R.string.lyrics_auto_scroll, sectionRes = R.string.lyrics),
        option(
            context,
            "smooth_lyrics_animation",
            R.string.smooth_lyrics_animation,
            sectionRes = R.string.lyrics
        ),
        option(
            context,
            "lyrics_animation_style",
            R.string.lyrics_animation_style,
            sectionRes = R.string.lyrics
        ),
        option(
            context,
            "translation_models",
            R.string.translation_models,
            sectionRes = R.string.lyrics
        ),
        option(
            context,
            "clear_translation_models",
            R.string.clear_translation_models,
            sectionRes = R.string.lyrics
        ),
        option(
            context,
            "lyrics_font_size",
            R.string.lyrics_font_size,
            sectionRes = R.string.lyrics
        ),
        option(
            context,
            "lyrics_custom_font",
            R.string.lyrics_custom_font,
            sectionRes = R.string.lyrics
        ),
        option(context, "default_open_tab", R.string.default_open_tab, sectionRes = R.string.misc),
        option(
            context,
            "default_lib_chips",
            R.string.default_lib_chips,
            sectionRes = R.string.misc
        ),
        option(
            context,
            "swipe_song_to_add",
            R.string.swipe_song_to_add,
            sectionRes = R.string.misc
        ),
        option(context, "slim_navbar", R.string.slim_navbar, sectionRes = R.string.misc),
        option(context, "grid_cell_size", R.string.grid_cell_size, sectionRes = R.string.misc),
        option(
            context,
            "show_liked_playlist",
            R.string.show_liked_playlist,
            sectionRes = R.string.auto_playlists
        ),
        option(
            context,
            "show_downloaded_playlist",
            R.string.show_downloaded_playlist,
            sectionRes = R.string.auto_playlists
        ),
        option(
            context,
            "show_top_playlist",
            R.string.show_top_playlist,
            sectionRes = R.string.auto_playlists
        ),
        option(
            context,
            "show_cached_playlist",
            R.string.show_cached_playlist,
            sectionRes = R.string.auto_playlists
        )
    )

    private fun account(context: Context): List<SettingOptionData> = listOf(
        option(context, "login", R.string.login),
        option(context, "logout", R.string.logout),
        option(context, "advanced_login", R.string.advanced_login),
        option(context, "token_hidden", R.string.token_hidden),
        option(context, "token_shown", R.string.token_shown),
        option(
            context,
            "use_login_for_browse",
            R.string.use_login_for_browse,
            R.string.use_login_for_browse_desc
        ),
        option(context, "ytm_sync", R.string.ytm_sync),
        option(context, "import_from_spotify", R.string.import_from_spotify),
        option(context, "discord_integration", R.string.discord_integration),
        option(context, "avatar_source", R.string.avatar_source)
    )

    private fun lastfm(context: Context): List<SettingOptionData> = listOf(
        option(
            context,
            "enable_scrobbling",
            R.string.enable_scrobbling,
            R.string.enable_scrobbling_description
        ),
        option(context, "love_tracks", R.string.love_tracks, R.string.love_tracks_description),
        option(context, "retry_pending_scrobbles", R.string.retry_pending_scrobbles),
        option(context, "clear_pending_scrobbles", R.string.clear_pending_scrobbles)
    )

    private fun content(context: Context): List<SettingOptionData> = listOf(
        option(context, "content_language", R.string.content_language),
        option(context, "content_country", R.string.content_country),
        option(context, "app_language", R.string.app_language),
        option(context, "enable_proxy", R.string.enable_proxy),
        option(context, "config_proxy", R.string.config_proxy),
        option(context, "enable_betterlyrics", R.string.enable_betterlyrics),
        option(context, "enable_simpmusic", R.string.enable_simpmusic),
        option(context, "enable_lrclib", R.string.enable_lrclib),
        option(context, "enable_kugou", R.string.enable_kugou),
        option(context, "set_first_lyrics_provider", R.string.set_first_lyrics_provider),
        option(context, "lyrics_romanization", R.string.lyrics_romanization),
        option(context, "top_length", R.string.top_length),
        option(context, "set_quick_picks", R.string.set_quick_picks)
    )

    private fun player(context: Context): List<SettingOptionData> = listOf(
        option(context, "audio_quality", R.string.audio_quality),
        option(context, "history_duration", R.string.history_duration),
        option(context, "skip_silence", R.string.skip_silence),
        option(context, "audio_normalization", R.string.audio_normalization),
        option(context, "audio_offload", R.string.audio_offload),
        option(context, "persistent_queue", R.string.persistent_queue),
        option(context, "auto_load_more", R.string.auto_load_more),
        option(context, "enable_similar_content", R.string.enable_similar_content),
        option(context, "auto_skip_next_on_error", R.string.auto_skip_next_on_error),
        option(context, "auto_download_on_like", R.string.auto_download_on_like),
        option(context, "auto_download_lyrics", R.string.auto_download_lyrics),
        option(context, "stop_music_on_task_clear", R.string.stop_music_on_task_clear),
        option(context, "enable_cast", R.string.enable_cast),
        option(context, "notification_button_type", R.string.notification_button_type)
    )

    private fun jam(context: Context): List<SettingOptionData> = listOf(
        option(context, "jam_settings_subtitle", R.string.jam_settings_subtitle),
        option(context, "jam_music_in_sync", R.string.jam_music_in_sync),
        option(context, "jam_online", R.string.jam_online),
        option(context, "jam_offline", R.string.jam_offline)
    )

    private fun privacy(context: Context): List<SettingOptionData> = listOf(
        option(context, "pause_listen_history", R.string.pause_listen_history),
        option(context, "clear_listen_history", R.string.clear_listen_history),
        option(context, "pause_search_history", R.string.pause_search_history),
        option(context, "clear_search_history", R.string.clear_search_history),
        option(
            context,
            "disable_screenshot",
            R.string.disable_screenshot,
            R.string.disable_screenshot_desc
        )
    )

    private fun storage(context: Context): List<SettingOptionData> = listOf(
        option(context, "downloaded_songs", R.string.downloaded_songs),
        option(context, "song_cache", R.string.song_cache),
        option(context, "image_cache", R.string.image_cache),
        option(context, "max_cache_size", R.string.max_cache_size),
        option(context, "clear_all_downloads", R.string.clear_all_downloads),
        option(context, "clear_song_cache", R.string.clear_song_cache),
        option(context, "clear_image_cache", R.string.clear_image_cache)
    )

    private fun backup(context: Context): List<SettingOptionData> = listOf(
        option(
            context,
            "auto_backup_settings",
            R.string.auto_backup_settings,
            R.string.auto_backup_settings_desc
        ),
        option(
            context,
            "enable_backup_upload",
            R.string.enable_backup_upload,
            R.string.enable_backup_upload_desc
        ),
        option(context, "backup", R.string.backup, R.string.backup_description),
        option(context, "restore", R.string.restore, R.string.restore_description),
        option(
            context,
            "import_online",
            R.string.import_online,
            R.string.import_online_description
        ),
        option(context, "import_csv", R.string.import_csv, R.string.import_csv_description)
    )

    private fun update(context: Context): List<SettingOptionData> = listOf(
        option(context, "check_for_updates_now", R.string.check_for_updates_now),
        option(
            context,
            "auto_update_enabled",
            R.string.auto_update_enabled,
            R.string.auto_update_enabled_description
        ),
        option(context, "update_check_frequency", R.string.update_check_frequency),
        option(context, "new_version_available", R.string.new_version_available)
    )

    private fun about(context: Context): List<SettingOptionData> = listOf(
        option(context, "links_about", R.string.links_about),
        option(context, "my_channel", R.string.my_channel, R.string.my_channel_info),
        option(context, "other_apps", R.string.other_apps, R.string.other_apps_info),
        option(context, "patreon", R.string.patreon, R.string.patreon_info),
        option(context, "beta_testers", R.string.beta_testers)
    )

    private fun option(
        context: Context,
        id: String,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int? = null,
        @StringRes sectionRes: Int? = null
    ): SettingOptionData = SettingOptionData(
        id = id,
        title = context.getString(titleRes),
        subtitle = subtitleRes?.let(context::getString),
        section = sectionRes?.let(context::getString)
    )
}
