# Pebble Desk — 작업 노트

크레마 페블(전자잉크 6인치, 1072×1456, 300dpi) 전용 글자 중심 런처. 패키지 `com.woody.pebbledesk`.
화면 규격·동작의 기준은 **DESIGN.md**다. 코드를 바꾸면 DESIGN.md도 같이 맞춘다.
이북 앱(교보도서관·밀리의서재 등)에서 알아낼 수 있는 것과 없는 것은 **docs/READER_APPS.md** 에 정리했다.
다른 기기(메이북 등) 호환 조사와 펌웨어 받기·풀기 방법은 **docs/DEVICES.md** 에 정리했다. 새 기기를 조사하면 거기에 더한다.
README 는 영어(`README.md`)와 한국어(`README.ko.md`) 두 벌이다. 하나를 고치면 다른 하나도 같이 고친다.
사용자 댓글로 들어온 기기 문제의 진행 상태는 **docs/ISSUES.md** 에서 따라간다(이북 앱 지원·기능 요청은 적지 않는다).
사용자가 직접 보는 문제 해결 안내는 **docs/TROUBLESHOOTING.md**(합니다체, 증상 → 해 볼 것. 읽는 사람은 개발자가 아니라 adb·PC 명령은 넣지 않는다). 기기 문제가 풀리거나 우회 방법이 확인되면 여기에도 사용자 말로 더한다. 메뉴 이름·판 번호가 바뀌면 같이 고친다.

## 빌드·설치

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew -q assembleRelease          # app/build/outputs/apk/release/PebbleDesk-1.7.4-release.apk
adb install -r app/build/outputs/apk/release/PebbleDesk-1.7.4-release.apk </dev/null
```

- 릴리스는 R8 축소(약 740KB, 1.6.5 는 720KB·1.6.4 는 705KB·1.6.1 은 734KB·1.5.3 은 480KB. HTML 라이브러리 없이 YES24 검색 결과를 직접 읽는다. 릴리스만 Kotlin 널 검사 호출을 빼고(`-Xno-*-assertions`) `-repackageclasses`, 도장 그림은 16단계 투명도 4비트 PNG). dex 는 minSdk 28 이라 압축하지 않고 넣는다(설치 뒤 풀어 두지 않아 저장 공간·실행이 낫다). 덮어 설치하면 앱 데이터가 남는다.
- 서명: 1.4.2 까지는 디버그 키. 1.5.0 부터는 APK 를 만드는 `packageRelease`·`packageDebug` 끝에 릴리스 키(`~/Dev/pebble-desk-keys`, 저장소 밖·백업은 iCloud `PebbleDesk-서명키`)와 교체 이력(lineage, 디버그 키 → 릴리스 키)으로 v3 서명만 다시 하고(v1·v2·v4 끔), 인증서·이력이 맞는지 `apksigner verify`·`lineage` 로 확인한다(`app/build.gradle.kts`). 그래서 1.4.2 위에 덮어 깔리고, 디버그 키로만 서명한 APK 는 그 위에 깔리지 않는다. 키 폴더가 없으면 디버그 빌드는 디버그 키 그대로, 릴리스 빌드는 실패한다(`-Ppebbledesk.debugSignedRelease=true` 면 디버그 키로). assemble·install·Android Studio 실행 모두 다시 서명된 APK 를 쓴다. 키는 저장소(공개)에 넣지 않는다.
- 앱 데이터는 **기기 보호 저장소**(`/data/user_de/0/com.woody.pebbledesk/`)에 있다(`Storage`). 홈이 잠금 해제 전에도 뜨게(directBootAware) 하려고 옮겼다.
  - DB `databases/pebble.db`(`Db`, 안드로이드 기본 SQLite, 버전 6): 읽은 시간 `reading_day`(날·앱 `pkg`·편 책 제목 `title`·붙은 책 `book_id`(모르면 "", 저장할 때 `bookFor` 로 한 번 정하고 다시 정하지 않는다)·ms. 버전 3 에서 `패키지/제목` 한 칸을 나누고 책 id 를 더함)·`reading_meta`, 편 책 `book_open`(그 책 id 도), Libby 읽는 화면이 뜨고 닫힌 때·KOReader 책을 열고 닫은 때 `viewer_mark`(버전 3), 읽고 있는 책 `shelf`(진행률 % 대신 앱이 보여 준 읽는 화 `chapter`, Mihon 의 화 이름. 버전 5), 나간 책 `past`, 완독 `finish`(한 번에 한 줄·줄 id 와 기기를 옮겨도 그대로인 `uid`, 같은 날 두 번도(순서는 누른 때 `at`). 회차 번호는 저장하지 않고 순서로 센다. 그 회차를 다시 읽기한 날 `started`·별점·감상 `stars`·`text`. 버전 3. 사용자가 고친 읽기 시작한 날 `began`, 버전 6), 진행 중인 마지막 회차의 별점·감상 `review`(버전 2), 다시 읽기한 날 `round_start`(버전 3), 진행 중 회차의 고친 읽기 시작한 날 `round_began`(완독하면 `finish.began` 으로, 버전 6), 파일로만 여는 이북 앱(네오리더)에서 연 책 파일 경로 `book_file`(책 id → 경로, 버전 4). 기록은 지우지 않는다. 릴리스된 DB 는 1.3.0~1.4.2 가 버전 1, 1.5.0~1.5.3 이 버전 2, 1.5.4 가 버전 3, 1.5.5~1.6.0 이 버전 4, 1.6.1~1.7.2 가 버전 5, 1.7.3 부터 버전 6(그 전은 JSON). 버전을 올리면 이 모든 판에서 올라오는 경로(`onUpgrade`)와 새 설치(`onCreate`)를 단위 테스트(`./gradlew testDebugUnitTest`, `app/src/test`: 회차 계산·마이그레이션·JSON 옮기기·저장과 캐시, Robolectric)와 에뮬에서 시험한다. 낮은 버전으로 되돌리기는 지원하지 않는다(판 낮춘 시험 빌드는 깨끗한 에뮬에서).
  - 파일: 설정 `shared_prefs/home.xml`, BookFusion 예전 판의 읽는 화면 이름 `shared_prefs/bookfusion.xml`, 표지 `files/books/<id>.jpg`(줄인 표지 `<id>_<폭>t<대비>[c].png`, 컬러면 c. 시각은 줄일 때의 원본 시각), 앱 목록 캐시 `files/apps.json`, 아이콘 캐시 `files/icons/<앱>@<px><g|c>@<업데이트한 때>[_<아이콘 팩>.<팩을 업데이트한 때>[.<앱마다 고른 그림>]].png`.
  - 1.2.1 까지의 JSON(`reading.json`·`books/books.json`)은 이 실행에서 처음 DB를 열 때 옮기고 `.migrated`로 이름을 바꿔 둔다(읽지 못하면 `.bad`). DB 확인: `adb exec-out run-as … cat …/databases/pebble.db > x.db` 후 `sqlite3`(디버그 빌드).
- **릴리스 설명**(GitHub 릴리스 본문): 앱의 설정 › 업데이트 창과 홈의 새 판 창에 **18줄까지** 보이고 넘으면 끝이 `…` 로 잘린다(`UpdateSheets.NOTES_LINES`). 크레마 페블에서 한 줄은 한국어 약 28자, 절 제목은 한 줄, 두 번째 절부터 위에 반 줄이 더 든다. 그래서 `## 새로 생긴 것` / `## 고친 것` 두 절, 항목(`- `)은 한두 줄짜리로 모두 합쳐 18줄 안에 쓴다(자세한 설명은 문서로). `**`·`` ` `` 는 앱에서 지워지니 써도 된다. 영어도 같은 본문에 쓴다: 한국어 두 절 아래 `## English` 제목 줄, 그 아래 `### New` / `### Fixed`. 앱은 앱 언어의 부분만 보여 주므로(`UpdateSheets.notesFor`, 한국어는 `## English` 위, 영어는 아래. 그 줄이 없으면 전체) 두 부분이 **각각** 18줄 안이면 된다. 1.5.8 까지의 앱은 본문 앞 18줄을 그대로 보여 주어 한국어 아래 남는 줄에 `English` 이하가 이어 보인다. 그래서 1.5.9(모두 1.5.8 이하에서 받는다)는 한국어만 쓰고, 두 언어는 1.5.10 부터 쓴다. 태그는 `v<판>`, 자산은 `PebbleDesk-<판>-release.apk`(APK 의 versionName 과 같아야 앱이 받는다).
- 만화 표지 압축 해제용 libarchive(`readers/ArchiveLib.kt`)의 받을 파일은 앱 릴리스와 따로 GitHub 릴리스 `libarchive-<판>` 에 둔다(`tools/libarchive/make-assets.sh` 로 만들고, 찍힌 크기·SHA-256 을 `ArchiveLib` 에). **반드시 `--prerelease --latest=false`** 로 만든다: 앱 업데이트는 `releases/latest` 를 보므로 이 릴리스가 최신이 되면 모든 앱의 업데이트 확인이 깨진다. libarchive 판을 올리면 새 태그로 올리고 앱의 값을 바꾼다(옛 판 앱은 옛 태그를 계속 받는다. 지우지 않는다).
- **R8 mapping 남기기**: 배포할 APK 를 만든 바로 뒤 `tools/release/save-mapping.sh` 로 `release-mappings/PebbleDesk-<판>-mapping.txt.gz` 를 만들어 커밋한다(GitHub 릴리스에는 올리지 않는다). 기기 정보 › 마지막 앱 오류의 `h4.b:41`(R8 이 바꾼 이름)을 `tools/release/retrace.sh <판> '<자리>'` 로 되찾는다. 1.6.1 까지는 없다.
- 릴리스는 `run-as` 가 안 된다. 앱 데이터를 직접 고쳐야 하면 같은 키로 서명된 디버그 빌드를 잠깐 깔아 `run-as` 로 넣고 다시 릴리스를 깐다.
- 성능은 꼭 **릴리스 빌드**로 잰다(디버그는 몇 배 느리다).

