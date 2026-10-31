# Kobo Books(게스트): My Books 둘째 칸(최근 활동 순) → 메뉴(진행률) → 돌아와 맨 위 칸으로 앎 → 홈. 책 상세 길은 `kobo2`.
# 에뮬에는 게스트로 미리보기 두 권(The Hollow·Pride and Prejudice)을 Save Preview 로 넣어 두었다.
# Kobo 는 떠 있던 화면(읽는 화면·상세)을 이어 보여 주므로 Kobo 만 강제 종료해 홈 탭에서 시작한다(BookLauncher 는 강제 종료하지 않는다).
P=com.kobobooks.android
a shell am force-stop $P
launch $P 8
tap 537 1380 4
shot 1-shelf
tap 500 860 8
shot 2-viewer
tap 1000 700 2
tap 536 600 4
shot 3-menu
key KEYCODE_BACK 2
key KEYCODE_BACK 6
shot 4-shelf
home 4
