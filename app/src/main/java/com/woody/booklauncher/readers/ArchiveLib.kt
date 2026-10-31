package com.woody.booklauncher.readers

import android.content.Context
import android.os.Build
import android.os.Process
import com.woody.booklauncher.Bg
import com.woody.booklauncher.Storage
import com.woody.booklauncher.Update
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * 압축 만화(CBR·RAR·CB7·7z·CBT·TAR)를 푸는 libarchive(`me.zhanghai.android.libarchive` 1.1.7, Maven Central)의 네이티브 라이브러리를
 * 필요한 사람만 받아 쓴다(설정 › 독서 › 만화 표지 압축 해제). 만화를 보지 않는 사람의 APK 를 키우지 않으려고 앱에 넣지 않는다.
 * - 받는 곳: 이 앱의 GitHub 릴리스 [TAG](앱 업데이트가 보는 최신 릴리스가 되지 않게 시험판으로 둔다). 이 앱이 도는 방식(64·32비트)에 맞는 파일 하나.
 * - 크기·SHA-256 은 여기 박아 두고 받을 때(gzip)와 불러올 때(.so) 본다(받은 파일이 바뀌어도 다른 코드를 부르지 않게).
 * - 받은 파일은 기기 보호 저장소에 읽기 전용으로 두고(안드로이드 14 의 동적 코드 규칙) System.load 로 부른다. Java 부분은 앱에 넣었고
 *   거기서 loadLibrary 한 줄만 뺐다(네이티브 함수는 이름으로 이어진다).
 * - 풀다가 앱이 죽으면(깨진 파일·메모리) 홈이 계속 죽지 않게, 풀기 전에 [MARK] 에 적고 끝나면 지운다. 다음에 떠서 남아 있으면 [BAD] 로 옮겨
 *   그 파일(같은 수정 시각)은 다시 열지 않는다. 불러오다 죽어도 같다.
 * (2026-10-26 에뮬레이터 안드로이드 10·11·14·15(16KB 페이지)·크레마 32비트에서 시험, docs/READER_APPS.md › KOReader)
 */
object ArchiveLib {
    const val VERSION = "1.1.7"
    private const val TAG = "libarchive-$VERSION"
    private const val BASE = "https://github.com/hissinger/pebble-desk/releases/download/$TAG/"
    private const val MARK = "archive_working"
    private const val BAD = "archive_bad"
    /** 그림 하나가 이보다 크면 꺼내지 않는다(메모리) */
    private const val MAX_IMAGE = 64L shl 20

    /** 받을 파일: gzip 으로 묶은 것의 크기·SHA-256, 푼 .so 의 크기·SHA-256(AAR 의 `jni/<abi>/libarchive-jni.so`) */
    internal class Asset(val abi: String, val gzSize: Long, val gzSha256: String, val soSize: Long, val soSha256: String) {
        val name get() = "libarchive-jni-$abi.so"
    }

    internal val ARM64 = Asset(
        "arm64-v8a", 1_108_863, "b942bd6d9b40bbb7bc7fd00efa736c22eeb26747db29b8501d3cf164f4277f48",
        2_406_200, "435a83d238e251be55bc50f597dba0ce0f88dd7459c4e87bb992930498931985",
    )
    internal val ARMV7 = Asset(
        "armeabi-v7a", 1_071_987, "9472474c2f2ddedeec525a2f2f5d3e98fd9a28673f67526e5f958366b21eba9f",
        1_842_532, "3e20bc3779df48c7bfa149211e7427726f5172082a757cac588a855ea5c6f72c",
    )

    /**
     * 이 앱이 도는 방식에 맞는 파일: 64비트면 arm64, 32비트면 armv7(32·64비트를 함께 돌리는 기기에서 32비트로 돌 수도 있어 기기 목록이 아니라 지금
     * 프로세스로 고른다). 그 방식의 첫 ABI 가 ARM 이 아니면(x86) 없다.
     */
    internal fun asset(is64: Boolean = Process.is64Bit(), abis64: Array<String> = Build.SUPPORTED_64_BIT_ABIS, abis32: Array<String> = Build.SUPPORTED_32_BIT_ABIS) =
        if (is64) ARM64.takeIf { abis64.firstOrNull() == it.abi } else ARMV7.takeIf { abis32.firstOrNull() == it.abi }

