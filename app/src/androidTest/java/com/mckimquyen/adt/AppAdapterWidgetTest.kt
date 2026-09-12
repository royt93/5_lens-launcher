package com.mckimquyen.adt

import android.view.View
import android.widget.ImageView
import androidx.biometric.BiometricManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget tests cho `AppAdapter.updateApps` sau khi đổi từ `notifyDataSetChanged()`
 * sang `DiffUtil` (NotifyDataSetChanged lint fix). `AppAdapter` chưa từng có test
 * riêng trước đây — các test này gắn adapter vào một `RecyclerView` thật trong một
 * Activity thật (cần context thật cho check biometric trong `AppViewHolder`), rồi
 * chứng minh thứ tự/nội dung hiển thị đúng sau nhiều lần cập nhật khác nhau
 * (thêm, xóa, đổi thứ tự, đổi nội dung) — không chỉ "không crash".
 */
@RunWith(AndroidJUnit4::class)
class AppAdapterWidgetTest {

    @Test
    fun testUpdateApps_insertRemoveReorderAndContentChange_endsWithCorrectVisibleContent() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val recyclerView = RecyclerView(activity)
            recyclerView.layoutManager = LinearLayoutManager(activity)

            val adapter = AppAdapter(activity, mutableListOf(app("a"), app("b"), app("c")))
            recyclerView.adapter = adapter
            recyclerView.measure(0, 0)
            recyclerView.layout(0, 0, 1080, 2000)

            assertEquals(3, adapter.itemCount)
            assertEquals("a", adapter.getItemForPosition(0).packageName.toString())
            assertEquals("b", adapter.getItemForPosition(1).packageName.toString())
            assertEquals("c", adapter.getItemForPosition(2).packageName.toString())

            // Insert "d" at the end.
            adapter.updateApps(listOf(app("a"), app("b"), app("c"), app("d")))
            assertEquals(4, adapter.itemCount)
            assertEquals("d", adapter.getItemForPosition(3).packageName.toString())

            // Remove "b".
            adapter.updateApps(listOf(app("a"), app("c"), app("d")))
            assertEquals(3, adapter.itemCount)
            assertEquals(listOf("a", "c", "d"), (0..2).map { adapter.getItemForPosition(it).packageName.toString() })

            // Reorder to c, a, d.
            adapter.updateApps(listOf(app("c"), app("a"), app("d")))
            assertEquals(listOf("c", "a", "d"), (0..2).map { adapter.getItemForPosition(it).packageName.toString() })

            // Same identities, "a" content changes (isVisible flips) — same size/order, new data.
            adapter.updateApps(listOf(app("c"), app("a", isVisible = false), app("d")))
            assertEquals(3, adapter.itemCount)
            assertFalse("updated content must be reflected, not the stale cached value", adapter.getItemForPosition(1).isVisible)

            // The RecyclerView must still be able to bind every position after all these diffs.
            recyclerView.measure(0, 0)
            recyclerView.layout(0, 0, 1080, 2000)
            for (position in 0 until adapter.itemCount) {
                val holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(position))
                adapter.bindViewHolder(holder, position)
            }
        }

        scenario.close()
    }

    @Test
    fun testUpdateApps_toEmptyThenBackToNonEmpty_reportsCorrectCounts() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val adapter = AppAdapter(activity, mutableListOf(app("a"), app("b")))
            assertEquals(2, adapter.itemCount)

            adapter.updateApps(emptyList())
            assertEquals(0, adapter.itemCount)

            adapter.updateApps(listOf(app("x"), app("y"), app("z")))
            assertEquals(3, adapter.itemCount)
            assertEquals("x", adapter.getItemForPosition(0).packageName.toString())
        }

        scenario.close()
    }

    /**
     * UI-006: the lock control is now an icon (was a text Button) — this device has no
     * enrolled biometric in CI/most test runs, in which case the row hides the control
     * entirely (existing, unchanged behavior); the icon/contentDescription assertions below
     * only apply when biometric actually is available, which is the real state on a real
     * enrolled device (e.g. the designated smoke-test device).
     */
    @Test
    fun testLockIcon_reflectsOpenedState_whenBiometricAvailable() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val hasBiometric = BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

            val recyclerView = RecyclerView(activity)
            recyclerView.layoutManager = LinearLayoutManager(activity)
            val unlockedApp = app("unlocked", isOpened = true)
            val lockedApp = app("locked", isOpened = false)
            val adapter = AppAdapter(activity, mutableListOf(unlockedApp, lockedApp))
            recyclerView.adapter = adapter
            recyclerView.measure(0, 0)
            recyclerView.layout(0, 0, 1080, 2000)

            val unlockedHolder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(0))
            adapter.bindViewHolder(unlockedHolder, 0)
            val lockedHolder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(1))
            adapter.bindViewHolder(lockedHolder, 1)

            val unlockedLockView = unlockedHolder.itemView.findViewById<ImageView>(R.id.btAppLock)
            val lockedLockView = lockedHolder.itemView.findViewById<ImageView>(R.id.btAppLock)

            if (!hasBiometric) {
                assertEquals(View.GONE, unlockedLockView.visibility)
                assertEquals(View.GONE, lockedLockView.visibility)
                return@onActivity
            }

            assertEquals(View.VISIBLE, unlockedLockView.visibility)
            assertEquals(View.VISIBLE, lockedLockView.visibility)

            // Unlocked -> tapping locks it -> content description is the action available
            // ("Lock"); locked -> tapping unlocks it -> action available is "Unlock". Which icon
            // resource is chosen per state is covered by the pure AppAdapterLockStateTest unit
            // tests (a device-level drawable-identity comparison here is unreliable: ImageView's
            // setColorFilter() calls Drawable.mutate(), which replaces the ConstantState, so it
            // never == a freshly loaded Drawable's ConstantState even when it's the same icon).
            assertEquals(activity.getString(R.string.lock), unlockedLockView.contentDescription)
            assertEquals(activity.getString(R.string.unlock), lockedLockView.contentDescription)
            assertNotEquals(unlockedLockView.contentDescription, lockedLockView.contentDescription)
        }

        scenario.close()
    }

    @Test
    fun testLockIcon_hiddenWhenDeviceHasNoBiometric_matchesDeviceCapability() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val hasBiometric = BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

            val recyclerView = RecyclerView(activity)
            recyclerView.layoutManager = LinearLayoutManager(activity)
            val adapter = AppAdapter(activity, mutableListOf(app("solo")))
            recyclerView.adapter = adapter
            recyclerView.measure(0, 0)
            recyclerView.layout(0, 0, 1080, 2000)

            val holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(0))
            adapter.bindViewHolder(holder, 0)
            val lockView = holder.itemView.findViewById<ImageView>(R.id.btAppLock)

            assertEquals(
                if (hasBiometric) View.VISIBLE else View.GONE,
                lockView.visibility
            )
        }

        scenario.close()
    }

    private fun app(pkg: String, label: String = pkg, isVisible: Boolean = true, isOpened: Boolean = true) =
        App(packageName = pkg, name = pkg, label = label, isVisible = isVisible, isOpened = isOpened)
}
