package com.woody.booklauncher.readers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.normTitle
import com.woody.booklauncher.sameTitle
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.math.abs

/**
 * Mihon(만화 앱, 0.21.2)과 그 포크 Kahon(0.20.6, 패키지만 다르고 화면이 같다): 책 한 권은 만화 한 작품이다(화마다 따로 책을 만들지 않는다).
 * 서재·찾아보기·기록·작품 상세가 모두 `ui.main.MainActivity` 하나의 Compose 화면이라 요소 이름이 없고, 눌러도 알림이 없다.
 * 작품은 서재 칸을 누르면 뜨는 작품 상세(표지([MihonApp.isCover]) 바로 뒤에 제목, 그 뒤 줄에 저자)에서 안다: 상세에서 화를 누르거나 `시작`·`이어 읽기`로
 * 읽는 화면(`ui.reader.ReaderActivity`)을 열면 그 작품([DetailReader]). 기록·업데이트 탭에서 바로 연 화는 책 모름으로 두고, 읽는 화면 가운데를 눌러
 * 메뉴를 띄우면 위 줄의 작품 제목으로 알아 읽는 화면을 연 때부터 그 작품에 붙인다([MihonReader]).
 * 표지는 서재 격자 칸(표지 위에 그린 제목·표시도 함께)·기록 탭 줄·작품 상세에서 자르고(표지를 불러오기 전·못 불러온 빈 표지는 자르지 않는다, [placeholderCover]),
 * 책 파일 읽기로 로컬 만화는 작품 폴더의 표지 그림으로 바꾼다.
 * 저자는 화면에서는 작품 상세에만 있고, 책 파일 읽기로 로컬 만화 정보·받은 화·자동 백업에서도 찾는다([fileInfo]). 작품의 진행률(%)은 모르고(읽는 화면에는 그 화의 쪽만 있다)
 * 메뉴의 화 이름을 앱이 쓴 그대로 읽는 화로 둔다(`Ch. 12`·`12화`·`Vol.2 Ch.15 - …`, 소스마다 다르다).
 */
