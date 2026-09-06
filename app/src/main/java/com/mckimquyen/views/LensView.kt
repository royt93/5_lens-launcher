package com.mckimquyen.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.NinePatchDrawable
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mckimquyen.R
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.DrawType
import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.util.UtilApp
import com.mckimquyen.util.UtilCalculator
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

class LensView : View {
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
    private var mUtilSettings: UtilSettings? = null
    private var mWorkspaceBackgroundDrawable: NinePatchDrawable? = null
    private var mInsets = Rect(0, 0, 0, 0)

    // PERF-002: de-dupes concurrent reload requests for the same evicted icon - onDraw runs
    // every frame, so without this the same cache miss would spawn a new coroutine per frame.
    private val mIconReloadsInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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

    fun setApps(apps: ArrayList<App>?) {
        mApps = apps
        invalidate()
    }

    fun setPackageManager(packageManager: PackageManager?) {
        mPackageManager = packageManager
    }

    private fun init() {
        mApps = ArrayList()
        mDrawType = DrawType.APPS
        setBackgroundColor(ContextCompat.getColor(context, R.color.colorTransparent))
        mUtilSettings = UtilSettings(context)
        setupPaints()
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }

    /**
     * Fix: 1.4 - Migrate từ fitSystemWindows (deprecated) sang WindowInsetsCompat
     * Xử lý system window insets (status bar, navigation bar) để view không bị che khuất
     */
    init {
        // Sử dụng WindowInsetsCompat thay cho fitSystemWindows deprecated
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Lưu insets để sử dụng khi draw
            mInsets = Rect(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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
                    invalidate()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!mMoving && sqrt(
                            (event.x - mTouchX).toDouble().pow(2.0) + (event.y - mTouchY).toDouble()
                                .pow(2.0)
                        ) > mTouchSlop
                    ) {
                        mMoving = true
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

        val grid = UtilCalculator.calculateGrid(
            context,
            width - (mInsets.left + mInsets.right),
            height - (mInsets.top + mInsets.bottom),
            itemCount,
            iconSizeDp
        )
        mInsideRect = false
        var selectIndex = -1
        mRectToSelect = null
        var y = 0.0f
        while (y < grid.itemCountVertical.toFloat()) {
            var x = 0.0f
            while (x < grid.itemCountHorizontal.toFloat()) {
                val currentItem = (y * grid.itemCountHorizontal.toFloat() + (x + 1.0f)).toInt()
                val currentIndex = currentItem - 1
                if (currentItem <= grid.itemCount || mDrawType == DrawType.CIRCLES) {
                    var rect = RectF()
                    rect.left =
                        mInsets.left + (x + 1.0f) * grid.spacingHorizontal + x * grid.itemSize
                    rect.top = mInsets.top + (y + 1.0f) * grid.spacingVertical + y * grid.itemSize
                    rect.right = rect.left + grid.itemSize
                    rect.bottom = rect.top + grid.itemSize
                    val animationMultiplier: Float = if (mDrawType == DrawType.APPS) {
                        mAnimationMultiplier
                    } else {
                        1.0f
                    }
                    if (mTouchX >= 0 && mTouchY >= 0) {
                        val shiftedCenterX = UtilCalculator.shiftPoint(
                            mTouchX,
                            rect.centerX(),
                            width.toFloat(),
                            animationMultiplier,
                            distortionFactor
                        )
                        val shiftedCenterY = UtilCalculator.shiftPoint(
                            mTouchY,
                            rect.centerY(),
                            height.toFloat(),
                            animationMultiplier,
                            distortionFactor
                        )
                        val scaledCenterX = UtilCalculator.scalePoint(
                            mTouchX,
                            rect.centerX(),
                            rect.width(),
                            width.toFloat(),
                            animationMultiplier,
                            scaleFactor,
                            distortionFactor
                        )
                        val scaledCenterY = UtilCalculator.scalePoint(
                            mTouchY,
                            rect.centerY(),
                            rect.height(),
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
                            rect = UtilCalculator.calculateRect(
                                shiftedCenterX,
                                shiftedCenterY,
                                newSize
                            )
                        } else if (distortionFactor > 0.0f && scaleFactor == 0.0f) {
                            rect = UtilCalculator.calculateRect(
                                shiftedCenterX,
                                shiftedCenterY,
                                rect.width()
                            )
                        }

                        if (UtilCalculator.isInsideRect(mTouchX, mTouchY, rect)) {
                            mInsideRect = true
                            selectIndex = currentIndex
                            mRectToSelect = rect
                        }
                    }
                    if (mDrawType == DrawType.APPS) {
                        drawAppIcon(canvas, rect, currentIndex)
                    } else if (mDrawType == DrawType.CIRCLES) {
                        drawCircle(canvas, rect)
                    }
                }
                x += 1.0f
            }
            y += 1.0f
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
            if (mUtilSettings?.getBoolean(UtilSettings.KEY_VIBRATE_APP_LAUNCH) == true) {
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
                duration = it.getLong(UtilSettings.KEY_ANIMATION_TIME)
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
        // Fix BUG-05: Cancel animation đang chạy để tránh AnimationListener callback
        // vào LensView (inner class giữ outer reference) sau khi view bị detach/destroy
        clearAnimation()
        // Null toàn bộ references để GC thu hồi
        mApps = null
        mUtilSettings = null
        mPackageManager = null
        mWorkspaceBackgroundDrawable = null
    }
}
