package com.woody.booklauncher

import android.app.Application
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** 프로세스가 뜰 때 [CrashLog] 를 단다(홈·자동 추가 서비스·받기 결과 등 어느 것으로 떠도) */
class BookLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

/**
 * 마지막 앱 오류(잡지 못한 예외): 앱이 죽기 직전에 때·판·스레드·예외와 이 앱 코드의 자리 몇 개를 기기 보호 저장소의 파일 하나에 적고,
 * 기기 정보(자동 추가 서비스 기록 아래)에 보인다. 안드로이드 기록(`ApplicationExitInfo`)에는 CRASH 라는 까닭만 남아, 사용자가 보낸
 * 기기 정보 사진으로는 어디서 죽었는지 알 수 없었다(2026-10-26 Bigme B7 Pro: 1.6.1 업데이트 직후 CRASH 뒤 자동 추가 서비스가 다시 붙지 않았다).
 * 기록은 기기 밖으로 보내지 않는다. 다음 오류가 덮어쓴다.
 */
object CrashLog {
    fun install(context: Context) {
        // 잠금 해제 전에도 쓸 수 있는 곳에, 죽는 중에 저장소를 옮기거나 하지 않게 미리 정해 둔다(Storage.of 는 처음 부를 때 옛 저장소를 옮긴다)
        val file = File(context.createDeviceProtectedStorageContext().filesDir, FILE)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { file.writeText(describe(System.currentTimeMillis(), version, thread.name, e)) }
            previous?.uncaughtException(thread, e)
        }
        runCatching { logLast(context, file) }
    }

    /**
     * 지난번에 죽은 오류를 자동 추가 기록(`기록 보기`)에도 한 줄로 남긴다(죽는 중에는 기록 파일을 안전하게 쓸 수 없어 다음에 뜰 때, 때는 죽은 때).
     * 같은 오류는 한 번만(남긴 오류의 파일 시각을 기억한다)
     */
    private fun logLast(context: Context, file: File) {
        val at = file.lastModified().takeIf { it > 0L } ?: return
        val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_LOGGED, 0L) >= at) return
        prefs.edit().putLong(KEY_LOGGED, at).apply()
        val (head, where) = file.readText().trim().lines().let { it.first() to it.getOrNull(1).orEmpty() }
        // 첫 줄의 때·판·스레드 뒤가 예외(기록 줄에는 때가 따로 있다)
        WatchLog.add(context, WatchLog.Kind.CRASH, arg = head.split(" · ", limit = 4).last(), detail = where, now = at)
    }

    /** 마지막 앱 오류(두 줄). 없으면 null */
    fun last(context: Context): String? =
        runCatching { File(Storage.of(context).filesDir, FILE).takeIf { it.isFile }?.readText()?.trim() }.getOrNull()?.ifEmpty { null }

    /**
     * 첫 줄: `10/26 09:48 · 1.6.1 · main · IllegalStateException: 메시지`. 둘째 줄: 실제로 던진 곳(가장 안쪽 원인, [CAUSES] 단계까지)의 이 앱 코드 자리
     * [FRAMES]개(`Db.kt:41 ← Reading.kt:300`, 이 앱 코드가 없으면 맨 위 자리). 원인이 다른 예외면 뒤에 그 이름(기기 정보에서 넘치면 끝이 잘려 자리를 앞에).
     * 안드로이드·자바·코틀린·androidx([PLATFORM])가 아닌 자리를 이 앱 코드로 본다. 릴리스는 R8 이 이 앱 클래스(`h4`)와 코틀린·androidx 클래스(`n3`)를
     * 패키지 없는 짧은 이름으로 바꿔(`-repackageclasses`) 라이브러리 자리도 섞일 수 있고(안드로이드·자바는 그대로라 걸러진다), 파일 이름도 그 빌드의 mapping 열쇠(`r8-map-id-…`)로 바뀌어
     * 그때는 `h4.b:41`(클래스.메서드:줄)로 적는다: 그 판의 mapping(`release-mappings/`)으로 되찾는다(`tools/release/retrace.sh`)
     */
    internal fun describe(at: Long, version: String, thread: String, e: Throwable): String {
        val root = generateSequence(e) { it.cause?.takeIf { c -> c !== it } }.take(CAUSES).last()
        val frames = root.stackTrace.filter { f -> PLATFORM.none { f.className.startsWith(it) } }.ifEmpty { root.stackTrace.take(1) }.take(FRAMES)
            .joinToString(" ← ") { f -> (f.fileName?.takeIf { SOURCE.containsMatchIn(it) } ?: "${f.className}.${f.methodName}") + ":${f.lineNumber}" }
        val head = listOf(SimpleDateFormat("MM/dd HH:mm", Locale.US).format(at), version, thread, name(e)).joinToString(" · ")
        return head + "\n" + listOfNotNull(frames.ifEmpty { null }, name(root).takeIf { root !== e }).joinToString(" · ")
    }

    /** `IllegalStateException: 메시지`(메시지는 [MESSAGE_MAX] 자까지) */
    private fun name(e: Throwable): String =
        e.javaClass.simpleName + (e.message?.trim()?.replace('\n', ' ')?.takeIf { it.isNotEmpty() }?.let { ": " + it.take(MESSAGE_MAX) } ?: "")

    private const val FILE = "last_crash.txt"
    private const val PREFS = "crash_log"
    /** 자동 추가 기록에 남긴 마지막 오류의 파일 시각([logLast]) */
    private const val KEY_LOGGED = "logged_at"
    /** 이 앱 코드가 아닌 자리(안드로이드·자바·코틀린·androidx) */
    private val PLATFORM = listOf("android.", "androidx.", "com.android.", "java.", "javax.", "kotlin.", "dalvik.", "libcore.", "sun.", "jdk.")
    /** 읽을 수 있는 파일 이름(R8 이 바꾼 `r8-map-id-…`·`SourceFile` 은 아니다) */
    private val SOURCE = Regex("""\.(kt|java)$""")
    /** 따라가는 원인의 단계(원인 사슬이 돌아도 멈추지 않게) */
    private const val CAUSES = 8
    private const val FRAMES = 3
    private const val MESSAGE_MAX = 80
}