    /** 이 기기에서 받아 쓸 수 있다 */
    fun supported() = asset() != null

    /** 받을 크기(gzip) */
    fun downloadSize() = asset()?.gzSize ?: 0L

    private fun dir(context: Context) = File(Storage.of(context).filesDir, "addons")
    private fun file(context: Context, a: Asset) = File(dir(context), a.name)

    /** 받아 두었다(크기가 맞다. 내용은 불러올 때 본다) */
    fun installed(context: Context): Boolean = asset()?.let { file(context, it).length() == it.soSize } == true

    /** 받는 중 */
    @Volatile var downloading = false
        private set

    /** 뒤에서 받아 확인하고 둔다. 끝나면 화면 스레드에서 [done]. 받는 중이면 아무것도 안 한다 */
    fun download(context: Context, done: (Result<Unit>) -> Unit) {
        if (downloading) return
        val app = context.applicationContext
        downloading = true
        Bg.run({
            val a = asset() ?: throw IOException("unsupported ABI")
            val gz = File(app.cacheDir, "${a.name}.gz")
            try {
                Update.fetch(BASE + a.name + ".gz", a.gzSize, a.gzSha256, gz) {}
                install(app, gz, a)
            } finally {
                gz.delete()
            }
        }) { r ->
            downloading = false
            done(r)
        }
    }

    /**
     * 받은 gzip [gz] 를 풀어 둔다([unpack]). 그리고 기억해 둔 실패를 잊는다: 불러오다 앱이 꺼진 적이 있으면(시스템이 메모리 때문에 죽였을 수도 있다)
     * 같은 파일이라 다시 받아도 쓰지 않게 되므로, 사용자가 다시 받으면 다시 해 본다.
     */
    @Synchronized internal fun install(context: Context, gz: File, a: Asset) {
        unpack(gz, file(context, a).apply { parentFile?.mkdirs() }, a)
        File(dir(context), BAD).delete()
        File(dir(context), MARK).delete()
        loadFailed = false
    }

