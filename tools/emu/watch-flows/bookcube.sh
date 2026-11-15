# 북큐브(로그인 없이 내서재에 있는 무료 책, CremaPebble_A11_Play): 내서재 리스트형 첫 칸(최근 읽은 책이 맨 위) → 읽는 화면 →
# 오른쪽 눌러 두 쪽 넘기기 → 가운데 눌러 메뉴(따로 액티비티) 열고 닫기 → 뒤로 서재 → 홈.
launch com.bookcube.bookplayer 10
shot 1-shelf
tap 560 290 10
tap 1000 700 2
tap 1000 700 2
tap 536 700 3
shot 2-menu
key KEYCODE_BACK 2
key KEYCODE_BACK 5
shot 3-shelf
home 5
