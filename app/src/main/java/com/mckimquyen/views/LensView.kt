package com.mckimquyen.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.NinePatchDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import com.mckimquyen.R
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.DrawType
import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.services.BroadcastReceivers
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.LensPhysicsPolicy
import com.mckimquyen.util.SmartFocusArranger
import com.mckimquyen.util.UtilApp
import com.mckimquyen.util.UtilCalculator
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * FISH-009: Listener notified when the user performs a live pinch gesture to adjust curvature.
 * [finished] is false during active pinch drag and true once fingers lift / gesture completes.
 */
fun interface OnCurvatureAdjustedListener {
    fun onCurvatureAdjusted(curvature: Float, finished: Boolean)
}

/**
 * FISH-008 Phase 3: long-press on empty grid space - the standard launcher gesture for "configure
 * this home screen". Before this, that branch of the long-press did nothing at all, which made the
 * lens management menu unreachable on any single-lens install: its only other entry point is a
 * long-press on the page-dots indicator, and Phase 2 deliberately hides that indicator while one
 * lens exists (zero clutter), so no user could ever create their second lens.
 */
fun interface OnEmptySpaceLongPressListener {
    /** [x]/[y] are the pressed point in this view's own coordinates. */
    fun onEmptySpaceLongPress(x: Float, y: Float)
}

/** FISH-016: fired once when the user pulls down from the top slice of the lens. */
fun interface OnSearchSwipeDownListener {
    fun onSearchSwipeDown()
}

/**
 * FISH-009: Deterministic multi-touch gesture state machine for LensView.
 * Precedence: In-progress single-pointer pan is protected from stray second pointers.
 * Initial multi-touch transitions into PINCHING. When one finger lifts, enters PINCH_RELEASE
 * to suppress accidental app launch until all fingers leave the screen.
 */
enum class LensGestureState {
    IDLE,
    PANNING,
    PINCHING,
    PINCH_RELEASE
}

class LensView : View {

    companion object {
        // PERF-001: documented frame-time budgets used to judge the real-device drag benchmark
        // (adb shell dumpsys gfxinfo framestats) against 60/90/120 Hz displays.
        const val FRAME_BUDGET_60HZ_MS = 16.6f
        const val FRAME_BUDGET_90HZ_MS = 11.1f
        const val FRAME_BUDGET_120HZ_MS = 8.3f
        const val LARGE_APP_LIST_BENCHMARK_SIZE = 300

        // UI-022: pure gesture-disambiguation predicates, extracted so the exact logic
        // `onTouchEvent` runs is also directly unit-testable without a Context/Robolectric
        // (same pattern as AppAdapter's `lockIconResFor`/`AppAdapterSelectionStateTest`).

        /** Has the touch moved far enough from its down-point to count as a real pan? */
        @androidx.annotation.VisibleForTesting
        internal fun exceedsTouchSlop(dx: Float, dy: Float, touchSlop: Float): Boolean =
            sqrt(dx.toDouble().pow(2.0) + dy.toDouble().pow(2.0)) > touchSlop

        /**
         * FISH-008 Phase 2: is this move clearly a horizontal page-swipe rather than a lens pan?
         *
         * ViewPager2 and LensView both want every ACTION_MOVE, so one of them has to yield before
         * the other confirms. A lens pan is a free 2D drag, so anything with real vertical travel
         * belongs to the lens; only a near-horizontal drag is handed to the pager. The 2:1 ratio is
         * a deliberate bias toward the lens (the primary, always-present interaction) - the pager
         * only wins when the intent is unambiguous.
         *
         * ponytail: fixed 2:1 ratio, tuned on real hardware (TECNO KJ7). Revisit only if a device
         * with a very different touch-slop profile reports mis-detection; do not "improve" it with
         * velocity tracking without that evidence.
         */
        @androidx.annotation.VisibleForTesting
        internal fun isHorizontalSwipeIntent(dx: Float, dy: Float, touchSlop: Float): Boolean =
            abs(dx) > touchSlop && abs(dx) > abs(dy) * HORIZONTAL_SWIPE_DOMINANCE_RATIO

        /** How much more horizontal than vertical a move must be to count as a page swipe. */
        private const val HORIZONTAL_SWIPE_DOMINANCE_RATIO = 2.0f

        // FISH-016: iOS-style pull-down search. Only the top slice of the lens is claimed, so
        // one-finger fisheye pan everywhere else is untouched.
        private const val SEARCH_SWIPE_ACTIVATION_ZONE_RATIO = 0.20f
        private const val SEARCH_SWIPE_DISTANCE_MULTIPLIER = 4f
        private const val SEARCH_SWIPE_VERTICAL_DOMINANCE_RATIO = 2f

        /** Did the touch land in the top slice of the view that is reserved for pull-down search? */
        @androidx.annotation.VisibleForTesting
        internal fun isInSearchSwipeActivationZone(downY: Float, viewHeight: Int): Boolean =
            viewHeight > 0 && downY >= 0f && downY <= viewHeight * SEARCH_SWIPE_ACTIVATION_ZONE_RATIO

        /** Has a top-zone touch travelled far enough, down and straight enough, to open search? */
        @androidx.annotation.VisibleForTesting
        internal fun shouldOpenSearchSwipe(
            startedInActivationZone: Boolean,
            dx: Float,
            dy: Float,
            touchSlop: Float,
            alreadyTriggered: Boolean,
        ): Boolean =
            startedInActivationZone &&
                !alreadyTriggered &&
                dy >= touchSlop * SEARCH_SWIPE_DISTANCE_MULTIPLIER &&
                dy >= abs(dx) * SEARCH_SWIPE_VERTICAL_DOMINANCE_RATIO


        /**
         * Should the pending long-press Runnable actually act when it fires? False whenever
         * the touch already turned into a pan (`moving`), was released/cancelled before the
         * timeout (`armed` false), or never landed on an icon (`selectIndex < 0`) - a real
         * state check, not just "the timer elapsed".
         */
        @androidx.annotation.VisibleForTesting
        internal fun shouldTriggerLongPress(armed: Boolean, moving: Boolean, selectIndex: Int): Boolean =
            armed && !moving && selectIndex >= 0

        /**
         * FISH-008 Phase 3: the same press, but landing on empty space instead of an icon. Shares
         * every guard with [shouldTriggerLongPress] (still a real press, still not a pan) and is
         * exactly its complement on `selectIndex`, so the two can never both fire for one touch.
         */
        @androidx.annotation.VisibleForTesting
        internal fun shouldTriggerEmptySpaceLongPress(armed: Boolean, moving: Boolean, selectIndex: Int): Boolean =
            armed && !moving && selectIndex < 0

        /**
         * UI-024: pure gating decision, shared by every badge-rendering/announcing surface
         * (this view's own `drawAppIcon` and [LensAccessibilityHelper]'s TalkBack label) so they
         * can never drift out of sync on what counts as "has a visible badge".
         */
        internal fun shouldDrawNotificationBadge(showBadgesSetting: Boolean, notificationCount: Int): Boolean =
            showBadgesSetting && notificationCount > 0

        /** Clean the Lens: one gate for "badges on" shared by the draw path and TalkBack. */
        internal fun shouldShowNotificationBadges(badgesSetting: Boolean, cleanMode: Boolean): Boolean =
            badgesSetting && !cleanMode

        /** Clean the Lens: hover label is suppressed regardless of its own toggle. */
        internal fun shouldDrawAppNameLabel(showNameSetting: Boolean, cleanMode: Boolean, moving: Boolean): Boolean =
            showNameSetting && !cleanMode && moving

        /** Clean the Lens: the NEW tag counts as chrome and is suppressed too. */
        internal fun shouldDrawNewAppTag(showTagSetting: Boolean, cleanMode: Boolean): Boolean =
            showTagSetting && !cleanMode

        // FISH-009: Pure state-machine transition and calculation functions

        /**
         * Determines next gesture state when a pointer goes down.
         * Precedence: If already PANNING, stay PANNING (stray second pointer does not fight pan).
         * If IDLE or PINCH_RELEASE and pointerCount >= 2, enter PINCHING.
         */
        @androidx.annotation.VisibleForTesting
        internal fun resolvePointerDown(currentState: LensGestureState, pointerCount: Int): LensGestureState {
            if (currentState == LensGestureState.PANNING) {
                return LensGestureState.PANNING
            }
            if (pointerCount >= 2) {
                return LensGestureState.PINCHING
            }
            return currentState
        }

        /**
         * Determines next gesture state when a pointer lifts.
         * If PINCHING and fingers remain, enter PINCH_RELEASE to prevent accidental pan or launch.
         * When all fingers lift, return to IDLE.
         */
        @androidx.annotation.VisibleForTesting
        internal fun resolvePointerUp(currentState: LensGestureState, remainingPointerCount: Int): LensGestureState {
            if (remainingPointerCount <= 0) {
                return LensGestureState.IDLE
            }
            if (currentState == LensGestureState.PINCHING) {
                return LensGestureState.PINCH_RELEASE
            }
            return currentState
        }

        /**
         * Pure function to scale and clamp live curvature during a pinch gesture.
         * Clamped to [min, max] (defaults to [0.5f, 5.0f]).
         */
        @androidx.annotation.VisibleForTesting
        internal fun calculatePinchDistortion(
            current: Float,
            scaleFactor: Float,
            min: Float = UtilSettings.MIN_DISTORTION_FACTOR,
            max: Float = 5.0f
        ): Float {
            if (scaleFactor.isNaN() || scaleFactor <= 0f) return current
            return (current * scaleFactor).coerceIn(min, max)
        }

        /**
         * Guard to ensure app launch only occurs on valid single-pointer tap or pan-release,
         * never from pinch or pinch-release.
         */
        @androidx.annotation.VisibleForTesting
        internal fun shouldAllowAppLaunch(currentState: LensGestureState): Boolean =
            currentState != LensGestureState.PINCHING && currentState != LensGestureState.PINCH_RELEASE
    }
    private var mPaintIcons: Paint? = null
    private var mPaintCircles: Paint? = null
    private var mPaintTouchSelection: Paint? = null
    private var mPaintText: Paint? = null
    private var mPaintNewAppTag: Paint? = null
    private var mPaintBadgeBackground: Paint? = null
    private var mPaintBadgeText: Paint? = null

