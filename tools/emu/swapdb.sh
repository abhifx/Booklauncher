#!/bin/bash
# 에뮬레이터 시험: 앱을 끄고(강제 종료 없이) DB 를 바꾼 뒤 홈을 띄운다. 디버그 빌드에서만(run-as).
#   tools/emu/swapdb.sh <로컬 DB 파일|NONE>     (ANDROID_SERIAL 로 에뮬 지정)
set -e
# 앱을 지우거나 기록을 덮으니 에뮬에서만(실기기의 기록을 지우지 않게 ANDROID_SERIAL 로 에뮬을 꼭 지정)
case "$ANDROID_SERIAL" in emulator-*) ;; *) echo "ANDROID_SERIAL=emulator-… 로 에뮬을 지정하세요(실기기 금지)" >&2; exit 1 ;; esac
D=/data/user_de/0/com.woody.booklauncher/databases
if [ "$1" != NONE ]; then adb push "$1" /data/local/tmp/seed.db </dev/null >/dev/null; adb shell chmod 644 /data/local/tmp/seed.db </dev/null; fi
adb shell input keyevent HOME </dev/null; sleep 1
adb shell am start -a android.settings.SETTINGS </dev/null >/dev/null 2>&1; sleep 2
pid=$(adb shell pidof com.woody.booklauncher </dev/null | tr -d '\r')
[ -n "$pid" ] && adb shell "run-as com.woody.booklauncher kill $pid" </dev/null || true
# 프로세스가 끝날 때까지 기다린다(살아 있는 DB 연결 아래에서 파일을 바꾸면 옛 표로 읽다 멈춘다)
for _ in $(seq 1 20); do [ -z "$(adb shell pidof com.woody.booklauncher </dev/null | tr -d '\r')" ] && break; sleep 0.5; done
if [ -n "$(adb shell pidof com.woody.booklauncher </dev/null | tr -d '\r')" ]; then echo "앱이 끝나지 않음" >&2; exit 1; fi
if [ "$1" = NONE ]; then adb shell "run-as com.woody.booklauncher sh -c 'rm -f $D/pebble.db*'" </dev/null
else adb shell "run-as com.woody.booklauncher sh -c 'mkdir -p $D && rm -f $D/pebble.db* && cp /data/local/tmp/seed.db $D/pebble.db'" </dev/null; fi
adb shell input keyevent HOME </dev/null; sleep 5
