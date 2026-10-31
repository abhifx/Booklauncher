package com.woody.booklauncher.readers

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.BookShelf
import com.woody.booklauncher.HomeSignals
import com.woody.booklauncher.OpenSteps
import com.woody.booklauncher.Seen
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.ShelfRecorder
import com.woody.booklauncher.Target
import com.woody.booklauncher.WatchLog
import com.woody.booklauncher.hasTitle
import java.io.File
import kotlin.math.abs

/**
 * 이지뷰어·채리뷰어(Cherie Soft. 내 파일(EPUB·TXT·PDF·만화 압축 파일 등)을 여는 리더, 채리뷰어는 유료판): 서재는 파일 목록 `actMain`
 * (폴더·최근문서·즐겨찾기 탭이 모두 이 화면 안). 칸에 파일 이름 `txtFileName`·그림 `imgFileIcon`(목록 보기는 아래에 파일 종류 띠 `txtFileExt` 가 겹친다), 목록 보기에는 EPUB 책 정보 `txtFileMemo`
 * (`제목\n저자: 홍길동`)·읽은 비율 `txtFilePer`(`23.5%`, 연 적 있는 파일만)도 있다. 격자 보기는 파일 이름만 있어 제목은 파일 이름(확장자 뗌)이다.
 * 폴더·그림 같은 칸도 같은 이름이라 책 파일 확장자인 칸만 책으로 보고, 눌러도 읽는 화면이 열려야 편 것으로 본다.
 * 읽는 화면은 글(`actViewText`: EPUB·TXT)과 그림(`actViewImage`: PDF·만화)이고, 본문은 직접 그려 글자가 드러나지 않는다. 위 상태 줄
 * `txtPageInfo_TitleBar` 에 EPUB 은 `장 안 쪽/장 쪽 수[장/전체 장]`(`2/5[3/12]`), 그 밖은 `쪽/전체 쪽`(`6/101`). 제목 자리(`txtFileName_TitleBar`,
 * 메뉴의 `txtFileName`)는 늘 파일 이름(`이름.epub#3 3장`, 이지뷰어 `TEzViewBase.W`)이라 제목으로 쓰지 않고, 그 파일로 넣은 책을 찾는다([ViewerInfo.file]).
 * 책은 서재에서 칸을 눌러 열 때와 ▶(마지막 파일)·다음 파일의 열기 확인 창([openingFile])으로 위치를 알 때 넣는다(다른 앱으로 연 파일은 넣지 않는다).
 * 그림 파일은 그 폴더가 한 권이다(제목은 폴더 이름, 표지는 첫 그림. 읽는 화면 위 줄에는 지금 보는 그림 이름만 보인다).
 * 칸의 위치는 최근문서 탭은 칸의 `txtFilePath`(`#휴대폰/Books`, 다른 앱이 연 파일은 `/sdcard/Download`), 폴더 탭은 위 경로 줄 `txtPath`
 * (`#휴대폰` · `Books`, 실제 경로로 들어간 폴더는 `sdcard` · `Download`). `#휴대폰` 은 내부 저장소, `#USB메모리`·`#USB_1234-5678` 은 SD 카드·USB 맨 위
 * (이지뷰어 문자열 `storage_internal`·`storage_usb`·`device_usb`, 기기 언어를 따른다)라 실제 경로로 바꿔 책의 파일로 적는다([bookFile]): EPUB 표지(책 파일 읽기)와
 * 홈에서 그 파일 바로 열기(`actMain` 이 `file://` 보기를 받는다). 사용자가 더한 서버(`+`: FTP·SMB·WebDAV·클라우드)의 파일은 이지뷰어가 받아 둔 파일에서
 * 찾는다([downloaded], 다운로드 경로를 휴대폰·SD카드로 바꾼 경우만). 원격 파일은 위치(서버 이름) + 이름 → 책도 적어 두어 받은 파일을 못 찾아도 같은 책으로 본다.
 * 패키지마다 같은 코드로 만든 앱이라(Play·원스토어 같은 패키지) 요소 이름은 같다. 이지뷰어(26.10.20)를 디컴파일해 보면 화면·앱 클래스가
 * 패키지와 상관없이 `com.ezne.easyview.` 아래에 있고(`MyApp`·`actMain`), 유료 기능은 실행 중에 패키지 이름이 `.pro` 로 끝나는지(와 리소스
 * `APP_PERM_PRO`)로 켠다. 채리뷰어는 받아 보지 못해 그 패키지 아래 이름(`com.ezne.easyview.pro.actMain`)도 같이 받는다
 * (문리더 유료판은 화면 클래스가 패키지마다 달라 1.5.3 에서 알아보지 못했다).
 */