open class MihonApp(pkg: String) : ReaderApp(
    pkg = pkg, classPrefix = "eu.kanade.tachiyomi",
    shelves = setOf("eu.kanade.tachiyomi.ui.main.MainActivity"),
    viewers = setOf("eu.kanade.tachiyomi.ui.reader.ReaderActivity"),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(),
    // 로컬 만화의 표지 그림, 로컬 만화 정보·받은 화·자동 백업의 저자([fileInfo]). 온라인으로만 읽으면 쓸모가 적지만 시작하기·홈에서 묻는다(사용자가 정함)
    needsBookFiles = true,
) {
    override fun newAdapter(): ReaderAdapter = MihonReader(this)

    /** 메뉴를 띄우면 위 줄에 작품 제목이 보인다 */
    override val tellsTitle get() = true

    /** 기록 탭 줄·서재 칸은 이름 없이 자리로 찾아, 찍은 뒤에도 같은 자리에 있을 때만 자른다 */
    override val cellsByPosition get() = true

    /**
     * 서재 칸: 기록 탭의 줄([historyRow])과 서재 격자 칸([gridCell]). 표지를 그대로(2:3) 그리는 곳은 이 둘과 작품 상세뿐이다
     * (소스 `HistoryItem`·`MangaComfortableGridItem` 등·`MangaInfoHeader`. 업데이트 탭·서재 목록 보기는 표지를 정사각형으로 잘라 그린다).
     * 화면에서 저자는 작품 상세에만 있다.
     */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        val found = mutableListOf<Cell>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            (historyRow(n, root) ?: gridCell(n, root))?.let { found += it; return }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return found
    }

    /**
     * 기록 탭 줄(누를 수 있는 묶음): 설명 없는 표지, 눌리지 않는 제목, 화·때(`2화 - 오전 4:22`)가 차례로. 위 막대·아래 탭 줄에 걸린 표지는 자르지 않는다
     * (작품 상세의 표지 단추에는 설명 `표지` 가 있고 제목이 눌린다)
     */
    private fun historyRow(row: AccessibilityNodeInfo, root: AccessibilityNodeInfo): Cell? {
        if (!row.isClickable) return null
        val kids = (0 until row.childCount).mapNotNull { row.getChild(it) }
        // Mihon 은 표지 단추, Kahon 은 그림·단추를 품은 묶음. 어느 쪽이든 설명이 없다(작품 상세의 표지는 그것이나 그 안에 설명 `표지`)
        val cover = kids.firstOrNull()?.takeIf { c ->
            c.isClickable && c.text.isNullOrEmpty() && c.contentDescription.isNullOrEmpty() &&
                (0 until c.childCount).none { i -> c.getChild(i)?.contentDescription.isNullOrEmpty() == false }
        } ?: return null
        val at = cover.bounds().takeIf(::usableThumb) ?: return null
        val title = kids.getOrNull(1)?.takeIf { it.className?.endsWith("TextView") == true && !it.isClickable }?.text?.let(::plain)?.takeIf { it.isNotEmpty() }
            ?: return null
        if (kids.getOrNull(2)?.className?.endsWith("TextView") != true) return null
        return Cell(title, "", -1, -1, at.takeUnless { drawnOver(root, cover, at) }, row.bounds())
    }

    /**
     * 작품 상세: 표지([isCover]) 바로 뒤의 눌리는 글자가 제목, 그 뒤 묶음의 첫 글자가 저자(모르면 앱이 `작가 미상`을 보여
     * [MihonReader] 가 거른다). 기록 탭의 칸도 표지 단추 뒤에 제목이 오지만 그 단추에는 설명이 없고 제목이 눌리지 않는다.
     * 상세를 위로 넘기면 표지가 위 막대 밑으로 들어가는데 표지 자리는 그대로 오므로, 표지보다 뒤에 그리는 요소가 표지와 겹치면 표지를 자르지 않는다.
     */
    fun detail(root: AccessibilityNodeInfo): Cell? {
        fun walk(n: AccessibilityNodeInfo, depth: Int): Pair<Cell, AccessibilityNodeInfo>? =
            header(n) ?: if (depth < DEEP_WALK_DEPTH) (0 until n.childCount).firstNotNullOfOrNull { i -> n.getChild(i)?.let { walk(it, depth + 1) } } else null
        val (cell, cover) = walk(root, 0) ?: return null
        return if (drawnOver(root, cover, cover.bounds())) cell.copy(thumb = null) else cell
    }

    /** [holder] 의 자식 가운데 표지 단추·제목·저자 줄이 이어지면 그 작품과 표지 단추 */
    private fun header(holder: AccessibilityNodeInfo): Pair<Cell, AccessibilityNodeInfo>? {
        val kids = (0 until holder.childCount).mapNotNull { holder.getChild(it) }
        for ((i, k) in kids.withIndex()) {
            if (!isCover(k)) continue
            val name = kids.getOrNull(i + 1)?.takeIf { it.isClickable && it.className?.endsWith("TextView") == true }
            val title = name?.text?.let(::plain)?.takeIf { it.isNotEmpty() } ?: continue
            val row = kids.getOrNull(i + 2)
            val author = row?.let { r -> (0 until r.childCount).firstNotNullOfOrNull { j -> r.getChild(j)?.text?.let(::plain)?.takeIf { it.isNotEmpty() } } }
            return Cell(title, author.orEmpty(), -1, -1, k.bounds(), Rect()) to k
        }
        return null
    }

    /**
     * 위 막대의 제목: 뒤로 단추(설명이 붙은 요소를 품은 눌리는 묶음) 바로 뒤 묶음 안의 글자. 작품 상세를 아래로 넘기면 머리(표지·제목·저자)는 화면 밖에서
     * 요소가 없어지고(화가 많은 작품, 2026-10-26 에뮬레이터 Mihon 0.21.3: 120화) 위 막대에 작품 제목이 나온다. 찾아보기의 소스 화면 등도 같은 모양이라
     * 이것만으로 작품 상세라고 보지 않는다([MihonReader] 가 마지막으로 본 머리의 제목과 맞춘다). 없으면 null
     */
    fun barTitle(root: AccessibilityNodeInfo): String? {
        val bottom = root.bounds().let { it.top + it.height() / TOP_PART }
        fun bar(n: AccessibilityNodeInfo): String? {
            if (n.childCount < 2) return null
            val back = n.getChild(0)?.takeIf { b -> b.isClickable && (0 until b.childCount).any { b.getChild(it)?.contentDescription.isNullOrEmpty() == false } }
                ?: return null
            if (back.bounds().bottom > bottom) return null
            val name = n.getChild(1)?.takeIf { !it.isClickable && it.childCount == 1 }?.getChild(0) ?: return null
            return name.takeIf { it.className?.endsWith("TextView") == true }?.text?.let(::plain)?.takeIf { it.isNotEmpty() }
        }
        // 위 막대는 위 [TOP_PART] 안에 있다: 그 아래에서 시작하는 묶음(화 줄·서재 칸)은 들어가지 않는다
        fun walk(n: AccessibilityNodeInfo, depth: Int): String? = if (n.bounds().top >= bottom) null
            else bar(n) ?: if (depth < DEEP_WALK_DEPTH) (0 until n.childCount).firstNotNullOfOrNull { i -> n.getChild(i)?.let { walk(it, depth + 1) } } else null
        return walk(root, 0)
    }

    /**
     * 서재(찾아보기 목록도) 격자 칸(누를 수 있는 묶음): 첫 아이가 글자·설명 없는 표지 자리, 그 뒤 글자가 제목. 표지 위에 겹친 것도 그대로 자른다:
     * 기본인 작은 격자는 표지 아래쪽 그늘 위에 제목을, 격자마다 위 모서리에 읽지 않은 화 수·로컬 표시(서재 설정 › 오버레이, 이 둘이 기본으로 켜짐)를 그린다
     * (사용자가 정함: 깨끗한 표지는 기록 탭·작품 상세에만 있어, 서재로 여는 작품도 표지가 들어오게. 안내에 적는다).
     * 칸 밖의 것(위 막대·아래 탭 줄)에 걸린 표지는 자르지 않는다.
     */
    private fun gridCell(cell: AccessibilityNodeInfo, root: AccessibilityNodeInfo): Cell? {
        if (!cell.isClickable) return null
        val kids = (0 until cell.childCount).mapNotNull { cell.getChild(it) }
        val cover = kids.firstOrNull()?.takeIf { it.text.isNullOrEmpty() && it.contentDescription.isNullOrEmpty() } ?: return null
        val at = cover.bounds().takeIf(::usableThumb) ?: return null
        val name = kids.drop(1).firstOrNull { it.className?.endsWith("TextView") == true && !it.text.isNullOrBlank() } ?: return null
        val title = name.text?.let(::plain)?.takeIf { it.isNotEmpty() } ?: return null
        return Cell(title, "", -1, -1, at.takeUnless { drawnOver(root, cell, at) }, cell.bounds())
    }

    /**
     * 작품 상세의 표지: 글자 없이 눌리고 설명(`표지`)이 붙은 세로로 긴 요소. Mihon 은 설명이 붙은 단추, Kahon 은 설명 요소와 단추를 품은 묶음
     * (표지를 손가락으로 키우는 기능 때문인 듯). 위 막대의 뒤로 단추처럼 설명이 붙은 정사각형 단추는 표지 모양이 아니다.
     */
    private fun isCover(n: AccessibilityNodeInfo): Boolean {
        if (!n.isClickable || !n.text.isNullOrEmpty() || !usableThumb(n.bounds())) return false
        return !n.contentDescription.isNullOrEmpty() ||
            (0 until n.childCount).any { i -> n.getChild(i)?.let { it.text.isNullOrEmpty() && !it.contentDescription.isNullOrEmpty() } == true }
    }

    /**
     * [root] 를 그리는 차례(앞 순회)로 [own] 뒤에 오는 요소 가운데 표지 자리 [at] 과 겹치는 것이 있는가(위 막대·아래 탭 줄 등).
     * [own] 안의 요소(Kahon 표지의 설명·단추, 서재 칸 안의 제목·표시)는 표지 자체·칸의 것이라 보지 않는다
     */
    private fun drawnOver(root: AccessibilityNodeInfo, own: AccessibilityNodeInfo, at: Rect): Boolean {
        var passed = false
        fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean {
            if (passed) { if (Rect.intersects(n.bounds(), at)) return true }
            else if (n == own) { passed = true; return false }
            return depth < DEEP_WALK_DEPTH && (0 until n.childCount).any { i -> n.getChild(i)?.let { walk(it, depth + 1) } == true }
        }
        return walk(root, 0)
    }

    /**
     * 읽는 화면: 메뉴(`compose_overlay` 안)를 띄우면 위 줄에 작품 제목, 그 아래 화 이름이 보인다(소스 `ReaderAppBars` 의 제목·부제).
     * 메뉴가 없을 때도 아래 끝에 화의 쪽(`1 / 8`)이 있어 묶음 위쪽 [TOP_PART] 안의 글자만 본다([menuLines]). 작품의 진행률은 없다.
     */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        val lines = windows.all.flatMap { it.byId(pkg, OVERLAY) }.map(::menuLines).firstOrNull { it.isNotEmpty() }.orEmpty()
        return ViewerInfo(lines.firstOrNull(), "", -1, chapter = lines.getOrNull(1).orEmpty())
    }

    /** 메뉴 묶음 [overlay] 위쪽 [TOP_PART] 안의 글자를 위에서부터(작품 제목, 화 이름. 메뉴가 없으면 빈 목록) */
    internal fun menuLines(overlay: AccessibilityNodeInfo): List<String> {
        val area = overlay.bounds()
        val texts = mutableListOf<Pair<String, Rect>>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            n.text?.let(::plain)?.takeIf { it.isNotEmpty() }?.let { texts += it to n.bounds() }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(overlay, 0)
        return texts.filter { (_, r) -> r.bottom <= area.top + area.height() / TOP_PART }.sortedBy { it.second.top }.map { it.first }
    }

    /**
     * 책 파일 읽기로 아는 작품 [title] 의 표지 그림·저자(Mihon 저장 폴더 [storages] 에서). 화면 스레드에서 부르지 않는다.
     * - 로컬 만화 `local/<작품>/`: `cover.*`(그림. 없으면 Mihon 이 첫 화의 첫 쪽으로 `cover.jpg` 를 만든다, 소스 `LocalCoverManager`)와
     *   `details.json` author·`ComicInfo.xml` Writer. 작품 폴더는 폴더 이름, 아니면 details title·ComicInfo Series 가 [title] 과 같은 것.
     * - 받은 화 `downloads/<소스>/<작품>/<화>/ComicInfo.xml`(CBZ 면 그 안): Mihon 이 화를 받을 때마다 넣는다(소스 `Downloader.createComicInfoFile`,
     *   Series 가 작품 제목·Writer 가 저자). 작품 폴더 이름은 제목에서 파일에 못 쓰는 글자만 바꾼 것이라 이름으로 먼저, 안 되면 Series 로 찾는다.
     * - 자동 백업 `autobackup/` 의 가장 새 백업(`.tachibk`, gzip + protobuf `Backup`: 1 = 서재 작품 `BackupManga`, 그 안 3 = 제목·5 = 저자). 기본 12시간마다라
     *   그 뒤에 서재에 넣은 작품은 다음 백업까지 모른다.
     * 저자는 이 차례로 먼저 찾은 것([withAuthor] 가 false 면 찾지 않는다). 온라인 소스 작품의 표지는 앱 전용 폴더(`Android/data/…/covers`)라 읽을 수 없다.
     */
    fun fileInfo(context: Context, title: String, withAuthor: Boolean): FileInfo? =
        if (!BookFiles.canRead(context)) null else fileInfoIn(storages(), title, withAuthor)

    internal fun fileInfoIn(storages: List<File>, title: String, withAuthor: Boolean = true): FileInfo {
        val local = localDir(storages, title)
        val cover = local?.listFiles { f -> f.isFile && f.nameWithoutExtension.equals("cover", ignoreCase = true) && isImageFile(f.name) }?.minByOrNull { it.name }
        val author = if (!withAuthor) null
            else local?.let { metaOf(it, DETAILS_AUTHOR, COMIC_WRITER) } ?: downloadedAuthor(storages, title) ?: backupAuthors(storages)[normTitle(title)]
        return FileInfo(cover, author)
    }

    private fun localDir(storages: List<File>, title: String): File? {
        val dirs = storages.flatMap { File(it, LOCAL).listFiles { f -> f.isDirectory }.orEmpty().toList() }
        return dirs.firstOrNull { sameTitle(it.name, title) } ?: dirs.firstOrNull { d -> metaOf(d, DETAILS_TITLE, COMIC_SERIES)?.let { sameTitle(it, title) } == true }
    }

    /** 로컬 작품 폴더의 정보 한 가지: `details.json` 의 것, 없으면 `ComicInfo.xml` 의 것(Mihon 이 써 둔다) */
    private fun metaOf(dir: File, details: Regex, comic: Regex): String? = runCatching {
        File(dir, "details.json").takeIf { it.isFile }?.readText()?.let { details.find(it)?.groupValues?.get(1)?.let(::jsonText) }?.takeIf { it.isNotBlank() }
            ?: File(dir, COMIC_INFO).takeIf { it.isFile }?.readText()?.let { comic.find(it)?.groupValues?.get(1)?.let(::xmlText) }
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** 받은 화의 ComicInfo 의 저자: 폴더 이름이 제목과 같은 작품, 없으면 첫 화의 Series 가 제목과 같은 작품 */
    private fun downloadedAuthor(storages: List<File>, title: String): String? {
        val dirs = storages.flatMap { s ->
            File(s, DOWNLOADS).listFiles { f -> f.isDirectory }.orEmpty().flatMap { src -> src.listFiles { f -> f.isDirectory }.orEmpty().toList() }
        }
        val (named, rest) = dirs.partition { sameTitle(it.name, title) }
        named.firstNotNullOfOrNull { d -> downloadedInfo(d).second }?.let { return it }
        return rest.firstNotNullOfOrNull { d -> downloadedInfo(d).takeIf { (series, _) -> series != null && sameTitle(series, title) }?.second }
    }

    /**
     * 받은 작품 폴더의 Series·Writer(첫 화의 ComicInfo). 작품이 많으면 책을 펼 때마다 화 묶음을 여는 일이 많아, 폴더(경로·고친 때)마다 기억해 두고
     * 폴더가 바뀌었을 때(화를 더 받음)만 다시 읽는다
     */
    private fun downloadedInfo(manga: File): Pair<String?, String?> {
        val key = "${manga.path}@${manga.lastModified()}"
        downloaded[manga.path]?.takeIf { it.first == key }?.let { return it.second }
        val info = chapterInfo(manga)
        fun tag(regex: Regex) = info?.let { regex.find(it)?.groupValues?.get(1)?.let(::xmlText)?.trim()?.takeIf { t -> t.isNotEmpty() } }
        return (tag(COMIC_SERIES) to tag(COMIC_WRITER)).also { downloaded[manga.path] = key to it }
    }

    /** 받은 작품 폴더(경로) → (읽은 때의 경로·고친 때, Series·Writer) */
    private val downloaded = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Pair<String?, String?>>>()

    /** 작품 폴더의 화 가운데 ComicInfo.xml 이 있는 첫 화(이름 순)의 글자: 화 폴더면 그 안, CBZ·ZIP 이면 묶음 안 */
    private fun chapterInfo(manga: File): String? = manga.listFiles().orEmpty().sortedBy { it.name }.firstNotNullOfOrNull { ch ->
        runCatching {
            when {
                ch.isDirectory -> File(ch, COMIC_INFO).takeIf { it.isFile }?.readText()
                ch.name.endsWith(".cbz", ignoreCase = true) || ch.name.endsWith(".zip", ignoreCase = true) ->
                    ZipFile(ch).use { z -> z.getEntry(COMIC_INFO)?.let { e -> z.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) } } }
                else -> null
            }
        }.getOrNull()
    }

    /** 가장 새 자동 백업의 서재 작품(정리한 제목 → 저자). 같은 파일(경로·고친 때)이면 다시 풀지 않는다 */
    private fun backupAuthors(storages: List<File>): Map<String, String> {
        val file = storages.flatMap { s ->
            File(s, AUTOBACKUP).listFiles { f -> f.isFile && (f.name.endsWith(".tachibk") || f.name.endsWith(".proto.gz")) }.orEmpty().toList()
        }.maxByOrNull { it.lastModified() } ?: return emptyMap()
        val key = "${file.path}@${file.lastModified()}"
        backup?.takeIf { it.first == key }?.let { return it.second }
        val authors = runCatching { backupAuthorsOf(GZIPInputStream(file.inputStream()).use { it.readBytes() }) }.getOrDefault(emptyMap())
        backup = key to authors
        return authors
    }

    @Volatile private var backup: Pair<String, Map<String, String>>? = null

    /** protobuf `Backup` 바이트에서 서재 작품(필드 1)마다 정리한 제목(3) → 저자(5). 저자가 없는 작품은 뺀다 */
    internal fun backupAuthorsOf(bytes: ByteArray): Map<String, String> {
        val out = mutableMapOf<String, String>()
        protoFields(bytes, 0, bytes.size) { field, start, end ->
            if (field != 1) return@protoFields
            var title: String? = null
            var author: String? = null
            protoFields(bytes, start, end) { f, s, e ->
                when (f) {
                    3 -> title = String(bytes, s, e - s, Charsets.UTF_8)
                    5 -> author = String(bytes, s, e - s, Charsets.UTF_8)
                }
            }
            val t = title?.trim()?.takeIf { it.isNotEmpty() } ?: return@protoFields
            author?.trim()?.takeIf { it.isNotEmpty() }?.let { out.putIfAbsent(normTitle(t), it) }
        }
        return out
    }

    /**
     * Mihon 저장 폴더(사용자가 처음에 고른 곳): `local` 과 `downloads`·`autobackup` 가운데 하나가 함께 있는 폴더. 저장소(SD 카드도) 맨 위와 두 단계 아래까지 본다.
     * 찾은 것은 기억하고(그 폴더가 없어지면 다시 찾는다), 못 찾았으면 [STORAGE_RECHECK_MS] 뒤에 다시 본다
     */
    private fun storages(): List<File> {
        val now = SystemClock.elapsedRealtime()
        // 찾은 폴더가 없어졌으면(Mihon 에서 저장 폴더를 옮김) 다시 찾는다
        found?.takeIf { (it.isNotEmpty() && it.all(File::isDirectory)) || (it.isEmpty() && now - foundAt < STORAGE_RECHECK_MS) }?.let { return it }
        fun isStorage(d: File) = File(d, LOCAL).isDirectory && (File(d, DOWNLOADS).isDirectory || File(d, AUTOBACKUP).isDirectory)
        fun subdirs(d: File) = d.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name != "Android" }.orEmpty().toList()
        val roots = listOf(BookFiles.storageRoot) + File("/storage").listFiles { f -> f.isDirectory && f.name != "emulated" && f.name != "self" }.orEmpty()
        return roots.flatMap { r -> (listOf(r) + subdirs(r) + subdirs(r).flatMap(::subdirs)).filter(::isStorage) }.distinct()
            .also { found = it; foundAt = now }
    }

    @Volatile private var found: List<File>? = null
    @Volatile private var foundAt = 0L

    /**
     * Mihon 이 표지 대신 그리는 빈 표지인가(소스 `MangaCover`): 불러오는 동안은 바탕 `0x1F888888` 한 가지 색, 못 불러왔으면(표지 없는 로컬 만화 등)
     * 그 위 가운데에 같은 색을 한 번 더 칠한 그림 아이콘(`cover_error`). 서재 격자 칸은 그 위 아래쪽에 그늘·제목을, 위 모서리에 표시를 그려
     * 가운데 부분(폭 10~90%·높이 15~60%)만 본다: 거의 모든 점이 한 밝기 가까이(아이콘은 바탕보다 조금 어둡다)이고 무채색이며, 흰색·검은색이 아니다
     * (흰 바탕·검은 바탕 표지와 가른다). 2026-10-26 에뮬레이터: 서재 칸 바탕 238(아이콘 225), 찾아보기의 서재에 넣은 칸(옅게 그림) 247.
     * 1.6.1 은 이것을 표지로 잘라 붙여, 그 뒤에 진짜 표지가 보여도 다시 찍지 않았다.
     */
    override fun placeholderCover(context: Context, crop: Bitmap): Boolean {
        val w = crop.width
        val h = crop.height
        val px = IntArray(w * h).also { crop.getPixels(it, 0, w, 0, 0, w, h) }
        return placeholder(px, w, h)
    }

    /** [placeholderCover] 의 판단: 폭 [w]·높이 [h] 의 ARGB 점들 [px] */
    internal fun placeholder(px: IntArray, w: Int, h: Int): Boolean {
        val left = (w * 0.1f).toInt()
        val right = (w * 0.9f).toInt()
        val top = (h * 0.15f).toInt()
        val bottom = (h * 0.6f).toInt()
        if (right <= left || bottom <= top) return false
        val lum = FloatArray((right - left) * (bottom - top))
        var chroma = 0L
        var i = 0
        for (y in top until bottom) for (x in left until right) {
            val p = px[y * w + x]
            val r = p shr 16 and 0xff
            val g = p shr 8 and 0xff
            val b = p and 0xff
            lum[i++] = 0.299f * r + 0.587f * g + 0.114f * b
            chroma += maxOf(r, g, b) - minOf(r, g, b)
        }
        val bg = lum.copyOf().apply { sort() }[lum.size / 2]
        return bg in MIN_BG..MAX_BG && chroma / lum.size <= GRAY_CHROMA &&
            lum.count { abs(it - bg) <= BG_SLACK }.toFloat() / lum.size >= MOSTLY
    }

    /** 앱이 저자를 모를 때 보이는 글자(`작가 미상`, 앱 문자열 `unknown_author`. 기기 언어로, 못 읽으면 영어 그대로) */
    fun unknownAuthor(context: Context): Set<String> {
        val local = runCatching {
            val res = context.packageManager.getResourcesForApplication(pkg)
            res.getIdentifier(UNKNOWN_AUTHOR, "string", pkg).takeIf { it != 0 }?.let(res::getString)
        }.getOrNull()
        return setOfNotNull(local, "Unknown author")
    }
}

