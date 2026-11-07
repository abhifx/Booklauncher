# 이씨오 범용뷰어 3.0(로그인해 대출한 책이 있어야 한다, Play 라이선스 확인이라 CremaPebble_A11_Play): 서재 첫 칸(목록·격자 보기 모두 이 자리) → 읽는 화면(웹 화면) →
# 메뉴 열고 닫기 → 뒤로 두 번(2초 안에 두 번 눌러야 닫힌다)으로 서재 → 홈. 앱을 서재에서 끝내 두어야 다음에 띄울 때 서재로 뜬다.
launch com.mackerly.eco.ecobookviewer3 10
shot 1-shelf
tap 187 420 12
tap 536 728 3
tap 536 728 3
key KEYCODE_BACK 0
key KEYCODE_BACK 6
shot 3-shelf
home 5
