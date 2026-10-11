package com.mckimquyen.model

import com.mckimquyen.util.LensAppScope
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

        /**
         * Schema-v2 entries are strict (workspace-backup-v2 design): unlike [fromJson], a bad
         * entry here fails the whole lens/file rather than being silently skipped, so the same
         * JSON shape gets a stricter reading under v2 - in particular an unrecognized
         * [PinnedZone] name is rejected rather than defaulted to [PinnedZone.NONE].
         */
        fun fromJsonStrict(json: JSONObject): LayoutBackupEntry? {
            val packageName = json.strictString(KEY_PACKAGE_NAME)?.takeIf(String::isNotBlank) ?: return null
            val name = json.strictString(KEY_NAME)?.takeIf(String::isNotBlank) ?: return null
            val orderNumber = json.strictInt(KEY_ORDER_NUMBER) ?: return null
            val appVisible = json.strictBoolean(KEY_APP_VISIBLE) ?: return null
            val appOpened = json.strictBoolean(KEY_APP_OPENED) ?: return null
            val isFavorite = json.strictBoolean(KEY_IS_FAVORITE) ?: return null
            if (!json.has(KEY_FOLDER_NAME)) return null
            val folderName = when (val value = json.opt(KEY_FOLDER_NAME)) {
                JSONObject.NULL -> null
                is String -> value
                else -> return null
            }
            val pinnedZone = json.strictString(KEY_PINNED_ZONE) ?: return null
            if (PinnedZone.entries.none { it.name == pinnedZone }) return null
            return LayoutBackupEntry(packageName, name, orderNumber, appVisible, appOpened, isFavorite, folderName, pinnedZone)
        }

        private fun JSONObject.strictString(key: String): String? =
            if (opt(key) is String) optString(key) else null

        private fun JSONObject.strictBoolean(key: String): Boolean? =
            (opt(key) as? Boolean)

        private fun JSONObject.strictInt(key: String): Int? {
            val number = opt(key) as? Number ?: return null
            val longValue = number.toLong()
            if (number.toDouble() != longValue.toDouble() || longValue !in Int.MIN_VALUE..Int.MAX_VALUE) return null
            return longValue.toInt()
        }
    }
}

/**
 * workspace-backup-v2: one lens's full backed-up state - identity/order plus FISH-021's
 * scope/selection/frozen settings and that lens's own [LayoutBackupEntry] list. Only meaningful
 * under schema v2; v1 files have no concept of multiple lenses.
 */
