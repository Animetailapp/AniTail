@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.anitail.music.ui.screens.artist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil.imageLoader
import coil.request.SuccessResult
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.ui.component.shimmer.ShimmerHost
import com.anitail.music.utils.ArtistPhoto
import com.anitail.music.utils.ArtistPhotoSaver
import com.anitail.music.utils.LastFmArtistClient
import com.anitail.music.utils.monthlyListenersCount
import com.anitail.music.viewmodels.AboutArtistViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.NumberFormat

private const val GALLERY_PREFETCH_COUNT = 2
private val GALLERY_MAX_WIDTH = 520.dp
private val WIDE_LAYOUT_WIDTH = 960.dp
private val CAROUSEL_SMALL_ITEM = 32.dp
private val CAROUSEL_SPACING = 12.dp

@Composable
fun AboutArtistScreen(
    navController: NavController,
    viewModel: AboutArtistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val seed = uiState.seed
    val artistName = seed?.artistName.orEmpty().ifBlank { stringResource(R.string.about_artist) }
    val bottomContentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val photoActions = rememberArtistPhotoActions(artistName)

    val galleryPhotos = remember(artistName, seed?.thumbnailUrl, uiState.lastFmData?.photos) {
        artistGalleryPhotos(artistName, seed?.thumbnailUrl, uiState.lastFmData?.photos.orEmpty())
    }
    val data = uiState.lastFmData
    val loading = uiState.isLoading && uiState.lastFmData == null
    val galleryState = rememberCarouselState { galleryPhotos.size }
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val scope = rememberCoroutineScope()

    var viewerOpen by rememberSaveable { mutableStateOf(false) }
    var viewerPreview by remember { mutableStateOf<ArtistPhotoPreview?>(null) }

    val viewerVisibility = remember { MutableTransitionState(false) }
    viewerVisibility.targetState = viewerOpen && galleryPhotos.isNotEmpty()
    val galleryInteractive = viewerVisibility.isIdle && !viewerVisibility.currentState
    var viewerPhotoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerLastPage by rememberSaveable { mutableIntStateOf(0) }
    val viewerPage = remember(galleryPhotos, viewerPhotoUrl, viewerLastPage) {
        artistPhotoIndex(galleryPhotos, viewerPhotoUrl, viewerLastPage)
    }

    var hadGalleryPhotos by remember { mutableStateOf(galleryPhotos.isNotEmpty()) }
    LaunchedEffect(galleryPhotos) {
        if (galleryPhotos.isEmpty() && hadGalleryPhotos) viewerOpen = false
        hadGalleryPhotos = galleryPhotos.isNotEmpty()
    }

    var photoJob by remember { mutableStateOf<Job?>(null) }

    fun openPhoto(index: Int) {
        if (!galleryInteractive || photoJob?.isActive == true) return
        photoJob = scope.launch {
            if (galleryPhotos.size > 1 && galleryState.currentItem != index) {
                galleryState.animateScrollToItem(index)
            }
            viewerPreview = artistPhotoPreview(imageLoader, context, galleryPhotos[index].url)
            viewerLastPage = index
            viewerPhotoUrl = galleryPhotos[index].url
            viewerOpen = true
        }
    }

    fun closePhoto() {
        if (!viewerOpen || photoJob?.isActive == true) return
        photoJob = scope.launch {
            if (galleryPhotos.size > 1) {
                galleryState.scrollToItem(viewerPage ?: 0)
                withFrameNanos { }
                withFrameNanos { }
            }
            viewerOpen = false
        }
    }

    val prefetched = remember(galleryPhotos) { mutableSetOf<String>() }
    LaunchedEffect(galleryPhotos, imageLoader, context, galleryInteractive) {
        if (!galleryInteractive) return@LaunchedEffect
        snapshotFlow {
            galleryPhotos
                .drop(galleryState.currentItem + 1)
                .take(GALLERY_PREFETCH_COUNT)
                .map(ArtistPhoto::url)
        }
            .distinctUntilChanged()
            .collectLatest { urls ->
                urls.filterNot(prefetched::contains).forEach { url ->
                    if (imageLoader.execute(artistPhotoRequest(context, url)) is SuccessResult) {
                        prefetched += url
                    }
                }
            }
    }

    val uriHandler = LocalUriHandler.current
    val loadingText = stringResource(R.string.loading)

    fun openLastFm(name: String) {
        runCatching { uriHandler.openUri(LastFmArtistClient.artistPageUrl(name)) }
    }

    SharedTransitionLayout {
        @Composable
        fun Gallery(heightLimit: Dp) {
            @Composable
            fun GalleryPhoto(
                index: Int,
                sharedPhoto: Boolean = false,
            ) {
                val photo = galleryPhotos.getOrNull(index) ?: return
                val photoDescription = stringResource(R.string.artist_photo_content_description, artistName)
                AnimatedVisibility(visible = !viewerOpen, enter = fadeIn(), exit = fadeOut()) {
                    val sharedModifier = if (sharedPhoto) {
                        Modifier.sharedElement(
                            rememberSharedContentState(artistPhotoSharedKey(photo.url)),
                            animatedVisibilityScope = this,
                            boundsTransform = rememberArtistPhotoBoundsTransform(),
                        )
                    } else {
                        Modifier
                    }
                    ArtistPhotoImage(
                        url = photo.url,
                        contentDescription = photoDescription,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(sharedModifier)
                            .clip(MaterialTheme.shapes.large)
                            .clickable(enabled = galleryInteractive) { openPhoto(index) },
                    )
                }
            }

            if (galleryPhotos.isEmpty()) {
                ShimmerHost {
                    Spacer(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            } else if (galleryPhotos.size == 1) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .widthIn(max = minOf(GALLERY_MAX_WIDTH, heightLimit * GALLERY_RATIO))
                            .fillMaxWidth()
                            .aspectRatio(GALLERY_RATIO),
                    ) {
                        GalleryPhoto(0, sharedPhoto = true)
                    }
                }
            } else {
                var galleryCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val heroBounds = remember { mutableStateOf<Rect?>(null) }
                BoxWithConstraints(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .onPlaced { galleryCoordinates = it },
                    contentAlignment = Alignment.Center,
                ) {
                    val peekWidth = 32.dp + (CAROUSEL_SMALL_ITEM + CAROUSEL_SPACING) * 2
                    val focusedWidth = minOf(
                        minOf(maxWidth, GALLERY_MAX_WIDTH) - peekWidth,
                        heightLimit * GALLERY_RATIO,
                    ).coerceAtLeast(180.dp)
                    val carouselWidth = focusedWidth + peekWidth
                    val flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(galleryState)
                    HorizontalCenteredHeroCarousel(
                        state = galleryState,
                        flingBehavior = flingBehavior,
                        minSmallItemWidth = CAROUSEL_SMALL_ITEM,
                        maxSmallItemWidth = CAROUSEL_SMALL_ITEM,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        itemSpacing = CAROUSEL_SPACING,
                        userScrollEnabled = galleryInteractive,
                        modifier = Modifier
                            .width(carouselWidth)
                            .height(focusedWidth / GALLERY_RATIO),
                    ) { index ->
                        val isHero by remember(index, galleryState) {
                            derivedStateOf { index == galleryState.currentItem }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if (isHero && !galleryInteractive && heroBounds.value != null) 0f else 1f
                                }
                                .maskClip(MaterialTheme.shapes.large)
                                .onGloballyPositioned { item ->
                                    val gallery = galleryCoordinates?.takeIf { it.isAttached }
                                    if (isHero && gallery != null) {
                                        val itemBounds = gallery.localBoundingBoxOf(item, clipBounds = false)
                                        heroBounds.value = carouselItemDrawInfo.maskRect.translate(itemBounds.topLeft)
                                    }
                                },
                        ) {
                            GalleryPhoto(index)
                        }
                    }
                    ArtistGalleryHero(
                        bounds = heroBounds,
                        photos = galleryPhotos,
                        galleryState = galleryState,
                        viewerOpen = viewerOpen,
                        showPhoto = !galleryInteractive,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
        }

        fun LazyListScope.details() {
            if (loading) {
                item(key = "loading", contentType = "loading") {
                    ShimmerHost {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 24.dp)
                                .clearAndSetSemantics {
                                    contentDescription = loadingText
                                    liveRegion = LiveRegionMode.Polite
                                },
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            val color = MaterialTheme.colorScheme.surfaceContainerHigh
                            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                repeat(2) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Spacer(Modifier.fillMaxWidth(0.8f).height(32.dp).background(color, MaterialTheme.shapes.small))
                                        Spacer(Modifier.fillMaxWidth(0.6f).height(20.dp).background(color, MaterialTheme.shapes.small))
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Spacer(Modifier.width(100.dp).height(28.dp).background(color, MaterialTheme.shapes.small))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    repeat(3) {
                                        Spacer(Modifier.weight(1f).height(36.dp).background(color, RoundedCornerShape(100.dp)))
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Spacer(Modifier.fillMaxWidth(0.5f).height(28.dp).background(color, MaterialTheme.shapes.small))
                                repeat(6) { index ->
                                    val lineWidth = if (index == 5) 0.6f else 1f
                                    Spacer(Modifier.fillMaxWidth(lineWidth).height(18.dp).background(color, MaterialTheme.shapes.small))
                                }
                            }
                        }
                    }
                }
                return
            }

            val stats = listOf(
                R.string.artist_lastfm_listeners to data?.listeners?.let(::formatStatCount),
                R.string.artist_lastfm_scrobbles to data?.scrobbles?.let(::formatStatCount),
                R.string.artist_monthly_audience to monthlyListenersCount(seed?.monthlyListenerCount),
                R.string.subscribers to seed?.subscriberCountText?.trim()?.takeIf { it.isNotEmpty() },
            ).filter { !it.second.isNullOrBlank() }

            if (stats.isNotEmpty()) {
                item(key = "stats", contentType = "stats") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        stats.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                row.forEach { (label, count) ->
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = count.orEmpty(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = stringResource(label),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!data?.tags.isNullOrEmpty()) {
                item(key = "tags", contentType = "tags") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp)) {
                        Text(stringResource(R.string.genres), style = MaterialTheme.typography.titleLargeEmphasized)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(data.tags) { tag ->
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = tag,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val lastFmSummary = data?.summary
            if (!lastFmSummary.isNullOrBlank()) {
                item(key = "lastfm_bio", contentType = "lastfm_bio") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp)) {
                        Text(
                            text = stringResource(R.string.artist_lastfm_biography),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                        )
                        Spacer(Modifier.height(12.dp))
                        ArtistExpandableText(
                            text = lastFmSummary,
                            collapsedMaxLines = 6,
                        )
                        TextButton(
                            onClick = { openLastFm(artistName) },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.artist_lastfm_read_more))
                        }
                    }
                }
            }

            val ytDescription = seed?.description
            if (!ytDescription.isNullOrBlank()) {
                item(key = "description", contentType = "description") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp)) {
                        Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLargeEmphasized)
                        Spacer(Modifier.height(12.dp))
                        ArtistExpandableText(
                            text = ytDescription,
                            collapsedMaxLines = 6,
                        )
                    }
                }
            }

            if (!data?.similarArtists.isNullOrEmpty()) {
                item(key = "similar", contentType = "similar") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp)) {
                        Text(
                            text = stringResource(R.string.artist_similar_artists),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                        )
                        Spacer(Modifier.height(12.dp))
                        data.similarArtists.forEach { name ->
                            TextButton(
                                onClick = { openLastFm(name) },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(name)
                            }
                        }
                    }
                }
            }

            val viewCount = seed?.viewCount
            if (!viewCount.isNullOrBlank()) {
                item(key = "views", contentType = "views") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp)) {
                        Text(
                            text = stringResource(R.string.total_views),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = viewCount,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (ytDescription.isNullOrBlank() && lastFmSummary.isNullOrBlank()) {
                item(key = "no_bio_hint", contentType = "no_bio_hint") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp)) {
                        LastfmHintCard(
                            title = stringResource(if (uiState.hasError) R.string.could_not_load_info else R.string.no_bio_title),
                            bodyText = stringResource(if (uiState.hasError) R.string.lastfm_profile_hint else R.string.no_bio_hint),
                            artistName = artistName,
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(artistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(
                                onClick = navController::navigateUp,
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                },
            ) { padding ->
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val showGallery = loading || galleryPhotos.isNotEmpty()
                    if (maxWidth >= WIDE_LAYOUT_WIDTH && showGallery) {
                        val galleryHeight = maxHeight - 32.dp - bottomContentPadding
                        Row(Modifier.widthIn(max = 1400.dp).fillMaxSize()) {
                            Column(Modifier.width(GALLERY_MAX_WIDTH).padding(vertical = 16.dp)) {
                                Gallery(galleryHeight)
                            }
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentPadding = PaddingValues(bottom = 32.dp + bottomContentPadding),
                                userScrollEnabled = galleryInteractive,
                            ) {
                                details()
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp + bottomContentPadding),
                            userScrollEnabled = galleryInteractive,
                        ) {
                            if (showGallery) item(key = "gallery", contentType = "gallery") { Gallery(Dp.Infinity) }
                            details()
                        }
                    }
                }
            }

            AnimatedVisibility(
                visibleState = viewerVisibility,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                ArtistPhotoViewer(
                    photos = galleryPhotos,
                    initialIndex = viewerPage ?: 0,
                    initialPreview = viewerPreview,
                    artistName = artistName,
                    actions = photoActions,
                    visibilityScope = this,
                    onPageChange = { index ->
                        viewerLastPage = index
                        viewerPhotoUrl = galleryPhotos.getOrNull(index)?.url
                    },
                    onDismiss = ::closePhoto,
                )
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.ArtistGalleryHero(
    bounds: State<Rect?>,
    photos: List<ArtistPhoto>,
    galleryState: CarouselState,
    viewerOpen: Boolean,
    showPhoto: Boolean,
    modifier: Modifier,
) {
    val hero = bounds.value?.takeUnless(Rect::isEmpty) ?: return
    val url = photos.getOrNull(galleryState.currentItem)?.url ?: return
    AnimatedVisibility(
        visible = !viewerOpen,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .absoluteOffset { hero.topLeft.round() }
            .size(with(LocalDensity.current) { hero.size.toDpSize() }),
    ) {
        Box(
            Modifier
                .sharedElement(
                    rememberSharedContentState(artistPhotoSharedKey(url)),
                    animatedVisibilityScope = this,
                    boundsTransform = rememberArtistPhotoBoundsTransform(),
                )
                .fillMaxSize()
                .graphicsLayer { alpha = if (showPhoto) 1f else 0f }
                .clip(MaterialTheme.shapes.large),
        ) {
            if (showPhoto) {
                ArtistAboutPhoto(
                    url = url,
                    gradient = { 0f },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LastfmHintCard(
    title: String,
    bodyText: String,
    artistName: String,
) {
    val uriHandler = LocalUriHandler.current
    val lastfmUrl = LastFmArtistClient.artistPageUrl(artistName)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { runCatching { uriHandler.openUri(lastfmUrl) } },
                modifier = Modifier.align(Alignment.End),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.lastfm_link_text))
            }
        }
    }
}

@Composable
private fun rememberArtistPhotoActions(artistName: String): ArtistPhotoActions {
    val context = LocalContext.current
    var permissionResult by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionResult?.complete(granted)
    }
    return remember(context, artistName, permissionLauncher) {
        ArtistPhotoActions(
            share = { link ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                }
                context.startActivity(Intent.createChooser(intent, null))
            },
            save = { photo ->
                val granted = !ArtistPhotoSaver.needsStoragePermission ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED ||
                    CompletableDeferred<Boolean>().also {
                        permissionResult = it
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }.await()
                check(granted) { "Storage permission denied" }
                ArtistPhotoSaver.save(context, photo, artistName)
                true
            },
            showMessage = { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

private fun formatStatCount(raw: String): String =
    raw.trim().toLongOrNull()?.let { NumberFormat.getIntegerInstance().format(it) } ?: raw

