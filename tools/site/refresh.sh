#!/bin/bash
# 앱 화면이 바뀌었을 때 홈페이지 그림(site/img)과 README 그림(첫 그림 docs/screenshots/hero-*.png·홈 home.png)을 한 번에 다시 만든다.
#   PEBBLE=emulator-5560 BAR=emulator-5556 tools/site/refresh.sh <작업 폴더>
# 에뮬 시계를 SITE_DATE(기본: 처음 릴리스 v1.0.0 한 날 12:00)로 맞추고 mock 도 그날을 오늘로 만들어, 언제 돌려도 같은 날짜의 그림이 나온다
# (root 가 되는 google_apis 에뮬. 끝나면 자동 시간을 켜고 지금 시각으로 돌린다). 그날 이북 앱을 쓴 기록이 있는 에뮬은 피한다:
# 사용 기록이 mock 의 오늘 읽은 시간을 덮는다(CremaPebble_A11_Millie -read-only 등).
# 저장소 맨 위에서, site/ 에 홈페이지 저장소(pebbledesk/pebbledesk.github.io)를 받아 두고. 에뮬은 페블(1072×1456)·바형(824×1648, Tango).
# 순서: 디버그 빌드 설치 → 없는 이북 앱은 빈 앱(stubs.sh) → 표지(covers.py, 저장소 밖 <작업 폴더>) → mock.py → 한국어·영어 × 페블·바형 캡처(shoot.sh)
#       → site/img/*.webp(images.py) → 공유 그림·README 첫 그림(og.py) → README 홈 그림 → 깐 빈 앱 지우기.
# 에뮬의 Pebble Desk 데이터는 mock 으로 바뀐다. PY 는 Pillow 가 있는 python(기본 python3).
set -e
W=${1:?작업 폴더}; PY=${PY:-python3}
: "${PEBBLE:?PEBBLE=emulator-…}" "${BAR:?BAR=emulator-…}"
[ -d site/img ] || { echo "site/ 에 홈페이지 저장소를 받아 두세요" >&2; exit 1; }
export JAVA_HOME=${JAVA_HOME:-"/Applications/Android Studio.app/Contents/jbr/Contents/Home"}; export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$W"
SITE_DATE=${SITE_DATE:-"2026-10-06 12:00"}
export SEED_TODAY=${SITE_DATE%% *}
stamp=$(date -j -f "%Y-%m-%d %H:%M" "$SITE_DATE" +%m%d%H%M%Y.00)
clock() { adb -s $1 shell date $stamp </dev/null >/dev/null; }  # 화면마다 시계가 같게 찍기 전에 다시 맞춘다
./gradlew -q assembleDebug
apk=$(ls app/build/outputs/apk/debug/*.apk | head -1)
trap 'set +e  # 정리는 실패가 있어도 끝까지
for d in $PEBBLE $BAR; do bash tools/site/stubs.sh $d "$W" remove || true; adb -s $d emu power ac on </dev/null >/dev/null; adb -s $d emu power status charging </dev/null >/dev/null
  adb -s $d shell settings put global auto_time 1 </dev/null; adb -s $d shell date -u $(date -u +%m%d%H%M%Y.%S) </dev/null >/dev/null; adb -s $d unroot </dev/null >/dev/null; done' EXIT
for d in $PEBBLE $BAR; do
  adb -s $d install -r "$apk" </dev/null >/dev/null
  bash tools/site/stubs.sh $d "$W"
  # 상태 줄에 충전 표시(⚡)가 없게
  adb -s $d emu power ac off </dev/null >/dev/null; adb -s $d emu power status not-charging </dev/null >/dev/null
  # 시계를 SITE_DATE 로(자동 시간을 끄고)
  adb -s $d root </dev/null >/dev/null; adb -s $d wait-for-device </dev/null
  [ "$(adb -s $d shell id -u </dev/null | tr -d '\r')" = 0 ] || { echo "$d: root 가 안 되는 에뮬이라 시계를 맞출 수 없습니다(google_apis 이미지로)" >&2; exit 1; }
  adb -s $d shell settings put global auto_time 0 </dev/null; clock $d
done
for lang in ko en; do
  export SITE_LANG=$([ $lang = en ] && echo en || echo "")
  $PY tools/site/covers.py "$W/covers-$lang"
  rm -rf "$W/mock-$lang"; $PY tools/site/mock.py "$W/mock-$lang" "$W/covers-$lang"
  clock $PEBBLE; ANDROID_SERIAL=$PEBBLE bash tools/site/shoot.sh "$W/mock-$lang" "$W/shots-$lang" pebble
  clock $BAR; ANDROID_SERIAL=$BAR bash tools/site/shoot.sh "$W/mock-$lang" "$W/shots-$lang" bar
done
unset SITE_LANG
$PY tools/site/images.py "$W/shots-ko" site/img
$PY tools/site/images.py "$W/shots-en" site/img en-
# 앱 아이콘은 그대로(바꿀 때는 IconDump 로 그린 512px 를 넘긴다)
cp site/img/icon-512.png "$W/icon-512.png"
# 지원 앱 아이콘은 그림이 그대로라 og.py 가 다시 저장만 하므로 돌리기 전 것으로 되돌린다(앱을 더할 때는 og.py 를 따로)
rm -rf "$W/app-icons"; cp -R docs/screenshots/app-icons "$W/app-icons"
$PY tools/site/og.py "$W/icon-512.png"
rm -rf docs/screenshots/app-icons; cp -R "$W/app-icons" docs/screenshots/app-icons
# README 화면 그림의 홈(다른 화면 그림처럼 폭 536px 흑백)
$PY -c "import sys; from PIL import Image; im = Image.open(sys.argv[1]).convert('L'); im.resize((536, round(im.height * 536 / im.width)), Image.LANCZOS).save(sys.argv[2], optimize=True)" "$W/shots-ko/pebble-home.png" docs/screenshots/home.png
echo "다 됐습니다: site/ 와 docs/screenshots/ 의 바뀐 것을 확인하세요(git status)"
