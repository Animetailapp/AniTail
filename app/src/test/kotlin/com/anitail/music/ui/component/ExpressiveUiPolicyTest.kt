package com.anitail.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveUiPolicyTest {

    @Test
    fun `segmentedListItemCorners computes outer inner and single radii`() {
        val single = segmentedListItemCorners(index = 0, count = 1)
        assertEquals(SegmentedCornerRadii(24, 24, 24, 24), single)

        val first = segmentedListItemCorners(index = 0, count = 3)
        assertEquals(SegmentedCornerRadii(20, 20, 5, 5), first)

        val middle = segmentedListItemCorners(index = 1, count = 3)
        assertEquals(SegmentedCornerRadii(5, 5, 5, 5), middle)

        val last = segmentedListItemCorners(index = 2, count = 3)
        assertEquals(SegmentedCornerRadii(5, 5, 20, 20), last)
    }

    @Test
    fun `connectedStripItemCorners shapes left middle and right strip tiles`() {
        val single = connectedStripItemCorners(index = 0, count = 1)
        assertEquals(SegmentedCornerRadii(28, 28, 28, 28), single)

        val twoLeft = connectedStripItemCorners(index = 0, count = 2)
        val twoRight = connectedStripItemCorners(index = 1, count = 2)
        assertEquals(SegmentedCornerRadii(28, 6, 28, 6), twoLeft)
        assertEquals(SegmentedCornerRadii(6, 28, 6, 28), twoRight)

        val threeLeft = connectedStripItemCorners(index = 0, count = 3)
        val threeMid = connectedStripItemCorners(index = 1, count = 3)
        val threeRight = connectedStripItemCorners(index = 2, count = 3)
        assertEquals(SegmentedCornerRadii(28, 6, 28, 6), threeLeft)
        assertEquals(SegmentedCornerRadii(6, 6, 6, 6), threeMid)
        assertEquals(SegmentedCornerRadii(6, 28, 6, 28), threeRight)
    }

    @Test
    fun `resolveActiveNavBarHeightDp returns exact expressive bar heights`() {
        assertEquals(0, resolveActiveNavBarHeightDp(isTelevision = true, floatingNav = false, slimNav = false))
        assertEquals(0, resolveActiveNavBarHeightDp(isTelevision = true, floatingNav = true, slimNav = true))
        assertEquals(72, resolveActiveNavBarHeightDp(isTelevision = false, floatingNav = true, slimNav = false))
        assertEquals(64, resolveActiveNavBarHeightDp(isTelevision = false, floatingNav = true, slimNav = true))
        assertEquals(56, resolveActiveNavBarHeightDp(isTelevision = false, floatingNav = false, slimNav = true))
        assertEquals(68, resolveActiveNavBarHeightDp(isTelevision = false, floatingNav = false, slimNav = false))
    }

    @Test
    fun `shouldUseHorizontalShortNavItems adapts on medium width when not slim`() {
        assertFalse(shouldUseHorizontalShortNavItems(screenWidthDp = 360, slimNav = false))
        assertFalse(shouldUseHorizontalShortNavItems(screenWidthDp = 599, slimNav = false))
        assertTrue(shouldUseHorizontalShortNavItems(screenWidthDp = 600, slimNav = false))
        assertTrue(shouldUseHorizontalShortNavItems(screenWidthDp = 840, slimNav = false))
        assertFalse(shouldUseHorizontalShortNavItems(screenWidthDp = 840, slimNav = true))
    }

    @Test
    fun `deterministicExpressivePolygonIndex returns stable index within bounds`() {
        val poolSize = 8
        val firstRun = deterministicExpressivePolygonIndex("t4:SKHLORDKIRA", poolSize)
        val secondRun = deterministicExpressivePolygonIndex("t4:SKHLORDKIRA", poolSize)
        assertEquals(firstRun, secondRun)
        assertTrue(firstRun in 0 until poolSize)

        val negativeHashSeed = "polygen_negative_hash_seed_test_123456"
        val negResult = deterministicExpressivePolygonIndex(negativeHashSeed, poolSize)
        assertTrue(negResult in 0 until poolSize)
    }

    @Test
    fun `computeDockedPillBoundsPx centers 64px pill in LTR and RTL slots and clamps narrow widths`() {
        val ltrMiddle = computeDockedPillBoundsPx(
            trackWidthPx = 360f,
            itemCount = 3,
            selectedIndex = 1,
            pillWidthPx = 64f,
        )
        assertEquals(148f, ltrMiddle.leftPx, 0.01f)
        assertEquals(212f, ltrMiddle.rightPx, 0.01f)

        val rtlFirst = computeDockedPillBoundsPx(
            trackWidthPx = 360f,
            itemCount = 3,
            selectedIndex = 0,
            pillWidthPx = 64f,
            isRtl = true,
        )
        assertEquals(268f, rtlFirst.leftPx, 0.01f)
        assertEquals(332f, rtlFirst.rightPx, 0.01f)

        val clampedNarrow = computeDockedPillBoundsPx(
            trackWidthPx = 150f,
            itemCount = 3,
            selectedIndex = 0,
            pillWidthPx = 64f,
            horizontalMarginPx = 5f,
        )
        assertEquals(5f, clampedNarrow.leftPx, 0.01f)
        assertEquals(45f, clampedNarrow.rightPx, 0.01f)
    }

    @Test
    fun `computeFloatingPillBoundsPx accounts for slot spacing in LTR and RTL`() {
        val ltrMiddle = computeFloatingPillBoundsPx(
            trackWidthPx = 312f,
            itemCount = 3,
            selectedIndex = 1,
            spacingPx = 6f,
        )
        // slotWidth = (312 - 12) / 3 = 100f; middle starts at 100 + 6 = 106f
        assertEquals(106f, ltrMiddle.leftPx, 0.01f)
        assertEquals(206f, ltrMiddle.rightPx, 0.01f)

        val rtlFirst = computeFloatingPillBoundsPx(
            trackWidthPx = 312f,
            itemCount = 3,
            selectedIndex = 0,
            spacingPx = 6f,
            isRtl = true,
        )
        assertEquals(212f, rtlFirst.leftPx, 0.01f)
        assertEquals(312f, rtlFirst.rightPx, 0.01f)
    }

    @Test
    fun `isPillMovingRight detects rightward and leftward transitions`() {
        assertTrue(isPillMovingRight(currentLeftPx = 28f, targetLeftPx = 148f))
        assertTrue(isPillMovingRight(currentLeftPx = 148f, targetLeftPx = 148f))
        assertFalse(isPillMovingRight(currentLeftPx = 268f, targetLeftPx = 148f))
    }
}

