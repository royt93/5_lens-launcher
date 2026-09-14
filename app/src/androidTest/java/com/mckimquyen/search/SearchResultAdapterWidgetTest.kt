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

    @Test
    fun submitBindAndClickExposeCurrentAppAndAccessibleContent() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val first = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
                val second = App(label = "Notes", packageName = "pkg.notes", name = "NotesActivity")
                var clickedApp: App? = null
                var clickedSource: android.view.View? = null
                val adapter = SearchResultAdapter { app, source ->
                    clickedApp = app
                    clickedSource = source
                }

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
                val adapter = SearchResultAdapter { _, _ -> }
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
                val adapter = SearchResultAdapter { _, _ -> }
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
                val adapter = SearchResultAdapter { _, _ -> }
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
                        R.id.menuItemElementUninstall
                    ),
                    ids
                )
                // No favorite/folder/move items - those need a stable grid position search
                // results don't have.
                assertNull(menu.findItem(R.id.menuItemFavorite))
                assertNull(menu.findItem(R.id.menuItemFolder))
                assertNull(menu.findItem(R.id.menuItemMoveEarlier))
                assertNull(menu.findItem(R.id.menuItemMoveLater))
            }
        }
    }
}
