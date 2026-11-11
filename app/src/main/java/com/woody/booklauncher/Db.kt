package com.woody.booklauncher

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * 앱 데이터 DB(기기 보호 저장소의 `databases/pebble.db`, 안드로이드 기본 SQLite라 앱 크기가 늘지 않는다).
 * - `reading_day`·`reading_meta`: 날마다 읽은 시간과 어디까지 셌는지([ReadingLog]). 한 줄이 그날 한 앱(`pkg`)에서 편 책 제목(`title`, 몰랐으면 "")
 *   으로 읽은 시간(`ms`)이고, `book_id` 는 그 시간이 붙은 책(목록에 없던 책이면 ""). 책은 그날을 저장할 때 한 번 정하고([bookFor]) 다시 정하지 않는다.
 *   버전 3 에서 `패키지/제목` 한 칸(`key`)을 나누고 `book_id` 를 더했다(옮길 때 그때의 목록으로 채운다).
 * - `book_open`: 이북 앱에서 책을 편 때([ReadingLog.bookOpened]). `book_id` 는 그 책(넣지 못한 책이면 ""), 버전 3 에서 더했다.
 * - `shelf`·`past`: 읽고 있는 책과 나간 책([BookShelf]). 표지 그림은 파일(`books/<id>.jpg`), 설정은 SharedPreferences.
 * - `finish`: 완독 한 번에 한 줄(줄 `id`, 책 id, 완독한 날. 같은 날 두 번 완독해도 두 줄). 다시 읽어도 지우지 않는다.
 *   `uid` 는 기기를 옮겨도 그대로인 그 완독의 이름(무작위 32자. 기록 옮기기에서 같은 완독을 알아본다. `id` 는 기기마다 따로 매긴 번호).
 *   `at` 은 완독을 누른 때(ms. 같은 날 두 완독의 순서를 기기를 옮겨도 지키게. 버전 3 전의 완독은 null, 그때는 하루에 한 번뿐이었다).
 *   `started` 는 그 회차를 `다시 읽기`로 시작한 날(`round_start` 에서 옮김, 없으면 null), `stars`·`text` 는 그 회차의 별점·감상(없으면 null).
 *   줄 id·시작한 날·별점·감상은 버전 3에서 더했다(그 전은 책·날이 키).
 * - `review`: 책(id)마다 진행 중인 마지막 회차의 별점(반 개 단위 0~10)과 감상([Review]). 완독하면 그 회차(`finish`)로 옮긴다. 버전 2에서 더했다.
 * - `round_start`: 완독한 책(id)에 진행 중 회차가 있다는 표시와 `다시 읽기`한 날(그 회차의 시작, 모르면 null). 완독하면 지운다. 버전 3에서 더했다.
 * - `viewer_mark`: 읽는 화면이 서재 안에 뜨는 앱(Libby)에서 그 화면이 보이고 사라진 때, KOReader 에서 책을 열고 닫은 때(KOReader 가 남긴 파일로 안 때)
 *   ([ReadingLog.viewerShown]). 버전 3에서 더했다.
 * - `book_file`: 책(id)마다 이북 앱이 연 파일의 경로(네오리더처럼 파일로만 여는 앱, 홈에서 그 파일을 바로 연다. [BookShelf.filePath]). 버전 4에서 더했다.
 * - `shelf.chapter`: 이북 앱이 보여 준 읽는 화(Mihon 의 화 이름 `Ch. 12` 등, 모르면 ""). 버전 5에서 더했다. 나간 책에는 남기지 않는다(반납일처럼).
 * - `finish.began`·`round_began`: 사용자가 고친 그 회차의 읽기 시작한 날(완독한 회차는 그 완독 줄에, 진행 중 회차는 책(id)마다 `round_began` 에.
 *   완독하면 그 완독 줄로 옮긴다). 앞 회차 완독한 날부터 끝 날(진행 중이면 오늘)까지 고친다([BookRecord.startDayRange]). 버전 6에서 더했다.
 * - `category`·`book_category`: 사용자가 만든 카테고리(이름·순서 `pos`·통계에서 빼기 `excluded`, 기기를 옮겨도 그대로인 `uid`)와 책(id)마다 붙인 카테고리
 *   (없으면 줄이 없다, 책 하나에 하나). [Categories]. 버전 7에서 더했다.
 * - `round_drop`: 지운 회차(전체 기록의 회차 줄 휴지통, [BookShelf.forgetRound]). 그 책의 `after_day` 다음 날(null 이면 처음)부터
 *   `until_day` 까지 읽은 시간은 그 책의 기록·회차에 넣지 않는다(하루 합계·연속일에는 그대로). 버전 7에서 더했다.
 * - `past.time_dropped`: `기록에서 지우기` 할 때 읽은 시간까지 지운 책(1, 아니면 0. [BookShelf.forget]). 그 책 id 의 `reading_day` 줄은 남겨 두고
 *   셀 때 뺀다(하루 합계·연속일·목표 달성에서도, [ReadingLog.days]). 버전 8에서 더했다.
 * - `shelf.home_hidden`·`past.home_hidden`: 홈에서 숨긴 책(1, 아니면 0. [ShelfBook.homeHidden]). 읽고 있는 책 목록에는 남고 홈에만 보이지 않는다.
 *   나간 책에도 남겨 빼고 다시 펴 되살아나도 숨긴 그대로. 버전 9에서 더했다.
 * 처음 열 때 1.2.1 까지의 JSON 파일을 옮기고 `<이름>.migrated` 로 바꿔 둔다([importFiles]).
 * 낮은 버전으로 되돌리기(새 판 DB 위에 옛 판을 까는 것)는 지원하지 않는다. 안드로이드가 낮은 판 덮어 설치를 막고, 억지로 깔면 DB 를 열지 못한다.
 */
