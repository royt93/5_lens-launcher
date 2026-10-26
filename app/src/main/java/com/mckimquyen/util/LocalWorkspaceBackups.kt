package com.mckimquyen.util

import android.content.Context
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupParseResult
import java.io.File
import java.io.FileOutputStream

/**
 * DB-009: a small rotating set of workspace snapshots kept inside the app's own storage.
 *
 * - Files are named `backup-<16-digit stamp>.json`, so lexical order is chronological. The stamp is the clock,
 *   bumped to stay strictly above the newest existing one, so two saves in one millisecond or a clock that moved
 *   backwards still sort newest-last-written first.
 * - Each file is written to a `.tmp` sibling, fsynced and renamed, so a crash never leaves a half-written
 *   snapshot that looks valid; leftover `.tmp` files are removed on the next save.
 * - Only files matching the naming pattern are listed, read or rotated away; anything else in the directory is
 *   left alone.
 * - The default directory is under `noBackupFilesDir`, so Android's own backup never carries these snapshots to
 *   another device, and it is not under any FileProvider path in `file_paths.xml`.
 */
class LocalWorkspaceBackups(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxBackups: Int = MAX_BACKUPS
) {
    /** Writes [json] as the newest snapshot and rotates old ones out. Returns the file, or null if it failed. */
    fun save(json: String): File? = try {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create backup directory $directory" }
        removeStaleTemps()
        val newest = list().firstOrNull()?.let(::stampOf)
        val stamp = maxOf(clock(), (newest ?: -1L) + 1L).coerceAtLeast(0L)
        val target = File(directory, nameFor(stamp))
        val temp = File(directory, nameFor(stamp) + TEMP_SUFFIX)
        FileOutputStream(temp).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Cannot move backup into place")
        }
        list().drop(maxBackups).forEach(File::delete)
        target
    } catch (error: Exception) {
        Logger.e("LocalWorkspaceBackups: save failed", error)
        null
    }

    /** Snapshot files, newest first. */
    fun list(): List<File> = directory.listFiles()
        ?.filter { NAME_PATTERN.matches(it.name) }
        ?.sortedByDescending { it.name }
        .orEmpty()

    /** The parsed snapshot in [file], or null when it is unreadable or not a valid backup. */
    fun read(file: File): LayoutBackup? = try {
        (LayoutBackup.parse(file.readText(Charsets.UTF_8)) as? LayoutBackupParseResult.Success)?.backup
    } catch (error: Exception) {
        Logger.e("LocalWorkspaceBackups: read failed for ${file.name}", error)
        null
    }

    /** The newest snapshot that still parses (a corrupt newest file is skipped, not fatal). */
    fun latest(): LayoutBackup? = list().firstNotNullOfOrNull(::read)

    private fun removeStaleTemps() {
        directory.listFiles()
            ?.filter { it.name.startsWith(PREFIX) && it.name.endsWith(JSON_SUFFIX + TEMP_SUFFIX) }
            ?.forEach(File::delete)
    }

    private fun stampOf(file: File): Long = NAME_PATTERN.matchEntire(file.name)?.groupValues?.get(1)?.toLong() ?: 0L

    private fun nameFor(stamp: Long) = PREFIX + stamp.toString().padStart(STAMP_DIGITS, '0') + JSON_SUFFIX

    companion object {
        /** How many snapshots are kept; older ones are deleted on each save. */
        const val MAX_BACKUPS = 5
        private const val DIRECTORY_NAME = "layout_backups"
        private const val PREFIX = "backup-"
        private const val JSON_SUFFIX = ".json"
        private const val TEMP_SUFFIX = ".tmp"
        private const val STAMP_DIGITS = 16
        private val NAME_PATTERN = Regex("""backup-(\d{$STAMP_DIGITS})\.json""")

        fun defaultDirectory(context: Context): File = File(context.applicationContext.noBackupFilesDir, DIRECTORY_NAME)
    }
}
