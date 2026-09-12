package com.mckimquyen.ui;

import static com.mckimquyen.ext.ActivityKt.rateAppInApp;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import com.mckimquyen.BuildConfig;
import com.mckimquyen.R;
import com.mckimquyen.app.RAppsSingleton;
import com.mckimquyen.enums.BackgroundMode;
import com.mckimquyen.model.App;
import com.mckimquyen.model.AppPersistent;
import com.mckimquyen.search.AppSearchEngine;
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

    // UI-002 follow-up: frosted-glass blur radius (dp-independent, RenderEffect takes raw px)
    // behind the search overlay on Android 12+; pre-12 devices fall back to a more opaque flat
    // scrim instead (res/color-v31/search_view_scrim_background.xml vs the default).
    private static final float SEARCH_BLUR_RADIUS_PX = 25f;

    LensView lensViews;
    MaterialProgressBar progressBarHome;
    private ArrayList<App> listApp;
    private SearchBar searchBar;
    private SearchView searchView;
    private EditText appSearch;
    private View recentHeader;
    private TextView noSearchResults;
    private RecyclerView searchResults;
    private View quickActionRow;
    private View quickActionDivider;
    private TextView tvQuickActionLabel;
    private TextView tvQuickActionValue;
    // Package-visible (not private) so AppSearchWidgetTest can assert on it directly -
    // View.getRenderEffect() has no Kotlin-friendly getter to read the value back through.
    boolean lensBlurActive = false;
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
        UIUtils.INSTANCE.setupEdgeToEdge2(findViewById(R.id.rootLayout), true, true);
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

        // Disable back button for launcher home screen. When SearchView is showing, its own
        // MaterialBackHandler intercepts back first (collapsing the panel via a higher-priority
        // dynamically-registered callback) - this callback only fires once it's fully hidden,
        // and its no-op body is what stops the launcher from finishing via back press.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Intentionally no-op.
            }
        });

        rateAppInApp(this, BuildConfig.DEBUG);
    }

    private void setupViews() {
        lensViews = findViewById(R.id.lensViews);
        progressBarHome = findViewById(R.id.progressBarHome);
        searchBar = findViewById(R.id.searchBar);
        searchView = findViewById(R.id.searchView);
        searchView.setupWithSearchBar(searchBar);
        appSearch = searchView.getEditText();
        // UI-002: the 50%-opacity scrim behind the search panel is set declaratively via
        // app:backgroundTint="@color/search_view_scrim_background" in act_home.xml -
        // com.google.android.material.search.SearchView reads its panel background only from
        // that XML attribute at inflate time and exposes no public runtime setter for it. The
        // result list itself sits in an opaque MaterialCardView so readability never depends on
        // what's behind the scrim.
        recentHeader = findViewById(R.id.recentHeader);
        noSearchResults = findViewById(R.id.tvNoSearchResults);
        searchResults = findViewById(R.id.rvSearchResults);
        quickActionRow = findViewById(R.id.quickActionRow);
        quickActionDivider = findViewById(R.id.quickActionDivider);
        tvQuickActionLabel = findViewById(R.id.tvQuickActionLabel);
        tvQuickActionValue = findViewById(R.id.tvQuickActionValue);

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
            // UI-002 follow-up: real frosted-glass blur behind the search panel (Android 12+),
            // instead of relying on the flat scrim alone to hide detail - owner reported the
            // lens grid/icons showing plainly through the scrim looked visually busy.
            if (newState == SearchView.TransitionState.SHOWING) {
                setLensBlurred(true);
            } else if (newState == SearchView.TransitionState.HIDING) {
                // Symmetric with SHOWING above: SearchView.isShowing() already flips to false as
                // soon as HIDING starts (not once HIDDEN completes), so clearing here - not on
                // HIDDEN - keeps setLensBlurred() in step with what callers observe via
                // isShowing() and avoids a race where blur stays applied after hide() returns.
                setLensBlurred(false);
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
    }

    private void updateSearchResults(CharSequence query) {
        if (!searchView.isShowing() && query.length() == 0) {
            return;
        }

        updateQuickAction(query);

        ArrayList<App> apps = listApp == null ? new ArrayList<>() : listApp;
        java.util.List<App> results = AppSearchEngine.search(apps, query, searchHistoryStore.recentKeys());
        boolean isEmptyQuery = AppSearchEngine.normalize(query).isEmpty();
        searchResultAdapter.submitList(results);
        int resultHeightDp = Math.min(results.size() * 64, 384);
        searchResults.getLayoutParams().height = Math.round(
                resultHeightDp * getResources().getDisplayMetrics().density
        );
        searchResults.requestLayout();
        recentHeader.setVisibility(isEmptyQuery && !results.isEmpty() ? View.VISIBLE : View.GONE);
        searchResults.setVisibility(results.isEmpty() ? View.GONE : View.VISIBLE);
        noSearchResults.setText(isEmptyQuery ? R.string.search_empty_state : R.string.no_apps_found);
        noSearchResults.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * UI-002 follow-up: blur the lens grid behind the search overlay on Android 12+
     * ({@link RenderEffect} requires API 31). Pre-31 devices have no equivalent API - they rely
     * solely on the more opaque flat scrim ({@code res/color/search_view_scrim_background.xml})
     * for the same "hide distracting detail" job.
     */
    private void setLensBlurred(boolean blurred) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        lensBlurActive = blurred;
        lensViews.setRenderEffect(
                blurred
                        ? RenderEffect.createBlurEffect(SEARCH_BLUR_RADIUS_PX, SEARCH_BLUR_RADIUS_PX, Shader.TileMode.CLAMP)
                        : null
        );
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
            tvQuickActionValue.setText("");
            quickActionRow.setOnClickListener(v -> {
                try {
                    startActivity(quickAction.getIntent());
                    hideSearch();
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void launchSearchResult(App app, View source) {
        searchHistoryStore.recordLaunch(AppSearchEngine.componentKey(app));
        hideSearch();
        com.mckimquyen.util.UtilApp.launchComponent(
                this,
                app.getPackageName().toString(),
                app.getLabel().toString(),
                app.getName().toString(),
                source,
                new android.graphics.Rect(0, 0, source.getWidth(), source.getHeight())
        );
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
        setupTransparentSystemBarsForLollipop();
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
