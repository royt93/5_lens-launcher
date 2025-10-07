# 🧪 Unit Tests - Lens Launcher

## Tổng Quan

Comprehensive unit tests để verify tất cả các fixes đã implement.

**Total Tests**: 55+
**Test Coverage**: 7 major fixes
**Framework**: JUnit 4, Mockito, Robolectric, Coroutines Test

---

## 📁 Test Structure

```
app/src/test/java/com/mckimquyen/
├── util/
│   └── BitmapCacheTest.kt          (14 tests) - Fix 5.1
├── services/
│   ├── AppEventManagerTest.kt      (13 tests) - Fix 1.2
│   ├── ObservableWrappersTest.kt   (11 tests) - Fix 1.2
│   └── TaskUpdateAppsTest.kt       (5 tests)  - Fix 1.1, 4.1
└── app/
    └── RAppsSingletonTest.kt       (12 tests) - Fix 2.1, 3.4
```

---

## 🚀 Quick Start

### Chạy tất cả tests:
```bash
./gradlew test
```

### Chạy test cụ thể:
```bash
./gradlew test --tests BitmapCacheTest
./gradlew test --tests "BitmapCacheTest.test thread safety*"
```

### Chạy với coverage:
```bash
./gradlew testDebugUnitTestCoverage
open app/build/reports/coverage/index.html
```

### Clean và rebuild tests:
```bash
./gradlew clean test
```

---

## 📊 Test Suites

### 1. BitmapCacheTest ✅
**Fix**: 5.1 - Bitmap LruCache

**Tests**:
- Basic operations (put/get/clear)
- Null safety
- Recycled bitmap handling
- Thread safety (concurrent put/get)
- Cache info

**Run**:
```bash
./gradlew test --tests BitmapCacheTest
```

---

### 2. AppEventManagerTest ✅
**Fix**: 1.2 - LiveData Events

**Tests**:
- All 7 LiveData events
- Multiple observers
- Thread-safe notifications
- Event isolation
- Singleton pattern

**Run**:
```bash
./gradlew test --tests AppEventManagerTest
```

---

### 3. RAppsSingletonTest ✅
**Fix**: 2.1 Optimization, 3.4 Thread Safety

**Tests**:
- Singleton thread safety (100 concurrent threads)
- No ArrayList copy optimization
- Concurrent read/write
- Null safety

**Run**:
```bash
./gradlew test --tests RAppsSingletonTest
```

---

### 4. ObservableWrappersTest ✅
**Fix**: 1.2 - Backward Compatibility

**Tests**:
- All 7 wrapper classes
- Delegation to AppEventManager
- Thread-safe singletons
- Mixed usage (wrapper + direct)

**Run**:
```bash
./gradlew test --tests ObservableWrappersTest
```

---

### 5. TaskUpdateAppsTest ✅
**Fix**: 1.1 Coroutines, 4.1 Memory Leak

**Tests**:
- Coroutines execution
- WeakReference leak prevention
- RAppsSingleton update
- Multiple executions

**Run**:
```bash
./gradlew test --tests TaskUpdateAppsTest
```

---

## ✅ Verification

### Fix 1.1: AsyncTask → Coroutines
```bash
./gradlew test --tests TaskUpdateAppsTest
# Expected: 5/5 PASSED
```

### Fix 1.2: Observable → LiveData
```bash
./gradlew test --tests AppEventManagerTest
./gradlew test --tests ObservableWrappersTest
# Expected: 24/24 PASSED
```

### Fix 2.1: ArrayList Optimization
```bash
./gradlew test --tests "RAppsSingletonTest.test get apps returns same reference*"
# Expected: PASSED
```

### Fix 3.4: Thread Safety Singleton
```bash
./gradlew test --tests "RAppsSingletonTest.test thread safety*"
# Expected: 3/3 PASSED
```

### Fix 4.1: Context Leak Prevention
```bash
./gradlew test --tests "TaskUpdateAppsTest.test WeakReference*"
# Expected: PASSED
```

### Fix 5.1: Bitmap LruCache
```bash
./gradlew test --tests BitmapCacheTest
# Expected: 14/14 PASSED
```

---

## 🧵 Thread Safety Tests

Tests verify concurrent access safety:

- **BitmapCache**: 10 threads × 100 operations
- **AppEventManager**: 10 threads × 10 notifications
- **RAppsSingleton**: 100 threads getInstance
- **Observable Wrappers**: 7 wrappers × 50-100 threads

**Run all thread safety tests**:
```bash
./gradlew test --tests "*thread safety*"
```

---

## 📈 Expected Results

```
BUILD SUCCESSFUL in 5s

> Task :app:testDebugUnitTest
  BitmapCacheTest: 14/14 tests passed
  AppEventManagerTest: 13/13 tests passed
  RAppsSingletonTest: 12/12 tests passed
  ObservableWrappersTest: 11/11 tests passed
  TaskUpdateAppsTest: 5/5 tests passed

Total: 55 tests, 55 passed, 0 failed, 0 skipped
```

---

## 🔧 Dependencies

Test dependencies trong `build.gradle`:

```gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0'
testImplementation 'androidx.arch.core:core-testing:2.2.0'
testImplementation 'org.mockito:mockito-core:5.8.0'
testImplementation 'org.mockito.kotlin:mockito-kotlin:5.2.1'
testImplementation 'org.robolectric:robolectric:4.11.1'
```

---

## 🐛 Troubleshooting

### Test fails với "No tests found"
```bash
./gradlew clean
./gradlew test
```

### Robolectric AndroidManifest error
→ Đã config `@Config(manifest = Config.NONE)`

### LiveData không trigger observer
→ Đã dùng `InstantTaskExecutorRule`

### Coroutines test timeout
→ Đã dùng `runTest` từ kotlinx-coroutines-test

---

## 📚 References

- [JUnit 4 Docs](https://junit.org/junit4/)
- [Mockito Kotlin](https://github.com/mockito/mockito-kotlin)
- [Robolectric](http://robolectric.org/)
- [Coroutines Test](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-test/)
- [LiveData Testing](https://developer.android.com/codelabs/android-testing)

---

## 📝 Notes

1. Tests chạy trên JVM (không cần emulator)
2. Robolectric simulate Android framework
3. InstantTaskExecutorRule làm LiveData execute ngay
4. runTest handle coroutines test scope
5. Thread safety tests có thể mất vài giây

---

**Xem thêm**: [TEST_DOCUMENTATION.md](../../../TEST_DOCUMENTATION.md)
