package com.mckimquyen.util

import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent

/** FISH-021: which apps a lens shows. [ALL] is the default, so existing lenses are unchanged. */
enum class LensAppScope {
    ALL,
    SELECTED;

    companion object {
        @JvmStatic
        fun fromStored(value: String?): LensAppScope =
            entries.firstOrNull { it.name == value } ?: ALL

        @JvmStatic
        fun identifierOf(app: App): String =
            AppPersistent.generateIdentifier(app.packageName.toString(), app.name.toString())

        /** Pure and Android-free apart from [App]; always returns a fresh list. */
        @JvmStatic
        @JvmOverloads
        fun filter(apps: List<App>, scope: LensAppScope, selection: Set<String>, hiddenIds: Set<String> = emptySet()): ArrayList<App> =
            ArrayList(apps.filter {
                val id = identifierOf(it)
                (scope == ALL || id in selection) && id !in hiddenIds
            })
    }
}