object Db {
    private const val NAME = "pebble.db"
    private const val VERSION = 9

    @Volatile private var db: SQLiteDatabase? = null

    /** DB(한 번 열어 재사용). 이 실행에서 처음 열 때 예전 JSON 파일이 남아 있으면 옮긴다. */
    fun get(context: Context): SQLiteDatabase {
        db?.let { return it }
        synchronized(this) {
            db?.let { return it }
            val device = Storage.of(context)
            return Helper(device).writableDatabase.also {
                importFiles(device, it)
                db = it
            }
        }
    }

    /**
     * 이 실행에서 기록이 바뀐 횟수: [tx] 의 바깥 트랜잭션이 들어갈 때마다(되돌리면 세지 않는다) 하나씩. 늘었으면 그사이 기록이 바뀌었다([Sheet.dismissStale]).
     * 기록을 쓰는 곳은 모두 [tx] 로 쓴다.
     */
    fun changes(): Long = revision.get()

    @PublishedApi internal val revision = java.util.concurrent.atomic.AtomicLong()

    /** 테스트: 연 DB 를 닫고 다음 [get] 에서 파일을 다시 열게 한다(마이그레이션·JSON 옮기기를 다시 거치게) */
    internal fun closeForTest() = synchronized(this) { db?.close(); db = null }

