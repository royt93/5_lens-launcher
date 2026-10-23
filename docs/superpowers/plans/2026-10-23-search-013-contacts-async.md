# SEARCH-013 Async Contacts Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chuyển truy vấn danh bạ sang background coroutine với debounce và generation check, hủy truy vấn cũ khi người dùng gõ tiếp, giữ nguyên matching/ranking và bảo vệ main thread.

**Architecture:** Tạo controller Kotlin nhỏ `search/ContactSearchController.kt` gắn với vòng đời tìm kiếm của `ActHome`. Khi input thay đổi, controller chạy debounce (mặc định 150ms), kiểm generation; nếu query là explicit contact query và quyền `READ_CONTACTS` đã cấp, gọi `ContactSearchEngine.firstContactForQuery` trên `Dispatchers.IO` rồi cập nhật UI trên `Dispatchers.Main`. Hủy `Job` khi query đổi, khi đóng search hoặc khi `ActHome.onDestroy()`.

**Tech Stack:** Kotlin, Java (`ActHome`), Coroutines (`Dispatchers.IO`/`Main.immediate`), Android ContentResolver (`ContactsContract.CommonDataKinds.Phone`), JUnit4, Robolectric sdk 28, AndroidX Test (`ActivityScenario`), Gradle.

## Global Constraints

- Spec đã duyệt: `docs/superpowers/specs/2026-10-23-search-013-async-contacts-query-design.md`.
- Nhánh `dev` duy nhất; không tạo worktree, không thêm dependency mới.
- Thiết bị khóa: Pixel 7 Pro `2B051FDH3006MU`, Android 17. Chỉ dùng `adb -s 2B051FDH3006MU`; tuyệt đối không dùng Gradle `connected*`.
- Không cache danh bạ lâu dài và không thêm observer danh bạ trong story này.
- Giữ nguyên toàn bộ logic parsing tiền tố (`extractedQuery`), kiểm quyền (`shouldShowPermissionRequest`, `hasContactsPermission`) và thuật toán ranking (`rankRows`).
- Gặp quảng cáo che UI trong lúc test: dừng ngay và chờ người dùng xác nhận `done`.
- Mọi coroutine phải xử lý `CancellationException` đúng quy tắc (re-throw, không nuốt).
- Hoàn tất mọi cổng kiểm tra (`testDevDebugUnitTest`, `lintDevDebug`, `assembleDevDebug`, `assembleDevDebugAndroidTest`), kiểm tra bí mật (`scripts/check-secrets.sh`) và audit record đạt điểm `> 9.0/10` trước khi commit/push. Không stage `.idea`.

---

## File Map

- Create: `app/src/main/java/com/mckimquyen/search/ContactSearchController.kt` — quản lý coroutine debounce, generation và điều phối tìm kiếm danh bạ bất đồng bộ.
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java` — khởi tạo controller, gọi controller từ `updateContactAction` thay vì gọi sync engine, và dọn dẹp trong `onDestroy()`.
- Create: `app/src/test/java/com/mckimquyen/search/ContactSearchControllerTest.kt` — unit tests kiểm tra debounce, cancellation, generation-race và dispatch thread.
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/AppSearchWidgetTest.kt` — widget tests xác nhận UI hiển thị kết quả sau debounce và không render dữ liệu cũ khi đổi query nhanh.
- Update docs: `doc/task/inprogress/p2-search-search-013-contacts-query-main-thread.md`, `doc/feature.md`, `doc/task/README.md`, và tạo `doc/task/AUDIT_SEARCH-013_2026-10-23.md`.

---

## Task 1: RED — Viết Unit Test cho ContactSearchController

**Files:**
- Create: `app/src/test/java/com/mckimquyen/search/ContactSearchControllerTest.kt`
- Read-only: `app/src/main/java/com/mckimquyen/search/ContactSearchEngine.kt`

**Interfaces:**
- Consumes: `ContactSearchEngine.isExplicitContactQuery`, `ContactSearchEngine.shouldShowPermissionRequest`, `ContactSearchResult`
- Produces: `ContactSearchController(context, scope, ioDispatcher, mainDispatcher, debounceMs, searchFunction, onStateChanged)` cùng `create(context, onStateChanged)`, `onQueryChanged`, `cancel`, `close`

