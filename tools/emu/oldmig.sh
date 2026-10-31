#!/bin/bash
# 옛 판에서 이 판으로 올라오는 마이그레이션 시험(루트 되는 에뮬, ANDROID_SERIAL 로 지정. docs/emulator-test-cases.md A7~A9).
#   tools/emu/oldmig.sh <json|v1|v2> <옛 판 APK> <새 판 APK> <결과 폴더> [<거칠 판 APK>]
# 옛 판을 새로 깔아 저장소를 만들게 한 뒤, tools/emu/oldseed.py 로 채운 자료를 앱 데이터 폴더에 덮고, 옛 판으로 한 번 띄워(그 판이 읽는지)
# 새 판을 덮어 깔아 띄운 다음 옮긴 DB 를 가져와 기대와 대조한다. 화면은 <결과 폴더>/old.png·new.png.
# 거칠 판을 주면 옛 판 다음에 그 판을 덮어 깔아 띄운 뒤(mid.png) 새 판으로 올린다(예: 1.5.3 → 1.5.4 가 만든 버전 3 DB → 새 판).
set -e
# 앱을 지우거나 기록을 덮으니 에뮬에서만(실기기의 기록을 지우지 않게 ANDROID_SERIAL 로 에뮬을 꼭 지정)
case "$ANDROID_SERIAL" in emulator-*) ;; *) echo "ANDROID_SERIAL=emulator-… 로 에뮬을 지정하세요(실기기 금지)" >&2; exit 1 ;; esac
KIND=$1; OLD=$2; NEW=$3; OUT=$4; MID=$5
P=com.woody.booklauncher
DD=/data/user_de/0/$P
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT/data"
a() { adb "$@" </dev/null; }
home() { a shell cmd package set-home-activity $P/.HomeActivity >/dev/null; a shell am start -n $P/.HomeActivity >/dev/null 2>&1 || true; sleep "${1:-6}"; }

a root >/dev/null; sleep 2
a uninstall $P >/dev/null 2>&1 || true
a install "$OLD" | tail -1
home 6
a shell am force-stop $P
python3 "$HERE/oldseed.py" "$KIND" "$OUT/data"
a shell "rm -rf /data/local/tmp/oldseed"
a push "$OUT/data" /data/local/tmp/oldseed >/dev/null
UID_=$(a shell stat -c %u $DD | tr -d '\r')
a shell "rm -f $DD/databases/pebble.db*; cp -r /data/local/tmp/oldseed/. $DD/ && chown -R $UID_:$UID_ $DD && restorecon -R $DD"
home 6
a exec-out screencap -p > "$OUT/old.png"
a shell am force-stop $P
if [ -n "$MID" ]; then
  a install -r "$MID" | tail -1
  home 10
  a exec-out screencap -p > "$OUT/mid.png"
  a shell am force-stop $P
fi
a install -r "$NEW" | tail -1
home 10
a exec-out screencap -p > "$OUT/new.png"
for f in pebble.db pebble.db-wal pebble.db-shm; do a exec-out "cat $DD/databases/$f 2>/dev/null" > "$OUT/$f"; done
a shell "ls $DD/files $DD/files/books" > "$OUT/files.txt"
python3 "$HERE/oldseed.py" check "$KIND" "$OUT/pebble.db"
