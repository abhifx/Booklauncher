#!/usr/bin/env python3
"""에뮬레이터 시험용 DB 만들기(docs/emulator-test-cases.md 의 S·V2 준비).

    python3 tools/emu/seed.py main  out.db   # DB 버전 3, B·C·D·E·F·H 케이스용 책들
    python3 tools/emu/seed.py v2    out.db   # DB 버전 2(1.5.3), A 케이스(마이그레이션)
    python3 tools/emu/seed.py b2    out.db   # 오늘 3분만(B2)
    python3 tools/emu/seed.py week  out.db   # 이번 주 책들(B5·B6·B7·B9·B10)
    python3 tools/emu/seed.py streak out.db  # 5분 경계(L1-10)
    python3 tools/emu/seed.py gone  out.db   # 지운 책이 마지막 편 책(L2-4)
    python3 tools/emu/seed.py unknown out.db # 이번 주 책 모름만(B4)
    python3 tools/emu/seed.py empty out.db   # 이번 주 기록 없음(B11)
    python3 tools/emu/seed.py books12 out.db # 읽고 있는 책 12권(G3)
    python3 tools/emu/seed.py heavy out.db   # 성능 재기: 2년 기록·완독 150권·읽는 책 10권
    python3 tools/emu/seed.py check docs/emulator-test-cases.md  # 문서의 S 케이스가 모두 시드에 있는지

날짜는 실행하는 날(오늘) 기준이다. 넣는 법은 tools/emu/swapdb.sh.
오늘 줄은 에뮬에서 이북 앱을 열지 않은 동안만 그대로 보인다(오늘은 사용 기록으로 다시 센다).
"""
import datetime as dt
import os
import random
import sqlite3
import sys
import uuid

# 시드의 '오늘'. 에뮬에서 그날 이북 앱을 이미 열었으면 오늘 줄은 사용 기록으로 다시 세어 덮이므로, 에뮬 날짜를 하루 옮기고
# SEED_TODAY=2026-10-17 처럼 그날로 만든다(docs/emulator-test-cases.md 준비).
TODAY = dt.date.fromisoformat(os.environ["SEED_TODAY"]) if os.environ.get("SEED_TODAY") else dt.date.today()
PKG = "com.amazon.kindle"
MIN = 60_000


def ep(d: dt.date) -> int:
    return (d - dt.date(1970, 1, 1)).days


def ms(d: dt.date, h: int = 12) -> int:
    return int(dt.datetime.combine(d, dt.time(h)).timestamp() * 1000)


def ago(n: int) -> dt.date:
    return TODAY - dt.timedelta(days=n)


def this_year(m: int, d: int) -> dt.date:
    return dt.date(TODAY.year, m, d)


def v3_schema(db):
    x = db.execute
    x("CREATE TABLE reading_day (day INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', book_id TEXT NOT NULL DEFAULT '', "
      "ms INTEGER NOT NULL, PRIMARY KEY (day, pkg, title, book_id))")
    x("CREATE INDEX reading_day_book ON reading_day (book_id)")
    x("CREATE TABLE reading_meta (name TEXT PRIMARY KEY, value TEXT)")
    x("CREATE TABLE book_open (at INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL, book_id TEXT NOT NULL DEFAULT '')")
    x("CREATE TABLE shelf (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, due INTEGER, seen_title TEXT)")
    x("CREATE TABLE past (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, seen_title TEXT, left_at INTEGER, hidden INTEGER)")
    x("CREATE TABLE finish (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL UNIQUE, book_id TEXT NOT NULL, day INTEGER NOT NULL, at INTEGER, started INTEGER, stars INTEGER, text TEXT)")
    x("CREATE INDEX finish_book ON finish (book_id)")
    x("CREATE TABLE review (book_id TEXT PRIMARY KEY, stars INTEGER NOT NULL, text TEXT NOT NULL, updated_at INTEGER NOT NULL)")
    x("CREATE TABLE round_start (book_id TEXT PRIMARY KEY, day INTEGER)")
    # 버전 3에서 함께 더한 표(Db.createViewerMark). 빠지면 버전이 같아 앱이 만들지 않고 홈이 `no such table` 로 멈춘다
    x("CREATE TABLE viewer_mark (at INTEGER NOT NULL, pkg TEXT NOT NULL, shown INTEGER NOT NULL)")
    db.execute("PRAGMA user_version=3")


