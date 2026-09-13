package com.mckimquyen.ui;

import static com.mckimquyen.ext.ActivityKt.rateAppInApp;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
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

import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import com.mckimquyen.BuildConfig;
import com.mckimquyen.R;
import com.mckimquyen.app.RAppsSingleton;
import com.mckimquyen.enums.BackgroundMode;
import com.mckimquyen.model.App;
import com.mckimquyen.model.AppPersistent;
import com.mckimquyen.search.AppSearchEngine;
import com.mckimquyen.search.ContactSearchEngine;
import com.mckimquyen.search.ContactSearchResult;
import com.mckimquyen.search.QuickAction;
import com.mckimquyen.search.QuickActionEngine;
import com.mckimquyen.search.SearchHistoryStore;
import com.mckimquyen.search.SearchResultAdapter;
import com.mckimquyen.util.Logger;
import com.mckimquyen.util.UIUtils;
import com.mckimquyen.services.AppEventManager;
import com.mckimquyen.util.UtilSettings;
import com.mckimquyen.views.LensView;

import java.util.ArrayList;
import java.util.Objects;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

//2023.03.19 tried to convert kotlin but failed
public class ActHome extends ActBase {

    // SEARCH-004: request codes for the two permission-gated quick actions.
    private static final int REQUEST_CODE_CAMERA = 1001;
    private static final int REQUEST_CODE_WIFI_SSID = 1002;
    private static final int REQUEST_CODE_CONTACTS = 1003;

    LensView lensViews;
    MaterialProgressBar progressBarHome;
    private ArrayList<App> listApp;
    private SearchBar searchBar;
    private SearchView searchView;
    private EditText appSearch;
    private View recentHeader;
    private View allAppsHeader;
    private View resultsSectionHeader;
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
        // UI-014/UI-015: rootLayout itself is no longer inset-padded at all - it and its direct
        // children (lensViews, searchCoordinator/searchView) all now extend genuinely
        // edge-to-edge on every side, so searchView's scrim reaches the true top edge (mirrors
        // UI-013's bottom fix). lensViews and searchBar each need their own top clearance
        // restored though - as a MARGIN, not padding. UI-014 first tried setupEdgeToEdge2
        // (setPadding) directly on lensViews, but LensView is a leaf custom View that never
        // reads its own getPaddingTop() in onDraw/onTouchEvent - padding on a leaf view changes
        // nothing about its actual laid-out bounds, so the fisheye grid kept drawing from y=0,
        // ending up hidden (and unclickable - touches went to searchCoordinator instead) behind
        // the search pill. A margin, unlike self-padding, genuinely shrinks/shifts a match_parent
        // child's laid-out bounds (confirmed live on Pixel 7 Pro: grid icons no longer hide under
        // or lose touch to the pill). searchView (the expanded search panel) still gets neither -
        // that's the intended UI-014 fix, unaffected by this correction.
        applyStatusBarInsetAsTopMargin(findViewById(R.id.lensViews));
        applyStatusBarInsetAsTopMargin(findViewById(R.id.searchBar));
        setupViews();
        setupSearch();
        // updateColor();
        PackageManager mPackageManager = getPackageManager();
        lensViews.setPackageManager(mPackageManager);
        lensViews.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
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

