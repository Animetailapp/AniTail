/**
 * AniTail Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.anitail.shared.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.shared.BuildConfig
import com.anitail.shared.generated.resources.Res
import com.anitail.shared.generated.resources.about
import com.anitail.shared.generated.resources.app_name
import com.anitail.shared.generated.resources.back
import com.anitail.shared.generated.resources.build_type_debug
import com.anitail.shared.generated.resources.build_type_release
import com.anitail.shared.generated.resources.devmode_already_active
import com.anitail.shared.generated.resources.devtools_enabled
import com.anitail.shared.settings.Settings
import com.anitail.shared.ui.components.images.AsyncImage
import com.anitail.shared.ui.icons.AnitailIcons
import com.anitail.shared.utils.getAppArchitecture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    logo: Painter,
    modifier: Modifier = Modifier,
    appName: String = "",
    version: String = BuildConfig.APP_VERSION,
    architecture: String = remember { getAppArchitecture().uppercase() },
    isDebug: Boolean = BuildConfig.DEBUG,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    bottomInset: Dp = 0.dp,
    onNavigateBack: (() -> Unit)? = null,
    onDevtoolsUnlocked: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onPlayFavoriteSong: ((videoId: String) -> Unit)? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyGridState()
    var logoClickCount by rememberSaveable { mutableIntStateOf(0) }
    var devtoolsUnlocked by remember { mutableStateOf(Settings.shared.devtoolsUnlocked) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val layoutDirection = LocalLayoutDirection.current

    val devtoolsEnabledMessage = stringResource(Res.string.devtools_enabled)
    val devmodeAlreadyActiveMessage = stringResource(Res.string.devmode_already_active)
    val buildLabel = stringResource(if (isDebug) Res.string.build_type_debug else Res.string.build_type_release)
    val items =
        createDefaultAboutItems(
            BuildInfo(
                buildType = buildLabel,
                versionName = version,
                deviceInfo = "iOS ($architecture)",
            ),
        )

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val openUrl: (String) -> Unit = { url ->
        try {
            uriHandler.openUri(url.trim())
        } catch (_: Throwable) {
        }
    }

    fun handleLogoClick() {
        if (devtoolsUnlocked) {
            coroutineScope.launch { snackbarHostState.showSnackbar(devmodeAlreadyActiveMessage) }
        } else if (++logoClickCount >= 3) {
            logoClickCount = 0
            devtoolsUnlocked = true
            Settings.shared.devtoolsUnlocked = true
            onDevtoolsUnlocked()
            coroutineScope.launch { snackbarHostState.showSnackbar(devtoolsEnabledMessage) }
        }
    }

    val titleAlpha by animateFloatAsState(targetValue = if (isVisible) 1f else 0f)

    Scaffold(
        modifier =
            if (onNavigateBack != null) {
                modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                modifier
            },
        containerColor = if (onNavigateBack == null) Color.Transparent else colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (onNavigateBack != null) {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.about),
                            modifier = Modifier.alpha(titleAlpha),
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.alpha(titleAlpha),
                        ) {
                            Icon(
                                AnitailIcons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = colorScheme.surface,
                            scrolledContainerColor = colorScheme.surfaceContainerHigh,
                        ),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomInset),
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = contentPadding.calculateLeftPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                    end = contentPadding.calculateRightPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding() + bottomInset,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.id },
                contentType = { _, item -> item::class.simpleName },
            ) { index, item ->
                StaggeredAnimation(
                    listState = listState,
                    index = index,
                ) {
                    when (item) {
                        is HeaderItem ->
                            HeaderCard(
                                logo = logo,
                                appName = appName,
                                buildInfo = item.buildInfo,
                                colorScheme = colorScheme,
                                onLogoClick = ::handleLogoClick,
                            )

                        is SectionItem ->
                            SectionCard(
                                section = item,
                                colorScheme = colorScheme,
                                onItemClick = openUrl,
                            )

                        is TeamSectionItem ->
                            TeamGrid(
                                section = item,
                                colorScheme = colorScheme,
                                onMemberClick = openUrl,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaggeredAnimation(
    listState: LazyGridState,
    index: Int,
    content: @Composable () -> Unit,
) {
    val isVisibleNow by remember(listState, index) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == index } }
    }

    var hasBeenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisibleNow) {
        if (isVisibleNow) hasBeenVisible = true
    }

    val firstVisible = listState.layoutInfo.visibleItemsInfo.minByOrNull { it.index }?.index ?: index
    val relativeIndex = (index - firstVisible).coerceAtLeast(0)
    val delayMillis = relativeIndex * 40 + 40

    AnimatedVisibility(
        visible = hasBeenVisible,
        enter =
            fadeIn(animationSpec = tween(durationMillis = 350, delayMillis = delayMillis)) +
                slideInVertically(
                    animationSpec =
                        tween(
                            durationMillis = 350,
                            delayMillis = delayMillis,
                            easing = FastOutSlowInEasing,
                        ),
                ) { fullHeight -> fullHeight / 10 },
        exit = fadeOut(animationSpec = tween(200)),
    ) {
        content()
    }
}

@Composable
private fun HeaderCard(
    logo: Painter,
    appName: String,
    buildInfo: BuildInfo,
    colorScheme: ColorScheme,
    onLogoClick: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
    )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                    )
                },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    colorScheme.primary.copy(alpha = 0.1f),
                                    colorScheme.tertiary.copy(alpha = 0.1f),
                                    colorScheme.surface,
                                ),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
            ) {
                val infiniteTransition = rememberInfiniteTransition("pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "pulse",
                )

                Surface(
                    onClick = onLogoClick,
                    modifier = Modifier.size(80.dp).scale(pulseScale),
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.primaryContainer,
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = logo,
                            contentDescription = appName.ifBlank { stringResource(Res.string.app_name) },
                            modifier = Modifier.size(48.dp),
                            colorFilter = ColorFilter.tint(colorScheme.onPrimaryContainer, BlendMode.SrcIn),
                        )

                        Surface(
                            modifier = Modifier.matchParentSize(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                        ) {
                            ShimmerOverlay()
                        }
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BuildInfoRow(AboutIcons.Build, "Build", buildInfo.buildType, colorScheme)
                BuildInfoRow(AboutIcons.Verified, "Version", buildInfo.versionName, colorScheme)
                BuildInfoRow(AboutIcons.Devices, "Device", buildInfo.deviceInfo, colorScheme)
            }
        }
    }
}

@Composable
private fun ShimmerOverlay() {
    val shimmerColors =
        listOf(
            Color.White.copy(alpha = 0f),
            Color.White.copy(alpha = 0.3f),
            Color.White.copy(alpha = 0f),
        )
    val transition = rememberInfiniteTransition("shimmer")
    val translate by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                tween(2000, easing = FastOutSlowInEasing),
                RepeatMode.Restart,
            ),
        label = "shimmer",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush =
                Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translate * size.width, 0f),
                    end = Offset(translate * size.width + size.width / 2f, 0f),
                ),
            blendMode = BlendMode.Screen,
        )
    }
}

@Composable
private fun BuildInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    colorScheme: ColorScheme,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionCard(
    section: SectionItem,
    colorScheme: ColorScheme,
    onItemClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = section.title,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.items.forEach { item ->
                    LinkCardItem(
                        item = item,
                        colorScheme = colorScheme,
                        onClick = { onItemClick(item.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkCardItem(
    item: CardItem,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
    )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .scale(scale)
                .pointerInput(item.url) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() },
                    )
                },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(28.dp),
                tint = colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = AboutIcons.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TeamGrid(
    section: TeamSectionItem,
    colorScheme: ColorScheme,
    onMemberClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = section.title,
                    tint = colorScheme.tertiary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "${section.members.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onTertiaryContainer,
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 500.dp) 2 else 5
                val rows = section.members.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { member ->
                                TeamMemberCard(
                                    member = member,
                                    colorScheme = colorScheme,
                                    onClick = { onMemberClick(member.url) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(columns - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMemberCard(
    member: TeamMember,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
    )

    Column(
        modifier =
            modifier
                .scale(scale)
                .pointerInput(member.url) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() },
                    )
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            shadowElevation = if (isPressed) 2.dp else 4.dp,
            color = colorScheme.surface,
        ) {
            Box(modifier = Modifier.size(64.dp)) {
                if (member.avatarUrl != null) {
                    AsyncImage(
                        url = member.avatarUrl.trim(),
                        contentDescription = member.name,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop,
                        shape = CircleShape,
                    )
                } else {
                    Icon(
                        imageVector = AboutIcons.Person,
                        contentDescription = member.name,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(colorScheme.primaryContainer)
                                .padding(16.dp),
                        tint = colorScheme.onPrimaryContainer,
                    )
                }

                if (member.id == "dev") {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .background(colorScheme.surface, CircleShape)
                                .padding(2.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(colorScheme.tertiary, CircleShape),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = member.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = member.role,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
