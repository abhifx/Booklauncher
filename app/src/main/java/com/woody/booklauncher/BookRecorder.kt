package com.woody.booklauncher

import android.content.Context
import android.graphics.Bitmap
import com.woody.booklauncher.readers.BookFiles
import com.woody.booklauncher.readers.isImageFile
import com.woody.booklauncher.readers.withoutBookFile
import java.io.File

/**
 * 읽는 책 자동 추가의 공통 기록 규칙(docs/reader-adapter-plan.md `기록기`): 편 책을 책 목록에 넣고 편 책 기록·진행률·표지를 적는다.
 * 이북 앱 종류를 모른다. 어떤 책인지는 앱이 [Target] 으로 가리킨다.
 */
interface BookRecorder {
    /**
     * 책을 폈다: 목록에 있으면 맨 앞으로, 없으면 새로 넣고(전에 뺀 책이면 되살린다) 그 책을 돌려준다. 안의 순서는
     * ① 찾기·넣기(맨 앞으로·진행률·반납일·저자) → ② [steps].found → ③ 기록(WatchLog)·편 책 기록 → ④ [steps].logged → ⑤ 잘라 둔 표지.
     * ①②④ 의 예외는 밖으로 내보내 그 뒤 단계를 하지 않고, ③ 편 책 기록 저장과 ⑤ 표지 저장의 실패는 삼킨다(실패 전에 끝난 저장은 되돌리지 않는다).
     * [at] 은 그 책을 편 때(null 이면 지금). 읽은 시간을 이때부터 이 책으로 센다.
     */
    fun opened(pkg: String, seen: Seen, at: Long?, target: Target, coverKey: String? = null, steps: OpenSteps? = null): ShelfBook
    /** 여기부터 [pkg] 에서 어떤 책인지 모른다(앞서 편 책에 붙이지 않게) */
    fun unknown(pkg: String, at: Long)
    /** 진행률·반납일·저자·읽는 화(음수·빈 값은 그대로 둔다) */
    fun status(bookId: String, progress: Int, due: Long = -1, author: String = "", chapter: String = "")
    /** 서재 한 화면의 것을 한 번에 */
    fun statuses(list: List<BookShelf.Status>)
    fun setFile(bookId: String, path: String)
    fun setTitle(bookId: String, title: String)
    fun setAuthor(bookId: String, author: String)
    /** 표지가 없을 때만 붙인다. 저장 실패는 삼킨다(못 넣은 책은 다음에 다시) */
    fun cover(bookId: String, jpeg: ByteArray)
    /**
     * 이북 앱 데이터의 표지 그림으로 바꾼다: 화면에서 자른 표지가 이미 붙어 있어도(그 표지에 앱이 그린 글자·표시가 든 경우). 저장 실패는 삼킨다.
     * 기본: [cover] 와 같다(표지가 없을 때만)
     */
    fun replaceCover(bookId: String, jpeg: ByteArray) = cover(bookId, jpeg)
    /**
     * 붙어 있는 표지가 [placeholder](이북 앱이 표지 대신 그리는 빈 그림)면 지운다(예전 판이 그것을 표지로 잘라 붙인 책. 그러면 진짜 표지가 보일 때 다시 자른다).
     * 지웠으면 true. 화면 스레드에서 부르지 않는다. 기본: 하지 않는다
     */
    fun dropPlaceholderCover(bookId: String, placeholder: (Bitmap) -> Boolean): Boolean = false
    /** 아직 목록에 없는 책의 잘라 둔 표지(그 책이 들어오면 [opened] 가 붙인다, 최근 [PENDING_MAX]권만). 열쇠는 정리한 제목 또는 파일 칸 열쇠 */
    fun pendingCover(key: String, jpeg: ByteArray)
    fun hasPendingCover(key: String): Boolean
    /** [title] 의 잘라 둔 표지가 있다(부제·공백 무시) */
    fun hasPendingTitle(title: String): Boolean
    fun viewerShown(pkg: String, shown: Boolean, at: Long, current: Boolean = true)
    /** 편 책 기록의 책 id(목록에 없어도. 책 모름이면 null) */
    fun openedId(pkg: String): String?
    /** 편 책 기록이 가리키는 읽고 있는 책(책 모름·목록에 없으면 null) */
    fun recordedBook(pkg: String): ShelfBook?
    /** 쓰지 않는 찾기 */
    val lookup: BookLookup

    companion object {
        const val PENDING_MAX = 12
    }
}

/** 화면·데이터에서 본 책: 앱이 보여 준 제목, 저자, 진행률·반납일(모르면 -1), 읽는 화(화 이름 등, 모르면 "") */
data class Seen(val title: String, val author: String = "", val progress: Int = -1, val due: Long = -1, val chapter: String = "")

