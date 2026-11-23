@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.anitail.music.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anitail.music.R
import com.anitail.music.constants.ThumbnailCornerRadius
import com.anitail.music.ui.component.PlayingIndicator
import com.anitail.music.ui.screens.artist.animatedCurrentSongArtworkShape
import com.anitail.music.ui.screens.artist.artistArtworkShape
import com.anitail.music.ui.screens.artist.rememberCurrentSongArtworkRotation
import com.anitail.music.ui.utils.resize

private val ChipShelfHeight = 48.dp
private val GridTileCornerRadius = 12.dp
private val GridCaptionSafeSpace = 8.dp
private val FabScrollDistance = 48.dp

internal fun libraryCoverDrawable(type: String): Int? =
    when (type) {
        "liked" -> R.drawable.library_cover_liked
        "downloaded" -> R.drawable.library_cover_downloaded
        "top100" -> R.drawable.library_cover_top100
        "cached" -> R.drawable.library_cover_cached
        "uploaded" -> R.drawable.library_cover_uploaded
        "podcasts" -> R.drawable.library_cover_podcasts
        "episodes" -> R.drawable.library_cover_episodes_for_later
        else -> null
    }

@Composable
internal fun LibraryChipShelf(
    options: List<LibraryExpressiveFilter>,
    selectedOption: LibraryExpressiveFilter?,
    onSelectionChange: (LibraryExpressiveFilter?) -> Unit,
    label: @Composable (LibraryExpressiveFilter) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 2.dp),
    scrollState: ScrollState = rememberScrollState(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ChipShelfHeight)
            .clipToBounds()
            .focusGroup()
            .selectableGroup()
            .horizontalScroll(scrollState)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = selectedOption == option
            val text = label(option)
            val chipDescription = stringResource(R.string.chip_content_description, text)
            ToggleButton(
                checked = isSelected,
                onCheckedChange = {
                    onSelectionChange(if (isSelected) null else option)
                },
                shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier
                    .height(ButtonDefaults.ExtraSmallContainerHeight)
                    .clearAndSetSemantics {
                        contentDescription = chipDescription
                        role = Role.Button
                        selected = isSelected
                        onClick {
                            onSelectionChange(if (isSelected) null else option)
                            true
                        }
                    },
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun LibrarySortAndControlsRow(
    selectedFilter: LibraryExpressiveFilter?,
    sortConfig: LibrarySortConfig,
    sortLabel: (LibraryExpressiveSortType) -> String,
    onSortSelect: (LibraryExpressiveSortType) -> Unit,
    onDirectionToggle: () -> Unit,
    isSearchOpen: Boolean,
    onSearchToggle: () -> Unit,
    isGridView: Boolean,
    onGridViewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    val ascending = sortConfig.ascending
    val sortType = sortConfig.sort

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SplitButtonDefaults.MediumContainerHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f, fill = false)) {
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.TonalLeadingButton(
                            onClick = { sortExpanded = true },
                            shapes = SplitButtonDefaults.leadingButtonShapesFor(SplitButtonDefaults.MediumContainerHeight),
                            contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(SplitButtonDefaults.MediumContainerHeight),
                        ) {
                            Text(
                                text = sortLabel(sortType),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    trailingButton = {
                        SplitButtonDefaults.TrailingButton(
                            onClick = onDirectionToggle,
                            enabled = sortType != LibraryExpressiveSortType.MANUAL,
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            elevation = ButtonDefaults.filledTonalButtonElevation(),
                            shapes = SplitButtonDefaults.trailingButtonShapesFor(SplitButtonDefaults.MediumContainerHeight),
                            contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(SplitButtonDefaults.MediumContainerHeight),
                        ) {
                            val rotation by animateFloatAsState(
                                targetValue = if (ascending) 180f else 0f,
                                label = "librarySortDirection",
                            )
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = stringResource(
                                    if (ascending) R.string.playlist_direction_descending else R.string.playlist_direction_ascending,
                                ),
                                modifier = Modifier
                                    .size(SplitButtonDefaults.TrailingIconSize)
                                    .graphicsLayer { rotationZ = rotation },
                            )
                        }
                    },
                )

                ExpressiveLibrarySelectionMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                    options = LibraryExpressiveSortType.entries.filter { it.isAvailableFor(selectedFilter) },
                    selectedOption = sortType,
                    optionLabel = sortLabel,
                    onOptionSelected = onSortSelect,
                )
            }

            FilledTonalIconButton(
                onClick = onSearchToggle,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isSearchOpen) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (isSearchOpen) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                ),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(R.string.search),
                )
            }
        }

        val listLabel = stringResource(R.string.list_view)
        val gridLabel = stringResource(R.string.grid_view)
        ButtonGroup(
            overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            listOf(false, true).forEachIndexed { index, grid ->
                val icon = if (grid) Icons.Default.GridView else Icons.AutoMirrored.Outlined.List
                val label = if (grid) gridLabel else listLabel
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        val shapes = if (index == 0) {
                            staticConnectedLeadingButtonShapes()
                        } else {
                            staticConnectedTrailingButtonShapes()
                        }
                        ToggleButton(
                            checked = isGridView == grid,
                            interactionSource = interactionSource,
                            onCheckedChange = { onGridViewChange(grid) },
                            modifier = Modifier
                                .size(40.dp)
                                .animateWidth(interactionSource),
                            shapes = shapes,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    menuContent = { state ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(icon, contentDescription = null) },
                            text = { Text(label) },
                            onClick = {
                                state.dismiss()
                                onGridViewChange(grid)
                            },
                        )
                    },
                )
            }
        }
    }
}

