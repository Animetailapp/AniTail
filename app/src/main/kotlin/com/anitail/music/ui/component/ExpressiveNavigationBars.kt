@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.anitail.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.graphics.shapes.RoundedPolygon
import coil.compose.AsyncImage
import com.anitail.music.BuildConfig
import com.anitail.music.R
import com.anitail.music.ui.screens.Screens
import com.anitail.music.ui.screens.artist.FittedPolygonShape
import com.anitail.music.ui.screens.artist.rememberArtistReducedMotion
import com.anitail.music.ui.utils.tvFocusable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val ExpressivePolygonShapeCache = ConcurrentHashMap<Pair<RoundedPolygon, Int>, Shape>()

/**
 * Converts a [RoundedPolygon] into a Compose [Shape] fitted cleanly within component bounds.
 */
@Composable
internal fun rememberExpressivePolygonShape(
    polygon: RoundedPolygon,
    startAngle: Int = 0,
): Shape {
    val key = polygon to startAngle
    ExpressivePolygonShapeCache[key]?.let { return it }
    val sourcePath = polygon.toPath(startAngle = startAngle)
    return remember(polygon, startAngle) {
        ExpressivePolygonShapeCache.getOrPut(key) {
            FittedPolygonShape(Path().apply { addPath(sourcePath) })
        }
    }
}

internal fun SegmentedCornerRadii.toRoundedCornerShape(): RoundedCornerShape =
    RoundedCornerShape(
        topStart = topStartDp.dp,
        topEnd = topEndDp.dp,
        bottomStart = bottomStartDp.dp,
        bottomEnd = bottomEndDp.dp,
    )

/**
 * Material 3 Expressive segmented row shape that morphs touching inner corners smoothly when pressed.
 */
