#!/bin/bash
# 홈페이지 캡처: mock.py 가 만든 DB·표지와 설정을 에뮬의 디버그 빌드에 넣고 홈을 띄운다.
#   ANDROID_SERIAL=emulator-5554 tools/site/load.sh <mock.py 출력 폴더>
# 홈의 책 앱 이름이 보이려면 이북 앱(이름만 있는 빈 앱이라도)이 깔려 있어야 한다.
set -e
case "$ANDROID_SERIAL" in emulator-*) ;; *) echo "ANDROID_SERIAL=emulator-… 로 에뮬을 지정하세요(실기기 금지)" >&2; exit 1 ;; esac
P=com.woody.booklauncher
D=/data/user_de/0/$P
adb shell rm -rf /data/local/tmp/mock </dev/null
adb push "$1" /data/local/tmp/mock </dev/null >/dev/null
adb shell chmod -R a+rX /data/local/tmp/mock </dev/null
# 설정: 올해 읽은 책 격자. SITE_LANG=en 이면 앱을 영어로, 자주 쓰는 앱은 영어 이름 앱 넷, 아니면 이북 앱 여덟(둘 다 격자)
if [ "$SITE_LANG" = en ]; then
  english=true; grid=true
  favs='["com.amazon.kindle/.UpgradePage","com.overdrive.mobile.android.libby/com.overdrive.mobile.android.nautilus.ui.Activity_Main","org.koreader.launcher/.MainActivity","com.flyersoft.moonreader/.ActivityMain"]'
else
  english=false; grid=true
  favs='["kr.co.kyobobook.KEL/com.kyobo.ebook.kel.ui.intro.IntroActivity","kr.co.millie.eink/.SplashActivity","com.initialcoms.ridi/com.ridi.books.viewer.main.activity.SplashActivity","kr.co.aladin.ebook/.SplashActivity","com.yes24.library.eink/com.yes24.library.MainActivity","com.amazon.kindle/.UpgradePage","org.koreader.launcher/.MainActivity","com.flyersoft.moonreader/.ActivityMain"]'
fi
cat > /tmp/pebble-home.xml <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="onboarded" value="true" />
    <boolean name="english" value="$english" />
    <boolean name="fav_grid" value="$grid" />
    <boolean name="year_grid" value="true" />
    <string name="favorite_list">$favs</string>
    <string name="update_skip">9.9.9</string>
</map>
EOF
adb push /tmp/pebble-home.xml /data/local/tmp/mock/home.xml </dev/null >/dev/null
adb shell chmod 644 /data/local/tmp/mock/home.xml </dev/null
adb shell appops set $P GET_USAGE_STATS allow </dev/null
adb shell input keyevent HOME </dev/null; sleep 1
adb shell am start -a android.settings.SETTINGS </dev/null >/dev/null 2>&1; sleep 2
pid=$(adb shell pidof $P </dev/null | tr -d '\r')
[ -n "$pid" ] && adb shell "run-as $P kill $pid" </dev/null || true
for _ in $(seq 1 20); do [ -z "$(adb shell pidof $P </dev/null | tr -d '\r')" ] && break; sleep 0.5; done
adb shell "run-as $P sh -c 'rm -rf $D/databases/pebble.db* $D/files/books && mkdir -p $D/databases $D/files/books && cp /data/local/tmp/mock/pebble.db $D/databases/ && cp /data/local/tmp/mock/books/* $D/files/books/ && cp /data/local/tmp/mock/home.xml $D/shared_prefs/home.xml'" </dev/null
# 앱을 끄면 자동 추가(접근성) 서비스도 멈춰 홈이 `멈춰 있습니다` 창을 띄운다 → 에뮬에서 껐다 켠다(실기기에서는 하지 않는다)
adb shell settings put secure enabled_accessibility_services null </dev/null; sleep 2
adb shell settings put secure enabled_accessibility_services $P/$P.ReaderWatchService </dev/null
adb shell settings put secure accessibility_enabled 1 </dev/null; sleep 2
adb shell am start -n $P/.HomeActivity </dev/null >/dev/null; sleep 5
