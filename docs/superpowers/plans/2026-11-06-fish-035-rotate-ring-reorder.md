# FISH-035 — Rotate-the-Ring Reorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** With focus pinned (FISH-029), a one-finger circular drag around the pinned centre rotates the displayed app order one slot per 30° step inside each pinned-zone/favorite group, previews live, and commits once on release. The lens is frozen only after the database write is confirmed. Two accessibility host actions do the same one step at a time.

**Architecture:** A pure `RingRotation` object (group-aware cyclic rotate, angle math) and a pure `RingGestureTracker` (bounded sample window that classifies ROTATE vs PAN) are driven from `LensView.handleAppTouch` only while focus is pinned. `LensView` previews by changing only `mApps` (never `mSourceApps`), and on release hands one `RingRotationRequest` to `LensViewHolder`. The holder calls a new `AppPersistent.commitAppOrderBatch(...)` that reports `COMMITTED/FAILED/SUPERSEDED`; only `COMMITTED` saves the frozen flag and lets the view adopt the order, anything else restores the pre-gesture list.

**Tech Stack:** Kotlin/Java, Android `MotionEvent`, `ExploreByTouchHelper`, Room via `AppPersistent`, JUnit4/Robolectric and AndroidJUnit4. No new dependency.

## Decisions (all owner-approved 2026-11-06; do not re-ask)

1. Gesture works only while FISH-029 focus is pinned. Unpinned behavior is byte-for-byte the old code path.
2. **Short classification wait** while pinned: after `touchSlop` the pan is held back until `RingGestureTracker` returns `ROTATE` or `PAN`. Pan uses absolute coordinates (`mTouchX = effective(event.x)`), so no replay of buffered samples is needed — the decision simply stops withholding the existing pan code. Cost: pinned pan starts up to `MAX_WINDOW_MS` later.
3. **Groups preserved** (owner chose "Giữ nguyên nhóm"): `UtilAppSorter` sorts by pinned-zone rank, then favorite, before `orderNumber`, so a whole-list rotation would not survive reload. `rotateByGroup` partitions by `(pinnedZone.rank, favorite)` in ascending key order, rotates each partition, and concatenates — exactly what the sorter reproduces after a reload. Preview uses this same function, so preview == reload.
4. Already-frozen lens still rotates and stays frozen. Frozen flag is saved **only after** `COMMITTED`.
5. TalkBack: exactly FISH-029's `Toggle focus lock` plus `Rotate order forward` / `Rotate order back` host actions. A11Y-002/A11Y-003 stay declined. No per-app node actions.
6. Thresholds are provisional named constants in one object (`RingRotation`), chosen with a throwaway synthetic simulation (1500 trials per row, 8 ms sampling, jitter up to 3 px; the scenarios are re-encoded as unit tests in Task 1, the script is not kept): straight and tangent lines never classified ROTATE; circles at >= ~115 deg/s did (>= 99.5%); slower circles become PAN. Real-finger tuning is out of scope and recorded as a limitation. Consequence of decision 2: every pinned pan starts up to `MAX_WINDOW_MS` (300 ms) late.

## Global Constraints

- Pixel 7 Pro `2B051FDH3006MU` only for this execution (owner explicitly changed target on 2026-11-06); TECNO `2B051FDH3006MU`, Samsung and OPPO are locked out. No Gradle `connected*`; use `adb -s 2B051FDH3006MU shell am instrument`.
- Before any device install: back up main APK, test APK and `databases shared_prefs` tar; SHA-256 every data member; record font/animator/transition/window scales, app locale and HOME resolver. After device work: restore and compare every hash and setting; stop if restore is not exact.
- adb smoke uses `input swipe x y x y 80` (hold ≥ 80 ms). **Never `input tap`** on icons (`mSelectIndex` race, `doc/task/AUDIT_FISH-029_2026-11-06.md`). Chained `input swipe` calls are separate gestures and do NOT prove rotation.
- If `AdActivity` covers the UI: stop and wait for the owner's `done` (R4). Never tap "Start (Ads may appear)".
- R5: named constants (no magic numbers), no `late`/`!!` without a justified comment, every listener/callback cleared on detach, a test for every behavior.
- Every behavior change: write the test, run it, watch it fail for the expected reason, write the minimum code, rerun. Do not stage `.idea/`. No push unless the owner asks. Commit trailer: `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

---

## File Map

- Create `app/src/main/java/com/mckimquyen/util/RingRotation.kt` — constants, angle math, `rotate`, `rotateByGroup`.
- Create `app/src/main/java/com/mckimquyen/util/RingGestureTracker.kt` — pure sample-window classifier.
- Create `app/src/test/java/com/mckimquyen/util/RingRotationTest.kt`, `RingGestureTrackerTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/model/AppPersistent.kt` — `OrderCommitOutcome`, `commitAppOrderBatch`.
- Modify `app/src/test/java/com/mckimquyen/model/AppPersistentFailureTest.kt` (or create `AppPersistentOrderOutcomeTest.kt` beside it).
- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt` — listener, request type, preview/commit/resolve/cancel, gesture wiring.
- Create `app/src/main/java/com/mckimquyen/views/RingRotationRequest.kt` — request + listener interface.
- Modify `app/src/main/java/com/mckimquyen/ui/home/LensViewHolder.java`, `ui/ActHome.java` (pause hook).
- Modify `views/LensAccessibilityHelper.kt`, `a11y/LensAccessibilityHelperTest.kt`, `res/values/ids.xml`, `strings.xml` ×17.
- Create `app/src/androidTest/java/com/mckimquyen/views/LensViewRingRotateIntegrationTest.kt`, `app/src/androidTest/java/com/mckimquyen/ui/ActHomeRingRotateWidgetTest.kt`.
- Evidence: `doc/task/AUDIT_FISH-035_2026-11-06.md`, `doc/feature.md`, `doc/task/README.md`; story `todo/` → `inprogress/` (Task 1) → `done/`.

## Interfaces

```kotlin
package com.mckimquyen.util

object RingRotation {
    const val MIN_RADIUS_DP = 24f
    const val STEP_RADIANS = 0.5235988f          // 30 degrees, literal so it is a compile-time const
    const val SWEEP_THRESHOLD_RAD = 0.5235988f   // 30 degrees: first list step and ROTATE decision coincide
    const val MAX_WINDOW_MS = 300L               // classification window after DOWN; expiry means PAN
    const val MAX_SAMPLES = 64                   // memory bound for the window (8 ms events: 512 ms)
    const val RADIUS_STABILITY_FRACTION = 0.25f
    const val MIN_SAMPLES = 4
    const val TURN_FRACTION = 0.4f               // heading turn must be >= this share of the sweep

    enum class Decision { UNDECIDED, PAN, ROTATE }

    fun angleOf(centerX: Float, centerY: Float, x: Float, y: Float): Float
    fun shortestAngleDelta(previous: Float, next: Float): Float   // finite input -> (-PI, PI]; non-finite -> 0f
    fun wholeSteps(sweepRad: Float): Int                          // truncates toward zero; non-finite -> 0
    fun <T> rotate(items: List<T>, steps: Int): List<T>
    fun <T> rotateByGroup(items: List<T>, steps: Int, groupKey: (T) -> Int): List<T>
    fun groupKeyOf(zoneRank: Int, favorite: Boolean): Int          // zoneRank * 2 + (favorite ? 0 : 1), the sorter's order
}

class RingGestureTracker(private val density: Float) {
    fun begin(centerX: Float, centerY: Float, x: Float, y: Float, timeMs: Long)
    fun onMove(x: Float, y: Float, timeMs: Long): RingRotation.Decision   // sticky once decided
    val sweepRadians: Float
    val decision: RingRotation.Decision
    fun reset()
}
```

```kotlin
package com.mckimquyen.views

class RingRotationRequest(val token: Long, val lensId: String, val apps: List<App>)
fun interface OnRingRotatedListener { fun onRingRotated(request: RingRotationRequest) }
```

```kotlin
// AppPersistent (additive; existing setAppOrderBatch(apps, lensId) untouched)
// top level in model/AppPersistent.kt (a nested enum in the companion would be AppPersistent.Companion.X)
enum class OrderCommitOutcome { COMMITTED, FAILED, SUPERSEDED }
@JvmStatic fun commitAppOrderBatch(apps: List<App>, lensId: String, onOutcome: (OrderCommitOutcome) -> Unit)
// onOutcome always runs on the main thread, exactly once.
```

`LensView` additions: `var onRingRotatedListener: OnRingRotatedListener?`, `fun resolveRingCommit(token: Long, committed: Boolean)`, `fun cancelRingRotationForPause()`, `internal fun rotateAndCommit(steps: Int): Boolean`, test seams `isRingRotatingForTest()`, `ringPendingTokenForTest()`.

Ids: `action_rotate_order_forward`, `action_rotate_order_back`. Strings: `lens_rotate_forward_action`, `lens_rotate_back_action`, `lens_rotate_announcement` (`%1$s` = new first app label).

---

## Task 1 — Pure math and gesture tracker

**Files:** create `RingRotation.kt`, `RingGestureTracker.kt`, `RingRotationTest.kt`, `RingGestureTrackerTest.kt`; move story to `inprogress/`.

- [ ] **Step 1: Move the story.** `git mv doc/task/todo/p3-fish-fish-035-rotate-ring-reorder.md doc/task/inprogress/p3-fish-fish-035-rotate-ring-reorder.md`; set `| Status | inprogress |`; point the `FISH-035` row in `doc/task/README.md` at `inprogress/…`.
- [ ] **Step 2: RED (math).** Create `app/src/test/java/com/mckimquyen/util/RingRotationTest.kt`:

```kotlin
package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import com.mckimquyen.enums.SortType
import com.mckimquyen.model.App
import com.mckimquyen.model.PinnedZone
import org.junit.Test
import kotlin.math.PI

class RingRotationTest {
    @Test fun rotateForwardMovesTheFirstItemToTheEnd() =
        assertEquals(listOf("b", "c", "d", "a"), RingRotation.rotate(listOf("a", "b", "c", "d"), 1))

    @Test fun rotateBackMovesTheLastItemToTheFront() =
        assertEquals(listOf("d", "a", "b", "c"), RingRotation.rotate(listOf("a", "b", "c", "d"), -1))

    @Test fun rotateWrapsAndIgnoresFullTurns() {
        val l = listOf(1, 2, 3, 4)
        assertEquals(l, RingRotation.rotate(l, 4))
        assertEquals(RingRotation.rotate(l, 1), RingRotation.rotate(l, 5))
        assertEquals(RingRotation.rotate(l, -1), RingRotation.rotate(l, 7))
        assertEquals(RingRotation.rotate(l, 3), RingRotation.rotate(l, -9))
    }

    @Test fun rotateIsSafeForEmptyAndSingleAndNeverMutates() {
        assertEquals(emptyList<Int>(), RingRotation.rotate(emptyList<Int>(), 3))
        assertEquals(listOf(9), RingRotation.rotate(listOf(9), 5))
        val input = mutableListOf(1, 2, 3)
        val out = RingRotation.rotate(input, 1)
        assertEquals(listOf(1, 2, 3), input)
        assertNotSame(input, out)
    }

    @Test fun rotateByGroupRotatesEachGroupAndNeverCrossesABoundary() {
        // key = first digit: group 1 = [10, 11, 12], group 2 = [20, 21]
        val items = listOf(10, 11, 12, 20, 21)
        val out = RingRotation.rotateByGroup(items, 1) { it / 10 }
        assertEquals(listOf(11, 12, 10, 21, 20), out)
        // back one step: group 1 -> [12,10,11], group 2 -> [21,20]
        assertEquals(listOf(12, 10, 11, 21, 20), RingRotation.rotateByGroup(items, -1) { it / 10 })
    }

    @Test fun rotateByGroupOrdersGroupsByAscendingKeyLikeTheSorter() {
        // input is interleaved (Smart Focus arrangement); output is grouped ascending, like a reload
        val items = listOf(20, 10, 21, 11)
        assertEquals(listOf(10, 11, 20, 21), RingRotation.rotateByGroup(items, 0) { it / 10 })
    }

    @Test fun rotateByGroupLeavesSingletonGroupsAndFullTurnsUnchanged() {
        val items = listOf(10, 20, 21)
        assertEquals(listOf(10, 20, 21), RingRotation.rotateByGroup(items, 2) { it / 10 })
        assertEquals(emptyList<Int>(), RingRotation.rotateByGroup(emptyList<Int>(), 1) { it })
    }

    @Test fun wholeStepsTruncateTowardZeroAndRejectNonFinite() {
        val s = RingRotation.STEP_RADIANS
        assertEquals(0, RingRotation.wholeSteps(s * 0.99f))
        assertEquals(1, RingRotation.wholeSteps(s * 1.01f))
        assertEquals(-1, RingRotation.wholeSteps(-s * 1.99f))
        assertEquals(0, RingRotation.wholeSteps(Float.NaN))
        assertEquals(0, RingRotation.wholeSteps(Float.POSITIVE_INFINITY))
    }

    @Test fun shortestAngleDeltaWrapsAcrossPiAndRejectsNonFinite() {
        assertEquals(0.2f, RingRotation.shortestAngleDelta((PI - 0.1).toFloat(), (-PI + 0.1).toFloat()), 1e-4f)
        assertEquals(-0.2f, RingRotation.shortestAngleDelta((-PI + 0.1).toFloat(), (PI - 0.1).toFloat()), 1e-4f)
        assertEquals(0f, RingRotation.shortestAngleDelta(1f, 1f), 0f)
        assertEquals(0f, RingRotation.shortestAngleDelta(Float.NaN, 1f), 0f)
        assertEquals(0f, RingRotation.shortestAngleDelta(0f, Float.POSITIVE_INFINITY), 0f)
        // a huge but finite jump must terminate and stay in range
        val huge = RingRotation.shortestAngleDelta(0f, 1.0e9f)
        assertTrue(huge > -PI.toFloat() - 1e-3f && huge <= PI.toFloat() + 1e-3f)
    }

    @Test fun groupKeyFollowsTheSorterMajorAndMinorOrder() {
        val keys = listOf(
            PinnedZone.START to true, PinnedZone.START to false,
            PinnedZone.NONE to true, PinnedZone.NONE to false,
            PinnedZone.END to true, PinnedZone.END to false
        ).map { (zone, fav) -> RingRotation.groupKeyOf(zone.rank, fav) }
        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun aRotatedOrderSurvivesTheRealSorterAfterAReload() {
        fun app(name: String, zone: PinnedZone, fav: Boolean) =
            App(label = name, packageName = "pkg.$name", name = "Main", pinnedZone = zone, isFavorite = fav)
        val displayed = listOf(
            app("a", PinnedZone.NONE, false), app("b", PinnedZone.START, false), app("c", PinnedZone.NONE, true),
            app("d", PinnedZone.NONE, false), app("e", PinnedZone.NONE, true), app("f", PinnedZone.NONE, false)
        )
        for (steps in -7..7) {
            val rotated = RingRotation.rotateByGroup(displayed, steps) { RingRotation.groupKeyOf(it.pinnedZone.rank, it.isFavorite) }
            // what the commit stores: orderNumber = position in the rotated list
            val stored = rotated.mapIndexed { index, a -> a.copyWithOrder(index) }
            // what a reload does: the same rows in any arrival order, then the real sorter
            val reloaded = ArrayList(stored.shuffled(java.util.Random(steps.toLong())))
            UtilAppSorter.sort(reloaded, SortType.LABEL_ASCENDING)
            assertEquals("steps=$steps", stored.map { it.packageName }, reloaded.map { it.packageName })
        }
    }

    @Test fun angleOfMatchesAtan2() {
        assertEquals(0f, RingRotation.angleOf(0f, 0f, 5f, 0f), 1e-6f)
        assertEquals((PI / 2).toFloat(), RingRotation.angleOf(0f, 0f, 0f, 5f), 1e-6f)
    }
}
```

