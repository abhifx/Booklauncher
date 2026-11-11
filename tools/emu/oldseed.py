#!/usr/bin/env python3
"""옛 판 자료를 빠짐없이 채운 것(마이그레이션 시험, docs/emulator-test-cases.md A7~A9).

    python3 tools/emu/oldseed.py json OUT_DIR   # 1.2.1 까지: files/reading.json, files/books/books.json·표지, shared_prefs/reader_watch.xml
    python3 tools/emu/oldseed.py v1   OUT_DIR   # 1.3.0~1.4.2: databases/pebble.db (버전 1) + 표지
    python3 tools/emu/oldseed.py v2   OUT_DIR   # 1.5.0~1.5.3: databases/pebble.db (버전 2, review) + 표지
    python3 tools/emu/oldseed.py check KIND DB  # 이 브랜치가 옮긴 DB(버전 9)를 기대와 대조

OUT_DIR 아래를 앱 데이터 폴더(/data/user_de/0/com.woody.booklauncher/)에 그대로 덮는다(루트 에뮬, tools/emu/oldmig.sh).
날짜는 2026-10-16 을 오늘로 고정한다(에뮬 날짜가 같아야 기대가 맞는다).
"""
import datetime as dt
import json
import os
import sqlite3
import sys

TODAY = dt.date(2026, 10, 16)
KINDLE = "com.amazon.kindle"
KYOBO = "kr.co.kyobo.elib"
# 시험 에뮬에 깔린 앱(문리더 유료판). 깔리지 않은 앱 키는 지워진 앱 정리(pruneApps)로 비워진다.
MOON = "com.flyersoft.moonreaderp/.ActivityMain"
MIN = 60_000
# 1×1 흰 JPEG
JPEG = bytes.fromhex(
    "ffd8ffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c"
    "20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffc0000b080001000101011100ffc4001f00000105010101010101000000"
    "00000000000102030405060708090a0bffc400b5100002010303020403050504040000017d01020300041105122131410613516107227114328191a1"
    "082342b1c11552d1f02433627282090a161718191a25262728292a3435363738393a434445464748494a535455565758595a636465666768696a7374"
    "75767778797a838485868788898a92939495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1"
    "e2e3e4e5e6e7e8e9eaf1f2f3f4f5f6f7f8f9faffda0008010100003f00fbfcffd9"
)


def ep(d):
    return (d - dt.date(1970, 1, 1)).days


def ms(d, h=12):
    return int(dt.datetime.combine(d, dt.time(h)).timestamp() * 1000)


def ago(n):
    return TODAY - dt.timedelta(days=n)


