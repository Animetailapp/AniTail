package com.anitail.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.music.R
import com.anitail.music.ui.utils.tvClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.IconButton as M3IconButton

private data class GithubRelease(
    val title: String,
    val tagName: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
)

private data class GithubCommit(
    val message: String,
    val authorName: String,
    val date: String,
    val htmlUrl: String,
    val shortSha: String,
)

private enum class ChangelogTab {
    RELEASES,
    COMMITS,
}

private data class ChangelogSheetState(
    val isLoadingReleases: Boolean = true,
    val isLoadingCommits: Boolean = true,
    val releases: List<GithubRelease> = emptyList(),
    val commits: List<GithubCommit> = emptyList(),
    val releasesError: String? = null,
    val commitsError: String? = null,
    val lastUpdated: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogBottomSheet(
    onDismissRequest: () -> Unit,
    repositoryOwner: String = "Animetailapp",
    repositoryName: String = "Anitail",
) {
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(ChangelogTab.RELEASES) }
    var state by remember { mutableStateOf(ChangelogSheetState()) }

    fun loadChangelog() {
        coroutineScope.launch {
            state = state.copy(
                isLoadingReleases = true,
                isLoadingCommits = true,
                releasesError = null,
                commitsError = null,
            )

            val (releasesResult, commitsResult) = coroutineScope {
                val releasesDeferred = async {
                    runCatching { fetchGithubReleases(repositoryOwner, repositoryName) }
                }
                val commitsDeferred = async {
                    runCatching { fetchGithubCommits(repositoryOwner, repositoryName) }
                }
                releasesDeferred.await() to commitsDeferred.await()
            }

            state = state.copy(
                isLoadingReleases = false,
                isLoadingCommits = false,
                releases = releasesResult.getOrElse { emptyList() },
                commits = commitsResult.getOrElse { emptyList() },
                releasesError = releasesResult.exceptionOrNull()?.message,
                commitsError = commitsResult.exceptionOrNull()?.message,
                lastUpdated = currentTimestamp(),
            )
        }
    }

    LaunchedEffect(repositoryOwner, repositoryName) {
        loadChangelog()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.changelog),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(
                            visible = state.lastUpdated != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.changelog_last_updated,
                                    state.lastUpdated.orEmpty()
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    M3IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.history),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.changelog_sheet_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                ChangelogTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        (fadeIn() + slideInVertically(initialOffsetY = { it / 8 })) togetherWith
                            (fadeOut() + slideOutVertically(targetOffsetY = { -it / 8 }))
                    },
                    label = "changelog_tab_content"
                ) { tab ->
                    when (tab) {
                        ChangelogTab.RELEASES -> {
                            ChangelogReleasesContent(
                                isLoading = state.isLoadingReleases,
                                error = state.releasesError,
                                releases = state.releases,
                                onRetry = ::loadChangelog,
                                onOpenLink = uriHandler::openUri
                            )
                        }

                        ChangelogTab.COMMITS -> {
                            ChangelogCommitsContent(
                                isLoading = state.isLoadingCommits,
                                error = state.commitsError,
                                commits = state.commits,
                                onRetry = ::loadChangelog,
                                onOpenLink = uriHandler::openUri
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogTabs(
    selectedTab: ChangelogTab,
    onTabSelected: (ChangelogTab) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ChangelogTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    label = "changelog_tab_bg"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "changelog_tab_label"
                )
                val tabElevation by animateDpAsState(
                    targetValue = if (isSelected) 1.dp else 0.dp,
                    label = "changelog_tab_elevation"
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .tvClickable { onTabSelected(tab) },
                    color = containerColor,
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = tabElevation
                ) {
                    Text(
                        text = when (tab) {
                            ChangelogTab.RELEASES -> stringResource(R.string.changelog_tab_releases)
                            ChangelogTab.COMMITS -> stringResource(R.string.changelog_tab_commits)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = labelColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangelogReleasesContent(
    isLoading: Boolean,
    error: String?,
    releases: List<GithubRelease>,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    when {
        isLoading -> {
            ChangelogLoadingState(message = stringResource(R.string.changelog_loading))
        }

        error != null -> {
            ChangelogErrorState(
                message = error,
                onRetry = onRetry
            )
        }

        releases.isEmpty() -> {
            ChangelogEmptyState(message = stringResource(R.string.changelog_empty_releases))
        }

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                releases.forEach { release ->
                    ReleaseCardItem(
                        release = release,
                        onOpenLink = onOpenLink
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseCardItem(
    release: GithubRelease,
    onOpenLink: (String) -> Unit,
) {
    var expanded by rememberSaveable(release.tagName) { mutableStateOf(false) }
    val hasBody = release.body.isNotBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = release.publishedAt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = release.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (hasBody) {
                Text(
                    text = release.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (hasBody) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            text = stringResource(
                                if (expanded) R.string.collapse else R.string.expand
                            )
                        )
                    }
                }
                TextButton(onClick = { onOpenLink(release.htmlUrl) }) {
                    Text(stringResource(R.string.open_in_browser))
                }
            }
        }
    }
}

@Composable
private fun ChangelogCommitsContent(
    isLoading: Boolean,
    error: String?,
    commits: List<GithubCommit>,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    when {
        isLoading -> {
            ChangelogLoadingState(message = stringResource(R.string.changelog_loading))
        }

        error != null -> {
            ChangelogErrorState(
                message = error,
                onRetry = onRetry
            )
        }

        commits.isEmpty() -> {
            ChangelogEmptyState(message = stringResource(R.string.changelog_empty_commits))
        }

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                commits.forEach { commit ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = commit.shortSha,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = commit.message,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${commit.authorName} • ${commit.date}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { onOpenLink(commit.htmlUrl) },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(stringResource(R.string.open_in_browser))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogLoadingState(
    message: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChangelogErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.changelog_load_failed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun ChangelogEmptyState(
    message: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private suspend fun fetchGithubReleases(
    repositoryOwner: String,
    repositoryName: String,
): List<GithubRelease> = withContext(Dispatchers.IO) {
    val body = getResponseBody("https://api.github.com/repos/$repositoryOwner/$repositoryName/releases")
    val json = JSONArray(body)

    buildList {
        for (index in 0 until minOf(json.length(), 20)) {
            val release = json.getJSONObject(index)
            val title = release.optString("name").ifBlank {
                release.optString("tag_name")
            }
            add(
                GithubRelease(
                    title = title,
                    tagName = release.optString("tag_name"),
                    body = release.optString("body"),
                    publishedAt = release.optString("published_at").toShortDate(),
                    htmlUrl = release.optString("html_url"),
                )
            )
        }
    }
}

private suspend fun fetchGithubCommits(
    repositoryOwner: String,
    repositoryName: String,
): List<GithubCommit> = withContext(Dispatchers.IO) {
    val body = getResponseBody("https://api.github.com/repos/$repositoryOwner/$repositoryName/commits?per_page=30")
    val json = JSONArray(body)

    buildList {
        for (index in 0 until json.length()) {
            val commitWrapper = json.getJSONObject(index)
            val commit = commitWrapper.optJSONObject("commit")
            val author = commit?.optJSONObject("author")
            val fullMessage = commit?.optString("message").orEmpty()
            val message = fullMessage
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .trim()
            val sha = commitWrapper.optString("sha")

            add(
                GithubCommit(
                    message = message,
                    authorName = author?.optString("name").orEmpty().ifBlank { "Unknown" },
                    date = author?.optString("date").orEmpty().toShortDate(),
                    htmlUrl = commitWrapper.optString("html_url"),
                    shortSha = sha.take(7),
                )
            )
        }
    }
}

private fun getResponseBody(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "AniTail")

    return try {
        val responseCode = connection.responseCode
        val inputStream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val responseBody = inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) {
            throw IOException("HTTP $responseCode")
        }
        responseBody
    } finally {
        connection.disconnect()
    }
}

private fun String.toShortDate(): String {
    if (isBlank()) return ""
    return substringBefore('T')
}

private fun currentTimestamp(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date())
}