- [ ] **Step 3: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.RingRotationTest'`. Expected: compile failure only, `Unresolved reference: RingRotation`.
- [ ] **Step 4: GREEN (math).** Create `app/src/main/java/com/mckimquyen/util/RingRotation.kt`:

```kotlin
package com.mckimquyen.util

import kotlin.math.atan2

/**
 * FISH-035: pure math for the rotate-the-ring gesture. No Android types, so it is fully unit-testable.
 * Thresholds are provisional (tuned only on synthetic input, not on real fingers).
 */
object RingRotation {
    const val MIN_RADIUS_DP = 24f
    /** 30 degrees of sweep per list step. */
    const val STEP_RADIANS = 0.5235988f
    /** 30 degrees of accumulated sweep is the minimum rotation evidence (equals one step). */
    const val SWEEP_THRESHOLD_RAD = 0.5235988f
    /** Classification window after DOWN; a gesture still undecided when it expires is a pan. */
    const val MAX_WINDOW_MS = 300L
    /** Memory bound for the sample window. */
    const val MAX_SAMPLES = 64
    /** Moves (after the DOWN sample) required before an arc can be accepted. */
    const val MIN_SAMPLES = 4
    /** Allowed radius spread inside the window, as a share of the starting radius. */
    const val RADIUS_STABILITY_FRACTION = 0.25f
    /** Heading turn must be at least this share of the sweep, which a straight tangent line never reaches. */
    const val TURN_FRACTION = 0.4f

    private const val TWO_PI = 6.2831855f
    private const val PI_F = 3.1415927f

    enum class Decision { UNDECIDED, PAN, ROTATE }

    fun angleOf(centerX: Float, centerY: Float, x: Float, y: Float): Float =
        atan2(y - centerY, x - centerX)

    /** Signed smallest difference `next - previous` in (-PI, PI]; non-finite input is 0 (never loops). */
    fun shortestAngleDelta(previous: Float, next: Float): Float {
        if (!previous.isFinite() || !next.isFinite()) return 0f
        var d = (next - previous) % TWO_PI
        if (d > PI_F) d -= TWO_PI else if (d <= -PI_F) d += TWO_PI
        return d
    }

    /** Whole list steps in [sweepRad], truncating toward zero; non-finite is 0. */
    fun wholeSteps(sweepRad: Float): Int =
        if (!sweepRad.isFinite()) 0 else (sweepRad / STEP_RADIANS).toInt()

    /** Cyclic shift: positive steps move the first items to the end. Never mutates [items]. */
    fun <T> rotate(items: List<T>, steps: Int): List<T> {
        if (items.size < 2) return ArrayList(items)
        val n = Math.floorMod(steps, items.size)
        if (n == 0) return ArrayList(items)
        return ArrayList(items.subList(n, items.size) + items.subList(0, n))
    }

    /**
     * Rotates every group separately and concatenates the groups in ascending key order. `UtilAppSorter` orders by
     * pinned-zone rank, then favorite, before `orderNumber`, so this is exactly what a reload reproduces.
     */
    fun <T> rotateByGroup(items: List<T>, steps: Int, groupKey: (T) -> Int): List<T> {
        if (items.size < 2) return ArrayList(items)
        return ArrayList(items.groupBy(groupKey).toSortedMap().values.flatMap { rotate(it, steps) })
    }

    /** Same major/minor order as `UtilAppSorter`: pinned-zone rank first, favorites before the rest. */
    fun groupKeyOf(zoneRank: Int, favorite: Boolean): Int = zoneRank * 2 + (if (favorite) 0 else 1)
}
```

- [ ] **Step 5: Verify GREEN.** Rerun the class; every test in `RingRotationTest` passes (12 tests; the tracker class has 14).
- [ ] **Step 6: RED (tracker).** Create `app/src/test/java/com/mckimquyen/util/RingGestureTrackerTest.kt`. The generators are deterministic (seeded `java.util.Random`), 8 ms apart, density 3:

```kotlin
package com.mckimquyen.util

import com.mckimquyen.util.RingRotation.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RingGestureTrackerTest {
    private val density = 3f
    private val cx = 500f
    private val cy = 500f
    private val dtMs = 8L

    private data class P(val x: Float, val y: Float, val t: Long)

    private fun circle(r: Float, radPerSec: Float, n: Int = 80, jitter: Float = 0f, seed: Long = 1, start: Float = 0f): List<P> {
        val rnd = Random(seed)
        return List(n) { i ->
            val a = start + radPerSec * i * dtMs / 1000f
            P(cx + r * cos(a) + (rnd.nextFloat() - .5f) * 2 * jitter, cy + r * sin(a) + (rnd.nextFloat() - .5f) * 2 * jitter, i * dtMs)
        }
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, pxPerSec: Float, n: Int = 80, jitter: Float = 0f, seed: Long = 1): List<P> {
        val rnd = Random(seed)
        val len = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
        val ux = (x1 - x0) / len
        val uy = (y1 - y0) / len
        return List(n) { i ->
            val d = pxPerSec * i * dtMs / 1000f
            P(x0 + ux * d + (rnd.nextFloat() - .5f) * 2 * jitter, y0 + uy * d + (rnd.nextFloat() - .5f) * 2 * jitter, i * dtMs)
        }
    }

    /** Feeds the points; returns the decision after the last one and the tracker. */
    private fun feed(points: List<P>, tracker: RingGestureTracker = RingGestureTracker(density)): Pair<Decision, RingGestureTracker> {
        tracker.begin(cx, cy, points[0].x, points[0].y, points[0].t)
        var d = tracker.decision
        for (p in points.drop(1)) d = tracker.onMove(p.x, p.y, p.t)
        return d to tracker
    }

    @Test fun aClockwiseArcAtNormalSpeedRotatesPositively() {
        val (d, t) = feed(circle(r = 300f, radPerSec = 3f))
        assertEquals(Decision.ROTATE, d)
        assertTrue(t.sweepRadians > 0f)
    }

    @Test fun aCounterClockwiseArcRotatesNegatively() {
        val (d, t) = feed(circle(r = 300f, radPerSec = -3f))
        assertEquals(Decision.ROTATE, d)
        assertTrue(t.sweepRadians < 0f)
    }

    @Test fun arcsAcrossRadiiAndJitterStillRotate() {
        for (r in listOf(100f, 200f, 450f, 600f)) for (seed in 1L..20L) {
            val (d, _) = feed(circle(r, 3f, jitter = 2f, seed = seed, start = seed * 0.3f))
            assertEquals("r=$r seed=$seed", Decision.ROTATE, d)
        }
    }

    @Test fun aStraightTangentDragNeverRotates() {
        for (seed in 1L..40L) for (speed in listOf(300f, 600f, 1000f, 1500f)) {
            val (d, _) = feed(line(800f, 500f, 800f, 1500f, speed, jitter = 3f, seed = seed))
            assertTrue("tangent speed=$speed seed=$seed was $d", d != Decision.ROTATE)
        }
    }

    @Test fun aStraightRadialOrThroughCentreDragNeverRotates() {
        assertTrue(feed(line(800f, 500f, 100f, 500f, 400f)).first != Decision.ROTATE)
        assertTrue(feed(line(800f, 500f, 1500f, 500f, 400f)).first != Decision.ROTATE)
        assertTrue(feed(line(800f, 500f, 200f, 500f, 1500f)).first != Decision.ROTATE)
    }

    @Test fun aStartInsideTheMinimumRadiusIsPanImmediately() {
        val (d, _) = feed(circle(r = RingRotation.MIN_RADIUS_DP * density * 0.9f, radPerSec = 8f))
        assertEquals(Decision.PAN, d)
    }

    @Test fun aSlowCircleExpiresTheWindowAsPan() {
        val (d, _) = feed(circle(r = 300f, radPerSec = 1f, n = 100))
        assertEquals(Decision.PAN, d)
    }

    @Test fun aSpiralThatDriftsOutsideTheRadiusBandIsPan() {
        val rnd = Random(3)
        val pts = List(80) { i ->
            val a = 3f * i * dtMs / 1000f
            val r = 300f + i * 6f       // grows 480 px over the window, far beyond 25 percent
            P(cx + r * cos(a) + rnd.nextFloat(), cy + r * sin(a), i * dtMs)
        }
        assertEquals(Decision.PAN, feed(pts).first)
    }

    @Test fun theDecisionIsStickyAndLaterSamplesCannotChangeIt() {
        val rotating = circle(300f, 3f, n = 120)
        val (d, tracker) = feed(rotating.take(40))
        assertEquals(Decision.ROTATE, d)
        assertEquals(Decision.ROTATE, tracker.onMove(cx + 300f, cy, rotating[40].t + 5000))
    }

    @Test fun sweepKeepsAccumulatingAfterTheDecisionAcrossThePiBoundary() {
        val pts = circle(300f, 3f, n = 200, start = (PI - 0.2).toFloat())
        val (_, tracker) = feed(pts)
        assertTrue("sweep must exceed pi without wrapping, was ${tracker.sweepRadians}", tracker.sweepRadians > PI.toFloat())
    }

    @Test fun nonFiniteSamplesAreIgnoredAndNeverRotate() {
        val tracker = RingGestureTracker(density)
        tracker.begin(cx, cy, 800f, 500f, 0)
        assertTrue(tracker.onMove(Float.NaN, 500f, 8) != Decision.ROTATE)
        assertTrue(tracker.onMove(800f, Float.POSITIVE_INFINITY, 16) != Decision.ROTATE)
        assertEquals(0f, tracker.sweepRadians, 0f)
    }

    @Test fun invalidDensityOrCentreMakesEverythingPan() {
        val bad = RingGestureTracker(0f)
        bad.begin(cx, cy, 800f, 500f, 0)
        assertEquals(Decision.PAN, bad.decision)
        val nan = RingGestureTracker(density)
        nan.begin(Float.NaN, cy, 800f, 500f, 0)
        assertEquals(Decision.PAN, nan.decision)
    }

    @Test fun resetAllowsAFreshGesture() {
        val (_, tracker) = feed(circle(300f, 3f))
        tracker.reset()
        assertEquals(Decision.UNDECIDED, tracker.decision)
        assertEquals(0f, tracker.sweepRadians, 0f)
        assertEquals(Decision.ROTATE, feed(circle(300f, 3f), tracker).first)
    }

    @Test fun moveBeforeBeginIsHarmlessAndUndecided() {
        val tracker = RingGestureTracker(density)
        assertEquals(Decision.UNDECIDED, tracker.onMove(10f, 10f, 0))
    }
}
```

- [ ] **Step 7: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.RingGestureTrackerTest'`. Expected: compile failure only, `Unresolved reference: RingGestureTracker`.
- [ ] **Step 8: GREEN (tracker).** Create `app/src/main/java/com/mckimquyen/util/RingGestureTracker.kt`:

```kotlin
package com.mckimquyen.util

import com.mckimquyen.util.RingRotation.Decision
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * FISH-035: decides, from a bounded window of MOVE samples, whether a one-finger drag around a pinned centre is a
 * rotation. ROTATE needs: enough signed sweep, a radius that stays inside a band around the starting radius, and a
 * heading that actually turns in the sweep direction (a straight line never turns). Anything else is PAN. The decision
 * is sticky; after ROTATE the sweep keeps accumulating. Allocation-free after construction.
 */
class RingGestureTracker(private val density: Float) {
    private val xs = FloatArray(RingRotation.MAX_SAMPLES)
    private val ys = FloatArray(RingRotation.MAX_SAMPLES)
    private var count = 0
    private var centerX = 0f
    private var centerY = 0f
    private var startRadius = 0f
    private var maxRadiusDeviation = 0f
    private var startTimeMs = 0L
    private var lastAngle = 0f
    private var begun = false

    var sweepRadians = 0f
        private set
    var decision = Decision.UNDECIDED
        private set

    fun begin(centerX: Float, centerY: Float, x: Float, y: Float, timeMs: Long) {
        reset()
        begun = true
        this.centerX = centerX
        this.centerY = centerY
        startTimeMs = timeMs
        val valid = density.isFinite() && density > 0f && centerX.isFinite() && centerY.isFinite() && x.isFinite() && y.isFinite()
        startRadius = if (valid) hypot(x - centerX, y - centerY) else 0f
        if (!valid || startRadius < RingRotation.MIN_RADIUS_DP * density) {
            decision = Decision.PAN
            return
        }
        lastAngle = atan2(y - centerY, x - centerX)
        xs[0] = x
        ys[0] = y
        count = 1
    }

    fun onMove(x: Float, y: Float, timeMs: Long): Decision {
        if (!begun || !x.isFinite() || !y.isFinite()) return decision
        if (decision == Decision.PAN) return decision
        val angle = atan2(y - centerY, x - centerX)
        sweepRadians += RingRotation.shortestAngleDelta(lastAngle, angle)
        lastAngle = angle
        if (decision == Decision.ROTATE) return decision

        if (count < RingRotation.MAX_SAMPLES) {
            xs[count] = x
            ys[count] = y
            count++
        }
        maxRadiusDeviation = maxOf(maxRadiusDeviation, abs(hypot(x - centerX, y - centerY) - startRadius))
        if (maxRadiusDeviation > RingRotation.RADIUS_STABILITY_FRACTION * startRadius) return decide(Decision.PAN)

        val moves = count - 1
        if (moves >= RingRotation.MIN_SAMPLES && abs(sweepRadians) >= RingRotation.SWEEP_THRESHOLD_RAD) {
            val turn = headingTurn(moves)
            if (abs(turn) >= RingRotation.TURN_FRACTION * abs(sweepRadians) && turn * sweepRadians > 0f) {
                return decide(Decision.ROTATE)
            }
        }
        if (timeMs - startTimeMs >= RingRotation.MAX_WINDOW_MS || count >= RingRotation.MAX_SAMPLES) {
            return decide(Decision.PAN)
        }
        return decision
    }

    fun reset() {
        count = 0
        begun = false
        sweepRadians = 0f
        maxRadiusDeviation = 0f
        startRadius = 0f
        decision = Decision.UNDECIDED
    }

    private fun decide(result: Decision): Decision {
        decision = result
        return result
    }

    /** Signed change of heading between the first and second half of the window, in (-PI, PI]. */
    private fun headingTurn(moves: Int): Float {
        val mid = moves / 2
        val first = atan2(ys[mid] - ys[0], xs[mid] - xs[0])
        val second = atan2(ys[moves] - ys[mid], xs[moves] - xs[mid])
        return RingRotation.shortestAngleDelta(first, second)
    }
}
```