## 기기 작업 요령

- adb 를 부르는 스크립트는 **bash 파일**로 쓴다(zsh 는 변수를 나누지 않고 `path` 가 특수 변수다). adb 명령에는 `</dev/null` 을 붙인다(heredoc 입력을 먹는다).
- 화면이 꺼져 있으면 `input keyevent KEYCODE_WAKEUP` 먼저. `am start -W` 는 화면이 꺼져 있으면 멈출 수 있어 `perl -e 'alarm 90; exec @ARGV' adb ...` 로 시간 제한을 둔다.
- 기기가 잠깐 offline 이 되면 기다렸다 다시 한다.
- my YES 는 USB 디버깅이 켜져 있으면 닫힌다. 크레마는 USB 디버깅을 끄면 무선 디버깅도 멈춰(설정에는 켜짐으로 보여도) adb 로 시험할 수 없다 → 디버그 빌드를 깔고 사용자가 디버깅을 끈 채 눌러 본 뒤, 다시 켜고 DB 로 확인한다. Mac 의 adb 서버가 `No route to host` 로 무선 기기에 못 붙으면 터미널에서 서버를 다시 띄운다(로컬 네트워크 권한).
- 글자로 누르기: `uiautomator dump` 후 `text="..."` 의 bounds 가운데를 탭(이중 공백은 하나로 접힌다). 이미지 뷰는 dump 에 안 나온다.
- `uiautomator dump`·`events` 를 돌리면 접근성 서비스가 새로 만들어져(메모리의 지금 화면·누른 칸이 지워진다) 읽는 책 자동 추가를 시험할 때는 쓰지 않는다. 화면 캡처와 좌표로 누르고, 결과는 DB(디버그 빌드 `run-as`)로 본다.
- **사용자가 기기를 쓰는 중일 수 있다.** 자동 조작은 필요한 만큼만, 와이파이·블루투스 등 기기 설정은 건드리지 않는다. 다른 앱 화면에서 길게 누르기·탭이 엉뚱한 곳(다른 이북 앱, Play 스토어)을 누른 적이 있다.
- **`am force-stop com.woody.pebbledesk` 를 하지 않는다**: 접근성 서비스(읽는 책 자동 추가)가 시스템 설정에서 꺼진다. 다시 켜는 것은 사용자가 한다(adb 로 켜지 않는다). 덮어 설치는 괜찮다.
- 홈페이지 `site/`(한 쪽, 홈·독서 기록·올해 읽은 책을 페블·바형 두 대로, 영어는 `site/en/`: 모든 스크립트에 `SITE_LANG=en` 이면 영어 앱·원서·Kindle 등, 그림은 `site/img/en-*`): 그림은 에뮬 실제 화면. `tools/site/covers.py`(YES24 표지 받기, 원본은 저장소 밖) → `mock.py`(mock DB·표지) → `shoot.sh`(넣고 세 화면 찍기, 이북 앱이 깔린 에뮬에서) → `images.py`(상단 바 지우고 `site/img/*.webp`). 앱 화면이 바뀌면 한 번에 `PEBBLE=… BAR=… tools/site/refresh.sh <저장소 밖 작업 폴더>`(디버그 빌드 설치, 없는 이북 앱은 `stubs.sh` 가 이름·아이콘만 같은 빈 앱으로 깔았다가 지움, 한국어·영어 × 페블·바형, `og.py` 와 README 홈 그림 `docs/screenshots/home.png` 까지. 에뮬 시계를 첫 릴리스 날 `2026-10-06 12:00`(`SITE_DATE`)으로 맞춰 언제 돌려도 같은 날짜로 찍는다(root 되는 google_apis 에뮬). 그날 이북 앱을 쓴 기록이 있는 에뮬은 피한다: 사용 기록이 mock 의 오늘 읽은 시간을 덮는다. 페블은 `CremaPebble_A11_Millie -read-only` 에서 진짜 밀리를 지우고). 기기 틀은 실제 기기 사진(크레마 페블 화이트 YES24 상품 사진·BOOX 팔마 공식 사진)을 `frames.py` 로 화면 자리를 비워 만들고 그 위에 캡처를 얹는다. 지원 앱 아이콘 `site/img/apps/` 는 에뮬에서 안드로이드가 그린 아이콘(`tools/site/IconDump.java` 를 `app_process` 로, 640dpi 그림을 192px PNG 로. 이지뷰어(`.free`)·채리뷰어는 Play 스토어 아이콘을 192px 로 받았다: 채리뷰어는 유료라 에뮬에 없고, 이지뷰어는 판마다 색이 달라 Play 다운로드가 많은 `.free`(빨강)로). 네오리더는 BOOX 런처에 아이콘이 없어 목록에서 뺐다. 검색·공유 정보(제목·설명·대표 주소·hreflang·공유 카드·JSON-LD·자주 묻는 질문·robots.txt·sitemap.xml·llms.txt)는 `tools/site/seo.py` 가 두 페이지에 다시 쓴다(글은 그 파일에서 고친다, 주소는 `https://pebbledesk.github.io/`(저장소 pebbledesk/pebbledesk.github.io) 기준). 공유 그림·아이콘은 `og.py`(README 첫 그림 `docs/screenshots/hero-ko.png`·`hero-en.png` 과 앱 아이콘도 같이 만든다). `site/` 는 이 저장소에 넣지 않는다(git 제외: 기기 사진·책 표지·다른 앱 아이콘이 들어 있다). 예외는 README 그림 `docs/screenshots/`(화면 캡처, 첫 그림 `hero-*.png`, 지원 앱 아이콘 `app-icons/`: `site/img/apps/` 를 96px 로 줄인 것, 첫 그림과 함께 `og.py` 가 만든다. README 는 홈페이지 지표에 잡히지 않게 홈페이지 그림을 부르지 않는다): 책 표지·앱 아이콘·기기 틀 사진이 들어가므로 README 라이선스 절에 PolyForm 라이선스가 아님을 적어 두었다. 그림을 더하면 그 줄도 맞춘다. 원본은 홈페이지 저장소 pebbledesk/pebbledesk.github.io 이고 main 에 push 하면 GitHub Pages 가 배포한다. 고칠 때는 그 저장소를 받아 `site/` 에 두고 고친 뒤 다시 복사해 커밋·push 한다.
- 캡처: `adb exec-out screencap -p > x.png`. 시안·비교 이미지는 `design-review/`(git 제외)에 둔다.

