@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.anitail.music.ui.screens.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberBottomSheetState as rememberM3BottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.music.BuildConfig
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.ui.component.connectedStripItemCorners
import com.anitail.music.ui.component.deterministicExpressivePolygonIndex
import com.anitail.music.ui.component.rememberExpressivePolygonShape
import com.anitail.music.ui.component.segmentedGroupShape
import com.anitail.music.ui.component.segmentedListItemCorners
import com.anitail.music.ui.component.toRoundedCornerShape
import com.anitail.music.ui.utils.tvFocusable

// ==================== MODELO DE DOMINIO ====================

sealed interface AboutItem {
    val id: String
}

data class HeaderItem(val buildInfo: BuildInfo) : AboutItem {
    override val id = "header"
}

data class SectionItem(
    override val id: String,
    val title: String,
    @param:DrawableRes val iconRes: Int,
    val items: List<CardItem>,
) : AboutItem

data class TeamSectionItem(
    override val id: String,
    val title: String,
    @param:DrawableRes val iconRes: Int,
    val members: List<TeamMember>,
) : AboutItem

data class BuildInfo(
    val buildType: String,
    val versionName: String,
    val versionCode: Int,
    val deviceInfo: String,
)

data class CardItem(
    val title: String,
    val subtitle: String,
    @param:DrawableRes val iconRes: Int,
    val url: String,
)

data class TeamMember(
    val id: String,
    val name: String,
    @param:DrawableRes val avatarRes: Int? = null,
    val avatarUrl: String? = null,
    val role: String,
    val url: String,
)

// ==================== DSL CONSTRUCTOR ====================

@Composable
fun AboutScreenDSL(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    content: @Composable AboutScope.() -> Unit,
) {
    val scope = AboutScope()
    scope.content()
    AboutScreen(navController = navController, scrollBehavior = scrollBehavior, items = scope.items)
}

class AboutScope {
    internal val items = mutableListOf<AboutItem>()

    fun header(buildInfo: BuildInfo) {
        items.add(HeaderItem(buildInfo))
    }

    fun section(
        id: String,
        title: String,
        @DrawableRes iconRes: Int,
        block: SectionScope.() -> Unit,
    ) {
        val sectionScope = SectionScope()
        sectionScope.block()
        items.add(SectionItem(id, title, iconRes, sectionScope.cards))
    }

    fun teamSection(
        id: String,
        title: String,
        @DrawableRes iconRes: Int,
        block: TeamScope.() -> Unit,
    ) {
        val teamScope = TeamScope()
        teamScope.block()
        items.add(TeamSectionItem(id, title, iconRes, teamScope.members))
    }
}

class SectionScope {
    internal val cards = mutableListOf<CardItem>()

    fun card(title: String, subtitle: String, @DrawableRes iconRes: Int, url: String) {
        cards.add(CardItem(title, subtitle, iconRes, url))
    }
}

class TeamScope {
    internal val members = mutableListOf<TeamMember>()

    fun member(
        id: String,
        name: String,
        role: String,
        @DrawableRes avatarRes: Int? = null,
        avatarUrl: String? = null,
        url: String,
    ) {
        members.add(TeamMember(id, name, avatarRes, avatarUrl, role, url))
    }
}

private fun UriHandler.openUriSafely(url: String) {
    try {
        openUri(url.trim())
    } catch (_: Exception) {
    }
}

// ==================== PANTALLA PRINCIPAL ====================

@Composable
fun AboutScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    items: List<AboutItem>? = null,
) {
    val uriHandler = LocalUriHandler.current
    val colorScheme = MaterialTheme.colorScheme
    val flexibleScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val actualItems = items ?: createDefaultItems()
    val bottomPlayerInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    var selectedMember by remember { mutableStateOf<TeamMember?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(flexibleScrollBehavior.nestedScrollConnection),
        containerColor = colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    scrolledContainerColor = colorScheme.surfaceContainerHigh,
                ),
                scrollBehavior = flexibleScrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = bottomPlayerInset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            actualItems.forEach { item ->
                when (item) {
                    is HeaderItem -> HeaderCard(item.buildInfo)
                    is SectionItem -> SectionCard(item, uriHandler)
                    is TeamSectionItem -> TeamGrid(
                        section = item,
                        uriHandler = uriHandler,
                        onMemberClick = { selectedMember = it },
                    )
                }
            }

            Text(
                text = "${stringResource(R.string.app_name)} • GPL-3.0",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button) {
                        uriHandler.openUriSafely("https://github.com/Animetailapp/AniTail/blob/master/LICENSE")
                    }
                    .padding(16.dp),
            )
        }
    }

    selectedMember?.let { member ->
        TeamMemberInfoSheet(
            member = member,
            onDismiss = { selectedMember = null },
            onOpenUrl = uriHandler::openUriSafely,
        )
    }
}

