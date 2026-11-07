package com.woody.booklauncher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors

/**
 * 읽는 책 자동 추가(접근성 서비스 [ReaderWatchService])가 받은 것과 한 일의 기록(설정 › 기기 정보 › 자동 추가 기록).
 * 자동 추가가 안 되는 기기에서 서비스가 연결되지 않는지, 연결은 됐는데 알림이 오지 않는지, 알림은 오는데 화면을 못 읽는지를
 * 사진 한 장으로 가르려고 남긴다(기록 화면은 쪽을 넘기지 않고 최근 것만 보인다. 지우고 한 번 해 본 뒤 찍게 한다). 기록은 기기 밖으로 보내지 않는다.
 * - 줄: 최근 [MAX]개만 기기 보호 저장소의 파일에 둔다(앱이 시스템에 끝나도 남게). 같은 것이 이어지면 새 줄 없이 횟수를 센다.
 *   진행률·서재 칸 수는 쪽을 넘길 때마다 바뀌어 같은 앱(진행률은 같은 책)이 이어지면 그 줄의 값만 고친다([UPDATED]. 오래 읽으면 다른 줄이 밀려나지 않게).
 * - 요약(마지막 연결이나 지운 뒤 [Summary.since] 부터): 이북 앱별 받은 알림 수·마지막 때, 첫 알림, 화면을 못 읽은 횟수.
 *   글 내용이 바뀌는 알림은 너무 잦아 줄로 남기지 않고 여기서만 센다.
 * 서비스와 기록 화면은 같은 프로세스의 메인 스레드에서 부른다. 파일은 잠시 모았다가 한 번에 쓴다: 새 줄은 [SAVE_DELAY_MS] 안에,
 * 알림 수·이어진 줄(횟수·진행률)만 바뀌면 [COUNT_DELAY_MS] 안에(읽는 동안 글 내용 바뀜 알림이 쉬지 않고 와 몇 초마다 저장소에 쓰지 않게).
 */
object WatchLog {
    enum class Kind {
        CONNECTED, UNBOUND, DESTROYED,
        /** 연결(또는 지운) 뒤 처음 받은 알림 */
        FIRST_EVENT,
        /** 그 앱의 화면(액티비티)이 바뀜. [Entry.arg] 화면 이름, [Entry.detail] 서재·읽는 화면·그 밖([Role]) */
        SCREEN,
        /** 화면 바뀜 알림인데 그 앱의 액티비티가 아니다(대화상자·팝업, 또는 이름을 다르게 보내는 기기) */
        OTHER_WINDOW,
        /** 서재·읽는 화면인데 화면 내용(창)을 못 얻었다 */
        NO_WINDOW,
        /** 서재에서 읽은 책 칸 수 */
        SHELF,
        /** 서재에서 누른 칸의 제목(못 찾았으면 빈 글자) */
        CLICK,
        /** 책을 폈다. [Entry.detail] 은 [Opened] */
        OPENED,
        /** 읽는 화면의 진행률(%). [Entry.detail] 은 읽는 화면에서 읽은 제목(없으면 빈 글자) */
        PROGRESS,
        /** 읽고 돌아온 서재 아래 '최근 읽은 책'([Entry.arg])이 읽으러 가기 전 그대로라 바뀌기를 기다린다(그 책을 편 것으로 보지 않는다) */
        RECENT_WAIT,
        /** 연 파일을 내놓지 않는 리더의 책([Entry.arg])을 저장소에서 찾았다. [Entry.detail] 은 그 파일 이름(1.7.2 까지의 문리더. 남은 기록을 읽으려고 둔다) */
        FILE,
        /** 이북 앱 알림을 처리하다 난 오류(그 알림만 건너뛴다). [Entry.arg] 는 오류 종류, [Entry.detail] 은 내용 */
        ERROR,
        /** 앱 어댑터가 남긴 진단 한 줄(책을 알아내지 못한 까닭 등). [Entry.arg] 를 그대로 보인다 */
        NOTE,
        /** 앱이 오류로 죽었다(다음에 뜰 때 [CrashLog] 가 남긴다, 때는 죽은 때). [Entry.arg] 는 예외, [Entry.detail] 은 이 앱 코드 자리 */
        CRASH,
    }