    private class Helper(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) = create(db)

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = upgrade(db, oldVersion)
    }

    /** 새로 설치: 지금 버전의 표를 만든다(마이그레이션 테스트도 쓴다) */
    internal fun create(db: SQLiteDatabase) {
        createReadingDay(db)
        db.execSQL("CREATE TABLE reading_meta (name TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("CREATE TABLE book_open (at INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL, book_id TEXT NOT NULL DEFAULT '')")
        db.execSQL(
            "CREATE TABLE shelf (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, " +
                "updated_at INTEGER, progress INTEGER, due INTEGER, seen_title TEXT, chapter TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE past (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, " +
                "updated_at INTEGER, progress INTEGER, seen_title TEXT, left_at INTEGER, hidden INTEGER)"
        )
        createFinish(db)
        createReview(db)
        createRoundStart(db)
        createViewerMark(db)
        createBookFile(db)
        addBegan(db)
        createCategory(db)
        createRoundDrop(db)
        addTimeDropped(db)
        addHomeHidden(db)
    }

    /** [oldVersion] 의 DB 를 지금 버전으로(SQLiteOpenHelper 가 한 트랜잭션으로 돌린다. 마이그레이션 테스트도 쓴다) */
    internal fun upgrade(db: SQLiteDatabase, oldVersion: Int) {
        if (oldVersion < 2) createReview(db)
        if (oldVersion < 3) {
            // 완독에 줄 id 와 기기를 옮겨도 그대로인 uid 를 주고(같은 날 두 번 완독해도 따로) 회차의 별점·감상을 더한다. 키가 바뀌어 표를 새로 만들어 옮긴다.
            db.execSQL("ALTER TABLE finish RENAME TO finish_v2")
            createFinish(db)
            db.execSQL("INSERT INTO finish (uid, book_id, day) SELECT lower(hex(randomblob(16))), book_id, day FROM finish_v2 ORDER BY day")
            db.execSQL("DROP TABLE finish_v2")
            // 버전 2 까지는 별점·감상이 책마다 하나였다. 완독한 적이 있는 책은 그 마지막 완독 회차로 옮기고(다시 읽는 중이어도
            // 완독할 때 쓴 것이라), 완독한 적이 없는 책은 그대로(하나뿐인 회차의 것) 둔다. 그 앞 회차는 남긴 것 없이.
            db.execSQL(
                "UPDATE finish SET stars = (SELECT stars FROM review r WHERE r.book_id = finish.book_id), " +
                    "text = (SELECT text FROM review r WHERE r.book_id = finish.book_id) " +
                    "WHERE book_id IN (SELECT book_id FROM review) AND " +
                    "id = (SELECT f.id FROM finish f WHERE f.book_id = finish.book_id ORDER BY f.day DESC, f.id DESC LIMIT 1)"
            )
            db.execSQL("DELETE FROM review WHERE book_id IN (SELECT book_id FROM finish)")
            // 지금 다시 읽는 중인 책(읽고 있는 책 중 완독한 적 있는 책)은 진행 중 회차 표시만 두고 다시 읽기한 날은 비워 둔다
            // (updated_at 은 표지·제목을 고쳐도 바뀌어 믿을 수 없다). 그 회차는 마지막 완독 뒤 처음 읽은 날부터, 아직 안 읽었으면 시작일 모름.
            // 표시를 두어야 읽지 않고 빼도 진행 중 회차가 남는다.
            createRoundStart(db)
            db.execSQL("INSERT INTO round_start (book_id, day) SELECT id, NULL FROM shelf WHERE id IN (SELECT book_id FROM finish)")
            // 읽은 시간: `패키지/제목` 한 칸을 앱·제목으로 나누고, 줄마다 그때의 목록으로 책을 정해 둔다(그 뒤 같은 제목의 새 책이 생겨도 옮겨 가지 않게).
            db.execSQL("ALTER TABLE reading_day RENAME TO reading_day_v2")
            createReadingDay(db)
            moveReadingDays(db)
            db.execSQL("DROP TABLE reading_day_v2")
            db.execSQL("ALTER TABLE book_open ADD COLUMN book_id TEXT NOT NULL DEFAULT ''")
            // 읽는 화면이 서재 안에 뜨는 앱(Libby)의 화면이 뜨고 닫힌 때
            createViewerMark(db)
        }
        // 파일로만 여는 이북 앱(네오리더)에서 연 책 파일의 경로
        if (oldVersion < 4) createBookFile(db)
        // 읽고 있는 책의 읽는 화(진행률 % 대신 앱이 보여 준 글자, Mihon 의 화 이름)
        if (oldVersion < 5) db.execSQL("ALTER TABLE shelf ADD COLUMN chapter TEXT NOT NULL DEFAULT ''")
        // 사용자가 고친 회차의 읽기 시작한 날
        if (oldVersion < 6) addBegan(db)
        // 카테고리와 책에 붙인 카테고리
        if (oldVersion < 7) createCategory(db)
        // 지운 회차
        if (oldVersion < 7) createRoundDrop(db)
        // 읽은 시간까지 지운 책
        if (oldVersion < 8) addTimeDropped(db)
        // 홈에서 숨긴 책
        if (oldVersion < 9) addHomeHidden(db)
    }

    /** 버전 9: 읽고 있는 책·나간 책에 홈에서 숨겼는지 */
    private fun addHomeHidden(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE shelf ADD COLUMN home_hidden INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE past ADD COLUMN home_hidden INTEGER NOT NULL DEFAULT 0")
    }

    /** 버전 8: 나간 책에 읽은 시간까지 지웠는지 */
    private fun addTimeDropped(db: SQLiteDatabase) = db.execSQL("ALTER TABLE past ADD COLUMN time_dropped INTEGER NOT NULL DEFAULT 0")

    /** 버전 7: 카테고리(이름·순서·통계에서 빼기)와 책마다 붙인 카테고리 */
    private fun createCategory(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE category (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL UNIQUE, name TEXT NOT NULL, pos INTEGER NOT NULL, " +
                "excluded INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE TABLE book_category (book_id TEXT PRIMARY KEY, category_id INTEGER NOT NULL)")
    }

    /**
     * 버전 6: 고친 읽기 시작한 날(완독한 회차는 `finish.began`, 진행 중 회차는 `round_began`). 새 설치도 이것으로 더한다
     * (버전 3 전에서 올릴 때 [createFinish] 로 만든 표에 칸이 겹치지 않게).
     */
    private fun addBegan(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE finish ADD COLUMN began INTEGER")
        db.execSQL("CREATE TABLE round_began (book_id TEXT PRIMARY KEY, day INTEGER NOT NULL)")
    }

    /** 날마다 읽은 시간: 그날·앱·편 책 제목·붙은 책이 키(모르는 것은 "", 기본 키에 NULL 은 겹쳐도 막히지 않아 쓰지 않는다) */
    private fun createReadingDay(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE reading_day (day INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', book_id TEXT NOT NULL DEFAULT '', " +
                "ms INTEGER NOT NULL, PRIMARY KEY (day, pkg, title, book_id))"
        )
        db.execSQL("CREATE INDEX reading_day_book ON reading_day (book_id)")
    }

    /**
     * 버전 2 까지의 `reading_day`(`key` = `패키지/제목` 또는 패키지만)를 새 표로 옮기며 책을 정한다([bookFor], 그때의 읽고 있는 책·나간 책).
     * 정하지 못한 줄(목록에 없던 책, 앱만 아는 시간)은 "".
     */
    private fun moveReadingDays(db: SQLiteDatabase) {
        val shelf = db.rows("SELECT id, title, seen_title FROM shelf ORDER BY pos") {
            ShelfBook(it.getString(0), it.getString(1).orEmpty(), "", null, 0, seenTitle = it.getString(2).orEmpty())
        }
        val past = db.rows("SELECT id, title, seen_title, left_at, hidden FROM past ORDER BY pos") {
            PastBook(ShelfBook(it.getString(0), it.getString(1).orEmpty(), "", null, 0, seenTitle = it.getString(2).orEmpty()), it.getLong(3), it.getInt(4) != 0)
        }
        // 다른 옛 칸이 같은 새 칸이 될 수 있어(앱만 아는 `pkg` 와 빈 제목 `pkg/`) 더해서 넣는다(그대로 넣으면 기본 키가 겹쳐 올리기가 실패한다).
        val rows = linkedMapOf<List<Any>, Long>()
        db.rows("SELECT day, key, ms FROM reading_day_v2") { Triple(it.getLong(0), it.getString(1), it.getLong(2)) }.forEach { (day, key, ms) ->
            val pkg = key.substringBefore('/')
            val title = key.substringAfter('/', "")
            val book = title.takeIf { it.isNotEmpty() }?.let { bookFor(it, LocalDate.ofEpochDay(day), shelf, past) }.orEmpty()
            val k = listOf(day, pkg, title, book)
            rows[k] = (rows[k] ?: 0L) + ms
        }
        rows.forEach { (k, ms) ->
            db.insertOrThrow("reading_day", null, values("day" to k[0], "pkg" to k[1], "title" to k[2], "book_id" to k[3], "ms" to ms))
        }
    }

    private fun createFinish(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE finish (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL UNIQUE, book_id TEXT NOT NULL, day INTEGER NOT NULL, " +
                "at INTEGER, started INTEGER, stars INTEGER, text TEXT)"
        )
        db.execSQL("CREATE INDEX finish_book ON finish (book_id)")
    }

    private fun createViewerMark(db: SQLiteDatabase) =
        db.execSQL("CREATE TABLE viewer_mark (at INTEGER NOT NULL, pkg TEXT NOT NULL, shown INTEGER NOT NULL)")

    private fun createBookFile(db: SQLiteDatabase) =
        db.execSQL("CREATE TABLE book_file (book_id TEXT PRIMARY KEY, path TEXT NOT NULL)")

    /** 지운 회차: 책 id, 그 회차 앞 회차가 끝난 날(`after_day`, 첫 회차면 null), 그 회차가 끝난 날(`until_day`). 한 책에 여러 줄 */
    private fun createRoundDrop(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE round_drop (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id TEXT NOT NULL, after_day INTEGER, until_day INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX round_drop_book ON round_drop (book_id)")
    }

    private fun createRoundStart(db: SQLiteDatabase) =
        db.execSQL("CREATE TABLE round_start (book_id TEXT PRIMARY KEY, day INTEGER)")

    private fun createReview(db: SQLiteDatabase) =
        db.execSQL("CREATE TABLE review (book_id TEXT PRIMARY KEY, stars INTEGER NOT NULL, text TEXT NOT NULL, updated_at INTEGER NOT NULL)")

    /** [block] 을 한 트랜잭션으로(중간에 실패하면 아무것도 바뀌지 않는다) */
    inline fun <T> SQLiteDatabase.tx(block: SQLiteDatabase.() -> T): T {
        // 바깥 트랜잭션이 들어가면(endTransaction 이 예외 없이 끝나면) [changes] 를 하나 올린다
        val outer = !inTransaction()
        var ok = false
        beginTransaction()
        try {
            return block().also { setTransactionSuccessful(); ok = true }
        } finally {
            endTransaction()
            if (outer && ok) revision.incrementAndGet()
        }
    }

    /** 커서의 줄마다 [row] 로 읽고 닫는다. */
    inline fun <T> SQLiteDatabase.rows(sql: String, args: Array<String>? = null, row: (Cursor) -> T): List<T> =
        rawQuery(sql, args).use { c -> ArrayList<T>().apply { while (c.moveToNext()) add(row(c)) } }

    fun values(vararg pairs: Pair<String, Any?>) = ContentValues().apply {
        for ((k, v) in pairs) when (v) {
            null -> putNull(k)
            is String -> put(k, v)
            is Long -> put(k, v)
            is Int -> put(k, v)
            is Boolean -> put(k, if (v) 1 else 0)
            else -> error("unsupported $k")
        }
    }

    /**
     * 1.2.1 까지 쓰던 JSON 파일(`reading.json`, `books/books.json`)을 DB로 옮긴다. 책 id·표지 이름은 [BookShelf] 가 읽을 때 새 방식으로 바꾼다. 파일마다 한 트랜잭션으로 넣고 `.migrated` 로 이름을 바꾼다(다시 옮기지 않게, 되살릴 수 있게).
     * 읽지 못하는 파일은 `.bad` 로 따로 두고 넘어간다. DB에 이미 있는 것은 덮어쓰지 않는다.
     */
    private fun importFiles(device: Context, db: SQLiteDatabase) {
        val files = device.filesDir
        fun move(file: File, import: (String) -> Unit) {
            if (!file.exists()) return
            runCatching { db.tx { import(file.readText()) } }.onFailure { keepBadCopy(file) }
            file.renameTo(File(file.parentFile, "${file.name}.migrated"))
        }
        // {"through": "2026-10-07", "since": "2026-09-28", "days": {"2026-10-07": {"kr.co.kyobo.elib": ms}}} (앱별 하루 합계, 책은 모른다)
        move(File(files, "reading.json")) { text ->
            val json = JSONObject(text)
            val days = json.getJSONObject("days")
            for (d in days.keys()) {
                val o = days.getJSONObject(d)
                val day = LocalDate.parse(d).toEpochDay()
                for (key in o.keys()) {
                    db.insertWithOnConflict("reading_day", null, values("day" to day, "pkg" to key, "ms" to o.getLong(key)), SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
            for (name in listOf("through", "since")) {
                json.optString(name).takeIf { it.isNotEmpty() && it != "null" }?.let {
                    db.insertWithOnConflict("reading_meta", null, values("name" to name, "value" to it), SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
        }
        val books = File(files, "books")
        move(File(books, "books.json")) { text ->
            if (count(db, "shelf") > 0) return@move
            val a = JSONArray(text)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.insert("shelf", null, values(
                    "pos" to i, "id" to o.getString("id"), "title" to o.optString("title"), "author" to o.optString("author"),
                    "app" to o.optString("app"), "updated_at" to o.optLong("updated_at"), "progress" to o.optInt("progress", -1),
                    "due" to o.optLong("due", -1),
                ))
            }
        }
    }

    /** 읽지 못한 파일을 `.bad` 로 따로 둔다(되살릴 수 있게). */
    private fun keepBadCopy(file: File) {
        runCatching { file.copyTo(File(file.parentFile, "${file.name}.bad"), overwrite = true) }
    }

    private fun count(db: SQLiteDatabase, table: String): Long = db.rows("SELECT COUNT(*) FROM $table") { it.getLong(0) }.first()
}
