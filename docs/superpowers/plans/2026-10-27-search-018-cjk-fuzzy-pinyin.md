# SEARCH-018 — CJK fuzzy gate and pinyin matching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop fuzzy typo matches on CJK labels and let users find Han-script app labels with pinyin initials or full pinyin, offline.

**Architecture:** Keep ranking in `AppSearchEngine`. Gate its existing Levenshtein tier for CJK scripts, then add a pinyin match tier backed by a small injectable provider. The production provider uses Android ICU `Han-Latin; Latin-ASCII`, caches transliterations by label, and fails closed to no pinyin match; tests use a fake for pure JVM behavior and Robolectric for real ICU data.

**Tech Stack:** Kotlin, `android.icu.text.Transliterator` (API 24+; app minSdk 25), JUnit4, Robolectric sdk 28, AndroidJUnitRunner/Espresso test infrastructure.

## Global Constraints

- Search remains local/offline; add no dependency, permission, settings switch, or localized string.
- CJK means Unicode scripts HAN, HIRAGANA, KATAKANA, or HANGUL.
- Fuzzy behavior for labels and tokens without CJK characters stays unchanged.
- Pinyin applies only to Han labels and ASCII-letter tokens; match only initials/full-pinyin prefixes.
- Pinyin tier score is `35`, after label-contains score `30` and before package-alias score `40`.
- Japanese kana/romaji is out of scope; do not use Chinese ICU readings to claim Japanese reading support.
- Keep SEARCH-012 package aliases and T9 behavior unchanged.
- Device target is locked: TECNO KJ7 `115333744A005844` only. Never run Gradle `connected*` tasks or send commands to another device.
- If any screenshot shows an ad covering UI, stop the test flow and wait for user confirmation `done`.
- Do not stage `.idea/` or unrelated working-tree files.

---

## File Structure

- Modify `app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt`: CJK fuzzy guard, pinyin score and match tier, optional provider injection.
- Create `app/src/main/java/com/mckimquyen/search/PinyinProvider.kt`: provider interface, ICU implementation, per-label cache.
- Modify `app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt`: fuzzy regressions and fake-provider behavior/ranking.
- Create `app/src/test/java/com/mckimquyen/search/IcuPinyinProviderTest.kt`: real ICU/Robolectric tests.
- Create `app/src/androidTest/java/com/mckimquyen/search/PinyinSearchWidgetTest.kt`: Home SearchView smoke for `wx` → `微信`.
- Modify `doc/task/todo/p3-search-search-018-cjk-fuzzy-and-pinyin-initials.md` → `inprogress/` then `done/`, plus `doc/task/README.md`, `doc/feature.md`, and create `doc/task/AUDIT_SEARCH-018_2026-10-27.md` after verification.

## Task 1: Stop CJK fuzzy false positives

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt`
- Move/update: SEARCH-018 story `todo/` → `inprogress/`

**Interface produced:** Private `containsCjk(text: String): Boolean`; CJK fuzzy matches are rejected if either the query token or normalized app label contains Han/Hiragana/Katakana/Hangul.

- [ ] **Step 1: Move story to in-progress and correct evidence.** Record that `微信读书` vs `微信支付` has distance 2 and does not match under current threshold 1 for length 4; record verified junk examples `微信读书` → `微信支书` and `微信读` → `微信书`. Keep the valid finding that whitespace-based word-prefix does not help CJK labels.

- [ ] **Step 2: Add focused failing regression tests** to `AppSearchEngineTest.kt`:

```kotlin
@Test
fun `fuzzy search does not match near miss Chinese Japanese or Korean labels`() {
    val chineseNearMiss = app("微信支书", "com.test.chinese", "Main")
    val japaneseNearMiss = app("カメヲ", "com.test.japanese", "Main")
    val koreanNearMiss = app("카카옹", "com.test.korean", "Main")

    assertTrue(AppSearchEngine.search(listOf(chineseNearMiss), "微信读书").isEmpty())
    assertTrue(AppSearchEngine.search(listOf(japaneseNearMiss), "カメラ").isEmpty())
    assertTrue(AppSearchEngine.search(listOf(koreanNearMiss), "카카오").isEmpty())
}