data class LayoutBackupLens(
    val id: String,
    val name: String,
    val orderIndex: Int,
    val scope: LensAppScope,
    val selectedAppIds: Set<String>,
    val frozen: Boolean,
    val entries: List<LayoutBackupEntry>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_NAME, name)
        put(KEY_ORDER_INDEX, orderIndex)
        put(KEY_SCOPE, scope.name)
        put(KEY_SELECTED_APP_IDS, JSONArray(selectedAppIds.toList()))
        put(KEY_FROZEN, frozen)
        put(KEY_ENTRIES, JSONArray(entries.map { it.toJson() }))
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_ORDER_INDEX = "orderIndex"
        private const val KEY_SCOPE = "scope"
        private const val KEY_SELECTED_APP_IDS = "selectedAppIds"
        private const val KEY_FROZEN = "frozen"
        private const val KEY_ENTRIES = "entries"

        private fun JSONObject.stringField(key: String): String? =
            if (opt(key) is String) optString(key) else null

        private fun JSONObject.intField(key: String): Int? {
            val number = opt(key) as? Number ?: return null
            val longValue = number.toLong()
            if (number.toDouble() != longValue.toDouble() || longValue !in Int.MIN_VALUE..Int.MAX_VALUE) return null
            return longValue.toInt()
        }

        private fun JSONObject.booleanField(key: String): Boolean? = opt(key) as? Boolean

        /**
         * Returns null (malformed) rather than a best-effort lens - a single bad lens must fail
         * the whole v2 file, never partially import a workspace. Cross-lens checks (unique ids,
         * exactly one "default", unique/nonnegative order) are done by the caller once every
         * lens here has parsed individually.
         */
        fun fromJson(json: JSONObject): LayoutBackupLens? {
            val id = json.stringField(KEY_ID)?.takeIf(String::isNotBlank) ?: return null
            val name = json.stringField(KEY_NAME)?.takeIf(String::isNotBlank) ?: return null
            val orderIndex = json.intField(KEY_ORDER_INDEX) ?: return null
            val scopeName = json.stringField(KEY_SCOPE) ?: return null
            val scope = when (scopeName) {
                LensAppScope.ALL.name -> LensAppScope.ALL
                LensAppScope.SELECTED.name -> LensAppScope.SELECTED
                else -> return null
            }

            val selectedIdsJson = json.optJSONArray(KEY_SELECTED_APP_IDS) ?: return null
            val selectedIds = mutableListOf<String>()
            for (i in 0 until selectedIdsJson.length()) {
                val selectedId = selectedIdsJson.opt(i) as? String ?: return null
                selectedIds.add(selectedId.takeIf(String::isNotBlank) ?: return null)
            }
            if (selectedIds.toSet().size != selectedIds.size) return null

            val entriesJson = json.optJSONArray(KEY_ENTRIES) ?: return null
            val entries = mutableListOf<LayoutBackupEntry>()
            for (i in 0 until entriesJson.length()) {
                val entryObject = entriesJson.optJSONObject(i) ?: return null
                entries.add(LayoutBackupEntry.fromJsonStrict(entryObject) ?: return null)
            }

            return LayoutBackupLens(
                id = id,
                name = name,
                orderIndex = orderIndex,
                scope = scope,
                selectedAppIds = selectedIds.toSet(),
                frozen = json.booleanField(KEY_FROZEN) ?: return null,
                entries = entries
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

/**
 * [entries] is the schema-v1 payload (a flat, single-lens list); [lenses] is the schema-v2
 * payload (one entry per workspace lens, each with its own entries and FISH-021 state). Exactly
 * one of the two is ever populated - which one depends on [schemaVersion] - the other stays
 * `emptyList()`. Both fields exist on every instance purely so v1-era callers (e.g.
 * [fromPersistent]) keep compiling unchanged; see [toJson].
 */
data class LayoutBackup(
    val schemaVersion: Int,
    val exportedAt: Long,
    val entries: List<LayoutBackupEntry> = emptyList(),
    val lenses: List<LayoutBackupLens> = emptyList()
) {
    /** Schema v2 omits the legacy root `entries` key entirely (design: "no v1 root entries"). */
    fun toJson(): String = JSONObject().apply {
        put(KEY_SCHEMA_VERSION, schemaVersion)
        put(KEY_EXPORTED_AT, exportedAt)
        if (schemaVersion >= SCHEMA_VERSION_V2) {
            put(KEY_LENSES, JSONArray(lenses.map { it.toJson() }))
        } else {
            put(KEY_ENTRIES, JSONArray(entries.map { it.toJson() }))
        }
    }.toString(JSON_INDENT_SPACES)

    companion object {
        const val SCHEMA_VERSION_V1 = 1
        private const val SCHEMA_VERSION_V2 = 2
        const val CURRENT_SCHEMA_VERSION = SCHEMA_VERSION_V2
        private const val JSON_INDENT_SPACES = 2
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_LENSES = "lenses"

        // ponytail: still produces a flat, single-lens v1-shaped entries list tagged as v1 -
        // real per-lens-grouped v2 export (reading every workspace lens + FISH-021 state) is
        // wired in a later workspace-backup-v2 task (export orchestration), not this model task.
        fun fromPersistent(rows: List<AppPersistent>, exportedAt: Long): LayoutBackup =
            LayoutBackup(SCHEMA_VERSION_V1, exportedAt, rows.mapNotNull(LayoutBackupEntry::fromPersistent))

        /**
         * Never throws. A future, higher schema version is rejected explicitly (this app version
         * cannot know what new fields mean) rather than silently importing a partial/best-effort
         * read of it. v1 keeps its historical leniency (one bad entry is skipped); v2 is strict -
         * any semantically invalid lens/entry/id makes the whole file [Malformed].
         */
        fun parse(json: String): LayoutBackupParseResult {
            val root = try {
                JSONObject(json)
            } catch (e: JSONException) {
                return LayoutBackupParseResult.Malformed
            }
            if (!root.has(KEY_SCHEMA_VERSION)) return LayoutBackupParseResult.Malformed
            val version = root.optInt(KEY_SCHEMA_VERSION, -1)
            if (version <= 0) return LayoutBackupParseResult.Malformed
            if (version > CURRENT_SCHEMA_VERSION) {
                return LayoutBackupParseResult.UnsupportedSchemaVersion(version)
            }
            val exportedAt = root.optLong(KEY_EXPORTED_AT, 0L)
            return if (version == SCHEMA_VERSION_V1) parseV1(root, exportedAt) else parseV2(root, exportedAt)
        }

        private fun parseV1(root: JSONObject, exportedAt: Long): LayoutBackupParseResult {
            val entriesArray = root.optJSONArray(KEY_ENTRIES)
            if (!root.has(KEY_ENTRIES) || entriesArray == null) return LayoutBackupParseResult.Malformed
            val entries = (0 until entriesArray.length()).mapNotNull { index ->
                entriesArray.optJSONObject(index)?.let(LayoutBackupEntry::fromJson)
            }
            return LayoutBackupParseResult.Success(LayoutBackup(SCHEMA_VERSION_V1, exportedAt, entries))
        }

        private fun parseV2(root: JSONObject, exportedAt: Long): LayoutBackupParseResult {
            val lensesArray = root.optJSONArray(KEY_LENSES)
            if (!root.has(KEY_LENSES) || lensesArray == null) return LayoutBackupParseResult.Malformed

            val lenses = mutableListOf<LayoutBackupLens>()
            for (index in 0 until lensesArray.length()) {
                val lensObject = lensesArray.optJSONObject(index) ?: return LayoutBackupParseResult.Malformed
                lenses.add(LayoutBackupLens.fromJson(lensObject) ?: return LayoutBackupParseResult.Malformed)
            }
            if (lenses.any { it.entries.map { entry -> AppPersistent.generateIdentifier(entry.packageName, entry.name) }.toSet().size != it.entries.size }) {
                return LayoutBackupParseResult.Malformed
            }

            val ids = lenses.map { it.id }
            if (ids.toSet().size != ids.size) return LayoutBackupParseResult.Malformed
            if (ids.count { it == LensWorkspace.DEFAULT_LENS_ID } != 1) return LayoutBackupParseResult.Malformed

            val orderIndices = lenses.map { it.orderIndex }
            if (orderIndices.any { it < 0 }) return LayoutBackupParseResult.Malformed
            if (orderIndices.toSet().size != orderIndices.size) return LayoutBackupParseResult.Malformed

            return LayoutBackupParseResult.Success(LayoutBackup(SCHEMA_VERSION_V2, exportedAt, lenses = lenses))
        }
    }
}
