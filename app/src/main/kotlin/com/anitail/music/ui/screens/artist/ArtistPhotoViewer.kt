@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)

package com.anitail.music.ui.screens.artist

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Size
import com.anitail.music.R
import com.anitail.music.ui.component.shimmer.ShimmerHost
import com.anitail.music.utils.ArtistPhoto
import com.anitail.music.utils.LastFmPhotoCache
import com.anitail.music.utils.LastFmPhotoDetails
import com.anitail.music.utils.formatPhotoUploadDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
internal const val GALLERY_RATIO = 3f / 4f
internal const val ARTIST_PHOTO_REQUEST_PX = 1080

private val OnScrim = Color.White

internal fun artistPhotoSharedKey(url: String) = "artist-photo-$url"

@Composable
internal fun rememberArtistPhotoBoundsTransform(): BoundsTransform {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return remember(spec) { BoundsTransform { _, _ -> spec } }
}

@Composable
private fun <T> viewerSpec() = MaterialTheme.motionScheme.defaultSpatialSpec<T>()

internal fun artistPhotoRequest(
    context: Context,
    url: String,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(url)
        .size(ARTIST_PHOTO_REQUEST_PX, ARTIST_PHOTO_REQUEST_PX)
        .memoryCacheKey("${url}@artist_photo_preview_${ARTIST_PHOTO_REQUEST_PX}")
        .precision(Precision.INEXACT)
        .crossfade(true)
        .build()

@Composable
internal fun ArtistPhotoImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val request = remember(url, context) { artistPhotoRequest(context, url) }
    val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Crop)
    val state = painter.state
    Box(modifier) {
        if (state !is AsyncImagePainter.State.Success) {
            if (state !is AsyncImagePainter.State.Error) {
                ShimmerHost {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            alignment = alignment,
            contentScale = ContentScale.Crop,
        )
    }
}

internal class ArtistPhotoActions(
    val share: (String) -> Unit,
    val save: suspend (ArtistPhoto) -> Boolean,
    val showMessage: (String) -> Unit,
)

internal data class ArtistPhotoPreview(
    val aspectRatio: Float,
    val memoryCacheKey: MemoryCache.Key?,
)

internal suspend fun artistPhotoPreview(
    imageLoader: ImageLoader,
    context: Context,
    url: String,
): ArtistPhotoPreview? {
    val result = imageLoader.execute(artistPhotoRequest(context, url)) as? SuccessResult ?: return null
    val drawable = result.drawable
    val width = drawable.intrinsicWidth
    val height = drawable.intrinsicHeight
    if (width <= 0 || height <= 0) return null
    return ArtistPhotoPreview(width.toFloat() / height.toFloat(), result.memoryCacheKey)
}

@Composable
internal fun SharedTransitionScope.ArtistPhotoViewer(
    photos: List<ArtistPhoto>,
    initialIndex: Int,
    initialPreview: ArtistPhotoPreview?,
    artistName: String,
    actions: ArtistPhotoActions,
    visibilityScope: AnimatedVisibilityScope,
    onPageChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val photoCache = remember(context) { LastFmPhotoCache.getInstance(context) }
    val pagerState = key(photos) {
        rememberPagerState(initialIndex.coerceIn(0, photos.lastIndex.coerceAtLeast(0))) { photos.size }
    }
    val previewPhotoUrl = remember { photos.getOrNull(initialIndex)?.url }
    var zoomed by remember(photos) { mutableStateOf(false) }
    val scrimAlpha by visibilityScope.transition.animateFloat(
        transitionSpec = { viewerSpec() },
        label = "photoScrim",
    ) { state ->
        if (state == EnterExitState.Visible) 1f else 0f
    }
    val photo = photos.getOrNull(pagerState.currentPage) ?: return
    var detailsVersion by remember { mutableIntStateOf(0) }
    var loadedPage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(photo.pageUrl, pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        val pageUrl = photo.pageUrl ?: return@LaunchedEffect
        loadedPage = null
        photoCache.get(pageUrl)
        loadedPage = pageUrl
        detailsVersion++
        delay(750)
        for (index in listOf(pagerState.currentPage + 1, pagerState.currentPage - 1)) {
            val neighborUrl = photos.getOrNull(index)?.pageUrl ?: continue
            photoCache.get(neighborUrl, prefetch = true)
            detailsVersion++
        }
    }

    val settled = visibilityScope.transition.currentState == EnterExitState.Visible &&
        visibilityScope.transition.targetState == EnterExitState.Visible
    LaunchedEffect(photos, pagerState.currentPage) { onPageChange(pagerState.currentPage) }

    val scope = rememberCoroutineScope()
    val savingPhotos = remember { mutableStateMapOf<String, Boolean>() }
    val savedText = stringResource(R.string.saved)
    val errorText = stringResource(R.string.error_occurred)

    fun save(photoToSave: ArtistPhoto) {
        if (savingPhotos[photoToSave.url] == true) return
        savingPhotos[photoToSave.url] = true
        scope.launch {
            val message = try {
                savedText.takeIf { actions.save(photoToSave) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                errorText
            } finally {
                savingPhotos.remove(photoToSave.url)
            }
            message?.let(actions.showMessage)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val detailsMaxHeight = maxHeight * 0.45f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(MaterialTheme.colorScheme.scrim),
        )
        Column(Modifier.fillMaxSize()) {
            ViewerTopBar(
                photo = photo,
                position = pagerState.currentPage + 1,
                count = photos.size,
                onDismiss = onDismiss,
                modifier = with(visibilityScope) {
                    Modifier.animateEnterExit(
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    )
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(photos) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !zoomed && settled,
                        pageSpacing = 16.dp,
                        key = { photos.getOrNull(it)?.url ?: it },
                    ) { page ->
                        val pagePhoto = photos.getOrNull(page) ?: return@HorizontalPager
                        ViewerPhoto(
                            photo = pagePhoto,
                            initialPreview = if (pagePhoto.url == previewPhotoUrl) initialPreview else null,
                            contentDescription = stringResource(R.string.artist_photo_content_description, artistName),
                            isCurrent = page == pagerState.currentPage,
                            sharedModifier = if (page == pagerState.currentPage) {
                                Modifier.sharedElement(
                                    rememberSharedContentState(artistPhotoSharedKey(pagePhoto.url)),
                                    animatedVisibilityScope = visibilityScope,
                                    boundsTransform = rememberArtistPhotoBoundsTransform(),
                                )
                            } else {
                                Modifier
                            },
                            onZoomChange = { if (page == pagerState.currentPage) zoomed = it },
                        )
                    }
                }
            }
            Crossfade(
                targetState = photo,
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "photoDetails",
                modifier = with(visibilityScope) {
                    Modifier
                        .heightIn(max = detailsMaxHeight)
                        .animateContentSize(viewerSpec())
                        .animateEnterExit(
                            enter = slideInVertically(viewerSpec()) { it } + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                            exit = slideOutVertically(viewerSpec()) { it } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        )
                },
            ) { displayedPhoto ->
                val details = remember(displayedPhoto, detailsVersion) {
                    displayedPhoto.pageUrl?.let(photoCache::peek)
                }
                PhotoDetailsSheet(
                    photo = displayedPhoto,
                    details = details,
                    loading = displayedPhoto.pageUrl != null && details == null && loadedPage != displayedPhoto.pageUrl,
                    saving = savingPhotos[displayedPhoto.url] == true,
                    onSave = { save(displayedPhoto) },
                    artistName = artistName,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun ViewerTopBar(
    photo: ArtistPhoto,
    position: Int,
    count: Int,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        FilledTonalIconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterStart),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(Icons.Outlined.Close, stringResource(R.string.close))
        }
        if (count > 1) {
            Text(
                text = stringResource(R.string.artist_photo_position, position, count),
                style = MaterialTheme.typography.titleMedium,
                color = OnScrim,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        FilledTonalIconButton(
            onClick = { runCatching { uriHandler.openUri(photo.url) } },
            modifier = Modifier.align(Alignment.CenterEnd),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(Icons.Outlined.Image, stringResource(R.string.artist_photo_open_original))
        }
    }
}

@Composable
private fun ViewerPhoto(
    photo: ArtistPhoto,
    initialPreview: ArtistPhotoPreview?,
    contentDescription: String,
    isCurrent: Boolean,
    sharedModifier: Modifier,
    onZoomChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val preview by produceState(initialPreview, photo.url) {
        if (value == null) value = artistPhotoPreview(imageLoader, context, photo.url)
    }
    val request = remember(photo.url, preview?.memoryCacheKey, context) {
        ImageRequest
            .Builder(context)
            .data(photo.url)
            .size(Size.ORIGINAL)
            .apply {
                preview?.memoryCacheKey?.let(::placeholderMemoryCacheKey)
            }
            .crossfade(false)
            .build()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun applyScale(value: Float) {
        scale = value
        if (value == 1f) offset = Offset.Zero
        onZoomChange(value > 1f)
    }
    LaunchedEffect(isCurrent) { if (!isCurrent) applyScale(1f) }

    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        applyScale((scale * zoomChange).coerceIn(1f, MAX_ZOOM))
        if (scale > 1f) offset += panChange
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { applyScale(if (scale > 1f) 1f else DOUBLE_TAP_ZOOM) })
            }
            .transformable(transformState, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        val ratio = preview?.aspectRatio ?: GALLERY_RATIO
        val width = minOf(maxWidth, maxHeight * ratio)
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width, width / ratio)
                .then(sharedModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun PhotoDetailsSheet(
    photo: ArtistPhoto,
    details: LastFmPhotoDetails?,
    loading: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    artistName: String,
    actions: ArtistPhotoActions,
) {
    val uriHandler = LocalUriHandler.current
    val scrollState = remember(photo.url) { ScrollState(0) }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val profileUrl = details?.authorProfileUrl
                val authorLabel = stringResource(R.string.artist_photo_view_author)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(
                            enabled = profileUrl != null,
                            role = Role.Button,
                            onClickLabel = authorLabel,
                        ) { profileUrl?.let { runCatching { uriHandler.openUri(it) } } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (details?.authorName != null) Icons.Outlined.Person else Icons.Outlined.Image,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            details?.authorAvatarUrl?.let { avatar ->
                                AsyncImage(
                                    model = avatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(
                            text = details?.authorName ?: if (photo.pageUrl != null) {
                                stringResource(R.string.brand_lastfm)
                            } else {
                                artistName
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (details?.authorName != null) {
                            Text(
                                text = stringResource(R.string.brand_lastfm),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (loading) ContainedLoadingIndicator(Modifier.size(24.dp))
                photo.pageUrl?.let { pageUrl ->
                    IconButton(
                        onClick = { runCatching { uriHandler.openUri(pageUrl) } },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.artist_photo_view_on_lastfm),
                        )
                    }
                }
            }
            details?.title?.let { title ->
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            details?.description?.let { description ->
                Spacer(Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            details?.uploadDate?.let(::formatPhotoUploadDate)?.let { date ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.artist_photo_uploaded, date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (photo.pageUrl != null && !loading && details == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.artist_photo_details_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { actions.share(photo.pageUrl ?: photo.url) },
                    modifier = Modifier.size(56.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Outlined.Share, stringResource(R.string.share))
                }
                FilledTonalButton(
                    onClick = onSave,
                    enabled = !saving,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.height(56.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    if (saving) {
                        ContainedLoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
                    } else {
                        Icon(Icons.Outlined.Download, null, Modifier.size(ButtonDefaults.IconSize))
                    }
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_download), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