- [ ] **Step 9: Verify GREEN.** Rerun both classes: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.RingRotationTest' --tests 'com.mckimquyen.util.RingGestureTrackerTest'`. If `arcsAcrossRadiiAndJitterStillRotate` or `aStraightTangentDragNeverRotates` fails, adjust only the named constants in `RingRotation` (never the test data) and record the change in the audit.
- [ ] **Step 10: Commit.** `git add app/src/main/java/com/mckimquyen/util/RingRotation.kt app/src/main/java/com/mckimquyen/util/RingGestureTracker.kt app/src/test/java/com/mckimquyen/util/RingRotationTest.kt app/src/test/java/com/mckimquyen/util/RingGestureTrackerTest.kt doc/task/inprogress/p3-fish-fish-035-rotate-ring-reorder.md doc/task/README.md && git commit -m "feat(fish-035): add ring rotation math and gesture tracker"` with the attribution trailer.

---

## Task 2 — Confirmed order commit (`commitAppOrderBatch`)

**Why:** `setAppOrderBatch` returns `Unit`, writes optimistically and never tells the caller whether the DAO write happened; its rollback restores `orderNumber` only. FISH-035 must not set the frozen flag or keep a previewed order unless the write is confirmed. This task adds a separate, additive API; `setAppOrderBatch` and its three callers stay untouched.

**Files:** modify `AppPersistent.kt`; create `app/src/test/java/com/mckimquyen/model/AppPersistentOrderOutcomeTest.kt` (same Robolectric setup as `AppPersistentFailureTest`).

- [ ] **Step 1: RED.** Create the test file:

```kotlin
package com.mckimquyen.model

import androidx.room.Room
import com.mckimquyen.app.RAppsSingleton
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** FISH-035: the confirmed-commit order API reports exactly one of COMMITTED, FAILED or SUPERSEDED, on main. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppPersistentOrderOutcomeTest {
    private lateinit var db: AppDatabase
    private val apps = listOf("a", "b", "c").map { App(packageName = "com.fish035.$it", name = "Main", orderNumber = -1) }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        setDatabaseInstance(db)
        RAppsSingleton.instance.clearAllData()
        RAppsSingleton.instance.apps = ArrayList(apps)
        runBlocking { db.lensWorkspaceDao().insertOrUpdate(LensWorkspace(LENS, "Work")) }
    }

    @After
    fun tearDown() {
        AppPersistent.beforeOrderCommitForTest = null
        if (db.isOpen) db.close()
        setDatabaseInstance(null)
        RAppsSingleton.instance.clearAllData()
    }

    private fun commit(list: List<App>, lens: String = LENS): List<OrderCommitOutcome> {
        val outcomes = mutableListOf<OrderCommitOutcome>()
        AppPersistent.commitAppOrderBatch(list, lens) { outcomes += it }
        return outcomes
    }

    private fun await(outcomes: List<*>, count: Int = 1) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (outcomes.size >= count) return
            Thread.sleep(POLL_MS)
        }
        fail("timed out waiting for $count outcome(s), got ${outcomes.size}")
    }

    private fun storedOrder() = runBlocking { db.appPersistentDao().getAllForLens(LENS) }
        .sortedBy { it.orderNumber }.map { it.name + "@" + it.packageName?.substringAfterLast('.') }

    @Test fun aSuccessfulWriteReportsCommittedStoresTheOrderAndUpdatesMemory() {
        val reversed = apps.reversed()
        val outcomes = commit(reversed)
        await(outcomes)
        assertEquals(listOf(OrderCommitOutcome.COMMITTED), outcomes)
        assertEquals(listOf("Main@c", "Main@b", "Main@a"), storedOrder())
        assertEquals(0, RAppsSingleton.instance.findApp("com.fish035.c", "Main")?.orderNumber)
    }

    @Test fun aMissingLensReportsFailedAndWritesNothing() {
        val outcomes = commit(apps, lens = "deleted-lens")
        await(outcomes)
        assertEquals(listOf(OrderCommitOutcome.FAILED), outcomes)
        assertTrue(runBlocking { db.appPersistentDao().getAllForLens("deleted-lens") }.isEmpty())
        assertEquals(-1, RAppsSingleton.instance.findApp("com.fish035.a", "Main")?.orderNumber)
    }

    @Test fun aBrokenDatabaseReportsFailedAndLeavesMemoryUntouched() {
        db.openHelper.writableDatabase.execSQL("DROP TABLE APP_PERSISTENT")
        val outcomes = commit(apps.reversed())
        await(outcomes)
        assertEquals(listOf(OrderCommitOutcome.FAILED), outcomes)
        assertEquals(-1, RAppsSingleton.instance.findApp("com.fish035.c", "Main")?.orderNumber)
    }

    @Test fun anEmptyOrUnusableListReportsFailed() {
        val empty = commit(emptyList())
        val blank = commit(listOf(App(packageName = "", name = "x")))
        await(empty)
        await(blank)
        assertEquals(listOf(OrderCommitOutcome.FAILED), empty)
        assertEquals(listOf(OrderCommitOutcome.FAILED), blank)
    }

    @Test fun anOlderWriteThatLosesTheRaceReportsSupersededAndOnlyTheNewestIsStored() {
        val firstReached = CountDownLatch(1)
        val release = CountDownLatch(1)
        var firstGate = true
        AppPersistent.beforeOrderCommitForTest = {
            if (firstGate) {
                firstGate = false
                firstReached.countDown()
                assertTrue(release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            }
        }
        val first = commit(apps.reversed())
        assertTrue(firstReached.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        val second = commit(apps)            // newer revision while the first is parked before its check
        release.countDown()
        await(first)
        await(second)
        assertEquals(listOf(OrderCommitOutcome.SUPERSEDED), first)
        assertEquals(listOf(OrderCommitOutcome.COMMITTED), second)
        assertEquals(listOf("Main@a", "Main@b", "Main@c"), storedOrder())
    }

    @Test fun theCallbackRunsExactlyOnceOnTheMainThread() {
        var threads = mutableListOf<Boolean>()
        AppPersistent.commitAppOrderBatch(apps, LENS) {
            threads += (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        }
        await(threads)
        Thread.sleep(POLL_MS * 4)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(listOf(true), threads)
    }

    @Test fun theLegacyFireAndForgetApiStillWorksUnchanged() {
        AppPersistent.setAppOrderBatch(apps.reversed(), LENS)
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && storedOrder() != listOf("Main@c", "Main@b", "Main@a")) Thread.sleep(POLL_MS)
        assertEquals(listOf("Main@c", "Main@b", "Main@a"), storedOrder())
    }

    private fun setDatabaseInstance(database: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, database)
    }

    private companion object {
        const val LENS = "fish035-lens"
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 25L
    }
}
```

- [ ] **Step 2: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.model.AppPersistentOrderOutcomeTest'`. Expected: compile failure only — `commitAppOrderBatch`, `OrderCommitOutcome`, `beforeOrderCommitForTest` unresolved. (`theLegacyFireAndForgetApiStillWorksUnchanged` is a characterization test and must be GREEN once the file compiles.)
- [ ] **Step 3: GREEN.** In `AppPersistent.kt`, at **file top level** (outside the class, below the imports) add `/** FISH-035: what happened to a [AppPersistent.commitAppOrderBatch] write. */ enum class OrderCommitOutcome { COMMITTED, FAILED, SUPERSEDED }`. Then add imports `androidx.room.withTransaction`, `androidx.annotation.VisibleForTesting`, `com.mckimquyen.app.ApplicationScope` (already used: keep), `kotlinx.coroutines.withContext`. Inside the `companion object`, after `setAppOrderBatch`:

```kotlin
        /** Test seam: runs inside the order mutex just before the revision check. Null in production. */
        @VisibleForTesting
        internal var beforeOrderCommitForTest: (() -> Unit)? = null

        /**
         * FISH-035: writes [apps] as the order of [lensId] and reports the real outcome exactly once, on the main
         * thread. Unlike [setAppOrderBatch] it is not optimistic: the singleton's order numbers change only after
         * the Room transaction committed. A write that lost the per-lens revision race is [OrderCommitOutcome.SUPERSEDED];
         * a missing lens, an unusable list or any database error is [OrderCommitOutcome.FAILED].
         */
        @JvmStatic
        fun commitAppOrderBatch(apps: List<App>, lensId: String, onOutcome: (OrderCommitOutcome) -> Unit) {
            val rows = apps.mapNotNull { app ->
                val packageName = app.packageName?.toString()
                val name = app.name?.toString()
                if (packageName.isNullOrBlank() || name.isNullOrBlank()) null else Triple(packageName, name, app.userId)
            }
            if (rows.isEmpty()) {
                ApplicationScope.scope.launch { onOutcome(OrderCommitOutcome.FAILED) }
                return
            }
            val revisionCounter = orderWriteRevisions.computeIfAbsent(lensId) { AtomicLong(0L) }
            val revision = revisionCounter.incrementAndGet()
            val mutex = orderWriteMutexes.computeIfAbsent(lensId) { Mutex() }
            ApplicationScope.scope.launch(Dispatchers.IO) {
                val outcome = try {
                    mutex.withLock {
                        beforeOrderCommitForTest?.invoke()
                        if (revision != revisionCounter.get()) {
                            OrderCommitOutcome.SUPERSEDED
                        } else {
                            val database = AppDatabase.getInstance()
                            database.withTransaction {
                                if (database.lensWorkspaceDao().findById(lensId) == null) {
                                    OrderCommitOutcome.FAILED
                                } else {
                                    database.appPersistentDao().setOrders(
                                        rows.mapIndexed { index, (pkg, name, userId) ->
                                            defaults(pkg, name, lensId, userId).copy(orderNumber = index)
                                        }
                                    )
                                    OrderCommitOutcome.COMMITTED
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.e("AppPersistent: confirmed order write failed", error)
                    AppEventManager.notifyPersistFailed()
                    OrderCommitOutcome.FAILED
                }
                withContext(Dispatchers.Main) {
                    if (outcome == OrderCommitOutcome.COMMITTED) {
                        rows.forEachIndexed { index, (pkg, name, userId) ->
                            RAppsSingleton.instance.updateAppOrder(pkg, name, index, userId)
                        }
                    }
                    onOutcome(outcome)
                }
            }
        }
```

- [ ] **Step 4: Verify GREEN.** Rerun the class: 7/7 pass. Then run the old suite to prove nothing regressed: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.model.AppPersistentFailureTest' --tests 'com.mckimquyen.util.LensFreezeEditorTest'`.
- [ ] **Step 5: Commit.** `git add app/src/main/java/com/mckimquyen/model/AppPersistent.kt app/src/test/java/com/mckimquyen/model/AppPersistentOrderOutcomeTest.kt && git commit -m "feat(fish-035): add confirmed order commit with outcome"` with the trailer.

---

## Task 3 — `LensView`: classification wait, group-aware preview, confirmed adoption

**Design (read before coding).**
- New phase `mRingPhase`: `IDLE`, `WAITING` (pinned, one pointer, tracker running, pan withheld), `ROTATING`, `PAN_DECIDED` (tracker said PAN, old code runs), `ABORTED` (events swallowed until UP/CANCEL).
- The tracker starts at ACTION_DOWN only when `mFocusLockPinned && displayedApps.size >= 2 && pointerCount == 1`. Edge-swipe and pull-down search are evaluated **before** the ring block (they win); the ring block sits right before the horizontal-swipe/pan-slop code, which is unchanged.
- `WAITING`: every MOVE feeds the tracker with `event.eventTime`; while `UNDECIDED` the MOVE is consumed (`return true`) so neither pan nor page-swipe starts. `PAN` → phase `PAN_DECIDED` and the old pan code runs on the same event (pan uses absolute coordinates, nothing to replay). A stationary hold still reaches the existing long-press runnable.
- `ROTATE` → snapshot `mRingBaseApps = ArrayList(mApps)`, cancel long press, clear the pending empty tap, `requestDisallowInterceptTouchEvent(true)`, set `mTouchX/Y = -Float.MAX_VALUE`, `mSelectIndex = -1`, `mRectToSelect = null` (pin stays the visual centre, no hover haptics). Preview is always `RingRotation.rotateByGroup(mRingBaseApps, wholeSteps(tracker.sweepRadians), key)` recomputed from the base only when the step count changes (no drift, no per-event allocation).
- `mSourceApps` is **never** touched by a preview. Only `resolveRingCommit(token, true)` replaces it.
- ACTION_UP while `ROTATING`: if the preview equals the base element-by-element (net zero) restore and report nothing; otherwise create `RingPending(token, baseSource, baseApps, previewApps, announce = false)` and call `onRingRotatedListener`. No listener, or a pending commit already in flight → restore base. The preview stays on screen until the host resolves.
- `resolveRingCommit(token, committed)`: ignored unless `token == mRingPending?.token`. `true` → `mSourceApps = ArrayList(preview)`, `mApps = preview`, `applySmartFocusArrangement(force = true)` (the host has already saved frozen, so the arranger keeps the order), announce if requested. `false` → `mSourceApps = baseSource`, `mApps = baseApps`.
- Anything that replaces the data under a preview drops it: `setApps` and the `lensId` setter call `dropRing()` (phase `ABORTED` if `ROTATING`, `mRingPending = null`). `applySmartFocusArrangement(force = false)` returns early while `ROTATING` or pending (otherwise a grid-size change would rebuild `mApps` from the old source).
- CANCEL, POINTER_DOWN, `resetTouchState()` (which detach and export call) and `cancelRingRotationForPause()` run `cancelRingRotation()`: restore the base if `ROTATING`, set `IDLE`, reset the tracker. A pending commit survives CANCEL/pause (the write is in flight) but is dropped on detach.
- `rotateAndCommit(steps)` (TalkBack): needs a listener, `displayedApps.size >= 2`, no pending commit and a non-empty net rotation; it builds the same `RingPending` with `announce = true` and fires the listener. No pin is required.

