package com.mckimquyen.search

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.ui.ActHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSearchIntegrationTest {

    @Test
    fun packageAndAppStateEventsRefreshAnActiveSearch() {
        val singleton = RAppsSingleton.instance
        val originalApps = singleton.apps
        val camera = app("Camera", visible = true)
        singleton.apps = arrayListOf(camera)

        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.etAppSearch).apply {
                        requestFocus()
                        setText("camera")
                    }
                    assertSearch(activity, 1, "Camera")
                }

                singleton.apps = arrayListOf(app("Gallery", visible = true))
                AppEventManager.notifyAppsUpdated()
                waitForMainThread()
                scenario.onActivity { assertSearch(it, 0) }

                singleton.apps = arrayListOf(app("Camera Pro", visible = true, openCount = 7))
                AppEventManager.notifyAppsEdited()
                waitForMainThread()
                scenario.onActivity { assertSearch(it, 1, "Camera Pro") }

                singleton.apps = arrayListOf(app("Camera Pro", visible = false, openCount = 7))
                AppEventManager.notifyVisibilityChanged()
                waitForMainThread()
                scenario.onActivity { activity ->
                    assertSearch(activity, 0)
                    assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.lensViews).visibility)
                }

                singleton.apps = arrayListOf()
                AppEventManager.notifyAppsUpdated()
                waitForMainThread()
                scenario.onActivity { assertSearch(it, 0) }
            }
        } finally {
            singleton.apps = originalApps
        }
    }

    @Test
    fun recentHeaderRestoresAndClearActionRemovesHistory() {
        val singleton = RAppsSingleton.instance
        val originalApps = singleton.apps
        val recent = app("Recent Camera", visible = true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SearchHistoryStore(context)
        store.clear()
        store.recordLaunch(AppSearchEngine.componentKey(recent))
        singleton.apps = arrayListOf(recent)

        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.etAppSearch).requestFocus()
                    assertSearch(activity, 1, "Recent Camera")
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recentHeader).visibility)
                }

                scenario.recreate()
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.etAppSearch).requestFocus()
                    assertSearch(activity, 1, "Recent Camera")
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recentHeader).visibility)
                    activity.findViewById<View>(R.id.btClearSearchHistory).performClick()
                    assertSearch(activity, 0)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.recentHeader).visibility)
                    assertTrue(store.recentKeys().isEmpty())
                }
            }
        } finally {
            store.clear()
            singleton.apps = originalApps
        }
    }

    @Test
    fun supportedImeActionsAndPhysicalEnterLaunchFirstResultOnly() {
        val singleton = RAppsSingleton.instance
        val originalApps = singleton.apps
        val launchable = app("Launchable", visible = true)
        val store = SearchHistoryStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear()
        singleton.apps = arrayListOf(launchable)

        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val search = activity.findViewById<EditText>(R.id.etAppSearch)
                    for (action in listOf(
                        EditorInfo.IME_ACTION_SEARCH,
                        EditorInfo.IME_ACTION_GO,
                        EditorInfo.IME_ACTION_DONE
                    )) {
                        search.requestFocus()
                        search.setText("launchable")
                        search.onEditorAction(action)
                        assertEquals("", search.text.toString())
                    }

                    search.requestFocus()
                    search.setText("launchable")
                    search.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    search.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    assertEquals("", search.text.toString())

                    search.requestFocus()
                    search.setText("launchable")
                    search.onEditorAction(EditorInfo.IME_ACTION_NEXT)
                    assertEquals("launchable", search.text.toString())

                    search.setText("unmatched-query")
                    search.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
                    assertEquals("unmatched-query", search.text.toString())
                    assertEquals(listOf(AppSearchEngine.componentKey(launchable)), store.recentKeys())
                }
            }
        } finally {
            store.clear()
            singleton.apps = originalApps
        }
    }

    private fun assertSearch(activity: ActHome, count: Int, firstLabel: String? = null) {
        val adapter = activity.findViewById<RecyclerView>(R.id.rvSearchResults).adapter as SearchResultAdapter
        assertEquals(count, adapter.itemCount)
        if (firstLabel != null) assertEquals(firstLabel, adapter.firstOrNull()?.label?.toString())
    }

    private fun waitForMainThread() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun app(label: String, visible: Boolean, openCount: Long = 0) = App(
        label = label,
        packageName = "com.example.${label.lowercase().replace(' ', '.')}",
        name = "com.example.MissingActivity",
        isVisible = visible,
        isOpened = true,
        openCount = openCount
    )
}
