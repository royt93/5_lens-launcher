package com.mckimquyen.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * FEAT-006: one exported app's organization state - the exact fields the story scopes as
 * "layout" (favorites/folders/pinned zones/order/hidden/locked). Deliberately excludes
 * [AppPersistent.openCount] and [AppPersistent.paletteColor] - those are usage stats/per-device
 * theming, not organization a user would expect a "restore my layout" feature to touch.
 */
data class LayoutBackupEntry(
    val packageName: String,
    val name: String,
    val orderNumber: Int,
    val appVisible: Boolean,
    val appOpened: Boolean,
    val isFavorite: Boolean,
    val folderName: String?,
    val pinnedZone: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_PACKAGE_NAME, packageName)
        put(KEY_NAME, name)
        put(KEY_ORDER_NUMBER, orderNumber)
        put(KEY_APP_VISIBLE, appVisible)
        put(KEY_APP_OPENED, appOpened)
        put(KEY_IS_FAVORITE, isFavorite)
        put(KEY_FOLDER_NAME, folderName ?: JSONObject.NULL)
        put(KEY_PINNED_ZONE, pinnedZone)
    }

    companion object {
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_NAME = "name"
        private const val KEY_ORDER_NUMBER = "orderNumber"
        private const val KEY_APP_VISIBLE = "appVisible"
        private const val KEY_APP_OPENED = "appOpened"
        private const val KEY_IS_FAVORITE = "isFavorite"
        private const val KEY_FOLDER_NAME = "folderName"
        private const val KEY_PINNED_ZONE = "pinnedZone"

        fun fromPersistent(row: AppPersistent): LayoutBackupEntry? {
            val packageName = row.packageName?.takeIf(String::isNotBlank) ?: return null
            val name = row.name?.takeIf(String::isNotBlank) ?: return null
            return LayoutBackupEntry(
                packageName = packageName,
                name = name,
                orderNumber = row.orderNumber,
                appVisible = row.appVisible,
                appOpened = row.appOpened,
                isFavorite = row.isFavorite,
                folderName = row.folderName,
                pinnedZone = row.pinnedZone
            )
        }

        /** Returns null (skip, not a malformed-file error) for one bad entry within a valid file. */
        fun fromJson(json: JSONObject): LayoutBackupEntry? {
            val packageName = json.optString(KEY_PACKAGE_NAME).takeIf(String::isNotBlank) ?: return null
            val name = json.optString(KEY_NAME).takeIf(String::isNotBlank) ?: return null
            return LayoutBackupEntry(
                packageName = packageName,
                name = name,
                orderNumber = json.optInt(KEY_ORDER_NUMBER, -1),
                appVisible = json.optBoolean(KEY_APP_VISIBLE, true),
                appOpened = json.optBoolean(KEY_APP_OPENED, true),
                isFavorite = json.optBoolean(KEY_IS_FAVORITE, false),
                folderName = json.optString(KEY_FOLDER_NAME, null).takeIf { !json.isNull(KEY_FOLDER_NAME) },
                pinnedZone = json.optString(KEY_PINNED_ZONE, PinnedZone.NONE.name)
            )
        }
    }
}

/** Outcome of parsing an imported file - never throws, always one of these. */
sealed class LayoutBackupParseResult {
    data class Success(val backup: LayoutBackup) : LayoutBackupParseResult()
    data object Malformed : LayoutBackupParseResult()
    data class UnsupportedSchemaVersion(val foundVersion: Int) : LayoutBackupParseResult()
}

data class LayoutBackup(
    val schemaVersion: Int,
    val exportedAt: Long,
    val entries: List<LayoutBackupEntry>
) {
    fun toJson(): String = JSONObject().apply {
        put(KEY_SCHEMA_VERSION, schemaVersion)
        put(KEY_EXPORTED_AT, exportedAt)
        put(KEY_ENTRIES, JSONArray(entries.map { it.toJson() }))
    }.toString(JSON_INDENT_SPACES)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val JSON_INDENT_SPACES = 2
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_ENTRIES = "entries"

        fun fromPersistent(rows: List<AppPersistent>, exportedAt: Long): LayoutBackup =
            LayoutBackup(CURRENT_SCHEMA_VERSION, exportedAt, rows.mapNotNull(LayoutBackupEntry::fromPersistent))

        /**
         * Never throws. A future, higher schema version is rejected explicitly (this app version
         * cannot know what new fields mean) rather than silently importing a partial/best-effort
         * read of it.
         */
        fun parse(json: String): LayoutBackupParseResult {
            val root = try {
                JSONObject(json)
            } catch (e: JSONException) {
                return LayoutBackupParseResult.Malformed
            }
            if (!root.has(KEY_SCHEMA_VERSION) || !root.has(KEY_ENTRIES)) {
                return LayoutBackupParseResult.Malformed
            }
            val version = root.optInt(KEY_SCHEMA_VERSION, -1)
            if (version <= 0) return LayoutBackupParseResult.Malformed
            if (version > CURRENT_SCHEMA_VERSION) {
                return LayoutBackupParseResult.UnsupportedSchemaVersion(version)
            }
            val entriesArray = root.optJSONArray(KEY_ENTRIES) ?: return LayoutBackupParseResult.Malformed
            val entries = (0 until entriesArray.length()).mapNotNull { index ->
                entriesArray.optJSONObject(index)?.let(LayoutBackupEntry::fromJson)
            }
            return LayoutBackupParseResult.Success(
                LayoutBackup(version, root.optLong(KEY_EXPORTED_AT, 0L), entries)
            )
        }
    }
}
