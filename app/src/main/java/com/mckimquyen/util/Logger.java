package com.mckimquyen.util;

import android.util.Log;

/**
 * Unified Logger for the application.
 * Uses a fixed tag "roy93~" for easier filtering in Logcat.
 */
public class Logger {
    private static final String TAG = "roy93~";

    /**
     * Log an information message.
     * 
     * @param message The message to log.
     */
    public static void i(String message) {
        Log.i(TAG, message);
    }
}
