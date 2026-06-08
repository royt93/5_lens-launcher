package com.mckimquyen.util;

import android.util.Log;

import com.mckimquyen.BuildConfig;

/**
 * Unified Logger for the application.
 * Uses a fixed tag "roy93~" for easier filtering in Logcat.
 * Debug logs are stripped in release builds (BuildConfig.DEBUG = false).
 */
public class Logger {
    private static final String TAG = "roy93~";

    /** Log a debug message. No-op in release builds. */
    public static void d(String message) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message);
        }
    }

    /** Log a debug message with a custom tag. No-op in release builds. */
    public static void d(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message);
        }
    }

    /** Log an information message. */
    public static void i(String message) {
        Log.i(TAG, message);
    }

    /** Log a warning message. */
    public static void w(String message) {
        Log.w(TAG, message);
    }

    /** Log an error message. */
    public static void e(String message) {
        Log.e(TAG, message);
    }

    /** Log an error message with an exception. */
    public static void e(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }
}

