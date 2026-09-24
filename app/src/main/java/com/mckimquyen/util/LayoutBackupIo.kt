package com.mckimquyen.util

import android.content.Context
import android.net.Uri
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * FEAT-006: SAF file I/O + Room read, kept out of ActSettings.java (a Java class with no
 * ergonomic way to call suspend functions directly) - matches this codebase's existing pattern of
 * exposing plain callback-based @JvmStatic entry points from a Kotlin object that self-manages
 * its own coroutine threading (see AppPersistent's companion functions).
 */
object LayoutBackupIo {

    @JvmStatic
    fun exportAsync(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        ApplicationScope.scope.launch(Dispatchers.IO) {
            val success = try {
                val rows = AppDatabase.getInstance().appPersistentDao().getAll()
                val json = LayoutBackup.fromPersistent(rows, System.currentTimeMillis()).toJson()
                context.applicationContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } != null
            } catch (error: Exception) {
                Logger.e("LayoutBackupIo: export failed", error)
                false
            }
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    @JvmStatic
    fun importAsync(context: Context, uri: Uri, onResult: (LayoutBackupParseResult) -> Unit) {
        ApplicationScope.scope.launch(Dispatchers.IO) {
            val result = try {
                val json = context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                }
                if (json == null) LayoutBackupParseResult.Malformed else LayoutBackup.parse(json)
            } catch (error: Exception) {
                Logger.e("LayoutBackupIo: import read failed", error)
                LayoutBackupParseResult.Malformed
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }
}
