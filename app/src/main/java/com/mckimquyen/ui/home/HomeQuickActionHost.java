package com.mckimquyen.ui.home;

import android.app.Activity;

/** ARCH-002: what {@link HomeQuickActionDelegate} needs from {@code ActHome}. */
public interface HomeQuickActionHost {
    Activity activity();

    /** The current search text ({@code appSearch.getText()}); may be empty. */
    CharSequence searchText();

    void hideSearch();

    /** Re-evaluates the "search the web" fallback row ({@code ActHome.updateWebSearchFallback()}). */
    void refreshWebSearchFallback();
}
