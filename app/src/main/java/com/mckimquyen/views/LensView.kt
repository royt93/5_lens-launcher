package com.mckimquyen.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.NinePatchDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import androidx.core.content.ContextCompat
import com.mckimquyen.R
import com.mckimquyen.enums.DrawType
import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.util.UtilApp
import com.mckimquyen.util.UtilCalculator
import com.mckimquyen.util.UtilSettings
import java.util.*
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
    private var mRectToSelect: RectF? = RectF(
        /* left = */ 0f,
        /* top = */ 0f,
        /* right = */ 0f,
        /* bottom = */ 0f
    )
    private var mMustVibrate = true
    private var mSelectIndex = 0
    private var mApps: ArrayList<App>? = null
    private var mAppIcons: ArrayList<Bitmap>? = null
    private var mPackageManager: PackageManager? = null
    private var mAnimationMultiplier = 0.0f
    private var mAnimationHiding = false
    private var mTouchSlop = 0f
    private var mMoving = false
    private var mUtilSettings: UtilSettings? = null
    private var mWorkspaceBackgroundDrawable: NinePatchDrawable? = null
    private var mInsets = Rect(
        /* left = */ 0,
        /* top = */ 0,
        /* right = */ 0,
        /* bottom = */ 0
    )

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

    fun setApps(apps: ArrayList<App>?, appIcons: ArrayList<Bitmap>?) {
        mApps = apps
        mAppIcons = appIcons
        invalidate()
    }

    fun setPackageManager(packageManager: PackageManager?) {
        mPackageManager = packageManager
    }

    private fun init() {
        mApps = ArrayList()
        mAppIcons = ArrayList()
        mDrawType = DrawType.APPS
        setBackgroundColor(ContextCompat.getColor(context, R.color.colorTransparent))
        mUtilSettings = UtilSettings(context)
        setupPaints()
//        mWorkspaceBackgroundDrawable =
//            ContextCompat.getDrawable(context, R.drawable.workspace_bg) as NinePatchDrawable?
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }

    @Deprecated("Deprecated in Java")
    override fun fitSystemWindows(insets: Rect): Boolean {
        mInsets = insets
        return true
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
                color = Color.parseColor(it.getString(UtilSettings.KEY_HIGHLIGHT_COLOR))
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
//            typeface = Typeface.createFromAsset(context.assets, "fonts/RobotoCondensed-Regular.ttf")
        }

        mPaintNewAppTag = Paint()
        mPaintNewAppTag?.apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            mUtilSettings?.let {
                color = Color.parseColor(it.getString(UtilSettings.KEY_HIGHLIGHT_COLOR))
            }
            isDither = true
            setShadowLayer(
                /* radius = */ resources.getDimension(R.dimen.shadow_text),
                /* dx = */ resources.getDimension(R.dimen.shadow_text),
                /* dy = */ resources.getDimension(R.dimen.shadow_text),
                /* shadowColor = */ ContextCompat.getColor(context, R.color.colorShadow)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mDrawType == DrawType.APPS) {
            mUtilSettings?.let { us ->
                if (us.getString(UtilSettings.KEY_BACKGROUND) == "Color") {
                    canvas.drawColor(Color.parseColor(us.getString(UtilSettings.KEY_BACKGROUND_COLOR)))
                }
                mPaintNewAppTag?.color =
                    Color.parseColor(us.getString(UtilSettings.KEY_HIGHLIGHT_COLOR))
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
            drawGrid(
                canvas = canvas,
                itemCount = mNumberOfCircles
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (mDrawType == DrawType.APPS) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mTouchX = if (event.x < 0.0f) {
                        0.0f
                    } else {
                        event.x
                    }
                    mTouchY = if (event.y < 0.0f) {
                        0.0f
                    } else {
                        event.y
                    }
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
                    mTouchX = if (event.x < 0.0f) {
                        0.0f
                    } else {
                        event.x
                    }
                    mTouchY = if (event.y < 0.0f) {
                        0.0f
                    } else {
                        event.y
                    }
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
        val rect = Rect(/* left = */ 0, /* top = */ 0, /* right = */ width, /* bottom = */ height)
        mWorkspaceBackgroundDrawable?.apply {
            bounds = rect
            draw(canvas)
        }
    }

    private fun drawTouchSelection(canvas: Canvas) {
        mPaintTouchSelection?.let {
            canvas.drawCircle(
                /* cx = */ mTouchX,
                /* cy = */ mTouchY,
                /* radius = */ resources.getDimension(R.dimen.radius_touch_selection),
                /* paint = */ it
            )
        }
    }

    private fun drawGrid(canvas: Canvas, itemCount: Int) {
        val grid = UtilCalculator.calculateGrid(
            /* context = */ context,
            /* screenWidth = */ width - (mInsets.left + mInsets.right),
            /* screenHeight = */ height - (mInsets.top + mInsets.bottom),
            /* itemCount = */ itemCount
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
                            /* context = */ context,
                            /* lensPosition = */ mTouchX,
                            /* itemPosition = */ rect.centerX(),
                            /* boundary = */ width.toFloat(),
                            /* multiplier = */ animationMultiplier
                        )
                        val shiftedCenterY = UtilCalculator.shiftPoint(
                            /* context = */ context,
                            /* lensPosition = */ mTouchY,
                            /* itemPosition = */ rect.centerY(),
                            /* boundary = */ height.toFloat(),
                            /* multiplier = */ animationMultiplier
                        )
                        val scaledCenterX = UtilCalculator.scalePoint(
                            /* context = */ context,
                            /* lensPosition = */ mTouchX,
                            /* itemPosition = */ rect.centerX(),
                            /* itemSize = */ rect.width(),
                            /* boundary = */ width.toFloat(),
                            /* multiplier = */ animationMultiplier
                        )
                        val scaledCenterY = UtilCalculator.scalePoint(
                            /* context = */ context,
                            /* lensPosition = */ mTouchY,
                            /* itemPosition = */ rect.centerY(),
                            /* itemSize = */ rect.height(),
                            /* boundary = */ height.toFloat(),
                            /* multiplier = */ animationMultiplier
                        )
                        val newSize = UtilCalculator.calculateSquareScaledSize(
                            /* scaledPositionX = */ scaledCenterX,
                            /* shiftedPositionX = */ shiftedCenterX,
                            /* scaledPositionY = */ scaledCenterY,
                            /* shiftedPositionY = */ shiftedCenterY
                        )
                        mUtilSettings?.let { us ->
                            if (us.getFloat(UtilSettings.KEY_DISTORTION_FACTOR) > 0.0f
                                && us.getFloat(UtilSettings.KEY_SCALE_FACTOR) > 0.0f
                            ) {
                                rect = UtilCalculator.calculateRect(
                                    /* newCenterX = */ shiftedCenterX,
                                    /* newCenterY = */ shiftedCenterY,
                                    /* newSize = */ newSize
                                )
                            } else if (us.getFloat(UtilSettings.KEY_DISTORTION_FACTOR) > 0.0f
                                && us.getFloat(UtilSettings.KEY_SCALE_FACTOR) == 0.0f
                            ) {
                                rect = UtilCalculator.calculateRect(
                                    /* newCenterX = */ shiftedCenterX,
                                    /* newCenterY = */ shiftedCenterY,
                                    /* newSize = */ rect.width()
                                )
                            }
                        }

                        if (UtilCalculator.isInsideRect(
                                /* x = */ mTouchX,
                                /* y = */ mTouchY,
                                /* rect = */ rect
                            )
                        ) {
                            mInsideRect = true
                            selectIndex = currentIndex
                            mRectToSelect = rect
                        }
                    }
                    if (mDrawType == DrawType.APPS) {
                        drawAppIcon(
                            canvas = canvas,
                            rect = rect,
                            index = currentIndex
                        )
                    } else if (mDrawType == DrawType.CIRCLES) {
                        drawCircle(
                            canvas = canvas,
                            rect = rect
                        )
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
            drawAppName(
                canvas = canvas,
                rect = mRectToSelect
            )
        }
    }

    private fun drawAppIcon(
        canvas: Canvas,
        rect: RectF,
        index: Int,
    ) {
        mAppIcons?.let { list ->
            if (index < list.size) {
                val appIcon = list[index]
                val src = Rect(
                    /* left = */ 0,
                    /* top = */ 0,
                    /* right = */ appIcon.width,
                    /* bottom = */ appIcon.height
                )
                canvas.drawBitmap(
                    /* bitmap = */ appIcon,
                    /* src = */ src,
                    /* dst = */ rect,
                    /* paint = */ mPaintIcons
                )
                mApps?.let { l ->
                    if (l[index].installDate >= System.currentTimeMillis() - UtilSettings.SHOW_NEW_APP_TAG_DURATION
                        &&
                        AppPersistent.getAppOpenCount(
                            Objects.requireNonNull(l[index].packageName).toString(),
                            Objects.requireNonNull(l[index].name).toString()
                        ) == 0L
                    ) {
                        drawNewAppTag(
                            canvas = canvas,
                            rect = rect
                        )
                    }
                }
            }
        }
    }

    private fun drawCircle(
        canvas: Canvas,
        rect: RectF,
    ) {
        mPaintCircles?.let {
            canvas.drawCircle(
                /* cx = */ rect.centerX(),
                /* cy = */ rect.centerY(),
                /* radius = */ rect.width() / 2.0f,
                /* paint = */ it
            )
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
                                /* text = */ list[mSelectIndex].label as String,
                                /* x = */ r.centerX(),
                                /* y = */ r.top - resources.getDimension(R.dimen.margin_lens_text),
                                /* paint = */ p
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
                        /* cx = */ rect.centerX(),
                        /* cy = */ rect.bottom + resources.getDimension(R.dimen.margin_new_app_tag),
                        /* radius = */ resources.getDimension(R.dimen.radius_new_app_tag),
                        /* paint = */ p
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
                val bounds = if (mRectToSelect == null)
                    null
                else Rect(
                    mRectToSelect!!.left.toInt(),
                    mRectToSelect!!.top.toInt(),
                    mRectToSelect!!.right.toInt(),
                    mRectToSelect!!.bottom.toInt()
                )
                UtilApp.launchComponent(
                    /* context = */ context,
                    /* packageName = */ list[mSelectIndex].packageName as String?,
                    /* label = */ list[mSelectIndex].label as String?,
                    /* name = */ list[mSelectIndex].name as String?,
                    /* view = */ this,
                    /* bounds = */ bounds
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
                            /* radius = */ resources.getDimension(R.dimen.shadow_text),
                            /* dx = */ resources.getDimension(R.dimen.shadow_text),
                            /* dy = */ resources.getDimension(R.dimen.shadow_text),
                            /* shadowColor = */ ContextCompat.getColor(context, R.color.colorShadow)
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
                        Color.parseColor(us.getString(UtilSettings.KEY_HIGHLIGHT_COLOR))
                }
                mPaintTouchSelection?.alpha = (255.0f * interpolatedTime).toInt()
                mPaintText?.alpha = (255.0f * interpolatedTime).toInt()
            } else {
                mAnimationMultiplier = 1.0f - interpolatedTime
                mUtilSettings?.let { us ->
                    mPaintTouchSelection?.color =
                        Color.parseColor(us.getString(UtilSettings.KEY_HIGHLIGHT_COLOR))
                }
                mPaintTouchSelection?.alpha = (255.0f * (1.0f - interpolatedTime)).toInt()
                mPaintText?.alpha = (255.0f * (1.0f - interpolatedTime)).toInt()
            }
            postInvalidate()
        }
    }
}
