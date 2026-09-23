package com.mckimquyen.ui;

import static com.mckimquyen.ext.ActivityKt.likeFacebookFanpage;
import static com.mckimquyen.ext.ActivityKt.moreApp;
import static com.mckimquyen.ext.ActivityKt.rateApp;
import static com.mckimquyen.ext.ActivityKt.shareApp;
import static com.mckimquyen.ext.ContextKt.openUrlInBrowser;
import static com.mckimquyen.ext.ContextKt.showDialog2;
import static com.mckimquyen.util.CKt.URL_POLICY_NOTION;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.mckimquyen.util.Logger;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AlertDialog;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.mckimquyen.BuildConfig;
import com.mckimquyen.R;
import com.mckimquyen.adt.FragmentPagerAdapter;
import com.mckimquyen.app.RAppsSingleton;
import com.mckimquyen.enums.BackgroundMode;
import com.mckimquyen.enums.SortType;
import com.mckimquyen.itf.AppsInterface;
import com.mckimquyen.itf.LensInterface;
import com.mckimquyen.itf.SettingsInterface;
import com.mckimquyen.model.App;
import com.mckimquyen.util.UIUtils;
import com.mckimquyen.services.AppEventManager;
import com.mckimquyen.services.BroadcastReceivers;
import com.mckimquyen.util.UtilIconPackManager;
import com.mckimquyen.util.UtilLauncher;
import com.mckimquyen.util.UtilNightModeUtil;
import com.mckimquyen.util.UtilSettings;

import com.mckimquyen.ui.settings.SettingsAdVipDelegate;
import com.mckimquyen.ui.settings.SettingsDialogCoordinator;
import com.mckimquyen.ui.settings.SettingsIntentHelper;
import com.mckimquyen.ui.settings.SettingsMenuAction;
import com.mckimquyen.ui.settings.SettingsMenuDispatcher;
import com.mckimquyen.ui.settings.SettingsMenuHost;
import com.mckimquyen.ui.settings.SettingsMenuResolver;
import com.roy.sdkadbmob.AdManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import kotlin.Unit;

// ARCH-001: Decomposed complex controller with dedicated collaborators
public class ActSettings extends ActBase implements SettingsMenuHost {

    private static final String TAG_COLOR_BACKGROUND = "BackgroundColor";
    private static final String TAG_COLOR_HIGHLIGHT = "HighlightColor";
    Toolbar toolbar;
    TabLayout tabs;
    ViewPager2 viewpager;
    FloatingActionButton fabSort;
    LinearLayout flAdOpenApp;
    private View adView = null;

    private final SettingsDialogCoordinator dialogCoordinator = new SettingsDialogCoordinator();
    private final SettingsAdVipDelegate adVipDelegate = new SettingsAdVipDelegate();
    private SettingsMenuDispatcher menuDispatcher;

    private ArrayList<App> listApp;
    private AlertDialog dlgSortType;
    private AlertDialog dlgIconPack;
    private AlertDialog dlgNightMode;
    private AlertDialog dlgBackground;
    private AlertDialog dlgHighlightColor;
    private AlertDialog dlgTerms;
    private LensInterface lensInterface;

    // Flag to prevent showing Terms dialog multiple times in same session
    private boolean hasShownTermsDialog = false;

    public void setLensInterface(LensInterface lensInterface) {
        this.lensInterface = lensInterface;
    }

    private AppsInterface appsInterface;

    public void setAppsInterface(AppsInterface appsInterface) {
        this.appsInterface = appsInterface;
    }

    private SettingsInterface settingsInterface;

    public void setSettingsInterface(SettingsInterface settingsInterface) {
        this.settingsInterface = settingsInterface;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.INSTANCE.setupEdgeToEdge1(getWindow());
        setContentView(R.layout.act_settings);
        UIUtils.INSTANCE.setupEdgeToEdge2(findViewById(R.id.rootLayout), true, true);
        AdManager.INSTANCE.setCurrentActivity(this);
        menuDispatcher = new SettingsMenuDispatcher(this);
        setupViews();

        // Observe app events using LiveData
        AppEventManager.INSTANCE.getAppsLoaded().observe(this, data -> {
            listApp = RAppsSingleton.getInstance().getApps();
            if (appsInterface != null) {
                appsInterface.onAppsUpdated(listApp);
            }
        });

        AppEventManager.INSTANCE.getNightModeChanged().observe(this, data -> updateNightMode());

        checkShowAd();
    }