    enum class Role { SHELF, VIEWER, OTHER }
    /** 새로 넣음 · 이미 있어 맨 앞으로 · 목록이 다 차 못 넣음 · 완독해 나간 책이라 다시 넣지 않음(뒤의 둘은 1.5.3 까지. 남은 기록을 읽으려고 둔다) */
    enum class Opened { NEW, FRONT, FULL, FINISHED }

    class Entry(var at: Long, val kind: Kind, val pkg: String, var arg: String, val detail: String, var repeat: Int = 1)

    /** 이어지면 값([Entry.arg])만 고치는 것 */
    private val UPDATED = setOf(Kind.PROGRESS, Kind.SHELF)

    /** [since](연결·지운 때, 모르면 0) 부터의 요약. [events] 는 앱별 (받은 알림 수, 마지막 때). */
    class Summary(val since: Long, val firstAt: Long, val firstPkg: String, val noWindow: Int, val events: Map<String, Pair<Int, Long>>)

    /** 한 화면에 들어가는 것보다 넉넉히(같은 것이 이어지면 한 줄이라 한 번 해 본 것은 한 화면에 든다) */
    private const val MAX = 60
    private const val SAVE_DELAY_MS = 3000L
    private const val COUNT_DELAY_MS = 15_000L
    private const val FILE = "watch_log.txt"
    private const val PREFS = "watch_log"

    private var app: Context? = null
    private val entries = java.util.ArrayDeque<Entry>()
    private val counts = mutableMapOf<String, Int>()
    private val lastAt = mutableMapOf<String, Long>()
    private var since = 0L
    private var firstAt = 0L
    private var firstPkg = ""
    private var noWindow = 0
    private val handler = Handler(Looper.getMainLooper())
    /** 쓰기는 하나씩 차례로(앞서 맡긴 것이 나중에 써지지 않게) */
    private val writer = Executors.newSingleThreadExecutor()
    private var saveQueued = false
    /** 맡겨 둔 저장의 때([SystemClock.uptimeMillis]) */
    private var saveDue = 0L
    /** 줄이 바뀌어 파일을 다시 써야 한다(알림 수만 바뀌면 요약만 쓴다) */
    private var entriesDirty = false
    private val saveLater = Runnable { save() }

    /** 서비스가 연결됐다: 요약을 새로 센다. */
    fun connected(context: Context) {
        load(context)
        restart()
        add(context, Kind.CONNECTED)
    }

    /** 기록 지우기: 줄을 비우고 요약을 지금부터 센다. */
    fun clear(context: Context) {
        load(context)
        entries.clear()
        entriesDirty = true
        restart()
        saveSoon(SAVE_DELAY_MS)
    }

    private fun restart() {
        counts.clear(); lastAt.clear()
        since = System.currentTimeMillis()
        firstAt = 0L; firstPkg = ""; noWindow = 0
    }

    /**
     * 서비스가 이북 앱 알림을 마지막으로 받은 때(어느 앱이든, 없으면 0). 그때는 서비스가 살아 있었다([ReaderWatchService.stoppedAt]).
     * 저장은 [COUNT_DELAY_MS] 까지 늦게 하므로 프로세스가 죽었으면 그보다 조금 이를 수 있다(이른 쪽이라 '책 모름'이 넓어질 뿐 다른 책에 붙지는 않는다).
     */
    @Synchronized
    fun lastEventAt(context: Context): Long {
        load(context)
        return lastAt.values.maxOrNull() ?: 0L
    }

    /** 알림을 하나 받았다(글 내용 바뀜 포함). 연결 뒤 첫 알림이면 줄로도 남긴다. */
    fun event(context: Context, pkg: String) {
        load(context)
        val now = System.currentTimeMillis()
        counts[pkg] = (counts[pkg] ?: 0) + 1
        lastAt[pkg] = now
        if (firstAt == 0L) {
            firstAt = now; firstPkg = pkg
            add(context, Kind.FIRST_EVENT, pkg)
        } else saveSoon(COUNT_DELAY_MS)
    }

