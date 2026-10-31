#!/bin/bash
# 만화 표지 압축 해제용 libarchive(readers/ArchiveLib.kt)의 받을 파일을 만든다.
#   tools/libarchive/make-assets.sh <결과 폴더>
# Maven Central 의 me.zhanghai.android.libarchive:library AAR 을 받아 Maven 이 알려 주는 SHA-1 과 맞는지 보고, 기기 종류마다
# jni/<abi>/libarchive-jni.so 를 gzip(-9 -n, 같은 입력이면 같은 결과)으로 묶는다. 함께 넣은 라이브러리들의 라이선스는 NOTICE.txt 로 모은다.
# 결과 폴더의 파일을 GitHub 릴리스 libarchive-<판>(시험판, 최신 아님)에 올리고, 끝에 찍는 크기·SHA-256 을 ArchiveLib 의 ARM64·ARMV7 에 넣는다.
set -euo pipefail
VERSION=1.1.7
OUT=${1:?결과 폴더}
mkdir -p "$OUT"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
M=https://repo1.maven.org/maven2/me/zhanghai/android/libarchive/library/$VERSION/library-$VERSION.aar
curl -sfL -o "$WORK/lib.aar" "$M"
want=$(curl -sfL "$M.sha1" | cut -c1-40)
have=$(shasum -a 1 "$WORK/lib.aar" | cut -c1-40)
[ "$want" = "$have" ] || { echo "AAR SHA-1 이 Maven 과 다르다: $have != $want" >&2; exit 1; }
unzip -q "$WORK/lib.aar" 'jni/*' -d "$WORK/aar"
for abi in arm64-v8a armeabi-v7a; do
  so="$OUT/libarchive-jni-$abi.so"
  cp "$WORK/aar/jni/$abi/libarchive-jni.so" "$so"
  gzip -9 -n -f "$so"
  cp "$WORK/aar/jni/$abi/libarchive-jni.so" "$so"
  echo "$abi gz $(stat -f %z "$so.gz") $(shasum -a 256 "$so.gz" | cut -c1-64) so $(stat -f %z "$so") $(shasum -a 256 "$so" | cut -c1-64)"
  rm "$so"
done
# 라이선스: libarchive-android 와 그 안에 함께 빌드된 것(.gitmodules·CMakeLists.txt, zlib 은 안드로이드 NDK 의 libz.a, libb2 는 libarchive 안)
raw() { curl -sfL "https://raw.githubusercontent.com/$1"; }
{
  echo "libarchive-jni.so from me.zhanghai.android.libarchive:library:$VERSION (Maven Central, $M)"
  echo "Used by BookLauncher to read covers of comic archives. Licenses of the code built into it follow."
  for part in \
    "libarchive-android (Apache-2.0)|zhanghai/libarchive-android/v$VERSION/LICENSE" \
    "libarchive (BSD-2-Clause and others, incl. bundled libb2 CC0)|libarchive/libarchive/master/COPYING" \
    "bzip2|https://gitlab.com/bzip2/bzip2/-/raw/master/COPYING" \
    "xz / liblzma (0BSD)|tukaani-project/xz/master/COPYING.0BSD" \
    "lz4 lib (BSD-2-Clause)|lz4/lz4/dev/lib/LICENSE" \
    "zstd (BSD-3-Clause)|facebook/zstd/dev/LICENSE" \
    "Mbed TLS (Apache-2.0)|Mbed-TLS/mbedtls/development/LICENSE" \
    "zlib (Android NDK libz)|madler/zlib/develop/LICENSE"; do
    name=${part%%|*}; src=${part#*|}
    echo; echo "================================================================"; echo "$name"; echo "================================================================"
    case "$src" in https://*) curl -sfL "$src" ;; *) raw "$src" ;; esac
  done
} > "$OUT/NOTICE.txt"
ls -l "$OUT"
