# 북큐브 연재(웹소설, 북큐브 계정으로 무료 화를 받아 둔 것, 화마다 한 권): 내서재 리스트형 맨 위 작품 칸(최근 읽은 책) → 같은 화면의 화 목록 첫 칸 →
# 읽는 화면 → 두 쪽 넘기기 → 메뉴(제목 `작품 1화`) 열고 닫기 → 뒤로 서재 → 홈. 앱이 화 목록에 있으면 작품 칸 대신 첫 화 칸을 누르게 된다(같은 결과)
launch com.bookcube.bookplayer 8
shot 1-shelf
tap 560 320 4
tap 560 320 12
tap 1000 700 2
tap 1000 700 2
tap 536 700 3
key KEYCODE_BACK 2
key KEYCODE_BACK 6
shot 3-shelf
home 5
