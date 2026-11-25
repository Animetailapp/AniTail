@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.anitail.music.ui.screens.artist

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.media3.exoplayer.offline.Download
import coil.compose.AsyncImage
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.PodcastItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalDownloadUtil
import com.anitail.music.R
import com.anitail.music.constants.SwipeToSongKey
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.ui.component.AlbumPlayButton
import com.anitail.music.ui.component.PlayingIndicator
import com.anitail.music.ui.component.SwipeToSongBox
import com.anitail.music.ui.utils.tvCombinedClickable
import com.anitail.music.utils.joinByBullet
import com.anitail.music.utils.makeTimeString
import com.anitail.music.utils.rememberPreference
import com.anitail.music.utils.sizedArtworkUrl
import com.anitail.music.utils.toLinkSegments
import com.anitail.music.utils.withoutPlaybackCountLabel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

internal val CarouselItemSize = 115.dp
internal val CarouselItemSpacing = 5.dp
internal val CarouselItemCornerRadius = 12.dp
private val GridCaptionSafeSpace = 8.dp
private const val CURRENT_SONG_ARTWORK_ROTATION_DEG_PER_SEC = 12f
private const val ACTIVE_THUMBNAIL_SCRIM_ALPHA = 0.6f

private object ReducedMotionScaleCache {
    private val registered = AtomicBoolean(false)
    private var reducedMotionState by mutableStateOf(false)

    fun observe(context: Context): Boolean {
        val appContext = context.applicationContext
        if (registered.compareAndSet(false, true)) {
            reducedMotionState = readReducedMotion(appContext)
            try {
                appContext.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                    false,
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            reducedMotionState = readReducedMotion(appContext)
                        }
                    },
                )
            } catch (_: SecurityException) {
            }
        }
        return reducedMotionState
    }

    private fun readReducedMotion(context: Context): Boolean =
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) <= 0f
        }.getOrDefault(false)
}

@Composable
internal fun rememberArtistReducedMotion(): Boolean {
    val context = LocalContext.current
    return ReducedMotionScaleCache.observe(context)
}

private val artistArtworkPolygons = listOf(
    MaterialShapes.Sunny,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Puffy,
    MaterialShapes.Bun,
)

private val ArtistArtworkShapeCache = ConcurrentHashMap<Int, FittedPolygonShape>()
private var CurrentSongCookieShape: Shape? = null
private val CurrentSongMorphCache = ConcurrentHashMap<Int, Morph>()

@Composable
internal fun artistArtworkShape(seed: String): Shape {
    val hash = seed.hashCode() and Int.MAX_VALUE
    val polyIndex = hash % artistArtworkPolygons.size
    val angleBucket = (hash / artistArtworkPolygons.size) % 8
    val cacheKey = polyIndex * 8 + angleBucket
    ArtistArtworkShapeCache[cacheKey]?.let { return it }
    val polygon = artistArtworkPolygons[polyIndex]
    val startAngle = angleBucket * 45
    val sourcePath = polygon.toPath(startAngle = startAngle)
    return remember(cacheKey) {
        ArtistArtworkShapeCache.getOrPut(cacheKey) {
            FittedPolygonShape(Path().apply { addPath(sourcePath) })
        }
    }
}

internal fun carouselArtworkShapeKey(item: YTItem): String? =
    if (item is ArtistItem) item.id else null

@Composable
internal fun carouselArtworkShape(item: YTItem): Shape =
    if (item is ArtistItem) artistArtworkShape(item.id) else RoundedCornerShape(CarouselItemCornerRadius)

internal class FittedPolygonShape(
    private val sourcePath: Path,
) : Shape {
    private val sourceBounds = sourcePath.getBounds()
    private var cachedSize: Size = Size.Unspecified
    private var cachedOutline: Outline? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size == cachedSize) {
            cachedOutline?.let { return it }
        }
        val path = Path().apply { addPath(sourcePath) }
        if (sourceBounds.width > 0f && sourceBounds.height > 0f && size.width > 0f && size.height > 0f) {
            val scale = minOf(size.width / sourceBounds.width, size.height / sourceBounds.height)
            path.translate(-sourceBounds.center)
            path.transform(Matrix().apply { scale(scale, scale) })
            path.translate(Offset(size.width / 2f, size.height / 2f))
        }
        return Outline.Generic(path).also {
            cachedSize = size
            cachedOutline = it
        }
    }
}

