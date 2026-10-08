# FISH-021 Per-lens app scope, freeze order, quick lens switch — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let each lens show its own subset of apps, freeze its app positions, and be reached quickly by tapping its name or by launcher shortcuts.

**Architecture:** Per-lens state lives in `UtilSettings` (SharedPreferences keys suffixed `_<lensId>`, no Room change). A pure `LensAppScope.filter` is applied in `ActHome` right before `LensView.setApps`. One `LensAppScopeEditor` is the only writer, used by three UI entry points. Freeze reuses `AppPersistent.setOrders` plus one new DAO query. Quick switch reuses `lensPager`, `onNewIntent` and `ShortcutManagerCompat`.

**Tech Stack:** Kotlin + Java, Room 2.8.4 (no schema change), SharedPreferences, ViewPager2, androidx `ShortcutManagerCompat`, JUnit4 + Robolectric (JVM), AndroidX Test (instrumented).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-08-lens-app-scope-design.md`. Out of scope: search filtering, new gestures on the lens grid, `LayoutBackup` of the selection, Room schema/migration.
- A lens with no stored scope is `ALL`. The `default` lens uses the unsuffixed key, every other lens `<key>_<lensId>`. Scope, selection and frozen keys must NOT fall back to another lens's key.
- Selection is a set of `AppPersistent.generateIdentifier(packageName, name)` strings. Never expose the live `SharedPreferences` set.
- Do not use `appVisible` for scope (`setOrders` and `defaults()` create rows with `appVisible = true`).
- No magic numbers or strings: named constants. No `late`/`!!` without a justifying comment. Every dialog/listener/popup released in `onDestroy`.
- Every new user-facing string goes into `res/values/strings.xml` AND all 16 locale files (`AllStringsTranslationTest` enforces it).
- Device: OPPO CPH1989 `FUJZIFIR7DQCNRWW` only. TECNO, S24U, Pixel are banned. Use `adb -s FUJZIFIR7DQCNRWW`. Never run a Gradle `connected*` task while another device is attached; install with `ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew installDevDebug installDevDebugAndroidTest`, then `am instrument`.
- A failing instrumented test is compared against baseline before being called pre-existing.
- Before each commit run `bash scripts/check-secrets.sh`. Commit messages end with `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

## File Structure

- Create `app/src/main/java/com/mckimquyen/util/LensAppScope.kt` — enum + pure filter.
- Create `app/src/main/java/com/mckimquyen/util/LensAppScopeEditor.kt` — the only writer of scope/selection.
- Modify `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` — keys, getters/savers, duplicate/delete.
- Modify `app/src/main/java/com/mckimquyen/ui/ActHome.java` — apply scope, checklist dialog, freeze, quick switch.
- Modify `app/src/main/java/com/mckimquyen/ui/FrmApps.kt`, `res/menu/menu_apps_selection.xml` — Apps tab add/remove.
- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt`, `res/menu/menu_search_result.xml` — grid remove, skip Smart Focus when frozen.
- Modify `app/src/main/java/com/mckimquyen/model/AppPersistentDao.kt` — `clearOrderForLens`.
- Create `app/src/main/java/com/mckimquyen/util/LensShortcuts.kt` — dynamic shortcuts.
- Tests: `app/src/test/java/com/mckimquyen/util/{LensAppScopeTest,UtilSettingsLensScopeTest,LensAppScopeEditorTest}.kt`, plus instrumented tests named per task.

---

### Task 1: `LensAppScope` and per-lens settings

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/LensAppScope.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` (companion keys near `KEY_ACTIVE_LENS_ID` line ~170; helpers near `getIconSize` line ~386; `duplicateLensSettings` ~449; `deleteLensSettings` ~465)
- Test: `app/src/test/java/com/mckimquyen/util/LensAppScopeTest.kt`, `app/src/test/java/com/mckimquyen/util/UtilSettingsLensScopeTest.kt`

**Interfaces:**
- Produces: `enum class LensAppScope { ALL, SELECTED }` with `companion fun fromStored(value: String?): LensAppScope`, `fun filter(apps: List<App>, scope: LensAppScope, selection: Set<String>): ArrayList<App>`, `fun identifierOf(app: App): String`.
- Produces on `UtilSettings`: `getLensAppScope(lensId: String?): LensAppScope`, `saveLensAppScope(lensId: String?, scope: LensAppScope)`, `getLensAppSelection(lensId: String?): Set<String>`, `saveLensAppSelection(lensId: String?, ids: Set<String>)`, `isLensFrozen(lensId: String?): Boolean`, `saveLensFrozen(lensId: String?, frozen: Boolean)`; constants `KEY_LENS_APP_SCOPE = "lens_app_scope"`, `KEY_LENS_APP_SELECTION = "lens_app_selection"`, `KEY_LENS_FROZEN = "lens_frozen"`.

- [ ] **Step 1: Write the failing filter test**

```kotlin
package com.mckimquyen.util

import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensAppScopeTest {

    private fun app(pkg: String, name: String) =
        App(id = 1, label = pkg, packageName = pkg, name = name)

    private val mail = app("com.mail", "Main")
    private val chat = app("com.chat", "Main")
    private val apps = listOf(mail, chat)

    @Test
    fun `ALL returns every app as a new list`() {
        val result = LensAppScope.filter(apps, LensAppScope.ALL, emptySet())
        assertEquals(apps, result)
        assertNotSame(apps, result)
    }

    @Test
    fun `SELECTED keeps only apps whose identifier is selected`() {
        val result = LensAppScope.filter(
            apps, LensAppScope.SELECTED, setOf(LensAppScope.identifierOf(chat))
        )
        assertEquals(listOf(chat), result)
    }

    @Test
    fun `SELECTED with an empty selection returns nothing`() {
        assertEquals(emptyList<App>(), LensAppScope.filter(apps, LensAppScope.SELECTED, emptySet()))
    }

    @Test
    fun `identifiers that match no installed app are ignored`() {
        val result = LensAppScope.filter(
            apps, LensAppScope.SELECTED, setOf("gone-App", LensAppScope.identifierOf(mail))
        )
        assertEquals(listOf(mail), result)
    }

    @Test
    fun `fromStored falls back to ALL for null or unknown values`() {
        assertEquals(LensAppScope.ALL, LensAppScope.fromStored(null))
        assertEquals(LensAppScope.ALL, LensAppScope.fromStored("bogus"))
        assertEquals(LensAppScope.SELECTED, LensAppScope.fromStored("SELECTED"))
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensAppScopeTest`
Expected: FAIL — `Unresolved reference: LensAppScope`.

- [ ] **Step 3: Implement `LensAppScope.kt`**

```kotlin
package com.mckimquyen.util

import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent

/** FISH-021: which apps a lens shows. [ALL] is the default, so existing lenses are unchanged. */
enum class LensAppScope {
    ALL,
    SELECTED;

    companion object {
        @JvmStatic
        fun fromStored(value: String?): LensAppScope =
            entries.firstOrNull { it.name == value } ?: ALL

        @JvmStatic
        fun identifierOf(app: App): String =
            AppPersistent.generateIdentifier(app.packageName.toString(), app.name.toString())

        /** Pure and Android-free apart from [App]; always returns a fresh list. */
        @JvmStatic
        fun filter(apps: List<App>, scope: LensAppScope, selection: Set<String>): ArrayList<App> =
            when (scope) {
                ALL -> ArrayList(apps)
                SELECTED -> ArrayList(apps.filter { identifierOf(it) in selection })
            }
    }
}
```

- [ ] **Step 4: Run it, expect PASS**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensAppScopeTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Write the failing settings test**

```kotlin
package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsLensScopeTest {

    private lateinit var context: Context // assigned in @Before, which JUnit runs before every test
    private lateinit var settings: UtilSettings

    private val work = "work-lens-id"
    private val personal = "personal-lens-id"
    private val default = LensWorkspace.DEFAULT_LENS_ID

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun `keys are stable`() {
        assertEquals("lens_app_scope", UtilSettings.KEY_LENS_APP_SCOPE)
        assertEquals("lens_app_selection", UtilSettings.KEY_LENS_APP_SELECTION)
        assertEquals("lens_frozen", UtilSettings.KEY_LENS_FROZEN)
    }

    @Test
    fun `a fresh lens is ALL, empty and not frozen`() {
        assertEquals(LensAppScope.ALL, settings.getLensAppScope(work))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(work))
        assertFalse(settings.isLensFrozen(work))
    }

    @Test
    fun `a non-default lens never inherits the default lens values`() {
        settings.saveLensAppScope(default, LensAppScope.SELECTED)
        settings.saveLensAppSelection(default, setOf("a-A"))
        settings.saveLensFrozen(default, true)

        assertEquals(LensAppScope.ALL, settings.getLensAppScope(work))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(work))
        assertFalse(settings.isLensFrozen(work))
    }

    @Test
    fun `the default lens uses the unsuffixed keys and others the suffixed ones`() {
        settings.saveLensAppScope(default, LensAppScope.SELECTED)
        settings.saveLensAppScope(work, LensAppScope.SELECTED)
        assertTrue(rawPrefs().contains("lens_app_scope"))
        assertTrue(rawPrefs().contains("lens_app_scope_$work"))
    }

    @Test
    fun `saving for one lens does not change another`() {
        settings.saveLensAppSelection(work, setOf("a-A"))
        settings.saveLensAppSelection(personal, setOf("b-B"))
        assertEquals(setOf("a-A"), settings.getLensAppSelection(work))
        assertEquals(setOf("b-B"), settings.getLensAppSelection(personal))
    }

    @Test
    fun `mutating the set passed in later does not change what was stored`() {
        val ids = mutableSetOf("a-A")
        settings.saveLensAppSelection(work, ids)
        ids.add("b-B")
        assertEquals(setOf("a-A"), settings.getLensAppSelection(work))
    }

    @Test
    fun `a wrong-typed stored value degrades to the defaults instead of crashing`() {
        rawPrefs().edit()
            .putInt("lens_app_scope_$work", 7)
            .putString("lens_app_selection_$work", "oops")
            .putString("lens_frozen_$work", "yes")
            .commit()
        assertEquals(LensAppScope.ALL, settings.getLensAppScope(work))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(work))
        assertFalse(settings.isLensFrozen(work))
    }

    @Test
    fun `duplicate copies scope selection and frozen flag`() {
        settings.saveLensAppScope(work, LensAppScope.SELECTED)
        settings.saveLensAppSelection(work, setOf("a-A"))
        settings.saveLensFrozen(work, true)

        settings.duplicateLensSettings(work, personal)

        assertEquals(LensAppScope.SELECTED, settings.getLensAppScope(personal))
        assertEquals(setOf("a-A"), settings.getLensAppSelection(personal))
        assertTrue(settings.isLensFrozen(personal))
    }

    @Test
    fun `delete removes all three keys so a reused id starts clean`() {
        settings.saveLensAppScope(work, LensAppScope.SELECTED)
        settings.saveLensAppSelection(work, setOf("a-A"))
        settings.saveLensFrozen(work, true)

        settings.deleteLensSettings(work)

        assertEquals(LensAppScope.ALL, settings.getLensAppScope(work))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(work))
        assertFalse(settings.isLensFrozen(work))
    }
}
```

