package com.woody.pebbledesk

import android.database.sqlite.SQLiteDatabase

/** 릴리스된 판의 DB 표(그대로 만들어 올리기를 시험한다): 1.3.0~1.4.2 버전 1(review 표 없음), 1.5.0~1.5.3 버전 2, 1.5.4 버전 3, 1.5.5~1.6.0 버전 4, 1.6.1~1.7.2 버전 5, 1.7.3~1.7.6 버전 6 */
object OldSchemas {
    fun v1(db: SQLiteDatabase) = with(db) {
        execSQL("CREATE TABLE reading_day (day INTEGER NOT NULL, key TEXT NOT NULL, ms INTEGER NOT NULL, PRIMARY KEY (day, key))")
        execSQL("CREATE TABLE reading_meta (name TEXT PRIMARY KEY, value TEXT)")
        execSQL("CREATE TABLE book_open (at INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL)")
        execSQL("CREATE TABLE shelf (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, due INTEGER, seen_title TEXT)")
        execSQL("CREATE TABLE past (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, seen_title TEXT, left_at INTEGER, hidden INTEGER)")
        execSQL("CREATE TABLE finish (book_id TEXT NOT NULL, day INTEGER NOT NULL, PRIMARY KEY (book_id, day))")
    }

    fun v2(db: SQLiteDatabase) {
        v1(db)
        db.execSQL("CREATE TABLE review (book_id TEXT PRIMARY KEY, stars INTEGER NOT NULL, text TEXT NOT NULL, updated_at INTEGER NOT NULL)")
    }

    /** 버전 6: 지금 표에서 버전 7 의 `category`·`book_category` 만 없다 */
    fun v6(db: SQLiteDatabase) = with(db) {
        Db.create(this)
        execSQL("DROP TABLE category")
        execSQL("DROP TABLE book_category")
    }

    /** 버전 5: 지금 표에서 버전 6 의 `finish.began`·`round_began` 만 없다 */
    fun v5(db: SQLiteDatabase) = with(db) {
        v6(this)
        execSQL("DROP TABLE round_began")
        execSQL("DROP TABLE finish")
        execSQL(
            "CREATE TABLE finish (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL UNIQUE, book_id TEXT NOT NULL, day INTEGER NOT NULL, " +
                "at INTEGER, started INTEGER, stars INTEGER, text TEXT)"
        )
        execSQL("CREATE INDEX finish_book ON finish (book_id)")
    }

    /** 버전 4: 버전 5 에서 `shelf.chapter` 가 없다 */
    fun v4(db: SQLiteDatabase) = with(db) {
        v5(this)
        execSQL("DROP TABLE shelf")
        execSQL("CREATE TABLE shelf (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, due INTEGER, seen_title TEXT)")
    }

    /** 버전 3: 버전 4 에서 `book_file` 이 없다 */
    fun v3(db: SQLiteDatabase) {
        v4(db)
        db.execSQL("DROP TABLE book_file")
    }
}