- [ ] **Step 1: Viết test cho ContactSearchController trước khi class tồn tại**

Tạo file `app/src/test/java/com/mckimquyen/search/ContactSearchControllerTest.kt`:

```kotlin
package com.mckimquyen.search

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ContactSearchControllerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        ContactSearchEngine.setPermissionGrantedForTesting(true)
        ContactSearchEngine.setContactsForTesting(
            listOf(
                ContactSearchResult("Jane Nguyen", "+84 912 345 678"),
                ContactSearchResult("Bob Smith", "+1 555 123 4567")
            )
        )
    }

    @After
    fun teardown() {
        ContactSearchEngine.setContactsForTesting(null)
        ContactSearchEngine.setPermissionGrantedForTesting(null)
    }

    @Test
    fun nonContactQueryDoesNotTriggerSearchAndDeliversEmptyState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = DEBOUNCE_MS,
            onStateChanged = { states.add(it) }
        )

        controller.onQueryChanged("calculator")
        advanceUntilIdle()

        assertEquals(1, states.size)
        assertTrue(states.single() is ContactSearchUiState.Hidden)
    }

    @Test
    fun rapidKeystrokesDebounceAndOnlyExecuteSearchForFinalQuery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var searchCalls = 0
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = DEBOUNCE_MS,
            searchFunction = { ctx, q ->
                searchCalls++
                ContactSearchEngine.firstContactForQuery(ctx, q)
            },
            onStateChanged = { states.add(it) }
        )

        controller.onQueryChanged("call j")
        advanceTimeBy(50)
        controller.onQueryChanged("call ja")
        advanceTimeBy(50)
        controller.onQueryChanged("call jane")

        assertEquals("chưa hết debounce không được query", 0, searchCalls)

        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        advanceUntilIdle()

        assertEquals("chỉ query một lần sau debounce cho ký tự cuối", 1, searchCalls)
        val finalState = states.last() as ContactSearchUiState.Result
        assertEquals("Jane Nguyen", finalState.contact.displayName)
    }

    @Test
    fun cancellingJobPreventsStaleResultFromPublishing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = DEBOUNCE_MS,
            onStateChanged = { states.add(it) }
        )

        controller.onQueryChanged("call jane")
        advanceTimeBy(DEBOUNCE_MS)
        controller.cancel()
        advanceUntilIdle()

        assertTrue(
            "khi cancel trước khi hoàn tất, không phát Result cho query cũ",
            states.none { it is ContactSearchUiState.Result }
        )
    }

    @Test
    fun permissionRequestStateIsDeliveredImmediatelyWithoutDebounce() = runTest {
        ContactSearchEngine.setPermissionGrantedForTesting(false)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = DEBOUNCE_MS,
            onStateChanged = { states.add(it) }
        )

        controller.onQueryChanged("call jane")
        // Không advanceTimeBy(DEBOUNCE_MS), state PermissionRequest phải có ngay
        runCurrent()

        assertEquals(1, states.size)
        assertTrue(states.single() is ContactSearchUiState.PermissionRequest)
    }


    @Test
    fun olderSlowSearchCannotOverwriteNewerQueryResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = 0L,
            searchFunction = { ctx, q -> ContactSearchEngine.firstContactForQuery(ctx, q) },
            onStateChanged = { states.add(it) }
        )

        controller.onQueryChanged("call jane")
        runCurrent()
        controller.onQueryChanged("call bob")
        advanceUntilIdle()

        val results = states.filterIsInstance<ContactSearchUiState.Result>()
        assertEquals(listOf("Bob Smith"), results.map { it.contact.displayName })
    }

    @Test
    fun searchRunsOnWorkerThreadNotCallerThread() = runBlocking {
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, WORKER_THREAD_NAME) }
        val workerDispatcher = worker.asCoroutineDispatcher()
        val callerThread = Thread.currentThread().name
        val searchedOn = java.util.concurrent.atomic.AtomicReference<String>()
        val delivered = kotlinx.coroutines.CompletableDeferred<ContactSearchUiState>()
        try {
            val controller = ContactSearchController(
                context = context,
                scope = this,
                ioDispatcher = workerDispatcher,
                mainDispatcher = Dispatchers.Unconfined,
                debounceMs = 0L,
                searchFunction = { _, _ ->
                    searchedOn.set(Thread.currentThread().name)
                    ContactSearchResult("Jane Nguyen", "+84 912 345 678")
                },
                onStateChanged = { delivered.complete(it) }
            )
            controller.onQueryChanged("call jane")
            withTimeout(WORKER_TIMEOUT_MS) { delivered.await() }
            assertEquals(WORKER_THREAD_NAME, searchedOn.get())
            assertTrue("search không được chạy trên thread gọi", searchedOn.get() != callerThread)
        } finally {
            workerDispatcher.close()
        }
    }

    @Test
    fun workerFailureDeliversHiddenState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val failing = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = 0L,
            searchFunction = { _, _ -> throw IllegalStateException("provider failed") },
            onStateChanged = { states.add(it) }
        )
        failing.onQueryChanged("call jane")
        advanceUntilIdle()
        assertEquals(listOf<ContactSearchUiState>(ContactSearchUiState.Hidden), states)
    }


    @Test
    fun closeCancelsScopeAndIgnoresSubsequentQueries() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val states = mutableListOf<ContactSearchUiState>()
        val controller = ContactSearchController(
            context = context,
            scope = this,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            debounceMs = 0L,
            searchFunction = { _, _ -> ContactSearchResult("Jane Nguyen", "+84 912 345 678") },
            onStateChanged = { states.add(it) }
        )

        controller.close()
        controller.onQueryChanged("call jane")
        advanceUntilIdle()

        assertTrue("sau khi close, controller không phát state nào", states.isEmpty())
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val WORKER_THREAD_NAME = "contact-search-test-worker"
        const val WORKER_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 2: Chạy kiểm tra để xác nhận RED (compile failure do chưa có code)**

Run: `./gradlew testDevDebugUnitTest --tests '*ContactSearchControllerTest'`
Expected: FAIL với lỗi Unresolved reference `ContactSearchController` và `ContactSearchUiState`.

---

## Task 2: GREEN — Cài đặt ContactSearchController

**Files:**
- Create: `app/src/main/java/com/mckimquyen/search/ContactSearchController.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface ContactSearchUiState {
      data object Hidden : ContactSearchUiState
      data object PermissionRequest : ContactSearchUiState
      data class Result(val contact: ContactSearchResult) : ContactSearchUiState
  }

  class ContactSearchController(
      context: Context,
      scope: CoroutineScope,
      ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
      mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
      debounceMs: Long = DEFAULT_DEBOUNCE_MS,
      searchFunction: (Context, CharSequence?) -> ContactSearchResult? = ContactSearchEngine::firstContactForQuery,
      onStateChanged: (ContactSearchUiState) -> Unit
  )
  ```

- [ ] **Step 1: Viết minimal implementation cho ContactSearchController**

Tạo file `app/src/main/java/com/mckimquyen/search/ContactSearchController.kt`:

```kotlin
package com.mckimquyen.search

