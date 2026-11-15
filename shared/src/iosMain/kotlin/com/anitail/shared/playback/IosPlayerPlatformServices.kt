package com.anitail.shared.playback

import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.metrolist.innertubex.extraction.ContentHints
import com.anitail.shared.model.SongItem
import com.anitail.shared.ui.settings.IosNativeFiles
import com.anitail.shared.ui.theme.ThemeColorExtractor
import com.anitail.shared.ui.theme.getGradientColors
import com.anitail.shared.utils.artworkColors
import com.anitail.shared.viewmodels.PlayerArtworkColors
import com.anitail.shared.viewmodels.PlayerPlatformServices
import com.anitail.shared.youtube.extraction.models.toContentHints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.UIKit.UIViewController

internal class IosPlayerPlatformServices(
    private val presenter: UIViewController,
) : PlayerPlatformServices {
    override suspend fun extractArtworkColors(
        songId: String,
        thumbnailUrl: String,
    ): PlayerArtworkColors? {
        val context = PlatformContext.INSTANCE
        val request =
            ImageRequest
                .Builder(context)
                .artworkColors(thumbnailUrl)
                .build()
        val bitmap = ((SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image as? BitmapImage)?.bitmap ?: return null
        val seed = withContext(Dispatchers.Default) { ThemeColorExtractor().extractSeedColor(bitmap) }
        return PlayerArtworkColors(dominant = seed, gradient = getGradientColors(seed))
    }

    override fun shareText(text: String) {
        IosNativeFiles.shareText(presenter, text)
    }

    override fun copyText(text: String) {
        platform.UIKit.UIPasteboard.generalPasteboard.string = text
    }

    override suspend fun spotifyCanvas(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        primaryArtist: String?,
    ): String? = null

    override fun musicVideoCanvasHints(song: SongItem): ContentHints = song.toContentHints()
}
