package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Kiểm tra cấu trúc hot path; test trên Pixel kiểm tra hành vi render/gesture thật. */
class LensRenderingContractTest {
    private val source = File("src/main/java/com/mckimquyen/views/LensView.kt").readText()

    @Test
    fun iconSourceRectIsReusedRatherThanAllocatedPerIcon() {
        val body = source.substringAfter("private fun drawAppIcon(").substringBefore("private fun requestIconReload(")
        assertFalse("không cấp phát Rect trong drawAppIcon", Regex("\\bRect\\(").containsMatchIn(body))
        assertTrue(body.contains("mScratchIconSourceRect.set(0, 0, appIcon.width, appIcon.height)"))
        assertTrue(body.contains("canvas.drawBitmap(appIcon, mScratchIconSourceRect, rect, mPaintIcons)"))
    }

    @Test
    fun unusedWorkspaceBackgroundPathIsRemoved() {
        assertFalse(source.contains("mWorkspaceBackgroundDrawable"))
        assertFalse(source.contains("drawWorkspaceBackground"))
    }

    @Test
    fun touchPolicySampleIsGuardedByActionDown() {
        val body = source.substringAfter("override fun onTouchEvent(event: MotionEvent)")
            .substringBefore("private fun updateFocusHaloPaint")
        assertTrue(body.contains("if (event.actionMasked == MotionEvent.ACTION_DOWN)"))
        assertTrue(body.contains("LensPhysicsPolicy.shouldReduceLensMotion(context).also { mGestureReduceMotion = it }"))
        assertTrue(body.contains("finally"))
        assertTrue(body.contains("MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL"))
    }
}