- [ ] **Step 1: RED — shared harness.** Create `app/src/androidTest/java/com/mckimquyen/views/LensViewRingRotateIntegrationTest.kt` (package `com.mckimquyen.views`). Harness (same shape as `LensViewFocusLockIntegrationTest`, plus explicit event times because the tracker reads `event.eventTime`):

```kotlin
@RunWith(AndroidJUnit4::class)
class LensViewRingRotateIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Already in the order UtilAppSorter would produce: F pinned first, D/E favorites, then A/B/C. */
    private fun fixture() = listOf(
        app("F", PinnedZone.START, false), app("D", PinnedZone.NONE, true), app("E", PinnedZone.NONE, true),
        app("A", PinnedZone.NONE, false), app("B", PinnedZone.NONE, false), app("C", PinnedZone.NONE, false)
    )

    private fun app(name: String, zone: PinnedZone, favorite: Boolean) =
        App(label = name, packageName = "pkg.ring.${name.lowercase()}", name = "Main", pinnedZone = zone, isFavorite = favorite)

    private fun expected(order: List<App>, steps: Int) =
        RingRotation.rotateByGroup(order, steps) { RingRotation.groupKeyOf(it.pinnedZone.rank, it.isFavorite) }

    private fun labels(apps: List<App>) = apps.map { it.label.toString() }

    private fun withView(apps: List<App> = fixture(), block: (LensView) -> Unit) {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(ArrayList(apps))
                    layoutParams = FrameLayout.LayoutParams(VIEW_SIZE_PX, VIEW_SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
                    draw(Canvas())
                }
            }
            instrumentation.waitForIdleSync()
            block(requireNotNull(view) { "view is assigned inside onActivity above" })
        }
    }

    private fun displayed(view: LensView) = labels(view.displayedApps)

    private fun pinAt500(view: LensView) = instrumentation.runOnMainSync { assertTrue(view.toggleFocusLockAtForTest(CX, CY)) }

    private fun listenerInto(view: LensView, sink: MutableList<RingRotationRequest>) =
        instrumentation.runOnMainSync { view.onRingRotatedListener = OnRingRotatedListener { sink += it } }

    private fun sendAt(view: LensView, downTime: Long, t: Long, action: Int, x: Float, y: Float) =
        instrumentation.runOnMainSync {
            val e = MotionEvent.obtain(downTime, downTime + t, action, x, y, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
        }

    private data class Pt(val x: Float, val y: Float)

    /** Points on a circle: [angles] are absolute radians, the first one is where the finger lands. */
    private fun onCircle(radius: Float, angles: List<Float>, centerX: Float = CX, centerY: Float = CY) =
        angles.map { Pt(centerX + radius * cos(it), centerY + radius * sin(it)) }

    /** Angles from 0 to [sweepRad] at [radPerSec], one per frame (the last one lands exactly on the sweep). */
    private fun ramp(sweepRad: Float, radPerSec: Float = ARC_SPEED_RAD_PER_SEC, from: Float = 0f): List<Float> {
        val perFrame = radPerSec * FRAME_MS / 1000f
        val frames = (abs(sweepRad) / perFrame).toInt().coerceAtLeast(1)
        val dir = if (sweepRad < 0f) -1f else 1f
        return List(frames + 1) { i -> if (i == frames) from + sweepRad else from + dir * perFrame * i }
    }

    /** One continuous finger: DOWN on the first point, a MOVE every frame, then UP (unless [release] is false). */
    private fun drag(view: LensView, points: List<Pt>, release: Boolean = true): Long {
        val downTime = SystemClock.uptimeMillis()
        sendAt(view, downTime, 0, MotionEvent.ACTION_DOWN, points[0].x, points[0].y)
        for (i in 1 until points.size) sendAt(view, downTime, i * FRAME_MS, MotionEvent.ACTION_MOVE, points[i].x, points[i].y)
        if (release) sendAt(view, downTime, points.size * FRAME_MS, MotionEvent.ACTION_UP, points.last().x, points.last().y)
        return downTime
    }

    private fun arc(view: LensView, sweepSteps: Float, release: Boolean = true, radius: Float = ARC_RADIUS_PX) =
        drag(view, onCircle(radius, ramp(sweepSteps * RingRotation.STEP_RADIANS)), release)

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, frames: Int) =
        List(frames + 1) { i -> Pt(x0 + (x1 - x0) * i / frames, y0 + (y1 - y0) * i / frames) }

    private fun send(view: LensView, downTime: Long, action: Int, x: Float, y: Float) =
        sendAt(view, downTime, SystemClock.uptimeMillis() - downTime, action, x, y)

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val CX = 500f
        const val CY = 500f
        const val FRAME_MS = 8L
        const val ARC_RADIUS_PX = 300f
        const val ARC_SPEED_RAD_PER_SEC = 3f
    }
}
```

  Imports: `android.graphics.Canvas`, `android.os.SystemClock`, `android.view.MotionEvent`, `android.view.View`, `android.widget.FrameLayout`, `androidx.test.core.app.ActivityScenario`, `androidx.test.ext.junit.runners.AndroidJUnit4`, `androidx.test.platform.app.InstrumentationRegistry`, `com.mckimquyen.feature.organization.OrganizationHarnessActivity`, `com.mckimquyen.model.App`, `com.mckimquyen.model.PinnedZone`, `com.mckimquyen.util.RingRotation`, `com.mckimquyen.util.UtilSettings`, `org.junit.Assert.*`, `org.junit.Test`, `org.junit.runner.RunWith`, `kotlin.math.abs/cos/sin`.

- [ ] **Step 2: RED — tests, part 1 (gesture classification and preview).** Add inside the class:

```kotlin
    @Test fun aClockwiseArcPreviewsTheGroupRotationAndReportsOneRequestOnRelease() = withView { view ->
        val base = view.displayedApps.toList()
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        assertEquals("exactly one commit request per gesture", 1, requests.size)
        // F is pinned, D/E favorites, A/B/C plain: each group rotates by one slot, never across a boundary
        assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(requests[0].apps))
        assertEquals(labels(expected(base, 1)), labels(requests[0].apps))
        assertEquals(labels(requests[0].apps), displayed(view))
        assertEquals(view.lensId, requests[0].lensId)
        assertEquals(requests[0].token, view.ringPendingTokenForTest())
    }

    @Test fun twoWholeStepsAreAppliedAsOneRequest() = withView { view ->
        val base = view.displayedApps.toList()
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 2.2f)
        assertEquals(1, requests.size)
        assertEquals(listOf("F", "D", "E", "C", "A", "B"), labels(requests[0].apps))   // 2 steps: D/E back to start
        assertEquals(labels(expected(base, 2)), labels(requests[0].apps))
    }

    @Test fun aCounterClockwiseArcRotatesBack() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = -1.2f)
        assertEquals(1, requests.size)
        assertEquals(listOf("F", "E", "D", "C", "A", "B"), labels(requests[0].apps))
    }

    @Test fun thePreviewFollowsTheFingerAndTheOrderIsStableWhileTheGestureIsStillDown() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val down = arc(view, sweepSteps = 1.2f, release = false)
        assertTrue(view.isRingRotatingForTest())
        assertEquals(listOf("F", "E", "D", "B", "C", "A"), displayed(view))
        assertTrue("nothing is reported before the finger lifts", requests.isEmpty())
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
    }

    @Test fun reversingTheArcBelowOneStepRestoresTheOrderAndReportsNothing() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val out = ramp(RingRotation.STEP_RADIANS * 2.2f)
        val back = ramp(-RingRotation.STEP_RADIANS * 1.9f, from = out.last())   // ends at +0.3 steps -> net 0
        drag(view, onCircle(ARC_RADIUS_PX, out + back.drop(1)))
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
        assertNull(view.ringPendingTokenForTest())
    }

    @Test fun aFullTurnIsANetNoOpAndWritesNothing() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 12.1f)           // 12 whole steps: every group returns to its start
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun whenEveryAppIsInItsOwnGroupARotationChangesNothingAndReportsNothing() = withView(
        apps = listOf(app("S", PinnedZone.START, false), app("N", PinnedZone.NONE, false), app("E", PinnedZone.END, false))
    ) { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        assertTrue(requests.isEmpty())
        assertEquals(listOf("S", "N", "E"), displayed(view))
    }

    @Test fun theSameArcWithoutAPinIsAnOrdinaryPanAndNeverRotates() = withView { view ->
        val base = labels(view.displayedApps)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val down = arc(view, sweepSteps = 3f, release = false)
        assertEquals(LensGestureState.PANNING, view.gestureState)
        assertFalse(view.isRingRotatingForTest())
        send(view, down, MotionEvent.ACTION_UP, 800f, 500f)
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun aPinnedStraightDragIsAPanThatStartsOnlyAfterTheClassificationWindow() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        // 600 px/s straight down the tangent: 4.8 px per 8 ms frame, 50 frames = 240 px
        val pts = line(800f, 400f, 800f, 640f, frames = 50)
        val down = SystemClock.uptimeMillis()
        sendAt(view, down, 0, MotionEvent.ACTION_DOWN, pts[0].x, pts[0].y)
        for (i in 1..10) sendAt(view, down, i * FRAME_MS, MotionEvent.ACTION_MOVE, pts[i].x, pts[i].y)
        assertEquals("pan is withheld while the tracker is still classifying", LensGestureState.IDLE, view.gestureState)
        for (i in 11..50) sendAt(view, down, i * FRAME_MS, MotionEvent.ACTION_MOVE, pts[i].x, pts[i].y)
        assertEquals("after the window the pan runs as before", LensGestureState.PANNING, view.gestureState)
        assertEquals("the pan follows the finger's absolute position, nothing was lost", 640f, view.touchCoordinatesForTest().second, 0.5f)
        sendAt(view, down, 51 * FRAME_MS, MotionEvent.ACTION_UP, pts[50].x, pts[50].y)
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun aPinnedDragStartingInsideTheMinimumRadiusPansImmediately() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        // a 20 px circle swept through 180 degrees puts the finger 40 px from where it landed, beyond any touch slop
        val down = drag(view, onCircle(20f, ramp(RingRotation.STEP_RADIANS * 6f)), release = false)
        assertEquals(LensGestureState.PANNING, view.gestureState)
        assertFalse(view.isRingRotatingForTest())
        send(view, down, MotionEvent.ACTION_UP, 480f, 500f)
        assertTrue(requests.isEmpty())
    }
```

- [ ] **Step 3: RED — tests, part 2 (precedence, lifecycle, confirmed adoption).** Add inside the same class:

```kotlin
    @Test fun aSecondFingerDuringTheWaitCancelsTheRingAndBecomesAPinch() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val down = SystemClock.uptimeMillis()
        sendAt(view, down, 0, MotionEvent.ACTION_DOWN, 800f, 500f)
        instrumentation.runOnMainSync {
            val e = MotionEvent.obtain(
                down, down + 16, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
                    MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { x = 800f; y = 500f },
                    MotionEvent.PointerCoords().apply { x = 200f; y = 500f }),
                0, 0, 1f, 1f, 0, 0, 0, 0
            )
            view.dispatchTouchEvent(e)
            e.recycle()
        }
        assertFalse(view.isRingRotatingForTest())
        assertTrue(requests.isEmpty())
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
        assertEquals(base, displayed(view))
    }

    @Test fun aSecondFingerDuringRotationRestoresTheOrderAndReportsNothing() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val down = arc(view, sweepSteps = 1.2f, release = false)
        assertTrue(view.isRingRotatingForTest())
        instrumentation.runOnMainSync {
            val e = MotionEvent.obtain(
                down, down + 400, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
                    MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { x = 700f; y = 600f },
                    MotionEvent.PointerCoords().apply { x = 200f; y = 200f }),
                0, 0, 1f, 1f, 0, 0, 0, 0
            )
            view.dispatchTouchEvent(e)
            e.recycle()
        }
        assertFalse(view.isRingRotatingForTest())
        assertEquals(base, displayed(view))
        send(view, down, MotionEvent.ACTION_UP, 700f, 600f)
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun cancelAndPauseAndDetachEachRestoreAnUncommittedPreview() {
        for (how in listOf("cancel", "pause", "detach")) withView { view ->
            val base = labels(view.displayedApps)
            pinAt500(view)
            val down = arc(view, sweepSteps = 1.2f, release = false)
            assertTrue("$how: rotating", view.isRingRotatingForTest())
            when (how) {
                "cancel" -> send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
                "pause" -> onMain { view.cancelRingRotationForPause() }
                else -> onMain { (view.parent as ViewGroup).removeView(view) }
            }
            assertFalse("$how: state cleared", view.isRingRotatingForTest())
            if (how != "detach") assertEquals("$how: order restored", base, displayed(view))
        }
    }

    @Test fun releaseAfterARotationLeavesNoSelectionSoNoAppCanLaunch() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        // launchApp() reads mSelectIndex; -1 means it has nothing to launch (and the UP handler returns before it anyway)
        assertEquals(-1, view.selectedIndexForTest)
        assertEquals(-Float.MAX_VALUE, view.touchCoordinatesForTest().first, 0f)
        assertEquals("the pin is untouched", CX to CY, view.pinnedFocusForTest())
    }

    @Test fun aLongPressNeverFiresWhileRotating() = withView { view ->
        pinAt500(view)
        val down = arc(view, sweepSteps = 1.2f, release = false)
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L)
        instrumentation.waitForIdleSync()
        assertTrue("still rotating, no menu or ring replaced the gesture", view.isRingRotatingForTest())
        assertFalse(view.isFocusRingActiveForTest())
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
    }

    @Test fun anEdgeSwipeWinsOverTheRingAndNeverRotates() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        var edgeSwipes = 0
        onMain { view.onEdgeSwipeListener = OnEdgeSwipeListener { edgeSwipes++ } }
        // lands on the right edge strip (x = 990) and drags horizontally left, far beyond the edge threshold
        drag(view, line(990f, 500f, 300f, 500f, frames = 40))
        assertEquals("the existing FISH-025 edge swipe still fires exactly once", 1, edgeSwipes)
        assertFalse(view.isRingRotatingForTest())
        assertTrue(requests.isEmpty())
        assertEquals(base, displayed(view))
    }

    @Test fun theHostConfirmsAndTheViewAdoptsTheNewOrderAsItsSource() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        val committed = labels(requests.single().apps)
        onMain { view.resolveRingCommit(requests.single().token, committed = true) }
        assertNull(view.ringPendingTokenForTest())
        assertEquals(committed, displayed(view))
        onMain { view.refreshSmartFocus() }
        assertEquals("a refresh must keep the adopted order, not rebuild the old one", committed, displayed(view))
    }

    @Test fun aFailedCommitRestoresTheOrderTheViewHadBeforeTheGesture() = withView { view ->
        val base = labels(view.displayedApps)
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        onMain { view.resolveRingCommit(requests.single().token, committed = false) }
        assertNull(view.ringPendingTokenForTest())
        assertEquals(base, displayed(view))
        onMain { view.refreshSmartFocus() }
        assertEquals("the source order was never changed", base, displayed(view))
    }

    @Test fun aStaleOrUnknownTokenIsIgnored() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        val preview = labels(requests.single().apps)
        onMain { view.resolveRingCommit(requests.single().token + 1, committed = false) }
        assertEquals("a wrong token must not touch the preview", preview, displayed(view))
        assertEquals(requests.single().token, view.ringPendingTokenForTest())
    }

    @Test fun newDataWhileACommitIsPendingDropsThePendingPreview() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        val fresh = ArrayList(fixture().reversed())
        onMain { view.setApps(fresh) }
        assertNull(view.ringPendingTokenForTest())
        assertEquals(labels(fresh), displayed(view))
        onMain { view.resolveRingCommit(requests.single().token, committed = true) }   // late callback
        assertEquals("a late confirmation must not overwrite newer data", labels(fresh), displayed(view))
    }

    @Test fun aNewGestureIsRefusedWhileACommitIsStillPending() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        val afterFirst = labels(requests.single().apps)
        arc(view, sweepSteps = 1.2f)
        assertEquals(1, requests.size)
        assertEquals(afterFirst, displayed(view))
    }

    @Test fun smartFocusIsNotAppliedOverAPreviewWhenTheGridSizeChanges() = withView { view ->
        val settings = UtilSettings(instrumentation.targetContext)
        val before = settings.isSmartFocusBias(view.lensId)
        try {
            settings.saveSmartFocusBias(view.lensId, true)
            pinAt500(view)
            val down = arc(view, sweepSteps = 1.2f, release = false)
            val preview = displayed(view)
            onMain { view.layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX - 100); view.draw(Canvas()) }
            assertEquals("a resize mid-gesture must not rebuild the list from the old source", preview, displayed(view))
            send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
        } finally {
            settings.saveSmartFocusBias(view.lensId, before)
        }
    }

    @Test fun rotateAndCommitForTheHostActionsNeedsNoPinAndIsRefusedBelowTwoAppsOrForANetNoOp() {
        withView { view ->
            val requests = mutableListOf<RingRotationRequest>()
            listenerInto(view, requests)
            var ok = false
            onMain { ok = view.rotateAndCommit(1) }
            assertTrue(ok)
            assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(requests.single().apps))
        }
        withView(apps = listOf(app("only", PinnedZone.NONE, false))) { view ->
            var ok = true
            onMain { ok = view.rotateAndCommit(1) }
            assertFalse(ok)
        }
        withView { view ->
            var ok = true
            onMain { ok = view.rotateAndCommit(12) }
            assertFalse("a full turn changes nothing", ok)
        }
    }

    @Test fun detachClearsTheListenerAndAnyPendingCommit() = withView { view ->
        pinAt500(view)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        arc(view, sweepSteps = 1.2f)
        onMain { (view.parent as ViewGroup).removeView(view) }
        assertNull(view.onRingRotatedListener)
        assertNull(view.ringPendingTokenForTest())
    }
```

  Additional imports: `android.view.ViewConfiguration`, `android.view.ViewGroup`, `com.mckimquyen.views.OnEdgeSwipeListener`. (Same package: `LensView`, `LensGestureState`, `RingRotationRequest`, `OnRingRotatedListener`.)