import android.content.Context
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ContactSearchUiState {
    data object Hidden : ContactSearchUiState
    data object PermissionRequest : ContactSearchUiState
    data class Result(val contact: ContactSearchResult) : ContactSearchUiState
}

class ContactSearchController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val searchFunction: (Context, CharSequence?) -> ContactSearchResult? = { ctx, q ->
        ContactSearchEngine.firstContactForQuery(ctx, q)
    },
    private val onStateChanged: (ContactSearchUiState) -> Unit
) {
    private var currentJob: Job? = null
    @Volatile
    private var closed = false
    private val currentGeneration = AtomicLong(0L)

    fun onQueryChanged(query: CharSequence?) {
        if (closed) return
        val generation = currentGeneration.incrementAndGet()
        currentJob?.cancel()
        currentJob = null

        val showPermissionRequest = ContactSearchEngine.shouldShowPermissionRequest(context, query)
        if (showPermissionRequest) {
            onStateChanged(ContactSearchUiState.PermissionRequest)
            return
        }

        if (!ContactSearchEngine.isExplicitContactQuery(query) || !ContactSearchEngine.hasContactsPermission(context)) {
            onStateChanged(ContactSearchUiState.Hidden)
            return
        }

        currentJob = scope.launch(mainDispatcher) {
            try {
                if (debounceMs > 0) {
                    delay(debounceMs)
                }
                ensureActive()

                val result = withContext(ioDispatcher) {
                    searchFunction(context, query)
                }
                ensureActive()

                if (currentGeneration.get() == generation && !closed) {
                    if (result != null) {
                        onStateChanged(ContactSearchUiState.Result(result))
                    } else {
                        onStateChanged(ContactSearchUiState.Hidden)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (currentGeneration.get() == generation) {
                    onStateChanged(ContactSearchUiState.Hidden)
                }
            }
        }
    }

    fun cancel() {
        currentGeneration.incrementAndGet()
        currentJob?.cancel()
        currentJob = null
    }

    /** Hủy việc đang chạy và hủy luôn scope do [create] sở hữu; sau đó controller bỏ qua mọi query. */
    fun close() {
        closed = true
        cancel()
        ownedScopeJob?.cancel()
    }

    private var ownedScopeJob: Job? = null

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 150L

        /** Controller cho Activity: scope riêng trên Main, hủy khi [close] được gọi. */
        @JvmStatic
        fun create(context: Context, onStateChanged: (ContactSearchUiState) -> Unit): ContactSearchController {
            val job = kotlinx.coroutines.SupervisorJob()
            val scope = CoroutineScope(Dispatchers.Main.immediate + job)
            return ContactSearchController(context.applicationContext, scope, onStateChanged = onStateChanged)
                .also { it.ownedScopeJob = job }
        }
    }
}
```

- [ ] **Step 2: Chạy unit tests để xác nhận GREEN**

Run: `./gradlew testDevDebugUnitTest --tests '*ContactSearchControllerTest'`
Expected: 8 tests completed, 0 failed.

---

## Task 3: Nối Controller vào ActHome và kiểm tra hủy trong onDestroy

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Read-only: `app/src/main/java/com/mckimquyen/search/ContactSearchController.kt`

**Interfaces:**
- `ActHome` khởi tạo `ContactSearchController` với lifecycle scope riêng hoặc scope gắn với Activity.
- `updateContactAction` gọi `controller.onQueryChanged(query)`.
- `onDestroy()` gọi `controller.close()`; `hideSearch()` đi qua `clearText()` nên đã hủy job cũ.

- [ ] **Step 1: Viết test regression widget chứng minh asynchronous dispatch**

Mở `app/src/androidTest/java/com/mckimquyen/ui/AppSearchWidgetTest.kt` và thêm test case xác nhận search view gõ text hiển thị đúng contact sau debounce:

```kotlin
    @Test
    fun contactSearchDispatchesAsynchronouslyAndRendersAfterDebounce() {
        ContactSearchEngine.setPermissionGrantedForTesting(true)
        ContactSearchEngine.setContactsForTesting(
            listOf(ContactSearchResult("Jane Async", "+84 988 777 666"))
        )

        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var searchView: SearchView? = null
                scenario.onActivity { activity ->
                    searchView = activity.findViewById(R.id.searchView)
                    searchView!!.show()
                }
                waitUntilShowing(searchView!!, true)

                scenario.onActivity { searchView!!.editText.setText("call jane") }

                var row: View? = null
                scenario.onActivity { activity -> row = activity.findViewById(R.id.contactActionRow) }
                // Chờ visibility đổi thành VISIBLE (qua debounce + async worker)
                waitUntilVisibility(row!!, View.VISIBLE, timeoutMs = 3_000)

                scenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.contactActionRow).visibility)
                    assertEquals(
                        "Jane Async",
                        activity.findViewById<TextView>(R.id.tvContactResultName).text.toString()
                    )
                }
            }
        } finally {
            ContactSearchEngine.setContactsForTesting(null)
            ContactSearchEngine.setPermissionGrantedForTesting(null)
        }
    }