@Composable
internal fun currentSongArtworkShape(): Shape {
    CurrentSongCookieShape?.let { return it }
    val sourcePath = MaterialShapes.Cookie9Sided.toPath(startAngle = 0)
    return remember {
        CurrentSongCookieShape ?: FittedPolygonShape(Path().apply { addPath(sourcePath) }).also {
            CurrentSongCookieShape = it
        }
    }
}

@Composable
internal fun animatedCurrentSongArtworkShape(
    isCurrent: Boolean,
    inactiveShape: Shape,
): Shape {
    var everCurrent by remember { mutableStateOf(isCurrent) }
    if (isCurrent && !everCurrent) {
        everCurrent = true
    }
    if (!everCurrent) return inactiveShape

    val reducedMotion = rememberArtistReducedMotion()
    val progress by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "currentSongArtworkShape",
    )
    val currentShape = currentSongArtworkShape()
    return remember(progress, inactiveShape, currentShape) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline {
                if (progress <= 0f || size.minDimension <= 0f) {
                    return inactiveShape.createOutline(size, layoutDirection, density)
                }
                if (progress >= 1f) return currentShape.createOutline(size, layoutDirection, density)
                val inactiveOutline = inactiveShape.createOutline(size, layoutDirection, density)
                val radius = (inactiveOutline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
                val normalizedRounding = (radius * 2f / size.minDimension).coerceIn(0f, 1f)
                val morphKey = (normalizedRounding * 100f).roundToInt()
                val morph = CurrentSongMorphCache.getOrPut(morphKey) {
                    Morph(
                        RoundedPolygon
                            .rectangle(rounding = CornerRounding(morphKey / 100f))
                            .normalized(),
                        MaterialShapes.Cookie9Sided,
                    )
                }
                return Outline.Generic(
                    morph.toPath(progress).apply { transform(Matrix().apply { scale(size.width, size.height) }) },
                )
            }
        }
    }
}

@Composable
internal fun rememberCurrentSongArtworkRotation(
    isCurrent: Boolean,
    isPlaying: Boolean,
): State<Float> {
    val rotation = remember { mutableFloatStateOf(0f) }
    if (!isCurrent) {
        if (rotation.floatValue != 0f) rotation.floatValue = 0f
        return rotation
    }
    val reducedMotion = rememberArtistReducedMotion()
    LaunchedEffect(isPlaying, reducedMotion) {
        if (!isPlaying || reducedMotion) return@LaunchedEffect
        val motionScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (motionScale <= 0f) return@LaunchedEffect
        var lastFrameNanos = -1L
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                if (lastFrameNanos >= 0L) {
                    val dtSeconds = ((frameTimeNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000f) * motionScale
                    rotation.floatValue = (rotation.floatValue + dtSeconds * CURRENT_SONG_ARTWORK_ROTATION_DEG_PER_SEC) % 360f
                }
                lastFrameNanos = frameTimeNanos
            }
        }
    }
    return rotation
}

internal data class CurrentSongColors(
    val container: Color,
    val content: Color,
    val subtitle: Color,
    val link: Color,
)

@Composable
internal fun animateCurrentSongColors(
    isCurrent: Boolean,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    container: Color = Color.Transparent,
    content: Color = colorScheme.onSurface,
    subtitle: Color = colorScheme.onSurfaceVariant,
): CurrentSongColors {
    var everCurrent by remember { mutableStateOf(isCurrent) }
    if (isCurrent && !everCurrent) {
        everCurrent = true
    }
    if (!everCurrent) {
        return CurrentSongColors(
            container = container,
            content = content,
            subtitle = subtitle,
            link = colorScheme.primary,
        )
    }
    val progress by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "currentSongColors",
    )
    return CurrentSongColors(
        container = lerp(container, colorScheme.primaryContainer, progress),
        content = lerp(content, colorScheme.onPrimaryContainer, progress),
        subtitle = lerp(subtitle, colorScheme.onPrimaryContainer, progress),
        link = lerp(colorScheme.primary, colorScheme.onPrimaryContainer, progress),
    )
}

internal fun Modifier.currentSongSemantics(
    isCurrent: Boolean,
    isPlaying: Boolean,
    playingLabel: String,
    pausedLabel: String,
): Modifier {
    if (!isCurrent) return this
    return semantics { stateDescription = if (isPlaying) playingLabel else pausedLabel }
}

