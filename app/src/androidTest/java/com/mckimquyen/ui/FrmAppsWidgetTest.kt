package com.mckimquyen.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget / Instrumentation tests cho FrmApps Fragment (LEAK-001)
 *
 * Chứng minh:
 * 1. onDestroyView() null-hóa rvApps/progressBarApps/utilSettings/appAdapter
 *    → view hierarchy cũ không bị giữ sống sau khi Fragment view bị destroy
 * 2. RecyclerView adapter được detach tường minh trước khi field bị null
 * 3. Nhiều vòng create/destroy liên tiếp (giả lập xoay màn hình/điều hướng) không crash
 */
@RunWith(AndroidJUnit4::class)
class FrmAppsWidgetTest {

    private fun <T> privateField(target: Any, name: String): T? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(target) as T?
    }

    @Test
    fun testFrmApps_inflatesSuccessfully() {
        val scenario = launchFragmentInContainer<FrmApps>(themeResId = R.style.AppTheme)

        scenario.onFragment { fragment ->
            assertNotNull("FrmApps should be attached", fragment.activity)
            assertTrue("FrmApps should be visible", fragment.isVisible)
        }

        scenario.close()
    }

    @Test
    fun testFrmApps_viewCreated_recyclerViewPresent() {
        val scenario = launchFragmentInContainer<FrmApps>(themeResId = R.style.AppTheme)

        scenario.onFragment { fragment ->
            val rv = fragment.view?.findViewById<RecyclerView>(R.id.rvApps)
            assertNotNull("rvApps must exist", rv)
        }

        scenario.close()
    }

    @Test
    fun testFrmApps_onDestroyView_clearsViewReferencesAndDetachesAdapter() {
        lateinit var fragmentRef: FrmApps
        var recycler: RecyclerView? = null

        val scenario = launchFragmentInContainer<FrmApps>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragmentRef = fragment
            recycler = fragment.view?.findViewById(R.id.rvApps)
        }

        // scenario.close() drives the fragment through onDestroyView(); the fragment
        // instance captured above stays reachable so its fields can still be inspected.
        scenario.close()

        assertNull("rvApps field must be cleared in onDestroyView", privateField<RecyclerView?>(fragmentRef, "rvApps"))
        assertNull("progressBarApps field must be cleared in onDestroyView", privateField<Any?>(fragmentRef, "progressBarApps"))
        assertNull("utilSettings field must be cleared in onDestroyView", privateField<Any?>(fragmentRef, "utilSettings"))
        assertNull("appAdapter field must be cleared in onDestroyView", privateField<Any?>(fragmentRef, "appAdapter"))
        assertNull("RecyclerView adapter must be detached before the view is torn down", recycler?.adapter)
    }

    @Test
    fun testFrmApps_multipleCreateDestroyCycles_noLeak() {
        repeat(3) { cycle ->
            val scenario = launchFragmentInContainer<FrmApps>(themeResId = R.style.AppTheme)

            scenario.onFragment { fragment ->
                assertNotNull("Fragment should be active in cycle $cycle", fragment.activity)
            }

            try {
                scenario.close()
            } catch (e: Exception) {
                fail("Create-destroy cycle $cycle failed: ${e.message}")
            }
        }
    }

    @Test
    fun testFrmApps_isFragment() {
        val fragment = FrmApps.newInstance()
        assertNotNull("newInstance() must return a Fragment", fragment)
        assertTrue("FrmApps must extend Fragment", fragment is androidx.fragment.app.Fragment)
    }
}