private fun Shape.asStaticShape(): Shape =
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline = this@asStaticShape.createOutline(size, layoutDirection, density)
    }

@Composable
private fun staticConnectedLeadingButtonShapes(): ToggleButtonShapes {
    val shape = ButtonGroupDefaults.connectedLeadingButtonShape.asStaticShape()
    val pressShape = ButtonGroupDefaults.connectedLeadingButtonPressShape.asStaticShape()
    return ButtonGroupDefaults.connectedLeadingButtonShapes(
        shape = shape,
        pressedShape = pressShape,
        checkedShape = shape,
    )
}

@Composable
private fun staticConnectedTrailingButtonShapes(): ToggleButtonShapes {
    val shape = ButtonGroupDefaults.connectedTrailingButtonShape.asStaticShape()
    val pressShape = ButtonGroupDefaults.connectedTrailingButtonPressShape.asStaticShape()
    return ButtonGroupDefaults.connectedTrailingButtonShapes(
        shape = shape,
        pressedShape = pressShape,
        checkedShape = shape,
    )
}

@Composable
internal fun <T> ExpressiveLibrarySelectionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    options: List<T>,
    selectedOption: T?,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(0, 1),
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            options.forEachIndexed { index, option ->
                val shapes = MenuDefaults.itemShape(index, options.size)
                val onClick = {
                    onOptionSelected(option)
                    onDismissRequest()
                }
                if (selectedOption == null) {
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        shape = shapes.shape,
                        onClick = onClick,
                    )
                } else {
                    SelectableDropdownMenuItem(
                        selected = option == selectedOption,
                        onClick = onClick,
                        text = { Text(optionLabel(option)) },
                        shapes = shapes,
                        selectedLeadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExpandableLibrarySearchBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(200)),
        modifier = modifier,
    ) {
        val textFieldState = rememberTextFieldState(initialText = query)
        val focusRequester = remember { FocusRequester() }
        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        val latestQuery by rememberUpdatedState(query)
        val latestOnQueryChange by rememberUpdatedState(onQueryChange)

        LaunchedEffect(visible) {
            if (visible) focusRequester.requestFocus()
        }
        LaunchedEffect(visibility.isIdle, visibility.currentState) {
            if (visibility.isIdle && visibility.currentState) {
                bringIntoViewRequester.bringIntoView()
            }
        }
        LaunchedEffect(query) {
            if (textFieldState.text.toString() != query) {
                textFieldState.setTextAndPlaceCursorAtEnd(query)
            }
        }
        LaunchedEffect(textFieldState) {
            snapshotFlow { textFieldState.text.toString() }.collect { text ->
                if (text != latestQuery) latestOnQueryChange(text)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .bringIntoViewRequester(bringIntoViewRequester),
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    BasicTextField(
                        state = textFieldState,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(android.R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryItemContainer(
    isGridView: Boolean,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(if (isGridView) GridTileCornerRadius else ThumbnailCornerRadius)
    Row(
        modifier = Modifier
            .clip(shape)
            .then(modifier)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .then(if (isGridView) Modifier.padding(4.dp) else Modifier),
        ) {
            content()
        }
        if (!isGridView && onMenuClick != null) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.width(60.dp).height(48.dp),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                )
            }
        }
    }
}

@Composable
internal fun LibraryGridCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    fallbackIcon: ImageVector,
    artistId: String? = null,
    coverRes: Int? = null,
    localThumbnails: List<String>? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    badge: String? = null,
    isDownloaded: Boolean = false,
    reorderHandle: (@Composable BoxScope.() -> Unit)? = null,
) {
    val isArtist = artistId != null
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                LibraryArtworkSlot(
                    thumbnailUrl = thumbnailUrl,
                    fallbackIcon = fallbackIcon,
                    coverRes = coverRes,
                    localThumbnails = localThumbnails,
                    artistId = artistId,
                    iconSize = 48.dp,
                    cornerRadius = GridTileCornerRadius,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                )
            }
            Spacer(Modifier.height(GridCaptionSafeSpace))
        }

        if (badge != null) {
            LibraryBadge(
                label = badge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        } else if (isDownloaded) {
            LibraryDownloadedIcon(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
        reorderHandle?.invoke(this)
    }
}

@Composable
internal fun LibraryListCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    fallbackIcon: ImageVector,
    artistId: String? = null,
    coverRes: Int? = null,
    localThumbnails: List<String>? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    badge: String? = null,
    isDownloaded: Boolean = false,
    reorderHandle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryArtworkSlot(
            thumbnailUrl = thumbnailUrl,
            fallbackIcon = fallbackIcon,
            coverRes = coverRes,
            localThumbnails = localThumbnails,
            artistId = artistId,
            iconSize = 24.dp,
            cornerRadius = ThumbnailCornerRadius,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 17.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badge != null) {
            LibraryBadge(
                label = badge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else if (isDownloaded) {
            LibraryDownloadedIcon(modifier = Modifier.padding(horizontal = 8.dp))
        }
        reorderHandle?.invoke()
    }
}

@Composable
private fun LibraryArtworkSlot(
    thumbnailUrl: String?,
    fallbackIcon: ImageVector,
    coverRes: Int?,
    localThumbnails: List<String>?,
    artistId: String?,
    iconSize: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseShape = artistId?.let { artistArtworkShape(it) } ?: RoundedCornerShape(cornerRadius)
    val animatedShape = animatedCurrentSongArtworkShape(isCurrent = isCurrent, inactiveShape = baseShape)
    val artworkRotation by rememberCurrentSongArtworkRotation(isCurrent = isCurrent, isPlaying = isPlaying)
    val nonEmptyThumbnails = localThumbnails?.filter { it.isNotBlank() }.orEmpty()

    Box(modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = artworkRotation }
                .clip(animatedShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = -artworkRotation },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    coverRes != null -> {
                        Image(
                            painter = painterResource(coverRes),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    nonEmptyThumbnails.size >= 4 -> {
                        LocalPlaylistCollage(
                            thumbnails = nonEmptyThumbnails,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    nonEmptyThumbnails.isNotEmpty() || !thumbnailUrl.isNullOrBlank() -> {
                        val url = nonEmptyThumbnails.firstOrNull() ?: thumbnailUrl!!
                        AsyncImage(
                            model = url.resize(544, 544),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = fallbackIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }
        }
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(animatedShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                PlayingIndicator(
                    color = Color.White,
                    bars = 4,
                    modifier = Modifier.size(width = 24.dp, height = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylistCollage(
    thumbnails: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        thumbnails.take(4).chunked(2).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                row.forEach { thumbnail ->
                    AsyncImage(
                        model = thumbnail.resize(544, 544),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun LibraryDownloadedIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
    ) {
        Icon(
            painter = painterResource(R.drawable.offline),
            contentDescription = stringResource(R.string.filter_downloaded),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(4.dp)
                .size(16.dp),
        )
    }
}

@Composable
internal fun LibraryReorderMenu(
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onMoveToTop: (() -> Unit)?,
    onMoveToBottom: (() -> Unit)?,
    dragHandleModifier: Modifier = Modifier,
    onArtwork: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.then(dragHandleModifier),
            shapes = IconButtonDefaults.shapes(),
            colors = if (onArtwork) {
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                IconButtonDefaults.iconButtonColors()
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.DragIndicator,
                contentDescription = stringResource(R.string.playlist_reorder),
            )
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_to_top)) },
                    enabled = onMoveToTop != null,
                    onClick = {
                        expanded = false
                        onMoveToTop?.invoke()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_up)) },
                    enabled = onMoveUp != null,
                    onClick = {
                        expanded = false
                        onMoveUp?.invoke()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_down)) },
                    enabled = onMoveDown != null,
                    onClick = {
                        expanded = false
                        onMoveDown?.invoke()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_to_bottom)) },
                    enabled = onMoveToBottom != null,
                    onClick = {
                        expanded = false
                        onMoveToBottom?.invoke()
                    },
                )
            }
        }
    }
}

