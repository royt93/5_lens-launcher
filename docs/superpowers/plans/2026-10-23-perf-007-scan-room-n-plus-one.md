# PERF-007 Room Scan Batching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bỏ N lượt đọc palette và N transaction ghi palette trong một scan launcher app.

**Architecture:** `UtilApp.getAppShells` đọc `getAllRows()` một lần, giữ palette row đầu tiên theo identifier trong map và gom row palette mới. DAO thực hiện `insertIfAbsent` + `updatePaletteColor` cho pending rows trong một transaction; không REPLACE entity. Không đổi schema hoặc generation guard.

**Tech Stack:** Kotlin, Java, Room 2.x hiện có, JUnit4, Robolectric sdk 28, Mockito hiện có, Android instrumentation, Gradle.

## Global Constraints

- Spec đã duyệt: `docs/superpowers/specs/2026-10-23-perf-007-scan-room-n-plus-one-design.md`.
- Chỉ nhánh `dev`; không worktree, không dependency mới, không migration/schema thay đổi.
- Pixel 7 Pro `2B051FDH3006MU` là target duy nhất. Chỉ `adb -s 2B051FDH3006MU`; không `connected*`. Phối hợp phiên khác trước smoke.
- Không clear DB/prefs, cài/gỡ app hay đổi launcher/locale để benchmark. Chỉ dùng DB fixture riêng; khôi phục singleton DB trong `finally`.
- Gặp quảng cáo che UI: dừng chờ owner `done`; không tự chấp nhận consent.
- Một SELECT chỉ áp dụng phần palette của `getAppShells`; merge lens/open count vẫn có query riêng.
- Một transaction không đồng nghĩa một SQL. Với K palette mới vẫn có K INSERT + K UPDATE; điều giảm là số transaction và số SELECT.
- Nếu nhiều lens lưu palette khác nhau, giữ row đầu gặp trong `getAllRows`; không tự ưu tiên non-zero/MAX/lens hiện hành. Legacy `findAnyByIdentifier LIMIT 1` cũng không có thứ tự xác định.
- Lỗi/cancellation phải được truyền lên và transaction rollback; không catch nuốt lỗi.
- Ghi docs theo kết quả thật, không chấm pass dự kiến. Commit/push sau full gate, device smoke và audit >9; không stage `.idea`.

---

## File Map

- Modify `app/src/main/java/com/mckimquyen/model/AppPersistentDao.kt`: helper `@Transaction suspend fun setPaletteColors(rows: List<AppPersistent>)`.
- Modify `app/src/main/java/com/mckimquyen/util/UtilApp.kt`: đọc map, tính màu và ghi pending cuối scan.
- Create `app/src/test/java/com/mckimquyen/model/AppPersistentPaletteBatchTest.kt`: Room thật, bảo toàn state + rollback.
- Create `app/src/test/java/com/mckimquyen/util/UtilAppPaletteScanTest.kt`: gọi scanner thật qua PackageManager fixture, đếm SQL Room thật.
- Create `app/src/androidTest/java/com/mckimquyen/util/PaletteScanBenchmarkTest.kt`: DB tạm riêng, đo phần Room và full scan; không seed DB người dùng.
- Update story `doc/task/inprogress/p2-perf-perf-007-scan-n-plus-one-room.md`, `doc/feature.md`, `doc/task/README.md`, audit `doc/task/AUDIT_PERF-007_<ngày thực thi>.md`.

## Task 1: Room palette batch, giữ ngữ nghĩa và rollback

**Interfaces:** dùng `AppPersistent.defaults(packageName, name)`; conflict `(lensId, identifier)`; helper mới `setPaletteColors(rows: List<AppPersistent>): Unit`. Caller không gọi helper khi pending rỗng.

- [ ] **Step 1: Tạo fixture test Room.** Theo `AppPersistentRoomTest.kt:27-40,321-327`: `Room.inMemoryDatabaseBuilder`, đóng DB trong `@After`, lưu/khôi phục `AppDatabase.INSTANCE` qua reflection nếu test cần dùng singleton. Dùng nullable `AppDatabase?` + `requireNotNull`, không thêm `lateinit`.

```kotlin
private var database: AppDatabase? = null
private val db get() = requireNotNull(database)
private val dao get() = db.appPersistentDao()

@Before
fun setup() {
    database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
}

@After
fun teardown() {
    database?.close()
    database = null
}
```

- [ ] **Step 2: Viết batch test trước API mới.** Seed default row với visibility/lock/favorite/folder/pinnedZone/order/openCount tùy biến, cùng identifier ở lens khác, và app chưa có row. Gọi helper mới; so sánh toàn entity cũ `copy(paletteColor = NEW_COLOR)` kể cả id, không chỉ vài field.