    private void setupViews() {
        flAdOpenApp = findViewById(R.id.flAdOpenApp);
        toolbar = findViewById(R.id.toolbar);
        tabs = findViewById(R.id.tabs);
        viewpager = findViewById(R.id.viewpager);
        fabSort = findViewById(R.id.fabSort);

        fabSort.setOnClickListener(view -> showSortTypeDialog());
        findViewById(R.id.btStart).setOnClickListener(view -> launchApps());

        fabSort.hide();
        setSupportActionBar(toolbar);
        FragmentPagerAdapter mPagerAdapter = new FragmentPagerAdapter(ActSettings.this);
        viewpager.setOffscreenPageLimit(2);
        viewpager.setAdapter(mPagerAdapter);

        // Setup TabLayout with TabLayoutMediator (ViewPager2 requirement)
        new TabLayoutMediator(tabs, viewpager, (tab, position) -> tab.setText(mPagerAdapter.getPageTitle(position)))
                .attach();

        viewpager.registerOnPageChangeCallback(new PageChangeCallback(fabSort));
        listApp = Objects.requireNonNull(RAppsSingleton.getInstance()).getApps();

        android.view.View chipVipBadge = findViewById(R.id.chipVipBadge);
        if (chipVipBadge != null) {
            chipVipBadge.setOnClickListener(v -> navigateToVipTab());
        }

        // BUG-1: loadInterstitial moved to checkShowAd() callback — after consent resolved
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);