enum class ScrollingFabState {
    Expanded,
    Collapsed,
    Hidden,
}

internal data class FabScrollPosition(
    val index: Int,
    val offset: Int,
    val atStart: Boolean,
    val nearEnd: Boolean,
)

internal class FabScrollTracker(
    private val threshold: Float,
) {
    private var previous: FabScrollPosition? = null
    private var travelled = 0f
    private var expanded = true
    private var scrollingDown = false

    fun update(position: FabScrollPosition): ScrollingFabState {
        val before = previous ?: position
        previous = position
        val delta = if (position.index == before.index) {
            (position.offset - before.offset).toFloat()
        } else {
            (position.index - before.index) * threshold
        }
        if (position.atStart) {
            travelled = 0f
            expanded = true
            scrollingDown = false
        } else if (delta != 0f) {
            if ((delta > 0f) != scrollingDown) travelled = 0f
            scrollingDown = delta > 0f
            travelled += delta
            if (travelled >= threshold) expanded = false
            if (travelled <= -threshold) expanded = true
        }
        return when {
            position.nearEnd && scrollingDown && !expanded -> ScrollingFabState.Hidden
            expanded -> ScrollingFabState.Expanded
            else -> ScrollingFabState.Collapsed
        }
    }
}

@Composable
internal fun rememberScrollingFabState(gridState: LazyGridState): ScrollingFabState {
    val threshold = with(LocalDensity.current) { FabScrollDistance.toPx() }
    var state by remember(gridState, threshold) { mutableStateOf(ScrollingFabState.Expanded) }
    LaunchedEffect(gridState, threshold) {
        val tracker = FabScrollTracker(threshold)
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            FabScrollPosition(
                index = gridState.firstVisibleItemIndex,
                offset = gridState.firstVisibleItemScrollOffset,
                atStart = !gridState.canScrollBackward,
                nearEnd = last != null &&
                    last.index == info.totalItemsCount - 1 &&
                    last.offset.y + last.size.height <= info.viewportEndOffset + threshold,
            )
        }.collect { state = tracker.update(it) }
    }
    return state
}

