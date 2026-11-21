package com.woody.booklauncher

/** One choice in a reading-quest chapter. [trait] decides the ending. */
internal data class QuestChoice(val ko: String, val en: String, val trait: QuestTrait)

internal enum class QuestTrait(val ko: String, val en: String) {
    COURAGE("용기", "courage"),
    WISDOM("지혜", "wisdom"),
    KINDNESS("다정함", "kindness"),
}

internal data class QuestChapter(
    val titleKo: String,
    val titleEn: String,
    val textKo: String,
    val textEn: String,
    val requires: Long,
    val choices: List<QuestChoice>,
)

/**
 * A short reading adventure for BookLauncher. Reading time opens the next
 * chapter; each chapter asks for one choice, and the ending follows the trait
 * chosen most often. The prose is original and bilingual, not copied from
 * another app's resource pack.
 */
internal object ReadingQuest {
    val chapters: List<QuestChapter> = listOf(
        QuestChapter(
            "빗속의 등불", "The Lantern in the Rain",
            "비가 내리는 밤, 당신은 골목 끝에서 작은 등불을 따라 걸음을 옮긴다. 등불은 낡은 도서관 문 앞에서 멈춘다. 문 안쪽에서 한 사람이 말한다. \"페이지를 넘길 때마다 등불 하나가 살아납니다. 들어오시겠어요?\"",
            "On a rainy night you follow a small lantern to the end of an alley. It stops at the door of an old library. A voice inside says: \"Every page you turn keeps one lantern alive. Will you come in?\"",
            0,
            listOf(
                QuestChoice("등불을 들고 문 안으로 들어간다", "Take the lantern and step inside", QuestTrait.COURAGE),
                QuestChoice("누가 등불을 밝히는지 묻는다", "Ask who lights the lanterns", QuestTrait.WISDOM),
            ),
        ),
        QuestChapter(
            "여백의 지도", "The Map in the Margins",
            "서가 사이에 펼쳐진 지도가 있다. 책 가장자리의 여백에 그려진 강과 다리, 그리고 아직 이름이 없는 섬. 지도의 잉크가 천천히 흐른다.",
            "A map lies open between the shelves: rivers and bridges drawn in the margins of books, and an island with no name yet. The ink moves slowly.",
            30,
            listOf(
                QuestChoice("잉크 강을 따라 걷는다", "Follow the river of ink", QuestTrait.COURAGE),
                QuestChoice("지도를 끝까지 읽는다", "Read the map to its edge", QuestTrait.WISDOM),
            ),
        ),
        QuestChapter(
            "사서의 규칙", "The Keeper's Rule",
            "벽에는 규칙이 하나 붙어 있다. '어떤 책도 거꾸로 읽지 말 것.' 사서는 규칙을 가리키며 미소 짓는다. \"지키는 사람도 있고, 묻는 사람도 있지요.\"",
            "One rule hangs on the wall: 'Never read a book backwards.' The keeper points to it and smiles. \"Some keep it. Some ask why.\"",
            90,
            listOf(
                QuestChoice("규칙을 어기고 책을 거꾸로 펼친다", "Break the rule and open a book backwards", QuestTrait.COURAGE),
                QuestChoice("규칙의 이유를 묻는다", "Ask why the rule exists", QuestTrait.WISDOM),
                QuestChoice("다음 사람에게 짧은 쪽지를 남긴다", "Leave a short note for the next reader", QuestTrait.KINDNESS),
            ),
        ),
        QuestChapter(
            "말이 없는 서가", "The Silent Shelf",
            "글자가 없는 책들만 꽂힌 서가. 그 아래에 한 아이가 앉아 책장을 넘기고 있다. 아이는 고개를 들지 않는다.",
            "A shelf holds books with no words. A child sits beneath it, turning pages without looking up.",
            180,
            listOf(
                QuestChoice("옆에 앉아 함께 책장을 넘긴다", "Sit down and turn the pages together", QuestTrait.KINDNESS),
                QuestChoice("책등에서 숨은 제목을 찾는다", "Look for a hidden title on the spines", QuestTrait.WISDOM),
                QuestChoice("서가 맨 위까지 올라간다", "Climb to the top of the shelf", QuestTrait.COURAGE),
            ),
        ),
        QuestChapter(
            "여우 사서", "The Fox Librarian",
            "재가 된 색인 책을 끌어안은 여우가 길을 막는다. 여우는 말한다. \"이 책을 고치려면 이야기가 필요해. 네 이야기면 충분할까?\"",
            "A fox blocks the way, holding a burnt index. \"To mend this book I need a story. Will yours be enough?\"",
            300,
            listOf(
                QuestChoice("색인을 함께 고치겠다고 한다", "Offer to mend the index together", QuestTrait.KINDNESS),
                QuestChoice("이야기를 한 장과 바꾼다", "Trade a story for one page", QuestTrait.WISDOM),
                QuestChoice("여우의 시선을 피해 달린다", "Dodge the fox and run", QuestTrait.COURAGE),
            ),
        ),
        QuestChapter(
            "잉크 강", "The River of Ink",
            "도서관 아래로 검은 강이 흐른다. 강가에 작은 배와 삿갓 쓴 뱃사공이 있다. 강 건너편에는 아직 꺼지지 않은 등불이 하나 보인다.",
            "A black river runs under the library. A small boat and a boatman in a straw hat wait at the bank. One lantern still burns on the far side.",
            450,
            listOf(
                QuestChoice("배에 올라 강을 건넌다", "Board the boat and cross", QuestTrait.COURAGE),
                QuestChoice("강의 이름을 묻는다", "Ask the river its name", QuestTrait.WISDOM),
                QuestChoice("물에 빠진 나방을 건져 준다", "Save a drowning moth", QuestTrait.KINDNESS),
            ),
        ),
        QuestChapter(
            "불탄 색인", "The Burned Index",
            "색인의 마지막 장에 당신의 이름이 적혀 있다. 이름 옆에는 한 단어만 남아 있다. '미완성'.",
            "On the last page of the index your name is written. Beside it, one word remains: 'unfinished'.",
            600,
            listOf(
                QuestChoice("이름 옆에 결말을 쓴다", "Write an ending beside your name", QuestTrait.COURAGE),
                QuestChoice("앞뒤의 기록을 읽어 본다", "Read the entries around it", QuestTrait.WISDOM),
                QuestChoice("다음 사람을 위한 빈 페이지를 남긴다", "Leave a blank page for the next reader", QuestTrait.KINDNESS),
            ),
        ),
        QuestChapter(
            "밤의 낭독", "The Night Reading",
            "모든 등불이 꺼지고 하나만 남았다. 어둠 속에서 목소리가 들린다. \"소리 내어 읽어 줄 수 있나요? 잠들기 전에 이 책의 끝을 듣고 싶어요.\"",
            "Every lantern has gone out but one. A voice in the dark asks: \"Will you read aloud? I want to hear how this book ends before I sleep.\"",
            800,
            listOf(
                QuestChoice("어둠 속에서도 끝까지 읽는다", "Read to the end, even in the dark", QuestTrait.COURAGE),
                QuestChoice("먼저 목소리의 주인을 찾는다", "Find who the voice belongs to first", QuestTrait.WISDOM),
                QuestChoice("마지막 등불을 나눠 든다", "Share the last lantern", QuestTrait.KINDNESS),
            ),
        ),
        QuestChapter(
            "펼쳐진 책", "The Open Book",
            "도서관 전체가 한 권의 책으로 접힌다. 사서가 마지막으로 묻는다. \"이제 이 책을 어떻게 할 건가요?\"",
            "The whole library folds into a single book. The keeper asks one last time: \"What will you do with it now?\"",
            1000,
            listOf(
                QuestChoice("책 속으로 걸어 들어간다", "Step into the book", QuestTrait.COURAGE),
                QuestChoice("조용히 덮는다", "Close it gently", QuestTrait.WISDOM),
                QuestChoice("다음 사람을 위해 펼쳐 둔다", "Leave it open for the next reader", QuestTrait.KINDNESS),
            ),
        ),
    )

