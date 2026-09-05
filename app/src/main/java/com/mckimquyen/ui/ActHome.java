package com.mckimquyen.ui;

import static com.mckimquyen.ext.ActivityKt.rateAppInApp;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mckimquyen.BuildConfig;
import com.mckimquyen.R;
import com.mckimquyen.app.RAppsSingleton;
import com.mckimquyen.model.App;
import com.mckimquyen.model.AppPersistent;
import com.mckimquyen.search.AppSearchEngine;
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

    LensView lensViews;
    MaterialProgressBar progressBarHome;
    private ArrayList<App> listApp;
    private EditText appSearch;
    private ImageButton clearAppSearch;
    private View searchResultsCard;
    private View recentHeader;
    private TextView noSearchResults;
    private RecyclerView searchResults;
    private SearchResultAdapter searchResultAdapter;
    private SearchHistoryStore searchHistoryStore;

    private void updateColor() {
        var mUtilSettings = new UtilSettings(this);
        var kBackground = mUtilSettings.getString(UtilSettings.KEY_BACKGROUND);
        Log.d("roy93~", "kBackground " + kBackground);
        if (Objects.equals(kBackground, "Color")) {
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

        // Disable back button for launcher home screen
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchResultsCard.getVisibility() == View.VISIBLE) {
                    if (appSearch.getText().length() > 0) {
                        appSearch.setText("");
                    } else {
                        hideSearch();
                    }
                }
            }
        });

        rateAppInApp(this, BuildConfig.DEBUG);
    }

    private void setupViews() {
        lensViews = findViewById(R.id.lensViews);
        progressBarHome = findViewById(R.id.progressBarHome);
        appSearch = findViewById(R.id.etAppSearch);
        clearAppSearch = findViewById(R.id.btClearAppSearch);
        searchResultsCard = findViewById(R.id.searchResultsCard);
        recentHeader = findViewById(R.id.recentHeader);
        noSearchResults = findViewById(R.id.tvNoSearchResults);
        searchResults = findViewById(R.id.rvSearchResults);

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

        appSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearAppSearch.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                updateSearchResults(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        appSearch.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) updateSearchResults(appSearch.getText());
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
        clearAppSearch.setOnClickListener(view -> appSearch.setText(""));
        Button clearHistory = findViewById(R.id.btClearSearchHistory);
        clearHistory.setOnClickListener(view -> {
            searchHistoryStore.clear();
            updateSearchResults(appSearch.getText());
            Toast.makeText(this, R.string.recent_apps_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateSearchResults(CharSequence query) {
        if (!appSearch.hasFocus() && query.length() == 0) {
            searchResultsCard.setVisibility(View.GONE);
            return;
        }

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
        searchResultsCard.setVisibility(View.VISIBLE);
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
        appSearch.setText("");
        appSearch.clearFocus();
        searchResultsCard.setVisibility(View.GONE);
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(appSearch.getWindowToken(), 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("roy93~", "onResume");
        updateColor();
        setupTransparentSystemBarsForLollipop();
        if (RAppsSingleton.getInstance().getApps() != null && !RAppsSingleton.getInstance().getApps().isEmpty()) {
            assignApps(Objects.requireNonNull(RAppsSingleton.getInstance().getApps()));
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
            if (appSearch.hasFocus() || appSearch.getText().length() > 0) {
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
            if (appSearch.hasFocus() || appSearch.getText().length() > 0) {
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
        if (appSearch.hasFocus() || appSearch.getText().length() > 0) {
            updateSearchResults(appSearch.getText());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