@Composable
internal fun ScrollingLibraryAddButton(
    label: String,
    onClick: () -> Unit,
    state: ScrollingFabState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val labelSpatial = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val expanded = state == ScrollingFabState.Expanded

    AnimatedVisibility(
        visible = visible && state != ScrollingFabState.Hidden,
        modifier = modifier,
        enter = fadeIn(effects) + slideInVertically(spatial) { it / 2 },
        exit = fadeOut(effects) + slideOutVertically(spatial) { it / 2 },
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = FloatingActionButtonDefaults.shape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = label.takeUnless { expanded },
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandHorizontally(labelSpatial, expandFrom = Alignment.Start) + fadeIn(effects),
                    exit = shrinkHorizontally(labelSpatial, shrinkTowards = Alignment.Start) + fadeOut(effects),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

internal fun Modifier.libraryGridPinch(
    enabled: Boolean,
    currentColumns: Int,
    onColumnsChange: (Int) -> Unit,
): Modifier =
    pointerInput(enabled, currentColumns) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinch: LibraryGridPinch? = null
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val activePinch = pinch ?: LibraryGridPinch(currentColumns).also { pinch = it }
                    val previousColumns = activePinch.columns
                    val nextColumns = activePinch.zoomBy(event.calculateZoom())
                    if (nextColumns != previousColumns) {
                        onColumnsChange(nextColumns)
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

internal fun fallbackIconForAutoPlaylist(type: String): ImageVector =
    when (type) {
        "liked" -> Icons.Outlined.FavoriteBorder
        "downloaded" -> Icons.Outlined.Download
        "cached" -> Icons.Outlined.Cached
        "uploaded" -> Icons.Outlined.CloudUpload
        "podcasts", "episodes" -> Icons.Outlined.Podcasts
        else -> Icons.AutoMirrored.Outlined.QueueMusic
    }

internal fun fallbackIconForEntry(entry: LibraryEntry): ImageVector =
    when (entry) {
        is LibraryEntry.SongEntry -> Icons.Outlined.MusicNote
        is LibraryEntry.AlbumEntry -> Icons.Outlined.Album
        is LibraryEntry.ArtistEntry -> Icons.Outlined.Person
        is LibraryEntry.PlaylistEntry -> Icons.AutoMirrored.Outlined.QueueMusic
        is LibraryEntry.PodcastEntry, is LibraryEntry.PodcastChannelEntry -> Icons.Outlined.Podcasts
    }

