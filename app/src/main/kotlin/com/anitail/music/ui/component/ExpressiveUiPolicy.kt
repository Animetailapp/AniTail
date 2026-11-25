package com.anitail.music.ui.component

data class SegmentedCornerRadii(
    val topStartDp: Int,
    val topEndDp: Int,
    val bottomStartDp: Int,
    val bottomEndDp: Int,
)

/**
 * Computes Material 3 Expressive segmented card stack corners where outer corners
 * are rounded generously and touching inner edges use tight corners.
 */
fun segmentedListItemCorners(
    index: Int,
    count: Int,
    outerRadiusDp: Int = 20,
    innerRadiusDp: Int = 5,
    singleRadiusDp: Int = 24,
): SegmentedCornerRadii =
    when {
        count <= 1 -> SegmentedCornerRadii(
            topStartDp = singleRadiusDp,
            topEndDp = singleRadiusDp,
            bottomStartDp = singleRadiusDp,
            bottomEndDp = singleRadiusDp,
        )
        index <= 0 -> SegmentedCornerRadii(
            topStartDp = outerRadiusDp,
            topEndDp = outerRadiusDp,
            bottomStartDp = innerRadiusDp,
            bottomEndDp = innerRadiusDp,
        )
        index >= count - 1 -> SegmentedCornerRadii(
            topStartDp = innerRadiusDp,
            topEndDp = innerRadiusDp,
            bottomStartDp = outerRadiusDp,
            bottomEndDp = outerRadiusDp,
        )
        else -> SegmentedCornerRadii(
            topStartDp = innerRadiusDp,
            topEndDp = innerRadiusDp,
            bottomStartDp = innerRadiusDp,
            bottomEndDp = innerRadiusDp,
        )
    }

/**
 * Computes horizontal connected-strip corners for expressive action rows.
 */
fun connectedStripItemCorners(
    index: Int,
    count: Int,
    outerRadiusDp: Int = 28,
    innerRadiusDp: Int = 6,
): SegmentedCornerRadii =
    when {
        count <= 1 -> SegmentedCornerRadii(
            topStartDp = outerRadiusDp,
            topEndDp = outerRadiusDp,
            bottomStartDp = outerRadiusDp,
            bottomEndDp = outerRadiusDp,
        )
        index <= 0 -> SegmentedCornerRadii(
            topStartDp = outerRadiusDp,
            topEndDp = innerRadiusDp,
            bottomStartDp = outerRadiusDp,
            bottomEndDp = innerRadiusDp,
        )
        index >= count - 1 -> SegmentedCornerRadii(
            topStartDp = innerRadiusDp,
            topEndDp = outerRadiusDp,
            bottomStartDp = innerRadiusDp,
            bottomEndDp = outerRadiusDp,
        )
        else -> SegmentedCornerRadii(
            topStartDp = innerRadiusDp,
            topEndDp = innerRadiusDp,
            bottomStartDp = innerRadiusDp,
            bottomEndDp = innerRadiusDp,
        )
    }

/**
 * Resolves the active bottom navigation container height in dp for inset and offset calculations.
 */
fun resolveActiveNavBarHeightDp(
    isTelevision: Boolean,
    floatingNav: Boolean,
    slimNav: Boolean,
): Int =
    when {
        isTelevision -> 0
        floatingNav && slimNav -> 64
        floatingNav -> 72
        slimNav -> 56
        else -> 68
    }

/**
 * Determines whether ShortNavigationBar items should lay out icons horizontally next to labels.
 */
fun shouldUseHorizontalShortNavItems(
    screenWidthDp: Int,
    slimNav: Boolean,
): Boolean = !slimNav && screenWidthDp >= 600

/**
 * Picks a stable positive polygon index within [0, poolSize) from a contributor seed.
 */
fun deterministicExpressivePolygonIndex(
    seed: String,
    poolSize: Int,
): Int {
    val safeSize = poolSize.coerceAtLeast(1)
    return (seed.hashCode() and Int.MAX_VALUE) % safeSize
}

data class SlidingPillBoundsPx(
    val leftPx: Float,
    val rightPx: Float,
)

/**
 * Computes horizontal [leftPx, rightPx] bounds for the centered active indicator pill
 * inside an equal-weight docked navigation bar track.
 */
fun computeDockedPillBoundsPx(
    trackWidthPx: Float,
    itemCount: Int,
    selectedIndex: Int,
    pillWidthPx: Float,
    horizontalMarginPx: Float = 0f,
    isRtl: Boolean = false,
): SlidingPillBoundsPx {
    if (itemCount <= 0 || trackWidthPx <= 0f) return SlidingPillBoundsPx(0f, 0f)
    val safeIndex = selectedIndex.coerceIn(0, itemCount - 1)
    val visualIndex = if (isRtl) (itemCount - 1 - safeIndex) else safeIndex
    val slotWidth = trackWidthPx / itemCount.toFloat()
    val clampedPillWidth = pillWidthPx.coerceAtMost((slotWidth - horizontalMarginPx * 2f).coerceAtLeast(0f))
    val centerX = slotWidth * (visualIndex + 0.5f)
    val halfWidth = clampedPillWidth / 2f
    return SlidingPillBoundsPx(
        leftPx = centerX - halfWidth,
        rightPx = centerX + halfWidth,
    )
}

/**
 * Computes horizontal [leftPx, rightPx] bounds for a full-slot capsule inside
 * a spaced floating navigation bar track.
 */
fun computeFloatingPillBoundsPx(
    trackWidthPx: Float,
    itemCount: Int,
    selectedIndex: Int,
    spacingPx: Float,
    isRtl: Boolean = false,
): SlidingPillBoundsPx {
    if (itemCount <= 0 || trackWidthPx <= 0f) return SlidingPillBoundsPx(0f, 0f)
    val safeIndex = selectedIndex.coerceIn(0, itemCount - 1)
    val visualIndex = if (isRtl) (itemCount - 1 - safeIndex) else safeIndex
    val totalSpacing = spacingPx * (itemCount - 1).coerceAtLeast(0)
    val slotWidth = ((trackWidthPx - totalSpacing) / itemCount.toFloat()).coerceAtLeast(0f)
    val left = visualIndex * (slotWidth + spacingPx)
    return SlidingPillBoundsPx(
        leftPx = left,
        rightPx = left + slotWidth,
    )
}

/**
 * Returns true when the pill moves toward higher X coordinates (rightward),
 * so the right edge leads and the left edge trails for an expressive stretch.
 */
fun isPillMovingRight(
    currentLeftPx: Float,
    targetLeftPx: Float,
): Boolean = targetLeftPx >= currentLeftPx