D = dt.date
# 읽고 있는 책: (id, 제목, 저자, 앱, 진행률, 반납일(epoch day, -1), 앱이 보여 준 제목)
SHELF = [
    ("b-s1", "데미안", "헤르만 헤세", MOON, 30, ep(TODAY) + 9, ""),
    ("b-s2", "어린 왕자", "생텍쥐페리", "", -1, -1, ""),           # 완독한 적 있음(다시 읽는 중)
    ("b-s3", "헤세의 소설", "", "", 12, -1, "싯다르타"),           # 제목을 고친 책
    ("b-s4", "a/b", "", "", -1, -1, ""),                           # 제목에 /
    ("b-s5", "인간 실격", "다자이 오사무", "", -1, -1, ""),         # 지운 책과 같은 제목
    ("b-dup", "양쪽 책", "", "", -1, -1, ""),                        # 옛 판이 끊겨 나간 책에도 남은 책(빼기·다시 읽기 도중)
]
# 나간 책: (id, 제목, 저자, 진행률, 앱이 보여 준 제목, 나간 날, 지움)
PAST = [
    ("b-p1", "데미안", "헤르만 헤세", 100, "", D(2026, 1, 20), 0),   # 완독
    ("b-p2", "모모", "미하엘 엔데", 40, "", D(2026, 9, 1), 0),        # 완독하지 않고 뺌(감상 있음)
    ("b-p3", "인간 실격", "", -1, "", D(2026, 9, 10), 1),            # 지움
    ("b-p4", "두 번", "", 100, "", D(2026, 5, 2), 0),                 # 두 번 완독
    ("b-dup", "양쪽 책", "", -1, "", D(2026, 10, 10), 0),             # 읽고 있는 책에도 있음
]
FINISH = [("b-p1", D(2026, 1, 20)), ("b-s2", D(2026, 7, 18)), ("b-p4", D(2026, 3, 1)), ("b-p4", D(2026, 5, 1))]
# 진행 중이던(v2: 책마다 하나) 별점·감상
REVIEW = [("b-p1", 8, "처음 읽은 데미안"), ("b-p2", 6, "모모의 시간"), ("b-s2", 4, "다시 읽는 중"), ("b-p4", 10, "두 번째가 더 좋다")]
# 읽은 시간: (날, 칸 키, 분) — 칸 키는 `패키지/제목` 또는 앱만
DAYS = [
    (D(2026, 1, 5), f"{KINDLE}/데미안", 60),      # 나간 날(1/20) 전 → b-p1
    (ago(3), f"{KINDLE}/데미안", 30),             # → 읽고 있는 b-s1
    (ago(2), f"{KINDLE}/싯다르타", 20),           # 앱이 보여 준 제목 → b-s3
    (ago(2), f"{KINDLE}/a/b", 10),                # 첫 / 에서 나눔 → 제목 a/b → b-s4
    (D(2026, 8, 1), f"{KINDLE}/인간 실격", 30),   # 지운 날(9/10) 전 → b-p3
    (D(2026, 10, 1), f"{KINDLE}/인간 실격", 15),  # 지운 날 뒤 → b-s5
    (D(2026, 8, 20), f"{KINDLE}/모모", 25),       # 뺀 날(9/1) 전 → b-p2
    (D(2026, 7, 10), f"{KINDLE}/어린 왕자", 40),  # → b-s2
    (D(2026, 2, 20), f"{KINDLE}/두 번", 30),      # → b-p4
    (ago(1), f"{KINDLE}/없는 책", 12),            # 목록에 없던 책 → 빈 id
    (ago(1), KYOBO, 5),                           # 앱만 아는 시간
    (ago(1), KYOBO + "/", 7),                     # 빈 제목 칸(앱만 아는 칸과 같은 새 칸 → 더함)
]
META = [("through", ago(1).isoformat()), ("since", D(2025, 11, 1).isoformat())]
OPEN = [(ms(ago(1), 9), KINDLE, "데미안"), (ms(ago(1), 10), KYOBO, "")]


def v1_schema(x):
    x("CREATE TABLE reading_day (day INTEGER NOT NULL, key TEXT NOT NULL, ms INTEGER NOT NULL, PRIMARY KEY (day, key))")
    x("CREATE TABLE reading_meta (name TEXT PRIMARY KEY, value TEXT)")
    x("CREATE TABLE book_open (at INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL)")
    x("CREATE TABLE shelf (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, due INTEGER, seen_title TEXT)")
    x("CREATE TABLE past (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, seen_title TEXT, left_at INTEGER, hidden INTEGER)")
    x("CREATE TABLE finish (book_id TEXT NOT NULL, day INTEGER NOT NULL, PRIMARY KEY (book_id, day))")


def covers(out, ids):
    d = os.path.join(out, "files", "books")
    os.makedirs(d, exist_ok=True)
    for i in ids:
        open(os.path.join(d, f"{i}.jpg"), "wb").write(JPEG)