@Composable
internal fun segmentedGroupShape(
    isFirst: Boolean,
    isLast: Boolean,
    pressed: Boolean,
    outer: Dp = 20.dp,
    inner: Dp = 5.dp,
): Shape {
    val reducedMotion = rememberArtistReducedMotion()
    val spec = if (reducedMotion) snap<Dp>() else MaterialTheme.motionScheme.fastSpatialSpec()
    val top by animateDpAsState(if (isFirst || pressed) outer else inner, spec, label = "segmentTop")
    val bottom by animateDpAsState(if (isLast || pressed) outer else inner, spec, label = "segmentBottom")
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * Staggered spatial entrance for stacked expressive groups.
 */
@Composable
internal fun Modifier.expressiveEntrance(index: Int = 0): Modifier {
    val reducedMotion = rememberArtistReducedMotion()
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            progress.snapTo(1f)
        } else {
            delay(index * 70L)
            progress.animateTo(1f, spatial)
        }
    }
    val rise = 24.dp
    return graphicsLayer {
        val value = progress.value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * rise.toPx()
        val scale = 0.96f + 0.04f * value
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Dynamic-color Material 3 Expressive brand emblem for app bar and rail headers.
 */
@Composable
fun ExpressiveBrandLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
) {
    val badgeShape = rememberExpressivePolygonShape(MaterialShapes.Cookie9Sided)
    Surface(
        modifier = modifier.size(size),
        shape = badgeShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ani_note),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Main top-level bar with brand logo on Home, expressive title transitions,
 * uniform action buttons, and a slot inside the profile avatar box for anchoring [ExpressiveAccountMenuPopup].
 */
@Composable
fun ExpressiveMainTopAppBar(
    currentRoute: String?,
    pureBlack: Boolean,
    hasUpdate: Boolean,
    avatarUrl: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    onHistoryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAvatarClick: () -> Unit,
    accountMenuContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberArtistReducedMotion()
    val settingsDescription = stringResource(R.string.settings)

    val titleContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                if (reducedMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 4 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 })
                }
            },
            label = "expressiveTopBarTitle",
        ) { route ->
            when (route) {
                Screens.Home.route -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ExpressiveBrandLogoBadge(size = 34.dp, iconSize = 20.dp)
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Screens.Explore.route -> {
                    Text(
                        text = stringResource(R.string.explore),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Screens.Library.route -> {
                    Text(
                        text = stringResource(R.string.filter_library),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    val avatarContent: @Composable () -> Unit = {
        BadgedBox(
            badge = {
                if (hasUpdate) {
                    Badge(containerColor = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = settingsDescription,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.account),
                    contentDescription = settingsDescription,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }

    val actionsContent: @Composable RowScope.() -> Unit = {
        if (currentRoute == Screens.Home.route) {
            IconButton(
                onClick = onHistoryClick,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.history),
                    contentDescription = stringResource(R.string.history),
                )
            }
        }

        IconButton(
            onClick = onSearchClick,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = stringResource(R.string.search),
            )
        }

        Box {
            IconButton(
                onClick = onAvatarClick,
                shapes = IconButtonDefaults.shapes(),
            ) {
                avatarContent()
            }
            accountMenuContent()
        }
    }

    TopAppBar(
        modifier = modifier,
        title = titleContent,
        actions = actionsContent,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
            scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

private object FullScreenScrimPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/**
 * Floating Material 3 Expressive account menu anchored to the top-bar profile avatar button.
 */
@Composable
fun ExpressiveAccountMenuPopup(
    displayName: String,
    accountHandle: String,
    accountEmail: String,
    isYouTubeSignedIn: Boolean,
    isSignedIn: Boolean,
    avatarUrl: String?,
    hasUpdate: Boolean,
    pureBlack: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val reducedMotion = rememberArtistReducedMotion()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current
    val menuWidth = (configuration.screenWidthDp.dp - 16.dp).coerceAtMost(360.dp)
    val maxMenuHeight = (configuration.screenHeightDp.dp - 96.dp).coerceIn(240.dp, 640.dp)

    var targetVisible by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val popupAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val popupScale = remember { Animatable(if (reducedMotion) 1f else 0.92f) }
    val scrimAlpha = remember { Animatable(if (reducedMotion) 0.32f else 0f) }

    val effectsSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatialSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    LaunchedEffect(Unit) {
        hasOpened = true
        targetVisible = true
    }

    LaunchedEffect(targetVisible) {
        if (targetVisible) {
            coroutineScope {
                launch { popupAlpha.animateTo(1f, effectsSpec) }
                launch { popupScale.animateTo(1f, spatialSpec) }
                launch { scrimAlpha.animateTo(0.32f, effectsSpec) }
            }
        } else if (hasOpened) {
            hasOpened = false
            val action = pendingAction
            pendingAction = null
            coroutineScope {
                launch { popupAlpha.animateTo(0f, effectsSpec) }
                launch { popupScale.animateTo(0.92f, spatialSpec) }
                launch { scrimAlpha.animateTo(0f, effectsSpec) }
            }
            onDismiss()
            action?.invoke()
        }
    }

    fun dismissWith(afterDismiss: (() -> Unit)? = null) {
        if (targetVisible) {
            pendingAction = afterDismiss
            targetVisible = false
        }
    }

    val marginPx = with(density) { 8.dp.roundToPx() }
    val spacingPx = with(density) { 6.dp.roundToPx() }
    val popupPositionProvider = remember(marginPx, spacingPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val desiredX = if (layoutDirection == LayoutDirection.Ltr) {
                    anchorBounds.right - popupContentSize.width
                } else {
                    anchorBounds.left
                }
                val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
                val belowY = anchorBounds.bottom + spacingPx
                val aboveY = anchorBounds.top - spacingPx - popupContentSize.height
                val desiredY = if (belowY + popupContentSize.height + marginPx <= windowSize.height) {
                    belowY
                } else {
                    aboveY
                }
                val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
                return IntOffset(
                    x = desiredX.coerceIn(marginPx, maxX),
                    y = desiredY.coerceIn(marginPx, maxY),
                )
            }
        }
    }

    val scrimColor = colorScheme.scrim
    Popup(
        popupPositionProvider = FullScreenScrimPositionProvider,
        properties = PopupProperties(focusable = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(scrimColor.copy(alpha = scrimAlpha.value.coerceIn(0f, 1f))) }
                .focusProperties { canFocus = false }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!showLogoutDialog) dismissWith() },
                ),
        )
    }

    val containerShape = RoundedCornerShape(28.dp)
    val containerColor = if (pureBlack) Color(0xFF121212) else colorScheme.surfaceContainerLow
    val heroTitle = displayName.ifBlank {
        stringResource(if (isSignedIn) R.string.account else R.string.login)
    }
    val heroSubtitle = accountHandle.ifBlank { accountEmail }.takeIf { it.isNotBlank() }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = { if (!showLogoutDialog) dismissWith() },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .width(menuWidth)
                .heightIn(max = maxMenuHeight)
                .graphicsLayer {
                    val a = popupAlpha.value.coerceIn(0f, 1f)
                    val s = popupScale.value
                    alpha = a
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (layoutDirection == LayoutDirection.Rtl) 0f else 1f,
                        pivotFractionY = 0f,
                    )
                }
                .shadow(6.dp, containerShape)
                .clip(containerShape)
                .background(containerColor, containerShape)
                .semantics { isTraversalGroup = true }
                .pointerInput(Unit) { detectTapGestures {} },
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColorFor(containerColor)) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveEntrance(0),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AccountMenuHeroRow(
                            title = heroTitle,
                            subtitle = heroSubtitle,
                            isSignedIn = isSignedIn,
                            isYouTubeSignedIn = isYouTubeSignedIn,
                            avatarUrl = avatarUrl,
                            onClick = {
                                dismissWith {
                                    onNavigate(if (isSignedIn) "settings/account" else "login")
                                }
                            },
                            onAvatarClick = {
                                dismissWith {
                                    onNavigate(if (isSignedIn) "settings/account" else "login")
                                }
                            },
                            onLogoutClick = { showLogoutDialog = true },
                        )

                        val tokenInteraction = remember { MutableInteractionSource() }
                        val tokenPressed by tokenInteraction.collectIsPressedAsState()
                        val tokenShape = segmentedGroupShape(
                            isFirst = false,
                            isLast = true,
                            pressed = tokenPressed,
                        )
                        val tokenBg by animateColorAsState(
                            if (tokenPressed) colorScheme.surfaceContainerHighest else colorScheme.surfaceContainer,
                            label = "accountMenuTokenBg",
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(tokenShape)
                                .background(tokenBg)
                                .clickable(
                                    interactionSource = tokenInteraction,
                                    indication = ripple(),
                                    role = Role.Button,
                                ) {
                                    dismissWith { onNavigate("settings/account") }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.token),
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(
                                    if (isSignedIn) R.string.account else R.string.advanced_login,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveEntrance(1),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ExpressiveMenuNavigationRow(
                            title = stringResource(R.string.history),
                            iconRes = R.drawable.history,
                            first = true,
                            last = false,
                            onClick = { dismissWith { onNavigate("history") } },
                        )
                        ExpressiveMenuNavigationRow(
                            title = stringResource(R.string.stats),
                            iconRes = R.drawable.stats,
                            first = false,
                            last = true,
                            onClick = { dismissWith { onNavigate("stats") } },
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveEntrance(2),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ExpressiveMenuNavigationRow(
                            title = stringResource(R.string.jam_lan_sync),
                            iconRes = R.drawable.sync,
                            first = true,
                            last = false,
                            onClick = { dismissWith { onNavigate("settings/jam") } },
                        )
                        ExpressiveMenuNavigationRow(
                            title = stringResource(R.string.settings),
                            iconRes = R.drawable.settings,
                            first = false,
                            last = true,
                            showBadge = hasUpdate,
                            onClick = { dismissWith { onNavigate("settings") } },
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveEntrance(3)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Button) {
                                dismissWith { onNavigate("settings/about") }
                            }
                            .padding(top = 4.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "GPL-3.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.account)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        dismissWith { onLogout() }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        text = stringResource(R.string.logout),
                        color = colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AccountMenuHeroRow(
    title: String,
    subtitle: String?,
    isSignedIn: Boolean,
    isYouTubeSignedIn: Boolean,
    avatarUrl: String?,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val reducedMotion = rememberArtistReducedMotion()
    val heroInteraction = remember { MutableInteractionSource() }
    val heroPressed by heroInteraction.collectIsPressedAsState()
    val heroShape = segmentedGroupShape(isFirst = true, isLast = false, pressed = heroPressed)
    val avatarShape = rememberExpressivePolygonShape(MaterialShapes.Cookie9Sided)

    val reveal = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val revealSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) reveal.snapTo(1f) else reveal.animateTo(1f, revealSpec)
    }

    val pressScale by animateFloatAsState(
        targetValue = if (heroPressed) 0.96f else 1f,
        animationSpec = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.fastSpatialSpec<Float>(),
        label = "accountHeroPress",
    )
    val heroBg by animateColorAsState(
        if (heroPressed) colorScheme.surfaceContainerHighest else colorScheme.surfaceContainer,
        label = "accountHeroBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .background(heroBg)
            .clickable(
                interactionSource = heroInteraction,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(
                start = 16.dp,
                top = 16.dp,
                bottom = 16.dp,
                end = if (isYouTubeSignedIn) 4.dp else 16.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(avatarShape)
                .clickable(role = Role.Button, onClick = onAvatarClick)
                .graphicsLayer {
                    val value = reveal.value
                    val scale = (0.6f + 0.4f * value) * pressScale
                    scaleX = scale
                    scaleY = scale
                    rotationZ = if (reducedMotion) 0f else (1f - value) * -45f
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(avatarShape)
                    .background(colorScheme.primary),
            )
            if (isSignedIn && !avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(avatarShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.account),
                    contentDescription = title,
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isYouTubeSignedIn) {
            IconButton(
                onClick = onLogoutClick,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = colorScheme.error),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.logout),
                    contentDescription = stringResource(R.string.logout),
                )
            }
        }
    }
}

@Composable
private fun ExpressiveMenuNavigationRow(
    title: String,
    @DrawableRes iconRes: Int,
    first: Boolean,
    last: Boolean,
    showBadge: Boolean = false,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = segmentedGroupShape(first, last, pressed)
    val containerColor by animateColorAsState(
        targetValue = if (pressed) colorScheme.surfaceContainerHighest else colorScheme.surfaceContainer,
        label = "accountNavRowBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            BadgedBox(
                badge = {
                    if (showBadge) {
                        Badge(containerColor = colorScheme.error)
                    }
                },
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private class StretchyPillIndicatorState(
    initialLeft: Float,
    initialRight: Float,
    initialCorner: Float,
) {
    val leftPx = Animatable(initialLeft)
    val rightPx = Animatable(initialRight)
    val cornerPx = Animatable(initialCorner)
    var initialized: Boolean = false
    var cornerInitialized: Boolean = false
}

@Composable
private fun rememberStretchyPillController(
    targetBounds: SlidingPillBoundsPx?,
    targetCornerPx: Float,
    reducedMotion: Boolean,
    cornerSpec: FiniteAnimationSpec<Float>,
): StretchyPillIndicatorState {
    val state = remember {
        StretchyPillIndicatorState(
            initialLeft = targetBounds?.leftPx ?: 0f,
            initialRight = targetBounds?.rightPx ?: 0f,
            initialCorner = targetCornerPx,
        )
    }

    LaunchedEffect(targetBounds, reducedMotion) {
        val bounds = targetBounds ?: return@LaunchedEffect
        if (!state.initialized || reducedMotion) {
            state.leftPx.snapTo(bounds.leftPx)
            state.rightPx.snapTo(bounds.rightPx)
            state.initialized = true
        } else {
            val movingRight = isPillMovingRight(state.leftPx.value, bounds.leftPx)
            val leadingSpec = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)
            val trailingSpec = spring<Float>(dampingRatio = 0.60f, stiffness = 340f)
            coroutineScope {
                if (movingRight) {
                    launch { state.rightPx.animateTo(bounds.rightPx, leadingSpec) }
                    launch { state.leftPx.animateTo(bounds.leftPx, trailingSpec) }
                } else {
                    launch { state.leftPx.animateTo(bounds.leftPx, leadingSpec) }
                    launch { state.rightPx.animateTo(bounds.rightPx, trailingSpec) }
                }
            }
        }
    }

    LaunchedEffect(targetCornerPx, reducedMotion) {
        if (!state.cornerInitialized || reducedMotion) {
            state.cornerPx.snapTo(targetCornerPx)
            state.cornerInitialized = true
        } else {
            state.cornerPx.animateTo(targetCornerPx, cornerSpec)
        }
    }

    return state
}

private class NavItemSelectionMotion(
    initialSelected: Boolean,
) {
    val activeProgress = Animatable(if (initialSelected) 1f else 0f)
    val iconPop = Animatable(1f)
    var wasSelected: Boolean = initialSelected
}

@Composable
private fun rememberNavItemSelectionMotion(
    selected: Boolean,
    reducedMotion: Boolean,
    activeSpec: FiniteAnimationSpec<Float>,
): NavItemSelectionMotion {
    val motion = remember { NavItemSelectionMotion(initialSelected = selected) }
    LaunchedEffect(selected, reducedMotion) {
        if (reducedMotion) {
            motion.wasSelected = selected
            motion.activeProgress.snapTo(if (selected) 1f else 0f)
            motion.iconPop.snapTo(1f)
        } else {
            val becameSelected = selected && !motion.wasSelected
            motion.wasSelected = selected
            coroutineScope {
                launch {
                    motion.activeProgress.animateTo(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = activeSpec,
                    )
                }
                if (becameSelected) {
                    launch {
                        motion.iconPop.snapTo(0.76f)
                        motion.iconPop.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.48f, stiffness = 420f),
                        )
                    }
                } else if (!selected) {
                    launch { motion.iconPop.snapTo(1f) }
                }
            }
        }
    }
    return motion
}

