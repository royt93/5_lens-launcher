package com.mckimquyen.app;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.lifecycle.ProcessLifecycleOwner;

import com.mckimquyen.services.AppEventManager;
import com.mckimquyen.util.Logger;
import com.mckimquyen.services.BroadcastReceivers;
import com.mckimquyen.services.TaskSortApps;
import com.mckimquyen.services.TaskUpdateApps;
import com.mckimquyen.model.AppDatabase;

import kotlin.Unit;

/**
 * ============================================================================
 * APPLICATION CLASS - Fisheye Launcher
 * ============================================================================
 * Class chính của ứng dụng, kế thừa từ Application
 * Khởi tạo khi app start và tồn tại trong suốt vòng đời của app

 * CHỨC NĂNG CHÍNH:
 * 1. Khởi tạo AdMob SDK (Google Ads)
 * 2. Quản lý danh sách apps qua LiveData event system
 * 3. Tự động update apps khi có thay đổi (install/uninstall/update)
 * 4. Tự động sort apps khi user thay đổi settings

 * EVENT MANAGEMENT:
 * - Lắng nghe AppEventManager.appsUpdated: Apps được install/uninstall/update
 * - Lắng nghe AppEventManager.appsEdited: User thay đổi settings (sort type, icon pack, etc.)
 * - Sử dụng LiveData thay vì deprecated Observable/Observer pattern

 * THREADING:
 * - AdMob initialization chạy trên background thread để không block main thread
 * - Task execution (TaskUpdateApps, TaskSortApps) chạy async với coroutines
 * - LiveData observers chạy trên main thread

 * NOTE:
 * - Class này được khai báo trong AndroidManifest.xml: android:name=".app.RApplication"
 * ============================================================================
 */
public class RApplication extends android.app.Application {

    private static final String TAG = "RApplication";

    // ========================================================================
    // LIFECYCLE - Application Initialization
    // ========================================================================

    /**
     * Được gọi khi app start lần đầu tiên
     * Chỉ chạy 1 lần trong toàn bộ lifecycle của app

     * Thứ tự thực hiện:
     * 1. super.onCreate() - Khởi tạo database Room
     * 2. setupAdmob() - Khởi tạo Google AdMob SDK
     * 3. setupEventListeners() - Đăng ký lắng nghe events từ AppEventManager
     * 4. updateApps() - Load danh sách apps lần đầu
     */
    private BroadcastReceiver packageReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        // Khởi tạo database Room
        AppDatabase.Companion.init(this);

        // Khởi tạo AdMob SDK trên background thread
        setupAdmob();

        // Đăng ký LiveData observers để lắng nghe app events
        setupEventListeners();

        // Fix BUG: Không nhận được PACKAGE_ADDED trên Android 8+
        // Đăng ký dynamic BroadcastReceiver thay vì dùng Manifest
        registerPackageChangeReceiver();