class V3:
    def __init__(self, db):
        self.db = db
        self.shelf_pos = 0
        self.past_pos = 0

    def shelf(self, i, title, author="", progress=-1, seen=""):
        self.db.execute("INSERT INTO shelf VALUES (?,?,?,?,NULL,?,?,-1,?)", (self.shelf_pos, i, title, author, ms(TODAY), progress, seen))
        self.shelf_pos += 1

    def past(self, i, title, left: dt.date, author="", hidden=False, seen=""):
        self.db.execute("INSERT INTO past VALUES (?,?,?,?,NULL,?,-1,?,?,?)", (self.past_pos, i, title, author, ms(left), seen, ms(left, 18), 1 if hidden else 0))
        self.past_pos += 1

    def read(self, d: dt.date, title, minutes, book="", seconds=0):
        self.db.execute("INSERT OR REPLACE INTO reading_day VALUES (?,?,?,?,?)", (ep(d), PKG, title, book, minutes * MIN + seconds * 1000))

    def read_span(self, a: dt.date, b: dt.date, title, minutes, book, step=2):
        d = a
        while d <= b:
            self.read(d, title, minutes, book)
            d += dt.timedelta(days=step)

    def finish(self, i, d: dt.date, stars=None, text=None, started=None, hour=20):
        self.db.execute("INSERT INTO finish (uid, book_id, day, at, started, stars, text) VALUES (?,?,?,?,?,?,?)",
                        (uuid.uuid4().hex, i, ep(d), ms(d, hour), ep(started) if started else None, stars, text))

    def review(self, i, stars, text):
        self.db.execute("INSERT INTO review VALUES (?,?,?,0)", (i, stars, text))

    def round_start(self, i, d):
        self.db.execute("INSERT INTO round_start VALUES (?,?)", (i, ep(d) if d else None))

    def opened(self, at, title, book, pkg=PKG):
        self.db.execute("INSERT INTO book_open VALUES (?,?,?,?)", (at, pkg, title, book))

    def meta(self, since: dt.date):
        self.db.execute("INSERT INTO reading_meta VALUES ('through', ?)", (ago(1).isoformat(),))
        self.db.execute("INSERT INTO reading_meta VALUES ('since', ?)", (since.isoformat(),))


