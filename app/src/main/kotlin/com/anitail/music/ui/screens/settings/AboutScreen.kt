package com.anitail.music.ui.screens.settings

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.music.BuildConfig
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.utils.backToMain
import kotlinx.coroutines.delay

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
    val items: List<CardItem>
) : AboutItem

data class TeamSectionItem(
    override val id: String,
    val title: String,
    @param:DrawableRes val iconRes: Int,
    val members: List<TeamMember>
) : AboutItem

data class BuildInfo(
    val buildType: String,
    val versionName: String,
    val versionCode: Int,
    val deviceInfo: String
)

data class CardItem(
    val title: String,
    val subtitle: String,
    @param:DrawableRes val iconRes: Int,
    val url: String
)

data class TeamMember(
    val id: String,
    val name: String,
    @param:DrawableRes val avatarRes: Int? = null,
    val avatarUrl: String? = null,
    val role: String,
    val url: String
)

// ==================== DSL CONSTRUCTOR ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenDSL(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    content: @Composable AboutScope.() -> Unit
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
        block: SectionScope.() -> Unit
    ) {
        val sectionScope = SectionScope()
        sectionScope.block()
        items.add(SectionItem(id, title, iconRes, sectionScope.cards))
    }

    fun teamSection(
        id: String,
        title: String,
        @DrawableRes iconRes: Int,
        block: TeamScope.() -> Unit
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
        url: String
    ) {
        members.add(TeamMember(id, name, avatarRes, avatarUrl, role, url))
    }
}

// ==================== PANTALLA PRINCIPAL ====================
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    items: List<AboutItem>? = null
) {
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyGridState()

    // Use composable creator when items not provided
    val actualItems = items ?: createDefaultItems()

    // Animación de entrada escalonada
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.about),
                        modifier = Modifier.alpha(animateFloatAsState(targetValue = if (isVisible) 1f else 0f).value)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                        modifier = Modifier.alpha(animateFloatAsState(targetValue = if (isVisible) 1f else 0f).value)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = actualItems,
                    key = { it.id },
                    contentType = { it::class.simpleName }
                ) { item ->
                    StaggeredAnimation(
                        listState = listState,
                        index = actualItems.indexOf(item)
                    ) {
                        when (item) {
                            is HeaderItem -> HeaderCard(item.buildInfo)
                            is SectionItem -> SectionCard(item, uriHandler)
                            is TeamSectionItem -> TeamGrid(item, uriHandler)
                        }
                    }
                }
            }
        }
    }
}

// ==================== COMPONENTES PREMIUM ====================

@Composable
fun StaggeredAnimation(
    listState: LazyGridState,
    index: Int,
    content: @Composable () -> Unit
) {
    // Watch if this item is currently visible in the lazy grid
    val isVisibleNow by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == index } }
    }

    // Keep it visible once it has been visible at least once (avoid flickering)
    var hasBeenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisibleNow) {
        if (isVisibleNow) hasBeenVisible = true
    }

    // Compute a small stagger based on index relative to the first visible item to create a flowing animation
    val firstVisible =
        listState.layoutInfo.visibleItemsInfo.minByOrNull { it.index }?.index ?: index
    val relativeIndex = (index - firstVisible).coerceAtLeast(0)
    val delay =
        relativeIndex * 40 + 40 // minimal delay so that items appear in order as they show up

    AnimatedVisibility(
        visible = hasBeenVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 350, delayMillis = delay)) +
                slideInVertically(
                    animationSpec = tween(
                        durationMillis = 350,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    )
                ) { fullHeight -> fullHeight / 10 },
        exit = fadeOut(animationSpec = tween(200))
    ) {
        content()
    }
}

@Composable
fun HeaderCard(buildInfo: BuildInfo) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { _: Offset ->
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        ) {
            // Logo animado con pulso
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition("pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "pulse"
                )

                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ani),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // Efecto shimmer sutil
                        Surface(
                            modifier = Modifier.matchParentSize(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent
                        ) {
                            ShimmerOverlay()
                        }
                    }
                }
            }

            // Información de build
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BuildInfoRow(Icons.Rounded.Build, "Build", buildInfo.buildType)
                BuildInfoRow(Icons.Rounded.Verified, "Version", buildInfo.versionName)
                BuildInfoRow(Icons.Rounded.Devices, "Device", buildInfo.deviceInfo)
            }
        }
    }
}

@Composable
fun ShimmerOverlay() {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0f),
        Color.White.copy(alpha = 0.3f),
        Color.White.copy(alpha = 0f)
    )
    val transition = rememberInfiniteTransition("shimmer")
    val translate by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ), label = "shimmer"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(translate * size.width, 0f),
                end = Offset(translate * size.width + size.width / 2, 0f)
            ),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
