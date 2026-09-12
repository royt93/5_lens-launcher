package com.mckimquyen.adt

import androidx.recyclerview.widget.DiffUtil
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests cho [AppDiffCallback] — dùng để thay `notifyDataSetChanged()` bằng
 * cập nhật RecyclerView theo từng thay đổi cụ thể (NotifyDataSetChanged lint fix).
 * Test mọi case: danh sách giống hệt, thêm, xóa, đổi thứ tự, đổi nội dung, danh sách rỗng.
 */
class AppDiffCallbackTest {

    private fun app(
        pkg: String,
        name: String = pkg,
        label: String = pkg,
        isVisible: Boolean = true
    ) = App(packageName = pkg, name = name, label = label, isVisible = isVisible)

    private fun diff(old: List<App>, new: List<App>): DiffUtil.DiffResult =
        DiffUtil.calculateDiff(AppDiffCallback(old, new))

    @Test
    fun `identical lists produce no changes`() {
        val list = listOf(app("a"), app("b"), app("c"))
        val result = diff(list, list.toList())

        val callback = TrackingListUpdateCallback()
        result.dispatchUpdatesTo(callback)

        assertTrue("no insert/remove/change/move expected", callback.events.isEmpty())
    }

    @Test
    fun `appending a new app only inserts the new item`() {
        val old = listOf(app("a"), app("b"))
        val new = listOf(app("a"), app("b"), app("c"))

        val callback = TrackingListUpdateCallback()
        diff(old, new).dispatchUpdatesTo(callback)

        assertEquals(listOf("insert(2,1)"), callback.events)
    }

    @Test
    fun `removing an app only removes that item`() {
        val old = listOf(app("a"), app("b"), app("c"))
        val new = listOf(app("a"), app("c"))

        val callback = TrackingListUpdateCallback()
        diff(old, new).dispatchUpdatesTo(callback)

        assertEquals(listOf("remove(1,1)"), callback.events)
    }

    @Test
    fun `reordering apps is reported as a move, not remove-and-insert`() {
        val old = listOf(app("a"), app("b"), app("c"))
        val new = listOf(app("c"), app("a"), app("b"))

        val callback = TrackingListUpdateCallback()
        diff(old, new).dispatchUpdatesTo(callback)

        assertTrue("expected at least one move event, got ${callback.events}", callback.events.any { it.startsWith("move") })
        assertTrue("must not fall back to remove+insert for every item", callback.events.size < old.size * 2)
    }

    @Test
    fun `same identity but different content is reported as a change, not remove-and-insert`() {
        val old = listOf(app("a", isVisible = true))
        val new = listOf(app("a", isVisible = false))

        val callback = TrackingListUpdateCallback()
        diff(old, new).dispatchUpdatesTo(callback)

        assertEquals(listOf("change(0,1)"), callback.events)
    }

    @Test
    fun `areItemsTheSame matches by packageName and name, ignoring other fields`() {
        val oldApp = app("pkg.a", name = "ActivityA", label = "Old Label")
        val newApp = app("pkg.a", name = "ActivityA", label = "New Label")
        val callback = AppDiffCallback(listOf(oldApp), listOf(newApp))

        assertTrue(callback.areItemsTheSame(0, 0))
        assertFalse(callback.areContentsTheSame(0, 0))
    }

    @Test
    fun `areItemsTheSame is false for a different package or a different activity name`() {
        val a = app("pkg.a", name = "Main")
        val differentPackage = app("pkg.b", name = "Main")
        val differentActivity = app("pkg.a", name = "Other")

        assertFalse(AppDiffCallback(listOf(a), listOf(differentPackage)).areItemsTheSame(0, 0))
        assertFalse(AppDiffCallback(listOf(a), listOf(differentActivity)).areItemsTheSame(0, 0))
    }

    @Test
    fun `emptying a list only removes every item`() {
        val old = listOf(app("a"), app("b"))
        val new = emptyList<App>()

        val callback = TrackingListUpdateCallback()
        diff(old, new).dispatchUpdatesTo(callback)

        assertTrue(callback.events.isNotEmpty())
        assertTrue(callback.events.all { it.startsWith("remove") })
    }

    /** Records dispatched updates as plain strings so assertions stay readable. */
    private class TrackingListUpdateCallback : androidx.recyclerview.widget.ListUpdateCallback {
        val events = mutableListOf<String>()
        override fun onInserted(position: Int, count: Int) { events += "insert($position,$count)" }
        override fun onRemoved(position: Int, count: Int) { events += "remove($position,$count)" }
        override fun onMoved(fromPosition: Int, toPosition: Int) { events += "move($fromPosition,$toPosition)" }
        override fun onChanged(position: Int, count: Int, payload: Any?) { events += "change($position,$count)" }
    }
}
