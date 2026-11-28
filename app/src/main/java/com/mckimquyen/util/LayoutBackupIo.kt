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

/**
 * FEAT-006: SAF file I/O + Room read, kept out of ActSettings.java (a Java class with no
 * ergonomic way to call suspend functions directly) - matches this codebase's existing pattern of
 * exposing plain callback-based @JvmStatic entry points from a Kotlin object that self-manages
 * its own coroutine threading (see AppPersistent's companion functions).
 */
object LayoutBackupIo {

    /**
     * Builds the workspace JSON from Room + preferences. Shared by the manual SAF export and the DB-009 pre-import
     * checkpoint so both always describe the same thing.
     */
    suspend fun currentWorkspaceJson(context: Context): String {
        val database = AppDatabase.getInstance()
        val lenses = database.lensWorkspaceDao().getAll()
        // Rows of a deleted lens can reappear (a late per-app write after deleteLens), and
        // the snapshot factory rightly rejects unknown lenses - so export only live ones.
        val liveLensIds = lenses.mapTo(mutableSetOf()) { it.id }
        val rows = database.appPersistentDao().getAllRows().filter { it.lensId in liveLensIds }
        val settings = UtilSettings(context.applicationContext)
        val appSettings = lenses.map { lens ->
            LensAppSettingsBackup(
                lens.id,
                settings.getLensAppScope(lens.id),
                settings.getLensAppSelection(lens.id),
                settings.isLensFrozen(lens.id),
                settings.getLensHiddenAppIds(lens.id),
                // Always written ({} when no keys) so import can tell "no override" from a legacy file.
                settings.getLensPhysicsBackup(lens.id)
            )
        }
        val activeLensId = settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
            ?.takeIf { id -> lenses.any { it.id == id } }
            ?: LensWorkspace.DEFAULT_LENS_ID
        return LayoutBackupSnapshotFactory.create(
            lenses,
            rows,
            appSettings,
            System.currentTimeMillis(),
            activeLensId,
            settings.getCollapsedLensGroups()
        ).toJson()
    }

    @JvmStatic
    fun exportAsync(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        ApplicationScope.scope.launch(Dispatchers.IO) {
            val success = try {
                val json = currentWorkspaceJson(context)
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
                val json = context.applicationContext.contentResolver.openInputStream(uri)?.use(::readBoundedText)
                if (json == null) LayoutBackupParseResult.Malformed else LayoutBackup.parse(json)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.e("LayoutBackupIo: workspace import read failed", error)
                LayoutBackupParseResult.Malformed
            } catch (error: OutOfMemoryError) {
                // The size cap below should make this unreachable; a tight heap on a small phone is still a refused file, not a dead process.
                Logger.e("LayoutBackupIo: workspace import ran out of memory", error)
                LayoutBackupParseResult.Malformed
            } catch (error: StackOverflowError) {
                // A deeply nested JSON document overflows the recursive parser; it is not a layout backup.
                Logger.e("LayoutBackupIo: workspace import JSON nested too deeply", error)
                LayoutBackupParseResult.Malformed
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    @JvmStatic
    fun applyWorkspaceAsync(context: Context, backup: LayoutBackup, onResult: (WorkspaceBackupImportResult) -> Unit) {
        val appContext = context.applicationContext
        // DB-009: every import first checkpoints the current workspace into rotating app-private snapshots.
        val recovery = WorkspaceImportRecovery(
            WorkspaceBackupRoomStore(appContext),
            LocalWorkspaceBackups(LocalWorkspaceBackups.defaultDirectory(appContext))
        ) { currentWorkspaceJson(appContext) }
        ApplicationScope.scope.launch(Dispatchers.IO) {
            val result = recovery.apply(backup)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /** Largest layout backup the import will read. A 12-lens, 3000-row backup is a few hundred KB; this is far above any real file. */
    internal const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
    private const val READ_BUFFER_BYTES = 8 * 1024

    /**
     * Reads the whole stream as UTF-8 text but never holds more than [MAX_IMPORT_BYTES] + 1 bytes: `readText()` on a huge file filled the heap before any
     * check could run. A file over the cap returns a value that is not JSON, so the parser reports it as malformed.
     */
    internal fun readBoundedText(stream: java.io.InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (output.size() <= MAX_IMPORT_BYTES) {
            val room = MAX_IMPORT_BYTES + 1 - output.size()
            val count = stream.read(buffer, 0, minOf(buffer.size, room))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        if (output.size() > MAX_IMPORT_BYTES) return TOO_LARGE_MARKER
        return output.toString(Charsets.UTF_8.name())
    }

    /** Not JSON on purpose: LayoutBackup.parse turns it into Malformed. */
    internal const val TOO_LARGE_MARKER = "too large"
}
