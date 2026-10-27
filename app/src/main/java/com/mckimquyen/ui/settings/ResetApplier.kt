package com.mckimquyen.ui.settings

import com.mckimquyen.util.UtilSettings

/**
 * UI-034: writes every entry's default through the same typed savers the old per-tab reset used, so side effects such as
 * the icon-pack cache invalidation still happen. An unexpected value type is a programming error and fails loudly.
 */
object ResetApplier {
    fun apply(settings: UtilSettings, entries: List<ResetEntry>) {
        entries.forEach { entry ->
            val value = entry.defaultValue
            when {
                entry.key == UtilSettings.KEY_ICON_PACK_PACKAGE_NAME -> settings.saveIconPackPackageName(value as String)
                entry.key == UtilSettings.KEY_ICON_PACK_LABEL_NAME -> settings.saveLegacyIconPackLabel(value as String)
                value is Boolean -> settings.save(entry.key, value)
                value is Float -> settings.save(entry.key, value)
                value is Long -> settings.save(entry.key, value)
                value is Int -> settings.save(entry.key, value)
                value is String -> settings.save(entry.key, value)
                else -> error("Unsupported reset default type ${value::class.java.name} for ${entry.key}")
            }
        }
    }
}