@Test
fun `latin typo tolerance remains enabled`() {
    val camera = app("Camera", "com.test.camera", "Main")
    assertEquals(listOf(camera), AppSearchEngine.search(listOf(camera), "camra"))
}
```

- [ ] **Step 3: Run tests and confirm red.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.AppSearchEngineTest' --rerun`
Expected: at least one CJK near-miss test fails because current fuzzy search returns the app; Latin case passes.

- [ ] **Step 4: Implement the script guard, retaining existing fuzzy threshold logic.** Add a private helper using Unicode script:

```kotlin
private fun containsCjk(value: String): Boolean = value.any { char ->
    when (Character.UnicodeScript.of(char.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL -> true
        else -> false
    }
}
```

At the beginning of `hasFuzzyMatch(label, token)`, return false when `containsCjk(label) || containsCjk(token)`. Keep Latin matching and all current tier scores unchanged.

- [ ] **Step 5: Run unit tests, then mutate to prove the guard is covered.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.AppSearchEngineTest' --rerun`
Expected: PASS. Temporarily remove the CJK early return, rerun this class with `--rerun`, and verify CJK near-miss test fails; restore source and rerun green.

- [ ] **Step 6: Commit fuzzy fix independently.**

```bash
git add app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt doc/task/inprogress/p3-search-search-018-cjk-fuzzy-and-pinyin-initials.md
git commit -m "fix(search): disable fuzzy typo matches for CJK labels"
```

## Task 2: Add offline Han pinyin matching

**Files:**
- Create: `app/src/main/java/com/mckimquyen/search/PinyinProvider.kt`
- Modify: `app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt`
- Modify: `app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt`
- Create: `app/src/test/java/com/mckimquyen/search/IcuPinyinProviderTest.kt`

**Consumes:** Task 1 `containsCjk`, unchanged `AppSearchEngine.search` ranking and tokenization.

**Interface produced:**

```kotlin
interface PinyinProvider {
    fun syllables(label: String): List<String>
}

object IcuPinyinProvider : PinyinProvider {
    override fun syllables(label: String): List<String>
}
```

`AppSearchEngine.search` appends `pinyinProvider: PinyinProvider = IcuPinyinProvider` after existing `nowMillis` parameter, preserving previous Kotlin/Java overloads through `@JvmOverloads`.

- [ ] **Step 1: Add fake-provider tests first** to `AppSearchEngineTest.kt`:

```kotlin
private class FakePinyinProvider(private val values: Map<String, List<String>>) : PinyinProvider {
    override fun syllables(label: String): List<String> = values[label].orEmpty()
}

@Test
fun `pinyin initials and full syllables match Han labels by prefix`() {
    val wechat = app("微信读书", "com.test.wechat", "Main")
    val pinyin = FakePinyinProvider(mapOf("微信读书" to listOf("wei", "xin", "du", "shu")))

    assertEquals(listOf(wechat), AppSearchEngine.search(listOf(wechat), "wx", pinyinProvider = pinyin))
    assertEquals(listOf(wechat), AppSearchEngine.search(listOf(wechat), "wxds", pinyinProvider = pinyin))
    assertEquals(listOf(wechat), AppSearchEngine.search(listOf(wechat), "weixin", pinyinProvider = pinyin))
    assertEquals(listOf(wechat), AppSearchEngine.search(listOf(wechat), "weixind", pinyinProvider = pinyin))
    assertTrue(AppSearchEngine.search(listOf(wechat), "xw", pinyinProvider = pinyin).isEmpty())
}

@Test
fun `pinyin does not apply to non-Han labels or non-letter queries`() {
    val latin = app("WeChat", "com.test.wechat", "Main")
    val han = app("微信", "com.test.han", "Main")
    val pinyin = FakePinyinProvider(mapOf("WeChat" to listOf("wei", "chat"), "微信" to listOf("wei", "xin")))

    assertTrue(AppSearchEngine.search(listOf(latin), "wx", pinyinProvider = pinyin).isEmpty())
    assertTrue(AppSearchEngine.search(listOf(han), "w-x", pinyinProvider = pinyin).isEmpty())
    assertTrue(AppSearchEngine.search(listOf(han), "12", pinyinProvider = pinyin).isEmpty())
}

@Test
fun `pinyin match ranks between label contains and package alias`() {
    val contains = app("Aweixin", "com.test.contains", "Main")
    val pinyin = app("微信读书", "com.test.pinyin", "Main")
    val alias = app("阅读", "com.test.weixin", "Main")
    val provider = FakePinyinProvider(mapOf("微信读书" to listOf("wei", "xin", "du", "shu")))

    assertEquals(
        listOf(contains, pinyin, alias),
        AppSearchEngine.search(listOf(alias, pinyin, contains), "weixin", pinyinProvider = provider)
    )
}
```

