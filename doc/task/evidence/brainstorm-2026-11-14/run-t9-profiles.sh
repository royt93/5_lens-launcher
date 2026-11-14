#!/bin/bash
# Runs ActHomeT9KeypadLandscapeBehaviorTest on one device in four display profiles. Rotation is done HERE, with ActHome in front,
# because it does not take effect from inside the test or right after an instrumented run (measured on a Pixel 7 Pro).
S=$1; OUT=$2; BASE=$3
P=com.mckimquyen.lenslauncher; A() { adb -s $S "$@"; }
RUNNER=$P.test/androidx.test.runner.AndroidJUnitRunner; CLS=com.mckimquyen.ui.ActHomeT9KeypadLandscapeBehaviorTest
cur() { A shell dumpsys window displays | grep -m1 -o 'cur=[0-9x]*' | cut -d= -f2; }
profile() { # label size density rotation expected wantedcur
  A shell wm size $2 >/dev/null; A shell wm density $3 >/dev/null
  A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 0; sleep 3
  A shell am start --user 0 -n $P/com.mckimquyen.ui.ActHome >/dev/null 2>&1; sleep 3
  A shell settings put system user_rotation $4
  got=""; for i in 1 2 3 4 5 6 7 8 9 10; do sleep 1; got=$(cur); [ "$got" = "$6" ] && break; done
  echo "=== $1 : size $2 density $3 rotation $4 expecting $5; display now $got (wanted $6)"
  if [ "$got" != "$6" ]; then echo "   SKIPPED: the display did not reach $6, not running the test"; return; fi
  A logcat -c
  A shell am instrument -w --user 0 -e t9Landscape true -e t9ExpectOrientation $5 -e class $CLS $RUNNER > $OUT-$1-raw.txt 2>&1
  grep -E "^OK|FAILURES|Tests run|^[0-9]+\) |AssertionError|ComparisonFailure" $OUT-$1-raw.txt | sed 's/^[ \t]*//' | sort | uniq -c | cut -c1-200 | head -6
  A logcat -d -s F211T9:I | cut -c30- | cut -c1-210
}
profile phone-portrait   1440x3120 560 0 portrait  1440x3120
profile phone-landscape  1440x3120 560 1 landscape 3120x1440
profile tablet-portrait  1600x2560 320 0 portrait  1600x2560
profile tablet-landscape 1600x2560 320 1 landscape 2560x1600
A shell wm size reset; A shell wm density reset; A shell settings put system user_rotation 0; A shell settings put system accelerometer_rotation 1; sleep 3
echo "display after: $(A shell wm size | tr '\n' ' ') accel=$(A shell settings get system accelerometer_rotation) rot=$(A shell settings get system user_rotation)"