- [ ] **Step 6: Run it, expect a compile failure**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsLensScopeTest`
Expected: FAIL — unresolved `KEY_LENS_APP_SCOPE` / `getLensAppScope`.

- [ ] **Step 7: Implement in `UtilSettings.kt`**

Add inside the `companion object`, after `const val KEY_ACTIVE_LENS_ID = "active_lens_id"`:

```kotlin
        /** FISH-021: per-lens app scope, selection and frozen flag (suffixed `_<lensId>`, never inherited). */
        const val KEY_LENS_APP_SCOPE = "lens_app_scope"
        const val KEY_LENS_APP_SELECTION = "lens_app_selection"
        const val KEY_LENS_FROZEN = "lens_frozen"
```

Add after `saveIconSize`:

```kotlin
    // FISH-021: unlike distortion/icon size these never fall back to the shared key - the default
    // lens owns the unsuffixed key, so a fallback would copy its selection into every new lens.
    // Each read goes through runCatching so one wrong-typed value can never crash a draw or bind.
    fun getLensAppScope(lensId: String?): LensAppScope =
        runCatching { prefs.getString(lensKey(KEY_LENS_APP_SCOPE, lensId), null) }
            .getOrNull()
            .let(LensAppScope::fromStored)

    fun saveLensAppScope(lensId: String?, scope: LensAppScope) {
        save(lensKey(KEY_LENS_APP_SCOPE, lensId), scope.name)
    }

    fun getLensAppSelection(lensId: String?): Set<String> =
        runCatching { prefs.getStringSet(lensKey(KEY_LENS_APP_SELECTION, lensId), null)?.toSet() }
            .getOrNull() ?: emptySet()

    fun saveLensAppSelection(lensId: String?, ids: Set<String>) {
        // Copy: SharedPreferences must never be handed a set the caller can still mutate.
        prefs.edit { putStringSet(lensKey(KEY_LENS_APP_SELECTION, lensId), HashSet(ids)) }
    }

    fun isLensFrozen(lensId: String?): Boolean =
        runCatching { prefs.getBoolean(lensKey(KEY_LENS_FROZEN, lensId), false) }.getOrDefault(false)

    fun saveLensFrozen(lensId: String?, frozen: Boolean) {
        save(lensKey(KEY_LENS_FROZEN, lensId), frozen)
    }
```

In `duplicateLensSettings`, after `saveIconSize(toLensId, getIconSize(fromLensId))` add:

```kotlin
        saveLensAppScope(toLensId, getLensAppScope(fromLensId))
        saveLensAppSelection(toLensId, getLensAppSelection(fromLensId))
        saveLensFrozen(toLensId, isLensFrozen(fromLensId))
```

In `deleteLensSettings`, inside the `prefs.edit { ... }` block add:

```kotlin
                remove("${KEY_LENS_APP_SCOPE}_$lensId")
                remove("${KEY_LENS_APP_SELECTION}_$lensId")
                remove("${KEY_LENS_FROZEN}_$lensId")
```

- [ ] **Step 8: Run both test classes, expect PASS**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensAppScopeTest --tests com.mckimquyen.util.UtilSettingsLensScopeTest`
Expected: PASS (14 tests).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/LensAppScope.kt app/src/main/java/com/mckimquyen/util/UtilSettings.kt app/src/test/java/com/mckimquyen/util/LensAppScopeTest.kt app/src/test/java/com/mckimquyen/util/UtilSettingsLensScopeTest.kt
bash scripts/check-secrets.sh
git commit -m "feat(lens): per-lens app scope, selection and frozen settings

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: `LensAppScopeEditor` (the only writer)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/LensAppScopeEditor.kt`
- Test: `app/src/test/java/com/mckimquyen/util/LensAppScopeEditorTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.getLensAppScope/saveLensAppScope/getLensAppSelection/saveLensAppSelection` (Task 1).
- Produces: `class LensAppScopeEditor(private val settings: UtilSettings)` with `effectiveIds(lensId: String?, allIds: Set<String>): Set<String>`, `add(lensId: String?, ids: Set<String>, allIds: Set<String>): LensAppScope`, `remove(lensId: String?, ids: Set<String>, allIds: Set<String>): LensAppScope`, `apply(lensId: String?, ids: Set<String>): LensAppScope`. Each returns the scope the lens ends up with. A lens can never end up `SELECTED` with an empty selection.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensAppScopeEditorTest {

    private lateinit var settings: UtilSettings // assigned in @Before, which JUnit runs before every test
    private lateinit var editor: LensAppScopeEditor

    private val lens = "work-lens-id"
    private val all = setOf("a-A", "b-B", "c-C")

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
        editor = LensAppScopeEditor(settings)
    }

    @Test
    fun `apply with ids selects them and switches the lens to SELECTED`() {
        assertEquals(LensAppScope.SELECTED, editor.apply(lens, setOf("a-A")))
        assertEquals(LensAppScope.SELECTED, settings.getLensAppScope(lens))
        assertEquals(setOf("a-A"), settings.getLensAppSelection(lens))
    }

    @Test
    fun `apply with no ids falls back to ALL and clears the selection`() {
        editor.apply(lens, setOf("a-A"))
        assertEquals(LensAppScope.ALL, editor.apply(lens, emptySet()))
        assertEquals(LensAppScope.ALL, settings.getLensAppScope(lens))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(lens))
    }

    @Test
    fun `effectiveIds is every app for ALL and the selection for SELECTED`() {
        assertEquals(all, editor.effectiveIds(lens, all))
        editor.apply(lens, setOf("a-A"))
        assertEquals(setOf("a-A"), editor.effectiveIds(lens, all))
    }

    @Test
    fun `remove on an ALL lens selects everything except the removed apps`() {
        assertEquals(LensAppScope.SELECTED, editor.remove(lens, setOf("b-B"), all))
        assertEquals(setOf("a-A", "c-C"), settings.getLensAppSelection(lens))
    }

    @Test
    fun `remove of the last selected app reverts the lens to ALL`() {
        editor.apply(lens, setOf("a-A"))
        assertEquals(LensAppScope.ALL, editor.remove(lens, setOf("a-A"), all))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(lens))
    }

    @Test
    fun `add extends a SELECTED lens`() {
        editor.apply(lens, setOf("a-A"))
        assertEquals(LensAppScope.SELECTED, editor.add(lens, setOf("c-C"), all))
        assertEquals(setOf("a-A", "c-C"), settings.getLensAppSelection(lens))
    }

    @Test
    fun `add on an ALL lens keeps every app selected`() {
        assertEquals(LensAppScope.SELECTED, editor.add(lens, setOf("a-A"), all))
        assertEquals(all, settings.getLensAppSelection(lens))
    }

    @Test
    fun `editing one lens leaves another untouched`() {
        editor.apply(lens, setOf("a-A"))
        assertEquals(LensAppScope.ALL, settings.getLensAppScope("other-lens-id"))
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensAppScopeEditorTest`
Expected: FAIL — `Unresolved reference: LensAppScopeEditor`.

- [ ] **Step 3: Implement `LensAppScopeEditor.kt`**

```kotlin
package com.mckimquyen.util

/**
 * FISH-021: the single writer of a lens's app scope. The Apps tab, the lens-menu checklist and
 * the grid's "Remove from this lens" all go through here, so none of them can leave a lens
 * SELECTED with nothing in it (a blank home screen).
 */
class LensAppScopeEditor(private val settings: UtilSettings) {

    /** The apps the lens shows right now, given every installed app id. */
    fun effectiveIds(lensId: String?, allIds: Set<String>): Set<String> =
        when (settings.getLensAppScope(lensId)) {
            LensAppScope.ALL -> allIds
            LensAppScope.SELECTED -> settings.getLensAppSelection(lensId)
        }

    fun add(lensId: String?, ids: Set<String>, allIds: Set<String>): LensAppScope =
        apply(lensId, effectiveIds(lensId, allIds) + ids)

    fun remove(lensId: String?, ids: Set<String>, allIds: Set<String>): LensAppScope =
        apply(lensId, effectiveIds(lensId, allIds) - ids)

    /** Replaces the selection. An empty result means "show everything", never a blank lens. */
    fun apply(lensId: String?, ids: Set<String>): LensAppScope {
        val scope = if (ids.isEmpty()) LensAppScope.ALL else LensAppScope.SELECTED
        settings.saveLensAppSelection(lensId, ids)
        settings.saveLensAppScope(lensId, scope)
        return scope
    }
}
```

- [ ] **Step 4: Run it, expect PASS**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensAppScopeEditorTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/LensAppScopeEditor.kt app/src/test/java/com/mckimquyen/util/LensAppScopeEditorTest.kt
bash scripts/check-secrets.sh
git commit -m "feat(lens): LensAppScopeEditor, the single writer of per-lens app scope

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Apply the scope on the home lenses

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/services/AppEventManager.kt` (add `lensScopeChanged`)
- Modify: `app/src/main/java/com/mckimquyen/util/LensAppScopeEditor.kt` (notify after `apply`)
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt:284` (test accessor)
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java` (the three `setApps(listApp)` sites at ~207, ~669, ~1872; observer in `onCreate` next to the other `AppEventManager` observers at ~305-322)
- Test: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensScopeWidgetTest.kt`