/**
 * 작품 상세를 기억하는 바탕([DetailReader])에, 상세의 저자가 `작가 미상`이면 저자 없이 넣는다.
 * 상세 없이 서재 화면(기록·업데이트 탭)에서 읽는 화면이 열려 책 모름으로 적었으면 그 때를 [AppContext.unknownOpenAt] 에 두어, 메뉴로 작품을 알면
 * 연 때부터 그 작품에 붙인다(읽는 화면 하나는 한 작품만 보여 준다: 다음 화로 넘어가도 같은 작품, 다른 작품은 서재로 돌아가 다시 연다).
 * 바탕은 사라진 상세를 잠깐(3초) 더 기억하지만(누르면 상세가 먼저 사라지는 앱), Mihon 은 화를 열어도 상세가 그대로 있고 읽는 화면이 위에 덮인다.
 * 그래서 상세가 사라졌는데 앞 화면이 읽는 화면이 아니면(뒤로 나가 기록 탭 등) 바로 잊는다: 그 사이 기록 탭에서 다른 작품을 열면 앞 작품에 붙었다.
 * 상세를 아래로 넘겨 머리가 화면 밖이면 위 막대의 제목으로 그 상세를 이어 본다([MihonDetails]).
 */
class MihonReader private constructor(private val mihon: MihonApp, private val details: MihonDetails) :
    DetailReader(mihon, clickOnly = true, details::of) {
    constructor(mihon: MihonApp) : this(mihon, MihonDetails(mihon))

    private var lastLeaveCheck = 0L

    /**
     * 바탕은 상세를 1초에 한 번 보아, 뒤로 나와 1초 안에 기록 탭의 줄을 누르면 사라진 것을 보기 전에 읽는 화면이 열린다(뒤로 나가는 동안은 상세가 아직
     * 요소로 남아 있기도 하다). 그래서 내용이 바뀔 때마다([LEAVE_CHECK_MS] 간격) 상세가 남아 있는지 따로 본다
     */
    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        super.onShelfLive(ctx, force)
        val now = ctx.now
        if (!remembers || (!force && now - lastLeaveCheck < LEAVE_CHECK_MS)) return
        lastLeaveCheck = now
        forgetLeftDetail(ctx)
    }

    /** 서재가 멈췄다: 뒤로 나간 상세는 잊고, 상세가 보이면 목록에 있는 그 작품의 빈 저자를 채운다(메뉴로 먼저 들어온 작품) */
    override fun onShelf(ctx: AppContext) {
        super.onShelf(ctx)
        val root = ctx.root ?: return
        val web = details.of(root)
        if (web == null) {
            if (remembers && root.count(ctx.pkg, READER) == 0) beforeOpened(ctx)
            return
        }
        val book = ctx.recorder.lookup.byTitle(web.title) ?: return
        checkCover(ctx, book, web)
        if (book.author.isNotBlank()) return
        val author = web.author.takeIf { it.isNotEmpty() && it !in unknownAuthor(ctx) } ?: return
        ctx.recorder.status(book.id, -1, author = author)
    }

    /**
     * 1.6.1 이 표지로 잘라 붙인 빈 표지([MihonApp.placeholderCover])를 지운다: 작품을 펴거나 상세를 볼 때(서비스가 붙을 때마다 책마다 한 번) 뒤에서 보아,
     * 빈 표지면 지우고 상세 [web] 가 있으면 그 표지를 바로 찍는다(아니면 다음에 서재·상세에 보일 때 바탕 규칙으로 찍는다)
     */
    private fun checkCover(ctx: AppContext, book: ShelfBook, web: Cell?) {
        if (!ctx.recorder.lookup.hasCover(book.id) || !coverChecked.add(book.id)) return
        val context = ctx.context
        ctx.inBackground({ ctx.recorder.dropPlaceholderCover(book.id) { mihon.placeholderCover(context, it) } }) { result ->
            val thumb = web?.thumb
            if (result.getOrNull() == true && thumb != null) ctx.capture(listOf(CoverSpot(web.title, thumb, book.id)))
        }
    }

    /** 붙은 표지가 빈 표지인지 본 책([checkCover]) */
    private val coverChecked = mutableSetOf<String>()

    /**
     * 앞 화면에 상세가 없고 읽는 화면도 아니면(뒤로 나가 기록 탭 등) 기억한 상세를 잊는다. 상세 기억을 지우는 길은 [beforeOpened] 뿐이다
     * (누른 칸 기억도 지우는데 Mihon 은 누름 알림이 없다)
     */
    private fun forgetLeftDetail(ctx: AppContext) {
        val root = ctx.root ?: return
        if (root.count(ctx.pkg, READER) == 0 && details.of(root) == null) beforeOpened(ctx)
    }

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        super.onEnterViewer(ctx, from, again)
        if (from in mihon.shelves && ctx.recorder.recordedBook(ctx.pkg) == null) ctx.unknownOpenAt = ctx.now
    }

    /**
     * 책 파일 읽기를 허락했으면 편 작품을 파일 정보로 채운다(뒤에서, [MihonApp.fileInfo]): 로컬 만화면 작품 폴더의 표지 그림으로 바꾸고(화면에서 자른 표지는
     * 작고 서재 칸 것은 제목·표시가 든다), 저자가 비었으면 로컬 만화 정보·받은 화의 ComicInfo·자동 백업의 저자로 채운다(메뉴로 먼저 들어온 작품)
     */
    override fun afterOpened(ctx: AppContext, book: ShelfBook) {
        checkCover(ctx, book, null)
        if (!BookFiles.canRead(ctx.context) || !fileLookups.add(book.id)) return
        val prefs = ctx.prefs
        val pref = "local_cover_${ctx.pkg}_${book.id}"
        val needAuthor = ctx.recorder.lookup.get(book.id)?.author.isNullOrBlank()
        ctx.inBackground({
            val info = mihon.fileInfo(ctx.context, book.title, needAuthor) ?: return@inBackground null
            // 이미 그 그림(경로·고친 때)으로 바꾼 책은 다시 하지 않는다
            val key = info.cover?.let { "${it.path}@${it.lastModified()}" }
            val bytes = info.cover?.takeIf { prefs.getString(pref, null) != key }?.let(BookFiles::imageCover)
            Triple(key, bytes, info.author)
        }) done@{ result ->
            fileLookups -= book.id
            val (key, bytes, author) = result.getOrNull() ?: return@done
            if (key != null && bytes != null) {
                ctx.recorder.replaceCover(book.id, bytes)
                prefs.edit().putString(pref, key).apply()
            }
            author?.takeIf { it !in unknownAuthor(ctx) }?.let { ctx.recorder.status(book.id, -1, author = it) }
        }
    }

    /** 파일 정보를 찾는 중인 책 */
    private val fileLookups = mutableSetOf<String>()

    /** 앱의 `작가 미상`(칸을 열 때마다 앱 자원을 열지 않게 처음 한 번, 어댑터는 서비스가 붙을 때마다 새로 만든다) */
    private fun unknownAuthor(ctx: AppContext): Set<String> = unknown ?: mihon.unknownAuthor(ctx.context).also { unknown = it }
    private var unknown: Set<String>? = null

    override fun openCell(ctx: AppContext, cell: Cell, at: Long?) {
        val known = cell.author.takeUnless { it in unknownAuthor(ctx) }.orEmpty()
        super.openCell(ctx, cell.copy(author = known), at)
    }
}