@Composable
private fun ExpressiveNavTabIcon(
    screen: Screens,
    motion: NavItemSelectionMotion,
    pressScale: Float,
    iconSize: Dp,
    activeLiftDp: Dp,
    selectedTint: Color,
    unselectedTint: Color,
    contentDescription: String?,
) {
    val density = LocalDensity.current
    val activeLiftPx = with(density) { activeLiftDp.toPx() }
    Box(
        modifier = Modifier
            .size(iconSize)
            .graphicsLayer {
                val popScale = motion.iconPop.value * pressScale
                scaleX = popScale
                scaleY = popScale
                translationY = -motion.activeProgress.value.coerceIn(0f, 1.2f) * activeLiftPx
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(screen.iconIdInactive),
            contentDescription = contentDescription,
            tint = unselectedTint,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - motion.activeProgress.value).coerceIn(0f, 1f) },
        )
        Icon(
            painter = painterResource(screen.iconIdActive),
            contentDescription = null,
            tint = selectedTint,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.activeProgress.value.coerceIn(0f, 1f) },
        )
    }
}

/**
 * Docked Material 3 Expressive bottom navigation bar with an asymmetric spring-sliding
 * indicator pill, press-morphing squircle corners, and bouncy outlined-to-filled icon choreography.
 */
@Composable
fun ExpressiveShortNavigationBar(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    slimNav: Boolean,
    pureBlack: Boolean,
    bottomInset: Dp,
    onItemClick: (Screens) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val horizontalItems = shouldUseHorizontalShortNavItems(screenWidthDp = screenWidthDp, slimNav = slimNav)
    val reducedMotion = rememberArtistReducedMotion()
    val spatialFloatSpec = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.fastSpatialSpec()
    val defaultSpatialSpec = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.defaultSpatialSpec()
    val containerColor = if (pureBlack) Color.Black else colorScheme.surfaceContainer
    val topBorderColor = colorScheme.outlineVariant.copy(alpha = if (pureBlack) 0.18f else 0.24f)
    val selectedIndex = items.indexOfFirst(isSelected)
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    val activeItemPressed = selectedIndex >= 0 && pressedIndex == selectedIndex
    val pillColor = colorScheme.secondaryContainer

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = if (pureBlack) Color.White else colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets(bottom = bottomInset)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(topBorderColor),
            )
            if (horizontalItems) {
                val itemBounds = remember(items.size) {
                    mutableStateListOf<SlidingPillBoundsPx?>().apply {
                        repeat(items.size) { add(null) }
                    }
                }
                val targetBounds = if (selectedIndex >= 0) itemBounds.getOrNull(selectedIndex) else null
                val targetCornerPx = with(density) { (if (activeItemPressed) 12.dp else 23.dp).toPx() }
                val pillState = rememberStretchyPillController(
                    targetBounds = targetBounds,
                    targetCornerPx = targetCornerPx,
                    reducedMotion = reducedMotion,
                    cornerSpec = spatialFloatSpec,
                )
                val horizontalPillTopPx = with(density) { 11.dp.toPx() }
                val horizontalPillHeightPx = with(density) { 46.dp.toPx() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .padding(horizontal = 24.dp)
                        .drawBehind {
                            if (selectedIndex >= 0 && pillState.initialized) {
                                val left = pillState.leftPx.value
                                val right = pillState.rightPx.value
                                val width = (right - left).coerceAtLeast(0f)
                                val corner = pillState.cornerPx.value.coerceAtLeast(0f)
                                if (width > 0f) {
                                    drawRoundRect(
                                        color = pillColor,
                                        topLeft = Offset(left, horizontalPillTopPx),
                                        size = Size(width, horizontalPillHeightPx),
                                        cornerRadius = CornerRadius(corner, corner),
                                    )
                                }
                            }
                        }
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, screen ->
                        val selected = index == selectedIndex
                        ExpressiveDockedHorizontalItem(
                            screen = screen,
                            selected = selected,
                            pureBlack = pureBlack,
                            reducedMotion = reducedMotion,
                            spatialFloatSpec = spatialFloatSpec,
                            defaultSpatialSpec = defaultSpatialSpec,
                            onPressedChange = { isPressed ->
                                if (isPressed) {
                                    pressedIndex = index
                                } else if (pressedIndex == index) {
                                    pressedIndex = null
                                }
                            },
                            onClick = { onItemClick(screen) },
                            modifier = Modifier.onGloballyPositioned { coords ->
                                val startX = coords.positionInParent().x
                                itemBounds[index] = SlidingPillBoundsPx(
                                    leftPx = startX,
                                    rightPx = startX + coords.size.width.toFloat(),
                                )
                            },
                        )
                    }
                }
            } else {
                var trackSize by remember { mutableStateOf(IntSize.Zero) }
                val trackWidthPx = trackSize.width.toFloat()
                val pillWidthPx = with(density) { 64.dp.toPx() }
                val horizontalMarginPx = with(density) { 4.dp.toPx() }
                val pillTopPx = with(density) { (if (slimNav) 10.dp else 8.dp).toPx() }
                val pillHeightPx = with(density) { (if (slimNav) 36.dp else 34.dp).toPx() }
                val isRtl = layoutDirection == LayoutDirection.Rtl
                val targetBounds = if (selectedIndex >= 0 && trackWidthPx > 0f) {
                    computeDockedPillBoundsPx(
                        trackWidthPx = trackWidthPx,
                        itemCount = items.size,
                        selectedIndex = selectedIndex,
                        pillWidthPx = pillWidthPx,
                        horizontalMarginPx = horizontalMarginPx,
                        isRtl = isRtl,
                    )
                } else {
                    null
                }
                val targetCornerPx = with(density) {
                    (if (activeItemPressed) 10.dp else (if (slimNav) 18.dp else 17.dp)).toPx()
                }
                val pillState = rememberStretchyPillController(
                    targetBounds = targetBounds,
                    targetCornerPx = targetCornerPx,
                    reducedMotion = reducedMotion,
                    cornerSpec = spatialFloatSpec,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (slimNav) 56.dp else 68.dp)
                        .padding(horizontal = 8.dp)
                        .onSizeChanged { trackSize = it },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                if (selectedIndex >= 0 && pillState.initialized) {
                                    val left = pillState.leftPx.value
                                    val right = pillState.rightPx.value
                                    val width = (right - left).coerceAtLeast(0f)
                                    val corner = pillState.cornerPx.value.coerceAtLeast(0f)
                                    if (width > 0f) {
                                        drawRoundRect(
                                            color = pillColor,
                                            topLeft = Offset(left, pillTopPx),
                                            size = Size(width, pillHeightPx),
                                            cornerRadius = CornerRadius(corner, corner),
                                        )
                                    }
                                }
                            }
                            .selectableGroup(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEachIndexed { index, screen ->
                            val selected = index == selectedIndex
                            ExpressiveDockedVerticalItem(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                screen = screen,
                                selected = selected,
                                slimNav = slimNav,
                                pureBlack = pureBlack,
                                reducedMotion = reducedMotion,
                                spatialFloatSpec = spatialFloatSpec,
                                defaultSpatialSpec = defaultSpatialSpec,
                                onPressedChange = { isPressed ->
                                    if (isPressed) {
                                        pressedIndex = index
                                    } else if (pressedIndex == index) {
                                        pressedIndex = null
                                    }
                                },
                                onClick = { onItemClick(screen) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveDockedVerticalItem(
    screen: Screens,
    selected: Boolean,
    slimNav: Boolean,
    pureBlack: Boolean,
    reducedMotion: Boolean,
    spatialFloatSpec: FiniteAnimationSpec<Float>,
    defaultSpatialSpec: FiniteAnimationSpec<Float>,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(pressed) {
        onPressedChange(pressed)
    }

    val motion = rememberNavItemSelectionMotion(
        selected = selected,
        reducedMotion = reducedMotion,
        activeSpec = defaultSpatialSpec,
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spatialFloatSpec,
        label = "dockedItemScale_${screen.route}",
    )
    val pressOverlayColor by animateColorAsState(
        targetValue = if (pressed && !selected) {
            colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
        } else {
            Color.Transparent
        },
        label = "dockedPressOverlay_${screen.route}",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            selected && pureBlack -> Color.White
            selected -> colorScheme.secondary
            pureBlack -> Color.White.copy(alpha = 0.72f)
            else -> colorScheme.onSurfaceVariant
        },
        label = "dockedLabelColor_${screen.route}",
    )
    val unselectedIconColor = if (pureBlack) Color.White.copy(alpha = 0.72f) else colorScheme.onSurfaceVariant
    val pillCornerDp = if (slimNav) 18.dp else 17.dp
    val pillShape = RoundedCornerShape(pillCornerDp)
    val titleText = stringResource(screen.titleId)

    Column(
        modifier = modifier
            .tvFocusable(shape = RoundedCornerShape(16.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(top = if (slimNav) 10.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(if (slimNav) 36.dp else 34.dp)
                .clip(pillShape)
                .drawBehind {
                    if (pressOverlayColor.alpha > 0f) {
                        val w = 48.dp.toPx().coerceAtMost(size.width)
                        val r = 11.dp.toPx()
                        drawRoundRect(
                            color = pressOverlayColor,
                            topLeft = Offset((size.width - w) / 2f, 0f),
                            size = Size(w, size.height),
                            cornerRadius = CornerRadius(r, r),
                        )
                    }
                }
                .indication(interactionSource, ripple(bounded = true)),
            contentAlignment = Alignment.Center,
        ) {
            ExpressiveNavTabIcon(
                screen = screen,
                motion = motion,
                pressScale = pressScale,
                iconSize = 24.dp,
                activeLiftDp = if (slimNav) 0.dp else 1.5.dp,
                selectedTint = colorScheme.onSecondaryContainer,
                unselectedTint = unselectedIconColor,
                contentDescription = if (slimNav) titleText else null,
            )
        }

        if (!slimNav) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = titleText,
                color = labelColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    val s = 0.96f + 0.04f * motion.activeProgress.value.coerceIn(0f, 1.15f)
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}

@Composable
private fun ExpressiveDockedHorizontalItem(
    screen: Screens,
    selected: Boolean,
    pureBlack: Boolean,
    reducedMotion: Boolean,
    spatialFloatSpec: FiniteAnimationSpec<Float>,
    defaultSpatialSpec: FiniteAnimationSpec<Float>,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(pressed) {
        onPressedChange(pressed)
    }

    val motion = rememberNavItemSelectionMotion(
        selected = selected,
        reducedMotion = reducedMotion,
        activeSpec = defaultSpatialSpec,
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spatialFloatSpec,
        label = "horizontalPillScale_${screen.route}",
    )
    val pressOverlayColor by animateColorAsState(
        targetValue = if (pressed && !selected) {
            colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        label = "horizontalPillPressBg_${screen.route}",
    )
    val unselectedContentColor = if (pureBlack) Color.White.copy(alpha = 0.72f) else colorScheme.onSurfaceVariant
    val labelColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onSecondaryContainer else unselectedContentColor,
        label = "horizontalPillContent_${screen.route}",
    )

    Row(
        modifier = modifier
            .height(46.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(pressOverlayColor)
            .tvFocusable(shape = CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = ripple(),
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExpressiveNavTabIcon(
            screen = screen,
            motion = motion,
            pressScale = 1f,
            iconSize = 22.dp,
            activeLiftDp = 0.dp,
            selectedTint = colorScheme.onSecondaryContainer,
            unselectedTint = unselectedContentColor,
            contentDescription = null,
        )
        Text(
            text = stringResource(screen.titleId),
            color = labelColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                val s = 0.96f + 0.04f * motion.activeProgress.value.coerceIn(0f, 1.15f)
                scaleX = s
                scaleY = s
            },
        )
    }
}

/**
 * Floating pill navigation bar matching MiniPlayer proportions with an asymmetric spring-sliding
 * active capsule, bouncy icon transitions, and balanced tabs without duplicating Search.
 */
@Composable
fun ExpressiveFloatingNavigationBar(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    slimNav: Boolean,
    pureBlack: Boolean,
    bottomInset: Dp,
    onItemClick: (Screens) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val reducedMotion = rememberArtistReducedMotion()
    val spatialFloatSpec = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.fastSpatialSpec()
    val defaultSpatialSpec = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.defaultSpatialSpec()
    val wideLayout = LocalConfiguration.current.screenWidthDp >= 520 && !slimNav
    val containerColor = if (pureBlack) Color(0xFF121212) else colorScheme.surfaceContainerHigh
    val contentColor = if (pureBlack) Color.White else colorScheme.onSurface
    val borderColor = colorScheme.outlineVariant.copy(alpha = if (pureBlack) 0.28f else 0.36f)
    val selectedIndex = items.indexOfFirst(isSelected)
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    val activeItemPressed = selectedIndex >= 0 && pressedIndex == selectedIndex
    val pillColor = colorScheme.secondaryContainer

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomInset + 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(if (slimNav) 56.dp else 64.dp),
            shape = RoundedCornerShape(32.dp),
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 8.dp,
        ) {
            var trackSize by remember { mutableStateOf(IntSize.Zero) }
            val trackWidthPx = trackSize.width.toFloat()
            val pillHeightPx = trackSize.height.toFloat()
            val spacingPx = with(density) { 6.dp.toPx() }
            val isRtl = layoutDirection == LayoutDirection.Rtl
            val targetBounds = if (selectedIndex >= 0 && trackWidthPx > 0f) {
                computeFloatingPillBoundsPx(
                    trackWidthPx = trackWidthPx,
                    itemCount = items.size,
                    selectedIndex = selectedIndex,
                    spacingPx = spacingPx,
                    isRtl = isRtl,
                )
            } else {
                null
            }
            val fallbackHeightPx = with(density) { (if (slimNav) 44.dp else 52.dp).toPx() }
            val resolvedHeightPx = if (pillHeightPx > 0f) pillHeightPx else fallbackHeightPx
            val targetCornerPx = if (activeItemPressed) {
                with(density) { 14.dp.toPx() }
            } else {
                resolvedHeightPx / 2f
            }
            val pillState = rememberStretchyPillController(
                targetBounds = targetBounds,
                targetCornerPx = targetCornerPx,
                reducedMotion = reducedMotion,
                cornerSpec = spatialFloatSpec,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .onSizeChanged { trackSize = it },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            if (selectedIndex >= 0 && pillState.initialized && size.height > 0f) {
                                val left = pillState.leftPx.value
                                val right = pillState.rightPx.value
                                val width = (right - left).coerceAtLeast(0f)
                                val corner = pillState.cornerPx.value.coerceAtLeast(0f)
                                if (width > 0f) {
                                    drawRoundRect(
                                        color = pillColor,
                                        topLeft = Offset(left, 0f),
                                        size = Size(width, size.height),
                                        cornerRadius = CornerRadius(corner, corner),
                                    )
                                }
                            }
                        }
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, screen ->
                        val selected = index == selectedIndex
                        FloatingNavTabItem(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            screen = screen,
                            selected = selected,
                            slimNav = slimNav,
                            pureBlack = pureBlack,
                            wideLayout = wideLayout,
                            reducedMotion = reducedMotion,
                            spatialFloatSpec = spatialFloatSpec,
                            defaultSpatialSpec = defaultSpatialSpec,
                            onPressedChange = { isPressed ->
                                if (isPressed) {
                                    pressedIndex = index
                                } else if (pressedIndex == index) {
                                    pressedIndex = null
                                }
                            },
                            onClick = { onItemClick(screen) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavTabItem(
    screen: Screens,
    selected: Boolean,
    slimNav: Boolean,
    pureBlack: Boolean,
    wideLayout: Boolean,
    reducedMotion: Boolean,
    spatialFloatSpec: FiniteAnimationSpec<Float>,
    defaultSpatialSpec: FiniteAnimationSpec<Float>,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(pressed) {
        onPressedChange(pressed)
    }

    val motion = rememberNavItemSelectionMotion(
        selected = selected,
        reducedMotion = reducedMotion,
        activeSpec = defaultSpatialSpec,
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (pressed) 14.dp else 26.dp,
        animationSpec = if (reducedMotion) snap<Dp>() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "floatingTabCorner_${screen.route}",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spatialFloatSpec,
        label = "floatingTabScale_${screen.route}",
    )
    val pressOverlayColor by animateColorAsState(
        targetValue = if (pressed && !selected) {
            colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
        } else {
            Color.Transparent
        },
        label = "floatingTabPressBg_${screen.route}",
    )
    val unselectedContentColor = if (pureBlack) Color.White.copy(alpha = 0.72f) else colorScheme.onSurfaceVariant
    val labelColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onSecondaryContainer else unselectedContentColor,
        label = "floatingTabContent_${screen.route}",
    )

    val itemShape = RoundedCornerShape(cornerRadius)
    val titleText = stringResource(screen.titleId)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(itemShape)
            .background(pressOverlayColor)
            .tvFocusable(shape = CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = ripple(),
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            slimNav -> {
                ExpressiveNavTabIcon(
                    screen = screen,
                    motion = motion,
                    pressScale = 1f,
                    iconSize = 24.dp,
                    activeLiftDp = 0.dp,
                    selectedTint = colorScheme.onSecondaryContainer,
                    unselectedTint = unselectedContentColor,
                    contentDescription = titleText,
                )
            }

            wideLayout -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpressiveNavTabIcon(
                        screen = screen,
                        motion = motion,
                        pressScale = 1f,
                        iconSize = 22.dp,
                        activeLiftDp = 0.dp,
                        selectedTint = colorScheme.onSecondaryContainer,
                        unselectedTint = unselectedContentColor,
                        contentDescription = null,
                    )
                    Text(
                        text = titleText,
                        color = labelColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            val s = 0.96f + 0.04f * motion.activeProgress.value.coerceIn(0f, 1.15f)
                            scaleX = s
                            scaleY = s
                        },
                    )
                }
            }

            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ExpressiveNavTabIcon(
                        screen = screen,
                        motion = motion,
                        pressScale = 1f,
                        iconSize = 21.dp,
                        activeLiftDp = 1.dp,
                        selectedTint = colorScheme.onSecondaryContainer,
                        unselectedTint = unselectedContentColor,
                        contentDescription = null,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = titleText,
                        color = labelColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            val s = 0.96f + 0.04f * motion.activeProgress.value.coerceIn(0f, 1.15f)
                            scaleX = s
                            scaleY = s
                        },
                    )
                }
            }
        }
    }
}