def make_db(out, version):
    os.makedirs(os.path.join(out, "databases"), exist_ok=True)
    path = os.path.join(out, "databases", "pebble.db")
    if os.path.exists(path):
        os.remove(path)
    db = sqlite3.connect(path)
    x = db.execute
    v1_schema(x)
    if version >= 2:
        x("CREATE TABLE review (book_id TEXT PRIMARY KEY, stars INTEGER NOT NULL, text TEXT NOT NULL, updated_at INTEGER NOT NULL)")
    for pos, (i, t, a, app, p, due, seen) in enumerate(SHELF):
        x("INSERT INTO shelf VALUES (?,?,?,?,?,?,?,?,?)", (pos, i, t, a, app, ms(ago(pos)), p, due, seen))
    for pos, (i, t, a, p, seen, left, hidden) in enumerate(PAST):
        x("INSERT INTO past VALUES (?,?,?,?,?,?,?,?,?,?)", (pos, i, t, a, "", ms(left), p, seen, ms(left, 18), hidden))
    for i, d in FINISH:
        x("INSERT INTO finish VALUES (?,?)", (i, ep(d)))
    if version >= 2:
        for i, s, t in REVIEW:
            x("INSERT INTO review VALUES (?,?,?,?)", (i, s, t, ms(ago(5))))
    for d, k, m in DAYS:
        x("INSERT INTO reading_day VALUES (?,?,?)", (ep(d), k, m * MIN))
    for n, v in META:
        x("INSERT INTO reading_meta VALUES (?,?)", (n, v))
    for o in OPEN:
        x("INSERT INTO book_open VALUES (?,?,?)", o)
    x(f"PRAGMA user_version={version}")
    db.commit()
    db.close()
    covers(out, [s[0] for s in SHELF] + [p[0] for p in PAST if not p[6]])


# 1.2.1 까지: 읽고 있는 책만(나간 책·완독 없음), id 는 YES24 상품 번호·`auto-<정리한 제목>`·`legacy`
JSON_BOOKS = [
    {"id": "89374604", "title": "데미안", "author": "헤르만 헤세", "app": MOON, "updated_at": ms(ago(1)), "progress": 30, "due": ep(TODAY) + 9},
    {"id": "auto-싯다르타", "title": "헤세의 소설", "author": "", "app": "", "updated_at": ms(ago(2)), "progress": -1, "due": -1},
    {"id": "legacy", "title": "모모", "author": "미하엘 엔데", "app": KYOBO + "/y", "updated_at": ms(ago(3)), "progress": 55, "due": -1},
]
JSON_DAYS = {ago(1).isoformat(): {KINDLE: 20 * MIN, KYOBO: 5 * MIN}, ago(30).isoformat(): {KINDLE: 40 * MIN}}


def make_json(out):
    f = os.path.join(out, "files")
    os.makedirs(os.path.join(f, "books"), exist_ok=True)
    json.dump({"through": ago(1).isoformat(), "since": ago(40).isoformat(), "days": JSON_DAYS},
              open(os.path.join(f, "reading.json"), "w"), ensure_ascii=False)
    json.dump(JSON_BOOKS, open(os.path.join(f, "books", "books.json"), "w"), ensure_ascii=False)
    covers(out, [b["id"] for b in JSON_BOOKS])
    p = os.path.join(out, "shared_prefs")
    os.makedirs(p, exist_ok=True)
    open(os.path.join(p, "reader_watch.xml"), "w").write(
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
        f'    <string name="opened_{KINDLE}">데미안</string>\n</map>\n')


# ---- 옮긴 뒤 대조 ----
fails = []


def expect(name, got, want):
    if got != want:
        fails.append(f"{name}: {got!r} != {want!r}")