def main(db):
    v3_schema(db)
    s = V3(db)
    s.meta(ago(400))  # B8·L1-11: 기록 시작 400일 전(목표는 기본 30분)
    # C1·D1·D2: 읽고 있는 『데미안』(이번 달), 같은 제목 나간 『데미안』(1월 완독)
    s.shelf("b-demian", "데미안", "헤르만 헤세", 30)
    s.read(ago(4), "데미안", 30, "b-demian"); s.read(ago(3), "데미안", 25, "b-demian")
    s.past("p-demian", "데미안", this_year(1, 20), "헤르만 헤세")
    s.read(this_year(1, 5), "데미안", 60, "p-demian"); s.read(this_year(1, 15), "데미안", 40, "p-demian")
    s.finish("p-demian", this_year(1, 20), 8, "처음 읽은 데미안")
    # D4·E7·D12: 4분만 읽은 읽고 있는 책
    s.shelf("b-wheel", "수레바퀴 아래", "헤르만 헤세")
    s.read(ago(1), "수레바퀴 아래", 4, "b-wheel")
    # D9: 막 넣은 책
    s.shelf("b-new", "막 넣은 책")
    # D3·D10·C6·C7·C9: 여러 해(3년 전부터) 완독 3회 + 진행 중 4회차(별점 없음)
    s.shelf("b-reread", "싯다르타", "헤르만 헤세", 12)
    s.read_span(dt.date(TODAY.year - 3, 9, 1), dt.date(TODAY.year - 3, 9, 10), "싯다르타", 30, "b-reread")
    s.finish("b-reread", dt.date(TODAY.year - 3, 9, 11), 8, "첫 만남")
    s.read_span(dt.date(TODAY.year - 2, 3, 1), dt.date(TODAY.year - 2, 3, 20), "싯다르타", 30, "b-reread")
    s.finish("b-reread", dt.date(TODAY.year - 2, 3, 21), 9, "처음 읽고 놀랐다")
    s.read_span(dt.date(TODAY.year - 1, 6, 1), dt.date(TODAY.year - 1, 6, 15), "싯다르타", 25, "b-reread")
    s.finish("b-reread", dt.date(TODAY.year - 1, 6, 16), 7, None, started=dt.date(TODAY.year - 1, 5, 30))
    s.round_start("b-reread", ago(5))
    s.read(ago(2), "싯다르타", 20, "b-reread")
    # C8: 4분 읽고 감상을 남긴 나간 책
    s.past("p-short", "짧게 읽은 책", ago(30))
    s.read(ago(31), "짧게 읽은 책", 4, "p-short")
    s.review("p-short", 6, "짧았지만 좋았다")
    # C5: 지운 책
    s.past("p-gone", "인간 실격", ago(20), hidden=True)
    s.read(ago(25), "인간 실격", 30, "p-gone")
    # D11: 완독 1회 뒤 다시 읽다 뺀 책(진행 중, 나간 책)
    s.past("p-stopped", "어린 왕자", ago(10))
    s.read(ago(60), "어린 왕자", 40, "p-stopped")
    s.finish("p-stopped", ago(58), 10, "다시 읽고 싶다")
    s.round_start("p-stopped", ago(15))
    s.read(ago(14), "어린 왕자", 12, "p-stopped")
    # L4-12: 아주 긴 감상(이모지·줄바꿈)
    long = ("밤에 다시 읽었다. 🌙\n" * 20 + "끝없는 문장. " * 120)[:2000]
    s.past("p-long", "긴 감상 책", ago(40))
    s.read(ago(45), "긴 감상 책", 50, "p-long")
    s.finish("p-long", ago(41), 9, long)
    # D8·D8b·L4-14: 완독 7회(감상 길이 제각각)
    s.past("p-many", "회차 많은 책", ago(3))
    for k in range(7):
        a = this_year(1, 1) + dt.timedelta(days=k * 35)
        s.read(a, "회차 많은 책", 20 + k, "p-many")
        s.finish("p-many", a + dt.timedelta(days=3), (k % 5) * 2 + 1, ("감상 " * (k * 15)).strip() or None, hour=21)
    # 같은 날 두 번 완독(E3 확인용)
    s.past("p-twice", "두 번 완독", ago(7))
    s.read(ago(9), "두 번 완독", 30, "p-twice")
    s.finish("p-twice", ago(8), 4, "첫 번째", hour=10)
    s.finish("p-twice", ago(8), 8, "두 번째", hour=22)
    # C3: 4분만 읽은 나간 책, 감상 없음
    s.past("p-four", "4분 책", ago(30))
    s.read(ago(31), "4분 책", 4, "p-four")
    # C4: A 는 60일 전 10분 읽고 그제 완독, B 는 열흘 전 10분 읽고 뺌
    s.past("p-c4a", "C4 앞 책", ago(2))
    s.read(ago(60), "C4 앞 책", 10, "p-c4a")
    s.finish("p-c4a", ago(2))
    s.past("p-c4b", "C4 뒤 책", ago(9))
    s.read(ago(10), "C4 뒤 책", 10, "p-c4b")
    # F1~F4: 스무날 전 완독한 책(그 회차 30분) 뒤에
    for i, title in [("p-f1", "F1 3분"), ("p-f2", "F2 6분"), ("p-f4", "F4 감상만")]:
        s.past(i, title, ago(20))
    s.shelf("b-f3", "F3 다시 읽기")
    for i, title in [("p-f1", "F1 3분"), ("p-f2", "F2 6분"), ("b-f3", "F3 다시 읽기"), ("p-f4", "F4 감상만")]:
        s.read(ago(25), title, 30, i)
        s.finish(i, ago(20))
    s.read(ago(10), "F1 3분", 3, "p-f1")                                 # F1: 3분
    s.read(ago(10), "F2 6분", 3, "p-f2"); s.read(ago(9), "F2 6분", 3, "p-f2")  # F2: 합쳐 6분
    s.read(ago(10), "F3 다시 읽기", 2, "b-f3")                           # F3: 2분 뒤 다음 날 다시 읽기
    s.round_start("b-f3", ago(9))
    s.read(ago(8), "F3 다시 읽기", 10, "b-f3")
    s.review("p-f4", 8, "완독 뒤 남긴 감상")                             # F4: 감상만
    # L1-12: 4분 읽고 완독한 회차, 40초만 읽고 완독한 회차
    s.past("p-short2", "짧은 회차", ago(39))
    s.read(ago(50), "짧은 회차", 4, "p-short2")
    s.finish("p-short2", ago(49))
    s.read(ago(40), "짧은 회차", 0, "p-short2", seconds=40)
    s.finish("p-short2", ago(39))
    # L2-8: 제목에 / 가 든 책
    s.past("p-slash", "a/b", ago(19))
    s.read(ago(20), "a/b", 10, "p-slash")
    # F5: 기록 켜기 전 완독(읽은 시간 없음)
    s.past("p-old", "기록 전 완독", this_year(2, 1))
    s.finish("p-old", this_year(2, 1), None, None)
    # B1·B3: 오늘 책 20분 + 책 모름 22분 = 42분
    s.shelf("b-today", "오늘 책")
    s.read(TODAY, "오늘 책", 20, "b-today")
    s.read(TODAY, "", 22)
    # C2: 목록에 없던 책·책 모름 시간·1분 경계(이번 주 시험은 week 시드)
    s.read(ago(1), "모모", 12)
    s.read(ago(1), "", 22)
    s.read(ago(1), "1분 책", 1)
    s.read(ago(1), "59초 책", 0, seconds=59)
    s.read(ago(1), "책 여섯", 6); s.read(ago(1), "책 일곱", 7)
    # 편 책 기록. L2-3: 열흘 넘은 줄(킨들)과 다른 앱의 마지막 줄(교보, 열흘 넘었어도 남음)
    s.opened(ms(ago(15), 9), "모모", "")
    s.opened(ms(ago(14), 9), "교보 책", "", pkg="kr.co.kyobo.elib")
    s.opened(ms(ago(3), 9), "데미안", "b-demian")


