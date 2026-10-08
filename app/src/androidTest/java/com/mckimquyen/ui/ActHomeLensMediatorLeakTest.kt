package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensMediatorLeakTest {

    private fun mediatorField() = ActHome::class.java.getDeclaredField("lensTabMediator")
        .apply { isAccessible = true }

    @Test
    fun lensTabMediatorIsHeldWhileAliveAndReleasedOnDestroy() {
        var activity: ActHome? = null
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity {
                activity = it
                assertNotNull("mediator must be created and retained", mediatorField().get(it))
            }
        }
        // use{} closes the scenario -> onDestroy ran
        assertNull("mediator must be detached and nulled on destroy", mediatorField().get(activity))
    }
}