## 기기 특성(크레마 페블)

- 1dp = 1.875px(300dpi, 572×777dp). 모든 dp 값은 이 폭 기준이고, 다른 기기는 짧은 변이 572dp 가 되게 앱 밀도를 다시 정한다(`Ui.designDensityDpi`. 메이북 E6 는 320 → 300). 패널 맨 아래 약 8px 는 테두리에 가려 보이지 않는다 → `Crema.reserveHiddenBottom()` 으로 모든 화면·아래 메뉴의 그쪽을 비운다(가로로 돌리면 왼쪽·오른쪽, 회전 방향을 본다. 실기기 미확인).
- 연한 회색을 실제보다 어둡게 그린다(#e6 → 약 #c1). 아주 연한 회색은 디자인 값보다 밝게 잡는다(링 바탕 #f2).
  - 원인은 크레마 **화면 대비**(`Settings.System hq_contrast`, 기본 56): 앱 화면 전체를 밝기^(1/s) 로 어둡게 그리고(s = 1 − 대비×90/8000 = 0.37, SystemUI `EinkSettingsProvider`) ImageView 그림만 같은 만큼 다시 밝힌다(bleach).
  - 그래서 직접 그리는 표지는 `Crema.undoContrast` 로 미리 밝힌다(`Crema.contrastTone`). 검색 결과처럼 ImageView 에 넣는 그림은 밝히지 않는다(두 번 밝아짐).
- 홈은 안드로이드 상단바를 숨기고 자체 상단(로고·시계·상태 줄)을 그린다. 상태 줄을 누르면 크레마 빠른 설정(`com.epd.drop_down` 브로드캐스트).
- 크레마가 고친 안드로이드는 앱이 바뀔 때마다 모든 `ImageView` 와 배경 있는 뷰 묶음에 명암(bleach) 필터를 다시 걸고 다시 그린다. 그래서 그림은 `PictureView`(직접 그림)로 그리고, 홈의 흰 바탕은 창 배경 대신 직접 칠한다.
- 화면 갱신(깜빡임)의 실제 횟수는 커널 로그 `ebc-dev: frame start` 로 센다. 그리기 횟수와 다르다(갱신 중 들어온 그리기는 합쳐진다).
- 부팅: 홈이 directBootAware 라 시스템 임시 홈(FallbackHome) 없이 부팅 화면 직후 바로 뜬다. 잠금 해제 전에는 앱 목록·아이콘을 `MATCH_DIRECT_BOOT_AWARE|UNAWARE` 로 찾고, 지워진 앱 정리와 읽은 시간 계산은 잠금이 풀린 뒤에 한다. SD 카드가 꽂혀 있으면 부팅 몇 초 뒤 시스템 알림 때문에 한 번 더 깜빡인다(시스템 동작).
- 잔상 제거 횟수는 `persist.vendor.fullmode_cnt`(리플렉션 SystemProperties). 로고는 크레마 런처(`com.wetao.cremalauncher`)의 리소스를 불러온다(상표 이미지를 앱에 넣지 않음).

## 코드 구조 (`app/src/main/java/com/woody/pebbledesk/`)

| 파일 | 역할 |
|---|---|
| HomeActivity | 홈: 상단 · 책 자리(스켈레톤/한 권/여러 권) · 자주 쓰는 앱(고정 3줄) · 하단 줄(모든 앱 · 오늘 읽은 시간 · 설정) |
| ReadingActivity | ⑫ 독서 기록: 연속일·오늘 링, 4주 링 달력·막대그래프(각각 뷰 하나로 그림, 같은 높이), 합계, 이번 주 읽은 책 |
| YearBooksActivity | ⑬ 올해 읽은 책(목록·격자, 해 고르기): 읽는 중·완독한 책(표지에 띠)·뺀 책, 완독·다시 읽기·기록에서 지우기 |
| Recap / RecapImage / RecapActivity | ⑮ 월간 결산: 한 달 데이터와 배치(막대 끊기·주마다 줄 채우기·오른쪽 칸 모양 `Recap`. 책 막대·책 시간은 회차의 고친 기간 안만 `BookRecord.periodDayMs`, 합계는 실제 읽은 날 그대로), 1080×1350 그림(`RecapImage`, 표지는 `BookShelf.shareCover`), 화면(올해 읽은 책 아래 줄 오른쪽 `10월 결산 ›`, `‹ 9월 · 공유 · 11월 ›`, 그림을 누르면 크게, 공유 = 파일로 저장·다른 앱으로 보내기, 보내는 그림은 `RecapFiles` 가 내준다) |
| BookRecord / BookDetail | 책 한 권의 기록(읽은 날·회차 `rounds`(읽기 시작해 완독까지, 회차마다 별점·감상), `BookRecord.all`) / 홈·올해 읽은 책이 같은 책 상세 `showBookDetail`(머리·마지막 회차 기록·마지막 회차 별점·감상·메뉴·`책 정보 고치기`, 메뉴만 상태로 갈림. 회차가 둘 이상이면 기록 칸을 누르면 전체 기록 `showHistory`(세로 타임라인, 줄을 누르면 회차 메뉴 `editRound`: 읽기 시작한 날·완독한 날·별점·감상, 닫으면 상세로. 회차가 하나면 기록 칸을 눌러 바로). 완독은 늘 오늘, 완독한 날·읽기 시작한 날(앞 회차 완독한 날 ~ 끝 날·오늘, 기간 글자만 바뀌고 읽은 시간은 그대로. 완독한 회차의 시간은 완독을 누른 날 `finish.at` 까지)은 회차 메뉴에서만 고친다) |
| Review | 회차마다 별점·감상(`Review`, 완독하지 않은 회차는 `BookShelf.setReview`, 완독한 회차는 완독한 날과 함께 `setFinish`, 완독할 때 `BookShelf.finish` 가 옮김), 별 다섯 `StarBar`(반 개, 누르기·밀기), 별점·감상 메뉴 `reviewSheet`(완독·책 빼기의 확인을 겸함), 상세의 칸 `reviewBlock` |
| Reading | `ReadingLog`: 사용 기록에서 이북 앱 **읽는 화면** 시간을 책별로 계산(편 책 `book_open`, Libby 는 읽는 화면이 보인 동안, KOReader 는 책이 열려 있던 동안 `viewer_mark` 로 자름), DB `reading_day` 저장(칸 `ReadKey`, 책을 모르는 칸은 `bookFor` 로 정하고 목록에 없던 책이 책이 되면 `adopt`), 연속일 |
| ReaderWatch / WatchCore / BookRecorder | 읽는 책 자동 추가(구조는 docs/reader-adapter-plan.md): `ReaderWatchService`(접근성, 안드로이드 연결만: 알림·창·화면 캡처·서비스 생명) → `WatchCore`(공통 흐름, 앱 종류 갈래 없음: 앱마다 지금 화면, 읽는 화면 잠잠·서재 멈춤·마지막 재확인 예약은 전체에 하나, 훅 순서, 연결·끝의 열림·닫힘 표시, 앱 처리 예외는 그 알림만 건너뛰고 `ERROR` 기록) → 앱마다 어댑터(`readers/`) → `BookRecorder`(`ShelfRecorder`: 편 책 넣기·되살리기·편 책 기록·잘라 둔 표지·책 찾기 `lookup`, 앱이 끼어드는 자리 `OpenSteps.found`·`logged`). 홈에서 연 파일 `HomeSignals` |
| WatchLog / WatchLogActivity / CrashLog | 자동 추가 서비스가 받은 알림·한 일 기록(최근 60줄 + 앱별 알림 수) / 기기 정보 › `기록 보기 ›` 화면(쪽 없이 한 화면, 사진으로 받는다. 요약 아래 오늘 읽은 시간 확인: 앱마다 앞에 있던 시간·센 시간, 화면 꺼짐·켜짐과 이북 앱 앞·뒤 사용 기록, 열림·닫힘 표시) / 마지막 앱 오류(잡지 못한 예외의 때·예외·이 앱 코드 자리, 기기 정보에 보인다. `PebbleDeskApp` 이 프로세스가 뜰 때 단다) |
| readers/ | 이북 앱마다 파일 하나(`KyoboLibrary`·`KyoboEbook`(e-ink)·`KyoboEbookMobile`(휴대폰)·`Millie`(e-ink)·`MillieMobile`(휴대폰)·`Aladin`·`Bookers`(1.x)·`Bookers2`(2.0 부터)·`Ridi`·`Yes24Library`·`Yes24Ebook`·`MyYes`·`MoonReader`·`EasyView`(이지뷰어·채리뷰어)·`Kindle`·`Libby`·`NeoReader`·`KOReader`·`Readest`·`BookFusion`·`Woojoo`(우주)·`PlayBooks`(Google Play 북)·`Mihon`(만화, `MihonApp`)·`Kobo`(Kobo Books)·`Kotatsu`(만화, `KotatsuApp`: Futon·Kotatsu)·`Librera`(`LibreraApp`: 무료판 `Librera`·Pro 판 `LibreraPro`, 우주와 코드를 나눔)·`IReader`(iReader 기기 내장판 4.1.0, 실기기 미확인). 패키지만 다른 판은 같은 클래스에 패키지만 줘서 객체 하나 더: 리디 원스토어판 `RidiOneStore`, 문리더 유료판 `MoonReaderPro`, YES24 도서관 휴대폰판 `Yes24LibraryMobile`, KOReader F-Droid 판 `KOReaderFdroid`, 이지뷰어 두 판 `EasyView`·`EasyViewFree` 와 유료판 채리뷰어 `CherieViewer`, Mihon 포크 `Kahon`, Kotatsu 포크 `Futon`(이름공간도 다르다). 패키지는 같은데 판에 따라 전혀 다른 앱이면 앱 파일을 따로 두고 `handles`(깔린 판, 북커스는 versionCode 205 부터 `Bookers2`)로 가른다: `ReaderApps.ALL` 에 새 판을 앞에 두고 `ReaderApps.forPackage`·서비스가 알림마다 고른다). 앱 파일에는 선언 `ReaderApp`(요소 이름·서재 칸·누른 칸·읽는 화면 읽기, 서비스 밖에서도 쓰는 `isViewer`·`viewerTime`·`markedScreens`·`readsContent`·`screenInsideActivity`(`viewerHolder`·`showsViewer`)·`keepsUnknownOnReenter`·`forgetsClickOnOtherTap`(서재에서 칸 아닌 것을 누르면 앞서 누른 칸을 잊음, 교보도서관만)·`fileCovers`(연 책 파일에서 표지를 꺼내는 길 `BookFiles.Covers`: 기본 `BOOKS`(EPUB·그림 폴더), 만화를 여는 KOReader·네오리더·이지뷰어·우주는 `WITH_COMICS`, Librera 는 PDF 첫 쪽까지 `withPdf(WITH_COMICS)`)·`fileEntry`·`needsBookFiles`·`fileOrder`·홈에서 열기 `open`→`OpenResult`·`bookFiles`·`placeholderCover`)과 그 앱의 판단과 기억인 어댑터(`newAdapter`, 서비스가 붙을 때마다 새로). 화면만 읽는 앱은 바탕 `ScreenReader`(서재 칸·누른 칸·메뉴 제목, 바로 자르기·찍은 뒤 다시 보기·책 파일 읽기 안내, 공통 전이 `onScreen` 은 덮지 않는다) 그대로이고, 다른 길로 책을 아는 앱만 상속해 훅(`onEnterViewer`·`onViewer`·`onShelf`·`onShelfLive`·`onClick`·`onClickNoCell`·`onWindow`·`onHome`·`beforeOpened`·`afterOpened`·`capturesCovers`·`stillSpots`·`openCell`)을 덮는다: `WoojooReader`(우주 기록 RecentBooks·app-Progress·표지 그림), `ReadestReader`(책 열쇠 해시·데이터 폴더, 홈에서 `readest://` 링크), `KOReaderReader`(history.lua·.sdr 로 앞에 올 때·홈에서 맞춤, 열림·닫힘 `viewer_mark`), `NeoReaderReader`(탭 ↔ 책, Metadata), `EasyViewReader`(파일 칸·열기 확인 창·읽는 화면 파일 이름·홈에서 연 파일), `AladinReader`(서재 '최근 읽은 책' 줄), `MillieReader`(e-ink 밀리: 받기 창이 책 상세를 덮는 동안 상세를 잊지 않음, `DetailReader` 상속), `MihonReader`(작품 상세, `작가 미상` 거름, 메뉴의 화 이름을 읽는 화 `ViewerInfo.chapter` 로, 로컬 만화 표지 그림으로 바꾸기 `BookRecorder.replaceCover`, `DetailReader` 상속), `PlayBooksReader`(창 제목이 앱 이름이면 거름, 마지막으로 본 라이브러리 칸·스토어 상세의 저자), `LibreraReader`(Librera 기록 app-Recent·app-Progress, 홈에서 `OpenerActivity`), `IReaderReader`(최근 책 Provider 로: 펌웨어가 지금 책 식별자를 내주면 그 줄, 없으면 맨 위 줄(정한 뒤에는 닫고 연 흔적이 있을 때만 바꿈), 목차는 보지 않는다. 표지는 앱이 내주는 그림), `KoboReader`(읽는 화면에 제목이 없어 상세 없이 연 책은 읽고 돌아온 My Books(최근 활동 순)의 맨 위 칸으로 연 때부터, `DetailReader` 상속). 두 앱 이상이 같은 규칙이면 도우미: `DetailReader`(책 상세 → `Read`, BookFusion·밀리 e-ink·Mihon·Kahon·Kobo·Futon·Kotatsu), `DataAsk`(앱 데이터 묻기 횟수, 우주·네오리더·Librera). 어댑터가 받는 것은 `AppContext`(기록은 `opened`·`unknown` 으로만). 파일 읽기는 `BookFiles`(모든 파일 액세스·EPUB 정보·표지 길 `BOOKS`·`WITH_COMICS`)·`ArchiveLib`(압축 만화를 푸는 libarchive 를 받아 쓰기, Java 부분은 `me/zhanghai/android/libarchive/`)·`LuaData`(KOReader 의 Lua 값 파일), 공통 도구 `Nodes`, 앱 목록 `ReaderApps`. 앱을 더하는 법은 아래 `자동 추가에 앱을 더할 때` |
| Books / BooksActivity / BookSearchActivity | 읽고 있는 책(권수 제한 없음, 홈에는 맨 앞 책 + 함께 읽는 책 3권씩 쪽으로, 표지 검색 `BookSearch`(한국어는 YES24, 그 밖은 Apple Books·Open Library). `책 추가`는 지원하지 않는 이북 앱용: 늘 새 책(id `m-`)이고 제목으로 다른 책·읽은 시간과 잇지 않는다)과 나간 책(완독·뺀 책, `BookShelf.history`), 책 목록 관리 |
| Apps / IconPacks | `AppStore`(앱 목록 캐시·디스크 캐시), `AppIcons`(흑백 아이콘 캐시, 디스크 캐시는 앱을 업데이트하면 다시 그림. 그사이 바뀐 앱의 아이콘만 비우고 홈은 그 앱이 보일 때만 다시 그린다) / 런처 아이콘 팩(설정 › 화면 › 아이콘 모양: 깔린 팩 찾기·appfilter 읽기, 팩에 없는 앱은 기본 아이콘, 앱마다 고를 그림 목록·찾기) |
| IconPickActivity | 길게 누르기 › 아이콘 바꾸기: 앱 하나에만 팩의 다른 그림(`HomePrefs.iconOverrides`), 영어 이름 찾기·4열 격자 |
| AppListActivity / FavoritesActivity / SettingsActivity / DeviceInfoActivity | 모든 앱·고르기·숨긴 앱 / 자주 쓰는 앱 관리 / 설정 / 기기 정보(사진으로 찍어 보내는 진단 화면: 속성·권한·와이파이·블루투스·자동 추가 서비스 기록·누른 키·시스템 화면 시험·회전 고정 시험(`HomePrefs.rotationLock`, 홈을 뺀 화면은 매니페스트 `locked` 로 열고 꺼져 있으면 `EinkActivity` 가 푼다)) |
| ReaderGuide | 설정 › 독서 › 자동 추가 안내의 앱별 내용(필요한 권한·앱에서 할 것·자동으로 들어오는 책 정보 다섯 가지·알아 둘 것, 화면은 SettingsActivity 의 앱 목록 → 앱별 안내). 근거는 docs/READER_APPS.md `자동 추가: 앱별로 가져오는 것` |
| OnboardingActivity | ⑭ 시작하기: 처음 한 번, 읽는 책 자동 추가·오늘 읽은 시간을 설명하고 켜게 한다(안드로이드 13+ 제한된 설정 안내 포함) |
| PagedRows | 스크롤 없는 페이지 목록(목록·격자, fixedRows), 높이가 제각각인 묶음을 쪽으로 나누는 `PagedBlocks`(자동 추가 안내), 쪽 표시 `pageLabel`·`pagesClick`(`Paged`) |
| EinkActivity / Sheet / Ui / TopBarView / HomePrefs | 공통 화면(밀기·언어·밀도 맞춤)·아래 메뉴(돌아왔을 때 그사이 DB 가 바뀌었으면 떠 있던 메뉴를 닫는 `Sheet.dismissStale`)·규격(`PictureView`, 완독 도장을 찍는 줄 `StampedRow`, 기준 폭 572dp 포함)·상단·설정 저장 |
| PageKeys | 페이지 버튼(설정 › 화면 › 페이지 버튼): 정한 앞 쪽·다음 쪽 키 판단(`direction`, 소리가 나는 동안의 음량 키는 빼고)·키 이름, 화면·메뉴마다 키 받기 `PageKeyHandler`(넘길 목록이 있을 때만 먹는다. 화면은 `EinkActivity.onPageKey`, 메뉴는 `Sheet.pages`) |
| Palette | 색(설정 › 화면 › 색): 흑백·컬러('색면의 서가'). 표지·아이콘 컬러 여부, 화면 묶음(`Area`)마다 색면·큰 제목 색, 완독(띠·도장)·큰 별 색. `Palette.current` 는 EinkActivity 가 정한다 |
| Device / Crema | 기기 종류(크레마·메이북·기타)와 기기마다 다른 동작(빠른 설정·조명·시스템 속성·BOOX 얼린 앱 열기·이 앱이 BOOX 얼리기 대상인지 `booxAutoFreeze`) / 크레마 전용 화면 보정(대비·가려지는 아래·로고) |
| Update / UpdateSheets | 설정 › 업데이트·홈의 새 판 알림: GitHub 최신 릴리스 확인, APK 받기·확인(크기·SHA-256·서명 이력), 설치 세션·설치 창, 설치 뒤 홈 다시 띄우기 / 두 화면이 같이 쓰는 창(바뀐 점·`설치`·허용 안내·받는 중·실패, 홈의 `새 버전이 나왔습니다`) |
| Storage / Db | 기기 보호 저장소와 예전 저장소에서 옮기기 / 앱 데이터 DB(SQLite)와 예전 JSON 옮기기 |

## 자동 추가에 앱을 더할 때

- **다른 앱 파일은 건드리지 않는다.** 바뀌는 것은 새 앱 파일(`readers/<앱>.kt`: `ReaderApp` 선언과, 필요하면 `ScreenReader` 를 상속한 어댑터)·그 시험·등록 자리 셋(`ReaderApps.ALL`·`res/xml/reader_watch.xml` 의 packageNames·`ReaderGuide.APPS` 와 그 문자열, 빠지면 `GuideAppsTest`)·문서뿐이다(README 두 벌의 지원 앱 표도. 아이콘은 홈페이지 `site/img/apps/` 에 넣고 `og.py` 로 `docs/screenshots/app-icons/` 에). `git diff --stat` 로 확인한다.
- 공통 코드(`ReaderApp`·`ReaderAdapter`·`ScreenReader`·`AppContext`·`BookRecorder`·`WatchCore`)에 없는 능력이 필요하면 **기본값이 아무것도 안 하는(지금 동작인) 자리를 더하기만** 한다. 공통 기능(책 파일 표지 등)을 넓힐 때도 같다: 공통 코드가 모든 앱의 동작을 한꺼번에 바꾸지 않게 공통 코드는 도구(책 파일 표지 길 `BookFiles.BOOKS`·`WITH_COMICS`)만 두고, 어느 것을 쓸지는 앱이 자기 파일에서 고른다(`fileCovers`). 공통 흐름은 그 책의 앱이 고른 것을 부를 뿐, 파일 종류·앱 이름으로 가르지 않는다. 이미 있는 훅의 때·순서·기록 규칙·바탕 흐름은 앱 추가로 바꾸지 않는다(바꿔야 하면 따로 하는 공통 변경이고 모든 앱을 시험한다). 공통 코드에 앱 이름·패키지를 넣지 않는다(`CommonCodeTest`).
- 같은 패키지가 업데이트로 다른 앱이 되면(북커스 2.0) 옛 판 앱 파일은 그대로 두고 새 앱 파일을 더해 `handles` 로 깔린 판을 맡게 한다(`ReaderApps.ALL` 에서 앞에, 안내에도 판을 괄호에 적어 따로). 옛 판 APK 로 그 앱 파일의 화면·요소 이름이 그대로인지 본다(`aapt2 dump`).
- 연 책 파일 경로를 알려 주지 않는 앱이어도 저장소 전체를 훑어 찾지 않는다(아래 `지금까지 정한 것`).
- 두 앱 이상이 같은 규칙을 쓰면 `readers/` 의 도우미로 두고 머리에 쓰는 앱을 적는다(`DetailReader`·`DataAsk`). 새 앱이 조금 다른 규칙이 필요하면 도우미를 고치지 않고 그 앱 파일에 따로 둔다.
- 에뮬 시험: `tools/emu/watch-cycle.sh`·`watch-e2e.sh <순서>`(docs/emulator-test-cases.md `M. 자동 추가 앱별`), 새 앱의 순서는 `tools/emu/watch-flows/` 에 더한다.

## 지금까지 정한 것(요지, 자세한 건 DESIGN.md)

- 전자잉크 원칙: 애니메이션 없음, 스크롤 대신 페이지, 바뀐 것만 다시 그림, 그리기 전에 채워 빈 화면 한 번 그리지 않기.
- 색: 처음은 흑백. 컬러('색면의 서가')는 사용자가 고른다(기기로 판단하지 않음). 화면마다 머리에 옅은 색면 하나와 큰 제목 색, 완독은 어디서나 주홍. 본문 글자·선·작은 별은 무채색, 그래프는 책별로 나누지 않는다(DESIGN.md `색`).
- 책: 권수 제한 없음(1.5.3 까지 10권), 홈에는 맨 앞 책과 `함께 읽는 책` 3칸(3권씩 쪽으로 넘김, 그 줄을 밀면 책을, 다른 곳을 밀면 자주 쓰는 앱을). 빼는 건 사용자만(자동으로 밀어내지 않는다). 맨 앞 책이 크게(표지가 남는 높이만큼), 책이 없으면 스켈레톤(회색 표지 + `+`, 회색 막대).
- 자주 쓰는 앱: 직접 정한 순서, 높이는 목록 3줄로 고정(격자 4×2, 칸 가운데 정렬. 새로 깐 앱은 격자가 기본, 1.5.12 까지 쓰던 사람은 목록 그대로). 여러 쪽이면 제목 오른쪽 끝 `‹ 1 / 2 ›`. 이름을 따로 붙일 수 있다(`favorite_names`, 모든 앱에는 원래 이름. 빼면 지운다).
- 글자 크기·줄 간격(설정 › 화면 › 글자 · 간격, 슬라이더 10% 씩): 글자 70~150%(기기 글꼴 크기에 곱한다, `Ui.textScale`·`setSp`·`spf`), 줄 간격 80~150%(목록·메뉴·설정 줄 높이, `Ui.rowScale`·`rowDp`). 둘 다 100% 면 1.5.0 과 같은 화면. 화면에 맞춰 자동으로 줄이지 않는다(사용자가 미리 보기를 보며 고른다). 홈은 틀(상단·표지·함께 읽는 책·자주 쓰는 앱·하단 줄) 높이를 100% 기준으로 고정하고 글자만 키운다(줄 간격은 홈에 안 씀). 칸에 다 들지 않는 줄은 그리지 않는다(`FitColumn`). 기기가 앱의 밀도를 덮어썼으면(BOOX 앱별 DPI) 홈에 `화면이 크게 보입니다` 창(`다시 보지 않기`)을 띄우고 틀은 1.5.3 까지처럼 그린다.
- 바형 화면(긴 변 ÷ 짧은 변 1.5 이상, `Ui.tallScreen`. Tango·Palma 등 2.0): 홈 함께 읽는 책 표지 두 배(글자는 아래, 가운데), 독서 기록은 남는 높이가 있으면 달력과 그래프를 함께, 합계와 이번 주 읽은 책은 위아래로(폭이 좁은 화면도), 홈 하단 오늘 읽은 시간은 `오늘` 없이. 시험은 `Tango_A14` 에뮬레이터(824×1648, 400dpi, API 34).
- 가로 화면(1.5.4 까지는 세로 고정): 세로의 칸 모양은 그대로 두고 남는 폭에 칸을 나란히(홈 책 | 자주 쓰는 앱, 독서 기록 요약 | 달력(넓으면 그래프도), 아래 메뉴 정보 | 항목, 설정 묶음 두 칸, 격자는 표지 크기 그대로 열을 늘림). 바형도 같은 규칙. 판단은 `landscape()`(폭 > 높이), 자세한 건 DESIGN.md `가로 화면`. 에뮬은 `settings put system user_rotation 1`(자동 회전 끄고)로 돌린다.
- 하단 줄 56dp(모든 화면). 홈 가운데에 `오늘 42분 · 5일째`(바형 화면은 `42분 · 5일째`), 누르면 독서 기록.
- 읽는 책 자동 추가·오늘 읽은 시간은 앱에 켜짐 값을 두지 않고 시스템 값(접근성 켜짐 / **사용 기록 액세스**(`PACKAGE_USAGE_STATS`) 허용)이 곧 켜짐이다(어긋나지 않게). 어떤 책인지는 접근성 서비스가 그 앱에서 마지막으로 편 책으로 나누고(그 앱에서 편 책을 한 번도 몰랐으면 앱별. 서비스가 꺼진 동안 읽은 시간은 책 없이 하루 합계·연속일에만 들어간다). 읽는 화면은 클래스 이름 규칙(`READER` 정규식)으로 가린다. 연속일 = 5분 이상 읽은 날. 하루 목표 기본 30분(설정, 없음 / 15분~1시간 / 직접 입력 1~300분). 바꿔도 지난날은 그날의 목표로 센다.
- 기록은 처음 켤 때 7일 전까지 채우고 그날을 기록 시작일로 둔다(그 전은 '기록 없음').
- KOReader 는 화면(접근성)으로 아무것도 알 수 없어 '모든 파일 액세스'(설정 › 독서 › 책 파일 읽기)로 KOReader 가 남기는 파일을 읽는다: 어떤 책은 `koreader/history.lua` 맨 앞 줄, 열림·닫힘은 그 줄의 때와 책 파일의 접근 시각(KOReader 가 열 때만 맞춘다, 저장소는 noatime), 제목·진행률은 책 옆 `.sdr`. 권한 없는 길(koreader 폴더만 허락·플러그인·KOSync)은 조사 뒤 쓰지 않기로 했다(docs/READER_APPS.md).
- **저장소 전체를 훑어 책 파일을 찾지 않는다**: 책 파일은 이북 앱이 알려 주는 경로(앱 기록·데이터 창구·화면에 보이는 위치·홈에서 연 파일)로만 쓰고, 모르면 쓰지 않는다(표지는 화면에서 잘라 오고, 홈에서는 앱을 연다). 이름·제목으로 저장소를 훑으면 같은 이름의 다른 파일(만화 `01.cbz`)·같은 책의 사본을 가를 수 없고 SD 카드는 찾지 못한다. 그래서 문리더(메뉴 제목으로 EPUB 찾기)·네오리더(파일 이름으로 다시 찾기)의 저장소 찾기를 뺐다(2026-11-01). 이북 앱의 데이터 폴더를 알려진 자리나 얕은 몇 단계에서 찾는 것(Readest·Mihon)은 책 파일 찾기가 아니라 괜찮다.

## 일하는 방식(사용자 선호)

- 답은 한국어로. 짧게, 결과·확인한 것 위주.
- "시안 보여줘"면 **앱을 건드리지 말고** 그림(PIL 등)으로만 보여 준다. 정한 뒤에 구현한다.
- 디자인을 바꾸면 기기에 설치하고 캡처로 확인한다.
- 커밋은 요청할 때만. 메시지는 한국어, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. 푸시는 하지 않는다.
- `/audit --fix` 를 자주 쓴다(버그 → 불필요 → 리팩토링 순, 찾은 것 바로 고침).
- 완독 도장 `drawable-nodpi/stamp_completed.png` 는 Pixabay 그림을 고친 것(`tools/stamp_completed.py`, 출처는 README 라이선스 절). 원본 파일은 저장소에 넣지 않는다(Pixabay 는 원본 그대로 따로 배포하는 것을 금지).
- 다른 앱 아이콘·책 표지가 들어간 시안 이미지는 저장소에 넣지 않는다(`design-review/`, `design-*.svg` 는 git 제외).

## 남은 일·아이디어

- 아이콘 크기 맞추기: 아이콘마다 여백이 달라 크기가 들쭉날쭉하다. 보이는 부분만 잘라 꽉 채우는 방법을 제안했고 답을 기다리는 중.
- 책 끝내기 나머지: `다음에 읽을 책` 목록. (완독 자동으로 묻기는 하지 않기로 함)
- 기록 내보내기(백업)·기기 옮기기: 추후. 앱을 지우면 기록(DB)이 모두 사라진다. 계획은 **docs/backup-plan.md**(교체·여러 기기 중 하나 정리, 동기화는 안 함).
- 책 바로 열기(조사만 함, 보류): 사용자는 주로 **교보도서관·밀리의서재**를 쓴다. 둘 다 특정 책 바로 열기는 안 된다(교보도서관은 그냥 열면 서재, 밀리는 코드가 암호화돼 확인 불가).
  - 알라딘(`aladinreader://?view=viewer&itemid=…&isShortcut=true`)·리디(실행 extra `book_id`)는 특정 책을 열 수 있다. 교보eBook은 `kyoboebookeink://mylibrary`(서재)까지.
  - 알라딘·리디·교보eBook은 "홈 화면에 바로가기 추가"를 보내므로, 런처가 핀 바로가기(`ACTION_CONFIRM_PIN_SHORTCUT`)를 받으면 제목·앱·바로 열기 링크를 얻는다. 표지는 알라딘만 쓸 만하고(리디는 아래가 잘림, 교보는 찌그러짐) YES24 검색으로 보완.
  - 받아 둔 책 파일은 다른 앱 저장소(안드로이드 11 제한)·DRM 이라 읽을 수 없다.
- 첫 설치 개선 후보: 빈 자주 쓰는 앱의 추가 동작, 크레마 기본 앱 기본 숨김, 기본 홈 앱 안내. (자동 추가·읽은 시간 켜기는 ⑭ 시작하기로 했다)
- 기기 버튼으로 쪽 넘기기(이슈 #9): 설정 › 화면 › 페이지 버튼으로 구현했다(다음 판, `PageKeys`, 동작·한계는 DESIGN.md 설정 절). 남은 것: 메이북(291·292)·음량 키 기기 실기기 확인, 크레마 페블에 영향 없는지 확인.
