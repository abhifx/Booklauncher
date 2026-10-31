package com.woody.booklauncher

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * 아이콘 팩의 `appfilter.xml` 읽기([IconPacks.parse]): 실행 화면이 맞는 줄, 없으면 같은 패키지의 첫 줄,
 * `.` 으로 시작하는 실행 화면, 실행 화면이 아닌 줄(달력 등)·그림 없는 줄은 거른다.
 * 아이콘 바꾸기의 그림 목록 `drawable.xml` 읽기([IconPacks.parseNames])와 찾기([IconPacks.search]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconPacksTest {
    private fun filter(items: String) = IconPacks.parse(
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources><iconback img1=\"back\"/>$items</resources>"))
        }
    )
    private fun app(pkg: String, cls: String) = ComponentName(pkg, cls)

    @Test fun `실행 화면이 맞는 줄`() {
        val f = filter(
            """<item component="ComponentInfo{com.kyobo.library/com.kyobo.library.Main}" drawable="kyobo"/>
               <item component="ComponentInfo{com.kyobo.library/com.kyobo.library.Other}" drawable="kyobo_other"/>"""
        )
        assertEquals("kyobo_other", f.drawableFor(app("com.kyobo.library", "com.kyobo.library.Other")))
    }

    @Test fun `실행 화면이 바뀐 판은 같은 패키지의 첫 줄`() {
        val f = filter(
            """<item component="ComponentInfo{com.millie/com.millie.Splash}" drawable="millie"/>
               <item component="ComponentInfo{com.millie/com.millie.Old}" drawable="millie_old"/>"""
        )
        assertEquals("millie", f.drawableFor(app("com.millie", "com.millie.NewSplash")))
    }

    @Test fun `점으로 시작하는 실행 화면은 패키지 안의 이름`() {
        val f = filter("""<item component="ComponentInfo{org.koreader.launcher/.MainActivity}" drawable="koreader"/>""")
        assertEquals("koreader", f.drawableFor(app("org.koreader.launcher", "org.koreader.launcher.MainActivity")))
    }

    @Test fun `실행 화면이 아니거나 그림이 없는 줄은 거른다`() {
        val f = filter(
            """<item component=":CALENDAR" drawable="calendar"/>
               <item component="ComponentInfo{com.ridi/com.ridi.Main}" drawable=""/>
               <item component="ComponentInfo{com.ridi/com.ridi.Main}"/>"""
        )
        assertNull(f.drawableFor(app("com.ridi", "com.ridi.Main")))
    }

    @Test fun `팩에 없는 앱`() =
        assertNull(filter("""<item component="ComponentInfo{a.b/a.b.C}" drawable="abc"/>""").drawableFor(app("x.y", "x.y.Z")))

    private fun names(items: String) = IconPacks.parseNames(
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources><version>1</version>$items</resources>"))
        }
    )

    @Test fun `그림 목록은 팩의 순서대로, 묶음 제목은 거르고 겹친 이름은 한 번`() = assertEquals(
        listOf("kindle", "book", "libby"),
        names("""<category title="New"/><item drawable="kindle"/><item drawable="book"/>
                 <category title="Google"/><item drawable="book"/><item drawable="libby"/><item drawable=""/>""")
    )

    private val all = listOf("google_play_books", "book_bookmark", "audiobooks", "book", "kindle", "Bookshelf")

    @Test fun `찾기는 시작하는 이름부터, 그다음 들어 있는 이름`() =
        assertEquals(listOf("book", "book_bookmark", "Bookshelf", "audiobooks", "google_play_books"), IconPacks.search(all, "Book"))

    @Test fun `띄어 쓴 말은 밑줄로`() = assertEquals(listOf("google_play_books"), IconPacks.search(all, " play  books "))

    @Test fun `찾기 칸이 비면 모두 그대로`() = assertEquals(all, IconPacks.search(all, "  "))
}