def b2(db):
    """B2: 오늘 3분만, 어제·그제 10분"""
    v3_schema(db)
    s = V3(db)
    s.meta(ago(30))
    s.shelf("b-demian", "데미안", "헤르만 헤세")
    for d, m in [(TODAY, 3), (ago(1), 10), (ago(2), 10)]:
        s.read(d, "데미안", m, "b-demian")


def week(db):
    """B5·B6·B7·B9·B10: 이번 주(오늘) 기록만. 오늘에 넣어 무슨 요일·주 시작이든 이번 주다."""
    v3_schema(db)
    s = V3(db)
    s.meta(ago(30))
    s.shelf("b-demian", "데미안", "헤르만 헤세")
    s.past("p-demian", "데미안", ago(20), "헤르만 헤세")
    s.read(TODAY, "데미안", 10, "b-demian")
    s.read(TODAY, "데미안", 8, "p-demian")
    for k in range(5):
        s.shelf(f"b-w{k}", f"이번 주 책 {k + 1}")
        s.read(TODAY, f"이번 주 책 {k + 1}", 5 + k, f"b-w{k}")
    s.read(TODAY, "모모", 12)
    # B6: 제목을 고친 책(읽은 시간은 그 책 id, 편 책 제목은 고치기 전 제목)
    s.shelf("b-edit", "헤세의 소설", seen="고치기 전 제목")
    s.read(TODAY, "고치기 전 제목", 7, "b-edit")
    # B10: 이번 주 5분 경계
    s.read(TODAY, "5분 책", 5)
    s.read(TODAY, "4분 59초 책", 4, seconds=59)


