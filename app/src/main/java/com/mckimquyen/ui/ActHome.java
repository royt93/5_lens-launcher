package com.mckimquyen.ui;

import static com.mckimquyen.ext.ActivityKt.rateAppInApp;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;

import com.mckimquyen.BuildConfig;
import com.mckimquyen.R;
import com.mckimquyen.app.RAppsSingleton;
import com.mckimquyen.model.App;
import com.mckimquyen.model.AppPersistent;
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
        // updateColor();
        PackageManager mPackageManager = getPackageManager();
        lensViews.setPackageManager(mPackageManager);
        lensViews.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        assignApps(Objects.requireNonNull(Objects.requireNonNull(RAppsSingleton.getInstance()).getApps()));

        // Observe app events using LiveData
        AppEventManager.INSTANCE.getAppsLoaded().observe(this,
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
                // Do nothing - back button disabled for home screen
            }
        });

        rateAppInApp(this, BuildConfig.DEBUG);
    }

    private void setupViews() {
        lensViews = findViewById(R.id.lensViews);
        progressBarHome = findViewById(R.id.progressBarHome);
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
                    || app1.isVisible() != app2.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private void assignApps(ArrayList<App> lApp) {
        Logger.d("ActHome: assignApps called, input list size: " + (lApp != null ? lApp.size() : "null"));
        if (lApp == null || lApp.isEmpty()) {
            return;
        }

        // Filter out hidden apps first to get the target visible list
        ArrayList<App> visibleApps = new ArrayList<>();
        for (App app : lApp) {
            if (app.isVisible()) {
                visibleApps.add(app);
            }
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
