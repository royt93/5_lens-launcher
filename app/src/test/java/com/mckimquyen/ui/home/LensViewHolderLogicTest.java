package com.mckimquyen.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LensViewHolderLogicTest {
    @Test
    public void chromeIsShownOnlyForPlainMultiLensHome() {
        assertTrue(LensViewHolder.chromeVisible(false, false, false, 2));
        assertTrue(LensViewHolder.chromeVisible(false, false, false, 5));
    }

    @Test
    public void chromeIsHiddenForASingleLens() {
        assertFalse(LensViewHolder.chromeVisible(false, false, false, 1));
        assertFalse(LensViewHolder.chromeVisible(false, false, false, 0));
    }

    @Test
    public void chromeIsHiddenInCleanModeListModeOrWhileSearching() {
        assertFalse(LensViewHolder.chromeVisible(true, false, false, 3));
        assertFalse(LensViewHolder.chromeVisible(false, true, false, 3));
        assertFalse(LensViewHolder.chromeVisible(false, false, true, 3));
    }

    @Test
    public void everyCombinationMatchesTheRule() {
        for (int mask = 0; mask < 8; mask++) {
            boolean clean = (mask & 1) != 0;
            boolean list = (mask & 2) != 0;
            boolean searching = (mask & 4) != 0;
            for (int count = 0; count <= 3; count++) {
                boolean expected = !clean && !list && !searching && count > 1;
                assertEquals("mask=" + mask + " count=" + count, expected,
                        LensViewHolder.chromeVisible(clean, list, searching, count));
            }
        }
    }

    @Test
    public void aPositionIsValidOnlyInsideTheList() {
        assertTrue(LensViewHolder.isValidPosition(0, 1));
        assertTrue(LensViewHolder.isValidPosition(2, 3));
        assertFalse(LensViewHolder.isValidPosition(-1, 3));
        assertFalse(LensViewHolder.isValidPosition(3, 3));
        assertFalse(LensViewHolder.isValidPosition(0, 0));
    }
}