    /** 받은 gzip [gz] 를 풀어 [a] 인지(크기·SHA-256) 보고 [target] 에 읽기 전용으로 둔다. 다르면 두지 않는다 */
    internal fun unpack(gz: File, target: File, a: Asset) {
        val tmp = File(target.path + ".tmp").apply { delete() }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            GZIPInputStream(gz.inputStream()).use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > a.soSize) throw IOException("larger than expected")
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            if (tmp.length() != a.soSize || !digest.digest().hex().equals(a.soSha256, ignoreCase = true)) throw IOException("size or SHA-256 mismatch")
            tmp.setReadOnly()
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("rename failed")
        } finally {
            tmp.delete()
        }
    }

    /** 받은 파일을 지운다(이미 불러온 것은 앱이 다시 뜰 때까지 남지만, 받지 않은 것으로 보고 쓰지 않는다. 꺼내 둔 표지는 남는다) */
    fun remove(context: Context) {
        dir(context).listFiles { f -> f.name.startsWith("libarchive-jni-") }?.forEach { it.delete() }
    }

    /** 이 앱이 떠서 불러왔는가, 불러오지 못했는가(앱이 다시 뜰 때까지 다시 해 보지 않는다) */
    private var loaded = false
    private var loadFailed = false
    /** 이 앱이 뜬 뒤 [MARK] 를 [BAD] 로 옮겼는가 */
    private var recovered = false

    /**
     * 압축 만화 [path] 의 첫 그림(이름 순, 숫자는 크기 순, [BookFiles.isComicPage])의 바이트. 받지 않았거나, 못 풀거나, 그림이 없으면 null.
     * [key] 는 그 파일(경로·수정 시각): 풀다가 앱이 죽은 파일이면 열지 않는다. 한 번에 하나만 푼다. 화면 스레드에서 부르지 않는다.
     * 솔리드 묶음은 뒤 항목으로 가려면 앞을 풀어야 해서 오래 걸릴 수 있다(한 번만 꺼내 표지로 둔다).
     */
    @Synchronized fun firstImage(context: Context, path: String, key: String): ByteArray? {
        if (!installed(context)) return null
        recover(context)
        if (key in bad(context) || !load(context)) return null
        mark(context, key)
        try {
            return runCatching { read(path) }.getOrNull()
        } finally {
            unmark(context)
        }
    }

    private fun load(context: Context): Boolean {
        if (loaded) return true
        if (loadFailed) return false
        val a = asset() ?: return false
        val f = file(context, a)
        val key = "load:${a.soSha256}"
        // 불러오다 죽은 적이 있거나, 내용이 받을 때와 다르면 부르지 않는다
        if (key in bad(context) || !f.readBytes().sha256().equals(a.soSha256, ignoreCase = true)) {
            loadFailed = true
            return false
        }
        mark(context, key)
        try {
            System.load(f.path)
            loaded = true
        } catch (e: Throwable) {
            loadFailed = true
        } finally {
            unmark(context)
        }
        return loaded
    }

    /** 첫 그림 고르기: 한 번 훑으며 지금까지 가장 앞 이름보다 앞인 그림만 읽는다(보통 묶음이 이름 순이라 첫 그림 하나만 읽는다) */
    private fun read(path: String): ByteArray? {
        val a = Archive.readNew()
        try {
            Archive.readSupportFilterAll(a)
            Archive.readSupportFormatAll(a)
            Archive.readOpenFileName(a, path.toByteArray(), 16 * 1024)
            var bestName: String? = null
            var best: ByteArray? = null
            while (true) {
                val e = Archive.readNextHeader(a)
                if (e == 0L) break
                if (ArchiveEntry.filetype(e) != ArchiveEntry.AE_IFREG) continue
                val name = ArchiveEntry.pathnameUtf8(e) ?: continue
                if (!BookFiles.isComicPage(name) || bestName?.let { BookFiles.NATURAL.compare(name, it) >= 0 } == true) continue
                val size = ArchiveEntry.size(e)
                if (size > MAX_IMAGE) continue
                best = readEntry(a, size)
                bestName = name
            }
            return best
        } finally {
            Archive.free(a)
        }
    }

    private fun readEntry(archive: Long, size: Long): ByteArray {
        val out = ByteArrayOutputStream(size.coerceIn(0, MAX_IMAGE).toInt())
        val buf = ByteBuffer.allocateDirect(64 * 1024)
        val chunk = ByteArray(buf.capacity())
        while (true) {
            buf.clear()
            Archive.readData(archive, buf)
            val n = buf.position()
            if (n == 0) break
            buf.flip()
            buf.get(chunk, 0, n)
            out.write(chunk, 0, n)
            if (out.size() > MAX_IMAGE) throw IOException("image too large")
        }
        return out.toByteArray()
    }

    /** 앱이 뜬 뒤 처음: 지난번에 풀다가(불러오다) 죽어 [MARK] 가 남았으면 그 파일을 [BAD] 로 */
    internal fun recover(context: Context) {
        if (recovered) return
        recovered = true
        val mark = File(dir(context), MARK)
        if (!mark.exists()) return
        val key = mark.readText()
        if (key.isNotEmpty()) File(dir(context), BAD).appendText("$key\n")
        mark.delete()
    }

    private fun bad(context: Context): Set<String> = File(dir(context), BAD).takeIf { it.exists() }?.readLines()?.toSet().orEmpty()

    internal fun mark(context: Context, key: String) = File(dir(context).apply { mkdirs() }, MARK).writeText(key)
    internal fun unmark(context: Context) { File(dir(context), MARK).delete() }

    /** 시험: 앱이 다시 뜬 것처럼 */
    internal fun resetForTest() { loaded = false; loadFailed = false; recovered = false }

    internal fun isBad(context: Context, key: String) = key in bad(context)

    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).hex()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
