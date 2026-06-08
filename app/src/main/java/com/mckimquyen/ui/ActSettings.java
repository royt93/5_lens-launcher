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
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.mckimquyen.util.Logger;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.color.ColorChooserDialog;
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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import kotlin.Unit;

//2023.03.19 tried to convert kotlin but failed
public class ActSettings extends ActBase
        implements ColorChooserDialog.ColorCallback {

    private static final String TAG_COLOR_BACKGROUND = "BackgroundColor";
    private static final String TAG_COLOR_HIGHLIGHT = "HighlightColor";
    Toolbar toolbar;
    TabLayout tabs;
    ViewPager2 viewpager;
    FloatingActionButton fabSort;
    LinearLayout flAdOpenApp;
    // private MaxAdView adView;
    private View adView = null;
    private android.animation.ObjectAnimator vipBadgeAnimator;

    private ArrayList<App> listApp;
    private MaterialDialog dlgSortType;
    private MaterialDialog dlgIconPack;
    private MaterialDialog dlgNightMode;
    private MaterialDialog dlgBackground;
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
        com.roy.sdkadbmob.AdManager.INSTANCE.setCurrentActivity(this);
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
        FragmentPagerAdapter mPagerAdapter = new FragmentPagerAdapter(ActSettings.this, ActSettings.this);
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

        // Banner chỉ load SAU KHI App Open Splash dismiss (xem checkShowAd)
        // để tránh count hidden impression khi flAdOpenApp đang che
        com.roy.sdkadbmob.AdManager.INSTANCE.loadInterstitial(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);

        // Tint all menu icons white
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                icon.setColorFilter(getResources().getColor(R.color.colorWhite), PorterDuff.Mode.SRC_IN);
            }
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (com.roy.sdkadbmob.AdManager.INSTANCE.isVIPMember() || com.roy.sdkadbmob.AdManager.INSTANCE.isVipByKeyActive()) {
            if (adView != null) {
                com.roy.sdkadbmob.AdManager.INSTANCE.bannerDestroy(adView);
                adView = null;
            }
            findViewById(R.id.bannerContainer).setVisibility(View.GONE);
            findViewById(R.id.tvLabelAd).setVisibility(View.GONE);
        } else {
            findViewById(R.id.bannerContainer).setVisibility(View.VISIBLE);
            findViewById(R.id.tvLabelAd).setVisibility(View.VISIBLE);
            if (adView == null) {
                adView = com.roy.sdkadbmob.AdManager.INSTANCE.loadBanner(this,
                        (android.view.ViewGroup) findViewById(R.id.bannerContainer),
                        (android.widget.TextView) findViewById(R.id.tvLabelAd),
                        com.roy.sdkadbmob.AdManager.INSTANCE.getAdaptiveBannerSize(this),
                        true);
            } else {
                com.roy.sdkadbmob.AdManager.INSTANCE.bannerResume(adView);
            }
        }
        bindToolbarVipBadge();
        // Show Terms and Privacy Policy dialog only once per session
        if (utilSettings != null && !hasShownTermsDialog) {
            boolean hasRead = utilSettings.getBoolean(UtilSettings.KEY_READ_POLICY);
            if (!hasRead) {
                hasShownTermsDialog = true; // Mark as shown for this session

                // Show non-cancelable dialog - user MUST choose an option
                showDialog2(
                        this,
                        getString(R.string.terms_and_privacy_policy),
                        getString(R.string.read_policy),
                        getString(R.string.agree_and_continue),
                        getString(R.string.cancel),
                        () -> {
                            // Button 1: Agree and Continue
                            utilSettings.save(UtilSettings.KEY_READ_POLICY, true);
                            openUrlInBrowser(this, URL_POLICY_NOTION, getString(R.string.terms_and_privacy_policy),
                                    false);
                        },
                        () -> {
                            // Button 2: Cancel
                            utilSettings.save(UtilSettings.KEY_READ_POLICY, true);
                        },
                        false, // isCancelable = false (user MUST choose)
                        () -> {
                            // onDismiss: Fallback to save state even if somehow dismissed
                            utilSettings.save(UtilSettings.KEY_READ_POLICY, true);
                        });
            }
        }
    }

    private void startVipBadgeAnimation(android.view.View view) {
        if (vipBadgeAnimator != null) {
            vipBadgeAnimator.cancel();
            vipBadgeAnimator = null;
        }
        if (view == null) return;
        vipBadgeAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            view,
            android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 0.9f, 1.0f),
            android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 0.9f, 1.0f)
        );
        vipBadgeAnimator.setDuration(1200);
        vipBadgeAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        vipBadgeAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        vipBadgeAnimator.start();
    }

    private void stopVipBadgeAnimation() {
        if (vipBadgeAnimator != null) {
            vipBadgeAnimator.cancel();
            vipBadgeAnimator = null;
        }
    }

    public void navigateToVipTab() {
        android.content.Intent intent = new android.content.Intent(this, com.mckimquyen.feature.vip.ActVipManagement.class);
        startActivity(intent);
    }

    private void bindToolbarVipBadge() {
        android.view.View chipVipBadge = findViewById(R.id.chipVipBadge);
        if (chipVipBadge != null) {
            boolean active = com.roy.sdkadbmob.AdManager.INSTANCE.isVIPMember();
            if (chipVipBadge instanceof android.view.ViewGroup) {
                android.widget.TextView tv = chipVipBadge.findViewById(R.id.tvVipBadgeStatus);
                android.widget.ImageView iv = chipVipBadge.findViewById(R.id.ivVipBadgeIcon);
                boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                                  == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                if (active) {
                    tv.setText(getString(R.string.vip_badge_active));
                    tv.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                    chipVipBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFD60A"))); // Gold
                    if (iv != null) {
                        iv.setColorFilter(android.graphics.Color.parseColor("#1C1C1E"), android.graphics.PorterDuff.Mode.SRC_IN);
                    }
                } else {
                    tv.setText(getString(R.string.vip_badge_get));
                    int bgColor = isNight ? android.graphics.Color.parseColor("#2C2C2E") : android.graphics.Color.parseColor("#E5E5EA");
                    int textColor = isNight ? android.graphics.Color.parseColor("#E5E5EA") : android.graphics.Color.parseColor("#3A3A3C");
                    tv.setTextColor(textColor);
                    chipVipBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
                    if (iv != null) {
                        iv.setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_IN);
                    }
                }
            }
            chipVipBadge.setVisibility(android.view.View.VISIBLE);
            startVipBadgeAnimation(chipVipBadge);
        }
    }

    @Override
    protected void onPause() {
        stopVipBadgeAnimation();
        com.roy.sdkadbmob.AdManager.INSTANCE.bannerPause(adView);
        // Dismiss dialogs in onPause to prevent WindowLeaked exception
        dismissAllDialogs();
        super.onPause();
    }

    private void launchApps() {
        boolean isDefaultLauncher = UtilLauncher.isDefaultLauncher(getApplication());
        Logger.d("ActSettings", "launchApps - isDefaultLauncher: " + isDefaultLauncher);

        if (isDefaultLauncher) {
            // Already default launcher -> go to launcher home screen
            com.roy.sdkadbmob.AdManager.INSTANCE.showInterstitial(this, aBoolean -> {
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(homeIntent);
                finish(); // Close settings activity to go back to launcher
                return Unit.INSTANCE;
            });
        } else {
            // Not default launcher -> show chooser to set as default
            showHomeLauncherChooser();
        }
        overridePendingTransition(R.anim.a_fade_in, R.anim.a_fade_out);
    }

    // private void createAdInter() {
    // boolean enableAdInter = getString(R.string.EnableAdInter).equals("true");
    // if (!enableAdInter) {
    // return;
    // }
    // String id = getString(R.string.INTER);
    // if (id.isEmpty()) {
    // return;
    // }
    //

    /// / interstitialAd = new MaxInterstitialAd(id, this);
    /// / interstitialAd.setListener(new MaxAdListener() {
    /// / @Override
    /// / public void onAdLoaded(@NonNull MaxAd maxAd) {
    /// /// retryAttempt = 0;
    /// / }
    /// /
    /// / @Override
    /// / public void onAdDisplayed(@NonNull MaxAd maxAd) {
    /// /
    /// / }
    /// /
    /// / @Override
    /// / public void onAdHidden(@NonNull MaxAd maxAd) {
    /// / // Interstitial ad is hidden. Pre-load the next ad
    /// / interstitialAd.loadAd();
    /// / }
    /// /
    /// / @Override
    /// / public void onAdClicked(@NonNull MaxAd maxAd) {
    /// /
    /// / }
    /// /
    /// / @Override
    /// / public void onAdLoadFailed(@NonNull String s, @NonNull MaxError maxError)
    /// {
    /// /// retryAttempt++;
    /// /// long delayMillis = TimeUnit.SECONDS.toMillis((long) Math.pow(2,
    /// Math.min(6, retryAttempt)));
    /// ///
    /// /// new Handler().postDelayed(() -> interstitialAd.loadAd(), delayMillis);
    /// / }
    /// /
    /// / @Override
    /// / public void onAdDisplayFailed(@NonNull MaxAd maxAd, @NonNull MaxError
    /// maxError) {
    /// / // Interstitial ad failed to display. AppLovin recommends that you load
    /// the next ad.
    /// / interstitialAd.loadAd();
    /// / }
    /// / });
    /// / // Load the first ad
    /// / interstitialAd.loadAd();
    // }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menuItemShowApps) {
            launchApps();
            return true;
        } else if (id == R.id.menuItemAbout) {
            com.roy.sdkadbmob.AdManager.INSTANCE.showInterstitial(this, aBoolean -> {
                Intent aboutIntent = new Intent(ActSettings.this, ActAbout.class);
                startActivity(aboutIntent);
                overridePendingTransition(R.anim.a_slide_in_left, R.anim.a_slide_out_right);
                return Unit.INSTANCE;
            });
            return true;
        } else if (id == R.id.menuItemResetDefaultSettings) {
            switch (viewpager.getCurrentItem()) {
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
            return true;
        } else if (id == R.id.menuRateApp) {
            rateApp(this, this.getPackageName());
            return true;
        } else if (id == R.id.menuMoreApp) {
            moreApp(this, "SAIGON PHANTOM LABS");
            return true;
        }
        // else if (id == R.id.menuApplovinConfig) {
        // if (BuildConfig.DEBUG) {
        // showMediationDebuggerApplovin(this);
        // } else {
        // Toast.makeText(
        // /* context = */ this,
        // /* resId = */ "This feature is only available in Debug mode",
        // /* duration = */ Toast.LENGTH_SHORT).show();
        // }
        // return true;
        // }
        else if (id == R.id.menuShareApp) {
            shareApp(this);
            return true;
        } else if (id == R.id.menuFacebookFanPage) {
            likeFacebookFanpage(this);
            return true;
        } else if (id == R.id.menuPolicy) {
            openUrlInBrowser(this, URL_POLICY_NOTION, getString(R.string.terms_and_privacy_policy), false);
            return true;
        } else if (id == R.id.menuGithubOriginal) {
            openUrlInBrowser(this, "https://github.com/ricknout/lens-launcher", getString(R.string.github_original),
                    true);
            return true;
        } else if (id == R.id.menuGithubFork) {
            openUrlInBrowser(this, "https://github.com/gj-loitp/lens-launcher", getString(R.string.github_fork), true);
            return true;
        } else if (id == R.id.menuLicense) {
            openUrlInBrowser(this, "https://raw.githubusercontent.com/ricknout/lens-launcher/master/LICENSE.md",
                    getString(R.string.license), true);
            return true;
        } else if (id == R.id.menuChangelog) {
            openUrlInBrowser(this, "https://raw.githubusercontent.com/gj-loitp/lens-launcher/dev/CHANGE_LOG.md",
                    getString(R.string.changelog), true);
            return true;
        } else if (id == R.id.menuFeedback) {
            sendEmail();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
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
        final List<SortType> lSortType = new ArrayList<>(EnumSet.allOf(SortType.class));
        final List<String> lSortTypeString = new ArrayList<>();
        for (int i = 0; i < lSortType.size(); i++) {
            lSortTypeString.add(getApplicationContext().getString(lSortType.get(i).getDisplayNameResId()));
        }
        assert utilSettings != null;
        SortType selectedSortType = utilSettings.getSortType();
        int selectedIndex = lSortType.indexOf(selectedSortType);
        dlgSortType = new MaterialDialog.Builder(ActSettings.this).title(R.string.setting_sort_apps)
                .items(lSortTypeString).alwaysCallSingleChoiceCallback()
                .itemsCallbackSingleChoice(selectedIndex, (dialog, view, which, text) -> {
                    utilSettings.save(lSortType.get(which));
                    sendEditAppsBroadcast();
                    return true;
                }).show();

        // Apply rounded background
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // Fix BUG-10: Guard Activity state trước khi chạm vào Window
            if (isDestroyed() || isFinishing()) return;
            if (dlgSortType != null && dlgSortType.getWindow() != null) {
                dlgSortType.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
            }
        }, 100);
    }

    public void showIconPackDialog() {
        final ArrayList<UtilIconPackManager.IconPack> lAvailableIconPack = new UtilIconPackManager()
                .getAvailableIconPacksWithIcons(true, getApplication());
        final ArrayList<String> lIconPackName = new ArrayList<>();
        lIconPackName.add(getString(R.string.setting_default_icon_pack));
        for (int i = 0; i < lAvailableIconPack.size(); i++) {
            if (!lIconPackName.isEmpty() && !lIconPackName.contains(lAvailableIconPack.get(i).mName)) {
                lIconPackName.add(lAvailableIconPack.get(i).mName);
            }
        }
        assert utilSettings != null;
        String selectedPackageName = utilSettings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME);
        int selectedIndex = lIconPackName.indexOf(selectedPackageName);
        dlgIconPack = new MaterialDialog.Builder(ActSettings.this).title(R.string.setting_icon_pack)
                .items(lIconPackName).alwaysCallSingleChoiceCallback()
                .itemsCallbackSingleChoice(selectedIndex, (dialog, view, which, text) -> {
                    utilSettings.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, lIconPackName.get(which));
                    if (settingsInterface != null) {
                        settingsInterface.onValuesUpdated();
                    }
                    sendUpdateAppsBroadcast();
                    return true;
                }).show();

        // Apply rounded background
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;
            if (dlgIconPack != null && dlgIconPack.getWindow() != null) {
                dlgIconPack.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
            }
        }, 100);
    }

    public void showHomeLauncherChooser() {
        UtilLauncher.resetPreferredLauncherAndOpenChooser(getApplicationContext());
    }

    public void showNightModeChooser() {
        String[] arrAvailableNightMode = getResources().getStringArray(R.array.night_modes);
        final ArrayList<String> nightModes = new ArrayList<>();
        Collections.addAll(nightModes, arrAvailableNightMode);
        assert utilSettings != null;
        String selectedNightMode = UtilNightModeUtil.getNightModeDisplayName(utilSettings.getNightMode());
        int selectedIndex = nightModes.indexOf(selectedNightMode);
        dlgNightMode = new MaterialDialog.Builder(ActSettings.this).title(R.string.setting_night_mode)
                .items(R.array.night_modes).alwaysCallSingleChoiceCallback()
                .itemsCallbackSingleChoice(selectedIndex, (dialog, view, which, text) -> {
                    String selection = nightModes.get(which);
                    utilSettings.save(UtilSettings.KEY_NIGHT_MODE,
                            UtilNightModeUtil.getNightModeFromDisplayName(selection));
                    sendNightModeBroadcast();
                    if (settingsInterface != null) {
                        settingsInterface.onValuesUpdated();
                    }
                    dismissBackgroundDialog();

                    // Recreate activity to apply new theme
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (!isDestroyed() && !isFinishing()) recreate();
                    }, 200);

                    return true;
                }).show();

        // Apply rounded background
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;
            if (dlgNightMode != null && dlgNightMode.getWindow() != null) {
                dlgNightMode.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
            }
        }, 100);
    }

    public void showBackgroundDialog() {
        String[] arrAvailableBackground = getResources().getStringArray(R.array.backgrounds);
        final ArrayList<String> backgroundNames = new ArrayList<>();
        Collections.addAll(backgroundNames, arrAvailableBackground);
        assert utilSettings != null;
        String selectedBackground = utilSettings.getString(UtilSettings.KEY_BACKGROUND);
        int selectedIndex = backgroundNames.indexOf(selectedBackground);
        dlgBackground = new MaterialDialog.Builder(ActSettings.this).title(R.string.setting_background)
                .items(R.array.backgrounds).alwaysCallSingleChoiceCallback()
                .itemsCallbackSingleChoice(selectedIndex, (dialog, view, which, text) -> {
                    String selection = backgroundNames.get(which);
                    if (selection.equals("Wallpaper")) {
                        utilSettings.save(UtilSettings.KEY_BACKGROUND, selection);
                        sendBackgroundChangedBroadcast();
                        if (settingsInterface != null) {
                            settingsInterface.onValuesUpdated();
                        }
                        dismissBackgroundDialog();
                        showWallpaperPicker();
                    } else if (selection.equals("Color")) {
                        dismissBackgroundDialog();
                        showBackgroundColorDialog();
                    }
                    return true;
                }).show();

        // Apply rounded background
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;
            if (dlgBackground != null && dlgBackground.getWindow() != null) {
                dlgBackground.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
            }
        }, 100);
    }

    public void showWallpaperPicker() {
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        startActivity(Intent.createChooser(intent, "Select Wallpaper"));
    }

    public void showBackgroundColorDialog() {
        // if (utilSettings == null) {
        // return;
        // }
        // ColorChooserDialog mBackgroundColorDialog = new
        // ColorChooserDialog.Builder(this,
        // R.string.setting_background_color).titleSub(R.string.setting_background_color).accentMode(false).doneButton(R.string.done).cancelButton(R.string.cancel).backButton(R.string.back).preselect(Color.parseColor(utilSettings.getString(UtilSettings.KEY_BACKGROUND_COLOR))).dynamicButtonColor(false).allowUserColorInputAlpha(false).tag(TAG_COLOR_BACKGROUND).show(this);
    }

    public void showHighlightColorDialog() {
        if (utilSettings == null) {
            return;
        }
        ColorChooserDialog dialog = new ColorChooserDialog.Builder(this, R.string.setting_highlight_color)
                .titleSub(R.string.setting_highlight_color)
                .accentMode(true)
                .doneButton(R.string.done)
                .cancelButton(R.string.cancel)
                .backButton(R.string.back)
                .preselect(Color.parseColor(utilSettings.getString(UtilSettings.KEY_HIGHLIGHT_COLOR)))
                .dynamicButtonColor(false)
                .allowUserColorInputAlpha(false)
                .tag(TAG_COLOR_HIGHLIGHT)
                .build();

        dialog.show(getSupportFragmentManager(), TAG_COLOR_HIGHLIGHT);

        // Apply rounded background after dialog is fully shown
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;
            if (dialog.getDialog() != null && dialog.getDialog().getWindow() != null) {
                dialog.getDialog().getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
            }
        }, 100);
    }

    @Override
    public void onColorSelection(@NonNull ColorChooserDialog dialog, @ColorInt int selectedColor) {
        if (utilSettings == null) {
            return;
        }
        String hexColor = String.format("#%06X", selectedColor);
        if (dialog.tag().equals(TAG_COLOR_BACKGROUND)) {
            utilSettings.save(UtilSettings.KEY_BACKGROUND, "Color");
            utilSettings.save(UtilSettings.KEY_BACKGROUND_COLOR, hexColor);
            sendBackgroundChangedBroadcast();
        } else if (dialog.tag().equals(TAG_COLOR_HIGHLIGHT)) {
            utilSettings.save(UtilSettings.KEY_HIGHLIGHT_COLOR, hexColor);
        }
        if (settingsInterface != null) {
            settingsInterface.onValuesUpdated();
        }
    }

    @Override
    public void onColorChooserDismissed(@NonNull ColorChooserDialog dialog) {
    }

    private void dismissSortTypeDialog() {
        if (dlgSortType != null && dlgSortType.isShowing()) {
            dlgSortType.dismiss();
        }
    }

    private void dismissIconPackDialog() {
        if (dlgIconPack != null && dlgIconPack.isShowing()) {
            dlgIconPack.dismiss();
        }
    }

    private void dismissNightModeDialog() {
        if (dlgNightMode != null && dlgNightMode.isShowing()) {
            dlgNightMode.dismiss();
        }
    }

    private void dismissBackgroundDialog() {
        if (dlgBackground != null && dlgBackground.isShowing()) {
            dlgBackground.dismiss();
        }
    }

    private void dismissAllDialogs() {
        dismissSortTypeDialog();
        dismissIconPackDialog();
        dismissNightModeDialog();
        dismissBackgroundDialog();
        // Color dialogs do not need to be dismissed
    }

    @Override
    protected void onDestroy() {
        try {
            dismissAllDialogs();
            // LiveData observers are automatically removed when lifecycle owner is
            // destroyed
            // Clear fragment interface references to prevent memory leaks
            lensInterface = null;
            appsInterface = null;
            settingsInterface = null;
        } finally {
            // Ensure AdView is always destroyed, even if exception occurs
            com.roy.sdkadbmob.AdManager.INSTANCE.bannerDestroy(adView);
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
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // Only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL,
                new String[]{"roy.mobile.dev@gmail.com", "20testersforclosedtesting@googlegroups.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback on Fisheye Launcher App");
        intent.putExtra(Intent.EXTRA_TEXT,
                """
                        Hello,
                        
                        I hope this message finds you well. Below are my feedback and suggestions regarding the Fisheye Launcher app:
                        
                        [Insert your feedback here]
                        
                        Thank you for your attention and support.
                        
                        Best regards,
                        [Your Name]""");

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            System.out.println("No email app found!");
        }
    }

    private void checkShowAd() {
        com.roy.sdkadbmob.AdManager.INSTANCE.requestConsentInfoUpdate(this, false, canRequestAds -> {
            com.roy.sdkadbmob.AdManager.INSTANCE.initSplashScreen(this, () -> {
                // 1. Ẩn splash overlay
                flAdOpenApp.setVisibility(View.GONE);
                // 2. Chỉ load banner SAU KHI user thực sự nhìn thấy nó
                if (!com.roy.sdkadbmob.AdManager.INSTANCE.isVIPMember() && !com.roy.sdkadbmob.AdManager.INSTANCE.isVipByKeyActive()) {
                    adView = com.roy.sdkadbmob.AdManager.INSTANCE.loadBanner(this,
                            (android.view.ViewGroup) findViewById(R.id.bannerContainer),
                            (android.widget.TextView) findViewById(R.id.tvLabelAd),
                            com.roy.sdkadbmob.AdManager.INSTANCE.getAdaptiveBannerSize(this),
                            true);
                } else {
                    findViewById(R.id.bannerContainer).setVisibility(View.GONE);
                    findViewById(R.id.tvLabelAd).setVisibility(View.GONE);
                }
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }

}
