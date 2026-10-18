package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LensOverscrollPhysicsTest {

    private companion object {
        const val MIN = 100f
        const val MAX = 900f
        const val MAX_OVERSCROLL = 80f // ~28dp on 480dpi
    }

    @Test
    fun enabled_requiresSettingAndNoReducedMotion() {
        assertTrue(LensOverscrollPhysics.isEnabled(true, false))
        org.junit.Assert.assertFalse(LensOverscrollPhysics.isEnabled(true, true))
        org.junit.Assert.assertFalse(LensOverscrollPhysics.isEnabled(false, false))
        org.junit.Assert.assertFalse(LensOverscrollPhysics.isEnabled(false, true))
    }

    @Test
    fun calculate_withinBounds_returnsRawCoordinate() {
        val x = 500f
        assertEquals(x, LensOverscrollPhysics.calculateEffectiveCoordinate(x, MIN, MAX, MAX_OVERSCROLL, true), 0.001f)
    }

    @Test
    fun calculate_disabled_clampsHardToBounds() {
        val beyondMax = 1200f
        val beyondMin = -200f
        assertEquals(MAX, LensOverscrollPhysics.calculateEffectiveCoordinate(beyondMax, MIN, MAX, MAX_OVERSCROLL, false), 0.001f)
        assertEquals(MIN, LensOverscrollPhysics.calculateEffectiveCoordinate(beyondMin, MIN, MAX, MAX_OVERSCROLL, false), 0.001f)
    }

    @Test
    fun calculate_beyondMax_appliesDampingAndNeverExceedsMaxOverscroll() {
        val raw1 = MAX + 50f
        val raw2 = MAX + 500f
        val rawExtreme = MAX + 100_000f

        val effective1 = LensOverscrollPhysics.calculateEffectiveCoordinate(raw1, MIN, MAX, MAX_OVERSCROLL, true)
        val effective2 = LensOverscrollPhysics.calculateEffectiveCoordinate(raw2, MIN, MAX, MAX_OVERSCROLL, true)
        val effectiveExtreme = LensOverscrollPhysics.calculateEffectiveCoordinate(rawExtreme, MIN, MAX, MAX_OVERSCROLL, true)

        assertTrue("Overscroll phải lớn hơn MAX", effective1 > MAX)
        assertTrue("Kéo xa hơn thì overscroll lớn hơn", effective2 > effective1)
        assertTrue("Không bao giờ vượt quá MAX + MAX_OVERSCROLL", effectiveExtreme <= MAX + MAX_OVERSCROLL)
    }

    @Test
    fun calculate_beyondMin_appliesDampingAndNeverExceedsMaxOverscroll() {
        val raw1 = MIN - 50f
        val rawExtreme = MIN - 100_000f

        val effective1 = LensOverscrollPhysics.calculateEffectiveCoordinate(raw1, MIN, MAX, MAX_OVERSCROLL, true)
        val effectiveExtreme = LensOverscrollPhysics.calculateEffectiveCoordinate(rawExtreme, MIN, MAX, MAX_OVERSCROLL, true)

        assertTrue("Overscroll phải nhỏ hơn MIN", effective1 < MIN)
        assertTrue("Không bao giờ vượt quá MIN - MAX_OVERSCROLL", effectiveExtreme >= MIN - MAX_OVERSCROLL)
    }
}