def streak(db):
    """L1-10: 정확히 5분 읽은 날과 4분 59초 읽은 날"""
    v3_schema(db)
    s = V3(db)
    s.meta(ago(30))
    s.shelf("b-demian", "데미안")
    s.read(TODAY, "데미안", 10, "b-demian")
    s.read(ago(1), "데미안", 5, "b-demian")
    s.read(ago(2), "데미안", 4, "b-demian", seconds=59)
    s.read(ago(3), "데미안", 10, "b-demian")


def gone(db):
    """L2-4: 지운 책이 킨들의 마지막 편 책 줄"""
    v3_schema(db)
    s = V3(db)
    s.meta(ago(30))
    s.past("p-gone", "인간 실격", ago(5), hidden=True)
    s.read(ago(6), "인간 실격", 30, "p-gone")
    s.opened(ms(ago(6), 9), "인간 실격", "p-gone")


def unknown(db):
    """B4: 이번 주 시간이 모두 책 모름"""
    v3_schema(db)
    s = V3(db)
    s.meta(ago(30))
    s.read(TODAY, "", 22)


def empty(db):
    """B11: 이번 주 기록 없음(지난 기록만)"""
    v3_schema(db)
    s = V3(db)
    s.meta(ago(30))
    s.shelf("b-demian", "데미안")
    s.read(ago(10), "데미안", 30, "b-demian")


def books12(db):
    """G3: 읽고 있는 책 12권"""
    v3_schema(db)
    s = V3(db)
    s.meta(ago(30))
    for k in range(12):
        s.shelf(f"b-{k:02d}", f"책 {k + 1}")
        s.read(ago(k % 5), f"책 {k + 1}", 10 + k, f"b-{k:02d}")


def heavy(db):
    """성능 재기: 2년 기록(앱 넷), 읽고 있는 책 10권, 완독 150권(그 가운데 22권은 두 번), 사흘마다 책 모르는 시간"""
    v3_schema(db)
    s = V3(db)
    s.meta(ago(730))
    rnd = random.Random(7)
    pkgs = ["kr.co.kyobobook.KEL", "kr.co.millie.eink", "com.initialcoms.ridi", PKG]
    # 완독한 책: 4일마다 한 권씩 끝내며 그 앞 12일 동안 읽었다(마지막 책은 4주 전)
    for k in range(150):
        end = ago(730 - 4 * k - 12)
        i, t = f"p-{k:03d}", f"끝낸 책 {k + 1}"
        s.past(i, t, end, author=f"지은이 {k % 40}")
        s.finish(i, end, stars=rnd.randint(4, 10), text="좋았다" if k % 3 == 0 else None, started=end - dt.timedelta(days=12))
        if k % 7 == 0:
            s.finish(i, end + dt.timedelta(days=40), started=end + dt.timedelta(days=30))
        pkg = pkgs[k % 4]
        for d in range(12):
            day = end - dt.timedelta(days=d)
            db.execute("INSERT OR REPLACE INTO reading_day VALUES (?,?,?,?,?)", (ep(day), pkg, t, i, rnd.randint(5, 60) * MIN))
    for k in range(10):
        i, t = f"b-{k:02d}", f"읽는 책 {k + 1}"
        s.shelf(i, t, author=f"지은이 {k}", progress=rnd.randint(1, 95))
        for d in range(1, 40, 1 + k % 3):
            db.execute("INSERT OR REPLACE INTO reading_day VALUES (?,?,?,?,?)", (ep(ago(d)), pkgs[k % 4], t, i, rnd.randint(5, 40) * MIN))
        s.opened(ms(ago(k + 1)), t, i, pkgs[k % 4])
    for d in range(1, 730, 3):
        db.execute("INSERT OR REPLACE INTO reading_day VALUES (?,?,?,?,?)", (ep(ago(d)), PKG, "", "", rnd.randint(1, 20) * MIN))