/** 편 책이 어떤 책인가 */
sealed interface Target {
    /** 제목으로 찾기(부제·공백 무시, 앱이 보이던 제목도). 나간 책은 같은 제목이면 되살린다 */
    object ByTitle : Target
    /**
     * 앱이 정한 책: [id] 의 책(읽고 있는 책이면 맨 앞으로, 나간 책이면 그 책만 되살림), null 이면 같은 제목이 있어도 새 책.
     * [id] 의 책이 있으면 기록에 쓰는 제목을 그 책의 제목으로 바꾼다(파일로 가른 책은 칸의 제목이 파일 이름이기도 책 정보이기도 하다)
     */
    data class Exact(val id: String?) : Target
}

/** [BookRecorder.opened] 안에서 앱이 끼어드는 자리(그 기록 한 번만의 것) */
interface OpenSteps {
    /** 찾기·넣기 직후, 편 책 기록 전 */
    fun found(book: ShelfBook) {}
    /** 편 책 기록 직후, 잘라 둔 표지 전 */
    fun logged(book: ShelfBook) {}
}

/** 책 목록에서 읽기만 하는 찾기 */
interface BookLookup {
    /** 읽고 있는 책 가운데 그 제목(부제·공백 무시) */
    fun byTitle(title: String): ShelfBook?
    /** 읽고 있는 책 [id] */
    fun get(id: String): ShelfBook?
    /** 읽고 있는 책, 없으면 나간 책 — 기록에서 지운 책도 */
    fun bookOf(id: String): ShelfBook?
    /** [path] 파일로 넣은 책(읽고 있는 책, 없으면 나간 책) */
    fun byFile(path: String): String?
    /** 실제 경로 [path] 로 넣은 책. 그림 파일이면 그 폴더의 그림으로 넣은 책(그림 폴더가 한 권) */
    fun byPath(path: String): String?
    /** 파일을 모르는 [title] 의 책(읽고 있는 책, 없으면 지우지 않은 나간 책) */
    fun withoutFile(title: String): String?
    /**
     * 이북 앱 데이터로 찾은 책 파일 [path] 의 책이 이미 있으면 그 책의 제목: 그 파일로 넣은 책, 없으면 파일 없이 파일 이름(확장자 뗌)으로 넣은 책.
     * 그래야 같은 책이 책 정보의 제목으로 한 번 더 들어가지 않고, 나간 책은 그 책으로 되살아난다. 없으면 [title]
     */
    fun knownTitle(path: String, title: String): String
    fun idsByFileName(name: String): Set<String>
    fun filePath(id: String): String?
    fun hasCover(id: String): Boolean
}

