# Wattpad(로그인한 계정, 서재 Current Reads 목록 보기): 서재 첫 칸(Pride and Prejudice (1813)) → 읽는 화면 → 쪽 넘김 → 뒤로(서재) → 홈.
# 읽던 화 중간에서 열리면 툴바·화 머리가 없어도 누른 칸으로 그 책이다(화 이름은 화 머리가 보일 때만 바뀐다).
# Wattpad 는 떠 있던 화면을 이어 보여 주므로 시작할 때 서재여야 한다. 아래 광고 띠(y 1345~)는 누르지 않는다(Chrome 이 열린다).
launch wp.wattpad 6
shot 1-shelf
tap 500 843 8
shot 2-viewer
a shell input swipe 536 1200 536 400 300
sleep 3
shot 3-scrolled
key KEYCODE_BACK 4
shot 4-shelf
home 4
