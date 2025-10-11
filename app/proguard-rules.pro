# ============================================================================
# PROGUARD RULES - Fisheye Launcher
# ============================================================================
# Optimized rules for maximum code shrinking while maintaining functionality
# R8 full mode enabled with aggressive optimization

# ============================================================================
# OPTIMIZATION FLAGS
# ============================================================================
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-dontpreverify
-dontusemixedcaseclassnames
-verbose

# ============================================================================
# SUGAR ORM - Database ORM Library
# ============================================================================
-keep class com.orm.** { *; }
-keep class com.mckimquyen.model.** { *; }
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
-keep class com.afollestad.materialdialogs.** { *; }
-dontwarn com.afollestad.materialdialogs.**

# ============================================================================
# KOTLIN & COROUTINES
# ============================================================================
-keepattributes *Annotation*
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
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
# ANDROIDX & LIFECYCLE
# ============================================================================
-keep class androidx.lifecycle.** { *; }
-keep class androidx.biometric.** { *; }

# ============================================================================
# REMOVE UNUSED CODE
# ============================================================================
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