```kotlin
@Test
fun batchOnlyChangesPaletteAndSeedsMissingDefaults() = runBlocking {
    val first = AppPersistent.defaults(FIRST_PACKAGE, COMPONENT).copy(
        appVisible = false, appOpened = false, isFavorite = true,
        folderName = FOLDER, pinnedZone = PinnedZone.START.name,
        orderNumber = CUSTOM_ORDER, openCount = OPEN_COUNT
    )
    dao.insert(first)
    dao.insert(first.copy(lensId = SECOND_LENS))
    val before = dao.getAllRows()
    val fresh = AppPersistent.defaults(SECOND_PACKAGE, COMPONENT).copy(paletteColor = SECOND_COLOR)
    dao.setPaletteColors(listOf(first.copy(paletteColor = FIRST_COLOR), fresh))
    val after = dao.getAllRows()
    before.forEach { old ->
        assertEquals(old.copy(paletteColor = FIRST_COLOR), after.single { it.id == old.id })
    }
    assertEquals(SECOND_COLOR, dao.findByIdentifier(fresh.identifier)?.paletteColor)
}
```

Các fixture literal được khai báo trong `private companion object`; palette là màu ARGB opaque khác 0, không chọn các số mang nghĩa nghiệp vụ rải rác trong test.

- [ ] **Step 3: Chạy RED:** `./gradlew testDevDebugUnitTest --tests '*AppPersistentPaletteBatchTest'`. Trước API có compile RED `Unresolved reference setPaletteColors`; sau khi thêm chữ ký, test phải kiểm chứng hành vi thật. Không nhận NPE/setup lỗi là RED đúng.
- [ ] **Step 4: Thêm helper tối thiểu:**

```kotlin
@Transaction
suspend fun setPaletteColors(rows: List<AppPersistent>) {
    rows.forEach { row ->
        insertIfAbsent(row)
        updatePaletteColor(row.identifier, row.paletteColor)
    }
}
```

Không gọi `setPaletteColor` từ đây vì helper đó có transaction riêng; không dùng `@Insert(REPLACE)` hay `@Update` toàn entity. Caller tạo defaults, không tái sử dụng row lens tùy ý cho app mới.

- [ ] **Step 5: Kiểm tra row chỉ có ở lens khác.** Seed `defaults.copy(lensId = SECOND_LENS)`, gọi batch với defaults; assert row lens khác giữ field ngoài palette và row default mới tồn tại. Nếu row đó đã có màu non-zero, scanner ở Task 2 không tạo pending.
- [ ] **Step 6: Viết test rollback giữa lô.** Seed hai app, tạo trigger gây ABORT khi UPDATE app thứ hai. Không dùng mock rollback.

```kotlin
db.openHelper.writableDatabase.execSQL("""
    CREATE TEMP TRIGGER fail_second_palette
    BEFORE UPDATE OF PALETTE_COLOR ON APP_PERSISTENT
    WHEN NEW.IDENTIFIER = '$secondIdentifier'
    BEGIN SELECT RAISE(ABORT, 'palette batch fixture'); END
""".trimIndent())
val before = dao.getAllRows()
val failure = runCatching { dao.setPaletteColors(pendingRows) }.exceptionOrNull()
assertNotNull(failure)
assertEquals(before, dao.getAllRows())
```

`secondIdentifier` chỉ là constant fixture nội bộ, không nội suy dữ liệu người dùng vào SQL. Thêm test cancellation ở unit DAO nếu dùng delegation fake: delegate DAO thật, override `updatePaletteColor` ném `CancellationException` ở row thứ hai, bọc bằng Room `withTransaction` của chính helper production nếu fixture hiện có cho phép. Không thêm API production chỉ để chèn cancellation; nếu không thể kiểm transaction helper theo cách đó, báo giới hạn, vẫn phải kiểm throwable rollback trên Room thật và không nuốt cancellation trong code.

- [ ] **Step 7: GREEN:** chạy test mới và `*AppPersistentRoomTest`, xác nhận mọi database/executor fixture được close/shutdown. Commit checkpoint chỉ sau verification theo owner gate; không tự push riêng task.

## Task 2: Scanner đọc một lần và ghi một transaction

**Interfaces:** `UtilApp.getAppShells(packageManager, context, application, iconPackLabelName)` hiện có; không đổi chữ ký public. Tái dùng `dao.getAllRows()` và helper Task 1.

- [ ] **Step 1: Fixture scanner không sửa production.** Dùng Room in-memory thật và set/restore `AppDatabase.INSTANCE` như test hiện có; mock `PackageManager`, `ResolveInfo`, `Application` và resources theo `UtilAppIconPackDiscoveryTest`. Launcher query (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) trả fixture apps; mọi icon-pack launcher action trả empty list. Icon bitmap thuộc fixture phải recycle sau khi mọi coroutine hoàn tất.

