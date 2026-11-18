package com.anitail.desktop.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.ArtistPhoto
import com.anitail.desktop.auth.LastFmArtistService
import com.anitail.desktop.auth.LastFmPhotoDetails
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ExpandableText
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.shimmerBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.net.URL
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val GALLERY_MAX_WIDTH = 520.dp
private val WIDE_LAYOUT_WIDTH = 960.dp
private val CAROUSEL_SMALL_ITEM = 32.dp
private val CAROUSEL_SPACING = 12.dp
private const val GALLERY_RATIO = 3f / 4f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutArtistScreen(
    artistId: String,
    artistName: String,
    thumbnailUrl: String?,
    description: String?,
    subscriberCountText: String?,
    monthlyListenerCount: String?,
    viewCount: String? = null,
    catalogTitles: List<String> = emptyList(),
    onBack: () -> Unit,
    onSimilarArtistClick: ((String) -> Unit)? = null,
) {
    var lastFmData by remember(artistId) { mutableStateOf(LastFmArtistService.getCached(artistId)) }
    var isLoading by remember(artistId) { mutableStateOf(lastFmData == null) }
    var hasError by remember(artistId) { mutableStateOf(false) }

    var viewerOpen by remember(artistId) { mutableStateOf(false) }
    var viewerInitialIndex by remember(artistId) { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(artistId, artistName, catalogTitles) {
        if (lastFmData == null) isLoading = true
        hasError = false
        LastFmArtistService.fetchArtistData(
            artistId = artistId,
            artistName = artistName,
            catalogTitles = catalogTitles,
        ).onSuccess { data ->
            lastFmData = data
            hasError = false
        }.onFailure {
            if (lastFmData == null) hasError = true
        }
        isLoading = false
    }

    val galleryPhotos = remember(thumbnailUrl, lastFmData?.photos) {
        val lfmPhotos = lastFmData?.photos.orEmpty()
        val ytPhoto = thumbnailUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { upgradeArtistImageUrl(it) }
            ?.let { ArtistPhoto(url = it, pageUrl = null) }
        val combined = if (lfmPhotos.isNotEmpty()) {
            lfmPhotos
        } else {
            listOfNotNull(ytPhoto)
        }
        combined.distinctBy { it.url }
    }

    val galleryState = rememberCarouselState { galleryPhotos.size }

    fun openExternalUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }

    fun copyLink(url: String, copiedMessage: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
        }
        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
    }

    fun savePhoto(photo: ArtistPhoto, savedMessage: String) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val safeName = artistName.replace(Regex("""[^\p{L}\p{N}_-]+"""), "_").trim('_').ifBlank { "artist" }
                    val chooser = JFileChooser().apply {
                        selectedFile = File(System.getProperty("user.home"), "$safeName.jpg")
                        fileFilter = FileNameExtensionFilter("JPEG Image", "jpg", "jpeg", "png")
                    }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val target = chooser.selectedFile
                        URL(photo.url).openStream().use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)
            }
            if (saved) {
                snackbarHostState.showSnackbar(savedMessage)
            }
        }
    }

    @Composable
    fun Gallery(heightLimit: Dp) {
        if (galleryPhotos.isEmpty() && isLoading) {
            Spacer(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(shimmerBrush()),
            )
        } else if (galleryPhotos.size == 1) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .widthIn(max = minOf(GALLERY_MAX_WIDTH, heightLimit * GALLERY_RATIO))
                        .fillMaxWidth()
                        .aspectRatio(GALLERY_RATIO)
                        .clip(MaterialTheme.shapes.large)
                        .clickable {
                            viewerInitialIndex = 0
                            viewerOpen = true
                        },
                ) {
                    RemoteImage(
                        url = galleryPhotos[0].url,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else if (galleryPhotos.size > 1) {
            BoxWithConstraints(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
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
                    modifier = Modifier
                        .width(carouselWidth)
                        .height(focusedWidth / GALLERY_RATIO),
                ) { index ->
                    val photo = galleryPhotos.getOrNull(index) ?: return@HorizontalCenteredHeroCarousel
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .maskClip(MaterialTheme.shapes.large)
                            .clickable {
                                viewerInitialIndex = index
                                viewerOpen = true
                            },
                    ) {
                        RemoteImage(
                            url = photo.url,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    val listenersLabel = stringResource("artist_lastfm_listeners")
    val scrobblesLabel = stringResource("artist_lastfm_scrobbles")
    val monthlyAudienceLabel = stringResource("artist_monthly_audience")
    val subscribersLabel = stringResource("subscribers")
    val genresLabel = stringResource("genres")
    val lfmBioLabel = stringResource("artist_lastfm_biography")
    val lfmReadMoreLabel = stringResource("artist_lastfm_read_more")
    val aboutLabel = stringResource("about")
    val similarArtistsLabel = stringResource("artist_similar_artists")
    val totalViewsLabel = stringResource("total_views")
    val showMoreLabel = stringResource("show_more")
    val showLessLabel = stringResource("show_less")
    val noBioTitle = stringResource("no_bio_title")
    val noBioHint = stringResource("no_bio_hint")
    val errorTitle = stringResource("could_not_load_info")
    val errorHint = stringResource("lastfm_profile_hint")
    val openLastFmLabel = stringResource("lastfm_link_text")

    fun LazyListScope.details() {
        val data = lastFmData
        if (isLoading && data == null) {
            item(key = "loading") {
                val brush = shimmerBrush()
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        repeat(2) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Spacer(Modifier.fillMaxWidth(0.8f).height(32.dp).clip(MaterialTheme.shapes.small).background(brush))
                                Spacer(Modifier.fillMaxWidth(0.6f).height(20.dp).clip(MaterialTheme.shapes.small).background(brush))
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.width(100.dp).height(28.dp).clip(MaterialTheme.shapes.small).background(brush))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) {
                                Spacer(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(100.dp)).background(brush))
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.fillMaxWidth(0.5f).height(28.dp).clip(MaterialTheme.shapes.small).background(brush))
                        repeat(6) { idx ->
                            Spacer(
                                Modifier
                                    .fillMaxWidth(if (idx == 5) 0.6f else 1f)
                                    .height(18.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(brush),
                            )
                        }
                    }
                }
            }
            return
        }

        val monthlyAudience = LastFmArtistService.parseAudienceCount(monthlyListenerCount)
        val subscribers = LastFmArtistService.parseAudienceCount(subscriberCountText)
        val stats = listOf(
            listenersLabel to data?.listeners,
            scrobblesLabel to data?.scrobbles,
            (if (monthlyAudience?.isCountOnly == true) monthlyAudienceLabel else null) to monthlyAudience?.text,
            (if (subscribers?.isCountOnly == true) subscribersLabel else null) to subscribers?.text,
        ).filter { !it.second.isNullOrBlank() }

        if (stats.isNotEmpty()) {
            item(key = "stats") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp),
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
                                    if (label != null) {
                                        Text(
                                            text = label,
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
        }

        if (!data?.tags.isNullOrEmpty()) {
            item(key = "tags") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp),
                ) {
                    Text(
                        text = genresLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(data!!.tags) { tag ->
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!data?.summary.isNullOrBlank()) {
            item(key = "lastfm_bio") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp),
                ) {
                    Text(
                        text = lfmBioLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(12.dp))
                    ExpandableText(
                        text = data!!.summary!!,
                        collapsedMaxLines = 6,
                        showMoreText = showMoreLabel,
                        showLessText = showLessLabel,
                    )
                    TextButton(onClick = { openExternalUrl(LastFmArtistService.artistPageUrl(artistName)) }) {
                        Text(lfmReadMoreLabel)
                    }
                }
            }
        }

        if (!description.isNullOrBlank()) {
            item(key = "description") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp),
                ) {
                    Text(
                        text = aboutLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(12.dp))
                    ExpandableText(
                        text = description,
                        collapsedMaxLines = 6,
                        showMoreText = showMoreLabel,
                        showLessText = showLessLabel,
                    )
                }
            }
        }

        if (!data?.similarArtists.isNullOrEmpty()) {
            item(key = "similar") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp),
                ) {
                    Text(
                        text = similarArtistsLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(12.dp))
                    data!!.similarArtists.forEach { name ->
                        TextButton(
                            onClick = {
                                if (onSimilarArtistClick != null) {
                                    onSimilarArtistClick(name)
                                } else {
                                    openExternalUrl(LastFmArtistService.artistPageUrl(name))
                                }
                            },
                        ) {
                            Text(name)
                        }
                    }
                }
            }
        }

        if (!viewCount.isNullOrBlank()) {
            item(key = "views") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp),
                ) {
                    Text(
                        text = totalViewsLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
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

        if (description.isNullOrBlank() && data?.summary.isNullOrBlank()) {
            item(key = "no_bio_hint") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (hasError) errorTitle else noBioTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (hasError) errorHint else noBioHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { openExternalUrl(LastFmArtistService.artistPageUrl(artistName)) },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(openLastFmLabel)
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = artistName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = IconAssets.arrowBack(),
                                contentDescription = stringResource("back"),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                val showGallery = isLoading || galleryPhotos.isNotEmpty()
                if (maxWidth >= WIDE_LAYOUT_WIDTH && showGallery) {
                    val galleryHeight = (maxHeight - 32.dp).coerceAtLeast(240.dp)
                    Row(
                        modifier = Modifier
                            .widthIn(max = 1400.dp)
                            .fillMaxSize(),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(GALLERY_MAX_WIDTH)
                                .padding(vertical = 16.dp),
                        ) {
                            Gallery(galleryHeight)
                        }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                        ) {
                            details()
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        if (showGallery) {
                            item(key = "gallery") {
                                Gallery(Dp.Infinity)
                            }
                        }
                        details()
                    }
                }
            }
        }

        val copiedMsg = stringResource("copied_to_clipboard")
        val savedMsg = stringResource("photo_saved")
        AnimatedVisibility(
            visible = viewerOpen && galleryPhotos.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ArtistPhotoViewerOverlay(
                photos = galleryPhotos,
                initialIndex = viewerInitialIndex.coerceIn(0, (galleryPhotos.size - 1).coerceAtLeast(0)),
                artistName = artistName,
                onShare = { link -> copyLink(link, copiedMsg) },
                onSave = { photo -> savePhoto(photo, savedMsg) },
                onOpenUrl = ::openExternalUrl,
                onDismiss = { viewerOpen = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistPhotoViewerOverlay(
    photos: List<ArtistPhoto>,
    initialIndex: Int,
    artistName: String,
    onShare: (String) -> Unit,
    onSave: (ArtistPhoto) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photos.first()
    var details by remember(currentPhoto.url) {
        mutableStateOf(currentPhoto.pageUrl?.let(LastFmArtistService::peekPhotoDetails))
    }
    var detailsLoading by remember(currentPhoto.url) { mutableStateOf(false) }

    LaunchedEffect(showDetailsSheet, currentPhoto.pageUrl) {
        val pageUrl = currentPhoto.pageUrl
        if (showDetailsSheet && pageUrl != null && details == null) {
            detailsLoading = true
            details = LastFmArtistService.fetchPhotoDetails(pageUrl)
            detailsLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f)),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val photo = photos[page]
            val isCurrent = page == pagerState.currentPage
            var scale by remember(photo.url) { mutableFloatStateOf(1f) }
            var offset by remember(photo.url) { mutableStateOf(Offset.Zero) }

            fun applyScale(value: Float) {
                scale = value
                if (value == 1f) offset = Offset.Zero
                zoomed = value > 1f
            }

            LaunchedEffect(isCurrent) {
                if (!isCurrent) applyScale(1f)
            }

            val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
                applyScale((scale * zoomChange).coerceIn(1f, MAX_ZOOM))
                if (scale > 1f) offset += panChange
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(photo.url) {
                        detectTapGestures(
                            onDoubleTap = { applyScale(if (scale > 1f) 1f else DOUBLE_TAP_ZOOM) },
                            onTap = { if (scale == 1f) onDismiss() },
                        )
                    }
                    .transformable(transformState, canPan = { scale > 1f }),
                contentAlignment = Alignment.Center,
            ) {
                RemoteImage(
                    url = photo.url,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(56.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
        }

        // Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(
                    imageVector = IconAssets.close(),
                    contentDescription = stringResource("close"),
                    tint = Color.White,
                )
            }

            if (photos.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                IconButton(
                    onClick = { showDetailsSheet = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(
                        imageVector = IconAssets.info(),
                        contentDescription = stringResource("details"),
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = { onShare(currentPhoto.pageUrl ?: currentPhoto.url) },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(
                        imageVector = IconAssets.share(),
                        contentDescription = stringResource("share"),
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = { onSave(currentPhoto) },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(
                        imageVector = IconAssets.download(),
                        contentDescription = stringResource("save_photo"),
                        tint = Color.White,
                    )
                }
            }
        }

        // Desktop prev / next page buttons
        if (photos.size > 1 && !zoomed) {
            if (pagerState.currentPage > 0) {
                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        imageVector = IconAssets.arrowBack(),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
            if (pagerState.currentPage < photos.lastIndex) {
                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        imageVector = IconAssets.arrowForward(),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }

    if (showDetailsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            PhotoDetailsSheetContent(
                photo = currentPhoto,
                details = details,
                loading = detailsLoading,
                artistName = artistName,
                onOpenUrl = onOpenUrl,
                onSave = { onSave(currentPhoto) },
            )
        }
    }
}

@Composable
private fun PhotoDetailsSheetContent(
    photo: ArtistPhoto,
    details: LastFmPhotoDetails?,
    loading: Boolean,
    artistName: String,
    onOpenUrl: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (details?.authorProfileUrl != null) {
                        Modifier.clickable { onOpenUrl(details.authorProfileUrl) }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = IconAssets.info(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = details?.authorName ?: if (photo.pageUrl != null) stringResource("brand_lastfm") else artistName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (details?.authorName != null) {
                    Text(
                        text = stringResource("brand_lastfm"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (details?.authorProfileUrl != null) {
                Icon(
                    imageVector = IconAssets.openInNew(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (loading) {
            Spacer(Modifier.height(12.dp))
            Spacer(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(20.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(shimmerBrush()),
            )
            Spacer(Modifier.height(8.dp))
            Spacer(
                Modifier
                    .fillMaxWidth(0.9f)
                    .height(16.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(shimmerBrush()),
            )
        } else if (details == null && photo.pageUrl != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource("artist_photo_details_unavailable"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            details?.title?.let { title ->
                Spacer(Modifier.height(12.dp))
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            details?.description?.let { desc ->
                Spacer(Modifier.height(8.dp))
                Text(text = desc, style = MaterialTheme.typography.bodyMedium)
            }
            details?.uploadDate?.let { date ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource("artist_photo_uploaded", date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            photo.pageUrl?.let { pageUrl ->
                OutlinedButton(
                    onClick = { onOpenUrl(pageUrl) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = IconAssets.openInNew(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource("artist_photo_view_on_lastfm"))
                }
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = IconAssets.download(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource("save_photo"))
            }
        }
    }
}

private fun upgradeArtistImageUrl(url: String): String =
    if (url.contains("lh3.googleusercontent.com")) {
        url.replace(Regex("=w\\d+-h\\d+.*$"), "=w1200-h1000-p-l90-rj")
            .replace(Regex("=s\\d+.*$"), "=w1200-h1000-p-l90-rj")
    } else {
        url
    }
