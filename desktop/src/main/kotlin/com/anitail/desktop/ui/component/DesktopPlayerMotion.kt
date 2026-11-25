package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * Material 3 Expressive motion specs and transitions for the Desktop player,
 * floating overlays, and dockable side sheets.
 */
object DesktopPlayerMotion {
    val expressiveEaseInOut: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val expressiveDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val expressiveAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    val artworkBounds: FiniteAnimationSpec<Rect> = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    val fade: FiniteAnimationSpec<Float> = tween(durationMillis = 200)
    val scrimFade: FiniteAnimationSpec<Float> = tween(durationMillis = 180, easing = FastOutSlowInEasing)

    val defaultSpatialSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f,
    )
    val fastSpatialSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.6f,
        stiffness = 800f,
    )
    val slowSpatialSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 200f,
    )
    val defaultEffectsSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1600f,
    )
    val fastEffectsSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 3800f,
    )

    val panelExpandSpec: FiniteAnimationSpec<IntSize> = tween(durationMillis = 280, easing = EaseOutCubic)
    val panelShrinkSpec: FiniteAnimationSpec<IntSize> = tween(durationMillis = 240, easing = EaseOutCubic)

    val panelWidthSpec: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = 0.82f,
        stiffness = 420f,
    )

    val sideSheetEnter: EnterTransition =
        expandHorizontally(
            expandFrom = Alignment.End,
            animationSpec = panelExpandSpec,
        ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = expressiveDecelerate))

    val sideSheetExit: ExitTransition =
        shrinkHorizontally(
            shrinkTowards = Alignment.End,
            animationSpec = panelShrinkSpec,
        ) + fadeOut(animationSpec = tween(durationMillis = 150, easing = expressiveAccelerate))

    val overlayEnter: EnterTransition =
        fadeIn(animationSpec = fastEffectsSpring) +
            scaleIn(animationSpec = fastSpatialSpring, initialScale = 0.90f)

    val overlayExit: ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = 150, easing = expressiveAccelerate)) +
            scaleOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing), targetScale = 0.96f)
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalDesktopSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalDesktopVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@Immutable
class DesktopMotionSet internal constructor(
    val scheme: MotionScheme,
    val enabled: Boolean = true,
) {
    val fastEffects: FiniteAnimationSpec<Float> = scheme.fastEffectsSpec()
    val defaultEffects: FiniteAnimationSpec<Float> = scheme.defaultEffectsSpec()
    val fastSpatial: FiniteAnimationSpec<Float> = scheme.fastSpatialSpec()
    val defaultSpatial: FiniteAnimationSpec<Float> = scheme.defaultSpatialSpec()
    val slowSpatial: FiniteAnimationSpec<Float> = scheme.slowSpatialSpec()

    val popupEnter: EnterTransition
        get() = if (enabled) {
            fadeIn(fastEffects) + scaleIn(fastSpatial, initialScale = 0.88f)
        } else {
            EnterTransition.None
        }

    val popupExit: ExitTransition
        get() = if (enabled) {
            fadeOut(fastEffects) + scaleOut(fastEffects, targetScale = 0.95f)
        } else {
            ExitTransition.None
        }

    val sideSheetEnter: EnterTransition
        get() = if (enabled) DesktopPlayerMotion.sideSheetEnter else EnterTransition.None

    val sideSheetExit: ExitTransition
        get() = if (enabled) DesktopPlayerMotion.sideSheetExit else ExitTransition.None
}

@Composable
fun rememberDesktopMotion(): DesktopMotionSet {
    val scheme = MaterialTheme.motionScheme
    val scale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    return remember(scheme, scale) { DesktopMotionSet(scheme = scheme, enabled = scale > 0f) }
}

@Composable
fun DesktopPlayerVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val enabled = rememberDesktopMotion().enabled
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (enabled) fadeIn(DesktopPlayerMotion.fade) else EnterTransition.None,
        exit = if (enabled) fadeOut(DesktopPlayerMotion.fade) else ExitTransition.None,
    ) {
        CompositionLocalProvider(LocalDesktopVisibilityScope provides this) {
            Box(Modifier.blockInput(!transition.acceptsInput(EnterExitState.Visible))) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.desktopPlayerArtwork(songId: String?): Modifier {
    val scope = LocalDesktopSharedTransitionScope.current
    val visibility = LocalDesktopVisibilityScope.current
    if (songId == null || scope == null || visibility == null || !rememberDesktopMotion().enabled) {
        return this
    }
    return with(scope) {
        sharedElement(
            rememberSharedContentState("desktop-player-artwork:$songId"),
            animatedVisibilityScope = visibility,
            boundsTransform = BoundsTransform { _, _ -> DesktopPlayerMotion.artworkBounds },
        )
    }
}

@Composable
fun Modifier.expressivePressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.94f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "expressivePressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun <T> Transition<T>.acceptsInput(visible: T): Boolean =
    targetState == visible && currentState == visible && !isRunning

fun Modifier.blockInput(blocked: Boolean): Modifier =
    then(if (blocked) Modifier.clearAndSetSemantics {} else Modifier)
        .onPreviewKeyEvent { blocked }
        .pointerInput(blocked) {
            if (blocked) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
        }