**Interfaces:**
- Consumes: `LensAppScope.filter`, `UtilSettings.getLensAppScope/getLensAppSelection`, `LensAppScopeEditor.apply` (Tasks 1-2).
- Produces: `AppEventManager.lensScopeChanged: LiveData<Any?>` and `AppEventManager.notifyLensScopeChanged(data: Any? = null)`; `ActHome.refreshLensScope()` (package-private, `@VisibleForTesting`); `LensView.appsForTest: List<App>?` (internal).

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.LensAppScopeEditor
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensScopeWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private fun app(pkg: String) =
        App(id = pkg.hashCode(), label = pkg, packageName = pkg, name = "Main", isVisible = true)

    private val mail = app("com.test.scope.mail")
    private val chat = app("com.test.scope.chat")
    private val maps = app("com.test.scope.maps")

    @Before
    fun setup() {
        // Restore the real snapshot afterwards; clearing it breaks later tests that need apps.
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(mail, chat, maps)
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    @After
    fun tearDown() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
        RAppsSingleton.instance.apps = originalApps
    }

    private fun shownLabels(scenario: ActivityScenario<ActHome>): List<String> {
        var labels = emptyList<String>()
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                labels = activity.findViewById<LensView>(R.id.lensViews)
                    ?.appsForTest?.map { it.label.toString() }.orEmpty()
            }
            if (labels.isNotEmpty()) break
            Thread.sleep(POLL_MS)
        }
        return labels
    }

    @Test
    fun allScopeShowsEveryApp() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(setOf(mail, chat, maps).map { it.label.toString() }.toSet(), shownLabels(scenario).toSet())
        }
    }

    @Test
    fun selectedScopeShowsOnlyTheSelectedApps() {
        LensAppScopeEditor(UtilSettings(context)).apply(lensId, setOf(LensAppScope.identifierOf(chat)))
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(listOf(chat.label.toString()), shownLabels(scenario))
        }
    }

    @Test
    fun changingTheSelectionWhileOpenRefreshesTheGrid() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(3, shownLabels(scenario).size)
            LensAppScopeEditor(UtilSettings(context)).apply(lensId, setOf(LensAppScope.identifierOf(mail)))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var labels = emptyList<String>()
            val deadline = System.currentTimeMillis() + WAIT_MS
            while (System.currentTimeMillis() < deadline && labels.size != 1) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    labels = it.findViewById<LensView>(R.id.lensViews).appsForTest?.map { a -> a.label.toString() }.orEmpty()
                }
                Thread.sleep(POLL_MS)
            }
            assertEquals(listOf(mail.label.toString()), labels)
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew assembleDevDebugAndroidTest`
Expected: FAIL — `Unresolved reference: appsForTest`.

- [ ] **Step 3: Add the event and the test accessor**

In `AppEventManager.kt`, after `val appsEdited: LiveData<Any?> = _appsEdited`:

```kotlin
    // FISH-021: a lens's app selection changed; ActHome re-filters every bound page.
    private val _lensScopeChanged = MutableLiveData<Any?>()
    val lensScopeChanged: LiveData<Any?> = _lensScopeChanged
```

and after `notifyAppsEdited`:

```kotlin
    fun notifyLensScopeChanged(data: Any? = null) {
        _lensScopeChanged.postValue(data)
    }
```

In `LensView.kt`, right after `private var mApps: ArrayList<App>? = null` (line 284):

```kotlin
    internal val appsForTest: List<App>? get() = mApps
```

In `LensAppScopeEditor.apply`, add the import `import com.mckimquyen.services.AppEventManager` and, as the last line of `apply` before `return scope`:

```kotlin
        AppEventManager.notifyLensScopeChanged(lensId)
```

- [ ] **Step 4: Apply the scope in `ActHome.java`**

Add the import `import com.mckimquyen.util.LensAppScope;`. Add next to `assignApps`:

```java
    /** FISH-021: {@code listApp} narrowed by one lens's scope. Search keeps using the full {@code listApp}. */
    private ArrayList<App> scopedApps(String lensId) {
        if (listApp == null || utilSettings == null) return listApp;
        return LensAppScope.filter(listApp,
                utilSettings.getLensAppScope(lensId), utilSettings.getLensAppSelection(lensId));
    }

    /** FISH-021: re-applies each bound page's scope after a selection change. */
    @androidx.annotation.VisibleForTesting
    void refreshLensScope() {
        if (listApp == null) return;
        for (int i = 0; i < currentLenses.size(); i++) {
            LensView page = lensViewAt(i);
            if (page != null) {
                page.setApps(scopedApps(page.getLensId()));
            }
        }
    }
```

Replace the three call sites (do not touch anything else on those lines):
- ~207 in `onPageSelected`: `view.setApps(listApp);` → `view.setApps(scopedApps(view.getLensId()));`
- ~669 in `bindLensView`: `view.setApps(listApp);` → `view.setApps(scopedApps(view.getLensId()));`
- ~1872 in `assignApps`: `lensViews.setApps(listApp);` → `lensViews.setApps(scopedApps(lensViews.getLensId()));`

Add after the `getLockChanged().observe(...)` block in `onCreate`:

```java
        AppEventManager.INSTANCE.getLensScopeChanged().observe(this, data -> refreshLensScope());
```

- [ ] **Step 5: Run the instrumented test on the OPPO**

```bash
ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew installDevDebug installDevDebugAndroidTest
adb -s FUJZIFIR7DQCNRWW shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensScopeWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: `OK (3 tests)`.

- [ ] **Step 6: Run the unit tests and the neighbouring lens tests, expect no regression**

Run: `./gradlew testDevDebugUnitTest` → all pass.
Then on the OPPO: `-e class com.mckimquyen.ui.ActHomeLensManagementWidgetTest,com.mckimquyen.ui.ActHomeLensMediatorLeakTest` → `OK`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mckimquyen/services/AppEventManager.kt app/src/main/java/com/mckimquyen/util/LensAppScopeEditor.kt app/src/main/java/com/mckimquyen/views/LensView.kt app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensScopeWidgetTest.kt
bash scripts/check-secrets.sh
git commit -m "feat(lens): apply per-lens app scope to the home lens pages

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Checklist dialog from the lens menu

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java` (constant near `MENU_ID_TOGGLE_CLEAN_LENS` ~108; menu item in `showLensManagementMenu` after the `MENU_ID_TOGGLE_CLEAN_LENS` item ~849; branch in `onLensMenuItemSelected` before the final `return false`; new methods after `showLensNameDialog`)
- Modify: `app/src/main/res/values/strings.xml` and the 16 locale `app/src/main/res/values-*/strings.xml` (one new string each)
- Test: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensAppsDialogWidgetTest.kt`

**Interfaces:**
- Consumes: `LensAppScopeEditor.effectiveIds/apply` (Task 2), `LensAppScope.identifierOf` (Task 1), the existing `lensDialog` field (released in `onDestroy`), `ActHome.listApp`.
- Produces: `ActHome.MENU_ID_LENS_APPS = 9` (`@VisibleForTesting`, package-private); `ActHome.showLensAppsDialog(LensWorkspace lens)` (`@VisibleForTesting`); string `lens_choose_apps`.

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
package com.mckimquyen.ui