    val totalMinutes: Long = chapters.last().requires

    fun ending(choices: List<Int>): Pair<String, String> {
        val counts = IntArray(QuestTrait.entries.size)
        choices.forEachIndexed { chapter, choice ->
            val c = chapters.getOrNull(chapter)?.choices?.getOrNull(choice) ?: return@forEachIndexed
            counts[c.trait.ordinal]++
        }
        val best = counts.maxOrNull() ?: 0
        val winners = counts.indices.filter { counts[it] == best }
        return when {
            winners.size != 1 -> "긴 밤의 도서관" to "The Long Night"
            winners[0] == QuestTrait.COURAGE.ordinal -> "등불의 길" to "The Lantern Road"
            winners[0] == QuestTrait.WISDOM.ordinal -> "조용한 색인" to "The Quiet Index"
            else -> "열린 문" to "The Open Door"
        }
    }

    fun endingText(choices: List<Int>): Pair<String, String> {
        return when (ending(choices).second) {
            "The Lantern Road" -> "당신은 책 속으로 걸어 들어간다. 다음 날 아침, 도서관에는 등불이 하나 더 늘어 있다. 누군가 당신의 이름을 부르며 그 등불을 집어 든다." to
                "You step into the book. The next morning the library has one more lantern. Someone calls your name and picks it up."
            "The Quiet Index" -> "당신은 책을 덮고 색인을 정리한다. 도서관은 조용해지고, 찾아오는 사람마다 필요한 책이 손닿는 곳에 놓여 있다." to
                "You close the book and mend the index. The library grows quiet, and every visitor finds the book they need within reach."
            "The Open Door" -> "당신은 책을 펼친 채로 둔다. 밤새 다른 사람들이 들어와 한 페이지씩 읽는다. 아침이 되면 책은 조금 더 두꺼워져 있다." to
                "You leave the book open. All night, other readers come in and read a page each. By morning the book is a little thicker."
            else -> "당신은 마지막 등불을 든 채 긴 밤을 보낸다. 세 가지 마음이 모두 남아 있어, 어떤 하나로도 결말을 정하지 못한다. 그래서 이야기는 계속된다." to
                "You spend the long night holding the last lantern. All three feelings remain, and no single ending will do. So the story continues."
        }
    }
}