Scores: `Aweixin` hits label-contains (30), `微信读书` hits pinyin (35), `阅读` hits only the `weixin` package alias (40), so the full ranked list is asserted and input order is deliberately shuffled.

- [ ] **Step 2: Run focused tests and confirm red.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.AppSearchEngineTest' --rerun`
Expected: compile failure because `PinyinProvider`/`pinyinProvider` do not exist yet.

- [ ] **Step 3: Implement provider.** `IcuPinyinProvider` only transliterates labels containing Han. Use `Transliterator.getInstance("Han-Latin; Latin-ASCII")`, lowercase with `Locale.ROOT`, split transliteration on whitespace, discard empty syllables, and cache by source label in a thread-safe map. Catch ICU/linkage/runtime failures and return `emptyList()`; never fail the search request. Do not add an external dependency.

- [ ] **Step 4: Implement pinyin rank tier.** Add named `PINYIN_SCORE = 35` and `containsCjk`-independent `isAsciiLetters` helper. For each token, add a new branch to the existing `when` directly after `label.contains(token) -> 30` and before `packageAliases.any { ... } -> 40`, so an app matching both pinyin and an alias scores 35:
  - attempt pinyin only if `label` contains Han and token contains ASCII letters only;
  - derive initials as `syllables.joinToString("") { it.first().toString() }`;
  - derive full pinyin as `syllables.joinToString("")`;
  - match if either string starts with token, otherwise continue to fuzzy matching;
  - use score 35, so pinyin never outranks a direct label-contains match or loses to an alias match.

The provider is injected with default `IcuPinyinProvider`; keep all previous call sites source/binary compatible through `@JvmOverloads` and tests for default existing behavior.

- [ ] **Step 5: Add real ICU tests** in `IcuPinyinProviderTest.kt`, annotated with `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [28])`:

```kotlin
@Test
fun `Han labels produce normalized pinyin syllables`() {
    assertEquals(listOf("wei", "xin", "du", "shu"), IcuPinyinProvider.syllables("微信读书"))
    assertEquals(listOf("zhi", "fu", "bao"), IcuPinyinProvider.syllables("支付宝"))
    assertEquals(listOf("wang", "yi", "yun", "yin", "yue"), IcuPinyinProvider.syllables("网易云音乐"))
}
```

If ICU emits a different syllable boundary, verify transliteration output and assert its stable normalized syllables; do not weaken to a non-empty assertion.

- [ ] **Step 6: Run focused and full search unit tests.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.AppSearchEngineTest' --tests 'com.mckimquyen.search.IcuPinyinProviderTest' --tests 'com.mckimquyen.search.T9SearchEngineTest' --rerun`
Expected: PASS; all old search/T9 behavior remains unchanged.

- [ ] **Step 7: Mutation checks.** Temporarily (a) remove pinyin score branch, (b) change startsWith to contains, (c) remove Han-label guard, (d) remove ASCII-token guard. For each, run the focused classes with `--rerun` and verify at least one targeted test fails. Restore after each mutation and confirm green.

