package com.mckimquyen.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.mckimquyen.model.App;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HomeSearchLogicTest {
    private static App app(String label, String pkg) {
        return new App(0, label, pkg, "Main", 0, null, 0L, 0, true, true, 0L, -1,
                false, null, com.mckimquyen.model.PinnedZone.NONE, "", "default", 0, 0, false);
    }

    @Test
    public void t9QueryMustContainADigitAndOnlyDigitsOrWhitespace() {
        assertFalse(HomeSearchController.isT9NumericQuery(null));
        assertFalse(HomeSearchController.isT9NumericQuery(""));
        assertFalse(HomeSearchController.isT9NumericQuery("   "));
        assertTrue(HomeSearchController.isT9NumericQuery("247"));
        assertTrue(HomeSearchController.isT9NumericQuery(" 24 7 "));
        assertTrue(HomeSearchController.isT9NumericQuery("٢٤٧"));
        assertFalse(HomeSearchController.isT9NumericQuery("24x"));
        assertFalse(HomeSearchController.isT9NumericQuery("x24"));
        assertFalse(HomeSearchController.isT9NumericQuery("24-7"));
        assertFalse(HomeSearchController.isT9NumericQuery("24.7"));
    }

    @Test
    public void allAppsFallbackNeedsEmptyQueryNoEngineResultsAndCandidates() {
        for (int mask = 0; mask < 8; mask++) {
            boolean empty = (mask & 1) != 0;
            boolean engineEmpty = (mask & 2) != 0;
            boolean candidates = (mask & 4) != 0;
            assertEquals("mask=" + mask, empty && engineEmpty && candidates,
                    HomeSearchController.showAllAppsFallback(empty, engineEmpty, candidates));
        }
    }

    @Test
    public void headersFollowTheOriginalPredicates() {
        for (int mask = 0; mask < 8; mask++) {
            boolean empty = (mask & 1) != 0;
            boolean hasResults = (mask & 2) != 0;
            boolean fallback = (mask & 4) != 0;
            assertEquals("recent mask=" + mask, empty && hasResults && !fallback,
                    HomeSearchController.showRecentHeader(empty, hasResults, fallback));
            assertEquals("results mask=" + mask, !empty && hasResults,
                    HomeSearchController.showResultsHeader(empty, hasResults));
        }
    }

    @Test
    public void webFallbackNeedsAQueryNoResultsAndNoOtherRows() {
        for (int mask = 0; mask < 16; mask++) {
            boolean empty = (mask & 1) != 0;
            boolean hasResults = (mask & 2) != 0;
            boolean quickRow = (mask & 4) != 0;
            boolean contactRow = (mask & 8) != 0;
            assertEquals("mask=" + mask, !empty && !hasResults && !quickRow && !contactRow,
                    HomeSearchController.showWebFallback(empty, hasResults, quickRow, contactRow));
        }
    }

    @Test
    public void sortByLabelUsesNormalizedTextAndKeepsTheSourceUntouched() {
        App zulu = app("Zulu", "pkg.zulu");
        App eclair = app("Éclair", "pkg.eclair");
        App alpha = app("alpha", "pkg.alpha");
        List<App> source = Arrays.asList(zulu, eclair, alpha);

        List<App> sorted = HomeSearchController.sortedByLabel(source);

        assertEquals(Arrays.asList(alpha, eclair, zulu), sorted);
        assertEquals(Arrays.asList(zulu, eclair, alpha), source);
        assertNotSame(source, sorted);
    }

    @Test
    public void sortByLabelIsStableForEqualNormalizedLabels() {
        App first = app("Cafe", "pkg.first");
        App second = app("Café", "pkg.second");

        assertEquals(Arrays.asList(first, second),
                HomeSearchController.sortedByLabel(Arrays.asList(first, second)));
        assertEquals(Arrays.asList(second, first),
                HomeSearchController.sortedByLabel(Arrays.asList(second, first)));
    }
}
