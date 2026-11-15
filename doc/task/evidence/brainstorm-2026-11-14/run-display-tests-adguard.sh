#!/bin/bash
# usage: run-display-tests-adguard.sh <serial> <out-prefix> <class> <expect-arg-name> <log-tag> <methods-file>
# Same as run-display-tests.sh, plus an ad guard for screens that start an ad flow. While a test runs, a watcher looks at the focused
# window every second: when it is an ad window (com.google.android.gms.ads / AdActivity) it sends KEYCODE_BACK and logs it. It never taps
# ad content. If the SAME ad window is still in front after 3 BACKs, the run is STOPPED (R4): the instrumentation is killed, a marker is
# printed, and nothing else is run until the owner clears it.
S=$1; OUT=$2; CLS=$3; ARG=$4; TAG=$5; METHODS=$6
P=com.mckimquyen.lenslauncher; A() { adb -s $S "$@"; }
RUNNER=$P.test/androidx.test.runner.AndroidJUnitRunner
cur() { A shell dumpsys window displays | grep -m1 -o 'cur=[0-9x]*' | cut -d= -f2; }
focus() { A shell dumpsys window | grep -m1 mCurrentFocus | grep -o '[A-Za-z0-9_.$]*/[A-Za-z0-9_.$]*' | head -1; }
STOPPED=0
guard() { # logfile; runs until $OUT.guardstop exists
  same=0; last=""
  while [ ! -f $OUT.guardstop ]; do
    f=$(focus)
    if echo "$f" | grep -qiE "gms\.ads|AdActivity|com\.google\.android\.gms"; then
      if [ "$f" = "$last" ]; then same=$((same+1)); else same=1; last=$f; fi
      echo "$(date +%H:%M:%S) AD WINDOW $f (BACK #$same)" >> $1
      if [ $same -gt 3 ]; then echo "$(date +%H:%M:%S) AD-STUCK $f: BACK did not close it, stopping (R4)" >> $1; touch $OUT.adstuck; A shell am force-stop $P.test; break; fi
      A shell input keyevent KEYCODE_BACK
    else last=""; same=0; fi
    sleep 1
  done
}
one() { # profile size density rotation expected wantedcur method
  [ -f $OUT.adstuck ] && { echo "$1 | $7 | NOT RUN: a previous ad could not be closed (R4)"; return; }
  A shell wm size $2 >/dev/null; A shell wm density $3 >/dev/null
  A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 0; A shell input keyevent KEYCODE_HOME; sleep 3
  A shell settings put system user_rotation $4
  got=""; for i in 1 2 3 4 5 6 7 8; do sleep 1; got=$(cur); [ "$got" = "$6" ] && break; done
  A logcat -c
  rm -f $OUT.guardstop; guard $OUT-$1-$7-guard.txt & GP=$!
  A shell am instrument -w --user 0 -e $ARG $5 -e class $CLS#$7 $RUNNER > $OUT-$1-$7-raw.txt 2>&1
  touch $OUT.guardstop; wait $GP
  res=$(grep -E "^OK|^Tests run" $OUT-$1-$7-raw.txt | head -1)
  fail=$(grep -m1 -E "AssertionError|ComparisonFailure" $OUT-$1-$7-raw.txt | cut -c1-200)
  echo "$1 | $7 | display before $got | $res $fail"
  [ -s $OUT-$1-$7-guard.txt ] && sed 's/^/      GUARD /' $OUT-$1-$7-guard.txt
  A logcat -d -s $TAG:I | cut -c30- | cut -c1-420 | sed 's/^/      /'
}
rm -f $OUT.adstuck
for m in $(cat $METHODS); do
  one phone-portrait   1440x3120 560 0 portrait  1440x3120 $m
  one phone-landscape  1440x3120 560 1 landscape 3120x1440 $m
  one tablet-portrait  1600x2560 320 0 portrait  1600x2560 $m
  one tablet-landscape 1600x2560 320 1 landscape 2560x1600 $m
done
A shell wm size reset; A shell wm density reset; A shell settings put system user_rotation 0; A shell settings put system accelerometer_rotation 1; sleep 3
echo "display after: $(A shell wm size | tr '\n' ' ') accel=$(A shell settings get system accelerometer_rotation) rot=$(A shell settings get system user_rotation)"
[ -f $OUT.adstuck ] && echo "AD-STUCK MARKER SET: the run was stopped because an ad window could not be closed with BACK"
