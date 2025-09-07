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

import com.mckimquyen.BuildConfig;
import com.mckimquyen.R;
import com.mckimquyen.app.RAppsSingleton;
import com.mckimquyen.model.App;
import com.mckimquyen.model.AppPersistent;
import com.mckimquyen.sdkadbmob.UIUtils;
import com.mckimquyen.services.BackgroundChangedObservable;
import com.mckimquyen.services.LoadedObservable;
import com.mckimquyen.services.LockChangedObservable;
import com.mckimquyen.services.NightModeObservable;
import com.mckimquyen.services.VisibilityChangedObservable;
import com.mckimquyen.util.UtilSettings;
import com.mckimquyen.views.LensView;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

//2023.03.19 tried to convert kotlin but failed
public class ActHome extends ActBase implements Observer {

    LensView lensViews;
    MaterialProgressBar progressBarHome;
    private ArrayList<App> listApp;
    private ArrayList<Bitmap> listAppIcon;

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
//        updateColor();
        PackageManager mPackageManager = getPackageManager();
        lensViews.setPackageManager(mPackageManager);
        lensViews.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        assignApps(Objects.requireNonNull(Objects.requireNonNull(RAppsSingleton.getInstance()).getApps()), RAppsSingleton.getInstance().getAppIcons());
        LoadedObservable.getInstance().addObserver(this);
        VisibilityChangedObservable.getInstance().addObserver(this);
        BackgroundChangedObservable.getInstance().addObserver(this);
        NightModeObservable.getInstance().addObserver(this);
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
    }

    private void setupTransparentSystemBarsForLollipop() {
        Window window = getWindow();
        window.getAttributes().systemUiVisibility |= (View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
    }

    private void setBackground() {
        lensViews.invalidate();
    }

    private void assignApps(ArrayList<App> lApp, ArrayList<Bitmap> lAppIcon) {
        if (lApp.isEmpty() || lAppIcon.isEmpty()) {
            return;
        }
        progressBarHome.setVisibility(View.INVISIBLE);
        lensViews.setVisibility(View.VISIBLE);
        listApp = lApp;
        listAppIcon = lAppIcon;
        removeHiddenApps();
        lensViews.setApps(listApp, listAppIcon);
    }

    private void removeHiddenApps() {
        for (int i = 0; i < listApp.size(); i++) {
            if (!AppPersistent.getAppVisibility(Objects.requireNonNull(listApp.get(i).getPackageName()).toString(), Objects.requireNonNull(listApp.get(i).getName()).toString())) {
                listApp.remove(i);
                listAppIcon.remove(i);
                i--;
            }
        }
    }

    @Override
    public void onBackPressed() {
        //do nothing
    }

    @Override
    protected void onDestroy() {
        LoadedObservable.getInstance().deleteObserver(this);
        super.onDestroy();
    }

    @Override
    public void update(Observable observable, Object data) {
        if (observable instanceof LoadedObservable
                || observable instanceof VisibilityChangedObservable
                || observable instanceof LockChangedObservable
        ) {
            assignApps(Objects.requireNonNull(
                            Objects.requireNonNull(RAppsSingleton.getInstance()).getApps()),
                    RAppsSingleton.getInstance().getAppIcons());
        } else if (observable instanceof BackgroundChangedObservable) {
            setBackground();
        } else if (observable instanceof NightModeObservable) {
            updateNightMode();
        }
    }
}
