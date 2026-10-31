#!/bin/bash
# 읽는 책 자동 추가 시험의 결과를 가져온다(디버그 빌드, run-as). docs/emulator-test-cases.md `M. 자동 추가 앱별`.
#   tools/emu/watch-snap.sh <결과 폴더>
# DB·자동 추가 기록·서비스 prefs 를 가져와 tools/emu/watch-norm.py 로 때를 뺀 요약(summary.txt)을 만든다.
# 같은 순서로 시험한 두 결과의 summary.txt 를 diff 하면 동작이 같은지 본다.
set -e
OUT=$1
[ -n "$OUT" ] || { echo "결과 폴더를 주세요" >&2; exit 1; }
P=com.woody.booklauncher
DD=/data/user_de/0/$P
mkdir -p "$OUT"
a() { adb "$@" </dev/null; }
# exec-out 은 없는 파일이어도 오류 글을 내보내므로 있는 것만 가져온다
pull() { if a shell run-as $P test -f "$DD/$1"; then a exec-out run-as $P cat "$DD/$1" > "$OUT/$2"; else rm -f "$OUT/$2"; fi; }
# DB 는 WAL·shm 과 한 번에(따로 가져오면 그사이 체크포인트로 WAL 이 맞지 않아 마지막 쓰기가 빠진다)
rm -rf "$OUT/db"; mkdir -p "$OUT/db"
a exec-out run-as $P sh -c "cd $DD/databases && tar cf - pebble.db*" | tar xf - -C "$OUT/db"
pull files/watch_log.txt watch_log.txt
pull shared_prefs/reader_watch.xml reader_watch.xml
a exec-out run-as $P ls $DD/files/books > "$OUT/covers.txt" 2>/dev/null || true
python3 "$(dirname "$0")/watch-norm.py" "$OUT" > "$OUT/summary.txt"
echo "$OUT/summary.txt"
