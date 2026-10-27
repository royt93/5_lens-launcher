package com.mckimquyen.search

import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class AppSearchEngineTest {
    @Test
    fun `text search shared result limit is twenty four`() {
        assertEquals(24, AppSearchEngine.SEARCH_RESULT_LIMIT)
    }

    @Test
    fun `normalize ignores case accents and extra whitespace`() {
        assertEquals("dien thoai", AppSearchEngine.normalize(" \t ĐIỆN   THOẠI \n "))
        assertEquals("", AppSearchEngine.normalize(null))
    }

    @Test
    fun `component key includes package and component`() {
        assertEquals(
            "com.vendor.camera/com.vendor.camera.MainActivity",
            AppSearchEngine.componentKey(
                app("Camera", "com.vendor.camera", "com.vendor.camera.MainActivity")
            )
        )
    }

    @Test
    fun `search matches labels and package aliases`() {
        val apps = listOf(
            app("Máy ảnh", "com.vendor.camera", "CameraActivity"),
            app("Ghi chú", "com.vendor.notes", "NotesActivity")
        )

        assertEquals("Máy ảnh", AppSearchEngine.search(apps, "may anh").single().label)
        assertEquals("Ghi chú", AppSearchEngine.search(apps, "notes").single().label)
    }

    @Test
    fun `ranking is deterministic and favors stronger text matches`() {
        val exact = app("Camera", "com.vendor.exact", "Main")
        val prefix = app("Camera Pro", "com.vendor.prefix", "Main", openCount = 99)
        val packageOnly = app("Photos", "com.vendor.camera", "Main", openCount = 999)

        val result = AppSearchEngine.search(listOf(packageOnly, prefix, exact), "camera")

        assertEquals(listOf(exact, prefix, packageOnly), result)
    }

    @Test
    fun `every searchable field has deterministic match precedence`() {
        val exact = app("Camera", "pkg.exact", "Exact")
        val labelPrefix = app("Camera Pro", "pkg.prefix", "Prefix")
        val wordPrefix = app("Pro CameraTool", "pkg.word", "Word")
        val labelContains = app("Pro MyCamera", "pkg.contains", "Contains")
        val packageAlias = app("Alias", "com.vendor.cameraapp", "Alias")
        val packageContains = app("Package", "com.vendor.mycameraapp", "Package")
        val componentContains = app("Component", "com.vendor.component", "CameraActivity")

        assertEquals(
            listOf(exact, labelPrefix, wordPrefix, labelContains, packageAlias, packageContains, componentContains),
            AppSearchEngine.search(
                listOf(componentContains, packageContains, packageAlias, labelContains, wordPrefix, labelPrefix, exact),
                "camera"
            )
        )
    }

    @Test
    fun `ranking tie breakers favor favorite first recent position open count label and component`() {
        val favorite = app("Camera", "pkg.favorite", "Z")
        val recent = app("Camera", "pkg.recent", "Z")
        val popular = app("Camera", "pkg.popular", "Z", openCount = 50)
        val componentA = app("Camera", "pkg.same", "A")
        val componentB = app("Camera", "pkg.same", "B")
        val recentKey = AppSearchEngine.componentKey(recent)

        val result = AppSearchEngine.search(
            listOf(componentB, popular, recent, componentA, favorite),
            "camera",
            recentKeys = listOf(recentKey, recentKey),
            favoriteKeys = setOf(AppSearchEngine.componentKey(favorite))
        )

        assertEquals(listOf(favorite, recent, popular, componentA, componentB), result)
    }

    @Test
    fun `empty query returns only local favorites and recents`() {
        val favorite = app("Favorite", "com.vendor.favorite", "Main")
        val recent = app("Recent", "com.vendor.recent", "Main")
        val unrelated = app("Other", "com.vendor.other", "Main")

        val result = AppSearchEngine.search(
            listOf(unrelated, recent, favorite),
            "",
            recentKeys = listOf(AppSearchEngine.componentKey(recent)),
            favoriteKeys = setOf(AppSearchEngine.componentKey(favorite))
        )

        assertEquals(listOf(favorite, recent), result)
    }

    @Test
    fun `empty query excludes hidden history and respects custom limit`() {
        val first = app("First", "pkg.first", "Main")
        val second = app("Second", "pkg.second", "Main")
        val hidden = app("Hidden", "pkg.hidden", "Main", visible = false)

        val result = AppSearchEngine.search(
            listOf(second, hidden, first),
            null,
            recentKeys = listOf(
                AppSearchEngine.componentKey(first),
                AppSearchEngine.componentKey(hidden),
                AppSearchEngine.componentKey(second)
            ),
            limit = 1
        )

        assertEquals(listOf(first), result)
    }

    @Test
    fun `single-character typo still finds the app via fuzzy fallback`() {
        val chrome = app("Chrome", "com.android.chrome", "Main")
        assertEquals("Chrome", AppSearchEngine.search(listOf(chrome), "chrme").single().label)
    }

    @Test
    fun `fuzzy match ranks below every exact and substring match`() {
        val exactSubstring = app("Chrme Notes", "pkg.substring", "Main") // literally contains "chrme"
        val fuzzyOnly = app("Chrome", "pkg.fuzzy", "Main")

        assertEquals(
            listOf(exactSubstring, fuzzyOnly),
            AppSearchEngine.search(listOf(fuzzyOnly, exactSubstring), "chrme")
        )
    }

    @Test
    fun `two-character typo on a long word still fuzzy matches`() {
        val app = app("Calculator", "pkg.calc", "Main")
        assertEquals("Calculator", AppSearchEngine.search(listOf(app), "calculatr").single().label)
        assertEquals("Calculator", AppSearchEngine.search(listOf(app), "calxulator").single().label)
    }

    @Test
    fun `short token never fuzzy matches to avoid noisy results`() {
        // "ab" (2 chars) is below MIN_FUZZY_TOKEN_LENGTH; must not fuzzy-match "Camera".
        assertTrue(AppSearchEngine.search(listOf(app("Camera", "pkg", "Main")), "ab").isEmpty())
    }

    @Test
    fun `edit distance beyond threshold is rejected not fuzzy matched`() {
        // "xyz" vs "Camera": way more than 2 edits apart, must not match.
        assertTrue(AppSearchEngine.search(listOf(app("Camera", "pkg", "Main")), "xyz").isEmpty())
    }

    @Test
    fun `fuzzy matching preserves accent and case insensitivity`() {
        val app = app("Điện Thoại", "pkg.dienthoai", "Main")
        assertEquals("Điện Thoại", AppSearchEngine.search(listOf(app), "dien thoal").single().label)
    }

    @Test
    fun `hidden apps and unmatched multi-token queries are excluded`() {
        val hidden = app("Secret Camera", "com.vendor.secret", "Main", visible = false)
        val visible = app("Camera", "com.vendor.camera", "Main")

        assertTrue(AppSearchEngine.search(listOf(hidden, visible), "secret").isEmpty())
        assertTrue(AppSearchEngine.search(listOf(visible), "camera missing").isEmpty())
    }

    @Test
    fun `non-positive limit returns no results`() {
        assertTrue(AppSearchEngine.search(listOf(app("Camera", "pkg", "Main")), "camera", limit = 0).isEmpty())
        assertTrue(AppSearchEngine.search(listOf(app("Camera", "pkg", "Main")), "camera", limit = -1).isEmpty())
    }

    @Test
    fun `default and custom limits bound results`() {
        val apps = (0 until 40).map { app("Camera $it", "pkg.$it", "Main") }

        assertEquals(24, AppSearchEngine.search(apps, "camera").size)
        assertEquals(3, AppSearchEngine.search(apps, "camera", limit = 3).size)
    }

    @Test
    fun `empty apps and whitespace query are safe`() {
        assertTrue(AppSearchEngine.search(emptyList(), "camera").isEmpty())
        assertTrue(AppSearchEngine.search(listOf(app("Camera", "pkg", "Main")), " \t\n ").isEmpty())
    }

    @Test
    fun `search stays within startup budget for more than 300 apps`() {
        val apps = (0 until 400).map { index ->
            app("Application $index", "com.example.application$index", "MainActivity")
        }
        repeat(5) { AppSearchEngine.search(apps, "application 39") }

        val elapsedMs = measureNanoTime {
            repeat(20) { AppSearchEngine.search(apps, "application 39") }
        } / 1_000_000.0 / 20

        assertTrue("Average search took ${elapsedMs}ms", elapsedMs < 150.0)
    }

    private fun app(
        label: String,
        packageName: String,
        name: String,
        openCount: Long = 0,
        visible: Boolean = true
    ) = App(
        label = label,
        packageName = packageName,
        name = name,
        openCount = openCount,
        isVisible = visible
    )

    // ---- FEAT-013: recent installs ----

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private fun installed(pkg: String, ago: Long, label: String = pkg, visible: Boolean = true, name: String = "Main") =
        App(label = label, packageName = pkg, name = name, installDate = now - ago, isVisible = visible)

    @Test
    fun `recent install window is seven days`() {
        assertEquals(7, AppSearchEngine.RECENT_INSTALL_DAYS)
    }

    @Test
    fun `an app installed exactly seven days ago is recent and one millisecond older is not`() {
        val onEdge = installed("com.edge", 7 * day)
        val tooOld = installed("com.old", 7 * day + 1)

        assertEquals(listOf(onEdge), AppSearchEngine.recentInstalls(listOf(tooOld, onEdge), now))
    }

    @Test
    fun `recent installs are newest first with deterministic ties`() {
        val newest = installed("com.newest", 1 * day)
        val middle = installed("com.middle", 3 * day)
        val tieB = installed("com.tie.b", 5 * day, label = "Beta")
        val tieA = installed("com.tie.a", 5 * day, label = "Alpha")

        assertEquals(
            listOf(newest, middle, tieA, tieB),
            AppSearchEngine.recentInstalls(listOf(tieB, middle, tieA, newest), now)
        )
    }

    @Test
    fun `hidden apps and apps without an install date are excluded`() {
        val visible = installed("com.visible", 1 * day)
        val hidden = installed("com.hidden", 1 * day, visible = false)
        val unknown = App(label = "Unknown", packageName = "com.unknown", name = "Main", installDate = 0L)

        assertEquals(listOf(visible), AppSearchEngine.recentInstalls(listOf(hidden, unknown, visible), now))
    }

    @Test
    fun `an install date in the future counts as recent`() {
        val skewed = App(label = "Skewed", packageName = "com.skew", name = "Main", installDate = now + day)

        assertEquals(listOf(skewed), AppSearchEngine.recentInstalls(listOf(skewed), now))
    }

    @Test
    fun `recent installs are capped and a non-positive limit is empty`() {
        val apps = (1..40).map { installed("com.app$it", it * 60_000L) }

        assertEquals(AppSearchEngine.SEARCH_RESULT_LIMIT, AppSearchEngine.recentInstalls(apps, now).size)
        assertEquals(apps.take(5), AppSearchEngine.recentInstalls(apps, now, limit = 5))
        assertTrue(AppSearchEngine.recentInstalls(apps, now, limit = 0).isEmpty())
    }

    @Test
    fun `a work profile copy installed recently is listed separately from the personal copy`() {
        val personal = installed("com.chat", 10 * day)
        val work = App(
            label = "Chat", packageName = "com.chat", name = "Main", installDate = now - 1 * day,
            userId = 10, isWorkProfile = true
        )

        assertEquals(listOf(work), AppSearchEngine.recentInstalls(listOf(personal, work), now))
    }

    @Test
    fun `each keyword and its accent or case variants list the recent installs`() {
        val recent = installed("com.recent", 1 * day, label = "Zeta")
        val old = installed("com.old", 30 * day, label = "Alpha")
        val apps = listOf(old, recent)

        listOf("new", "NEW", " recent ", "mới cài", "MOI CAI", "vừa cài", "vua cai").forEach { query ->
            assertEquals(query, listOf(recent), AppSearchEngine.search(apps, query, nowMillis = now))
        }
    }

    @Test
    fun `a longer query that merely contains a keyword uses normal search`() {
        val newYork = App(label = "New York Times", packageName = "com.nyt", name = "Main", installDate = now - 40 * day)
        val recent = installed("com.recent", 1 * day, label = "Zeta")

        assertEquals(listOf(newYork), AppSearchEngine.search(listOf(newYork, recent), "new york", nowMillis = now))
    }

    @Test
    fun `a keyword query does not return old apps and ignores history and favorites`() {
        val old = installed("com.old", 30 * day, label = "New Notes")
        val recent = installed("com.recent", 2 * day, label = "Zeta")

        val result = AppSearchEngine.search(
            listOf(old, recent), "new",
            recentKeys = listOf(AppSearchEngine.componentKey(old)),
            favoriteKeys = setOf(AppSearchEngine.componentKey(old)),
            nowMillis = now
        )

        assertEquals(listOf(recent), result)
    }

    @Test
    fun `keyword queries respect hidden apps and the explicit limit`() {
        val hidden = installed("com.hidden", 1 * day, visible = false)
        val apps = (1..10).map { installed("com.app$it", it * 60_000L) } + hidden

        assertEquals(3, AppSearchEngine.search(apps, "new", limit = 3, nowMillis = now).size)
        assertTrue(AppSearchEngine.search(apps, "new", nowMillis = now).none { it.packageName == "com.hidden" })
        assertTrue(AppSearchEngine.search(apps, "new", limit = 0, nowMillis = now).isEmpty())
    }

    @Test
    fun `blank and unrelated queries behave exactly as before`() {
        val recent = installed("com.recent", 1 * day, label = "Zeta")

        assertTrue(AppSearchEngine.search(listOf(recent), "", nowMillis = now).isEmpty())
        assertEquals(listOf(recent), AppSearchEngine.search(listOf(recent), "zeta", nowMillis = now))
        assertTrue(AppSearchEngine.search(listOf(recent), "newer", nowMillis = now).isEmpty())
    }

    @Test
    fun `an unknown install date is never recent even when the clock itself is near zero`() {
        val unknown = App(label = "Unknown", packageName = "com.unknown", name = "Main", installDate = 0L)
        val negative = App(label = "Negative", packageName = "com.negative", name = "Main", installDate = -5L)

        assertTrue(AppSearchEngine.recentInstalls(listOf(unknown, negative), nowMillis = day).isEmpty())
    }
}
