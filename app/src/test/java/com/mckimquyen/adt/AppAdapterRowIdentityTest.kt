package com.mckimquyen.adt

import com.mckimquyen.model.App
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI-030 follow-up: a biometric prompt can finish after RecyclerView rebound the holder to another
 * row, so the completion callback must only touch the holder when it still shows the same app and lens.
 */
class AppAdapterRowIdentityTest {
    private val mail = App(packageName = "com.test.mail", name = "Main", lensId = "work")

    @Test
    fun sameComponentAndLensIsTheSameRow() {
        assertTrue(AppAdapter.isSameRow(mail.copy(isOpened = false), mail))
    }

    @Test
    fun nullCurrentRowIsNotTheSameRow() {
        assertFalse(AppAdapter.isSameRow(null, mail))
    }

    @Test
    fun differentComponentIsNotTheSameRow() {
        assertFalse(AppAdapter.isSameRow(mail.copy(name = "Other"), mail))
    }

    @Test
    fun differentPackageIsNotTheSameRow() {
        assertFalse(AppAdapter.isSameRow(mail.copy(packageName = "com.test.chat"), mail))
    }

    @Test
    fun sameComponentInAnotherLensIsNotTheSameRow() {
        assertFalse(AppAdapter.isSameRow(mail.copy(lensId = "personal"), mail))
    }
}
