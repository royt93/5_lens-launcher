# ============================================================================
# PROGUARD RULES - Fisheye Launcher
# ============================================================================
# Shrunk and optimized by R8 (minifyEnabled + shrinkResources in app/build.gradle). Full mode is
# the AGP default, so no flag is needed; the legacy ProGuard-only flags (-optimizationpasses,
# -dontpreverify, -dontusemixedcaseclassnames, -verbose) are ignored by R8 and were removed.

# ============================================================================
# OPTIMIZATION FLAGS
# ============================================================================
-allowaccessmodification
-repackageclasses ''

# ============================================================================
# ROOM & DATA MODELS
# ============================================================================
# Room finds its generated *_Impl classes and maps columns by name, so only the annotated
# types are kept. The rest of com.mckimquyen.model (App, LayoutBackup*, ...) is plain Kotlin
# with hand-written org.json (de)serialisation and can be shrunk/renamed like any other code.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepattributes *Annotation*

# ============================================================================
# ADMOB - Google Mobile Ads SDK
# ============================================================================
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# AppLovin Mediation
-keep class com.applovin.** { *; }
-dontwarn com.applovin.**

# ============================================================================
# MATERIAL DIALOGS
# ============================================================================

# ============================================================================
# KOTLIN & COROUTINES
# ============================================================================
-keepattributes *Annotation*
-dontwarn kotlinx.coroutines.**

# ============================================================================
# WEBVIEW WITH JAVASCRIPT
# ============================================================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# ============================================================================
# PARCELABLE & SERIALIZABLE
# ============================================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepnames class * implements java.io.Serializable

# ============================================================================
# ENUMS
# ============================================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
# REMOVE LOGGING (Production only)
# ============================================================================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# ============================================================================
# ANDROIDX & LIFECYCLE (consumer AAR rules apply automatically)
# ============================================================================

# ============================================================================
# REMOVE UNUSED CODE
# ============================================================================
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