    /** Test hook only - mirrors LensGridCache.recomputeCount's production-counter convention. */
    @androidx.annotation.VisibleForTesting
    var badgeDrawCallCount: Int = 0
        private set

    private var mTouchX = -Float.MAX_VALUE
    private var mTouchY = -Float.MAX_VALUE
    private var mInsideRect = false
    private var mShowNotificationBadgesThisFrame = false
    private var mRectToSelect: RectF? = RectF(0f, 0f, 0f, 0f)
    private var mMustVibrate = true

    // FISH-017: test seam only. Called with the exact constant right before the haptic is
    // performed. Nulled on detach so the view never keeps a test lambda (and its captures) alive.
    @VisibleForTesting
    internal var onHapticPerformed: ((Int) -> Unit)? = null

    // FISH-020: injectable Vibrator provider for testing; defaults to system service.
    @VisibleForTesting
    internal var vibratorProvider: (() -> Vibrator?)? = null

    private var mSelectIndex = 0
    private var mSourceApps: ArrayList<App>? = null
    private var mApps: ArrayList<App>? = null
    private var mSmartFocusCols = -1
    private var mSmartFocusRows = -1
    private var mPackageManager: PackageManager? = null
    private var mAnimationMultiplier = 0.0f
    private var mAnimationHiding = false
    private var mTouchSlop = 0f
    private var mMoving = false

    // FISH-008 Phase 3: Lens-scoped configuration
    var lensId: String = LensWorkspace.DEFAULT_LENS_ID
        set(value) {
            if (field != value) {
                field = value
                mSmartFocusCols = -1
                mSmartFocusRows = -1
                applySmartFocusArrangement(force = true)
                invalidate()
            }
        }

    // FISH-009: Live pinch-to-adjust curvature state and detector
    var gestureState: LensGestureState = LensGestureState.IDLE
        internal set
    var liveDistortionFactor: Float? = null
        internal set
    var onCurvatureAdjustedListener: OnCurvatureAdjustedListener? = null
    private var mScaleGestureDetector: ScaleGestureDetector? = null
    private var mPinchReported = false
    private var mPaintHud: Paint? = null
    private var mPaintHudBackground: Paint? = null
    private val mHudRect = RectF()

    fun commitLiveDistortionFactor() {
        val factor = liveDistortionFactor ?: return
        mUtilSettings?.saveDistortionFactor(lensId, factor)
        mUtilSettings?.clearPendingDistortionFactor(lensId)
        liveDistortionFactor = null
        invalidate()
    }

    fun resetLiveDistortionFactor() {
        if (liveDistortionFactor != null) {
            liveDistortionFactor = null
            invalidate()
        }
        mUtilSettings?.clearPendingDistortionFactor(lensId)
    }

    /** FISH-012: restores a pinch adjustment that was persisted to the pending key but never
     *  resolved (Save/dismiss) before the process died - called by ActHome.bindLensView on every
     *  page bind when UtilSettings.getPendingDistortionFactor(lensId) is non-null. Does not touch
     *  the pending key itself; commitLiveDistortionFactor()/resetLiveDistortionFactor() clear it
     *  the normal way once the user answers again. */
    fun restoreLiveDistortionFactor(value: Float) {
        liveDistortionFactor = value
        invalidate()
    }

    /** FISH-012: persists the just-finished pinch value to the pending key *before* notifying the
     *  listener (which triggers ActHome's confirmation Snackbar) - so a process kill between
     *  gesture-end and the user answering that Snackbar doesn't lose the adjustment silently.
     *  Both call sites below (onScaleEnd, ACTION_UP) previously duplicated this same
     *  mPinchReported-guarded block; centralizing it here also closes that duplication.
     *  Visible for testing: driving a real ScaleGestureDetector span change via synthetic
     *  MotionEvents is impractical/flaky, so LensViewPinchIntegrationTest calls this directly -
     *  same seam shape as setLensStateForTest below. */
    @androidx.annotation.VisibleForTesting
    internal fun reportPinchFinished(finalDistortion: Float) {
        if (mPinchReported) return
        mPinchReported = true
        mUtilSettings?.savePendingDistortionFactor(lensId, finalDistortion)
        onCurvatureAdjustedListener?.onCurvatureAdjusted(finalDistortion, true)
    }

