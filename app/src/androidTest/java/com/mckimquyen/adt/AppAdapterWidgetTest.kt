package com.mckimquyen.adt

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun app(pkg: String, label: String = pkg, isVisible: Boolean = true) =
        App(packageName = pkg, name = pkg, label = label, isVisible = isVisible)

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
}
