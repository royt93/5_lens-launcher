#!/bin/bash
# usage: run-ad-guard-probe.sh <serial> <out-prefix>
# ONE interstitial request (ActSettingsAdGuardProbeTest, opt-in) with a watcher on the focused window, once per second.
#  - com.google.android.gms.ads.AdActivity (AdMob): send KEYCODE_BACK, log, keep watching; if it is still in front after 3 BACKs, STOP.
#  - any other ad window (com.applovin.*): send ONE KEYCODE_BACK, log it, then STOP the run immediately (owner choice: AdMob only).
# Never taps. The run is a single attempt: no retry.
S=$1; OUT=$2
P=com.mckimquyen.lenslauncher; A() { adb -s $S "$@"; }
RUNNER=$P.test/androidx.test.runner.AndroidJUnitRunner
focus() { A shell dumpsys window | grep -m1 mCurrentFocus | grep -o '[A-Za-z0-9_.$]*/[A-Za-z0-9_.$]*' | head -1; }
LOG=$OUT-guard.txt; : > $LOG; rm -f $OUT.stop
A shell am force-stop $P; A shell input keyevent KEYCODE_HOME; sleep 2; A logcat -c
t0=$(date +%s)
( same=0; last=""
  while [ ! -f $OUT.stop ]; do
    f=$(focus); now=$(( $(date +%s) - t0 ))
    if echo "$f" | grep -qE "com\.google\.android\.gms\.ads\.AdActivity"; then
      if [ "$f" = "$last" ]; then same=$((same+1)); else same=1; last=$f; fi
      echo "t+${now}s ADMOB $f BACK #$same" >> $LOG
      if [ $same -gt 3 ]; then echo "t+${now}s ADMOB-STUCK still in front after 3 BACKs: STOP" >> $LOG; touch $OUT.stop; A shell am force-stop $P.test; break; fi
      A shell input keyevent KEYCODE_BACK
    elif echo "$f" | grep -qE "com\.applovin\.|AppLovin"; then
      echo "t+${now}s NON-ADMOB AD WINDOW $f: one BACK then STOP" >> $LOG
      A shell input keyevent KEYCODE_BACK
      touch $OUT.stop; A shell am force-stop $P.test; break
    else last=""; same=0; echo "t+${now}s focus=$f" >> $LOG.focus; fi
    sleep 1
  done ) &
GP=$!
A shell am instrument -w --user 0 -e adProbe true -e class com.mckimquyen.ui.ActSettingsAdGuardProbeTest $RUNNER > $OUT-raw.txt 2>&1
touch $OUT.stop; wait $GP
echo "instrument result: $(grep -E '^OK|^Tests run|FAILURES' $OUT-raw.txt | head -2 | tr '\n' ' ')"
echo "--- guard log"; cat $LOG
echo "--- focus samples (distinct)"; [ -f $LOG.focus ] && sed -E 's/^t\+[0-9]+s //' $LOG.focus | sort | uniq -c | sort -rn | head -6
echo "--- app log"; A logcat -d -s F278ADPROBE:I | cut -c30-
echo "--- ad windows seen by dumpsys right now"; A shell dumpsys window | grep -c -E "gms\.ads\.AdActivity|AppLovinFullscreenActivity"
A shell input keyevent KEYCODE_HOME