        // Load danh sách apps lần đầu tiên
        updateApps();
    }

    /**
     * Đăng ký receiver lắng nghe cài đặt/gỡ cài đặt ứng dụng.
     * Bắt buộc đăng ký dynamically vì Android 8.0+ giới hạn implicit broadcasts
     * trong Manifest (bao gồm PACKAGE_ADDED, PACKAGE_REMOVED).
     */
    private void registerPackageChangeReceiver() {
        Logger.d("RApplication: Registering dynamic package receiver for PACKAGE_ADDED/REMOVED");
        packageReceiver = new BroadcastReceivers.AppsUpdatedReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    // ========================================================================
    // ADMOB INITIALIZATION
    // ========================================================================

    /**
     * Khởi tạo Google AdMob SDK

     * CHÚ Ý:
     * - Chạy trên background thread để không block main thread
     * - MobileAds.initialize() có thể mất 1-2 giây
     * - AdMobManager.init() cần GAID (Google Advertising ID) nên cũng mất thời gian

     * PROCESS:
     * 1. MobileAds.initialize(): Khởi tạo AdMob SDK
     * 2. AdMobManager.init(): Setup custom ad manager (GAID, consent, etc.)

     * THREADING:
     * - new Thread().start(): Background thread để tránh ANR (Application Not Responding)
     * - Lambda callback: Được gọi khi initialization hoàn tất
     */
    private void setupAdmob() {
        boolean isTestEnv = false;
        try {
            Class<?> registryClass = Class.forName("androidx.test.platform.app.InstrumentationRegistry");
            java.lang.reflect.Method getInstrumentation = registryClass.getMethod("getInstrumentation");
            isTestEnv = (getInstrumentation.invoke(null) != null);
        } catch (Throwable ignored) {
            // Throws exception (e.g. IllegalStateException: No instrumentation registered) if not running under a test runner
        }

        com.roy.sdkadbmob.AdSafetyLimits safety = com.mckimquyen.BuildConfig.DEBUG ? 
                com.roy.sdkadbmob.AdSafetyLimits.Companion.getTEST() : 
                new com.roy.sdkadbmob.AdSafetyLimits();

        com.roy.sdkadbmob.AdSdkConfig adConfig = new com.roy.sdkadbmob.AdSdkConfig(
            isTestEnv ? false : com.mckimquyen.BuildConfig.IS_ENABLE_ADMOB,
            com.mckimquyen.BuildConfig.DEBUG,
            isTestEnv ? "" : com.mckimquyen.BuildConfig.ADMOB_APP_OPEN_ID,
            isTestEnv ? "" : com.mckimquyen.BuildConfig.ADMOB_INTERSTITIAL_ID,
            isTestEnv ? "" : com.mckimquyen.BuildConfig.ADMOB_BANNER_ID,
            isTestEnv ? "" : com.mckimquyen.BuildConfig.ADMOB_REWARDED_ID,
            isTestEnv ? "" : com.mckimquyen.BuildConfig.APPLOVIN_APP_OPEN_ID,
            isTestEnv ? "" : com.mckimquyen.BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            isTestEnv ? "" : com.mckimquyen.BuildConfig.APPLOVIN_BANNER_ID,
            isTestEnv ? "" : com.mckimquyen.BuildConfig.APPLOVIN_REWARDED_ID,
            safety,
            com.mckimquyen.feature.vip.AdKeys.INSTANCE.getVIP_SECRET(),
            "e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt"
        );

        com.roy.sdkadbmob.AdManager.INSTANCE.setConfig(adConfig);
        com.roy.sdkadbmob.AdManager.INSTANCE.initialize(this, (success, gaid) -> {
            Logger.d(TAG, "AdManager init success=" + success + ", gaid=" + gaid);
            return kotlin.Unit.INSTANCE;
        });
    }

    // ========================================================================
    // EVENT LISTENERS - Setup LiveData Observers
    // ========================================================================

    /**
     * Đăng ký lắng nghe các sự kiện từ AppEventManager

     * MIGRATION NOTE:
     * - OLD: Observable/Observer pattern (deprecated since Java 9)
     * - NEW: LiveData từ AndroidX Lifecycle
     * - FIX: Dùng ProcessLifecycleOwner.get() thay vì observeForever()

     * EVENTS:
     * - appsUpdated: Apps được install/uninstall/update
     *   → Gọi updateApps() để reload danh sách apps từ PackageManager

     * - appsEdited: User thay đổi settings (sort type, visibility, etc.)
     *   → Gọi editApps() để re-sort danh sách apps hiện tại

     * FLOW:
     * BroadcastReceiver → AppEventManager.notify() → LiveData.postValue() → Observer callback → Task.execute()

     * THREADING:
     * - observe() chạy trên main thread (LiveData requirement)
     * - Callbacks được gọi trên main thread
     * - Tasks (updateApps/editApps) tự động chuyển sang background thread

     * LIFECYCLE AWARE:
     * - ProcessLifecycleOwner represents the lifecycle of the whole application process
     * - Observers tự động cleanup khi process bị destroyed
     * - No memory leak: Lifecycle-aware observers được auto-removed
     */
    private void setupEventListeners() {
        // Lắng nghe sự kiện apps updated (install/uninstall/update)
        // Use ProcessLifecycleOwner instead of observeForever to prevent memory leak
        AppEventManager.INSTANCE.getAppsUpdated().observe(ProcessLifecycleOwner.get(), data -> {
            // Apps changed → Reload from PackageManager
            updateApps();
        });

        // Lắng nghe sự kiện apps edited (settings changed)
        // Use ProcessLifecycleOwner instead of observeForever to prevent memory leak
        AppEventManager.INSTANCE.getAppsEdited().observe(ProcessLifecycleOwner.get(), data -> {
            // Settings changed → Re-sort existing apps
            editApps();
        });
    }

    // ========================================================================
    // TASK EXECUTION - Background Processing
    // ========================================================================

    /**
     * Update danh sách apps từ PackageManager

     * KỊCH BẢN SỬ DỤNG:
     * - Lần đầu app start
     * - User install app mới
     * - User uninstall app
     * - User update app

     * PROCESS:
     * 1. Query PackageManager để lấy tất cả apps có LAUNCHER intent
     * 2. Load icon cho từng app
     * 3. Apply settings (icon pack, visibility, sort type)
     * 4. Lưu vào RAppsSingleton
     * 5. Notify observers (FrmLens, FrmApps) để update UI

     * THREADING:
     * - Task chạy async trên background thread (Kotlin coroutines)
     * - UI update trên main thread
     */
    private void updateApps() {
        new TaskUpdateApps(
                getPackageManager(),        // PackageManager để query apps
                getApplicationContext(),    // Context để load resources
                this)                       // Application instance
                .execute();                 // Execute async
    }

    /**
     * Re-sort danh sách apps hiện tại

     * KỊCH BẢN SỬ DỤNG:
     * - User thay đổi sort type (A-Z, Z-A, Most Used, etc.)
     * - User thay đổi icon pack
     * - User hide/unhide apps
     * - User lock/unlock apps

     * PROCESS:
     * 1. Lấy danh sách apps từ RAppsSingleton
     * 2. Apply sort type từ settings
     * 3. Filter apps theo visibility
     * 4. Lưu lại vào RAppsSingleton
     * 5. Notify observers để update UI

     * THREADING:
     * - Task chạy async trên background thread (Kotlin coroutines)
     * - UI update trên main thread

     * PERFORMANCE:
     * - Nhanh hơn updateApps() vì không cần query PackageManager
     * - Chỉ re-sort data có sẵn trong memory
     */
    private void editApps() {
        new TaskSortApps(
                getApplicationContext(),    // Context để load settings
                this)                       // Application instance
                .execute();                 // Execute async
    }

    /**
     * Override onTerminate() để unregister packageReceiver.
     * Lưu ý: trên real Android device, onTerminate() thường không được gọi.
     * Nhưng cần đối với emulator, tests, và trành đã đăng ký lạ trên Android 14+.
     */
    @Override
    public void onTerminate() {
        if (packageReceiver != null) {
            try {
                unregisterReceiver(packageReceiver);
                packageReceiver = null;
                Logger.d("RApplication: packageReceiver unregistered in onTerminate()");
            } catch (IllegalArgumentException e) {
                Logger.w("RApplication: packageReceiver was already unregistered");
            }
        }
        super.onTerminate();
    }

    // ========================================================================
    // DEVELOPMENT NOTES & TODO LIST
    // ========================================================================
    //
    // ✅ COMPLETED:
    // - In-app review integration
    // - 120hz support (smooth scrolling)
    // - AppLovin mediation logic
    // - Keystore configuration
    // - Fix toggle lock/unlock, hide/unhide bugs
    // - Fix UI light mode bug on first app install
    // - Fix infinite loading in APPS tab
    // - Privacy policy dialog on first launch
    // - App launcher icon (ic_launcher)
    // - Icon pack search feature
    // - Check default home launcher on start
    // - Lock/unlock app with biometric
    // - GitHub repository links
    // - License page
    // - AppLovin mediation debugger (debug builds only)
    // - iOS-style customized switches
    // - Internal WebView for links
    // - Changelog viewer
    // - Font scale support
    // - App launcher uninstall feature
    // - App launcher app info feature
    // - Migrate from Observable/Observer to LiveData (Fix deprecation warnings)
    //
    // 📝 NOTES:
    // - Splash screen: App này không cần splash screen (direct to ActSettings)
    // - Screen orientation: Locked to portrait for better UX
    // - Cannot convert to Kotlin: SugarApp has compatibility issues with Kotlin
    //
    // ========================================================================
}
