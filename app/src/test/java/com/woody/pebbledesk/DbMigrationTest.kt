package com.woody.pebbledesk

import android.database.sqlite.SQLiteDatabase
import com.woody.pebbledesk.Db.rows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DB 마이그레이션([Db.upgrade])·새 설치([Db.create])의 SQL. 릴리스된 판의 표([OldSchemas])를 메모리에 만들어 올린 뒤 결과를 본다.
 * 실제 파일을 앱처럼 여는 경로(버전 전환·JSON 옮기기)는 [DbOpenTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DbMigrationTest {
    private fun v1(): SQLiteDatabase = SQLiteDatabase.create(null).apply { OldSchemas.v1(this) }
    private fun v2(): SQLiteDatabase = SQLiteDatabase.create(null).apply { OldSchemas.v2(this) }

    private fun SQLiteDatabase.columns(table: String) = rows("PRAGMA table_info($table)") { it.getString(1) }
    private fun SQLiteDatabase.count(sql: String) = rows(sql) { it.getLong(0) }.single()

    /** 표마다 칸 이름(안드로이드·SQLite 자체 표는 빼고) */
    private fun SQLiteDatabase.schema(): Map<String, List<String>> =
        rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name") { it.getString(0) }
            .associateWith { columns(it) }

    @Test fun `v1~v8 에서 올린 표가 새로 설치한 표와 같다(Libby 읽는 화면 viewer_mark, 네오리더 책 파일 book_file, 읽는 화 chapter, 고친 시작일 began, 카테고리·지운 회차 round_drop, 읽은 시간까지 지운 책 time_dropped, 홈에서 숨긴 책 home_hidden 포함)`() {
        val fresh = SQLiteDatabase.create(null).apply { Db.create(this) }.schema()
        assertEquals(listOf("at", "pkg", "shown"), fresh["viewer_mark"])
        assertEquals(fresh, v1().apply { Db.upgrade(this, 1) }.schema())
        assertEquals(fresh, v2().apply { Db.upgrade(this, 2) }.schema())
        assertEquals(fresh, SQLiteDatabase.create(null).apply { OldSchemas.v3(this); Db.upgrade(this, 3) }.schema())
        assertEquals(fresh, SQLiteDatabase.create(null).apply { OldSchemas.v4(this); Db.upgrade(this, 4) }.schema())
        assertEquals(fresh, SQLiteDatabase.create(null).apply { OldSchemas.v5(this); Db.upgrade(this, 5) }.schema())
        assertEquals(fresh, SQLiteDatabase.create(null).apply { OldSchemas.v6(this); Db.upgrade(this, 6) }.schema())
        assertEquals(fresh, SQLiteDatabase.create(null).apply { OldSchemas.v7(this); Db.upgrade(this, 7) }.schema())
        assertEquals(fresh, SQLiteDatabase.create(null).apply { OldSchemas.v8(this); Db.upgrade(this, 8) }.schema())
        assertEquals(listOf("time_dropped", "home_hidden"), fresh["past"]?.takeLast(2))
        assertEquals(listOf("chapter", "home_hidden"), fresh["shelf"]?.takeLast(2))
        assertEquals(listOf("id", "uid", "name", "pos", "excluded"), fresh["category"])
        assertEquals(listOf("book_id", "category_id"), fresh["book_category"])
        assertEquals(listOf("id", "book_id", "after_day", "until_day"), fresh["round_drop"])
        assertEquals("began", fresh["finish"]?.last())
        assertEquals(listOf("book_id", "day"), fresh["round_began"])
    }

    @Test fun `v7 에서 올리면 나간 책·지운 책은 그대로이고 읽은 시간은 지우지 않은 것으로`() {
        val db = SQLiteDatabase.create(null).apply {
            OldSchemas.v7(this)
            execSQL("INSERT INTO past VALUES (0, 'b-1', '데미안', '', '', 0, -1, '', 100, 1), (1, 'b-2', '수레바퀴 아래서', '', '', 0, -1, '', 200, 0)")
            Db.upgrade(this, 7)
        }
        assertEquals(listOf("b-1|1|0", "b-2|0|0"), db.rows("SELECT id, hidden, time_dropped FROM past ORDER BY pos") {
            "${it.getString(0)}|${it.getInt(1)}|${it.getInt(2)}"
        })
    }

    @Test fun `v8 에서 올리면 읽고 있는 책·나간 책은 그대로이고 모두 홈에 보인다`() {
        val db = SQLiteDatabase.create(null).apply {
            OldSchemas.v8(this)
            execSQL("INSERT INTO shelf VALUES (0, 'b-1', '데미안', '', NULL, 0, 10, -1, '', 'Ch. 3')")
            execSQL("INSERT INTO past VALUES (0, 'b-2', '수레바퀴 아래서', '', '', 0, -1, '', 200, 1, 1)")
            Db.upgrade(this, 8)
        }
        assertEquals(listOf("b-1|Ch. 3|0"), db.rows("SELECT id, chapter, home_hidden FROM shelf") { "${it.getString(0)}|${it.getString(1)}|${it.getInt(2)}" })
        assertEquals(listOf("b-2|1|1|0"), db.rows("SELECT id, hidden, time_dropped, home_hidden FROM past") {
            "${it.getString(0)}|${it.getInt(1)}|${it.getInt(2)}|${it.getInt(3)}"
        })
    }

    @Test fun `v5 에서 올리면 완독은 그대로이고 고친 시작일은 비어 있다`() {
        val db = SQLiteDatabase.create(null).apply {
            OldSchemas.v5(this)
            execSQL("INSERT INTO finish (uid, book_id, day, at, started, stars, text) VALUES ('u1', 'b-1', 100, 5, 90, 8, '좋았다')")
            Db.upgrade(this, 5)
        }
        assertEquals(listOf("u1|100|90|8|null"), db.rows("SELECT uid, day, started, stars, began FROM finish") {
            "${it.getString(0)}|${it.getLong(1)}|${it.getLong(2)}|${it.getInt(3)}|${if (it.isNull(4)) null else it.getLong(4)}"
        })
        assertEquals(0L, db.count("SELECT COUNT(*) FROM round_began"))
    }

    @Test fun `v4 에서 올리면 읽고 있는 책은 그대로이고 읽는 화는 비어 있다`() {
        val db = SQLiteDatabase.create(null).apply {
            OldSchemas.v4(this)
            execSQL("INSERT INTO shelf VALUES (0, 'b-1', '데미안', '헤세', 'x/y', 1000, 20, 5, '데미안')")
            Db.upgrade(this, 4)
        }
        assertEquals(listOf("b-1|20|5|"), db.rows("SELECT id, progress, due, chapter FROM shelf") { "${it.getString(0)}|${it.getInt(1)}|${it.getLong(2)}|${it.getString(3)}" })
    }

    @Test fun `v2 에서 올리면 감상은 마지막 완독 회차로 옮기고 완독한 적 없는 책의 감상은 그대로`() {
        val db = v2().apply {
            execSQL("INSERT INTO shelf VALUES (0, 'b-reread', '다시 읽는 책', '', NULL, 1000, 20, -1, '')")
            execSQL("INSERT INTO finish VALUES ('b-reread', 100), ('b-reread', 200), ('b-once', 150)")
            execSQL("INSERT INTO review VALUES ('b-reread', 8, '두 번째가 좋았다', 0), ('b-never', 4, '그만둠', 0)")
            execSQL("INSERT INTO reading_day VALUES (90, 'pkg/다시 읽는 책', 600000)")
        }
        Db.upgrade(db, 2)

        assertEquals(listOf("id", "uid", "book_id", "day", "at", "started", "stars", "text", "began"), db.columns("finish"))
        assertEquals(3L, db.count("SELECT COUNT(*) FROM finish"))
        assertEquals(3L, db.count("SELECT COUNT(DISTINCT uid) FROM finish WHERE length(uid) = 32"))
        // 마지막 완독(200)에만 옮겼다
        assertEquals(listOf("200|8|두 번째가 좋았다"), db.rows("SELECT day, stars, text FROM finish WHERE stars IS NOT NULL") {
            "${it.getLong(0)}|${it.getInt(1)}|${it.getString(2)}"
        })
        assertEquals(listOf("b-never"), db.rows("SELECT book_id FROM review") { it.getString(0) })
        // 다시 읽는 중인 책은 진행 중 회차 표시만 두고 다시 읽기한 날은 모르므로 비운다
        assertEquals(listOf("b-reread|null"), db.rows("SELECT book_id, day FROM round_start") { "${it.getString(0)}|${if (it.isNull(1)) null else it.getLong(1)}" })
        assertEquals(1L, db.count("SELECT COUNT(*) FROM reading_day"))
    }

    @Test fun `v2 에서 올리면 읽은 시간을 앱·제목으로 나누고 그때의 목록으로 책을 정해 둔다`() {
        val zone = java.time.ZoneId.systemDefault()
        fun noon(day: Long) = java.time.LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3_600_000L
        val db = v2().apply {
            // 1월(날 100)에 읽고 날 110 에 나간 A, 같은 제목으로 새로 넣은 B, 고친 제목의 책 C, 목록에 없던 책, 앱만 아는 시간
            execSQL("INSERT INTO past VALUES (0, 'p-a', '데미안', '', NULL, 0, -1, '', ${noon(110)}, 0)")
            execSQL("INSERT INTO shelf VALUES (0, 'b-b', '데미안', '', NULL, 0, -1, -1, ''), (1, 'b-c', '헤세의 소설', '', NULL, 0, -1, -1, '싯다르타')")
            execSQL("INSERT INTO reading_day VALUES (100, 'pkg/데미안', 60), (200, 'pkg/데미안', 30), (200, 'pkg/싯다르타', 20), " +
                "(200, 'pkg/어린 왕자', 10), (200, 'kr.co.kyobo.elib', 5), (201, 'pkg/a/b', 1)")
            execSQL("INSERT INTO book_open VALUES (0, 'pkg', '데미안')")
        }
        Db.upgrade(db, 2)

        assertEquals(listOf("day", "pkg", "title", "book_id", "ms"), db.columns("reading_day"))
        assertEquals(
            listOf("100|pkg|데미안|p-a|60", "200|kr.co.kyobo.elib|||5", "200|pkg|데미안|b-b|30", "200|pkg|싯다르타|b-c|20",
                "200|pkg|어린 왕자||10", "201|pkg|a/b||1"),
            db.rows("SELECT day, pkg, title, book_id, ms FROM reading_day ORDER BY day, pkg, title") {
                "${it.getLong(0)}|${it.getString(1)}|${it.getString(2)}|${it.getString(3)}|${it.getLong(4)}"
            },
        )
        assertEquals(listOf("at", "pkg", "title", "book_id"), db.columns("book_open"))
        assertEquals(listOf(""), db.rows("SELECT book_id FROM book_open") { it.getString(0) })
    }

    @Test fun `빈 제목 칸(pkg 슬래시)과 앱만 아는 칸이 같은 날 있어도 올리기가 실패하지 않고 시간을 더한다`() {
        val db = v2().apply {
            execSQL("INSERT INTO reading_day VALUES (200, 'pkg', 5), (200, 'pkg/', 7), (200, 'pkg/데미안', 3)")
        }
        Db.upgrade(db, 2)
        assertEquals(listOf("pkg||12", "pkg|데미안|3"), db.rows("SELECT pkg, title, ms FROM reading_day ORDER BY title") {
            "${it.getString(0)}|${it.getString(1)}|${it.getLong(2)}"
        })
    }

    @Test fun `v1 에서 올리면 review 표가 생기고 완독은 그대로`() {
        val db = v1().apply { execSQL("INSERT INTO finish VALUES ('b-1', 100), ('b-1', 300), ('b-2', 200)") }
        Db.upgrade(db, 1)

        assertTrue(db.columns("review").isNotEmpty())
        assertTrue(db.columns("round_start").isNotEmpty())
        assertEquals(listOf("b-1|100", "b-2|200", "b-1|300"), db.rows("SELECT book_id, day FROM finish ORDER BY id") { "${it.getString(0)}|${it.getLong(1)}" })
        assertEquals(0L, db.count("SELECT COUNT(*) FROM finish WHERE stars IS NOT NULL OR started IS NOT NULL OR at IS NOT NULL"))
    }

    @Test fun `새로 설치하면 지금 표가 생기고 같은 날 완독을 두 번 넣을 수 있다`() {
        val db = SQLiteDatabase.create(null).apply { Db.create(this) }
        db.execSQL("INSERT INTO finish (uid, book_id, day) VALUES ('a', 'b-1', 100), ('b', 'b-1', 100)")
        assertEquals(2L, db.count("SELECT COUNT(*) FROM finish WHERE book_id = 'b-1' AND day = 100"))
        assertEquals(listOf("book_id", "day"), db.columns("round_start"))
        assertEquals(listOf("book_id", "stars", "text", "updated_at"), db.columns("review"))
        assertEquals(listOf("day", "pkg", "title", "book_id", "ms"), db.columns("reading_day"))
        assertEquals(listOf("at", "pkg", "title", "book_id"), db.columns("book_open"))
        // 같은 날·같은 앱·같은 제목이어도 책이 다르면 두 칸
        db.execSQL("INSERT INTO reading_day VALUES (1, 'pkg', '데미안', 'b-1', 5), (1, 'pkg', '데미안', 'b-2', 7)")
        assertEquals(2L, db.count("SELECT COUNT(*) FROM reading_day"))
    }
}
