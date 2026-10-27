package com.mckimquyen.util

/** FISH-038: one lens's app scope, selection and hidden set as stored in preferences. */
data class LensState(val scope: LensAppScope, val selection: Set<String>, val hidden: Set<String>)

/** FISH-038: pure rules for moving one app from a source lens to a target lens. */
object LensMovePlan {
    /** New state for (source, target), or null when [appId] is blank. The caller rejects same-lens moves. */
    fun plan(appId: String, source: LensState, target: LensState): Pair<LensState, LensState>? {
        if (appId.isBlank()) return null
        val newSource = when (source.scope) {
            LensAppScope.SELECTED -> {
                val remaining = source.selection - appId
                // Same safeguard as LensAppScopeEditor.apply: an empty selection means "show everything".
                if (remaining.isEmpty()) source.copy(scope = LensAppScope.ALL, selection = emptySet())
                else source.copy(selection = remaining)
            }
            LensAppScope.ALL -> source.copy(hidden = source.hidden + appId)
        }
        val newTarget = when (target.scope) {
            LensAppScope.SELECTED -> target.copy(selection = target.selection + appId, hidden = target.hidden - appId)
            LensAppScope.ALL -> target.copy(hidden = target.hidden - appId)
        }
        return newSource to newTarget
    }
}