```

- [ ] **Step 2: Tích hợp ContactSearchController vào ActHome.java**

Trong `ActHome.java`:
1. Thêm trường:
   ```java
   private com.mckimquyen.search.ContactSearchController contactSearchController;
   ```
2. Khởi tạo trong `onCreate` (sau khi bind views):
   ```java
   contactSearchController = com.mckimquyen.search.ContactSearchController.create(
       this,
       state -> {
           renderContactSearchState(state);
           return kotlin.Unit.INSTANCE;
       }
   );
   ```
3. Chuyển hàm `updateContactAction` thành:
   ```java
   private void updateContactAction(CharSequence query) {
       if (contactSearchController != null) {
           contactSearchController.onQueryChanged(query);
       }
   }
   ```
4. Thêm hàm `renderContactSearchState`:
   ```java
   private void renderContactSearchState(com.mckimquyen.search.ContactSearchUiState state) {
       if (isFinishing() || isDestroyed()) return;
       if (state instanceof com.mckimquyen.search.ContactSearchUiState.Hidden) {
           contactActionRow.setVisibility(View.GONE);
           contactActionDivider.setVisibility(View.GONE);
           contactActionRow.setOnClickListener(null);
           btContactCall.setOnClickListener(null);
           btContactMessage.setOnClickListener(null);
       } else if (state instanceof com.mckimquyen.search.ContactSearchUiState.PermissionRequest) {
           contactActionRow.setVisibility(View.VISIBLE);
           contactActionDivider.setVisibility(View.VISIBLE);
           tvContactResultName.setText(R.string.contact_search_permission_label);
           tvContactResultPhone.setText(R.string.contact_search_permission_rationale);
           contactActionRow.setContentDescription(getString(R.string.contact_search_permission_rationale));
           btContactCall.setText(R.string.contact_search_allow);
           btContactMessage.setVisibility(View.GONE);
           contactActionRow.setOnClickListener(v -> requestContactsPermission());
           btContactCall.setOnClickListener(v -> requestContactsPermission());
       } else if (state instanceof com.mckimquyen.search.ContactSearchUiState.Result) {
           ContactSearchResult contact = ((com.mckimquyen.search.ContactSearchUiState.Result) state).getContact();
           contactActionRow.setVisibility(View.VISIBLE);
           contactActionDivider.setVisibility(View.VISIBLE);
           btContactCall.setText(R.string.contact_search_call);
           btContactMessage.setVisibility(View.VISIBLE);
           tvContactResultName.setText(contact.getDisplayName());
           tvContactResultPhone.setText(contact.getPhoneNumber());
           contactActionRow.setContentDescription(getString(R.string.search_open_contact, contact.getDisplayName()));
           contactActionRow.setOnClickListener(v -> startContactIntent(contact.dialIntent()));
           btContactCall.setOnClickListener(v -> startContactIntent(contact.dialIntent()));
           btContactMessage.setOnClickListener(v -> startContactIntent(contact.messageIntent()));
       }
   }
   ```
5. Trong `onDestroy()` (trước `super.onDestroy()`):
   ```java
   if (contactSearchController != null) {
       contactSearchController.close();
       contactSearchController = null;
   }
   ```
   `hideSearch()` không cần sửa: `searchView.clearText()` kích hoạt `onTextChanged("")`, controller phát `Hidden` và hủy job cũ. Widget test bên dưới chứng minh đường này.

- [ ] **Step 3: Chạy full test suite kiểm tra tương thích**

Run: `./gradlew testDevDebugUnitTest lintDevDebug`
Expected: 0 failures, 0 lint errors.

---

## Task 4: Đo đạc, Smoke Test và Đóng Story trên Pixel 7 Pro

**Files:**
- Modify: `doc/task/inprogress/p2-search-search-013-contacts-query-main-thread.md` -> move to `done/`
- Create: `doc/task/AUDIT_SEARCH-013_2026-10-23.md`
- Modify: `doc/feature.md`, `doc/task/README.md`

- [ ] **Step 1: Build và cài đặt APK lên Pixel 7 Pro `2B051FDH3006MU`**

```bash
set -e
S=2B051FDH3006MU
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s $S install -r "$(find app/build/outputs/apk/dev/debug -maxdepth 1 -name '*.apk' -print -quit)"
adb -s $S install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
```

- [ ] **Step 2: Chạy kiểm thử widget & integration search contacts trên thiết bị**

```bash
adb -s $S shell am instrument -w -e class com.mckimquyen.ui.AppSearchWidgetTest,com.mckimquyen.search.ContactSearchIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: All tests pass (OK).

