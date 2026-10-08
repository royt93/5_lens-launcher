package com.mckimquyen.util

/**
 * FISH-021: the single writer of a lens's app scope. The Apps tab, the lens-menu checklist and
 * the grid's "Remove from this lens" all go through here, so none of them can leave a lens
 * SELECTED with nothing in it (a blank home screen).
 */
class LensAppScopeEditor(private val settings: UtilSettings) {

    /** The apps the lens shows right now, given every installed app id. */
    fun effectiveIds(lensId: String?, allIds: Set<String>): Set<String> =
        when (settings.getLensAppScope(lensId)) {
            LensAppScope.ALL -> allIds
            LensAppScope.SELECTED -> settings.getLensAppSelection(lensId)
        }

    fun add(lensId: String?, ids: Set<String>, allIds: Set<String>): LensAppScope =
        apply(lensId, effectiveIds(lensId, allIds) + ids)

    fun remove(lensId: String?, ids: Set<String>, allIds: Set<String>): LensAppScope =
        apply(lensId, effectiveIds(lensId, allIds) - ids)

    /** Replaces the selection. An empty result means "show everything", never a blank lens. */
    fun apply(lensId: String?, ids: Set<String>): LensAppScope {
        val scope = if (ids.isEmpty()) LensAppScope.ALL else LensAppScope.SELECTED
        settings.saveLensAppSelection(lensId, ids)
        settings.saveLensAppScope(lensId, scope)
        return scope
    }
}
