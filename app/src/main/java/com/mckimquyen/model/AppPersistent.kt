package com.mckimquyen.model;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.orm.SugarRecord;
import com.orm.query.Condition;
import com.orm.query.Select;
import com.orm.util.NamingHelper;

/**
 * Persistent storage model for application metadata.
 * <p>
 * Stores per-app data including:
 * - Open count (for sorting by most used)
 * - Custom order number (for manual sorting)
 * - Visibility state (for hiding apps)
 * - Lock state (for biometric protection)
 * <p>
 * Uses SugarORM for database operations.
 * <p>
 * Note: 2023.03.19 tried to convert to Kotlin but failed due to SugarORM compatibility
 */
@Keep
public class AppPersistent extends SugarRecord {

    /* Required Default Constructor for SugarORM */
    public AppPersistent() {
    }

    // Fields - use Hungarian notation for SugarORM compatibility
    private String mPackageName;
    private String mName;
    private String mIdentifier;
    private long mOpenCount;
    private int mOrderNumber;
    private boolean mAppVisible;
    private boolean mAppOpened;

    // Default values
    private static final boolean DEFAULT_APP_VISIBILITY = true;
    private static final boolean DEFAULT_APP_OPENED = true;
    private static final int DEFAULT_ORDER_NUMBER = -1;
    private static final long DEFAULT_OPEN_COUNT = 1;

    // Cache column name to avoid repeated NamingHelper calls
    private static final String COLUMN_IDENTIFIER = NamingHelper.toSQLNameDefault("mIdentifier");