    // UI-022: long-press-and-hold quick actions (info/pin/uninstall). Explicit state guards
    // (mLongPressArmed/mMoving/mSelectIndex), not a bare timer alone: the posted Runnable only
    // acts if the touch never crossed the pan threshold and still sits over an icon by the time
    // it fires, and is cancelled outright the instant a real pan starts (ACTION_MOVE past
    // mTouchSlop) or the touch ends/cancels - so an in-progress pan can never be mistaken for a
    // long-press and a long-press can never fight the existing pan/launch gesture.
    private var mLongPressArmed = false
    private var mLongPressTriggered = false

    // UI-025: LensView draws every icon on one canvas, so there is no child view to anchor a
    // popup to. A transient 1x1 invisible View is placed in the parent FrameLayout at the pressed
    // icon's resting cell and removed again on dismiss/detach. It uses the BASE rect (the cell the
    // icon returns to), not the live magnified rect, so it does not depend on animation state.
    private var mQuickActionsAnchor: View? = null
    private var mQuickActionsMenu: PopupMenu? = null
    private var mIsDetachingFromWindow = false

    @androidx.annotation.VisibleForTesting
    internal val quickActionsAnchorForTest: View? get() = mQuickActionsAnchor

    @androidx.annotation.VisibleForTesting
    internal val quickActionsMenuForTest: PopupMenu? get() = mQuickActionsMenu

    /** Returns the new anchor, or null when this view has no FrameLayout parent or icon bounds. */
    private fun attachQuickActionsAnchor(index: Int): View? {
        removeQuickActionsAnchor()
        val frame = parent as? FrameLayout ?: return null
        val bounds = Rect()
        if (!getAppBounds(index, bounds)) return null
        return PointAnchor.attach(
            frame,
            left + bounds.centerX(),
            top + bounds.bottom,
            FrameLayout.LayoutParams(PointAnchor.SIZE_PX, PointAnchor.SIZE_PX)
        ).also { mQuickActionsAnchor = it }
    }

    private fun removeQuickActionsAnchor() {
        val anchor = mQuickActionsAnchor ?: return
        mQuickActionsAnchor = null
        PointAnchor.remove(anchor, deferUntilParentFinishes = mIsDetachingFromWindow)
    }
    private val mLongPressHandler = Handler(Looper.getMainLooper())
    private val mLongPressRunnable = Runnable {
        if (shouldTriggerLongPress(mLongPressArmed, mMoving, mSelectIndex)) {
            mLongPressTriggered = true
            if (!LensPhysicsPolicy.shouldReduceLensMotion(context)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            showAppOptionsAtIndex(mSelectIndex)
        } else if (onEmptySpaceLongPressListener != null
            && shouldTriggerEmptySpaceLongPress(mLongPressArmed, mMoving, mSelectIndex)
        ) {
            mLongPressTriggered = true
            if (!LensPhysicsPolicy.shouldReduceLensMotion(context)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            onEmptySpaceLongPressListener?.onEmptySpaceLongPress(mTouchDownX, mTouchDownY)
        }
    }

    /** See [OnEmptySpaceLongPressListener]. Null (the default) keeps the pre-Phase-3 no-op. */
    var onEmptySpaceLongPressListener: OnEmptySpaceLongPressListener? = null

    // FISH-016: pull-down search. Classified from the fixed ACTION_DOWN point (not the moving
    // mTouchX/Y, which tracks the finger), and deliberately never reads Clean mode, so the
    // gesture cannot behave differently between modes.
    var onSearchSwipeDownListener: OnSearchSwipeDownListener? = null
    private var mTouchDownX = 0f
    private var mTouchDownY = 0f
    private var mSearchSwipeStartedInActivationZone = false
    private var mSearchSwipeTriggered = false

    private fun resetSearchSwipeState() {
        mSearchSwipeStartedInActivationZone = false
        mSearchSwipeTriggered = false
    }
    private var mUtilSettings: UtilSettings? = null
    private var mWorkspaceBackgroundDrawable: NinePatchDrawable? = null
    // UI-019: system-bar avoidance is now owned by ActHome.applyHomeColumnInsets (margins on this
    // view's own layout params), not here - this used to also carve out systemBars/displayCutout
    // internally via its own OnApplyWindowInsetsListener, double-reserving the same space and
    // firing invalidate() on every insets dispatch (visible top/bottom gaps and flicker).
    private val mInsets = Rect(0, 0, 0, 0)

    // PERF-002: de-dupes concurrent reload requests for the same evicted icon - onDraw runs
    // every frame, so without this the same cache miss would spawn a new coroutine per frame.
    private val mIconReloadsInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // PERF-001: grid geometry and each cell's base rect are cached in LensGridCache (unit-tested
    // on its own in LensGridCacheTest) since they don't depend on touch position; mScratchRect is
    // reused for the per-cell fisheye shift/scale math so that no longer allocates a RectF per
    // cell per frame either.
    private val mGridCache = LensGridCache()
    private val mScratchRect = RectF()

    // FISH-007: depth-of-field. The user toggle (UtilSettings.KEY_DEPTH_OF_FIELD, off by default)
    // and reduced-motion are sampled once per lens show/hide (LensAnimation), never per frame.
    // The @VisibleForTesting fields expose the last frame's decision to tests.
    private var mReduceMotion = false
    private val mDofLayers: DepthOfFieldLayers? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            DepthOfFieldLayers(resources.displayMetrics.density)
        } else {
            null
        }
    @androidx.annotation.VisibleForTesting
    internal var depthOfFieldEnabled = UtilSettings.DEFAULT_DEPTH_OF_FIELD
    @androidx.annotation.VisibleForTesting
    internal var dofLastMode = DepthOfField.Mode.OFF
    @androidx.annotation.VisibleForTesting
    internal var dofLastTransition = 0f
    @androidx.annotation.VisibleForTesting
    internal val dofBandCounts = IntArray(DepthOfField.BLUR_BAND_COUNT + 1)

    /** Puts the lens in a given drag state without driving the (unattached-view-unfriendly) Animation. */
    @androidx.annotation.VisibleForTesting
    internal fun setLensStateForTest(touchX: Float, touchY: Float, animationMultiplier: Float, reduceMotion: Boolean) {
        mTouchX = touchX
        mTouchY = touchY
        mAnimationMultiplier = animationMultiplier
        mReduceMotion = reduceMotion
    }

    /** FISH-EXPORT: called by PolaroidExportHelper.exportAsync right before it draws this view
     *  into an offscreen bitmap, so a live touch/pinch never bakes distortion or the pinch HUD
     *  (drawPinchHud, gated on gestureState) into the exported snapshot. Deliberately narrower
     *  than setLensStateForTest above (that one also drives animation/reduceMotion state for a
     *  different testing purpose this export path has no reason to touch). */
    internal fun resetToIdleForExport() {
        resetTouchState()
    }

    /** BUG-019: a ViewPager2 page detach must not leave stale touch/selection/gesture state behind
     *  for the next attach of this SAME instance (pager recycles rather than recreating views) —
     *  otherwise a re-attached page can redraw mid-gesture or with a stale selected cell highlighted. */
    private fun resetTouchState() {
        mTouchX = -Float.MAX_VALUE
        mTouchY = -Float.MAX_VALUE
        gestureState = LensGestureState.IDLE
        mSelectIndex = -1
        mRectToSelect = null
        mMoving = false
        mLongPressArmed = false
        mMustVibrate = true
    }

    @androidx.annotation.VisibleForTesting
    internal val selectedIndexForTest: Int get() = mSelectIndex

    @androidx.annotation.VisibleForTesting
    internal fun selectedRectForTest(): RectF? = mRectToSelect?.let { RectF(it) }

    private var mDrawType: DrawType? = null
    fun setDrawType(drawType: DrawType?) {
        mDrawType = drawType
        invalidate()
    }

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context, attrs, defStyle
    ) {
        init()
    }

