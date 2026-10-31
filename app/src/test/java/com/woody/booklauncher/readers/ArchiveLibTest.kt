package com.woody.booklauncher.readers

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/** 받아 쓰는 libarchive([ArchiveLib]): 받을 파일 고르기, 받은 파일 확인, 풀다가 죽은 파일 기억. 네이티브는 여기서 부르지 않는다 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArchiveLibTest {
    @get:Rule val tmp = TemporaryFolder()

    private val app get() = RuntimeEnvironment.getApplication()

    @After fun tearDown() {
        ArchiveLib.resetForTest()
        File(com.woody.booklauncher.Storage.of(app).filesDir, "addons").deleteRecursively()
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun `도는 방식에 맞는 파일 - 64비트는 arm64, 32비트는 armv7, x86 은 없다`() {
        assertEquals("arm64-v8a", ArchiveLib.asset(true, arrayOf("arm64-v8a"), arrayOf("armeabi-v7a", "armeabi"))?.abi)
        // 32·64비트를 함께 돌리는 기기에서 32비트로 돌면 armv7
        assertEquals("armeabi-v7a", ArchiveLib.asset(false, arrayOf("arm64-v8a"), arrayOf("armeabi-v7a", "armeabi"))?.abi)
        assertNull(ArchiveLib.asset(true, arrayOf("x86_64"), arrayOf("x86")))
        assertNull(ArchiveLib.asset(false, emptyArray(), arrayOf("x86", "armeabi-v7a")))
    }

    @Test fun `받은 파일을 풀어 크기·SHA-256 이 맞으면 읽기 전용으로 둔다`() {
        val so = ByteArray(5000) { (it * 31).toByte() }
        val gz = File(tmp.root, "a.so.gz").also { f -> GZIPOutputStream(f.outputStream()).use { it.write(so) } }
        val target = File(tmp.root, "lib/a.so").apply { parentFile!!.mkdirs() }
        ArchiveLib.unpack(gz, target, ArchiveLib.Asset("arm64-v8a", gz.length(), "", so.size.toLong(), sha(so)))
        assertArrayEquals(so, target.readBytes())
        assertFalse(target.canWrite())
        assertFalse(File(target.path + ".tmp").exists())
    }

    @Test fun `받은 파일이 다르면 두지 않는다`() {
        val so = ByteArray(5000) { it.toByte() }
        val gz = File(tmp.root, "a.so.gz").also { f -> GZIPOutputStream(f.outputStream()).use { it.write(so) } }
        val target = File(tmp.root, "b.so")
        val wrongSha = ArchiveLib.Asset("arm64-v8a", gz.length(), "", so.size.toLong(), "0".repeat(64))
        assertTrue(runCatching { ArchiveLib.unpack(gz, target, wrongSha) }.isFailure)
        // 알려 준 크기보다 크면 다 풀기 전에 멈춘다
        val tooBig = ArchiveLib.Asset("arm64-v8a", gz.length(), "", 100, sha(so))
        assertTrue(runCatching { ArchiveLib.unpack(gz, target, tooBig) }.isFailure)
        assertFalse(target.exists())
        assertFalse(File(target.path + ".tmp").exists())
    }

    @Test fun `풀다가 앱이 죽은 파일은 다음에 떠서 다시 열지 않는다`() {
        ArchiveLib.mark(app, "/sdcard/a.cbr@1@2")
        // 앱이 다시 뜬 것처럼
        ArchiveLib.resetForTest()
        ArchiveLib.recover(app)
        assertTrue(ArchiveLib.isBad(app, "/sdcard/a.cbr@1@2"))
        assertFalse(ArchiveLib.isBad(app, "/sdcard/b.cbr@1@2"))
        // 끝까지 푼 파일은 남지 않는다
        ArchiveLib.mark(app, "/sdcard/b.cbr@1@2")
        ArchiveLib.unmark(app)
        ArchiveLib.resetForTest()
        ArchiveLib.recover(app)
        assertFalse(ArchiveLib.isBad(app, "/sdcard/b.cbr@1@2"))
    }

    @Test fun `다시 받으면 풀다가·불러오다 꺼졌던 기억을 지운다`() {
        val so = ByteArray(3000) { (it * 7).toByte() }
        val gz = File(tmp.root, "c.so.gz").also { f -> GZIPOutputStream(f.outputStream()).use { it.write(so) } }
        val a = ArchiveLib.Asset("arm64-v8a", gz.length(), "", so.size.toLong(), sha(so))
        ArchiveLib.mark(app, "load:${sha(so)}")
        ArchiveLib.resetForTest()
        ArchiveLib.recover(app)
        assertTrue(ArchiveLib.isBad(app, "load:${sha(so)}"))
        ArchiveLib.install(app, gz, a)
        assertFalse(ArchiveLib.isBad(app, "load:${sha(so)}"))
        assertArrayEquals(so, File(com.woody.booklauncher.Storage.of(app).filesDir, "addons/${a.name}").readBytes())
    }

    @Test fun `받지 않았으면 압축 만화는 표지가 없다고 본다`() {
        val cbr = File(tmp.root, "x.cbr").apply { writeBytes("Rar!".toByteArray()) }
        assertFalse(BookFiles.WITH_COMICS.has(app, cbr.path))
        assertNull(BookFiles.WITH_COMICS.cover(app, cbr.path))
        assertTrue(BookFiles.isArchiveComic("a/B.CB7") && BookFiles.isArchiveComic("x.rar") && !BookFiles.isArchiveComic("x.cbz"))
    }
}
