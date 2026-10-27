package com.mckimquyen.ui.home

import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class T9KeypadBinderTest {
    @Test
    fun typingBackspaceClearAndCloseKeepTheOriginalBehavior() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val column = LinearLayout(activity)
            val input = EditText(activity)
            column.addView(input)
            val ids = listOf(R.id.btnT9_2, R.id.btnT9_4, R.id.btnT9_7, R.id.btnT9_Clear, R.id.btnT9_Backspace)
            val buttons = ids.associateWith { id -> Button(activity).also { it.id = id; column.addView(it) } }
            activity.setContentView(column)
            val binder = T9KeypadBinder(activity, input)
            listOf(R.id.btnT9_2, R.id.btnT9_4, R.id.btnT9_7).forEach { buttons.getValue(it).performClick() }
            assertEquals("247", input.text.toString())
            buttons.getValue(R.id.btnT9_Backspace).performClick()
            assertEquals("24", input.text.toString())
            assertEquals(2, input.selectionStart)
            buttons.getValue(R.id.btnT9_Backspace).performLongClick()
            assertEquals("", input.text.toString())
            buttons.getValue(R.id.btnT9_2).performClick()
            buttons.getValue(R.id.btnT9_Clear).performClick()
            assertEquals("", input.text.toString())
            buttons.getValue(R.id.btnT9_Backspace).performClick()
            assertEquals("", input.text.toString())
            assertTrue(buttons.getValue(R.id.btnT9_2).hasOnClickListeners())
            binder.close()
            assertFalse(buttons.getValue(R.id.btnT9_2).hasOnClickListeners())
            buttons.getValue(R.id.btnT9_2).performClick()
            buttons.getValue(R.id.btnT9_Backspace).performLongClick()
            assertEquals("", input.text.toString())
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