@Composable
internal fun ArtistSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    seeAllLabel: String = stringResource(R.string.see_all),
    showSeeAllLabel: Boolean = false,
    onClick: (() -> Unit)? = null,
    isWide: Boolean = false,
) {
    val titleStyle = if (isWide) {
        MaterialTheme.typography.headlineMediumEmphasized
    } else {
        MaterialTheme.typography.headlineSmallEmphasized
    }
    val rowHeight = if (isWide) 60.dp else 50.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = seeAllLabel,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = titleStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )

        if (onClick != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showSeeAllLabel) {
                    Text(
                        text = seeAllLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SectionActionButton(
                    icon = Icons.Outlined.ChevronRight,
                    contentDescription = seeAllLabel,
                    isWide = isWide,
                )
            }
        }
    }
}

@Composable
private fun SectionActionButton(
    icon: ImageVector,
    contentDescription: String?,
    isWide: Boolean = false,
) {
    val boxSize = if (isWide) 56.dp else 48.dp
    Box(
        modifier = Modifier.size(boxSize),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(40.dp, 32.dp),
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun SubscribeConfetti(
    successCount: Int,
    modifier: Modifier = Modifier,
) {
    val initialCount = remember { successCount }
    val progress = remember { Animatable(1f) }
    val velocities = remember { List(32) { Offset(Random.nextFloat() * 2f - 1f, -0.5f - Random.nextFloat()) } }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )
    LaunchedEffect(successCount) {
        if (successCount > initialCount && (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1000, easing = LinearEasing))
        }
    }
    Canvas(modifier) {
        val time = progress.value
        if (time >= 1f) return@Canvas
        velocities.forEachIndexed { index, velocity ->
            val position = center + Offset(
                velocity.x * 100.dp.toPx() * time,
                (velocity.y * time + time * time) * 120.dp.toPx(),
            )
            rotate(index * 23f + time * 360f, position) {
                drawRect(
                    color = colors[index % colors.size],
                    topLeft = position,
                    size = Size(4.dp.toPx(), 7.dp.toPx()),
                    alpha = 1f - time,
                )
            }
        }
    }
}

@Composable
internal fun ArtistExpandableText(
    text: String = "",
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    initiallyExpanded: Boolean = false,
    showMoreText: String = stringResource(R.string.show_more),
    showLessText: String = stringResource(R.string.show_less),
    linkColor: Color = MaterialTheme.colorScheme.primary,
    bodyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onExpansionFinished: (() -> Unit)? = null,
) {
    var isExpanded by remember(text, initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    var hasOverflow by remember(text) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val toggleExpanded: () -> Unit = {
        isExpanded = !isExpanded
        onExpandedChange?.invoke(isExpanded)
    }

    val runs = remember(text) { text.toLinkSegments() }
    val annotatedText: AnnotatedString = remember(text, runs, linkColor) {
        if (runs.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                runs.forEach { segment ->
                    if (segment.url != null) {
                        val link = LinkAnnotation.Clickable(
                            tag = segment.url,
                            linkInteractionListener = {
                                runCatching { uriHandler.openUri(segment.url) }
                            },
                        )
                        withLink(link) {
                            withStyle(SpanStyle(color = linkColor)) {
                                append(segment.text)
                            }
                        }
                    } else {
                        append(segment.text)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = hasOverflow,
                onClickLabel = if (isExpanded) showLessText else showMoreText,
                onClick = toggleExpanded,
            )
            .then(modifier)
            .animateContentSize(finishedListener = { _, _ -> if (isExpanded) onExpansionFinished?.invoke() }),
    ) {
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium.copy(color = bodyColor),
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutResult ->
                if (!isExpanded) {
                    hasOverflow = layoutResult.hasVisualOverflow || layoutResult.lineCount > collapsedMaxLines
                }
            },
        )

        if (hasOverflow || isExpanded) {
            Text(
                text = if (isExpanded) showLessText else showMoreText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun ArtistExplicitBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(16.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "E",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
internal fun ArtistBlurredArtworkBackground(
    url: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 48.dp,
    scrimAlpha: Float = 0.25f,
    colorFilter: ColorFilter? = null,
    verticalBias: Float = 0f,
) {
    if (url.isNullOrBlank()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer))
        return
    }
    Box(modifier) {
        val imageModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier
                .fillMaxSize()
                .blur(blurRadius)
        } else {
            Modifier.fillMaxSize()
        }
        AsyncImage(
            model = sizedArtworkUrl(url, 544),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment { size, space, _ ->
                val x = ((space.width - size.width) / 2f).toInt()
                val y = ((space.height - size.height) * ((verticalBias + 1f) / 2f)).toInt()
                androidx.compose.ui.unit.IntOffset(x, y)
            },
            colorFilter = colorFilter,
            modifier = imageModifier,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) scrimAlpha else scrimAlpha + 0.25f)),
        )
    }
}

