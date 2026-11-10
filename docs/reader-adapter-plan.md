# 읽는 책 자동 추가: 앱별 코드 분리 계획

상태: **구현함**(2026-10-24, 아래 `구현 메모`). codex(gpt-6.1-sol/high) 검토 1차(2026-10-23, needs_work 72, 지적 13개)와 2차(needs_work 83, 지적 5개)·3차(needs_work 87, 지적 4개)·4차(needs_work 89, 지적 3개)·5차(needs_work 89, 지적 2개)·6차(needs_work 90, 지적 2개)·7차(needs_work 91, 지적 3개)·8차(needs_work 93, 지적 3개)를 모두 반영했다.
8차 정리(2026-10-24): 7차 판의 설계(앱마다 어댑터, 공통 WatchCore·BookRecorder)는 그대로 두고, 문서 전체를 지금 코드(`ReaderWatch.kt`·`ReaderSpec.kt`·`ReaderApps.kt`·`Apps.kt`·`Reading.kt`)와 대조해 어긋난 곳을 한 번에 고쳤다.
앱을 더할 때 다른 앱 파일을 건드리지 않는다는 규칙을 확인 항목으로 넣었다(`앱을 더하는 법`, 단계 통과 조건). 무엇을 고쳤는지는 `검토 반영` 의 `8차 정리` 표.
**【권고로 정함】** 표시는 열린 질문이던 것을 이 계획의 권고안으로 정한 자리다. 사용자가 다르게 정하면 그 자리만 바꾼다.

## 왜

- 자동 추가는 접근성 서비스 `ReaderWatchService`(`ReaderWatch.kt`, 1,329줄)와 `readers/` 의 파일 25개(앱 파일과 공통 도구 `ReaderSpec`·`ReaderApps`·`Nodes`·`BookFiles`·`LuaData`)로 되어 있다.
  앱 파일은 "화면에서 무엇을 읽나"(요소 이름·칸 읽기)만 정하고, **어떤 책을 폈는지 판단하고 기록하는 일은 모두 서비스**가 한다.
- 그래서 화면 말고 다른 데서 책을 아는 앱이 생길 때마다 `ReaderSpec` 에 표시가 하나 늘고, 서비스에는 그 앱 종류만을 위한 갈래·기억·상수가 들어갔다.
  지금 서비스 안의 앱 종류별 처리:

| 앱 | `ReaderSpec` 표시 | 서비스에 들어간 처리 | 서비스에 든 그 앱의 기억 |
|---|---|---|---|
| KOReader | `fileState` | `syncFiles`·`fileOpened`·`markFileViewer`·`newFile`·`saveFile`, 내용 바뀜 무시, `closeHeldViewers` 의 '열림' | prefs `sync_<앱>`·`file_<앱>` |
| Readest | `viewerKey` | `readKeyed`·`newKeyed`·`keyedBook`·`keyCover`·`cropSpots`(찍기 전·찍은 뒤), 누른 칸을 열쇠 볼 때 씀 | `unknownKey`·`keyCovers`, prefs `key_…`·`book_key_…`·`last_key_…` |
| 네오리더 | `viewerTabs`·`fileViewer` | `enterTab`, `fileRetries`, 파일 모르면 넣지 않음 | `unknownTab`, prefs `tab_book_<탭>` |
| 이지뷰어·채리뷰어 | `confirmsOpen`(+`bookFile`) | `rememberOpening`·`openingFor`·`viewerFileAt`·`fileGuess`·`inSameFolder`·`rememberShelfFile`·`lastShelfBook`·`retitle`·`fileInfo`, 홈에서 연 파일 `homeOpened` | `openingFiles`·`viewerFiles`·`viewerFileBooks`·`clickedFiles`, prefs `shelf_file_…`·`shelf_book_…`·원격 파일 `remote_…`·`remote_of_…` |
| BookFusion·밀리(e-ink) | `clickOnly`·`webBook` | 책 상세 기억·사라진 뒤 3초·누를 때 상세 표지 찍기 | `webBook`·`webGone`·`lastWebRead` |
| 알라딘 | `recent` | 돌아와서 '최근 읽은 책' 줄로 판단 | `readSince`·`readerAt`·`readKnown`·`recentWait`, prefs `recent_<앱>` |
| Libby·Readest | `viewerHolder` | `withHolder`·`checkHolder`, 읽는 화면 열림·닫힘 표시 | `holderSpec`·`lastHolderCheck` |
| 문리더 | `searchesBookFile` | `searchFile`(저장소에서 EPUB 찾기) | `fileSearched` |
| my YES·밀리·YES24 도서관 등 | `openOnViewer` | 누른 칸을 읽는 화면이 열릴 때 씀, 기간 지난 책 | `clicked` |
| 네오리더·우주 | `fileBook` | `fileBook`(묻는 횟수 `fileTries`)·`knownTitle` | `fileTries`·`fileTitles` |
| 우주 | `fileData`·`fileOpener` | `dataCover`, 데이터 진행률 | `dataCovers` |

- 이 구조에서는 앱을 하나 더하거나 고칠 때 **모든 앱이 같이 쓰는 서비스**를 고치게 되고, 그 변경이 다른 앱에 닿지 않는지 매번 확인해야 한다.
  우주를 더하면서 실제로 그랬다(진행률 끝 단계를 고치다 모든 앱의 읽는 화면마다 DB 조회가 하나씩 늘었다).
- 앱의 판단과 기억이 앱 파일과 서비스에 반씩 나뉘어 있어, 한 앱의 동작을 알려면 두 파일을 오가야 한다.

## 목표

1. **앱의 판단과 기억은 그 앱 파일에만** 둔다. 공통 코드(서비스·`WatchCore`·`BookRecorder`·`ScreenReader`)에는 앱 이름도, 앱 종류를 가르는 갈래도 두지 않는다.
2. 서비스는 알림을 받아 그 앱 파일을 부르고, 앱 파일은 공통 기록기(`BookRecorder`)를 불러 **기록한다**. 기록 규칙(책 찾기·목록·진행률·표지·편 책 기록·읽은 시간 표시)은 기록기 한 곳에.
3. 앱 동작을 고치면 **그 앱 파일과 그 앱 시험만** 바뀐다.
4. **앱을 더할 때 다른 앱 파일은 바뀌지 않는다.** 바뀌는 것은 새 앱 파일·그 시험·등록 자리(아래)뿐이다. 공통 코드에 없는 능력이 필요하면 공통 코드에 "기본값은 아무것도 안 함"인 자리를 더한다(`앱을 더하는 법`).
5. 동작은 그대로 둔다. DB·prefs 의 값과 이름도 그대로라 덮어 깔기와 판 되돌리기(같은 DB 버전 안)에서 기록이 이어진다.

**등록 자리(앱 파일 밖에서 바뀌는 곳)**: `ReaderApps.ALL`(목록)·`res/xml/reader_watch.xml` 의 packageNames·`ReaderGuide.APPS` 와 그 문자열·문서.
이것들은 동작이 아니라 "이 앱이 있다"는 선언이다. 빠지면 이미 있는 단위 테스트가 알린다(`GuideAppsTest`: 안내가 모든 앱을 덮는지, `reader_watch.xml` 의 packageNames 가 `ReaderApps` 의 패키지와 같은지).

### 단계 통과 조건

- 단위 테스트·릴리스 빌드 통과, 그 단계가 건드린 앱의 에뮬 시험이 1단계 기준과 같다.
- **바뀐 파일 확인**(4단계부터): 그 단계의 diff 가 그 앱 파일·그 앱 시험·`Legacy` 에서 그 앱 갈래 지우기·문서만 건드린다. 다른 앱 파일(`readers/<다른 앱>.kt`)이 바뀌면 통과가 아니다.
  공통 코드가 바뀌었으면 "기본값은 아무것도 안 함"인 자리를 더한 것뿐인지, 다른 앱의 단위 테스트·연결 시험을 고치지 않고 통과하는지 본다.
- 에뮬에 없는 앱(교보eBook e-ink, 에뮬에 없으면 네오리더, 대출이 필요한 Libby 등)은 **"실기기 확인 대기"** 로 적고 다음 단계로 간다.
  **【권고로 정함】** 그 앱을 옮긴 단계가 든 판은 사용자가 실기기에서 확인하기 전에는 릴리스하지 않는다(로컬 커밋은 쌓는다).

## 앱을 더하는 법(옮긴 뒤)

1. `readers/<앱>.kt` 에 `ReaderApp`(선언)과 어댑터를 둔다. 화면만 읽는 앱은 `ScreenReader` 의 설정(요소 이름)만 주고, 다른 길로 책을 아는 앱은 `ScreenReader` 를 상속해 훅을 덮는다.
2. 등록 자리 셋(`ReaderApps.ALL`·`reader_watch.xml`·`ReaderGuide.APPS`)과 문서(READER_APPS.md·README·DESIGN.md·TROUBLESHOOTING.md 필요하면).
3. 그 앱의 단위 테스트(가짜 `AppContext`·가짜 기록기)와 에뮬 시험.
4. **확인**: `git diff --stat` 에 다른 앱 파일이 없다. 공통 코드가 바뀌었으면 아래 규칙에 맞는지.

**공통 코드를 바꿔야 할 때**(새 앱에 지금 없는 능력이 필요할 때):

- 공통 코드(`ReaderApp`·`ReaderAdapter`·`ScreenReader`·`AppContext`·`BookRecorder`·`WatchCore`)에는 **더하기만** 한다: 새 훅·선언·기록기 함수는 기본값이 "아무것도 안 함"(또는 지금 동작)이라 다른 앱의 동작이 바뀌지 않는다.
- 이미 있는 훅의 부르는 때·순서, 기록 규칙, 바탕 흐름은 바꾸지 않는다. 바꿔야 하면 그건 앱 추가가 아니라 따로 하는 공통 변경이고, 모든 앱을 시험한다.
- 공통 코드에 앱 이름·패키지·그 앱만의 갈래를 넣지 않는다(5단계의 단위 테스트가 잡는다).
- 두 앱 이상이 같은 규칙을 쓰면 `readers/` 의 도우미로 둔다(`DetailMemory`: BookFusion·밀리 e-ink, `DataAsk`: 우주·네오리더). 도우미 머리에 쓰는 앱을 적고, 도우미를 바꾸면 쓰는 앱을 모두 시험한다.
  새 앱이 조금 다른 규칙이 필요하면 도우미를 고치지 않고 그 앱 파일에 따로 둔다.

## 하지 않는 것

- 앱별 동작 바꾸기(이번은 자리 옮기기만). 옮기다 찾은 버그는 따로 적고, 고치는 것은 옮긴 뒤 따로 한다.
- DB 버전 올리기.
- 읽은 시간 계산(`ReadingLog`)·책 목록(`BookShelf`)의 규칙 바꾸기.
- 화면 캡처(표지 자르기)의 방식 바꾸기. 서비스가 계속 맡는다(접근성 서비스만 찍을 수 있다).
- 백그라운드 작업이 서비스가 끝난 뒤 끝나도 결과를 적용하는 지금 동작 바꾸기(아래 `inBackground`).

## 목표 구조

```
안드로이드 접근성 알림
        │
        ▼
ReaderWatchService (안드로이드 연결만: 알림 → WatchCore, 액티비티인가(PackageManager), 창·맨 앞 창, 화면 캡처, 서비스 생명)
        │
        ▼
WatchCore (공통, 앱 종류 갈래 없음. 시계·예약을 주입받아 서비스 없이 Robolectric 단위 테스트)
  - 앱마다 지금 화면 기억, 멈춤·잠잠함·재확인 예약, 훅 부르는 순서, 열림·닫힘 표시(선언대로)
        │ 훅 호출(AppContext)
        ▼
ReaderAdapter (앱마다, 서비스가 붙을 때 새로 만듦. 그 앱의 판단과 기억. 모두 ScreenReader 를 상속)
        │ ctx.opened·ctx.unknown 으로 기록하고 결과(책)를 돌려받음
        ▼
BookRecorder (공통 기록 규칙: BookShelf·ReadingLog·잘라 둔 표지)

ReaderApp (앱마다, 서비스와 상관없는 선언: 화면 역할·읽은 시간 방식·앱 목록 항목·책 파일 필요·홈에서 열기)
  ← ReadingLog·AppStore·시작하기·자동 추가 안내도 서비스 없이 이것을 읽는다
```

### 상태의 소유(B6·G4 반영)

| 무엇 | 어디 | 수명 | 예 |
|---|---|---|---|
| 앱의 선언 | `ReaderApp`(앱 파일의 `object`, 바뀌는 값 없음) | 프로세스 | 패키지, `role`, `viewerTime`·`markedScreens`, `readsContent`, `fileEntry`, `needsBookFiles`, `open(…)`, 요소 이름 |
| 앱의 판단 기억 | `ReaderAdapter`(그 앱 `ReaderApp.newAdapter()` 가 만든 인스턴스) | 서비스 연결 한 번 | 누른 칸, 열기 확인 창, 탭별 모르는 책, 열쇠 몰라 책 모름, 바로 자르기 횟수 |
| 여러 흐름이 같이 쓰는 기억 | `WatchCore`(앱마다) | 서비스 연결 한 번 | 지금 화면, 읽는 화면이 앞에 온 때, 책 모름이 된 때 `unknownOpen`, 책을 편 때 `lastOpenedAt` |
| 오래 남는 값 | `ctx.prefs`(지금 `reader_watch` prefs 의 같은 키) | 기기 | 열쇠 ↔ 책, 탭 ↔ 책, 서재에서 마지막으로 편 파일 |
| 바깥 데이터 캐시 | 앱 파일의 `object` 안 캐시(지금처럼) | 프로세스 | Readest 데이터·전체 쪽 수(`totals`), 우주 기록 파일, KOReader Lua |
| 홈 → 서비스 신호 | `HomeSignals`(프로세스, 지금 `homeOpened` 를 옮김) | 프로세스 | 홈에서 연 파일(이지뷰어)·링크로 연 책(Wattpad, `bookOpened`) |

- **판단 기억은 인스턴스에만** 둔다(서비스가 다시 붙으면 사라지는 지금 동작 그대로).
- **바깥 데이터 캐시는 프로세스 `object` 에 남겨도 된다**: 판단이 아니라 다른 앱 파일을 읽은 결과이고, 바뀌었는지(시각·크기)로 다시 읽는다.
  Readest `totals`(책마다 전체 쪽)는 지금도 서비스가 다시 붙어도 남아 쪽 진행률을 이어 셈한다 — 그대로 둔다(동작 보존).
- `HomeSignals` 는 지금 `ReaderWatchService.homeOpened`(companion)와 같은 프로세스 수명이다. 이지뷰어(파일)·Wattpad(링크로 연 책) 어댑터만 읽는다.
- 지금 서비스 전체에 하나인 간격 기억(`lastWebRead`·`lastEagerRead`·`eagerTries`)은 그 일을 하는 어댑터로 간다. 다른 앱으로 넘어가면 그 앱 화면 알림(창 바뀜, `force`)이 먼저 와
  지금도 그때 다시 정해지므로 앱마다 두어도 결과가 같다. 화면 캡처 간격 `lastShot` 은 서비스에 남는다(캡처는 서비스 일).

### `ReaderApp`: 서비스 밖에서도 쓰는 선언(G4 반영)

```kotlin
abstract class ReaderApp(val pkg: String /* , 요소 이름 등 지금 ReaderSpec 의 읽기 설정 */) {
    /** 화면 이름 → 서재/읽는 화면/그 밖(지금 shelves·isViewer. BookFusion 은 매니페스트 속성) */
    open fun role(context: Context, cls: String): Role
    /** 읽은 시간: SCREEN(읽는 화면이 앞에 있던 동안), MARKS_CLOSED(표시로 자름, 없으면 안 셈: Libby·Readest), MARKS_OPEN(표시로 자름, 없으면 다 셈: KOReader) */
    open val viewerTime: ViewerTime get() = ViewerTime.SCREEN
    /** 표시로 자르는 액티비티(지금 ReaderApps.hosts: Libby·Readest 는 서재 액티비티, KOReader 는 읽는 화면 액티비티). viewerTime 이 SCREEN 이면 비어 있다 */
    open val markedScreens: Set<String> get() = emptySet()
    /** 내용 바뀜 알림으로 화면을 읽는다(기본 true). KOReader 는 false: 앞에 올 때만 데이터로 맞춘다(지금 fileState 면 내용 바뀜 무시) */
    open val readsContent: Boolean get() = true
    /** 읽는 화면이 서재 액티비티 안의 요소라 화면 이름이 알림 없이 바뀐다(Libby·Readest, 지금 viewerHolder): 내용 바뀜마다 screenName·마지막 재확인 */
    open val screenInsideActivity: Boolean get() = false
    /**
     * 읽는 화면에 다시 들어와도 "제목 못 읽은 칸으로 들어와 책 모름이 된 때"(unknownOpen)를 지우지 않는다(네오리더, 지금 viewerTabs):
     * 같은 모르는 탭으로 돌아와도 처음 시각을 남겨, 나중에 메뉴·데이터로 책을 알면 그 앞 시간까지 그 책으로 센다(탭별 판단은 네오리더 onEnterViewer)
     */
    open val keepsUnknownOnReenter: Boolean get() = false
    /** 실행 화면이 없어 앱 목록 항목을 따로 두는 앱(네오리더): 파일을 받는 화면과 이름 */
    open val fileEntry: FileViewer? get() = null
    /** 책 파일 읽기가 필요(시작하기·안내·읽는 화면에서 꺼져 있으면 홈에서 묻기). 시작하기의 정렬은 `fileOrder`(KOReader 0, 나머지 1) */
    open val needsBookFiles: Boolean get() = false
    open val fileOrder: Int get() = 1
    /** 홈에서 이 책을 연다(아래 `홈에서 열기`) */
    open fun open(ctx: HomeContext, book: ShelfBook): OpenResult = OpenResult.Launch
    /** 파일을 모르거나 없을 때 그 앱 데이터에서 찾은 책 파일들(지금 bookFiles 그대로, 네오리더). 화면 스레드에서 부르지 않는다 */
    open fun bookFiles(context: Context, titles: Collection<String>): List<String> = emptyList()
    /** 잘라 낸 그림이 그 앱의 기본 그림인가(지금 placeholderCover, 이지뷰어). 뒤 스레드에서 부른다 */
    open fun placeholderCover(context: Context, crop: Bitmap): Boolean = false
    abstract fun newAdapter(): ReaderAdapter
}
```

- 서비스 밖 소비자 대응(3단계에서 바꾼다):

| 지금 | 옮긴 뒤 |
|---|---|
| `ReaderApps.isViewer` → `ReadingLog.isReaderScreen`(읽는 화면 또는 `READER` 규칙) | `role(…) == VIEWER`(규칙 쪽은 그대로) |
| `ReaderApps.hostsViewer(cls)`(`ReadingLog.feed`) | 모든 앱의 `markedScreens` 합 |
| `ReaderApps.shownByDefault(pkg)` | `viewerTime == MARKS_OPEN` |
| `ReaderApps.fileApps`, 시작하기의 `sortedByDescending { it.fileState }` | `needsBookFiles`, `fileOrder` |
| `AppStore.fileReaders` | `fileEntry` |
| `AppStore.openBook` 의 앱 종류 갈래(열쇠·`opensBookFile`·`fileOpener`·`fileViewer`) | `open(…)` 의 결과만 처리 |
| `AppStore.findBookFile` 의 `spec.bookFiles` | `ReaderApp.bookFiles`(고르기·저장소 같은 이름 찾기는 `AppStore` 에 공통으로 남음) |

