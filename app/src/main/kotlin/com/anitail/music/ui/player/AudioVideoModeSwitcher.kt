@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.anitail.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.anitail.music.R

@Composable
fun AudioVideoModeSwitcher(
    useNewPlayerDesign: Boolean,
    isVideoActive: Boolean,
    isResolving: Boolean,
    contentColor: Color,
    onSongClick: () -> Unit,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songText = stringResource(R.string.song)
    val videoText = stringResource(R.string.video)

    if (!useNewPlayerDesign) {
        VintageAudioVideoModeSwitcher(
            songDescription = songText,
            videoDescription = videoText,
            isVideoActive = isVideoActive,
            isResolving = isResolving,
            contentColor = contentColor,
            onSongClick = onSongClick,
            onVideoClick = onVideoClick,
            modifier = modifier,
        )
        return
    }

    val isResolvingToVideo = isResolving && !isVideoActive
    val isResolvingToSong = isResolving && isVideoActive
    val songSelected = !isVideoActive

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState)
        },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.height(40.dp),
    ) {
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                ToggleButton(
                    interactionSource = interactionSource,
                    checked = songSelected,
                    enabled = !isResolving,
                    onCheckedChange = { onSongClick() },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    contentPadding = PaddingValues(0.dp),
                    colors =
                        ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            contentColor = contentColor,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            disabledContentColor = contentColor,
                        ),
                    modifier =
                        Modifier
                            .size(width = 46.dp, height = 40.dp)
                            .animateWidth(interactionSource),
                ) {
                    if (isResolvingToSong) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (songSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = if (songSelected) Icons.Filled.Headphones else Icons.Outlined.Headphones,
                            contentDescription = songText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            menuContent = { state ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = if (songSelected) Icons.Filled.Headphones else Icons.Outlined.Headphones,
                            contentDescription = null,
                        )
                    },
                    text = { Text(songText) },
                    enabled = !isResolving,
                    onClick = {
                        state.dismiss()
                        onSongClick()
                    },
                )
            },
        )

        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                ToggleButton(
                    interactionSource = interactionSource,
                    checked = isVideoActive,
                    enabled = !isResolving,
                    onCheckedChange = { onVideoClick() },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    contentPadding = PaddingValues(0.dp),
                    colors =
                        ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            contentColor = contentColor,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            disabledContentColor = contentColor,
                        ),
                    modifier =
                        Modifier
                            .size(width = 46.dp, height = 40.dp)
                            .animateWidth(interactionSource),
                ) {
                    if (isResolvingToVideo) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (isVideoActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = if (isVideoActive) Icons.Filled.SmartDisplay else Icons.Outlined.SmartDisplay,
                            contentDescription = videoText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            menuContent = { state ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = if (isVideoActive) Icons.Filled.SmartDisplay else Icons.Outlined.SmartDisplay,
                            contentDescription = null,
                        )
                    },
                    text = { Text(videoText) },
                    enabled = !isResolving,
                    onClick = {
                        state.dismiss()
                        onVideoClick()
                    },
                )
            },
        )
    }
}

@Composable
fun VintageAudioVideoModeSwitcher(
    songDescription: String,
    videoDescription: String,
    isVideoActive: Boolean,
    isResolving: Boolean,
    contentColor: Color,
    onSongClick: () -> Unit,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capsuleShape = RoundedCornerShape(50)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val selectedColor = contentColor
    val selectedIconColor = if (contentColor.luminance() > 0.5f) Color.Black else Color.White
    Row(
        modifier =
            modifier
                .width(96.dp)
                .height(36.dp)
                .clip(capsuleShape)
                .background(trackColor)
                .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VintageAudioVideoModeItem(
            selected = !isVideoActive,
            enabled = !isResolving,
            description = songDescription,
            icon = if (isVideoActive) Icons.Outlined.Headphones else Icons.Filled.Headphones,
            showProgress = isResolving && isVideoActive,
            contentColor = contentColor,
            selectedContentColor = selectedIconColor,
            selectedColor = selectedColor,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onSongClick,
        )
        VintageAudioVideoModeItem(
            selected = isVideoActive,
            enabled = !isResolving,
            description = videoDescription,
            icon = if (isVideoActive) Icons.Filled.SmartDisplay else Icons.Outlined.SmartDisplay,
            showProgress = isResolving && !isVideoActive,
            contentColor = contentColor,
            selectedContentColor = selectedIconColor,
            selectedColor = selectedColor,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onVideoClick,
        )
    }
}

@Composable
private fun VintageAudioVideoModeItem(
    selected: Boolean,
    enabled: Boolean,
    description: String,
    icon: ImageVector,
    showProgress: Boolean,
    contentColor: Color,
    selectedContentColor: Color,
    selectedColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val iconTint = if (selected) selectedContentColor else contentColor
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(if (selected) selectedColor else Color.Transparent)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showProgress) {
            LoadingIndicator(modifier = Modifier.size(20.dp), color = iconTint)
        } else {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

