package com.anitail.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

object IconAssets {
    @Composable
    fun history(): ImageVector = Icons.Filled.History

    @Composable
    fun stats(): ImageVector = Icons.Filled.Insights

    @Composable
    fun search(): ImageVector = Icons.Filled.Search

    @Composable
    fun settings(): ImageVector = Icons.Filled.Settings

    @Composable
    fun repeat(): ImageVector = Icons.Filled.Repeat

    @Composable
    fun repeatOne(): ImageVector = Icons.Filled.RepeatOne

    @Composable
    fun previous(): ImageVector = Icons.Filled.SkipPrevious

    @Composable
    fun next(): ImageVector = Icons.Filled.SkipNext

    @Composable
    fun shuffle(): ImageVector = Icons.Filled.Shuffle

    @Composable
    fun play(): ImageVector = Icons.Filled.PlayArrow

    @Composable
    fun pause(): ImageVector = Icons.Filled.Pause

    @Composable
    fun favorite(): ImageVector = Icons.Filled.Favorite

    @Composable
    fun favoriteBorder(): ImageVector = Icons.Filled.FavoriteBorder
}