### 기록기: `BookRecorder` — 결과(책)를 돌려받는다(G1·B1·B2 반영)

**【권고로 정함】 Fact 목록을 돌려주는 대신 앱 파일이 기록기를 부르고 책을 돌려받는다**(편 책 기록만은 `ctx.opened`·`ctx.unknown` 을 거친다 — 아래 `기록과 기억 비우기`). 이유: 기록 직후 그 책 id 로 앱 기억을 고쳐야 하는 자리가 셋 있다
(Readest 열쇠 ↔ 책, 네오리더 탭 ↔ 책, 이지뷰어 `shelf_book`). Fact 목록이면 결과를 돌려받을 길을 또 만들어야 한다.
테스트는 가짜 기록기(호출을 적어 두는 것)로 한다.

```kotlin
interface BookRecorder {
    /**
     * 책을 폈다. 넣거나 되살리거나 맨 앞으로 옮긴 그 책을 돌려준다(지금 bookOpened 와 같은 일). 안의 순서는 지금 그대로(6차 B1):
     * ① 찾기·넣기(맨 앞으로·진행률·반납일·저자) → ② [steps].found(book) → ③ 기록(WatchLog)·편 책 기록 → ④ [steps].logged(book)
     * → ⑤ 잘라 둔 표지(아래. 그 책의 파일이 표지를 꺼낼 수 있는 EPUB·그림이면 버린다 — 파일에서 원본 표지를 꺼내게).
     * 단계마다 실패를 다루는 방식은 지금 그대로(아래 `단계별 실패`, 7차 B2): ①②④ 의 예외는 밖으로 내보내 그 뒤 단계를 하지 않고,
     * ③ 편 책 기록 저장과 ⑤ 표지 저장의 실패는 기록기 안에서 삼켜 다음 단계로 간다. 실패 전에 끝난 저장은 되돌리지 않는다.
     */
    fun opened(pkg: String, seen: Seen, at: Long?, target: Target, coverKey: String? = null, steps: OpenSteps? = null): ShelfBook
    /** 여기부터 어떤 책인지 모른다(ReadingLog.bookOpened(pkg, "", "", at)) */
    fun unknown(pkg: String, at: Long)
    /** 진행률·반납일·저자(음수·빈 값은 두기, 지금 BookShelf.updateStatus) */
    fun status(bookId: String, progress: Int, due: Long = -1, author: String = "")
    /** 서재 한 화면의 것을 한 번에(지금 updateStatuses) */
    fun statuses(list: List<BookShelf.Status>)
    fun setFile(bookId: String, path: String)
    fun setTitle(bookId: String, title: String)
    fun setAuthor(bookId: String, author: String)
    /** 표지가 없을 때만 붙인다. 저장 실패는 삼킨다(지금 `if (!hasCover) runCatching { setCover }`) */
    fun cover(bookId: String, jpeg: ByteArray)
    /** 잘라 둔 표지(아직 목록에 없는 책, 최근 12권). 열쇠는 앱이 정한다(제목 자리는 정리한 제목, 이지뷰어 파일 칸은 `file:` + 위치·이름) */
    fun pendingCover(key: String, jpeg: ByteArray)
    fun hasPendingCover(key: String): Boolean
    fun viewerShown(pkg: String, shown: Boolean, at: Long, current: Boolean = true)
    /** 편 책 기록의 책 id(목록에 없어도. 지금 ReadingLog.openBook) */
    fun openedId(pkg: String): String?
    /** 편 책 기록이 가리키는 읽고 있는 책(지금 recordedBook) */
    fun recordedBook(pkg: String): ShelfBook?
    /** 읽기만 하는 찾기(아래) */
    val lookup: BookLookup
}
/** 화면·데이터에서 본 것: 앱이 보여 준 제목, 저자, 진행률, 반납일 */
data class Seen(val title: String, val author: String, val progress: Int, val due: Long)
sealed interface Target {
    /** 제목으로 찾기(sameTitle: 공백 무시, 부제는 한쪽에만 있으면 무시. seenTitle). 나간 책은 같은 제목이면 되살린다 — 지금 byFile = false */
    object ByTitle : Target
    /**
     * 앱이 정한 책: [id] 의 책(읽고 있는 책이면 맨 앞으로, 나간 책이면 그 책만 되살림), null 이면 같은 제목이 있어도 새 책 — 지금 byFile = true.
     * [id] 의 책이 있으면(읽고 있는 책·나간 책) 기록에 쓰는 제목을 그 책의 제목으로 바꾼다(지금 bookOpened 의 seen)
     */
    data class Exact(val id: String?) : Target
}
/** opened 안에서 앱이 끼어드는 자리(그 기록 한 번만의 것이라 어댑터가 부를 때마다 만들어 넘긴다) */
interface OpenSteps {
    /** 찾기·넣기 직후, 편 책 기록 전(이지뷰어: 제목 바꾸기 retitle → 파일·책 정보·서재 파일 prefs rememberShelfFile) */
    fun found(book: ShelfBook) {}
    /** 편 책 기록 직후, 잘라 둔 표지 전(네오리더: 지금 화면이 읽는 화면 탭이면 탭 ↔ 책 setTabBook) */
    fun logged(book: ShelfBook) {}
}
/** 지금 BookShelf 의 읽기 함수와 서비스의 찾기 도우미를 모은 것(쓰지 않는다) */
interface BookLookup {
    fun byTitle(title: String): ShelfBook?          // 읽고 있는 책(findByTitle)
    fun get(id: String): ShelfBook?                 // 읽고 있는 책
    fun bookOf(id: String): ShelfBook?              // 읽고 있는 책, 없으면 나간 책 — 기록에서 지운(hidden) 책도(지금 BookShelf.bookOf 그대로. 되살리기는 addOpened 가 지운 책을 뺀다)
    fun byFile(path: String): String?               // idByFile
    fun byPath(path: String): String?               // 그림이면 폴더로(idByPath)
    fun withoutFile(title: String): String?         // 파일 없는 같은 제목(idWithoutFile)
    fun knownTitle(path: String, title: String): String  // 그 파일·파일 이름으로 넣은 책의 제목, 없으면 title(knownTitle)
    fun idsByFileName(name: String): Set<String>
    fun filePath(id: String): String?
    fun hasCover(id: String): Boolean
}
```

- **지금 동작 그대로의 대응**(B2): 어떤 앱이 어느 `Target` 을 쓰는지는 지금 코드가 정한 그대로 앱 파일에 옮긴다.

| 지금 부르는 곳 | 지금 | 옮긴 뒤 |
|---|---|---|
| 서재 칸·누른 칸·메뉴 제목(대부분의 앱) | `bookOpened(cell)` byFile=false | `ByTitle` |
| KOReader `fileOpened` | `bookOpened(Cell(knownTitle…), at)` byFile=false, 뒤에 `saveFile`(제목으로 찾은 책에 파일) | `ByTitle` 뒤 `lookup.byTitle(제목)` 에 `setFile` |
| Readest `newKeyed` | `bookOpened(Cell(title…), opened)` byFile=false, 뒤에 `recordedBook` 을 **다시 읽어** 열쇠 ↔ 책 | `ByTitle` 뒤 `afterOpened` 에서 `recordedBook` 을 다시 읽어 열쇠 ↔ 책 |
| Readest 열쇠로 아는 책 | `bookOpened(…, b.id)` | `Exact(b.id)` |
| 네오리더 탭 | `bookOpened(…, now, book.id)`, 안에서 편 책 기록 뒤 `setTabBook` | `Exact(id)` + `steps.logged` 에서 탭 ↔ 책 |
| 네오리더·우주 데이터 파일 | `bookOpened(…, bookId, byFile)` bookId = `idByFile ?: idWithoutFile`, 뒤에 `recordedBook` 을 **다시 읽어** 파일 | `Exact(lookup.byFile(path) ?: lookup.withoutFile(title))` 뒤 `recordedBook` 에 `setFile`(기록하지 않은 때도) |
| 이지뷰어 파일 칸 | `bookOpened(cell(file))` → `fileBookId`, 안에서 찾기·넣기 → `retitle` → `rememberShelfFile` → 편 책 기록 → 잘라 둔 표지 | 어댑터가 `fileBookId` 를 정해 `opened(Exact(id), coverKey = 파일 열쇠, steps.found = { retitle; rememberShelfFile })` |
| 이지뷰어 읽는 화면 파일 이름으로 정한 책 | `bookOpened(Cell(title), at, fileId, byFile = true)` | `Exact(fileId)` |

- 기록기는 앱 종류를 모른다. `Exact` 는 "앱이 이미 가른 책"이라는 뜻만 있다.
- **잘라 둔 표지**(B4, 지금 `bookOpened` 끝): `ByTitle` 이면 그 책 제목과 같은(부제·공백 무시) 잘라 둔 표지, `Exact` 면 `coverKey` 를 준 때만 그 열쇠의 것(이지뷰어 파일 칸). `Exact` 에 `coverKey` 가 없으면 붙이지 않는다(Readest·네오리더·우주, 지금 그대로).
  그래서 같은 제목·다른 폴더 파일 표지가 섞이지 않는다.
- **파일과 표지의 순서**(2차 B1, 6차 B1 로 고침): 이지뷰어가 `steps.found` 에서 **경로를 먼저 적으면**(제목 바꾸기 뒤, 편 책 기록 전) 기록기가 ⑤에서 잘라 둔 표지를 정할 때
  그 경로를 본다. 경로가 EPUB·그림이면 잘라 둔 작은 표지를 버려, 그릴 때 파일에서 원본 표지를 꺼낸다(`BookShelf.cover` 는 표지 파일이 있으면 파일에서 꺼내지 않으므로 순서가 바뀌면 원본을 못 쓴다).
  지금 이 순서인 곳은 이지뷰어 파일 칸뿐이다. KOReader·네오리더·우주는 지금처럼 `opened` 다음 `setFile` 이다 — 잘라 둔 표지를 붙이지 않아(`ByTitle` 인 KOReader 는 붙일 수 있지만 지금도 파일을 뒤에 적는다) 순서를 바꾸지 않는다.

### 훅: `ReaderAdapter` 와 부르는 순서(B3·G3 반영)

