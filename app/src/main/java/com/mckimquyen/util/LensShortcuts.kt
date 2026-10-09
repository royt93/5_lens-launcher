package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.ui.ActHome

/** FISH-021: launcher shortcuts that jump straight to a lens. */
object LensShortcuts {
    /** Declared in res/xml/shortcuts.xml; a unit test keeps this honest. They share the per-activity limit. */
    const val STATIC_SHORTCUT_COUNT = 2
    const val SHORTCUT_ID_PREFIX = "lens_"

    @JvmStatic
    fun shortcutIdFor(lensId: String): String = SHORTCUT_ID_PREFIX + lensId

    /** The lens id a shortcut id carries, or null for any id that is not one of ours. */
    @JvmStatic
    fun lensIdFromShortcutId(shortcutId: String): String? =
        shortcutId.removePrefix(SHORTCUT_ID_PREFIX).takeIf {
            shortcutId.startsWith(SHORTCUT_ID_PREFIX) && it.isNotEmpty()
        }

    @JvmStatic
    fun pick(lenses: List<LensWorkspace>, maxPerActivity: Int): List<LensWorkspace> {
        val room = (maxPerActivity - STATIC_SHORTCUT_COUNT).coerceAtLeast(0)
        return lenses.sortedBy { it.orderIndex }.take(room)
    }

    /** Never throws: a launcher that rejects shortcuts must not break the home screen. */
    @JvmStatic
    fun refresh(context: Context, lenses: List<LensWorkspace>) {
        runCatching {
            val picked = pick(lenses, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
            val shortcuts = picked.map { lens ->
                ShortcutInfoCompat.Builder(context, shortcutIdFor(lens.id))
                    .setShortLabel(lens.name)
                    .setLongLabel(lens.name)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_swap_horiz_24dp))
                    .setIntent(
                        Intent(context, ActHome::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(ActHome.EXTRA_TARGET_LENS_ID, lens.id)
                    )
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }.onFailure { Logger.e("LensShortcuts: could not publish lens shortcuts", it) }
    }
}