@Composable
internal fun ArtistAboutPhoto(
    url: String,
    gradient: () -> Float,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        ArtistBlurredArtworkBackground(
            url = url,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 0.55f },
            blurRadius = 48.dp,
            scrimAlpha = 0.2f,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.3f) }),
        )
        AsyncImage(
            model = sizedArtworkUrl(url, 1200),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val strength = gradient()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.35f to Color.Black,
                            0.7f to Color.Black.copy(alpha = androidx.compose.ui.util.lerp(1f, 0.35f, strength)),
                            1f to Color.Black.copy(alpha = 1f - strength),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .graphicsLayer { alpha = gradient() }
                .background(
                    Brush.verticalGradient(
                        0f to surface.copy(alpha = 0f),
                        0.45f to surface.copy(alpha = 0.72f),
                        1f to surface.copy(alpha = 0.92f),
                    ),
                ),
        )
    }
}

internal data class CarouselCaptionLayout(
    val left: Int,
    val width: Int,
    val alpha: Float,
)

internal fun carouselCaptionLayout(
    mask: Rect,
    itemWidth: Int,
): CarouselCaptionLayout {
    val left = ceil(mask.left).toInt().coerceIn(0, itemWidth)
    val right = floor(mask.right).toInt().coerceIn(left, itemWidth)
    val visibleWidth = right - left
    val minimumWidth = itemWidth / 2
    val alpha = if (itemWidth > 0) ((visibleWidth.toFloat() / itemWidth - 0.5f) * 2f).coerceIn(0f, 1f) else 0f
    return CarouselCaptionLayout(left, maxOf(visibleWidth, minimumWidth), alpha)
}

internal fun Modifier.carouselCaption(info: CarouselItemDrawInfo?): Modifier =
    if (info == null) {
        this
    } else {
        layout { measurable, constraints ->
            val caption = carouselCaptionLayout(info.maskRect, constraints.maxWidth)
            val placeable = measurable.measure(constraints.copy(minWidth = caption.width, maxWidth = caption.width))
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeWithLayer(caption.left, 0) { alpha = caption.alpha }
            }
        }
    }