/**
 * 지금 보이는 작품 상세: 머리(표지·제목·저자)가 보이면 그것([MihonApp.detail]). 화가 많은 작품을 아래로 넘겨 머리가 화면 밖이면 위 막대의 제목
 * ([MihonApp.barTitle])이 마지막으로 본 머리의 제목과 같을 때 그 작품(표지 자리 없이). 1.6.1 은 넘긴 채 고른 화를 '책 모름'으로 두어,
 * 읽던 화를 찾아 넘겨 연 작품(이미 읽고 있던 만화)이 들어오지 않았다(2026-10-26 Reddit 댓글, 에뮬레이터에서 재현)
 */
internal class MihonDetails(private val mihon: MihonApp) {
    /** 마지막으로 본 머리 */
    private var header: Cell? = null

    fun of(root: AccessibilityNodeInfo): Cell? {
        mihon.detail(root)?.let { header = it; return it }
        val last = header ?: return null
        return last.takeIf { mihon.barTitle(root)?.let { sameTitle(it, last.title) } == true }?.copy(thumb = null)
    }
}

/** 책 파일 읽기로 아는 작품 정보: 로컬 만화의 표지 그림, 로컬 만화 정보·받은 화·자동 백업의 저자([MihonApp.fileInfo]) */
class FileInfo(val cover: File?, val author: String?)

