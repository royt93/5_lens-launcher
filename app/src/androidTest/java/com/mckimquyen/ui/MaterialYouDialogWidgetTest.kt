package com.mckimquyen.ui

import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.ext.showDialog2
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Instrumented Widget Test verifying Material 3 showDialog2 behavior on device.
 */
@RunWith(AndroidJUnit4::class)
class MaterialYouDialogWidgetTest {

    @Test
    fun testShowDialog2BuildsValidMaterialDialog() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val btn1Clicked = AtomicBoolean(false)
        val dismissed = AtomicBoolean(false)
        var dialogRef: AlertDialog? = null

        scenario.onActivity { activity ->
            dialogRef = activity.showDialog2(
                title = "Material 3 Test",
                msg = "Testing dialog with MaterialYouDialogTheme",
                button1 = "Agree",
                button2 = "Cancel",
                onClickButton1 = { btn1Clicked.set(true) },
                isCancelable = false,
                onDismiss = { dismissed.set(true) }
            )
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val dialog = requireNotNull(dialogRef) { "Dialog must not be null" }
        assertTrue("Dialog must be showing", dialog.isShowing)

        // Verify button texts
        val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val btnNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        assertNotNull("Positive button must exist", btnPositive)
        assertNotNull("Negative button must exist", btnNegative)

        assertEquals("Agree", btnPositive.text.toString())
        assertEquals("Cancel", btnNegative.text.toString())

        // Test positive button action
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            btnPositive.performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue("onClickButton1 must be called", btn1Clicked.get())

        // Dismiss dialog
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dialog.dismiss()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertFalse("Dialog must not be showing after dismissal", dialog.isShowing)
        assertTrue("onDismiss callback must be called", dismissed.get())

        scenario.close()
    }
}