/** [BookShelf]·[ReadingLog] 에 적는 기록기. 잘라 둔 표지는 이 인스턴스(서비스 연결 한 번)에 둔다 */
class ShelfRecorder(private val context: Context) : BookRecorder, BookLookup {
    private val pendingCovers = object : LinkedHashMap<String, ByteArray>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > BookRecorder.PENDING_MAX
    }

    override val lookup: BookLookup get() = this

    override fun opened(pkg: String, seen: Seen, at: Long?, target: Target, coverKey: String?, steps: OpenSteps?): ShelfBook {
        val id = (target as? Target.Exact)?.id
        val exact = target is Target.Exact
        val title = id?.let { BookShelf.bookOf(context, setOf(it)) }?.title ?: seen.title
        // 편 때는 한 번만 정해 회차 시작일과 편 책 기록에 같이 쓴다(자정에 걸쳐도 같은 날)
        val openedAt = at ?: System.currentTimeMillis()
        var result = WatchLog.Opened.FRONT
        val book = (if (exact) id?.let { BookShelf.get(context, it) } else BookShelf.findByTitle(context, title))?.also {
            BookShelf.moveToFront(context, it.id)
            BookShelf.updateStatus(context, it.id, seen.progress, seen.due, seen.author, seen.chapter)
        } ?: run {
            // 완독한 책도 펴면 들어온다(새 회차가 그날 시작, [BookShelf.addOpened]). 파일로만 여는 이북 앱(네오리더)은 그 항목([AppStore.fileReaders])으로
            val app = AppStore.readerKey(context, pkg)
            result = WatchLog.Opened.NEW
            // 앱이 정한 책은 그 책(나간 책)만 되살린다(같은 제목의 다른 파일을 되살리지 않게)
            if (exact) BookShelf.addOpened(context, title, seen.author, app, seen.progress, seen.due, openedAt, seen.chapter) { it.id == id }
            else BookShelf.addOpened(context, title, seen.author, app, seen.progress, seen.due, openedAt, seen.chapter)
        }
        steps?.found(book)
        WatchLog.add(context, WatchLog.Kind.OPENED, pkg, title, result.name)
        // 편 책 기록에는 앱이 보여 준 제목을 적는다(제목을 고친 책이어도. 지운 뒤 그 시간을 다시 정할 때 같은 제목의 새 책을 찾게)
        ReadingLog.bookOpened(context, pkg, title, book.id, openedAt)
        steps?.logged(book)
        // 미리 잘라 둔 표지(검색 화면의 `바로 읽기`, 서재). 앱이 정한 책은 그 열쇠의 것만
        (if (exact) coverKey?.takeIf { it in pendingCovers } else pendingFor(book.title))?.let { key ->
            val bytes = pendingCovers.remove(key) ?: return@let
            if (coverFromFile(context, BookShelf.filePath(context, book.id), BookShelf.fileCovers(context, book.id))) return@let
            cover(book.id, bytes)
        }
        return book
    }

    override fun unknown(pkg: String, at: Long) = ReadingLog.bookOpened(context, pkg, "", "", at)
    override fun status(bookId: String, progress: Int, due: Long, author: String, chapter: String) =
        BookShelf.updateStatus(context, bookId, progress, due, author, chapter)
    override fun statuses(list: List<BookShelf.Status>) = BookShelf.updateStatuses(context, list)
    override fun setFile(bookId: String, path: String) = BookShelf.setFilePath(context, bookId, path)
    override fun setTitle(bookId: String, title: String) = BookShelf.setTitle(context, bookId, title)
    override fun setAuthor(bookId: String, author: String) = BookShelf.setAuthor(context, bookId, author)

    override fun cover(bookId: String, jpeg: ByteArray) {
        if (!BookShelf.hasCover(context, bookId)) runCatching { BookShelf.setCover(context, bookId, jpeg) }
    }

    override fun replaceCover(bookId: String, jpeg: ByteArray) {
        runCatching { BookShelf.setCover(context, bookId, jpeg) }
    }

    override fun dropPlaceholderCover(bookId: String, placeholder: (Bitmap) -> Boolean) =
        runCatching { BookShelf.dropCoverIf(context, bookId, placeholder) }.getOrDefault(false)

    override fun pendingCover(key: String, jpeg: ByteArray) { pendingCovers[key] = jpeg }
    override fun hasPendingCover(key: String) = key in pendingCovers
    override fun hasPendingTitle(title: String) = pendingFor(title) != null

    /** 잘라 둔 표지 가운데 [title] 의 것(부제·공백 무시)의 열쇠 */
    private fun pendingFor(title: String): String? {
        val n = normTitle(title)
        return pendingCovers.keys.firstOrNull { sameTitle(it, n) }
    }

    override fun viewerShown(pkg: String, shown: Boolean, at: Long, current: Boolean) = ReadingLog.viewerShown(context, pkg, shown, at, current)
    override fun openedId(pkg: String) = ReadingLog.openBook(context, pkg)
    override fun recordedBook(pkg: String) = ReadingLog.openBook(context, pkg)?.let { BookShelf.get(context, it) }

    override fun byTitle(title: String) = BookShelf.findByTitle(context, title)
    override fun get(id: String) = BookShelf.get(context, id)
    override fun bookOf(id: String) = BookShelf.bookOf(context, setOf(id))
    override fun byFile(path: String) = BookShelf.idByFile(context, path)

    override fun byPath(path: String): String? =
        if (isImageFile(path)) File(path).parent?.let { BookShelf.idByFolder(context, it) } else BookShelf.idByFile(context, path)

    override fun withoutFile(title: String): String? = BookShelf.idByTitle(context, title) { BookShelf.filePath(context, it.id) == null }

    override fun knownTitle(path: String, title: String): String {
        BookShelf.titleByFile(context, path)?.let { return it }
        val fileTitle = withoutBookFile(File(path).name)
        return withoutFile(fileTitle)?.let { BookShelf.bookOf(context, setOf(it))?.title } ?: title
    }

    override fun idsByFileName(name: String) = BookShelf.idsByFileName(context, name)
    override fun filePath(id: String) = BookShelf.filePath(context, id)
    override fun hasCover(id: String) = BookShelf.hasCover(context, id)

    companion object {
        /**
         * 책 파일 [path] 에서 그 책을 넣은 앱의 길 [covers]([com.woody.booklauncher.readers.ReaderApp.fileCovers])로 표지를 꺼낼 수 있다([BookShelf.cover]):
         * 화면에서 자른 작은 표지를 두지 않는다. 꺼낼 수 없으면(표지가 없는 EPUB 등) 아니다(그 책은 화면에서 자른다)
         */
        fun coverFromFile(context: Context, path: String?, covers: BookFiles.Covers): Boolean =
            path != null && BookFiles.canRead(context) && covers.has(context, path)
    }
}
