package com.woody.booklauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** 기기 정보에 보이는 마지막 앱 오류 두 줄([CrashLog.describe]) */
class CrashLogTest {
    private val at = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 26, 9, 48, 0) }.timeInMillis

    private fun frame(cls: String, file: String, line: Int) = StackTraceElement(cls, "run", file, line)

    @Test fun `가장 안쪽 원인이 던진 이 앱 코드 자리를 세 개까지`() {
        val root = IllegalStateException("database is locked").apply {
            stackTrace = arrayOf(
                frame("android.database.sqlite.SQLiteConnection", "SQLiteConnection.java", 10),
                frame("com.woody.booklauncher.Db", "Db.kt", 41),
                frame("com.woody.booklauncher.ReadingLog", "Reading.kt", 300),
                frame("com.woody.booklauncher.ReaderWatchService", "ReaderWatch.kt", 51),
                frame("com.woody.booklauncher.WatchCore", "WatchCore.kt", 9),
            )
        }
        val e = RuntimeException("wrapped", root)
        assertEquals(
            "10/26 09:48 · 1.6.1 · main · RuntimeException: wrapped\n" +
                "Db.kt:41 ← Reading.kt:300 ← ReaderWatch.kt:51 · IllegalStateException: database is locked",
            CrashLog.describe(at, "1.6.1", "main", e),
        )
    }

    @Test fun `릴리스(R8)에서 이름이 바뀐 이 앱 클래스도 이 앱 코드로 본다`() {
        val e = IllegalStateException("x").apply {
            stackTrace = arrayOf(frame("java.util.ArrayList", "ArrayList.java", 1), frame("h4", "r8-map-id-7994", 41), frame("kotlin.collections.CollectionsKt", "Collections.kt", 2), frame("bg", "r8-map-id-7994", 88))
        }
        // 파일 이름이 mapping 열쇠라 클래스.메서드:줄(그 판의 mapping.txt 로 되찾는다)
        assertEquals("10/26 09:48 · 1.6.1 · main · IllegalStateException: x\nh4.run:41 ← bg.run:88", CrashLog.describe(at, "1.6.1", "main", e))
    }

    @Test(timeout = 5_000) fun `원인 사슬이 돌아도 멈추지 않는다`() {
        val a = RuntimeException("a")
        val b = IllegalStateException("b", a)
        a.initCause(b)
        // a → b → a … 를 8 단계(a, b, a, b, a, b, a, b)까지만 따라가 b 에서 멈춘다
        val second = CrashLog.describe(at, "1.6.1", "main", a).lines()[1]
        assertEquals("IllegalStateException: b", second.substringAfter(" · "))
    }

    @Test fun `이 앱 코드가 없으면 맨 위 자리, 원인이 없으면 이름을 되풀이하지 않는다`() {
        val e = NullPointerException().apply { stackTrace = arrayOf(frame("android.view.View", "View.java", 7)) }
        assertEquals("10/26 09:48 · 1.6.1 · main · NullPointerException\nView.java:7", CrashLog.describe(at, "1.6.1", "main", e))
    }
}
