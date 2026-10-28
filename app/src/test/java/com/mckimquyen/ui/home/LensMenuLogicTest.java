package com.mckimquyen.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mckimquyen.R;
import com.mckimquyen.model.LensWorkspace;
import com.mckimquyen.ui.ActHome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LensMenuLogicTest {
    @Test
    public void everyKnownMenuIdRoutesToItsAction() {
        assertEquals(LensMenuCoordinator.MenuAction.CREATE, LensMenuCoordinator.routeMenuItem(1));
        assertEquals(LensMenuCoordinator.MenuAction.RENAME, LensMenuCoordinator.routeMenuItem(2));
        assertEquals(LensMenuCoordinator.MenuAction.DELETE, LensMenuCoordinator.routeMenuItem(3));
        assertEquals(LensMenuCoordinator.MenuAction.SMART_FOCUS, LensMenuCoordinator.routeMenuItem(4));
        assertEquals(LensMenuCoordinator.MenuAction.SHARE, LensMenuCoordinator.routeMenuItem(5));
        assertEquals(LensMenuCoordinator.MenuAction.RECENT, LensMenuCoordinator.routeMenuItem(6));
        assertEquals(LensMenuCoordinator.MenuAction.SEARCH, LensMenuCoordinator.routeMenuItem(7));
        assertEquals(LensMenuCoordinator.MenuAction.CLEAN, LensMenuCoordinator.routeMenuItem(8));
        assertEquals(LensMenuCoordinator.MenuAction.LENS_APPS, LensMenuCoordinator.routeMenuItem(9));
        assertEquals(LensMenuCoordinator.MenuAction.FREEZE, LensMenuCoordinator.routeMenuItem(10));
        assertEquals(LensMenuCoordinator.MenuAction.GROUP, LensMenuCoordinator.routeMenuItem(11));
        assertEquals(LensMenuCoordinator.MenuAction.HIDDEN, LensMenuCoordinator.routeMenuItem(12));
    }

    @Test
    public void unknownMenuIdsDoNothing() {
        for (int id : new int[]{-1, 0, 13, 99, Integer.MAX_VALUE}) {
            assertEquals("id=" + id, LensMenuCoordinator.MenuAction.NONE, LensMenuCoordinator.routeMenuItem(id));
        }
    }

    @Test
    public void namedMenuIdsMatchTheValuesActHomePublishesToTests() throws Exception {
        assertEquals(LensMenuCoordinator.ID_SEARCH_APPS, readMenuId("MENU_ID_SEARCH_APPS"));
        assertEquals(LensMenuCoordinator.ID_TOGGLE_CLEAN_LENS, readMenuId("MENU_ID_TOGGLE_CLEAN_LENS"));
        assertEquals(LensMenuCoordinator.ID_LENS_APPS, readMenuId("MENU_ID_LENS_APPS"));
        assertEquals(LensMenuCoordinator.ID_FREEZE_LENS, readMenuId("MENU_ID_FREEZE_LENS"));
        assertEquals(LensMenuCoordinator.ID_MANAGE_GROUP, readMenuId("MENU_ID_MANAGE_CURRENT_LENS_GROUP"));
        assertEquals(LensMenuCoordinator.ID_HIDDEN_APPS, readMenuId("MENU_ID_HIDDEN_LENS_APPS"));
    }

    private static int readMenuId(String name) throws Exception {
        java.lang.reflect.Field field = ActHome.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    @Test
    public void theLabelsNameTheActionNotTheState() {
        assertEquals(R.string.lens_smart_focus_disable, LensMenuCoordinator.smartFocusLabelRes(true));
        assertEquals(R.string.lens_smart_focus_enable, LensMenuCoordinator.smartFocusLabelRes(false));
        assertEquals(R.string.lens_unfreeze_positions, LensMenuCoordinator.freezeLabelRes(true));
        assertEquals(R.string.lens_freeze_positions, LensMenuCoordinator.freezeLabelRes(false));
    }

    @Test
    public void containsLensMatchesByIdOnly() {
        List<LensWorkspace> lenses = Arrays.asList(new LensWorkspace("a", "Alpha", 0, 0L, null), new LensWorkspace("b", "Beta", 1, 0L, null));
        assertTrue(LensMenuCoordinator.containsLens(lenses, "b"));
        assertFalse(LensMenuCoordinator.containsLens(lenses, "Beta"));
        assertFalse(LensMenuCoordinator.containsLens(lenses, "c"));
        assertFalse(LensMenuCoordinator.containsLens(Collections.<LensWorkspace>emptyList(), "a"));
    }

    @Test
    public void deleteIsOfferedOnlyForAValidPositionOfADeletableLens() {
        assertFalse("out of range", LensMenuCoordinator.deleteEnabled(-1, 2, "a"));
        assertFalse("past the end", LensMenuCoordinator.deleteEnabled(2, 2, "a"));
        assertFalse("the last lens cannot be deleted", LensMenuCoordinator.deleteEnabled(0, 1, "a"));
        assertTrue(LensMenuCoordinator.deleteEnabled(1, 2, "b"));
    }
}
