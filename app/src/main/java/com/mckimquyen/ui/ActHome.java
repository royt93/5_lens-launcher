package com.mckimquyen.ui;

import static com.mckimquyen.ext.ActivityKt.rateAppInApp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.mckimquyen.BuildConfig;
import com.mckimquyen.R;
import com.mckimquyen.adt.AppAdapter;
import com.mckimquyen.adt.LensPagerAdapter;
import com.mckimquyen.app.RApplication;
import com.mckimquyen.app.RAppsSingleton;
import com.mckimquyen.enums.BackgroundMode;
import com.mckimquyen.enums.LauncherMode;
import com.mckimquyen.model.App;
import com.mckimquyen.model.AppPersistent;
import com.mckimquyen.model.LensWorkspace;
import com.mckimquyen.search.AppSearchEngine;
import com.mckimquyen.search.T9SearchEngine;
import com.mckimquyen.model.SearchFilterPreset;
import com.mckimquyen.search.ContactSearchEngine;
import com.mckimquyen.search.ContactSearchResult;
import com.mckimquyen.search.QuickAction;
import com.mckimquyen.search.QuickActionEngine;
import com.mckimquyen.search.SearchEnterKeyPolicy;
import com.mckimquyen.search.SearchFilterEngine;
import com.mckimquyen.search.SearchHistoryStore;
import com.mckimquyen.search.SearchResultAdapter;
import com.mckimquyen.ui.home.HomeQuickActionDelegate;
import com.mckimquyen.ui.home.HomeSearchController;
import com.mckimquyen.ui.home.HomeQuickActionHost;
import com.mckimquyen.ui.home.LensViewHolder;
import com.mckimquyen.ui.home.LensViewHolderHost;
import com.mckimquyen.ui.home.LensDropStrip;
import com.mckimquyen.ui.home.LensMenuCoordinator;
import com.mckimquyen.util.ApertureRevealHelper;
import com.mckimquyen.util.LensAppScope;
import com.mckimquyen.util.LensLabelResolver;
import com.mckimquyen.util.LensShortcuts;
import com.mckimquyen.util.Logger;
import com.mckimquyen.util.PolaroidExportHelper;
import com.mckimquyen.util.UIUtils;
import com.mckimquyen.util.UtilCalculator;
import com.mckimquyen.services.AppEventManager;
import com.mckimquyen.util.UtilSettings;
import com.mckimquyen.views.LensView;
import com.mckimquyen.views.PointAnchor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import kotlin.Unit;

//2023.03.19 tried to convert kotlin but failed
public class ActHome extends ActBase implements LensViewHolderHost, HomeQuickActionHost {

    // FISH-016: ids 1-6 of the lens-management menu are the pre-existing literals above in
    // showLensManagementMenu/onLensMenuItemSelected; the two new entries are named.
    @androidx.annotation.VisibleForTesting
    static final int MENU_ID_SEARCH_APPS = 7;
    @androidx.annotation.VisibleForTesting
    static final int MENU_ID_TOGGLE_CLEAN_LENS = 8;
    @androidx.annotation.VisibleForTesting
    public static final int MENU_ID_LENS_APPS = 9;
    @androidx.annotation.VisibleForTesting
    public static final int MENU_ID_FREEZE_LENS = 10;
    @androidx.annotation.VisibleForTesting
    static final int MENU_ID_MANAGE_CURRENT_LENS_GROUP = 11;
    static final int MENU_ID_HIDDEN_LENS_APPS = 12;

    // UI-021: predictive-back shrink/fade preview bounds for the search overlay, matching
    // Android's own predictive-back design guidance (subtle scale-down + slight fade, not a
    // dramatic transform).
    private static final float PREDICTIVE_BACK_MAX_SCALE = 1.0f;
    private static final float PREDICTIVE_BACK_MIN_SCALE = 0.95f;
    private static final float PREDICTIVE_BACK_MAX_ALPHA = 1.0f;
    private static final float PREDICTIVE_BACK_MIN_ALPHA = 0.7f;