import android.content.DialogInterface
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensAppsDialogWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private fun app(pkg: String) =
        App(id = pkg.hashCode(), label = pkg, packageName = pkg, name = "Main", isVisible = true)

    private val mail = app("com.test.dialog.mail")
    private val chat = app("com.test.dialog.chat")

    @Before
    fun setup() {
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(mail, chat)
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    @After
    fun tearDown() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
        RAppsSingleton.instance.apps = originalApps
    }

    private fun waitForApps(scenario: ActivityScenario<ActHome>) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var ready = false
        while (System.currentTimeMillis() < deadline && !ready) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { ready = it.lensDialog == null && !it.isFinishing }
            Thread.sleep(POLL_MS)
        }
    }

    @Test
    fun menuEntryOpensTheChecklistWithEveryAppTickedForAnAllLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForApps(scenario)
            scenario.onActivity { activity ->
                assertTrue(activity.onLensMenuItemSelected(ActHome.MENU_ID_LENS_APPS, 0))
                val dialog = activity.lensDialog
                assertNotNull("The checklist dialog must be showing", dialog)
                val list: ListView = dialog!!.listView
                val checked = (0 until list.count).count { list.isItemChecked(it) }
                assertEquals("An ALL lens starts with every app ticked", list.count, checked)
            }
        }
    }

    @Test
    fun confirmingASubsetSelectsOnlyThoseApps() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForApps(scenario)
            scenario.onActivity { activity ->
                activity.onLensMenuItemSelected(ActHome.MENU_ID_LENS_APPS, 0)
                val dialog = activity.lensDialog!!
                val list = dialog.listView
                val keepIndex = (0 until list.count).first {
                    list.adapter.getItem(it).toString() == chat.label.toString()
                }
                (0 until list.count).forEach { list.setItemChecked(it, it == keepIndex) }
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val settings = UtilSettings(context)
            assertEquals(LensAppScope.SELECTED, settings.getLensAppScope(lensId))
            assertEquals(setOf(LensAppScope.identifierOf(chat)), settings.getLensAppSelection(lensId))
        }
    }

    @Test
    fun untickingEverythingKeepsTheLensOnAll() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForApps(scenario)
            scenario.onActivity { activity ->
                activity.onLensMenuItemSelected(ActHome.MENU_ID_LENS_APPS, 0)
                val dialog = activity.lensDialog!!
                (0 until dialog.listView.count).forEach { dialog.listView.setItemChecked(it, false) }
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(LensAppScope.ALL, UtilSettings(context).getLensAppScope(lensId))
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew assembleDevDebugAndroidTest`
Expected: FAIL — `Unresolved reference: MENU_ID_LENS_APPS`.

- [ ] **Step 3: Add the string to all 17 files**

Add to `res/values/strings.xml`: `<string name="lens_choose_apps">Choose apps for this lens</string>`. Add the same key to each of `values-ar, de, es, fr, hi, in, it, ja, km, ko, lo, pt, ru, th, vi, zh` with a real translation (never the English text; `AllStringsTranslationTest` rejects English copies):

| locale | value |
|---|---|
| ar | `اختيار التطبيقات لهذه العدسة` |
| de | `Apps für diese Linse auswählen` |
| es | `Elegir apps para esta lente` |
| fr | `Choisir les applis de cette lentille` |
| hi | `इस लेंस के लिए ऐप चुनें` |
| in | `Pilih aplikasi untuk lensa ini` |
| it | `Scegli le app per questa lente` |
| ja | `このレンズのアプリを選択` |
| km | `ជ្រើសរើសកម្មវិធីសម្រាប់កញ្ចក់នេះ` |
| ko | `이 렌즈에 표시할 앱 선택` |
| lo | `ເລືອກແອັບສຳລັບເລນນີ້` |
| pt | `Escolher apps para esta lente` |
| ru | `Выбрать приложения для этой линзы` |
| th | `เลือกแอปสำหรับเลนส์นี้` |
| vi | `Chọn ứng dụng cho thấu kính này` |
| zh | `为此镜头选择应用` |

- [ ] **Step 4: Implement in `ActHome.java`**

Add the constant after `MENU_ID_TOGGLE_CLEAN_LENS`:

```java
    @androidx.annotation.VisibleForTesting
    static final int MENU_ID_LENS_APPS = 9;
```

In `showLensManagementMenu`, after the `MENU_ID_TOGGLE_CLEAN_LENS` item:

```java
        addLensMenuItem(menu, themed, MENU_ID_LENS_APPS, R.string.lens_choose_apps,
                R.drawable.ic_apps_24dp, true);
```

In `onLensMenuItemSelected`, before the final `return false`:

```java
        } else if (itemId == MENU_ID_LENS_APPS) {
            showLensAppsDialog(current);
            return true;
```

Add after `showLensNameDialog` (add `import com.mckimquyen.util.LensAppScopeEditor;` and `import java.util.HashSet; import java.util.Set;` if missing):

```java
    /**
     * FISH-021: tick the apps a lens shows. Reuses {@link #lensDialog}, so onDestroy and rotation
     * already dismiss it. Search is deliberately not filtered, so this lists every installed app.
     */
    @androidx.annotation.VisibleForTesting
    void showLensAppsDialog(LensWorkspace lens) {
        if (listApp == null || listApp.isEmpty() || utilSettings == null) return;
        ArrayList<App> apps = new ArrayList<>(listApp);
        Set<String> allIds = new HashSet<>();
        for (App app : apps) allIds.add(LensAppScope.identifierOf(app));
        LensAppScopeEditor editor = new LensAppScopeEditor(utilSettings);
        Set<String> shown = editor.effectiveIds(lens.getId(), allIds);

        CharSequence[] labels = new CharSequence[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).getLabel();
            checked[i] = shown.contains(LensAppScope.identifierOf(apps.get(i)));
        }

        lensDialog = new MaterialAlertDialogBuilder(this, R.style.MaterialYouDialogTheme)
                .setTitle(R.string.lens_choose_apps)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> { })
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        lensDialog.show();
        lensDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            Set<String> picked = new HashSet<>();
            android.widget.ListView list = lensDialog.getListView();
            for (int i = 0; i < apps.size(); i++) {
                if (list.isItemChecked(i)) picked.add(LensAppScope.identifierOf(apps.get(i)));
            }
            editor.apply(lens.getId(), picked);
            lensDialog.dismiss();
        });
        lensDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
                .setOnClickListener(v -> lensDialog.dismiss());
    }
```

Note: the test casts a `ListView` item via `adapter.getItem(i).toString()`; `setMultiChoiceItems(CharSequence[], ...)` backs the list with those labels, so that matches `label.toString()`.

- [ ] **Step 5: Run the new test, the translation test and the neighbours**

```bash
./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.a11y.*'
ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew installDevDebug installDevDebugAndroidTest
adb -s FUJZIFIR7DQCNRWW shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensAppsDialogWidgetTest,com.mckimquyen.ui.ActHomeLensManagementWidgetTest,com.mckimquyen.ui.ActHomeLensScopeWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: unit PASS; instrumented `OK`. If `ActHomeLensManagementWidgetTest` asserts an exact menu size, update that count to include the new item and note it in the commit message.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/main/res/values*/strings.xml app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensAppsDialogWidgetTest.kt
bash scripts/check-secrets.sh
git commit -m "feat(lens): choose apps for a lens from the lens menu

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Apps tab — add/remove selected apps to a lens

**Files:**
- Modify: `app/src/main/res/menu/menu_apps_selection.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmApps.kt` (`onActionItemClicked` ~235, `onDestroyView`/cleanup ~195-203, new helpers)
- Modify: `app/src/main/res/values/strings.xml` and the 16 locale files (two new strings)
- Test: `app/src/androidTest/java/com/mckimquyen/ui/FrmAppsLensScopeIntegrationTest.kt`

**Interfaces:**
- Consumes: `LensAppScopeEditor.add/remove` (Task 2), `LensAppScope.identifierOf` (Task 1), `LensWorkspace.loadAll(onLoaded)` (Kotlin lambda, delivers on main), `AppAdapter.getSelectedApps(): List<App>`.
- Produces: `FrmApps.applyBulkLensScope(lensId: String, ids: Set<String>, add: Boolean)` (`internal`, `@VisibleForTesting`); menu ids `R.id.menuItemBulkAddToLens`, `R.id.menuItemBulkRemoveFromLens`; strings `menu_bulk_add_to_lens`, `menu_bulk_remove_from_lens`.

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.adt.FragmentPagerAdapter
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmAppsLensScopeIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private val mail = App(packageName = "com.test.bulk.mail", name = "Main")
    private val chat = App(packageName = "com.test.bulk.chat", name = "Main")
    private val maps = App(packageName = "com.test.bulk.maps", name = "Main")

    @Before
    fun setup() {
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(mail, chat, maps)
        reset()
    }

    @After
    fun tearDown() {
        reset()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun reset() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    private fun withFrmApps(block: (FrmApps) -> Unit) {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<ViewPager2>(R.id.viewpager).setCurrentItem(FragmentPagerAdapter.TAB_APPS, false)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                block(activity.supportFragmentManager.fragments.filterIsInstance<FrmApps>().first())
            }
        }
    }

    @Test
    fun removeFromAnAllLensSelectsEveryOtherApp() = withFrmApps { fragment ->
        fragment.applyBulkLensScope(lensId, setOf(LensAppScope.identifierOf(chat)), add = false)
        val settings = UtilSettings(context)
        assertEquals(LensAppScope.SELECTED, settings.getLensAppScope(lensId))
        assertEquals(
            setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(maps)),
            settings.getLensAppSelection(lensId)
        )
    }

    @Test
    fun addExtendsASelectedLens() = withFrmApps { fragment ->
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.SELECTED)
        UtilSettings(context).saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail)))
        fragment.applyBulkLensScope(lensId, setOf(LensAppScope.identifierOf(maps)), add = true)
        assertEquals(
            setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(maps)),
            UtilSettings(context).getLensAppSelection(lensId)
        )
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew assembleDevDebugAndroidTest`
Expected: FAIL — `Unresolved reference: applyBulkLensScope`.

- [ ] **Step 3: Add the two menu items**

In `menu_apps_selection.xml`, before `</menu>`:

```xml
    <item
        android:id="@+id/menuItemBulkAddToLens"
        android:title="@string/menu_bulk_add_to_lens"
        app:showAsAction="never" />

    <item
        android:id="@+id/menuItemBulkRemoveFromLens"
        android:title="@string/menu_bulk_remove_from_lens"
        app:showAsAction="never" />
```

- [ ] **Step 4: Add the strings to all 17 files**

`values/strings.xml`: `menu_bulk_add_to_lens` = `Add to lens…`, `menu_bulk_remove_from_lens` = `Remove from lens…`. Every locale gets a real translation (not the English text):

| locale | add_to_lens | remove_from_lens |
|---|---|---|
| ar | `إضافة إلى عدسة…` | `إزالة من عدسة…` |
| de | `Zu Linse hinzufügen …` | `Aus Linse entfernen …` |
| es | `Añadir a una lente…` | `Quitar de una lente…` |
| fr | `Ajouter à une lentille…` | `Retirer d'une lentille…` |
| hi | `लेंस में जोड़ें…` | `लेंस से हटाएं…` |
| in | `Tambahkan ke lensa…` | `Hapus dari lensa…` |
| it | `Aggiungi a una lente…` | `Rimuovi da una lente…` |
| ja | `レンズに追加…` | `レンズから削除…` |
| km | `បន្ថែមទៅកញ្ចក់…` | `ដកចេញពីកញ្ចក់…` |
| ko | `렌즈에 추가…` | `렌즈에서 제거…` |
| lo | `ເພີ່ມໃສ່ເລນ…` | `ເອົາອອກຈາກເລນ…` |
| pt | `Adicionar a uma lente…` | `Remover de uma lente…` |
| ru | `Добавить в линзу…` | `Убрать из линзы…` |
| th | `เพิ่มในเลนส์…` | `นำออกจากเลนส์…` |
| vi | `Thêm vào thấu kính…` | `Xóa khỏi thấu kính…` |
| zh | `添加到镜头…` | `从镜头移除…` |

- [ ] **Step 5: Implement in `FrmApps.kt`**

Add imports `com.mckimquyen.model.LensWorkspace`, `com.mckimquyen.util.LensAppScope`, `com.mckimquyen.util.LensAppScopeEditor`, `com.google.android.material.dialog.MaterialAlertDialogBuilder`, `androidx.annotation.VisibleForTesting`, `com.mckimquyen.app.RAppsSingleton`. Add a field next to `actionMode`:

```kotlin
    private var lensPickerDialog: androidx.appcompat.app.AlertDialog? = null
```

In `onActionItemClicked`, before `else -> return false`:

```kotlin
            R.id.menuItemBulkAddToLens, R.id.menuItemBulkRemoveFromLens -> {
                // Capture before finish(): onDestroyActionMode clears the adapter's selection.
                val ids = appAdapter?.selectedApps.orEmpty().map(LensAppScope::identifierOf).toSet()
                val add = item.itemId == R.id.menuItemBulkAddToLens
                mode.finish()
                pickLensThen { lensId -> applyBulkLensScope(lensId, ids, add) }
            }
```