@Composable
internal fun BoxScope.ArtistSongThumbnailOverlay(
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = ACTIVE_THUMBNAIL_SCRIM_ALPHA)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isPlaying) {
                PlayingIndicator(
                    color = Color.White,
                    modifier = Modifier.height(22.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
internal fun ArtistItemStatusBadges(
    item: YTItem,
    showExplicit: Boolean = true,
) {
    val database = LocalDatabase.current
    val song by remember(item) {
        if (item is SongItem || item is EpisodeItem) database.song(item.id) else flowOf(null)
    }.collectAsState(initial = null)
    val album by remember(item) {
        if (item is AlbumItem) database.album(item.browseId) else flowOf(null)
    }.collectAsState(initial = null)

    val isLiked = when (item) {
        is EpisodeItem -> song?.song?.inLibrary != null
        is SongItem -> song?.song?.liked == true
        is AlbumItem -> album?.album?.bookmarkedAt != null
        else -> false
    }
    val isInLibrary = (item is SongItem || item is EpisodeItem) && song?.song?.inLibrary != null
    val isDownloaded = if (item is SongItem || item is EpisodeItem) {
        val download by LocalDownloadUtil.current.getDownload(item.id).collectAsState(initial = null)
        download?.state == Download.STATE_COMPLETED ||
            (song?.song?.isLocal != true && !song?.song?.mediaStoreUri.isNullOrEmpty())
    } else {
        false
    }
    val hasExplicit = showExplicit && item.explicit

    if (!isLiked && !isInLibrary && !isDownloaded && !hasExplicit) return

    Row(
        modifier = Modifier.padding(end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLiked) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(13.dp),
            )
        }
        if (isInLibrary) {
            Icon(
                painter = painterResource(R.drawable.library_add_check),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(12.dp),
            )
        }
        if (isDownloaded) {
            Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(12.dp),
            )
        }
        if (hasExplicit) {
            ArtistExplicitBadge()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistCarouselCard(
    item: YTItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageSize: Dp = CarouselItemSize,
    artworkAspectRatio: Float? = null,
    artworkModifier: Modifier = Modifier,
    carouselItemDrawInfo: CarouselItemDrawInfo? = null,
    subtitleOverride: String? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    largeText: Boolean = false,
    onAlbumPlay: (() -> Unit)? = null,
) {
    val subtitleStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = if (largeText) 12.sp else 10.sp,
        lineHeight = if (largeText) 16.sp else 12.sp,
        fontWeight = FontWeight.Medium,
    )
    val shape = when (item) {
        is ArtistItem -> artistArtworkShape(item.id)
        is PodcastItem -> CircleShape
        else -> RoundedCornerShape(CarouselItemCornerRadius)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CarouselItemCornerRadius))
            .tvCombinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(artworkAspectRatio ?: 1f)
                .then(artworkModifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .then(if (item !is ArtistItem || carouselItemDrawInfo == null) Modifier.clip(shape) else Modifier)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val thumbUrl = item.thumbnail
                if (thumbUrl != null) {
                    AsyncImage(
                        model = sizedArtworkUrl(thumbUrl, 544),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = when (item) {
                            is ArtistItem -> Icons.Outlined.AccountCircle
                            is PlaylistItem, is PodcastItem -> Icons.Outlined.LibraryMusic
                            else -> Icons.Outlined.MusicNote
                        },
                        contentDescription = null,
                        modifier = Modifier.size(imageSize / 2),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (item is SongItem || item is EpisodeItem) {
                    ArtistSongThumbnailOverlay(
                        isActive = isCurrent,
                        isPlaying = isPlaying,
                        shape = shape,
                    )
                }
                if (item is AlbumItem && onAlbumPlay != null) {
                    AlbumPlayButton(
                        visible = !isCurrent,
                        onClick = onAlbumPlay,
                    )
                }
            }
        }

        Column(Modifier.carouselCaption(carouselItemDrawInfo)) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = if (largeText) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            val subtitle = subtitleOverride ?: when (item) {
                is SongItem -> item.artists.joinToString { it.name }
                is AlbumItem -> {
                    val artistNames = item.artists?.joinToString { it.name }.orEmpty()
                    artistNames.ifEmpty { item.year?.toString().orEmpty() }
                }
                is ArtistItem -> ""
                is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText).joinToString(" • ")
                is PodcastItem -> listOfNotNull(item.author?.name, item.episodeCountText).joinToString(" • ")
                is EpisodeItem -> listOfNotNull(item.author?.name, item.publishDateText).joinToString(" • ")
            }

            if (subtitle.isNotEmpty() || item.explicit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (item is ArtistItem) Arrangement.Center else Arrangement.Start,
                ) {
                    ArtistItemStatusBadges(item)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = subtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(GridCaptionSafeSpace))
        }
    }
}

