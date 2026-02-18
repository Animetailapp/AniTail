package com.anitail.music.ui.screens

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.anitail.music.viewmodels.SoundCapsuleViewModel

@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: SoundCapsuleViewModel = hiltViewModel(),
) {
    SoundCapsuleScreen(navController = navController, viewModel = viewModel)
}

@Composable
fun StatsTimeListenedScreen(
    navController: NavController,
    year: Int,
    month: Int,
    viewModel: SoundCapsuleViewModel = hiltViewModel(),
) {
    SoundCapsuleTimeListenedScreen(
        navController = navController,
        year = year,
        month = month,
        viewModel = viewModel,
    )
}

@Composable
fun StatsTopArtistsScreen(
    navController: NavController,
    year: Int,
    month: Int,
    viewModel: SoundCapsuleViewModel = hiltViewModel(),
) {
    SoundCapsuleTopArtistsScreen(
        navController = navController,
        year = year,
        month = month,
        viewModel = viewModel,
    )
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
