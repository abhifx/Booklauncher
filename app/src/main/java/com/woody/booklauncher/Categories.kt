package com.woody.booklauncher

import android.content.Context
import com.woody.booklauncher.Db.rows
import com.woody.booklauncher.Db.tx
import java.util.UUID

/** 사용자가 만든 카테고리 하나. [excluded] 면 통계(홈 하단·독서 기록·올해 읽은 책·결산의 `전체`)에서 뺀다. */
data class Category(val id: Long, val name: String, val excluded: Boolean)

/**
 * 카테고리(DB `category`)와 책마다 붙인 카테고리(`book_category`, 책 하나에 없거나 하나). 붙이지 않은 책은 `분류 없음`.
 * 책은 책 상세의 `카테고리` 에서 붙이고, 카테고리는 설정 › 독서 › 카테고리([CategoriesActivity])에서 만들고 고친다.
 * 통계에서 거르는 것은 [StatsFilter].
 */
object Categories {
    /** 보기 거르기에서 `분류 없음`(카테고리를 붙이지 않은 책) */
    const val NONE = -1L

    private class Snapshot(val db: android.database.sqlite.SQLiteDatabase, val revision: Long, val list: List<Category>, val books: Map<String, Long>)

    @Volatile private var cache: Snapshot? = null

    /** 지금 카테고리와 책마다 붙인 것(기록이 바뀌지 않았으면 읽어 둔 것) */
    private fun load(context: Context): Snapshot {
        val revision = Db.changes()
        val db = Db.get(context)
        cache?.takeIf { it.revision == revision && it.db === db }?.let { return it }
        val list = db.rows("SELECT id, name, excluded FROM category ORDER BY pos, id") { Category(it.getLong(0), it.getString(1), it.getInt(2) != 0) }
        val ids = list.map { it.id }.toSet()
        // 지운 카테고리를 가리키는 줄은 없지만(지울 때 함께 지운다) 있어도 `분류 없음`으로
        val books = db.rows("SELECT book_id, category_id FROM book_category") { it.getString(0) to it.getLong(1) }.filter { it.second in ids }.toMap()
        return Snapshot(db, revision, list, books).also { cache = it }
    }

    /** 테스트: 읽어 둔 것을 버린다(DB 를 새로 열 때) */
    internal fun clearCache() { cache = null }

    /** 카테고리(정한 순서) */
    fun list(context: Context): List<Category> = load(context).list

    /** 책 id → 붙인 카테고리 id */
    fun books(context: Context): Map<String, Long> = load(context).books

    /** [book] 에 붙인 카테고리(없으면 null) */
    fun of(context: Context, book: String): Category? = load(context).let { s -> s.books[book]?.let { id -> s.list.firstOrNull { it.id == id } } }

    /** 이름을 다듬는다(앞뒤 빈칸, 너무 길면 자른다) */
    private fun clean(name: String) = name.trim().take(MAX_NAME)

    /** 같은 이름(대소문자 무시)의 카테고리. [name] 은 다듬은 이름. */
    private fun named(context: Context, name: String) = list(context).firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** [name] 을 저장할 때처럼 다듬으면 다른 카테고리([except] 빼고)와 이름이 같은가(입력 칸에서 저장 전에 알린다) */
    fun sameName(context: Context, name: String, except: Long? = null): Boolean = named(context, clean(name))?.let { it.id != except } == true

    /** 새 카테고리(맨 뒤). 같은 이름이 있으면 그것을 돌려준다. 빈 이름이면 null. */
    fun add(context: Context, name: String): Category? {
        val n = clean(name)
        if (n.isEmpty()) return null
        named(context, n)?.let { return it }
        val id = Db.get(context).tx {
            val pos = rows("SELECT COALESCE(MAX(pos), -1) + 1 FROM category") { it.getLong(0) }.first()
            insertOrThrow("category", null, Db.values(
                "uid" to UUID.randomUUID().toString().replace("-", ""), "name" to n, "pos" to pos, "excluded" to false,
            ))
        }
        cache = null
        return list(context).firstOrNull { it.id == id }
    }

    /** 이름 바꾸기. 빈 이름이거나 다른 카테고리와 같은 이름이면 바꾸지 않는다(false). */
    fun rename(context: Context, id: Long, name: String): Boolean {
        val n = clean(name)
        if (n.isEmpty() || sameName(context, n, except = id)) return false
        update(context, id, "name" to n)
        return true
    }

    /** 통계에서 빼기([excluded])·다시 넣기 */
    fun setExcluded(context: Context, id: Long, excluded: Boolean) = update(context, id, "excluded" to excluded)

    private fun update(context: Context, id: Long, value: Pair<String, Any>) {
        Db.get(context).tx { update("category", Db.values(value), "id = ?", arrayOf(id.toString())) }
        cache = null
    }