```kotlin
Mockito.`when`(pm.queryIntentActivities(any(Intent::class.java), anyInt())).thenAnswer { call ->
    val intent = call.getArgument<Intent>(0)
    if (intent.action == Intent.ACTION_MAIN) launcherActivities else emptyList<ResolveInfo>()
}
```

Mỗi `ResolveInfo` có `ActivityInfo.packageName`, `name` và `iconResource`; mock `loadLabel(pm)`, `getApplicationInfo`, `getResourcesForApplication`, `getApplicationIcon` trả fixture bitmap giống test reload hiện tại. `getPackageInfo` trả version/install time hợp lệ để không fail sai lý do.

- [ ] **Step 2: Đếm SQL trong Room builder bằng `setQueryCallback`.** Dùng direct executor `Executor { it.run() }` và synchronized list; mở DB/schema và seed trước, sau đó clear counter ngay trước scan. Phân loại SELECT theo `APP_PERSISTENT` chứ không đếm PackageManager queries/schema PRAGMA. Đếm BEGIN/COMMIT nếu callback hỗ trợ; nếu Room không report transaction boundary, dùng delegating DAO test helper để đếm helper invocation kết hợp rollback Room test, không suy diễn từ số UPDATE.

```kotlin
val statements = java.util.Collections.synchronizedList(mutableListOf<String>())
val builder = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
    .setQueryCallback({ sql, _ -> statements.add(sql) }, java.util.concurrent.Executor { it.run() })
```

- [ ] **Step 3: RED qua public scanner thật.** Seed 3 launcher apps đều có palette khác 0; một app chỉ ở lens phụ. `getAppShells` baseline có 3 SELECT, mong đợi 1. Assert màu trả về đúng và không có ghi palette. Assertion query-count phải fail 3 vs 1, không compile/setup failure.

```kotlin
val shells = UtilApp.getAppShells(pm, context, application, UtilSettings.DEFAULT_ICON_PACK_PACKAGE_NAME)
val reads = statements.filter {
    it.trimStart().startsWith("SELECT", ignoreCase = true) && it.contains("APP_PERSISTENT", ignoreCase = true)
}
assertEquals(APP_COUNT, shells.size)
assertEquals(1, reads.size)
assertTrue(reads.none { it.contains("WHERE IDENTIFIER =", ignoreCase = true) })
```

- [ ] **Step 4: Thay code palette tối thiểu:**

```kotlin
// Trước vòng lặp apps, chỉ lấy DAO một lần.
val dao = AppDatabase.getInstance().appPersistentDao()
val paletteColors = HashMap<String, Int>()
dao.getAllRows().forEach { row ->
    if (!paletteColors.containsKey(row.identifier)) {
        paletteColors[row.identifier] = row.paletteColor
    }
}
val pendingPalettes = ArrayList<AppPersistent>()
```

Thay phần read/write trong vòng lặp bằng:

```kotlin
val identifier = AppPersistent.generateIdentifier(packageName, name)
var paletteColor = paletteColors[identifier] ?: 0
if (paletteColor == 0 && icon != null) {
    paletteColor = UtilColor.getPaletteColorFromBitmap(icon)
    if (paletteColor != 0) {
        paletteColors[identifier] = paletteColor
        pendingPalettes.add(AppPersistent.defaults(packageName, name).copy(paletteColor = paletteColor))
    }
}
```

Cuối vòng lặp, trước `return shells`:

```kotlin
if (pendingPalettes.isNotEmpty()) dao.setPaletteColors(pendingPalettes)
```

- [ ] **Step 5: GREEN query-count test**, chạy cả `*UtilAppIconReloadTest`, `*UtilAppIconPackIdentityTest`, `*TaskUpdateAppsTest` và Room batch tests. Không thay mock expectation để giấu SELECT thừa.
- [ ] **Step 6: Thêm failure/edge assertions:** app mới có icon sinh palette được persist; app thiếu icon vẫn có shell nhưng không ghi; danh sách rỗng không ghi; component lặp cùng palette chỉ tạo một pending entry; row màu đã có không đổi. Nếu màu 0 không thể phát sinh tự nhiên vì `UtilColor` fallback `Color.BLACK`, không thêm seam color chỉ để test case bất khả thi; ghi code guard và giới hạn trong audit.
- [ ] **Step 7: Đếm transaction batch trên cold fixture:** clear SQL sau seed, scan nhiều app cần palette; assert 1 SELECT, K INSERT, K UPDATE, một batch transaction nếu driver report boundaries. Kiểm state cuối bằng truy vấn sau khi chụp counter. Lỗi do trigger ở row cuối phải giữ DB trước đó và ngăn scanner trả snapshot mới.