        // UI-010 fix: this used to be an unconditional no-op, relying on SearchView's own
        // internal MaterialBackOrchestrator to intercept back first and collapse the panel.
        // Confirmed live on TECNO KJ7 that back does NOT close the search overlay - explicitly
        // checking isShowing() here instead makes it work regardless of whatever SearchView is or
        // isn't doing internally. Falls through to no-op (blocking launcher exit) otherwise, since
        // this is the HOME activity and back should never finish it.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchView.isShowing()) {
                    hideSearch();
                }
            }
        });

        rateAppInApp(this, BuildConfig.DEBUG);
    }

    /**
     * UI-014: adds the status bar inset to view's existing static topMargin (its XML
     * android:layout_marginTop, e.g. searchBar's 12dp) instead of overwriting it via padding -
     * keeps the pill's own shape/content undisturbed while still positioning it below the status
     * bar now that rootLayout no longer supplies that clearance itself.
     */
    private void applyStatusBarInsetAsTopMargin(View view) {
        int baseTopMargin = ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusBarInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.topMargin = baseTopMargin + statusBarInset;
            v.setLayoutParams(params);
            return insets;
        });
    }

    private void setupViews() {
        lensViews = findViewById(R.id.lensViews);
        progressBarHome = findViewById(R.id.progressBarHome);
        searchBar = findViewById(R.id.searchBar);
        searchView = findViewById(R.id.searchView);
        searchView.setupWithSearchBar(searchBar);
        appSearch = searchView.getEditText();
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
                searchBar.setVisibility(View.VISIBLE);
            }
            // UI-009: status/nav bar color now matches the search scrim (same
            // ?attr/colorSurfaceContainerHigh tone) so the whole screen reads as one continuous
            // surface while search is open. Toggled on SHOWING/HIDING (not SHOWN/HIDDEN) to match
            // SearchView.isShowing() semantics, same reasoning as the old blur toggle it replaces.
            if (newState == SearchView.TransitionState.SHOWING) {
                setSearchSystemBarsHarmonized(true);
            } else if (newState == SearchView.TransitionState.HIDING) {
                setSearchSystemBarsHarmonized(false);
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
        recentHeader.setVisibility(isEmptyQuery && hasResults && !showAllAppsFallback ? View.VISIBLE : View.GONE);
        allAppsHeader.setVisibility(showAllAppsFallback ? View.VISIBLE : View.GONE);
        resultsSectionHeader.setVisibility(!isEmptyQuery && hasResults ? View.VISIBLE : View.GONE);
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

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("roy93~", "onResume");
        updateColor();
        updateSearchBarVisibility();
        updateSearchCustomization();
        setupTransparentSystemBarsForLollipop();
        // UI-009 fix: setupTransparentSystemBarsForLollipop() unconditionally forces transparent
        // bars - if the task is paused/resumed (e.g. Home button, or launching an app from a
        // search result) while SearchView is still showing, that clobbers the harmonized scrim
        // color set by setSearchSystemBarsHarmonized(), leaving the wallpaper showing through
        // behind the search panel instead of the matching tonal color.
        if (searchView.isShowing()) {
            setSearchSystemBarsHarmonized(true);
        }
        if (RAppsSingleton.getInstance().getApps() != null && !RAppsSingleton.getInstance().getApps().isEmpty()) {
            assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps()));
        }
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
        boolean showSearchBar = new UtilSettings(this).getBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR);
        searchBar.setVisibility(showSearchBar ? View.VISIBLE : View.GONE);
        if (!showSearchBar && searchView.isShowing()) {
            searchView.hide();
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
        lensViews.invalidate();
    }

    private boolean isSameAppList(java.util.List<App> list1, java.util.List<App> list2) {
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
                    || app1.getOpenCount() != app2.getOpenCount()) {
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
            lensViews.setVisibility(View.INVISIBLE);
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
            lensViews.setVisibility(View.INVISIBLE);
            if (searchView.isShowing() || appSearch.getText().length() > 0) {
                updateSearchResults(appSearch.getText());
            }
            return;
        }

        // Check if the new visible list is identical to the currently displayed listApp
        if (listApp != null && isSameAppList(listApp, visibleApps)) {
            Logger.d("ActHome: assignApps skipped - identical list of visible apps");
            return;
        }

        progressBarHome.setVisibility(View.INVISIBLE);
        lensViews.setVisibility(View.VISIBLE);
        listApp = visibleApps;
        Logger.d("ActHome: Setting " + listApp.size() + " apps to lensViews");
        lensViews.setApps(listApp);
        if (searchView.isShowing() || appSearch.getText().length() > 0) {
            updateSearchResults(appSearch.getText());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