    // A11Y-001: Virtual view accessibility helper for TalkBack and D-pad/keyboard navigation
    private var mAccessibilityHelper: LensAccessibilityHelper? = null

    @androidx.annotation.VisibleForTesting
    internal fun getAccessibilityHelper(): LensAccessibilityHelper? = mAccessibilityHelper

    fun setApps(apps: ArrayList<App>?) {
        mSourceApps = apps?.let(::ArrayList)
        applySmartFocusArrangement(force = true)
    }

    fun refreshSmartFocus() {
        applySmartFocusArrangement(force = true)
    }

    private fun applySmartFocusArrangement(force: Boolean = false) {
        val source = mSourceApps ?: arrayListOf()
        val grid = mGridCache.grid
        val cols = grid?.itemCountHorizontal ?: -1
        val rows = grid?.itemCountVertical ?: -1
        if (!force && cols == mSmartFocusCols && rows == mSmartFocusRows) return

        mSmartFocusCols = cols
        mSmartFocusRows = rows
        val enabled = mUtilSettings?.isSmartFocusBias(lensId)
            ?: UtilSettings.DEFAULT_SMART_FOCUS_BIAS
        mApps = if (enabled && cols > 0 && rows > 0) {
            SmartFocusArranger.arrange(source, cols, rows, smartFocusEnabled = true)
        } else {
            ArrayList(source)
        }
        mAccessibilityHelper?.invalidateRoot()
        invalidate()
    }

    fun setPackageManager(packageManager: PackageManager?) {
        mPackageManager = packageManager
    }