- [ ] **Step 4: Verify RED.** `./gradlew compileDevDebugAndroidTestKotlin`. Expected: unresolved `onRingRotatedListener`, `RingRotationRequest`, `OnRingRotatedListener`, `isRingRotatingForTest`, `ringPendingTokenForTest`, `resolveRingCommit`, `cancelRingRotationForPause`, `rotateAndCommit`. Compile errors only; fix typos in the test, never by weakening an assertion.
  (If `anEdgeSwipeWinsOverTheRingAndNeverRotates` does not see exactly one edge swipe, compare the geometry with `EdgeSwipePolicyTest` / `LensViewEdgeSwipe*` and adjust the line's start x or length — the assertion that the ring never takes over stays.)

- [ ] **Step 5: GREEN — request type and listener.** Create `app/src/main/java/com/mckimquyen/views/RingRotationRequest.kt`:

```kotlin
package com.mckimquyen.views

import com.mckimquyen.model.App

/**
 * FISH-035: one finished rotation waiting for the host to confirm the database write. [apps] is the full new order of
 * lens [lensId]; [token] ties the host's later answer to this exact request (see [LensView.resolveRingCommit]).
 */
class RingRotationRequest(val token: Long, val lensId: String, val apps: List<App>)

/** Called on the main thread when a rotation finished; the host must answer through [LensView.resolveRingCommit]. */
fun interface OnRingRotatedListener {
    fun onRingRotated(request: RingRotationRequest)
}
```

- [ ] **Step 6: GREEN — `LensView` state and helpers.** In `LensView.kt` add the imports `com.mckimquyen.util.RingGestureTracker` and `com.mckimquyen.util.RingRotation`, then next to the FISH-029 state (after `toggleFocusLockAt`):

```kotlin
    // FISH-035: rotate the pinned focus ring to cycle the order inside each pinned/favorite group.
    private enum class RingPhase { IDLE, WAITING, ROTATING, PAN_DECIDED, ABORTED }

    private class RingPending(
        val token: Long,
        val baseSource: ArrayList<App>?,
        val baseApps: ArrayList<App>?,
        val preview: ArrayList<App>,
        val announce: Boolean
    )

    var onRingRotatedListener: OnRingRotatedListener? = null
    private var mRingPhase = RingPhase.IDLE
    private val mRingTracker = RingGestureTracker(resources.displayMetrics.density)
    private var mRingBaseApps: ArrayList<App>? = null
    private var mRingAppliedSteps = 0
    private var mRingPending: RingPending? = null
    private var mRingNextToken = 0L

    @VisibleForTesting internal fun isRingRotatingForTest(): Boolean = mRingPhase == RingPhase.ROTATING
    @VisibleForTesting internal fun ringPendingTokenForTest(): Long? = mRingPending?.token

    private fun ringGroupKey(app: App) = RingRotation.groupKeyOf(app.pinnedZone.rank, app.isFavorite)

    private fun sameOrder(a: List<App>, b: List<App>) =
        a.size == b.size && a.indices.all { a[it].identifier == b[it].identifier }

    private fun canStartRing(pointerCount: Int) =
        mFocusLockPinned && pointerCount == 1 && mRingPending == null && (mApps?.size ?: 0) >= 2

    /** Restores the pre-gesture list if a preview is on screen, then forgets the gesture. Safe at any time. */
    private fun cancelRingRotation() {
        if (mRingPhase == RingPhase.ROTATING) mRingBaseApps?.let { mApps = ArrayList(it) }
        mRingPhase = RingPhase.IDLE
        mRingBaseApps = null
        mRingAppliedSteps = 0
        mRingTracker.reset()
        mAccessibilityHelper?.invalidateRoot()
        invalidate()
    }

    /** Home pause hook (Java caller in ActHome); a write already in flight is left to resolve. */
    fun cancelRingRotationForPause() = cancelRingRotation()

    /** Newer data replaced the list the preview was built from: forget the gesture and the pending commit. */
    private fun dropRing() {
        mRingPending = null
        mRingPhase = if (mRingPhase == RingPhase.ROTATING) RingPhase.ABORTED else RingPhase.IDLE
        mRingBaseApps = null
        mRingAppliedSteps = 0
        mRingTracker.reset()
    }

    private fun beginRingPreview() {
        mRingBaseApps = ArrayList(mApps.orEmpty())
        mRingAppliedSteps = 0
        mRingPhase = RingPhase.ROTATING
        mLongPressArmed = false
        mLongPressHandler.removeCallbacks(mLongPressRunnable)
        mFirstEmptyTap = null
        mCurrentTapEligible = false
        mCurrentTapSecond = false
        resetFocusRing()
        parent?.requestDisallowInterceptTouchEvent(true)
        mTouchX = -Float.MAX_VALUE
        mTouchY = -Float.MAX_VALUE
        mSelectIndex = -1
        mRectToSelect = null
        if (mMoving) {
            mMoving = false
            startAnimation(LensAnimation(false))
        }
    }

    private fun updateRingPreview() {
        val base = mRingBaseApps ?: return
        val steps = RingRotation.wholeSteps(mRingTracker.sweepRadians)
        if (steps == mRingAppliedSteps) return
        mRingAppliedSteps = steps
        mApps = ArrayList(RingRotation.rotateByGroup(base, steps, ::ringGroupKey))
        mAccessibilityHelper?.invalidateRoot()
        invalidate()
    }

    /** Hands a finished preview to the host, or restores the base when nothing changed or nobody can confirm. */
    private fun finishRingRotation() {
        val base = mRingBaseApps
        val preview = mApps
        val listener = onRingRotatedListener
        mRingPhase = RingPhase.IDLE
        mRingBaseApps = null
        mRingAppliedSteps = 0
        mRingTracker.reset()
        if (base == null || preview == null || listener == null || sameOrder(base, preview)) {
            if (base != null) mApps = base
            mAccessibilityHelper?.invalidateRoot()
            invalidate()
            return
        }
        submitRingRequest(base, preview, announce = false, listener = listener)
    }

    private fun submitRingRequest(
        base: ArrayList<App>, preview: ArrayList<App>, announce: Boolean, listener: OnRingRotatedListener
    ) {
        val token = ++mRingNextToken
        mRingPending = RingPending(token, mSourceApps?.let(::ArrayList), base, preview, announce)
        listener.onRingRotated(RingRotationRequest(token, lensId, ArrayList(preview)))
    }

    /**
     * The host's answer for [token]. `true`: the order is stored, so it becomes this view's source order and Smart Focus
     * (already frozen by the host) keeps it. `false`: the write failed or lost a race, so the pre-gesture list comes back.
     * Unknown or stale tokens are ignored.
     */
    fun resolveRingCommit(token: Long, committed: Boolean) {
        val pending = mRingPending ?: return
        if (pending.token != token) return
        mRingPending = null
        if (committed) {
            mSourceApps = ArrayList(pending.preview)
            applySmartFocusArrangement(force = true)
            if (pending.announce) announceForAccessibility(context.getString(
                R.string.lens_rotate_announcement, pending.preview.firstOrNull()?.label?.toString().orEmpty()
            ))
        } else {
            mSourceApps = pending.baseSource
            mApps = pending.baseApps
            mAccessibilityHelper?.invalidateRoot()
            invalidate()
        }
    }

    /** TalkBack/host entry: one step (or [steps]) without a gesture. Returns false when nothing would change. */
    internal fun rotateAndCommit(steps: Int): Boolean {
        val listener = onRingRotatedListener ?: return false
        val current = mApps ?: return false
        if (current.size < 2 || mRingPending != null || mRingPhase == RingPhase.ROTATING) return false
        val rotated = ArrayList(RingRotation.rotateByGroup(current, steps, ::ringGroupKey))
        if (sameOrder(current, rotated)) return false
        val base = ArrayList(current)
        mApps = rotated
        mAccessibilityHelper?.invalidateRoot()
        invalidate()
        submitRingRequest(base, rotated, announce = true, listener = listener)
        return true
    }
```

- [ ] **Step 7: GREEN — data replacement guards.** (a) `setApps(...)`: first line `dropRing()`. (b) the `lensId` setter: call `dropRing()` before `applySmartFocusArrangement(force = true)`. (c) `applySmartFocusArrangement`: after `val source = …` add `if (!force && (mRingPhase == RingPhase.ROTATING || mRingPending != null)) return`. The forced call from `resolveRingCommit` and `refreshSmartFocus()` is intentionally unaffected; `refreshSmartFocus()` while ROTATING must also be a no-op, so make it `fun refreshSmartFocus() { if (mRingPhase == RingPhase.ROTATING) return; applySmartFocusArrangement(force = true) }`.

- [ ] **Step 8: GREEN — gesture wiring in `handleAppTouch`.** All edits are additive; the unpinned path never enters them because `canStartRing` is false and every other phase is `IDLE`.
  - **ACTION_DOWN**, right after `resetFocusRing()`:
    ```kotlin
    if (mRingPhase == RingPhase.ROTATING) cancelRingRotation()      // a lost UP must not leave a preview behind
    mRingPhase = RingPhase.IDLE
    mRingTracker.reset()
    if (canStartRing(event.pointerCount)) {
        mRingTracker.begin(mFocusLockX, mFocusLockY, event.x, event.y, event.eventTime)
        mRingPhase = if (mRingTracker.decision == RingRotation.Decision.PAN) RingPhase.PAN_DECIDED else RingPhase.WAITING
    }
    ```
  - **ACTION_POINTER_DOWN**, first line: `cancelRingRotation()`.
  - **ACTION_MOVE**, in the two places where edge-swipe (`mEdgeSwipeTriggered = true`) and pull-down search (`mSearchSwipeTriggered = true`) fire, add `if (mRingPhase == RingPhase.WAITING) { mRingPhase = RingPhase.IDLE; mRingTracker.reset() }` so those existing gestures keep precedence. Then, immediately **before** `val dx = event.x - mTouchX`, insert:
    ```kotlin
    if (mRingPhase == RingPhase.ABORTED) return true
    if (mRingPhase == RingPhase.WAITING) {
        when (mRingTracker.onMove(event.x, event.y, event.eventTime)) {
            RingRotation.Decision.ROTATE -> { beginRingPreview(); updateRingPreview(); return true }
            RingRotation.Decision.PAN -> mRingPhase = RingPhase.PAN_DECIDED   // old pan code below runs on this very event
            RingRotation.Decision.UNDECIDED -> return true
        }
    } else if (mRingPhase == RingPhase.ROTATING) {
        mRingTracker.onMove(event.x, event.y, event.eventTime)
        updateRingPreview()
        return true
    }
    ```
    The pan code that follows uses absolute coordinates (`mTouchX = effective(event.x)`), so nothing buffered needs replaying.
  - **ACTION_UP**, right after `mLongPressHandler.removeCallbacks(mLongPressRunnable)`:
    ```kotlin
    val ringPhase = mRingPhase
    if (ringPhase == RingPhase.ROTATING || ringPhase == RingPhase.ABORTED) {
        if (ringPhase == RingPhase.ROTATING) finishRingRotation() else { mRingPhase = RingPhase.IDLE; mRingTracker.reset() }
        resetSearchSwipeState()
        gestureState = LensGestureState.IDLE
        mSelectIndex = -1
        mRectToSelect = null
        mTouchX = -Float.MAX_VALUE
        mTouchY = -Float.MAX_VALUE
        invalidate()
        return true
    }
    mRingPhase = RingPhase.IDLE
    mRingTracker.reset()
    ```
  - **ACTION_CANCEL**, first line: `cancelRingRotation()` (restores an uncommitted preview; a pending write is untouched).
  - **`resetTouchState()`** (used by detach and export): add `cancelRingRotation()` as its first line.
  - **`onDetachedFromWindow`**, beside the other nulled listeners: `onRingRotatedListener = null` and `mRingPending = null`.
- [ ] **Step 9: Verify GREEN (compile, JVM, then device).** `./gradlew testDevDebugUnitTest compileDevDebugAndroidTestKotlin`. After the Task 6 device backup procedure, run on Pixel: `LensViewRingRotateIntegrationTest` and the regression set `LensViewFocusLockIntegrationTest`, `LensViewPinchIntegrationTest`, `LensViewPagerGestureIntegrationTest`, `LensViewFocusRingIntegrationTest`, `LensViewReattachIntegrationTest`, `LensViewWidgetTest`, `LensViewQuickActionsIntegrationTest`, `LensViewVignetteWidgetTest`, `LensViewTiltIntegrationTest` — every class must print `OK (N)` with N > 0. If `aPinnedStraightDragIsAPanThatStartsOnlyAfterTheClassificationWindow` fails on the "still IDLE after 10 frames" assertion, the constants or the DOWN wiring are wrong — do not loosen the assertion.
- [ ] **Step 10: Commit.** `git add app/src/main/java/com/mckimquyen/views/LensView.kt app/src/main/java/com/mckimquyen/views/RingRotationRequest.kt app/src/androidTest/java/com/mckimquyen/views/LensViewRingRotateIntegrationTest.kt && git commit -m "feat(fish-035): rotate pinned focus ring order with confirmed adoption"` with the trailer.

---

## Task 4 — Host commit: write, confirm, then freeze

**Design.** `LensViewHolder.bindLensView` (the only place that sets `LensView` listeners) installs one `OnRingRotatedListener`. Per request it: (1) calls `AppPersistent.commitAppOrderBatch(request.apps, request.lensId, …)`; (2) in the outcome callback (already on main) acts **only on `COMMITTED`**: `settings.saveLensFrozen(lensId, true)` first, then `view.resolveRingCommit(token, true)` — so `refreshSmartFocus`'s frozen gate is already true when the view rebuilds; (3) on `FAILED` or `SUPERSEDED` calls `view.resolveRingCommit(token, false)`; (4) a **page-recycle guard**: if `view.getLensId()` no longer equals `request.lensId` when the answer arrives (ViewPager2 reused the view for another lens), the freeze preference is still written for the committed lens, but the view is not touched (it was already reset by `setApps`/`lensId` and ignores the stale token anyway). After a commit the host sends `BroadcastReceivers.AppsEditedReceiver` (same as `AppAdapter.persistOrder`) so the singleton re-sorts and the list-mode adapter refreshes; the re-sort keeps the rotated order because the new `orderNumber`s sit below the group keys the sorter already applies. A failed write already posted `AppEventManager.notifyPersistFailed()` inside `commitAppOrderBatch`; the holder adds no second message.

**Files:** modify `LensViewHolder.java`, `ActHome.java` (pause); create `app/src/androidTest/java/com/mckimquyen/ui/ActHomeRingRotateWidgetTest.kt`.

- [ ] **Step 1: RED.** Create `ActHomeRingRotateWidgetTest.kt` (package `com.mckimquyen.ui`). It follows `ActHomeLensFreezeWidgetTest`: real `ActHome`, `DeviceStateGuard`, `RApplication.appRefreshPipeline.cancel()`, snapshot/restore of `RAppsSingleton.apps`, the default lens' rows and frozen flag, fisheye mode. Fixture: six visible apps `com.test.ring.{f,d,e,a,b,c}` with pinned/favorite flags as in Task 3 but seeded through the **real** path (`AppPersistent.defaults(...).copy(orderNumber = i, isFavorite = …, pinnedZone = …)` inserted with `dao.insert`, then `RAppsSingleton.instance.apps = ArrayList(fixture)`).

```kotlin
@RunWith(AndroidJUnit4::class)
class ActHomeRingRotateWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private val dao get() = AppDatabase.getInstance().appPersistentDao()
    private var originalApps: ArrayList<App>? = null
    private var roomSnapshot: LensManagerRoomSnapshot? = null

    private val fixture = listOf(
        row("f", PinnedZone.START, false), row("d", PinnedZone.NONE, true), row("e", PinnedZone.NONE, true),
        row("a", PinnedZone.NONE, false), row("b", PinnedZone.NONE, false), row("c", PinnedZone.NONE, false)
    )
    private fun row(n: String, zone: PinnedZone, fav: Boolean) = App(
        id = n.hashCode(), label = n.uppercase(), packageName = "com.test.ring.$n", name = "Main",
        isVisible = true, pinnedZone = zone, isFavorite = fav
    )

    @Before fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        roomSnapshot = LensManagerRoomSnapshot.capture(context)
        originalApps = RAppsSingleton.instance.apps
        val settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
        settings.setLauncherMode(LauncherMode.FISHEYE)
        settings.saveLensFrozen(lensId, false)
        settings.saveSmartFocusBias(lensId, false)
        settings.saveLensAppScope(lensId, LensAppScope.ALL)
        settings.saveLensHiddenAppIds(lensId, emptySet())
        runBlocking {
            dao.deleteForLens(lensId)
            fixture.forEachIndexed { i, a -> dao.insert(AppPersistent.defaults(a.packageName.toString(), "Main", lensId)
                .copy(orderNumber = i, isFavorite = a.isFavorite, pinnedZone = a.pinnedZone.name)) }
        }
        RAppsSingleton.instance.apps = ArrayList(fixture)
    }

    @After fun tearDown() {
        UtilSettings(context).saveLensFrozen(lensId, false)
        roomSnapshot?.restore(context)
        RAppsSingleton.instance.apps = originalApps
    }

    private fun storedLabels() = runBlocking { dao.getAllForLens(lensId) }.sortedBy { it.orderNumber }
        .map { it.packageName?.substringAfterLast('.')?.uppercase() }

    private fun awaitGrid(scenario: ActivityScenario<ActHome>): LensView {
        var view: LensView? = null
        val deadline = System.currentTimeMillis() + WAIT_MS
        var size = 0
        while (System.currentTimeMillis() < deadline && size != fixture.size) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { view = it.findViewById(R.id.lensViews); size = view?.displayedApps?.size ?: 0 }
            if (size != fixture.size) Thread.sleep(POLL_MS)
        }
        assertEquals("the grid never held the fixture", fixture.size, size)
        return requireNotNull(view) { "assigned in onActivity above" }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && !condition()) { instrumentation.waitForIdleSync(); Thread.sleep(POLL_MS) }
        assertTrue("timed out: $what", condition())
    }

    private fun labels(view: LensView) = view.displayedApps.map { it.label.toString() }

    @Test fun oneHostStepWritesTheGroupRotatedOrderThenFreezesAndKeepsItOnTheView() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            assertFalse(UtilSettings(context).isLensFrozen(lensId))
            var ok = false
            scenario.onActivity { ok = view.rotateAndCommit(1) }
            assertTrue(ok)
            waitUntil("frozen after the confirmed write") { UtilSettings(context).isLensFrozen(lensId) }
            waitUntil("rows rewritten") { storedLabels() == listOf("F", "E", "D", "B", "C", "A") }
            scenario.onActivity { assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(view)) }
            assertNull(view.ringPendingTokenForTest())
        }
    }

    @Test fun theFrozenFlagIsNotSetBeforeTheWriteIsConfirmed() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val gate = java.util.concurrent.CountDownLatch(1)
            val parked = java.util.concurrent.CountDownLatch(1)
            AppPersistent.beforeOrderCommitForTest = { parked.countDown(); gate.await(WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) }
            try {
                scenario.onActivity { assertTrue(view.rotateAndCommit(1)) }
                assertTrue(parked.await(WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS))
                assertFalse("write is still parked, so the lens must not be frozen yet", UtilSettings(context).isLensFrozen(lensId))
                gate.countDown()
                waitUntil("frozen after release") { UtilSettings(context).isLensFrozen(lensId) }
            } finally {
                AppPersistent.beforeOrderCommitForTest = null
                gate.countDown()
            }
        }
    }

    @Test fun anAlreadyFrozenLensStillRotatesAndStaysFrozen() {
        UtilSettings(context).saveLensFrozen(lensId, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            scenario.onActivity { assertTrue(view.rotateAndCommit(1)) }
            waitUntil("rows rewritten") { storedLabels() == listOf("F", "E", "D", "B", "C", "A") }
            assertTrue(UtilSettings(context).isLensFrozen(lensId))
            scenario.onActivity { assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(view)) }
        }
    }

    @Test fun smartFocusDoesNotReshuffleAfterARotation() {
        UtilSettings(context).saveSmartFocusBias(lensId, true)
        runBlocking { dao.insert(AppPersistent.defaults("com.test.ring.c", "Main", lensId).copy(orderNumber = 5, openCount = 999)) }
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            scenario.onActivity { assertTrue(view.rotateAndCommit(1)) }
            waitUntil("frozen") { UtilSettings(context).isLensFrozen(lensId) }
            scenario.onActivity { view.refreshSmartFocus() }
            scenario.onActivity {
                assertFalse(view.smartFocusAppliedForTest)
                assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(view))
            }
        }
    }

    @Test fun aFailedWriteRestoresTheViewAndNeverFreezes() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val before = labels(view)
            val missing = "never-created-lens"                                   // no LENS_WORKSPACE row: the write must fail
            scenario.onActivity { view.lensId = missing; view.setApps(ArrayList(fixture)); assertTrue(view.rotateAndCommit(1)) }
            waitUntil("view restored after the failed write") { labels(view) == before && view.ringPendingTokenForTest() == null }
            assertFalse(UtilSettings(context).isLensFrozen(missing))
            assertTrue(runBlocking { dao.getAllForLens(missing) }.isEmpty())
            scenario.onActivity { view.lensId = lensId }
        }
    }

    @Test fun anAnswerForAnotherLensAfterAPageRecycleNeverResolvesTheRecycledView() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val gate = java.util.concurrent.CountDownLatch(1)
            val parked = java.util.concurrent.CountDownLatch(1)
            AppPersistent.beforeOrderCommitForTest = { parked.countDown(); gate.await(WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) }
            try {
                scenario.onActivity { assertTrue(view.rotateAndCommit(1)) }
                assertTrue(parked.await(WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS))
                val recycledFor = "recycled-for-another-lens"
                scenario.onActivity { view.lensId = recycledFor }                // the pager reused the view for another lens
                assertNull("re-binding the view dropped the pending request", view.ringPendingTokenForTest())
                gate.countDown()
                waitUntil("the write for the original lens is confirmed and frozen") { UtilSettings(context).isLensFrozen(lensId) }
                waitUntil("rows rewritten") { storedLabels() == listOf("F", "E", "D", "B", "C", "A") }
                instrumentation.waitForIdleSync()
                assertFalse("the recycled view's lens must not be frozen by the other lens' answer", UtilSettings(context).isLensFrozen(recycledFor))
                assertNull(view.ringPendingTokenForTest())
                scenario.onActivity { view.lensId = lensId }
            } finally {
                AppPersistent.beforeOrderCommitForTest = null
                gate.countDown()
            }
        }
    }

    @Test fun pausingHomeCancelsAnInProgressPreview() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val before = labels(view)
            scenario.onActivity { view.toggleFocusLockAtForTest(view.width / 2f, view.height / 2f) }
            scenario.onActivity { activity -> activity.startRingPreviewForTest(1) }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)      // onPause
            scenario.onActivity { assertEquals(before, labels(view)); assertFalse(view.isRingRotatingForTest()) }
        }
    }

    private companion object { const val WAIT_MS = 8_000L; const val POLL_MS = 50L }
}
```

  Required seams introduced by this step (and nothing else): `LensView.startRingPreviewForTest(steps: Int)` — `@VisibleForTesting internal`, begins a `ROTATING` preview exactly as the gesture would (`beginRingPreview(); applies steps`) so a lifecycle test needs no MotionEvents; `ActHome` exposes it as `void startRingPreviewForTest(int steps) { if (lensViews != null) lensViews.startRingPreviewForTest(steps); }` marked `@androidx.annotation.VisibleForTesting`. Imports: the usual `ActHomeLensFreezeWidgetTest` set plus `PinnedZone`, `LauncherMode`, `LensManagerRoomSnapshot` (same package), `OnRingRotatedListener` not needed.
- [ ] **Step 2: Verify RED.** `./gradlew compileDevDebugAndroidTestKotlin` fails on `startRingPreviewForTest` (and, once added to `LensView`, the tests fail at run time because no listener is bound: `rotateAndCommit` returns false). Record both failures.
- [ ] **Step 3: GREEN — seam.** In `LensView.kt` add next to the other ring helpers:

```kotlin
    /** Public (not internal) because ActHome, a Java class, calls it; test-only. */
    @VisibleForTesting
    fun startRingPreviewForTest(steps: Int) {
        beginRingPreview()
        val base = mRingBaseApps ?: return
        mRingAppliedSteps = steps
        mApps = ArrayList(RingRotation.rotateByGroup(base, steps, ::ringGroupKey))
    }
```

- [ ] **Step 4: GREEN — holder listener.** In `LensViewHolder.bindLensView`, directly after `view.setOnAppMovedToLensListener(...)`:

```java
        // FISH-035: a finished ring rotation is written first; only a confirmed write freezes the lens and is adopted.
        view.setOnRingRotatedListener(request -> {
            final LensView source = view;
            AppPersistent.commitAppOrderBatch(request.getApps(), request.getLensId(), outcome -> {
                boolean committed = outcome == OrderCommitOutcome.COMMITTED;
                UtilSettings settings = utilSettings();
                if (committed && settings != null) {
                    settings.saveLensFrozen(request.getLensId(), true);
                    host.activity().sendBroadcast(new android.content.Intent(
                            host.activity(), com.mckimquyen.services.BroadcastReceivers.AppsEditedReceiver.class));
                }
                if (request.getLensId().equals(source.getLensId())) {
                    source.resolveRingCommit(request.getToken(), committed);
                }
                return Unit.INSTANCE;
            });
        });
```

  Add imports `com.mckimquyen.model.AppPersistent` and `com.mckimquyen.model.OrderCommitOutcome`. The `Unit.INSTANCE` return is required because the Kotlin callback is `(OrderCommitOutcome) -> Unit`.
- [ ] **Step 5: GREEN — pause.** In `ActHome.onPause()` after the FISH-029 line: `if (lensViews != null) lensViews.cancelRingRotationForPause();`. Add the `ActHome.startRingPreviewForTest` method from Step 1.
- [ ] **Step 6: Verify GREEN (device).** After the Task 6 device backup, run `ActHomeRingRotateWidgetTest` (7 tests) plus `ActHomeLensFreezeWidgetTest`, `ActHomeLensManagementWidgetTest`, `ActHomeQuickLensSwitchWidgetTest`, `ActHomeFocusLockLifecycleTest`, `ActHomeListModeToggleWidgetTest`. Every class `OK (N>0)`.
- [ ] **Step 7: Commit.** `git add app/src/main/java/com/mckimquyen/ui/home/LensViewHolder.java app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/main/java/com/mckimquyen/views/LensView.kt app/src/androidTest/java/com/mckimquyen/ui/ActHomeRingRotateWidgetTest.kt && git commit -m "feat(fish-035): confirm ring rotation write before freezing"` with the trailer.

---

## Task 5 — Two TalkBack host actions (owner-approved extension)

**Design.** Exactly two extra actions on the `LensView` host node, beside FISH-029's `Toggle focus lock`; never on app nodes. Both call `LensView.rotateAndCommit(±1)` (Task 3), i.e. the same preview-free, confirmed-commit path as the gesture's request: group-aware, needs no pin, refused (returns false) for < 2 apps, a net no-op, a pending commit, or no bound listener. The announcement of the new first app is made by `resolveRingCommit(token, true)` only after the write is confirmed.

**Files:** modify `LensAccessibilityHelper.kt`, `LensView.kt`, `LensAccessibilityHelperTest.kt`, `res/values/ids.xml`, `res/values*/strings.xml` ×17; extend `LensViewRingRotateIntegrationTest.kt`.

- [ ] **Step 1: RED (JVM).** In `LensAccessibilityHelperTest.kt`: add `private var rotateCalls = mutableListOf<Int>()` and `private var rotateResult = true`, reset both in `setup()`. Extend `createHelper` with `withRotate: Boolean = false` and pass `onRotateOrder = if (withRotate) { { steps -> rotateCalls += steps; rotateResult } } else null`. Stub three more strings in `setup()` exactly like the FISH-029 ones (`stringContext.getString(R.string.lens_rotate_forward_action)` → `ROTATE_FORWARD`, `lens_rotate_back_action` → `ROTATE_BACK`). Add to the companion `const val ROTATE_FORWARD = "Rotate order forward"` and `const val ROTATE_BACK = "Rotate order back"`. Tests:

```kotlin
    @Test
    fun `host node exposes exactly one forward and one back rotate action and app nodes do not`() {
        val helper = createHelper(withFocusLock = true, withRotate = true)
        val host = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        helper.testPopulateNodeForHost(host)
        val forward = com.mckimquyen.R.id.action_rotate_order_forward
        val back = com.mckimquyen.R.id.action_rotate_order_back
        assertEquals(1, host.actionList.count { it.id == forward })
        assertEquals(1, host.actionList.count { it.id == back })
        assertEquals(ROTATE_FORWARD, host.actionList.first { it.id == forward }.label)
        assertEquals(ROTATE_BACK, host.actionList.first { it.id == back }.label)
        assertEquals("focus lock action is unchanged", 1, host.actionList.count { it.id == com.mckimquyen.R.id.action_toggle_focus_lock })

        val app = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        helper.testPopulateNodeForVirtualView(0, app)
        assertFalse(app.actionList.any { it.id == forward || it.id == back })
    }

    @Test
    fun `forward performs plus one and back performs minus one`() {
        val helper = createHelper(withRotate = true)
        assertTrue(helper.testPerformActionForHost(com.mckimquyen.R.id.action_rotate_order_forward))
        assertTrue(helper.testPerformActionForHost(com.mckimquyen.R.id.action_rotate_order_back))
        assertEquals(listOf(1, -1), rotateCalls)
    }

    @Test
    fun `rotate actions report failure when the view refuses`() {
        rotateResult = false
        val helper = createHelper(withRotate = true)
        assertFalse(helper.testPerformActionForHost(com.mckimquyen.R.id.action_rotate_order_forward))
        assertFalse(helper.testPerformActionForHost(com.mckimquyen.R.id.action_rotate_order_back))
        assertEquals("the handler was asked once per action", listOf(1, -1), rotateCalls)
    }

    @Test
    fun `without a rotate handler there are no rotate actions and performing them fails`() {
        val helper = createHelper(withFocusLock = true, withRotate = false)
        val host = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        helper.testPopulateNodeForHost(host)
        assertFalse(host.actionList.any { it.id == com.mckimquyen.R.id.action_rotate_order_forward })
        assertFalse(host.actionList.any { it.id == com.mckimquyen.R.id.action_rotate_order_back })
        assertTrue("focus lock still present", host.actionList.any { it.id == com.mckimquyen.R.id.action_toggle_focus_lock })
        assertFalse(helper.testPerformActionForHost(com.mckimquyen.R.id.action_rotate_order_forward))
        assertTrue(rotateCalls.isEmpty())
    }

    @Test
    fun `rotate actions alone do not add a focus lock state description`() {
        val helper = createHelper(withFocusLock = false, withRotate = true)
        val host = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        helper.testPopulateNodeForHost(host)
        assertNull(host.stateDescription)
        assertFalse(host.actionList.any { it.id == com.mckimquyen.R.id.action_toggle_focus_lock })
    }
```

- [ ] **Step 2: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.a11y.LensAccessibilityHelperTest'` — compile failure only: `onRotateOrder`, `action_rotate_order_forward`, `lens_rotate_forward_action` unresolved. The four existing FISH-029 tests plus the original nine stay as they are.
- [ ] **Step 3: GREEN — ids and strings.** `ids.xml`: add `<item name="action_rotate_order_forward" type="id" />` and `<item name="action_rotate_order_back" type="id" />` beside `action_toggle_focus_lock`. In `res/values/strings.xml` and each of the 16 `res/values-<locale>/strings.xml` (`ar de es fr hi in it ja km ko lo pt ru th vi zh`; Indonesian is the `in` folder) add the three strings before `</resources>`, values from this table (AI-written; a limitation is recorded in the audit):

| locale | `lens_rotate_forward_action` | `lens_rotate_back_action` | `lens_rotate_announcement` |
|---|---|---|---|
| base | Rotate order forward | Rotate order back | Order rotated. First app: %1$s |
| ar | تدوير الترتيب للأمام | تدوير الترتيب للخلف | تم تدوير الترتيب. أول تطبيق: %1$s |
| de | Reihenfolge vorwärts drehen | Reihenfolge rückwärts drehen | Reihenfolge gedreht. Erste App: %1$s |
| es | Rotar orden hacia delante | Rotar orden hacia atrás | Orden rotado. Primera app: %1$s |
| fr | Faire pivoter l'ordre vers l'avant | Faire pivoter l'ordre vers l'arrière | Ordre pivoté. Première appli : %1$s |
| hi | क्रम आगे घुमाएँ | क्रम पीछे घुमाएँ | क्रम घुमाया गया। पहला ऐप: %1$s |
| in | Putar urutan maju | Putar urutan mundur | Urutan diputar. Aplikasi pertama: %1$s |
| it | Ruota ordine in avanti | Ruota ordine indietro | Ordine ruotato. Prima app: %1$s |
| ja | 順序を前へ回転 | 順序を後ろへ回転 | 順序を回転しました。先頭のアプリ: %1$s |
| km | បង្វិលលំដាប់ទៅមុខ | បង្វិលលំដាប់ថយក្រោយ | បានបង្វិលលំដាប់។ កម្មវិធីដំបូង៖ %1$s |
| ko | 순서 앞으로 회전 | 순서 뒤로 회전 | 순서를 회전했습니다. 첫 앱: %1$s |
| lo | ໝູນລຳດັບໄປໜ້າ | ໝູນລຳດັບຖອຍຫຼັງ | ໝູນລຳດັບແລ້ວ. ແອັບທຳອິດ: %1$s |
| pt | Girar ordem para frente | Girar ordem para trás | Ordem girada. Primeiro app: %1$s |
| ru | Повернуть порядок вперёд | Повернуть порядок назад | Порядок повёрнут. Первое приложение: %1$s |
| th | หมุนลำดับไปข้างหน้า | หมุนลำดับย้อนกลับ | หมุนลำดับแล้ว แอปแรก: %1$s |
| vi | Xoay thứ tự tới | Xoay thứ tự lui | Đã xoay thứ tự. Ứng dụng đầu tiên: %1$s |
| zh | 向前旋转顺序 | 向后旋转顺序 | 顺序已旋转。第一个应用：%1$s |

- [ ] **Step 4: GREEN — helper.** In `LensAccessibilityHelper.kt` add constructor parameter `private val onRotateOrder: ((steps: Int) -> Boolean)? = null` after `onFocusLockToggle` and replace the host overrides with:

```kotlin
    override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
        super.onPopulateNodeForHost(node)
        if (onFocusLockToggle != null) {
            node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                R.id.action_toggle_focus_lock, host.context.getString(R.string.lens_focus_lock_action)
            ))
            node.stateDescription = host.context.getString(
                if (focusLockPinnedProvider()) R.string.lens_focus_locked else R.string.lens_focus_unlocked
            )
        }
        if (onRotateOrder != null) {
            node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                R.id.action_rotate_order_forward, host.context.getString(R.string.lens_rotate_forward_action)
            ))
            node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                R.id.action_rotate_order_back, host.context.getString(R.string.lens_rotate_back_action)
            ))
        }
    }

    override fun performAccessibilityAction(host: android.view.View, action: Int, args: Bundle?): Boolean = when {
        action == R.id.action_toggle_focus_lock && onFocusLockToggle != null -> onFocusLockToggle.invoke()
        action == R.id.action_rotate_order_forward && onRotateOrder != null -> onRotateOrder.invoke(RotateStep.FORWARD)
        action == R.id.action_rotate_order_back && onRotateOrder != null -> onRotateOrder.invoke(RotateStep.BACK)
        else -> super.performAccessibilityAction(host, action, args)
    }

    private object RotateStep {
        const val FORWARD = 1
        const val BACK = -1
    }
```

  (`LensView` is wired in Step 7, after its device test has failed.)
- [ ] **Step 5: Verify GREEN (JVM).** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.a11y.LensAccessibilityHelperTest' --tests 'com.mckimquyen.a11y.AllStringsTranslationTest' --tests 'com.mckimquyen.a11y.LensStringTranslationTest'`. `LensAccessibilityHelperTest` now has 18 tests (13 existing + 5 new); report the number the runner prints. `AllStringsTranslationTest` enforces that every key exists in all 17 locales and keeps `%1$s` placeholders.
- [ ] **Step 6: RED/GREEN (device, `LensViewRingRotateIntegrationTest`).** Add:

```kotlin
    @Test fun theHostNodeExposesThreeActionsAndForwardThenBackRoundTripsThroughTheHost() = withView { view ->
        val base = labels(view.displayedApps)
        val requests = mutableListOf<RingRotationRequest>()
        listenerInto(view, requests)
        val node = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.obtain()
        onMain { androidx.core.view.ViewCompat.onInitializeAccessibilityNodeInfo(view, node) }
        val forward = com.mckimquyen.R.id.action_rotate_order_forward
        val back = com.mckimquyen.R.id.action_rotate_order_back
        assertEquals(1, node.actionList.count { it.id == forward })
        assertEquals(1, node.actionList.count { it.id == back })
        assertEquals(1, node.actionList.count { it.id == com.mckimquyen.R.id.action_toggle_focus_lock })
        assertTrue(node.actionList.first { it.id == forward }.label?.isNotBlank() == true)
        assertTrue(node.actionList.first { it.id == back }.label?.isNotBlank() == true)

        var ok = false
        onMain { ok = view.performAccessibilityAction(forward, null) }
        assertTrue(ok)
        assertEquals(listOf("F", "E", "D", "B", "C", "A"), labels(requests.single().apps))
        onMain { view.resolveRingCommit(requests.single().token, committed = true) }
        onMain { ok = view.performAccessibilityAction(back, null) }
        assertTrue(ok)
        assertEquals("back undoes forward", base, labels(requests.last().apps))
    }

    @Test fun hostRotateIsRefusedForASingleAppAndWhileACommitIsPending() {
        withView(apps = listOf(app("solo", PinnedZone.NONE, false))) { view ->
            val requests = mutableListOf<RingRotationRequest>()
            listenerInto(view, requests)
            var ok = true
            onMain { ok = view.performAccessibilityAction(com.mckimquyen.R.id.action_rotate_order_forward, null) }
            assertFalse(ok)
            assertTrue(requests.isEmpty())
        }
        withView { view ->
            val requests = mutableListOf<RingRotationRequest>()
            listenerInto(view, requests)
            var first = false
            var second = true
            onMain {
                first = view.performAccessibilityAction(com.mckimquyen.R.id.action_rotate_order_forward, null)
                second = view.performAccessibilityAction(com.mckimquyen.R.id.action_rotate_order_forward, null)
            }
            assertTrue(first)
            assertFalse("the second request waits for the first answer", second)
            assertEquals(1, requests.size)
        }
    }
```

- [ ] **Step 7: Verify RED, then GREEN (device).** Compile and run the two new tests on Pixel after the Task 6 backup: both must FAIL now (`performAccessibilityAction` returns false because `LensView` passes no `onRotateOrder`, and the host node has only the focus-lock action). Then wire `LensView.installAccessibilityHelper` to pass `onRotateOrder = { steps -> rotateAndCommit(steps) }` after `onFocusLockToggle`, rebuild and rerun: both pass, together with `LensViewAccessibilityWidgetTest` and `AccessibilityActionsIntegrationTest` (no regression in the existing virtual-node actions).
- [ ] **Step 8: Commit.** `git add app/src/main/java/com/mckimquyen/views/LensAccessibilityHelper.kt app/src/main/java/com/mckimquyen/views/LensView.kt app/src/test/java/com/mckimquyen/a11y/LensAccessibilityHelperTest.kt app/src/androidTest/java/com/mckimquyen/views/LensViewRingRotateIntegrationTest.kt app/src/main/res/values/ids.xml app/src/main/res/values*/strings.xml && git commit -m "feat(fish-035): add rotate-order accessibility actions"` with the trailer.

---

## Task 6 — Device procedure: backup, regression, smoke, restore (TECNO Pixel 7 Pro only)

Everything uses `adb -s 2B051FDH3006MU`. `adb devices -l` currently lists a Pixel `2B051FDH3006MU` too: never address it, and never use bare `adb`, `./gradlew connected*` or `installDevDebug`. Each command below is run on its own and its output file is read afterwards (loop output has been truncated before; a truncated display does not mean the command stopped — check the file).

- [ ] **Step 1: Lock the device and create the run directory.**

```bash
S=2B051FDH3006MU
adb devices -l | grep "$S"                               # must print one line ending in model:TECNO_Pixel 7 Pro
D=/tmp/f035-run-$(date +%Y%m%d-%H%M%S); mkdir -p "$D"; echo "$D" > /tmp/f035-dir
```

- [ ] **Step 2: Back up before the first install (APKs, data, hashes, system state).** Fresh backup every run; never reuse an older directory as "the baseline".

```bash
D=$(cat /tmp/f035-dir); S=2B051FDH3006MU
MAIN=$(adb -s $S shell pm path com.mckimquyen.lenslauncher | sed 's/package://' | tr -d '\r')
TEST=$(adb -s $S shell pm path com.mckimquyen.lenslauncher.test | sed 's/package://' | tr -d '\r')
adb -s $S pull "$MAIN" "$D/before.apk"
adb -s $S pull "$TEST" "$D/before-test.apk"
adb -s $S exec-out run-as com.mckimquyen.lenslauncher tar cf - databases shared_prefs > "$D/before-data.tar"
{ adb -s $S shell settings get system font_scale; adb -s $S shell settings get global animator_duration_scale; \
  adb -s $S shell settings get global transition_animation_scale; adb -s $S shell settings get global window_animation_scale; \
  adb -s $S shell cmd locale get-app-locales com.mckimquyen.lenslauncher; \
  adb -s $S shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME; } > "$D/device-state.txt"
mkdir "$D/x" && tar -xf "$D/before-data.tar" -C "$D/x"
( cd "$D/x" && find . -type f | sort | xargs shasum -a 256 ) > "$D/before-hashes.txt"
shasum -a 256 "$D/before.apk" "$D/before-test.apk" > "$D/apk-hashes.txt"
wc -l "$D/before-hashes.txt" "$D/device-state.txt"; cat "$D/apk-hashes.txt"
```

  Expected: 18 data files; font `1.15`, animator/transition `1.0`, window `0.0`, locale `[en]`, HOME `com.transsion.hilauncher/…QuickstepLauncher`; if the APK hashes are not `b1c0b81d…`/`14cfa462…` the device is not at the FISH-034 baseline — record the new hashes and continue (they are the baseline for **this** run).
- [ ] **Step 3: Host gate (no device).** `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` (baseline 1537 unit tests, lint 0 errors / 15 warnings), then `scripts/check-secrets.sh` and `git diff --check`; record counts, exit codes and `git rev-parse --short HEAD`.
- [ ] **Step 4: Install and run the device classes.** Install both APKs from `app/build/outputs/apk/…/dev/debug/` with `adb -s $S install -r -d …`, run `adb -s $S shell pm clear com.mckimquyen.lenslauncher.test`, then run each class with `adb -s $S shell am instrument -w -e class <FQCN> com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner > "$D/run-<Class>.txt"` and read the file. Classes (FQCNs under `com.mckimquyen.…`): `views.LensViewRingRotateIntegrationTest`, `ui.ActHomeRingRotateWidgetTest`, `a11y.LensViewAccessibilityWidgetTest`, `a11y.AccessibilityActionsIntegrationTest`, and the regression set `views.LensViewFocusLockIntegrationTest`, `views.LensViewPinchIntegrationTest`, `views.LensViewPagerGestureIntegrationTest`, `views.LensViewFocusRingIntegrationTest`, `views.LensViewReattachIntegrationTest`, `views.LensViewWidgetTest`, `views.LensViewQuickActionsIntegrationTest`, `views.LensViewVignetteWidgetTest`, `views.LensViewTiltIntegrationTest`, `ui.ActHomeLensFreezeWidgetTest`, `ui.ActHomeLensManagementWidgetTest`, `ui.ActHomeQuickLensSwitchWidgetTest`, `ui.ActHomeFocusLockLifecycleTest`, `ui.ActHomeListModeToggleWidgetTest`. A stale test APK prints `OK (0 tests)`: that is a failure — rebuild `assembleDevDebugAndroidTest` and reinstall.
- [ ] **Step 5: Smoke — what adb can and cannot prove.** Launch Home, take `s0-idle.png`; pin focus with `adb -s $S shell "input swipe 190 1105 190 1105 80; sleep 0.1; input swipe 190 1105 190 1105 80"`; `s1-pinned.png`. A faithful one-finger arc needs a continuous multi-point pointer stream: `input swipe` is a straight line, chained swipes are separate fingers, and `sendevent` needs root. Therefore: (a) check whether `adb shell input motionevent` is available (`adb -s $S shell input 2>&1 | grep -i motionevent`); if yes, script one continuous gesture `motionevent DOWN x y`, then ≥ 14 `motionevent MOVE` points around a 300 px circle at ~8 ms spacing, then `UP`, and compare `s0`/`s2` icon rows; if no, write "adb cannot synthesize a continuous arc on this device" in the audit and rely on the instrumented MotionEvent tests — do **not** claim a rotation smoke. (b) Either way run: a straight `input swipe` drag while pinned still pans and returns to idle (0 changed pixels after release); Home → Settings → back clears the pin; `adb -s $S shell run-as com.mckimquyen.lenslauncher grep lens_frozen shared_prefs/*.xml` shows no frozen flag unless a rotation was committed. Stop immediately if `AdActivity` appears (R4) and wait for the owner's `done`; never tap "Start (Ads may appear)".
- [ ] **Step 6: Restore and prove it.**

```bash
D=$(cat /tmp/f035-dir); S=2B051FDH3006MU
adb -s $S shell am force-stop com.mckimquyen.lenslauncher
adb -s $S install -r -d "$D/before.apk"
adb -s $S install -r -d "$D/before-test.apk"
adb -s $S shell am force-stop com.mckimquyen.lenslauncher
adb -s $S shell run-as com.mckimquyen.lenslauncher sh -c 'rm -rf databases shared_prefs'
adb -s $S exec-out run-as com.mckimquyen.lenslauncher tar xf - < "$D/before-data.tar"   # if this form fails, push the tar to /data/local/tmp and extract with run-as
adb -s $S shell am force-stop com.mckimquyen.lenslauncher
adb -s $S exec-out run-as com.mckimquyen.lenslauncher tar cf - databases shared_prefs > "$D/restored-data.tar"
mkdir "$D/y" && tar -xf "$D/restored-data.tar" -C "$D/y"
( cd "$D/y" && find . -type f | sort | xargs shasum -a 256 ) > "$D/restored-hashes.txt"
diff "$D/before-hashes.txt" "$D/restored-hashes.txt" && echo DATA_HASHES_IDENTICAL
adb -s $S pull "$(adb -s $S shell pm path com.mckimquyen.lenslauncher | sed 's/package://' | tr -d '\r')" "$D/after.apk"
adb -s $S pull "$(adb -s $S shell pm path com.mckimquyen.lenslauncher.test | sed 's/package://' | tr -d '\r')" "$D/after-test.apk"
shasum -a 256 "$D/after.apk" "$D/after-test.apk" | diff - "$D/apk-hashes.txt" | sed 's#after#before#' | head
```

  The restore uses the same user id: `run-as` extraction keeps ownership because the app uid owns the process. Compare `device-state.txt` with a fresh capture of the same six values. **Stop and disclose** if any hash, setting or the HOME resolver differs; do not continue to the audit with an inexact restore. Do not clear device logs or caches the OS created itself.

---

## Task 7 — Audit, tracker, closure

**Files:** create `doc/task/AUDIT_FISH-035_2026-11-06.md`; modify `doc/feature.md`, `doc/task/README.md`, the story file.

- [ ] **Step 1: Audit document.** Sections: scope (what was built, the three owner decisions: classification wait, groups preserved, confirmed commit), evidence table (JVM total and new tests per class; lint; build; secrets; every device class with `OK (N)`; restore hashes), defects found by RED tests (list each with the failing assertion), smoke result (arc smoke done or the explicit adb limitation), and limitations: thresholds tuned on synthetic input only and not on real fingers; pinned pan starts up to 300 ms late; 17-locale strings are AI-written; owner has not hand-tested; TalkBack not run with a real screen reader; rotation is per group so apps never cross pinned/favorite boundaries; `setAppOrderBatch` itself still has no outcome (other callers unchanged); a process kill between the DB write and the frozen preference leaves a rotated but unfrozen lens until the next Smart Focus pass (accepted: order is durable, only auto-sort protection is delayed).
- [ ] **Step 2: Score honestly** out of 10. The story moves `inprogress/` → `done/` only if the score is > 9.0, every device class showed `OK (N>0)` and the restore was proven identical; otherwise it stays `inprogress/` and the audit lists what is missing. The owner decides closure.
- [ ] **Step 3: Trackers.** `doc/feature.md`: move the FISH-035 bullet from "📋 Picked — decision" to the done/in-progress state with the audit link, counts and limitations. `doc/task/README.md`: update the `FISH-035` row (status, audit score, "chưa push"). R2 requires this immediately after the owner's decision.
- [ ] **Step 4: Commit** the audit and tracker changes as their own commit (`docs(fish-035): audit and close story` or `docs(fish-035): record audit, story stays inprogress`) with the trailer. Never stage `.idea/`. Do not push.

---

## Self-review

**Spec coverage** (`docs/superpowers/specs/2026-11-06-fish-035-rotate-ring-reorder-design.md`, amended for groups):
- Pinned-only trigger → Task 3 `canStartRing`, tests `theSameArcWithoutAPinIsAnOrdinaryPan…`.
- Rotation vs pan arbitration → Task 1 tracker (sweep + radius band + heading turn + window), Task 3 `WAITING/PAN_DECIDED`, tests for tangent/radial/slow/inside-radius.
- Rotate within groups, preview == reload → Task 1 `rotateByGroup` + real-`UtilAppSorter` test; Task 3 preview uses the same function.
- Commit once per gesture; no write for a net no-op → Task 3 `finishRingRotation` (`sameOrder`), tests `aFullTurn…`, `reversing…`, `whenEveryAppIsInItsOwnGroup…`.
- Frozen only after a confirmed write; already-frozen still rotates → Task 2 outcome API, Task 4 holder + widget tests.
- Failure/superseded/stale/recycled-page handling → Task 2 (`FAILED`/`SUPERSEDED`), Task 3 (`resolveRingCommit` token, `setApps`/`lensId` drop), Task 4 tests.
- Special gestures keep precedence (edge, search, pinch, long press, tap) → Task 3 Step 8 wiring and tests.
- Cleanup on cancel/pause/detach/second finger → Task 3, Task 4 pause hook.
- Two TalkBack actions, owner-approved, nothing else → Task 5.
- Smoke honesty, backup/restore → Task 6.

**Placeholder scan:** no `TBD`/`TODO`/comment-only test bodies; every test has assertions. Two deliberate "adjust, never weaken" notes (edge-swipe geometry, tracker constants) name exactly what may change.

**Type consistency:** `RingRotation.{rotate, rotateByGroup, groupKeyOf, wholeSteps, shortestAngleDelta, angleOf, Decision, MIN_RADIUS_DP, STEP_RADIANS, SWEEP_THRESHOLD_RAD, MAX_WINDOW_MS, MAX_SAMPLES, MIN_SAMPLES, RADIUS_STABILITY_FRACTION, TURN_FRACTION}`; `RingGestureTracker.{begin, onMove, reset, sweepRadians, decision}`; `OrderCommitOutcome.{COMMITTED, FAILED, SUPERSEDED}` (top level); `AppPersistent.{commitAppOrderBatch, beforeOrderCommitForTest}`; `RingRotationRequest(token, lensId, apps)`; `OnRingRotatedListener.onRingRotated(request)`; `LensView.{onRingRotatedListener, resolveRingCommit, rotateAndCommit, cancelRingRotationForPause, isRingRotatingForTest, ringPendingTokenForTest, startRingPreviewForTest}`; `LensAccessibilityHelper(onRotateOrder)`; ids `action_rotate_order_forward/back`; strings `lens_rotate_forward_action/back_action/announcement`.

**Known risks the executor must watch:** (1) the per-gesture unit constants are provisional and only synthetic-tested; (2) `LensView` becomes larger — if the ring code grows past what reads well, extract `RingPreview` into its own file in a follow-up, not in this story; (3) `commitAppOrderBatch` posts the singleton update on main after the DB commit, so a refresh pipeline run in the gap can briefly see old `orderNumber`s — the post-commit `AppsEditedReceiver` broadcast re-sorts, and the view already holds the adopted order, so there is no visible flip; (4) no push without the owner's request.

**Execution handoff** (after the plan commit): ask the owner with `AskUserQuestion` — subagent-driven (recommended by the skill) vs inline (used for FISH-029/034) — and do not start Task 1 before they answer.
