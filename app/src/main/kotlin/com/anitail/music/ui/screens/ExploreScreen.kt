@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.anitail.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.ui.screens.artist.ArtistSectionTitle
import com.anitail.music.viewmodels.ChartsViewModel
import com.anitail.music.viewmodels.ExploreViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun ExploreScreen(
    navController: NavController,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val explorePage by exploreViewModel.explorePage.collectAsState()
    val isExploreLoading by exploreViewModel.isLoading.collectAsState()
    val exploreError by exploreViewModel.error.collectAsState()

    val chartsPage by chartsViewModel.chartsPage.collectAsState()
    val isChartsLoading by chartsViewModel.isLoading.collectAsState()
    val chartsError by chartsViewModel.error.collectAsState()

    val listState = rememberLazyListState()
    var selectedChip by rememberSaveable { mutableStateOf<ExploreFilterChip?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop by backStackEntry?.savedStateHandle
        ?.getStateFlow("scrollToTop", false)?.collectAsState() ?: return

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            chartsViewModel.loadCharts()
        }
    }

    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            listState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    BackHandler(enabled = selectedChip != null) {
        selectedChip = null
    }

    var userRefreshing by remember { mutableStateOf(false) }
    val isAnyLoadingNow by rememberUpdatedState(isExploreLoading || isChartsLoading)
    LaunchedEffect(userRefreshing) {
        if (!userRefreshing) return@LaunchedEffect
        withTimeoutOrNull(1_500L) { snapshotFlow { isAnyLoadingNow }.first { it } }
        snapshotFlow { isAnyLoadingNow }.first { !it }
        userRefreshing = false
    }

    fun refreshAll() {
        exploreViewModel.refresh()
        chartsViewModel.refresh()
    }

    val itemActions = rememberExploreItemActions(
        navController = navController,
        currentSongId = mediaMetadata?.id,
    )

    val visibility = remember(selectedChip) { filterExploreVisibility(selectedChip) }
    val classifiedCharts = remember(chartsPage) {
        chartsPage?.sections.orEmpty().mapNotNull { section ->
            val kind = classifyChartSection(section) ?: return@mapNotNull null
            section to kind
        }
    }
    val newReleaseAlbums = explorePage?.newReleaseAlbums.orEmpty()
    val moodAndGenres = explorePage?.moodAndGenres.orEmpty()
    val hasAnyContent = classifiedCharts.isNotEmpty() || newReleaseAlbums.isNotEmpty() || moodAndGenres.isNotEmpty()

    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val topPadding = insets.calculateTopPadding() + 6.dp
    val bottomPadding = insets.calculateBottomPadding() + 24.dp
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = userRefreshing,
        onRefresh = {
            userRefreshing = true
            refreshAll()
        },
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = refreshState,
                isRefreshing = userRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = insets.calculateTopPadding()),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!hasAnyContent && (isExploreLoading || isChartsLoading)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
            ) {
                ExploreExpressiveShimmer()
            }
        } else if (!hasAnyContent && (exploreError != null || chartsError != null)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = exploreError ?: chartsError ?: stringResource(R.string.error_unknown),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { refreshAll() },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "explore_filter_chips", contentType = "chips") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExploreFilterChipShelf(
                            selectedFilter = selectedChip,
                            onSelectionChange = { selectedChip = it },
                        )
                        if (selectedChip == null) {
                            ExploreQuickAccessRow(
                                onChartsClick = { navController.navigate("charts_screen") },
                                onNewReleasesClick = { navController.navigate("new_release") },
                                onMoodsAndGenresClick = { navController.navigate("mood_and_genres") },
                            )
                        }
                    }
                }

                if (visibility.showCharts) {
                    classifiedCharts.forEachIndexed { index, (section, kind) ->
                        item(
                            key = "chart_section_${index}_${section.title}",
                            contentType = "chart_shelf",
                        ) {
                            ExploreChartSectionShelf(
                                section = section,
                                kind = kind,
                                currentSongId = mediaMetadata?.id,
                                currentAlbumId = mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                navController = navController,
                                actions = itemActions,
                            )
                        }
                    }
                }

                if (visibility.showNewReleases && newReleaseAlbums.isNotEmpty()) {
                    item(key = "explore_new_releases", contentType = "album_carousel") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArtistSectionTitle(
                                title = stringResource(R.string.new_release_albums),
                                onClick = { navController.navigate("new_release") },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            ExploreMultiBrowseShelf(
                                items = newReleaseAlbums,
                                currentSongId = mediaMetadata?.id,
                                currentAlbumId = mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                onItemClick = itemActions.onItemClick,
                                onItemMenu = itemActions.onItemMenu,
                                onAlbumPlay = itemActions.onAlbumPlay,
                            )
                        }
                    }
                }

                if (visibility.showMoodsAndGenres && moodAndGenres.isNotEmpty()) {
                    item(key = "explore_mood_genres", contentType = "mood_genres") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArtistSectionTitle(
                                title = stringResource(R.string.mood_and_genres),
                                onClick = { navController.navigate("mood_and_genres") },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            ExploreMoodGenreCarousel(
                                items = moodAndGenres,
                                onItemClick = { item ->
                                    navController.navigate(
                                        "youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}",
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