open class EasyViewSpec(pkg: String) : ReaderApp(
    pkg = pkg, classPrefix = BASE,
    shelves = screens(pkg, "actMain"),
    viewers = screens(pkg, "actViewText") + screens(pkg, "actViewImage"),
    titles = listOf("txtFileName"), thumbs = listOf("imgFileIcon"), authors = emptyList(), progress = "txtFilePer", due = null,
    viewerProgress = listOf("txtPageInfo_TitleBar"),
    openOnViewer = true,
    needsBookFiles = true,
) {
    override fun newAdapter(): ReaderAdapter = EasyViewReader(this)

    /** 만화 묶음(ZIP·CBZ·CBR, CB7 은 글자 파일로 연다)은 목록 그림을 자르지 않고 그 파일의 첫 그림을 표지로(목록 그림은 작고, CB7 은 깨진 글자다) */
    override val fileCovers get() = BookFiles.WITH_COMICS

    /** 홈에서 그 책의 파일을 바로 연다(`actMain` 이 `file://` 보기를 받는다). 그 파일이 열리면 이 책으로(읽는 화면에 파일 이름만 보인다). 모르거나 지워졌으면 앱을 연다 */
    override fun open(ctx: HomeContext, book: ShelfBook): OpenResult =
        ctx.bookFile(book)?.let { path -> OpenResult.File(ctx.component, path, started = { HomeSignals.fileOpened(pkg, book, path) }) } ?: OpenResult.Launch

    /**
     * 파일 이름 칸 → 책 파일만, 제목·저자는 EPUB 책 정보가 있으면 그것. 표지 칸은 아래에 겹친 파일 종류 띠(`EPUB`)를 빼고,
     * 글 파일(TXT 등)은 그림이 본문 첫 쪽 미리 보기라 표지로 자르지 않는다. 탭(폴더·최근문서·즐겨찾기)은 옆으로 넘기는 쪽이라
     * 화면 밖 탭의 칸도 트리에 남는데, 그 탭은 다시 열 때까지 읽은 비율이 그대로라 보이지 않는 칸은 읽지 않는다.
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? {
        if (!cell.isVisibleToUser) return null
        val c = super.readCell(cell) ?: return null
        val image = isImageFile(c.title)
        val name = withoutBookFile(c.title).takeIf { it != c.title && it.isNotBlank() || image } ?: return null
        val parts = partsOf(cell)
        val memo = parts[MEMO]?.text?.lines()?.map { it.trim() }.orEmpty()
        val title = memo.firstOrNull()?.takeIf { it.isNotEmpty() && AUTHOR.matchEntire(it) == null } ?: name
        val author = memo.firstNotNullOfOrNull { AUTHOR.matchEntire(it)?.groupValues?.get(1)?.trim() }.orEmpty()
        val thumb = c.thumb?.takeIf { !TEXT_FILE.containsMatchIn(c.title) }?.let { t ->
            val ext = parts[EXT]?.bounds()
            if (ext != null && ext.top > t.top && ext.top < t.bottom) Rect(t.left, t.top, t.right, ext.top) else t
        }
        // 최근문서 탭은 칸에 위치가 있고, 폴더 탭은 위 경로 줄을 붙인다([shelfCells]·[clickedCell])
        val folder = parts[FOLDER]?.text?.let(::plain)?.takeIf { it.isNotEmpty() }
        // 그림 칸의 읽은 비율은 그 그림부터 열었을 때의 것이라(그림마다 따로) 폴더 한 권의 진행률로 쓰지 않는다(읽는 화면의 쪽으로)
        val read = c.copy(title = title, author = author, thumb = thumb, file = c.title, progress = if (image) -1 else c.progress)
        return folder?.let { inFolder(read, it) } ?: read
    }

    /**
     * 칸 [c] 의 파일이 [folder] 안에 있다. 그림 파일은 그 폴더가 한 권(제목은 폴더 이름. 이지뷰어는 그림을 열면 그 폴더의 그림을 차례로 보인다,
     * 표지는 그 폴더의 첫 그림 [BookFiles.BOOKS])
     */
    private fun inFolder(c: Cell, folder: String): Cell {
        val file = "$folder/${c.file}"
        val title = folder.substringAfterLast('/').trimStart('#').takeIf { isImageFile(c.file.orEmpty()) && it.isNotEmpty() } ?: c.title
        return c.copy(title = title, file = file)
    }

    /**
     * 최근문서 탭은 같은 파일이 경로 표기(`#휴대폰/Books`·`/sdcard/Books`)마다 따로 보이고 읽은 비율도 따로라, 같은 파일은 맨 위(마지막으로 연) 칸만.
     * 폴더 탭 칸에는 지금 보이는 위 경로 줄을 붙인다
     */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        val cells = super.shelfCells(root)
        val folder by lazy { folderOf(root) }
        // 같은 파일인지는 제목과 폴더 마지막 이름·파일 이름으로 본다(다른 폴더의 같은 이름 파일은 합치지 않는다)
        return cells.groupBy { c -> c.title + "|" + c.file?.split('/')?.takeLast(2)?.joinToString("/")?.lowercase() }.values.map { it.first() }
            .map { c -> if (c.file != null && '/' !in c.file) folder?.let { inFolder(c, it) } ?: c else c }
            // 어느 폴더인지 모르는 그림은 책으로 보지 않는다(폴더가 한 권)
            .filterNot { c -> c.file?.let { '/' !in it && isImageFile(it) } == true }
    }

    /**
     * 누른 칸에 위치가 없으면(폴더 탭) 지금 보이는 위 경로 줄을 붙인다: 저장소 맨 위에서 들어가면 `#휴대폰` · `Books`,
     * 다른 앱이 연 파일의 폴더처럼 실제 경로로 들어가면 맨 앞 `/` 없이 `sdcard` · `Download`. 원격 파일은 누르자마자 받는 창이 맨 앞에 떠
     * 그 앱의 모든 창에서 찾고, 그래도 없으면 방금 읽어 둔 서재 칸([last], 위치를 붙여 둠)에서 같은 이름의 칸을 쓴다
     */
    override fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? {
        val c = super.clickedCell(source, text, windows, last) ?: return null
        // 파일이 없는 칸(누른 요소를 잃어 글자로만 맞춘 칸. 글자는 파일 이름 `a.epub`)은 방금 읽어 둔 서재 칸 가운데 그 파일 이름이 하나뿐일 때만 그 칸으로
        // (파일로 책을 가르는 앱이라 제목만으로 넣지 않는다)
        val found = c.file ?: return last?.filter { it.file?.let { f -> sameFileName(f.substringAfterLast('/'), c.title) } == true }
            ?.singleOrNull()?.copy(stale = true)
        val file = found.takeIf { '/' !in it } ?: return c
        val folder = (listOfNotNull(windows.active) + windows.all).firstNotNullOfOrNull(::folderOf)
        if (folder != null) return inFolder(c, folder)
        return last?.singleOrNull { it.file?.let { f -> '/' in f && f.substringAfterLast('/') == file } == true }?.let { c.copy(title = it.title, file = it.file) }
            ?: c.takeIf { !isImageFile(file) }
    }

    /** 폴더 탭의 지금 보이는 위 경로 줄(`#휴대폰` · `Books` → `#휴대폰/Books`, 맨 앞 `/` 없이 `sdcard` · `Download` → `/sdcard/Download`). 없으면 null */
    private fun folderOf(root: AccessibilityNodeInfo): String? {
        val folder = root.byId(pkg, "txtPath").filter { it.isVisibleToUser }
            .mapNotNull { n -> n.text?.let(::plain)?.takeIf { it.isNotEmpty() } }.joinToString("/").ifEmpty { return null }
        return if (folder.startsWith("#") || folder.startsWith("/")) folder else "/$folder"
    }

    /** 위 상태 줄의 쪽(EPUB 은 장까지 셈해 전체에서의 비율, 그 밖은 쪽 ÷ 전체 쪽)과 파일 이름(`이름.epub#3 3장` → `이름.epub`) */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        fun text(id: String) = windows.all.flatMap { it.byId(pkg, id) }.firstNotNullOfOrNull { n -> n.text?.let(::plain)?.takeIf { it.isNotEmpty() } }
        val progress = viewerProgress.firstNotNullOfOrNull(::text)?.let(::pageInfoPercent) ?: -1
        return ViewerInfo(null, "", progress, text(TITLE_BAR)?.let(::viewerFileName))
    }

    /** 찾은 실제 경로(서재를 볼 때마다 칸마다 찾으므로. 폴더 목록을 읽는 [realCase] 를 되풀이하지 않게, 못 찾은 것은 받아 둘 수 있어 담지 않는다) */
    private val resolved = LruCache<String, String>(RESOLVED_MAX)

    /** `#휴대폰/Books/a.epub` → `/storage/emulated/0/Books/a.epub`, `/sdcard/Download/a.epub` → `/storage/emulated/0/Download/a.epub`. 그 밖(네트워크·클라우드 등)은 null */
    fun bookFile(context: Context, shown: String): String? {
        // 책 파일 읽기를 켜고 끄면 실제 대소문자를 찾는지가 달라진다
        val key = "${BookFiles.canRead(context)}|$shown"
        resolved.get(key)?.let { return it }
        return resolve(context, shown)?.also { resolved.put(key, it) }
    }

    /** 사용자가 더한 서버(FTP·클라우드 등)의 파일 `#서버/…` 인가(저장소 이름을 못 읽으면 아니다: 로컬 파일을 원격으로 기억하지 않게) */
    fun isRemote(context: Context, shown: String): Boolean {
        if (!shown.startsWith("#") || '/' !in shown) return false
        val labels = labels(context).takeIf { it.isNotEmpty() } ?: return false
        val label = shown.substring(1).substringBefore('/')
        val local = listOf("storage_internal", "device_phone", "storage_external", "device_sdcard", "storage_usb").mapNotNull(labels::get)
        val usb = labels["device_usb"]?.let { "${it}_" }
        return label !in local && !(usb != null && label.startsWith(usb))
    }

    private fun resolve(context: Context, shown: String): String? {
        if (shown.startsWith("/")) return realCase(context, shown.replaceFirst(PRIMARY, BookFiles.storageRoot.path))
        // 위치가 없는 이름(어느 폴더인지 모름)은 짐작하지 않는다
        if (!shown.startsWith("#") || '/' !in shown) return null
        val label = shown.substring(1).substringBefore('/')
        val labels = labels(context)
        val usb = labels["device_usb"]?.let { "${it}_" }
        val root = when {
            label == labels["storage_internal"] || label == labels["device_phone"] -> BookFiles.storageRoot.path
            // 카드 번호가 붙은 SD 카드·USB(`#USB_1234-5678`, 이지뷰어 안에서는 `#EXT_SD_1234-5678`)
            usb != null && label.startsWith(usb) -> "/storage/" + label.removePrefix(usb)
            // SD 카드(`#USB메모리` 등): 앱 전용 폴더 목록의 둘째부터(`/storage/XXXX-XXXX/Android/data/…`)
            label == labels["storage_external"] || label == labels["device_sdcard"] || label == labels["storage_usb"] -> sdRoots(context).firstOrNull()?.path
            // 사용자가 더한 서버(FTP·클라우드 등)는 받아 둔 파일에서 찾는다
            else -> null
        } ?: return downloaded(context, shown.substringAfter('/'))
        return realCase(context, root + "/" + shown.substringAfter('/'))
    }

    /** SD 카드·USB 맨 위들(앱 전용 폴더 목록의 둘째부터 `/storage/XXXX-XXXX/Android/data/…` 의 앞) */
    private fun sdRoots(context: Context): List<File> = context.getExternalFilesDirs(null).drop(1)
        .mapNotNull { it?.path?.substringBefore("/Android/", "")?.ifEmpty { null }?.let(::File) }

    /**
     * 목록에 보이는 이름은 이지뷰어 설정(폴더/파일명 처리 › 첫글자를 대문자로: 첫 글자만 대문자, 나머지는 소문자 `DCIM` → `Dcim`)에 따라 실제 이름과
     * 대소문자가 다를 수 있어, 파일을 읽을 수 있으면 조각마다 폴더 목록에서 실제 이름을 찾는다(공유 저장소는 대소문자를 가리지 않아 보이는 대로도 열리지만,
     * 다른 앱이 적은 같은 파일의 경로와 맞게). 못 찾거나 읽을 수 없으면 그대로
     */
    private fun realCase(context: Context, path: String): String {
        if (!BookFiles.canRead(context)) return path
        var dir = File("/")
        for (part in path.split('/').filter { it.isNotEmpty() }) {
            val names = dir.list()
            // 목록을 못 읽는 위쪽 폴더(`/storage` 등)는 그대로 내려간다
            val real = if (names == null || part in names) part else names.firstOrNull { it.equals(part, ignoreCase = true) } ?: return path
            dir = File(dir, real)
        }
        return dir.path
    }

    /**
     * 원격(서버·클라우드) 파일 [remote](서버 맨 위에서의 경로 `novels/a.epub`)를 열 때 이지뷰어가 받아 둔 파일(하나뿐일 때, 없으면 null).
     * 받는 곳은 이지뷰어 설정의 다운로드 경로(`App.Cache.Down.Path`): 시스템 기본은 이지뷰어 전용 캐시라 읽을 수 없고, 휴대폰·SD카드면 그 저장소의
     * `easyview/down/<서버 번호>/<원격 경로>`(이지뷰어 `u51.Z(…, 3)`·`xq4`). 책 파일 읽기가 켜져 있어야 찾는다. 이름이 50자가 넘으면 줄여 받으므로 찾지 못한다
     */
    private fun downloaded(context: Context, remote: String): String? {
        if (remote.isEmpty() || !BookFiles.canRead(context)) return null
        return (listOf(BookFiles.storageRoot) + sdRoots(context)).flatMap { root -> File(root, DOWNLOADS).listFiles().orEmpty().toList() }
            .filter { it.isDirectory }.map { File(it, remote) }.filter { it.isFile }
            .singleOrNull()?.let { realCase(context, it.path) }
    }

    /**
     * 이지뷰어가 그림을 아직 만들지 않은 파일(받기 전의 원격 파일 포함)에 그리는 파일 종류 아이콘(`ic_file_ebook`·`ic_file_pdf` 등. 흰 모양을 회색으로 칠해
     * 그린다)인가: 자른 그림에서 바탕과 밝기가 다른 곳의 모양을 아이콘의 불투명한 곳의 모양과 [MASK]×[MASK] 칸으로 줄여 겹치는 정도(IoU)로 본다
     * (2026-10-21 에뮬레이터 화면: 기본 아이콘 0.89, EPUB 표지·만화 첫 쪽·PDF 첫 쪽·TXT 미리 보기 0.33 아래)
     */
    override fun placeholderCover(context: Context, crop: Bitmap): Boolean {
        val shape = cropMask(crop) ?: return false
        return iconMasks(context).any { icon ->
            abs(icon.aspect - shape.aspect) <= ASPECT_SLACK * icon.aspect && iou(icon.cells, shape.cells) >= PLACEHOLDER_IOU
        }
    }

    private class Mask(val cells: BooleanArray, val aspect: Float)

    /** 이지뷰어의 파일 종류 아이콘 모양(이지뷰어가 업데이트되면 다시 읽는다) */
    @Volatile private var icons: Pair<Long, List<Mask>>? = null

    private fun iconMasks(context: Context): List<Mask> {
        val updated = runCatching { context.packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrNull() ?: return emptyList()
        icons?.takeIf { it.first == updated }?.let { return it.second }
        val res = runCatching { context.packageManager.getResourcesForApplication(pkg) }.getOrNull() ?: return emptyList()
        val masks = ICONS.mapNotNull { name ->
            val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: return@mapNotNull null
            val d = runCatching { res.getDrawable(id, null) }.getOrNull() ?: return@mapNotNull null
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: ICON_PX
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: ICON_PX
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, w, h)
            d.draw(Canvas(b))
            maskOf(b) { Color.alpha(it) > OPAQUE }.also { b.recycle() }
        }
        icons = updated to masks
        return masks
    }

    /** 자른 그림의 모양: 바탕(네 귀퉁이 밝기 가운데 셋째로 밝은 것)과 밝기가 [INK] 넘게 다른 곳 */
    private fun cropMask(b: Bitmap): Mask? {
        val bg = listOf(b.getPixel(0, 0), b.getPixel(b.width - 1, 0), b.getPixel(0, b.height - 1), b.getPixel(b.width - 1, b.height - 1))
            .map(::luma).sorted()[2]
        return maskOf(b) { abs(luma(it) - bg) > INK }
    }

    /** [b] 에서 [on] 인 곳을 감싸는 상자를 [MASK]×[MASK] 칸으로 나눠 칸 가운데 점마다 본 모양과 상자의 가로 ÷ 세로. 아무 데도 없으면 null */
    private fun maskOf(b: Bitmap, on: (Int) -> Boolean): Mask? {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) if (on(px[y * w + x])) {
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        if (right < 0) return null
        val cells = BooleanArray(MASK * MASK) { k ->
            val x = left + ((right - left) * (k % MASK + 0.5f) / MASK).toInt()
            val y = top + ((bottom - top) * (k / MASK + 0.5f) / MASK).toInt()
            on(px[y * w + x])
        }
        return Mask(cells, (right - left + 1f) / (bottom - top + 1f))
    }

    private fun iou(a: BooleanArray, b: BooleanArray): Float {
        var both = 0
        var any = 0
        for (i in a.indices) { if (a[i] && b[i]) both++; if (a[i] || b[i]) any++ }
        return if (any == 0) 0f else both.toFloat() / any
    }

    private fun luma(c: Int) = (299 * Color.red(c) + 587 * Color.green(c) + 114 * Color.blue(c)) / 1000

    /** 열기 확인 창(`첫페이지부터 보기` 칸 `chkOpenFirstPage` 가 있는 창)의 위치 `txtFilePath` + 파일 이름 `txtFileName` */
    fun openingFile(windows: AppWindows): String? {
        for (root in windows.all) {
            if (root.byId(pkg, "chkOpenFirstPage").isEmpty()) continue
            val name = root.byId(pkg, "txtFileName").firstNotNullOfOrNull { n -> n.text?.let(::plain)?.takeIf { it.isNotEmpty() } } ?: continue
            val folder = root.byId(pkg, FOLDER).firstNotNullOfOrNull { n -> n.text?.let(::plain)?.takeIf { it.isNotEmpty() } }
            return if (folder == null) name else "$folder/$name"
        }
        return null
    }

    /** 이지뷰어가 저장소 이름으로 보이는 글자(이지뷰어가 업데이트되거나 기기 언어가 바뀌면 다시 읽는다. 서재 칸마다 쓴다) */
    @Volatile private var labelCache: Pair<String, Map<String, String>>? = null

    /** 이지뷰어가 저장소 이름으로 보이는 글자(문자열 이름 → 기기 언어의 글자). 이지뷰어 리소스를 못 읽으면 빈 표 */
    private fun labels(context: Context): Map<String, String> {
        val updated = runCatching { context.packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrNull() ?: return emptyMap()
        val key = "$updated|${Resources.getSystem().configuration.locales.toLanguageTags()}"
        labelCache?.takeIf { it.first == key }?.let { return it.second }
        val res = runCatching { context.packageManager.getResourcesForApplication(pkg) }.getOrNull() ?: return emptyMap()
        return LABELS.mapNotNull { name -> res.getIdentifier(name, "string", pkg).takeIf { it != 0 }?.let { name to res.getString(it) } }.toMap()
            .also { labelCache = key to it }
    }

    /** 칸 안의 EPUB 책 정보·파일 종류 띠·위치(이벤트로 받은 칸에서는 이름으로 찾기가 안 되어 직접 훑는다) */
    private fun partsOf(cell: AccessibilityNodeInfo): Map<String, AccessibilityNodeInfo> {
        val found = mutableMapOf<String, AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            n.viewIdResourceName?.substringAfter(":id/")?.takeIf { it == MEMO || it == EXT || it == FOLDER }?.let { found.putIfAbsent(it, n) }
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(cell, 0)
        return found
    }

    companion object {
        private const val BASE = "com.ezne.easyview"
        private const val MEMO = "txtFileMemo"
        private const val EXT = "txtFileExt"
        private const val FOLDER = "txtFilePath"
        private const val TITLE_BAR = "txtFileName_TitleBar"
        private const val RESOLVED_MAX = 64
        /** 원격 파일을 받아 두는 폴더(저장소 맨 위에서. 그 아래 서버마다 폴더 하나, 그 안에 원격 폴더 모양) */
        private const val DOWNLOADS = "easyview/down"
        /** 이지뷰어 저장소 이름 문자열(내부 저장소·SD 카드·USB) */
        private val LABELS = listOf("storage_internal", "device_phone", "storage_external", "device_sdcard", "storage_usb", "device_usb")

        /** 이지뷰어 파일 종류 아이콘(그림이 없는 파일에 그린다) */
        private val ICONS = listOf("ic_file_ebook", "ic_file_pdf", "ic_file_zip", "ic_file_txt", "ic_file_img", "ic_file_file", "ic_file_unknown")
        private const val ICON_PX = 144
        /** 모양을 줄여 비교하는 칸 수(가로·세로), 기본 아이콘으로 볼 겹침, 가로 ÷ 세로가 다를 수 있는 정도 */
        private const val MASK = 24
        private const val PLACEHOLDER_IOU = 0.7f
        private const val ASPECT_SLACK = 0.2f
        /** 아이콘의 불투명한 곳, 자른 그림에서 바탕과 다른 밝기 */
        private const val OPAQUE = 128
        private const val INK = 40

        /** 내부 저장소의 다른 이름(최근문서 탭은 다른 앱이 연 파일을 `/sdcard/…` 로 보인다) */
        private val PRIMARY = Regex("""^/(sdcard|storage/self/primary)(?=/|$)""")

        /** 읽는 화면 위 줄 → 파일 이름: 첫 줄에서 EPUB 의 `#장 번호 장 제목` 을 떼고, 경로가 붙었으면(설정·압축 파일 안 그림) 책 파일인 마지막 조각 */
        internal fun viewerFileName(text: String): String? {
            val first = text.lineSequence().first()
            val line = when {
                CHAPTER.containsMatchIn(first) -> first.replace(CHAPTER, "")
                isBookFile(first) -> first
                else -> first.replace(BARE_CHAPTER, "")
            }.trim()
            val parts = line.split('/').filter { it.isNotBlank() }
            return (parts.lastOrNull { withoutBookFile(it) != it } ?: parts.lastOrNull())?.trim()
        }

        /** EPUB 파일 이름 뒤의 `#3 3장`(`a.epub#3 3장`. 다른 파일 이름 안의 `Vol#3 x.cbz` 는 이름의 일부) */
        private val CHAPTER = Regex("""(?i)(?<=\.epub)#\d+(\s.*)?$""")
        /** 확장자를 숨긴 EPUB 이름 뒤의 장(`이름#3 3장`, 보이는 확장자가 없을 때만. 앞에 빈칸이 있는 `원피스 #12` 는 이름의 일부) */
        private val BARE_CHAPTER = Regex("""(?<=\S)#\d+(\s.*)?$""")

        /** 화면 클래스: 이지뷰어처럼 패키지와 상관없는 이름과, 패키지 아래 이름(채리뷰어 미확인) */
        private fun screens(pkg: String, name: String) = setOf("$BASE.$name", "$pkg.$name")

        /** 그림이 본문 미리 보기인 글 파일 */
        private val TEXT_FILE = Regex("""\.(txt|html?|xhtml|md|rtf|docx?|odt)$""", RegexOption.IGNORE_CASE)

        /** `저자: 홍길동`(이지뷰어 문자열 `item_book_author`: 한국어 `저자:`, 영어 `Author:`, 중국어·일본어 `作者:`·`作者：`) */
        private val AUTHOR = Regex("""(?:저자|Author|作者)\s*[:：]\s*(.*)""", RegexOption.IGNORE_CASE)

        /** EPUB 의 `2/5[3/12]`(장 안 쪽/장 쪽 수[장/전체 장]) */
        private val CHAPTER_PAGES = Regex("""(\d+)\s*/\s*(\d+)\s*\[\s*(\d+)\s*/\s*(\d+)\s*]""")

        /** `2/5[3/12]` → (앞 장 2개 + 이 장의 2/5) ÷ 12 = 20, `6/101` → 5. 모르면 -1 */
        internal fun pageInfoPercent(text: String): Int {
            val m = CHAPTER_PAGES.find(text) ?: return viewerPercent(text, false)
            val (page, pages, chapter, chapters) = m.destructured.toList().map { it.toLong() }
            if (pages <= 0 || chapters <= 0 || chapter < 1) return -1
            return ((chapter - 1 + page.toDouble() / pages) * 100 / chapters).toInt().coerceIn(0, 100)
        }
    }
}