Add the helpers:

```kotlin
    /** One lens: no question asked. Several: a chooser, preselecting the lens being viewed. */
    private fun pickLensThen(onPicked: (String) -> Unit) {
        LensWorkspace.loadAll { lenses ->
            val ctx = context
            if (!isAdded || ctx == null || lenses.isEmpty()) return@loadAll
            if (lenses.size == 1) {
                onPicked(lenses.first().id)
                return@loadAll
            }
            val activeId = utilSettings?.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
            val checked = lenses.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
            lensPickerDialog?.dismiss()
            lensPickerDialog = MaterialAlertDialogBuilder(ctx, R.style.MaterialYouDialogTheme)
                .setSingleChoiceItems(lenses.map { it.name }.toTypedArray(), checked) { dialog, which ->
                    onPicked(lenses[which].id)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    @VisibleForTesting
    internal fun applyBulkLensScope(lensId: String, ids: Set<String>, add: Boolean) {
        val settings = utilSettings ?: return
        val allIds = RAppsSingleton.instance.apps.orEmpty().map(LensAppScope::identifierOf).toSet()
        val editor = LensAppScopeEditor(settings)
        if (add) editor.add(lensId, ids, allIds) else editor.remove(lensId, ids, allIds)
    }
```

In the view-teardown block next to `utilSettings = null` (~line 203) add, before it:

```kotlin
        lensPickerDialog?.dismiss()
        lensPickerDialog = null
```

- [ ] **Step 6: Run the new test, the translation test and the neighbours**

```bash
./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.a11y.*'
ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew installDevDebug installDevDebugAndroidTest
adb -s FUJZIFIR7DQCNRWW shell am instrument -w -e class com.mckimquyen.ui.FrmAppsLensScopeIntegrationTest,com.mckimquyen.ui.FrmAppsSelectionIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: unit PASS; instrumented `OK`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/menu/menu_apps_selection.xml app/src/main/res/values*/strings.xml app/src/main/java/com/mckimquyen/ui/FrmApps.kt app/src/androidTest/java/com/mckimquyen/ui/FrmAppsLensScopeIntegrationTest.kt
bash scripts/check-secrets.sh
git commit -m "feat(lens): add or remove selected apps to a lens from the Apps tab

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: "Remove from this lens" on the grid

**Files:**
- Modify: `app/src/main/res/menu/menu_search_result.xml` (shared with `SearchResultAdapter`)
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt` (`showQuickActionsMenu` ~628-680)
- Modify: `app/src/main/java/com/mckimquyen/search/SearchResultAdapter.kt` (`showActionMenu` ~220)
- Modify: `app/src/main/res/values/strings.xml` and the 16 locale files (one new string)
- Test: `app/src/androidTest/java/com/mckimquyen/views/LensViewRemoveFromLensIntegrationTest.kt`

