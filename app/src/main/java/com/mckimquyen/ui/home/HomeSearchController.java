package com.mckimquyen.ui.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import com.mckimquyen.R;
import com.mckimquyen.model.App;
import com.mckimquyen.model.LensWorkspace;
import com.mckimquyen.model.SearchFilterPreset;
import com.mckimquyen.search.AppSearchEngine;
import com.mckimquyen.search.T9SearchEngine;
import com.mckimquyen.search.SearchEnterKeyPolicy;
import com.mckimquyen.search.SearchFilterEngine;
import com.mckimquyen.search.SearchHistoryStore;
import com.mckimquyen.search.SearchKeypadPolicy;
import com.mckimquyen.search.SearchQuickActionsRowPolicy;
import com.mckimquyen.views.BoundedHeightNestedScrollView;
import com.mckimquyen.search.SearchResultAdapter;
import com.mckimquyen.util.UtilSettings;
import java.util.ArrayList;
import java.util.List;

/** ARCH-002: original Home search UI, ranking calls, listeners and lifecycle teardown. */
public final class HomeSearchController {
    private static final float BACK_IDENTITY = 1.0f;
    public interface Host {
        UtilSettings settings();
        ArrayList<App> apps();
        List<App> appsForLens(String lensId);
        LensViewHolder lenses();
        HomeQuickActionDelegate quickActions();
        void restoreHiddenApps();
        void refreshSearchBarVisibility();
        void refreshHiddenRecovery();
        void hideHomeAppList();
        void refreshModeVisibility();
    }
    private final Activity activity;
    private final Host host;
    private final SearchBar searchBar;
    private final SearchView searchView;
    private final EditText appSearch;
    private final View recentHeader;
    private final TextView allAppsHeader;
    private final TextView resultsSectionHeader;
    private final View llEmptyQuickActions;
    private final TextView noSearchResults;
    private final TextView webSearchFallback;
    private final RecyclerView searchResults;
    private View t9Keypad;
    private ViewGroup searchColumn;
    private View.OnLayoutChangeListener columnLayoutListener;
    private ChipGroup chipGroupSearchFilters;
    private SearchResultAdapter searchResultAdapter;
    private SearchHistoryStore searchHistoryStore;
    private boolean searchEnterDownHandled;
    private int searchLaunchCount;
    private TextWatcher textWatcher;
    private SearchView.TransitionListener transitionListener;
    private T9KeypadBinder t9Binder;
    private final Runnable refreshBarVisibility;
    private final Runnable showSearch;
    private final List<View> prefillTiles = new ArrayList<>();

    public HomeSearchController(Activity activity, SearchBar searchBar, SearchView searchView, Host host) {
        this.activity = activity;
        this.host = host;
        this.searchBar = searchBar;
        this.searchView = searchView;
        appSearch = searchView.getEditText();
        recentHeader = activity.findViewById(R.id.recentHeader);
        allAppsHeader = activity.findViewById(R.id.allAppsHeader);
        resultsSectionHeader = activity.findViewById(R.id.resultsSectionHeader);
        llEmptyQuickActions = activity.findViewById(R.id.llEmptyQuickActions);
        noSearchResults = activity.findViewById(R.id.tvNoSearchResults);
        webSearchFallback = activity.findViewById(R.id.tvWebSearchFallback);
        searchResults = activity.findViewById(R.id.rvSearchResults);
        refreshBarVisibility = host::refreshSearchBarVisibility;
        showSearch = searchView::show;
    }
    public CharSequence text() { return appSearch.getText(); }
    public SearchResultAdapter adapter() { return searchResultAdapter; }
    public boolean enterDownHandled() { return searchEnterDownHandled; }
    public int launchCount() { return searchLaunchCount; }
    public void trimShortcutCache(float fraction) {
        if (searchResultAdapter != null) searchResultAdapter.trimShortcutCache(fraction);
    }
    public void onDestroy() {
        if (textWatcher != null) { appSearch.removeTextChangedListener(textWatcher); textWatcher = null; }
        if (transitionListener != null) { searchView.removeTransitionListener(transitionListener); transitionListener = null; }
        searchBar.removeCallbacks(refreshBarVisibility);
        searchBar.removeCallbacks(showSearch);
        appSearch.setOnEditorActionListener(null);
        if (chipGroupSearchFilters != null) chipGroupSearchFilters.setOnCheckedStateChangeListener(null);
        activity.findViewById(R.id.restoreHiddenLensApps).setOnClickListener(null);
        activity.findViewById(R.id.btClearSearchHistory).setOnClickListener(null);
        webSearchFallback.setOnClickListener(null);
        for (View tile : prefillTiles) tile.setOnClickListener(null);
        prefillTiles.clear();
        if (t9Binder != null) { t9Binder.close(); t9Binder = null; }
        if (searchColumn != null && columnLayoutListener != null) { searchColumn.removeOnLayoutChangeListener(columnLayoutListener); }
        columnLayoutListener = null;
        searchColumn = null;
        searchResults.setAdapter(null);
    }

