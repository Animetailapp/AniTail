package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.shimmer.ShimmerHost
import com.anitail.innertube.YouTube
import com.anitail.innertube.pages.MoodAndGenres
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MoodAndGenresButtonHeight = 48.dp

/**
 * MoodAndGenresScreen displays mood and genre categories for browsing.
 * Mirrors the Android MoodAndGenresScreen functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodAndGenresScreen(
    onBack: () -> Unit,
    onCategoryClick: (browseId: String, params: String?, title: String) -> Unit,
) {
    var moodAndGenresList by remember { mutableStateOf<List<MoodAndGenres>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (moodAndGenresList == null) {
            scope.launch {
                isLoading = true
                withContext(Dispatchers.IO) {
                    YouTube.moodAndGenres().onSuccess { list ->
                        moodAndGenresList = list
                    }
                }
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = IconAssets.arrowBack(),
                    contentDescription = "Volver",
                )
            }
            Text(
                text = "Estados de ánimo y géneros",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        if (isLoading || moodAndGenresList == null) {
            ShimmerHost(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    repeat(8) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                moodAndGenresList?.forEach { moodAndGenres ->
                    item {
                        NavigationTitle(title = moodAndGenres.title)
                    }

                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            // 3 items per row for desktop
                            moodAndGenres.items.chunked(3).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    row.forEach { item ->
                                        MoodAndGenresButton(
                                            title = item.title,
                                            onClick = {
                                                onCategoryClick(
                                                    item.endpoint.browseId,
                                                    item.endpoint.params,
                                                    item.title,
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 4.dp),
                                        )
                                    }

                                    // Fill remaining space if row is incomplete
                                    repeat(3 - row.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodAndGenresButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(MoodAndGenresButtonHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
