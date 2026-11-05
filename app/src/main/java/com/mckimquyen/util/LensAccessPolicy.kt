package com.mckimquyen.util

import com.mckimquyen.model.LensWorkspace

enum class LensTargetDecision {
    ALLOWED,
    FALLBACK,
    BLOCKED
}

data class LensTargetResolution(
    val decision: LensTargetDecision,
    val lensId: String?
)

object LensAccessPolicy {
    fun canLock(lensId: String, lenses: List<LensWorkspace>, locked: Set<String>): Boolean {
        if (lenses.none { it.id == lensId } || lensId in locked) return false
        return firstUnlocked(lenses, locked - lensId) != null && firstUnlocked(lenses, locked + lensId) != null
    }

    fun canDelete(lensId: String, lenses: List<LensWorkspace>, locked: Set<String>): Boolean {
        if (lenses.none { it.id == lensId }) return false
        return firstUnlocked(lenses.filterNot { it.id == lensId }, locked - lensId) != null
    }

    fun firstUnlocked(lenses: List<LensWorkspace>, locked: Set<String>): String? =
        lenses.firstOrNull { it.id !in locked }?.id

    fun resolveTarget(
        requestedLensId: String,
        lenses: List<LensWorkspace>,
        locked: Set<String>
    ): LensTargetResolution {
        val known = lenses.any { it.id == requestedLensId }
        return if (known && requestedLensId !in locked) {
            LensTargetResolution(LensTargetDecision.ALLOWED, requestedLensId)
        } else {
            LensTargetResolution(LensTargetDecision.BLOCKED, null)
        }
    }

    fun resolveActive(
        savedActiveLensId: String?,
        lenses: List<LensWorkspace>,
        locked: Set<String>
    ): LensTargetResolution {
        val target = savedActiveLensId?.takeIf { id -> lenses.any { it.id == id } && id !in locked }
        if (target != null) return LensTargetResolution(LensTargetDecision.ALLOWED, target)
        val fallback = firstUnlocked(lenses, locked)
            ?: return LensTargetResolution(LensTargetDecision.BLOCKED, null)
        return LensTargetResolution(LensTargetDecision.FALLBACK, fallback)
    }
}
