package com.mckimquyen.ui;

import static com.mckimquyen.ext.ActivityKt.rateAppInApp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
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
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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
import com.mckimquyen.search.ContactSearchEngine;
import com.mckimquyen.search.ContactSearchResult;
import com.mckimquyen.search.QuickAction;
import com.mckimquyen.search.QuickActionEngine;
import com.mckimquyen.search.SearchHistoryStore;
import com.mckimquyen.search.SearchResultAdapter;
import com.mckimquyen.util.ApertureRevealHelper;
import com.mckimquyen.util.LensLabelResolver;
import com.mckimquyen.util.Logger;
import com.mckimquyen.util.PolaroidExportHelper;
import com.mckimquyen.util.UIUtils;
import com.mckimquyen.util.UtilCalculator;
import com.mckimquyen.services.AppEventManager;
import com.mckimquyen.util.UtilSettings;
import com.mckimquyen.views.LensView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import kotlin.Unit;

//2023.03.19 tried to convert kotlin but failed
public class ActHome extends ActBase {

    // SEARCH-004: request codes for the two permission-gated quick actions.
    private static final int REQUEST_CODE_CAMERA = 1001;
    private static final int REQUEST_CODE_WIFI_SSID = 1002;
    private static final int REQUEST_CODE_CONTACTS = 1003;