    public AppPersistent(String packageName, String name, long openCount, int orderNumber, boolean appVisible, boolean appOpened) {
        this.mPackageName = packageName;
        this.mName = name;
        this.mIdentifier = AppPersistent.generateIdentifier(packageName, name);
        this.mOpenCount = openCount;
        this.mOrderNumber = orderNumber;
        this.mAppVisible = appVisible;
        this.mAppOpened = appOpened;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(String packageName) {
        this.mPackageName = packageName;
        this.mIdentifier = AppPersistent.generateIdentifier(this.mPackageName, this.mName);
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
        this.mIdentifier = AppPersistent.generateIdentifier(this.mPackageName, this.mName);
    }

    public String getIdentifier() {
        return mIdentifier;
    }

    public long getOpenCount() {
        return mOpenCount;
    }

    public void setOpenCount(long openCount) {
        this.mOpenCount = openCount;
    }

    public int getOrderNumber() {
        return mOrderNumber;
    }

    public void setOrderNumber(int orderNumber) {
        this.mOrderNumber = orderNumber;
    }

    public boolean isAppVisible() {
        return mAppVisible;
    }

    public void setAppVisible(boolean appVisible) {
        this.mAppVisible = appVisible;
    }

    public boolean isAppOpened() {
        return mAppOpened;
    }

    public void setAppOpened(boolean appOpened) {
        this.mAppOpened = appOpened;
    }

    @NonNull
    @Override
    public String toString() {
        return "AppPersistent{" +
                "mPackageName='" + mPackageName + '\'' +
                ", mName='" + mName + '\'' +
                ", mIdentifier='" + mIdentifier + '\'' +
                ", mOpenCount=" + mOpenCount +
                ", mOrderNumber=" + mOrderNumber +
                ", mAppVisible=" + mAppVisible +
                ", mAppOpened=" + mAppOpened +
                '}';
    }

    /**
     * Generates unique identifier from packageName and name.
     * Format: "packageName-name"
     *
     * @param packageName Android package name (e.g., "com.example.app")
     * @param name Activity name
     * @return Unique identifier string
     */
    @NonNull
    public static String generateIdentifier(@Nullable String packageName, @Nullable String name) {
        if (packageName == null || name == null) {
            return "";
        }
        return packageName + "-" + name;
    }

    /**
     * Helper method: Find existing app by identifier or return null.
     * Reduces code duplication across all query methods.
     */
    @Nullable
    private static AppPersistent findByIdentifier(@NonNull String identifier) {
        return Select.from(AppPersistent.class)
                .where(Condition.prop(COLUMN_IDENTIFIER).eq(identifier))
                .first();
    }

    /**
     * Helper method: Find existing app or create new one with defaults.
     * Reduces code duplication across all setter methods.
     */
    @NonNull
    private static AppPersistent findOrCreate(@NonNull String packageName,
                                              @NonNull String name) {
        String identifier = generateIdentifier(packageName, name);
        AppPersistent existing = findByIdentifier(identifier);

        if (existing != null) {
            return existing;
        }

        return new AppPersistent(
                packageName,
                name,
                DEFAULT_OPEN_COUNT,
                DEFAULT_ORDER_NUMBER,
                DEFAULT_APP_VISIBILITY,
                DEFAULT_APP_OPENED
        );
    }

    /**
     * Increments the open count for an app.
     * Creates new entry if app doesn't exist in database.
     *
     * @param packageName Android package name
     * @param name Activity name
     */
    public static void incrementAppCount(@Nullable String packageName, @Nullable String name) {
        if (packageName == null || name == null) return;

        String identifier = generateIdentifier(packageName, name);
        AppPersistent app = findByIdentifier(identifier);

        if (app != null) {
            // Existing app: increment count
            app.setOpenCount(app.getOpenCount() + 1);
            app.save();
        } else {
            // New app: create with DEFAULT_OPEN_COUNT (1)
            AppPersistent newApp = new AppPersistent(
                    packageName,
                    name,
                    DEFAULT_OPEN_COUNT,
                    DEFAULT_ORDER_NUMBER,
                    DEFAULT_APP_VISIBILITY,
                    DEFAULT_APP_OPENED
            );
            newApp.save();
        }
    }

    /**
     * Sets custom order number for manual app sorting.
     *
     * @param packageName Android package name
     * @param name Activity name
     * @param orderNumber Custom order position
     */
    public static void setAppOrderNumber(@Nullable String packageName,
                                         @Nullable String name,
                                         int orderNumber) {
        if (packageName == null || name == null) return;

        AppPersistent app = findOrCreate(packageName, name);
        app.setOrderNumber(orderNumber);
        app.save();
    }

    /**
     * Gets app lock state (for biometric protection).
     *
     * @param packageName Android package name
     * @param name Activity name
     * @return true if app is unlocked (opened), false if locked
     */
    public static boolean getAppOpened(@Nullable String packageName, @Nullable String name) {
        if (packageName == null || name == null) return true;

        String identifier = generateIdentifier(packageName, name);
        AppPersistent app = findByIdentifier(identifier);

        return app == null || app.isAppOpened();
    }

    /**
     * Sets app lock state (for biometric protection).
     *
     * @param packageName Android package name
     * @param name Activity name
     * @param appOpened true to unlock app, false to lock it
     */
    public static void setAppOpened(@Nullable String packageName,
                                    @Nullable String name,
                                    boolean appOpened) {
        if (packageName == null || name == null) return;

        AppPersistent app = findOrCreate(packageName, name);
        app.setAppOpened(appOpened);
        app.save();
    }

    /**
     * Gets app visibility state.
     *
     * @param packageName Android package name
     * @param name Activity name
     * @return true if app is visible, false if hidden
     */
    public static boolean getAppVisibility(@Nullable String packageName, @Nullable String name) {
        if (packageName == null || name == null) return true;

        String identifier = generateIdentifier(packageName, name);
        AppPersistent app = findByIdentifier(identifier);

        return app == null || app.isAppVisible();
    }

    /**
     * Sets app visibility state.
     *
     * @param packageName Android package name
     * @param name Activity name
     * @param visible true to show app, false to hide it
     */
    public static void setAppVisibility(@Nullable String packageName,
                                        @Nullable String name,
                                        boolean visible) {
        if (packageName == null || name == null) return;

        AppPersistent app = findOrCreate(packageName, name);
        app.setAppVisible(visible);
        app.save();
    }

    /**
     * Gets the number of times an app has been opened.
     *
     * @param packageName Android package name
     * @param name Activity name
     * @return Open count, or 0 if app not found
     */
    public static long getAppOpenCount(@Nullable String packageName, @Nullable String name) {
        if (packageName == null || name == null) return 0;

        String identifier = generateIdentifier(packageName, name);
        AppPersistent app = findByIdentifier(identifier);

        return app != null ? app.getOpenCount() : 0;
    }
}
