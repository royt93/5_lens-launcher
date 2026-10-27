package com.mckimquyen.util

import com.mckimquyen.services.AppEventManager
import com.mckimquyen.ui.settings.ResetSnapshot

/** FISH-038: writes a lens-to-lens move through [UtilSettings] and gives back an exact undo snapshot. */
class LensMoveApplier(private val settings: UtilSettings) {
    class Result(val snapshot: ResetSnapshot)

    /** Writes the move and returns the undo snapshot, or null when nothing was changed. */
    fun move(appId: String, sourceLensId: String, targetLensId: String): Result? {
        if (sourceLensId == targetLensId) return null
        val (newSource, newTarget) = LensMovePlan.plan(appId, stateOf(sourceLensId), stateOf(targetLensId)) ?: return null
        val snapshot = settings.snapshotKeys(keysOf(sourceLensId) + keysOf(targetLensId))
        write(sourceLensId, newSource)
        write(targetLensId, newTarget)
        AppEventManager.notifyLensScopeChanged(sourceLensId)
        AppEventManager.notifyLensScopeChanged(targetLensId)
        return Result(snapshot)
    }

    /** Puts the snapshot back; false (nothing refreshed) when the write failed. */
    fun undo(snapshot: ResetSnapshot): Boolean {
        val restored = settings.restoreKeys(snapshot)
        if (restored) AppEventManager.notifyLensScopeChanged(null)
        return restored
    }

    private fun stateOf(lensId: String) = LensState(
        settings.getLensAppScope(lensId), settings.getLensAppSelection(lensId), settings.getLensHiddenAppIds(lensId)
    )

    private fun write(lensId: String, state: LensState) {
        // Write the plan's scope as is: LensAppScopeEditor.apply would infer SELECTED from a stale selection on an
        // ALL target. The empty -> ALL safeguard already lives in LensMovePlan, so a blank lens cannot happen here.
        settings.saveLensAppScope(lensId, state.scope)
        settings.saveLensAppSelection(lensId, state.selection)
        settings.saveLensHiddenAppIds(lensId, state.hidden)
    }

    private fun keysOf(lensId: String): Set<String> = setOf(
        settings.lensKey(UtilSettings.KEY_LENS_APP_SCOPE, lensId),
        settings.lensKey(UtilSettings.KEY_LENS_APP_SELECTION, lensId),
        settings.lensKey(UtilSettings.KEY_LENS_HIDDEN_APP_IDS, lensId)
    )
}