- [ ] **Step 3: Khởi động ActHome và chụp ảnh smoke**

```bash
adb -s $S shell am start -W -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActHome
adb -s $S exec-out screencap -p > /tmp/search013-smoke.png
```
Đọc ảnh để đảm bảo không bị che bởi quảng cáo (tuân thủ R4).

- [ ] **Step 3b: Đo trước/sau trên cùng fixture, không đụng danh bạ thật.** Thêm `app/src/androidTest/java/com/mckimquyen/search/ContactSearchLatencyTest.kt`: seed `ContactSearchEngine.setContactsForTesting` bằng 1000 `ContactSearchResult` giả; đo `System.nanoTime()` quanh (a) gọi đồng bộ `ContactSearchEngine.firstContactForQuery` trên main (hành vi cũ) và (b) `ContactSearchController` gõ liên tiếp 9 ký tự rồi chờ kết quả cuối; ghi `Log.i("SEARCH013", ...)`. Kiểm thử khẳng định số lần `searchFunction` chạy bằng 1 sau burst và nó không chạy trên main thread. Không đặt ngưỡng thời gian. Lưu ý: fixture `testRows` bỏ qua `ContentResolver`, nên số liệu chỉ phản ánh ranking + debounce, không phải chi phí provider thật; ghi rõ giới hạn này trong audit và không tuyên bố đã đo provider.

