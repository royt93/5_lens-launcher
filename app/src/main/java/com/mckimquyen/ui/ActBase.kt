package com.mckimquyen.ui

import android.app.ActivityManager.TaskDescription
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.appcompat.R as AppCompatR
import com.google.android.material.color.MaterialColors
import com.mckimquyen.R
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.util.UtilSettings

open class ActBase : BaseActivity() {
    @JvmField
    protected var utilSettings: UtilSettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        utilSettings = UtilSettings(this)
        if (savedInstanceState == null) {
            updateNightMode()
        }
        super.onCreate(savedInstanceState)
        observePersistFailures()
    }

    /**
     * DB-006: a failed Room write has already been rolled back in memory; tell the user their
     * change did not stick. LiveData replays its last value to a new observer, so the count at
     * subscription time is the baseline and only later increments are shown.
     */
    private fun observePersistFailures() {
        var lastSeen = AppEventManager.persistFailureCount.value ?: 0L
        AppEventManager.persistFailureCount.observe(this) { count ->
            if (shouldShowSaveFailure(lastSeen, count)) {
                lastSeen = count
                Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        /** True only for a failure newer than the last one this screen has already shown. */
        @androidx.annotation.VisibleForTesting
        internal fun shouldShowSaveFailure(lastSeen: Long, current: Long): Boolean = current > lastSeen
    }


    override fun setContentView(@LayoutRes layoutResID: Int) {
        super.setContentView(layoutResID)
        setTaskDescription()
    }

    private fun setTaskDescription() {
        val taskDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: Use Builder pattern, icon is resource ID
            TaskDescription.Builder()
                .setLabel(getString(R.string.app_name))
                .setIcon(R.mipmap.ic_launcher)
                .build()
        } else {
            // API < 33: Use deprecated constructor with colorPrimary and Bitmap icon
            // Fix BUG-12: Recycle bitmap sau khi setTaskDescription() để tránh memory waste.
            // TaskDescription tạo internal copy của bitmap nên an toàn để recycle ngay.
            val appIconBitmap = try {
                BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
            } catch (e: Exception) {
                null
            }
            @Suppress("DEPRECATION")
            val desc = TaskDescription(
                /* label = */ getString(R.string.app_name),
                /* icon = */ appIconBitmap,
                /* colorPrimary = */ MaterialColors.getColor(
                    baseContext,
                    AppCompatR.attr.colorPrimary,
                    ContextCompat.getColor(baseContext, R.color.colorPrimaryDark)
                )
            )
            appIconBitmap?.recycle()
            desc
        }
        setTaskDescription(taskDescription)
    }

    protected open fun updateNightMode() {
        if (utilSettings == null) {
            utilSettings = UtilSettings(this)
        }
        utilSettings?.let {
            delegate.localNightMode = it.nightMode
        }
    }
}