fun BuildInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionCard(section: SectionItem, uriHandler: UriHandler) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header de sección
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    painter = painterResource(section.iconRes),
                    contentDescription = section.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Cards de links
            section.items.forEach { item ->
                LinkCardItem(item, uriHandler)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun LinkCardItem(item: CardItem, uriHandler: UriHandler) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { _: Offset ->
                        isPressed = true; tryAwaitRelease(); isPressed = false
                    },
                    onTap = { uriHandler.openUri(item.url.trim()) }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = item.title,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = "Open link",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TeamGrid(section: TeamSectionItem, uriHandler: UriHandler) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    painter = painterResource(section.iconRes),
                    contentDescription = section.title,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "${section.members.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Grid de miembros
            val columns = if (LocalConfiguration.current.screenWidthDp < 500) 2 else 5
            val rows = section.members.chunked(columns)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { member ->
                        TeamMemberCard(
                            member = member,
                            onClick = { uriHandler.openUri(member.url.trim()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun TeamMemberCard(
    member: TeamMember,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    Column(
        modifier = modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { _: Offset ->
                        isPressed = true; tryAwaitRelease(); isPressed = false
                    },
                    onTap = { _: Offset -> onClick() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            shadowElevation = if (isPressed) 2.dp else 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.size(64.dp)) {
                when {
                    member.avatarUrl != null -> {
                        AsyncImage(
                            model = member.avatarUrl.trim(),
                            contentDescription = member.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    member.avatarRes != null -> {
                        Icon(
                            painter = painterResource(member.avatarRes),
                            contentDescription = member.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Indicador online (para desarrollador)
                if (member.id == "dev") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
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
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = member.role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
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
            deviceInfo = "${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})"
        )
    ),

    SectionItem(
        id = "links",
        title = stringResource(R.string.links_about),
        iconRes = R.drawable.links,
        items = listOf(
            CardItem(
                title = stringResource(R.string.my_channel),
                subtitle = stringResource(R.string.my_channel_info),
                iconRes = R.drawable.discord,
                url = "https://discord.gg/fvskrQZb9j"
            ),
            CardItem(
                title = stringResource(R.string.other_apps),
                subtitle = stringResource(R.string.other_apps_info),
                iconRes = R.drawable.babel_software_apps,
                url = "https://github.com/Animetailapp"
            ),
            CardItem(
                title = stringResource(R.string.patreon),
                subtitle = stringResource(R.string.patreon_info),
                iconRes = R.drawable.patreon,
                url = "https://www.patreon.com/abydev"
            )
        )
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
                url = "https://github.com/Dark25"
            )
        )
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
                url = "https://discord.com/users/237686500567810058"
            ),
            TeamMember(
                id = "t2",
                name = "Lucia (Lú)",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/DPjf5V78/61fc6cc5422936a8fd81a913fbdf773b.png",
                url = "https://discord.com/users/553307420688908320"
            ),
            TeamMember(
                id = "t3",
                name = "ElDeLasTojas",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/mrgvc1nb/14f2a18fa8b9e553a048027375db5f81.png",
                url = "https://discord.com/users/444680132393697291"
            ),
            TeamMember(
                id = "t4",
                name = "SKHLORDKIRA",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/gnXkhnJ/be81ecb723cfd4186e85bfe81793f594.png",
                url = "https://discord.com/users/445310321717018626"
            ),
            TeamMember(
                id = "t5",
                name = "Abyss",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/TDdPq2jF/0f0f47f2a47eca3eda2a433237b4a05d.png",
                url = "https://discord.com/users/341662495301304323"
            ),
            TeamMember(
                id = "t6",
                name = "Jack",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/3YPX1wsj/dec881377d42d58473b6d988165406b6.png",
                url = "https://discord.com/users/1166985299885309954"
            ),
            TeamMember(
                id = "t7",
                name = "R4fa3l_2008",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/htmds91/b514910877f4b585309265fbe922f020.png",
                url = "https://discord.com/users/1318948121782521890"
            ),
            TeamMember(
                id = "t8",
                name = "Ryak",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/mrwz7J7K/165cbedbd96ae35c2489286c8db9777d.png",
                url = "https://discord.com/users/1075797587770228856"
            ),
            TeamMember(
                id = "t9",
                name = "LucianRC",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/LXXWGJCt/e8cdcf2c32ee7056806c5a8bfa607830.png",
                url = "https://discord.com/users/420641532446769157"
            ),
            TeamMember(
                id = "t10",
                name = "Alexx",
                role = "Beta Tester",
                avatarUrl = "https://i.ibb.co/8Dc1f67r/image.png",
                url = "https://discord.com/users/743896907184734268"
            )
        )
    )
)
