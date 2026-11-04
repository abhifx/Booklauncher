package com.woody.booklauncher.readers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Xml
import com.woody.booklauncher.Storage
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 기기 저장소의 책 파일 읽기(KOReader 처럼 화면에 글자를 드러내지 않는 앱이 남긴 기록과 책 파일). 안드로이드 11+ 은 '모든 파일 액세스',
 * 그 전은 저장소 읽기 권한이 있어야 한다([canRead]). 사용자가 설정 › 독서에서 허용한다([request]).
 */
object BookFiles {
    /** 저장소의 다른 앱 파일(KOReader 기록·책 옆 `.sdr`·책 파일)을 읽을 수 있는가 */
    fun canRead(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /**
     * 허용하러 간다: 11+ 은 이 앱의 '모든 파일 액세스' 시스템 화면(없으면 목록), 그 전은 권한 창. 그 전 판에서 '다시 묻지 않음'으로 거절해
     * 권한 창이 더는 뜨지 않으면(한 번 물었는데 안내도 못 띄움) 이 앱의 앱 정보(권한)를 연다.
     */
    fun request(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}")))
            }.recoverCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            return
        }
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        val prefs = Storage.of(activity).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(ASKED, false) && !activity.shouldShowRequestPermissionRationale(permission)) {
            runCatching { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))) }
            return
        }
        prefs.edit().putBoolean(ASKED, true).apply()
        activity.requestPermissions(arrayOf(permission), 0)
    }

    private const val PREFS = "book_files"
    private const val ASKED = "asked"

    /** EPUB 책 정보: 제목·저자(여럿이면 `, `)와 표지 그림의 묶음 안 경로(없으면 null) */
    class Epub(val title: String, val author: String, val cover: String?)

    /** EPUB 의 책 정보(EPUB 이 아니거나 못 읽으면 null) */
    fun epub(path: String): Epub? {
        if (!path.endsWith(".epub", ignoreCase = true)) return null
        return runCatching { ZipFile(path).use { zip -> readEpub(zip) } }.getOrNull()
    }

    /**
     * 책 파일에서 표지를 꺼내는 길. 어느 길을 쓸지는 이북 앱이 자기 파일에서 고른다([ReaderApp.fileCovers]): 기본은 [BOOKS](EPUB·그림 폴더),
     * 만화를 여는 앱은 [WITH_COMICS](그 위에 만화 묶음의 첫 그림), PDF 첫 쪽도 쓰는 앱은 [withPdf] 로 감싼 것. 공통 흐름(홈이 그릴 때·화면에서 자를지)은 그 책을 넣은 앱의 길만 부른다.
     */
    interface Covers {
        /** [path] 에서 표지를 꺼낼 수 있다(화면에서 자르지 않을지 정할 때라 가볍게: 파일마다 한 번 읽어 기억하거나 열지 않고 정한다) */
        fun has(context: Context, path: String): Boolean
        /** [path] 의 표지(JPEG, 긴 변을 [COVER_PX] 안으로 줄임). 없거나 못 읽으면 null. 큰 그림·압축을 다루므로 화면 스레드에서 부르지 않는다 */
        fun cover(context: Context, path: String): ByteArray?
        /** [path] 에서 꺼내는 데 오래 걸릴 수 있다(화면 스레드에서는 꺼내지 않고 뒤에서) */
        fun slow(path: String): Boolean = false
    }

    /** EPUB 은 책 정보의 표지 그림, 그림 파일(그림 폴더 한 권, 이지뷰어)은 그 폴더의 첫 그림(이름 순, 숫자는 크기 순) */
    val BOOKS: Covers = object : Covers {
        override fun has(context: Context, path: String) =
            isImageFile(path) || (path.endsWith(".epub", ignoreCase = true) && hasZipCover(path) { readEpub(it)?.cover?.let(it::getEntry) })

        override fun cover(context: Context, path: String): ByteArray? = runCatching {
            if (isImageFile(path)) {
                val first = File(path).parentFile?.listFiles { f -> f.isFile && isImageFile(f.name) }?.minWithOrNull(compareBy(NATURAL) { it.name }) ?: return null
                return scaledJpeg { first.inputStream() }
            }
            zipCover(path) { readEpub(it)?.cover?.let(it::getEntry) }
        }.getOrNull()
    }

    /**
     * [BOOKS] 에 더해 만화 묶음은 묶음 안 첫 그림: ZIP(CBZ·ZIP)은 안드로이드 기본으로, CBR·CB7 등은 받아 둔 [ArchiveLib] 로
     * (받지 않았으면 없다. 받았으면 열어 보지 않고 있다고 본다: 솔리드 묶음은 훑기만 해도 오래 걸린다)
     */
    val WITH_COMICS: Covers = object : Covers {
        override fun has(context: Context, path: String) = when {
            isArchiveComic(path) -> ArchiveLib.installed(context)
            isZipComic(path) -> hasZipCover(path, ::comicCover)
            else -> BOOKS.has(context, path)
        }

        override fun cover(context: Context, path: String): ByteArray? = when {
            isArchiveComic(path) -> runCatching {
                ArchiveLib.firstImage(context, path, fileKey(File(path)))?.let { bytes -> scaledJpeg { bytes.inputStream() } }
            }.getOrNull()
            isZipComic(path) -> runCatching { zipCover(path, ::comicCover) }.getOrNull()
            else -> BOOKS.cover(context, path)
        }

        override fun slow(path: String) = isArchiveComic(path)
    }

    /**
     * [base] 에 더해 PDF 는 첫 쪽을 그린 그림(안드로이드 기본 `PdfRenderer`). 쓰는 앱: Librera.
     * 있는지는 열지 않고 파일 머리(앞 1024바이트 안의 `%PDF-`)로만 본다(화면 스레드에서 묻는다). 그려 보다 못 그린 PDF(암호 등)는 기억해 두고 그 뒤로는 없다고 답한다
     * (그 책은 화면에서 자른다). 큰 PDF 는 여는 데 오래 걸릴 수 있어 늘 뒤에서 꺼낸다
     */
    fun withPdf(base: Covers): Covers = object : Covers {
        override fun has(context: Context, path: String) = if (isPdf(path)) hasPdfCover(path) else base.has(context, path)
        override fun cover(context: Context, path: String): ByteArray? = if (isPdf(path)) pdfCover(path) else base.cover(context, path)
        override fun slow(path: String) = isPdf(path) || base.slow(path)
    }

    private fun isPdf(path: String) = path.endsWith(".pdf", ignoreCase = true)

    /**
     * PDF [path] 의 앞 [PDF_HEAD] 바이트 안에 `%PDF-` 가 있다(PDF 리더들도 머리 앞에 다른 바이트가 붙은 파일을 연다).
     * 파일마다 한 번 읽어 기억한다. 못 그린 PDF 는 [pdfCover] 가 거짓으로 바꿔 둔다
     */
    private fun hasPdfCover(path: String): Boolean = fileCovers.getOrPut(fileKey(File(path))) {
        runCatching {
            val head = ByteArray(PDF_HEAD)
            // `readNBytes` 는 안드로이드 13 부터라 끝이나 다 찰 때까지 직접 읽는다
            val n = File(path).inputStream().use { s ->
                var read = 0
                while (read < head.size) { val r = s.read(head, read, head.size - read); if (r < 0) break; read += r }
                read
            }
            String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF-")
        }.getOrDefault(false)
    }
    private const val PDF_HEAD = 1024

    /** PDF [path] 의 첫 쪽을 긴 변 [COVER_PX] 안으로 흰 바탕에 그린 JPEG. 못 그리면(쪽이 없음·암호·깨진 파일) null 이고 표지가 없다고 기억한다 */
    private fun pdfCover(path: String): ByteArray? = runCatching {
        openPdf(path) { pdf ->
            if (pdf.pageCount <= 0) return@openPdf null
            pdf.openPage(0).use { page ->
                if (page.width <= 0 || page.height <= 0) return@openPdf null
                val scale = COVER_PX.toFloat() / maxOf(page.width, page.height)
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                try {
                    // PDF 바탕은 투명이라 흰 종이로 깐다
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }.getOrNull().also { if (it == null) fileCovers[fileKey(File(path))] = false }

    /** PDF [path] 를 열어 [block] 에 준다. `PdfRenderer` 는 여러 스레드에서 함께 쓰지 않는다(한 번에 하나) */
    private fun <T> openPdf(path: String, block: (PdfRenderer) -> T): T = synchronized(pdfLock) {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use(block) }
    }
    private val pdfLock = Any()

    /** ZIP 묶음 [path] 의 [entry] 항목이 있고 그림으로 읽힌다(파일마다 한 번 읽어 기억한다). 못 읽으면 아니다 */
    private fun hasZipCover(path: String, entry: (ZipFile) -> ZipEntry?): Boolean = fileCovers.getOrPut(fileKey(File(path))) {
        runCatching {
            ZipFile(path).use { zip ->
                val e = entry(zip) ?: return@use false
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                zip.getInputStream(e).use { BitmapFactory.decodeStream(it, null, bounds) }
                bounds.outWidth > 0 && bounds.outHeight > 0
            }
        }.getOrDefault(false)
    }

    /** ZIP 묶음 [path] 의 [entry] 항목을 표지로. 없으면 null */
    private fun zipCover(path: String, entry: (ZipFile) -> ZipEntry?): ByteArray? = ZipFile(path).use { zip ->
        val e = entry(zip) ?: return null
        scaledJpeg { zip.getInputStream(e) }
    }

    /** 파일을 기억하는 열쇠(경로·고친 때·길이. 고친 때를 두고 덮어쓴 파일도 다시 읽게) */
    private fun fileKey(f: File) = "${f.path}@${f.lastModified()}@${f.length()}"

    /** 파일(`경로@고친 때`)마다 표지가 있는지 */
    private val fileCovers = ConcurrentHashMap<String, Boolean>()

    /** ZIP 으로 묶은 만화(CBZ·ZIP. KOReader·이지뷰어는 ZIP 도 만화로 연다)인가: 안드로이드 기본으로 푼다 */
    private fun isZipComic(path: String) = ZIP_COMIC.containsMatchIn(path)
    private val ZIP_COMIC = Regex("""\.(cbz|zip)$""", RegexOption.IGNORE_CASE)

    /** 그 밖의 압축 만화(CBR·RAR·CB7·7z·CBT·TAR)인가: 받아 둔 [ArchiveLib] 로만 푼다 */
    internal fun isArchiveComic(path: String) = ARCHIVE_COMIC.containsMatchIn(path)
    private val ARCHIVE_COMIC = Regex("""\.(cbr|rar|cb7|7z|cbt|tar)$""", RegexOption.IGNORE_CASE)

    /** ZIP 만화의 첫 그림([isComicPage] 가운데 이름 순, 숫자는 크기 순. KOReader 가 쓰는 MuPDF 도 이 순서로 쪽을 놓는다) */
    internal fun comicCover(zip: ZipFile): ZipEntry? = zip.entries().asSequence()
        .filter { e -> !e.isDirectory && isComicPage(e.name) }
        .minWithOrNull(compareBy(NATURAL) { it.name })

    /** 만화 묶음 안의 쪽 그림인가: 그림 파일이고 숨긴 항목(`.`)·`__MACOSX` 아래가 아니다 */
    internal fun isComicPage(name: String) = isImageFile(name) && name.split('/', '\\').none { it.startsWith('.') || it == "__MACOSX" }

    /** 그림 파일 [file] 하나를 표지로(이북 앱이 따로 둔 표지 그림, Readest `cover.png`). 못 읽으면 null. 화면 스레드에서 부르지 않는다. */
    fun imageCover(file: File): ByteArray? = streamCover { file.inputStream() }

    /** [open] 이 여는 그림 하나를 표지로(앱이 읽기를 허락한 content URI, iReader). 못 읽으면 null. 화면 스레드에서 부르지 않는다. */
    internal fun streamCover(open: () -> InputStream): ByteArray? = runCatching { scaledJpeg(open) }.getOrNull()

    /** [open] 이 여는 그림을 긴 변 [COVER_PX] 안으로 줄인 JPEG(그림이 아니면 null). 두 번 연다(크기 보기·줄여 읽기) */
    private fun scaledJpeg(open: () -> InputStream): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= COVER_PX) sample *= 2
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val scale = COVER_PX.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            .also { decoded.recycle() } else decoded
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); bitmap.recycle() }.toByteArray()
    }

    /** 파일 이름 순서(대소문자 무시, 숫자는 크기 순: `p2` 가 `p10` 앞) */
    internal val NATURAL = Comparator<String> { a, b ->
        val x = DIGITS.findAll(a.lowercase()).map { it.value }.toList()
        val y = DIGITS.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val (p, q) = x[i] to y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) compareValuesBy(p, q, { it.trimStart('0').length }, { it.trimStart('0') }) else p.compareTo(q)
            if (c != 0) return@Comparator c
        }
        x.size - y.size
    }
    private val DIGITS = Regex("""\d+|\D+""")

    /** EPUB 묶음: `META-INF/container.xml` 이 가리키는 OPF 의 제목·저자·표지 */
    private fun readEpub(zip: ZipFile): Epub? {
        val opfPath = zip.getEntry("META-INF/container.xml")?.let { e ->
            zip.getInputStream(e).use { s -> firstAttr(s, "rootfile", "full-path") }
        } ?: return null
        val opf = zip.getEntry(opfPath) ?: return null
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        var title = ""
        val authors = mutableListOf<String>()
        var coverId: String? = null
        val items = mutableMapOf<String, String>()
        var coverImage: String? = null
        zip.getInputStream(opf).use { s ->
            val p = parser(s)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.name.substringAfter(':')) {
                    "title" -> if (title.isEmpty()) title = p.nextText().trim()
                    "creator" -> p.nextText().trim().takeIf { it.isNotEmpty() }?.let(authors::add)
                    "meta" -> if (p.attr("name") == "cover") coverId = p.attr("content")
                    "item" -> {
                        val id = p.attr("id")
                        val href = p.attr("href") ?: continue
                        if (id != null) items[id] = href
                        if (p.attr("properties")?.split(' ')?.contains("cover-image") == true) coverImage = href
                    }
                }
            }
        }
        // 경로는 URI 라 `+` 는 글자 그대로다(URLDecoder 는 공백으로 바꾼다)
        val cover = (coverImage ?: coverId?.let(items::get))?.let { base + URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }?.let(::normalize)
        return Epub(title, authors.joinToString(", "), cover)
    }

    /** `OEBPS/../Images/a.jpg` → `Images/a.jpg` */
    private fun normalize(path: String): String {
        val out = ArrayList<String>()
        for (part in path.split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
            else -> out += part
        }
        return out.joinToString("/")
    }

    private fun parser(s: InputStream): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(s, null)
    }

    private fun XmlPullParser.attr(name: String): String? =
        (0 until attributeCount).firstOrNull { getAttributeName(it).substringAfter(':') == name }?.let(::getAttributeValue)

    private fun firstAttr(s: InputStream, tag: String, name: String): String? {
        val p = parser(s)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name.substringAfter(':') == tag) return p.attr(name)
        }
        return null
    }

    /** 표지의 긴 변(홈의 큰 표지보다 조금 크게) */
    private const val COVER_PX = 900

    /** 기기 저장소 맨 위(`/storage/emulated/0`) */
    val storageRoot: File get() = Environment.getExternalStorageDirectory()
}
