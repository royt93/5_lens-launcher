package com.mckimquyen.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.NinePatchDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import com.mckimquyen.R
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.DrawType
import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.services.BroadcastReceivers
import com.mckimquyen.util.LensPhysicsPolicy
import com.mckimquyen.util.UtilApp
import com.mckimquyen.util.UtilCalculator
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

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
         * Should the pending long-press Runnable actually act when it fires? False whenever
         * the touch already turned into a pan (`moving`), was released/cancelled before the
         * timeout (`armed` false), or never landed on an icon (`selectIndex < 0`) - a real
         * state check, not just "the timer elapsed".
         */
        @androidx.annotation.VisibleForTesting
        internal fun shouldTriggerLongPress(armed: Boolean, moving: Boolean, selectIndex: Int): Boolean =
            armed && !moving && selectIndex >= 0
    }
    private var mPaintIcons: Paint? = null
    private var mPaintCircles: Paint? = null
    private var mPaintTouchSelection: Paint? = null
    private var mPaintText: Paint? = null
    private var mPaintNewAppTag: Paint? = null
    private var mTouchX = -Float.MAX_VALUE
    private var mTouchY = -Float.MAX_VALUE
    private var mInsideRect = false
    private var mRectToSelect: RectF? = RectF(0f, 0f, 0f, 0f)
    private var mMustVibrate = true
    private var mSelectIndex = 0
    private var mApps: ArrayList<App>? = null
    private var mPackageManager: PackageManager? = null
    private var mAnimationMultiplier = 0.0f
    private var mAnimationHiding = false
    private var mTouchSlop = 0f
    private var mMoving = false

    // UI-022: long-press-and-hold quick actions (info/pin/uninstall). Explicit state guards
    // (mLongPressArmed/mMoving/mSelectIndex), not a bare timer alone: the posted Runnable only
    // acts if the touch never crossed the pan threshold and still sits over an icon by the time
    // it fires, and is cancelled outright the instant a real pan starts (ACTION_MOVE past
    // mTouchSlop) or the touch ends/cancels - so an in-progress pan can never be mistaken for a
    // long-press and a long-press can never fight the existing pan/launch gesture.
    private var mLongPressArmed = false
    private var mLongPressTriggered = false
    private val mLongPressHandler = Handler(Looper.getMainLooper())
    private val mLongPressRunnable = Runnable {
        if (shouldTriggerLongPress(mLongPressArmed, mMoving, mSelectIndex)) {
            mLongPressTriggered = true
            if (!LensPhysicsPolicy.shouldReduceLensMotion(context)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            showAppOptionsAtIndex(mSelectIndex)
        }
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
        mApps = apps
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
        return showQuickActionsMenu(list[index])
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
     * ponytail: anchored to `this` (the whole `LensView`), not the pressed icon's own rect -
     * live-verified the menu always opens near the same corner regardless of which icon was
     * pressed, rather than tracking the icon. `LensView` draws every icon on one `Canvas` (no
     * per-icon child View to anchor to), so precise per-icon positioning needs a transient
     * anchor View added to LensView's own parent ViewGroup at the icon's translated rect - real
     * but non-trivial extra plumbing for a purely cosmetic improvement (the menu is reachable
     * and correct either way). Upgrade if real usage shows the fixed position is confusing.
     */
    private fun showQuickActionsMenu(app: App): Boolean {
        return try {
            val wrapper = ContextThemeWrapper(context, R.style.PopupMenuTheme)
            val popupMenu = PopupMenu(wrapper, this, Gravity.CENTER)
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
            popupMenu.show()
            true
        } catch (_: Exception) {
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
            zone
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

    private fun init() {
        mApps = ArrayList()
        mDrawType = DrawType.APPS
        setBackgroundColor(ContextCompat.getColor(context, R.color.colorTransparent))
        mUtilSettings = UtilSettings(context)
        setupPaints()
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        mAccessibilityHelper = LensAccessibilityHelper(
            host = this,
            appProvider = { mApps },
            rectProvider = { index, outRect -> getAppBounds(index, outRect) },
            onAppClicked = { index -> launchAppAtIndex(index) },
            onAppLongClicked = { index -> showAppOptionsAtIndex(index) }
        )
        ViewCompat.setAccessibilityDelegate(this, mAccessibilityHelper)
        isFocusable = true
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
            }
        } else if (mDrawType == DrawType.CIRCLES) {
            val mNumberOfCircles = 100
            mTouchX = (width / 2).toFloat()
            mTouchY = (height / 2).toFloat()
            drawGrid(canvas, mNumberOfCircles)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (mDrawType == DrawType.APPS) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mTouchX = event.x.coerceAtLeast(0.0f)
                    mTouchY = event.y.coerceAtLeast(0.0f)
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

                MotionEvent.ACTION_MOVE -> {
                    if (mLongPressTriggered) return true
                    if (!mMoving && exceedsTouchSlop(event.x - mTouchX, event.y - mTouchY, mTouchSlop)) {
                        mMoving = true
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

                MotionEvent.ACTION_UP -> {
                    mLongPressArmed = false
                    mLongPressHandler.removeCallbacks(mLongPressRunnable)
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
                        launchApp()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mLongPressArmed = false
                    mLongPressTriggered = false
                    mLongPressHandler.removeCallbacks(mLongPressRunnable)
                    mMoving = false
                    super.onTouchEvent(event)
                }

                else -> {
                    super.onTouchEvent(event)
                }
            }
        } else super.onTouchEvent(event)
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
        val iconSizeDp = us.getFloat(UtilSettings.KEY_ICON_SIZE)
        val distortionFactor = us.getFloat(UtilSettings.KEY_DISTORTION_FACTOR)
        val scaleFactor = us.getFloat(UtilSettings.KEY_SCALE_FACTOR)

        val grid = mGridCache.getOrCompute(
            context,
            width - (mInsets.left + mInsets.right),
            height - (mInsets.top + mInsets.bottom),
            itemCount,
            iconSizeDp,
            mInsets
        )
        val baseRects = mGridCache.baseRects
        mInsideRect = false
        var selectIndex = -1
        mRectToSelect = null
        val animationMultiplier: Float = if (mDrawType == DrawType.APPS) {
            mAnimationMultiplier
        } else {
            1.0f
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
                drawAppIcon(canvas, rect, currentIndex)
            } else if (mDrawType == DrawType.CIRCLES) {
                drawCircle(canvas, rect)
            }
        }
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
            if (us.getBoolean(UtilSettings.KEY_SHOW_NAME_APP_HOVER) && mMoving) {
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
            if (us.getBoolean(UtilSettings.KEY_SHOW_NEW_APP_TAG)) {
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

    private fun performHoverVibration() {
        if (mInsideRect) {
            if (mMustVibrate) {
                mUtilSettings?.let { us ->
                    if (us.getBoolean(UtilSettings.KEY_VIBRATE_APP_HOVER)
                        && !mAnimationHiding
                        && !LensPhysicsPolicy.shouldReduceLensMotion(context)
                    ) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
            mUtilSettings?.let {
                duration = if (LensPhysicsPolicy.shouldReduceLensMotion(context)) {
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
        // UI-022: a pending long-press Runnable must never fire (and show a PopupMenu) after
        // this view is detached/destroyed.
        mLongPressHandler.removeCallbacks(mLongPressRunnable)
        // Fix BUG-05: Cancel animation đang chạy để tránh AnimationListener callback
        // vào LensView (inner class giữ outer reference) sau khi view bị detach/destroy
        clearAnimation()
        // Null toàn bộ references để GC thu hồi
        mApps = null
        mUtilSettings = null
        mPackageManager = null
        mWorkspaceBackgroundDrawable = null
        mAccessibilityHelper = null
        // PERF-001: drop the cached grid/base-rects too, they're only valid for this view instance.
        mGridCache.clear()
    }
}