@Composable
internal fun ArtistVideoShelf(
    items: List<YTItem>,
    onItemClick: (YTItem) -> Unit,
    onItemLongClick: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = if (isWide) 24.dp else 16.dp),
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    state: CarouselState = rememberCarouselState { items.size },
) {
    val carouselFling = CarouselDefaults.multiBrowseFlingBehavior(state)
    val itemWidth = if (isWide) 320.dp else 280.dp
    HorizontalMultiBrowseCarousel(
        state = state,
        flingBehavior = carouselFling,
        preferredItemWidth = itemWidth,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .clip(RoundedCornerShape(CarouselItemCornerRadius)),
    ) { index ->
        val item = items[index]
        ArtistCarouselCard(
            item = item,
            onClick = { onItemClick(item) },
            onLongClick = { onItemLongClick(item) },
            imageSize = itemWidth,
            artworkAspectRatio = 16f / 9f,
            artworkModifier = Modifier.maskClip(RoundedCornerShape(CarouselItemCornerRadius)),
            carouselItemDrawInfo = carouselItemDrawInfo,
            isCurrent = item.id == currentSongId,
            isPlaying = isPlaying && item.id == currentSongId,
            largeText = isWide,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistSongRow(
    item: YTItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)
    val songItem = when (item) {
        is SongItem -> item
        is EpisodeItem -> item.asSongItem()
        else -> null
    }

    val colors = animateCurrentSongColors(isCurrent)
    val contentColor = colors.content
    val subtitleColor = colors.subtitle
    val shape = animatedCurrentSongArtworkShape(isCurrent, RoundedCornerShape(4.dp))
    val artworkRotationState = rememberCurrentSongArtworkRotation(isCurrent, isPlaying)

    val subtitleText = when (item) {
        is SongItem -> joinByBullet(
            item.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
            item.duration?.let { makeTimeString(it * 1000L) } ?: item.playCountText,
        )
        is EpisodeItem -> joinByBullet(
            item.author?.name,
            item.publishDateText,
            item.duration?.let { makeTimeString(it * 1000L) },
        )
        else -> null
    }

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(if (isCurrent) CircleShape else RoundedCornerShape(8.dp))
                .background(colors.container)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(if (isCurrent) Modifier.graphicsLayer { rotationZ = artworkRotationState.value } else Modifier)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isCurrent) Modifier.graphicsLayer { rotationZ = -artworkRotationState.value } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    item.thumbnail?.let { url ->
                        AsyncImage(
                            model = sizedArtworkUrl(url, 544),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (isCurrent) {
                        ArtistSongThumbnailOverlay(
                            isActive = true,
                            isPlaying = isPlaying,
                            shape = shape,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtistItemStatusBadges(item)
                    if (!subtitleText.isNullOrBlank()) {
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            IconButton(
                onClick = onShowMenu,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = if (isCurrent) contentColor else LocalContentColor.current,
                )
            }
        }
    }

    if (songItem != null && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = songItem.toMediaItem(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            rowContent()
        }
    } else {
        rowContent()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistWideSongRow(
    song: SongItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLikeClick: (() -> Unit)?,
    onMenuClick: () -> Unit,
) {
    val database = LocalDatabase.current
    val dbSong by database.song(song.id).collectAsState(initial = null)
    val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
    val isDownloaded = download?.state == Download.STATE_COMPLETED ||
        (dbSong?.song?.isLocal != true && !dbSong?.song?.mediaStoreUri.isNullOrEmpty())

    val colors = animateCurrentSongColors(isCurrent)
    val contentColor = colors.content
    val subtitleColor = colors.subtitle
    val artworkShape = animatedCurrentSongArtworkShape(isCurrent, RoundedCornerShape(4.dp))
    val artworkRotationState = rememberCurrentSongArtworkRotation(isCurrent, isPlaying)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(if (isCurrent) CircleShape else RoundedCornerShape(8.dp))
            .background(colors.container)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .then(if (isCurrent) Modifier.graphicsLayer { rotationZ = artworkRotationState.value } else Modifier)
                .clip(artworkShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isCurrent) Modifier.graphicsLayer { rotationZ = -artworkRotationState.value } else Modifier),
            ) {
                AsyncImage(
                    model = sizedArtworkUrl(song.thumbnail, 544),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = song.title,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.explicit) ArtistExplicitBadge()
            if (isDownloaded) {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(start = 4.dp).size(12.dp),
                )
            }
        }
        Text(
            text = listOfNotNull(
                song.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
                song.duration?.let { makeTimeString(it * 1000L) },
            ).joinToString(" • "),
            modifier = Modifier.weight(0.7f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ArtistFittingText(
            text = song.playCountText.orEmpty(),
            compactText = song.playCountText?.withoutPlaybackCountLabel().orEmpty(),
            modifier = Modifier.weight(0.4f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor,
        )
        Text(
            text = song.album?.name.orEmpty(),
            modifier = Modifier.weight(0.8f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onLikeClick != null) {
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.size(48.dp),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = when {
                        isCurrent -> contentColor
                        isLiked -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(48.dp),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.artist_actions),
                tint = subtitleColor,
            )
        }
    }
}

@Composable
private fun ArtistFittingText(
    text: String,
    compactText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (text.isBlank()) {
        Spacer(modifier)
        return
    }
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val fullWidth = textMeasurer.measure(text, style = style, maxLines = 1).size.width
        val chosen = if (fullWidth <= constraints.maxWidth || compactText.isBlank()) text else compactText
        Text(
            text = chosen,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