    /** 한 줄 남긴다. [now] 는 그 일이 있던 때(기본: 지금. 앱 오류는 다음에 뜰 때 죽은 때로 남긴다) */
    fun add(context: Context, kind: Kind, pkg: String = "", arg: String = "", detail: String = "", now: Long = System.currentTimeMillis()) {
        load(context)
        if (kind == Kind.NO_WINDOW) noWindow++
        val a = clean(arg)
        val d = clean(detail)
        val last = entries.lastOrNull()
        // 같은 줄이 이어져 그 줄만 고치면(쪽을 넘길 때마다의 진행률 등) 늦게 쓴다
        val delay = if (last != null && last.kind == kind && last.pkg == pkg && last.detail == d && (last.arg == a || kind in UPDATED)) {
            last.repeat++
            last.at = now
            last.arg = a
            COUNT_DELAY_MS
        } else {
            entries.addLast(Entry(now, kind, pkg, a, d))
            while (entries.size > MAX) entries.removeFirst()
            SAVE_DELAY_MS
        }
        entriesDirty = true
        saveSoon(delay)
    }

    /** 최근 것부터 */
    fun entries(context: Context): List<Entry> {
        load(context)
        // java.util.ArrayDeque.reversed() 는 안드로이드 15 부터라 쓰지 않는다
        return entries.descendingIterator().asSequence().toList()
    }

    fun summary(context: Context): Summary {
        load(context)
        return Summary(since, firstAt, firstPkg, noWindow, counts.keys.associateWith { (counts[it] ?: 0) to (lastAt[it] ?: 0L) })
    }

    /** 앞에 붙는 흔한 이름을 뗀 패키지(`kr.co.millie.eink` → `millie.eink`). 한 줄에 들게 줄인다. */
    fun shortPkg(pkg: String) = pkg.removePrefix("kr.co.").removePrefix("com.")

    /** 서비스가 끝날 때: 기다리지 않고 쓴다. */
    fun flush() {
        handler.removeCallbacks(saveLater)
        save()
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    /** 알림이 쉬지 않고 와도 [delayMs] 안에는 쓴다(미루기만 하면 앱이 끝날 때 잃는다). 더 늦게 맡겨 둔 저장이 있으면 앞당긴다. */
    private fun saveSoon(delayMs: Long) {
        val due = SystemClock.uptimeMillis() + delayMs
        if (saveQueued && saveDue <= due) return
        handler.removeCallbacks(saveLater)
        saveQueued = true
        saveDue = due
        handler.postAtTime(saveLater, due)
    }

    private fun load(context: Context) {
        if (app != null) return
        val c = Storage.of(context)
        app = c
        runCatching {
            File(c.filesDir, FILE).takeIf { it.exists() }?.forEachLine { line ->
                val f = line.split('\t')
                if (f.size < 6) return@forEachLine
                val kind = runCatching { Kind.valueOf(f[1]) }.getOrNull() ?: return@forEachLine
                entries.addLast(Entry(f[0].toLongOrNull() ?: 0L, kind, f[2], f[4], f[5], f[3].toIntOrNull() ?: 1))
            }
        }
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        since = p.getLong("since", 0L)
        firstAt = p.getLong("first_at", 0L)
        firstPkg = p.getString("first_pkg", "").orEmpty()
        noWindow = p.getInt("no_window", 0)
        p.getStringSet("pkgs", emptySet()).orEmpty().forEach { pkg ->
            counts[pkg] = p.getInt("count_$pkg", 0)
            lastAt[pkg] = p.getLong("last_$pkg", 0L)
        }
    }

    private fun save() {
        saveQueued = false
        val c = app ?: return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply {
            putLong("since", since).putLong("first_at", firstAt).putString("first_pkg", firstPkg).putInt("no_window", noWindow)
            putStringSet("pkgs", counts.keys.toSet())
            counts.forEach { (pkg, n) -> putInt("count_$pkg", n).putLong("last_$pkg", lastAt[pkg] ?: 0L) }
        }.apply()
        if (!entriesDirty) return
        entriesDirty = false
        val text = entries.joinToString("") { "${it.at}\t${it.kind}\t${it.pkg}\t${it.repeat}\t${it.arg}\t${it.detail}\n" }
        writer.execute { runCatching { File(c.filesDir, FILE).writeText(text) } }
    }
}