- [ ] **Step 8: Commit pinyin as second implementation commit.**

```bash
git add app/src/main/java/com/mckimquyen/search/PinyinProvider.kt app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt app/src/test/java/com/mckimquyen/search/IcuPinyinProviderTest.kt
git commit -m "feat(search): match Han app labels by pinyin initials"
```

## Task 3: Verify Home search on TECNO KJ7

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/search/PinyinSearchWidgetTest.kt`

- [ ] **Step 1: Add widget test** using the existing patterns in `app/src/androidTest/java/com/mckimquyen/search/AppSearchIntegrationTest.kt` and `app/src/androidTest/java/com/mckimquyen/ui/RecentInstallSearchWidgetTest.kt`. Seed visible app `App(label = "微信", packageName = "com.tencent.mm", name = "Main")`, launch `ActHome`, open SearchView, type `wx`, and assert visible result list contains only `com.tencent.mm`. Preserve and restore `RAppsSingleton.instance.apps`, cancel `RApplication.appRefreshPipeline`, and use `DeviceStateGuard`.

- [ ] **Step 2: Capture device state before install.** Use only serial `115333744A005844`. Save `databases/` and `shared_prefs/` with `run-as`; verify backup tar is non-empty before installing. Record locale, font scale, Contacts permission and device model/API. Do not invoke any `connected*` Gradle task.

- [ ] **Step 3: Build/install/run focused test.** Run `./gradlew assembleDevDebug assembleDevDebugAndroidTest`; install generated APKs using `adb -s 115333744A005844`; run only `PinyinSearchWidgetTest` with `am instrument`. Expected: `OK (1 test)`. If a screenshot reveals an ad overlay, stop immediately and wait for `done`.

- [ ] **Step 4: Restore and verify.** Force-stop app, restore `databases/` and `shared_prefs/`, re-export, extract both tar files, and verify `diff -rq` has no differences. Recheck locale, font scale, Contacts permission. Record timestamp, device/API, APK commit SHA, and test output.

- [ ] **Step 5: Commit widget test.**

```bash
git add app/src/androidTest/java/com/mckimquyen/search/PinyinSearchWidgetTest.kt
git commit -m "test(search): cover pinyin matching in Home search"
```

## Task 4: Full gate, audit, story close, and push

**Files:**
- Create: `doc/task/AUDIT_SEARCH-018_2026-10-27.md`
- Move: story `inprogress/` → `done/`
- Modify: `doc/task/README.md`, `doc/feature.md`

- [ ] Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun`; record unit XML counts and lint warning/error counts.
- [ ] Run `git diff --check` and `bash scripts/check-secrets.sh`; both must pass.
- [ ] Record evidence correction, owner decisions, fuzzy/pinyin mutations, Robolectric and real-device outcomes, and limits (Japanese romaji, heteronyms, ICU initialization latency). Mark UI accessibility as not applicable: no visible new UI/control/string; Home search widget covers user-visible result behavior. Mark persistence integration as not applicable: feature reads app labels and adds no stored state.
- [ ] Audit all changed code, compatibility (`@JvmOverloads`), cache behavior, locale-independent lowercasing, failure handling, and no new dependency/permission/string. Score must be strictly greater than 9.0 to push.
- [ ] Check and tick story acceptance/verification boxes. Update README and `doc/feature.md`; keep exactly one `## 🟡 In progress` heading. Move story to `done/` only after gates and audit pass.
- [ ] Commit audit/story/tracker docs. Push `origin/dev` only if score > 9.0. Do not stage `.idea/` or unrelated files.

## Self-review

- Spec coverage: fuzzy gate, Han pinyin initials/full prefix, ranking score, ICU provider/cache/fail-closed, fake + real ICU tests, KJ7 widget smoke, mutation checks, limitations and audit are all assigned tasks.
- Scope: one search subsystem with independently committed fuzzy and pinyin stages; both are retained because owner approved full SEARCH-018. No T9/UI setting work.
- Compatibility: append provider after `nowMillis`; generated overloads preserve old call signatures.
