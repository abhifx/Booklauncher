#!/bin/bash
# 기기 정보 › 마지막 앱 오류의 자리(R8 이 바꾼 이름)를 원래 이름으로 되찾는다.
#   tools/release/retrace.sh 1.6.2 'h4.b:41 ← bg.c:88'
# 판의 mapping 은 release-mappings/BookLauncher-<판>-mapping.txt.gz(tools/release/save-mapping.sh 가 남긴다).
set -euo pipefail
cd "$(dirname "$0")/../.."
version=$1; frames=$2
map=release-mappings/BookLauncher-$version-mapping.txt.gz
[ -f "$map" ] || { echo "그 판의 mapping 이 없다: $map" >&2; exit 1; }
export JAVA_HOME=${JAVA_HOME:-"/Applications/Android Studio.app/Contents/jbr/Contents/Home"}
retrace=$(ls "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/cmdline-tools/*/bin/retrace | head -1)
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
gunzip -c "$map" > "$tmp/mapping.txt"
# `클래스.메서드:줄 ← …` 를 retrace 가 읽는 스택 줄(`at 클래스.메서드(SourceFile:줄)`)로
echo "$frames" | sed 's/ · .*//' | tr '←' '\n' | sed -E 's/^ *//; s/ *$//' | grep -v '^$' |
  sed -E 's/^(.*)\.([^.:]+):([0-9-]+)$/    at \1.\2(SourceFile:\3)/' > "$tmp/stack.txt"
# (retrace 실행 스크립트가 늘 내는 `test: : integer expression expected` 경고는 거른다)
"$retrace" "$tmp/mapping.txt" "$tmp/stack.txt" 2> >(grep -v "integer expression expected" >&2)