**Interfaces:**
- Consumes: `LensAppScopeEditor.remove` (Task 2), `LensView.appsForTest` (Task 3), `LensView.mUtilSettings`, `LensView.quickActionsMenuForTest`, `LensView.showAppOptionsAtIndex`.
- Produces: menu id `R.id.menuItemRemoveFromLens`; string `menu_remove_from_lens`.

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
package com.mckimquyen.views

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewRemoveFromLensIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private val mail = App(id = 1, label = "Mail", packageName = "com.test.rm.mail", name = "Main", isVisible = true)
    private val chat = App(id = 2, label = "Chat", packageName = "com.test.rm.chat", name = "Main", isVisible = true)

    @Before
    fun setup() {
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(mail, chat)
        reset()
    }

    @After
    fun tearDown() {
        reset()
        // Restore, never clearAllData(): that leaves the shared singleton empty for later tests.
        RAppsSingleton.instance.apps = originalApps
    }

    private fun reset() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    private fun lensViewWithApps(scenario: ActivityScenario<ActHome>): LensView {
        var view: LensView? = null
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && view?.appsForTest.isNullOrEmpty()) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { view = it.findViewById(R.id.lensViews) }
            Thread.sleep(POLL_MS)
        }
        return requireNotNull(view) { "the lens view never bound apps" }
    }

    @Test
    fun removeEntryIsHiddenOnAnAllLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithApps(scenario)
            scenario.onActivity {
                assertTrue(lensView.showAppOptionsAtIndex(0))
                val item = lensView.quickActionsMenuForTest!!.menu.findItem(R.id.menuItemRemoveFromLens)
                assertNotNull(item)
                assertFalse("An ALL lens has nothing to remove an app from", item.isVisible)
            }
        }
    }

    @Test
    fun removeEntryDropsTheAppFromASelectedLens() {
        val settings = UtilSettings(context)
        settings.saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
        settings.saveLensAppScope(lensId, LensAppScope.SELECTED)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithApps(scenario)
            lateinit var removed: App // assigned in the same onActivity block before any use
            scenario.onActivity {
                removed = lensView.appsForTest!![0]
                assertTrue(lensView.showAppOptionsAtIndex(0))
                val menu = lensView.quickActionsMenuForTest!!.menu
                assertTrue(menu.findItem(R.id.menuItemRemoveFromLens).isVisible)
                menu.performIdentifierAction(R.id.menuItemRemoveFromLens, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(
                setOf(mail, chat).map(LensAppScope::identifierOf).toSet() - LensAppScope.identifierOf(removed),
                UtilSettings(context).getLensAppSelection(lensId)
            )
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew assembleDevDebugAndroidTest`
Expected: FAIL — `Unresolved reference: menuItemRemoveFromLens`.

- [ ] **Step 3: Add the menu item and the string**

In `menu_search_result.xml`, after `menuItemUnpin`:

```xml
    <!-- FISH-021: only offered by the lens grid, and only for a lens whose scope is SELECTED. -->
    <item
        android:id="@+id/menuItemRemoveFromLens"
        android:icon="@drawable/ic_close_24dp"
        android:title="@string/menu_remove_from_lens"
        app:iconTint="@color/popup_menu_icon_tint" />
```

`values/strings.xml`: `<string name="menu_remove_from_lens">Remove from this lens</string>`. Every locale gets a real translation:

| locale | value |
|---|---|
| ar | `إزالة من هذه العدسة` |
| de | `Aus dieser Linse entfernen` |
| es | `Quitar de esta lente` |
| fr | `Retirer de cette lentille` |
| hi | `इस लेंस से हटाएं` |
| in | `Hapus dari lensa ini` |
| it | `Rimuovi da questa lente` |
| ja | `このレンズから削除` |
| km | `ដកចេញពីកញ្ចក់នេះ` |
| ko | `이 렌즈에서 제거` |
| lo | `ເອົາອອກຈາກເລນນີ້` |
| pt | `Remover desta lente` |
| ru | `Убрать из этой линзы` |
| th | `นำออกจากเลนส์นี้` |
| vi | `Xóa khỏi thấu kính này` |
| zh | `从此镜头移除` |

- [ ] **Step 4: Hide it in the search UIs**

In `SearchResultAdapter.showActionMenu`, after the `menuItemElementUninstall` line:

```kotlin
            popupMenu.menu.findItem(R.id.menuItemRemoveFromLens).isVisible = false
```

- [ ] **Step 5: Show and handle it in `LensView.showQuickActionsMenu`**

Add imports `com.mckimquyen.util.LensAppScope` and `com.mckimquyen.util.LensAppScopeEditor`. After the `menuItemUnpin` visibility line:

```kotlin
            popupMenu.menu.findItem(R.id.menuItemRemoveFromLens).isVisible =
                mUtilSettings?.getLensAppScope(lensId) == LensAppScope.SELECTED
```

In the `when (item.itemId)` add before `else -> false`:

```kotlin
                    R.id.menuItemRemoveFromLens -> {
                        removeFromLens(app)
                        true
                    }
```

Add next to `pinQuickAction`:

```kotlin
    /** FISH-021: only reachable on a SELECTED lens, so no "all apps" set is needed to subtract from. */
    private fun removeFromLens(app: App) {
        val settings = mUtilSettings ?: return
        LensAppScopeEditor(settings).remove(lensId, setOf(LensAppScope.identifierOf(app)), emptySet())
    }
```

- [ ] **Step 6: Run the new test and the menus that share the resource**

```bash
./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.a11y.*'
ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew installDevDebug installDevDebugAndroidTest
adb -s FUJZIFIR7DQCNRWW shell am instrument -w -e class com.mckimquyen.views.LensViewRemoveFromLensIntegrationTest,com.mckimquyen.views.LensViewQuickActionsIntegrationTest,com.mckimquyen.search.SearchResultAdapterWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: unit PASS; instrumented `OK`. If `SearchResultAdapterWidgetTest` counts visible menu items, the new item is hidden there, so counts must not change.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/menu/menu_search_result.xml app/src/main/res/values*/strings.xml app/src/main/java/com/mckimquyen/views/LensView.kt app/src/main/java/com/mckimquyen/search/SearchResultAdapter.kt app/src/androidTest/java/com/mckimquyen/views/LensViewRemoveFromLensIntegrationTest.kt
bash scripts/check-secrets.sh
git commit -m "feat(lens): remove an app from a selected lens from the grid menu

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: Freeze app positions in a lens

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/model/AppPersistentDao.kt` (new query after `updateOrder` ~line 45)
- Modify: `app/src/main/java/com/mckimquyen/model/AppPersistent.kt` (new `clearOrderForLens` in the companion, next to `setAppOrderBatch` ~130)
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt` (`applySmartFocusArrangement` ~547; accessor for the displayed order)
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java` (menu id, menu item, branch, `toggleFreezeForLens`, confirm dialog)
- Modify: `app/src/main/res/values/strings.xml` and the 16 locale files (four new strings)
- Test: `app/src/androidTest/java/com/mckimquyen/model/AppPersistentClearOrderTest.kt`, `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensFreezeWidgetTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.isLensFrozen/saveLensFrozen` (Task 1), `AppPersistent.setAppOrderBatch(apps: List<App>, lensId: String)` (existing), `LensView.appsForTest` (Task 3).
- Produces: `AppPersistentDao.clearOrderForLens(lensId: String): Int`; `AppPersistent.clearOrderForLens(lensId: String)`; `LensView.displayedApps: List<App>` (the post-arrangement order); `ActHome.MENU_ID_FREEZE_LENS = 10`; strings `lens_freeze_positions`, `lens_unfreeze_positions`, `lens_unfreeze_title`, `lens_unfreeze_message`.

- [ ] **Step 1: Write the failing DAO test**

```kotlin
package com.mckimquyen.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPersistentClearOrderTest {

    private val dao get() = AppDatabase.getInstance().appPersistentDao()
    private val lensA = "clear-order-lens-a"
    private val lensB = "clear-order-lens-b"

    @Before
    fun setup() = clean()

    @After
    fun tearDown() = clean()

    private fun clean() = runBlocking {
        AppDatabase.init(InstrumentationRegistry.getInstrumentation().targetContext)
        dao.deleteForLens(lensA)
        dao.deleteForLens(lensB)
        Unit
    }

    @Test
    fun clearOrderResetsOnlyTheGivenLens() = runBlocking {
        dao.insert(AppPersistent.defaults("com.test.order", "A", lensA).copy(orderNumber = 3))
        dao.insert(AppPersistent.defaults("com.test.order", "A", lensB).copy(orderNumber = 5))

        val changed = dao.clearOrderForLens(lensA)

        assertEquals(1, changed)
        assertEquals(-1, dao.getAllForLens(lensA).single().orderNumber)
        assertEquals(5, dao.getAllForLens(lensB).single().orderNumber)
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew assembleDevDebugAndroidTest`
Expected: FAIL — `Unresolved reference: clearOrderForLens`.

- [ ] **Step 3: Add the DAO query and the companion wrapper**

In `AppPersistentDao.kt`, after `updateOrder`:

```kotlin
    /** FISH-021: unfreeze - every app of the lens goes back to "no manual position". */
    @Query("UPDATE APP_PERSISTENT SET ORDER_NUMBER = -1 WHERE LENS_ID = :lensId")
    suspend fun clearOrderForLens(lensId: String): Int
```

In `AppPersistent.kt` companion, after `setAppOrderBatch`:

```kotlin
        @JvmStatic
        fun clearOrderForLens(lensId: String) {
            persist { clearOrderForLens(lensId) }
        }
```

- [ ] **Step 4: Run the DAO test, expect PASS**

```bash
ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew installDevDebug installDevDebugAndroidTest
adb -s FUJZIFIR7DQCNRWW shell am instrument -w -e class com.mckimquyen.model.AppPersistentClearOrderTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: `OK (1 test)`.

- [ ] **Step 5: Write the failing freeze UI test**

```kotlin
package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensFreezeWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private fun app(pkg: String) =
        App(id = pkg.hashCode(), label = pkg, packageName = pkg, name = "Main", isVisible = true)

    @Before
    fun setup() {
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(app("com.test.f.a"), app("com.test.f.b"), app("com.test.f.c"))
        UtilSettings(context).saveLensFrozen(lensId, false)
    }

    @After
    fun tearDown() {
        UtilSettings(context).saveLensFrozen(lensId, false)
        RAppsSingleton.instance.apps = originalApps
    }

    private fun lensView(scenario: ActivityScenario<ActHome>): LensView {
        var view: LensView? = null
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && view?.displayedApps.isNullOrEmpty()) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { view = it.findViewById(R.id.lensViews) }
            Thread.sleep(POLL_MS)
        }
        return requireNotNull(view) { "the lens view never bound apps" }
    }

    @Test
    fun freezingStoresTheFlagAndKeepsTheDisplayedOrder() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = lensView(scenario)
            lateinit var before: List<String> // assigned in the same onActivity block before any use
            scenario.onActivity { activity ->
                before = view.displayedApps.map { it.packageName.toString() }
                assertTrue(activity.onLensMenuItemSelected(ActHome.MENU_ID_FREEZE_LENS, 0))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(UtilSettings(context).isLensFrozen(lensId))
            scenario.onActivity {
                assertEquals(before, view.displayedApps.map { a -> a.packageName.toString() })
            }
        }
    }

    @Test
    fun smartFocusDoesNotRearrangeAFrozenLens() {
        val settings = UtilSettings(context)
        settings.saveSmartFocusBias(lensId, true)
        settings.saveLensFrozen(lensId, true)
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                val view = lensView(scenario)
                scenario.onActivity {
                    assertEquals(
                        RAppsSingleton.instance.apps!!.map { a -> a.packageName.toString() }.sorted(),
                        view.displayedApps.map { a -> a.packageName.toString() }.sorted()
                    )
                    assertFalse("frozen lens must not be reordered by Smart Focus", view.smartFocusAppliedForTest)
                }
            }
        } finally {
            settings.saveSmartFocusBias(lensId, UtilSettings.DEFAULT_SMART_FOCUS_BIAS)
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
```

- [ ] **Step 6: Run it, expect a compile failure**

Run: `ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew assembleDevDebugAndroidTest`
Expected: FAIL — `Unresolved reference: displayedApps` / `MENU_ID_FREEZE_LENS` / `smartFocusAppliedForTest`.

- [ ] **Step 7: `LensView` — expose the displayed order and skip Smart Focus when frozen**

Add after `appsForTest` (Task 3):

```kotlin
    /** FISH-021: the order actually drawn (after any Smart Focus arrangement). */
    val displayedApps: List<App> get() = mApps.orEmpty()

    private var mSmartFocusApplied = false
    internal val smartFocusAppliedForTest: Boolean get() = mSmartFocusApplied
```

In `applySmartFocusArrangement`, replace the `val enabled = ...` and the `mApps = if (enabled && ...)` block with:

```kotlin
        val frozen = mUtilSettings?.isLensFrozen(lensId) == true
        val enabled = !frozen && (mUtilSettings?.isSmartFocusBias(lensId)
            ?: UtilSettings.DEFAULT_SMART_FOCUS_BIAS)
        mSmartFocusApplied = enabled && cols > 0 && rows > 0
        mApps = if (mSmartFocusApplied) {
            SmartFocusArranger.arrange(source, cols, rows, smartFocusEnabled = true)
        } else {
            ArrayList(source)
        }
```

- [ ] **Step 8: Strings in all 17 files**

`values/strings.xml`:

```xml
<string name="lens_freeze_positions">Freeze app positions</string>
<string name="lens_unfreeze_positions">Unfreeze app positions</string>
<string name="lens_unfreeze_title">Unfreeze positions?</string>
<string name="lens_unfreeze_message">This also discards any order you set by dragging.</string>
```

Locales (real translations; `AllStringsTranslationTest` rejects English copies):

| locale | freeze | unfreeze | title | message |
|---|---|---|---|---|
| ar | `تثبيت مواضع التطبيقات` | `إلغاء تثبيت مواضع التطبيقات` | `إلغاء التثبيت؟` | `سيؤدي هذا أيضًا إلى تجاهل أي ترتيب ضبطته بالسحب.` |
| de | `App-Positionen fixieren` | `App-Positionen lösen` | `Positionen lösen?` | `Dadurch geht auch jede per Ziehen festgelegte Reihenfolge verloren.` |
| es | `Fijar posiciones de las apps` | `Soltar posiciones de las apps` | `¿Soltar posiciones?` | `Esto también descarta el orden que definiste arrastrando.` |
| fr | `Figer la position des applis` | `Libérer la position des applis` | `Libérer les positions ?` | `Cela supprime aussi l'ordre défini par glisser-déposer.` |
| hi | `ऐप की स्थिति फ़िक्स करें` | `ऐप की स्थिति अनफ़िक्स करें` | `स्थिति अनफ़िक्स करें?` | `इससे खींचकर तय किया गया क्रम भी हट जाएगा।` |
| in | `Kunci posisi aplikasi` | `Lepas kunci posisi aplikasi` | `Lepas kunci posisi?` | `Ini juga menghapus urutan yang Anda atur dengan menyeret.` |
| it | `Blocca posizioni delle app` | `Sblocca posizioni delle app` | `Sbloccare le posizioni?` | `Verrà scartato anche l'ordine impostato trascinando.` |
| ja | `アプリの位置を固定` | `アプリの位置の固定を解除` | `固定を解除しますか？` | `ドラッグで設定した並び順も破棄されます。` |
| km | `ចាក់សោទីតាំងកម្មវិធី` | `ដោះសោទីតាំងកម្មវិធី` | `ដោះសោទីតាំង?` | `វាក៏លុបចោលលំដាប់ដែលអ្នកកំណត់ដោយអូសផងដែរ។` |
| ko | `앱 위치 고정` | `앱 위치 고정 해제` | `위치 고정을 해제할까요?` | `드래그로 정한 순서도 함께 사라집니다.` |
| lo | `ລັອກຕຳແໜ່ງແອັບ` | `ປົດລັອກຕຳແໜ່ງແອັບ` | `ປົດລັອກຕຳແໜ່ງບໍ?` | `ນີ້ຈະລຶບລຳດັບທີ່ທ່ານຕັ້ງດ້ວຍການລາກນຳ.` |
| pt | `Fixar posições dos apps` | `Soltar posições dos apps` | `Soltar posições?` | `Isso também descarta a ordem definida ao arrastar.` |
| ru | `Закрепить позиции приложений` | `Открепить позиции приложений` | `Открепить позиции?` | `Также будет сброшен порядок, заданный перетаскиванием.` |
| th | `ตรึงตำแหน่งแอป` | `เลิกตรึงตำแหน่งแอป` | `เลิกตรึงตำแหน่ง?` | `การลำดับที่คุณลากจัดไว้จะถูกล้างด้วย` |
| vi | `Cố định vị trí ứng dụng` | `Bỏ cố định vị trí ứng dụng` | `Bỏ cố định vị trí?` | `Thao tác này cũng xóa thứ tự bạn đã kéo thả.` |
| zh | `固定应用位置` | `取消固定应用位置` | `取消固定位置？` | `这也会丢弃你通过拖动设置的顺序。` |

- [ ] **Step 9: `ActHome` — menu entry, freeze, unfreeze with confirmation**

Add the constant after `MENU_ID_LENS_APPS`:

```java
    @androidx.annotation.VisibleForTesting
    static final int MENU_ID_FREEZE_LENS = 10;
```

In `showLensManagementMenu`, after the `MENU_ID_LENS_APPS` item:

```java
        addLensMenuItem(menu, themed, MENU_ID_FREEZE_LENS, lensFreezeMenuLabelRes(position),
                R.drawable.ic_lock_24dp, true);
```

In `onLensMenuItemSelected`, before the final `return false`:

```java
        } else if (itemId == MENU_ID_FREEZE_LENS) {
            toggleFreezeForLens(current);
            return true;
```

Add next to `toggleSmartFocusForLens`:

```java
    /** Like {@link #lensSmartFocusMenuLabelRes}: the entry names the action, so it reads current state. */
    @androidx.annotation.VisibleForTesting
    int lensFreezeMenuLabelRes(int position) {
        boolean frozen = position >= 0 && position < currentLenses.size() && utilSettings != null
                && utilSettings.isLensFrozen(currentLenses.get(position).getId());
        return frozen ? R.string.lens_unfreeze_positions : R.string.lens_freeze_positions;
    }

    /**
     * FISH-021: freezing writes the order the user currently sees as each app's orderNumber, so
     * the sorter keeps it and Smart Focus stops rearranging this lens. Unfreezing also discards
     * any manual drag order, hence the confirmation.
     */
    private void toggleFreezeForLens(LensWorkspace lens) {
        if (utilSettings == null) return;
        if (utilSettings.isLensFrozen(lens.getId())) {
            lensDialog = new MaterialAlertDialogBuilder(this, R.style.MaterialYouDialogTheme)
                    .setTitle(R.string.lens_unfreeze_title)
                    .setMessage(R.string.lens_unfreeze_message)
                    .setPositiveButton(R.string.lens_unfreeze_positions, (d, w) -> {
                        utilSettings.saveLensFrozen(lens.getId(), false);
                        AppPersistent.clearOrderForLens(lens.getId());
                        reloadLensAfterOrderChange(lens);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        LensView view = lensViews;
        if (view == null) return;
        AppPersistent.setAppOrderBatch(new ArrayList<>(view.getDisplayedApps()), lens.getId());
        utilSettings.saveLensFrozen(lens.getId(), true);
        view.refreshSmartFocus();
    }

    private void reloadLensAfterOrderChange(LensWorkspace lens) {
        Object application = getApplication();
        if (application instanceof RApplication) {
            ((RApplication) application).getAppRefreshPipeline().switchLens(lens.getId());
        }
        if (lensViews != null) {
            lensViews.refreshSmartFocus();
        }
    }
```

Add `import com.mckimquyen.model.AppPersistent;` if missing. `ic_lock_24dp` exists in `res/drawable` (checked).

- [ ] **Step 10: Run the new tests and the neighbours**

```bash
./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.a11y.*'
ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew installDevDebug installDevDebugAndroidTest
adb -s FUJZIFIR7DQCNRWW shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensFreezeWidgetTest,com.mckimquyen.model.AppPersistentClearOrderTest,com.mckimquyen.ui.ActHomeLensManagementWidgetTest,com.mckimquyen.views.LensViewSmartFocusIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: unit PASS; instrumented `OK`. If the Smart Focus test class has another name, find it with `grep -rl "SmartFocus" app/src/androidTest` and run that instead.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/mckimquyen/model/AppPersistentDao.kt app/src/main/java/com/mckimquyen/model/AppPersistent.kt app/src/main/java/com/mckimquyen/views/LensView.kt app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/main/res/values*/strings.xml app/src/androidTest/java/com/mckimquyen/model/AppPersistentClearOrderTest.kt app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensFreezeWidgetTest.kt
bash scripts/check-secrets.sh
git commit -m "feat(lens): freeze and unfreeze app positions per lens

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: Quick lens switch (tap the name, dynamic shortcuts)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/LensShortcuts.kt`
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java` (extra constant next to `EXTRA_AUTO_EXPORT_LENS` ~186; `tvLensName` click listener next to the long-click one ~483; `onCreate` ~300 and `onNewIntent` ~333; the four `currentLenses = lenses;` sites at ~579, ~1031, ~1048, ~1129)
- Modify: `app/src/main/res/values/strings.xml` and the 16 locale files (one new string)
- Test: `app/src/test/java/com/mckimquyen/util/LensShortcutsTest.kt`, `app/src/androidTest/java/com/mckimquyen/ui/ActHomeQuickLensSwitchWidgetTest.kt`

**Interfaces:**
- Consumes: `LensWorkspace` (`id`, `name`, `orderIndex`), `LensWorkspace.loadAll`, `UtilSettings.KEY_ACTIVE_LENS_ID`, existing `refreshLensList()`.
- Produces: `ActHome.EXTRA_TARGET_LENS_ID` (`"com.mckimquyen.lenslauncher.EXTRA_TARGET_LENS_ID"`); `ActHome.handleTargetLensIntent(Intent)` (`@VisibleForTesting`); `LensShortcuts.pick(lenses, maxPerActivity): List<LensWorkspace>`, `LensShortcuts.refresh(context, lenses)`, constants `STATIC_SHORTCUT_COUNT = 2`, `SHORTCUT_ID_PREFIX = "lens_"`; string `lens_switch_title`.

- [ ] **Step 1: Write the failing JVM test**

```kotlin
package com.mckimquyen.util

import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LensShortcutsTest {

    private fun lens(id: String, order: Int) = LensWorkspace(id = id, name = id, orderIndex = order)

    @Test
    fun `leaves room for the static shortcuts`() {
        val lenses = (0 until 6).map { lens("l$it", it) }
        val picked = LensShortcuts.pick(lenses, maxPerActivity = 5)
        assertEquals(5 - LensShortcuts.STATIC_SHORTCUT_COUNT, picked.size)
    }

    @Test
    fun `keeps lens order and ignores input order`() {
        val lenses = listOf(lens("c", 2), lens("a", 0), lens("b", 1))
        assertEquals(listOf("a", "b"), LensShortcuts.pick(lenses, maxPerActivity = 4).map { it.id })
    }

    @Test
    fun `a limit at or below the static count yields nothing`() {
        val lenses = listOf(lens("a", 0))
        assertEquals(emptyList<LensWorkspace>(), LensShortcuts.pick(lenses, maxPerActivity = LensShortcuts.STATIC_SHORTCUT_COUNT))
        assertEquals(emptyList<LensWorkspace>(), LensShortcuts.pick(lenses, maxPerActivity = 0))
    }

    @Test
    fun `fewer lenses than room returns them all`() {
        val lenses = listOf(lens("a", 0))
        assertEquals(lenses, LensShortcuts.pick(lenses, maxPerActivity = 10))
    }

    @Test
    fun `STATIC_SHORTCUT_COUNT matches shortcuts xml`() {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val xml = sequenceOf(
            File(workingDirectory, "src/main/res/xml/shortcuts.xml"),
            File(workingDirectory, "app/src/main/res/xml/shortcuts.xml")
        ).first { it.isFile }.readText()
        assertEquals(LensShortcuts.STATIC_SHORTCUT_COUNT, Regex("<shortcut\\b").findAll(xml).count())
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensShortcutsTest`
Expected: FAIL — `Unresolved reference: LensShortcuts`.

- [ ] **Step 3: Implement `LensShortcuts.kt`**

```kotlin
package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.ui.ActHome

/** FISH-021: launcher shortcuts that jump straight to a lens. */
object LensShortcuts {
    /** Declared in res/xml/shortcuts.xml; a unit test keeps this honest. They share the per-activity limit. */
    const val STATIC_SHORTCUT_COUNT = 2
    const val SHORTCUT_ID_PREFIX = "lens_"

    @JvmStatic
    fun pick(lenses: List<LensWorkspace>, maxPerActivity: Int): List<LensWorkspace> {
        val room = (maxPerActivity - STATIC_SHORTCUT_COUNT).coerceAtLeast(0)
        return lenses.sortedBy { it.orderIndex }.take(room)
    }

    /** Never throws: a launcher that rejects shortcuts must not break the home screen. */
    @JvmStatic
    fun refresh(context: Context, lenses: List<LensWorkspace>) {
        runCatching {
            val picked = pick(lenses, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
            val shortcuts = picked.map { lens ->
                ShortcutInfoCompat.Builder(context, SHORTCUT_ID_PREFIX + lens.id)
                    .setShortLabel(lens.name)
                    .setLongLabel(lens.name)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_swap_horiz_24dp))
                    .setIntent(
                        Intent(context, ActHome::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(ActHome.EXTRA_TARGET_LENS_ID, lens.id)
                    )
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }.onFailure { Logger.e("LensShortcuts: could not publish lens shortcuts", it) }
    }
}
```

- [ ] **Step 4: Add the extra constant so it compiles**

In `ActHome.java`, after `EXTRA_AUTO_EXPORT_LENS` (~line 187):

```java
    /** FISH-021: set by a lens launcher shortcut; {@link #handleTargetLensIntent} consumes it. */
    public static final String EXTRA_TARGET_LENS_ID =
            "com.mckimquyen.lenslauncher.EXTRA_TARGET_LENS_ID";
```

- [ ] **Step 5: Run the JVM test, expect PASS**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensShortcutsTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Write the failing instrumented test**

```kotlin
package com.mckimquyen.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeQuickLensSwitchWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val secondId = "quick-switch-second"

    @Before
    fun setup() = seed()

    @After
    fun tearDown() = clean()

    private fun clean() = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        Unit
    }

    private fun seed() {
        clean()
        runBlocking { dao.insertOrUpdate(LensWorkspace(id = secondId, name = "Second", orderIndex = 1)) }
    }

    private fun currentPage(scenario: ActivityScenario<ActHome>, expected: Int): Int {
        var page = -1
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && page != expected) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { page = it.findViewById<ViewPager2>(R.id.lensPager).currentItem }
            Thread.sleep(POLL_MS)
        }
        return page
    }

    private fun targetIntent(lensId: String) =
        Intent(context, ActHome::class.java).putExtra(ActHome.EXTRA_TARGET_LENS_ID, lensId)

    @Test
    fun coldStartWithATargetLensOpensThatLens() {
        ActivityScenario.launch<ActHome>(targetIntent(secondId)).use { scenario ->
            assertEquals(1, currentPage(scenario, expected = 1))
        }
    }

    @Test
    fun anUnknownTargetLensIsIgnored() {
        ActivityScenario.launch<ActHome>(targetIntent("does-not-exist")).use { scenario ->
            assertEquals(0, currentPage(scenario, expected = 0))
        }
    }

    @Test
    fun aNewIntentWhileRunningSwitchesLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(0, currentPage(scenario, expected = 0))
            scenario.onActivity { it.handleTargetLensIntent(targetIntent(secondId)) }
            assertEquals(1, currentPage(scenario, expected = 1))
        }
    }

    @Test
    fun theTargetExtraIsConsumedSoARecreateDoesNotReplayIt() {
        val intent = targetIntent(secondId)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.handleTargetLensIntent(intent) }
            assertEquals(false, intent.hasExtra(ActHome.EXTRA_TARGET_LENS_ID))
        }
    }

    @Test
    fun tappingTheLensNameOpensTheSwitcher() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(0, currentPage(scenario, expected = 0))
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.tvLensName).performClick()
                assertNotNull("The lens switcher must be showing", activity.lensDialog)
            }
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
```

- [ ] **Step 7: Run it, expect a compile failure**

Run: `ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew assembleDevDebugAndroidTest`
Expected: FAIL — `Unresolved reference: handleTargetLensIntent`.

- [ ] **Step 8: Add the string to all 17 files**

`values/strings.xml`: `<string name="lens_switch_title">Switch lens</string>`. Real translations:

| locale | value |
|---|---|
| ar | `تبديل العدسة` |
| de | `Linse wechseln` |
| es | `Cambiar de lente` |
| fr | `Changer de lentille` |
| hi | `लेंस बदलें` |
| in | `Ganti lensa` |
| it | `Cambia lente` |
| ja | `レンズを切り替え` |
| km | `ប្តូរកញ្ចក់` |
| ko | `렌즈 전환` |
| lo | `ປ່ຽນເລນ` |
| pt | `Trocar de lente` |
| ru | `Сменить линзу` |
| th | `สลับเลนส์` |
| vi | `Chuyển thấu kính` |
| zh | `切换镜头` |

- [ ] **Step 9: Implement in `ActHome.java`**

Add `import com.mckimquyen.util.LensShortcuts;`. Add the consumer next to `consumeAutoExportExtra`:

```java
    /**
     * FISH-021: a lens shortcut names its target lens. Saving it as the active lens BEFORE
     * refreshLensList() lets that method's existing "restore the saved active lens" path do the
     * paging, and an id that no longer exists falls back there exactly as a stale saved id does.
     * The extra is removed so a later recreate never replays it.
     */
    @androidx.annotation.VisibleForTesting
    void handleTargetLensIntent(Intent intent) {
        if (intent == null || utilSettings == null) return;
        String target = intent.getStringExtra(EXTRA_TARGET_LENS_ID);
        if (target == null) return;
        intent.removeExtra(EXTRA_TARGET_LENS_ID);
        if (target.isEmpty()) return;
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, target);
        refreshLensList();
    }
