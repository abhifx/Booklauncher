# 북큐브 도서관 대출 책(경산시립 멀티전자도서관, 받지 않은 채): 내서재 리스트형 맨 아래 칸(서재를 끝까지 민다) → 받기 → 읽는 화면 →
# 두 쪽 넘기기 → 메뉴 열고 닫기 → 뒤로 서재 → 홈. 한 번 읽으면 최근 읽은 책이라 맨 위로 가니 그 뒤는 bookcube.sh
launch com.bookcube.bookplayer 8
a shell input swipe 536 1200 536 300 300; sleep 3
shot 1-shelf
tap 560 1200 90
tap 1000 700 2
tap 1000 700 2
tap 536 700 3
shot 2-menu
key KEYCODE_BACK 2
key KEYCODE_BACK 6
shot 3-shelf
home 5