    // UI-010 fix: this used to be an unconditional no-op, relying on SearchView's own internal
    // MaterialBackOrchestrator to intercept back first and collapse the panel. Confirmed live on
    // TECNO KJ7 that back does NOT close the search overlay - explicitly checking isShowing()
    // here instead makes it work regardless of whatever SearchView is or isn't doing internally.
    // Falls through to no-op (blocking launcher exit) otherwise, since this is the HOME activity
    // and back should never finish it.
    //
    // UI-021: kept unconditionally enabled (never toggled by isShowing()) rather than disabling
    // when search is closed - this is the HOME activity's task root, so letting back fall through
    // to the default dispatcher when disabled risks finish()'ing it, which UI-010's fix
    // deliberately prevents. The predictive-back preview (handleOnBackStarted/Progressed/
    // Cancelled) is driven by this same reliable callback rather than SearchView's own internal
    // predictive-back handling, since that's the exact path UI-010 already found unreliable on
    // real hardware - safer to reuse the proven interception point than gamble the underlying
    // Material bug is now fixed. Kept as a named field (not an inline anonymous registration) so
    // widget tests can reach these methods directly via reflection, the same pattern
    // BaseActivityRefreshRateWidgetTest already uses for a protected method.
    private final OnBackPressedCallback searchBackCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
            if (searchView.isShowing()) {
                searchView.setPivotY(0f);
            }
        }

        @Override
        public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
            if (!searchView.isShowing()) return;
            float progress = backEvent.getProgress();
            float scale = PREDICTIVE_BACK_MAX_SCALE
                    - (PREDICTIVE_BACK_MAX_SCALE - PREDICTIVE_BACK_MIN_SCALE) * progress;
            searchView.setScaleX(scale);
            searchView.setScaleY(scale);
            searchView.setAlpha(
                    PREDICTIVE_BACK_MAX_ALPHA
                            - (PREDICTIVE_BACK_MAX_ALPHA - PREDICTIVE_BACK_MIN_ALPHA) * progress);
        }

        @Override
        public void handleOnBackCancelled() {
            resetPredictiveBackPreview();
        }

        @Override
        public void handleOnBackPressed() {
            resetPredictiveBackPreview();
            if (searchView.isShowing()) {
                hideSearchOverlay();
            }
        }
    };

    // FISH-008 Phase 2: the currently VISIBLE page's LensView. Kept current by
    // lensPageChangeCallback (or bootstrapped once at cold start by bindLensView) - never
    // written to from a page bind that isn't the tracked current one, so a not-yet-selected
    // prefetched page can't clobber it with the wrong lens's view.
    LensView lensViews;
    // Held so onDestroy can detach() it: the mediator registers observers on the pager/adapter.
    private TabLayoutMediator lensTabMediator;
    private final LensViewHolder lensHolder = new LensViewHolder(this);
    private final HomeQuickActionDelegate quickActions = new HomeQuickActionDelegate(this);
    /** FISH-038b: created lazily on the first drag so installs that never drag pay nothing. */
    private LensDropStrip dropStrip;
    private final LensMenuCoordinator lensMenu = new LensMenuCoordinator(new LensMenuCoordinator.Host() {
        @Override public android.app.Activity activity() { return ActHome.this; }
        @Override public UtilSettings settings() { return utilSettings; }
        @Override public ArrayList<App> apps() { return listApp; }
        @Override public LensViewHolder lenses() { return lensHolder; }
        @Override public LensView activeLensView() { return lensViews; }
        @Override public PopupMenu menu() { return lensManagementMenu; }
        @Override public void setMenu(PopupMenu menu) { lensManagementMenu = menu; }
        @Override public androidx.appcompat.app.AlertDialog dialog() { return lensDialog; }
        @Override public void setDialog(androidx.appcompat.app.AlertDialog dialog) { lensDialog = dialog; }
        @Override public void launchShare(Intent chooser) { lensShareLauncher.launch(chooser); }
        @Override public void setLaunchIntent(Intent intent) { lensManagementLaunchIntentForTest = intent; }
        @Override public void showRecentAppsPanel() { ActHome.this.showRecentAppsPanel(); }
        @Override public void openSearch() { openSearchFromHome(); }
        @Override public void refreshSearchBarVisibility() { updateSearchBarVisibility(); }
    });

    /** FISH-EXPORT: set by FrmLens's share button on the Intent that (re)launches this singleTask
     *  activity, so onLensMenuItemSelected's item 5 path runs automatically once lensHolder.currentLenses() is
     *  loaded, instead of FrmLens needing its own copy of the export logic. */
    public static final String EXTRA_AUTO_EXPORT_LENS =
            "com.mckimquyen.lenslauncher.EXTRA_AUTO_EXPORT_LENS";

    /** FISH-021: set by a lens launcher shortcut; {@link #handleTargetLensIntent} consumes it. */
    public static final String EXTRA_TARGET_LENS_ID =
            "com.mckimquyen.lenslauncher.EXTRA_TARGET_LENS_ID";
    private boolean pendingAutoExportLens = false;

    /** Test seam so PolaroidExportHelperWidgetTest-style tests can capture the built chooser
     *  Intent instead of actually popping a real system share sheet - same pattern as
     *  lensManagementMenu/lensDialog below. */
    @androidx.annotation.VisibleForTesting
    interface LensShareLauncher {
        void launch(Intent chooserIntent);
    }

    @androidx.annotation.VisibleForTesting
    LensShareLauncher lensShareLauncher = this::startActivity;
    private RecyclerView rvHomeAppList;
    private AppAdapter homeAppAdapter;
    private UtilSettings utilSettings;
    CircularProgressIndicator progressBarHome;
    private ArrayList<App> listApp;
    private boolean hasReportedFullyDrawn = false;
    @androidx.annotation.VisibleForTesting
    int reportFullyDrawnCallCount = 0;
    private SearchBar searchBar;
    private SearchView searchView;
    private HomeSearchController searchController;

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
    public SearchResultAdapter getSearchResultAdapterForTest() {
        return searchController.adapter();
    }

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
    public boolean isSearchEnterDownHandledForTest() {
        return searchController.enterDownHandled();
    }

    /** How many times a search result has been launched from this Activity instance. */
    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
    public int getSearchLaunchCountForTest() {
        return searchController.launchCount();
    }

    private void updateColor() {
        var mUtilSettings = new UtilSettings(this);
        var kBackground = mUtilSettings.getBackgroundMode();
        Log.d("roy93~", "kBackground " + kBackground);
        if (kBackground == BackgroundMode.COLOR) {
            Log.d("roy93~", "setBackgroundColor");
            // UI-026: a malformed stored color must never crash onResume.
            findViewById(R.id.rootLayout).setBackgroundColor(mUtilSettings.backgroundColorInt());
        } else {
            findViewById(R.id.rootLayout).setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.INSTANCE.setupEdgeToEdge1(getWindow());
        setContentView(R.layout.act_home);
        setupViews();
        applyHomeColumnInsets(findViewById(R.id.rootLayout));
        setupSearch();
        // updateColor();
        consumeAutoExportExtra(getIntent());
        // FISH-021: a lens shortcut's target becomes the saved active lens before the list loads,
        // so refreshLensList's existing "restore the saved lens" path does the paging.
        applyTargetLensExtra(getIntent());
        refreshLensList();
        assignApps(Objects.requireNonNull(Objects.requireNonNull(RAppsSingleton.getInstance()).getApps()));

        // Observe app events using LiveData
        AppEventManager.INSTANCE.getAppsLoaded().observe(this,
                data -> assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps())));

        AppEventManager.INSTANCE.getAppsUpdated().observe(this,
                data -> assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps())));

        AppEventManager.INSTANCE.getAppsEdited().observe(this, data -> {
            updateSearchBarVisibility();
            assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps()));
        });

        AppEventManager.INSTANCE.getVisibilityChanged().observe(this,
                data -> assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps())));

        AppEventManager.INSTANCE.getLockChanged().observe(this,
                data -> assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps())));

        AppEventManager.INSTANCE.getLensScopeChanged().observe(this, data -> refreshLensScope());

        AppEventManager.INSTANCE.getBackgroundChanged().observe(this, data -> setBackground());

        AppEventManager.INSTANCE.getNightModeChanged().observe(this, data -> updateNightMode());

        // See searchBackCallback's own doc comment for why this stays unconditionally enabled
        // (UI-010) and drives its own predictive-back preview instead of relying on SearchView's
        // internal handling (UI-021).
        getOnBackPressedDispatcher().addCallback(this, searchBackCallback);

        rateAppInApp(this, BuildConfig.DEBUG);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeAutoExportExtra(intent);
        handleTargetLensIntent(intent);
    }

    /**
     * FISH-021: saves a shortcut's target lens as the active one and removes the extra so a later
     * recreate never replays it. Returns whether a usable id was present. An id that no longer
     * exists is handled by refreshLensList exactly like a stale saved id: the pager stays put.
     */
    private boolean applyTargetLensExtra(Intent intent) {
        if (intent == null || utilSettings == null) return false;
        String target = intent.getStringExtra(EXTRA_TARGET_LENS_ID);
        if (target == null) return false;
        intent.removeExtra(EXTRA_TARGET_LENS_ID);
        if (target.isEmpty()) return false;
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, target);
        LensShortcuts.reportUsed(getApplicationContext(), target);
        return true;
    }

    @androidx.annotation.VisibleForTesting
    void handleTargetLensIntent(Intent intent) {
        String requestedLensId = intent != null ? intent.getStringExtra(EXTRA_TARGET_LENS_ID) : null;
        applyTargetLensExtra(intent);
        if (requestedLensId != null && !requestedLensId.isEmpty()) {
            lensHolder.setManagementReturnTargetId(requestedLensId);
        }
        refreshLensList();
    }

    /** Consumes (clears) the one-shot auto-export intent extra so a later rotation/recreate never
     *  re-triggers it from the Intent - the same one-shot-extra pattern this codebase already
     *  needs because ActHome is launchMode="singleTask" (relaunching it calls onNewIntent, not
     *  onCreate). FISH-013: the Intent extra alone is not durable across a process kill (nothing
     *  guarantees the OS replays the pre-mutation Intent), so the real durable signal is
     *  {@link UtilSettings#hasPendingAutoExportLens()} - set here (and by FrmLens.shareLensImage
     *  at the moment of the tap) and read back on every entry point, cleared only once
     *  exportActiveLensImage() actually reaches a terminal outcome. */
    private void consumeAutoExportExtra(Intent intent) {
        boolean fromIntent = intent != null && intent.getBooleanExtra(EXTRA_AUTO_EXPORT_LENS, false);
        if (fromIntent) {
            intent.removeExtra(EXTRA_AUTO_EXPORT_LENS);
            if (utilSettings != null) {
                utilSettings.setPendingAutoExportLens(true);
            }
        }
        boolean fromDisk = utilSettings != null && utilSettings.hasPendingAutoExportLens();
        if (fromIntent || fromDisk) {
            pendingAutoExportLens = true;
        }
    }

    /**
     * UI-017: collapsed home is a real top-search + app-grid column. LensView is a leaf custom
     * view, so padding does not move its drawing/touch geometry; margins change its laid-out
     * bounds and keep icons clear of both the search bar and system navigation.
     */
    private void applyHomeColumnInsets(View rootView) {
        // UI-019 fix: must read a fixed dimen, not searchBar's live layoutParams.topMargin - this
        // method re-runs on every onConfigurationChanged (rotation, etc.), and that margin is
        // itself overwritten below with (base + inset). Reading it back as the next "base" made
        // the top gap compound larger on every single config change.
        int searchBaseTopMargin = getResources().getDimensionPixelSize(R.dimen.home_search_top_margin);
        int searchBaseHorizontalMargin = getResources().getDimensionPixelSize(R.dimen.home_search_margin_horizontal);
        int lensSearchGap = getResources().getDimensionPixelSize(R.dimen.home_search_to_grid_gap);
        // FEAT-004: cap the lens/list column width on tablets and landscape large screens so it
        // doesn't stretch edge-to-edge (matches home_content_max_width, previously defined but
        // never applied). Extra margin only kicks in once the screen is actually wider than the cap.
        int maxContentWidth = getResources().getDimensionPixelSize(R.dimen.home_content_max_width);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int contentMaxWidthMargin = UtilCalculator.calculateContentMaxWidthMargin(screenWidth, maxContentWidth);

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            ViewGroup.MarginLayoutParams updatedSearchParams =
                    (ViewGroup.MarginLayoutParams) searchBar.getLayoutParams();
            int targetSearchTopMargin = searchBaseTopMargin + systemBars.top;
            int targetSearchLeftMargin = Math.max(searchBaseHorizontalMargin, systemBars.left);
            int targetSearchRightMargin = Math.max(searchBaseHorizontalMargin, systemBars.right);
            if (updatedSearchParams.topMargin != targetSearchTopMargin
                    || updatedSearchParams.leftMargin != targetSearchLeftMargin
                    || updatedSearchParams.rightMargin != targetSearchRightMargin) {
                updatedSearchParams.topMargin = targetSearchTopMargin;
                updatedSearchParams.leftMargin = targetSearchLeftMargin;
                updatedSearchParams.rightMargin = targetSearchRightMargin;
                searchBar.setLayoutParams(updatedSearchParams);
            }

            // SEARCH-010: the edge-to-edge SearchView content (incl. the docked T9 keypad) has no
            // inset handling of its own, so its last row drew under the navigation bar.
            View searchContent = findViewById(R.id.searchContent);
            if (searchContent != null && searchContent.getPaddingBottom() != systemBars.bottom) {
                searchContent.setPadding(0, 0, 0, systemBars.bottom);
            }

            searchBar.post(() -> {
                int targetLensTopMargin = targetSearchTopMargin
                        + searchBar.getHeight()
                        + lensSearchGap;

                int targetLensSideMargin = Math.max(systemBars.left, contentMaxWidthMargin);
                int targetLensRightMargin = Math.max(systemBars.right, contentMaxWidthMargin);

                ViewGroup.MarginLayoutParams updatedLensParams =
                        (ViewGroup.MarginLayoutParams) lensHolder.pager().getLayoutParams();
                if (updatedLensParams.topMargin != targetLensTopMargin
                        || updatedLensParams.bottomMargin != systemBars.bottom
                        || updatedLensParams.leftMargin != targetLensSideMargin
                        || updatedLensParams.rightMargin != targetLensRightMargin) {
                    updatedLensParams.topMargin = targetLensTopMargin;
                    updatedLensParams.bottomMargin = systemBars.bottom;
                    updatedLensParams.leftMargin = targetLensSideMargin;
                    updatedLensParams.rightMargin = targetLensRightMargin;
                    lensHolder.pager().setLayoutParams(updatedLensParams);
                }

                if (rvHomeAppList != null) {
                    ViewGroup.MarginLayoutParams updatedListParams =
                            (ViewGroup.MarginLayoutParams) rvHomeAppList.getLayoutParams();
                    if (updatedListParams.topMargin != targetLensTopMargin
                            || updatedListParams.bottomMargin != systemBars.bottom
                            || updatedListParams.leftMargin != targetLensSideMargin
                            || updatedListParams.rightMargin != targetLensRightMargin) {
                        updatedListParams.topMargin = targetLensTopMargin;
                        updatedListParams.bottomMargin = systemBars.bottom;
                        updatedListParams.leftMargin = targetLensSideMargin;
                        updatedListParams.rightMargin = targetLensRightMargin;
                        rvHomeAppList.setLayoutParams(updatedListParams);
                    }
                }
            });
            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    // LINT-009: `android:configChanges` keeps ActHome alive across rotation/fold, so rows must be
    // force-rebound here to re-measure for the new width/column count. `updateApps()`'s DiffUtil
    // path (used elsewhere for real data changes) would see identical content and skip every
    // rebind, leaving stale layout — notifyDataSetChanged is the correct call for this case, not
    // the lint-preferred "last resort".
    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        View root = findViewById(R.id.rootLayout);
        if (root != null) {
            applyHomeColumnInsets(root);
        }
        if (lensViews != null) {
            lensViews.post(() -> lensViews.invalidate());
        }
        if (homeAppAdapter != null) {
            homeAppAdapter.notifyDataSetChanged();
        }
    }

    private void setupViews() {
        lensHolder.attach();
        rvHomeAppList = findViewById(R.id.rvHomeAppList);
        utilSettings = new UtilSettings(this);
        homeAppAdapter = new AppAdapter(this, new ArrayList<>());
        rvHomeAppList.setLayoutManager(new LinearLayoutManager(this));
        rvHomeAppList.setAdapter(homeAppAdapter);
        progressBarHome = findViewById(R.id.progressBarHome);
        searchBar = findViewById(R.id.searchBar);
        searchView = findViewById(R.id.searchView);
        searchView.setupWithSearchBar(searchBar);
        // FEAT-009: recent-apps quick panel, reachable without opening search first.
        searchBar.inflateMenu(R.menu.menu_search_bar);
        searchBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menuItemRecentAppsPanel) {
                showRecentAppsPanel();
                return true;
            }
            return false;
        });
        // UI-009/UI-011: the near-opaque scrim behind the search panel is set declaratively via
        // app:backgroundTint="@color/search_view_scrim_background" in act_home.xml -
        // com.google.android.material.search.SearchView reads its panel background only from
        // that XML attribute at inflate time and exposes no public runtime setter for it. Content
        // sits directly on that scrim (no secondary card, see UI-011) since the scrim is already
        // near-opaque, so text contrast doesn't depend on an extra opaque layer.
        searchController = new HomeSearchController(this, searchBar, searchView, new HomeSearchController.Host() {
            @Override public UtilSettings settings() { return utilSettings; }
            @Override public ArrayList<App> apps() { return listApp; }
            @Override public List<App> appsForLens(String lensId) { return scopedApps(lensId); }
            @Override public LensViewHolder lenses() { return lensHolder; }
            @Override public HomeQuickActionDelegate quickActions() { return quickActions; }
            @Override public void restoreHiddenApps() {
                int position = lensHolder.pager().getCurrentItem();
                if (position >= 0 && position < lensHolder.currentLenses().size()) {
                    lensMenu.showHiddenLensAppsDialog(lensHolder.currentLenses().get(position).getId());
                }
            }
            @Override public void refreshSearchBarVisibility() { updateSearchBarVisibility(); }
            @Override public void refreshHiddenRecovery() { updateHiddenRecoveryButton(); }
            @Override public void hideHomeAppList() { if (rvHomeAppList != null) rvHomeAppList.setVisibility(View.GONE); }
            @Override public void refreshModeVisibility() { updateModeVisibility(); }
        });
        quickActions.attach();

        // Hide progress bar in test environments to prevent indeterminate animation loops from hanging tests
        boolean isTestEnv = false;
        try {
            Class.forName("androidx.test.platform.app.InstrumentationRegistry");
            isTestEnv = true;
        } catch (ClassNotFoundException ignored) {}
        if (isTestEnv) {
            progressBarHome.setVisibility(View.GONE);
        }
    }

    /**
     * FISH-014: synchronizes visibility and text for the lens navigation chrome (both dots indicator
     * and the active lens name label) in lockstep across all lifecycle, swipe, search, and mode states.
     */
    void updateLensNavigationChrome() {
        lensHolder.updateLensNavigationChrome();
    }

    // ========================================================================
    // FISH-008 Phase 2: multi-lens workspace paging + create/rename/delete
    // ========================================================================

    /** (Re)loads the lens list from Room and hands it to the pager adapter; hides the dots
     *  indicator entirely when only one lens exists so single-lens devices look and behave
     *  exactly like before this feature. */
    @androidx.annotation.VisibleForTesting
    void refreshLensList() {
        lensHolder.refreshLensList();
    }

    /** Runs on every page bind (initial + recycled). Only the page currently tracked as
     *  {@link #lensViews} (or the very first bind, before any page has been selected yet) is
     *  fed today's already-loaded apps here - a different, not-yet-selected page gets its own
     *  lens's apps once the user actually swipes to it (see lensPageChangeCallback), so a
     *  prefetched adjacent page never briefly shows the wrong lens's layout. */
    private void bindLensView(LensView view) {
        lensHolder.bindLensView(view);
    }

    private void bindLensView(LensView view, LensWorkspace lens) {
        lensHolder.bindLensView(view, lens);
    }

    /** The lens menu currently built, exposed so tests can inspect what it actually offered.
     *  ARCH-002: storage stays here (tests read it by name); {@link LensMenuCoordinator} owns the behavior. */
    @androidx.annotation.VisibleForTesting
    PopupMenu lensManagementMenu;

    @androidx.annotation.VisibleForTesting
    View getLensMenuAnchorForTest() {
        return lensMenu.menuAnchorForTest();
    }

    /** See {@link LensMenuCoordinator#lensMenuAnchor()}. */
    @androidx.annotation.VisibleForTesting
    View lensMenuAnchor() {
        return lensMenu.lensMenuAnchor();
    }

    /**
     * FISH-016: the one path into full search from Home outside the SearchBar itself - shared by
     * the lens pull-down gesture and the lens menu's Search item. A GONE bar (Clean mode, or the
     * user's own setting) has no layout for SearchView's morph to start from, so it is made
     * INVISIBLE first; the HIDDEN transition restores it from settings, never unconditionally.
     */
    @androidx.annotation.VisibleForTesting
    void openSearchFromHome() {
        if (searchController != null) searchController.openSearchFromHome();
    }

    /** FEAT-009: shared by the SearchBar icon and the lens-management menu's own entry point. */
    private void showRecentAppsPanel() {
        new RecentAppsPanelFragment().show(getSupportFragmentManager(), RecentAppsPanelFragment.TAG);
    }

    /** Tests exercise this exact method instead of a reimplementation (see {@link LensMenuCoordinator}). */
    @androidx.annotation.VisibleForTesting
    boolean onLensMenuItemSelected(int itemId, int position) {
        return lensMenu.onLensMenuItemSelected(itemId, position);
    }

    /** The menu entry names the action it performs, so it has to read the lens's current state. */
    @androidx.annotation.VisibleForTesting
    int lensSmartFocusMenuLabelRes(int position) {
        return lensMenu.smartFocusLabelResAt(position);
    }

    /** FISH-021: one place that adopts a fresh lens list, so the launcher shortcuts never go stale. */
    private void onLensesChanged(List<LensWorkspace> lenses) {
        lensHolder.onLensesChanged(lenses);
    }

    /** FISH-022: lens name opens grouped lens management and selection in a separate Activity. */
    @androidx.annotation.VisibleForTesting
    void openLensManagement() {
        lensMenu.openLensManagement(null);
    }

    @androidx.annotation.VisibleForTesting
    Intent lensManagementLaunchIntentForTest;

    /** Like {@link #lensSmartFocusMenuLabelRes}: the entry names the action, so it reads current state. */
    @androidx.annotation.VisibleForTesting
    int lensFreezeMenuLabelRes(int position) {
        return lensMenu.freezeLabelResAt(position);
    }

    /** FISH-EXPORT: see {@link LensMenuCoordinator#exportActiveLensImage()}. Package-visible so
     *  FISH-013's unrecoverable-state test can call it directly with lensViews == null. */
    @androidx.annotation.VisibleForTesting
    void exportActiveLensImage() {
        lensMenu.exportActiveLensImage();
    }

    /**
     * The lens dialog currently on screen, exposed so tests can drive its real buttons. Espresso
     * cannot be used for this on API 37 (its event injector needs the removed
     * InputManager.getInstance), and even a non-clicking onView() call builds that injector.
     */
    @androidx.annotation.VisibleForTesting
    androidx.appcompat.app.AlertDialog lensDialog;

    /** FISH-021: tick the apps a lens shows. See {@link LensMenuCoordinator#showLensAppsDialog}. */
    @androidx.annotation.VisibleForTesting
    void showLensAppsDialog(com.mckimquyen.model.LensWorkspace lens) {
        lensMenu.showLensAppsDialog(lens);
    }

    private void setupSearch() {
        searchController.setupSearch();
    }

    /** UI-012: re-read on every resume (matches updateSearchBarVisibility's pattern) so toggling
     *  a quick action off/on in Settings, or changing the custom hint, applies immediately. */
    private void updateSearchCustomization() {
        searchController.updateSearchCustomization();
    }

    private void updateSearchResults(CharSequence query) {
        searchController.updateSearchResults(query);
    }

    private void updateWebSearchFallback() {
        searchController.updateWebSearchFallback();
    }

    /** UI-011: all-apps fallback list, sorted alphabetically regardless of the lens grid's own
     *  sort setting (e.g. icon-color sort) - a text list should read A-Z. */

    /**
     * UI-009: replaces the old real-blur approach (RenderEffect on lensViews), which recomputed
     * every frame during the SearchBar<->SearchView morph and caused visible transition jank.
     * Instead, paint the status/navigation bars the exact same tonal color as the search scrim
     * (res/color/search_view_scrim_background.xml's ?attr/colorSurfaceContainerHigh) so the whole
     * screen - bars included - reads as one continuous surface, with zero per-frame cost.
     */
    private void setSearchSystemBarsHarmonized(boolean showing) {
        searchController.setSearchSystemBarsHarmonized(showing);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        quickActions.onPermissionResult(requestCode, permissions, grantResults);
    }

    private void hideSearchOverlay() {
        searchController.hideSearchOverlay();
    }

    /**
     * UI-021: restores the search overlay to its identity transform after a predictive-back
     * preview ends, whether the gesture completed (about to run hide()'s own animation from a
     * clean baseline) or was cancelled (snap back to fully visible, matching the system's own
     * predictive-back cancel convention).
     */
    private void resetPredictiveBackPreview() {
        searchController.resetPredictiveBackPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("roy93~", "onResume");
        updateColor();
        updateSearchBarVisibility();
        if (lensViews != null) {
            lensViews.refreshSmartFocus();
        }
        // Keep-screen-on flag is now applied to every screen by BaseActivity.onResume()
        // (called via super.onResume() above), not just here - see UtilSettings.KEY_KEEP_SCREEN_ON.
        updateSearchCustomization();
        updateModeVisibility();
        checkA11ySuggestion();
        setupTransparentSystemBarsForLollipop();
        // UI-009 fix: setupTransparentSystemBarsForLollipop() unconditionally forces transparent
        // bars - if the task is paused/resumed (e.g. Home button, or launching an app from a
        // search result) while SearchView is still showing, that clobbers the harmonized scrim
        // color set by setSearchSystemBarsHarmonized(), leaving the wallpaper showing through
        // behind the search panel instead of the matching tonal color.
        if (searchView.isShowing()) {
            setSearchSystemBarsHarmonized(true);
            // SEARCH-007: DND access has no onRequestPermissionsResult callback - re-resolve the
            // quick-action row on every resume so returning from openDndAccessSettings() reflects
            // the real granted/denied state instead of the stale pre-Settings copy.
            quickActions.updateQuickAction(searchController.text());
        }
        if (RAppsSingleton.getInstance().getApps() != null && !RAppsSingleton.getInstance().getApps().isEmpty()) {
            assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps()));
        }
    }

    public void updateModeVisibility() {
        if (searchView != null && searchView.isShowing()) {
            if (lensHolder.pager() != null) lensHolder.pager().setVisibility(View.INVISIBLE);
            updateLensNavigationChrome();
            if (rvHomeAppList != null) rvHomeAppList.setVisibility(View.GONE);
            return;
        }
        boolean isList = utilSettings != null && utilSettings.isListMode();
        if (isList) {
            if (lensHolder.pager() != null) lensHolder.pager().setVisibility(View.GONE);
            updateLensNavigationChrome();
            if (rvHomeAppList != null) {
                rvHomeAppList.setVisibility(listApp != null && !listApp.isEmpty() ? View.VISIBLE : View.GONE);
            }
        } else {
            if (rvHomeAppList != null) rvHomeAppList.setVisibility(View.GONE);
            boolean hasApps = listApp != null && !listApp.isEmpty();
            if (lensHolder.pager() != null) {
                lensHolder.pager().setVisibility(hasApps ? View.VISIBLE : View.INVISIBLE);
            }
            updateLensNavigationChrome();
        }
    }

    private void checkA11ySuggestion() {
        if (utilSettings != null && utilSettings.shouldSuggestAccessibleListMode()) {
            Snackbar.make(findViewById(R.id.rootLayout), R.string.a11y_suggest_list_mode_title, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.action_switch, v -> {
                        utilSettings.setLauncherMode(LauncherMode.LIST);
                        utilSettings.dismissA11ySuggestion();
                        updateModeVisibility();
                    })
                    .addCallback(new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar transientBottomBar, int event) {
                            if (event != DISMISS_EVENT_ACTION) {
                                utilSettings.dismissA11ySuggestion();
                            }
                        }
                    })
                    .show();
        }
    }

    // FISH-009: Material3 confirmation snackbar for live pinch-to-adjust curvature
    @androidx.annotation.VisibleForTesting
    Snackbar pinchCurvatureSnackbar;

    @androidx.annotation.VisibleForTesting
    void showPinchCurvatureSnackbar(float curvature) {
        if (pinchCurvatureSnackbar != null && pinchCurvatureSnackbar.isShown()) {
            pinchCurvatureSnackbar.dismiss();
        }
        String label = getString(R.string.setting_distortion_factor);
        String message = getString(R.string.pinch_curvature_preview, label, curvature);
        View root = findViewById(R.id.rootLayout);
        if (root == null) return;
        pinchCurvatureSnackbar = Snackbar.make(root, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.pinch_save_default, v -> {
                    // FISH-008 Phase 3: commitLiveDistortionFactor() already persists to the
                    // currently-viewed page's own lens (LensView.lensId) - a second explicit
                    // write to the global key here was redundant and, once lensId is real, wrong
                    // (it would leak this lens's curvature into the shared default).
                    if (lensViews != null) {
                        lensViews.commitLiveDistortionFactor();
                    }
                })
                .addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        if (event != DISMISS_EVENT_ACTION) {
                            if (lensViews != null) {
                                lensViews.resetLiveDistortionFactor();
                            }
                        }
                    }
                });
        pinchCurvatureSnackbar.show();
    }

    // DISPLAY-001: this is the live, continuously-dragged fisheye grid — the one screen
    // where a high refresh rate is actually visible to the user. Settings/list screens keep
    // BaseActivity's default (let the system choose) instead of forcing it here too.
    @Override
    protected boolean wantsHighRefreshRate() {
        return true;
    }

    // UI-001: re-read on every resume (matches updateColor's established pattern) so toggling
    // the setting in ActSettings takes effect immediately when the user returns Home.
    private void updateSearchBarVisibility() {
        UtilSettings settings = new UtilSettings(this);
        boolean isCleanMode = settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE);
        boolean showSearchBar = !isCleanMode && settings.getBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR);
        searchBar.setVisibility(showSearchBar ? View.VISIBLE : View.GONE);
        // A hidden bar (Clean mode / KEY_SHOW_SEARCH_BAR=false) only means there is no inline entry
        // point. It must not close a search the user opened via the pull-down or the lens menu, and
        // onResume re-runs this method.
        updateRecentAppsPanelIconVisibility();
        updateLensNavigationChrome();
    }

    /** FEAT-009: the icon has its own, finer-grained toggle than the whole SearchBar. */
    @androidx.annotation.VisibleForTesting
    void updateRecentAppsPanelIconVisibility() {
        boolean enabled = new UtilSettings(this).getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED);
        android.view.MenuItem item = searchBar.getMenu().findItem(R.id.menuItemRecentAppsPanel);
        if (item != null) {
            item.setVisible(enabled);
        }
    }

    private void setupTransparentSystemBarsForLollipop() {
        Window window = getWindow();
        window.getAttributes().systemUiVisibility |= (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
    }

    private void setBackground() {
        if (lensViews != null) {
            lensViews.invalidate();
        }
    }

    @androidx.annotation.VisibleForTesting
    boolean isSameAppList(java.util.List<App> list1, java.util.List<App> list2) {
        if (list1 == list2) return true;
        if (list1 == null || list2 == null) return false;
        if (list1.size() != list2.size()) return false;
        for (int i = 0; i < list1.size(); i++) {
            App app1 = list1.get(i);
            App app2 = list2.get(i);
            if (app1 == null || app2 == null) {
                if (app1 != app2) return false;
                continue;
            }
            if (!Objects.equals(app1.getPackageName().toString(), app2.getPackageName().toString())
                    || !Objects.equals(app1.getName().toString(), app2.getName().toString())
                    || !Objects.equals(app1.getLabel().toString(), app2.getLabel().toString())
                    || app1.isVisible() != app2.isVisible()
                    || app1.isOpened() != app2.isOpened()
                    || app1.getOpenCount() != app2.getOpenCount()
                    // UI-024: a badge-count-only change must still trigger a re-render, or
                    // AppEventManager.notifyAppsEdited() silently never reaches LensView/AppAdapter.
                    || app1.getNotificationCount() != app2.getNotificationCount()) {
                return false;
            }
        }
        return true;
    }

    /** FISH-021: {@code listApp} narrowed by one lens's scope. Search keeps using the full {@code listApp}. */
    private ArrayList<App> scopedApps(String lensId) {
        if (listApp == null || utilSettings == null) return listApp;
        return LensAppScope.filter(listApp,
                utilSettings.getLensAppScope(lensId), utilSettings.getLensAppSelection(lensId),
                utilSettings.getLensHiddenAppIds(lensId));
    }

    /** FEAT-011: only a lens emptied by temporary hiding gets the recovery button. */
    private void updateHiddenRecoveryButton() {
        View button = findViewById(R.id.restoreHiddenLensApps);
        if (button == null) return;
        boolean show = false;
        if (utilSettings != null && listApp != null && lensHolder.pager() != null
                && lensHolder.pager().getVisibility() == View.VISIBLE && searchView != null && !searchView.isShowing()
                && !utilSettings.isListMode()) {
            int position = lensHolder.pager().getCurrentItem();
            if (position >= 0 && position < lensHolder.currentLenses().size()) {
                String lensId = lensHolder.currentLenses().get(position).getId();
                show = !LensAppScope.filter(listApp, utilSettings.getLensAppScope(lensId),
                        utilSettings.getLensAppSelection(lensId)).isEmpty()
                        && scopedApps(lensId).isEmpty();
            }
        }
        button.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** FISH-021: re-applies each bound page's scope after a selection change. */
    @androidx.annotation.VisibleForTesting
    void refreshLensScope() {
        if (listApp == null) return;
        for (int i = 0; i < lensHolder.currentLenses().size(); i++) {
            LensView page = lensHolder.lensViewAt(i);
            if (page != null) {
                page.setApps(scopedApps(page.getLensId()));
            }
        }
        updateHiddenRecoveryButton();
    }

    private void assignApps(ArrayList<App> lApp) {
        Logger.d("ActHome: assignApps called, input list size: " + (lApp != null ? lApp.size() : "null"));
        if (lApp == null || lApp.isEmpty()) {
            listApp = new ArrayList<>();
            progressBarHome.setVisibility(View.INVISIBLE);
            updateModeVisibility();
            updateLensNavigationChrome();
            if (searchView.isShowing() || searchController.text().length() > 0) {
                updateSearchResults(searchController.text());
            }
            return;
        }

        // Filter out hidden apps first to get the target visible list
        ArrayList<App> visibleApps = new ArrayList<>();
        for (App app : lApp) {
            if (app.isVisible()) {
                visibleApps.add(app);
            }
        }

        if (visibleApps.isEmpty()) {
            listApp = visibleApps;
            progressBarHome.setVisibility(View.INVISIBLE);
            updateModeVisibility();
            updateLensNavigationChrome();
            if (searchView.isShowing() || searchController.text().length() > 0) {
                updateSearchResults(searchController.text());
            }
            return;
        }

        // Check if the new visible list is identical to the currently displayed listApp
        if (listApp != null && isSameAppList(listApp, visibleApps) && (homeAppAdapter == null || homeAppAdapter.getItemCount() > 0)) {
            Logger.d("ActHome: assignApps skipped - identical list of visible apps");
            updateModeVisibility();
            updateLensNavigationChrome();
            return;
        }

        progressBarHome.setVisibility(View.INVISIBLE);
        listApp = visibleApps;
        Logger.d("ActHome: Setting " + listApp.size() + " apps to lensViews and homeAppAdapter");
        if (lensViews != null) {
            lensViews.setApps(scopedApps(lensViews.getLensId()));
        }
        if (homeAppAdapter != null) {
            homeAppAdapter.updateApps(listApp);
        }
        updateModeVisibility();
        updateLensNavigationChrome();
        // PERF-004: the first moment icons are actually on screen - on a true cold start this is
        // the appsLoaded observer's call, not onCreate's (the snapshot is still empty then).
        // StartupTimingMetric's timeToFullDisplay only exists because of this call.
        if (UtilCalculator.shouldReportFullyDrawn(hasReportedFullyDrawn, !listApp.isEmpty())) {
            hasReportedFullyDrawn = true;
            reportFullyDrawnCallCount++;
            reportFullyDrawn();
        }
        if (searchView.isShowing() || searchController.text().length() > 0) {
            updateSearchResults(searchController.text());
        }
    }

    /**
     * SEARCH-014: this Activity's search adapter caches shortcut Drawables for its whole lifetime, so it
     * must follow the same memory-pressure tiers RApplication already applies to BitmapCache.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (searchController == null) return;
        if (level >= TRIM_MEMORY_BACKGROUND) {
            searchController.trimShortcutCache(0f);
        } else if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            searchController.trimShortcutCache(0.5f);
        } else if (level >= TRIM_MEMORY_RUNNING_LOW) {
            searchController.trimShortcutCache(0.75f);
        }
    }

    // LensViewHolderHost: the holder is the only writer of the active LensView pointer.
    @Override public android.app.Activity activity() { return this; }
    @Override public UtilSettings utilSettings() { return utilSettings; }
    @Override public ArrayList<App> listApp() { return listApp; }
    @Override public ArrayList<App> appsForLens(String lensId) { return scopedApps(lensId); }
    @Override public boolean isSearchShowing() { return searchView != null && searchView.isShowing(); }
    @Override public void refreshHiddenRecovery() { updateHiddenRecoveryButton(); }
    @Override public void offerPinchConfirmation(float curvature) { showPinchCurvatureSnackbar(curvature); }
    @Override public void offerLensMenu(View anchor) { lensMenu.showLensManagementMenu(anchor); }
    @Override public void dismissLensMenu() { lensMenu.dismissLensManagementMenu(); }
    @Override public View lensMenuAnchorAt(View lens, float x, float y) { return lensMenu.anchorAtPressedPoint(lens, x, y); }
    @Override public void requestLensManagement() { lensMenu.openLensManagement(null); }
    @Override public void requestSearch() { openSearchFromHome(); }
    @Override public boolean pendingAutoExportLens() { return pendingAutoExportLens; }
    @Override public void clearPendingAutoExportLens() { pendingAutoExportLens = false; }
    @Override public void requestLensExport() { exportActiveLensImage(); }

    @Override
    public void offerDropStrip(com.mckimquyen.views.LensDragPayload payload) {
        if (dropStrip == null) {
            dropStrip = new LensDropStrip(new LensDropStrip.Host() {
                @Override public android.app.Activity activity() { return ActHome.this; }
                @Override public ViewGroup root() { return findViewById(R.id.rootLayout); }
                @Override public List<LensWorkspace> lenses() { return lensHolder.currentLenses(); }
                @Override public void onDrop(com.mckimquyen.views.LensDragPayload payload, String targetLensId, String targetName) {
                    payload.getSourceView().moveAppToLensFromDrop(payload.getApp(), targetLensId, targetName);
                }
            });
        }
        dropStrip.show(payload);
    }

    @Override
    public void dismissDropStrip() {
        if (dropStrip != null) dropStrip.hide();
    }

    /** Test seam: drops [app] (by package) from [source] on [chip], as a drop on that chip would. */
    @androidx.annotation.VisibleForTesting
    boolean dropStripDropForTest(View chip, LensView source, String packageName) {
        return dropStrip != null && dropStrip.dropForTest(chip, source, packageName);
    }

    /** Test seam: ends the drag without a drop, as the system does on cancel. */
    @androidx.annotation.VisibleForTesting
    void dropStripEndForTest(View chip) {
        if (dropStrip != null) dropStrip.endForTest(chip);
    }
    @Override public void setActiveLensView(LensView view) { lensViews = view; }
    @Override public LensView activeLensView() { return lensViews; }
    @Override public void setLensTabMediator(TabLayoutMediator mediator) { lensTabMediator = mediator; }
    @Override public TabLayoutMediator lensTabMediator() { return lensTabMediator; }

    // HomeQuickActionHost
    @Override public CharSequence searchText() { return searchController.text(); }
    @Override public void hideSearch() { hideSearchOverlay(); }
    @Override public void refreshWebSearchFallback() { updateWebSearchFallback(); }

    @Override
    protected void onDestroy() {
        if (dropStrip != null) {
            dropStrip.onDestroy();
            dropStrip = null;
        }
        lensHolder.onDestroy();
        // FISH-008 Phase 3: both hold a View (a dialog's decor, a popup's anchor) belonging to
        // this Activity. Dismiss a dialog still showing during a rotation - it would leak its
        // window - and drop both references so the destroyed Activity is not retained.
        lensMenu.onDestroy();
        searchController.onDestroy();
        quickActions.onDestroy();
        super.onDestroy();
    }
}
