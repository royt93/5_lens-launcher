#!/bin/bash
# like run-display-tests.sh but passes the expected quick-actions row visibility per profile
S=$1; OUT=$2; P=com.mckimquyen.lenslauncher; A() { adb -s $S "$@"; }
RUNNER=$P.test/androidx.test.runner.AndroidJUnitRunner; CLS=com.mckimquyen.ui.ActHomeSearchLowColumnWidgetTest
cur() { A shell dumpsys window displays | grep -m1 -o 'cur=[0-9x]*' | cut -d= -f2; }
one() { # profile size density rotation expectedOrientation wantedcur expectRow
  A shell wm size $2 >/dev/null; A shell wm density $3 >/dev/null
  A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 0; A shell input keyevent KEYCODE_HOME; sleep 3
  A shell settings put system user_rotation $4
  got=""; for i in 1 2 3 4 5 6 7 8; do sleep 1; got=$(cur); [ "$got" = "$6" ] && break; done
  A logcat -c
  A shell am instrument -w --user 0 -e displayExpectOrientation $5 -e lowColumnExpectRow $7 -e class $CLS $RUNNER > $OUT-$1-raw.txt 2>&1
  res=$(grep -E "^OK|^Tests run" $OUT-$1-raw.txt | head -1); fail=$(grep -m1 -E "AssertionError|ComparisonFailure" $OUT-$1-raw.txt | cut -c1-200)
  echo "$1 | expect row $7 | display $got | $res $fail"
  A logcat -d -s F297LOW:I | cut -c30- | cut -c1-300 | sed 's/^/      /'
}
one phone-portrait   1440x3120 560 0 portrait  1440x3120 shown
one phone-landscape  1440x3120 560 1 landscape 3120x1440 hidden
one tablet-portrait  1600x2560 320 0 portrait  1600x2560 shown
one tablet-landscape 1600x2560 320 1 landscape 2560x1600 shown
A shell wm size reset; A shell wm density reset; A shell settings put system user_rotation 0; A shell settings put system accelerometer_rotation 1; sleep 3
echo "display after: $(A shell wm size | tr '\n' ' ') accel=$(A shell settings get system accelerometer_rotation) rot=$(A shell settings get system user_rotation)"
