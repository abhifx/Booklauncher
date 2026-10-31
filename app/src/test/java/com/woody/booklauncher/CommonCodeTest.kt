package com.woody.booklauncher

import com.woody.booklauncher.readers.ReaderApps
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 읽는 책 자동 추가의 공통 코드(서비스·공통 흐름·기록기·바탕 어댑터·선언의 바탕)에는 앱 이름도 앱 종류 갈래도 두지 않는다
 * (docs/reader-adapter-plan.md 목표 1·4, CLAUDE.md `자동 추가에 앱을 더할 때`). 앱을 더하거나 고칠 때 공통 코드에 그 앱을 넣으면 여기서 알린다.
 * 주석은 보지 않는다(설명에 예로 앱을 드는 것은 괜찮다). 앱 목록을 만들려면 안드로이드가 있어야 해 Robolectric
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommonCodeTest {
    private val common = listOf(
        "ReaderWatch.kt", "WatchCore.kt", "BookRecorder.kt",
        "readers/ScreenReader.kt", "readers/ReaderAdapter.kt", "readers/ReaderApp.kt", "readers/OpenResult.kt",
    )

    /** 테스트는 모듈 폴더(app)에서 돈다 */
    private fun source(name: String) = File("src/main/java/com/woody/booklauncher", name).readText()

    /** 주석을 뺀 코드(`file://` 같은 글자 안의 `//` 는 둔다) */
    private fun code(text: String) = text.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?<![:"])//.*"""), "")

    @Test fun `공통 코드에 앱 이름·패키지가 없다`() {
        val names = ReaderApps.ALL.flatMap { listOfNotNull(it.pkg, it::class.simpleName) }.toSet()
        val found = common.flatMap { file ->
            val c = code(source(file))
            names.filter { n -> Regex("""(?<![\w.])${Regex.escape(n)}(?![\w])""").containsMatchIn(c) }.map { "$file: $it" }
        }
        assertEquals(emptyList<String>(), found)
    }
}