def check(kind, path):
    db = sqlite3.connect(path)
    q = lambda s, *a: db.execute(s, a).fetchall()
    expect("버전", q("PRAGMA user_version")[0][0], 9)
    cols = lambda t: [r[1] for r in q(f"PRAGMA table_info({t})")]
    # 버전 5: 읽고 있는 책의 읽는 화(옛 판에서 온 책은 모두 빈 값)
    # 칸이 없으면(올리기가 안 된 DB) 대조가 멈추지 않고 어긋남으로 적히게 센다
    filled = q("SELECT count(*) FROM shelf WHERE chapter != ''")[0][0] if "chapter" in cols("shelf") else None
    expect("읽는 화 칸 shelf.chapter(빈 채)", (cols("shelf")[-2:-1], filled), (["chapter"], 0))
    expect("reading_day 칸", cols("reading_day"), ["day", "pkg", "title", "book_id", "ms"])
    expect("book_open 칸", cols("book_open"), ["at", "pkg", "title", "book_id"])
    expect("finish 칸", cols("finish"), ["id", "uid", "book_id", "day", "at", "started", "stars", "text", "began"])
    # 버전 6: 고친 읽기 시작한 날(옛 판에서 온 완독·진행 중 회차는 모두 빈 값)
    began = q("SELECT count(*) FROM finish WHERE began IS NOT NULL")[0][0] if "began" in cols("finish") else None
    expect("고친 시작일 finish.began(빈 채)", began, 0)
    rb = cols("round_began")
    expect("고친 시작일 표 round_began(빈 채)", (rb, q("SELECT count(*) FROM round_began")[0][0] if rb else None), (["book_id", "day"], 0))
    expect("round_start 칸", cols("round_start"), ["book_id", "day"])
    expect("책 파일 표 book_file(빈 채)", (cols("book_file"), q("SELECT count(*) FROM book_file")[0][0]), (["book_id", "path"], 0))
    # 버전 7: 카테고리·책에 붙인 카테고리·지운 회차(옛 판에서 온 DB 에는 모두 없다)
    empty = lambda t: (cols(t), q(f"SELECT count(*) FROM {t}")[0][0] if cols(t) else None)
    expect("카테고리 표 category(빈 채)", empty("category"), (["id", "uid", "name", "pos", "excluded"], 0))
    expect("책 카테고리 표 book_category(빈 채)", empty("book_category"), (["book_id", "category_id"], 0))
    expect("지운 회차 표 round_drop(빈 채)", empty("round_drop"), (["id", "book_id", "after_day", "until_day"], 0))
    # 버전 8: 읽은 시간까지 지운 책(옛 판에서 온 나간 책·지운 책은 모두 0)
    dropped = q("SELECT count(*) FROM past WHERE time_dropped != 0")[0][0] if "time_dropped" in cols("past") else None
    expect("읽은 시간까지 지운 책 past.time_dropped(모두 0)", (cols("past")[-2:-1], dropped), (["time_dropped"], 0))
    # 버전 9: 홈에서 숨긴 책(옛 판에서 온 읽고 있는 책·나간 책은 모두 0, 홈에 보인다)
    for t in ("shelf", "past"):
        hid = q(f"SELECT count(*) FROM {t} WHERE home_hidden != 0")[0][0] if "home_hidden" in cols(t) else None
        expect(f"홈에서 숨긴 책 {t}.home_hidden(모두 0)", (cols(t)[-1:], hid), (["home_hidden"], 0))
    if kind == "json":
        shelf = q("SELECT pos, id, title, author, app, progress, due, seen_title FROM shelf ORDER BY pos")
        expect("읽고 있는 책 수", len(shelf), 3)
        expect("id 는 모두 b-", all(r[1].startswith("b-") for r in shelf), True)
        expect("책 내용", [(r[0], r[2], r[3], r[4], r[5], r[6]) for r in shelf],
               [(i, b["title"], b["author"], b["app"] if b["app"] in ("", MOON) else "", b["progress"], b["due"]) for i, b in enumerate(JSON_BOOKS)])
        expect("auto- 는 앱이 보여 준 제목으로", shelf[1][7], "싯다르타")
        rd = sorted(q("SELECT day, pkg, title, book_id, ms FROM reading_day"))
        want = sorted((ep(D.fromisoformat(d)), pkg, "", "", m) for d, apps in JSON_DAYS.items() for pkg, m in apps.items())
        expect("읽은 시간(앱별)", rd, want)
        expect("메타", dict(q("SELECT name, value FROM reading_meta")), {"through": ago(1).isoformat(), "since": ago(40).isoformat()})
        expect("나간 책·완독 없음", (q("SELECT count(*) FROM past")[0][0], q("SELECT count(*) FROM finish")[0][0]), (0, 0))
        return shelf
    ids = {r[0]: r[1] for r in q("SELECT title || '|' || pos, id FROM shelf")}
    shelf = q("SELECT id, title, author, app, progress, due, seen_title FROM shelf ORDER BY pos")
    expect("읽고 있는 책", [(r[0], r[1], r[2], r[3], r[4], r[5], r[6]) for r in shelf],
           [(i, t, a, app, p, due, seen) for i, t, a, app, p, due, seen in SHELF])
    past = q("SELECT id, title, author, progress, seen_title, left_at, hidden FROM past ORDER BY pos")
    expect("나간 책", past, [(i, t, a, p, seen, ms(left, 18), h) for i, t, a, p, seen, left, h in PAST])
    fin = q("SELECT book_id, day, started, stars, text FROM finish ORDER BY book_id, day")
    rev = dict((i, (s, t)) for i, s, t in REVIEW) if kind == "v2" else {}
    want_fin = []
    for i, d in sorted(FINISH):
        last = max(dd for ii, dd in FINISH if ii == i)
        s, t = rev.get(i, (None, None)) if d == last else (None, None)
        want_fin.append((i, ep(d), None, s, t))
    expect("완독(마지막 완독에 감상)", fin, want_fin)
    expect("완독 uid 채움(누른 때 at 은 옛 완독이라 모름)", q("SELECT count(*) FROM finish WHERE uid IS NULL OR uid = '' OR at IS NOT NULL")[0][0], 0)
    expect("완독 uid 서로 다름", q("SELECT count(DISTINCT uid) FROM finish")[0][0], len(FINISH))
    want_rev = {"b-p2": (6, "모모의 시간")} if kind == "v2" else {}
    expect("남은 감상(완독한 적 없는 책만)", dict((i, (s, t)) for i, s, t in q("SELECT book_id, stars, text FROM review")), want_rev)
    expect("다시 읽는 중(완독한 적 있는 읽고 있는 책)", q("SELECT book_id, day FROM round_start"), [("b-s2", None)])
    expect("Libby 읽는 화면 표 viewer_mark(빈 채)", ([r[1] for r in q("PRAGMA table_info(viewer_mark)")], q("SELECT count(*) FROM viewer_mark")[0][0]), (["at", "pkg", "shown"], 0))
    # 칸 전체(날·앱·제목·책 id)를 키로 센다(책 id 만 다른 줄이 더 생겨도 덮이지 않게)
    rows = [r for r in q("SELECT day, pkg, title, book_id, ms FROM reading_day") if r[0] < ep(TODAY)]
    want_rd = {
        (ep(D(2026, 1, 5)), KINDLE, "데미안"): ("b-p1", 60 * MIN),
        (ep(ago(3)), KINDLE, "데미안"): ("b-s1", 30 * MIN),
        (ep(ago(2)), KINDLE, "싯다르타"): ("b-s3", 20 * MIN),
        (ep(ago(2)), KINDLE, "a/b"): ("b-s4", 10 * MIN),
        (ep(D(2026, 8, 1)), KINDLE, "인간 실격"): ("b-p3", 30 * MIN),
        (ep(D(2026, 10, 1)), KINDLE, "인간 실격"): ("b-s5", 15 * MIN),
        (ep(D(2026, 8, 20)), KINDLE, "모모"): ("b-p2", 25 * MIN),
        (ep(D(2026, 7, 10)), KINDLE, "어린 왕자"): ("b-s2", 40 * MIN),
        (ep(D(2026, 2, 20)), KINDLE, "두 번"): ("b-p4", 30 * MIN),
        (ep(ago(1)), KINDLE, "없는 책"): ("", 12 * MIN),
        (ep(ago(1)), KYOBO, ""): ("", 12 * MIN),
    }
    want_rows = sorted((d, p, t, b, m) for (d, p, t), (b, m) in want_rd.items())
    expect("읽은 시간(칸·책 id·ms 모두, 빈 제목 칸은 앱만 아는 칸에 더함)", sorted(rows), want_rows)
    expect("메타", dict(q("SELECT name, value FROM reading_meta")), dict(META))
    bo = q("SELECT at, pkg, title, book_id FROM book_open ORDER BY at, rowid")
    expect("편 책 기록(옛 줄 + 빈 책 id)", [r for r in bo if (r[0], r[1], r[2]) in OPEN], [(a, p, t, "") for a, p, t in OPEN])
    return shelf


if __name__ == "__main__":
    kind = sys.argv[1]
    if kind == "check":
        check(sys.argv[2], sys.argv[3])
        print("\n".join(fails) or "모두 맞음")
        sys.exit(1 if fails else 0)
    out = sys.argv[2]
    {"json": make_json, "v1": lambda o: make_db(o, 1), "v2": lambda o: make_db(o, 2)}[kind](out)
