#!/bin/bash
# usage: run-display-tests.sh <serial> <out-prefix> <class> <expect-arg-name> <log-tag> <methods-file>
# One `am instrument` per test method and per display profile. Rotation is applied HERE with the Pixel launcher in front, because an
# instrumented run sends the display back to portrait when it ends (measured: only the first test after a rotation sees landscape).
S=$1; OUT=$2; CLS=$3; ARG=$4; TAG=$5; METHODS=$6
P=com.mckimquyen.lenslauncher; A() { adb -s $S "$@"; }
RUNNER=$P.test/androidx.test.runner.AndroidJUnitRunner
cur() { A shell dumpsys window displays | grep -m1 -o 'cur=[0-9x]*' | cut -d= -f2; }
one() { # profile size density rotation expected wantedcur method
  A shell wm size $2 >/dev/null; A shell wm density $3 >/dev/null
  A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 0; A shell input keyevent KEYCODE_HOME; sleep 3
  A shell settings put system user_rotation $4
  got=""; for i in 1 2 3 4 5 6 7 8; do sleep 1; got=$(cur); [ "$got" = "$6" ] && break; done
  # No skip gate on the display read: it showed 1440x3120 before launch in runs where the Activity then really was 3120x1440
  # (f242). The test checks the orientation the Activity itself sees and fails loudly when it is wrong.
  A logcat -c
  A shell am instrument -w --user 0 -e $ARG $5 -e class $CLS#$7 $RUNNER > $OUT-$1-$7-raw.txt 2>&1
  res=$(grep -E "^OK|^Tests run" $OUT-$1-$7-raw.txt | head -1)
  fail=$(grep -m1 -E "AssertionError|ComparisonFailure" $OUT-$1-$7-raw.txt | cut -c1-170)
  echo "$1 | $7 | display $got | $res $fail"
  A logcat -d -s $TAG:I | cut -c30- | cut -c1-330 | sed 's/^/      /'
}
for m in $(cat $METHODS); do
  one phone-portrait   1440x3120 560 0 portrait  1440x3120 $m
  one phone-landscape  1440x3120 560 1 landscape 3120x1440 $m
  one tablet-portrait  1600x2560 320 0 portrait  1600x2560 $m
  one tablet-landscape 1600x2560 320 1 landscape 2560x1600 $m
done
A shell wm size reset; A shell wm density reset; A shell settings put system user_rotation 0; A shell settings put system accelerometer_rotation 1; sleep 3
echo "display after: $(A shell wm size | tr '\n' ' ') accel=$(A shell settings get system accelerometer_rotation) rot=$(A shell settings get system user_rotation)"