/** 무료판(Play 의 `이지뷰어-TTS,만화,…` 와 원스토어 이지뷰어) */
object EasyView : EasyViewSpec("com.ezne.easyview.n")

/** 무료판(Play 의 `이지뷰어-만화,소설,…`. 2026-10 에뮬레이터에서 확인한 판) */
object EasyViewFree : EasyViewSpec("com.ezne.easyview.free")

/** 유료판 채리뷰어(Play·원스토어) */
object CherieViewer : EasyViewSpec("com.ezne.easyview.pro")

/**
 * 이지뷰어·채리뷰어의 판단과 기억: 서재는 파일 목록이라 책을 제목이 아니라 그 파일로 가른다(폴더가 다르면 이름·제목이 같아도 다른 책,
 * 서재 칸의 진행률·표지도 그 파일의 책에). 칸을 눌러도 읽는 화면에 그 파일 이름이 보일 때 편 것으로 보고(누른 뒤 다른 앱으로 다른 파일을 열었을 수 있다),
 * ▶·다음 파일의 열기 확인 창과 홈에서 바로 연 파일도 그렇게 안다. 다른 앱으로 연 파일은 넣지 않는다.
 */
class EasyViewReader(private val ev: EasyViewSpec) : ScreenReader(ev) {
    /** 열기 확인 창(▶·다음 파일)에 보인 파일([Cell.file] 꼴)과 그 때 */
    private var openingFiles: Pair<String, Long>? = null
    /** 이 읽는 화면에서 파일 이름마다 정한 책 id(모르면 null. 같은 파일을 보는 동안 판단을 바꾸지 않게) */
    private var viewerFileBooks: Pair<String, String?>? = null
    /** 서재에서 누른 파일 칸. 읽는 화면에 그 파일 이름이 보이면 편 것으로 본다 */
    private var clickedFiles: Cell? = null
    /** 이번 읽는 화면에서 본 파일 이름(바뀌면 다른 책을 연 것) */
    private var viewerFiles: String? = null

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        // 같은 읽는 화면의 알림이 한 번 더 온 것이면 이 읽는 화면에서 본 파일·누른 칸을 그대로 둔다
        if (!again) {
            viewerFiles = null
            viewerFileBooks = null
            clickedFiles = null
        }
        // 파일 칸은 읽는 화면에 그 파일 이름이 보일 때 편 것으로 본다(누른 뒤 다른 앱으로 다른 파일을 열었을 수 있다)
        takeClicked(ctx)?.let { cell ->
            // 서재 칸을 눌러 연 것이면 앞서 뜬 열기 확인 창(취소했을 수 있다)은 이 읽는 화면에 쓰지 않는다
            openingFiles = null
            // 이 앱의 누른 칸에는 늘 파일이 있다([clickedCell])
            clickedFiles = cell
        }
        // 홈에서 바로 연 책의 파일도 그렇게
        HomeSignals.take(ctx.pkg)?.takeIf { ctx.now - it.second < CLICK_OPEN_MS }?.let { (cell, _) -> if (clickedFiles == null) clickedFiles = cell }
    }

    /** 열기 확인 창(▶·다음 파일)이 뜨면 그 파일을 기억한다 */
    override fun onWindow(ctx: AppContext, cls: String?) = rememberOpening(ctx)

    override fun onClick(ctx: AppContext, click: Click) {
        // 열기 확인 창의 `확인`을 누르는 순간에도 본다(창이 뜰 때 글자가 아직 비어 있었을 수 있다)
        rememberOpening(ctx)
        super.onClick(ctx, click)
    }

    override fun openCell(ctx: AppContext, cell: Cell, at: Long?) = openBook(ctx, cell, at)

    /**
     * 책을 폈다. 파일 칸([Cell.file])과 읽는 화면의 파일 이름으로 정한 책 [id] 는 제목이 아니라 파일로 책을 가른다([fileBookId]):
     * 찾기·넣기 뒤 편 책 기록 전에 제목을 책 정보로 바꾸고 그 파일을 적는다(잘라 둔 표지보다 먼저 — EPUB·그림이면 파일에서 원본 표지를 꺼내게).
     * 잘라 둔 표지는 그 파일 칸의 것만.
     */
    private fun openBook(ctx: AppContext, cell: Cell, at: Long? = null, id: String? = null) {
        val path = cell.file?.let { ev.bookFile(ctx.context, it) }
        val name = cell.file?.substringAfterLast('/')
        val fileId = id ?: cell.file?.let { fileBookId(ctx, it, cell.title, path) }
        val byFile = id != null || cell.file != null
        ctx.opened(Seen(cell.title, cell.author, cell.progress, cell.due), at, if (byFile) Target.Exact(fileId) else Target.ByTitle,
            cell.file?.let(::fileCoverKey), object : OpenSteps {
                override fun found(book: ShelfBook) {
                    // 파일 이름이 제목이던 책(책 정보가 보이기 전에 넣음)은 이제 칸에 보이는 책 정보의 제목·저자로
                    if (fileId != null && name != null) retitle(ctx, book, name, cell)
                    cell.file?.let { rememberShelfFile(ctx, it, path, book) }
                }
            })
        // 그 밖의 표지는 지금(누른 칸이 반전돼 있거나 화면이 넘어가는 중) 찍지 않고, 서재로 돌아와 화면이 멈췄을 때 잘라 온다.
    }

    /** 서재가 멈췄다: 파일 칸은 파일로 책을 정해 그 책에 진행률을 적고 표지 자리는 그 파일 열쇠로(같은 제목의 다른 폴더 파일에 붙지 않게) */
    override fun onShelf(ctx: AppContext) {
        val root = ctx.root ?: return ctx.noWindow()
        // 열기 확인 창이 닫혔는데 읽는 화면이 열리지 않은 채 서재가 멈췄으면 그 창은 취소한 것이다(그 뒤 다른 앱으로 연 같은 이름의 파일에 쓰지 않게)
        // (받는 창 등 그 앱의 다른 창이 떠 있으면 아직 여는 중이다)
        openingFiles?.takeIf { ctx.now - it.second >= OPENING_CANCEL_MS && ctx.windows().all.size <= 1 }?.let { openingFiles = null }
        val seen = readShelf(root)
        ctx.log(WatchLog.Kind.SHELF, "${seen.size}")
        val lookup = ctx.recorder.lookup
        val spots = mutableListOf<CoverSpot>()
        val statuses = mutableListOf<BookShelf.Status>()
        for (s in seen) {
            val key = s.file?.let(::fileCoverKey)
            val book = if (s.file != null) fileBookId(ctx, s.file, s.title, ev.bookFile(ctx.context, s.file))?.let { lookup.get(it) }
                else lookup.byTitle(s.title)
            if (book == null) {
                val thumb = s.thumb?.takeIf(::usableThumb)
                if (thumb != null && (key?.let { !ctx.recorder.hasPendingCover(it) } ?: !ctx.recorder.hasPendingTitle(s.title))) spots += CoverSpot(s.title, thumb, key = key)
                continue
            }
            statuses += BookShelf.Status(book.id, s.progress, s.due, s.author)
            if (s.thumb != null && !lookup.hasCover(book.id) && !ShelfRecorder.coverFromFile(ctx.context, lookup.filePath(book.id), app.fileCovers)) spots += CoverSpot(s.title, s.thumb, book.id, key)
        }
        // 서재 한 화면의 진행률을 한 번에 쓴다
        ctx.recorder.statuses(statuses)
        ctx.capture(spots)
    }

    /**
     * 읽는 화면: 위 줄의 파일 이름([ViewerInfo.file])으로 그 파일로 넣은 책. 처음 보는(바뀐) 파일이면 그때(처음이면 읽는 화면이 열린 때)부터 그 책.
     * 넣은 적 없는 파일을 ▶·다음 파일로 열었으면 확인 창으로 그 파일을 알 때 넣고, 다른 앱으로 연 파일은 넣지 않고 여기부터 책 모름.
     */
    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        val recorder = ctx.recorder
        val lookup = recorder.lookup
        val shownFile = shown.file?.takeIf { shown.title == null }
        val fileAt = shownFile?.let { viewerFileAt(ctx, it) }
        // 서재에서 누른 파일 칸: 그 파일 이름이 보이면 편 것으로 적는다. 열린 직후 앞 파일 이름이 남아 보이는 동안([VIEWER_SWAP_MS])은 기다리고(그때 다시 읽는다),
        // 그 뒤에도 파일 이름이 한 번도 보이지 않으면(이지뷰어 설정으로 위 줄·파일명을 끔) 누른 칸을 편 것으로, 다른 파일이 보이면 버린다
        clickedFiles?.let { cell ->
            val opened = ctx.viewerOpenedAt ?: 0L
            val wait = opened + VIEWER_SWAP_MS - ctx.now
            when {
                fileAt != null && sameFileName(cell.file.orEmpty().substringAfterLast('/'), shownFile.orEmpty()) -> { clickedFiles = null; openBook(ctx, cell, fileAt) }
                wait > 0 -> ctx.viewerAgain(wait)
                viewerFiles != null -> clickedFiles = null
                else -> { clickedFiles = null; openBook(ctx, cell, opened) }
            }
        }
        // 열기 확인 창으로 안 파일(▶·다음 파일). 그 창은 그 뒤 처음 보는 파일에 한 번만 쓴다(같은 이름의 다른 파일을 나중에 열어도 그 파일로 보지 않게)
        val opening = shownFile?.takeIf { fileAt != null }?.let { openingFor(ctx, it) }?.also { openingFiles = null }
        // 파일로 정한 책은 제목이 아니라 id 로 다룬다(폴더가 다른 같은 이름·제목의 책과 섞지 않게)
        val fileBook = shownFile?.let { name ->
            val decided = viewerFileBooks?.takeIf { fileAt == null && it.first == name }
            if (decided != null) decided.second else fileGuess(ctx, name, opening, fresh = fileAt != null).also { viewerFileBooks = name to it }
        }?.let { lookup.bookOf(it) }
        val fileId = fileBook?.id
        // 그 책의 파일을 아직 모르면(서재에서 누를 때는 받기 전이던 원격 파일) 이 읽는 화면에서 처음 볼 때, 서재에서 열 때 본 위치로 다시 찾는다
        if (fileAt != null && fileId != null) lookup.get(fileId)?.takeIf { lookup.filePath(it.id) == null }?.let { book ->
            shelfFileOf(ctx, book.id)?.let { ev.bookFile(ctx.context, it) }?.let { recorder.setFile(book.id, it); fileInfo(ctx, book, it) }
        }
        val info = fileBook?.title?.let { ViewerInfo(it, "", shown.progress) } ?: shown
        val progress = info.progress
        if (progress >= 0 || info.title != null || shownFile != null) ctx.log(WatchLog.Kind.PROGRESS, "$progress", info.title ?: shownFile.orEmpty())
        retryProgress(ctx, progress)
        if (shownFile != null && fileId == null) {
            // 넣은 적 없는 파일을 ▶·다음 파일로 열었다: 확인 창으로 그 파일을 알면 서재에서 연 것처럼 넣는다
            // (실제 경로를 모르면(원격) 확인 창에 보인 위치로. 제목은 파일 이름·그림은 폴더 이름, 읽히는 EPUB 이면 책 정보 [fileInfo])
            if (opening != null) {
                viewerFileBooks = null
                val f = File(opening.path ?: opening.shown)
                val title = if (isImageFile(f.name)) f.parentFile?.name.orEmpty().trimStart('#') else withoutBookFile(f.name)
                return openBook(ctx, Cell(title, "", progress, -1, null, Rect(), file = opening.path ?: opening.shown), fileAt)
            }
            // 다른 앱으로 연 파일은 넣지 않고 여기부터 책 모름(앞서 편 책에 붙이지 않게)
            if (fileAt != null && recorder.openedId(ctx.pkg) != null) ctx.unknown(fileAt)
            return
        }
        // 그림 폴더 책은 쪽을 넘기며 보는 그림을 책의 파일로(홈에서 누르면 그 그림부터 연다)
        if (fileAt != null && fileId != null && isImageFile(shownFile.orEmpty())) lookup.filePath(fileId)?.takeIf(::isImageFile)?.let { cur ->
            File(File(cur).parentFile, shownFile.orEmpty()).takeIf { it.path != cur && it.isFile }?.let { recorder.setFile(fileId, it.path) }
        }
        info.title?.let { title ->
            // 편 책 기록의 책이 이 책(파일로 정한 책은 id)이면 그 책을 읽는 중이다. 아니면 이 책을 편 것으로 다시 적는다(그래야 그 뒤 시간이 이 책에)
            if (recorder.recordedBook(ctx.pkg)?.let { if (fileId != null) it.id == fileId else it.hasTitle(title) } != true) {
                openBook(ctx, Cell(title, info.author, progress, -1, null, Rect()), ctx.unknownOpenAt ?: fileAt, fileId)
                return
            }
        }
        // 진행률은 편 책 기록의 책에('책 모름'이면 적지 않는다)
        if (progress < 0) return
        val book = recorder.recordedBook(ctx.pkg) ?: return
        recorder.status(book.id, progress)
    }

    /**
     * 읽는 화면에 [name] 파일이 보인다: 이 읽는 화면에서 처음 본 파일이면 읽는 화면이 열린 때, 앞서 본 파일과 다르면 지금, 같으면 null.
     * 이지뷰어는 읽는 화면을 다시 쓸 때 위 줄을 늦게 바꿔 처음에 앞 파일 이름이 보이므로, 열린 지 [VIEWER_SWAP_MS] 안에 바뀌면 열린 때로 본다
     */
    private fun viewerFileAt(ctx: AppContext, name: String): Long? {
        val now = ctx.now
        val opened = ctx.viewerOpenedAt
        val before = viewerFiles
        viewerFiles = name
        return when (before) {
            null -> opened ?: now
            name -> null
            else -> opened?.takeIf { now - it < VIEWER_SWAP_MS } ?: now
        }
    }

    /**
     * 읽는 화면에 보이는 파일 이름 [name] 의 책 id. 서재에서 눌러 넣은 책만, 확인한 것만 쓰고 짐작하지 않는다(모르면 null, 다른 책에 붙이지 않는다):
     * - ▶·다음 파일의 열기 확인 창으로 연 파일 [opening] 이면 그 파일로 넣은 책(실제 경로를 모르는 원격 파일은 서재에서 마지막으로 편 파일과 위치·이름이 모두 같을 때)
     * - 서재에서 눌러 이 읽는 화면을 연 책(그 책을 이 읽는 화면이 열릴 무렵에 폈을 때만, [fresh] 가 아니면 같은 화면에서 이어 읽는 중)이
     *   그 이름의 파일로 넣은 책이거나, 위치를 모르는 파일로 넣은 책인데 서재에서 마지막으로 편 파일 이름이 이것이면 그 책
     * 그 밖(다른 앱으로 연 파일, 같은 이름의 책이 한 권 있어도)은 모른다
     */
    private fun fileGuess(ctx: AppContext, name: String, opening: Opening?, fresh: Boolean): String? {
        val lookup = ctx.recorder.lookup
        if (opening != null) return opening.path?.let(lookup::byPath) ?: lastShelfBook(ctx) { it.equals(opening.shown, ignoreCase = true) }
        val justOpened = !fresh || (ctx.lastOpenedAt ?: 0L) >= (ctx.viewerOpenedAt ?: Long.MAX_VALUE) - OPENED_SLACK_MS
        return ctx.recorder.openedId(ctx.pkg)?.takeIf { justOpened }
            ?.takeIf { it in lookup.idsByFileName(name) || inSameFolder(ctx, it, name) || it == lastShelfBook(ctx) { last -> sameFileName(last.substringAfterLast('/'), name) } }
    }

    /**
     * 그림 폴더 책 [bookId] 의 폴더에 그림 [name] 이 있다(쪽을 넘기면 읽는 화면 위 줄의 그림 이름이 바뀐다). 책 파일 읽기가 꺼져 있으면 파일이 있는지 볼 수 없어
     * 그 책이 그림 폴더 책이면 그 폴더의 그림으로 본다(이 읽는 화면에서 그 책을 이미 정했을 때만 묻는다, [fileGuess])
     */
    private fun inSameFolder(ctx: AppContext, bookId: String, name: String): Boolean {
        if (!isImageFile(name)) return false
        val cur = ctx.recorder.lookup.filePath(bookId)?.takeIf(::isImageFile) ?: return false
        return !BookFiles.canRead(ctx.context) || File(File(cur).parentFile, name).isFile
    }

    /** 이 앱 서재에서 마지막으로 편 파일이 [bookId] 책이면 그 파일(화면에 보인 위치 + 이름) */
    private fun shelfFileOf(ctx: AppContext, bookId: String): String? =
        ctx.prefs.getString("shelf_file_${ctx.pkg}", null)?.takeIf { ctx.prefs.getString("shelf_book_${ctx.pkg}", null) == bookId }

    /** 이 앱 서재에서 마지막으로 편 파일(화면에 보인 위치 + 이름)이 [matches] 이고 그 책의 파일(실제 경로)을 모르면 그 책의 id */
    private fun lastShelfBook(ctx: AppContext, matches: (String) -> Boolean): String? {
        ctx.prefs.getString("shelf_file_${ctx.pkg}", null)?.takeIf(matches) ?: return null
        return ctx.prefs.getString("shelf_book_${ctx.pkg}", null)?.takeIf { ctx.recorder.lookup.filePath(it) == null }
    }

    /** 열기 확인 창에 보인 파일을 기억한다([EasyViewSpec.openingFile]) */
    private fun rememberOpening(ctx: AppContext) {
        ev.openingFile(ctx.windows())?.let { openingFiles = it to ctx.now }
    }

    /** 열기 확인 창으로 연 파일: 창에 보인 위치 + 이름 [shown](이름은 읽는 화면의 실제 이름)과 실제 경로 [path](원격 등 모르면 null) */
    private class Opening(val shown: String, val path: String?)

    /** 조금 전 열기 확인 창에 보인 파일의 이름이 [name] 이면 그 파일 */
    private fun openingFor(ctx: AppContext, name: String): Opening? {
        val (shown, at) = openingFiles ?: return null
        // 창을 본 뒤 곧 열린 파일만(확인을 누르고 읽는 화면이 열리기까지). 읽는 화면 안에서 뜬 다음 파일 창은 읽는 화면이 창보다 먼저 열려 있어,
        // 창을 본 지 [CLICK_OPEN_MS] 안이면 쓴다(창이 닫힌 뒤 서재가 멈추면 취소로 보고 지운다, [onShelf])
        if (at < (ctx.viewerOpenedAt ?: 0L) - OPENING_MS || ctx.now - at > CLICK_OPEN_MS) return null
        val shownName = shown.substringAfterLast('/')
        if (!sameFileName(shownName, name)) return null
        // 확인 창의 이름은 목록처럼 대소문자가 바뀌어 보일 수 있어, 같은 이름이면 읽는 화면 위 줄의 이름(실제 이름)으로
        val real = if (shownName.equals(name, ignoreCase = true)) shown.dropLast(shownName.length) + name else shown
        return Opening(real, ev.bookFile(ctx.context, real))
    }

    /**
     * 파일 목록인 서재(이지뷰어)에서 [shown] 파일([Cell.file], 실제 경로 [path])로 [book] 을 폈다: 실제 경로를 알면 책의 파일로 적고(읽는 화면의 파일 이름으로
     * 찾고, EPUB 표지·책 정보를 꺼내고, 홈에서 바로 연다), 그 파일 이름을 이 앱에서 마지막으로 편 책으로 둔다(실제 경로를 모르는 파일도 읽는 화면에서 알아보게)
     */
    private fun rememberShelfFile(ctx: AppContext, shown: String, path: String?, book: ShelfBook) {
        // 이미 다른 파일이 적힌 책(제목이 같은 다른 파일)의 경로는 덮어쓰지 않는다
        path?.takeIf { p -> ctx.recorder.lookup.filePath(book.id)?.equals(p, ignoreCase = true) != false }
            ?.let { ctx.recorder.setFile(book.id, it); fileInfo(ctx, book, it) }
        val edit = ctx.prefs.edit().putString("shelf_file_${ctx.pkg}", shown).putString("shelf_book_${ctx.pkg}", book.id)
        // 원격 파일은 보인 위치(서버 이름) + 이름 → 책으로 따로 적어 둔다(받은 파일을 못 찾아도 다시 열 때 같은 책, 같은 제목의 다른 파일과 섞지 않게)
        if (ev.isRemote(ctx.context, shown)) edit.putString(remotePref(ctx, shown), book.id).putBoolean(remoteBookPref(ctx, book.id), true)
        edit.apply()
    }

    /** 원격 파일 [shown] 으로 넣은 책(읽고 있는 책, 또는 기록에서 지우지 않은 나간 책). 없으면 null */
    private fun remoteBook(ctx: AppContext, shown: String, title: String): String? {
        if (!ev.isRemote(ctx.context, shown)) return null
        val lookup = ctx.recorder.lookup
        return ctx.prefs.getString(remotePref(ctx, shown), null)?.takeIf { lookup.get(it) != null || lookup.withoutFile(title) == it }
    }

    private fun remotePref(ctx: AppContext, shown: String) = "remote_${ctx.pkg}_${shown.lowercase()}"
    private fun remoteBookPref(ctx: AppContext, id: String) = "remote_of_${ctx.pkg}_$id"

    /**
     * 파일 이름이 제목인 책(책 정보가 보이지 않던 칸: 받기 전의 원격 파일·격자 보기·TXT 등)은 그 파일이 읽히는 EPUB 이면 책 정보의 제목·저자로 바꾼다.
     * 앱이 보이던 제목(파일 이름)은 [ShelfBook.seenTitle] 로 남아 서재 칸·편 책 기록과 계속 맞는다([BookShelf.setTitle]).
     */
    private fun fileInfo(ctx: AppContext, book: ShelfBook, path: String) {
        // 사용자가 고친 제목(앱이 보인 제목과 다르다)은 두고, 파일 이름이 제목인 책만
        if (book.seenTitle.isNotEmpty() && book.seenTitle != book.title) return
        if (!book.title.equals(withoutBookFile(File(path).name), ignoreCase = true) || !BookFiles.canRead(ctx.context)) return
        val epub = BookFiles.epub(path) ?: return
        if (epub.title.isNotBlank()) ctx.recorder.setTitle(book.id, epub.title)
        if (book.author.isEmpty() && epub.author.isNotBlank()) ctx.recorder.setAuthor(book.id, epub.author)
    }

    /**
     * 파일 [name] 으로 넣은 책 [book] 의 제목이 그 파일 이름이면(받기 전의 원격 파일·격자 보기에서 넣음) 서재 칸 [cell] 에 보이는 책 정보의 제목·저자로 바꾼다.
     * 앱이 보이던 제목(파일 이름)은 [ShelfBook.seenTitle] 로 남는다
     */
    private fun retitle(ctx: AppContext, book: ShelfBook, name: String, cell: Cell) {
        val stem = withoutBookFile(name)
        if (!book.title.equals(stem, ignoreCase = true) || cell.title.equals(stem, ignoreCase = true)) return
        ctx.recorder.setTitle(book.id, cell.title)
        if (book.author.isEmpty() && cell.author.isNotEmpty()) ctx.recorder.setAuthor(book.id, cell.author)
    }

    /**
     * 파일 칸(파일 목록인 서재, 이지뷰어)에 보인 파일 [shown](제목 [title], 실제 경로 [path])의 책 id. 폴더가 다르면 이름·제목이 같아도 다른 책이다
     * (`만화A/01.zip`·`만화B/01.zip`): 실제 경로를 알면 그 파일로 넣은 책, 없으면 제목이 같고 파일이 적히지 않은 책(파일을 적기 전에 넣은 책).
     * 원격 파일은 그 위치·이름으로 넣은 책([remoteBook], 받은 파일을 나중에 찾아도 같은 책). 실제 경로를 모르면 그 밖에는 서재에서 마지막으로 편 파일과
     * 위치·이름이 모두 같은 책만(제목만 같은 책에 붙이면 다른 폴더의 같은 이름 파일이 섞인다). 원격 파일로 넣은 책은 제목이 같아도 로컬 파일의 책이 아니다.
     * 읽고 있는 책이 먼저, 없으면 나간 책(지운 책은 빼고). 없으면 null(새 책)
     */
    private fun fileBookId(ctx: AppContext, shown: String, title: String, path: String?): String? {
        val lookup = ctx.recorder.lookup
        val remote = remoteBook(ctx, shown, title)
        if (path == null) return remote ?: lastShelfBook(ctx) { it.equals(shown, ignoreCase = true) }
        return lookup.byPath(path) ?: remote ?: lookup.withoutFile(title)?.takeIf { !ctx.prefs.getBoolean(remoteBookPref(ctx, it), false) }
    }

    /** 파일 칸에서 잘라 둔 표지의 열쇠(제목이 아니라 그 파일) */
    private fun fileCoverKey(shown: String) = "file:" + shown.lowercase()

    companion object {
        /** 열기 확인 창을 본 뒤 이만큼 안에 열린 읽는 화면만 그 창의 파일로 본다(읽는 화면이 창보다 먼저 열려 있던 다음 파일 창은 [CLICK_OPEN_MS] 까지) */
        private const val OPENING_MS = 30_000L
        /** 열기 확인 창을 본 지 이만큼 지나 창이 없는데 읽는 화면이 열리지 않았으면 취소한 것으로 본다(큰 파일·원격 받기는 읽는 화면이 늦게 뜬다) */
        private const val OPENING_CANCEL_MS = 10_000L
        /** 읽는 화면이 열린 뒤 위 줄의 파일 이름이 이만큼 안에 바뀌면 앞 파일 이름이 남아 있던 것 */
        private const val VIEWER_SWAP_MS = 5_000L
        /** 서재에서 눌러 연 책은 읽는 화면이 열리기 조금 전에 편 것으로 적힐 수 있다 */
        private const val OPENED_SLACK_MS = 5_000L
    }
}
