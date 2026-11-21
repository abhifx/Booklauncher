package com.woody.booklauncher.games

/**
 * The Intercept: 짧은 첩보 선택형 이야기. CrossNext의 리소스 그림 대신 글과 선택지만 쓴다.
 * 진행은 마지막 장을 `games` 설정에 남겨 앱을 다시 띄워도 이어 간다.
 */
internal class InterceptStory {
    data class Choice(val ko: String, val en: String, val next: String)
    data class Node(val id: String, val titleKo: String, val titleEn: String, val ko: String, val en: String, val choices: List<Choice>)

    var node = "start"
    var over = false
    var ending = ""

    fun current(): Node = NODES.getValue(node)

    fun choose(index: Int): Boolean {
        if (over) return false
        val c = current().choices.getOrNull(index) ?: return false
        node = c.next
        if (NODES[node]?.choices.isNullOrEmpty()) {
            over = true
            ending = node
        }
        return true
    }

    fun restart() {
        node = "start"
        over = false
        ending = ""
    }

    fun encode(): String = "$node|${if (over) 1 else 0}|$ending"

    companion object {
        fun decode(s: String): InterceptStory? = runCatching {
            val p = s.split('|')
            if (p[0] !in NODES) return null
            InterceptStory().apply {
                node = p[0]
                over = p[1] == "1"
                ending = p.getOrElse(2) { "" }
            }
        }.getOrNull()

        private val NODES: Map<String, Node> = listOf(
            Node(
                "start", "빗속의 역", "The Station in the Rain",
                "자정. 플랫폼의 형광등 하나가 깜빡인다. 당신은 마라, 정보국 소속이다. 검은 코트의 남자가 오른손에 신문을 들고 서 있다. 접선 신호다. 하지만 오늘은 연락관이 오지 않았다.",
                "Midnight. One fluorescent tube flickers over the platform. You are Mara, an intelligence officer. A man in a black coat waits with a newspaper in his right hand: the contact signal. But your handler never came.",
                listOf(
                    Choice("다가간다", "Approach him", "approach"),
                    Choice("기차를 탄다", "Board the train", "train"),
                    Choice("역을 빠져나간다", "Leave the station", "leave"),
                ),
            ),
            Node(
                "approach", "가까이", "Close",
                "남자가 신문을 낮춘다. \"연락관이 죽었어.\" 그의 목소리는 젖은 종이처럼 낮다. \"이걸 가지고 가. 나를 믿든 말든.\" 그는 작은 은색 열쇠를 내민다.",
                "The man lowers the paper. \"Your handler is dead.\" His voice is low, like wet paper. \"Take this. Trust me or don't.\" He offers a small silver key.",
                listOf(
                    Choice("열쇠를 받는다", "Take the key", "key"),
                    Choice("묻는다: 누가 죽였나", "Ask who killed him", "ask"),
                ),
            ),
            Node(
                "train", "막차", "The Last Train",
                "문이 닫힌다. 창밖으로 남자가 뛰어온다. 그는 유리창에 손바닥을 대고 입 모양으로 말한다. '삼 분.' 그다음 어둠이 그를 삼킨다.",
                "The doors close. Outside, the man runs. He presses a palm to the glass and mouths: 'Three minutes.' Then the dark takes him.",
                listOf(
                    Choice("다음 역에서 내린다", "Get off at the next stop", "platform"),
                    Choice("그대로 간다", "Stay on board", "ride"),
                ),
            ),
            Node(
                "leave", "뒤로", "Back",
                "계단을 오르자 누군가 뒤따른다. 발소리는 하나, 하지만 그림자는 둘이다. 역무원의 안내판이 흔들린다.",
                "Someone follows you up the stairs. One set of footsteps, two shadows. The station sign sways.",
                listOf(
                    Choice("돌아본다", "Turn around", "shadow"),
                    Choice("뛴다", "Run", "street"),
                ),
            ),
            Node(
                "key", "은색 열쇠", "The Silver Key",
                "역 뒤편의 오래된 사물함. 열쇠가 맞는다. 안에는 봉투 하나와 정류장 표 한 장. 봉투에는 당신의 필체로 '믿지 마'라고 적혀 있다.",
                "An old locker behind the station. The key fits. Inside: an envelope and a bus ticket. On the envelope, in your own handwriting: 'Don't trust.'",
                listOf(
                    Choice("봉투를 연다", "Open the envelope", "reveal"),
                    Choice("표를 보고 역을 떠난다", "Take the ticket and go", "bus"),
                ),
            ),
            Node(
                "ask", "이름", "The Name",
                "\"윤.**\" 남자가 말을 삼킨다. 당신의 연락관 이름이다. \"그가 마지막으로 남긴 말은, 네가 아직 안전하지 않다는 거였어.\"",
                "\"Yun.**\" The man swallows the rest. It is your handler's name. \"His last words were that you are not safe yet.\"",
                listOf(
                    Choice("열쇠를 받는다", "Take the key", "key"),
                    Choice("그를 역에 두고 간다", "Leave him at the station", "street"),
                ),
            ),
            Node(
                "platform", "다음 역", "The Next Stop",
                "플랫폼은 비어 있다. 안내판에는 당신이 내린 역 이름이 없다. 대신 벤치 위에 검은 우산이 놓여 있다. 손잡이에 작은 은색 열쇠가 묶여 있다.",
                "The platform is empty. The sign does not name the station you left. On the bench lies a black umbrella, a small silver key tied to its handle.",
                listOf(
                    Choice("우산을 가져간다", "Take the umbrella", "key"),
                    Choice("기차에 다시 탄다", "Get back on the train", "ride"),
                ),
            ),
            Node(
                "ride", "끝까지", "To the End",
                "기차는 텅 빈 채로 달린다. 창밖의 도시가 반복된다. 같은 간판, 같은 불빛. 당신은 주머니에서 봉투를 꺼낸다. 안에는 아무것도 없고, 봉투 안쪽에 작은 글씨가 있다. '여기서 내려.'",
                "The train runs empty. The city repeats outside: the same signs, the same lights. You take out the envelope. It is empty; inside, in tiny letters: 'Get off here.'",
                listOf(
                    Choice("내린다", "Get off", "reveal"),
                    Choice("계속 앉아 있는다", "Keep sitting", "ending_loop"),
                ),
            ),
            Node(
                "shadow", "그림자", "The Shadow",
                "뒤에는 아무도 없다. 하지만 벽에 비친 그림자가 한 박자 늦게 움직인다. 당신은 숨을 죽이고 계단 난간을 잡는다.",
                "No one is behind you. But the shadow on the wall moves a beat late. You hold your breath and grip the rail.",
                listOf(
                    Choice("그림자에게 말을 건다", "Speak to the shadow", "ending_shadow"),
                    Choice("역무실로 뛴다", "Run to the station office", "street"),
                ),
            ),
            Node(
                "street", "거리", "The Street",
                "비가 그쳤다. 골목 끝에서 검은 차가 시동을 걸고 있다. 당신은 봉투를 품에 넣고 걷기 시작한다. 누가 뒤에서 당신의 이름을 부른다.",
                "The rain has stopped. At the end of the alley a black car starts. You tuck the envelope inside your coat and walk. Someone behind you calls your name.",
                listOf(
                    Choice("멈춘다", "Stop", "ending_car"),
                    Choice("계속 걷는다", "Keep walking", "ending_rain"),
                ),
            ),
            Node(
                "reveal", "봉투", "The Envelope",
                "봉투 안에는 사진 한 장이 있다. 당신과 연락관이 함께 찍힌 사진, 그 뒤에 오늘 날짜가 적혀 있다. 그리고 한 줄. '이 사진이 공개되면 너는 살아 있는 게 아니야.'",
                "Inside is a photograph of you and your handler, dated today. Beneath it, one line: 'If this photo goes public, you are not alive.'",
                listOf(
                    Choice("사진을 태운다", "Burn the photo", "ending_fire"),
                    Choice("사진을 가지고 간다", "Keep the photo", "ending_car"),
                ),
            ),
            Node(
                "bus", "정류장", "The Stop",
                "표에 적힌 정류장은 폐선된 노선이다. 그곳에 도착했을 때, 당신의 연락관이 우산을 들고 서 있다. 그는 분명히 죽었다고 들었다.",
                "The stop on the ticket belongs to a closed route. When you arrive, your handler is standing there with an umbrella. You were told he was dead.",
                listOf(
                    Choice("다가간다", "Approach", "ending_handler"),
                    Choice("돌아선다", "Turn away", "ending_rain"),
                ),
            ),
            Node(
                "ending_fire", "불", "Fire",
                "사진은 재가 되고, 비가 재를 씻어 간다. 당신은 역을 떠난다. 누가 당신을 기억하든, 증거는 없다.",
                "The photo becomes ash, and the rain washes it away. You leave the station. Whatever anyone remembers, there is no evidence.",
                emptyList(),
            ),
            Node(
                "ending_car", "검은 차", "The Black Car",
                "차문이 열린다. 뒷좌석에는 당신의 연락관이 앉아 있다. 그는 다친 다리를 감싸고 웃는다. \"늦었어. 하지만 아직 할 일이 남았지.\"",
                "The door opens. Your handler sits in the back, holding his injured leg and smiling. \"You're late. But there's still work to do.\"",
                emptyList(),
            ),
            Node(
                "ending_rain", "비", "Rain",
                "당신은 걷는다. 이름을 부르는 소리는 점점 멀어진다. 봉투는 젖고, 잉크가 번진다. 살아남으려면 아무것도 믿지 않아야 한다.",
                "You walk. The voice calling your name fades. The envelope gets wet and the ink runs. To survive, you must trust nothing.",
                emptyList(),
            ),
            Node(
                "ending_shadow", "그림자", "The Shadow",
                "그림자가 대답한다. \"네가 나를 알아본 건 처음이야.\" 목소리는 당신의 것이다. 계단 아래에서 또 다른 당신이 미소 짓는다.",
                "The shadow answers. \"It's the first time you've recognised me.\" The voice is yours. At the bottom of the stairs, another you smiles.",
                emptyList(),
            ),
            Node(
                "ending_handler", "우산", "Umbrella",
                "연락관은 우산을 건넨다. \"네가 올 줄 알았어.\" 우산 손잡이에는 은색 열쇠가 묶여 있다. 그는 덧붙인다. \"이제 시작이야.\"",
                "Your handler hands you the umbrella. \"I knew you'd come.\" A silver key is tied to the handle. He adds: \"Now it begins.\"",
                emptyList(),
            ),
            Node(
                "ending_loop", "반복", "Loop",
                "기차는 같은 역을 다시 지난다. 플랫폼에는 검은 코트의 남자가 서 있다. 그는 신문을 오른손에 들고 있다. 당신은 처음으로 돌아왔다.",
                "The train passes the same station again. On the platform, the man in the black coat stands with the newspaper in his right hand. You are back at the beginning.",
                emptyList(),
            ),
        ).associateBy { it.id }
    }
}
