package com.mckimquyen.search

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.BitmapCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchResultAdapterWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


    @Test
    fun submitBindAndClickExposeCurrentAppAndAccessibleContent() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val first = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
                val second = App(label = "Notes", packageName = "pkg.notes", name = "NotesActivity")
                var clickedApp: App? = null
                var clickedSource: android.view.View? = null
                val adapter = SearchResultAdapter(onAppClick = { app, source ->
                    clickedApp = app
                    clickedSource = source
                })

                assertFalse(adapter.hasStableIds())
                assertNull(adapter.firstOrNull())
                adapter.submitList(listOf(first, second))
                assertEquals(2, adapter.itemCount)
                assertSame(first, adapter.firstOrNull())

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)
                assertEquals(
                    "Camera",
                    holder.itemView.findViewById<TextView>(R.id.tvSearchResultLabel).text.toString()
                )
                assertEquals(
                    "pkg.camera",
                    holder.itemView.findViewById<TextView>(R.id.tvSearchResultPackage).text.toString()
                )
                // SEARCH-003: click/contentDescription live on the inner main row now, not the
                // item root - the root also hosts the (separately clickable) shortcuts row.
                val mainRow = holder.itemView.findViewById<android.view.View>(R.id.llSearchResultMainRow)
                assertEquals(activity.getString(R.string.search_open_app, "Camera"), mainRow.contentDescription)

                mainRow.performClick()
                assertSame(first, clickedApp)
                assertSame(mainRow, clickedSource)

                adapter.submitList(listOf(second))
                assertEquals(1, adapter.itemCount)
                assertSame(second, adapter.firstOrNull())
            }
        }
    }

    /**
     * SEARCH-003: this test device is not set as the default launcher, so
     * [AppShortcutsProvider.shortcutsFor] must hit its SecurityException path and return an
     * empty list - proving apps with no (queryable) shortcuts render exactly as before, no
     * empty affordance shown.
     */
    @Test
    fun shortcutsRowStaysHiddenWhenNoShortcutsAreQueryable() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
                val adapter = SearchResultAdapter(onAppClick = { _, _ -> })
                adapter.submitList(listOf(app))

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)

                val shortcutsRow = holder.itemView.findViewById<View>(R.id.llSearchResultShortcuts)
                assertEquals(View.GONE, shortcutsRow.visibility)
            }
        }
    }

    @Test
    fun cacheMissStillShowsPlaceholderIconImmediately() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BitmapCache.clear()
                val app = App(
                    label = "Missing Icon",
                    packageName = "pkg.missing.icon",
                    name = "MissingIconActivity",
                    iconCacheKey = "missing-icon-cache-key"
                )
                val adapter = SearchResultAdapter(onAppClick = { _, _ -> })
                adapter.submitList(listOf(app))

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)

                val icon = holder.itemView.findViewById<ImageView>(R.id.ivSearchResultIcon)
                assertTrue(
                    "Search result rows must never render a blank icon while cache reloads",
                    icon.drawable != null
                )

                adapter.onViewRecycled(holder)
                assertTrue(
                    "Recycled rows must reset to a placeholder instead of carrying a blank bitmap",
                    icon.drawable != null
                )
            }
        }
    }

    /**
     * SEARCH-014: shortcut lists are cached per package. Binding more packages than the cache holds
     * must not grow it past its bound, and a memory trim must release entries.
     */
    @Test
    fun shortcutCacheStaysBoundedAndReleasesEntriesOnTrim() {
        val total = ShortcutCache.DEFAULT_MAX_PACKAGES + EXTRA_PACKAGES
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val adapter = SearchResultAdapter(onAppClick = { _, _ -> })
            scenario.onActivity { activity ->
                val apps = (0 until total).map {
                    App(label = "App $it", packageName = "pkg.cache.$it", name = "Main$it")
                }
                adapter.submitList(apps)
                val parent = FrameLayout(activity)
                apps.indices.forEach { index ->
                    val holder = adapter.onCreateViewHolder(parent, 0)
                    adapter.onBindViewHolder(holder, index)
                }
            }
            // Shortcut queries finish on a worker thread, then post back to the main thread.
            val deadline = System.currentTimeMillis() + WAIT_MS
            var cached = 0
            while (System.currentTimeMillis() < deadline) {
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { cached = adapter.cachedShortcutPackageCountForTest() }
                if (cached >= ShortcutCache.DEFAULT_MAX_PACKAGES) break
                Thread.sleep(POLL_MS)
            }
            assertEquals("cache must fill to, and never exceed, its bound", ShortcutCache.DEFAULT_MAX_PACKAGES, cached)

            scenario.onActivity {
                adapter.trimShortcutCache(0.5f)
                assertEquals(ShortcutCache.DEFAULT_MAX_PACKAGES / 2, adapter.cachedShortcutPackageCountForTest())
                adapter.trimShortcutCache(0f)
                assertEquals(0, adapter.cachedShortcutPackageCountForTest())
            }
        }
    }

    private companion object {
        const val EXTRA_PACKAGES = 20
        const val WAIT_MS = 8_000L
        const val POLL_MS = 100L
    }

    /** SEARCH-003: long-press must show the row action menu without crashing. */
    @Test
    fun longPressShowsActionMenuWithoutCrashing() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = App(
                    label = "Camera",
                    packageName = "pkg.camera",
                    name = "CameraActivity",
                    pinnedZone = PinnedZone.START
                )
                val adapter = SearchResultAdapter(onAppClick = { _, _ -> })
                adapter.submitList(listOf(app))

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)
                val mainRow = holder.itemView.findViewById<View>(R.id.llSearchResultMainRow)

                assertTrue("long click must be handled", mainRow.performLongClick())
            }
        }
    }

    /** SEARCH-003: the row action menu offers exactly info/pin(start/end)/unpin/uninstall. */
    @Test
    fun searchResultMenuResourceHasExactlyTheExpectedActions() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val menu = android.widget.PopupMenu(activity, FrameLayout(activity)).menu
                activity.menuInflater.inflate(R.menu.menu_search_result, menu)

                val ids = (0 until menu.size()).map { menu.getItem(it).itemId }
                assertEquals(
                    listOf(
                        R.id.menuItemElementAppInfo,
                        R.id.menuItemPinStart,
                        R.id.menuItemPinEnd,
                        R.id.menuItemUnpin,
                        R.id.menuItemHideFromLens,
                        R.id.menuItemRemoveFromLens,
                        R.id.menuItemElementUninstall,
                        R.id.menuItemRemoveFromRecent
                    ),
                    ids
                )
                assertFalse("Action ẩn chỉ hiện trên lens grid", menu.findItem(R.id.menuItemHideFromLens).isVisible)
                // No favorite/folder/move items - those need a stable grid position search
                // results don't have.
                assertNull(menu.findItem(R.id.menuItemFavorite))
                assertNull(menu.findItem(R.id.menuItemFolder))
                assertNull(menu.findItem(R.id.menuItemMoveEarlier))
                assertNull(menu.findItem(R.id.menuItemMoveLater))
            }
        }
    }

    /**
     * Repo audit: the adapter cancels its own CoroutineScope in onDetachedFromRecyclerView. A
     * cancelled scope is permanently dead - every later `launch` is silently dropped - so if the
     * adapter is ever re-attached (a second setAdapter, a RecyclerView moved between windows, a
     * config change that re-uses the instance) its icon loads would stop happening with no crash
     * and no log. ActHome attaches it exactly once today, which is the only reason this is not a
     * live bug; this pins the behaviour so a future re-attach does not regress silently.
     */
    @Test
    fun adapterStillLoadsIconsAfterBeingDetachedAndReattached() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BitmapCache.clear()
                val app = App(
                    label = "Reattached",
                    packageName = "pkg.reattached",
                    name = "ReattachedActivity",
                    iconCacheKey = "reattached-cache-key"
                )
                val adapter = SearchResultAdapter(onAppClick = { _, _ -> })
                adapter.submitList(listOf(app))

                val recyclerView = androidx.recyclerview.widget.RecyclerView(activity).apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
                }

                recyclerView.adapter = adapter
                recyclerView.adapter = null      // detaches -> cancels the scope
                recyclerView.adapter = adapter   // re-attaches the same instance

                // Binding must still work, and the coroutine-backed icon path must still run.
                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)

                assertEquals(
                    "Reattached",
                    holder.itemView.findViewById<TextView>(R.id.tvSearchResultLabel).text.toString()
                )
                assertTrue(
                    "A re-attached adapter must still render icons - a dead scope drops them silently",
                    holder.itemView.findViewById<ImageView>(R.id.ivSearchResultIcon).drawable != null
                )
                assertTrue(
                    "The adapter's scope must be alive again after re-attach",
                    adapter.isScopeActiveForTest()
                )
            }
        }
    }

    /** FEAT-009: the panel passes a callback; the search overlay (existing usage) does not. */
    @Test
    fun removeFromRecentItemIsHiddenWhenNoCallbackIsProvided() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
                val adapter = SearchResultAdapter(onAppClick = { _, _ -> })
                adapter.submitList(listOf(app))

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)
                holder.itemView.findViewById<View>(R.id.llSearchResultMainRow).performLongClick()

                val menuItem = holder.lastActionMenu?.menu?.findItem(R.id.menuItemRemoveFromRecent)
                assertEquals(false, menuItem?.isVisible)
            }
        }
    }

    /** FEAT-009: the panel usage shows the item and tapping it invokes the callback. */
    @Test
    fun removeFromRecentItemIsVisibleAndInvokesCallbackWhenProvided() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
                var removed: App? = null
                val adapter = SearchResultAdapter(
                    onAppClick = { _, _ -> },
                    onRemoveFromRecent = { removed = it }
                )
                adapter.submitList(listOf(app))

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)
                holder.itemView.findViewById<View>(R.id.llSearchResultMainRow).performLongClick()

                val menuItem = holder.lastActionMenu?.menu?.findItem(R.id.menuItemRemoveFromRecent)
                assertEquals(true, menuItem?.isVisible)

                // API 37 cannot inject a real popup-item click (see class docstring); call the
                // exact handler the popup's own click listener dispatches to, same convention as
                // ActHome.onLensMenuItemSelected.
                val handled = holder.handleMenuAction(R.id.menuItemRemoveFromRecent, app, holder.itemView)
                assertEquals(true, handled)
                assertSame(app, removed)
            }
        }
    }

    /**
     * FEAT-009 root-cause fix: the panel context (onRemoveFromRecent != null) must offer exactly
     * App info + Remove from recent - never Pin start/Pin end/Unpin/Uninstall, which only make
     * sense for the search overlay's stable-grid-organization use case, not a transient MRU list.
     */
    @Test
    fun panelContextShowsOnlyAppInfoAndRemoveFromRecent() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = App(
                    label = "Camera",
                    packageName = "pkg.camera",
                    name = "CameraActivity",
                    pinnedZone = PinnedZone.START
                )
                val adapter = SearchResultAdapter(onAppClick = { _, _ -> }, onRemoveFromRecent = { })
                adapter.submitList(listOf(app))

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)
                holder.itemView.findViewById<View>(R.id.llSearchResultMainRow).performLongClick()

                val menu = holder.lastActionMenu!!.menu
                assertEquals(true, menu.findItem(R.id.menuItemElementAppInfo).isVisible)
                assertEquals(true, menu.findItem(R.id.menuItemRemoveFromRecent).isVisible)
                assertEquals(false, menu.findItem(R.id.menuItemPinStart).isVisible)
                assertEquals(false, menu.findItem(R.id.menuItemPinEnd).isVisible)
                // pinnedZone = START would normally make Unpin visible in the search-overlay
                // context - it must still be hidden here.
                assertEquals(false, menu.findItem(R.id.menuItemUnpin).isVisible)
                assertEquals(false, menu.findItem(R.id.menuItemElementUninstall).isVisible)
                // FISH-021: a lens-only entry never belongs in the recent-apps panel.
                assertEquals(false, menu.findItem(R.id.menuItemRemoveFromLens).isVisible)
            }
        }
    }

    /** Regression guard: the search overlay's existing 5-item behavior must stay untouched. */
    @Test
    fun searchOverlayContextStillShowsAllOriginalActions() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = App(
                    label = "Camera",
                    packageName = "pkg.camera",
                    name = "CameraActivity",
                    pinnedZone = PinnedZone.START
                )
                val adapter = SearchResultAdapter(onAppClick = { _, _ -> })
                adapter.submitList(listOf(app))

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)
                holder.itemView.findViewById<View>(R.id.llSearchResultMainRow).performLongClick()

                val menu = holder.lastActionMenu!!.menu
                assertEquals(true, menu.findItem(R.id.menuItemElementAppInfo).isVisible)
                assertEquals(true, menu.findItem(R.id.menuItemPinStart).isVisible)
                assertEquals(true, menu.findItem(R.id.menuItemPinEnd).isVisible)
                assertEquals(true, menu.findItem(R.id.menuItemUnpin).isVisible)
                assertEquals(true, menu.findItem(R.id.menuItemElementUninstall).isVisible)
                // FISH-021: nor in the search overlay, whatever the lens scope is.
                assertEquals(false, menu.findItem(R.id.menuItemRemoveFromLens).isVisible)
                assertEquals(false, menu.findItem(R.id.menuItemRemoveFromRecent).isVisible)
            }
        }
    }
}
