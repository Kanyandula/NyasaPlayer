#!/bin/zsh
# T29: does the car app's offline banner follow the system through ten airplane-mode round trips?
# Needs the AAOS_AOSP_33_userdebug emulator as emulator-5554 with the oem debug build installed for
# user 10, signed in, and playback stopped. Waits for the system to reach each state, settles 5 s, and
# counts a transition only when two system reads agree. Prints each transition and a total.
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}
# The phone pass overrides these: NT_USER=0 NT_LAUNCH="monkey -p com.example.nyasaplayer -c android.intent.category.LAUNCHER 1"
NT_USER=${NT_USER:-10}
NT_LAUNCH=${NT_LAUNCH:-"am start --user $NT_USER -n com.example.nyasaplayer/com.example.nyasaplayer.auto.ui.AutomotiveActivity"}
banner(){ adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; if adb shell ls /sdcard/ui.xml >/dev/null 2>&1; then adb shell cat /sdcard/ui.xml | grep -c 'No internet connection'; else echo DUMPFAIL; fi; }
net(){ adb shell dumpsys connectivity | grep -q '^Active default network: [0-9]' && echo online || echo offline; }
waitfor(){ for k in $(seq 1 30); do [ "$(net)" = "$1" ] && return 0; sleep 2; done; return 1; }
adb shell cmd connectivity airplane-mode disable; waitfor online
adb shell am force-stop --user $NT_USER com.example.nyasaplayer; sleep 2
adb shell $NT_LAUNCH >/dev/null; sleep 18
miss=0; valid=0
check(){ want=$1; want_b=$2; label=$3
  waitfor $want || { echo "$label: system never reached $want (skipped)"; return; }
  sleep 5; n1=$(net); b=$(banner); n2=$(net)
  if [ "$n1" != "$want" ] || [ "$n2" != "$want" ]; then echo "$label: system moved during check ($n1/$n2) (skipped)"; return; fi
  valid=$((valid+1)); if [ "$b" = "$want_b" ]; then echo "$label: system=$want banner=$b ok"; else miss=$((miss+1)); echo "$label: system=$want banner=$b MISS"; fi; }
for i in 1 2 3 4 5 6 7 8 9 10; do
  adb shell cmd connectivity airplane-mode enable;  check offline 1 "cycle $i OFF"
  adb shell cmd connectivity airplane-mode disable; check online 0  "cycle $i ON "
done
echo "TOTAL: $miss misses / $valid valid transitions"
