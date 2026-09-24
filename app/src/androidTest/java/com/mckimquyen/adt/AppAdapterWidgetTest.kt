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
import org.junit.Assert.assertTrue
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

    /**
     * FEAT-007: toggling a selection must rebind only via [AppAdapter.PAYLOAD_SELECTION] —
     * proven here by calling the payload-aware `onBindViewHolder` overload directly with a
     * payload that is NOT the selection marker: the label must stay whatever `tvAppLabel`
     * already showed (a real content rebind would instead replace it), while the selection
     * payload path does apply the checked state and hides the per-row action icons.
     */
    @Test
    fun testSelectionPayload_onlyUpdatesCheckedStateAndIconVisibility_notUnrelatedFields() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val recyclerView = RecyclerView(activity)
            recyclerView.layoutManager = LinearLayoutManager(activity)
            val adapter = AppAdapter(activity, mutableListOf(app("a", label = "A Label")))
            recyclerView.adapter = adapter
            recyclerView.measure(0, 0)
            recyclerView.layout(0, 0, 1080, 2000)

            val holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(0))
            adapter.bindViewHolder(holder, 0)
            val labelBefore = holder.itemView.findViewById<android.widget.TextView>(R.id.tvAppLabel).text
            val card = holder.itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvAppContainer)
            assertFalse("row must start unchecked", card.isChecked)
            assertEquals(View.VISIBLE, holder.itemView.findViewById<ImageView>(R.id.ivAppMenu).visibility)

            // An unrelated payload must fall through to the normal DiffUtil-driven full bind.
            adapter.onBindViewHolder(holder, 0, mutableListOf<Any>("something-else"))
            assertEquals(labelBefore, holder.itemView.findViewById<android.widget.TextView>(R.id.tvAppLabel).text)

            adapter.toggleSelection(adapter.getItemForPosition(0))
            adapter.onBindViewHolder(holder, 0, mutableListOf<Any>(AppAdapter.PAYLOAD_SELECTION))

            assertTrue("selection payload must check the card", card.isChecked)
            assertEquals(
                "selection mode must hide the per-row menu button",
                View.GONE,
                holder.itemView.findViewById<ImageView>(R.id.ivAppMenu).visibility
            )
            assertEquals(labelBefore, holder.itemView.findViewById<android.widget.TextView>(R.id.tvAppLabel).text)
        }

        scenario.close()
    }

    @Test
    fun testToggleSelection_addsAndRemovesFromSelectedApps() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val adapter = AppAdapter(activity, mutableListOf(app("a"), app("b")))
            assertFalse(adapter.isSelectionMode)
            assertEquals(0, adapter.selectionCount)

            adapter.toggleSelection(adapter.getItemForPosition(0))
            assertTrue(adapter.isSelectionMode)
            assertEquals(listOf("a"), adapter.selectedApps.map { it.packageName.toString() })

            adapter.toggleSelection(adapter.getItemForPosition(1))
            assertEquals(2, adapter.selectionCount)

            adapter.toggleSelection(adapter.getItemForPosition(0))
            assertEquals(listOf("b"), adapter.selectedApps.map { it.packageName.toString() })

            adapter.clearSelection()
            assertFalse(adapter.isSelectionMode)
            assertEquals(0, adapter.selectionCount)
        }

        scenario.close()
    }

    /**
     * FEAT-007 audit finding: [AppAdapter.AppViewHolder.setSelectionState] only ever set the
     * per-row action icons to GONE when entering selection mode — it never restored them on
     * exit, because the payload-only rebind path never calls back into `setAppElement`'s
     * visibility rules. Deselecting the last selected item (the single most common way a user
     * exits multi-select) must bring the row's hide/lock/menu icons back.
     */
    @Test
    fun testDeselectingLastItem_restoresPerRowIconVisibility() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val recyclerView = RecyclerView(activity)
            recyclerView.layoutManager = LinearLayoutManager(activity)
            val adapter = AppAdapter(activity, mutableListOf(app("a")))
            recyclerView.adapter = adapter
            recyclerView.measure(0, 0)
            recyclerView.layout(0, 0, 1080, 2000)

            val holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(0))
            adapter.bindViewHolder(holder, 0)
            val menuView = holder.itemView.findViewById<ImageView>(R.id.ivAppMenu)
            val hideView = holder.itemView.findViewById<ImageView>(R.id.ivAppHide)
            assertEquals(View.VISIBLE, menuView.visibility)
            assertEquals(View.VISIBLE, hideView.visibility)

            val target = adapter.getItemForPosition(0)
            adapter.toggleSelection(target)
            adapter.onBindViewHolder(holder, 0, mutableListOf<Any>(AppAdapter.PAYLOAD_SELECTION))
            assertEquals("selecting must hide the row menu icon", View.GONE, menuView.visibility)
            assertEquals("selecting must hide the row hide icon", View.GONE, hideView.visibility)

            adapter.toggleSelection(target)
            adapter.onBindViewHolder(holder, 0, mutableListOf<Any>(AppAdapter.PAYLOAD_SELECTION))
            assertEquals(
                "deselecting the last item must restore the row menu icon, not leave it GONE forever",
                View.VISIBLE,
                menuView.visibility
            )
            assertEquals(
                "deselecting the last item must restore the row hide icon, not leave it GONE forever",
                View.VISIBLE,
                hideView.visibility
            )
        }

        scenario.close()
    }

    /**
     * FEAT-007 audit finding: a background app-list refresh (`updateApps`) while a selection
     * is active must not leave the `ActionMode`/selection count silently referencing apps that
     * no longer exist in the list.
     */
    @Test
    fun testUpdateApps_prunesSelectionToAppsStillPresent() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val adapter = AppAdapter(activity, mutableListOf(app("a"), app("b"), app("c")))
            adapter.toggleSelection(adapter.getItemForPosition(0))
            adapter.toggleSelection(adapter.getItemForPosition(1))
            assertEquals(2, adapter.selectionCount)

            var lastReportedCount = -1
            adapter.setSelectionListener(AppAdapter.SelectionListener { count -> lastReportedCount = count })

            // "a" is removed from the refreshed list; "b" and "d" remain/arrive.
            adapter.updateApps(listOf(app("b"), app("d")))

            assertEquals(
                "selection must drop identifiers for apps no longer in the list",
                1,
                adapter.selectionCount
            )
            assertEquals(listOf("b"), adapter.selectedApps.map { it.packageName.toString() })
            assertEquals(
                "the selection listener must be told the count changed, so ActionMode updates",
                1,
                lastReportedCount
            )
        }

        scenario.close()
    }

    /**
     * FEAT-007: the launcher's own row must never enter selection — it can't be
     * hidden/pinned/uninstalled anyway (see the `PKG_NAME` special case in
     * `setAppElement`). Drives a real `performLongClick()`, not a direct
     * `toggleSelection()` call, so it actually exercises the guard in
     * `setOnClickListeners()`.
     */
    @Test
    fun testLongPress_onLauncherOwnRow_neverEntersSelection() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val recyclerView = RecyclerView(activity)
            recyclerView.layoutManager = LinearLayoutManager(activity)
            val ownApp = App(packageName = com.mckimquyen.util.PKG_NAME, name = "MainActivity", label = "Fisheye Launcher")
            val adapter = AppAdapter(activity, mutableListOf(ownApp, app("other")))
            recyclerView.adapter = adapter
            recyclerView.measure(0, 0)
            recyclerView.layout(0, 0, 1080, 2000)

            val holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(0))
            adapter.bindViewHolder(holder, 0)

            val consumed = holder.itemView.performLongClick()

            assertTrue("the long-click listener must still consume the event (no popup fallback)", consumed)
            assertFalse("long-pressing the launcher's own row must never enter selection", adapter.isSelectionMode)
            assertEquals(0, adapter.selectionCount)
        }

        scenario.close()
    }

    @Test
    fun testSelectAll_selectsEveryCurrentApp() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val adapter = AppAdapter(activity, mutableListOf(app("a"), app("b"), app("c")))
            adapter.selectAll()
            assertEquals(3, adapter.selectionCount)
            assertEquals(listOf("a", "b", "c"), adapter.selectedApps.map { it.packageName.toString() })
        }

        scenario.close()
    }

    private fun app(pkg: String, label: String = pkg, isVisible: Boolean = true, isOpened: Boolean = true) =
        App(packageName = pkg, name = pkg, label = label, isVisible = isVisible, isOpened = isOpened)
}
