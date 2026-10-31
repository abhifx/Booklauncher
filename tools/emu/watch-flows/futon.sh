# Futon(9.9): 탐색 › 내장 메모리 → 둘째 칸의 작품 상세 → `계속`(또는 `지금 읽기`) → 메뉴(작품 이름·화 이름) → 상세(진행률) → 홈.
# 에뮬에는 만든 만화 두 편(`Sea Song`·`별빛 도서관`, .zip)을 내장 메모리 › 불러오기 › 코믹스 모음으로 넣어 두었다(파일 선택 화면은 탭을 받지 않아 방향키·Enter 로 골랐다).
# 상세 없이 여는 길(최근에 본 만화 탭의 `계속` 단추: 책 모름 → 메뉴의 작품 이름)은 둘째 탭(930 1245) 뒤 메뉴로 본다.
# Futon 은 떠 있던 화면을 이어 보여 주므로 Futon 만 강제 종료해 첫 탭에서 시작한다(BookLauncher 는 강제 종료하지 않는다).
P=io.github.landwarderer.futon
a shell am force-stop $P
launch $P 6
tap 669 1403 3
tap 277 292 3
shot 1-shelf
tap 405 444 4
shot 2-detail
tap 869 1396 6
tap 536 728 3
shot 3-menu
key KEYCODE_BACK 3
shot 4-detail
home 4
