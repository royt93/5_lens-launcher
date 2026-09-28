package com.mckimquyen.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Dialog
import android.graphics.Rect
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.hypot
import kotlin.math.max

/**
 * FISH-010: Camera aperture/iris circular reveal for organization dialogs.
 * Uses native [ViewAnimationUtils.createCircularReveal] to open and close dialogs
 * expanding from or collapsing to the center of the triggering anchor view.
 */
object ApertureRevealHelper {

    const val DEFAULT_DURATION_MS = 250L

    data class RevealParams(
        val centerX: Int,
        val centerY: Int,
        val maxRadius: Float
    )

    /**
     * Pure calculation of circular reveal center and maximum radius from an anchor rect
     * relative to a dialog container rect.
     */
    @JvmStatic
    fun calculateParams(anchorRect: Rect?, containerWidth: Int, containerHeight: Int): RevealParams {
        val w = containerWidth.coerceAtLeast(1)
        val h = containerHeight.coerceAtLeast(1)

        val validAnchor = anchorRect?.takeIf { it.left < it.right && it.top < it.bottom }

        val cx = if (validAnchor != null) {
            ((validAnchor.left + validAnchor.right) / 2).coerceIn(0, w)
        } else {
            w / 2
        }

        val cy = if (validAnchor != null) {
            ((validAnchor.top + validAnchor.bottom) / 2).coerceIn(0, h)
        } else {
            h / 2
        }

        val dx = max(cx, w - cx).toDouble()
        val dy = max(cy, h - cy).toDouble()
        val maxRadius = hypot(dx, dy).toFloat().coerceAtLeast(1f)

        return RevealParams(cx, cy, maxRadius)
    }

    /**
     * Calculates the anchor's bounds relative to the decorView's coordinate space.
     */
    @JvmStatic
    fun getAnchorRelativeRect(anchor: View?, decorView: View): Rect? {
        if (anchor == null || !anchor.isAttachedToWindow) return null
        val anchorLoc = IntArray(2)
        val decorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        decorView.getLocationOnScreen(decorLoc)

        val relLeft = anchorLoc[0] - decorLoc[0]
        val relTop = anchorLoc[1] - decorLoc[1]
        return Rect(relLeft, relTop, relLeft + anchor.width, relTop + anchor.height)
    }

    /** Pure gate shared by entrance and exit paths. */
    @JvmStatic
    fun shouldAnimate(reduceMotion: Boolean, hasWindow: Boolean, isAttached: Boolean): Boolean =
        !reduceMotion && hasWindow && isAttached

    /** Call after create(), before show(), so Material's default window animation cannot start. */
    @JvmStatic
    fun prepareDialog(dialog: Dialog) {
        if (!LensPhysicsPolicy.shouldReduceLensMotion(dialog.context)) {
            dialog.window?.setWindowAnimations(0)
        }
    }

    /** Runs the circular entrance reveal after [dialog] has already been shown. */
    @JvmStatic
    fun revealShownDialog(dialog: Dialog, anchor: View?) {
        val window = dialog.window ?: return
        val decorView = window.decorView
        val reduceMotion = LensPhysicsPolicy.shouldReduceLensMotion(dialog.context)
        if (!shouldAnimate(reduceMotion, hasWindow = true, decorView.isAttachedToWindow)) return

        window.setWindowAnimations(0)
        decorView.post {
            if (!decorView.isAttachedToWindow || decorView.width <= 0 || decorView.height <= 0) return@post
            val params = calculateParams(
                getAnchorRelativeRect(anchor, decorView),
                decorView.width,
                decorView.height
            )
            try {
                ViewAnimationUtils.createCircularReveal(
                    decorView,
                    params.centerX,
                    params.centerY,
                    0f,
                    params.maxRadius
                ).apply {
                    duration = DEFAULT_DURATION_MS
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            } catch (_: Exception) {
                // Dialog is already visible; instant display is the correct fallback.
            }
        }
    }

    /**
     * Performs a circular un-reveal collapsing to [anchor], dismisses [dialog], then runs
     * [afterDismiss] exactly once. Reduced-motion and detached-window paths dismiss immediately.
     */
    @JvmStatic
    @JvmOverloads
    fun dismissWithReveal(dialog: Dialog, anchor: View?, afterDismiss: Runnable? = null) {
        var completed = false
        fun complete() {
            if (completed) return
            completed = true
            try {
                dialog.dismiss()
            } finally {
                afterDismiss?.run()
            }
        }

        val window = dialog.window
        val decorView = window?.decorView
        val reduceMotion = LensPhysicsPolicy.shouldReduceLensMotion(dialog.context)
        if (!shouldAnimate(reduceMotion, window != null, decorView?.isAttachedToWindow == true)
            || decorView == null
            || decorView.width <= 0
            || decorView.height <= 0
        ) {
            complete()
            return
        }

        val params = calculateParams(
            getAnchorRelativeRect(anchor, decorView),
            decorView.width,
            decorView.height
        )
        try {
            ViewAnimationUtils.createCircularReveal(
                decorView,
                params.centerX,
                params.centerY,
                params.maxRadius,
                0f
            ).apply {
                duration = DEFAULT_DURATION_MS
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = complete()
                    override fun onAnimationCancel(animation: Animator) = complete()
                })
                start()
            }
        } catch (_: Exception) {
            complete()
        }
    }
}