```kotlin
interface ReaderAdapter {
    /**
     * 그 앱의 화면이 [name] 으로 바뀌었다(지금 enterScreen). [again]: 같은 화면 알림이 한 번 더 온 것, [byWindow]: 창 바뀜 알림으로 들어옴(내용 바뀜으로 안 것이 아님).
     * **ScreenReader 가 final 로 구현하고 덮지 않는다**: 공통 전이(아래)를 한 뒤 읽는 화면이면 [onEnterViewer] 를 부른다.
     */
    fun onScreen(ctx: AppContext, name: String, from: String?, again: Boolean, byWindow: Boolean)
    /** 읽는 화면에 들어왔다(공통 전이 뒤): 그 앱의 읽는 화면 기억 비우기와 무엇으로 책을 기록하나(바탕: 누른 칸). 앱마다 다른 것은 여기서만 바꾼다 */
    fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {}
    /** 액티비티가 아닌 창이 떴다(대화상자. 지금 이지뷰어 열기 확인 창 rememberOpening) */
    fun onWindow(ctx: AppContext, cls: String?) {}
    /**
     * 화면 이름(screenInsideActivity 앱만 부른다, Libby·Readest): [activity](창 알림의 액티비티, 내용 알림이면 기억한 화면의 액티비티 부분, 모르면 null)
     * 안에 읽는 화면이 떠 있으면 `activity#요소`, 아니면 activity. 판단할 수 없으면(맨 앞 창이 그 앱이 아님) null — "지금 모름".
     * [activity] 가 null 이고 맨 앞 창이 그 앱이면 서재 액티비티가 하나뿐일 때 그것으로 본다(서비스가 그 앱이 떠 있는 동안 다시 붙음, 지금 checkHolder)
     */
    fun screenName(ctx: AppContext, activity: String?): String? = activity
    /** 서재가 보이는 동안 바로(멈추기 전): 책 상세 기억·'최근 읽은 책' 줄·바로 자르기(지금 readScreen 의 서재 갈래). 간격(1초)은 조각마다 [force] 가 아니면 */
    fun onShelfLive(ctx: AppContext, force: Boolean) {}
    /** 서재가 멈췄다(지금 settleShelf) */
    fun onShelf(ctx: AppContext) {}
    /** 무엇인가를 눌렀다. 서재에서 누른 것인지는 ctx.screen 으로(지금 확인 창 누름 + shelfClicked) */
    fun onClick(ctx: AppContext, click: Click) {}
    /**
     * 그 앱의 내용이 바뀌었다(내용 바뀜 알림, readsContent 앱만, 화면을 읽기 전에). [source] 를 부르면 바뀐 요소. 창 뿌리에서 훑어 닿지 않는 요소
     * (Compose 안의 웹 화면, 이씨오 범용뷰어 3.0)를 알림으로 붙잡아 두는 앱만 쓴다. 바탕: 아무것도 안 함(요소를 얻지 않는다). 2026-11-07 더함
     */
    fun onContent(ctx: AppContext, source: () -> AccessibilityNodeInfo?) {}
    /** 읽는 화면이 잠잠해졌다(지금 readViewer. 그때 그 앱 화면이 읽는 화면일 때만 부른다) */
    fun onViewer(ctx: AppContext) {}
    /** 홈으로 돌아왔다(지금 syncFileApps: KOReader·Readest) */
    fun onHome(ctx: AppContext) {}
    /** ctx.opened 가 기록하기 **전**: 그 앱의 판단 기억을 비운다(아래 `기록과 기억 비우기`) */
    fun beforeOpened(ctx: AppContext) {}
    /** ctx.opened 의 기록기가 **예외 없이 돌아온 뒤**: bookOpened 가 끝난 뒤에 하던 이어 두기(Readest 열쇠 ↔ 책). 기록 도중의 일은 OpenSteps */
    fun afterOpened(ctx: AppContext, book: ShelfBook) {}
    /** 화면에서 표지를 잘라도 되나(기본 true). ctx.capture 가 맨 앞에서 본다(지금 덮는 앱 없음. Readest 는 데이터에 표지가 없는 책만 찍게 찍기 전에 거른다) */
    fun capturesCovers(ctx: AppContext): Boolean = true
    /**
     * 화면을 찍은 직후(메인 스레드), 찍기 전에 정한 자리 가운데 아직 그 자리에 있는 것만 돌려준다(기본: 그대로).
     * ScreenReader 는 찍은 뒤 다시 보는 앱(recheckCovers, 기본은 자리로 칸을 찾는 cellsByPosition)이나 표지 위에 단추를 겹치는(coverOverlays) 앱이면 서재 칸을 다시 읽어 맞춘다(지금 captureCovers 의 still)
     */
    fun stillSpots(ctx: AppContext, spots: List<CoverSpot>): List<CoverSpot> = spots
}
/** 잘라 올 표지 자리. [key] 가 없으면 제목 자리(찍은 뒤 그 제목의 책에, 없으면 정리한 제목으로 잘라 둠), 있으면 그 열쇠의 자리([id] 의 책에, 없으면 [key] 로 잘라 둠 — 이지뷰어 파일 칸) */
class CoverSpot(val title: String, val rect: Rect, val id: String? = null, val key: String? = null)
```

**`ScreenReader.onScreen` 의 공통 전이**(지금 `enterScreen` 순서 그대로. WatchCore 가 먼저 그 앱의 지금 화면·`lastTransition`, 읽는 화면이면 `viewerOpenedAt` 을 적고 부른다):

1. [byWindow] 면 바로 자르기 횟수를 비운다(지금 창 바뀜에서만 `eagerTries.clear()`).
2. `viewerTime == MARKS_CLOSED` 면 읽는 화면인지(`role == VIEWER`)를 열림·닫힘 표시로(지금 `viewerHolder` 앱의 `viewerShown`).
3. `needsBookFiles` 이고 읽는 화면인데 책 파일을 읽을 수 없으면 홈에서 묻게 적는다(`wantFiles`).
4. 읽는 화면이 앞에 왔으면(`ReadingLog.isReaderScreen`: `role == VIEWER` 또는 이름 규칙 `READER` — 지금 그대로) —
   제목 못 읽은 칸을 120초 안에 눌렀으면 `unknownOpenAt = 지금` + `unknown(지금)`, 아니면 다시 들어온 것이면(`!again`) `unknownOpenAt` 을 지운다(`keepsUnknownOnReenter` 앱은 두기, 7차 B1).
   그 뒤 `onEnterViewer(ctx, from, again)`.

- 일괄 "`super` 먼저"를 `onScreen` 에 쓰면 바탕이 누른 칸을 먼저 기록해 책 상세로 연 책 대신 앞서 누른 칸이 들어가고, 그 기록의 `beforeOpened` 가 아직 쓰지 않은 상세 기억까지 지운다(3차 B1).
  그래서 `onScreen` 은 덮을 수 없게 하고, 앱마다 다른 것은 `onEnterViewer` 에서만 바꾼다.
- 잠잠 예약·`onViewer` 는 `role == VIEWER` 인 화면만(지금 `readScreen`·`readViewer` 의 `isViewer`). 4번의 "읽는 화면이 앞에 옴"은 이름 규칙도 보므로 조금 더 넓다 — 지금 그대로 둔다.

**부르는 순서**(WatchCore, 지금 `onAccessibilityEvent` 와 같다):

| 알림 | 순서 |
|---|---|
| 모든 알림(그 앱의 것) | 알림 수 기록(`WatchLog.event`) → 그 앱의 서재 멈춤 예약이 걸려 있으면 다시 건다(지금 `if (settleSpec?.pkg == spec.pkg) scheduleSettle`) → 아래 |
| 창 바뀜, 액티비티(서비스가 이름 앞부분·`isActivity` 로 판단) | (`screenInsideActivity` 앱이면 `screenName(ctx, 액티비티)`, null 이면 기억한 화면이 같은 액티비티면 그것, 아니면 액티비티) → 기록(WatchLog) → `onScreen(byWindow = true)` → 서재면 `onShelfLive(force = true)`·멈춤 예약, 읽는 화면이면 진행률 재시도 3번으로 두고 잠잠 예약(700ms) |
| 창 바뀜, 액티비티 아님 | 기록 → `onWindow` → 지금 화면대로 `onShelfLive(force = true)`·멈춤 예약 / 잠잠 예약 |
| 내용 바뀜 | `readsContent == false`(KOReader)면 여기서 끝 → `onContent(ctx, 알림의 요소)` → (`screenInsideActivity` 앱만, 0.5초에 한 번 + **마지막 재확인**, 아래) `screenName(ctx, 기억한 액티비티)` 가 지금 화면과 다르면 기록 → `onScreen(byWindow = false)` → 서재면 `onShelfLive(force = false)`·멈춤 다시 예약, 읽는 화면이면 재시도 3번·잠잠 다시 예약 |
| 마지막 재확인 예약이 돎 | 위 `screenName` 확인 → 지금 화면대로 `onShelfLive(force = false)`·예약(지금 `holderLater` 가 `checkHolder` 뒤 `readScreen(force = false)` 하는 것과 같다) |
| 누름 | `onClick`(서재가 아니어도 부른다. 지금도 확인 창 누름은 화면과 상관없이 본다) |
| 서재 멈춤 예약이 돎 | 그 앱 화면이 서재면 `onShelf`(지금 `settleShelf` 첫 줄) |
| 잠잠 예약이 돎 | 그 앱 화면이 읽는 화면이면 `onViewer`(지금 `readViewer` 첫 줄) |
| 서비스 연결 / 끝 | WatchCore 가 선언대로 표시: `MARKS_CLOSED` 앱은 지금 화면을 잊고 '닫힘', `MARKS_OPEN` 앱은 '열림'(지금 `closeHeldViewers`, 어댑터 훅 없음). 끝에는 예약 셋을 지운다 |
| 홈 `onResume` | 깔린 앱 어댑터 `onHome`(지금 `syncFileApps`: Readest 의 마지막 열쇠 → KOReader, 이 순서) |

- `onShelfLive` 의 바탕 동작(바로 자르기 `eagerCovers`: 화면이 바뀐 지 1초·찍은 지 1.1초가 지났을 때만, 1초 간격)은 `ScreenReader` 에, 책 상세(BookFusion·밀리)·최근 줄(알라딘, 1초 간격)은 그 앱 어댑터에 있다.
  간격은 WatchCore 가 걸지 않는다: 지금도 상세·최근 줄(`lastWebRead`)과 바로 자르기(`lastEagerRead`)가 따로 재고, 바로 자르기는 준비가 안 됐으면 간격을 쓰지 않는다.
- **`screenName` 의 마지막 재확인**(2차 G1): 지금 `checkHolder` 는 0.5초(`HOLDER_CHECK_MS`) 안에 다시 온 알림을 버리지 않고, 0.5초 뒤 한 번 더 보도록 예약한다(`holderLater`).
  웹 화면은 내용 알림이 몰려 오다 끊기므로, 마지막 알림이 간격 안에 들면 이 예약이 없을 때 읽는 화면이 뜨고 닫힌 것을 놓친다(Libby·Readest 의 열림·닫힘 표시와 읽은 시간).
  - 소유: **WatchCore** 가 갖는다(어댑터는 이름만 판단). 예약은 **WatchCore 전체에 하나**(지금 `holderSpec`·`holderLater` 그대로, 아래 `예약은 전체에 하나`).
  - 대상: `ReaderApp.screenInsideActivity == true` 인 앱(Libby·Readest, 지금 `viewerHolder != null`)만. **선언으로 정하고 `screenName` 의 결과로 정하지 않는다**(3차 G1):
    서비스가 다시 붙은 직후처럼 화면이나 맨 앞 창을 몰라 `screenName` 이 null 이어도 예약은 건다(지금 `checkHolder` 도 판단 전에 예약한다).
  - 실행: 간격 안의 알림마다 앞 예약을 지우고 0.5초 뒤로 새로 건다(마지막 알림 기준). 돌면 위 순서표의 `마지막 재확인` 줄대로(그때도 null 이면 화면을 그대로 둔다).
  - 취소: 서비스가 끝나면(`onDestroy`) 지운다. 다른 앱이 새 예약을 걸면 바뀐다(전체에 하나).

### 어댑터 관계 **【권고로 정함】**(2차 A1)

**관계: 모든 어댑터는 바탕 어댑터 `ScreenReader` 를 상속한다.** 위임이 아니라 상속인 이유: 대부분의 전용 앱이 바탕 흐름(서재 칸·누른 칸·메뉴 제목)을 그대로 쓰고 한두 훅만 더하거나 바꾼다.
지금 `ReaderSpec` 을 앱 파일이 상속해 메서드를 바꾸는 방식과도 같다.

- **훅을 덮을 때 `super` 와의 순서는 지금 코드 순서 그대로**다. 대부분은 `super` 먼저(바탕 흐름 → 그 앱 것). `super` 앞에 두거나 `super` 를 부르지 않는(대신하는) 훅은 아래 표에만 있다.
  대신하는 훅도 바탕의 조각은 불러 쓴다: `readShown`(화면 읽기 결과 `ViewerInfo`), `recordShown`(메뉴 제목·진행률을 바탕 규칙으로 기록), `takeClicked`(읽는 화면이 열릴 때 쓸 누른 칸, 120초 안),
  `readShelfCells`(서재 칸 읽기, 편집 중이면 빈 목록), `shelfStatuses`(칸들의 진행률·반납일을 제목으로 맞춰 적기), `shelfCoverSpots`(표지 없는 책·목록에 없는 책의 표지 자리).
- `recordShown(ctx, info, force = false)` 는 지금 `readViewer` 의 규칙 그대로 — 편 책 기록의 책이 이 제목이면 다시 적지 않고 진행률만, 아니면 `opened(ByTitle, at = unknownOpenAt)`.
  [force] 가 true 면 같은 책이어도 편 것으로 적는다(지금 알라딘의 `first`, 5차 B1).

| 앱 | 바탕과 순서가 다른 훅 | 지금 그 자리 |
|---|---|---|
| 우주 | `onViewer` 대신(데이터로 안 책이 먼저 — 편 때는 아래 `데이터로 안 책의 편 때`, 없으면 `recordShown`. 그 뒤 데이터 진행률·표지) | `readViewer` 의 `fileBook`·`fileData` 흐름 |
| 네오리더 | `onViewer` 대신(데이터로 안 책이 먼저, **없으면 제목은 기록하지 않고** 진행률만 편 책 기록의 책에 — `recordShown` 을 부르지 않는다), `onEnterViewer`(아래) | `readViewer` 의 `fileBook` 흐름 + `fileViewer` 면 `ViewerInfo(null, …)`, `enterTab` |
| Readest | `onViewer` 대신(열쇠가 보이면 열쇠 흐름, 없으면 다시 예약), `onEnterViewer` 대신(아래) | `readViewer` 의 `viewerKeys` 갈래(일찍 돌아감) |
| KOReader | `onViewer` 대신(화면을 읽지 않고 데이터로 맞추기 — 지금도 잠잠 예약이 돌 때 `syncFiles`) | `readViewer` 의 `fileState` 갈래 |
| 이지뷰어 | `onViewer` 대신(파일 이름 흐름 전체), `onShelf` 대신(아래), `onEnterViewer` 대신(아래) | `readViewer` 의 `shownFile` 흐름, `settleShelf` 의 `fileBookId`·파일 열쇠 표지 |
| 알라딘 | `onViewer` 대신(서재를 떠난 뒤 메뉴로 처음 안 책이면 앞서 편 책과 같아도 다시 편 것으로 — `recordShown(force = 첫 메뉴)`, 그리고 "메뉴로 이미 앎" 기억), `onShelf` 는 최근 줄 판단 **먼저** 그 뒤 `super` | `readViewer` 의 `first = readSince && readKnown.add(pkg)`, `settleShelf` 의 `recent` 갈래(칸 진행률·표지 앞) |
| 문리더 | `onViewer` 대신(`readShown` → `recordShown` → 메뉴 제목이 보였으면 저장소에서 파일 찾기) | `readViewer` 의 `searchesBookFile` |
| BookFusion·밀리 e-ink | `onEnterViewer` 대신(아래), `onShelf` 대신(상세를 다시 기억하고 상세 표지 자리를 서재 칸 자리와 **한 번에** 찍음 — `readShelfCells`·`shelfStatuses`·`shelfCoverSpots` 사용) | `enterScreen` 의 `webBook`·`clickOnly`, `settleShelf` 의 상세(칸 표지와 같은 `captureCovers` 한 번) |
| 이씨오 범용뷰어 3.0 | `onViewer` 대신(`onContent` 로 붙잡아 둔 웹 화면에서 읽고 — 창 뿌리로는 닿지 않는다 — 표지 단추로 제목을 알았을 때만 `recordShown`. PDF 뷰어면 그 읽는 화면이 열린 때로 `unknown` 한 번(지원하지 않음). 웹 화면을 못 붙잡았으면 다시 예약), `onEnterViewer`(아래) | (2026-11-07 새 앱) |

- 서재 멈춤에서 찍기는 한 번이다(지금 `settleShelf` 끝의 `captureCovers` 하나). 앱 것을 `super` 뒤에 따로 찍으면 1.1초 간격에 걸려 멈춤이 다시 예약되므로, 찍을 자리를 더하는 앱은 `onShelf` 를 대신하고 조각으로 자리를 모아 한 번 찍는다.
- `ScreenReader.onClick` 은 서재에서 누른 것이 책 칸이 아니면(제목 못 읽은 누름 기록 뒤) `onClickNoCell(ctx)` 를 부른다(바탕: 아무것도 안 함, `ScreenReader` 안의 덮는 자리). 지금 `shelfClicked` 의 `cell == null` 갈래.
- `super` 뒤에 더하기만 하는 훅(표에 없음): 이지뷰어 `onWindow`·`onClick`(열기 확인 창 기억), 알라딘 `onShelfLive`(최근 줄)·`onEnterViewer`(읽으러 간 때), BookFusion·밀리 `onShelfLive`(상세 기억)·`onClickNoCell`(상세를 기억하고 상세 표지 찍기, 지금 `rememberWebBook`),
  우주·네오리더 `onEnterViewer`(묻는 횟수 다시 세기, 네오리더는 탭), Readest·KOReader `onHome`, Readest `afterOpened`, Libby·Readest `screenName`(바탕은 판단 없음),
  이씨오 범용뷰어 3.0 `onShelf`(서재에서 본 제목 기억)·`onContent`(읽는 화면이면 웹 화면 붙잡기, 바탕은 아무것도 안 함).
- 이지뷰어 파일 칸 누름은 덮지 않는다: 이지뷰어는 `openOnViewer` 라 파일 칸도 바탕의 누른 칸으로 남고, 파일 칸인지는 읽는 화면에 들어올 때 가른다(지금 그대로).
- 이지뷰어 `onShelf` 는 대신한다(3차 B2): `readShelfCells` 로 칸을 읽은 뒤, 파일 칸은 파일로 책을 정해(`fileBookId`, 어댑터 안) 그 책에 진행률·반납일을 적고
  표지 자리를 **파일 열쇠**로 넘긴다. 파일 칸이 아닌 칸은 `shelfStatuses`·`shelfCoverSpots`(제목). 바탕 `onShelf`(제목으로 맞춤)를 먼저 돌리지 않는다 —
  같은 제목의 다른 폴더 파일에 진행률·표지가 붙는다.

**읽는 화면에 들어올 때(`onEnterViewer`)** — 지금 `enterScreen` 의 순서 그대로:

| 앱 | 순서 |
|---|---|
| 바탕(대부분) | 누른 칸(`takeClicked`, 120초 안)이 있으면 그 칸 `opened(ByTitle)` |
| BookFusion·밀리 e-ink | ① 기억한 책 상세(보이거나 사라진 지 3초 안·`keepsHidden`(밀리 받기 창), 상세를 마지막으로 본 뒤 다른 칸을 누르지 않았을 때)면 그 상세 `opened(ByTitle)` → 그 `beforeOpened` 가 누른 칸을 비움 ② 없으면 바탕(누른 칸) ③ BookFusion 은 둘 다 없고 서재에서 넘어왔으면 `unknown` |
| Readest | 누른 칸을 **기록하지 않고 남겨 둔다**(읽는 화면에서 열쇠를 볼 때 처음 보는 열쇠의 제목으로 쓴다) |
| 이지뷰어 | 다시 들어온 것이면(`!again`) 이 읽는 화면의 파일 기억(본 파일·파일마다 정한 책·누른 파일)을 비움 → 누른 칸이 파일 칸이면 기록하지 않고 "누른 파일"로, 아니면 바탕 → 홈에서 연 파일(`HomeSignals`, 120초 안)도 누른 파일이 없을 때 "누른 파일"로 |
| 우주 | 데이터 묻기 횟수·메뉴 제목 기억을 다시 세고(`DataAsk`) 바탕 |
| 네오리더 | 데이터 묻기·파일 재시도 횟수를 다시 세고 바탕 → 지금 화면이 읽는 화면 탭(`role == VIEWER`)이면 그 탭의 책(`opened(Exact)`, 편 책 기록의 책과 다를 때만) 또는 `unknown`(다른 모르는 탭이거나 책 모름 시각이 없을 때만) |
| 이씨오 범용뷰어 3.0 | 새로 들어온 것이면(`!again`) 붙잡아 둔 웹 화면을 비움(**`super` 앞**) → 바탕(누른 칸은 누름 알림이 없어 늘 없다) |
| 알라딘 | 바탕 → 서재를 떠나 처음 연 읽는 화면(또는 줄이 바뀌기를 기다리던 중)이면 읽으러 간 때를 적음 |
| KOReader | 바탕 그대로(누른 칸이 없다. 데이터 맞추기는 `onViewer`) |

**데이터로 안 책의 편 때**(우주·네오리더, 5차 B2 **【권고로 정함】**) — 지금 `readViewer` 의 `at` 규칙 그대로, 앞에서부터 있는 것:

1. 제목 못 읽은 칸으로 들어와 책 모름이 된 때(`ctx.unknownOpenAt`, 지금 `unknownOpen`)
2. **메뉴 제목이 안 보이는데** 데이터로 안 경우에만 읽는 화면이 열린 때(`ctx.viewerOpenedAt`, 지금 `viewerAt.takeIf { file != null && shown.title == null }`)
3. 그 밖(메뉴를 띄운 뒤 데이터로 처음 알아봄 등)은 null — 기록기가 지금 때로 적는다

- 늘 "읽는 화면이 열린 때"로 거슬러 적지 않는다. 메뉴를 띄운 뒤에야 알아본 책을 열린 때로 적으면 그 사이 시간이 다른 책(앞서 편 책)에서 이 책으로 옮겨 가,
  편 책 기록(`book_open`)의 때와 책별 읽은 시간이 지금과 달라진다.
- 다시 적을지는 `recordShown` 과 같은 규칙: 편 책 기록의 책이 그 책(파일로 가른 책은 id)이면 다시 적지 않는다.

### 기록과 기억 비우기는 한 길로

어댑터는 편 책 기록을 기록기로 직접 하지 않고 **`ctx.opened(…)`·`ctx.unknown(…)`** 만 부른다. `ctx.opened` 는 지금 `bookOpened` 의 순서 그대로
**① 비우기(기록 전) → ② 기록 → ③ 이어 두기(기록기가 돌아온 뒤)** 로 돈다(4차 B2 **【권고로 정함】**):

1. **비우기, 기록 전**: WatchCore 가 그 앱의 공통 기억을 비우고(`unknownOpen`, 편 때 `lastOpenedAt` 적기) `adapter.beforeOpened()` 를 부른다. 지금 `bookOpened` 도 DB 에 쓰기 **전에** 비운다.
   그래서 기록기가 예외로 끝나도(연결 시험 ③) 앞서 누른 칸·상세가 남아 다음 방문에 다시 쓰이지 않는다.
2. **기록**: `recorder.opened(…, steps)` — 안에서 찾기·넣기 → `steps.found` → 편 책 기록 → `steps.logged` → 잘라 둔 표지(위 기록기 절).
   찾기·넣기·`found`·`logged` 의 예외면 그 뒤 단계와 ③을 건너뛰고, WatchCore 가 그 알림 처리 바깥에서 잡아 기록(WatchLog)만 남긴다(다음 알림은 그대로 받는다).
3. **이어 두기, 돌아온 뒤**: `adapter.afterOpened(book)` — `bookOpened` 가 끝난 **뒤**에 하던 일만(지금은 Readest 의 열쇠 ↔ 책 하나).

`beforeOpened`·`afterOpened` 모두 `super` 를 먼저 부른다. `steps` 는 그 기록 한 번의 것이라 어댑터가 `ctx.opened` 를 부를 때 넘긴다(대부분 없음).

| 비우거나 적는 것 | 언제·누가 | 지금 `bookOpened` 의 자리 |
|---|---|---|
| `unknownOpen` 비우기, 이 앱에서 책을 편 때 적기 | 기록 전 · WatchCore | `unknownOpen -= pkg`, `lastOpenedAt`(쓰기 전) |
| 누른 칸 `clicked`, 제목 못 읽은 누름 `unknownClick` | 기록 전 · `ScreenReader.beforeOpened` | `clicked -= pkg`·`unknownClick -= pkg` |
| 책 상세 `webBook`·`webGone` | 기록 전 · BookFusion·밀리 e-ink 의 `DetailMemory` | `webBook -= pkg`·`webGone -= pkg` |
| 열쇠 몰라 책 모름 `unknownKeys` | 기록 전 · Readest | `unknownKeys -= pkg` |
| 제목 바꾸기, 서재에서 마지막으로 편 파일(경로·책 정보·`shelf_file`·`shelf_book`) | 찾기·넣기 뒤 · 편 책 기록 전 · 이지뷰어 `steps.found` | `retitle`·`rememberShelfFile`(편 책 기록 전) |
| 탭 ↔ 책 적기 | 편 책 기록 뒤 · 표지 전 · 네오리더 `steps.logged`(지금 읽는 화면 탭이면) | `setTabBook`(편 책 기록 뒤) |
| 열쇠 ↔ 책 | 돌아온 뒤 · Readest `afterOpened`(처음 보는 열쇠), **편 책 기록을 다시 읽어** | `newKeyed` 의 `recordedBook(spec)?.also { setKeyBook }`(`bookOpened` 뒤) |

- 그래서 "책 상세로 연 책을 기록하면 앞서 누른 다른 칸은 버린다"(지금 동작)가 그대로다: 상세로 기록 → `ScreenReader.beforeOpened` 가 `clicked` 를 비움.
- 기록할 때 쓸 값(누른 칸·상세)은 어댑터가 `ctx.opened` 를 부르기 **전에** 꺼내 `Seen`·`Target` 에 담는다. `beforeOpened` 가 그 기억을 비워도 기록에는 영향이 없다.
- 기록하지 않고 기억만 비우는 자리는 그 어댑터가 직접 비운다(지금 Readest 가 열쇠로 아는 책이면 다시 적지 않아도 `clicked -= pkg`).
- `ctx.unknown` 은 지금 `ReadingLog.bookOpened(pkg, "", "", at)` 들처럼 **아무 기억도 비우지 않는다**.
- 서재 진행률 등 `status`·`statuses`·`setFile`·`cover` 는 기억을 건드리지 않으므로 `ctx.recorder` 를 바로 불러도 된다.

**단계별 실패**(7차 B2 **【권고로 정함】**) — 지금 `bookOpened` 에서 실패가 어떻게 흐르는지 그대로:

| 단계 | 지금 하는 일 | 실패하면 |
|---|---|---|
| ① 찾기·넣기 | `moveToFront`·`updateStatus` 또는 `addOpened`(DB) | 예외를 밖으로 → ②~⑤·`afterOpened` 안 함(이미 비운 기억은 비워진 채) |
| ② `steps.found` | 이지뷰어 `retitle` → `rememberShelfFile`(`setFilePath` → `fileInfo` → prefs) | 예외를 밖으로 → ③~⑤ 안 함. **그 안에서 먼저 끝난 저장(경로 등)은 남는다** |
| ③ 기록·편 책 기록 | `WatchLog.add`, `ReadingLog.bookOpened` | **삼킴**: 편 책 기록 저장 실패는 `keepOpens` 의 `runCatching` 이 삼키고 다음으로(아래 `성공의 뜻`) |
| ④ `steps.logged` | 네오리더 `setTabBook`(prefs) | 예외를 밖으로 → ⑤ 안 함 |
| ⑤ 잘라 둔 표지 | `pendingCovers.remove` → `setCover` | **삼킴**: `setCover` 는 `runCatching` — 실패해도 `opened` 는 정상으로 돌아오고 `afterOpened`(Readest 열쇠 ↔ 책)도 돈다 |

- 기록기를 옮길 때 이 `runCatching` 두 곳을 지우거나 넓히지 않는다(넓히면 ①②④ 의 실패가 숨어 그 뒤 단계가 돌고, 지우면 ⑤ 실패 때 `afterOpened` 가 빠진다).

**성공의 뜻**(6차 G1 **【권고로 정함】**): `opened` 가 **예외 없이 돌아오면** 기록기의 단계를 다 지난 것이다. **편 책 기록이 저장됐다는 뜻은 아니다** —
편 책 기록을 쓰는 `ReadingLog.bookOpened` 는 지금도 저장 실패를 안에서 삼킨다(`keepOpens` 의 `runCatching`, 메모리 사본을 비워 다음에 DB 에서 다시 읽는다). 이 동작은 바꾸지 않는다.
그래서 **기록 뒤 일에 어떤 책을 쓰는지는 지금 코드 그대로** 둔다(돌려받은 책으로 바꾸면 저장이 조용히 실패한 때 결과가 달라진다):

| 기록 뒤 일 | 지금 쓰는 책 | 옮긴 뒤 |
|---|---|---|
| Readest 열쇠 ↔ 책 | `recordedBook` 을 다시 읽음 | `opened` 가 돌려준 책이 `recordedBook` 과 같을 때만(2026-10-24 리뷰로 고침) |
| 우주·네오리더 파일 적기 | `recordedBook` 을 다시 읽음(기록하지 않은 때도) | 기록했으면 `opened` 가 돌려준 책, 아니면 `recordedBook`(2026-10-24 리뷰로 고침) |
| KOReader 파일 적기 | 제목으로 찾은 책(`saveFile`) | `opened` 가 돌려준 책이나 `recordedBook`, KOReader(두 판) 책일 때만(다른 앱 책에는 적지 않는다, 2026-10-24 리뷰로 고침) |
| 이지뷰어 제목 바꾸기·서재 파일, 네오리더 탭 ↔ 책 | 기록기 안의 그 책 | `steps.found`·`logged` 의 `book` |

### `AppContext`: 앱 파일이 받는 것(G5 반영)

| 능력 | 내용 |
|---|---|
| `context`, `app`, `recorder` | 파일·패키지 정보, 그 앱의 `ReaderApp`, 기록기(편 책 기록을 바꾸지 않는 `status`·`statuses`·`setFile`·`cover` 등만 바로) |
| `opened(seen, at, target, coverKey, steps)`, `unknown(at)` | 책을 폈다 / 책 모름 — **편 책 기록은 이 둘로만**(`opened`: 비우기·`beforeOpened` → 기록기(`steps.found`·`logged` 포함) → 예외 없이 돌아오면 `afterOpened`) |
| `unknownOpenAt`(읽고 쓰기) | 제목 못 읽은 칸·모르는 탭으로 들어와 책 모름이 된 때(WatchCore 소유, 지금 `unknownOpen`) |
| `now`, `screen`, `viewerOpenedAt`, `lastTransition`, `lastOpenedAt` | 지금 때, 지금 화면, 읽는 화면이 앞에 온 때, 마지막 화면 바뀜, 이 앱에서 책을 편 것으로 마지막으로 적은 때(이지뷰어 `fileGuess`) |
| `windows`, `root` | 그 앱의 창들, 맨 앞 창(다른 앱이면 null) |
| `prefs` | `reader_watch` prefs(같은 키) |
| `log(kind, …)` | 자동 추가 기록(WatchLog) |
| `capture(spots, retry)` | 표지 자리를 찍어 잘라 달라(아래) |
| `viewerAgain(ms)`, `viewerRetry()` | 이 앱의 `onViewer` 를 [ms] 뒤 다시 / 진행률 재시도가 남았으면 하나 쓰고 true(아래) |
| `inBackground(work, done)` | 무거운 일 |

- **`capture`**: 맨 앞에서 `adapter.capturesCovers(ctx)` 가 false 면 아무것도 안 한다(지금 `captureCovers` 첫 줄의 `spec.dataCovers(this)` 자리, 4차 G1). 바탕의 `onShelfLive`·`onShelf`·책 상세 찍기 모두 이 한 곳을 지나므로 상속한 훅도 막힌다.
  안드로이드 11 아래는 하지 않고, 찍은 지 1.1초가 안 됐으면 [retry] 일 때 그 앱의 서재 멈춤을 다시 예약한다. 찍은 직후 `adapter.stillSpots`, 뒤 스레드에서 잘라 `ReaderApp.placeholderCover`·한 가지 색이면 버리고,
  메인 스레드에서 자리마다: 제목 자리는 그때 그 제목의 책에 `cover`, 없으면 `pendingCover(정리한 제목)` / 열쇠 자리는 `id` 의 책에, 없으면 `pendingCover(key)`(지금 그대로). 서비스가 끝난 뒤 끝나도 적용한다.
- **예약은 전체에 하나**(3차 B3) **【권고로 정함】**: 지금 서비스의 예약 셋 — 읽는 화면 잠잠(`viewerSpec`·`viewerLater`)·서재 멈춤(`settleSpec`·`settleLater`)·마지막 재확인(`holderSpec`·`holderLater`) —
  은 **WatchCore 전체에 종류마다 하나**로 그대로 둔다. 새로 걸면 **어느 앱 것이든** 앞 예약을 지운다. 앱마다 하나로 나누면 안 된다: 앱 A 의 읽기 예약 뒤 앱 B 의 읽는 화면 알림이 오면
  지금은 A 예약이 지워지는데, 앱마다 두면 A 의 화면 기억(`screen[A]`)은 다른 앱으로 넘어가도 지우지 않아 여전히 읽는 화면이라 A 의 `onViewer` 가 돈다.
- **`viewerAgain`**: 그 하나뿐인 읽는 화면 예약을 이 앱으로 다시 건다(지금 `viewerSpec = spec; postDelayed(viewerLater)`). 실행할 때 그 앱 화면이 읽는 화면이 아니면 부르지 않는다
  (지금 `readViewer` 첫 줄). 서비스가 끝나면 지운다(`onDestroy`).
- **`viewerRetry`**: 진행률 재시도 횟수(`viewerRetries`)는 지금처럼 **WatchCore 전체에 하나**다 — 읽는 화면 잠잠 예약을 새로 걸 때(창·내용 알림)마다 3(`VIEWER_RETRIES`)으로 두고,
  어댑터가 `viewerRetry()` 로 하나씩 쓴다(바탕·Readest·네오리더, 지금 `viewerRetries-- > 0`). 그 밖의 다시 부르는 횟수는 **어댑터가 가진다**: 네오리더 파일 재시도 5번(`FILE_RETRIES`)·우주·네오리더 데이터 묻기 6번(`FILE_TRIES`, `DataAsk`).
- **`inBackground`**: 지금 `Bg.run` 그대로 — 스레드 3개짜리 풀에서 하고, `done` 은 메인 스레드에서 결과(`Result`)로 받는다. 실패는 어댑터가 정한다(지금처럼 버리고 다음에 다시).
  같은 일을 겹쳐 하지 않게 하는 집합(`keyCovers`·`dataCovers`·`fileSearched`)은 그 어댑터의 기억이다. **서비스가 끝난 뒤 끝나도 결과를 적용한다**(지금과 같다: DB 쓰기는 서비스와 상관없다).

### 홈에서 열기: `OpenResult`(G2 반영)

```kotlin
sealed interface OpenResult {
    /** 앱을 연다(BOOX 얼린 앱은 런처로 풀어서) */
    object Launch : OpenResult
    /** 이 인텐트로 연다. 열리면 [started] 를 부른다. 못 열면 [onFail] */
    data class Start(val intent: Intent, val started: (() -> Unit)? = null, val onFail: Fallback = Fallback.LAUNCH) : OpenResult
    /** 파일로 연다(file:// + MIME, StrictMode 를 잠시 풀어 연다 — 지금 openFile). 열리면 [started](이지뷰어: 홈에서 연 파일을 HomeSignals 에), 못 열면 [onFail] */
    data class File(val component: ComponentName, val path: String, val started: (() -> Unit)? = null, val onFail: Fallback = Fallback.LAUNCH) : OpenResult
    /** 파일을 모르거나 없다: 뒤에서 [ReaderApp.bookFiles]·저장소로 다시 찾아 열고, 못 찾으면 '파일을 찾지 못함' 창(지금 HomeActivity.openBook 의 흐름) */
    object FindFile : OpenResult
}
/** 열기에 실패했을 때: 앱을 연다(실행 화면이 있는 앱) / 뒤에서 파일을 다시 찾는다(실행 화면이 없는 네오리더) */
enum class Fallback { LAUNCH, FIND_FILE }
```

- `AppStore.openBook` 은 `ReaderApp.open` 의 결과만 처리한다: `Start`·`File` 이 실패하면(예외) 그 결과의 `onFail` 대로 —
  `LAUNCH` 면 앱을 열고(지금 KOReader·이지뷰어·우주의 `if (!openFile(…)) return@let` 뒤 `launch`), `FIND_FILE` 이면 `FindFile` 과 같게 `false` 를 돌려
  `HomeActivity` 가 지금처럼 뒤에서 찾고 못 찾으면 안내한다(지금 네오리더의 `return openFile(…)`, 2차 B2).
  찾은 파일은 `openFoundFile` 그대로(같은 파일로 넣은 다른 책이 있으면 적지 않고 열기만).
- 파일을 고르는 규칙(파일을 알고, 읽을 수 없거나 그 파일이 있으면)은 지금 그대로 각 앱의 `open` 안에서 `HomeContext` 의 도우미로 한다.
- 앱별: Readest `Start(readest://book/<해시>)`(얼린 앱이면 `Launch`), KOReader `File(실행 화면, 경로)`, 우주 `File(OpenerActivity, 경로)`, 이지뷰어 `File(실행 화면, 경로, started = HomeSignals)`, Wattpad `Start(wattpad://story/<이야기 번호>, started = HomeSignals.bookOpened)`(번호를 모르면 `Launch`),
  네오리더 `File(ReaderHomeActivity, onFail = FIND_FILE)` 또는 파일 모르면 `FindFile`, 나머지 `Launch`.

## 앱별 계획

앱마다 "지금 어디에 무엇이 있나 → 옮긴 뒤 그 앱 파일에 무엇이 있나 → 어떻게 시험하나". 패키지만 다른 판(리디 원스토어·문리더 Pro·YES24 도서관 휴대폰·
KOReader F-Droid·이지뷰어 두 판·채리뷰어)은 지금처럼 같은 클래스에 패키지만 준다.

### A. 설정만 있는 앱: 바탕 어댑터 `ScreenReader` 그대로

요소 이름과 화면 읽기 메서드만 있고 서비스에 그 앱 종류만의 처리가 없다. 따로 옮길 단계가 없고, **2·3단계(공통 흐름을 옮길 때) 결과가 지금과 같은지**만 본다.

| 앱 | 지금 쓰는 공통 흐름 설정 | 앱 파일에서 바꾼 메서드 | 에뮬 | 시험 |
|---|---|---|---|---|
| 교보도서관 `kr.co.kyobobook.KEL` | — | — | Play 에 있음 | 목록 보기 칸 누르기 → 제목·저자·표지·반납일, 하단 진행률, 표지 보기 칸(제목 없음) → 메뉴 제목 |
| 교보eBook(휴대폰) `com.kyobo.ebook.common.b2c` | — | — | Play 에 있음 | 서재는 읽지 않음, 메뉴 제목 → 책, 하단 진행률 |
| 교보eBook(e-ink) `com.kyobo.ebook.eink` | — | — | 없음 | 실기기(크레마), 확인 대기 |
| 부커스 `com.bookers.ebook` | `eagerCovers`·`viewerPages`·`pagesRoundUp` | — | Play 에 있음 | 칸 누르기, 하단 쪽 → 진행률(올림), 반납일 |
| 리디·원스토어 `com.initialcoms.ridi` | `coverOverlays` | `shelfCells`·`cellsByPosition`·`viewerTitleIds` | Play 에 리디 | 자리로 칸 찾기·받기 단추 겹친 표지 안 자름, 메뉴 제목 |
| YES24 전자도서관 e-ink·휴대폰 | `eagerCovers`·`openOnViewer`·`pagesRoundUp` | `titleRoots`(메뉴 위 막대, `Yes24Viewer`) | Play 에 둘 다 | 칸 안의 받기·반납 단추는 읽는 화면이 열려야 편 것 |
| YES24 eBook `com.yes24.ebook.fourth` | `eagerCovers`·`pagesRoundUp` | `titleRoots`(메뉴 위 막대, `Yes24Viewer`)·`eagerWhenStill`·`recheckCovers`·`coverStill` | Play 에 있음 | 칸 누르기·하단 진행률, 탭을 넘기는 중에 표지 안 자름 |
| my YES `com.yes24.ebook.einkstore` | `openOnViewer`·`secure`·`clickByPlace=false` | — | Play 에 있음(USB 디버깅 중엔 닫힘) | 디버그 빌드 + 디버깅 끈 채 사용자가 눌러 보고 DB 로 확인(CLAUDE.md 기기 작업 요령) |
| 밀리(휴대폰) `kr.co.millie.millieshelf` | `eagerCovers`·`openOnViewer`·`viewerPages` | `shelfCells`·`clickedCell`·`cellsByPosition`·`titleRoots` | Play 에 있음 | 받지 않은 책은 받기만 하고 안 들어옴, 메뉴 쪽 → 진행률 |
| 킨들 `com.amazon.kindle` | `eagerCovers`·`openOnViewer` | `shelfCells`(이어 읽기 표지 칸 `recentCell` 포함)·`clickedCell`·`cellsByPosition` | Play 에 있음 | 서재 칸·'이어 읽기' 표지 칸 누르기, 받지 않은 책은 메뉴 제목, 메뉴 진행률 |

- 킨들은 `recent` 설정(서재의 '최근 읽은 책' 줄로 판단)을 **쓰지 않는다**. `Kindle.kt` 의 `recent`·`recentCell` 은 '이어 읽기' 표지를 서재 칸으로 읽는 함수 안의 이름이다(B5 반영).
- `openOnViewer`(누른 칸을 읽는 화면이 열릴 때 씀, 기간 지난 책 무시)·`eagerCovers`·`eagerWhenStill`(바로 자르기는 앞서 읽은 때와 같은 자리만)·`secure`(서재 멈춤에서 찍지 않음)·`coverOverlays`·`cellsByPosition`·`recheckCovers`(찍은 뒤 다시 보기)·`clickByPlace`·`shelfEditing`·`tellsTitle` 은
  `ScreenReader` 의 설정으로 남는다(같은 흐름 안의 값). 이것들을 쓰는 서비스 코드(`clicked`·`unknownClick`·`lastShelf`·바로 자르기·찍은 뒤 다시 보기 `stillSpots`)는 3단계에서 `ScreenReader` 로 간다.

### B. 전용 어댑터가 필요한 앱(옮기는 순서)

#### B1. 우주 `com.inno.woojoo`

- **【권고로 정함】 지금 우주 작업(공통 파일에 `fileData`·`fileOpener` 통로)을 먼저 커밋하고, 이 개편의 기준 커밋으로 삼는다.** 에뮬에서 확인을 마쳤고(서재·메뉴·데이터 진행률·PDF·홈 열기),
  다른 앱 무영향도 확인했다. 이 단계(B1)에서 통로를 우주 어댑터로 옮긴다.
- **지금**: `Woojoo.kt` + 공통 `fileBook`(묻는 횟수 `fileTries`·메뉴 제목별 기억 `fileTitles`·`knownTitle`), `fileData`(데이터 진행률·`dataCover`), `fileOpener`(`Apps.openBook`).
- **옮긴 뒤 `Woojoo.kt`**: `onViewer` 에서 RecentBooks 로 책을 찾고(`DataAsk`: 메뉴 제목이 새로 보일 때와 읽는 화면이 앞에 온 뒤 메뉴 없이 6번까지)
  `opened(Exact(lookup.byFile(path) ?: lookup.withoutFile(title)), at = 데이터로 안 책의 편 때)`(제목은 `lookup.knownTitle`), 그 뒤 `opened` 가 돌려준 책에 `setFile`(메뉴 없이 데이터로 안 책이 떠난 탭의 책이면 이 탭의 책으로 보지 않는다).
  찾지 못하면 `recordShown`(메뉴 제목). 화면에 진행률이 없으면 `app-Progress.json` 으로 `status`, 표지가 없고 그 파일에서 표지를 꺼낼 수 없으면(EPUB·그림이 아니거나 책 파일을 읽을 수 없음) `inBackground { 우주 표지 그림 } → cover`(같은 파일 겹쳐 꺼내지 않음).
  서재는 `ScreenReader`(PDF 칸 거르기 `readCell`). `open` = `File(OpenerActivity, 경로)`.
- **`DataAsk`**(readers/ 도우미, 우주·네오리더): 지금 서비스의 `fileBook`·`fileTries`·`fileTitles` 를 그대로 옮긴 것. 이 단계에서 만들고 네오리더는 B4 에서 쓴다(그때까지 네오리더는 `Legacy` 의 것).
- **서비스에서 지울 것**: `fileData`·`fileOpener`·`fileProgress`·`fileCover`·`dataCover`·`dataCovers`, `readViewer` 의 데이터 진행률 대체, `Apps.openBook` 의 `fileOpener`·`fileData` 갈래. `fileBook` 흐름은 네오리더(B4)가 옮겨 가면 지운다.
- **에뮬**(Play): 서재 칸(EPUB) → 책·저자·1%·파일·EPUB 표지 / 메뉴 14% / 메뉴 없이 쪽 넘김 → 진행률 / `OpenerActivity` 로 연 책(메뉴 없이) / PDF 칸 → '책 모름' 뒤 파일 이름 책·우주 표지 그림 / 홈에서 누르면 읽던 쪽.
- **알아 둘 것**: 앞 책 화면이 남은 채 다른 파일을 열면 우주가 MuPDF 에서 죽는다(우주 문제, 시험 순서에서 책을 닫고 연다).

#### B2. Readest `com.bilingify.readest`

- **지금**: `Readest.kt`(열쇠·데이터 읽기, 프로세스 캐시 `totals`·데이터) + 서비스 `readKeyed`·`newKeyed`·`keyedBook`·`setKeyBook`·`keyCover`·`syncKeys`, 기억 `unknownKeys`·`keyCovers`,
  prefs `key_<앱>_<열쇠>`·`book_key_<앱>_<id>`·`last_key_<앱>`, 누른 칸을 열쇠를 볼 때 씀(`enterScreen` 의 `viewerKeys` 갈래), 데이터 표지가 있으면 화면 자르기 안 함(`dataCovers`),
  `Apps.openBook` 의 `bookKey`→`keyIntent`. 읽는 화면은 서재 안(B8 과 같은 `screenName`).
- **옮긴 뒤 `Readest.kt`**: `onViewer` 에서 열쇠 → 마지막 열쇠 prefs → 적어 둔 책이면 누른 칸을 비우고 편 책 기록의 책과 다를 때만 `opened(Exact(id))`,
  처음 보는 열쇠면 데이터 제목 → 누른 칸 → 목차 제목 순으로 `opened(ByTitle)`(지금 `newKeyed` 와 같이 제목으로) 하고 **`afterOpened` 에서 편 책 기록(`recordedBook`)을 다시 읽어 열쇠 ↔ 책을 prefs 에**.
  편 때는 둘 다 읽는 화면이 열린 때(없으면 지금, 지금 `readKeyed` 의 `opened`).
  제목을 모르면 `unknown`(같은 열쇠 한 번, 편 책 기록이 있을 때만). 그 책에 데이터 진행률·저자 `status`, 표지가 없으면 데이터 표지. 진행률이 없으면 `viewerRetry` 로 다시.
  열쇠가 안 보이면 `viewerRetry` 로 다시. `onHome` 에서 마지막 열쇠의 진행률·저자·표지. 데이터 폴더를 읽을 수 있으면 `capturesCovers` = false(지금 `dataCovers`) — 상속한 서재 훅의 자르기도 `ctx.capture` 에서 막힌다.
  서재 칸 읽기 설정·메서드(`shelfCells`·`coverStill` 등)는 지금처럼 앱 파일에(`stillSpots` 가 쓴다).
  `open` = `Start(readest://book/<해시>)`. `viewerTime = MARKS_CLOSED`, `markedScreens` = 서재 액티비티, `screenInsideActivity = true`(B8).
- **서비스에서 지울 것**: 위 함수·기억 전부, `viewerKeys`·`keyIntent` 표시, `dataCovers`(→ 어댑터 `capturesCovers`), `ReaderWatchService.bookKey`.
- **에뮬**(`CremaPebble_A11_Play_Readest`): 데이터 위치를 옮긴 경우와 아닌 경우 각각 — 서재 칸 → 책 / 다른 길로 연 책은 목차 창 제목으로 / 다시 열면 열쇠로 같은 책(제목 고쳐도) / 홈 진행률 / 홈에서 누르면 링크로 바로 열림 / 서비스 다시 붙은 뒤 쪽 진행률(전체 쪽 기억이 남음).

#### B3. KOReader `org.koreader.launcher`·F-Droid 판

- **지금**: `KOReader.kt`(history.lua·.sdr) + 서비스 `syncFiles`(앞에 올 때·홈으로 돌아올 때)·`fileOpened`·`markFileViewer`·`newFile`·`saveFile`, prefs `sync_<앱>`·`file_<앱>`, 내용 바뀜 무시,
  `closeHeldViewers` 에서 '열림', `ReaderApps.hostsViewer`·`shownByDefault`, 시작하기에서 먼저 보임.
- **옮긴 뒤 `KOReader.kt`**: `onViewer`(앞에 와 잠잠해짐 — 지금 `readViewer` 의 `fileState` 갈래와 같은 때)·`onHome`(깔려 있을 때) 에서 지난번 뒤에 열고 닫은 책들을 **`opened(ByTitle)` 뒤 그 책에 `setFile`**(KOReader 두 판의 책일 때만, `fileOpened`·`saveFile`) ·`viewerShown(at)` 으로.
  처음 맞출 때 지금 책만, 목록에 없는 책은 처음 보는 파일일 때만 넣는 규칙도 어댑터 안에. 데이터를 읽을 수 없으면 '열림'.
  `viewerTime = MARKS_OPEN`, `markedScreens` = 읽는 화면 액티비티, `readsContent = false`, `fileOrder = 0`. `open` = `File(실행 화면, 경로)`.
- **서비스에서 지울 것**: 위 함수, `fileState` 표시와 그 갈래 4곳(내용 바뀜·`readViewer`·`closeHeldViewers`·`syncFiles`), `ReaderApps` 의 `fileState` 쓰임.
- **에뮬**(Play 에 GitHub 판): 책 열기 → 홈으로 → 들어옴·진행률 / KOReader 안에서 책 바꾸기 → 홈으로 → 두 책의 연 때·읽은 시간 / koreader 폴더 안 책 / 홈에서 누르면 그 파일 / 책 파일 읽기 끈 채(앞에 있던 동안 다 셈) / 같은 제목의 나간 책이 있을 때 되살아남(제목으로 찾기 그대로).

#### B4. 네오리더(BOOX) `com.onyx.kreader`

- **지금**: `NeoReader.kt`(Metadata 창구) + 서비스 `enterTab`·`setTabBook`·`tabBook`(prefs `tab_book_<탭>`)·`unknownTab`, `fileBook`·`fileTries`·`fileTitles`·`knownTitle`·`fileRetries`(파일을 모르면 넣지 않음),
  `Apps` 의 `fileReaders`·`fileViewer` 갈래·`findBookFile` 의 `bookFiles`.
- **옮긴 뒤 `NeoReader.kt`**: `keepsUnknownOnReenter = true`(공통 전이가 책 모름이 된 때를 지우지 않음 — 같은 모르는 탭에 다시 오면 처음 열린 때를 둔다, 지금 `enterTab` 의 `unknownTab` 비교와 함께. 날이 바뀌었으면 다시 적는다(탭이 앞에 올 때 본다)).
  `onEnterViewer`(바탕 뒤)에서 탭의 책 `opened(Exact(id))` 또는 `unknown`, `onViewer` 에서 메뉴 제목·Metadata(`DataAsk`)로
  `opened(Exact(lookup.byFile(path) ?: lookup.withoutFile(title)), at = 데이터로 안 책의 편 때, steps.logged = 탭 ↔ 책)` 뒤 `recordedBook` 에 `setFile`.
  파일을 모르면 메뉴 제목으로 **기록하지 않고**(바탕 `recordShown` 을 부르지 않음) 진행률만 편 책 기록의 책에 적고 `viewerAgain`, 5번까지 — 지금 동작.
  `fileEntry = ReaderHomeActivity`, `open` = 파일을 알면 `File(ReaderHomeActivity, 경로, onFail = FIND_FILE)`(열기에 실패해도 앱을 열지 않고 다시 찾기 — 실행 화면이 없다),
  모르거나 없으면 `FindFile`, `bookFiles` = Metadata.
- **서비스에서 지울 것**: `viewerTabs`·`fileViewer` 갈래, 탭 기억, `fileBook` 흐름 전부(B1 에서 `DataAsk` 로 옮긴 것의 `Legacy` 쪽), `fileRetries`, `knownTitle`(→ `lookup`).
- **에뮬**: BOOX 에뮬(`Palma2_A13`, 네오리더가 깔려 있는지 먼저 확인). 없으면 실기기(사용자 BOOX), 확인 대기. 탭 둘에 책 둘 → 탭 오가기 / 메뉴 없이 연 책 / 홈에서 파일로 열기 / 파일을 옮긴 책 → 다시 찾기 / 못 찾음 창.

#### B5. 이지뷰어·채리뷰어 `com.ezne.easyview.*`

- **지금**: `EasyView.kt`(파일 칸·위치 → 실제 경로·기본 그림 거르기·열기 확인 창) + 서비스의 파일 칸 처리 전부:
  `clickedFiles`·`viewerFiles`·`viewerFileAt`·`VIEWER_SWAP_MS`·`viewerFileBooks`·`fileGuess`·`inSameFolder`·`openingFiles`·`rememberOpening`·`openingFor`·`OPENING_MS`·`OPENED_SLACK_MS`·
  `rememberShelfFile`·`lastShelfBook`·`shelfFileOf`(prefs `shelf_file_<앱>`·`shelf_book_<앱>`)·`fileBookId`·`retitle`·`fileInfo`·파일 열쇠 잘라 둔 표지(`fileCoverKey`), 홈에서 연 파일(`homeOpened`), 그림 폴더 책의 파일 바꾸기.
- **옮긴 뒤 `EasyView.kt`**: 위 판단과 기억이 모두 이지뷰어 어댑터로. 열기 확인 창은 `onWindow`·`onClick` 에서 기억한다. 어댑터가 화면에 보인 위치·파일 이름을 실제 경로나 책 id 로 정해
  `opened(Exact(id), coverKey = "file:" + 위치·이름, steps.found = { retitle → rememberShelfFile(경로·책 정보·`shelf_file`/`shelf_book`) })`(편 책 기록·표지보다 먼저 — 지금 순서). 서재 칸 표지는 `capture` 에 같은 파일 열쇠로.
  기록기에는 화면 위치(`#휴대폰/…`)·마지막으로 편 파일 같은 이지뷰어 개념이 들어가지 않는다(열쇠는 그냥 문자열). 기본 그림 거르기는 `ReaderApp.placeholderCover`.
  `open` = `File(actMain, 경로, started = { HomeSignals.fileOpened(…) })`(지금처럼 열렸을 때만 기억).
- **서비스에서 지울 것**: 위 함수·기억·상수 전부, `confirmsOpen`·`bookFile` 갈래, `Cell.file` 을 보는 서비스 코드, `homeOpened`.
- **에뮬**(Play 에 `.free`): 최근문서·폴더 탭 칸 / 그림 폴더(만화) 한 권 / 같은 이름 다른 폴더(`SameA`·`SameB`·`DupC1`·`DupC2`)의 **표지가 섞이지 않음** / ▶·다음 파일 확인 창 / 원격(FTP) 받은 파일 /
  홈에서 파일로 열기 / 다른 앱으로 연 파일은 안 들어옴. 지금 READER_APPS.md·`EasyViewTest` 의 경우를 그대로. 채리뷰어(유료)는 같은 코드라 `.free` 로 갈음.
- **알아 둘 것**: 가장 크다(서비스 쪽 처리가 300줄 남짓). 둘로 나눈다: ① 파일 칸·확인 창·홈에서 연 파일, ② 그림 폴더·원격 파일·제목 바꾸기.
  지금 `readViewer` 는 누른 파일을 기다리는 예약(남은 시간) 뒤 진행률 재시도(1초)를 또 걸어 둘 다 돌 수 있다. `viewerAgain` 은 마지막 것만 남지만, 1초 뒤 다시 읽을 때 아직 기다리는 중이면 남은 시간으로 다시 걸어 결과는 같다.

#### B6. BookFusion `com.bookfusion.android.reader`·밀리(e-ink) `kr.co.millie.eink`

- **지금**: `BookFusion.kt`·`Millie.kt`(`webBook`: 책 상세·검색 상세) + 서비스 `webBook`·`webGone`·`WEB_KEEP_MS`·`lastWebRead`(1초마다 기억)·`rememberWebBook`(누를 때 표지 찍기)·`settleShelf` 의 상세 표지, `clickOnly`(누른 칸 모르면 `Unknown`).
- **옮긴 뒤**: 책 상세 기억과 사라진 뒤 3초 규칙을 두 앱이 같이 쓰는 도우미(`DetailMemory`, readers/ 안)로 두고 두 어댑터가 쓴다.
  **`onShelfLive`(멈추기 전, `force` 가 아니면 1초에 한 번)에서 상세를 기억**하고, `onShelf` 는 대신해 상세를 다시 기억하고 상세 표지 자리를 칸 표지 자리와 한 번에 찍고(지금 `settleShelf`), `onClickNoCell` 에서 상세를 기억하고 표지를 찍고,
  `onEnterViewer` 에서 기억한 상세로 `opened(ByTitle)` → 없으면 바탕(누른 칸) → BookFusion 은 둘 다 없고 서재에서 넘어왔으면 `unknown`.
  BookFusion 의 `role`(매니페스트 속성, 예전 이름 기억 prefs `bookfusion`)은 지금처럼 BookFusion 파일에. BookFusion 의 `coverStill`(같은 제목 칸이 여럿)도 그대로.
- **서비스에서 지울 것**: `webBook`·`webGone`·`lastWebRead`·`rememberWebBook`, `settleShelf` 의 상세 표지, `clickOnly` 갈래.
- **에뮬**(Play 에 둘 다): BookFusion 줄 칸·격자 칸·목록 칸 → 상세 → `Read`(상세가 먼저 사라짐) / 누른 칸 모르고 넘어감 → 책 모름 / 받지 않은 책. 밀리 e-ink 검색 → 상세 → `바로 읽기` / 받지 않은 책은 받기만.

#### B7. 알라딘 `kr.co.aladin.ebook`

- **지금**: `recent`(서재의 '최근 읽은 책' 줄) + 서비스 `readSince`·`readerAt`·`readKnown`·`recentWait`·`rememberRecent`·`recentBefore`(prefs `recent_<앱>`), `settleShelf`·`enterScreen`·`readScreen` 의 돌아와서 판단.
  이 설정을 쓰는 앱은 알라딘 하나다(킨들은 아님, A 표).
- **옮긴 뒤 `Aladin.kt`**: **【권고로 정함】 공통 도우미로 두지 않고 알라딘 어댑터 안에 둔다**(쓰는 앱이 하나). `onShelfLive` 에서 읽으러 가기 전 줄을 적고(`force` 가 아니면 1초에 한 번),
  `onEnterViewer`(바탕 뒤)에서 읽으러 간 때를 적고, `onShelf` 에서 **`super` 전에** 줄이 바뀌었으면 `opened(ByTitle, at = 읽으러 간 때)`(지금 `settleShelf` 처럼 칸 진행률·표지보다 먼저).
  메뉴로 먼저 안 경우·줄이 늦게 바뀌는 경우 규칙 그대로.
  **`onViewer` 는 대신한다**(5차 B1 **【권고로 정함】**): 메뉴 제목이 보이면, 서재를 떠난 뒤 메뉴로 **처음** 안 것인지(`readSince` 이고 `readKnown` 에 처음 더함)를 바탕 기록보다 **먼저** 정해
  `recordShown(force = 처음)` 으로 기록한다 — 같은 책을 다시 읽어도 맨 앞으로 오게(서재로 돌아와서는 줄이 그대로라 알 수 없다). 처음이면 "메뉴로 이미 앎"(`readKnown`)을 알라딘 기억에 두고,
  서재가 멈출 때(`onShelf`) 그 표시가 있으면 지우고 최근 줄로 다시 판단하지 않는다(지금 `settleShelf` 의 `readKnown.remove`).
- **서비스에서 지울 것**: 위 기억·함수, `settleShelf`·`enterScreen`·`readScreen` 의 `recent` 갈래, `ReaderSpec.recent`·`recentTitle`.
- **에뮬**(Play 에 있음): 격자 칸(누름 알림 없음) → 읽고 돌아옴 → 줄로 책 / 줄이 늦게 바뀜 / 메뉴로 먼저 안 경우(돌아와서 줄로 다시 기록하지 않음) /
  앞서 편 책을 다시 열고 메뉴를 띄움 → 같은 책이어도 맨 앞으로·편 책 기록 한 줄 / 내 파일(PDF)은 줄에 안 오름.

#### B8. Libby `com.overdrive.mobile.android.libby`(+ Readest 의 같은 능력)

- **지금**: `viewerHolder`(읽는 화면이 서재 액티비티 안의 요소) + 서비스 `withHolder`·`checkHolder`·`HOLDER_CHECK_MS`·`holderSpec`·`lastHolderCheck`, `closeHeldViewers` 의 '닫힘', `ReaderApps.hostsViewer`.
- **옮긴 뒤**: 어댑터가 `screenName` 에서 지금 화면 이름(`액티비티` 또는 `액티비티#요소`)을 돌려주면 WatchCore 가 창 바뀜마다, 내용 바뀜은 0.5초에 한 번(간격 안의 마지막 알림은 마지막 재확인 예약으로) 그것으로 화면을 다시 정하고 `onScreen` 을 부른다.
  그 판단(`bifocalHolder` 가 보이는가, Readest 는 HTML id)과 액티비티를 모를 때 서재 액티비티가 하나뿐이면 그것으로 보는 규칙은 각 어댑터에.
  `screenInsideActivity = true`, `viewerTime = MARKS_CLOSED`, `markedScreens` = 서재 액티비티 — 열림·닫힘 표시는 `onScreen` 의 공통 전이와 WatchCore 의 연결·끝 처리가 적는다.
- **서비스에서 지울 것**: `viewerHolder` 를 보는 갈래, `closeHeldViewers` 의 앱 종류 갈래, `ReaderApps.hostsViewer`·`shownByDefault` 의 표시 쓰임.
- **에뮬**(Play 에 Libby, 대출한 책이 있어야 함. 없으면 실기기 확인 대기): 서재 → 읽는 화면 열림·닫힘 표시, 읽은 시간이 읽는 동안만, 서비스 다시 시작 뒤,
  읽는 화면을 열고 곧바로 손을 뗌(알림이 끊긴 뒤에도 열림이 적힘 — 마지막 재확인).

#### B9. 문리더·Pro `com.flyersoft.moonreader*`

- **지금**: `searchesBookFile` + 서비스 `searchFile`·`fileSearched`(표지·파일 없으면 저장소에서 같은 제목 EPUB 찾기, 뒤에서).
- **옮긴 뒤**: `onViewer` 를 대신해 `readShown` → `recordShown` → 메뉴 제목이 보였으면(기록 여부와 상관없이, 지금 그대로) 그 제목의 읽고 있는 책이 표지·파일이 없고 책 파일을 읽을 수 있을 때 `inBackground { findEpub } → setFile`.
  책(정리한 제목)마다 한 번은 어댑터 기억.
- **서비스에서 지울 것**: `searchFile`·`fileSearched`, `searchesBookFile` 갈래.
- **에뮬**(Play 에 무료판): 목록 칸 / 홈 탭 최근 줄(표지만, 메뉴 제목으로) → 저장소 EPUB 표지 / TXT 파일 이름 제목.

### C. 공통으로 남는 것(앱별이 아님)

- WatchCore: 앱마다 지금 화면(`screen`)·읽는 화면이 앞에 온 때(`viewerAt`)·마지막 화면 바뀜(`lastTransition`), 멈춤·잠잠함·마지막 재확인 예약과 진행률 재시도 횟수,
  여러 흐름이 같이 쓰는 `unknownOpen`·이 앱에서 책을 편 때(`lastOpenedAt`), 훅 순서, `ctx.opened` 의 기억 비우기, 연결·끝의 열림·닫힘 표시(선언대로).
- 서비스: 액티비티인가(`isActivity`), 창·맨 앞 창, 화면 캡처와 표지 다듬기(`trimEdges`·`isFlat`)와 1.1초 간격, 서비스 생명(`downSince`·`recordLife`·`watchStopped`), 기록(WatchLog).
- 기록기: 잘라 둔 표지(`pendingCovers`, 열쇠는 앱이 정한 문자열 — 제목 또는 이지뷰어 파일), 책 찾기·넣기·되살리기, 편 책 기록, 찾기 도우미(`lookup`).
- `ScreenReader`(바탕 어댑터, 모든 어댑터가 상속): 공통 전이(`onScreen`), `clicked`·`unknownClick`·`lastShelf`(제목 못 읽은 칸 → 책 모름 → 메뉴로 알면 그때부터, 그 때는 WatchCore 의 `unknownOpen`),
  서재 칸 읽기·누른 칸·메뉴 제목 기록, 바로 자르기, 찍은 뒤 다시 보기, 책 파일 읽기 안내(`wantFiles`).

## 옮기는 단계

한 단계 = 로컬 커밋 하나 이상. 단계마다 `testDebugUnitTest`, 릴리스 빌드, 그 단계가 건드린 앱의 에뮬 시험(단계 통과 조건). 기준 커밋은 우주 지원 커밋(B1 권고).
단계는 앞 단계가 끝나야 시작한다(2·3단계는 모든 앱이 지나는 파일을, 4단계도 앱마다 같은 `Legacy` 를 고친다). 한 번에 한 단계, 에뮬도 한 대로 차례로.

1. **지금 동작 묶어 두기(테스트 먼저)**
   - 기록 규칙 단위 테스트(Robolectric, DB): `ByTitle` 되살리기·`Exact(null)` 새 책·`Exact(id)`(그 책 제목으로 기록)·`stale` 진행률·기간 지난 책·잘라 둔 표지(제목 열쇠·파일 열쇠 따로, `Exact` 에 열쇠 없으면 안 붙음)·`idByPath`(그림이면 폴더)·`idWithoutFile`·`knownTitle`·`bookOf`(기록에서 지운 나간 책도 찾음 — 이지뷰어가 같은 읽는 화면에서 그 파일의 책을 다시 알아봄, 되살리기는 안 함).
   - 등록 테스트는 이미 있다(`GuideAppsTest`). 옮기는 동안 `ReaderApps` 가 `ReaderApp` 목록으로 바뀌어도 그 테스트가 같은 것을 보도록 고친다.
   - 앱별 에뮬 시험 목록(`docs/emulator-test-cases.md` 에 `M. 자동 추가 앱별` 절: 이 문서 A·B 의 시험 줄)과 지금 판의 결과(`watch_log`·DB)를 기준으로 남긴다.
2. **BookRecorder 꺼내기, 서비스 흐름을 WatchCore 로(공통, 동작 그대로)**: `bookOpened`·`fileBookId` 중 공통 부분·`idByPath`·`idWithoutFile`·`knownTitle`·잘라 둔 표지·표지 붙이기를 `BookRecorder` 로.
   `onAccessibilityEvent`·예약·화면 기억을 시계·예약을 주입받는 `WatchCore` 로 옮긴다 — 이 단계의 WatchCore 는 아직 훅이 없고 지금처럼 `ReaderSpec` 을 부른다(코드를 옮기기만).
   서비스에는 안드로이드 연결만 남는다. **연결 시험**(G6)을 더한다(아래 `시험`). 1의 테스트가 그대로 통과. **전 앱 에뮬 시험.**
3. **`ReaderApp`·`ReaderAdapter`·`AppContext`·`ScreenReader` 만들기(공통, 동작 그대로)**: 지금 `ReaderSpec` 을 `ReaderApp`(선언)+`ScreenReader`(바탕 어댑터)로 나누고,
   WatchCore 가 훅을 부르게 한다. `ReadingLog`·`AppStore`·시작하기·안내를 `ReaderApp` 의 값으로 바꾼다(위 `서비스 밖 소비자 대응` 표).
   **과도기 상태 소유【권고로 정함】**: 특수 앱의 갈래와 기억은 4단계까지 `Legacy`(지금 서비스 코드를 그대로 옮긴 것, `ReaderSpec` 의 표시로 갈림)에 두고, `ScreenReader` 의 훅이 아직 옮기지 않은 앱 것을 `Legacy` 에 맡긴다.
   `ScreenReader` 는 바탕 흐름 기억(`clicked`·`unknownClick`·`lastShelf`·바로 자르기)만 가진다. 여러 흐름이 같이 쓰는 `unknownOpen`·`viewerAt` 은 WatchCore 가 갖고 `AppContext` 로 읽고 쓰게 한다. **전 앱 에뮬 시험.**
4. **특수 앱을 하나씩 옮기기**: 위 B 묶음을 그 순서대로(B1 우주 → … → B9 문리더). 단계마다 그 앱 어댑터가 훅을 덮고, `Legacy` 에서 그 앱 갈래·기억·표시를 지운다. 그 앱만 시험하고, 바뀐 파일을 확인한다(단계 통과 조건).
5. **정리**: `Legacy` 와 `ReaderSpec` 의 흐름 표시가 없어졌는지, 그리고 **공통 코드(`WatchCore`·`BookRecorder`·`ScreenReader`·서비스)에 앱 패키지·앱 파일 이름이 없음**을 보는 단위 테스트(소스 글자를 훑는다)를 더한다.
   CLAUDE.md·DESIGN.md 의 구조 설명과 `앱을 더하는 법` 을 CLAUDE.md 에 옮긴다.

## 데이터 호환

- prefs `reader_watch` 의 키는 이름을 바꾸지 않고 그 앱 어댑터가 `ctx.prefs` 로 같은 키를 읽고 쓴다(`tab_book_<탭>`·`key_<앱>_<열쇠>`·`book_key_<앱>_<id>`·`last_key_<앱>`·`shelf_file_<앱>`·`shelf_book_<앱>`·`recent_<앱>`·`file_<앱>`·`sync_<앱>`).
  `AppStore.openBook` 이 쓰는 `ReaderWatchService.bookKey` 는 Readest 의 `open` 안으로 간다(같은 키를 읽는다).
- DB(`book_open`·`viewer_mark`·`book_file`·`shelf`)는 BookRecorder·ReadingLog 가 지금처럼 쓴다.
- 판단 기억은 지금 서비스 필드처럼 서비스가 다시 붙으면 사라진다(어댑터 인스턴스). 프로세스 수명이던 것(Readest `totals`·데이터 캐시, 홈에서 연 파일)은 프로세스 수명 그대로 둔다(소유 표).

## 시험

- **단위**
  - 기록 규칙(1단계).
  - 데이터로 아는 앱의 판단(우주·KOReader·Readest 는 지금도 있음).
  - 어댑터: 가짜 `AppContext`·가짜 기록기(부른 것을 적어 둠)로 훅마다 무엇을 기록하는지.
  - **연결 시험(G6)**: `WatchCore` 에 가짜 알림(패키지·종류·화면 이름)과 가짜 창, 가짜 시계를 넣어 —
    ① 앱 A 읽는 화면 → 앱 B 서재 → 앱 A 로 돌아옴(화면 기억은 앱마다 따로, 예약은 전체에 하나), 앱 A 읽기 예약 중 앱 B 읽는 화면 알림 → A 의 `onViewer` 는 돌지 않음,
    ② 같은 때에 `unknown` 다음 `opened`(편 책 기록에서 뒤의 것이 쓰임),
    ③ 기록기 단계가 실패해도(예외) 다음 알림을 계속 처리하고, 앞서 누른 칸·상세 기억은 이미 비워져 다음 방문에 쓰이지 않음(기록 전 비우기). 결과는 `단계별 실패` 표대로 —
      **실패 전에 끝난 저장은 남고 실패 뒤 단계만 빠진다**(7차 B3): ① 첫 쓰기(`moveToFront`·`addOpened`) 실패 → 책·파일·편 책 기록·표지·열쇠 모두 안 적힘 /
      ① 안에서 `moveToFront` 는 저장되고 `updateStatus` 실패 → 바뀐 순서는 남고 진행률·그 뒤 단계(②~⑤·`afterOpened`)만 안 됨 /
      ② 이지뷰어 `fileInfo` 실패 → 이미 적은 경로는 남고 `shelf_file` prefs·편 책 기록·표지·`afterOpened` 는 안 됨 /
      ⑤ 표지 저장 실패 → 삼켜 `opened` 는 돌아오고 편 책 기록은 남으며 `afterOpened`(Readest 열쇠 ↔ 책)도 됨,
    ④ 서비스 연결 → 끝 → 다시 연결(WatchCore 의 열림·닫힘 표시: Libby·Readest '닫힘', KOReader '열림', 판단 기억이 비워짐, 프로세스 캐시는 남음),
    ⑤ `inBackground` 가 끝나기 전에 서비스가 끝남(결과는 적용, 예약된 `viewerAgain` 은 부르지 않음),
    ⑥ `viewerAgain` 을 두 번 부르면 앞 것이 바뀜, 읽는 화면을 떠난 뒤에는 부르지 않음, `viewerRetry` 는 잠잠 예약을 새로 걸 때 3으로,
    ⑦ `screenInsideActivity` 앱에 0.5초 안에 내용 알림 둘(두 번째에 읽는 화면이 뜸) 뒤 알림 없음 → 0.5초 뒤 재확인으로 읽는 화면이 됨·열림 표시, 서비스가 끝나면 재확인 안 함,
      서비스가 막 다시 붙어 화면을 모를 때(`screenName` 이 null)도 재확인은 걸리고 그 뒤 화면을 알면 바뀜,
    ⑧ 기록하면 비워지는 기억(`기록과 기억 비우기`): 서재 칸을 누른 뒤 책 상세로 연 책을 기록 → 앞서 누른 칸이 다음 읽는 화면에 쓰이지 않음,
    ⑨ BookFusion: 서재 칸을 누른 뒤 책 상세 → `Read` 로 읽는 화면 → 상세의 책이 기록되고 누른 칸은 버려짐 / Readest: 누른 칸이 읽는 화면에 들어올 때 기록되지 않고 열쇠를 볼 때 쓰임,
    ⑩ 이지뷰어 서재 멈춤: 같은 제목의 두 폴더 파일 칸 → 진행률·표지가 파일마다 그 책에만,
    ⑪ 네오리더: 파일을 모르는 메뉴 제목은 책으로 들어가지 않고 진행률만 편 책에 / Readest: 데이터 폴더를 읽을 수 있으면 서재 멈춤·바로 자르기가 화면을 찍지 않음,
    ⑫ 우주·네오리더 편 때: 메뉴 없이 데이터로 안 책은 읽는 화면이 열린 때, 메뉴를 띄운 뒤 데이터로 처음 안 책은 그때(지금), 제목 못 읽은 칸으로 들어왔으면 책 모름이 된 때 —
      가짜 기록기에 적힌 `at` 으로 확인 / 알라딘: 서재를 떠난 뒤 메뉴로 처음 안 책이 앞서 편 책과 같아도 다시 기록(맨 앞), 그 뒤 서재가 멈춰도 최근 줄로 다시 기록하지 않음,
    ⑬ `opened` 안 순서: 이지뷰어 `steps.found` 에서 예외 → 편 책 기록·잘라 둔 표지가 적히지 않음(지금 `retitle` 실패와 같게) / 네오리더 `steps.logged` 는 편 책 기록 뒤·표지 전,
    ⑭ 편 책 저장이 조용히 실패(`ReadingLog` 가 삼킴, `opened` 는 정상으로 돌아옴) → Readest 열쇠 ↔ 책은 `recordedBook` 을 다시 읽어 앞서 편 책이나 null 로 — 돌려받은 책에 잇지 않음(지금과 같게),
    ⑮ 네오리더: 모르는 탭(책 모름 시각 T) → 다른 앱 → 같은 탭으로 돌아옴 → 메뉴로 책을 앎 → 그 책의 편 때가 T(지우지 않음), 바탕 앱은 같은 흐름에서 지워짐,
    ⑯ 그 앱의 서재 멈춤 예약이 걸린 동안 오는 알림(누름·창 바뀜 포함, KOReader 의 내용 바뀜도)마다 멈춤이 다시 예약됨 / KOReader 의 내용 바뀜은 그 밖에 아무것도 하지 않음,
    ⑰ 바로 자르기 횟수: 창 바뀜으로 들어오면 비워지고, Libby 처럼 내용 바뀜으로 읽는 화면을 닫고 서재로 돌아오면 비워지지 않음.
  - 요소 트리가 필요한 화면 읽기는 지금처럼 에뮬.
- **앱 추가 확인**(5단계 뒤 모든 앱 추가에): 위 `앱을 더하는 법` 의 4번 — `git diff --stat` 에 다른 앱 파일이 없음, 공통 코드 변경은 기본값이 아무것도 안 하는 자리 더하기뿐, 다른 앱 단위 테스트·연결 시험을 고치지 않고 통과, 5단계의 "공통 코드에 앱 이름 없음" 테스트 통과.
- **에뮬**: 앱마다 같은 순서 — 서재에서 책 누르기 → 들어옴(제목·저자·진행률·표지) / 메뉴로 진행률 / 홈에서 그 책 누르기 / 다른 길로 연 책 / 서재로 돌아오기. 결과는 `watch_log` 와 DB 로 비교(1단계 기준과 같아야 한다).
  - 에뮬에 깔린 앱(`CremaPebble_A11_Play`): 교보도서관·교보eBook(휴대폰)·밀리(e-ink·휴대폰)·알라딘·부커스·리디·YES24(my YES·도서관 e-ink·휴대폰·eBook)·문리더·이지뷰어·킨들·Libby·KOReader·BookFusion·우주.
  - 다른 에뮬: Readest(`CremaPebble_A11_Play_Readest`), 네오리더(BOOX 에뮬 `Palma2_A13` 에 있으면).
  - 로그인·대출이 필요한 앱은 에뮬에 있는 책으로만. 안 되는 앱은 실기기 확인 대기(단계 통과 조건).
- **실기기**: 크레마 페블(주로 교보도서관·밀리), BOOX(네오리더)는 사용자에게 부탁한다.

## 위험

- 2·3단계는 모든 앱이 지나는 길을 옮긴다. 1단계 테스트·연결 시험과 전 앱 에뮬 비교 없이 진행하지 않는다.
- 타이머·재시도 상한은 앱마다 미묘하게 기대가 달라, 값은 지금 상수 그대로 옮긴다(`VIEWER_RETRIES` 3·`FILE_RETRIES` 5·`FILE_TRIES` 6·`VIEWER_SWAP_MS` 5초·`WEB_KEEP_MS` 3초·`CLICK_OPEN_MS` 120초·`OPENING_MS` 30초·`OPENED_SLACK_MS` 5초·`EAGER_TRIES` 3·`PENDING_MAX` 12).
- 예약(읽는 화면 잠잠·서재 멈춤·마지막 재확인)과 진행률 재시도 횟수는 지금처럼 전체에 종류마다 하나로 둔다(앱마다 나누면 다른 앱으로 넘어간 뒤 앞 앱 예약이 돈다). 연결 시험 ①·⑥으로 확인한다.
- 일이 크다(서비스 대부분 + 앱 파일 25개). 단계 사이에 릴리스가 끼면 그 판은 반쯤 옮긴 구조로 나간다. 단계마다 동작이 같아야 하는 이유이고, 실기기 확인 대기 앱이 든 판은 내보내지 않는다.
- 공통 코드에 "기본값은 아무것도 안 함"인 자리를 더하는 일이 쌓이면 공통 코드가 커진다. 자리를 더할 때 이 문서(옮긴 뒤에는 CLAUDE.md)에 쓰는 앱과 함께 적는다.

## 구현 메모(2026-10-24)

단계마다 로컬 커밋으로 구현했다(1단계 `847e849` … B8·B9). 계획과 다르게 한 곳:

- **2·3단계 경계**: 2단계는 `BookRecorder`(`ShelfRecorder`)만 꺼냈다. 훅이 없는 `WatchCore` 는 가짜 앱으로 시험할 수 없어, `WatchCore`·연결 시험은 3단계에서 훅과 함께 만들었다.
- **`Legacy`**: 3단계에서 서비스 코드를 그대로 옮긴 임시 어댑터 `LegacyReader`(모든 앱의 기본)로 두고, 4단계에서 앱마다 덮는 어댑터로 옮기며 갈래를 지웠다. B9 에서 지우고 기본 어댑터를 `ScreenReader` 로 했다.
- **서재 안 읽는 화면의 이름**(`screenName`, Libby·Readest): 선언(`viewerHolder`·`showsViewer`)만 보는 일반 규칙이라 B2 에서 바탕 `ScreenReader` 로 올렸다. 그래서 Libby(B8)는 따로 어댑터가 없다.
- **B6**: `DetailMemory` 대신 두 앱이 같이 쓰는 어댑터 `DetailReader`(책 상세 읽기는 각 앱의 `webBook` 을 넘긴다).
- **Readest 열쇠 ↔ 책**: `afterOpened` 대신 `ctx.opened` 가 돌아온 뒤 바로(`newKeyed`) — 예외면 둘 다 건너뛰어 같다.
- **앱 처리 예외**: `WatchCore` 가 그 알림만 건너뛰고 `WatchLog` 에 `ERROR` 로 적는다(예전에는 서비스가 죽었다).
- **옮긴 뒤 리뷰·기기 시험으로 더한 공통 자리(2026-10-24)**: 모양(서비스 → `WatchCore` → 앱 선언 + 어댑터 → 기록기)은 그대로이고, 다음을 더하거나 바꿨다.
  - 패키지는 같은데 판에 따라 다른 앱(부커스 2.0): `ReaderApp.handles` 와 `installed()`, `WatchCore` 는 패키지마다 앱 목록을 두고 알림마다 깔린 판을 고른다(계획에 없던 경우).
  - 훅 `onWindow(ctx, cls)`: 뜬 창의 이름을 넘긴다(밀리 받기 창).
  - 도우미 `DetailReader`: `keepsHidden(ctx, gone)`(기본 아니다), 상세를 마지막으로 본 때로 누른 칸과 가름, 상세 안의 단추는 누른 칸을 버리지 않음.
  - 서재에서 칸이 아닌 것을 누르면 앞서 누른 칸을 잊기: 처음에는 바탕 `ScreenReader` 에 넣어 모든 앱의 흐름을 바꿨으나(공통 변경), my YES 처럼 안내 창 단추의 창 번호를 뒤 창의 것으로 보내는 앱에서 기억을 잃을 수 있어
    앱 선언 `forgetsClickOnOtherTap`(기본 아니다, 바꾸기 전 흐름 그대로)으로 바꾸고 문제를 본 교보도서관만 켰다. 서재 화면 창인지는 서재 화면이 뜰 때·서재가 멈췄을 때(그 앱의 창이 하나뿐이면) 기억한 창 id 로 가린다(모달 대화상자는 창 목록에서 아래 창을 빼고, 누름 알림 전에 닫히기도 한다. 화면이 바뀐 알림 때는 시작 화면의 창이 아직 맨 앞이기도 하다, 2026-10-24 페블 알라딘).
  - `WatchCore.capture`: 찍는 사이 그 앱을 떠났거나 화면이 바뀌었으면 찍은 것을 버린다.
  - 기록기 `coverFromFile`: EPUB 에서 표지를 실제로 꺼낼 수 있을 때만.
- **시험**: 단위 `BookLookupTest`·`BookRecorderTest`·`WatchCoreTest`(연결 시험)·`CommonCodeTest`(공통 코드에 앱 이름 없음). 에뮬은 `tools/emu/watch-*.sh` 로 우주·BookFusion·이지뷰어(한 바퀴)·KOReader·문리더(주 에뮬)·Readest(Readest 에뮬)를
  단계마다 바꾸기 전 빌드와 비교해 모두 같았다.
- **에뮬 더(2026-10-24)**: Libby·킨들·문리더 유료판도 바꾸기 전 빌드와 같았다. 네오리더는 `Poke3Dpi350_A10`(Go6 시험판, 여러 탭)에서 두 앱 데이터를 비우고
  바꾸기 전·뒤 빌드를 두 번씩 같은 순서(메뉴 없이 PDF / 파일 안 제목이 같은 다른 PDF / 아는 PDF 다시 / CBZ 메뉴 없이 → 메뉴)로 돌려 DB·탭 기억이 같았고, 홈에서 누르면 그 책의 탭으로 열렸다
  (제목 없는 진행률 다시 보기 줄이 한 번은 한쪽에만 있었으나 다음 판에서는 둘 다 있어 때의 차이).
- **실기기(크레마 페블, 2026-10-24)**: 밀리(e-ink) 서재 칸, 알라딘 격자 칸(메뉴 제목 → 나간 책 되살림, 돌아와서 다시 적지 않음), 리디(26.10.2 업데이트 앞뒤), 킨들(나간 책 되살림·저자),
  교보eBook(e-ink, 아래 줄 진행률·메뉴 제목)이 바꾸기 전과 같게 들어왔다. 밀리(e-ink) 검색 › 상세 › `바로 읽기` 는 밀리가 다운로드에 실패해 읽는 화면까지 못 갔다(실패해도 책은 안 들어감).
  교보도서관·YES24 전자도서관은 빌린 책이 없어 못 봤고, my YES 는 USB 디버깅을 끈 채 사용자가 눌러 봐야 한다. 부커스는 시험 중에 2.0.0 으로 업데이트되어 지금 코드로는 읽을 수 없다(READER_APPS.md 부커스).
- **같이 고친 것**: 서재에 책이 한 권뿐일 때 위 탭을 누르면 그 책을 누른 것으로 잡았다(바꾸기 전부터. 부커스 1.x 는 만료된 칸이 그대로 들어갔고, 밀리 e-ink 는 누른 칸으로 기억만 했다).
  위로 올라가며 칸을 찾을 때 목록(RecyclerView 등)을 넘지 않고, 제목이 누른 곳 밖의 목록에만 있는 묶음은 칸으로 보지 않는다(`ReaderApp.cellOf`·`titleInOtherList`, `ClickedCellTest`).
  고친 뒤 페블에서 밀리 탭 누르기(책 아님)·칸 누르기, 알라딘·리디·킨들이 같게 들어오는 것을 봤다.
- **실기기(갤럭시 A23 안드로이드 14, 2026-10-24)**: 밀리(휴대폰) 5.47.1.0(내서재 표지 → 책·저자·표지, 메뉴 진행률), 교보eBook(휴대폰) 3.9.14(메뉴 제목 → 책, 아래 줄 진행률),
  YES24 eBook 3.4.44(서재 칸 → 책·저자·표지·진행률, 책이 한 권인 서재에서 탭을 눌러도 책 아님)가 들어왔다. 부커스 2.0.0 은 새 앱 파일 `Bookers2` 로 읽는다(READER_APPS.md).
- **교보도서관(페블, 2026-10-24)**: 받지 않은 칸을 눌러 받기만 했는데 그 책이 들어왔다(개편 전부터, 누르면 바로 편 것으로 봤다). `openOnViewer` 로 읽는 화면이 열려야 편 것으로 바꿨고,
  받아 둔 「성과를 만드는 생각」을 누르면 읽는 화면이 뜬 뒤 들어오는 것을 봤다(저자·반납일·표지). 고친 뒤 받기만 한 책이 들어오지 않는 것은 사용자가 확인했다(기록에도 누름만, 편 책 없음).
- **부커스 1.0.33(페블, 2026-10-24)**: 받기 단추만 눌러도 들어오던 것을 교보도서관처럼 `openOnViewer` 로 고쳤고, 받기만 한 책은 안 들어오고 열면 들어오는 것·탭 누름은 책이 아닌 것·반납일을 봤다.
- **밀리(e-ink) `바로 읽기`(페블, 2026-10-24)**: 개편 전부터 있던 두 가지를 고쳤다(`DetailReader`, BookFusion 도 같이 쓴다).
  ① 상세 표지가 들어오지 않았다: 목록에 없는 책은 상세 표지를 찍지 않았고(`onShelf` 가 목록에 있는 책만), 찍어도 찍은 뒤 다시 보기가 서재 칸끼리만 맞춰 상세 자리를 늘 버렸다(`stillSpots`).
  → 상세가 멈추면 목록에 없는 책도 잘라 두고, 상세 자리는 그 상세가 그대로면 둔다. ② 받아야 하는 책은 받기 창(`SearchDownloadDialog`)이 떠 있는 동안 상세가 안 보여 3초 뒤 잊고,
  읽는 화면이 열릴 때 메뉴 제목으로만(저자 없이) 들어왔다 → 밀리만의 일이라 도우미에는 기본이 아무것도 안 하는 자리(`keepsHidden`)만 두고, 밀리 어댑터 `MillieReader` 가
  상세를 기억한 채 받기 창(`SearchDownloadDialog`)이 뜨고 상세가 그 무렵(2초 안) 사라졌으면 60초까지 잊지 않게 했다(`onWindow`·`keepsHidden(ctx, gone)`. 받기를 취소하고 뒤로 나가 사라진 것은 3초). 나눈 뒤에도 받아야 하는 「할 수 있다! AI 주식 투자」가 저자 머신러너·표지로 들어왔다. 고친 뒤 받아야 하는 「AI 시대에도 승승장구하는 아날로그형 직장인」이
  제목·저자 김찬기·상세 표지로 들어왔다. BookFusion 은 `CremaPebble_A11_Play`(BookFusion 2.24.0, 사용자 계정)에서 이 수정 앞뒤 빌드로 `bookfusion` 순서(격자 칸 → 상세 → `Read`)를
  앱 데이터를 비우고 두 번씩 돌려 같았다(첫 바퀴는 서비스가 붙는 때 차이로 기록 줄 하나만 달랐다).
- **YES24 전자도서관 e-ink 1.0.27(페블, 2026-10-24, 고양시도서관센터)**: 대출 → 상세의 `다운로드 받기`(받기만)는 들어오지 않고(`openOnViewer`, 상세는 `LibDetailActivity` 라 서재가 아니다),
  내 서재 칸을 눌러 열면 「고양이와 물리학」이 저자 블라트코 베드럴·표지·진행률 1%·반납일(`반납 D-day` → 오늘)로 들어왔다. 책이 한 권인 서재에서 탭을 눌러도 책이 아니다.
- **YES24 전자도서관 휴대폰 1.0.63(갤럭시, 2026-10-24, 같은 계정)**: 내 서재 칸의 받기 단추만 누르면 들어오지 않고, 표지를 눌러 열면 저자·표지·반납일(`D-15 (2026.11.08 까지)`)로,
  메뉴를 띄우면 진행률 2% 가 들어왔다. 탭 누름은 책이 아니다. 책을 처음 열면 읽는 화면 위에 다섯 쪽짜리 사용 안내(`LibTutorialActivity`)가 떠, 위 `닫기` 를 누르기 전에는
  쪽 넘김·메뉴가 그 안내로 가 진행률이 들어오지 않는다(닫으면 그 뒤로는 안 뜬다). 페블 e-ink 판은 같은 책을 빌린 직후 서재에 `반납 D-day` 로 보여 반납일이 오늘로 적혔다
  (서재를 볼 때마다 다시 적으므로 앱이 맞게 보이면 고쳐진다, 페블을 다시 보지 못함).
- **my YES 1.5.99(페블, 2026-10-24, 사용자가 USB 디버깅을 끈 채 눌러 보고 기록으로 확인)**: 기본책장에서 연 「메밀꽃 필 무렵 [에디션]」이 저자 이효석(서재 칸)·진행률 46% 로,
  구매목록 창의 `열기`(누른 요소가 비어 와 먼저 '책 모름')로 다시 연 같은 책은 메뉴 제목으로 그 책이 다시 앞에·50% 로 들어왔다. 받기만 한 「수상한 사람들」은 들어오지 않았고, 표지는 화면 캡처가 막혀 없다(원래대로).
- **이지뷰어 남은 경우(에뮬 `CremaPebble_A11_Play`, 2026-10-24)**: 바꾸기 전(`eedd412`)·뒤 빌드를 앱 데이터를 비우고 같은 순서로 돌려 같았다 —
  같은 이름 다른 폴더(`SameA`·`SameB` 의 `same.epub` 가 두 권, 각자 경로·저자), 다른 앱으로 연 파일(책 모름) → ▶ 열기 확인 창 `확인`(그 파일로 넣음, 「세 번째 시험 책」·이순신·경로),
  원격 파일(Mac 의 pyftpdlib `10.0.2.2:2121` 의 `#macftp/novels/Ftp_Book2.epub` → 받은 파일 `easyview/down/<서버>/novels/ftp_book2.epub` 로 「FTP 두 번째 책」·정약용).
  다른 곳은 기록 줄 하나뿐: 바꾸기 전 빌드는 책 하나인 폴더에서 위 경로 단추(`#휴대폰`)를 누른 것을 그 책을 누른 것으로 적었다(오늘 고친 책 한 권 서재의 탭 누름).
- **앱별 코드 리뷰(2026-10-24, 두 번)**: 1차 지적을 고친 뒤 페블(교보도서관 칸 → 열면 들어옴, 밀리 e-ink 받은 책·받아야 하는 책의 `바로 읽기` → 저자·표지, 부커스 1.0.33 받기만 → 안 들어옴·열면 들어옴)과
  에뮬(BookFusion 두 번, 이지뷰어 원격·같은 이름 다른 폴더·▶ 확인 창)에서 바꾸기 전과 같았다. 2차에서 고친 것: 책 상세를 마지막으로 본 뒤 누른 칸이 먼저(`DetailReader` 사라진 때는 늦게 적혀),
  밀리 받기 창 기억은 상세가 그 창 무렵 사라졌을 때만, Readest 열쇠의 책이 나간 책이면 그 책만 되살리고 같은 제목의 나간 책이 다른 열쇠면 새 책·찍기 전에 거름·config.json 막 고친 것은 다시 읽음,
  이지뷰어 원격 파일은 위치·이름 → 책을 따로 적어 다시 열어도 같은 책(로컬 같은 제목과 섞지 않음)·`Vol#3 x.cbz` 는 장이 아님·확인 창 취소는 10초, 부커스 2.0 메뉴 제목은 나간 `(접근성)` 책도 보고 일반판 책이면 그대로,
  네오리더 메뉴 없이 데이터로 안 책이 떠난 탭의 책이면 이 탭의 책으로 보지 않음·파일을 받는 화면을 거쳐 연 완독한 책은 되살림·모르는 탭 시각은 날이 바뀔 때만 지움,
  EPUB 표지는 실제로 꺼낼 수 있어야 있다고 봄·같은 책 복사본은 한 권으로, KOReader 두 판이 서로의 책에 파일을 적음, 문리더는 이 앱 책만 저장소를 훑음.
  그 뒤 남긴 것을 기기에서 확인해 고쳤다(2026-10-24): 우주 닫은 책 기록(에뮬, 고치기 전 3/3 → 4/4 맞음), 부커스 2.0 `읽고 싶은 책` 탭·받지 않은 칸(갤럭시: 칸으로 못 읽어 `(접근성)` 없는 메뉴 제목으로 들어왔다 →
  격자·리스트 모두 그 칸, 받기 창·받는 중은 읽는 화면이 아님), YES24 eBook 묶음 칸(갤럭시: 시리즈 이름으로 들어왔다 → 시리즈 책 목록도 서재), 찍는 사이 앱을 떠나면 버림(단위 시험),
  화면만 한 대화상자는 크기 대신 창 층으로(맨 아래 층이 화면 자체. 단위 시험, 기기에서는 화면을 누른 것이 그대로 화면으로 보였다).
- **세 번째 리뷰(2026-10-24)**: 대화상자 누름을 창 id 로, 찍는 사이 화면이 바뀌면 버림, 밀리 받기 창 기억은 상세가 다시 보이면 지움·120초, 휴대폰 밀리 첫 저자 표시,
  Readest 같은 읽는 화면 안 열쇠 바뀜·library.json 캐시·지운 책, 이지뷰어 원격 표는 정말 원격일 때만·받은 파일을 나중에 찾아도 같은 책·누른 칸 파일 이름 맞추기, 부커스 2.0 `내 서재` 는 위 제목 글자만·
  `다운로드` 로 시작하는 제목·`D-day`·나간 `(접근성)` 책보다 읽고 있는 일반판, 네오리더 떠난 탭의 책은 묻기에서 빼고 계속 묻기·파일로 열면 지움·날 바뀜은 탭이 올 때, 우주 제목 못 읽은 칸 누른 때도 기준,
  EPUB 찾기는 부제가 둘 다 있고 다르면 다른 권, 문리더 찾는 사이 다른 앱 책이 되면 버림. 기기: 우주 3/3·BookFusion·이지뷰어(같은 이름·원격 두 번)·부커스 2.0(홈 카드 아님·격자·리스트)·YES24 시리즈 권이 맞게 들어왔다.
- **마지막 판 다시 시험(2026-10-24, `cfd69cc`)**: 이 세션 전 판(`58816fb`)과 같은 순서로 A/B.
  - 페블: 교보도서관(탭 누름 뒤 칸 → 들어옴), 밀리 e-ink 서재 칸, 부커스 1.x 칸이 맞게 들어왔다.
  - 에뮬 `CremaPebble_A11_Play`: KOReader(책 둘 번갈아, 바퀴마다 책 파일을 새로 넣음 — 같은 파일을 여러 번 열면 저장소가 접근 시각을 다시 맞추지 않아(relatime) 두 판 모두 열림을 놓친다),
    KOReader 새 책 → 홈, 문리더(둘 번갈아)·새 책 저장소 찾기, Libby 가 같았다(첫 바퀴·앱 상태 순서 차이만).
  - `CremaPebble_A11`: 킨들(로그인, 세 바퀴)·문리더 유료판 새 책이 같았다. `CremaPebble_A11_Play_Readest`: Readest 는 같고, 진행률은 새 판이 방금 고친 config.json 을 다시 읽어 그 순간 쪽(8/81 → 10%)으로 적었다.
  - `Poke3Dpi350_A10`: 네오리더 다섯 단계 두 번씩 같았다.
  - 덮어 깔기: 공개한 1.5.12 릴리스를 깨끗이 깔아 킨들·문리더 유료판 책 셋을 넣은 뒤 새 릴리스 빌드를 덮어 깔았다 → 책·자동 추가 켜짐이 그대로이고, 킨들 책을 열면 중복 없이 맨 앞으로.
  - 그 뒤 칸 잊기를 교보도서관만으로 좁혀(`forgetsClickOnOtherTap`) my YES 등 다른 앱은 바꾸기 전 흐름 그대로라 my YES 안내 창 시험은 필요 없어졌다.
- **아직 확인 못 함**: 리디 원스토어판·채리뷰어(APK 를 본 적 없어 Play 판·이지뷰어와 같은 코드라고 보고 읽는다).
  그 밖의 앱은 실기기나 에뮬에서 봤다(네오리더·우주·KOReader·Readest·BookFusion·문리더·Libby 는 에뮬). 정한 것 10 의 실기기 확인은 위로 마쳤다.

## 검토 반영

### 1차(codex gpt-6.1-sol/high, needs_work 72)

| 지적 | 반영한 곳 |
|---|---|
| B1 `BookRef` 에 책 id 없음 | `Target.Exact(id)`(기록기 절), 대응표의 네오리더 탭·이지뷰어 |
| B2 통합 찾기 규칙이 KOReader·Readest 의 제목 찾기와 다름 | `Target.ByTitle`/`Exact` 를 앱이 고름, 대응표, B2·B3 |
| B3 `reset()` 반환값 없음, 연결·끝 두 곳 | 연결·끝 표시(8차에 WatchCore 가 선언대로 적게 고침), B8 |
| B4 잘라 둔 표지 파일 열쇠 | `pendingCover(key)`·`coverKey`, C 절, B5 |
| B5 킨들은 `recent` 를 안 씀 | A 표로 옮김, B7 은 알라딘만 |
| B6 서비스와 같이 사라지지 않는 기억 | 소유 표(바깥 데이터 캐시·`HomeSignals`), 데이터 호환 |
| G1 적용 결과(책 id)를 돌려받을 길 | 기록기가 책을 돌려줌 `opened(): ShelfBook`【권고로 정함】 |
| G2 홈 열기 실패·다시 찾기·StrictMode·열린 뒤 알림 | `OpenResult`(`File`·`Start(started)`·`FindFile`), B4·B5 |
| G3 멈추기 전 관찰·확인 창 | `onShelfLive`·`onWindow`·`onClick`(서재 밖), 순서표, B5·B6·B7 |
| G4 서비스 밖 소비자 계약 | `ReaderApp` 과 `서비스 밖 소비자 대응` 표 |
| G5 `later`·`inBackground` 규칙 | `AppContext` 절(`viewerAgain`·`viewerRetry`·`inBackground`), 위험 |
| G6 연결 구조 시험 | `WatchCore` 분리, 시험의 연결 시험 |
| A 미정 결정·성공 기준 예외 | 아래 `정한 것`, 목표의 등록 자리·단계 통과 조건, 3단계 과도기 소유 |

### 2차(codex gpt-6.1-sol/high, needs_work 83)

| 지적 | 반영한 곳 |
|---|---|
| B1 잘라 둔 표지를 붙인 뒤 파일을 적어 EPUB 원본 표지를 막음 | 기록기 절 `파일과 표지의 순서`, 대응표·B5 의 이지뷰어 |
| B2 네오리더 열기 실패를 `Launch` 로 바꾸면 다시 찾기·안내가 빠짐 | `OpenResult` 의 `onFail`(`Fallback.LAUNCH`/`FIND_FILE`), 홈에서 열기 앱별, B4 |
| G1 `onContent` 간격 안 마지막 알림의 재확인 예약 | 훅 절 `마지막 재확인`(WatchCore 소유·실행·취소), 순서표, B8, 연결 시험 ⑦ |
| A1 바탕·전용 어댑터 관계와 기록 때 기억 비우기 | `어댑터 관계`(상속·순서·바탕과 다른 훅 표), `기록과 기억 비우기`, `AppContext`, C 절, 연결 시험 ⑧ |
| nit XML 패키지 테스트는 이미 있음 | 목표의 등록 자리, 1단계 |

### 3차(codex gpt-6.1-sol/high, needs_work 87)

| 지적 | 반영한 곳 |
|---|---|
| B1 `onScreen` 에 일괄 "`super` 먼저"를 쓰면 상세보다 누른 칸이 먼저 기록되고 Readest 누른 칸이 소비됨 | `onScreen` 은 덮지 않음·`onEnterViewer`(8차 전 이름 `recordOnEnter`), `읽는 화면에 들어올 때` 표, 연결 시험 ⑨ |
| B2 이지뷰어 `onShelf` 가 대신하는 훅 표에 없어 같은 제목 다른 파일에 진행률·표지 | 바탕과 다른 훅 표의 이지뷰어 `onShelf`, 서재 조각(`readShelfCells`·`shelfStatuses`·`shelfCoverSpots`), 연결 시험 ⑩ |
| B3 예약을 앱마다 하나로 나누면 앞 앱 예약이 돎 | `예약은 전체에 하나`(AppContext 절), 마지막 재확인 소유, 위험, 연결 시험 ① |
| G1 재확인 대상을 `onContent` 의 null 로 판별 | `ReaderApp.screenInsideActivity`, `screenName`(8차 전 이름 `onContent`)의 null = 지금 모름, 순서표, 연결 시험 ⑦ |

### 4차(codex gpt-6.1-sol/high, needs_work 89)

| 지적 | 반영한 곳 |
|---|---|
| B1 대신하는 훅 표가 네오리더도 파일 모르면 `recordShown` 이라 B4 와 어긋남 | 바탕과 다른 훅 표를 우주·네오리더로 나눔(네오리더는 제목 기록 안 함·진행률만), B4, 연결 시험 ⑪ |
| B2 기억 비우기를 기록 뒤로 옮겨 기록 실패 때 누른 칸이 남음 | `기록과 기억 비우기` 를 비우기(기록 전)·기록·이어 두기(돌아온 뒤)로, `beforeOpened`, 표의 `언제`, `AppContext`, 연결 시험 ③ |
| G1 Readest 가 상속한 서재 훅의 자르기를 막는 계약 없음 | `ReaderAdapter.capturesCovers`, `AppContext.capture` 맨 앞 확인, B2, 연결 시험 ⑪ |

### 5차(codex gpt-6.1-sol/high, needs_work 89)

| 지적 | 반영한 곳 |
|---|---|
| B1 알라딘의 메뉴 첫 확인(`first`·`readKnown`) 분기가 갈 곳이 없음 | 바탕과 다른 훅 표의 알라딘 `onViewer`, `recordShown(force)`, B7(`readKnown` 은 알라딘 기억·`onShelf` 에서 지움), 연결 시험 ⑫ |
| B2 우주의 편 때를 늘 읽는 화면이 열린 때로 적음 | `데이터로 안 책의 편 때`(책 모름 때 → 메뉴 없이 알았을 때만 열린 때 → 지금), B1 우주·B4 네오리더, 연결 시험 ⑫ |

### 6차(codex gpt-6.1-sol/high, needs_work 90)

| 지적 | 반영한 곳 |
|---|---|
| B1 이지뷰어 제목 바꾸기·파일 적기를 기록 뒤로 보내 순서가 지금과 다름 | `BookRecorder.opened` 의 안 순서 ①~⑤와 `OpenSteps.found`·`logged`, 대응표(이지뷰어·네오리더), `파일과 표지의 순서`, 기억 표, B4·B5, 연결 시험 ⑬ |
| G1 `opened` 가 정상으로 돌아와도 편 책 저장 성공이 아님 | `성공의 뜻`(예외 없이 돌아옴, 저장 실패는 지금처럼 삼킴), 기록 뒤 일에 쓰는 책 표, 연결 시험 ⑭ |

### 7차(codex gpt-6.1-sol/high, needs_work 91)

| 지적 | 반영한 곳 |
|---|---|
| B1 공통 전이가 네오리더의 "다시 들어와도 책 모름 시각 유지" 예외를 지움 | `ReaderApp.keepsUnknownOnReenter`, `onScreen` 공통 전이 4번, B4 네오리더, 연결 시험 ⑮ |
| B2 잘라 둔 표지 저장 실패는 지금 삼키는데 "어느 단계든 예외면 중단"으로 적음 | `BookRecorder.opened` 설명, `단계별 실패` 표(③·⑤ 삼킴, ①②④ 밖으로), `한 길로` ② 줄 |
| B3 시험 ③이 실패 전에 끝난 저장까지 없어야 한다고 요구 | 연결 시험 ③을 단계별로(실패 전에 끝난 저장은 남음), `단계별 실패` 표 |

### 8차(codex gpt-6.1-sol/high, needs_work 93)

| 지적 | 반영한 곳 |
|---|---|
| B1 `BookLookup.bookOf` 를 "지우지 않은 나간 책"으로 적었으나 지금 `BookShelf.bookOf` 는 지운(hidden) 책도 돌려줌 | `BookLookup.bookOf` 설명, 1단계 기록 규칙 테스트 |
| B2 연결 시험 ③ 의 ① 실패 기대가 `moveToFront` 저장 뒤 `updateStatus` 실패를 구분하지 않음 | 연결 시험 ③(첫 쓰기 실패 / 순서는 남고 그 뒤만 빠짐) |
| nit 킨들은 `coverStill` 을 덮지 않음(BookFusion·Readest 가 덮음) | A 표 킨들 줄, B2·B6 |

### 8차 정리(검토 없이, 7차 판을 지금 코드와 대조)

설계는 그대로 두고, 지금 코드와 다르거나 문서 안에서 서로 어긋난 곳을 고쳤다. 줄 번호는 우주 작업이 든 지금 작업 트리의 `ReaderWatch.kt`.

| 어긋난 곳 | 지금 코드 | 고친 곳 |
|---|---|---|
| `onScreen` 을 덮지 않는다면서 앱마다 읽는 화면에 들어올 때 비우는 기억(이지뷰어 파일 기억은 `!again` 일 때, 우주·네오리더 묻기 횟수)을 둘 자리가 없음 | `enterScreen` 224~231 | `recordOnEnter` → `onEnterViewer(ctx, from, again)`(기억 비우기와 기록 순서), `onScreen` 은 `ScreenReader` 의 final, 공통 전이를 번호로 |
| 읽는 화면이 앞에 온 판단과 `onViewer` 판단이 다름 | 221 `isReaderScreen`(이름 규칙 포함) / 365·381 `isViewer` | 공통 전이 4번과 그 아래 줄 |
| 연결·끝 표시를 어댑터 훅이 직접 적는다는 것과 `viewerTime` 으로 적는다는 것이 둘 다 있음 | `closeHeldViewers` 318(앱 종류로만 갈림) | `onAttach`·`onDetach` 훅을 없애고 WatchCore 가 선언대로(순서표, C, 연결 시험 ④) |
| KOReader 내용 바뀜 무시를 `viewerTime` 에 묶음(뜻이 둘) | 164 `fileState` | `ReaderApp.readsContent` |
| 모든 알림이 그 앱의 서재 멈춤을 다시 예약하는 것이 순서표에 없음 | 148 | 순서표 첫 줄, 연결 시험 ⑯ |
| 화면 이름 판단을 내용 바뀜에만 부름(창 바뀜에도 씀), 액티비티를 모를 때 규칙 | 154 `withHolder`, 284~289, 306 | `onContent` → `screenName(ctx, activity)`, 순서표 |
| 바로 자르기 횟수를 비우는 때 | 159(창 바뀜에서만) | `onScreen(byWindow)`, 공통 전이 1번, 연결 시험 ⑰ |
| `onShelfLive` 의 1초 제한을 WatchCore 가 건다고 적음 | 338·351 두 간격이 따로, 바로 자르기는 준비됐을 때만 | `onShelfLive(ctx, force)`, 간격은 조각마다(순서표 아래), 소유 표 끝 줄 |
| 진행률 재시도 횟수가 "전체에 하나"와 "어댑터가 가짐"으로 서로 어긋남 | 136·367(잠잠 예약마다 3, 전체에 하나) | `ctx.viewerRetry()`, `AppContext` 절, 위험 |
| `Exact` 에도 제목으로 잘라 둔 표지를 붙인다고 적음 | 893 byFile 이면 파일 열쇠만 | 기록기 절 `잘라 둔 표지` |
| `Exact(id)` 의 책이 있으면 그 책 제목으로 기록하는 것이 빠짐 | 862 | `Target.Exact` 설명, 1단계 테스트 |
| 기록 뒤 일은 돌려받은 책에 쓴다고 일반화했으나 우주·네오리더 파일 적기는 다시 읽음 | 468 `recordedBook(spec)?.let { setFilePath }` | `성공의 뜻` 아래 `기록 뒤 일에 쓰는 책` 표, 대응표, B1·B4 |
| 기록기 함수가 지금 쓰는 것과 다름(저자 없는 `progress`, 조건 있는 `retitle`) | 589·875 `updateStatus(…, author)`, 756~768 | `status(id, progress, due, author)`·`setTitle`·`setAuthor`·`openedId`·`hasPendingCover`, `BookLookup` 목록 |
| `AppContext` 에 이 앱에서 책을 편 때·기록이 없음 | 697 `lastOpenedAt`, `WatchLog.add` 곳곳 | `lastOpenedAt`·`log` |
| 찍은 뒤 다시 보기·기본 그림 거르기·자리별 결과가 계약에 없음 | 1008·1019·1031 | `stillSpots`, `ReaderApp.placeholderCover`, `CoverSpot`, `capture` 설명 |
| 이지뷰어 `onClick` 이 파일 칸을 대신한다고 적음 | 이지뷰어는 `openOnViewer`, 파일 칸은 252 에서 가름 | 바탕과 다른 훅 표 아래 줄 |
| 알라딘 `onShelf` 를 `super` 뒤라고 적음 | 942~955 가 칸 진행률·표지 앞 | 표·B7(`super` 전에) |
| BookFusion·밀리 `onShelf` 를 `super` 뒤 상세 표지라고 적음(찍기가 둘로 나뉘어 1.1초 간격에 걸림) | 958~963·980 상세 자리를 칸 자리와 한 번에 찍음 | 표에서 `onShelf` 대신, 표 아래 줄, B6 |
| 책 칸이 아닌 것을 누른 때 상세를 기억할 자리가 없음 | 185~190 `cell == null` 갈래 | `ScreenReader.onClickNoCell`, B6 |
| `hostsViewer`·`findBookFile` 대응이 선언으로 안 됨 | `ReaderApps` 33(액티비티 목록), `Apps.kt` 243(공통 고르기·저장소 찾기) | `markedScreens`, `bookFiles`(지금 그대로), 소비자 대응 표 |
| Readest 가 열쇠로 아는 책이면 기록하지 않아도 누른 칸을 비우는 것이 빠짐 | 585 | `기록과 기억 비우기` 아래 줄, B2 |
| 2단계가 훅이 아직 없는데 "훅 부르는 순서"를 꺼낸다고 적음, 3단계 `Legacy` 의 자리가 모호 | — | 2·3단계 |
| "안드로이드 없이 단위 테스트" | 창은 `AccessibilityNodeInfo` | 시계·예약 주입, 서비스 없이 Robolectric |
| 문서 안: 네오리더 줄이 둘, 소유 표 제목("셋")과 줄 수, `성공의 뜻` 아래 엉뚱한 줄, 정한 것 14번이 끝에 | — | 표 합침, `상태의 소유`, 줄을 `기록과 기억 비우기` 로, 번호 순서 |
| 앱 추가 규칙이 목표에만 있고 확인 방법이 없음 | — | 목표 4, `앱을 더하는 법`, 단계 통과 조건, 5단계 테스트, 시험의 `앱 추가 확인`, 정한 것 28~30 |

## 정한 것(열린 질문이던 것)

1. **【권고로 정함】 우주 먼저 커밋**: 지금 우주 지원을 커밋해 기준으로 삼고 B1 에서 옮긴다(B1 절).
2. **【권고로 정함】 기록기가 책을 돌려줌**: Fact 목록 대신 `BookRecorder` 를 부르고 책을 돌려받는다(기록기 절).
3. **【권고로 정함】 책 가리키기**: `Target.ByTitle`/`Exact(id?)` 둘, 어느 쪽인지는 지금 코드 그대로 앱이 고른다(대응표).
4. **【권고로 정함】 훅과 순서**: `onScreen`(final)+`onEnterViewer`·`onWindow`·`screenName`·`onShelfLive`·`onShelf`·`onClick`·`onViewer`·`onHome`·`beforeOpened`·`afterOpened`·`capturesCovers`·`stillSpots`, `ScreenReader` 안의 덮는 자리 `onClickNoCell`, 순서표대로(8차에 이름·목록 고침).
5. **【권고로 정함】 상태 소유**: 선언 `ReaderApp` / 판단 기억 어댑터 인스턴스 / 같이 쓰는 기억 WatchCore / 오래 남는 값 prefs / 바깥 데이터 캐시 프로세스 / 홈 신호 `HomeSignals`(소유 표).
6. **【권고로 정함】 홈 열기**: `OpenResult` 넷, 실패·다시 찾기는 지금 `HomeActivity` 흐름 그대로.
7. **【권고로 정함】 예약·뒤 작업**: 예약과 진행률 재시도 횟수는 종류마다 WatchCore 전체에 하나(새로 걸면 어느 앱 것이든 바꿈, 3차 B3 로 고침), 그 밖의 상한은 어댑터, 뒤 작업은 메인 스레드 완료·서비스 끝나도 적용(지금과 같게).
8. **【권고로 정함】 `ScreenReader` 설정 범위**: `openOnViewer`·`eagerCovers`·`secure`·`coverOverlays`·`cellsByPosition`·`clickByPlace`·`shelfEditing`·`tellsTitle` 은 바탕 설정, `recent` 는 알라딘 어댑터로.
9. **【권고로 정함】 과도기**: 3단계 동안 특수 앱 처리는 `Legacy` 에(`ScreenReader` 의 훅이 맡김), 같이 쓰는 `unknownOpen`·`viewerAt` 은 WatchCore 에.
10. **【권고로 정함】 실기기 확인 대기 앱**: 단계는 넘어가되 그 앱을 옮긴 판은 실기기 확인 전 릴리스하지 않는다.
11. **【권고로 정함】 바탕·전용 어댑터 관계**: 상속, 덮는 훅의 `super` 와의 순서는 지금 코드 그대로(대부분 `super` 먼저), 다른 것은 표에 적은 것만(2차 A1, 8차에 "앞에 두는 훅"을 표에 더함).
12. **【권고로 정함】 기억 비우기 한 길**: 편 책 기록은 `ctx.opened`·`ctx.unknown` 으로만, `opened` 는 비우기(WatchCore·`beforeOpened`) → 기록 → 돌아오면 `afterOpened`(2차 A1, 4차 B2 로 고침).
13. **【권고로 정함】 마지막 재확인 예약**: WatchCore 전체에 하나, 간격 안 알림마다 다시 걸고, 서비스 끝에 지운다(2차 G1, 3차 B3·G1 로 고침).
14. **【권고로 정함】 열기 실패**: 앱이 `onFail` 로 정한다 — 실행 화면 있는 앱 `LAUNCH`, 네오리더 `FIND_FILE`(2차 B2).
15. **【권고로 정함】 `onScreen` 은 덮지 않고 앱마다 다른 것은 `onEnterViewer` 로**: 바탕 누른 칸 / 상세 앱 상세 → 누른 칸 → (BookFusion) 책 모름 / Readest 기록 안 함 / 이지뷰어 파일 기억 비우기·누른 파일 / 우주·네오리더 묻기 횟수, 네오리더 탭 / 알라딘 읽으러 간 때(3차 B1, 8차에 기억 비우기를 더함).
16. **【권고로 정함】 이지뷰어 `onShelf` 는 대신**: 칸 읽기 조각만 재사용, 파일 칸은 파일로 책·파일 열쇠로 표지(3차 B2).
17. **【권고로 정함】 예약은 전체에 하나**: 읽는 화면 잠잠·서재 멈춤·마지막 재확인 모두 지금처럼 종류마다 하나, 진행률 재시도 횟수도 하나(3차 B3).
18. **【권고로 정함】 재확인 대상은 선언으로**: `ReaderApp.screenInsideActivity`(Libby·Readest), `screenName` 의 null 은 "지금 모름"(3차 G1).
19. **【권고로 정함】 네오리더는 파일을 모르면 제목 기록 안 함**: 우주(`recordShown` 으로 넘어감)와 나누고, 진행률만 편 책에(4차 B1).
20. **【권고로 정함】 비우기는 기록 전, 이어 두기는 지금 자리에**: `beforeOpened`(누른 칸·상세·책 모름·열쇠 모름) / 기록 도중 `steps.found`(이지뷰어 제목·파일)·`steps.logged`(네오리더 탭) / 돌아온 뒤 `afterOpened`(Readest 열쇠), 기록 예외면 그 뒤 단계 안 함(4차 B2, 6차 B1 로 고침).
21. **【권고로 정함】 자르기 막기는 어댑터 선언 한 곳**: `capturesCovers(ctx)`(기본 true, Readest 데이터를 읽을 수 있으면 false), `ctx.capture` 맨 앞에서 본다(4차 G1).
22. **【권고로 정함】 알라딘 `onViewer` 는 대신**: 메뉴 첫 확인을 바탕 기록보다 먼저 정해 `recordShown(force = 처음)`, `readKnown` 은 알라딘 어댑터 기억(5차 B1).
23. **【권고로 정함】 데이터로 안 책의 편 때는 지금 규칙 그대로**: 책 모름이 된 때 → 메뉴 제목 없이 알았을 때만 읽는 화면이 열린 때 → 지금. 우주·네오리더 둘 다(5차 B2).
24. **【권고로 정함】 `opened` 안에 앱이 끼어드는 자리 둘**: `OpenSteps.found`(찾기·넣기 뒤·편 책 기록 전)·`logged`(편 책 기록 뒤·표지 전)(6차 B1).
25. **【권고로 정함】 성공의 뜻**: `opened` 가 예외 없이 돌아옴 = 단계를 다 지남(편 책 저장 실패는 지금처럼 기록기 안에서 삼킴), 기록 뒤 일에 쓰는 책은 지금 코드 그대로(표, 6차 G1, 8차에 표로 고침).
26. **【권고로 정함】 네오리더의 책 모름 시각 유지는 선언으로**: `ReaderApp.keepsUnknownOnReenter`(네오리더만 true), 공통 전이가 보고 지우지 않음(7차 B1).
27. **【권고로 정함】 단계별 실패는 지금 그대로**: ①찾기·넣기·`found`·`logged` 는 밖으로(그 뒤 단계 안 함), 편 책 기록·표지 저장은 기록기 안에서 삼킴, 실패 전에 끝난 저장은 되돌리지 않음(7차 B2·B3).
28. **【권고로 정함】 앱을 더할 때 다른 앱 파일은 바뀌지 않는다**: 새 앱 파일·시험·등록 자리만. 공통 코드에는 기본값이 아무것도 안 하는 자리를 더하기만 하고, 이미 있는 훅의 때·순서·기록 규칙은 앱 추가로 바꾸지 않는다(목표 4, `앱을 더하는 법`).
29. **【권고로 정함】 두 앱이 같은 규칙이면 도우미**: `DetailMemory`(BookFusion·밀리 e-ink)·`DataAsk`(우주·네오리더). 도우미를 바꾸면 쓰는 앱을 모두 시험하고, 새 앱이 다른 규칙이 필요하면 도우미를 고치지 않고 그 앱 파일에 둔다.
30. **【권고로 정함】 앱 추가 규칙은 시험으로 지킨다**: 4단계부터 단계마다 바뀐 파일 확인, 5단계에서 "공통 코드에 앱 이름 없음" 단위 테스트, 옮긴 뒤 모든 앱 추가에 `앱 추가 확인`.
31. **【권고로 정함】 연결·끝 표시와 내용 무시는 선언으로**: `onAttach`·`onDetach` 훅 없이 WatchCore 가 `viewerTime` 대로, KOReader 의 내용 바뀜 무시는 `readsContent = false`(8차).