        // Tint all menu icons with colorOnPrimary
        int tintColor = androidx.core.content.ContextCompat.getColor(this, R.color.colorOnPrimary);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                icon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
            }
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean isVip = AdManager.INSTANCE.isVIPMember() || AdManager.INSTANCE.isVipByKeyActive();
        adVipDelegate.onResume(this, findViewById(R.id.bannerContainer), findViewById(R.id.tvLabelAd), isVip);
        adView = adVipDelegate.getBannerView();
        bindToolbarVipBadge();

        // 1. Show Terms and Privacy Policy dialog only once per session
        if (utilSettings != null && !hasShownTermsDialog) {
            boolean hasRead = utilSettings.getBoolean(UtilSettings.KEY_READ_POLICY);
            if (!hasRead) {
                hasShownTermsDialog = true;
                dialogCoordinator.showTermsDialog(
                        this,
                        utilSettings,
                        () -> {
                            openUrlInBrowser(this, URL_POLICY_NOTION, getString(R.string.terms_and_privacy_policy), false);
                            return Unit.INSTANCE;
                        },
                        () -> {
                            if (!isFinishing() && !isDestroyed()) {
                                checkShowLanguagePicker();
                            }
                            return Unit.INSTANCE;
                        }
                );
                dlgTerms = dialogCoordinator.getDlgTerms();
                return;
            }
        }

        // 2. Show language selection dialog if not selected yet on first launch
        checkShowLanguagePicker();
    }

    private void checkShowLanguagePicker() {
        if (isFinishing() || isDestroyed()) return;
        if (!com.mckimquyen.util.LocaleHelper.INSTANCE.isLanguageSelected(this)) {
            if (getSupportFragmentManager().findFragmentByTag("LanguageFirstLaunchBottomSheet") == null) {
                LanguageBottomSheetDialogFragment dialog = new LanguageBottomSheetDialogFragment();
                dialog.setOnLanguageSelectedListener(new LanguageBottomSheetDialogFragment.OnLanguageSelectedListener() {
                    @Override
                    public void onLanguageSelected(@NonNull String languageCode) {
                        recreate();
                    }
                });
                dialog.show(getSupportFragmentManager(), "LanguageFirstLaunchBottomSheet");
            }
        }
    }

    public void navigateToVipTab() {
        startActivity(SettingsIntentHelper.createVipIntent(this));
    }

    private void bindToolbarVipBadge() {
        View chipVipBadge = findViewById(R.id.chipVipBadge);
        if (chipVipBadge != null) {
            boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            boolean isVip = AdManager.INSTANCE.isVIPMember();
            adVipDelegate.bindVipBadge(chipVipBadge, isVip, isNight, () -> {
                navigateToVipTab();
                return Unit.INSTANCE;
            });
        }
    }

    @Override
    protected void onPause() {
        adVipDelegate.onPause();
        dismissAllDialogs();
        super.onPause();
    }

    @Override
    public void launchApps() {
        boolean isDefaultLauncher = UtilLauncher.isDefaultLauncher(getApplication());
        Logger.d("ActSettings", "launchApps - isDefaultLauncher: " + isDefaultLauncher);

        if (isDefaultLauncher) {
            AdManager.INSTANCE.showInterstitial(this, aBoolean -> {
                Intent homeIntent = SettingsIntentHelper.createHomeLauncherIntent();
                startActivity(homeIntent);
                finish();
                return Unit.INSTANCE;
            });
        } else {
            showHomeLauncherChooser();
        }
        overridePendingTransition(R.anim.a_fade_in, R.anim.a_fade_out);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SettingsMenuAction action = SettingsMenuResolver.resolve(item.getItemId(), viewpager.getCurrentItem());
        if (menuDispatcher.dispatch(action)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void openAbout() {
        AdManager.INSTANCE.showInterstitial(this, aBoolean -> {
            Intent aboutIntent = SettingsIntentHelper.createAboutIntent(ActSettings.this);
            startActivity(aboutIntent);
            overridePendingTransition(R.anim.a_slide_in_left, R.anim.a_slide_out_right);
            return Unit.INSTANCE;
        });
    }

    @Override
    public void resetTabDefaults(int currentTab) {
        switch (currentTab) {
            case 0:
                if (lensInterface != null) {
                    lensInterface.onDefaultsReset();
                }
                break;
            case 1:
                if (appsInterface != null) {
                    appsInterface.onDefaultsReset();
                }
                break;
            case 2:
                if (settingsInterface != null) {
                    settingsInterface.onDefaultsReset();
                }
                break;
        }
        Snackbar.make(toolbar, getString(R.string.snackbar_reset_successful), Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void rateApp() {
        com.mckimquyen.ext.ActivityKt.rateApp(this, this.getPackageName());
    }

    @Override
    public void openMoreApps() {
        com.mckimquyen.ext.ActivityKt.moreApp(this, "SAIGON PHANTOM LABS");
    }

    @Override
    public void shareApp() {
        com.mckimquyen.ext.ActivityKt.shareApp(this);
    }

    @Override
    public void openFacebookFanPage() {
        com.mckimquyen.ext.ActivityKt.likeFacebookFanpage(this);
    }

    @Override
    public void openWebUrl(@NonNull String url, int titleResId, boolean isExternal) {
        openUrlInBrowser(this, url, getString(titleResId), isExternal);
    }

    @Override
    public void sendFeedback() {
        sendEmail();
    }

    private void sendUpdateAppsBroadcast() {
        Intent refreshAppsIntent = new Intent(ActSettings.this, BroadcastReceivers.AppsUpdatedReceiver.class);
        sendBroadcast(refreshAppsIntent);
    }

    private void sendEditAppsBroadcast() {
        Intent editAppsIntent = new Intent(ActSettings.this, BroadcastReceivers.AppsEditedReceiver.class);
        sendBroadcast(editAppsIntent);
    }

    private void sendBackgroundChangedBroadcast() {
        Intent changeBackgroundIntent = new Intent(ActSettings.this,
                BroadcastReceivers.BackgroundChangedReceiver.class);
        sendBroadcast(changeBackgroundIntent);
    }

    public void sendNightModeBroadcast() {
        Intent nightModeIntent = new Intent(ActSettings.this, BroadcastReceivers.NightModeReceiver.class);
        sendBroadcast(nightModeIntent);
    }

    private void showSortTypeDialog() {
        if (utilSettings == null) return;
        dialogCoordinator.showSortTypeDialog(this, utilSettings, sortType -> {
            sendEditAppsBroadcast();
            return Unit.INSTANCE;
        });
        dlgSortType = dialogCoordinator.getDlgSortType();
    }

    public void showIconPackDialog() {
        if (utilSettings == null) return;
        dialogCoordinator.showIconPackDialog(this, utilSettings, packValue -> {
            if (settingsInterface != null) {
                settingsInterface.onValuesUpdated();
            }
            sendUpdateAppsBroadcast();
            return Unit.INSTANCE;
        });
        dlgIconPack = dialogCoordinator.getDlgIconPack();
    }

    public void showHomeLauncherChooser() {
        UtilLauncher.resetPreferredLauncherAndOpenChooser(getApplicationContext());
    }

    public void showNightModeChooser() {
        if (utilSettings == null) return;
        dialogCoordinator.showNightModeChooser(this, utilSettings, mode -> {
            sendNightModeBroadcast();
            if (settingsInterface != null) {
                settingsInterface.onValuesUpdated();
            }
            return Unit.INSTANCE;
        }, () -> {
            recreate();
            return Unit.INSTANCE;
        });
        dlgNightMode = dialogCoordinator.getDlgNightMode();
    }

    public void showBackgroundDialog() {
        if (utilSettings == null) return;
        dialogCoordinator.showBackgroundDialog(this, utilSettings, () -> {
            sendBackgroundChangedBroadcast();
            if (settingsInterface != null) {
                settingsInterface.onValuesUpdated();
            }
            showWallpaperPicker();
            return Unit.INSTANCE;
        }, () -> {
            showBackgroundColorDialog();
            return Unit.INSTANCE;
        });
        dlgBackground = dialogCoordinator.getDlgBackground();
    }

    public void showWallpaperPicker() {
        startActivity(SettingsIntentHelper.createWallpaperPickerChooserIntent());
    }

    public void showBackgroundColorDialog() {
        if (utilSettings == null) return;
        dialogCoordinator.showBackgroundColorDialog(this, utilSettings, selectedHex -> {
            sendBackgroundChangedBroadcast();
            if (settingsInterface != null) {
                settingsInterface.onValuesUpdated();
            }
            return Unit.INSTANCE;
        });
    }

    public void showHighlightColorDialog() {
        if (utilSettings == null) return;
        dialogCoordinator.showHighlightColorDialog(this, utilSettings, selectedHex -> {
            if (settingsInterface != null) {
                settingsInterface.onValuesUpdated();
            }
            return Unit.INSTANCE;
        });
        dlgHighlightColor = dialogCoordinator.getDlgHighlightColor();
    }

    private void dismissAllDialogs() {
        dialogCoordinator.dismissAllDialogs();
        dlgSortType = null;
        dlgIconPack = null;
        dlgNightMode = null;
        dlgBackground = null;
        dlgHighlightColor = null;
        dlgTerms = null;
    }

    @Override
    protected void onDestroy() {
        try {
            dismissAllDialogs();
            lensInterface = null;
            appsInterface = null;
            settingsInterface = null;
        } finally {
            adVipDelegate.onDestroy();
            adView = null;
            super.onDestroy();
        }
    }

    /**
     * Fix 4.6: Static inner class for ViewPager2 callback to avoid memory leak
     */
    private static class PageChangeCallback extends ViewPager2.OnPageChangeCallback {
        private final java.lang.ref.WeakReference<FloatingActionButton> fabSortRef;

        PageChangeCallback(FloatingActionButton fabSort) {
            this.fabSortRef = new java.lang.ref.WeakReference<>(fabSort);
        }

        @Override
        public void onPageSelected(int position) {
            FloatingActionButton fabSort = fabSortRef.get();
            if (fabSort != null) {
                if (position == 1) {
                    fabSort.show();
                } else {
                    fabSort.hide();
                }
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.a_fade_in, R.anim.a_fade_out);
    }

    private void sendEmail() {
        Intent intent = SettingsIntentHelper.createFeedbackEmailIntent();
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            System.out.println("No email app found!");
        }
    }

    private void checkShowAd() {
        boolean isNet = SettingsAdVipDelegate.isNetworkConnected(this);
        adVipDelegate.checkShowAd(this, flAdOpenApp, findViewById(R.id.bannerContainer), findViewById(R.id.tvLabelAd), isNet);
    }
}