object Mihon : MihonApp("app.mihon")

/** Kahon(Mihon 포크, F-Droid·GitHub 판 같은 패키지) */
object Kahon : MihonApp("com.amanoteam.kahon")

/**
 * 빈 표지([MihonApp.placeholder]) 바탕으로 볼 수 있는 밝기(0~255): 어두운 테마의 바탕(약 32)부터, 흰 표지(255)는 아니게.
 * 크레마 화면 대비가 캡처를 어둡게 해도(238 → 약 214) 들어온다
 */
private const val MIN_BG = 16f
private const val MAX_BG = 251f
/** 바탕 밝기에서 벗어날 수 있는 만큼(아이콘은 바탕보다 약 13, 크레마 화면 대비로 약 28 어둡다. 글자·그림 선은 훨씬 더) */
private const val BG_SLACK = 40f
/** 그래야 하는 점의 비율(빈 표지는 1. 바탕이 한 가지 색인 책 표지도 제목·그림 때문에 0.97 아래, 2026-10-26 에뮬레이터의 책 표지 16권) */
private const val MOSTLY = 0.995f
/** 무채색으로 볼 점마다 색 차이(가장 큰 채널 - 가장 작은 채널)의 평균(테마 바탕의 옅은 색은 3쯤) */
private const val GRAY_CHROMA = 16