```

In `onCreate`, replace `consumeAutoExportExtra(getIntent());` + `refreshLensList();` (~300-301) with:

```java
        consumeAutoExportExtra(getIntent());
        // Before refreshLensList so the saved active lens is already the shortcut's target.
        if (utilSettings != null && getIntent() != null
                && getIntent().getStringExtra(EXTRA_TARGET_LENS_ID) != null) {
            utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID,
                    getIntent().getStringExtra(EXTRA_TARGET_LENS_ID));
            getIntent().removeExtra(EXTRA_TARGET_LENS_ID);
        }
        refreshLensList();
```

In `onNewIntent`, replace `refreshLensList();` with `handleTargetLensIntent(intent); refreshLensList();` (the second call is harmless when the extra was absent and keeps the existing behavior).

Next to the `tvLensName.setOnLongClickListener` (~483):

```java
        tvLensName.setOnClickListener(v -> showLensSwitcherDialog());
```

Add after `showLensAppsDialog`:

```java
    /** FISH-021: tapping the lens name lists every lens; picking one pages to it. */
    private void showLensSwitcherDialog() {
        if (currentLenses.size() < 2 || lensPager == null) return;
        CharSequence[] names = new CharSequence[currentLenses.size()];
        for (int i = 0; i < names.length; i++) names[i] = currentLenses.get(i).getName();
        lensDialog = new MaterialAlertDialogBuilder(this, R.style.MaterialYouDialogTheme)
                .setTitle(R.string.lens_switch_title)
                .setSingleChoiceItems(names, lensPager.getCurrentItem(), (dialog, which) -> {
                    lensPager.setCurrentItem(which, true);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** One place to adopt a fresh lens list, so the launcher shortcuts never go stale. */
    private void onLensesChanged(List<LensWorkspace> lenses) {
        currentLenses = lenses;
        LensShortcuts.refresh(getApplicationContext(), lenses);
    }
```

Then run `grep -n "currentLenses = lenses;" app/src/main/java/com/mckimquyen/ui/ActHome.java` — expect exactly 4 matches inside callbacks (not inside `onLensesChanged`). Replace each of those four with `onLensesChanged(lenses);`.

- [ ] **Step 10: Run the new tests and the lens neighbours**

```bash
./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.a11y.*' --tests com.mckimquyen.util.LensShortcutsTest
ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew installDevDebug installDevDebugAndroidTest
adb -s FUJZIFIR7DQCNRWW shell am instrument -w -e class com.mckimquyen.ui.ActHomeQuickLensSwitchWidgetTest,com.mckimquyen.ui.ActHomeLensManagementWidgetTest,com.mckimquyen.ui.ActHomeLensLabelWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: unit PASS; instrumented `OK`.

- [ ] **Step 11: Verify the shortcut risk from the spec on the real device**

```bash
adb -s FUJZIFIR7DQCNRWW shell dumpsys shortcut | grep -A6 "lens_"
```
Expected: dynamic shortcuts named `lens_<id>` with an intent to `com.mckimquyen.ui.ActHome` and the `EXTRA_TARGET_LENS_ID` extra. Then prove routing with the same intent:

```bash
adb -s FUJZIFIR7DQCNRWW shell am start -n com.mckimquyen.lenslauncherdebug/com.mckimquyen.ui.ActHome --es com.mckimquyen.lenslauncher.EXTRA_TARGET_LENS_ID <second-lens-id>
```
Expected: the home screen shows that lens. Record in the audit whether the OPPO launcher actually lists the shortcut (spec risk 1); a launcher that does not show it is a limitation, not a failure, and must be stated.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/LensShortcuts.kt app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/main/res/values*/strings.xml app/src/test/java/com/mckimquyen/util/LensShortcutsTest.kt app/src/androidTest/java/com/mckimquyen/ui/ActHomeQuickLensSwitchWidgetTest.kt
bash scripts/check-secrets.sh
git commit -m "feat(lens): switch lens by tapping its name or from launcher shortcuts

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 9: Whole-feature verification, docs and audit

**Files:**
- Modify: `doc/feature.md`, `doc/task/README.md`
- Create: `doc/task/done/p2-fish-fish-021-per-lens-app-scope.md`, `doc/task/AUDIT_ROUND_<date of the run>.md`

- [ ] **Step 1: Full JVM suite**

Run: `./gradlew testDevDebugUnitTest` — Expected: all pass, 0 failures. Record the count.

- [ ] **Step 2: Lint**

Run: `./gradlew lintDevDebug -q` — Expected: exit 0, 0 errors. Any warning added by this feature (grep the report for the new files) is fixed, not left.

- [ ] **Step 3: Full instrumented suite on the OPPO**

```bash
ANDROID_SERIAL=FUJZIFIR7DQCNRWW ./gradlew installDevDebug installDevDebugAndroidTest
adb -s FUJZIFIR7DQCNRWW shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner > /tmp/fish021_full.log 2>&1; echo $?
```
Expected: `OK (N tests)`. Any failure is compared against baseline `e0c3097`-equivalent before being called pre-existing; reduced-motion tests need "Disable permission monitoring" on in Developer options.

- [ ] **Step 4: Manual smoke on the OPPO**

Create a second lens, set it to "Choose apps" with two apps, swipe between lenses, freeze and unfreeze, tap the lens name, then rotate. Capture one screenshot per behaviour. Record results, not impressions.

- [ ] **Step 5: Secret scan and docs**

Run `bash scripts/check-secrets.sh` and the five patterns over `git diff origin/dev..HEAD`. Write the done file, add a `doc/feature.md` entry under Implemented and a README line under Implemented, and write the audit record with a score from the evidence above (same table as `AUDIT_ROUND_2026-10-08.md`).

- [ ] **Step 6: Commit; push only when the owner asks**

```bash
git add doc/
bash scripts/check-secrets.sh
git commit -m "docs: record FISH-021 delivery and audit

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:** scope storage and no-inheritance → Task 1; editor never leaves a blank lens → Task 2; apply scope per page and refresh → Task 3; three pickers (lens-menu dialog, Apps tab, grid) → Tasks 4, 5, 6; freeze and unfreeze with the new DAO query and Smart Focus skip → Task 7; tap-name switcher and dynamic shortcuts with the runtime limit → Task 8; the three spec risks → Task 3 test (prefetch uses `view.getLensId()`), Task 8 Step 11 (shortcut routing), Task 8 `pick` (runtime limit). Verification, docs, audit → Task 9.

**Known gaps to confirm while executing:** the test class name for Smart Focus in Task 7 Step 10 may differ; Task 3's prefetch concern is covered only for the selection refresh, not measured for flicker, so check it in the Task 9 smoke.

**Type consistency:** `LensAppScope.filter/identifierOf/fromStored`, `UtilSettings.getLensAppScope/saveLensAppScope/getLensAppSelection/saveLensAppSelection/isLensFrozen/saveLensFrozen`, `LensAppScopeEditor.effectiveIds/add/remove/apply`, `AppEventManager.lensScopeChanged/notifyLensScopeChanged`, `LensView.appsForTest/displayedApps/smartFocusAppliedForTest`, `ActHome.MENU_ID_LENS_APPS=9/MENU_ID_FREEZE_LENS=10/EXTRA_TARGET_LENS_ID/handleTargetLensIntent`, `LensShortcuts.pick/refresh/STATIC_SHORTCUT_COUNT` are used with the same names and signatures in every task that references them.
