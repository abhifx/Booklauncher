package com.woody.pebbledesk

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 책 한 권의 읽은 기록: 책(목록에 없던 책은 제목만 든 임시 책), 상태, 완독 모두(오래된 순, 그 회차의 별점·감상과 함께 [finishList]),
 * 날마다 읽은 시간(남은 기록 전체), 진행 중인 마지막 회차의 별점·감상([openReview])과 `다시 읽기`한 날([openStart])·고친 읽기 시작한 날([openBegan]).
 * 홈과 올해 읽은 책에서 같은 책 상세([showBookDetail])로 보인다.
 */
class BookRecord(
    val book: ShelfBook, val state: State, val hidden: Boolean, private val finishList: List<BookShelf.Finish>, val dayMs: Map<LocalDate, Long>,
    private val openReview: Review? = null, private val openStart: LocalDate? = null,
    /** 진행 중인 회차가 있다는 표시(`round_start` 줄. 시작한 날 [openStart] 를 몰라도) */
    private val openRound: Boolean = openStart != null,
    private val openBegan: LocalDate? = null,
) {
    enum class State {
        /** 읽고 있는 책 목록에 있다(완독했던 책이면 다시 읽는 중) */
        READING,
        /** 나간 책(완독했거나 뺀 책) */
        PAST,
        /** 목록에 넣지 못한 책(읽은 시간에만 있는 제목) */
        TITLE_ONLY,
    }

    /** 완독한 날(오래된 순, 같은 날 두 번 완독하면 두 번) */
    val finishes: List<LocalDate> = finishList.map { it.day }

    /** 한 번이라도 완독했다(표지에 띠) */
    val finished get() = finishes.isNotEmpty()
    /** 남은 기록 전체에서 읽은 시간 */
    val ms = dayMs.values.sum()

    /** [year] 에 읽은 시간 */
    fun yearMs(year: Int) = dayMs.entries.sumOf { (d, ms) -> if (d.year == year) ms else 0L }

    /**
     * 그해 올해 읽은 책에 보이는 해: 합쳐 5분 이상 읽은 해. 그해에 걸친 회차에 별점·감상을 남겼으면 조금이라도 읽은 해(짧게 읽고 남긴 감상도
     * 볼 곳이 있게. 1.5 판까지는 1분이 기준이었다). 직접 넣은 책([manual], 읽은 시간이 붙지 않음)은 회차가 걸친 해(넣은 날 첫 회차가 시작한다).
     */
    val readYears: List<Int> by lazy {
        // 그해에 걸친 회차(시작한 해 ~ 끝난 해, 진행 중이면 올해까지)에 별점·감상이 있는가
        fun reviewedIn(y: Int) = rounds.any { r ->
            r.review != null && (r.from ?: r.to ?: LocalDate.now()).year <= y && y <= (r.to ?: LocalDate.now()).year
        }
        val read = dayMs.keys.map { it.year }.distinct().filter { y -> yearMs(y) >= ReadingLog.MIN_DAY_MS || (yearMs(y) > 0 && reviewedIn(y)) }
        // 직접 넣은 책([manual])은 읽은 시간이 붙지 않으니 회차가 걸친 해(시작한 해 ~ 완독한 해, 읽고 있는 책의 진행 중 회차는 올해까지)
        if (!book.manual) read else (read + rounds.flatMap { r ->
            val from = (r.from ?: r.to)?.year ?: return@flatMap emptyList()
            val to = r.to?.year ?: if (state == State.READING) LocalDate.now().year else from
            (from..maxOf(from, to)).toList()
        }).distinct()
    }

    /** 어느 해의 올해 읽은 책에든 보이는가: 그해 합쳐 5분 이상 읽었거나(직접 넣은 책은 회차가 걸친 해) 완독했다(별점·감상을 남길 만한 책) */
    val inYearBooks get() = finished || readYears.isNotEmpty()

    /**
     * 지금 회차에 별점·감상을 남길 수 있다(상세의 별점 칸·`책 빼기`의 별점·감상 메뉴): 올해 읽은 책에 보이는 책이거나 이미 남긴 것이 있다
     * (읽은 시간 없이 감상만 있는 1.5.3 이전 기록도 고칠 수 있게). 아니면 남긴 것을 볼 곳이 없어 막는다.
     */
    val canReview get() = inYearBooks || lastRound.review != null

    /** [after] 다음 날부터 [upTo] 까지(null 이면 끝없이) 읽은 시간: 회차마다 읽은 시간 */
    private fun spent(after: LocalDate?, upTo: LocalDate?) =
        dayMs.entries.sumOf { (d, ms) -> if ((after == null || d > after) && (upTo == null || d <= upTo)) ms else 0L }

    /**
     * 한 회차(읽기 시작해 완독까지). 상태는 완독([finished])과 진행 중 둘뿐이다(뺀 책도 다시 읽으면 이어지니 진행 중).
     * 번호(회차가 둘 이상일 때만), 시작 날(모르면 null), 끝 날(완독한 날, 진행 중이면 null), 그 동안 읽은 시간, 그 회차의 별점·감상,
     * 완독한 회차면 그 완독 줄 id(완독한 날·별점·감상을 고칠 때), 그 회차에 마지막으로 읽은 날(완독한 날을 고칠 수 있는 범위, [finishDayRange]),
     * 회차가 시작된 경계(`다시 읽기`로 시작했으면 그날, 아니면 기록으로 센 시작 날(읽기 시작한 날을 고쳐도 그대로). 앞뒤 회차의 완독한 날을
     * 이 너머로 고치지 못하게), 완독을 누른 날(그날까지 읽은 시간은 완독한 날을 앞으로 고쳐도 이 회차. 누른 때를 모르는 1.5.3 이전 완독과
     * 진행 중 회차는 null), 이 회차 시간에 들었지만 고친 기간([from] ~ [to]) 밖에 읽은 날(읽기 시작한 날을 처음 읽은 날 뒤로, 완독한 날을
     * 누른 날 앞으로 고쳤을 때. 월간 결산만 책 막대·시간에서 뺀다, [periodDayMs]), 시작 날이 사용자가 고친 날인가(결산 막대가 그날부터).
     */
    class Round(
        val nth: Int?, val from: LocalDate?, val to: LocalDate?, val ms: Long, val finished: Boolean, val review: Review?,
        val finishId: Long? = null, val lastRead: LocalDate? = null, val begin: LocalDate? = from, val pressed: LocalDate? = null,
        val outside: List<LocalDate> = emptyList(), val startSet: Boolean = false,
    )

    /**
     * 회차들(오래된 순). 완독마다 한 회차(시간 = 앞 완독 다음 날부터 그 완독 날까지, 시작 = 그 사이 처음 읽은 날. 같은 날 두 번 완독하면
     * 두 번째 회차는 그날 하루, 시간 없이)이고, 회차는 완독해야 넘어간다(완독하기 전에 뺐다 다시 읽어도 같은 회차).
     * 마지막 완독 뒤에 읽고 있거나, `다시 읽기`를 눌렀거나, 별점·감상을 남겼거나, 모두 합쳐 [ReadingLog.MIN_DAY_MS] 이상 읽었으면
     * 진행 중인 회차가 하나 더 있다(완독한 책을 잠깐 열어 확인한 것은 회차가 아니다). 완독한 적이 없는 책은 그것 하나.
     * `다시 읽기`로 시작한 회차([BookShelf.Finish.started]·[openStart])는 그 전에 합쳐 [ReadingLog.MIN_DAY_MS] 미만으로 잠깐 연 시간을 빼고
     * 그날부터 센다(그 전에 그만큼 읽었으면 이미 시작한 회차라 그대로). 시작 날은 잠깐이라도 읽은 날, 아직 안 읽었으면 `다시 읽기`한 날.
     * 사용자가 읽기 시작한 날을 고쳤으면([BookShelf.Finish.began]·[openBegan]) 시작 날은 그날(앞으로도 뒤로도. 기간 글자만, 읽은 시간은
     * 그 전에 읽은 것도 그대로 이 회차). 그날이 그 회차의 범위(앞 완독한 날 ~ 끝 날)를 벗어났으면(그 뒤 완독한 날을 고쳐 회차가 바뀜) 기록으로 센 날.
     */
    val rounds: List<Round> by lazy {
        val days = dayMs.filterValues { it > 0 }.keys.sorted()
        // [after] 다음 날부터 [upTo] 까지 읽은 회차(`다시 읽기`한 날 [start], 고친 읽기 시작한 날 [began])의 시작 날·시간·마지막으로 읽은 날·시작 경계.
        // 고친 읽기 시작한 날은 앞 회차 완독한 날 [prevDay] ~ 이 회차 완독한 날 [day] 안이어야 쓴다(읽은 시간을 나누는 [after]·[upTo] 와 따로).
        fun span(
            after: LocalDate?, upTo: LocalDate?, prevDay: LocalDate?, day: LocalDate?, start: LocalDate?, began: LocalDate?,
            make: (LocalDate?, Long, LocalDate?, LocalDate?, List<LocalDate>, Boolean) -> Round,
        ): Round {
            val before = start?.minusDays(1)
            val fromStart = before != null && spent(after, before) < ReadingLog.MIN_DAY_MS
            val bound = if (fromStart) listOfNotNull(after, before).max() else after
            val read = days.filter { (bound == null || it > bound) && (upTo == null || it <= upTo) }
            val recorded = read.firstOrNull() ?: start
            val set = began?.takeIf { b -> (prevDay == null || b >= prevDay) && (day == null || b <= day) }
            val from = set ?: recorded
            val outside = read.filter { (from != null && it < from) || (day != null && it > day) }
            return make(from, spent(bound, upTo), read.lastOrNull(), if (fromStart) start else recorded, outside, set != null)
        }
        val out = mutableListOf<Round>()
        // 앞 회차까지 읽은 시간의 끝(완독을 누른 날과 완독한 날 중 늦은 날)과 앞 회차 완독한 날
        var prevEnd: LocalDate? = null
        var prevDay: LocalDate? = null
        for (f in finishList) {
            val pressed = f.at?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            val end = listOfNotNull(f.day, pressed).max()
            out += span(prevEnd, end, prevDay, f.day, f.started, f.began) { from, ms, lastRead, begin, outside, set ->
                Round(null, from, f.day, ms, true, f.review, f.id, lastRead, begin, pressed, outside, set)
            }
            prevEnd = end
            prevDay = f.day
        }
        val last = prevEnd
        if (last == null || state == State.READING || openRound || openReview != null || spent(last, null) >= ReadingLog.MIN_DAY_MS) {
            out += span(last, null, prevDay, null, openStart, openBegan) { from, ms, lastRead, begin, outside, set ->
                Round(null, from, null, ms, false, openReview, null, lastRead, begin, outside = outside, startSet = set)
            }
        }
        if (out.size < 2) out
        else out.mapIndexed { i, r -> Round(i + 1, r.from, r.to, r.ms, r.finished, r.review, r.finishId, r.lastRead, r.begin, r.pressed, r.outside, r.startSet) }
    }

    /**
     * 월간 결산의 책 막대·시간: 날마다 읽은 시간에서 회차의 고친 기간 밖에 읽은 날([Round.outside])을 뺀 것. 날짜를 고치지 않은 책은 [dayMs] 그대로.
     * 결산 밖의 화면(상세의 회차 시간·올해 읽은 책·독서 기록)과 DB 는 [dayMs] 를 쓴다.
     */
    val periodDayMs: Map<LocalDate, Long> by lazy {
        val outside = rounds.flatMapTo(HashSet()) { it.outside }
        if (outside.isEmpty()) dayMs else dayMs.filterKeys { it !in outside }
    }

    /**
     * 완독한 회차 [round] 의 완독한 날을 고칠 수 있는 범위: 가장 이른 날(앞 회차 완독일·그 회차 읽기 시작한 날 중 늦은 날. 첫 회차에 둘 다
     * 없으면 null = 아래 제한은 [EARLIEST_DAY] 만) ~ 가장 늦은 날(다음 회차 시작 경계(다시 읽기한 날, 없으면 처음 읽은 날)와 고친 읽기 시작한 날
     * 중 이른 날, 마지막 회차면 [today]. 둘 다 [today] 를 넘지 않는다). 완독을 누른 날까지 읽은 시간은 완독한 날을 그 앞으로 고쳐도 이 회차라
     * 마지막으로 읽은 날보다 앞으로도 옮긴다(기간 글자만 바뀐다). 다만 누른 날 뒤에 읽은 날(완독한 날을 누른 날 뒤로 고쳐 들어온 시간)과,
     * 누른 때를 모르는 옛 완독(1.5.3 이전)은 그 회차 시작 경계·마지막으로 읽은 날보다 앞으로 옮기지 못한다(읽은 시간이 엉뚱한 회차로 가지 않게).
     * 읽기 시작한 날은 [from](회차 메뉴에서 함께 고르는 중이면 고른 날).
     */
    fun finishDayRange(round: Round, today: LocalDate, from: LocalDate? = round.from): Pair<LocalDate?, LocalDate> {
        val i = rounds.indexOf(round)
        val pressed = round.pressed
        val held = if (pressed == null) listOfNotNull(round.begin, round.lastRead) else listOfNotNull(round.lastRead?.takeIf { it > pressed })
        val earliest = (listOfNotNull(rounds.getOrNull(i - 1)?.to, from) + held).maxOrNull()
        val next = rounds.getOrNull(i + 1)
        val nextStart = next?.let { listOfNotNull(it.begin, it.from).minOrNull() ?: it.to }
        return earliest to minOf(nextStart ?: today, today)
    }

    /** 완독한 회차 [round] 의 완독한 날을 [day] 로 고칠 수 있는가([finishDayRange] 안, [EARLIEST_DAY] 이후. 오타 `0206.1.1` 같은 날은 막는다) */
    fun canFinishOn(round: Round, day: LocalDate, today: LocalDate, from: LocalDate? = round.from): Boolean {
        val (earliest, latest) = finishDayRange(round, today, from)
        return day >= (earliest ?: EARLIEST_DAY) && day >= EARLIEST_DAY && day <= latest
    }

    /**
     * 회차 [round] 의 읽기 시작한 날을 고칠 수 있는 범위: 가장 이른 날(앞 회차의 완독한 날, 첫 회차면 null = [EARLIEST_DAY] 만) ~ 가장 늦은 날
     * (끝 날 [to](완독한 날, 회차 메뉴에서 함께 고치는 중이면 고른 날)와 [today] 중 이른 날). 기록으로 센 첫날보다 앞(기록을 켜기 전·다른 기기에서
     * 먼저 읽음)으로도 뒤(잠깐 펴 본 날 뒤에 실제로 시작)로도 옮긴다. 그 전에 읽은 시간도 그대로 이 회차의 시간이다.
     */
    fun startDayRange(round: Round, today: LocalDate, to: LocalDate? = round.to): Pair<LocalDate?, LocalDate> {
        val i = rounds.indexOf(round)
        return rounds.getOrNull(i - 1)?.to to listOfNotNull(to, today).min()
    }

    /** 회차 [round] 의 읽기 시작한 날을 [day] 로 고칠 수 있는가([startDayRange] 안, [EARLIEST_DAY] 이후) */
    fun canStartOn(round: Round, day: LocalDate, today: LocalDate, to: LocalDate? = round.to): Boolean {
        val (earliest, latest) = startDayRange(round, today, to)
        return day >= (earliest ?: EARLIEST_DAY) && day >= EARLIEST_DAY && day <= latest
    }

    /**
     * 이북 앱에서 다시 펴 되살릴 때 새 회차가 시작하는가: 완독한 적이 있고 마지막 회차가 완독으로 끝났다(이미 진행 중이던 회차 — 다시 읽기를
     * 눌렀거나, 감상을 남겼거나, 완독 뒤 5분 넘게 읽은 회차 — 면 이어진다).
     */
    val reopenStartsRound: Boolean get() = finished && lastRound.finished

    /** 마지막 회차(상세의 기록 칸·별점·감상 칸, 목록의 작은 별) */
    val lastRound: Round get() = rounds.last()

    /**
     * 같은 책의 지금 기록(고친 뒤·닫은 뒤 상세를 다시 띄울 때). 목록에 없던 책은 id 가 없어 같은 제목으로 찾는다(그사이 나간 책으로 적어 뒀거나
     * 자동 추가로 들어온 책. 그 제목의 읽은 시간도 그 책으로 옮겨 갔다). 읽은 시간도 완독도 없어 [all] 에 없는 책은 빈 기록.
     */
    fun reload(context: Context): BookRecord = reloadIn(context, all(context))

    /**
     * 이 기록의 회차 [round] 가 지금 기록 [now] 에서는 어떤 회차인가(같은 순번). 회차는 완독해야 넘어가고 지우지 않으니 순번이 그대로다
     * (열어 둔 사이 다른 화면에서 완독했으면 그 진행 중 회차가 완독한 회차가 돼 있다).
     */
    fun roundIn(now: BookRecord, round: Round): Round? = rounds.indexOf(round).takeIf { it >= 0 }?.let { now.rounds.getOrNull(it) }

    /**
     * 회차 [round] 를 지울 때([BookShelf.forgetRound]) 그 책의 기록에서 뺄 날: 앞 회차까지 읽은 시간의 끝(완독한 날과 완독을 누른 날 중 늦은 날,
     * 첫 회차면 처음부터) 다음 날부터 이 회차까지 읽은 시간의 끝(완독한 회차는 같은 식, 진행 중 회차는 [today] 와 마지막으로 읽은 날 중 늦은 날)까지.
     * 남은 회차의 시간은 그대로다(완독한 회차를 지워도 그 시간이 다음 회차로 넘어가지 않는다). 읽은 시간은 날 단위라 진행 중 회차를 지운 날
     * 그 책을 다시 읽어도 그날 시간은 이 책의 기록에 들지 않는다(받아들인 한계).
     */
    fun dropOf(round: Round, today: LocalDate): BookShelf.Drop {
        val prev = rounds.getOrNull(rounds.indexOf(round) - 1)
        val after = prev?.let { p -> listOfNotNull(p.to, p.pressed).maxOrNull() }
        val until = if (round.finished) listOfNotNull(round.to, round.pressed).max() else listOfNotNull(today, round.lastRead).max()
        return BookShelf.Drop(after, until)
    }

    internal fun reloadIn(context: Context, records: List<BookRecord>): BookRecord {
        val id = if (state == State.TITLE_ONLY) {
            (BookShelf.findByTitle(context, book.title) ?: BookShelf.findLivePast(context, book.title)?.book)?.id ?: book.id
        } else book.id
        records.firstOrNull { it.book.id == id }?.let { return it }
        BookShelf.list(context).firstOrNull { it.id == id }?.let { return empty(context, it, State.READING, false) }
        BookShelf.history(context).firstOrNull { it.book.id == id }?.let { return empty(context, it.book, State.PAST, it.hidden) }
        return this
    }

    companion object {
        /** 완독한 날로 받는 가장 이른 날(읽은 기록이 없는 오래전 완독도 넣을 수 있게 넉넉히, 오타만 막는다) */
        val EARLIEST_DAY: LocalDate = LocalDate.of(1900, 1, 1)

        /** 목록에 없던 책(읽은 시간에만 있는 제목)의 임시 id 머리 */
        private const val TITLE_ONLY = "t:"

        /** 목록에 없던 책([State.TITLE_ONLY])의 임시 id: 같은 제목([normTitle])이면 같은 책 */
        fun titleOnlyId(title: String) = TITLE_ONLY + normTitle(title)

        /**
         * 조금이라도 읽었거나 완독한 모든 책(지운 책 포함, [hidden]). 읽은 시간은 칸에 정해 둔 책 id([ReadKey.book])로 모으고(제목으로 다시 찾지
         * 않는다. 같은 제목의 새 책이 생기거나 제목을 고쳐도 그대로), 책을 모르는 칸은 제목만 든 기록(목록에 없던 책).
         * 완독한 책은 읽은 시간이 없어도(기록을 켜기 전에 읽은 책) 들어간다.
         */
        fun all(context: Context): List<BookRecord> =
            // 사용 기록 권한이 없으면(꺼 둠·잠금 해제 전) 저장해 둔 지난 기록으로(회차 판단과 책 기록이 사라지지 않게)
            all(context, ReadingLog.days(context, ReadingLog.readerPackages(context)) ?: ReadingLog.savedDays(context))

        /** [all] 의 몸통: 날마다 칸별 읽은 시간([days], 사용 기록 권한이 없으면 null)을 책에 붙인다(테스트는 읽은 시간을 직접 넣는다) */
        internal fun all(context: Context, days: ReadingDays?): List<BookRecord> {
            val shelf = BookShelf.list(context)
            val history = BookShelf.history(context)
            val finishes = BookShelf.finishList(context)
            class Acc(val book: ShelfBook, val state: State, val hidden: Boolean) {
                val dayMs = sortedMapOf<LocalDate, Long>()
            }
            val acc = linkedMapOf<String, Acc>()
            val reading = shelf.associateBy { it.id }
            val left = history.associateBy { it.book.id }
            // 앱마다 한 번만 묻는다(책마다 앱 목록이 바뀌었는지 시스템에 묻지 않게)
            val readerKeys = HashMap<String, String?>()
            fun readerKey(pkg: String) = if (pkg in readerKeys) readerKeys[pkg] else AppStore.readerKey(context, pkg).also { readerKeys[pkg] = it }
            fun accOf(k: ReadKey): Acc {
                reading[k.book]?.let { b -> return acc.getOrPut(b.id) { Acc(b, State.READING, false) } }
                left[k.book]?.let { p -> return acc.getOrPut(p.book.id) { Acc(p.book, State.PAST, p.hidden) } }
                // 책을 모르는 시간(목록에 없던 책). 있어서는 안 되지만 어디에도 없는 책 id 의 시간도 다른 책에 붙이지 않고 여기로.
                val id = titleOnlyId(k.title)
                return acc.getOrPut(id) { Acc(ShelfBook(id, k.title, "", readerKey(k.pkg), 0), State.TITLE_ONLY, false) }
            }
            days?.forEach { (day, keys) ->
                keys.forEach { (k, ms) ->
                    // 어떤 책인지 모르는 시간(그 앱에서 편 책을 몰랐을 때)은 합계에만
                    if (k.title.isEmpty() && k.book.isEmpty()) return@forEach
                    val a = accOf(k)
                    a.dayMs[day] = (a.dayMs[day] ?: 0) + ms
                }
            }
            // 다시 읽는 중인 책은 읽고 있는 책으로. 직접 넣은 책([manual])은 읽은 시간이 붙지 않으니 완독하지 않았어도 늘.
            shelf.filter { finishes[it.id] != null || it.manual }.forEach { b -> acc.getOrPut(b.id) { Acc(b, State.READING, false) } }
            history.filter { finishes[it.book.id] != null || it.book.manual }.forEach { p ->
                acc.getOrPut(p.book.id) { Acc(p.book, State.PAST, p.hidden) }
            }
            val openReviews = BookShelf.reviews(context)
            val starts = BookShelf.roundStarts(context)
            val began = BookShelf.roundBegans(context)
            val drops = BookShelf.roundDrops(context)
            return acc.values.map {
                BookRecord(
                    it.book, it.state, it.hidden, finishes[it.book.id].orEmpty(), kept(it.dayMs, drops[it.book.id]), openReviews[it.book.id], starts[it.book.id],
                    it.book.id in starts, began[it.book.id],
                )
            }
        }

        /** 책의 날마다 읽은 시간에서 지운 회차([BookShelf.forgetRound])에 읽은 날을 뺀 것 */
        private fun kept(dayMs: Map<LocalDate, Long>, drops: List<BookShelf.Drop>?): Map<LocalDate, Long> =
            if (drops.isNullOrEmpty()) dayMs else dayMs.filterKeys { d -> drops.none { it.covers(d) } }

        /** 읽고 있는 책 한 권의 기록(읽은 시간도 완독도 없으면 빈 기록) */
        fun of(context: Context, book: ShelfBook): BookRecord =
            all(context).firstOrNull { it.book.id == book.id } ?: empty(context, book, State.READING, false)

        /** 읽은 시간이 없는 책의 기록(완독·별점·감상·회차 시작은 저장된 대로) */
        private fun empty(context: Context, book: ShelfBook, state: State, hidden: Boolean): BookRecord {
            val starts = BookShelf.roundStarts(context)
            return BookRecord(
                book, state, hidden, BookShelf.finishList(context)[book.id].orEmpty(), emptyMap(),
                BookShelf.reviews(context)[book.id], starts[book.id], book.id in starts, BookShelf.roundBegans(context)[book.id],
            )
        }
    }
}