## Task 3: Benchmark, UI regression và đóng story

**Interfaces:** dùng helper Task 1; benchmark baseline nội bộ giữ đúng read/palette decode/write semantics cũ. Không đổi production để thêm benchmark mode.

- [ ] **Step 1: Ghi baseline host và device.** `git diff --check`; `adb devices -l`; xác nhận chỉ gửi lệnh tới serial khóa. Snapshot prefs/settings/DB fixture và model/build, ghi candidate HEAD + pending diff checksum. Không ghi token SDK ra docs.
- [ ] **Step 2: Benchmark DB fixture riêng.** Tạo `Room.inMemoryDatabaseBuilder` riêng không thay DB singleton đang được app thật dùng trên thiết bị. So baseline đọc từng identifier + `setPaletteColor` từng app với một `getAllRows` + `setPaletteColors`; seed cùng số row/đa lens và cùng palette ở từng lượt. Dùng named constants cho 100/350 apps, warmup và số lượt. Đo cold và warm riêng, đảo thứ tự baseline/candidate; Log kết quả nanos, không assert ngưỡng timing dễ flake. Assert kết quả rows/palette giống nhau, rollback/close DB ở `finally`.

```kotlin
val started = System.nanoTime()
// baseline: N findAnyByIdentifier + helper cũ cho pending
val legacyNanos = System.nanoTime() - started
val batchedStarted = System.nanoTime()
// candidate: getAllRows + helper batch cho pending
val batchedNanos = System.nanoTime() - batchedStarted
Log.i("PERF007", "apps=$appCount warm=$warm legacyNs=$legacyNanos batchedNs=$batchedNanos")
```

Đây là đo phần Room, không gọi đó là full refresh. Đo full `getAppShells` trước/sau trên cùng fixture host với PackageManager mock ở Task 2 và DB riêng để đối chiếu toàn scan. Trên thiết bị đo full refresh candidate với state người dùng giữ nguyên; nếu không có baseline full refresh tương đương, ghi rõ giới hạn và hỏi owner trước khi coi AC timing full refresh hoàn thành. Không rollback APK/mutate prefs người dùng để tạo con số.
- [ ] **Step 3: Full host gate:**

```bash
./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest
```

Bất kỳ failure nào phải kiểm tra và sửa trước smoke. Test count tính từ XML fresh; không dùng con số baseline cũ như kết quả mới.
- [ ] **Step 4: Cài APK đích danh rồi chạy instrumentation bằng serial khóa:**

```bash
set -e
S=2B051FDH3006MU
adb -s "$S" install -r "$(find app/build/outputs/apk/dev/debug -maxdepth 1 -name '*.apk' -print -quit)"
adb -s "$S" install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s "$S" shell am instrument -w -e class com.mckimquyen.util.PaletteScanBenchmarkTest,com.mckimquyen.views.LensViewIconReloadWidgetTest,com.mckimquyen.ui.ActHomeLensScopeWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Parse instrumentation output `OK`/failure; adb exit 0 không tự đồng nghĩa test pass. Cài app/test APK đều Success mới tính kết quả.
- [ ] **Step 5: Smoke** mở `ActHome` bằng `am start -W`, kiểm foreground đúng package, chờ app snapshot Ready/grid hiện đủ rồi screenshot. Đọc từng ảnh trước thao tác tiếp; gặp ad dừng. Không tự chọn app bên ngoài hoặc đổi default launcher. So prefs/settings và dữ liệu fixture sau test; SDK keys thay đổi ghi tên key, không lộ values.
- [ ] **Step 6: Rà diff trực tiếp** transaction semantics, resource lifecycle, generation guard nguyên trạng, test rollback có lỗi thật. Không agent worktree vì owner cấm và checkout có thể lệch. Cập nhật audit trung thực theo từng lớp unit/widget/integration.
- [ ] **Step 7: Hoàn tất:** gate xanh + audit >9 mới chuyển story done, cập nhật README/feature, `git diff --check` và `scripts/check-secrets.sh` staged, commit đúng các file story rồi push `origin dev` theo owner authorization. Nếu gate thiếu giữ inprogress, không tăng điểm chỉ để push.

## Tự rà plan

- Một SELECT palette: Task 2, RED có baseline N vs 1.
- Một transaction, không REPLACE/state loss: Task 1 và cold scanner Task 2.
- Row đa lens/cancellation/rollback: Task 1; giới hạn màu bất đồng nhất giữ nguyên hành vi không xác định cũ.
- Hiệu năng: Task 3 tách Room/full scan, không claim cùng metric nếu không đo được.
- UI regression/device state/R4/nhánh dev: Task 3.
- Chưa có code production hoặc test implementation nào được viết trong bước lập plan này.