def v2(db):
    x = db.execute
    x("CREATE TABLE reading_day (day INTEGER NOT NULL, key TEXT NOT NULL, ms INTEGER NOT NULL, PRIMARY KEY (day, key))")
    x("CREATE TABLE reading_meta (name TEXT PRIMARY KEY, value TEXT)")
    x("CREATE TABLE book_open (at INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL)")
    x("CREATE TABLE shelf (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, due INTEGER, seen_title TEXT)")
    x("CREATE TABLE past (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, seen_title TEXT, left_at INTEGER, hidden INTEGER)")
    x("CREATE TABLE finish (book_id TEXT NOT NULL, day INTEGER NOT NULL, PRIMARY KEY (book_id, day))")
    x("CREATE TABLE review (book_id TEXT PRIMARY KEY, stars INTEGER NOT NULL, text TEXT NOT NULL, updated_at INTEGER NOT NULL)")
    # A1·A2: 나간 A(1/20 완독, 감상), 읽고 있는 같은 제목 B
    x("INSERT INTO shelf VALUES (0,'b-new','데미안','헤르만 헤세',NULL,?,30,-1,'')", (ms(TODAY),))
    x("INSERT INTO past VALUES (0,'b-old','데미안','헤르만 헤세',NULL,?,100,'',?,0)", (ms(this_year(1, 20)), ms(this_year(1, 20))))
    x("INSERT INTO finish VALUES ('b-old',?)", (ep(this_year(1, 20)),))
    x("INSERT INTO review VALUES ('b-old',8,'처음 읽은 데미안',0)")
    # A3: 다시 읽는 중(읽고 있는 책인데 완독한 적 있음)
    x("INSERT INTO shelf VALUES (1,'b-reread','어린 왕자','',NULL,?,-1,-1,'')", (ms(TODAY),))
    x("INSERT INTO finish VALUES ('b-reread',?)", (ep(ago(90)),))
    # A4: 고친 제목(seen 싯다르타)
    x("INSERT INTO shelf VALUES (2,'b-edit','헤세의 소설','',NULL,?,-1,-1,'싯다르타')", (ms(TODAY),))
    # E7b: 읽은 시간 없이 감상만 있는 읽고 있는 책
    x("INSERT INTO shelf VALUES (4,'b-revonly','감상만 책','',NULL,?,-1,-1,'')", (ms(TODAY),))
    x("INSERT INTO review VALUES ('b-revonly',6,'예전 감상',0)")
    # L8-3: 지운 책과 같은 제목 읽고 있는 책
    x("INSERT INTO past VALUES (1,'b-gone','인간 실격','',NULL,0,-1,'',?,1)", (ms(ago(30)),))
    x("INSERT INTO shelf VALUES (3,'b-again','인간 실격','',NULL,?,-1,-1,'')", (ms(TODAY),))
    for d, key, m in [(this_year(1, 5), f"{PKG}/데미안", 60), (this_year(1, 15), f"{PKG}/데미안", 40), (ago(3), f"{PKG}/데미안", 30),
                      (ago(2), f"{PKG}/싯다르타", 20), (ago(1), f"{PKG}/모모", 12), (ago(1), "kr.co.kyobo.elib", 5),
                      (ago(40), f"{PKG}/인간 실격", 30), (ago(20), f"{PKG}/a/b", 10), (ago(10), f"{PKG}/인간 실격", 15), (ago(95), f"{PKG}/어린 왕자", 40)]:
        x("INSERT INTO reading_day VALUES (?,?,?)", (ep(d), key, m * MIN))
    x("INSERT INTO reading_meta VALUES ('through', ?), ('since', ?)", (ago(1).isoformat(), ago(400).isoformat()))
    x("INSERT INTO book_open VALUES (?,?,?)", (ms(ago(3), 9), PKG, "데미안"))
    db.execute("PRAGMA user_version=2")


