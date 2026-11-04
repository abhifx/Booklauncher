package com.woody.booklauncher

import com.woody.booklauncher.readers.ReaderApps
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File

/**
 * 자동 추가가 받는 이북 앱([ReaderApps.ALL])이 자동 추가 안내([ReaderGuide.APPS])와 접근성 설정(reader_watch.xml)에 빠짐없이 있고,
 * 책 파일을 쓰는 앱에 책 파일 읽기 필요가 적혀 있으며 안내에도 책 파일 읽기 줄이 있는가
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuideAppsTest {
    @Test fun `자동 추가 안내에 모든 이북 앱이 한 번씩`() {
        val guided = ReaderGuide.APPS.flatMap { it.specs }
        assertEquals(guided.size, guided.toSet().size)
        assertEquals(ReaderApps.ALL.map { it.pkg }.sorted(), guided.map { it.pkg }.sorted())
    }

    @Test fun `파일로만 여는 앱은 책 파일 읽기가 필요하다고 적혀 있다`() {
        val usesFiles = ReaderApps.ALL.filter { it.fileEntry != null }
        assertEquals(emptyList<String>(), usesFiles.filterNot { it.needsBookFiles }.map { it.pkg })
    }

    @Test fun `책 파일 읽기가 필요한 앱만 안내에 책 파일 읽기 줄이 있다`() {
        val wrong = ReaderGuide.APPS.filter { app -> app.specs.any { it.needsBookFiles } != (app.info.bookFiles != null) }
        assertEquals(emptyList<String>(), wrong.flatMap { app -> app.specs.map { it.pkg } })
    }

    /** 안드로이드 9·10 은 화면 캡처가 없어 서재에서 자르는 표지가 들어오지 않는다. 표지가 들어오는 것은 책 파일·앱 데이터를 읽거나 앱이 표지를 내주는 앱뿐 */
    @Config(sdk = [29])
    @Test fun `안드로이드 10 에서는 파일이나 공개 데이터로만 표지가 들어온다`() {
        val wrong = ReaderGuide.APPS.filter { app -> app.info.cover.added != (app.info.bookFiles != null || app.info.appCover) }
        assertEquals(emptyList<String>(), wrong.flatMap { app -> app.specs.map { it.pkg } })
    }

    @Test fun `접근성 서비스가 모든 이북 앱 화면을 받는다`() {
        val xml = File("src/main/res/xml/reader_watch.xml")
        val p = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(xml.reader()) }
        var names: List<String> = emptyList()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            for (i in 0 until p.attributeCount) if (p.getAttributeName(i).endsWith("packageNames")) names = p.getAttributeValue(i).split(',')
        }
        assertEquals(ReaderApps.ALL.map { it.pkg }.distinct().sorted(), names.sorted())
    }
}