    /** 순서를 [delta] 칸 옮긴다(목록 끝에서는 그대로) */
    fun move(context: Context, id: Long, delta: Int) {
        val order = list(context).map { it.id }.toMutableList()
        val i = order.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in order.indices) return
        order.add(j, order.removeAt(i))
        Db.get(context).tx {
            order.forEachIndexed { pos, c -> update("category", Db.values("pos" to pos), "id = ?", arrayOf(c.toString())) }
        }
        cache = null
    }

    /** 카테고리를 지운다. 그 책들은 `분류 없음`이 된다(읽은 기록은 그대로). */
    fun delete(context: Context, id: Long) {
        Db.get(context).tx {
            delete("book_category", "category_id = ?", arrayOf(id.toString()))
            delete("category", "id = ?", arrayOf(id.toString()))
        }
        cache = null
    }

    /** [book] 에 카테고리 [category] 를 붙인다(null 이면 뗀다) */
    fun assign(context: Context, book: String, category: Long?) {
        Db.get(context).tx {
            if (category == null) delete("book_category", "book_id = ?", arrayOf(book))
            else insertWithOnConflict("book_category", null, Db.values("book_id" to book, "category_id" to category), android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        }
        cache = null
    }

    /** 카테고리마다 붙은 책 수(읽고 있는 책과 기록에서 지우지 않은 나간 책만) */
    fun bookCounts(context: Context): Map<Long, Int> {
        val alive = BookShelf.list(context).map { it.id }.toSet() + BookShelf.history(context).filter { !it.hidden }.map { it.book.id }
        return books(context).filterKeys { it in alive }.values.groupingBy { it }.eachCount()
    }

    /** 카테고리 이름 길이 한도 */
    private const val MAX_NAME = 30
}

/**
 * 통계 거르기. [picked] 가 비면 `전체`: 통계에서 뺀 카테고리의 책만 빼고 모두(어떤 책인지 모르는 시간도 든다).
 * 아니면 고른 카테고리([Categories.NONE] 은 `분류 없음`)의 책만(어떤 책인지 모르는 시간은 뺀다. 제목만 아는 책은 `분류 없음`).
 * 없어진 카테고리를 고른 것은 무시하고, 남는 것이 없거나 [selectable] 을 꼭 그만큼 고른 것이면 `전체`.
 */
class StatsFilter(picked: Set<Long>, val categories: List<Category>, private val books: Map<String, Long>) {
    private val excluded = categories.filter { it.excluded }.map { it.id }.toSet()

    /** 보기 메뉴에서 `전체`를 한 번 더 누르면 켜는 것: 통계에서 빼지 않은 카테고리와 `분류 없음`(꼭 이것만 고르면 `전체`와 같게 본다) */
    val selectable: Set<Long> = categories.map { it.id }.filter { it !in excluded }.toSet() + Categories.NONE

    val picked: Set<Long> = picked.filter { it == Categories.NONE || categories.any { c -> c.id == it } }.toSet()
        .takeIf { it != selectable }.orEmpty()

    /** `전체` */
    val all get() = picked.isEmpty()

    /** 거를 것이 없다(`전체`이고 통계에서 뺀 카테고리에 붙은 책이 없다): 기록을 그대로 쓴다 */
    private val passThrough = all && books.values.none { it in excluded }

    /** 책 [id] 가 드는가(목록에 없던 책·제목만 아는 책은 `분류 없음`) */
    fun keepsBook(id: String): Boolean {
        val c = books[id]
        return if (all) c == null || c !in excluded else (c ?: Categories.NONE) in picked
    }

    /** 읽은 시간 한 칸이 드는가: 책을 알면 그 책으로, 제목만 알면 `분류 없음`, 아무것도 모르면 `전체`에만 */
    fun keeps(k: ReadKey): Boolean = when {
        k.book.isNotEmpty() -> keepsBook(k.book)
        all -> true
        else -> k.title.isNotEmpty() && Categories.NONE in picked
    }

    /** 거른 날마다 기록(날은 그대로 두고 칸만 거른다: 기록이 있는 날이 바뀌지 않게) */
    fun days(days: ReadingDays): ReadingDays = if (passThrough) days else days.mapValues { (_, m) -> m.filterKeys(::keeps) }

    /** 같은 카테고리·책으로 다른 것을 고른 거르기 */
    fun with(picked: Set<Long>) = StatsFilter(picked, categories, books)

    companion object {
        /** 지금 카테고리로 [picked] 를 고른 거르기(비면 `전체`. 홈 하단은 늘 `전체`) */
        fun of(context: Context, picked: Set<Long> = emptySet()) = StatsFilter(picked, Categories.list(context), Categories.books(context))
    }
}
