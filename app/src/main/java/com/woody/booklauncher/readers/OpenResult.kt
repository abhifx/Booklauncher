package com.woody.booklauncher.readers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.woody.booklauncher.BookShelf
import com.woody.booklauncher.Device
import com.woody.booklauncher.ShelfBook
import java.io.File

/** 홈에서 이북 앱의 책을 어떻게 여나([ReaderApp.open]). 여는 일은 [com.woody.booklauncher.AppStore.openBook] 이 한다 */
sealed interface OpenResult {
    /** 앱을 연다(BOOX 얼린 앱은 런처로 풀어서) */
    object Launch : OpenResult
    /** 이 인텐트로 연다. 열리면 [started], 못 열면 [onFail] */
    data class Start(val intent: Intent, val started: (() -> Unit)? = null, val onFail: Fallback = Fallback.LAUNCH) : OpenResult
    /** 파일로 연다(`file://` + MIME, StrictMode 를 잠시 풀어). 열리면 [started], 못 열면 [onFail] */
    data class File(val component: ComponentName, val path: String, val started: (() -> Unit)? = null, val onFail: Fallback = Fallback.LAUNCH) : OpenResult
    /** 파일을 모르거나 없다: 홈이 뒤에서 다시 찾아 열고([ReaderApp.bookFiles]·저장소), 못 찾으면 '파일을 찾지 못함' 창 */
    object FindFile : OpenResult
}

/** 열기에 실패했을 때: 앱을 연다(실행 화면이 있는 앱) / 뒤에서 파일을 다시 찾는다(실행 화면이 없는 앱) */
enum class Fallback { LAUNCH, FIND_FILE }

/** 홈에서 책을 열 때 앱이 받는 것: 책의 앱 항목([component])과 도우미 */
class HomeContext(val context: Context, val component: ComponentName) {
    val pkg: String get() = component.packageName

    /** 그 책의 파일. 모르거나, 책 파일을 읽을 수 있는데 그 파일이 없으면(지워짐·옮겨짐) null */
    fun bookFile(book: ShelfBook): String? =
        BookShelf.filePath(context, book.id)?.takeIf { !BookFiles.canRead(context) || File(it).isFile }

    /** BOOX 가 얼린 앱(런처로 풀어야 해서 앱을 연다) */
    val frozen: Boolean get() = Device.hasBooxLauncher(context) && Device.isFrozen(context.packageManager, pkg)
}
