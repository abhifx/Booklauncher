#!/bin/bash
# 홈페이지 캡처용 '이름만 있는 빈 앱'(load.sh): mock 책·자주 쓰는 앱의 이북 앱 가운데 에뮬에 없는 것을
# 패키지·실행 화면·이름이 같고 아이콘은 site/img/apps/ 그림인 코드 없는 APK 로 만들어 깐다(진짜 앱이 있으면 그대로 둔다).
#   tools/site/stubs.sh <에뮬> <작업 폴더>     # 저장소 맨 위에서. 깐 패키지를 <작업 폴더>/stubs-<에뮬>.txt 에 적는다
#   tools/site/stubs.sh <에뮬> <작업 폴더> remove   # 그 목록의 빈 앱을 지운다
# 앞서 끊겨 남은 빈 앱(versionName stub)도 목록에 올려 함께 지운다. java(JAVA_HOME)와 디버그 키(~/.android/debug.keystore)가 있어야 한다.
set -e
case "$1" in emulator-*) ;; *) echo "에뮬(emulator-…)만" >&2; exit 1 ;; esac
A="adb -s $1"; W=$2; LIST="$W/stubs-$1.txt"
if [ "$3" = remove ]; then
  [ -f "$LIST" ] && while read -r p; do $A uninstall "$p" </dev/null >/dev/null && echo "지움 $p"; done < "$LIST"
  rm -f "$LIST"; exit 0
fi
[ -n "$JAVA_HOME" ] && PATH="$JAVA_HOME/bin:$PATH"  # apksigner 는 PATH 의 java 를 쓴다
SDK=${ANDROID_HOME:-~/Library/Android/sdk}
BT=$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)
JAR=$SDK/platforms/$(ls "$SDK/platforms" | sort -V | tail -1)/android.jar
ICONS=site/img/apps
mkdir -p "$W/stubs"; : > "$LIST"
have=$($A shell pm list packages </dev/null | tr -d '\r')
# 패키지|실행 화면|이름|아이콘(load.sh·mock.py 의 앱과 같다)
while IFS='|' read -r pkg act label icon; do
  if echo "$have" | grep -qx "package:$pkg"; then
    # 앞서 끊긴 실행이 남긴 빈 앱이면 이번에 지우게 적는다(진짜 앱은 그대로)
    $A shell dumpsys package "$pkg" </dev/null | grep -q "versionName=stub" && echo "$pkg" >> "$LIST"
    continue
  fi
  apk="$W/stubs/$pkg.apk"
  if [ ! -f "$apk" ]; then
    t=$(mktemp -d); mkdir -p "$t/res/mipmap"; cp "$ICONS/$icon.png" "$t/res/mipmap/ic.png"
    cat > "$t/AndroidManifest.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$pkg" android:versionCode="1" android:versionName="stub">
  <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="30" />
  <application android:label="$label" android:icon="@mipmap/ic" android:hasCode="false">
    <activity android:name="$act" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
    </activity>
  </application>
</manifest>
EOF
    "$BT/aapt2" compile -o "$t/res.zip" --dir "$t/res"
    "$BT/aapt2" link -I "$JAR" --manifest "$t/AndroidManifest.xml" -o "$t/u.apk" "$t/res.zip"
    "$BT/zipalign" -f 4 "$t/u.apk" "$t/a.apk"
    "$BT/apksigner" sign --ks ~/.android/debug.keystore --ks-pass pass:android --out "$apk" "$t/a.apk"
    rm -rf "$t"
  fi
  $A install "$apk" </dev/null >/dev/null
  echo "$pkg" >> "$LIST"; echo "빈 앱 $pkg"
done <<'APPS'
kr.co.kyobobook.KEL|com.kyobo.ebook.kel.ui.intro.IntroActivity|교보도서관|kyobo-library
kr.co.millie.eink|kr.co.millie.eink.SplashActivity|밀리의서재 E ink|millie
com.initialcoms.ridi|com.ridi.books.viewer.main.activity.SplashActivity|리디|ridi
kr.co.aladin.ebook|kr.co.aladin.ebook.SplashActivity|알라딘 ebook|aladin
com.yes24.library.eink|com.yes24.library.MainActivity|전자도서관|yes24-library
com.amazon.kindle|com.amazon.kindle.UpgradePage|Kindle|kindle
com.flyersoft.moonreader|com.flyersoft.moonreader.ActivityMain|Moon+ Reader|moonreader
org.koreader.launcher|org.koreader.launcher.MainActivity|KOReader|koreader
com.overdrive.mobile.android.libby|com.overdrive.mobile.android.nautilus.ui.Activity_Main|Libby|libby
APPS
