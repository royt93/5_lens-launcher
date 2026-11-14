#!/bin/bash
# One `am instrument` per test method. The Pixel launcher takes the foreground after every test and, on a phone-size screen,
# sends the display back to portrait, so rotation has to be re-applied (with the launcher in front) before EACH test.
S=$1; OUT=$2; METHODS=$3
P=com.mckimquyen.lenslauncher; A() { adb -s $S "$@"; }
RUNNER=$P.test/androidx.test.runner.AndroidJUnitRunner; CLS=com.mckimquyen.ui.ActHomeT9KeypadLandscapeBehaviorTest
cur() { A shell dumpsys window displays | grep -m1 -o 'cur=[0-9x]*' | cut -d= -f2; }
one() { # profile size density rotation expected wantedcur method
  A shell wm size $2 >/dev/null; A shell wm density $3 >/dev/null
  A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 0; A shell input keyevent KEYCODE_HOME; sleep 3
  A shell settings put system user_rotation $4
  got=""; for i in 1 2 3 4 5 6 7 8; do sleep 1; got=$(cur); [ "$got" = "$6" ] && break; done
  A logcat -c
  A shell am instrument -w --user 0 -e t9Landscape true -e t9ExpectOrientation $5 -e class $CLS#$7 $RUNNER > $OUT-$1-$7-raw.txt 2>&1
  res=$(grep -E "^OK|^Tests run" $OUT-$1-$7-raw.txt | head -1)
  seen=$(A logcat -d -s F211T9:I | cut -c30- | grep -o 'orientation check.*' | sed 's/ decor=.*//' | head -1)
  jank=$(A logcat -d -s F211T9:I | cut -c30- | grep -o 'jank:.*' | sed 's/typing rounds //' | head -1)
  echo "$1 | $7 | display before $got | $res | $seen | $jank"
}
for m in $(cat $METHODS); do one phone-landscape 1440x3120 560 1 landscape 3120x1440 $m; done
A shell wm size reset; A shell wm density reset; A shell settings put system user_rotation 0; A shell settings put system accelerometer_rotation 1; sleep 3
echo "display after: $(A shell wm size | tr '\n' ' ') accel=$(A shell settings get system accelerometer_rotation) rot=$(A shell settings get system user_rotation)"
