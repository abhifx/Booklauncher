package com.woody.booklauncher.readers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import com.woody.booklauncher.*
import com.woody.booklauncher.Target
import com.woody.booklauncher.Db.rows
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowAccessibilityNodeInfo
import org.robolectric.shadows.ShadowContentResolver

/** 기기 공개 Provider의 응답을 재현한다. 실제 기기에서의 API 가용성은 별도 확인이 필요하다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [IReaderTest.Nodes::class])
class IReaderTest {
    /** Robolectric 에는 요소 이름으로 찾기가 없어 안드로이드처럼 나무를 훑어 찾는다. */
    @Implements(AccessibilityNodeInfo::class)
    class Nodes : ShadowAccessibilityNodeInfo() {
        @RealObject lateinit var node: AccessibilityNodeInfo
        @Implementation
        protected fun findAccessibilityNodeInfosByViewId(id: String): List<AccessibilityNodeInfo> = buildList {
            fun walk(n: AccessibilityNodeInfo) {
                if (n.viewIdResourceName == id) add(n)
                for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)
            }
            walk(node)
        }
    }
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: BooksProvider

    class BooksProvider : ContentProvider() {
        var data: Bundle? = null
        var denied = false
        var coverCalls = 0
        var coverFile: File? = null
        var onCover: () -> Unit = {}
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            if (denied) throw SecurityException("Provider unavailable")
            return when (method) {
                "bookWallpaperInfo" -> data
                "getBookCoverWallpaper" -> {
                    coverCalls++
                    onCover()
                    coverFile?.let { Bundle().apply {
                        putString("EXTRA_BOOK_COVER_URI", "content://${IReader.pkg}.SHARE_FILE/cover")
                    } }
                }
                else -> error("Unexpected method: $method")
            }
        }
        override fun onCreate() = true
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = coverFile?.let {
            ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        var recordId: String? = "/books/current.epub"
        var encrypted = false
        var recordActivity = IReader.viewers.first()
        var recordProcess = IReader.pkg
        var secondRecord: String? = null
        var duplicateRow = false
        var onRecent: () -> Unit = {}
        /** 있으면 최근 책 줄을 [data] 대신 이것으로(읽은 때가 늦은 순으로 넣는다) */
        var rows: List<Map<String, Any>>? = null
        /** 진행률 칸이 없는 판: 그 칸을 물으면 쿼리가 실패한다 */
        var noPercent = false
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            if (denied) throw SecurityException("Provider unavailable")
            if (uri.authority == "test.reader.records") return MatrixCursor(arrayOf("k_bi", "k_an", "k_pn")).apply {
                recordId?.let { addRow(arrayOf(it, recordActivity, recordProcess)) }
                secondRecord?.let { addRow(arrayOf(it, recordActivity, recordProcess)) }
            }
            onRecent()
            if (noPercent && projection?.contains("readpercent") == true) throw IllegalArgumentException("no such column: readpercent")
            val columns = projection ?: IReader.COLUMNS
            rows?.let { list -> return MatrixCursor(columns).apply { list.forEach { v -> addRow(columns.map { v[it] }.toTypedArray()) } } }
            return MatrixCursor(columns).apply {
                data?.let { b ->
                    val values = mapOf<String, Any>("id" to 42, "name" to b.getString("EXTRA_BOOK_NAME").orEmpty(),
                        "author" to b.getString("EXTRA_BOOK_AUTHOR").orEmpty(), "path" to "/books/current.epub", "bookid" to "remote-42",
                        "readlasttime" to b.getLong("EXTRA_BOOK_READ_TIME"), "is_encrypt" to if (encrypted) 1 else 0, "isnotebook" to 0, "type" to 1)
                    addRow(columns.map { values[it] }.toTypedArray())
                    if (duplicateRow) addRow(columns.map { values[it] }.toTypedArray())
                }
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    @Before fun setup() {
        // 책 파일 경로를 적으면 표지를 파일에서 꺼낼지 권한을 본다(Robolectric 은 외부 저장소가 비어 있어 권한을 볼 수 없다)
        org.robolectric.shadows.ShadowEnvironment.addExternalDir("sdcard")
        provider = BooksProvider().apply {
            attachInfo(context, ProviderInfo().apply { authority = IReader.DATA.authority })
        }
        ShadowContentResolver.registerProviderInternal(IReader.DATA.authority, provider)
        ShadowContentResolver.registerProviderInternal("${IReader.pkg}.SHARE_FILE", provider)
        ShadowContentResolver.registerProviderInternal(IReader.RECENT.authority, provider)
        ShadowContentResolver.registerProviderInternal("test.reader.records", provider)
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(IReader.RECORD_ACTION), ResolveInfo().apply {
            providerInfo = ProviderInfo().apply { authority = "test.reader.records"; exported = true }
        })
    }

    @After fun cleanup() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(context).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun book(title: String = "테스트 책", readAt: Long = 100_000L) = Bundle().apply {
        putString("EXTRA_BOOK_NAME", title)
        putString("EXTRA_BOOK_AUTHOR", "테스트 저자")
        putLong("EXTRA_BOOK_READ_TIME", readAt)
    }

    /** 실제 WatchCore의 알림·700ms 예약·백그라운드 응답 순서를 손으로 진행한다. */
    private inner class Harness {
        val clock = Clock()
        val recorder = ShelfRecorder(context)
        val host = Host()
        val core = WatchCore(host, clock, recorder, listOf(IReader))
        val opened get() = recorder.recordedBook(IReader.pkg)
        fun enter(name: String = screen) = core.onEvent(IReader.pkg, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, name, null)
        fun changed() = core.onEvent(IReader.pkg, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, null, null)
        fun settle() { clock.advance(1000); host.finish() }
        inner class Host : WatchHost {
            override val context get() = this@IReaderTest.context
            var front: AccessibilityNodeInfo? = window("10%")
            val jobs = ArrayDeque<() -> Unit>()
            override fun root(pkg: String) = front?.takeIf { it.packageName == pkg }
            override fun windows(pkg: String) = AppWindows(root(pkg), listOfNotNull(root(pkg)))
            override val noActiveWindow get() = front == null
            override fun isActivity(pkg: String, cls: String) = true
            override val canScreenshot = false
            override fun screenshot(done: (Bitmap) -> Unit) = error("No screenshot needed")
            override fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit) { jobs += { done(runCatching(work)) } }
            fun finish() { while (jobs.isNotEmpty()) jobs.removeFirst()() }
        }
    }

    private class Clock : WatchClock {
        var time = 100_000L
        private val jobs = mutableListOf<Pair<Long, Runnable>>()
        override fun now() = time
        override fun post(r: Runnable, delayMs: Long) { jobs += time + delayMs to r }
        override fun remove(r: Runnable) { jobs.removeAll { it.second === r } }
        fun advance(ms: Long) {
            val end = time + ms
            while (true) {
                val next = jobs.filter { it.first <= end }.minByOrNull { it.first } ?: break
                jobs.remove(next)
                time = next.first
                next.second.run()
            }
            time = end
        }
    }

    private val screen = IReader.viewers.first()

    private fun window(progress: String): AccessibilityNodeInfo {
        fun node(id: String? = null, text: String? = null) = AccessibilityNodeInfo.obtain().apply {
            packageName = IReader.pkg
            className = "android.widget.TextView"
            id?.let { viewIdResourceName = "${IReader.pkg}:id/$it" }
            this.text = text
        }
        val root = node()
        shadowOf(root).addChild(node("Id_read_menu_progress_ui", progress))
        fun seal(n: AccessibilityNodeInfo) {
            for (i in 0 until n.childCount) seal(n.getChild(i))
            AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        }
        seal(root)
        return root
    }

    @Test fun `책을 열기만 해도 현재 프로세스의 책과 진행률을 추가한다`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.clock.advance(700)
        assertTrue(h.host.jobs.isEmpty())
        h.clock.advance(300)
        h.host.finish()
        assertEquals("테스트 책", h.opened?.title)
        assertEquals("테스트 저자", h.opened?.author)
        assertEquals(10, h.opened?.progress)
    }

    @Test fun `읽은 때가 가까운 앞 책이라도 현재 책 식별자가 다르면 기록하지 않는다`() {
        provider.data = book("앞 책", 99_900)
        provider.recordId = "/books/encrypted.epub"
        val h = Harness()
        h.enter()
        h.settle()
        assertNull(h.opened)
    }

    @Test fun `현재 책 기록이 없으면 최근 책만으로 추정하지 않는다`() {
        provider.data = book()
        provider.recordId = null
        val h = Harness()
        h.enter()
        h.settle()
        assertNull(h.opened)
    }

    @Test fun `다른 프로세스의 책 기록은 사용하지 않는다`() {
        provider.data = book()
        provider.recordProcess = "${IReader.pkg}:read1"
        val h = Harness()
        h.enter()
        h.settle()
        assertNull(h.opened)
    }

    @Test fun `현재 프로세스 식별자가 여러 개면 보류한다`() {
        provider.data = book()
        provider.secondRecord = "/books/other.epub"
        val h = Harness()
        h.enter()
        h.settle()
        assertNull(h.opened)
    }

    @Test fun `식별자에 여러 책 행이 맞으면 보류한다`() {
        provider.data = book()
        provider.duplicateRow = true
        val h = Harness()
        h.enter()
        h.settle()
        assertNull(h.opened)
    }

    @Test fun `이미 열려 있던 책도 식별자가 맞으면 시각으로 거절하지 않는다`() {
        provider.data = book(readAt = 50_000)
        val h = Harness()
        h.enter()
        h.settle()
        assertEquals("테스트 책", h.opened?.title)
    }

    @Test fun `같은 제목이어도 암호화된 행은 기록하지 않는다`() {
        provider.data = book()
        provider.encrypted = true
        val h = Harness()
        h.enter()
        h.settle()
        assertNull(h.opened)
    }

    @Test fun `식별자 조회 중 현재 책이 바뀌면 응답을 버린다`() {
        provider.data = book()
        provider.onRecent = { provider.recordId = "/books/next.epub" }
        val h = Harness()
        h.enter()
        h.settle()
        assertNull(h.opened)
    }

    @Test fun `감시하지 않는 앱으로 떠나도 늦은 응답을 기록하지 않는다`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.clock.advance(1000)
        h.host.front = null
        h.core.onEvent("other.app", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "OtherActivity", null)
        h.host.finish()
        assertNull(h.opened)
    }

    @Test fun `홈으로 돌아가면 진행 중 요청을 무효화한다`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.clock.advance(1000)
        h.core.home()
        h.host.finish()
        assertNull(h.opened)
    }

    @Test fun `1초 제한 안의 마지막 진행률도 예약해 기록한다`() {
        provider.data = book()
        provider.coverFile = coverFile()
        val h = Harness()
        h.enter()
        h.settle()
        h.clock.advance(100)
        h.host.front = window("20%")
        h.changed()
        h.clock.advance(700)
        h.host.finish()
        assertEquals(10, h.opened?.progress)
        h.clock.advance(200)
        h.host.finish()
        assertEquals(20, h.opened?.progress)
    }

    @Test fun `요청 중 화면이 바뀌면 그 답을 버리고 다시 물어 적는다`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.clock.advance(1000)
        h.host.front = window("35%")
        h.changed()
        h.clock.advance(700)
        assertEquals(1, h.host.jobs.size)
        h.host.finish()
        assertNull(h.opened)
        h.clock.advance(1000)
        assertTrue(h.host.jobs.isNotEmpty())
        h.host.finish()
        assertEquals(35, h.opened?.progress)
    }

    @Test fun `실패한 표지는 재시도해서 저장한다`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.settle()
        assertEquals(1, provider.coverCalls)
        provider.coverFile = coverFile()
        h.clock.advance(1000)
        h.host.finish()
        assertEquals(2, provider.coverCalls)
        assertTrue(h.recorder.lookup.hasCover(h.opened!!.id))
    }

    @Test fun `표지 실패 재시도는 세 번으로 제한한다`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.settle()
        repeat(5) { h.clock.advance(1000); h.host.finish() }
        assertEquals(3, provider.coverCalls)
    }

    @Test fun `Provider 접근 거부는 책을 추가하지 않는다`() {
        provider.denied = true
        val h = Harness()
        h.enter()
        h.settle()
        assertNull(h.opened)
    }

    @Test fun `표지 요청 전에 최근 책이 바뀌면 표지를 요청하지 않는다`() {
        provider.data = book("다른 책")
        assertNull(IReader.cover(context, IReader.Book("테스트 책", "테스트 저자", 100_000L)) { IReader.book(context, screen) })
        assertEquals(0, provider.coverCalls)
    }

    private fun coverFile(): File = File(context.cacheDir, "ireader-cover.png").apply {
        val bitmap = Bitmap.createBitmap(60, 90, Bitmap.Config.ARGB_8888)
        outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        shadowOf(context.contentResolver).registerInputStreamSupplier(Uri.parse("content://${IReader.pkg}.SHARE_FILE/cover")) { inputStream() }
    }

    @Test fun `권한을 받은 content URI의 표지를 JPEG로 가져온다`() {
        provider.data = book()
        provider.coverFile = coverFile()
        val jpeg = IReader.cover(context, IReader.book(context, screen)!!) { IReader.book(context, screen) }
        assertNotNull(jpeg)
        val image = BitmapFactory.decodeByteArray(jpeg, 0, jpeg!!.size)
        assertEquals(60, image.width)
        assertEquals(90, image.height)
        image.recycle()
    }

    @Test fun `표지 요청 중 최근 책이 바뀌면 다른 책의 표지를 버린다`() {
        provider.data = book()
        provider.coverFile = coverFile()
        provider.onCover = { provider.data = book("다른 책") }
        assertNull(IReader.cover(context, IReader.book(context, screen)!!) { IReader.book(context, screen) })
    }

    /** iReader 의 편 책 기록 줄 수 */
    private fun opens() = Db.get(context).rows("SELECT COUNT(*) FROM book_open WHERE pkg = '${IReader.pkg}'") { it.getInt(0) }.single()

    @Test fun `같은 책으로 다시 들어오면 편 책 기록이 늘지 않는다`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.settle()
        val first = h.opened!!.id
        val rows = opens()
        h.core.home()
        h.enter()
        h.settle()
        assertEquals(first, h.opened?.id)
        assertEquals(rows, opens())
    }

    @Test fun `끝내 확인하지 못하면 들어온 때부터 책 모름`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.settle()
        assertNotNull(h.opened)
        h.core.home()
        provider.recordId = null
        h.enter()
        val entered = h.clock.time
        repeat(6) { h.settle() }
        assertNull(h.opened)
        assertNull(ReadingLog.openBook(context, IReader.pkg, entered))
    }

    @Test fun `확인하기 전에 홈으로 가면 들어온 때부터 책 모름`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.settle()
        h.core.home()
        h.enter()
        val entered = h.clock.time
        h.clock.advance(500)
        h.core.home()
        assertNull(h.opened)
        assertNull(ReadingLog.openBook(context, IReader.pkg, entered))
    }

    @Test fun `다른 창이 잠깐 앞에 와도 들어온 때를 지우지 않는다`() {
        provider.data = book()
        val h = Harness()
        h.enter()
        h.settle()
        val rows = opens()
        h.core.home()
        h.enter()
        val shown = h.host.front
        h.host.front = null
        h.clock.advance(1000)
        h.host.front = shown
        h.changed()
        h.settle()
        assertNotNull(h.opened)
        assertEquals(rows, opens())
    }

    @Test fun `확인한 뒤 읽은 때가 바뀌어도 같은 책이면 표지를 가져온다`() {
        provider.data = book(readAt = 100_000)
        provider.coverFile = coverFile()
        val expected = IReader.book(context, screen)!!
        provider.data = book(readAt = 200_000)
        assertNotNull(IReader.cover(context, expected) { IReader.book(context, screen) })
    }

    @Test fun `빈 제목과 이름 없는 응답은 책이 아니다`() {
        assertNull(IReader.metadata(null))
        assertNull(IReader.metadata(Bundle()))
        assertNull(IReader.metadata(book("   ")))
    }

    @Test fun `책을 알아내지 못하면 단계별 진단을 들어올 때마다 한 번 남긴다`() {
        WatchLog.clear(context)
        provider.data = book()
        provider.recordProcess = "${IReader.pkg}:read1"
        val h = Harness()
        h.enter()
        repeat(3) { h.settle() }
        val notes = notes().filter { it.startsWith("rec") || it.startsWith("want") }
        assertEquals(listOf("rec 1 test.reader.records", "rec rows 1 cols k_bi,k_an,k_pn", "rec an=ActivityRead pn=:read1 bi=…oks/current.epub",
            "want an=ActivityRead pn=main", "recent rows 1", "recent id=42 enc=0 note=0 type=1 at=+0s name=O", "recent path=…oks/current.epub bid=…emote-42"), notes)
        assertNull(h.opened)
    }

    /** 펌웨어 기록 Provider 가 없는 기기(Ocean 5 Pro 4.1.0.215.0) */
    private fun noFirmware() = shadowOf(context.packageManager).setResolveInfosForIntent(Intent(IReader.RECORD_ACTION), emptyList())

    private fun row(id: Int, name: String, readAt: Long, encrypted: Boolean = false) = mapOf<String, Any>(
        "id" to id, "name" to name, "author" to "저자 $id", "path" to "/books/$id.epub", "bookid" to "b$id",
        "readlasttime" to readAt, "is_encrypt" to if (encrypted) 1 else 0, "isnotebook" to 0, "type" to 1)

    @Test fun `펌웨어 기록이 없으면 들어온 뒤 연 맨 위 책을 들어온 때부터 넣는다`() {
        noFirmware()
        provider.rows = listOf(row(1, "새 책", 100_500), row(2, "앞 책", 90_000))
        val h = Harness()
        h.enter()
        val entered = h.clock.time
        h.settle()
        assertEquals("새 책", h.opened?.title)
        assertEquals("저자 1", h.opened?.author)
        assertEquals(h.opened?.id, ReadingLog.openBook(context, IReader.pkg, entered))
    }

    @Test fun `이어 읽기는 잠깐 기다린 뒤 맨 위 책으로 들어온 때부터`() {
        noFirmware()
        provider.rows = listOf(row(1, "읽던 책", 50_000))
        val h = Harness()
        h.enter()
        val entered = h.clock.time
        h.settle()
        assertNull(h.opened)
        h.clock.advance(2000)
        h.host.finish()
        assertEquals("읽던 책", h.opened?.title)
        assertEquals(h.opened?.id, ReadingLog.openBook(context, IReader.pkg, entered))
    }

    @Test fun `새 책이 늦게 열리면 앞 책이 아니라 그 책으로`() {
        noFirmware()
        provider.rows = listOf(row(2, "앞 책", 90_000))
        val h = Harness()
        h.enter()
        val entered = h.clock.time
        h.settle()
        provider.rows = listOf(row(1, "새 책", 101_500), row(2, "앞 책", 90_000))
        h.clock.advance(2000)
        h.host.finish()
        assertEquals("새 책", h.opened?.title)
        assertEquals(h.opened?.id, ReadingLog.openBook(context, IReader.pkg, entered))
    }

    @Test fun `읽는 중 가져온 책이 맨 위로 와도 바꾸지 않는다`() {
        noFirmware()
        provider.rows = listOf(row(1, "읽는 책", 100_500))
        val h = Harness()
        h.enter()
        h.settle()
        assertEquals("읽는 책", h.opened?.title)
        provider.rows = listOf(row(3, "가져온 책", 102_000), row(1, "읽는 책", 100_500))
        h.changed()
        h.settle()
        assertEquals("읽는 책", h.opened?.title)
    }

    @Test fun `읽던 책을 닫고 다른 책을 열면 그 책으로 바꾼다`() {
        noFirmware()
        provider.rows = listOf(row(1, "읽는 책", 100_500))
        val h = Harness()
        h.enter()
        h.settle()
        provider.rows = listOf(row(4, "다음 책", 103_000), row(1, "읽는 책", 102_900))
        h.changed()
        h.settle()
        assertEquals("다음 책", h.opened?.title)
    }

    @Test fun `두 번째 읽는 화면은 맨 위 책으로 정하지 않는다`() {
        noFirmware()
        provider.rows = listOf(row(1, "책", 100_500))
        val h = Harness()
        h.enter(IReader.SECOND_VIEWER)
        repeat(6) { h.settle() }
        assertNull(h.opened)
    }

    @Test fun `맨 위 줄이 잠근 책이면 넣지 않는다`() {
        noFirmware()
        provider.rows = listOf(row(1, "잠근 책", 100_500, encrypted = true), row(2, "다른 책", 90_000))
        val h = Harness()
        h.enter()
        repeat(6) { h.settle() }
        assertNull(h.opened)
    }

    @Test fun `펌웨어 기록이 없어도 맨 위 책의 표지를 가져온다`() {
        noFirmware()
        provider.rows = listOf(row(1, "새 책", 100_500))
        provider.data = Bundle().apply {
            putString("EXTRA_BOOK_NAME", "새 책"); putString("EXTRA_BOOK_AUTHOR", "저자 1"); putLong("EXTRA_BOOK_READ_TIME", 100_500)
        }
        provider.coverFile = coverFile()
        val h = Harness()
        h.enter()
        h.settle()
        assertTrue(h.recorder.lookup.hasCover(h.opened!!.id))
    }

    /** 기록 보기의 iReader 진단 줄(보이는 차례, 새 줄이 위) */
    private fun notes() = WatchLog.entries(context).filter { it.kind == WatchLog.Kind.NOTE && it.pkg == IReader.pkg }.map { it.arg }

    @Test fun `정한 규칙과 최근 책 줄·표지 결과를 기록 보기에 남긴다`() {
        WatchLog.clear(context)
        noFirmware()
        provider.rows = listOf(row(1, "새 책", 102_000), row(2, "앞 책", 90_000, encrypted = true))
        provider.data = Bundle().apply {
            putString("EXTRA_BOOK_NAME", "새 책"); putString("EXTRA_BOOK_AUTHOR", "저자 1"); putLong("EXTRA_BOOK_READ_TIME", 102_000)
        }
        provider.coverFile = coverFile()
        val h = Harness()
        h.enter()
        h.settle()
        assertEquals(listOf("cover ok", "pick open", "rows 1.epub@+2s 2.epub@-10sx", "enter new"), notes())
    }

    @Test fun `읽는 중 가져온 책으로 바꾸지 않은 것도 남긴다`() {
        WatchLog.clear(context)
        noFirmware()
        provider.rows = listOf(row(1, "읽는 책", 100_500))
        val h = Harness()
        h.enter()
        h.settle()
        provider.rows = listOf(row(3, "가져온 책", 102_000), row(1, "읽는 책", 100_500))
        h.changed()
        h.settle()
        assertTrue("keep (top changed, not closed)" in notes())
    }

    @Test fun `이어 읽기로 정한 뒤 늦게 열린 책이 맨 위로 오면 그 책으로`() {
        noFirmware()
        provider.rows = listOf(row(2, "앞 책", 90_000))
        val h = Harness()
        h.enter()
        h.settle()
        h.clock.advance(2000)
        h.host.finish()
        assertEquals("앞 책", h.opened?.title)
        provider.rows = listOf(row(1, "늦게 연 책", 104_000), row(2, "앞 책", 90_000))
        h.changed()
        h.settle()
        assertEquals("늦게 연 책", h.opened?.title)
    }

    @Test fun `이어 읽는 동안 한참 뒤에 가져온 책으로는 바꾸지 않는다`() {
        noFirmware()
        provider.rows = listOf(row(2, "읽던 책", 90_000))
        val h = Harness()
        h.enter()
        h.settle()
        h.clock.advance(2000)
        h.host.finish()
        assertEquals("읽던 책", h.opened?.title)
        h.clock.advance(120_000)
        provider.rows = listOf(row(3, "가져온 책", h.clock.time), row(2, "읽던 책", 90_000))
        h.changed()
        h.settle()
        assertEquals("읽던 책", h.opened?.title)
    }

    private fun home() = HomeContext(context, android.content.ComponentName(IReader.pkg, "com.zhangyue.iReader.Eink.Activity.alias.ActivityAliasBookStore"))

    @Test fun `홈에서 누르면 적어 둔 그 책의 파일로 연다(없어졌으면 서재)`() {
        noFirmware()
        val file = File(context.cacheDir, "새 책.epub").apply { writeText("epub") }
        provider.rows = listOf(row(1, "새 책", 100_500) + ("path" to file.path))
        val h = Harness()
        h.enter()
        h.settle()
        val book = h.opened!!
        assertEquals(file.path, BookShelf.filePath(context, book.id))
        val open = (IReader.open(home(), book) as OpenResult.Start).intent
        assertEquals(IReader.OPEN_BOOK, open.component?.className)
        assertEquals(file.path, open.getStringExtra("file"))
        file.delete()
        assertEquals(IReader.SHELF, (IReader.open(home(), book) as OpenResult.Start).intent.component?.className)
    }

    @Test fun `파일을 모르는 책은 서재를 연다`() {
        val book = ShelfRecorder(context).opened(IReader.pkg, Seen("파일을 모르는 책"), null, Target.ByTitle)
        assertEquals(IReader.SHELF, (IReader.open(home(), book) as OpenResult.Start).intent.component?.className)
    }

    @Test fun `메뉴에 진행률이 없으면 iReader 가 저장한 진행률, 홈에 오면 다시 읽는다`() {
        noFirmware()
        provider.rows = listOf(row(1, "책", 100_500) + ("readpercent" to 0.25f))
        val h = Harness()
        h.host.front = window("")
        h.enter()
        h.settle()
        assertEquals(25, h.opened?.progress)
        provider.rows = listOf(row(1, "책", 102_000) + ("readpercent" to 0.5f))
        h.core.home()
        h.host.finish()
        assertEquals(50, h.opened?.progress)
    }

    @Test fun `진행률 칸이 없는 판이어도 책은 정한다`() {
        noFirmware()
        provider.noPercent = true
        provider.rows = listOf(row(1, "새 책", 100_500))
        val h = Harness()
        h.host.front = window("")
        h.enter()
        h.settle()
        assertEquals("새 책", h.opened?.title)
        assertEquals(-1, h.opened?.progress)
    }
}
