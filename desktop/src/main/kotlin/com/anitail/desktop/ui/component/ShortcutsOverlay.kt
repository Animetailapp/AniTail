@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private data class ShortcutItem(
    val description: String,
    val keyCombos: List<List<String>>,
)

private data class ShortcutCategory(
    val title: String,
    val shortcuts: List<ShortcutItem>,
)

/**
 * Material 3 Expressive overlay (`Ctrl+/`) listing all desktop keyboard shortcuts
 * organized into searchable cards with OS-aware keycap badges.
 */
@Composable
fun ShortcutsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = visible, label = "shortcutsOverlay")
    if (!visible && !transition.currentState && !transition.isRunning) {
        return
    }

    val motion = rememberDesktopMotion()
    val colorScheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val searchState = rememberTextFieldState()
    val searchQuery = searchState.text.toString()

    val isMac = remember {
        System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)
    }
    val modKey = if (isMac) "⌘" else "Ctrl"
    val shiftKey = if (isMac) "⇧" else "Shift"
    val isSpanish = LocalStrings.current.locale.language.equals("es", ignoreCase = true)

    val titleText = if (isSpanish) "Atajos de teclado" else "Keyboard shortcuts"
    val subtitleText = if (isSpanish) {
        "Pulsa $modKey+/ o Esc para cerrar"
    } else {
        "Press $modKey+/ or Esc to dismiss"
    }
    val searchHint = if (isSpanish) "Buscar atajos..." else "Search shortcuts..."
    val emptyText = if (isSpanish) {
        "No hay atajos que coincidan con \"$searchQuery\""
    } else {
        "No shortcuts matching \"$searchQuery\""
    }
    val orLabel = if (isSpanish) "o" else "or"
    val closeLabel = stringResource("close")
    val clearLabel = stringResource("clear")

    val categories = remember(modKey, shiftKey, isSpanish) {
        buildShortcutCategories(
            modKey = modKey,
            shiftKey = shiftKey,
            isSpanish = isSpanish,
        )
    }

    val filteredCategories = remember(searchQuery, categories) {
        val trimmed = searchQuery.trim().lowercase()
        if (trimmed.isEmpty()) {
            categories
        } else {
            categories.mapNotNull { category ->
                val matching = category.shortcuts.filter { item ->
                    item.description.lowercase().contains(trimmed) ||
                        item.keyCombos.any { combo ->
                            combo.any { key -> key.lowercase().contains(trimmed) }
                        }
                }
                if (matching.isNotEmpty()) category.copy(shortcuts = matching) else null
            }
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            searchState.setTextAndPlaceCursorAtEnd("")
            delay(60.milliseconds)
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    fun dismissOverlay() {
        focusManager.clearFocus()
        onDismiss()
    }

    val scrimAlpha by transition.animateFloat(
        transitionSpec = { motion.fastEffects },
        label = "shortcutsScrimAlpha",
    ) { isShown -> if (isShown) 0.52f else 0f }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                paneTitle = titleText
                isTraversalGroup = true
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && visible && event.key == Key.Escape) {
                    dismissOverlay()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = closeLabel,
                    onClick = ::dismissOverlay,
                ),
        )

        transition.AnimatedVisibility(
            visible = { it },
            enter = motion.popupEnter,
            exit = motion.popupExit,
        ) {
            Surface(
                modifier = Modifier
                    .blockInput(!this.transition.acceptsInput(EnterExitState.Visible))
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 700.dp)
                    .focusGroup()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                shadowElevation = 24.dp,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colorScheme.primaryContainer,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = keyboardIcon(),
                                        contentDescription = null,
                                        tint = colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        IconButton(
                            onClick = ::dismissOverlay,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = IconAssets.close(),
                                contentDescription = closeLabel,
                                tint = colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = IconAssets.search(),
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                state = searchState,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(colorScheme.primary),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                decorator = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = searchHint,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = IconAssets.close(),
                                    contentDescription = clearLabel,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { searchState.setTextAndPlaceCursorAtEnd("") },
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (filteredCategories.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = emptyText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            filteredCategories.forEach { category ->
                                ShortcutCategoryCard(
                                    category = category,
                                    orLabel = orLabel,
                                    colorScheme = colorScheme,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCategoryCard(
    category: ShortcutCategory,
    orLabel: String,
    colorScheme: ColorScheme,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.primary,
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                category.shortcuts.forEachIndexed { index, shortcut ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = shortcut.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            shortcut.keyCombos.forEachIndexed { comboIndex, combo ->
                                if (comboIndex > 0) {
                                    Text(
                                        text = orLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .padding(horizontal = 2.dp),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    combo.forEach { keyLabel ->
                                        KeyBadge(
                                            label = keyLabel,
                                            colorScheme = colorScheme,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (index < category.shortcuts.lastIndex) {
                        HorizontalDivider(
                            color = colorScheme.outlineVariant.copy(alpha = 0.16f),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyBadge(
    label: String,
    colorScheme: ColorScheme,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.28f)),
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun buildShortcutCategories(
    modKey: String,
    shiftKey: String,
    isSpanish: Boolean,
): List<ShortcutCategory> =
    if (isSpanish) {
        listOf(
            ShortcutCategory(
                title = "Reproducción",
                shortcuts = listOf(
                    ShortcutItem("Reproducir / Pausar", listOf(listOf("Space"))),
                    ShortcutItem("Avanzar / Retroceder en la pista", listOf(listOf("←"), listOf("→"))),
                    ShortcutItem("Saltar a porcentaje de la pista", listOf(listOf("0 – 9"))),
                    ShortcutItem("Siguiente canción", listOf(listOf(modKey, "→"), listOf("N"))),
                    ShortcutItem("Canción anterior", listOf(listOf(modKey, "←"), listOf("P"))),
                    ShortcutItem("Reproducción aleatoria", listOf(listOf("S"))),
                    ShortcutItem("Cambiar modo de repetición", listOf(listOf("R"))),
                ),
            ),
            ShortcutCategory(
                title = "Volumen",
                shortcuts = listOf(
                    ShortcutItem("Subir / Bajar volumen", listOf(listOf("↑"), listOf("↓"))),
                    ShortcutItem("Ajuste fino de volumen", listOf(listOf("+"), listOf("-"))),
                    ShortcutItem("Silenciar / Activar sonido", listOf(listOf("M"))),
                ),
            ),
            ShortcutCategory(
                title = "Navegación e Interfaz",
                shortcuts = listOf(
                    ShortcutItem("Búsqueda rápida", listOf(listOf(modKey, "K"), listOf("/"))),
                    ShortcutItem("Panel de letras", listOf(listOf("L"))),
                    ShortcutItem("Panel de cola", listOf(listOf("Q"))),
                    ShortcutItem("Reproductor en pantalla completa", listOf(listOf("F"), listOf("T"))),
                    ShortcutItem("Abrir ajustes", listOf(listOf(modKey, ","))),
                    ShortcutItem("Mostrar atajos de teclado", listOf(listOf(modKey, "/"), listOf(modKey, shiftKey, "?"))),
                    ShortcutItem("Cerrar superposiciones y diálogos", listOf(listOf("Esc"))),
                ),
            ),
        )
    } else {
        listOf(
            ShortcutCategory(
                title = "Playback",
                shortcuts = listOf(
                    ShortcutItem("Play / Pause", listOf(listOf("Space"))),
                    ShortcutItem("Seek backward / forward", listOf(listOf("←"), listOf("→"))),
                    ShortcutItem("Jump to track percentage", listOf(listOf("0 – 9"))),
                    ShortcutItem("Next track", listOf(listOf(modKey, "→"), listOf("N"))),
                    ShortcutItem("Previous track", listOf(listOf(modKey, "←"), listOf("P"))),
                    ShortcutItem("Toggle shuffle", listOf(listOf("S"))),
                    ShortcutItem("Cycle repeat mode", listOf(listOf("R"))),
                ),
            ),
            ShortcutCategory(
                title = "Volume",
                shortcuts = listOf(
                    ShortcutItem("Volume up / down", listOf(listOf("↑"), listOf("↓"))),
                    ShortcutItem("Step volume up / down", listOf(listOf("+"), listOf("-"))),
                    ShortcutItem("Mute / Unmute", listOf(listOf("M"))),
                ),
            ),
            ShortcutCategory(
                title = "Navigation & Interface",
                shortcuts = listOf(
                    ShortcutItem("Quick search overlay", listOf(listOf(modKey, "K"), listOf("/"))),
                    ShortcutItem("Toggle lyrics panel", listOf(listOf("L"))),
                    ShortcutItem("Toggle queue panel", listOf(listOf("Q"))),
                    ShortcutItem("Toggle fullscreen player", listOf(listOf("F"), listOf("T"))),
                    ShortcutItem("Open settings", listOf(listOf(modKey, ","))),
                    ShortcutItem("Keyboard shortcuts", listOf(listOf(modKey, "/"), listOf(modKey, shiftKey, "?"))),
                    ShortcutItem("Dismiss overlays & dialogs", listOf(listOf("Esc"))),
                ),
            ),
        )
    }

private fun keyboardIcon(): ImageVector =
    ImageVector.Builder(
        name = "Keyboard",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 5f)
            horizontalLineTo(4f)
            curveTo(2.9f, 5f, 2.01f, 5.9f, 2.01f, 7f)
            lineTo(2f, 17f)
            curveTo(2f, 18.1f, 2.9f, 19f, 4f, 19f)
            horizontalLineTo(20f)
            curveTo(21.1f, 19f, 22f, 18.1f, 22f, 17f)
            verticalLineTo(7f)
            curveTo(22f, 5.9f, 21.1f, 5f, 20f, 5f)
            close()
            moveTo(11f, 8f)
            horizontalLineTo(13f)
            verticalLineTo(10f)
            horizontalLineTo(11f)
            verticalLineTo(8f)
            close()
            moveTo(11f, 11f)
            horizontalLineTo(13f)
            verticalLineTo(13f)
            horizontalLineTo(11f)
            verticalLineTo(11f)
            close()
            moveTo(8f, 8f)
            horizontalLineTo(10f)
            verticalLineTo(10f)
            horizontalLineTo(8f)
            verticalLineTo(8f)
            close()
            moveTo(8f, 11f)
            horizontalLineTo(10f)
            verticalLineTo(13f)
            horizontalLineTo(8f)
            verticalLineTo(11f)
            close()
            moveTo(7f, 13f)
            horizontalLineTo(5f)
            verticalLineTo(11f)
            horizontalLineTo(7f)
            verticalLineTo(13f)
            close()
            moveTo(7f, 10f)
            horizontalLineTo(5f)
            verticalLineTo(8f)
            horizontalLineTo(7f)
            verticalLineTo(10f)
            close()
            moveTo(16f, 17f)
            horizontalLineTo(8f)
            verticalLineTo(15f)
            horizontalLineTo(16f)
            verticalLineTo(17f)
            close()
            moveTo(16f, 13f)
            horizontalLineTo(14f)
            verticalLineTo(11f)
            horizontalLineTo(16f)
            verticalLineTo(13f)
            close()
            moveTo(16f, 10f)
            horizontalLineTo(14f)
            verticalLineTo(8f)
            horizontalLineTo(16f)
            verticalLineTo(10f)
            close()
            moveTo(19f, 13f)
            horizontalLineTo(17f)
            verticalLineTo(11f)
            horizontalLineTo(19f)
            verticalLineTo(13f)
            close()
            moveTo(19f, 10f)
            horizontalLineTo(17f)
            verticalLineTo(8f)
            horizontalLineTo(19f)
            verticalLineTo(10f)
            close()
        }
    }.build()