    public static boolean isT9NumericQuery(CharSequence query) {
        if (query == null || query.length() == 0) return false;
        boolean hasDigit = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return hasDigit;
    }

    public static boolean showAllAppsFallback(boolean emptyQuery, boolean engineEmpty, boolean hasCandidates) {
        return emptyQuery && engineEmpty && hasCandidates;
    }

    public static boolean showRecentHeader(boolean emptyQuery, boolean hasResults, boolean fallback) {
        return emptyQuery && hasResults && !fallback;
    }

    public static boolean showResultsHeader(boolean emptyQuery, boolean hasResults) {
        return !emptyQuery && hasResults;
    }

    public static boolean showWebFallback(boolean emptyQuery, boolean hasResults, boolean quickRow, boolean contactRow) {
        return !emptyQuery && !hasResults && !quickRow && !contactRow;
    }

    public static List<App> sortedByLabel(List<App> apps) {
        List<App> sorted = new ArrayList<>(apps);
        sorted.sort(java.util.Comparator.comparing(a -> AppSearchEngine.normalize(a.getLabel())));
        return sorted;
    }

    public void setupSearch() {
        searchHistoryStore = new SearchHistoryStore(activity);
        searchResultAdapter = new SearchResultAdapter(this::launchSearchResult);
        searchResults.setLayoutManager(new LinearLayoutManager(activity));
        searchResults.setAdapter(searchResultAdapter);
        // UI-002: one shared divider between flat rows instead of a per-row card, matching
        // Pixel Launcher's search list and keeping layout nesting shallow (see LINT-008).
        searchResults.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));

        chipGroupSearchFilters = activity.findViewById(R.id.chipGroupSearchFilters);
        if (chipGroupSearchFilters != null && host.settings() != null) {
            updateSearchFilterChipChecked(host.settings().getSearchFilterPreset());
            chipGroupSearchFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds == null || checkedIds.isEmpty()) return;
                int checkedId = checkedIds.get(0);
                SearchFilterPreset preset = SearchFilterPreset.ALL;
                if (checkedId == R.id.chipFilterThisLens) {
                    preset = SearchFilterPreset.THIS_LENS;
                } else if (checkedId == R.id.chipFilterFavorites) {
                    preset = SearchFilterPreset.FAVORITES;
                } else if (checkedId == R.id.chipFilterHidden) {
                    preset = SearchFilterPreset.HIDDEN;
                }
                if (host.settings() != null) {
                    host.settings().saveSearchFilterPreset(preset);
                }
                updateSearchResults(appSearch.getText());
            });
        }

        textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSearchResults(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        appSearch.addTextChangedListener(textWatcher);
        // SearchBar->SearchView expand/collapse is a Material3 morph animation, not an instant
        // focus change; refresh results once the panel is fully SHOWN and ready for input.
        activity.findViewById(R.id.restoreHiddenLensApps).setOnClickListener(v -> host.restoreHiddenApps());
        transitionListener = (view, previousState, newState) -> {
            if (newState == SearchView.TransitionState.SHOWN) {
                updateSearchResults(appSearch.getText());
                // UI-007 fix: SearchBar was never actually hidden by the morph transition - it
                // relied on the (now translucent, see UI-007) SearchView panel fully covering it.
                // At the lighter 0.35 alpha this let the SearchBar's own hint text ("Tìm ứng
                // dụng") show through, doubled up with the real SearchView edit text's hint at
                // almost the same position - a confusing ghosted-text overlap. Hide it once the
                // expand morph settles (kept visible during SHOWING so the animation still has
                // its start-anchor); restored at HIDDEN below.
                searchBar.setVisibility(View.INVISIBLE);
            } else if (newState == SearchView.TransitionState.HIDDEN) {
                // FISH-016: was an unconditional VISIBLE, which resurrected a bar that Clean mode
                // or KEY_SHOW_SEARCH_BAR=false had deliberately hidden once search closed.
                host.refreshSearchBarVisibility();
                // Closing via system BACK goes through SearchView's own back orchestrator, which
                // makes the SearchBar VISIBLE again right after this callback returns (measured on
                // TECNO KJ7: GONE here, VISIBLE ~10 ms later; the arrow/hide() path does not). Re-apply
                // from settings on the next message so settings stay the single source of truth.
                searchBar.post(refreshBarVisibility);
            }
            // UI-009: status/nav bar color now matches the search scrim (same
            // ?attr/colorSurfaceContainerHigh tone) so the whole screen reads as one continuous
            // surface while search is open. Toggled on SHOWING/HIDING (not SHOWN/HIDDEN) to match
            // SearchView.isShowing() semantics, same reasoning as the old blur toggle it replaces.
            if (newState == SearchView.TransitionState.SHOWING) {
                host.lenses().pager().setVisibility(View.INVISIBLE);
                host.refreshHiddenRecovery();
                host.lenses().indicator().setVisibility(View.GONE);
                if (host.lenses().nameLabel() != null) host.lenses().nameLabel().setVisibility(View.GONE);
                host.hideHomeAppList();
                setSearchSystemBarsHarmonized(true);
            } else if (newState == SearchView.TransitionState.HIDING) {
                setSearchSystemBarsHarmonized(false);
            } else if (newState == SearchView.TransitionState.HIDDEN) {
                host.refreshModeVisibility();
            }
        };
        searchView.addTransitionListener(transitionListener);
        appSearch.setOnEditorActionListener((view, actionId, event) -> {
            SearchEnterKeyPolicy.Outcome outcome = SearchEnterKeyPolicy.decide(
                    actionId,
                    event == null ? null : event.getAction(),
                    event == null ? null : event.getKeyCode(),
                    event == null ? 0 : event.getRepeatCount(),
                    searchEnterDownHandled
            );
            if (outcome.getDecision() == SearchEnterKeyPolicy.Decision.CONSUME) {
                searchEnterDownHandled = outcome.getDownHandled();
                return true;
            }
            if (outcome.getDecision() == SearchEnterKeyPolicy.Decision.LAUNCH) {
                App first = searchResultAdapter.firstOrNull();
                if (first != null) {
                    searchEnterDownHandled = outcome.getDownHandled();
                    launchSearchResult(first, appSearch);
                    return true;
                }
                searchEnterDownHandled = false;
                return false;
            }
            searchEnterDownHandled = outcome.getDownHandled();
            return false;
        });
        // No manual clear-button wiring: SearchView's built-in clear affordance already does this.
        Button clearHistory = activity.findViewById(R.id.btClearSearchHistory);
        clearHistory.setOnClickListener(view -> {
            searchHistoryStore.clear();
            updateSearchResults(appSearch.getText());
            Toast.makeText(activity, R.string.recent_apps_cleared, Toast.LENGTH_SHORT).show();
        });

        // UI-011: each tile prefills a working example into the search box instead of executing
        // directly - the existing contextual quickActionRow (already wired above) then shows the
        // real result exactly as if the user had typed it, so there's only one execution path.
        prefillOnTap(R.id.tileCalculator, "12*7");
        prefillOnTap(R.id.tileUnitConvert, "10 km to mi");
        prefillOnTap(R.id.tileTimer, "hẹn giờ 5 phút");
        prefillOnTap(R.id.tileBattery, "pin");
        prefillOnTap(R.id.tileWifi, "wifi");

        // SEARCH-006: local search found nothing - offer the user's own default browser/search
        // app instead of a dead end. No network call from this app itself; ACTION_WEB_SEARCH
        // hands the query off entirely, preserving the app's own local-only search property.
        webSearchFallback.setOnClickListener(v -> {
            String query = appSearch.getText().toString();
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(android.app.SearchManager.QUERY, query);
            try {
                activity.startActivity(intent);
                hideSearchOverlay();
            } catch (ActivityNotFoundException e) {
                Toast.makeText(activity, R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
            }
        });
        // SEARCH-010: T9 dialpad buttons wiring
        t9Keypad = activity.findViewById(R.id.scrollT9Keypad);
        t9Binder = new T9KeypadBinder(activity, appSearch);
        // The column's height changes with rotation, window size and system bars, none of which touch the query: re-decide the
        // quick-actions row then. setVisibility only runs on a change, so the layout pass it causes converges instead of looping.
        searchColumn = activity.findViewById(R.id.searchContent);
        columnLayoutListener = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (searchView.isShowing()) {
                applyKeypadChoice();
                applyQuickActionsRow(AppSearchEngine.normalize(appSearch.getText()).isEmpty());
            }
        };
        if (searchColumn != null) searchColumn.addOnLayoutChangeListener(columnLayoutListener);
    }

    /**
     * Shows the empty-search quick-actions row unless it would squeeze the all-apps list under one row (phone landscape with the T9
     * keypad: 596 px of rows plus the keypad in a 974 px column left the list 0 px high). The column's height is unknown before its
     * first layout; then the previous behaviour (show) stands and the layout listener corrects it.
     */
    private void applyQuickActionsRow(boolean isEmptyQuery) {
        boolean show = isEmptyQuery;
        if (isEmptyQuery && searchColumn != null) {
            int column = searchColumn.getHeight() - searchColumn.getPaddingTop() - searchColumn.getPaddingBottom();
            if (column > 0) {
                int fixedRows = 0;
                for (int i = 0; i < searchColumn.getChildCount(); i++) {
                    View child = searchColumn.getChildAt(i);
                    if (child == searchResults || child == t9Keypad || child == llEmptyQuickActions || child.getVisibility() == View.GONE) continue;
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                    fixedRows += child.getHeight() + lp.topMargin + lp.bottomMargin;
                }
                // The row's natural height, measured: a GONE row has none, and counting what it would take keeps the decision independent of the last one.
                llEmptyQuickActions.measure(
                        View.MeasureSpec.makeMeasureSpec(searchColumn.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                fixedRows += llEmptyQuickActions.getMeasuredHeight();
                int keypad = 0;
                if (t9Keypad != null && t9Keypad.getVisibility() == View.VISIBLE) {
                    // At most the cap; the keypad's own content when that is smaller (it does not depend on this decision either).
                    int cap = column * BoundedHeightNestedScrollView.MAX_HEIGHT_PERCENT / 100;
                    View content = ((ViewGroup) t9Keypad).getChildAt(0);
                    keypad = content != null && content.getHeight() > 0 ? Math.min(cap, content.getHeight()) : cap;
                }
                int minList = (int) (SearchQuickActionsRowPolicy.MIN_LIST_DP * activity.getResources().getDisplayMetrics().density);
                show = SearchQuickActionsRowPolicy.shouldShow(true, column, fixedRows, keypad, minList);
            }
        }
        int want = show ? View.VISIBLE : View.GONE;
        if (llEmptyQuickActions.getVisibility() != want) {
            llEmptyQuickActions.setVisibility(want);
            // Measured on a Pixel in phone landscape: with the row GONE the column kept the old layout (list.top still reserved the row's
            // 274 px, list 0 px high) until a layout pass was requested; with it the list got its 274 px. This runs from a layout listener
            // too, where the visibility change alone does not always schedule another pass.
            // post(): a requestLayout issued from inside a layout pass (the listener below runs in one) is dropped by the pass that is
            // still running, which left the list at 0 px for over a second; queued after it, it runs as a fresh pass.
            if (searchColumn != null) searchColumn.post(() -> {
                if (searchColumn != null) searchColumn.requestLayout();
            });
        }
    }

    private void prefillOnTap(int tileViewId, String exampleQuery) {
        View tile = activity.findViewById(tileViewId);
        prefillTiles.add(tile);
        tile.setOnClickListener(v -> {
            appSearch.setText(exampleQuery);
            appSearch.setSelection(exampleQuery.length());
        });
    }

    private void updateSearchFilterChipChecked(SearchFilterPreset preset) {
        if (chipGroupSearchFilters == null) return;
        int targetChipId = R.id.chipFilterAll;
        if (preset == SearchFilterPreset.THIS_LENS) {
            targetChipId = R.id.chipFilterThisLens;
        } else if (preset == SearchFilterPreset.FAVORITES) {
            targetChipId = R.id.chipFilterFavorites;
        } else if (preset == SearchFilterPreset.HIDDEN) {
            targetChipId = R.id.chipFilterHidden;
        }
        chipGroupSearchFilters.check(targetChipId);
    }

    /**
     * The in-app T9 keypad replaces the soft keyboard when the user turned it on, or when the window is too short for the soft keyboard to
     * leave any room for the results (phone landscape: 0 px of list measured). Re-run on rotation, since ActHome handles those itself.
     */
    private boolean keypadInUse() {
        boolean userEnabled = host.settings() != null && host.settings().getEnableT9SearchKeypad();
        // The Configuration's screenHeightDp is not updated when the Activity is put into split-screen after it was launched full screen
        // (measured on the Pixel 7 Pro: 891 dp reported for a 440 dp window), so the real window height decides, the Configuration only
        // stands in until the window is laid out.
        int heightDp = SearchKeypadPolicy.windowHeightDp(activity.getWindow().getDecorView().getHeight(),
                activity.getResources().getDisplayMetrics().density, activity.getResources().getConfiguration().screenHeightDp);
        return SearchKeypadPolicy.useT9Keypad(userEnabled, heightDp);
    }

    private void applyKeypadChoice() {
        boolean useKeypad = keypadInUse();
        searchView.setAutoShowKeyboard(!useKeypad);
        if (t9Keypad != null) {
            int want = useKeypad ? View.VISIBLE : View.GONE;
            if (t9Keypad.getVisibility() != want) t9Keypad.setVisibility(want);
        }
        if (appSearch != null) {
            appSearch.setShowSoftInputOnFocus(!useKeypad);
            // The keypad took over while the soft keyboard was already up (the window shrank into split-screen with the search open):
            // setShowSoftInputOnFocus only affects the next focus, so the open keyboard has to be dismissed explicitly.
            if (useKeypad && searchView.isShowing()) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(appSearch.getWindowToken(), 0);
            }
        }
    }

    public void updateSearchCustomization() {
        UtilSettings settings = new UtilSettings(activity);
        updateSearchFilterChipChecked(settings.getSearchFilterPreset());
        String customHint = settings.getString(UtilSettings.KEY_SEARCH_HINT_TEXT);
        String hint = (customHint == null || customHint.isEmpty())
                ? activity.getString(R.string.search_apps_hint)
                : customHint;
        searchBar.setHint(hint);
        searchView.setHint(hint);

        applyKeypadChoice();

        activity.findViewById(R.id.tileCalculator).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_CALCULATOR) ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.tileUnitConvert).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_UNIT) ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.tileTimer).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_TIMER) ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.tileBattery).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_BATTERY) ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.tileWifi).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_SETTINGS) ? View.VISIBLE : View.GONE);
    }

    public void updateSearchResults(CharSequence query) {
        if (!searchView.isShowing() && query.length() == 0) {
            return;
        }

        host.quickActions().updateQuickAction(query);
        host.quickActions().updateContactAction(query);

        ArrayList<App> apps = host.apps() == null ? new ArrayList<>() : host.apps();
        SearchFilterPreset preset = host.settings() != null
                ? host.settings().getSearchFilterPreset()
                : SearchFilterPreset.ALL;
        int currentPos = host.lenses().pager() != null ? host.lenses().pager().getCurrentItem() : 0;
        String currentLensId = host.lenses().currentLenses() != null && currentPos >= 0 && currentPos < host.lenses().currentLenses().size()
                ? host.lenses().currentLenses().get(currentPos).getId()
                : LensWorkspace.DEFAULT_LENS_ID;
        List<App> lensApps = host.appsForLens(currentLensId);
        java.util.Set<String> hiddenIds = host.settings() != null
                ? host.settings().getLensHiddenAppIds(currentLensId)
                : java.util.Collections.emptySet();
        List<App> candidates = SearchFilterEngine.filterCandidates(apps, preset, lensApps, hiddenIds);

        // SEARCH-010: if T9 dialpad is active and query is all digits, use T9SearchEngine instead.
        boolean useT9 = keypadInUse() && isT9NumericQuery(query);
        java.util.List<App> engineResults = useT9
                ? T9SearchEngine.search(candidates, query, searchHistoryStore.recentKeys())
                : AppSearchEngine.search(candidates, query, searchHistoryStore.recentKeys());
        boolean isEmptyQuery = AppSearchEngine.normalize(query).isEmpty();
        // UI-011: a blank query with no recent/favorite apps used to show a bare empty state.
        // Falling back to the full (alphabetical) app list instead means the panel is only ever
        // truly empty if the device has zero visible apps at all.
        boolean showAllAppsFallback = showAllAppsFallback(isEmptyQuery, engineResults.isEmpty(), !candidates.isEmpty());
        java.util.List<App> results = showAllAppsFallback ? sortedByLabel(candidates) : engineResults;
        boolean hasResults = !results.isEmpty();

        // UI-009 note: a TransitionManager.beginDelayedTransition crossfade was tried here for
        // the state switch, but onTextChanged can fire once per keystroke (adb `input text` and
        // some IMEs commit char-by-char) - overlapping beginDelayedTransition calls left stuck
        // GhostView fade-out overlays rendering on top of the new content, i.e. the exact
        // "overlapping UI" bug this whole revamp exists to remove. Plain visibility swaps instead.
        searchResultAdapter.submitList(results);
        int resultCount = results.size();
        recentHeader.setVisibility(showRecentHeader(isEmptyQuery, hasResults, showAllAppsFallback) ? View.VISIBLE : View.GONE);
        allAppsHeader.setVisibility(showAllAppsFallback ? View.VISIBLE : View.GONE);
        if (showAllAppsFallback) {
            allAppsHeader.setText(activity.getResources().getQuantityString(
                    R.plurals.apps_count, resultCount, resultCount));
        }
        resultsSectionHeader.setVisibility(showResultsHeader(isEmptyQuery, hasResults) ? View.VISIBLE : View.GONE);
        if (!isEmptyQuery && hasResults) {
            resultsSectionHeader.setText(activity.getResources().getQuantityString(
                    R.plurals.search_results_count, resultCount, resultCount));
        }
        applyQuickActionsRow(isEmptyQuery);
        searchResults.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        noSearchResults.setText(isEmptyQuery ? R.string.search_empty_state : R.string.no_apps_found);
        noSearchResults.setVisibility(hasResults ? View.GONE : View.VISIBLE);

        updateWebSearchFallback(query, isEmptyQuery, hasResults);
    }

    public void setSearchSystemBarsHarmonized(boolean showing) {
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        if (showing) {
            int scrimColor = MaterialColors.getColor(activity, R.attr.colorSurfaceContainerHigh, Color.BLACK);
            window.setStatusBarColor(scrimColor);
            window.setNavigationBarColor(scrimColor);
            // Dynamic color can land on a light or dark tone depending on wallpaper/day-night, so
            // bar icon contrast is picked from the actual color instead of being hardcoded.
            boolean lightIcons = ColorUtils.calculateLuminance(scrimColor) > 0.5;
            controller.setAppearanceLightStatusBars(lightIcons);
            controller.setAppearanceLightNavigationBars(lightIcons);
        } else {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            // Restore the defaults in effect the rest of the time: status bar was never
            // explicitly themed (system default = light/white icons); nav bar is statically
            // android:windowLightNavigationBar=true in AppTheme (dark icons).
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(true);
        }
    }

    private void launchSearchResult(App app, View source) {
        // UI-010 fix: hideSearch() used to run first - its clearText() call fires the
        // TextWatcher synchronously, wiping the result list to the empty/recent state a split
        // second before the target app's launch animation covers the screen, i.e. a visible
        // flash of the result the user just tapped disappearing. Launching first means whatever
        // the user sees next is the app's own reveal animation, not our own UI clearing itself.
        searchLaunchCount++;
        searchHistoryStore.recordLaunch(AppSearchEngine.componentKey(app));
        com.mckimquyen.util.UtilApp.launchComponent(
                activity,
                app.getPackageName().toString(),
                app.getLabel().toString(),
                app.getName().toString(),
                source,
                new android.graphics.Rect(0, 0, source.getWidth(), source.getHeight()),
                app.getUserId()
        );
        hideSearchOverlay();
    }

    public void hideSearchOverlay() {
        // SearchView.hide() runs the reverse morph animation and handles keyboard/focus itself.
        searchView.clearText();
        searchView.hide();
    }

    public void resetPredictiveBackPreview() {
        searchView.setScaleX(BACK_IDENTITY);
        searchView.setScaleY(BACK_IDENTITY);
        searchView.setAlpha(BACK_IDENTITY);
    }

    public void openSearchFromHome() {
        if (searchView == null || searchBar == null || searchView.isShowing()) return;
        if (searchBar.getVisibility() == View.GONE) {
            searchBar.setVisibility(View.INVISIBLE);
        }
        searchBar.post(showSearch);
    }

    private void updateWebSearchFallback(CharSequence query, boolean isEmptyQuery, boolean hasResults) {
        boolean showWebFallback = showWebFallback(isEmptyQuery, hasResults,
                host.quickActions().quickActionRowVisible(), host.quickActions().contactActionRowVisible());
        webSearchFallback.setVisibility(showWebFallback ? View.VISIBLE : View.GONE);
        if (showWebFallback && query != null) {
            webSearchFallback.setText(activity.getString(R.string.web_search_fallback, query.toString()));
        }
    }

    public void updateWebSearchFallback() {
        CharSequence query = appSearch.getText();
        boolean isEmptyQuery = query == null || query.length() == 0;
        boolean hasResults = searchResultAdapter.getItemCount() > 0;
        updateWebSearchFallback(query, isEmptyQuery, hasResults);
    }
}
