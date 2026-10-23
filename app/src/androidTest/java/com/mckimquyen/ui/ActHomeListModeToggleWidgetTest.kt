package com.mckimquyen.ui

import android.widget.ImageView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.adt.AppAdapter
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * UI-030: AppAdapter dùng chung cho ActSettings và list mode của ActHome. Trước đây nút ẩn chỉ
 * phát broadcast khi context là ActSettings, nên ở Home list mode lưới/lens không được báo.
 * Test dựng adapter trong ActHome thật và bấm nút ẩn trên hàng thật.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeListModeToggleWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private val mail = App(label = "Toggle Mail", packageName = "com.test.toggle.mail", name = "Main", isVisible = true)
    private val chat = App(label = "Toggle Chat", packageName = "com.test.toggle.chat", name = "Main", isVisible = true)
    private val fixture get() = listOf(mail, chat)
    private var originalApps: ArrayList<App>? = null
    private var originalPreferences: Map<String, *> = emptyMap<String, Any>()
    private val visibilityEvents = AtomicInteger(0)
    private val visibilityObserver = Observer<Any?> { visibilityEvents.incrementAndGet() }

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        originalPreferences = HashMap(prefs.all)
        originalApps = RAppsSingleton.instance.apps
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
        settings.setLauncherMode(LauncherMode.LIST)
        settings.saveLensAppScope(lensId, LensAppScope.ALL)
        settings.saveLensAppSelection(lensId, emptySet())
        settings.saveLensHiddenAppIds(lensId, emptySet())
        RAppsSingleton.instance.apps = ArrayList(fixture)
        instrumentation.runOnMainSync { AppEventManager.visibilityChanged.observeForever(visibilityObserver) }
    }

    @After
    fun cleanup() {
        instrumentation.runOnMainSync { AppEventManager.visibilityChanged.removeObserver(visibilityObserver) }
        val editor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
        originalPreferences.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun fixtureVisibleCount(): Int =
        RAppsSingleton.instance.apps.orEmpty()
            .count { it.packageName.toString().startsWith("com.test.toggle.") && it.isVisible }

    /** Chờ danh sách Home có đủ hàng fixture; reseed nếu snapshot PackageManager thật đè lên. */
    private fun awaitHomeRows(scenario: ActivityScenario<ActHome>): Int {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var rows = -1
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val current = RAppsSingleton.instance.apps.orEmpty().map { it.packageName.toString() }.toSet()
            if (current != fixture.map { it.packageName.toString() }.toSet()) {
                RAppsSingleton.instance.apps = ArrayList(fixture)
                AppEventManager.notifyAppsUpdated()
            }
            scenario.onActivity { activity ->
                rows = activity.findViewById<RecyclerView>(R.id.rvHomeAppList).adapter?.itemCount ?: -1
            }
            if (rows == fixture.size) return rows
            Thread.sleep(POLL_MS)
        }
        return rows
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            Thread.sleep(POLL_MS)
        }
        assertTrue(message, condition())
    }

    @Test
    fun hidingAnAppFromHomeListModeEmitsVisibilityChangedAndUpdatesSnapshot() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals("Home list mode phải hiển thị đủ hàng fixture", fixture.size, awaitHomeRows(scenario))
            visibilityEvents.set(0)
            assertEquals(fixture.size, fixtureVisibleCount())

            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)
                val holder = recycler.findViewHolderForAdapterPosition(0) as? AppAdapter.AppViewHolder
                assertTrue("Hàng đầu của list mode Home phải là AppViewHolder", holder != null)
                val hide = requireNotNull(holder).itemView.findViewById<ImageView>(R.id.ivAppHide)
                assertTrue("Nút ẩn phải bấm được trên Home list mode", hide.performClick())
            }

            awaitCondition("Bấm ẩn trên Home list mode phải phát AppEventManager.visibilityChanged") {
                visibilityEvents.get() >= 1
            }
            assertEquals("Đúng một app fixture bị ẩn trong snapshot", fixture.size - 1, fixtureVisibleCount())
            assertEquals("Một lần bấm chỉ phát một sự kiện", 1, visibilityEvents.get())
            awaitCondition("Sự kiện phải cập nhật danh sách Home") {
                var rows: Int? = null
                scenario.onActivity { activity ->
                    rows = activity.findViewById<RecyclerView>(R.id.rvHomeAppList).adapter?.itemCount
                }
                rows == fixture.size - 1
            }
            // ViewPager chưa tạo LensView khi GONE trong list mode; mở lưới để kiểm dữ liệu thật.
            scenario.onActivity { activity ->
                settings.setLauncherMode(LauncherMode.FISHEYE)
                activity.updateModeVisibility()
            }
            awaitCondition("Chuyển về fisheye không được hiện lại app đã ẩn") {
                var updated = false
                scenario.onActivity { activity ->
                    val grid = activity.lensViews?.appsForTest
                    updated = grid?.size == fixture.size - 1 && grid.all { it.isVisible }
                }
                updated
            }
        }
    }

    /** Tiêu chí "không đổi hành vi ở ActSettings": adapter dựng trong ActSettings vẫn phát đúng một sự kiện. */
    @Test
    fun hidingAnAppFromSettingsAdapterStillEmitsExactlyOneVisibilityChanged() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            visibilityEvents.set(0)
            scenario.onActivity { activity ->
                val recycler = RecyclerView(activity)
                recycler.layoutManager = LinearLayoutManager(activity)
                val adapter = AppAdapter(activity, mutableListOf(mail, chat))
                recycler.adapter = adapter
                recycler.measure(0, 0)
                recycler.layout(0, 0, SETTINGS_VIEWPORT_PX, SETTINGS_VIEWPORT_PX)
                val holder = adapter.createViewHolder(recycler, adapter.getItemViewType(0))
                adapter.bindViewHolder(holder, 0)
                assertTrue(holder.itemView.findViewById<ImageView>(R.id.ivAppHide).performClick())
            }
            awaitCondition("ActSettings phải vẫn phát visibilityChanged") { visibilityEvents.get() >= 1 }
            assertEquals(fixture.size - 1, fixtureVisibleCount())
            assertEquals("Một lần bấm chỉ phát một sự kiện", 1, visibilityEvents.get())
        }
    }

    private companion object {
        const val SETTINGS_VIEWPORT_PX = 2000
        const val WAIT_MS = 8_000L
        const val POLL_MS = 100L
    }
}