/** 뒤로 나간 상세를 잊으려고 앞 화면을 다시 보는 최소 간격 */
private const val LEAVE_CHECK_MS = 200L

/** Mihon 저장 폴더 안의 로컬 만화·받은 화·자동 백업 폴더 */
private const val LOCAL = "local"
private const val DOWNLOADS = "downloads"
private const val AUTOBACKUP = "autobackup"
private const val COMIC_INFO = "ComicInfo.xml"
/** Mihon 저장 폴더를 못 찾았을 때 다시 찾기까지 */
private const val STORAGE_RECHECK_MS = 60_000L
private val DETAILS_TITLE = Regex(""""title"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private val DETAILS_AUTHOR = Regex(""""author"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private val COMIC_SERIES = Regex("""<Series>([^<]*)</Series>""")
private val COMIC_WRITER = Regex("""<Writer>([^<]*)</Writer>""")

/** JSON 글자의 이스케이프(`\"`·`\\`·`\u0041` 등)를 푼다 */
private fun jsonText(s: String): String = JSON_ESCAPE.replace(s) { m ->
    val c = m.groupValues[1]
    when {
        c.length == 5 && c[0] == 'u' -> c.substring(1).toInt(16).toChar().toString()
        c == "n" -> "\n"
        c == "t" -> "\t"
        else -> c
    }
}
private val JSON_ESCAPE = Regex("""\\(u[0-9a-fA-F]{4}|.)""")

/** XML 글자의 기본 엔티티를 푼다 */
private fun xmlText(s: String): String =
    s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

/**
 * protobuf 묶음 [bytes] 의 [start]~[end] 를 필드마다 훑어 길이 붙은 값(문자열·안쪽 묶음)만 [each] 에 넘긴다(필드 번호, 값의 시작·끝).
 * 숫자 값은 건너뛰고, 모르는 꼴이나 깨진 묶음이면 거기서 멈춘다
 */
private fun protoFields(bytes: ByteArray, start: Int, end: Int, each: (field: Int, start: Int, end: Int) -> Unit) {
    var i = start
    fun varint(): Long {
        var r = 0L
        var shift = 0
        while (i < end && shift < 64) {
            val b = bytes[i++].toInt() and 0xff
            r = r or ((b and 0x7f).toLong() shl shift)
            if (b < 0x80) return r
            shift += 7
        }
        return -1
    }
    while (i < end) {
        val tag = varint().takeIf { it > 0 } ?: return
        when ((tag and 7).toInt()) {
            0 -> if (varint() < 0) return
            1 -> i += 8
            2 -> {
                val n = varint().takeIf { it >= 0 && it <= end - i }?.toInt() ?: return
                each((tag ushr 3).toInt(), i, i + n)
                i += n
            }
            5 -> i += 4
            else -> return
        }
    }
}

/** 읽는 화면의 본문 묶음(읽는 화면 창에만 있다) */
private const val READER = "reader_container"

/** 읽는 화면 위에 메뉴·쪽을 그리는 Compose 묶음 */
private const val OVERLAY = "compose_overlay"

/** 읽는 화면 메뉴의 위 줄로 보는 범위(묶음 높이의 1/n) */
private const val TOP_PART = 5

private const val UNKNOWN_AUTHOR = "unknown_author"