    fun getAppBounds(index: Int, outRect: Rect): Boolean {
        val baseRects = mGridCache.baseRects
        if (index in baseRects.indices) {
            val r = baseRects[index]
            outRect.set(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            return true
        }
        return false
    }

    fun launchAppAtIndex(index: Int) {
        val list = mApps ?: return
        if (index in list.indices && mPackageManager != null) {
            val app = list[index]
            val bounds = Rect()
            getAppBounds(index, bounds)
            UtilApp.launchComponent(
                context,
                app.packageName.toString(),
                app.label.toString(),
                app.name.toString(),
                this,
                bounds
            )
        }
    }

    /**
     * UI-022: shared quick-actions entry point for BOTH the touch long-press gesture and the
     * accessibility (TalkBack) long-click path (`LensAccessibilityHelper.onAppLongClicked`) -
     * one path, so a non-gesture accessible route always reaches the exact same actions a
     * touch long-press does (this project's A11Y-001 standard).
     */
    fun showAppOptionsAtIndex(index: Int): Boolean {
        val list = mApps ?: return false
        if (index !in list.indices) return false
        return showQuickActionsMenu(list[index], index)
    }

    /**
     * UI-022: reuses the exact same menu resource, intents and organization persistence
     * `SearchResultAdapter`'s row long-press already established (SEARCH-003) - no new intent
     * construction or pin logic duplicated a third time. The whole construct-inflate-show
     * sequence is one try/catch, not just `.show()`: a stray long-press firing while this
     * view's context can't resolve the popup's theme (e.g. mid-detach, or - found live while
     * testing this very story - a non-Activity context with no Material3 theme applied) must
     * degrade to "no menu appears" rather than crash the app.
     *
     * UI-025: anchored at the pressed icon's cell via [attachQuickActionsAnchor]; falls back to
     * `this` + [Gravity.CENTER] when no anchor can be made (unattached view, non-FrameLayout parent).
     */
    private fun showQuickActionsMenu(app: App, index: Int): Boolean {
        return try {
            // A previous menu's dismiss listener removes the previous anchor; dismiss it first so
            // that listener can never remove the anchor created for this press.
            mQuickActionsMenu?.dismiss()
            val anchor = attachQuickActionsAnchor(index)
            val wrapper = ContextThemeWrapper(context, R.style.PopupMenuTheme)
            val popupMenu = PopupMenu(
                wrapper,
                anchor ?: this,
                if (anchor != null) Gravity.NO_GRAVITY else Gravity.CENTER
            )
            popupMenu.inflate(R.menu.menu_search_result)
            popupMenu.menu.findItem(R.id.menuItemUnpin).isVisible = app.pinnedZone != PinnedZone.NONE
            popupMenu.setForceShowIcon(true)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menuItemElementAppInfo -> {
                        startQuickActionIntent(UtilApp.appInfoIntent(app.packageName.toString()))
                        true
                    }
                    R.id.menuItemElementUninstall -> {
                        startQuickActionIntent(UtilApp.uninstallIntent(app.packageName.toString()))
                        true
                    }
                    R.id.menuItemPinStart -> {
                        pinQuickAction(app, PinnedZone.START)
                        true
                    }
                    R.id.menuItemPinEnd -> {
                        pinQuickAction(app, PinnedZone.END)
                        true
                    }
                    R.id.menuItemUnpin -> {
                        pinQuickAction(app, PinnedZone.NONE)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.setOnDismissListener {
                if (mQuickActionsMenu === popupMenu) {
                    removeQuickActionsAnchor()
                    mQuickActionsMenu = null
                }
            }
            mQuickActionsMenu = popupMenu
            popupMenu.show()
            true
        } catch (_: Exception) {
            removeQuickActionsAnchor()
            mQuickActionsMenu = null
            false
        }
    }

    private fun startQuickActionIntent(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun pinQuickAction(app: App, zone: PinnedZone) {
        AppPersistent.setOrganization(
            app.packageName.toString(),
            app.name.toString(),
            app.isFavorite,
            app.folderName,
            zone,
            app.lensId
        )
        context.sendBroadcast(Intent(context, BroadcastReceivers.AppsEditedReceiver::class.java))
        Toast.makeText(context, R.string.organization_saved, Toast.LENGTH_SHORT).show()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (mAccessibilityHelper?.dispatchHoverEvent(event) == true) {
            return true
        }
        return super.dispatchHoverEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mAccessibilityHelper?.dispatchKeyEvent(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        mAccessibilityHelper?.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    /**
     * FEAT-004: Recalculate grid geometry and accessibility bounds on view resize
     * (e.g. orientation rotation, multi-window split-screen, foldable folding/unfolding).
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            mGridCache.clear()
            mAccessibilityHelper?.invalidateRoot()
            invalidate()
        }
    }

    private fun installAccessibilityHelper() {
        mAccessibilityHelper = LensAccessibilityHelper(
            host = this,
            appProvider = { mApps },
            rectProvider = { index, outRect -> getAppBounds(index, outRect) },
            onAppClicked = { index -> launchAppAtIndex(index) },
            onAppLongClicked = { index -> showAppOptionsAtIndex(index) },
            // UI-024: TalkBack must honor the same toggle the visual badge paths do.
            showBadgesSettingProvider = {
                mUtilSettings?.let {
                    shouldShowNotificationBadges(
                        it.getBoolean(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES),
                        it.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE)
                    )
                } == true
            }
        )
        ViewCompat.setAccessibilityDelegate(this, mAccessibilityHelper)
    }

    /**
     * A ViewPager2 page is a RecyclerView item: swiping away detaches it and swiping back attaches
     * the SAME instance again, without running the constructor. [onDetachedFromWindow] frees this
     * view's references, so they have to be rebuilt here or the page comes back blank (onDraw finds
     * its settings null) and TalkBack loses its virtual nodes. Only what detach nulled is rebuilt,
     * so a first attach (everything still set by [init]) is untouched.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mUtilSettings == null) {
            mUtilSettings = UtilSettings(context)
        }
        if (mAccessibilityHelper == null) {
            installAccessibilityHelper()
        }
        // Detach nulls this too and only ActHome.bindLensView sets it; without it a re-attached
        // page draws but a tap on an icon launches nothing.
        if (mPackageManager == null) {
            mPackageManager = context.packageManager
        }
        // Detach also nulls the displayed list; rebuild it from the surviving source so the page
        // does not stay blank until the pager happens to call setApps again.
        if (mApps == null && mSourceApps != null) {
            applySmartFocusArrangement(force = true)
        }
        // The grid cache was cleared on detach; the next draw recomputes it from the settings above.
        invalidate()
    }

    private fun init() {
        mApps = ArrayList()
        mDrawType = DrawType.APPS
        setBackgroundColor(ContextCompat.getColor(context, R.color.colorTransparent))
        mUtilSettings = UtilSettings(context)
        setupPaints()
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        installAccessibilityHelper()
        isFocusable = true

        // FISH-009: Live pinch gesture detector for real-time curvature adjustment
        mScaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (gestureState != LensGestureState.PINCHING) return false
                val factor = detector.scaleFactor
                if (factor.isNaN() || factor <= 0f) return false
                val current = liveDistortionFactor
                    ?: mUtilSettings?.getDistortionFactor(lensId)
                    ?: UtilSettings.DEFAULT_DISTORTION_FACTOR
                val newDistortion = calculatePinchDistortion(current, factor)
                liveDistortionFactor = newDistortion
                onCurvatureAdjustedListener?.onCurvatureAdjusted(newDistortion, false)
                invalidate()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                liveDistortionFactor?.let(::reportPinchFinished)
            }
        })
    }

    private fun setupPaints() {
        mPaintIcons = Paint()
        mPaintIcons?.apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            isFilterBitmap = true
            isDither = true
        }

        mPaintCircles = Paint()
        mPaintCircles?.apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.colorCircles)
        }

        mPaintTouchSelection = Paint()
        mPaintTouchSelection?.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            mUtilSettings?.let {
                color = it.getString(UtilSettings.KEY_HIGHLIGHT_COLOR)?.toColorInt() ?: 0
            }
            strokeWidth = resources.getDimension(R.dimen.stroke_width_touch_selection)
        }

        mPaintText = Paint()
        mPaintText?.apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.colorWhite)
            textSize = resources.getDimension(R.dimen.text_size_lens)
            textAlign = Paint.Align.CENTER
        }

        mPaintNewAppTag = Paint()
        mPaintNewAppTag?.apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            mUtilSettings?.let {
                color = it.getString(UtilSettings.KEY_HIGHLIGHT_COLOR)?.toColorInt() ?: 0
            }
            isDither = true
            setShadowLayer(
                resources.getDimension(R.dimen.shadow_text),
                resources.getDimension(R.dimen.shadow_text),
                resources.getDimension(R.dimen.shadow_text),
                ContextCompat.getColor(context, R.color.colorShadow)
            )
        }

        mPaintBadgeBackground = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.colorPrimary)
        }

        mPaintBadgeText = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = resources.getDimension(R.dimen.text_size_notification_badge)
            typeface = Typeface.DEFAULT_BOLD
        }

        mPaintHudBackground = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.argb(200, 30, 30, 30)
        }

        mPaintHud = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.WHITE
            textSize = resources.displayMetrics.scaledDensity * 16f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mDrawType == DrawType.APPS) {
            mUtilSettings?.let { us ->
                if (us.backgroundMode == BackgroundMode.COLOR) {
                    canvas.drawColor(us.getString(UtilSettings.KEY_BACKGROUND_COLOR)?.toColorInt() ?: 0)
                }
                mPaintNewAppTag?.color =
                    us.getString(UtilSettings.KEY_HIGHLIGHT_COLOR)?.toColorInt() ?: 0
                drawWorkspaceBackground(canvas)
                mApps?.let {
                    drawGrid(canvas, it.size)
                }
                if (us.getBoolean(UtilSettings.KEY_SHOW_TOUCH_SELECTION)) {
                    drawTouchSelection(canvas)
                }
                if (gestureState == LensGestureState.PINCHING || gestureState == LensGestureState.PINCH_RELEASE) {
                    drawPinchHud(canvas)
                }
            }
        } else if (mDrawType == DrawType.CIRCLES) {
            val mNumberOfCircles = 100
            mTouchX = (width / 2).toFloat()
            mTouchY = (height / 2).toFloat()
            drawGrid(canvas, mNumberOfCircles)
        }
    }

    private fun drawPinchHud(canvas: Canvas) {
        val distortion = liveDistortionFactor ?: return
        val text = String.format(Locale.US, "%.1fx", distortion)
        val textWidth = mPaintHud?.measureText(text) ?: return
        val pillWidth = textWidth + 48f
        val pillHeight = 72f
        val cx = width / 2f
        val top = (mInsets.top + 32f).coerceAtLeast(32f)
        mHudRect.set(cx - pillWidth / 2f, top, cx + pillWidth / 2f, top + pillHeight)
        mPaintHudBackground?.let { canvas.drawRoundRect(mHudRect, 36f, 36f, it) }
        mPaintHud?.let {
            val fontMetrics = it.fontMetrics
            val baseline = mHudRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(text, cx, baseline, it)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mDrawType != DrawType.APPS) return super.onTouchEvent(event)

        val reduceMotion = LensPhysicsPolicy.shouldReduceLensMotion(context)
        if (!reduceMotion && mScaleGestureDetector != null) {
            mScaleGestureDetector?.onTouchEvent(event)
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mPinchReported = false
                // FISH-008 Phase 2: claim the gesture up front so a ViewPager2 ancestor cannot page
                // away on its own slop before this view has seen enough movement to classify it;
                // ACTION_MOVE releases the claim as soon as the drag reads as a horizontal swipe.
                parent?.requestDisallowInterceptTouchEvent(true)
                gestureState = LensGestureState.IDLE
                mTouchX = event.x.coerceAtLeast(0.0f)
                mTouchY = event.y.coerceAtLeast(0.0f)
                mTouchDownX = event.x
                mTouchDownY = event.y
                mSearchSwipeStartedInActivationZone = isInSearchSwipeActivationZone(event.y, height)
                mSearchSwipeTriggered = false
                mSelectIndex = -1
                mMoving = false
                mLongPressTriggered = false
                mLongPressArmed = true
                mLongPressHandler.removeCallbacks(mLongPressRunnable)
                mLongPressHandler.postDelayed(
                    mLongPressRunnable,
                    ViewConfiguration.getLongPressTimeout().toLong()
                )
                invalidate()
                true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger before the trigger means pinch; once search has triggered, the
                // release must still be ours, so a late second finger cannot launch an app.
                if (!mSearchSwipeTriggered) {
                    mSearchSwipeStartedInActivationZone = false
                }
                if (!reduceMotion) {
                    val nextState = resolvePointerDown(gestureState, event.pointerCount)
                    if (nextState == LensGestureState.PINCHING) {
                        gestureState = LensGestureState.PINCHING
                        // FISH-008 Phase 2: a confirmed pinch must never be interrupted by a
                        // ViewPager2 ancestor paging to the next lens.
                        parent?.requestDisallowInterceptTouchEvent(true)
                        mLongPressArmed = false
                        mLongPressHandler.removeCallbacks(mLongPressRunnable)
                        mSelectIndex = -1
                        mRectToSelect = null
                        invalidate()
                        return true
                    }
                }
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureState == LensGestureState.PINCHING || gestureState == LensGestureState.PINCH_RELEASE) {
                    return true
                }
                if (mLongPressTriggered) return true
                val searchDx = event.x - mTouchDownX
                val searchDy = event.y - mTouchDownY
                if (shouldOpenSearchSwipe(
                        mSearchSwipeStartedInActivationZone, searchDx, searchDy,
                        mTouchSlop, mSearchSwipeTriggered
                    )
                ) {
                    mSearchSwipeTriggered = true
                    mLongPressArmed = false
                    mLongPressHandler.removeCallbacks(mLongPressRunnable)
                    mSelectIndex = -1
                    mRectToSelect = null
                    // A drag that already became a pan must be wound down, or the lens stays magnified.
                    if (mMoving) {
                        startAnimation(LensAnimation(false))
                        mMoving = false
                    }
                    onSearchSwipeDownListener?.onSearchSwipeDown()
                    return true
                }
                if (searchDy <= 0f || abs(searchDx) >= searchDy) {
                    mSearchSwipeStartedInActivationZone = false
                }
                val dx = event.x - mTouchX
                val dy = event.y - mTouchY
                // FISH-008 Phase 2: before a pan is confirmed, an unambiguously horizontal drag is
                // a lens page-swipe - release the gesture so the ViewPager2 ancestor can take it.
                if (!mMoving && isHorizontalSwipeIntent(dx, dy, mTouchSlop)) {
                    mLongPressArmed = false
                    mLongPressHandler.removeCallbacks(mLongPressRunnable)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return false
                }
                if (!mMoving && exceedsTouchSlop(dx, dy, mTouchSlop)) {
                    mMoving = true
                    gestureState = LensGestureState.PANNING
                    // FISH-008 Phase 2: only claim the gesture once a pan is actually confirmed -
                    // until then a ViewPager2 ancestor is free to decide (via its own slop/
                    // direction check) that this is a page-swipe instead, exactly as it would for
                    // any other horizontally-scrolling child.
                    parent?.requestDisallowInterceptTouchEvent(true)
                    mLongPressArmed = false
                    mLongPressHandler.removeCallbacks(mLongPressRunnable)
                    val lensShowAnimation = LensAnimation(true)
                    startAnimation(lensShowAnimation)
                }
                if (!mMoving) {
                    return true
                }
                mTouchX = event.x.coerceAtLeast(0.0f)
                mTouchY = event.y.coerceAtLeast(0.0f)
                invalidate()
                true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (!reduceMotion) {
                    val remaining = event.pointerCount - 1
                    gestureState = resolvePointerUp(gestureState, remaining)
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                mLongPressArmed = false
                mLongPressHandler.removeCallbacks(mLongPressRunnable)
                val searchTriggered = mSearchSwipeTriggered
                resetSearchSwipeState()
                if (searchTriggered) {
                    gestureState = LensGestureState.IDLE
                    mSelectIndex = -1
                    mTouchX = -Float.MAX_VALUE
                    mTouchY = -Float.MAX_VALUE
                    if (mMoving) {
                        startAnimation(LensAnimation(false))
                        mMoving = false
                    }
                    invalidate()
                    return true
                }
                if (gestureState == LensGestureState.PINCHING || gestureState == LensGestureState.PINCH_RELEASE) {
                    gestureState = LensGestureState.IDLE
                    mSelectIndex = -1
                    mTouchX = -Float.MAX_VALUE
                    mTouchY = -Float.MAX_VALUE
                    liveDistortionFactor?.let(::reportPinchFinished)
                    invalidate()
                    return true
                }
                gestureState = LensGestureState.IDLE
                if (mLongPressTriggered) {
                    mLongPressTriggered = false
                    return true
                }
                performLaunchVibration()
                if (mMoving) {
                    val lensHideAnimation = LensAnimation(false)
                    startAnimation(lensHideAnimation)
                    mMoving = false
                } else {
                    if (shouldAllowAppLaunch(gestureState)) {
                        launchApp()
                    }
                }
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                mLongPressArmed = false
                mLongPressTriggered = false
                mLongPressHandler.removeCallbacks(mLongPressRunnable)
                resetSearchSwipeState()
                gestureState = LensGestureState.IDLE
                if (mMoving) {
                    val lensHideAnimation = LensAnimation(false)
                    startAnimation(lensHideAnimation)
                    mMoving = false
                } else {
                    mTouchX = -Float.MAX_VALUE
                    mTouchY = -Float.MAX_VALUE
                    invalidate()
                }
                true
            }

            else -> super.onTouchEvent(event)
        }
    }

    private fun drawWorkspaceBackground(canvas: Canvas) {
        val rect = Rect(0, 0, width, height)
        mWorkspaceBackgroundDrawable?.apply {
            bounds = rect
            draw(canvas)
        }
    }

    private fun drawTouchSelection(canvas: Canvas) {
        mPaintTouchSelection?.let {
            canvas.drawCircle(
                mTouchX,
                mTouchY,
                resources.getDimension(R.dimen.radius_touch_selection),
                it
            )
        }
    }

    private fun drawGrid(canvas: Canvas, itemCount: Int) {
        val us = mUtilSettings ?: return
        val iconSizeDp = us.getIconSize(lensId)
        val distortionFactor = liveDistortionFactor ?: us.getDistortionFactor(lensId)
        val scaleFactor = us.getFloat(UtilSettings.KEY_SCALE_FACTOR)
        // UI-024: one setting read per frame, not per cell - drawAppIcon() is called once per
        // visible icon inside the loop below, which runs continuously while dragging.
        mShowNotificationBadgesThisFrame = shouldShowNotificationBadges(
            us.getBoolean(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES),
            us.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE)
        )

        val grid = mGridCache.getOrCompute(
            context,
            width - (mInsets.left + mInsets.right),
            height - (mInsets.top + mInsets.bottom),
            itemCount,
            iconSizeDp,
            mInsets
        )
        if (grid.itemCountHorizontal != mSmartFocusCols || grid.itemCountVertical != mSmartFocusRows) {
            applySmartFocusArrangement()
        }
        val baseRects = mGridCache.baseRects
        mInsideRect = false
        var selectIndex = -1
        mRectToSelect = null
        val animationMultiplier: Float = if (mDrawType == DrawType.APPS) {
            mAnimationMultiplier
        } else {
            1.0f
        }
        // FISH-007: one mode/transition decision per frame; per-cell work below is arithmetic only.
        val dofTransition = DepthOfField.transition(animationMultiplier, mReduceMotion)
        val dofMode = DepthOfField.renderMode(
            depthOfFieldEnabled,
            mDrawType == DrawType.APPS && mTouchX >= 0 && mTouchY >= 0 && dofTransition > 0f,
            android.os.Build.VERSION.SDK_INT,
            canvas.isHardwareAccelerated
        )
        dofLastMode = dofMode
        dofLastTransition = dofTransition
        dofBandCounts.fill(0)
        val dofLayers = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && dofMode == DepthOfField.Mode.BLUR) mDofLayers else null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            dofLayers?.begin(width, height)
        }
        for (currentIndex in baseRects.indices) {
            if (currentIndex >= grid.itemCount && mDrawType != DrawType.CIRCLES) continue
            val baseRect = baseRects[currentIndex]
            mScratchRect.set(baseRect)
            val rect = mScratchRect
            if (mTouchX >= 0 && mTouchY >= 0) {
                val shiftedCenterX = UtilCalculator.shiftPoint(
                    mTouchX,
                    baseRect.centerX(),
                    width.toFloat(),
                    animationMultiplier,
                    distortionFactor
                )
                val shiftedCenterY = UtilCalculator.shiftPoint(
                    mTouchY,
                    baseRect.centerY(),
                    height.toFloat(),
                    animationMultiplier,
                    distortionFactor
                )
                val scaledCenterX = UtilCalculator.scalePoint(
                    mTouchX,
                    baseRect.centerX(),
                    baseRect.width(),
                    width.toFloat(),
                    animationMultiplier,
                    scaleFactor,
                    distortionFactor
                )
                val scaledCenterY = UtilCalculator.scalePoint(
                    mTouchY,
                    baseRect.centerY(),
                    baseRect.height(),
                    height.toFloat(),
                    animationMultiplier,
                    scaleFactor,
                    distortionFactor
                )
                val newSize = UtilCalculator.calculateSquareScaledSize(
                    scaledCenterX,
                    shiftedCenterX,
                    scaledCenterY,
                    shiftedCenterY
                )
                if (distortionFactor > 0.0f && scaleFactor > 0.0f) {
                    UtilCalculator.calculateRect(mScratchRect, shiftedCenterX, shiftedCenterY, newSize)
                } else if (distortionFactor > 0.0f && scaleFactor == 0.0f) {
                    UtilCalculator.calculateRect(mScratchRect, shiftedCenterX, shiftedCenterY, baseRect.width())
                }

                if (UtilCalculator.isInsideRect(mTouchX, mTouchY, rect)) {
                    mInsideRect = true
                    selectIndex = currentIndex
                    mRectToSelect = RectF(rect)
                }
            }
            if (mDrawType == DrawType.APPS) {
                // FISH-007: blur only picks the target canvas/alpha - rect and hit-test above are untouched.
                val dofIntensity = if (dofMode == DepthOfField.Mode.OFF) 0f else DepthOfField.intensity(
                    DepthOfField.focusDistance(
                        mTouchX, mTouchY, baseRect.centerX(), baseRect.centerY(),
                        width.toFloat(), height.toFloat()
                    ),
                    dofTransition
                )
                val dofBand = DepthOfField.band(dofIntensity)
                dofBandCounts[dofBand]++
                if (dofMode == DepthOfField.Mode.ALPHA) {
                    mPaintIcons?.alpha = DepthOfField.fallbackAlpha(dofIntensity)
                }
                val iconCanvas = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    dofLayers?.canvas(dofBand) ?: canvas
                } else {
                    canvas
                }
                drawAppIcon(iconCanvas, rect, currentIndex)
            } else if (mDrawType == DrawType.CIRCLES) {
                drawCircle(canvas, rect)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            dofLayers?.endAndDraw(canvas)
        }
        mPaintIcons?.alpha = DepthOfField.OPAQUE_ALPHA
        mMustVibrate = if (selectIndex >= 0) {
            selectIndex != mSelectIndex
        } else {
            false
        }
        if (!mAnimationHiding) {
            mSelectIndex = selectIndex
        }
        if (mDrawType == DrawType.APPS) {
            performHoverVibration()
        }
        if (mRectToSelect != null && mDrawType == DrawType.APPS && mApps != null && mSelectIndex >= 0) {
            drawAppName(canvas, mRectToSelect)
        }
    }

    private fun drawAppIcon(
        canvas: Canvas,
        rect: RectF,
        index: Int,
    ) {
        mApps?.let { list ->
            if (index < list.size) {
                val app = list[index]
                // Fetch icon from cache on-the-fly (Fix 5.1)
                // CORE-002: keyed by iconCacheKey, not packageName (see BitmapCache.buildKey)
                val appIcon = com.mckimquyen.app.RAppsSingleton.instance.getAppIcon(app.iconCacheKey)
                
                if (appIcon != null && !appIcon.isRecycled) {
                    val src = Rect(0, 0, appIcon.width, appIcon.height)
                    canvas.drawBitmap(appIcon, src, rect, mPaintIcons)
                } else {
                    requestIconReload(app)
                }

                if (app.installDate >= System.currentTimeMillis() - UtilSettings.SHOW_NEW_APP_TAG_DURATION
                    && app.openCount == 0L
                ) {
                    drawNewAppTag(canvas, rect)
                }

                if (shouldDrawNotificationBadge(mShowNotificationBadgesThisFrame, app.notificationCount)) {
                    drawNotificationBadge(canvas, rect, app.notificationCount)
                }
            }
        }
    }

    /**
     * PERF-002: BitmapCache trims/clears entries under memory pressure with no other source
     * of truth for the bitmap (App.icon is nulled out after the initial load to avoid holding
     * it twice - see TaskSortApps), so a cache miss here must reload from PackageManager/the
     * active icon pack instead of leaving that grid cell permanently blank.
     */
    private fun requestIconReload(app: App) {
        val iconCacheKey = app.iconCacheKey
        if (iconCacheKey.isEmpty() || !mIconReloadsInFlight.add(iconCacheKey)) return
        val application = context?.applicationContext as? android.app.Application ?: run {
            mIconReloadsInFlight.remove(iconCacheKey)
            return
        }
        com.mckimquyen.app.ApplicationScope.scope.launch {
            try {
                val icon = UtilApp.loadSingleAppIcon(application, app.packageName.toString(), app.iconResId)
                if (icon != null) {
                    com.mckimquyen.app.RAppsSingleton.instance.setAppIcon(iconCacheKey, icon)
                    invalidate()
                }
            } catch (e: Exception) {
                // A background icon reload must never crash the launcher; that grid cell just
                // stays blank until the next full app-list refresh, same as before PERF-002.
                android.util.Log.e("LensView", "Icon reload failed for $iconCacheKey", e)
            } finally {
                mIconReloadsInFlight.remove(iconCacheKey)
            }
        }
    }

    private fun drawCircle(
        canvas: Canvas,
        rect: RectF,
    ) {
        mPaintCircles?.let {
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2.0f, it)
        }
    }

    private fun drawAppName(
        canvas: Canvas,
        rect: RectF?,
    ) {
        mUtilSettings?.let { us ->
            if (shouldDrawAppNameLabel(
                    us.getBoolean(UtilSettings.KEY_SHOW_NAME_APP_HOVER),
                    us.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE),
                    mMoving
                )
            ) {
                mApps?.let { list ->
                    rect?.let { r ->
                        mPaintText?.let { p ->
                            canvas.drawText(
                                list[mSelectIndex].label as String,
                                r.centerX(),
                                r.top - resources.getDimension(R.dimen.margin_lens_text),
                                p
                            )
                        }
                    }
                }
            }
        }
    }

    private fun drawNewAppTag(
        canvas: Canvas,
        rect: RectF,
    ) {
        mUtilSettings?.let { us ->
            if (shouldDrawNewAppTag(
                    us.getBoolean(UtilSettings.KEY_SHOW_NEW_APP_TAG),
                    us.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE)
                )
            ) {
                mPaintNewAppTag?.let { p ->
                    canvas.drawCircle(
                        rect.centerX(),
                        rect.bottom + resources.getDimension(R.dimen.margin_new_app_tag),
                        resources.getDimension(R.dimen.radius_new_app_tag),
                        p
                    )
                }
            }
        }
    }

    private fun drawNotificationBadge(canvas: Canvas, rect: RectF, count: Int) {
        badgeDrawCallCount++
        val radius = resources.getDimension(R.dimen.radius_notification_badge)
        // Badge overlaps the icon's top-right corner instead of covering its center: half inside,
        // half outside, matching the Apps-tab TextView's negative-margin placement.
        val cx = rect.right
        val cy = rect.top
        mPaintBadgeBackground?.let { canvas.drawCircle(cx, cy, radius, it) }
        mPaintBadgeText?.let { p ->
            val label = com.mckimquyen.util.NotificationBadgeFormatter.format(count)
            canvas.drawText(label, cx, cy - (p.ascent() + p.descent()) / 2, p)
        }
    }

    private fun performIntensityHaptic() {
        val level = mUtilSettings?.getHapticIntensity() ?: HapticIntensity.DEFAULT

        // API 26+: use VibrationEffect for duration/amplitude control
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = vibratorProvider?.invoke()
                ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

            if (vibrator != null && vibrator.hasVibrator()) {
                try {
                    val effect = VibrationEffect.createOneShot(
                        level.duration,
                        level.amplitude
                    )
                    vibrator.vibrate(effect)
                    onHapticPerformed?.invoke(level.amplitude) // encode amplitude in seam for test verification
                } catch (e: Exception) {
                    // Gracefully handle any vibrator errors
                }
            }
        } else {
            // API 25: fallback to performHapticFeedback (acceptable baseline per FISH-017)
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onHapticPerformed?.invoke(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun performHoverVibration() {
        if (mInsideRect) {
            if (mMustVibrate) {
                mUtilSettings?.let { us ->
                    if (us.getBoolean(UtilSettings.KEY_VIBRATE_APP_HOVER)
                        && !mAnimationHiding
                        && !LensPhysicsPolicy.shouldReduceLensMotion(context)
                    ) {
                        performIntensityHaptic()
                    }
                }
                mMustVibrate = false
            }
        } else {
            mMustVibrate = true
        }
    }

    private fun performLaunchVibration() {
        if (mInsideRect) {
            if (mUtilSettings?.getBoolean(UtilSettings.KEY_VIBRATE_APP_LAUNCH) == true
                && !LensPhysicsPolicy.shouldReduceLensMotion(context)
            ) {
                performIntensityHaptic()
            }
        }
    }

    private fun launchApp() {
        mApps?.let { list ->
            if (mPackageManager != null && mSelectIndex >= 0) {
                val bounds = mRectToSelect?.let {
                    Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
                }
                UtilApp.launchComponent(
                    context,
                    list[mSelectIndex].packageName as String?,
                    list[mSelectIndex].label as String?,
                    list[mSelectIndex].name as String?,
                    this,
                    bounds
                )
            }
        }
    }

    private inner class LensAnimation(private val mShow: Boolean) : Animation() {
        init {
            interpolator = AccelerateDecelerateInterpolator()
            mReduceMotion = LensPhysicsPolicy.shouldReduceLensMotion(context)
            depthOfFieldEnabled = mUtilSettings?.getBoolean(UtilSettings.KEY_DEPTH_OF_FIELD)
                ?: UtilSettings.DEFAULT_DEPTH_OF_FIELD
            mUtilSettings?.let {
                duration = if (mReduceMotion) {
                    0L
                } else {
                    it.getLong(UtilSettings.KEY_ANIMATION_TIME)
                }
            }
            setAnimationListener(object : AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    if (!mShow) {
                        mAnimationHiding = true
                        mPaintText?.clearShadowLayer()
                    } else {
                        mAnimationHiding = false
                    }
                }

                override fun onAnimationEnd(animation: Animation) {
                    if (!mShow) {
                        launchApp()
                        mTouchX = -Float.MAX_VALUE
                        mTouchY = -Float.MAX_VALUE
                        mAnimationHiding = false
                    } else {
                        mPaintText?.setShadowLayer(
                            resources.getDimension(R.dimen.shadow_text),
                            resources.getDimension(R.dimen.shadow_text),
                            resources.getDimension(R.dimen.shadow_text),
                            ContextCompat.getColor(context, R.color.colorShadow)
                        )
                    }
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            super.applyTransformation(interpolatedTime, t)
            if (mShow) {
                mAnimationMultiplier = interpolatedTime
                mUtilSettings?.let { us ->
                    mPaintTouchSelection?.color =
                        us.getString(UtilSettings.KEY_HIGHLIGHT_COLOR)?.toColorInt() ?: 0
                }
                mPaintTouchSelection?.alpha = (255.0f * interpolatedTime).toInt()
                mPaintText?.alpha = (255.0f * interpolatedTime).toInt()
            } else {
                mAnimationMultiplier = 1.0f - interpolatedTime
                mUtilSettings?.let { us ->
                    mPaintTouchSelection?.color =
                        us.getString(UtilSettings.KEY_HIGHLIGHT_COLOR)?.toColorInt() ?: 0
                }
                mPaintTouchSelection?.alpha = (255.0f * (1.0f - interpolatedTime)).toInt()
                mPaintText?.alpha = (255.0f * (1.0f - interpolatedTime)).toInt()
            }
            postInvalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mIsDetachingFromWindow = true
        try {
            // UI-022: a pending long-press Runnable must never fire (and show a PopupMenu) after
            // this view is detached/destroyed.
            mLongPressHandler.removeCallbacks(mLongPressRunnable)
            resetSearchSwipeState()
            // UI-025: an open icon menu is a window owned by this view; it and its anchor must not
            // outlive the view (rotation, page recycle, Activity destroy).
            mQuickActionsMenu?.dismiss()
            removeQuickActionsAnchor()
            mQuickActionsMenu = null
            // Fix BUG-05: Cancel animation đang chạy để tránh AnimationListener callback
            // vào LensView (inner class giữ outer reference) sau khi view bị detach/destroy
            clearAnimation()
            // BUG-019: a stale touch/selection/gesture state must not survive into the next attach
            // of this SAME instance, and the accessibility delegate this view installed on itself
            // must not outlive it either.
            resetTouchState()
            // ViewCompat.setAccessibilityDelegate(this, null) does NOT clear it: AndroidX swaps a
            // previously-installed compat delegate for a neutral no-op one instead of a real null,
            // so hasAccessibilityDelegate() stays true. Call the framework setter directly.
            setAccessibilityDelegate(null)
            // Null toàn bộ references để GC thu hồi
            onHapticPerformed = null
            vibratorProvider = null  // FISH-020: null the test seam
            mApps = null
            mUtilSettings = null
            mPackageManager = null
            mWorkspaceBackgroundDrawable = null
            mAccessibilityHelper = null
            // PERF-001: drop the cached grid/base-rects too, they're only valid for this view instance.
            mGridCache.clear()
            // FISH-007: free the blur layers' GPU display lists.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                mDofLayers?.discard()
            }
        } finally {
            mIsDetachingFromWindow = false
        }
    }
}