    // FISH-016: ids 1-6 of the lens-management menu are the pre-existing literals above in
    // showLensManagementMenu/onLensMenuItemSelected; the two new entries are named.
    @androidx.annotation.VisibleForTesting
    static final int MENU_ID_SEARCH_APPS = 7;
    @androidx.annotation.VisibleForTesting
    static final int MENU_ID_TOGGLE_CLEAN_LENS = 8;

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
                hideSearch();
            }
        }
    };

    // FISH-008 Phase 2: the currently VISIBLE page's LensView. Kept current by
    // lensPageChangeCallback (or bootstrapped once at cold start by bindLensView) - never
    // written to from a page bind that isn't the tracked current one, so a not-yet-selected
    // prefetched page can't clobber it with the wrong lens's view.
    LensView lensViews;
    private ViewPager2 lensPager;
    private TabLayout lensPageIndicator;
    private TextView tvLensName;
    private LensPagerAdapter lensPagerAdapter;
    private List<LensWorkspace> currentLenses = new ArrayList<>();

    /** FISH-EXPORT: set by FrmLens's share button on the Intent that (re)launches this singleTask
     *  activity, so onLensMenuItemSelected's item 5 path runs automatically once currentLenses is
     *  loaded, instead of FrmLens needing its own copy of the export logic. */
    public static final String EXTRA_AUTO_EXPORT_LENS =
            "com.mckimquyen.lenslauncher.EXTRA_AUTO_EXPORT_LENS";
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
    private final ViewPager2.OnPageChangeCallback lensPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            LensView view = lensViewAt(position);
            if (view != null) {
                lensViews = view;
                if (listApp != null) {
                    view.setApps(listApp);
                }
            }
            if (position >= 0 && position < currentLenses.size()) {
                LensWorkspace lens = currentLenses.get(position);
                String currentActive = utilSettings != null
                        ? utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                        : null;
                if (lens.getId() != null && !lens.getId().equals(currentActive)) {
                    if (utilSettings != null) {
                        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lens.getId());
                    }
                    Object application = getApplication();
                    if (application instanceof RApplication) {
                        ((RApplication) application).getAppRefreshPipeline().switchLens(lens.getId());
                    }
                }
                // FISH-012: this page may already have been bound (and its pending value already
                // silently restored) as a prefetched neighbour before the user ever swiped to it
                // - bindLensView will not run again just because it's now selected, so the
                // confirmation Snackbar has to be (re-)offered from here too.
                maybeShowResurrectSnackbar(lens);
                updateLensNavigationChrome();
            }
        }
    };
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
    private EditText appSearch;
    private View recentHeader;
    private TextView allAppsHeader;
    private TextView resultsSectionHeader;
    private View llEmptyQuickActions;
    private TextView noSearchResults;
    private TextView webSearchFallback;
    private RecyclerView searchResults;
    private View quickActionRow;
    private View quickActionDivider;
    private TextView tvQuickActionLabel;
    private TextView tvQuickActionValue;
    private View contactActionRow;
    private View contactActionDivider;
    private TextView tvContactResultName;
    private TextView tvContactResultPhone;
    private Button btContactCall;
    private Button btContactMessage;
    private SearchResultAdapter searchResultAdapter;
    private SearchHistoryStore searchHistoryStore;

    private void updateColor() {
        var mUtilSettings = new UtilSettings(this);
        var kBackground = mUtilSettings.getBackgroundMode();
        Log.d("roy93~", "kBackground " + kBackground);
        if (kBackground == BackgroundMode.COLOR) {
            Log.d("roy93~", "setBackgroundColor");
            var kBackgroundColor = mUtilSettings.getString(UtilSettings.KEY_BACKGROUND_COLOR);
            Log.d("roy93~", "kBackgroundColor " + kBackgroundColor);
            findViewById(R.id.rootLayout).setBackgroundColor(Color.parseColor(kBackgroundColor));
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
        refreshLensList();
        assignApps(Objects.requireNonNull(Objects.requireNonNull(RAppsSingleton.getInstance()).getApps()));

        // Observe app events using LiveData
        AppEventManager.INSTANCE.getAppsLoaded().observe(this,
                data -> assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps())));

        AppEventManager.INSTANCE.getAppsUpdated().observe(this,
                data -> assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps())));

        AppEventManager.INSTANCE.getAppsEdited().observe(this,
                data -> assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps())));

        AppEventManager.INSTANCE.getVisibilityChanged().observe(this,
                data -> assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps())));

        AppEventManager.INSTANCE.getLockChanged().observe(this,
                data -> assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps())));

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

            searchBar.post(() -> {
                int targetLensTopMargin = targetSearchTopMargin
                        + searchBar.getHeight()
                        + lensSearchGap;

                int targetLensSideMargin = Math.max(systemBars.left, contentMaxWidthMargin);
                int targetLensRightMargin = Math.max(systemBars.right, contentMaxWidthMargin);

                ViewGroup.MarginLayoutParams updatedLensParams =
                        (ViewGroup.MarginLayoutParams) lensPager.getLayoutParams();
                if (updatedLensParams.topMargin != targetLensTopMargin
                        || updatedLensParams.bottomMargin != systemBars.bottom
                        || updatedLensParams.leftMargin != targetLensSideMargin
                        || updatedLensParams.rightMargin != targetLensRightMargin) {
                    updatedLensParams.topMargin = targetLensTopMargin;
                    updatedLensParams.bottomMargin = systemBars.bottom;
                    updatedLensParams.leftMargin = targetLensSideMargin;
                    updatedLensParams.rightMargin = targetLensRightMargin;
                    lensPager.setLayoutParams(updatedLensParams);
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
        lensPager = findViewById(R.id.lensPager);
        lensPageIndicator = findViewById(R.id.lensPageIndicator);
        tvLensName = findViewById(R.id.tvLensName);
        lensPagerAdapter = new LensPagerAdapter((view, lens) -> {
            // FISH-008 Phase 3: every page carries its own lens identity, set on every bind and
            // rebind (not on page selection) so a prefetched neighbour page never draws with the
            // currently-visible lens's curvature/Smart Focus for a frame before being swiped to.
            view.setLensId(lens.getId());
            bindLensView(view, lens);
            return Unit.INSTANCE;
        });
        lensPager.setAdapter(lensPagerAdapter);
        new TabLayoutMediator(lensPageIndicator, lensPager,
                (tab, position) -> tab.setIcon(R.drawable.lens_page_indicator_dot)).attach();
        lensPager.registerOnPageChangeCallback(lensPageChangeCallback);
        lensPageIndicator.setOnLongClickListener(v -> {
            showLensManagementMenu(v);
            return true;
        });
        tvLensName.setOnLongClickListener(v -> {
            showLensManagementMenu(v);
            return true;
        });
        rvHomeAppList = findViewById(R.id.rvHomeAppList);
        utilSettings = new UtilSettings(this);
        homeAppAdapter = new AppAdapter(this, new ArrayList<>());
        rvHomeAppList.setLayoutManager(new LinearLayoutManager(this));
        rvHomeAppList.setAdapter(homeAppAdapter);
        progressBarHome = findViewById(R.id.progressBarHome);
        searchBar = findViewById(R.id.searchBar);
        searchView = findViewById(R.id.searchView);
        searchView.setupWithSearchBar(searchBar);
        appSearch = searchView.getEditText();
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
        recentHeader = findViewById(R.id.recentHeader);
        allAppsHeader = findViewById(R.id.allAppsHeader);
        resultsSectionHeader = findViewById(R.id.resultsSectionHeader);
        llEmptyQuickActions = findViewById(R.id.llEmptyQuickActions);
        noSearchResults = findViewById(R.id.tvNoSearchResults);
        webSearchFallback = findViewById(R.id.tvWebSearchFallback);
        searchResults = findViewById(R.id.rvSearchResults);
        quickActionRow = findViewById(R.id.quickActionRow);
        quickActionDivider = findViewById(R.id.quickActionDivider);
        tvQuickActionLabel = findViewById(R.id.tvQuickActionLabel);
        tvQuickActionValue = findViewById(R.id.tvQuickActionValue);
        contactActionRow = findViewById(R.id.contactActionRow);
        contactActionDivider = findViewById(R.id.contactActionDivider);
        tvContactResultName = findViewById(R.id.tvContactResultName);
        tvContactResultPhone = findViewById(R.id.tvContactResultPhone);
        btContactCall = findViewById(R.id.btContactCall);
        btContactMessage = findViewById(R.id.btContactMessage);

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
        if (lensPageIndicator == null) return;
        boolean isCleanMode = utilSettings != null && utilSettings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE);
        boolean isList = utilSettings != null && utilSettings.isListMode();
        boolean isSearchShowing = searchView != null && searchView.isShowing();
        boolean visible = !isCleanMode && !isList && !isSearchShowing && currentLenses != null && currentLenses.size() > 1;

        lensPageIndicator.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (tvLensName != null) {
            tvLensName.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible && currentLenses != null) {
                int position = lensPager != null ? lensPager.getCurrentItem() : 0;
                if (position >= 0 && position < currentLenses.size()) {
                    tvLensName.setText(currentLenses.get(position).getName());
                } else {
                    String activeId = utilSettings != null
                            ? utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                            : null;
                    tvLensName.setText(LensLabelResolver.resolveActiveLensName(currentLenses, activeId));
                }
            }
        }
    }

    // ========================================================================
    // FISH-008 Phase 2: multi-lens workspace paging + create/rename/delete
    // ========================================================================

    /** (Re)loads the lens list from Room and hands it to the pager adapter; hides the dots
     *  indicator entirely when only one lens exists so single-lens devices look and behave
     *  exactly like before this feature. */
    @androidx.annotation.VisibleForTesting
    void refreshLensList() {
        LensWorkspace.loadAll(lenses -> {
            currentLenses = lenses;
            lensPagerAdapter.submitLenses(lenses);
            updateLensNavigationChrome();
            // FISH-008 Phase 2 r2: restore active page after rotation/recreate so the user stays on
            // the lens they were viewing instead of bouncing back to page 0.
            if (utilSettings != null && !lenses.isEmpty()) {
                String savedActive = utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID);
                for (int i = 0; i < lenses.size(); i++) {
                    if (lenses.get(i).getId().equals(savedActive)) {
                        final int targetIndex = i;
                        // ViewPager2 needs a layout tick after adapter update to accept target position
                        lensPager.post(() -> {
                            if (lensPager != null && lensPager.getCurrentItem() != targetIndex) {
                                lensPager.setCurrentItem(targetIndex, false);
                            }
                        });
                        break;
                    }
                }
            }
            // B3 (test-audit): consumed here ONLY when a page is already bound (lensViews !=
            // null) - the real-world case that matters most, since ActHome is launchMode=
            // "singleTask" and FrmLens's share button re-launches an already-running, already-
            // fully-bound instance via onNewIntent. submitLenses() with an unchanged lens list
            // triggers no rebind at all in that case, so bindLensView's own check below would
            // never run - this is the only place that ever fires for that path. On a genuine
            // cold launch, lensViews can still be null here (the DB load can finish before
            // ViewPager2 has bound its first page) - leave the flag set so bindLensView catches
            // it the moment that first bind actually happens, instead of firing against a null
            // view or, worse, silently losing the one-shot request forever.
            if (pendingAutoExportLens && lensViews != null) {
                pendingAutoExportLens = false;
                lensPager.post(this::exportActiveLensImage);
            }
            return Unit.INSTANCE;
        });
    }

    /** Runs on every page bind (initial + recycled). Only the page currently tracked as
     *  {@link #lensViews} (or the very first bind, before any page has been selected yet) is
     *  fed today's already-loaded apps here - a different, not-yet-selected page gets its own
     *  lens's apps once the user actually swipes to it (see lensPageChangeCallback), so a
     *  prefetched adjacent page never briefly shows the wrong lens's layout. */
    private void bindLensView(LensView view) {
        bindLensView(view, null);
    }

    /**
     * @param lens the workspace this page is being bound to, when the caller knows it. During a
     *             rebind the holder is not yet discoverable through the pager, so the lens id is
     *             the only reliable way to tell "this is the page the user is looking at".
     */
    private void bindLensView(LensView view, LensWorkspace lens) {
        view.setPackageManager(getPackageManager());
        view.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        view.setOnCurvatureAdjustedListener((curvature, finished) -> {
            if (finished) {
                showPinchCurvatureSnackbar(curvature);
            }
        });
        // FISH-008 Phase 3: the dots indicator - the menu's only other entry point - is hidden
        // while a single lens exists, so long-pressing empty grid space has to reach it too.
        // Otherwise no single-lens install (i.e. everyone, right after the v11 migration) can
        // ever create a second lens. Anchored on the page itself, same as UI-022's icon menu.
        view.setOnEmptySpaceLongPressListener(() -> showLensManagementMenu(lensMenuAnchor()));
        // FISH-016: pull-down from the lens top opens full search, in Clean mode too.
        view.setOnSearchSwipeDownListener(this::openSearchFromHome);
        // FISH-012: a pinch adjustment that was never resolved (Save/dismiss) before the process
        // died is restored here silently, on every bind - not just the active page - so it's
        // ready the instant the user swipes to whichever lens it belonged to. The confirmation
        // Snackbar itself is only re-shown for the page currently on screen (below) - see
        // maybeShowResurrectSnackbar's own doc comment for why onPageSelected also needs it.
        restorePendingPinchIfAny(view, lens);
        // FISH-008 Phase 3 fix: after a configuration change every page rebinds while `listApp`
        // is already populated, but `lensViews` still points at the destroyed Activity's
        // LensView - so no page matched here and the restored page was left with an empty grid
        // until the next app-list broadcast. The pager cannot resolve its holder mid-rebind, so
        // match on the lens id instead: the page whose lens is the active one is the visible one.
        // Found by rotating a real TECNO KJ7 with two lenses on the second page.
        boolean isActiveLensPage = lens != null && utilSettings != null
                && lens.getId().equals(utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID));
        if (lensViews == null || lensViews == view || isActiveLensPage) {
            lensViews = view;
            if (listApp != null) {
                view.setApps(listApp);
            }
            maybeShowResurrectSnackbar(lens);
            // B3 (test-audit): must fire from here, the exact point lensViews first becomes
            // non-null, not from refreshLensList()'s DB-load callback via a separate
            // lensPager.post() - that raced two independent queuing mechanisms (a plain
            // Handler.post against the RecyclerView's own Choreographer-scheduled bind pass) and
            // could silently lose the one-shot auto-export forever if this bind lost the race.
            if (pendingAutoExportLens) {
                pendingAutoExportLens = false;
                view.post(this::exportActiveLensImage);
            }
        }
    }

    /** FISH-012: silently restores {@code lens}'s pending pinch value (if any) into {@code view}'s
     *  live/preview state. Safe to call on every bind, active page or not, so a not-yet-visible
     *  page already shows the right curvature the instant the user swipes to it. */
    private void restorePendingPinchIfAny(LensView view, LensWorkspace lens) {
        if (view == null || lens == null || utilSettings == null) return;
        Float pending = utilSettings.getPendingDistortionFactor(lens.getId());
        if (pending != null) {
            view.restoreLiveDistortionFactor(pending);
        }
    }

    /** FISH-012: lens ids this Activity instance has already offered the resurrect Snackbar for -
     *  guards against showing it twice for the same cold-launch page (bindLensView AND
     *  onPageSelected both fire for page 0 at launch). A second show would call
     *  showPinchCurvatureSnackbar again, which dismisses the still-showing first Snackbar as a
     *  side effect - and that dismissal now calls resetLiveDistortionFactor(), which clears the
     *  pending key too, silently un-resurrecting what was just resurrected. Reset per Activity
     *  instance only (a fresh instance after recreate/relaunch should still get one real prompt
     *  if the value is still genuinely unresolved). */
    private final java.util.Set<String> resurrectPromptedLensIds = new java.util.HashSet<>();

    /** FISH-012: re-shows the confirmation Snackbar for {@code lens}'s pending pinch value, if
     *  any - call this ONLY for the page the user is actually looking at right now. Called from
     *  both bindLensView (covers the active page being freshly bound or rebound) and
     *  lensPageChangeCallback.onPageSelected (covers swiping onto a page that was already bound
     *  as a prefetched neighbour and therefore never goes through bindLensView again -
     *  ViewPager2's underlying RecyclerView keeps adjacent pages bound without rebinding them on
     *  selection). resurrectPromptedLensIds above ensures only the first of those two calls for a
     *  given lens actually shows anything. */
    private void maybeShowResurrectSnackbar(LensWorkspace lens) {
        if (lens == null || utilSettings == null) return;
        if (!resurrectPromptedLensIds.add(lens.getId())) return;
        Float pending = utilSettings.getPendingDistortionFactor(lens.getId());
        if (pending != null) {
            showPinchCurvatureSnackbar(pending);
        }
    }

    /** ViewPager2 wraps exactly one child, its own internal RecyclerView - a well-known (if
     *  unofficial) way to reach a specific page's already-bound ViewHolder, used here only to
     *  resolve which LensView a just-selected page actually is. */
    private LensView lensViewAt(int position) {
        if (lensPager == null || lensPager.getChildCount() == 0) return null;
        View child = lensPager.getChildAt(0);
        if (!(child instanceof RecyclerView)) return null;
        RecyclerView.ViewHolder holder = ((RecyclerView) child).findViewHolderForAdapterPosition(position);
        if (holder instanceof LensPagerAdapter.PageHolder) {
            return ((LensPagerAdapter.PageHolder) holder).getLensView();
        }
        return null;
    }

    /** The lens menu currently built, exposed so tests can inspect what it actually offered. */
    @androidx.annotation.VisibleForTesting
    PopupMenu lensManagementMenu;

    /**
     * A PopupMenu sizes and places itself against its anchor, so anchoring to the full-screen
     * LensView pushed the menu off the top edge and clipped it to a single visible row - found on
     * a real TECNO KJ7, where only "Add lens" of the four items was reachable. The page-dots
     * indicator is a small view near the top with room below it, exactly what a popup wants; it is
     * the menu's other entry point anyway, so both routes now open the identical menu. It is GONE
     * on a single-lens install, but a hidden view is still a valid anchor (it has real bounds),
     * which is precisely the case this entry point exists for.
     */
    @androidx.annotation.VisibleForTesting
    View lensMenuAnchor() {
        // setupViews() assigns lensPageIndicator before the adapter that can trigger this, so it
        // is non-null by the time any menu can open; rootLayout is a defensive fallback only.
        return lensPageIndicator != null ? lensPageIndicator : findViewById(R.id.rootLayout);
    }

    private void showLensManagementMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        lensManagementMenu = menu;
        menu.getMenu().add(0, 1, 0, R.string.lens_add);
        menu.getMenu().add(0, 2, 0, R.string.lens_rename);
        android.view.MenuItem delete = menu.getMenu().add(0, 3, 0, R.string.lens_delete);
        delete.setEnabled(currentLenses.size() > 1);
        int position = lensPager.getCurrentItem();
        // FISH-008 Phase 3: Smart Focus is per-lens, so it gets a quick toggle right here on the
        // lens it applies to (FrmSettings' own switch covers the same lens from Settings). The
        // label states the action rather than using a checkable item - PopupMenu check marks and
        // icons proved unreliable on real hardware in UI-022.
        menu.getMenu().add(0, 4, 0, lensSmartFocusMenuLabelRes(position));
        menu.getMenu().add(0, 5, 0, R.string.lens_share_image);
        menu.getMenu().add(0, 6, 0, R.string.recent_apps);
        // FISH-016: accessible fallback to the pull-down gesture, plus a quick Clean-mode switch
        // (reusing the Settings strings, already translated in every locale).
        menu.getMenu().add(0, MENU_ID_SEARCH_APPS, 0, R.string.search_apps_hint);
        menu.getMenu().add(0, MENU_ID_TOGGLE_CLEAN_LENS, 0, R.string.setting_clean_lens_mode);
        menu.setOnMenuItemClickListener(item -> onLensMenuItemSelected(item.getItemId(), position));
        menu.show();
    }

    /**
     * FISH-016: the one path into full search from Home outside the SearchBar itself - shared by
     * the lens pull-down gesture and the lens menu's Search item. A GONE bar (Clean mode, or the
     * user's own setting) has no layout for SearchView's morph to start from, so it is made
     * INVISIBLE first; the HIDDEN transition restores it from settings, never unconditionally.
     */
    @androidx.annotation.VisibleForTesting
    void openSearchFromHome() {
        if (searchView == null || searchBar == null || searchView.isShowing()) return;
        if (searchBar.getVisibility() == View.GONE) {
            searchBar.setVisibility(View.INVISIBLE);
        }
        searchBar.post(searchView::show);
    }

    /** FEAT-009: shared by the SearchBar icon and the lens-management menu's own entry point. */
    private void showRecentAppsPanel() {
        new RecentAppsPanelFragment().show(getSupportFragmentManager(), RecentAppsPanelFragment.TAG);
    }

    /**
     * The menu's real behavior, split out from the {@link PopupMenu} that hosts it. Espresso
     * cannot inject the touches that drive a popup on this project's newer test devices
     * (InputManager.getInstance is gone on API 37), so tests exercise this exact method instead
     * of a reimplementation of it.
     */
    @androidx.annotation.VisibleForTesting
    boolean onLensMenuItemSelected(int itemId, int position) {
        if (position < 0 || position >= currentLenses.size()) return false;
        LensWorkspace current = currentLenses.get(position);
        if (itemId == 1) {
            createLensDialog(current.getId());
            return true;
        } else if (itemId == 2) {
            renameLensDialog(current);
            return true;
        } else if (itemId == 3) {
            confirmDeleteLensDialog(current);
            return true;
        } else if (itemId == 4) {
            toggleSmartFocusForLens(current);
            return true;
        } else if (itemId == 5) {
            // FISH-013: this menu's "Share" entry calls exportActiveLensImage() directly, with no
            // pending-flag write first - intentional, not a missed call site. It's a synchronous
            // foreground action (the menu is already on screen), not the cross-activity/
            // process-death gap between FrmLens's tap and export completing that FISH-013 exists
            // to cover; exportActiveLensImage() clearing an already-absent key is a harmless no-op.
            exportActiveLensImage();
            return true;
        } else if (itemId == 6) {
            showRecentAppsPanel();
            return true;
        } else if (itemId == MENU_ID_SEARCH_APPS) {
            openSearchFromHome();
            return true;
        } else if (itemId == MENU_ID_TOGGLE_CLEAN_LENS) {
            toggleCleanLensMode();
            return true;
        }
        return false;
    }

    /** The menu entry names the action it performs, so it has to read the lens's current state. */
    @androidx.annotation.VisibleForTesting
    int lensSmartFocusMenuLabelRes(int position) {
        boolean on = position >= 0 && position < currentLenses.size()
                && utilSettings != null
                && utilSettings.isSmartFocusBias(currentLenses.get(position).getId());
        return on ? R.string.lens_smart_focus_disable : R.string.lens_smart_focus_enable;
    }

    /** FISH-016: Clean mode is global, so unlike Smart Focus it flips one shared key, then
     *  re-applies the same gates every resume already uses - no second source of truth. */
    private void toggleCleanLensMode() {
        if (utilSettings == null) return;
        utilSettings.save(UtilSettings.KEY_CLEAN_LENS_MODE,
                !utilSettings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE));
        updateSearchBarVisibility();
        if (lensViews != null) {
            lensViews.invalidate();
        }
    }

    private void toggleSmartFocusForLens(LensWorkspace lens) {
        if (utilSettings == null) return;
        boolean enabled = !utilSettings.isSmartFocusBias(lens.getId());
        utilSettings.saveSmartFocusBias(lens.getId(), enabled);
        if (lensViews != null) {
            lensViews.refreshSmartFocus();
        }
    }

    /** FISH-EXPORT: snapshots the currently visible lens - via {@link #lensViews}, this file's
     *  existing "which LensView is actually on screen" pointer (see its own field comment), kept
     *  correct across page-selection and rebind-after-rotation - into a polaroid-framed PNG and
     *  hands it to the share sheet via {@link #lensShareLauncher}. Package-visible (not private)
     *  so FISH-013's unrecoverable-state test can call it directly with lensViews == null instead
     *  of racing ViewPager2's real bind timing to reach that branch. */
    @androidx.annotation.VisibleForTesting
    void exportActiveLensImage() {
        LensView view = lensViews;
        if (view == null) {
            // FISH-013: an auto-export request that can never be resolved (no bound page) must
            // not stay pending on disk forever, and must not fail silently.
            clearPendingAutoExportLensAndNotifyFailure();
            return;
        }
        String activeLensId = view.getLensId();
        LensWorkspace matched = null;
        for (LensWorkspace lens : currentLenses) {
            if (lens.getId().equals(activeLensId)) {
                matched = lens;
                break;
            }
        }
        if (matched == null) {
            // FISH-013: the requesting lens no longer exists (e.g. deleted while the request was
            // in flight) - same unrecoverable case as above.
            clearPendingAutoExportLensAndNotifyFailure();
            return;
        }
        String lensName = matched.getName();
        PolaroidExportHelper.exportAsync(view, lensName, this, uri -> {
            // FISH-013: only clear the durable pending flag once export has actually reached a
            // terminal outcome (success or failure) - never at request time - so a process kill
            // mid-export still resurrects the request on next launch instead of losing it.
            if (utilSettings != null) {
                utilSettings.clearPendingAutoExportLens();
            }
            if (uri != null) {
                try {
                    lensShareLauncher.launch(Intent.createChooser(
                            PolaroidExportHelper.buildShareIntent(uri),
                            getString(R.string.share_via)));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
            }
            return Unit.INSTANCE;
        });
    }

    /** FISH-013: shared terminal-failure path for exportActiveLensImage's two unrecoverable
     *  early-return cases (no bound view, or the active lens was deleted) - clears the durable
     *  pending flag so the request stops resurrecting forever, and tells the user explicitly
     *  instead of the previous silent no-op. */
    private void clearPendingAutoExportLensAndNotifyFailure() {
        if (utilSettings != null) {
            utilSettings.clearPendingAutoExportLens();
        }
        Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
    }

    private void createLensDialog(String copyFromLensId) {
        String defaultName = getString(R.string.lens_new_name_template, currentLenses.size() + 1);
        showLensNameDialog(R.string.lens_add, defaultName, name ->
                LensWorkspace.createLens(name, copyFromLensId, lenses -> {
                    // FISH-008 Phase 3: the new lens copies its source's curvature/Smart Focus
                    // too, not just its layout. createLens() doesn't return the new id, so find
                    // the one lens that wasn't there before.
                    if (utilSettings != null) {
                        for (LensWorkspace lens : lenses) {
                            if (!containsLens(currentLenses, lens.getId())) {
                                utilSettings.duplicateLensSettings(copyFromLensId, lens.getId());
                            }
                        }
                    }
                    currentLenses = lenses;
                    lensPagerAdapter.submitLenses(lenses);
                    updateLensNavigationChrome();
                    return Unit.INSTANCE;
                }));
    }

    private static boolean containsLens(List<LensWorkspace> lenses, String lensId) {
        for (LensWorkspace lens : lenses) {
            if (lens.getId().equals(lensId)) return true;
        }
        return false;
    }

    private void renameLensDialog(LensWorkspace lens) {
        showLensNameDialog(R.string.lens_rename_title, lens.getName(), name ->
                LensWorkspace.renameLens(lens, name, lenses -> {
                    currentLenses = lenses;
                    lensPagerAdapter.submitLenses(lenses);
                    updateLensNavigationChrome();
                    return Unit.INSTANCE;
                }));
    }

    private interface LensNameCallback {
        void onNameEntered(String name);
    }

    /**
     * The lens dialog currently on screen, exposed so tests can drive its real buttons. Espresso
     * cannot be used for this on API 37 (its event injector needs the removed
     * InputManager.getInstance), and even a non-clicking onView() call builds that injector.
     */
    @androidx.annotation.VisibleForTesting
    androidx.appcompat.app.AlertDialog lensDialog;

    private void showLensNameDialog(int titleRes, String initialText, LensNameCallback onDone) {
        EditText input = new EditText(this);
        // A programmatic View with no id is skipped by onSaveInstanceState, so the typed name would
        // be lost on rotation. android.R.id.edit is the platform's own id for this exact role.
        input.setId(android.R.id.edit);
        input.setText(initialText);
        input.setHint(R.string.lens_name_hint);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        int paddingH = (int) (24 * getResources().getDisplayMetrics().density);
        int paddingV = (int) (8 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(paddingH, paddingV, paddingH, 0);
        container.addView(input);

        lensDialog = new MaterialAlertDialogBuilder(this, R.style.MaterialYouDialogTheme)
                .setTitle(titleRes)
                .setView(container)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        // FISH-010: circular reveal expanding from/collapsing to the lens menu's own anchor -
        // the same anchor already resolved for the menu itself (lensMenuAnchor()).
        ApertureRevealHelper.prepareDialog(lensDialog);
        lensDialog.show();
        ApertureRevealHelper.revealShownDialog(lensDialog, lensMenuAnchor());

        lensDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText() != null ? input.getText().toString().trim() : "";
            ApertureRevealHelper.dismissWithReveal(lensDialog, lensMenuAnchor(), () -> {
                if (!name.isEmpty()) {
                    onDone.onNameEntered(name);
                }
            });
        });
        lensDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v ->
                ApertureRevealHelper.dismissWithReveal(lensDialog, lensMenuAnchor(), null)
        );
    }

    private void confirmDeleteLensDialog(LensWorkspace lens) {
        String message = getString(R.string.lens_delete_confirm_message, lens.getName());
        lensDialog = new MaterialAlertDialogBuilder(this, R.style.MaterialYouDialogTheme)
                .setTitle(R.string.lens_delete)
                .setMessage(message)
                .setPositiveButton(R.string.lens_delete, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        ApertureRevealHelper.prepareDialog(lensDialog);
        lensDialog.show();
        ApertureRevealHelper.revealShownDialog(lensDialog, lensMenuAnchor());

        lensDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v ->
                ApertureRevealHelper.dismissWithReveal(lensDialog, lensMenuAnchor(), () ->
                        LensWorkspace.deleteLens(lens.getId(), lenses -> {
                            // FISH-008 Phase 3: no cascade exists for prefs either - drop the
                            // deleted lens's own curvature/Smart Focus keys explicitly.
                            if (utilSettings != null) {
                                utilSettings.deleteLensSettings(lens.getId());
                            }
                            currentLenses = lenses;
                            lensPagerAdapter.submitLenses(lenses);
                            updateLensNavigationChrome();
                            // FISH-008 Phase 2 r2: if we just deleted the active lens, clamp the
                            // pager and switch RAppsSingleton to the newly active lens so the
                            // deleted lens's layout is not retained in memory.
                            if (!lenses.isEmpty()) {
                                int targetPos = Math.min(lensPager.getCurrentItem(), lenses.size() - 1);
                                lensPager.setCurrentItem(targetPos, false);
                                LensWorkspace targetLens = lenses.get(targetPos);
                                if (utilSettings != null) {
                                    utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, targetLens.getId());
                                }
                                Object app = getApplication();
                                if (app instanceof RApplication) {
                                    ((RApplication) app).getAppRefreshPipeline().switchLens(targetLens.getId());
                                }
                            }
                            return Unit.INSTANCE;
                        })));
        lensDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v ->
                ApertureRevealHelper.dismissWithReveal(lensDialog, lensMenuAnchor(), null)
        );
    }

    private void setupSearch() {
        searchHistoryStore = new SearchHistoryStore(this);
        searchResultAdapter = new SearchResultAdapter(this::launchSearchResult);
        searchResults.setLayoutManager(new LinearLayoutManager(this));
        searchResults.setAdapter(searchResultAdapter);
        // UI-002: one shared divider between flat rows instead of a per-row card, matching
        // Pixel Launcher's search list and keeping layout nesting shallow (see LINT-008).
        searchResults.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        appSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSearchResults(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        // SearchBar->SearchView expand/collapse is a Material3 morph animation, not an instant
        // focus change; refresh results once the panel is fully SHOWN and ready for input.
        searchView.addTransitionListener((view, previousState, newState) -> {
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
                updateSearchBarVisibility();
                // Closing via system BACK goes through SearchView's own back orchestrator, which
                // makes the SearchBar VISIBLE again right after this callback returns (measured on
                // TECNO KJ7: GONE here, VISIBLE ~10 ms later; the arrow/hide() path does not). Re-apply
                // from settings on the next message so settings stay the single source of truth.
                searchBar.post(this::updateSearchBarVisibility);
            }
            // UI-009: status/nav bar color now matches the search scrim (same
            // ?attr/colorSurfaceContainerHigh tone) so the whole screen reads as one continuous
            // surface while search is open. Toggled on SHOWING/HIDING (not SHOWN/HIDDEN) to match
            // SearchView.isShowing() semantics, same reasoning as the old blur toggle it replaces.
            if (newState == SearchView.TransitionState.SHOWING) {
                lensPager.setVisibility(View.INVISIBLE);
                lensPageIndicator.setVisibility(View.GONE);
                if (tvLensName != null) tvLensName.setVisibility(View.GONE);
                if (rvHomeAppList != null) {
                    rvHomeAppList.setVisibility(View.GONE);
                }
                setSearchSystemBarsHarmonized(true);
            } else if (newState == SearchView.TransitionState.HIDING) {
                setSearchSystemBarsHarmonized(false);
            } else if (newState == SearchView.TransitionState.HIDDEN) {
                updateModeVisibility();
            }
        });
        appSearch.setOnEditorActionListener((view, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == KeyEvent.ACTION_UP
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || isEnterKey) {
                App first = searchResultAdapter.firstOrNull();
                if (first != null) {
                    launchSearchResult(first, appSearch);
                    return true;
                }
            }
            return false;
        });
        // No manual clear-button wiring: SearchView's built-in clear affordance already does this.
        Button clearHistory = findViewById(R.id.btClearSearchHistory);
        clearHistory.setOnClickListener(view -> {
            searchHistoryStore.clear();
            updateSearchResults(appSearch.getText());
            Toast.makeText(this, R.string.recent_apps_cleared, Toast.LENGTH_SHORT).show();
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
                startActivity(intent);
                hideSearch();
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void prefillOnTap(int tileViewId, String exampleQuery) {
        findViewById(tileViewId).setOnClickListener(v -> {
            appSearch.setText(exampleQuery);
            appSearch.setSelection(exampleQuery.length());
        });
    }

    /** UI-012: re-read on every resume (matches updateSearchBarVisibility's pattern) so toggling
     *  a quick action off/on in Settings, or changing the custom hint, applies immediately. */
    private void updateSearchCustomization() {
        UtilSettings settings = new UtilSettings(this);
        String customHint = settings.getString(UtilSettings.KEY_SEARCH_HINT_TEXT);
        String hint = (customHint == null || customHint.isEmpty())
                ? getString(R.string.search_apps_hint)
                : customHint;
        searchBar.setHint(hint);
        searchView.setHint(hint);

        findViewById(R.id.tileCalculator).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_CALCULATOR) ? View.VISIBLE : View.GONE);
        findViewById(R.id.tileUnitConvert).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_UNIT) ? View.VISIBLE : View.GONE);
        findViewById(R.id.tileTimer).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_TIMER) ? View.VISIBLE : View.GONE);
        findViewById(R.id.tileBattery).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_BATTERY) ? View.VISIBLE : View.GONE);
        findViewById(R.id.tileWifi).setVisibility(
                settings.getBoolean(UtilSettings.KEY_QUICK_ACTION_SETTINGS) ? View.VISIBLE : View.GONE);
    }

    private void updateSearchResults(CharSequence query) {
        if (!searchView.isShowing() && query.length() == 0) {
            return;
        }

        updateQuickAction(query);
        updateContactAction(query);

        ArrayList<App> apps = listApp == null ? new ArrayList<>() : listApp;
        java.util.List<App> engineResults = AppSearchEngine.search(apps, query, searchHistoryStore.recentKeys());
        boolean isEmptyQuery = AppSearchEngine.normalize(query).isEmpty();
        // UI-011: a blank query with no recent/favorite apps used to show a bare empty state.
        // Falling back to the full (alphabetical) app list instead means the panel is only ever
        // truly empty if the device has zero visible apps at all.
        boolean showAllAppsFallback = isEmptyQuery && engineResults.isEmpty() && !apps.isEmpty();
        java.util.List<App> results = showAllAppsFallback ? sortedByLabel(apps) : engineResults;
        boolean hasResults = !results.isEmpty();

        // UI-009 note: a TransitionManager.beginDelayedTransition crossfade was tried here for
        // the state switch, but onTextChanged can fire once per keystroke (adb `input text` and
        // some IMEs commit char-by-char) - overlapping beginDelayedTransition calls left stuck
        // GhostView fade-out overlays rendering on top of the new content, i.e. the exact
        // "overlapping UI" bug this whole revamp exists to remove. Plain visibility swaps instead.
        searchResultAdapter.submitList(results);
        int resultCount = results.size();
        recentHeader.setVisibility(isEmptyQuery && hasResults && !showAllAppsFallback ? View.VISIBLE : View.GONE);
        allAppsHeader.setVisibility(showAllAppsFallback ? View.VISIBLE : View.GONE);
        if (showAllAppsFallback) {
            allAppsHeader.setText(getResources().getQuantityString(
                    R.plurals.apps_count, resultCount, resultCount));
        }
        resultsSectionHeader.setVisibility(!isEmptyQuery && hasResults ? View.VISIBLE : View.GONE);
        if (!isEmptyQuery && hasResults) {
            resultsSectionHeader.setText(getResources().getQuantityString(
                    R.plurals.search_results_count, resultCount, resultCount));
        }
        llEmptyQuickActions.setVisibility(isEmptyQuery ? View.VISIBLE : View.GONE);
        searchResults.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        noSearchResults.setText(isEmptyQuery ? R.string.search_empty_state : R.string.no_apps_found);
        noSearchResults.setVisibility(hasResults ? View.GONE : View.VISIBLE);

        // SEARCH-006: only once every local result set (apps, shortcuts, quick actions) is empty -
        // never alongside real local results.
        boolean showWebFallback = !isEmptyQuery && !hasResults
                && quickActionRow.getVisibility() != View.VISIBLE
                && contactActionRow.getVisibility() != View.VISIBLE;
        webSearchFallback.setVisibility(showWebFallback ? View.VISIBLE : View.GONE);
        if (showWebFallback) {
            webSearchFallback.setText(getString(R.string.web_search_fallback, query.toString()));
        }
    }

    /** UI-011: all-apps fallback list, sorted alphabetically regardless of the lens grid's own
     *  sort setting (e.g. icon-color sort) - a text list should read A-Z. */
    private java.util.List<App> sortedByLabel(java.util.List<App> apps) {
        java.util.List<App> sorted = new ArrayList<>(apps);
        sorted.sort(java.util.Comparator.comparing(a -> AppSearchEngine.normalize(a.getLabel())));
        return sorted;
    }

    /**
     * UI-009: replaces the old real-blur approach (RenderEffect on lensViews), which recomputed
     * every frame during the SearchBar<->SearchView morph and caused visible transition jank.
     * Instead, paint the status/navigation bars the exact same tonal color as the search scrim
     * (res/color/search_view_scrim_background.xml's ?attr/colorSurfaceContainerHigh) so the whole
     * screen - bars included - reads as one continuous surface, with zero per-frame cost.
     */
    private void setSearchSystemBarsHarmonized(boolean showing) {
        Window window = getWindow();
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        if (showing) {
            int scrimColor = MaterialColors.getColor(this, R.attr.colorSurfaceContainerHigh, Color.BLACK);
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

    /**
     * SEARCH-002: show/hide/populate the calculator/unit-conversion/timer/battery%/settings
     * quick-action row above the normal app results, based on what the typed query resolves to.
     */
    private void updateQuickAction(CharSequence query) {
        QuickAction action = QuickActionEngine.resolve(this, query);
        boolean show = action != null;
        quickActionRow.setVisibility(show ? View.VISIBLE : View.GONE);
        quickActionDivider.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            quickActionRow.setOnClickListener(null);
            return;
        }

        if (action instanceof QuickAction.Info info) {
            tvQuickActionLabel.setText(info.getLabel());
            tvQuickActionValue.setText(info.getValue());
            quickActionRow.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(info.getLabel(), info.getValue()));
                Toast.makeText(this, info.getValue(), Toast.LENGTH_SHORT).show();
            });
        } else if (action instanceof QuickAction.Action quickAction) {
            tvQuickActionLabel.setText(quickAction.getLabel());
            // UI-016: was empty - the row looked like dead space with nothing on the right,
            // compounding why this row read as "not working" (it did; it was just too
            // understated to notice). A trailing chevron signals "this opens something" the
            // same way a tappable list row conventionally does.
            tvQuickActionValue.setText("›");
            quickActionRow.setOnClickListener(v -> {
                try {
                    startActivity(quickAction.getIntent());
                    hideSearch();
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (action instanceof QuickAction.FlashlightToggle toggle) {
            tvQuickActionLabel.setText(toggle.getLabel());
            tvQuickActionValue.setText(flashlightOn ? R.string.quick_action_flashlight_on : R.string.quick_action_flashlight_off);
            quickActionRow.setOnClickListener(v -> toggleFlashlight());
        } else if (action instanceof QuickAction.WifiSsidPermissionRequest permissionRequest) {
            tvQuickActionLabel.setText(permissionRequest.getLabel());
            tvQuickActionValue.setText(R.string.quick_action_tap_to_allow);
            quickActionRow.setOnClickListener(v -> requestWifiSsidPermission());
        } else if (action instanceof QuickAction.DndAccessRequest dndRequest) {
            tvQuickActionLabel.setText(dndRequest.getLabel());
            tvQuickActionValue.setText(dndRequest.getPreviouslyRequested()
                    ? R.string.quick_action_dnd_denied
                    : R.string.quick_action_tap_to_allow);
            quickActionRow.setOnClickListener(v -> openDndAccessSettings());
        } else if (action instanceof QuickAction.DndToggle dndToggle) {
            tvQuickActionLabel.setText(dndToggle.getLabel());
            tvQuickActionValue.setText(dndToggle.isOn() ? R.string.quick_action_flashlight_on : R.string.quick_action_flashlight_off);
            quickActionRow.setOnClickListener(v -> performDndToggle(dndToggle.isOn()));
        }
    }

    /**
     * SEARCH-004: CameraManager.setTorchMode() has no public getter for current state, so this
     * app tracks it itself - correct as long as nothing else in the system toggles the torch
     * behind this app's back, an accepted limitation matching how every flashlight app works.
     */
    private boolean flashlightOn = false;

    private void toggleFlashlight() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            new UtilSettings(this).save(UtilSettings.KEY_FLASHLIGHT_PERMISSION_REQUESTED, true);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);
            return;
        }
        performFlashlightToggle();
    }

    private void performFlashlightToggle() {
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String torchCameraId = null;
            for (String id : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) {
                    torchCameraId = id;
                    break;
                }
            }
            if (torchCameraId == null) {
                Toast.makeText(this, R.string.error_no_flashlight, Toast.LENGTH_SHORT).show();
                return;
            }
            flashlightOn = !flashlightOn;
            cameraManager.setTorchMode(torchCameraId, flashlightOn);
            updateQuickAction(appSearch.getText());
        } catch (CameraAccessException e) {
            Toast.makeText(this, R.string.error_no_flashlight, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestWifiSsidPermission() {
        new UtilSettings(this).save(UtilSettings.KEY_WIFI_SSID_PERMISSION_REQUESTED, true);
        // Android 12+ requires requesting ACCESS_COARSE_LOCATION alongside FINE (lint
        // CoarseFineLocation) - the user may grant only coarse, in which case
        // QuickActionEngine.resolveWifiSsid still won't read the SSID (needs FINE specifically)
        // and silently falls through, same as an outright denial.
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_CODE_WIFI_SSID
        );
    }

    /**
     * SEARCH-007: DND special access has no requestPermissions() callback - the only way to grant
     * it is this Settings screen, and the only way to detect the result is re-checking
     * isNotificationPolicyAccessGranted() next time the row renders, which onResume() does below.
     */
    private void openDndAccessSettings() {
        new UtilSettings(this).save(UtilSettings.KEY_DND_PERMISSION_REQUESTED, true);
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void performDndToggle(boolean currentlyOn) {
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!notificationManager.isNotificationPolicyAccessGranted()) {
            // Access was revoked externally between resolve() and this tap - fall back to the
            // request flow instead of crashing on setInterruptionFilter's SecurityException.
            openDndAccessSettings();
            return;
        }
        notificationManager.setInterruptionFilter(currentlyOn
                ? android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                : android.app.NotificationManager.INTERRUPTION_FILTER_NONE);
        updateQuickAction(appSearch.getText());
    }

    private void updateContactAction(CharSequence query) {
        boolean showPermissionRequest = ContactSearchEngine.shouldShowPermissionRequest(this, query);
        ContactSearchResult contact = showPermissionRequest
                ? null
                : ContactSearchEngine.firstContactForQuery(this, query);
        boolean show = showPermissionRequest || contact != null;
        contactActionRow.setVisibility(show ? View.VISIBLE : View.GONE);
        contactActionDivider.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            contactActionRow.setOnClickListener(null);
            btContactCall.setOnClickListener(null);
            btContactMessage.setOnClickListener(null);
            return;
        }

        if (showPermissionRequest) {
            tvContactResultName.setText(R.string.contact_search_permission_label);
            tvContactResultPhone.setText(R.string.contact_search_permission_rationale);
            contactActionRow.setContentDescription(getString(R.string.contact_search_permission_rationale));
            btContactCall.setText(R.string.contact_search_allow);
            btContactMessage.setVisibility(View.GONE);
            contactActionRow.setOnClickListener(v -> requestContactsPermission());
            btContactCall.setOnClickListener(v -> requestContactsPermission());
            return;
        }

        btContactCall.setText(R.string.contact_search_call);
        btContactMessage.setVisibility(View.VISIBLE);
        tvContactResultName.setText(contact.getDisplayName());
        tvContactResultPhone.setText(contact.getPhoneNumber());
        contactActionRow.setContentDescription(getString(R.string.search_open_contact, contact.getDisplayName()));
        contactActionRow.setOnClickListener(v -> startContactIntent(contact.dialIntent()));
        btContactCall.setOnClickListener(v -> startContactIntent(contact.dialIntent()));
        btContactMessage.setOnClickListener(v -> startContactIntent(contact.messageIntent()));
    }

    private void requestContactsPermission() {
        new UtilSettings(this).save(UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED, true);
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_CONTACTS},
                REQUEST_CODE_CONTACTS
        );
    }

    private void startContactIntent(Intent intent) {
        try {
            startActivity(intent);
            hideSearch();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                performFlashlightToggle();
            } else {
                updateQuickAction(appSearch.getText());
            }
        } else if (requestCode == REQUEST_CODE_WIFI_SSID) {
            updateQuickAction(appSearch.getText());
        } else if (requestCode == REQUEST_CODE_CONTACTS) {
            updateContactAction(appSearch.getText());
        }
    }

    private void launchSearchResult(App app, View source) {
        // UI-010 fix: hideSearch() used to run first - its clearText() call fires the
        // TextWatcher synchronously, wiping the result list to the empty/recent state a split
        // second before the target app's launch animation covers the screen, i.e. a visible
        // flash of the result the user just tapped disappearing. Launching first means whatever
        // the user sees next is the app's own reveal animation, not our own UI clearing itself.
        searchHistoryStore.recordLaunch(AppSearchEngine.componentKey(app));
        com.mckimquyen.util.UtilApp.launchComponent(
                this,
                app.getPackageName().toString(),
                app.getLabel().toString(),
                app.getName().toString(),
                source,
                new android.graphics.Rect(0, 0, source.getWidth(), source.getHeight())
        );
        hideSearch();
    }

    private void hideSearch() {
        // SearchView.hide() runs the reverse morph animation and handles keyboard/focus itself.
        searchView.clearText();
        searchView.hide();
    }

    /**
     * UI-021: restores the search overlay to its identity transform after a predictive-back
     * preview ends, whether the gesture completed (about to run hide()'s own animation from a
     * clean baseline) or was cancelled (snap back to fully visible, matching the system's own
     * predictive-back cancel convention).
     */
    private void resetPredictiveBackPreview() {
        searchView.setScaleX(PREDICTIVE_BACK_MAX_SCALE);
        searchView.setScaleY(PREDICTIVE_BACK_MAX_SCALE);
        searchView.setAlpha(PREDICTIVE_BACK_MAX_ALPHA);
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
            updateQuickAction(appSearch.getText());
        }
        if (RAppsSingleton.getInstance().getApps() != null && !RAppsSingleton.getInstance().getApps().isEmpty()) {
            assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps()));
        }
    }

    public void updateModeVisibility() {
        if (searchView != null && searchView.isShowing()) {
            if (lensPager != null) lensPager.setVisibility(View.INVISIBLE);
            updateLensNavigationChrome();
            if (rvHomeAppList != null) rvHomeAppList.setVisibility(View.GONE);
            return;
        }
        boolean isList = utilSettings != null && utilSettings.isListMode();
        if (isList) {
            if (lensPager != null) lensPager.setVisibility(View.GONE);
            updateLensNavigationChrome();
            if (rvHomeAppList != null) {
                rvHomeAppList.setVisibility(listApp != null && !listApp.isEmpty() ? View.VISIBLE : View.GONE);
            }
        } else {
            if (rvHomeAppList != null) rvHomeAppList.setVisibility(View.GONE);
            boolean hasApps = listApp != null && !listApp.isEmpty();
            if (lensPager != null) {
                lensPager.setVisibility(hasApps ? View.VISIBLE : View.INVISIBLE);
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
        if (!showSearchBar && searchView.isShowing()) {
            searchView.hide();
        }
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

    private void assignApps(ArrayList<App> lApp) {
        Logger.d("ActHome: assignApps called, input list size: " + (lApp != null ? lApp.size() : "null"));
        if (lApp == null || lApp.isEmpty()) {
            listApp = new ArrayList<>();
            progressBarHome.setVisibility(View.INVISIBLE);
            updateModeVisibility();
            updateLensNavigationChrome();
            if (searchView.isShowing() || appSearch.getText().length() > 0) {
                updateSearchResults(appSearch.getText());
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
            if (searchView.isShowing() || appSearch.getText().length() > 0) {
                updateSearchResults(appSearch.getText());
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
            lensViews.setApps(listApp);
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
        if (searchView.isShowing() || appSearch.getText().length() > 0) {
            updateSearchResults(appSearch.getText());
        }
    }

    @Override
    protected void onDestroy() {
        if (lensPager != null && lensPageChangeCallback != null) {
            lensPager.unregisterOnPageChangeCallback(lensPageChangeCallback);
        }
        // FISH-008 Phase 3: both hold a View (a dialog's decor, a popup's anchor) belonging to
        // this Activity. Dismiss a dialog still showing during a rotation - it would leak its
        // window - and drop both references so the destroyed Activity is not retained.
        if (lensDialog != null) {
            if (lensDialog.isShowing()) {
                lensDialog.dismiss();
            }
            lensDialog = null;
        }
        if (lensManagementMenu != null) {
            lensManagementMenu.dismiss();
            lensManagementMenu = null;
        }
        super.onDestroy();
    }
}
