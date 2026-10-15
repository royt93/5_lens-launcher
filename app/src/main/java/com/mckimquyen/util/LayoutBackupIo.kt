package com.mckimquyen.util

import android.content.Context
import android.net.Uri
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupParseResult
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.CancellationException
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
                val database = AppDatabase.getInstance()
                val lenses = database.lensWorkspaceDao().getAll()
                val rows = database.appPersistentDao().getAllRows()
                val settings = UtilSettings(context.applicationContext)
                val appSettings = lenses.map { lens ->
                    LensAppSettingsBackup(
                        lens.id,
                        settings.getLensAppScope(lens.id),
                        settings.getLensAppSelection(lens.id),
                        settings.isLensFrozen(lens.id)
                    )
                }
                val activeLensId = settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                    ?.takeIf { id -> lenses.any { it.id == id } }
                    ?: LensWorkspace.DEFAULT_LENS_ID
                val json = LayoutBackupSnapshotFactory.create(
                    lenses,
                    rows,
                    appSettings,
                    System.currentTimeMillis(),
                    activeLensId,
                    settings.getCollapsedLensGroups()
                ).toJson()
                context.applicationContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Unable to open backup destination")
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.e("LayoutBackupIo: workspace export failed", error)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.e("LayoutBackupIo: workspace import read failed", error)
                LayoutBackupParseResult.Malformed
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    @JvmStatic
    fun applyWorkspaceAsync(context: Context, backup: LayoutBackup, onResult: (WorkspaceBackupImportResult) -> Unit) {
        val coordinator = WorkspaceBackupImportCoordinator(WorkspaceBackupRoomStore(context.applicationContext))
        ApplicationScope.scope.launch(Dispatchers.IO) {
            val result = coordinator.apply(backup)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }
}