// ==================== COMPONENTES MATERIAL 3 EXPRESSIVE ====================

@Composable
fun HeaderCard(buildInfo: BuildInfo) {
    val colorScheme = MaterialTheme.colorScheme
    val logo = painterResource(R.drawable.ic_ani)
    val abiLabel = remember {
        Build.SUPPORTED_ABIS.firstOrNull()?.uppercase() ?: "ANDROID"
    }
    val buildModeLabel = if (BuildConfig.DEBUG) "Debug" else "Release"

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .logoStripes(logo = logo, tint = colorScheme.primary.copy(alpha = 0.08f))
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Surface(
                onClick = {},
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.primaryContainer,
                modifier = Modifier.size(112.dp),
            ) {
                Image(
                    painter = logo,
                    contentDescription = stringResource(R.string.app_name),
                    colorFilter = ColorFilter.tint(colorScheme.onPrimaryContainer, BlendMode.SrcIn),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InfoBadge(
                    label = buildInfo.versionName,
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurfaceVariant,
                )
                InfoBadge(
                    label = abiLabel,
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurfaceVariant,
                )
                InfoBadge(
                    label = buildModeLabel,
                    containerColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private fun Modifier.logoStripes(
    logo: Painter,
    tint: Color,
): Modifier =
    drawWithCache {
        val iconSize = 20.dp.toPx()
        val columnSpacing = 36.dp.toPx()
        val rowSpacing = 56.dp.toPx()
        val colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)
        val reach = size.width + size.height
        onDrawBehind {
            clipRect {
                rotate(-45f) {
                    var row = 0
                    var y = center.y - reach
                    while (y < center.y + reach) {
                        var x = center.x - reach + if (row % 2 == 0) 0f else columnSpacing / 2f
                        while (x < center.x + reach) {
                            translate(x, y) {
                                with(logo) { draw(Size(iconSize, iconSize), colorFilter = colorFilter) }
                            }
                            x += columnSpacing
                        }
                        y += rowSpacing
                        row++
                    }
                }
            }
        }
    }

@Composable
private fun InfoBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun SectionCard(
    section: SectionItem,
    uriHandler: UriHandler,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (section.items.size in 1..3) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            section.items.forEachIndexed { index, item ->
                val shape = connectedStripItemCorners(
                    index = index,
                    count = section.items.size,
                ).toRoundedCornerShape()
                Surface(
                    shape = shape,
                    color = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .height(86.dp)
                        .tvFocusable(shape = shape)
                        .clip(shape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button,
                        ) {
                            uriHandler.openUriSafely(item.url)
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp)
                    .semantics { heading() },
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                section.items.forEachIndexed { index, item ->
                    val shape = segmentedListItemCorners(
                        index = index,
                        count = section.items.size,
                    ).toRoundedCornerShape()
                    Surface(
                        shape = shape,
                        color = colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable(role = Role.Button) {
                                    uriHandler.openUriSafely(item.url)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = null,
                                    tint = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val symmetricTeamMemberPolygons by lazy {
    listOf(
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Cookie12Sided,
    )
}

@Composable
private fun memberAvatarShape(member: TeamMember): Shape {
    val polygon = if (member.id == "dev") {
        MaterialShapes.Cookie9Sided
    } else {
        symmetricTeamMemberPolygons[
            deterministicExpressivePolygonIndex(
                seed = "${member.id}:${member.name}",
                poolSize = symmetricTeamMemberPolygons.size,
            ),
        ]
    }
    return rememberExpressivePolygonShape(polygon)
}

@Composable
private fun MemberAvatar(
    member: TeamMember,
    size: Dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = memberAvatarShape(member)
    Surface(
        modifier = Modifier.size(size),
        shape = shape,
        color = colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
    ) {
        when {
            member.avatarUrl != null -> {
                AsyncImage(
                    model = member.avatarUrl.trim(),
                    contentDescription = member.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                )
            }

            member.avatarRes != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(member.avatarRes),
                        contentDescription = member.name,
                        modifier = Modifier.size(size * 0.55f),
                        tint = colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun TeamGrid(
    section: TeamSectionItem,
    uriHandler: UriHandler,
    onMemberClick: (TeamMember) -> Unit = { uriHandler.openUriSafely(it.url) },
) {
    val colorScheme = MaterialTheme.colorScheme
    val avatarSize = if (section.members.size == 1) 64.dp else 48.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp)
                .semantics { heading() },
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            section.members.forEachIndexed { index, member ->
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val itemShape = segmentedGroupShape(
                    isFirst = index == 0,
                    isLast = index == section.members.lastIndex,
                    pressed = pressed,
                    outer = 20.dp,
                    inner = 5.dp,
                )
                val containerColor by animateColorAsState(
                    targetValue = if (pressed) colorScheme.surfaceContainerHighest else colorScheme.surfaceContainer,
                    label = "teamRowBg_${member.id}",
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = itemShape,
                    color = containerColor,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .tvFocusable(shape = itemShape)
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                onClick = { onMemberClick(member) },
                                onLongClick = { uriHandler.openUriSafely(member.url) },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MemberAvatar(member = member, size = avatarSize)

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = member.role,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMemberInfoSheet(
    member: TeamMember,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberM3BottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MemberAvatar(member = member, size = 72.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = member.role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button) {
                        onDismiss()
                        onOpenUrl(member.url)
                    },
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    when {
                        member.url.contains("github.com", ignoreCase = true) -> {
                            Icon(
                                painter = painterResource(R.drawable.github),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        member.url.contains("discord", ignoreCase = true) -> {
                            Icon(
                                painter = painterResource(R.drawable.discord),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        else -> {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.open_profile),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (member.id == "dev") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) {
                            onDismiss()
                            onOpenUrl("https://www.patreon.com/abydev")
                        },
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.patreon),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.patreon),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ==================== DATOS POR DEFECTO ====================

@Composable
fun createDefaultItems(): List<AboutItem> = listOf(
    HeaderItem(
        BuildInfo(
            buildType = BuildConfig.BUILD_TYPE,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            deviceInfo = "${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})",
        ),
    ),

    SectionItem(
        id = "links",
        title = stringResource(R.string.links_about),
        iconRes = R.drawable.links,
        items = listOf(
            CardItem(
                title = "Discord",
                subtitle = stringResource(R.string.my_channel_info),
                iconRes = R.drawable.discord,
                url = "https://discord.gg/fvskrQZb9j",
            ),
            CardItem(
                title = "GitHub",
                subtitle = stringResource(R.string.other_apps_info),
                iconRes = R.drawable.github,
                url = "https://github.com/Animetailapp",
            ),
            CardItem(
                title = stringResource(R.string.patreon),
                subtitle = stringResource(R.string.patreon_info),
                iconRes = R.drawable.patreon,
                url = "https://www.patreon.com/abydev",
            ),
        ),
    ),

    TeamSectionItem(
        id = "dev",
        title = stringResource(R.string.developer_about),
        iconRes = R.drawable.person,
        members = listOf(
            TeamMember(
                id = "dev",
                name = "[̲̅A̲̅][̲̅b̲̅][̲̅y̲̅]",
                role = stringResource(R.string.info_dev),
                avatarUrl = "https://avatars.githubusercontent.com/u/21024973?v=4",
                url = "https://github.com/Dark25",
            ),
        ),
    ),

    TeamSectionItem(
        id = "testers",
        title = stringResource(R.string.beta_testers),
        iconRes = R.drawable.person,
        members = listOf(
            TeamMember(
                id = "t1",
                name = "im.shoul",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/pjkzBvGn/image.png",
                url = "https://discord.com/users/237686500567810058",
            ),
            TeamMember(
                id = "t2",
                name = "Lucia (Lú)",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/DPjf5V78/61fc6cc5422936a8fd81a913fbdf773b.png",
                url = "https://discord.com/users/553307420688908320",
            ),
            TeamMember(
                id = "t3",
                name = "ElDeLasTojas",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/mrgvc1nb/14f2a18fa8b9e553a048027375db5f81.png",
                url = "https://discord.com/users/444680132393697291",
            ),
            TeamMember(
                id = "t4",
                name = "SKHLORDKIRA",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/gnXkhnJ/be81ecb723cfd4186e85bfe81793f594.png",
                url = "https://discord.com/users/445310321717018626",
            ),
            TeamMember(
                id = "t5",
                name = "Abyss",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/TDdPq2jF/0f0f47f2a47eca3eda2a433237b4a05d.png",
                url = "https://discord.com/users/341662495301304323",
            ),
            TeamMember(
                id = "t6",
                name = "Jack",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/3YPX1wsj/dec881377d42d58473b6d988165406b6.png",
                url = "https://discord.com/users/1166985299885309954",
            ),
            TeamMember(
                id = "t7",
                name = "R4fa3l_2008",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/htmds91/b514910877f4b585309265fbe922f020.png",
                url = "https://discord.com/users/1318948121782521890",
            ),
            TeamMember(
                id = "t8",
                name = "Ryak",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/mrwz7J7K/165cbedbd96ae35c2489286c8db9777d.png",
                url = "https://discord.com/users/1075797587770228856",
            ),
            TeamMember(
                id = "t9",
                name = "LucianRC",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/LXXWGJCt/e8cdcf2c32ee7056806c5a8bfa607830.png",
                url = "https://discord.com/users/420641532446769157",
            ),
            TeamMember(
                id = "t10",
                name = "Alexx",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/8Dc1f67r/image.png",
                url = "https://discord.com/users/743896907184734268",
            ),
        ),
    ),
)
