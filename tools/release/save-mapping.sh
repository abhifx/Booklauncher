#!/bin/bash
# 릴리스 APK 와 짝인 R8 mapping 을 저장소의 release-mappings/BookLauncher-<판>-mapping.txt.gz 로 남긴다(GitHub 에는 올리지 않는다).
# 기기 정보 › 마지막 앱 오류의 `h4.b:41`(R8 이 바꾼 이름)을 원래 이름으로 되찾는 데 쓴다(tools/release/retrace.sh).
# 배포할 APK 를 만든 바로 그 빌드 뒤에 부른다: APK 안의 mapping 열쇠(r8-map-id-…)와 mapping 의 pg_map_id 가 같은지 확인한다.
#   tools/release/save-mapping.sh            # 판은 APK 이름에서
set -euo pipefail
cd "$(dirname "$0")/../.."
apk=$(ls app/build/outputs/apk/release/BookLauncher-*-release.apk | head -1)
map=app/build/outputs/mapping/release/mapping.txt
version=$(basename "$apk" | sed -E 's/^BookLauncher-(.*)-release\.apk$/\1/')
id=$(sed -nE 's/^# pg_map_id: ([0-9a-f]+)$/\1/p' "$map")
[ -n "$id" ] || { echo "mapping 에 pg_map_id 가 없다: $map" >&2; exit 1; }
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
unzip -q -o "$apk" 'classes*.dex' -d "$tmp"
grep -aq "r8-map-id-$id" "$tmp"/classes*.dex || { echo "APK($apk)와 mapping 의 열쇠가 다르다(다시 빌드했나?): $id" >&2; exit 1; }
out=release-mappings/BookLauncher-$version-mapping.txt.gz
gzip -9 -n -c "$map" > "$out"
echo "$out ($(wc -c < "$out" | tr -d ' ')B, map id ${id:0:12}…)"