SEEDS = {"main": main, "v2": v2, "b2": b2, "week": week, "streak": streak, "gone": gone, "unknown": unknown, "empty": empty, "books12": books12, "heavy": heavy}


# 시드가 만든 DB 에서 그 케이스의 핵심 상태(SQL 결과)
STATE = {
    "main": [
        ("D3·D10 완독 3회·진행 중", "SELECT count(*) FROM finish WHERE book_id='b-reread'", 3),
        ("D3·D10 진행 중 회차", "SELECT count(*) FROM round_start WHERE book_id='b-reread'", 1),
        ("D10 첫 완독 해", f"SELECT min(day) < {(dt.date(TODAY.year - 2, 1, 1) - dt.date(1970, 1, 1)).days} FROM finish WHERE book_id='b-reread'", 1),
        ("D8 완독 7회", "SELECT count(*) FROM finish WHERE book_id='p-many'", 7),
        ("E3 같은 날 두 번", "SELECT count(DISTINCT day) || '/' || count(*) FROM finish WHERE book_id='p-twice'", "1/2"),
        ("B1 오늘 42분", f"SELECT sum(ms) / 60000 FROM reading_day WHERE day={(TODAY - dt.date(1970, 1, 1)).days}", 42),
        ("D1 데미안 55분", "SELECT sum(ms) / 60000 FROM reading_day WHERE book_id='b-demian'", 55),
        ("F5 읽은 시간 없음", "SELECT count(*) FROM reading_day WHERE book_id='p-old'", 0),
        ("F2 완독 뒤 6분", "SELECT sum(ms) / 60000 FROM reading_day r JOIN finish f ON f.book_id=r.book_id WHERE r.book_id='p-f2' AND r.day > f.day", 6),
    ],
    "streak": [("L1-10 4분 59초", "SELECT count(*) FROM reading_day WHERE ms=299000", 1)],
    "week": [("B5 같은 제목 두 책", "SELECT count(DISTINCT book_id) FROM reading_day WHERE title='데미안'", 2)],
}


def check(doc):
    """문서 표에서 준비가 `S`·`S:이름`·`V2` 인 케이스 ID 가 그 시드 함수(주석·설명)에 적혀 있는지"""
    import inspect
    import re
    missing = []
    for line in open(doc, encoding="utf-8"):
        m = re.match(r"\| ([A-L]\d+[a-z]?(?:-\d+)?) \| (V2|S(?::(\w+))?)[:/ ]", line)
        if not m:
            continue
        cid, kind = m[1], "v2" if m[2] == "V2" else (m[3] or "main")
        src = inspect.getsource(SEEDS[kind])
        if not re.search(rf"(?<![\w-]){re.escape(cid)}(?![\w-])", src):
            missing.append(f"{cid} ({kind})")
    for kind, cases in STATE.items():
        con = sqlite3.connect(":memory:")
        SEEDS[kind](con)
        for name, sql, want in cases:
            got = con.execute(sql).fetchone()[0]
            if got != want:
                missing.append(f"{name} ({kind}): {got} != {want}")
        con.close()
    print("\n".join(missing) or "모두 있음")
    return 1 if missing else 0


if __name__ == "__main__":
    if sys.argv[1] == "check":
        sys.exit(check(sys.argv[2]))
    kind, out = sys.argv[1], sys.argv[2]
    if os.path.exists(out):
        os.remove(out)
    con = sqlite3.connect(out)
    SEEDS[kind](con)
    con.commit()
    con.close()