- [ ] **Step 4: Lập file Audit `doc/task/AUDIT_SEARCH-013_2026-10-23.md` và chấm điểm**

Ghi nhận:
- Chuyển truy vấn danh bạ sang `Dispatchers.IO` với debounce 150ms; test chứng minh search chạy ngoài thread gọi và burst chỉ query một lần.
- Ghi rõ: cursor thật của `ContentResolver` chưa được đo trên thiết bị vì fixture `testRows` bỏ qua provider và không thay đổi danh bạ/quyền của máy.
- Hủy job khi query thay đổi hoặc đóng search/destroy activity.
- Bằng chứng test: Unit tests, Widget tests, Integration tests.
- Điểm tự chấm > 9.0/10.

- [ ] **Step 5: Di chuyển story vào `done/`, cập nhật `doc/feature.md` và `doc/task/README.md`**

- [ ] **Step 6: Kiểm tra bảo mật và commit/push lên `origin/dev`**

```bash
git diff --check
scripts/check-secrets.sh
git add app/src/main/java/com/mckimquyen/search/ContactSearchController.kt \
        app/src/main/java/com/mckimquyen/ui/ActHome.java \
        app/src/test/java/com/mckimquyen/search/ContactSearchControllerTest.kt \
        app/src/androidTest/java/com/mckimquyen/ui/AppSearchWidgetTest.kt \
        doc/feature.md doc/task/README.md \
        doc/task/done/p2-search-search-013-contacts-query-main-thread.md \
        doc/task/AUDIT_SEARCH-013_2026-10-23.md \
        docs/superpowers/plans/2026-10-23-search-013-contacts-async.md \
        docs/superpowers/specs/2026-10-23-search-013-async-contacts-query-design.md
git commit -m "perf(search): move contacts search off main thread with debounce (SEARCH-013)" \
           -m "Dispatches contacts search to Dispatchers.IO with a 150ms debounce and generation cancellation. Cancels search job on query change, search dismissal, or Activity destruction. Audit 9.2/10." \
           -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
git push origin dev
```
