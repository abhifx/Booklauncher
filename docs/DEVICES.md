# 기기 호환 노트

Pebble Desk 를 크레마 페블 말고 다른 전자잉크 기기에서 쓸 수 있는지 조사한 기록이다. 새 기기를 조사하면 **한눈에** 표에 열을 더하고 기기 절을 하나 더 쓴다. 댓글로 들어온 문제의 진행 상태는 **docs/ISSUES.md** 에서 따라간다.
방법: 기기에서 `getprop`·`settings`·`adb pull` 한 시스템 파일, 제조사 OTA 펌웨어를 풀어 jadx 로 디컴파일, 리뷰 영상·설명서.
2026-10 기준. 실제 기기에서 확인하지 않은 것은 **(미확인)**, 같은 회사 다른 기종에서 미루어 본 것은 **(추정)** 으로 표시했다.

## 한눈에

|  | 크레마 페블 | Meebook E6 | Viwoods AiPaper Mini | iReader Ocean 5 Pro | Minimal Phone | Moaan MIX7S | iReader Tango (1세대) | 교보 sam 7 mono | Onyx BOOX Leaf | Onyx BOOX Poke3 | iReader Neo 3 Ultra | 리디페이퍼 4 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 근거 | 실제 기기 | 실제 기기 사진(2026-10-07, 기기 정보 화면) + 리뷰 | 실제 기기 사진(2026-10-07, 홈 화면·기기 정보 화면) + 리뷰·판매 페이지·다른 개발자 기록 | 실제 기기 사진(2026-10-10, 기기 정보·접근성 화면) + Musnap Ocean 설명서(2025-12, 만든 곳 Shenzhen Zhangyue) + 리뷰 | 실제 기기 사진(2026-10-10, 기기 정보 화면) + 리뷰(HelenTech·Tom's Guide·Liliputing) | 실제 기기 사진(2026-10-10, 기기 정보·설정 › 독서 화면, 사용자 댓글) + Good e-Reader 기사 | 실제 기기 사진(2026-10-13, 기기 정보·홈 화면 각 1장) + 판매 사양 | 실제 기기 사진(2026-10-13, 설정·기기 정보 화면, 사용자) + 교보 상단 바 안내 그림·펀딩 페이지 | 실제 기기 사진(2026-10-14, 기기 정보 화면, 사용자) + 판매 사양 | 실제 기기 사진(2026-10-14, 기기 정보 화면, 사용자) + 판매 사양 | 실제 기기 사진(2026-10-18, 기기 정보 화면, 사용자 댓글) + 출시 기사(2026-03) | 실제 기기 사진(2026-10-23, 기기 정보·홈 화면, 사용자 댓글) + 출시 기사(2022-04) |
| 화면 | 6" 1072×1456, 밀도 300 (1dp = 1.875px) | 6" Carta 1300 1072×1448, 밀도 320 (1dp = 2px) | 8.2" Carta 1000 1440×1920, 약 293ppi(제조사는 297·292), 밀도 320 (1dp = 2px) | 7" Carta 1300 1264×1680, 300ppi, 밀도 320 (632×840dp) | 4.3" 600×800(4:3), 약 230ppi, 밀도 240 (1dp = 1.5px) | 7" 1264×1680, 300ppi(기사, Carta 1200), 기기 밀도 **254** | 6.13" Carta 1300 824×1648, 약 300ppi(사양). 밀도는 400 으로 선언(실제와 다르다) | 7" Carta 1300 1264×1680, 300ppi, 밀도 320 (632×840dp) | 7" 1264×1680, 300ppi, 밀도 300 (674×896dp) | 6" 1072×1448, 300ppi, 밀도 300 (1dp = 1.875px) | 6" Carta 1300 1072×1448, 300ppi(기사). 밀도는 240 으로 선언(실제와 다르다) | 7" 1264×1680, 밀도 320 (632×840dp) |
| dp 크기 | 572×777dp | 536×724dp | 720×960dp → 앱 기준 572×762dp(밀도 맞춤 403) | 앱 기준 571×759dp(밀도 맞춤 354, 기기 정보) | 400×533dp → 앱 기준 571×762dp(밀도 맞춤 168, 1dp = 1.05px) | 796×1058dp → 앱 기준 674×896dp(밀도 300. 맞춤대로면 354·572dp 여야 한다, 아래) | 330×659dp → 앱 기준 573×1146dp(밀도 맞춤 230, 1dp = 1.44px). 긴 변 ÷ 짧은 변 **2.0**(바형) | 앱 기준 571×759dp(밀도 맞춤 354) | 674×896dp → 앱 기준 571×759dp(밀도 맞춤 354) | 572×772dp (밀도 맞춤 없음) | 715×965dp → 앱 기준 572×772dp(밀도 맞춤 300) | 앱 기준 571×759dp(밀도 맞춤 354) |
| 칩 · OS | RK3566 · 안드로이드 11 (API 30) | RK3566 (`updatemodel`) · 안드로이드 11 (API 30) | MediaTek MT8788 (판매 페이지) · 안드로이드 13 (API 33), `Build.DISPLAY=3.15.15` | 8코어 2.2GHz(Ocean 5 Pro·Musnap. 표준 Ocean 5 는 4코어) · 안드로이드 14 (API 34, 빌드 `UP1A.231005.007`, SmartOS 3.1) | MediaTek Helio G99 (리뷰) · 안드로이드 14 (API 34), 빌드 `MP01_20260104_1412` | RK3566(`rk3566_ebook`) · 안드로이드 14 (API 34, 빌드 `UQ1A.240205.004.B1`). 기사의 MIX 7S 는 4코어 A55 · 안드로이드 11 이라 펌웨어나 판이 다르다 | 안드로이드 14 (API 34), 빌드 `UP1A.231005.007`. 칩 **(미확인)** | RK3576 (`updatemodel`) · 안드로이드 14 (API 34), 빌드 `KBRead-V2.2.0-2026070618` | 안드로이드 10 (API 29), 빌드 `2023-12-12_23-00_3.5_4687b1901`(BOOX 펌웨어 3.5). 칩 **(미확인)** | 안드로이드 10 (API 29), 빌드 `2023-12-12_23-45_3.5_4687b1901`(BOOX 펌웨어 3.5, Leaf 와 같은 커밋). 칩 **(미확인)** | 8코어 · 4GB · 64GB(기사) · 안드로이드 14 (API 34), 빌드 `UP1A.231005.007`(Ocean·Tango 와 같다) | Exynos 850 · 안드로이드 10 (API 29), 빌드 `QP1A.190711.020 dev-keys` |
| `Build.MODEL` | `CREMA PEBBLE` | `E6` | `Viwoods AiPaper Mini`. 제조사 모델 번호는 `SE05`(FCC ID `2BKO6-SE05`) | `Ocean 5 Pro`(기기 정보). Musnap Ocean 모델 번호 `SM07A-AKDW`(FCC ID `2BQD6-SM07A-OCEAN`) | `MP01` | `MoaanMIX7S` | `Tango` | `sam7mono` | `Leaf` | `Poke3` | `Neo 3 Ultra` | `RIDIPAPER 4` |
| `Build.DEVICE` | `CREMA_PEBBLE` | `E6` | `Viwoods` | `SM07A-CKDA`(Ocean 5 Pro 기기 정보, 모델 번호와 같다) | `MP01` | `rk3566_ebook` | `PM06A-CKD` | `sam7mono` | `Leaf` | `Poke3` | `RM06L-CKDW` | `exynos850` |
| `Build.MANUFACTURER` · `BRAND` | `into1` · `YES24` | `Haoqing` · `MEEBOOK` | `Viwoods` · `Viwoods AiPaper Mini` | `iReader` · `iReader`. 만든 곳 Shenzhen Zhangyue Technology(掌阅 iReader) | `ALONG` · `Minimal_Phone`. 파는 곳은 Minimal Company(미국) | `rockchip` · `rockchip`. 만든 곳 Moaan(墨案, 샤오미 생태계) | `iReader` · `iReader` | `Kyobo` · `Kyobo` | `ONYX` · `ONYX` | `ONYX` · `ONYX` | `iReader` · `iReader` | `Hajen Co., Ltd.` · `Android` |
| 펌웨어 만든 곳 | wetao / into1 (`ZlSystemUI`, `ZlSettings`) | Haoqing | ODM **Wisky**(`ro.build.user=wisky`, 시스템 앱 `com.wisky.*`), 위에 Viwoods 앱. 펌웨어 3.x, 두 달쯤마다 업데이트. Rockchip 계열이 아니다 | 掌阅 iReader **SmartOS** 3.x. Musnap 은 같은 기기에 이름만 바꾼 것 | `ALONG`(제조사 값. 만든 곳으로 보인다 **(추정)**), 위에 Minimal 런처(`com.example.minimallauncher`)·빠른 설정 앱. AOSP Launcher3(Quickstep)도 들어 있다 | Moaan(시스템 앱 `com.moan.*`, 설정 값 `APPS_WHITE_LIST_KEY`·`APPS_HIDDEN_LIST_KEY`). Rockchip 개발 키트 계열(`init.svc.vendor.*-rockchip`) | 掌阅 iReader SmartOS **(추정, Ocean 과 같은 회사)** | Haoqing(`ro.haoqing.brand=haoqing-meebook`, `updatemodel=Kyobo-G7-RK3576`)을 교보가 고친 것 | Onyx(속성 `init.svc.onyx-sh`·`sys.onyx.idledelay=3000`, 설정 값 `cold_brightness_state_key`·`ctm_brightness_state_key`·`ctm_temperature_state_key`·`low_power_work_…`(잘림)) | Onyx(Leaf 와 같은 속성·설정 값) | 掌阅 iReader SmartOS(기사) | Hajen(속성 `ro.epd.fw.version=Hajen_v.1.0`, 전자잉크는 `epdd` 데몬) |
| 안드로이드 하단 내비게이션 바 | 없음 | 없음. 대신 **상단 바**에 홈·뒤로·새로고침·조명 버튼과 최근 앱 버튼(약 50dp, 사진으로 어림) | 없음(사진). 화면 아래 정전식 버튼 3개: 뒤로·홈·AI | 없음 **(추정)**. 아래 가장자리 끌어올리기: 왼쪽 = 작업 관리자, 가운데 = 홈, 오른쪽 = 표시 최적화(리뷰). 모든 화면 위에 시스템 상단 바(시각 · BT · 조명 · 배터리 · `···`, 설명서 그림) | 없음. 화면 아래 **물리 버튼 3개**(뒤로·홈·최근, 리뷰). 안드로이드는 3버튼 모드인데 화면 버튼을 숨겼다(리뷰). 화면 위에 안드로이드 기본 상단 바(사진) | 없음(사진. 아래 가운데 손잡이 줄만, `gesture_hidden=1`) | 없음(홈 사진) | 없음. 대신 **상단 바**에 홈·뒤로·새로고침·조명·(블루투스·Wi-Fi)·설정, 오른쪽 파일 관리자·배터리·시각 | **(미확인)**. 기기 정보 화면에 안드로이드 상단 바(시각·Wi-Fi·배터리)가 보인다 | **(미확인)**. 기기 정보 화면에 안드로이드 상단 바(시각·Wi-Fi·배터리)가 보인다 | **(미확인)**. 기기 정보 화면 위에 SmartOS 상단 바(시각·배터리)가 보인다 | **(미확인)**. 기기 정보 화면 위에 상단 바(조명·Wi-Fi·블루투스·설정·시각·배터리)가 보인다 |
| 빠른 설정 | 크레마 전용 앱 `com.inno.quicksetting` | 안드로이드 기본 패널을 고친 것, 타일 2줄×6칸 (실기기 사진) | Viwoods **제어 센터**: 오른쪽 위에서 손가락으로 끌어내림, 화면 오른쪽 절반 세로 패널. Wi-Fi·BT·새로고침·대비·버튼 잠금·새로고침 모드·밝기 등. 알림창은 없다. 왼쪽 위 끌어내리기 = 새로고침 | **제어 패널**: 화면 위에서 끌어내린다. 오른쪽 위 모서리에서 끌어내리면 캡처·노트·작업 전환 패널(Musnap 리뷰). 손동작은 설정 › 보조 기능 › 전역 손동작에서 바꿀 수 있다 | 안드로이드 기본 알림창(끌어내리기, 리뷰) + 왼쪽 옆 **새로고침 버튼 길게** = Minimal 설정 창(밝기·색온도·키보드 조명·새로고침 속도 3단계, HelenTech) | **(미확인)** | **(미확인)** | **없음**. 끌어내리는 패널이 없고 상단 바 아이콘마다 팝업(조명·새로고침)이나 설정 화면(블루투스·Wi-Fi)이 뜬다 | **(미확인)** | **(미확인)** | **(미확인)** | **(미확인)** |
| 빠른 설정 여는 방송 | `com.epd.drop_down` | `com.haoqing.action.QUICK_SETTINGS`. `메이북 ›` 로 열림(2026-10-07 사용자 확인). `크레마 ›` 는 아무 일 없음 | 없음. 크레마·메이북 방송으로는 안 열리고 안드로이드 기본 펼치기로 열린다 | **(미확인)**. 크레마·메이북 방송으로는 안 열릴 것 **(추정)** | 없음 **(추정)**. 기기 정보 사진에 `크레마 ›`·`메이북 ›` 결과 글자가 없다 | **(미확인)**. `크레마 ›`·`메이북 ›` 결과 글자 없음 | **(미확인)** | **(미확인)**. 펼칠 패널이 없어 아무 일도 없을 것 **(추정)** | **(미확인)** | **(미확인)** | **(미확인)** | **(미확인)** |
| 안드로이드 기본 펼치기(`expandSettingsPanel`) | 열림: 크레마식 시스템 알림창(밝기·음량·대비) | 열림. 메이북 방송과 같은 패널 | 열림: Viwoods 제어 센터(기기 정보 `안드로이드 ›`, 사용자 확인) | **(미확인)** | `보냄`(함수는 불렸다). 패널이 열렸는지 **(미확인)** | `보냄`. 열렸는지 **(미확인)** | **(미확인)** | **(미확인)** | **(미확인)** | **(미확인)** | **(미확인)** | **(미확인)** |
| 화면 대비 | `hq_contrast`(기본 56), 앱 화면을 밝기^(1/s) 로 어둡게, s = 1 − c×90/8000 | `Settings.System hq_contrast` 키가 **없다**(기기 정보에 `-`). 다른 곳에 두는 듯 **(미확인)** | 제어 센터의 대비 + 펌웨어 3.12(2026-01)부터 앱별 `App Display`(대비·연한 색 지우기). 키 **(미확인)** | 앱별 표시 최적화(대비·선명도·전체 새로고침 간격·다크 모드). 키 **(미확인)** | **(미확인)** | **(미확인)** | `persist.sys.eink.contrast=6`·`persist.sys.eink.regal=1`·`persist.eink.dither256.disable=0` 등 `persist.sys.eink.*` 속성(기기 정보) | `Settings.System hq_contrast=35` | **(미확인)** | **(미확인)** | **(미확인)**. 속성 `persist.sys.eink.contrast=30` | **(미확인)** |
| 페이지 버튼 | 없음 | 2개 + 화면 아래 정전식 버튼. `persist.haoqing.pagekeys=41`. 키 코드 **(미확인)** | 없음. 정전식 뒤로·홈·AI 버튼, 전원 겸 지문 버튼. AI 버튼 키 코드 **(미확인)** | 오른쪽 손잡이에 2개. 페이지 넘김이 필요 없는 앱에서는 **음량 키**로 동작(Musnap 리뷰) → 기기 정보에서 `24 KEYCODE_VOLUME_UP`(scan 115) 확인. Pebble Desk 목록은 설정 › 화면 › 페이지 버튼에서 음량 키를 정하면 넘어간다(기본은 안 넘어간다) | 없음. 물리 QWERTY 키보드(35키, 단축키 1개), 왼쪽 옆 음량·새로고침, 오른쪽 전원 겸 지문. 아래 물리 뒤로 = `4 KEYCODE_BACK`(scan 158, 리눅스 `KEY_BACK`) 확인 | 있음 → 기기 정보에서 `25 KEYCODE_VOLUME_DOWN`(scan 0). iReader 처럼 **음량 키**로 들어와 Pebble Desk 목록은 페이지 버튼에서 음량 키를 정해야 넘어간다. 설정 값 `long_side_key_a=0`·`long_side_key_b=5`(옆 버튼 배정으로 보인다 **(추정)**) | 오른쪽 옆에 버튼 2개(사진). 키 코드 **(미확인)** | 오른쪽 옆 2개. `persist.haoqing.pag…`(잘림). 키 코드 **(미확인)** | 있음(옆 버튼 2개, 판매 사양). 키 코드 **(미확인)**(기기 정보에서 누르지 않음) | 없음(판매 사양) | **(미확인)**. 설정 값 `down_key_long_function=7`(Ocean 에도 있는 키) | 있음(출시 기사). 키 코드 **(미확인)**(기기 정보에서 누르지 않음) |
| 기본 홈 바꾸기 | 됨 (지금 쓰는 중) | 됨 (기기 정보 `기본 홈 켜짐`, 홈 화면 사진) | 됨 (Pebble Desk 홈 사진. 리뷰에서도 Nova·inkOS 를 안드로이드 설정 › 앱 › 기본 앱 › 홈 으로 씀) | 됨 (기기 정보 `기본 홈 켜짐`) | 아직 안 함 (기기 정보 `기본 홈 꺼짐`. 원래 홈은 Minimal 런처) | 됨 (기기 정보 `기본 홈 켜짐`) | 됨 (기기 정보 `기본 홈 켜짐`, 홈 화면 사진) | 됨 (기기 정보 `기본 홈 켜짐`) | 됨 (기기 정보 `기본 홈 켜짐`) | 됨 (기기 정보 `기본 홈 켜짐`) | 됨 (기기 정보 `기본 홈 켜짐`) | 아직 안 함 (기기 정보 `기본 홈 꺼짐`) |
| Pebble Desk 판정 `Device.kind` | `CREMA` (`Build.DEVICE` 가 `CREMA` 로 시작) | `MEEBOOK` (기기 정보 `메이북`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) | `MEEBOOK` (기기 정보 `메이북`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) |

- 크레마와 메이북은 둘 다 **Rockchip 전자잉크 개발 키트**에서 나왔다(Viwoods 는 MediaTek 이라 다르다). 안드로이드 빌드 기반(`RQ2A.210505.003`), `android.os.EinkManager`, SystemUI 의 `EinkSettingsManager`·`EinkSettingsProvider`(`content://com.android.systemui.eink/einksettings`) 이름이 같다. 그 위를 회사마다 따로 고쳤다.
- `Build.DEVICE` 는 기기 코드명(`ro.product.device`)이다. 안드로이드 11 은 `ro.product.device` 가 직접 설정돼 있지 않으면 `ro.product.property_source_order`(기본 product, odm, vendor, system_ext, system) 순서로 `ro.product.<파티션>.device` 중 처음 비어 있지 않은 값을 쓴다(`init/property_service.cpp`). `MODEL`·`MANUFACTURER`·`BRAND` 도 같다.
- 앱 설정 › **기기 정보** 화면을 사진으로 찍어 받는다. 모델(코드명)·제조사·브랜드·안드로이드·빌드·화면(px·dpi·dp)·기기 종류·앱 버전, 메이북 속성·조명/대비 설정 값, 권한, **마지막으로 누른 버튼의 키 번호·스캔 코드**, **최근에 앞에 뜬 다른 화면**(사용 기록. 기본 런처에서 빠른 설정을 열었다 돌아오면 그것이 따로 된 화면인지 시스템 알림창인지 알 수 있다), 빠른 설정 세 방법(크레마·메이북 방송, 안드로이드 기본 펼치기) 시험이 한 화면에 있었다. 1.4.0 부터는 빠른 설정 시험을 빼고(결과가 `보냄` 뿐이라 열렸는지는 어차피 물어야 했다. 지금은 홈 상단 줄을 눌러 열리는지 묻는다), 앱이 밀도를 정할 때 본 크기, 자동 추가 서비스 연결·끊김과 앱이 끝난 까닭·배터리 최적화·대기 그룹·백그라운드 제한, 이 앱이 여는 시스템 화면(접근성·사용 기록·이 앱 사용 기록·앱 정보) 시험을 더했다. 새 기기는 이 사진부터 받는다.
- 기기마다 다른 동작은 `Device`(종류 판정·빠른 설정·조명·시스템 속성)에, 크레마 전용 화면 보정은 `Crema` 에 둔다.

## 크레마 페블 (YES24)

- 패널 맨 아래 약 8px 가 테두리에 가려 보이지 않는다 → `Crema.reserveHiddenBottom()`.
- 화면 대비: 앱 화면 전체를 어둡게 그리고 `ImageView` 그림만 다시 밝힌다(bleach). 직접 그리는 표지는 `Crema.undoContrast` 로 미리 밝힌다. 자세한 건 CLAUDE.md.
- 빠른 설정: SystemUI(`ZlSystemUI` 의 `StatusBar`)가 `com.epd.drop_down` 방송을 받아 크레마 빠른 설정 앱을 띄운다. 화면 위에서 끌어내릴 때도 `DisplayPolicy` 가 같은 방송을 보낸다.
- 조명: `Settings.System` 의 `screen_brightness`(백색)·`warm_light`(온색).
- 잔상 제거 횟수: `persist.vendor.fullmode_cnt`.
- 로고: 크레마 런처(`com.wetao.cremalauncher`)의 `ic_icon_crema` 를 불러온다.

## Meebook (Haoqing 펌웨어)

Meebook 은 선전 Haoqing Technology(`haoqingtech.com`)의 브랜드다. E6 안내서의 연락처가 `meebook@haoqingtech.com`·선전 바오안구 주소이고, MEEBOOK 상표 주인도 이 회사다. Good e-Reader 는 Boyue(Likebook) 출신들이 세운 회사라고 소개한다. 펌웨어 코드에 밀리의서재 기기 분기(`Build.BRAND == "Millie"`)와 한국어 문구, 한국 이북 앱 이름(교보·YES24·리디·알라딘·크레마 앱)이 들어 있어 국내 기기에도 같은 펌웨어를 쓰는 것으로 보인다. 교보 sam 7 mono(`updatemodel=Kyobo-G7-RK3576`)가 실제로 그렇다(아래 절).

### 펌웨어 얻기

- 업데이트 서버: `POST http://api.haoqingtech.com/update/v1` (https 는 연결 안 됨), 폼 값(`x-www-form-urlencoded`) `model`·`packageDate`·`packageCode`·`serialNumber`(13자)·`language`. 원래 요청(MobileRead)에는 일련번호가 들어가지만 빼고 보내도 답이 왔다(2026-10 직접 확인).
  - `model` = 기기의 `getprop persist.haoqing.updatemodel` (예: `MEEBOOK-M6C-RK3566`, `MEEBOOK-M8-RK3576`)
  - `packageCode` = `getprop ro.build.display.id` (예: `MEEBOOK-V2.0.0-2025042911`), `packageDate` 는 그 뒤 10자리
  - 답의 `retobj.packageUrl` 이 다운로드 주소(`http://update.haoqingtech.com/...`), `packageMd5`·`size` 도 온다. 없으면 `"retobj":""`.
- 기종마다 다르다: M6C·M8 은 옛 버전 번호를 넣으면 **전체 OTA** 를 준다. M6 은 정확한 현재 버전에서 올리는 **증분**만 줘서 버전을 맞혀야 한다.
- E6 는 받지 못했다. 실기기 값은 `updatemodel=MEEBOOK-E6-RK3566`, `Build.DISPLAY=V2.0.0-2026071718`(M6C 와 달리 `MEEBOOK-` 접두가 없다)인데, `packageCode` 를 `V2.0.0-2026071718`·`MEEBOOK-V2.0.0-2026071718`·옛 날짜(`V2.0.0-2025010100`)로 보내도 모두 `"retobj":""` 였다(2026-10-07). 지금 버전이 최신이거나 일련번호가 필요한 듯하다.
- 출처: 4pda Meebook M6 토론방 #165, MobileRead Meebook M8 펌웨어 글.

### 펌웨어 풀기

```bash
brew install jadx sevenzip
python3 tools/ota_payload.py update.zip img system system_ext product vendor odm
7zz x -snl -ofs/system img/system.img          # ext4. 파티션마다
jadx --no-res -d src/SystemUI fs/system_ext/priv-app/SystemUI/SystemUI.apk
```

- M6C 의 `update.zip` 은 A/B 전체 OTA(`payload.bin`)이고 파티션은 모두 ext4 다.
- 시스템 앱 리소스(타일 목록·문구·아이콘)는 `--no-res` 를 빼고 `--no-src` 로 푼다.

### M6C 에서 확인한 것 (V2.0.0, 2025-04)

- **속성**: `ro.haoqing.brand=haoqing-meebook`, `persist.haoqing.updatemodel=MEEBOOK-M6C-RK3566`, `ro.sf.lcd_density=320`, `ro.product.cpu.abilist=arm64-v8a,armeabi-v7a,armeabi`, `persist.sys.eink.color=1`(컬러 화면), `persist.haoqing.pagekeys=0`(버튼 없음).
- **시스템 앱**: 런처 `com.haoqing.app.hqreader`(`Haoqing-Launcher`, HOME), 설정 `com.haoqing.settings`, 떠 있는 버튼 `Haoqing-FloatingButton`, 앱 장터·파일 관리자·와이파이 전송 등. AOSP `Launcher3QuickStepGo` 도 들어 있다.
- **빠른 설정**: 안드로이드 기본 패널(`QSPanel`)을 고친 것.
  - 기본 타일 `refresh,wifi,bt,sound,autorotation,batteryusage,screenrecord,location,screenshot,cast,battery,airplane`, 한 쪽 6칸×2줄, 전자잉크용 아이콘(`by_ic_qs_*`).
  - 상단 바 왼쪽에 홈·뒤로·새로고침·조명(·A2) 버튼, 오른쪽에 최근 앱 버튼(`haoqing_status_bar_left/right_btn_layout`).
  - 조명은 패널 대신 상단 바 버튼으로 따로 뜨는 창: 꺼짐/차가운/따뜻한/은은한/수동 + 슬라이더 2개(`haoqing_brightness`).
  - 새로고침 설정 창: 갱신 모드(Regal·Normal·Fast·Top speed/A2), 전역 256단계 회색, 대비 슬라이더(`pop_view_refresh_mode`).
- **SystemUI 가 받는 방송** (`com.android.systemui.haoqing.Haoqing`, 권한 없이 동적 등록):
  - `com.haoqing.action.QUICK_SETTINGS` → `instantExpandNotificationsPanel()`
  - `com.haoqing.action.FULL_REFRESH` → 화면 전체 잔상 제거
  - `TOGGLE_RECENTS`, `SHOW_GLOBAL_ACTIONS_MENU`, `SETUP_REFRESH_MODE`(extra `refresh_mode`, `global`), `toggleBrightness`, `closepanel` 등
  - `PackageManagerService.isProtectedBroadcast` 가 `com.haoqing` 로 시작하는 방송을 보호 대상에서 뺀다 → 일반 앱도 보낼 수 있다 **(미확인: 실제 기기에서 일반 앱으로 보내 보기)**.
- **화면 대비**: SystemUI `ContrastController` 가 `Settings.System hq_contrast`(기본 34)를 `persist.hq.hwc.contrast_level` 과 `persist.vendor.hwc.contrast_key = 1.5 − c×0.018`(앞 4글자)로 넘긴다. 하드웨어 합성 단계에서 화면 전체에 적용된다. 크레마와 키 이름은 같고 식은 다르다.
- **앱별 전자잉크 설정** (`android.util.haoqing.PaintConf`, `/data/haoqing/paint.conf`): 앱마다 DPI, 전체 화면, 대비, 밝게 바꾸기(bleach), 갱신 모드, 페이지 버튼을 음량 키로 바꾸기. `Canvas` 의 그림 감마 보정(`ImageProcesser.gammaCorrection`)은 앱별 설정이 있을 때만 돈다.
- **페이지 키**: `KeyEvent.KEYCODE_HAOQING_PAGE_UP = 291`, `KEYCODE_HAOQING_PAGE_DOWN = 292`(키 배치 파일에서 스캔 코드 603·604).
- **앱을 강제 종료하는 경로** (`forceStopPackage`. 크레마에서는 이러면 접근성 서비스가 꺼진다):
  - 떠 있는 버튼의 메모리 정리: 최근 앱 목록의 시스템 앱 아닌 앱(맨 앞 하나 빼고). Pebble Desk 화면은 모두 `excludeFromRecents` 라 대상이 될 가능성이 낮다.
  - 앱별 전자잉크 설정을 바꿀 때(`com.haoqing.action.clearapp` → `Haoqing-Settings` 의 `HaoqingReceiver`), 시스템 글꼴을 바꿀 때.
  - 접근성 서비스 목록을 지우는 코드는 못 찾았다.
- **기본 홈**: 표준 `RoleManager`(`android.app.role.HOME`)·권한 관리 앱 경로 그대로. 메이북 런처로 되돌리는 코드는 못 찾았다.

### E6 (2026-07 출시)

- 리뷰([Good e-Reader 영상](https://www.youtube.com/watch?v=hsDaqbdV1-I) 2:10)의 빠른 설정은 **M6C 와 타일·순서·배치까지 같다**: Refresh Settings · Wi-Fi · Bluetooth · Sound · Rotation Manually · 배터리% / Screen Record · Location · Screenshot · Screen Cast · Battery Saver · Airplane mode. 상단 바 버튼도 같다.
- 하드웨어: 페이지 버튼 2개, 화면 아래 정전식 버튼 1개(helpix), 1.8GHz 4코어·3GB·32GB·2200mAh, 180g, $189.99~199. 칩은 helpix 도 RK3566 으로 추정만 한다. M6(2023)와 거의 같고(helpix: "один к одному") 버튼·패널이 다르다.
- 빠른 시작 안내서(한 장을 12면으로 접은 것, 중국어·영어)는 A2 빠른 갱신 켜기("[A2] super speed refreshing"), 상단 바 【작업 관리】("[Task manage] at top bar")로 작업 정리, 【앱】 화면 왼쪽 【눈송이】("[Snow] icon", 백그라운드 앱 관리)만 언급한다. 빠른 설정 설명은 없다.
- **(미확인)** 페이지 버튼·정전식 버튼의 키 코드, 대비 설정 키, 눈송이가 접근성 서비스에 주는 영향.

### E6 에서 확인한 것 (V2.0.0-2026071718, 2026-10-07 사용자 사진 5장)

Pebble Desk 1.2.0 을 기본 홈으로 쓰는 E6 의 홈·설정·기기 정보 화면과, 기기 정보 위로 빠른 설정이 열린 사진.

- **기기**: `Build.MODEL=E6`, `Build.DEVICE=E6`, `MANUFACTURER=Haoqing`, `BRAND=MEEBOOK`, 안드로이드 11 (API 30), `Build.DISPLAY=V2.0.0-2026071718`. 화면 1072×1448 · 320dpi · 536×724dp. `Device.kind` 는 `메이북`.
- **속성**: `ro.haoqing.brand=haoqing-meebook`, `persist.haoqing.updatemodel=MEEBOOK-E6-RK3566`, `persist.haoqing.pagekeys=41`(M6C 는 0. 버튼 구성 값인 듯), `ro.sf.lcd_density=320`.
- **설정 값**(`Settings.System`): `isLightOn=false`, `light_mode=0`, `screen_brightness=0`, `warm_light` 없음, `haoqing_warm_light=0`, **`hq_contrast` 없음**. 대비는 M6C 와 다른 곳에 두거나 한 번도 바꾸지 않은 것일 수 있다.
  - 홈 사진(11:31)의 상태 줄에는 조명 아이콘 ☀ 이 있고, 그 앞뒤 기기 정보 사진(11:25·11:34)에는 `isLightOn=false` 다. 그 사이에 조명을 켰다 껐는지, 아니면 E6 가 `isLightOn` 을 쓰지 않는지 **확인 필요**(조명을 끈 채 홈에 ☀ 가 보이면 다른 키를 봐야 한다).
- **상단 바**: 홈이 아닌 화면에는 메이북 시스템 상단 바가 보인다. 왼쪽 홈·뒤로·새로고침·조명(꺼짐이면 사선 표시), 오른쪽 Ⓕ 표시·배터리·시각·최근 앱 버튼. 높이는 사진으로 어림해 약 54dp(페블의 크레마 상단 바는 약 43dp). 홈은 `hideStatusBar()` 로 숨긴 자체 상단이 그대로 나온다.
  - 그래서 하위 화면의 세로는 724dp 가 아니라 **약 670dp** 다. 1.2.0 에서는 설정 화면의 마지막 줄 `기기 정보` 가 `Pebble Desk 1.2.0` 아래로 잘렸고(제목 80 + 소제목·줄 11개 + 바닥글 44 ≈ 708dp), 홈도 책이 3권이면 함께 읽는 책 줄 아래가 잘렸다(큰 표지 최소 136dp). → 밀도를 572dp 폭으로 맞춰 고쳤다(아래 표).
- **글꼴**: 시스템 기본 글꼴(`Typeface.DEFAULT`)이 페블의 고딕과 달리 **명조 계열**이다. 같은 sp 에서 글자가 넓고 줄 높이가 커서(함께 읽는 책 제목 16sp 두 줄이 약 57dp), 홈 `함께 읽는 책` 칸(글자 폭 82dp. 페블은 94dp)에 한 줄 5자만 들어가 `수학이 가 / 르쳐 준 …` 처럼 끊긴다.
- **권한**: `자동 추가 꺼짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`. 사용 기록 권한을 주면 홈 하단에 `오늘 28분 · 2일째` 가 나오고, 밀리의서재 E ink(`kr.co.millie.eink`)의 `EPubViewActivity` 가 읽는 화면으로 잡혔다.
- **최근에 뜬 다른 화면**: `kr.co.millie.eink / EPubViewActivity · BookshelfActivity · SplashActivity`, `com.haoqing.wallpaper / DreamActivity`(슬립 화면이 Activity 로 뜬다 → 사용 기록에 남는다), `com.android.systemui / RecentsActivity`(상단 바의 최근 앱 버튼). 빠른 설정 패널은 Activity 가 아니라 남지 않았다.
- **버튼**: `4 KEYCODE_BACK · scan 0` 만 기록됐다(스캔 코드 0 = 상단 바의 뒤로 버튼으로 보인다). 페이지 버튼·정전식 버튼은 사진에 없다. 다음에 그 버튼을 누른 기기 정보 사진을 받는다.
- **빠른 설정**: 기기 정보 화면 위로 열렸다. 먼저 아이콘 한 줄(새로고침·Wi-Fi·BT·소리·회전·배터리)과 손잡이만 보이는 접힌 상태, 당기면 두 줄 타일에 한국어 이름 `새로고침 모드 · Wi-Fi · 블루투스 · 소리 · 수동 회전 · 83% / 화면 녹화 시작 · 위치 · 캡처 · 화면 전송(…지 않음) · 절전 모드 · 비행기 모드` 와 아래 연필(편집)·톱니(설정). M6C 펌웨어의 타일 목록과 같다. `메이북 ›`(`com.haoqing.action.QUICK_SETTINGS`)과 `안드로이드 ›`(`expandSettingsPanel`) 둘 다 이 패널을 연다(사용자 확인). 홈 상단 줄 누르기(`Device.openQuickSettings`)도 그래서 통한다.

## Viwoods AiPaper Mini

선전 Viwoods Technology & Design(深圳, FCC 신청 회사 코드 `2BKO6`)의 8.2인치 필기용 전자잉크 태블릿, 모델 번호 **SE05**(2024-08 출시, 펜·케이스 포함 $429~549). 같은 회사의 10.65인치 AiPaper(조명 없음), 7.8인치 AiPaper Reader 와 펌웨어 계열이 다르다(아래).

### 사양 (리뷰·판매 페이지)

- 화면 8.2" E Ink Carta 1000, 1440×1920(세로 모양 3:4), 제조사 297ppi(대각선으로 계산하면 약 293). 유리 덮개, 조명 20단계(색온도 조절 없음).
- MediaTek MT8788(2.0GHz 8코어, Amazon 상품 설명) · 4GB · 128GB(SD 없음) · 2450mAh · 191×138×5.2mm · 230~234g.
- 안드로이드 13. Google Play 는 기본으로 없고 설정에서 Google 서비스를 켜 깔 수 있다. 자체 앱 장터가 있다.
- 버튼: 화면 아래 정전식 **뒤로·홈·AI** 3개, 오른쪽 위 전원 겸 지문. 페이지 버튼 없음. 가로 화면을 지원하지 않는다(Parka Blogs).
- 펌웨어: 3.x(안드로이드 13), 두 달쯤마다 업데이트. 3.12(2026-01)에서 새로고침 모드에 `Quick Refresh` 추가, **다른 앱 표시 설정(`App Display`: 대비, 연한 색 지우기)** 추가(eWritable).
- 새로고침 모드: Best · Fast · Ultra-fast 세 가지(+3.12 의 Quick Refresh), 세부 조정은 없다.
- **제어 센터** (Viwoods 공식 블로그 설명 그림, eWritable 펌웨어 3.9 글):
  - 화면 **오른쪽 위에서 손가락으로** 끌어내리면 열린다. 같은 자리를 **펜**으로 끌어내리면 Picking 메뉴(자르기 캡처·화면에 쓰기·떠 있는 메모), **왼쪽 위**에서 끌어내리면 손으로 하는 화면 새로고침. 네 손가락으로 끌어내리면 캡처.
  - 화면 전체가 아니라 **오른쪽 절반쯤의 세로 패널**이 위에서 내려온다(왼쪽에 굵은 테두리, 뒤 화면은 그대로 보인다).
  - 위에서부터: 배터리% · 날짜·요일·시각 · 톱니(설정) / 큰 버튼 `WLAN` · `Bluetooth`(짧게 = 켜고 끄기, 길게 = 그 설정 화면) / 원형 버튼 `Refresh` · `WLAN Cast` · `Contrast` · `Button Lock`(아래 정전식 버튼 끄기) · `WLAN Transfer` · `Memo` (+ 캡처·비행기 모드·Picking 켜기) / `Refresh mode`: `Auto Ghosting Removal` 스위치, `Best Display` · `Fast Mode` · `Ultra-fast Mode` / **미니만** `Brightness` 0~20 막대(헤드폰을 꽂으면 음량도).
  - 안드로이드 알림창은 없고, 기본 런처에서는 위에서 끌어내려도 알림이 아니라 이 패널이나 새로고침이 된다(inkBell 설명).
  - Pebble Desk 와의 관계: 기기 정보의 `안드로이드 ›`(`expandSettingsPanel`)로 이 제어 센터가 열린다(사용자 확인). 크레마·메이북 방송으로는 안 열려 홈 상단 줄 누르기는 아무 일도 하지 않지만, 원래 기기처럼 끌어내리기로 열면 되므로 따로 맞추지 않는다. Pebble Desk 홈 위에서 끌어내리기가 한 번에 여는지는 **(미확인)**.
- 다른 런처: Nova Launcher·inkOS 를 안드로이드 설정 › 앱 › 기본 앱 › 홈 으로 바꿔 쓰는 사용자 글이 있다(svartling.net, inkOS).

### 시스템 (다른 개발자들이 미니 실기기에서 본 것)

출처: `jdkruzr/ViwoodsAppDev`(미니에서 빠른 펜을 쓰려고 펌웨어 앱을 디컴파일한 기록, `VIWOODS_APP_DEV.md`), `FoxesRCool1/Margin-Eink-Launcher`(미니용 런처, `docs/decisions/0014`·`0004`), `TrevTV` 블로그(10.65" AiPaper 의 앱·통신 분석). 둘 다 라이선스 없는 참고 자료다.

- **ODM 은 Wisky**: `ro.build.user=wisky`, `ro.build.host=wisky3`, Viwoods 시스템 앱은 모두 `com.wisky.*`(메모 `com.wisky.notewriter`, AI `com.wisky.wiskyai`, OCR `com.wisky.ocr` 등). 커널 4.19, MediaTek.
- **속성**: `ro.eink.model=SE05`, `ro.eink.type=SoftSolution`, `ro.wisky.has_backlight=true`, `persist.eink.mode_default`(기본 갱신 모드, GL16=3). → `Device.kind` 를 나눈다면 `Build.MANUFACTURER == "Viwoods"` 나 `ro.wisky.*` 가 쓸 만하다.
- **시스템 앱**: 런처 `WiskyLauncher`(`/product/priv-app/`, 패키지 이름은 **(미확인)**. AiPaper Reader 는 `com.viwoods.launcher`), 설정 `setting`(Viwoods 설정. 안드로이드 기본 설정 `com.android.settings` 도 남아 있어 직접 열면 뜬다), 메모 `WiNote`·`Wmemo`, 손글씨 키보드 `WSmartIME`, AI `wiskyAi`.
- **전자잉크 API**: 숨은 `android.os.enote.ENoteSetting`(바인더 서비스 `ENoteSetting`). 갱신 모드 `setPictureMode`(3 GL16 · 4 FAST · 17 GC 등), 감마·앱별 연한 색 지우기, T1000 빠른 펜(AutoDraw). 일반 앱도 리플렉션·`service call` 로 부를 수 있다고 한다. Pebble Desk 는 쓰지 않는다.
- **접근성**: Viwoods 의 `FocusMonitorService` 가 접근성으로 원노트·킵 등의 화면을 보고 빠른 펜을 켠다(`persist.sys.focusmonitor.config=1`). 접근성 서비스 자체는 일반 앱도 쓸 수 있다. Pebble Desk 의 자동 추가도 켜졌다(기기 정보 사진).
- **기본 홈**: 안드로이드 설정 › 앱 › 기본 앱 › 홈 으로 바꾼다. Viwoods 설정 화면에는 이 메뉴가 없어 안드로이드 설정을 따로 열어야 한다(Margin 은 `RoleManager.ROLE_HOME` 요청 → `ACTION_HOME_SETTINGS` 순으로 연다).
- **상단 바와 제어 센터**: Margin 은 모든 화면에서 상단 바를 숨기는데, 위에서 끌어내리면 바가 잠깐 나오고 "조명·Wi-Fi 패널이 그 바에 달려 있다"고 적었다. 즉 제어 센터는 SystemUI 상단 바 쪽 동작으로 보인다 **(추정)**. Pebble Desk 홈도 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` 로 상단 바를 숨기므로 **첫 끌어내리기는 바만 꺼내고, 한 번 더 끌어내려야 패널이 열릴 수 있다** **(미확인)**.
- **adb**: 미니도 `adb shell`·`install`·`pull`·`logcat` 이 막혀 있다(`error: not support command`). `adb forward` 와 앱 안의 `service call` 은 된다. AiPaper Reader 에서는 Viwoods 디버그 도구가 쓰는 `persist.wisky.hold_enable_adb` 속성으로 잠금을 푸는 방법이 공개됐다(yowmamasita 블로그 "Bypassing ADB Restrictions"). 키 앞부분이 10.65" AiPaper 모델 번호(`SE03`)라 미니에도 통할 수 있다 **(미확인)**.
- **AI 버튼**(AiPaper Reader, `equwal/assistkey`): 입력 장치 `AI KEY`, 키 `KEY_F1`. 펌웨어가 `Settings.System CustomAiKey` 에 정한 화면을 띄우고 앱에는 잘 안 온다. 미니도 같은지 **(미확인)**.

### 펌웨어 얻기

- **공개된 미니 펌웨어 파일은 못 찾았다**(2026-10-07, XDA·4PDA·MobileRead·GitHub 검색).
- 업데이트 확인은 `https://api.viwoods.com/api/v1/system/version`, 릴리스 노트는 `…/v1/system/details`(TrevTV 블로그). 요청마다 데이터와 비밀 값으로 만든 서명이 붙어, 서명 없이 보내면 `{"code":10032,"status":"error"}` 만 온다(직접 확인). 메이북처럼 모델·버전만 넣어 받을 수 없다.
- 남은 길: (1) 위 adb 잠금 풀기가 미니에서 되면 `framework.jar`·`services.jar`·SystemUI·`WiskyLauncher` 를 `adb pull`, (2) 펌웨어 앱(설정·런처)에서 서명 방식을 찾아 업데이트 서버에 묻기, (3) 기기에서 업데이트를 받을 때 내려받는 주소를 잡기. 부트로더를 풀거나 `mtkclient` 로 통째로 읽는 방법은 기기를 지울 수 있어 하지 않는다.

### 같은 회사 AiPaper Reader 에서 알려진 것 (다른 펌웨어, 미니에 같은지는 **(미확인)**)

AiPaper Reader(7.8", MediaTek Helio G99 MT8781, 펌웨어 1.x · 안드로이드 16)를 루팅해 고친 사람들의 기록(GitHub `magcrider/Viwoods-Notification-Unlocker`, MobileRead).

- **알림 막기**: `services.jar` 의 `NotificationManagerService.enqueueNotificationInternal` 에 허용 목록(`ALLOWED_PKGS`)이 박혀 있어 목록 밖 앱 알림을 버린다(로그 `eink project,Blocked notification from package:`). Pebble Desk 는 알림을 쓰지 않아 상관없다.
- **백그라운드 앱 정리**: `com.viwoods.refresh.service` 가 백그라운드 프로세스를 세게 죽인다. 접근성 서비스(읽는 책 자동 추가)가 꺼지거나 멈출 수 있다 **(미확인)**. 대응은 배터리 최적화 제외(`cmd deviceidle whitelist +패키지`).
- **adb 제한**: 1.3.4 에서 adb 와 버튼 바꾸기(Keymapper)를 막았다가 1.3.8 에서 Viwoods 인증 도구(PC 프로그램)로 기기를 매번 승인해야 쓰게 했다. 접근성 서비스도 중간 베타에서 막혔다가 1.3.8 에서 돌아왔다.
- 부트로더 잠김, AVB 강제. 펌웨어 백업·쓰기는 `mtkclient`(BROM 모드).
- **Pebble Desk 사용자 보고**: AiPaper Reader C(컬러 판으로 보인다 **(추정)**)에서 읽는 책 자동 추가가 된다(2026-10-25 Reddit 댓글, KOReader 책 두 권이 홈에 나옴). 펌웨어 판은 모른다. 접근성이 막히는 펌웨어가 있어 댓글에서 `앱 정보 › 오른쪽 위 메뉴 › 제한된 설정 허용` 을 먼저 안내했다.

### 실기기 사진에서 본 것 (2026-10-07, 홈 화면 1장)

Pebble Desk(1.2.x)를 기본 홈으로 쓰는 홈 화면. 책 1권(밀리의서재 E ink `독서의 기술`), 자주 쓰는 앱 3개(밀리의서재 E ink · 윌라 · Kindle).

- **기본 홈**: 됨. 홈을 누른 뒤 Pebble Desk 가 뜬 것으로 보인다. 시스템 상단 바는 `hideStatusBar()` 로 숨겨지고 자체 상단(시계·`10월 7일 수요일 · 83% · Wi-Fi · BT`)이 나온다. 크레마 로고 자리는 비어 있다(크레마 런처 없음, 예상대로).
- **내비게이션 바 없음**: 앱이 화면 맨 아래까지 쓰고 하단 줄(`모든 앱 › · 오늘 2분 · 4일째 · 톱니`)이 다 보인다. 크레마처럼 아래가 가려지는 일은 없어 보인다.
- **밀도 맞춤**: 기기는 320dpi(720×960dp)인데 짧은 변 1440px → 앱 밀도 403dpi(1dp ≈ 2.52px), 572×762dp 로 페블(572×777dp)과 거의 같은 배치가 그대로 들어간다. 다만 화면이 커서 1dp 가 페블의 약 1.37배(0.21mm, 페블 0.16mm) 크기로 보인다. 글자·줄이 큼직하다.
- **글꼴**: 시스템 기본 글꼴이 고딕 계열로, E6(명조 계열)처럼 줄이 넘치는 일은 없다.
- **오늘 읽은 시간**: `오늘 2분 · 4일째` → 사용 기록 권한을 줬고, 밀리의서재 E ink 의 읽는 화면이 잡힌다.
- **상태 줄에 조명 아이콘 없음**: `Device.kind` 가 `OTHER` 이면 조명 키를 모른다(`lightKeys` 비어 있음). 조명을 켜 둔 상태인지는 사진으로 알 수 없다.
- 그림(표지)은 회색이 고르게 나온다. 대비를 따로 보정하지 않는다.

### 기기 정보 사진 (2026-10-07 21:38, Pebble Desk 1.2.1)

- **기기**: `Build.MODEL=Viwoods AiPaper Mini`, `Build.DEVICE=Viwoods`, `MANUFACTURER=Viwoods`, `BRAND=Viwoods AiPaper Mini`, 안드로이드 13 (API 33), `Build.DISPLAY=3.15.15`(펌웨어 3.15). 화면 1440×1920 · 320dpi(`ro.sf.lcd_density=320`) · 720×960dp, 앱 기준 403dpi · 572×762dp. `기기 종류` 는 `기타`.
- **속성·설정 값**: 1.2.1 의 기기 정보는 크레마·메이북 키만 보여서 Viwoods 고유 값은 나오지 않았다. 메이북 속성(`ro.haoqing.*`)은 모두 없고, `Settings.System` 은 `screen_brightness=1` 만 있다(`isLightOn`·`light_mode`·`warm_light`·`hq_contrast` 없음).
- **권한**: `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`. 접근성 서비스를 켤 수 있다(Viwoods 가 막지 않는다). 홈 사진의 밀리의서재 책은 자동 추가가 아니라 **직접 넣은 것**이었다(아래 2026-10-10 댓글).
- **최근에 뜬 다른 화면**: `kr.co.millie.eink / BookshelfActivity · EPubViewActivity`.
- **버튼**: 기록 없음(아직 누르지 않음).
- **빠른 설정 시험**: `안드로이드 ›`(`expandSettingsPanel`)를 누르면 빠른 설정(제어 센터)이 뜬다(사용자 확인). 크레마·메이북 방송으로는 열리지 않는다. 홈 상단 줄 누르기는 방송만 보내 열리지 않지만, 원래 기기처럼 끌어내리기로 열면 되므로 따로 맞추지 않는다.
- **상단 바**: 하위 화면에는 Viwoods 시스템 상단 바(오른쪽에 시각 · Wi-Fi · 배터리%)가 얇게 보인다(사진으로 어림해 앱 기준 약 21dp). 기기 정보 화면은 맨 아래 빠른 설정 시험 줄까지 다 들어간다.

### 사용자가 알려 준 문제 (2026-10-10 18:25 댓글, 1.4.0)

- **접근성을 켰는데 읽는 책 자동 추가가 안 된다**: 안내대로 접근성을 켰고(기기 정보도 `자동 추가 켜짐`), 밀리의서재·밀리의서재 E ink·리디에서 책을 열어도 들어오지 않는다. 재부팅·재설치해도 같다. 전에 "됐다"고 한 것은 책을 직접 넣은 것이었다.
- 이 기기는 **Play 인증 기기가 아니고** GSF ID 를 등록해 Google 서비스를 켰다고 한다. 접근성 서비스는 Play 인증과 상관이 없어 이것이 원인이라고 보기는 어렵다 **(추정)**.
- Leaf2(켤 수가 없다)와 달리 **켜졌는데 동작하지 않는** 경우다. 생각해 볼 원인 **(미확인)**:
  1. 시스템이 서비스에 연결하지 않거나 곧 끊는다(백그라운드 정리 `com.viwoods.refresh.service` 같은 것, 위 AiPaper Reader 절).
  2. 연결은 되는데 이벤트가 오지 않는다(Viwoods 의 `FocusMonitorService` 처럼 접근성을 쓰는 시스템 서비스와 겹침, 또는 펌웨어가 다른 앱 접근성 이벤트를 거름).
  3. 이벤트는 오는데 이 기기의 이북 앱 화면을 못 읽는다(같은 앱이 페블에서는 되니 가능성은 낮다).
- 1.4.0 기기 정보의 `자동 추가 서비스 기록`(연결·끊김 때, 앱 종료 까닭, 배터리 최적화·대기 그룹·백그라운드 제한)으로 1 과 2·3 을 가를 수 있다. 마지막 댓글에 아직 답하지 않았다.

### 다음에 받을 것

1. 기기 정보 사진은 받았다(위). 기기 정보가 기기 고유 값을 찾아 보이게 고친 뒤, 조명을 켠 채·끈 채 한 장씩 더.
2. Pebble Desk 홈에서 오른쪽 위 끌어내리기가 제어 센터를 여는지(한 번에 열리는지, 상단 바가 먼저 나오고 두 번째에 열리는지). 빠른 설정 시험은 `안드로이드 ›` 로 열림을 확인했다.
3. 정전식 AI 버튼(·뒤로·홈)을 누른 뒤 기기 정보의 키 번호·스캔 코드.
4. 제어 센터를 열었다 돌아온 뒤 `최근에 뜬 다른 화면`(따로 된 Activity 인지).
5. **자동 추가 기록 사진**(1.4.1, 기기 정보 › `기록 보기 ›`): `기록 지우기` → 밀리 E ink 에서 책 열기 → 돌아와 찍기. 요약의 `첫 알림`·`받은 알림` 이 없으면 시스템이 알림을 보내지 않는 것(Viwoods 접근성 잠금 의심), 알림은 오는데 `화면 못 읽음`·`창 … (화면 아님)` 뿐이면 화면을 못 읽는 것이다. adb 가 되는지.
   - Viwoods 는 2026 초 AiPaper Reader 베타에서 adb·접근성을 기기마다 잠갔다(설정에는 켜져 있는데 Keymapper 가 동작하지 않음, 고객지원에 요청하면 그 기기를 풀어 준다. MobileRead `t=372886`). 미니도 같다면 Viwoods 고객지원에 시리얼 번호로 잠금 해제를 요청하게 한다 **(추정)**.

## iReader Ocean 5 Pro

중국 掌阅(Zhangyue, 브랜드 iReader)의 7인치 전자잉크 리더. 국내 사용자는 `오션`, `아이리더 오션5` 로 부른다(해외 구매·오픈마켓). 2025-10 에 Ocean 5 · 5 Pro · 5 장기 배터리판 · 5C(컬러) 가 나왔다. 앞 세대 Ocean 4 Turbo 도 같은 모양이다. 모두 Kindle Oasis 처럼 한쪽 손잡이가 두껍고 거기에 페이지 버튼이 있다. **Musnap Ocean / Ocean C**(2025-12, 검은색, 영문판)는 같은 회사가 만든 같은 기기다(설명서의 만든 곳 Shenzhen Zhangyue Technology, SmartOS).

사용자가 보낸 기기 정보로 **Ocean 5 Pro**(흰색, 오른쪽 손잡이에 버튼 두 개)를 확인했다(2026-10-10): `Build.MODEL=Ocean 5 Pro`, `Build.DEVICE=SM07A-CKDA`(모델 번호), `MANUFACTURER`·`BRAND` `iReader`, 안드로이드 14(API 34, 빌드 `UP1A.231005.007`), 화면 1264×1680 · 320dpi(632×840dp) → 앱 기준 354dpi · 571×759dp, `기기 종류` 기타. Musnap Ocean(`SM07A-AKDW`)과 모델 번호 앞부분이 같다. **Ocean 5C**(컬러판, `SM07A-CKDC`)는 2026-10-31 기기 정보 사진을 받았다(아래 [Tango·Ocean 5C 기기 정보 사진](#tangoocean-5c-기기-정보-사진-2026-10-31-pebble-desk-171)). 다른 Ocean 모델(5 · 4 Turbo)은 기기 정보를 보지 못했다.

### 사양 (IT之家·Musnap 설명서·리뷰)

- 화면 7" E Ink Carta 1300, 1264×1680, 300ppi(5C 는 Kaleido 3 컬러, 흑백 300 · 컬러 150ppi). 조명 30단계, 색온도 조절.
- Ocean 5 Pro: 8코어 · 4GB · 64GB · 2400mAh · 185g. Ocean 5 표준: 4코어 · 4GB · 64GB · 2000mAh · 179g. 장기 배터리판: 1GB · 64GB. Musnap Ocean: 8코어 2.2GHz · 4GB · 64GB · 3000mAh. SD 카드 없음.
- OS: 掌阅 **SmartOS**. Musnap Ocean 은 `SmartOS 3.1.0`, 시스템 버전 `3.1.10.183.0`, 소프트웨어 `3.1.2702.183(127021)`(설명서 그림), 안드로이드 14 · 보안 패치 2024-07(jpwhiteside 리뷰). 다른 앱을 깔 수 있다. Google Play 는 설정 › `Google Settings` 에서 Google 서비스를 켜면 나타난다(설명서·리뷰). 중국판도 같은지는 **(미확인)**.
- 설정(`Mine` 탭): WLAN · Bluetooth · Google Settings · System Settings(Display: 홈 보기·테마·화면 보호기·배경·조명·잠자기 / System: 음량·날짜·언어·입력기·글꼴·화면 회전 / **Auxiliary functions: Global Gestures**·Smart Assistant·펜 버튼·덮개 깨우기 / Privacy / System & Updates) · Read · Notes · Schedule · Apps · Help · About.
- 앱별 표시 최적화: 대비·선명도·전체 새로고침 간격·다크 모드. 새로고침 모드는 The Clearest · Quick · Quick Brush · Intelligent 넷(Good e-Reader). Musnap Ocean 리뷰(jpwhiteside)는 아래 오른쪽에서 끌어올려 열고, 검은색 강조·깜빡임 줄이기(약·강)·몇 쪽마다 새로고침·새로고침 모드(선명·빠름)가 있다며 "BOOX 처럼 앱별 DPI·대비 설정은 없다" 고 한다. 앱별 DPI 를 바꾸는 곳은 찾지 못했다. 예전 iReader 앱 장터가 받은 앱 폴더에 `dpilist`·`idlist` 파일을 두는데(知乎 설치 안내) 무엇인지는 **(미확인)**.
- **Musnap Ocean C**(Kaleido 3 컬러판): iReader 가 만든 영문판(Good e-Reader). 7" 1264×1680, 흑백 300 · 컬러 150ppi, 안드로이드 14, 8코어 2.2GHz · 4GB · 64GB · 3000mAh, Google Play, 펜 따로(Notebookcheck·the-ebook-reader). Ocean 5C 와 같은 기기라는 댓글이 있다 **(미확인)**.

### 빠른 설정(제어 패널) 여는 법

설명서(27쪽)에는 손동작 설명이 없다. 리뷰·사용기에서 본 것:

- **화면 위에서 끌어내리면 제어 패널**이 열린다(Ocean 4 Turbo 는 밝기·색온도·화면 모드·캡처·회전·BT·Wi-Fi, 知乎). Musnap Ocean 리뷰(jpwhiteside)에는 `swipe down from the top` 로, Ocean C 사용기(클리앙)에는 "상단 중간에서 우측 방향으로 쓸어내리면 오닉스와 비슷한 설정 메뉴" 로 나온다. Ocean 4 Turbo 사용기(知乎)에는 **오른쪽 위에서 끌어내린다**로 되어 있다. 펌웨어마다 자리가 조금 다른 듯하다.
- 오른쪽 위 **모서리**에서 끌어내리면 캡처·노트·작업 전환 패널(Musnap 리뷰). 아래 가장자리에서 끌어올리면 왼쪽 = 작업 관리자, 가운데 = 홈, 오른쪽 = 표시 최적화.
- 손동작은 7방향 + 오른쪽 위 대각선, 모두 8방향을 설정 › 보조 기능 › 전역 손동작(Global Gestures)에서 바꿀 수 있다(클리앙). SmartOS 2.1·2.2 에서 들어온 기능이다.
- 시스템 상단 바(시각 · BT · 조명 · 배터리 · `···`)가 모든 화면 위에 있다(설명서 그림). 그래서 제어 패널은 SystemUI 상단 바의 끌어내리기 동작으로 보인다 **(추정)**. 그렇다면 `expandSettingsPanel` 로도 열릴 수 있다 **(미확인)**.
- Pebble Desk 와의 관계: 홈 상단 줄 누르기는 크레마·메이북 방송만 보내므로 아무 일도 하지 않을 것이다 **(추정)**. 홈은 상단 바를 숨기니(`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) 첫 끌어내리기는 바만 꺼내고 두 번째에 패널이 열릴 수 있다 **(미확인)**. Viwoods 처럼 원래 기기의 끌어내리기에 맡길지, `expandSettingsPanel` 이 열리면 상단 줄 누르기에 쓸지는 기기 정보 사진을 받고 정한다.

### 페이지 버튼

- 오른쪽(돌리면 왼쪽) 손잡이에 2개. "페이지를 넘기는 앱이 아닐 때는 **음량**을 바꾼다"(Musnap 리뷰). 그렇다면 키는 `VOLUME_UP`·`VOLUME_DOWN` 이고, 읽는 앱에서만 펌웨어가 넘김으로 바꿔 주는 것이다 **(추정)**.
- Ocean 5 Pro 기기 정보의 `마지막으로 누른 버튼`: `24 KEYCODE_VOLUME_UP`(scan 115). 아래 버튼은 `VOLUME_DOWN` **(추정)**. Pebble Desk 는 1.5.5 부터 기기 버튼으로 쪽을 넘기지 않았고(키 처리를 지움), 이제 설정 › 화면 › 페이지 버튼에서 사용자가 음량 키를 정하면 넘긴다(이슈 #9). 음량 조절을 빼앗지 않게 넘길 목록이 없는 화면과 소리가 나는 동안에는 음량 키를 그대로 둔다. 실기기 확인은 아직.
- 짧게·길게 누르기 동작을 각각 정할 수 있다(Ocean C 사용기: 짧게 = 다음/이전, 길게 = 새로고침/뒤로).

### 시스템 (다른 사람들이 본 것)

- iReader 는 중국판에서 다른 앱 설치·adb 를 막아 왔다. Ocean 2 는 iReader 앱을 죽여 안드로이드 설정으로 들어가 USB 디버깅을 켜는 방법이 공개됐다(GitHub `akhan23wgu/iReader-Zhangyue-USBDebug`, MobileRead). Ocean 4 Turbo 이후(SmartOS 2.2~)는 앱 장터와 APK 설치를 공식으로 허용한다. adb 가 되는지는 **(미확인)**.
- 국내 사용기에서 Musnap Ocean 은 Onyx Go 7 과 거의 같고 "플레이스토어를 선택적으로 켤 수 있다" 고 했다(DC 전자책 갤러리).
- 펌웨어 파일: 찾아봤지만 공개된 곳이 없다(2026-10-31, 기본 리더 앱 이름을 알려고). OTA 는 기기 설정 › 시스템 및 업데이트뿐이고 받는 주소·파일 이름은 알려져 있지 않다. 다른 앱 설치를 뚫던 글(Smart XS Pro 2021 블로그, Ocean 2 의 `akhan23wgu/iReader-Zhangyue-USBDebug`·MobileRead)은 앱 장터 응답을 프록시로 바꾸는 방식이라 펌웨어 주소가 없고, 시스템 10.7(2022)부터 HTTPS 라 막혔다. 그래서 기본 리더(시스템 앱)의 패키지·화면 이름은 기기에서 직접 봐야 한다(기기 정보의 `최근에 뜬 다른 화면`). 다른 기기(inkpalm 5) adb 안내에 `com.zhangyue.read.iReader.eink` 가 보이나 Ocean 계열인지는 **(미확인)**.

### 실기기 사진에서 본 것 (2026-10-10 00:16, Pebble Desk 1.3.0)

- **기기 정보**(사진 1장, 화면 전체가 들어감): 위 표에 넣었다. `모델 Ocean 5 Pro (SM07A-CKDA)` · `제조사 iReader · iReader` · `안드로이드 14 (API 34)` · `빌드 UP1A.231005.007 release-keys` · `화면 1264×1680 · 320dpi · 632×840dp / 앱 기준 354dpi · 571×759dp` · `기기 종류 기타 · Pebble Desk 1.3.0`.
  - 시스템 속성: `persist.eink.dither256.disable=0` · `persist.sys.dither.contrast=102` · `persist.sys.eink.color.filter=0` · `persist.sys.eink.contrast=30` · `persist.sys.eink.gesture.width=2.0` · `persist.sys.eink.regal=1` · `ro.eink.fpga.bridg…`(잘림).
  - 설정 값: `screen_brightness=255` · `EinkLightStep=0` · `KEY_AUTO_SWITCH_THEME=0` · `clock_wallpaper_lock_screen_refresh_interval=600000` · `down_key_long_function=4` · `eink_wallpaper_name=시스템 캘린더` · `gesture_ins…`(잘림). 조명 키는 `EinkLightStep` 이다 **(추정)**(같은 회사 Neo 3 Ultra 에서 켜면 14, 끄면 0, 아래 [iReader Neo 3 Ultra](#ireader-neo-3-ultra)). 상태 줄 조명 표시에 쓰려면 조명을 켠 채·끈 채 사진이 더 필요하다.
  - 권한: `자동 추가 꺼짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`.
  - 하위 화면 위에 SmartOS 시스템 상단 바(왼쪽 시각 `오전 12:16`, 오른쪽 Wi-Fi · `75%` · 배터리)가 얇게 있다. 그래도 기기 정보는 빠른 설정 시험 줄까지 다 들어간다.
- **오늘 읽은 시간**: 켜짐(사용 기록 권한 됨). 최근 화면에 `kr.co.millie.millieshelf/MillieViewerActivity`, `kr.co.kyobobook.KEL/B2BViewerEpubMainActivity` 가 잡혔다. 둘 다 `READER` 정규식에 맞으니 읽은 시간은 쌓인다.
- **읽는 책 자동 추가가 켜지지 않는다**: 접근성 › 다운로드한 앱 › Pebble Desk 가 흐리게 `사용 안함`, 누르면 `제한된 설정 — 보안을 위해 이 설정은 현재 사용할 수 없습니다`. iReader 고유 제한이 아니라 **안드로이드 13+ 의 제한된 설정**이다: 파일(APK)로 직접 깐 앱은 접근성·알림 접근 같은 권한을 켜지 못하게 막는다(앱 장터의 세션 설치는 해당 없음). 같은 화면의 Smart Launcher Gestures 는 흐리지 않다(Play 로 깐 것으로 보인다).
  - 푸는 법: 접근성에서 Pebble Desk 를 한 번 눌러 `제한된 설정` 창을 본 뒤(그래야 `⋮` 에 항목이 생긴다. 에뮬레이터 API 33 에서 확인: 창을 보면 `ACCESS_RESTRICTED_SETTINGS` 가 `deny` → `ignore`), 설정 › 앱 › Pebble Desk › 앱 정보 오른쪽 위 `⋮` › **제한된 설정 허용** → 다시 접근성에서 켠다. SmartOS 앱 정보 화면에 `⋮` 가 있는지는 **(미확인)**.
  - Pebble Desk 1.3.2 는 ⑭ 시작하기 1쪽에 이 순서를 단계로 보이고 `앱 정보 열기 ›` 로 앱 정보를 직접 연다(기기 설정 아이콘을 숨긴 SmartOS 에서도 열릴 것 **(추정)**. 안드로이드 13 설정 앱은 extra `uId` 가 있어야 직접 연 앱 정보에 `⋮` 를 만든다. 14 도 같은지 **(미확인)**). 설정 › 독서 › 읽는 책 자동 추가 창에도 같은 안내와 버튼이 있다.
  - 안 되면 PC 에서 `adb shell appops set com.woody.pebbledesk ACCESS_RESTRICTED_SETTINGS allow`(adb 가 되는 경우) 또는 adb 로 다시 설치(`adb install` 은 제한이 걸리지 않는다).
  - 페블·메이북(안드로이드 11)에는 이 제한이 없다. AiPaper Mini(안드로이드 13)는 같은 제한이 있을 텐데 보고가 없었다 **(미확인)**.

### 기기 정보 사진 (2026-10-14 08:55, Pebble Desk 1.5.2)

같은 기기(`Ocean 5 Pro (SM07A-CKDA)`, 빌드 `UP1A.231005.007`, 화면·앱 기준 dpi 그대로)를 1.5.2 로 올린 뒤 찍은 사진 1장. 화면 전체가 들어간다.

- **권한**: `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`. 제한된 설정을 푼 뒤(10/10) 나흘 뒤에도 켜져 있다.
- **자동 추가 서비스 기록**: `연결 10/14 08:17 · 끊김 - · 끊긴 횟수 0`, `앱 종료 10/14 08:17 PKG_UPDATED · 08:17 OTHER`. 덮어 설치(1.5.2)로 앱이 끝난 뒤 서비스가 바로 다시 붙었다. `배터리 최적화 제외 꺼짐 · 대기 그룹 EXEMPTED · 백그라운드 제한 꺼짐`.
- **시스템 속성**(1.3.0 사진과 다른 값): `persist.sys.eink.color.filter` 0 → **10**, `persist.sys.eink.contrast` 30 → **6**. `persist.eink.dither256.disable=0`·`persist.sys.eink.gesture.width=2.0` 은 같다. 사용자가 무엇을 바꿨는지는 **(미확인)**(`contrast` 는 화면 대비, `color.filter` 는 색 거르기 정도로 보인다 **(추정)**). Tango 도 `color.filter=10`·`contrast=6` 이라 이 값이 SmartOS 기본일 수 있다 **(추정)**.
- **설정 값**: `screen_brightness` 255 → **1**, `EinkLightStep=0`, `down_key_long_function` 4 → **2**(아래 버튼 길게 누르기 동작으로 보인다 **(추정)**), `eink_wallpaper_name=系统默认`(전에는 `시스템 캘린더`. 고를 때의 언어로 저장되는 듯 **(추정)**), 새 키 `ireader_systemui_shutdown_pending=0`·`key_b…`(잘림).
- **최근 화면**: `com.flyersoft.moonreader / ActivityTxt`, `com.flyersoft.moonreaderp / ActivityTxt`(기기 정보는 클래스의 마지막 이름만 보인다. 실제 클래스는 `com.flyersoft.moonreaderp.ActivityTxt` 로, 무료판과 패키지 부분이 다르다), `com.android.settings / SpaActivity`(안드로이드 14 설정의 새 앱 정보·권한 화면).
- **화면 위**: 하위 화면 위에 SmartOS 상단 바 대신 안드로이드식 상태 줄(시각 `8:55`, BT · Wi-Fi · `91%`)이 보이고, 제목 왼쪽 `‹` 가 없다. 1.5.2 의 설정 › 화면 › 기기 상단 바를 켰는지, 펌웨어가 바뀐 것인지 **(미확인)**.

### Musnap Ocean C 사용자 댓글 (2026-10-26, Reddit No_Mixture_2431, 판 미상)

- 킨들 표지 문제(Kindle Unlimited·보낸 문서 표지가 안 나온다는 댓글)가 같다며 킨들 서재와 `기록 보기` 사진 두 장을 보냈다(imgur, 아직 보지 않음). 사진을 찍고 나니 킨들 책 하나가 홈에 표지 없이 들어왔고, 다른 책을 열었는데 그 책은 들어오지 않았다고 한다.
- 홈에 `화면이 크게 보입니다`(DPI 가 354 여야 한다) 창이 뜬다. 사용자는 바꾼 것이 없고 화면도 괜찮아 보인다고 한다. Ocean 5 Pro 는 354 가 그대로 먹혔으니(위 기기 정보) 이 기기에서만 무언가가 앱 밀도를 덮어쓴다. 앱별 표시 최적화에는 DPI 가 없다는 리뷰라 원인은 **(미확인)**. 기기 정보(화면 줄의 `App uses …dpi` 와 `intended 354dpi not applied`) 사진을 부탁했다.

### Tango·Ocean 5C 기기 정보 사진 (2026-10-31, Pebble Desk 1.7.1)

- 댓글(캐슈넛파이, Tango 1세대·Ocean 5C 두 대): 서드 앱은 다 되는데 기본 리더로 읽은 EPUB 은 독서 기록에 남지 않는다. 기본 리더가 정해 둔 이북 앱 목록에 없어 읽은 시간을 세지 않는다. 지원하지 않기로 했다(읽은 시간만 세는 것도 넣지 않음). 조사한 것은 docs/READER_APPS.md › 아이리더 기본 리더. 기본 리더 APK 는 인터넷에서 구하지 못했다(화웨이용 같은 앱 1.8.3 만 보았다).
- **Ocean 5C**: `Ocean 5C (SM07A-CKDC) · iReader · iReader · 안드로이드 14 (API 34) · 기타 · Pebble Desk 1.7.1 · 화면 1264×1680 · 320dpi · 632×840dp → 앱 기준 354dpi · 571×759dp · 1264×1680 로 셈 · UP1A.231005.007 release-keys`. Ocean 5 Pro 와 화면·밀도가 같고 앱 기준 354dpi 가 그대로 먹혔다(Musnap Ocean C 의 `화면이 크게 보입니다` 는 이 기기에서는 없음).
  - 시스템 속성: `ro.build.versionCode=4.1.0.214.0` · `persist.eink.dither256.disable=1`(Ocean 5 Pro·Tango 는 0) · `persist.eink.idisplay.cfa.wbmode=0`(컬러 필터(CFA) 패널 값으로 보인다 **(추정)**) · `persist.sys.eink.brightness=0` · `persist.sys.…`(잘림).
  - 설정 값: `screen_brightness=103` · `EinkLightStep=31`(조명 켜짐, 30단계 기기에서 31) · `clock_wallpaper_lock_screen_refresh_interval=300000` · `down_key_long_functio…`(잘림).
  - 권한: `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 꺼짐`. `와이파이 꺼짐 · 블루투스 켜짐 A2DP? HFP? HID?`.
  - 자동 추가 서비스: `연결 10/31 02:02 · 끊김 - · 끊긴 횟수 0`, `앱 종료 10/31 02:02 PKG_UPDATED · 10/29 23:59 OTHER · 23:55 PKG_UPDATED`, 마지막 앱 오류 없음, `배터리 최적화 제외 꺼짐 · 대기 그룹 EXEMPTED · 백그라운드 제한 꺼짐`.
  - 최근 화면: `com.szzy.ireader.ink.launcher / LauncherActivity`(iReader 홈), `com.zhangyue.iReader.Eink / ActivityRead`(기본 리더 읽는 화면). 기본 홈이 꺼져 있어 iReader 홈을 쓰며 Pebble Desk 는 앱으로 연다.
- **Tango**(1세대): `PM06A-CKD`, `ro.build.versionCode=4.1.0.215.0`, 화면 824×1648 · **320dpi** · 412×824dp → 앱 기준 230dpi(2026-10-13 사진은 400dpi · 330×659dp 였다. 사용자가 화면 크기를 바꿨는지 펌웨어가 바뀐 것인지 **(미확인)**. 앱 기준 230dpi 는 그대로). `persist.eink.dither256.disable=0` · `persist.sys.eink.color.filter=10` · `persist.sys.eink.contrast=6` · `screen_brightness=103` · `EinkLightStep=20` · `down_key_long_function=7` · `eink_wallpaper_name=系统日历` · `ireader_systemui_shutdown_pending=0`. 권한 `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 꺼짐`, `와이파이 켜짐·연결됨 · 블루투스 켜짐`. 서비스 `연결 10/31 01:29 · 끊긴 횟수 0`, `앱 종료 10/31 01:29 PKG_UPDATED · 10/30 01:13 PKG_UPDATED`. 최근 화면 `com.szzy.ireader.ink.launcher / LauncherActivity`, `com.zhangyue.iReader.Eink / RecentTaskListActivity`(최근 작업), `com.zhangyue.iReader.Eink / ActivityRead`.

### 다음에 받을 것

1. ~~설정 › 기기 정보 사진~~ 받음(2026-10-10). 빠른 설정 시험 결과는 아직.
2. 기기 정보의 빠른 설정 시험 세 버튼(`크레마 ›` · `메이북 ›` · `안드로이드 ›`) 가운데 무엇이 제어 패널을 여는지.
3. ~~페이지 버튼 키 번호~~ 위 버튼 = `VOLUME_UP`(scan 115). 아래 버튼은 `VOLUME_DOWN` **(추정)**.
4. Pebble Desk 홈에서 위에서 끌어내리기가 제어 패널을 한 번에 여는지.
5. ~~접근성(읽는 책 자동 추가): 제한된 설정을 푼 뒤 켜지는지~~ 풀어서 켰다(2026-10-10 댓글). 2026-10-14 에도 켜져 있다(1.5.2 기기 정보). 1.3.2 의 `앱 정보 열기` 로 연 화면에 `⋮` 가 나왔는지는 아직.
6. ~~사진의 기기 정보 화면 아래쪽~~ 받음(2026-10-14, 1.4.0 부터 빠른 설정 시험은 없다). 홈 화면은 아직.

## iReader Neo 3 Ultra

掌阅 iReader 의 6인치 전자잉크 리더(2026-03 출시, 중국 1,499위안). 2026-10-18 사용자(네삼울)가 댓글로 "와이파이를 끄고 켜면 상태 줄 아이콘이 잘 꺼졌다 켜지는데, 조명은 켜도 아이콘이 생기지 않는다"고 하며 기기 정보 사진 1장을 보냈다. → docs/ISSUES.md 6.

### 사양 (Notebookcheck·IT之家·sohu 기사)

- 6" E Ink Carta 1300, 1072×1448(4:3), 300ppi. 화면이 테두리보다 들어가 있다. 조명 ComfortLight Pro+: **30단계**, 색온도 조절, 주변 밝기에 따라 자동.
- 8코어 · 4GB · 64GB · 2000mAh · 약 150g · 7.5mm. OS 는 SmartOS(다른 앱 설치 됨).

### 기기 정보 사진 (2026-10-18 21:57, Pebble Desk 1.5.3)

- 기기: `Neo 3 Ultra (RM06L-CKDW) · iReader · iReader · 안드로이드 14 (API 34) · 기타 · Pebble Desk 1.5.3 · 화면 1072×1448 · 240dpi · 715×965dp → 앱 기준 300dpi · 572×772dp · 1072×1448 로 셈 · UP1A.231005.007 release-keys`. 크레마 페블과 픽셀이 거의 같고(아래 8px 짧다) 앱 기준 dp 는 메이북 E6·BOOX Poke3 와 같다.
- 시스템 속성: `persist.eink.dither256.disable=0` · `persist.sys.eink.color.filter=10` · `persist.sys.eink.contrast=30` · `persist.sys.eink.gesture.width=1.5` · `persist.sys.eink.…`(잘림). Ocean 5 Pro 와 같은 키들이다(SmartOS 공통).
- 설정 값: `screen_brightness=103` · **`EinkLightStep=14`** · `down_key_long_function=7` · `eink_wallpaper_name=系统默认` · `ireader_systemui_shutdown_pending=0` · `key…`(잘림).
- 권한: `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`. 자동 추가 서비스 `연결 10/18 20:48 · 끊김 - · 끊긴 횟수 0`, `앱 종료 -`, `배터리 최적화 제외 꺼짐 · 대기 그룹 EXEMPTED · 백그라운드 제한 꺼짐`.
- 최근 화면: `kr.co.millie.eink / EPubViewActivity`(밀리의서재 e-ink 읽는 화면) 세 번.
- 화면 위에 SmartOS 상단 바(왼쪽 시각 `21:57`, 오른쪽 `61%` · 배터리)가 있고 제목 왼쪽에 `‹` 가 있다.

### 두 번째 사진 (2026-10-18, 시스템 속성·설정 값 부분만)

- 다른 값은 모두 같고 **`EinkLightStep` 14 → 0**, `screen_brightness=103` 은 그대로다. 사용자가 조명 상태를 적지 않았지만 앞 사진이 조명을 켠 채, 이 사진이 끈 채로 보인다 **(추정)**.
- 그러면 `EinkLightStep` 이 조명 단계(0 = 꺼짐)이고, `screen_brightness` 는 조명과 상관없는 안드로이드 값이다. Ocean 5 Pro 두 사진(`EinkLightStep=0`)은 둘 다 조명을 끈 때였을 것이다 **(추정)**.

### 조명 아이콘이 안 나오는 까닭

- 상태 줄의 와이파이는 안드로이드 공통(`WifiManager.isWifiEnabled`)이라 어느 기기에서나 맞다. 조명은 기기마다 키가 달라 크레마·메이북만 안다(`Device.lightKeys`). iReader 는 `기기 종류` 가 `기타` 라 조명 키가 없고 `Device.lightOn` 이 늘 거짓이다 → 조명을 켜도 아이콘이 나오지 않는다. Ocean 5 Pro·Tango 도 같다.
- 조명 키는 **`EinkLightStep`**: 이 기기 조명이 30단계인데 첫 사진에 14, 두 번째 사진에 0 이고 `screen_brightness` 는 103 그대로다(위). 메이북처럼 꺼도 단계가 남는 기기가 아니다.
- 끄고 켤 때 이 키에 바뀜 알림이 오는지(SmartOS 가 `Settings.System` 으로 쓰는지)는 **(미확인)**. 오지 않으면 홈으로 돌아올 때만 아이콘이 맞는다.
- 고침(1.5.8, 2026-10-18 배포): 기기 종류가 `기타` 면 `Settings.System` 의 `EinkLightStep` > 0 을 조명 켜짐으로 본다(`Device.lightKeys`·`lightOn`. 키가 없으면 0 으로 읽혀 꺼짐). iReader 라도 기종·펌웨어에 따라 이 키가 없을 수 있어 제조사로 가르지 않는다. 기기 종류는 그대로 `기타`. 에뮬레이터(`CremaPebble_A11_Millie`)에서 키가 없는 채 홈을 띄운 뒤 `settings put system EinkLightStep 14` → `0` 으로 홈 상태 줄 ☀ 가 없음 → 나타남 → 홈을 떠나지 않고 사라짐을 확인.

### 다음에 받을 것

1. ~~조명을 끈 채·켠 채 기기 정보 사진~~ 받음(14 → 0, 어느 쪽이 켠 것인지는 추정). 고친 판(1.5.8)에서 홈 상태 줄 조명 아이콘이 켜고 끌 때 바로 바뀌는지(SmartOS 가 바뀜 알림을 보내는지).
2. 1.5.8 로 업데이트(지금 1.5.3).

## Minimal Phone

미국 Minimal Company 의 4.3인치 전자잉크 **폰**(물리 QWERTY 키보드, 전화·LTE). 이북 리더가 아니라 '방해 없는 폰'으로 나왔다. 2024 Indiegogo, 2025 초 출하, $399~(6GB·128GB / 8GB·256GB). 기기의 모델 번호는 `MP01`(Punkt. 의 2G 폰 MP01 과는 다른 기기). 후속 Minimal Phone 2 는 OLED 라 해당 없다.

### 사양 (리뷰)

- 화면 4.3" 흑백 E Ink 600×800(4:3), 약 230ppi(HelenTech. 대각선으로 계산하면 233). 조명·색온도 조절.
- MediaTek Helio G99 · 6/8GB · 128/256GB(microSD) · 3000mAh · 15W 무선 충전 · 168g. 카메라 앞뒤, NFC, 3.5mm.
- 버튼: 화면 아래 **물리 내비게이션 3개**(뒤로·홈·최근. 키보드 조명이 켜져도 이것은 안 빛난다), 그 아래 QWERTY 35키(단축키 1개, 조명 있음), 왼쪽 옆 음량·**새로고침**, 오른쪽 옆 전원 겸 지문. 페이지 버튼은 없다.
- 안드로이드 14, Google Play 있음. 원래 홈은 글자 목록만 있는 **Minimal Launcher**. AOSP Launcher3(Quickstep)도 들어 있어 바꿔 쓸 수 있다(HelenTech).
- 새로고침 버튼: 누르면 잔상 지우기, **길게 누르면 Minimal 설정 창**(밝기·색온도·키보드 밝기·새로고침 속도 Slow/…/Ultra 3단계). 빠른 모드는 2·3줄마다 한 줄만 갱신해 거칠다(리뷰).
- 2024 Indiegogo 렌더(밝은 몸체, 화면 위 앞 카메라, `9:41` 아이폰식 상단 바, 위젯 홈)는 출하품과 다르다. 출하품은 검은 몸체, 화면 아래 버튼 3개(가운데 로고), 앞 카메라는 키보드 왼쪽 아래(HelenTech·Tom's Guide 사진). 비교할 때는 리뷰 사진을 본다.
- 알림창은 안드로이드 기본 것을 끌어내려 연다(리뷰). 안드로이드는 3버튼 내비게이션 모드인데 화면의 버튼은 숨겼다(Keyboard Vagabond 사용기).

### 기기 정보 사진 (2026-10-10 07:51, Pebble Desk 1.3.1)

- **기기**: `Build.MODEL=MP01`, `Build.DEVICE=MP01`, `MANUFACTURER=ALONG`, `BRAND=Minimal_Phone`, 안드로이드 14 (API 34), `Build.DISPLAY=MP01_20260104_1412`. 화면 600×800 · 240dpi · 400×533dp, 앱 기준 168dpi · 571×762dp. `기기 종류` 는 `기타`.
  - `ALONG` 은 Minimal Company 가 아닌 이름이라 실제로 만든 곳(ODM)으로 보인다 **(추정)**.
- **밀도 맞춤 때문에 작다**: 572dp 폭으로 맞추느라 밀도를 240 → 168 로 낮춰 1dp = 1.05px ≈ 0.11mm 다. 페블(0.16mm)과 이 폰의 다른 앱(240dpi, 0.16mm)보다 **약 0.7배**로 보인다. 16sp 글자가 약 1.9mm(페블 2.5mm). 사진에서도 기기 정보 글자가 상단 바 글자보다 작다. 6~8인치 리더를 기준으로 한 맞춤이라 4.3인치 폰에는 맞지 않는다 → 정할 것(아래).
- **시스템 속성·설정 값**: 기기 고유 값으로 보이는 것은 없다. 나온 것은 대부분 잡음이다: 브랜드 `Minimal_Phone` 을 나눈 낱말 `phone` 이 `cache_key.telephony.phone_account_to_subid`·`gsm.current.phone-type`·`persist.log.tag.GsmCdmaPhone`·`volume_music_headphone` 에, 조명 낱말 `warm` 이 `ota.warm_reset` 에 걸렸다(`DeviceInfoActivity.makerWords`·`VENDOR_KEY`. 1.3.2 에서 고침). 조명은 `screen_brightness=8` 만 있다. 색온도 키는 **(미확인)**.
- **권한**: `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 꺼짐`. 안드로이드 14 인데 접근성이 켜졌다. 제한된 설정을 풀었는지, 막지 않는 설치 방법이었는지는 **(미확인)**.
- **버튼**: `4 KEYCODE_BACK · scan 158`. 스캔 코드 158 은 리눅스 `KEY_BACK` 이라 화면 아래 **물리 뒤로 버튼**이다(E6 의 상단 바 뒤로는 scan 0).
- **최근에 뜬 다른 화면**: `com.example.minimallauncher / MainActivity`(원래 홈. 패키지 이름이 안드로이드 예제 이름 그대로다), `com.android.launcher3 / RecentsActivity`(최근 버튼 → Launcher3 Quickstep 의 최근 앱 화면).
- **빠른 설정 시험**: `안드로이드 ›` 에 `보냄`(함수를 불렀다)만 보이고 열렸는지는 사진으로 알 수 없다 **(미확인)**. 상단 바가 안드로이드 기본 것이라 기본 알림창이 열릴 것이다 **(추정)**. `크레마 ›`·`메이북 ›` 는 결과 글자가 없다.
- **상단 바**: 하위 화면 위에 안드로이드 기본 상단 바(시각 · 알림 아이콘 / BT · 방해 금지 · Wi-Fi · 배터리 `62%`)가 있다. 그래도 기기 정보는 빠른 설정 시험 줄까지 다 들어간다. 아래 내비게이션 바는 없다.
- 글꼴은 고딕 계열이다(E6 의 명조 문제 없음).

### 다음에 받을 것·정할 것

1. **밀도 맞춤을 화면 크기로 제한할지**: 지금은 짧은 변이 무조건 572dp. 이 폰처럼 작은 화면은 기기 밀도(400dp 폭)를 그대로 쓰면 홈·목록 배치가 들어가지 않으니, 물리 크기 하한(예: 1dp ≥ 0.14mm)을 둘지 정한다. 홈 화면 사진을 먼저 받는다.
2. ~~기기 정보의 잡음 줄이기~~ 고침(1.3.2): 제조사·브랜드 낱말에서 `phone`·`mobile`·`tech` 같은 흔한 낱말(`COMMON_WORDS`)을 빼고, `warm` 은 `warm_reset`·`warmboot` 를 뺀다. 고친 판의 기기 정보 사진을 다시 받는다.
3. `안드로이드 ›` 를 눌렀을 때 알림창이 열렸는지. 홈 상단 줄 누르기에 쓸지 정한다.
4. 새로고침 버튼(짧게·길게)·키보드 단축키의 키 번호. 새로고침 길게로 뜨는 설정 창을 열었다 돌아온 뒤 `최근에 뜬 다른 화면`.
5. 물리 키보드로 홈·목록에서 글자를 치면 어떻게 되는지(Pebble Desk 는 키보드 입력을 받지 않는다).
6. 기본 홈으로 바꿨을 때 홈 버튼·최근 버튼이 그대로 동작하는지.

## Moaan MIX7S

중국 Moaan(墨案, 샤오미 생태계 브랜드)의 7인치 이북 리더. 사용자 댓글로 들어온 기기다(2026-10-10, Pebble Desk 1.3.0). 두 문제 모두 사용자가 됐다고 해 해결로 본다(2026-10-10 20:00 답글로 마무리). 아래 **다음에 받을 것**은 문제가 다시 생기면 받는다.

### 사양 (Good e-Reader)

- 7" E Ink Carta 1200, 1264×1680, 300ppi, 256단계. 조명(색온도 24단계). 새로고침 모드 균형·선명·빠름.
- 4코어 Cortex-A55 1.8GHz · 2GB · 64GB(microSD 없음) · 2300mAh. Wi-Fi · BT 5.1 · USB-C. $319.99.
- 기사에는 안드로이드 11, Google Play 없음(직접 설치나 다른 앱 장터), 중국 독서 앱(WeChat 读书·多看·京东) 기본 설치로 나온다.
- 기기 정보는 안드로이드 14(API 34)·RK3566 이다. 기사 이후 판(또는 업데이트)으로 보인다 **(추정)**.

### 기기 정보 사진 (2026-10-10 14:39, Pebble Desk 1.3.0)

- **기기**: `Build.MODEL=MoaanMIX7S`, `Build.DEVICE=rk3566_ebook`, `MANUFACTURER`·`BRAND` 모두 `rockchip`. 안드로이드 14 (API 34), 빌드 `UQ1A.240205.004.B1 release-keys`. `기기 종류` 는 `기타`.
- **화면**: `1264×1680 · 254dpi · 796×1058dp`, `앱 기준 300dpi · 674×896dp`.
  - 짧은 변 1264px 를 572dp 로 맞추면 밀도는 354 여야 하는데 **300** 이 나왔다. 앱이 밀도를 정할 때(`EinkActivity.attachBaseContext` 의 `displayMetrics`) 짧은 변을 1072px 로 받은 것과 같은 값이다. 기기의 앱별 해상도·DPI 설정(Rockchip 전자잉크 키트의 앱별 DPI) 때문인지 **(미확인)**.
  - 그래서 화면이 674dp 폭으로 그려져, 글자·줄이 맞춤대로보다 **약 0.85배** 작다.
- **시스템 속성·설정 값**: `init.svc.vendor.{health,light,power-aidl,thermal}-rockchip=running`(Rockchip 키트). Moaan 고유 설정 값:
  - `APPS_WHITE_LIST_KEY=["com.moan.sdmanage","info.plate…`(목록 앞부분만 보인다) — 백그라운드에서 살려 두는 앱 목록으로 보인다 **(추정)**.
  - `APPS_HIDDEN_LIST_KEY=["com.google.android.verifier",…` — 숨긴 앱 목록.
  - `gesture_hidden=1`, `long_side_key_a=0`·`long_side_key_b=5`, `wallpaper_path_key=["/mnt/sdcard/Wallpaper/IMG_3025…`·`wallpaper_type_key=3`, `screen_brightness=3`.
- **권한**: `자동 추가 켜짐 · 오늘 읽은 시간 꺼짐 · 기본 홈 켜짐`.
- **버튼**: `25 KEYCODE_VOLUME_DOWN · scan 0` → 페이지 버튼이 음량 키로 온다(iReader Ocean 과 같다).
- **최근에 뜬 다른 화면**: 사용 기록 권한이 없어 안 나옴.
- **빠른 설정 시험**: `안드로이드 ›` 에 `보냄`. `크레마 ›`·`메이북 ›` 는 결과 글자 없음.
- 시스템 글꼴이 손글씨체라 앱 글자도 그 글꼴로 보인다.

### 사용자가 알려 준 문제 (2026-10-10 댓글, 1.3.0)

1. **설정 › 독서 › 오늘 읽은 시간을 누르면 `설정이(가) 계속 중단됨`**: 앱이 여는 사용 기록 액세스 화면(`Settings.ACTION_USAGE_ACCESS_SETTINGS`)에서 **시스템 설정 앱이 멈춘다**(사진). Pebble Desk 쪽은 화면을 열었을 뿐이라 실패를 알 수 없다. 그래서 오늘 읽은 시간을 켤 수 없다.
   - 1.4.0 기기 정보의 시스템 화면 시험으로 확인(2026-10-10 19:10 댓글): `사용 기록 ›`(목록)은 그대로 멈추고, **`이 앱 기록 ›`(이 앱만의 사용 기록 화면, 같은 동작 + `package:` 주소)은 열려** 거기서 허용하니 오늘 읽은 시간이 켜졌다.
   - → 1.4.1 부터 앱이 처음부터 이 앱만의 화면을 연다(그 화면이 없는 기기는 목록, `ReadingLog.openAccessSettings`).
   - PC 우회(필요 없어짐): `adb shell appops set com.woody.pebbledesk GET_USAGE_STATS allow`.
2. **읽는 책 자동 추가를 켰는데 다시 꺼지는 때가 있다**: 1.3.0 은 앱 안의 켜짐 값과 접근성이 따로라(1.3.1 에서 하나로 함) 어긋나 보였을 수 있다. 실제로 접근성 서비스가 꺼진다면, 백그라운드 정리로 앱이 강제 종료돼 시스템이 서비스를 끄는 것으로 보인다(`APPS_WHITE_LIST_KEY` 에 없는 앱) **(추정)**.
   - 2026-10-10 18:25 기준: 지금은 켜져 있고, 사용자가 메모리 정리를 한 적은 없다고 한다. 꺼진다면 사용자 정리가 아니라 시스템의 자동 정리일 것이다 **(추정)**.
   - 사용자 답(2026-10-10 19:10): 재부팅하면서 백그라운드에서 앱이 꺼진 것 같다. **Moaan 설정에서 Pebble Desk 를 백그라운드에서 꺼지지 않게 바꾼 뒤로는 괜찮다**. 어느 화면의 어떤 설정인지는 **(미확인)**(`APPS_WHITE_LIST_KEY` 에 들어가는지는 다음 기기 정보 사진의 자동 추가 서비스 기록 끝에 보인다).

### 다음에 받을 것

1. 최신 판(1.4.0)의 기기 정보 사진: `앱 기준` 밀도가 그대로 300 인지와 `… 로 셈`(앱이 밀도를 정할 때 본 크기), 홈 화면 사진.
   - (받음) 시스템 화면 시험: 목록은 멈추고 이 앱만의 화면은 열린다 → 앱이 그 화면을 열게 바꿨다.
2. 자동 추가가 또 꺼지면 그때의 기기 정보 `자동 추가 서비스 기록`(끊긴 때·앱 종료 까닭·배터리 최적화·대기 그룹·백그라운드 제한, `APPS_WHITE_LIST_KEY` 에 들었는지).
3. 사용자가 바꾼 '백그라운드에서 꺼지지 않게' 설정 화면 사진(README·안내에 쓸 경로).
4. 자동 추가가 꺼졌을 때 안드로이드 접근성 화면에서 Pebble Desk 가 꺼져 있는지(앱 표시만 꺼진 것인지).
5. 옆 페이지 버튼 두 개의 키 번호(위·아래), `long_side_key` 설정 화면.

## 교보 sam 7 mono

교보문고의 7인치 흑백 리더(2026-03 출시, 컬러 sam 7 vivid 와 함께). A/S 는 인투원(into1)이 맡는다. 사용자가 Pebble Desk 1.5.1 로 쓰는 중이다(2026-10-13 사진).

### 사양 (교보 펀딩 페이지·기사·사용기)

- 7" Carta 1300, 300ppi. 물리 버튼 있음. 쿨·웜 조명. 옥타코어 2.2GHz · 4GB · 64GB(microSD 512GB) · 2700mAh. Wi-Fi 2.4/5GHz · BT 5.2. 안드로이드 14. Google Play 됨(vivid 사용기).
- 바탕 기기는 메이북 M7 계열 외형에 M8급 사양이라는 분석이 있었다(전파인증 정보를 근거로 한 블로그 글). 기기 정보로 **Haoqing 펌웨어**임을 확인했다(아래).

### 기기 정보 사진 (2026-10-13 22:55, Pebble Desk 1.5.1)

- **기기**: `sam7mono (sam7mono) · Kyobo · Kyobo · 안드로이드 14 (API 34) · 메이북`. 빌드 `KBRead-V2.2.0-2026070618`.
- **화면**: `1264×1680 · 320dpi · 632×840dp → 앱 기준 354dpi · 571×759dp · 1264×1680 로 셈`. 밀도 맞춤이 그대로 됐다. 하위 화면 위에 상단 바가 있어도 기기 정보는 시스템 화면 시험 줄까지 다 들어가고 아래가 남는다.
- **시스템 속성·설정 값**: `ro.haoqing.brand=haoqing-meebook` · `persist.haoqing.updatemodel=Kyobo-G7-RK3576` · `persist.haoqing.pag…`(잘림, 페이지 키 값) · `screen_brightness=0` · `isLightOn=true` · `light_mode=4` · `haoqing_warm_light=0` · `hq_contrast=35` · `brightness_start_bar=6` · `brig…`(잘림).
  - 교보 안의 기종 이름은 **G7** 이다. 칩은 RK3576.
  - 조명: `isLightOn=true` 인데 백색·온색 값이 모두 0 이다. `light_mode=4` 는 조명 팝업의 프리셋(꺼짐·차가운·따뜻한·은은한·내 설정) 번호로 보이고, 실제 밝기는 잘린 `brightness_*` 키에 있을 수 있다 **(미확인)**. Pebble Desk 는 `isLightOn` 만 보므로 홈 상태 줄에 조명 아이콘이 나올 것이다.
- **권한**: `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`.
- **자동 추가 서비스 기록**: 연결 10/13 21:28, 끊긴 횟수 0. 앱 종료 `PKG_UPDATED`(덮어 설치). 배터리 최적화 제외 꺼짐 · 대기 그룹 ACTIVE · 백그라운드 제한 꺼짐.
- **마지막으로 누른 버튼**: `4 KEYCODE_BACK · scan 0`. 스캔 코드가 0 이라 상단 바의 뒤로 아이콘(소프트 키)으로 보인다 **(추정)**. 옆 페이지 버튼 키 번호는 아직.
- **최근에 뜬 다른 화면**: `com.haoqing.wallpaper / DreamActivity` 셋(슬립 화면, 메이북 E6 와 같다).
- **펌웨어**: Haoqing 업데이트 서버에 `model=Kyobo-G7-RK3576`, `packageCode=KBRead-V2.2.0-2026070618`(와 옛 날짜)을 보내도 `"retobj":""` 였다(2026-10-13). 최신이거나, 교보 판은 다른 서버·일련번호가 필요한 듯하다.

### 상단 바 (사용자 사진·교보 안내 그림)

- **끌어내리는 알림창·빠른 설정이 없다**(사용자). 상단 바 아이콘을 하나씩 누르면 팝업이나 설정 화면이 뜨는 방식이다.
- 교보 안내 그림: 왼쪽 **홈 · 뒤로 · 새로고침(디스플레이) · 조명(소리) · 사용자 설정(톱니)**, 오른쪽 **블루투스 · Wi-Fi · 파일 관리자 · 배터리 · 시각**. 메이북 M6C 상단 바(왼쪽 홈·뒤로·새로고침·조명, 오른쪽 최근 앱)에 교보가 더한 모양이다.
  - 사용자 사진(Pebble Desk 설정 화면 위)에서는 블루투스·Wi-Fi 가 왼쪽 톱니 앞에 있고, 오른쪽은 파일 관리자 · `75%` · 시각이다. 펌웨어 판에 따라 배치가 다른 듯하다 **(추정)**.
- 새로고침·조명은 **팝업**, Wi-Fi·블루투스는 **설정 화면**으로 간다(사용자).
  - 새로고침 팝업은 `새로 고침 | 디스플레이` 두 탭. 새로 고침 탭은 갱신 모드 `고화질 · 기본 · A2 · 스크롤` 과 고른 모드 설명(교보 안내 그림). 메이북 새로고침 설정 창(Regal·Normal·Fast·A2, 대비)을 고친 것으로 보인다 **(추정)**.
  - 조명 팝업은 `꺼짐 · 차가운 · 따뜻한 · 은은한 · 내 설정` 버튼과 슬라이더 3개(달: 따뜻한 빛, 해: 차가운 빛, 스피커: 소리). 메이북 조명 창(`haoqing_brightness`: 꺼짐/차가운/따뜻한/은은한/수동 + 슬라이더 2개)과 버튼까지 같고 소리 슬라이더만 더했다 → **Haoqing 펌웨어로 거의 확실**하다.
  - 블루투스 아이콘은 `‹ 블루투스` 화면(켜기 스위치 · 검색 · 기기 이름 `sam 7 mono` · 사용할 수 있는 기기 · `태블릿 블루투스 주소`)을 띄운다. 안내 그림에서는 상단 바 아래 앱 화면이 흐리게 비쳐, 전체 화면이 아니라 위에 겹친 창으로 보인다 **(추정)**. 상단 바는 그대로 보인다.
  - Wi-Fi 아이콘도 같은 모양의 `‹ Wi-Fi` 화면(추가 `+` · 켜기 스위치 · `⋮` · 네트워크 목록, 연결·해제는 거기서). 안드로이드 설정의 블루투스·Wi-Fi 화면을 띄우는 것으로 보인다 **(추정)**.
- 오른쪽 옆에 페이지 버튼 두 개(안내 그림·사진).
- Pebble Desk 홈은 시스템 상단 바를 숨기므로(`hideStatusBar`) 홈에서는 이 아이콘들이 안 보인다. 위에서 아래로 쓸면 잠깐 나온다. `Device.kind` 가 `MEEBOOK` 이라 홈 상단 줄을 누르면 `com.haoqing.action.QUICK_SETTINGS`(→ `instantExpandNotificationsPanel`)를 보내는데, 펼칠 패널이 없어 아무 일도 없을 것이다 **(미확인)**. → 다음 판에 설정 › 화면 › `기기 상단 바 · 홈에서도 보이기`(처음은 꺼짐)를 넣었다. 켜면 홈에서도 상단 바가 보여 아이콘을 바로 누를 수 있다(홈 상단 두 줄은 뺀다).

### 다음에 받을 것

1. ~~기기 정보 사진~~ 받음(2026-10-13).
2. 홈 상단 줄을 누르면 무엇이 열리는지, 홈에서 맨 위를 아래로 쓸면 상단 바가 나오는지.
3. 옆 페이지 버튼 두 개의 키 번호(기기 정보 `마지막으로 누른 버튼`), 잘린 `persist.haoqing.pag…` 값.
4. 조명을 끈 채의 기기 정보(`isLightOn`·`light_mode` 가 어떻게 바뀌는지), 홈 상태 줄에 조명 아이콘이 맞게 나오는지.

## 바형 기기 (iReader Tango 외)

휴대폰 모양(세로가 가로의 약 2배)의 전자잉크 기기. 2026-10-13 iReader **Tango**(1세대. 사용자 확인, `Build.MODEL` 은 그냥 `Tango`) 사용자가 "바형이라 글자가 너무 작다, 글자나 전체 크기를 조정할 수 있느냐"는 댓글과 기기 정보·홈 화면 사진을 보냈다.

### 바형 기기 사양 (판매 페이지·리뷰, 2026-10-13 조사)

| 기기 | 화면 | 긴 변 ÷ 짧은 변 | 화면 폭 | 572dp 맞춤 시 페블 대비 크기 | 안드로이드 |
|---|---|---|---|---|---|
| (비교) 크레마 페블 | 6" 1072×1456, 300ppi | 1.36 | 91mm | 1.00 | 11 |
| (비교) Minimal Phone | 4.3" 600×800 | 1.33 | 65mm | 0.72 | 14 |
| **iReader Tango**(1세대) | 6.13" 824×1648, 300ppi | 2.0 | 70mm | 0.77 | 14 (기기 정보) |
| iReader Tango 2S | 5.84" 788×1576, 300ppi | 2.0 | 67mm | 0.74 | **(미확인)** |
| Onyx BOOX Palma · Palma 2 · Palma 2 Pro(컬러, 흑백 기준) | 6.13" 824×1648, 300ppi | 2.0 | 70mm | 0.77 | 11~13 **(미확인)** |
| Bigme HiBreak Pro | 6.13" 824×1648, 300ppi | 2.0 | 70mm | 0.77 | 14 |
| Bigme HiBreak | 5.84" 720×1440, 276ppi | 2.0 | 66mm | 0.73 | **(미확인)** |
| Hisense A9 · A9 Pro | 6.1" 824×1648, 300ppi | 2.0 | 70mm | 0.77 | 11~ **(미확인)** |
| Hisense A7 | 6.7" 900×1800, 300ppi | 2.0 | 76mm | 0.84 | **(미확인)** |
| Mudita Kompakt | 4.3" 480×800, 약 217ppi | 1.67 | 56mm | 0.62 | 자체 OS(AOSP 기반). 앱 설치 **(미확인)** |
| Xiaomi·Moaan InkPalm 5 | 5.2" 720×1280, 284ppi | 1.78 | 64mm | 0.71 | 8.1 → **설치 안 됨**(minSdk 28) |

- 같은 6.13" 824×1648 300ppi 패널(Carta 1200·1300)을 Tango·Palma·HiBreak Pro·Hisense A9 가 함께 쓴다. 한 기기로 맞추면 이 무리가 다 맞는다.
- **바형인지 가르기**: 화면 전체 크기(px)의 긴 변 ÷ 짧은 변. 지금까지 본 리더·폰은 모두 1.33~1.36, 바형은 1.67~2.0 이라 1.5 를 기준으로 하면 애매한 기기가 없다. 앱 기준(짧은 변 572dp)으로는 높이 약 860dp 이상. 앱은 이미 `Ui.designDensityDpi` 에서 이 크기를 읽는다(기기 정보의 `… 로 셈`). 선언 밀도(dpi)는 실제와 달라(Tango 400 → 실제 약 300ppi) 물리 크기를 셀 수 없다.
- 작게 보이는 것은 바형 때문이 아니라 **화면 폭**이 좁아서다(Minimal Phone 도 바형이 아닌데 0.72). 바형은 그에 더해 세로가 남는다.

### Tango(1세대) 에서 본 것 (기기 정보·홈 사진, Pebble Desk 1.5.0)

- 기기 정보: `Tango (PM06A-CKD) · iReader · iReader · 안드로이드 14 (API 34) · 기타 · 화면 824×1648 · 400dpi · 330×659dp → 앱 기준 230dpi · 573×1146dp · 824×1648 로 셈 · UP1A.231005.007 release-keys`. 권한 `자동 추가 꺼짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`. 최근 화면에 `kr.co.kyobobook.KEL / B2BViewerEpubMainActivity`(교보도서관 읽는 화면), `com.szzy.ireader.settings / ProxyActivity`, `com.szzy.ireader.ink.abupdate / MainActivity`.
- 홈: 배치는 페블과 같고(폭 573dp), 함께 읽는 책 아래부터 자주 쓰는 앱 위까지 비어 있다. 사용자는 자주 쓰는 앱 크기는 적당하다고 했다.

- 2026-10-31 기기 정보 사진(Pebble Desk 1.7.1)은 화면이 **320dpi** · 412×824dp 로 바뀌어 있었다(앱 기준 230dpi 그대로). 나머지는 [Tango·Ocean 5C 기기 정보 사진](#tangoocean-5c-기기-정보-사진-2026-10-31-pebble-desk-171).

### 에뮬레이터로 본 것 (`Tango_A14`: android-34 google_apis, 824×1648, 400dpi, 내비게이션 바 없음)

- 1.5.0 에서 잘리거나 겹치는 화면은 없다. 세로가 페블보다 약 370dp 길어 홈 책 자리(큰 표지는 230dp 에서 멈춘다)와 설정·독서 기록·기기 정보 등 한 화면짜리는 아래 약 40% 가 빈다. 모든 앱 같은 쪽 목록은 한 쪽 줄 수가 늘어 다 쓴다.
- 앱 글자는 sp 라 기기 글꼴 크기가 걸린다. 안드로이드 14 는 큰 글자를 덜 키워(비선형) 130% 로 해도 제목·목록 글자는 거의 그대로였다.

## Onyx BOOX Leaf · Poke3 (안드로이드 10)

Leaf(7인치 1264×1680, 2021)·Poke3(6인치 1072×1448, 2020). 사용자가 보낸 기기 정보 사진(2026-10-14 10:23, Pebble Desk 1.5.0, 두 기기 모두 기본 홈)으로 둘 다 **안드로이드 10 (API 29)**, BOOX 펌웨어 3.5(빌드 `2023-12-12_…_3.5_4687b1901`)임을 확인했다. 값은 **한눈에** 표에 있다.

- 밀도: Leaf 는 300dpi(674×896dp)로 선언돼 앱이 354dpi(571×759dp)로 맞춘다. Poke3 는 300dpi(572×772dp)라 맞추지 않는다.
- 자동 추가 서비스는 둘 다 켜져 있고 연결된 뒤 끊긴 적이 없다(배터리 최적화 제외 꺼짐 · 대기 그룹 ACTIVE · 백그라운드 제한 꺼짐). 오늘 읽은 시간(사용 기록 액세스)은 둘 다 꺼짐.

### 사용자가 알려 준 문제 (2026-10-14, 1.5.0 에서 1.5.2 로 업데이트, 사진 2장)

- **업데이트 실패**: `설치` 뒤 받는 중 창이 지나고 `설치하지 못했습니다`. 원인은 `PackageManager.getPackageArchiveInfo` 가 안드로이드 9·10 에서 `GET_SIGNATURES` 가 있을 때만 서명을 읽는 것(AOSP `android-9.0.0_r1`·`android-10.0.0_r1` 소스. 11 부터 `GET_SIGNING_CERTIFICATES` 로도 읽는다). `GET_SIGNING_CERTIFICATES` 만 주던 `Update.verify` 는 `signingInfo` 가 null 이라 늘 실패했다. 1.5.3 에서 둘 다 준다. 에뮬레이터 `Poke3_A10`(API 29, 이름과 달리 지금 Leaf 크기 1264×1680·300dpi)에서 1.5.0 정식 판은 실패, 고친 판은 1.5.2 를 받아 설치까지 됐다. 1.5.2 이하가 깔린 안드로이드 9·10 기기는 1.5.3 을 한 번 직접 설치해야 한다.
- **아래 메뉴의 `닫기` 잘림**(1264px 화면): 앱이 밀도를 300 → 354dpi 로 바꿀 때 화면 dp 크기(`screenWidthDp` 674)를 그대로 둬, 안드로이드 10 이 `displayMetrics.widthPixels` 를 674 × 2.2125 = **1491px** 로 셈했다. 아래 메뉴(`Sheet`, 창 최소 폭 100%)가 그 폭으로 높이를 재서 두 줄 설명을 한 줄로 보고 모자란 높이(373px, 필요 438px)를 잡아 `닫기` 줄이 밀려났다. 1.5.3 에서 `EinkActivity.attachBaseContext` 가 dp 크기도 같은 비율로 바꾼다(674 → 571). 안드로이드 14 에뮬레이터(Tango)는 픽셀을 실제 화면에서 잡아 바뀌기 전후 화면이 같았다. Poke3 는 300dpi 로 선언돼(기기 정보) 밀도를 바꾸지 않아 이 문제가 없다(잘린 사진은 Leaf 로 보인다).
- 같은 이유로 안드로이드 10 의 밀도를 바꾸는 기기에서는 `displayMetrics.widthPixels` 로 폭을 셈하는 홈 책 정보·올해 읽은 책 격자·책 검색도 1.5.2 까지 화면보다 넓게 셈했을 것이다 **(추정, 책이 있는 화면은 확인 전)**.

## Onyx BOOX Leaf2 (사용자 보고)

오닉스(Onyx) BOOX 의 7인치 이북 리더(2022). 리뷰 기준 안드로이드 11, 오닉스 자체 런처를 얹은 안드로이드이고 Google Play 는 설정에서 켜야 한다. 기기 정보 사진은 아직 없어 **한눈에** 표에는 넣지 않는다.

### 사용자가 알려 준 문제 (2026-10-10 댓글 2개, 사진 1장)

- 시작하기·설정에서 접근성을 켜려 하면(시스템 접근성 › Pebble Desk › `Use Pebble Desk`) 안드로이드의 확인 창 **`Allow Pebble Desk to have full control of your device?`** 가 뜨는데, 설명과 두 권한(`View and control screen`, `View and perform actions`) 아래에 **`Allow`·`Deny` 단추가 보이지 않는다**(창 아래쪽이 비어 있다). 그래서 켤 수 없다.
- 이 창은 안드로이드 설정 앱의 것(안드로이드 11 `AccessibilityServiceWarning`)이다. 단추가 그 빈자리에 있는데 글자색(테마의 강조색)이 전자잉크에서 흰색에 가깝게 그려져 안 보이는 것으로 보인다 **(추정)**. 제한된 설정(안드로이드 13+)과는 다르다(Leaf2 는 11).
- Pebble Desk 가 고칠 수 있는 화면이 아니다. 해 볼 것:
  1. 권한 목록 바로 아래 빈 곳(첫 단추 `Allow` 자리)을 눌러 본다. 그 아래가 `Deny` 라 잘못 눌러도 창이 닫힐 뿐이다 **(미확인)**.
  2. BOOX 앱 최적화(설정 앱에 대비·진하게)로 단추 글자가 보이게 한다 **(미확인)**.
  3. PC 에서 켠다: `adb shell settings put secure enabled_accessibility_services com.woody.pebbledesk/com.woody.pebbledesk.ReaderWatchService` 와 `adb shell settings put secure accessibility_enabled 1`.
- 처음 보고(15:42)에서 "권한을 아무리 주고 싶어도 안 된다"고 한 것이 이것이다. 시작하기에서 켜지 못해 나갈 수 없던 문제는 1.4.0 의 `나중에 하기` 로 풀었다.
- 2026-10-10 18:25 기준: 위 1 을 해 봤는데 **빈자리를 다 눌러도 반응이 없다**. 단추가 안 보일 뿐 아니라 눌러도 안 되므로, 창 아래가 잘려 단추가 화면 밖에 있거나 창이 터치를 받지 않는 것일 수 있다 **(추정)**. 남은 길은 2(앱 최적화)·3(adb).
- **해결(2026-10-11 21:41 답글)**: "allow 는 안 보이지만 아래 shortcut 인가 그걸 켜면 위에도 켜지더라". 접근성 › Pebble Desk 화면의 아래 스위치(`Pebble Desk shortcut`, 한국어 11·12 `단축키`·13+ `바로가기`)를 켜면 서비스도 켜진다. 이때 확인 창이 떴는지, 왜 되는지는 **(미확인)**. 앱 안내 창(꺼져 있을 때 설명 아래 단락)과 TROUBLESHOOTING.md 에 이 방법을 적었다. 에뮬레이터(안드로이드 11, 영어)에서 위 스위치 `Use Pebble Desk` 를 누르면 `Allow`/`Deny` 확인 창이 뜨는 것을 봤다. 아래 스위치로 켜는 것은 Leaf2 사용자 확인뿐이다.
- 같은 사용자의 **BOOX Poke6 에서는 된다**. 다른 오닉스 기기(Go 10.3 Lumi)도 된다는 보고가 있다. Leaf2 의 펌웨어(또는 화면 크기·회전)에서만 생기는 문제로 보인다 **(추정)**.

### 다음에 받을 것

1. 기기 정보 사진(안드로이드 버전·밀도·키 번호).
2. ~~빈자리를 눌렀을 때 켜지는지~~ 안 된다(18:25). ~~접근성 확인 창 전체 사진~~ 바로가기 스위치로 켜서 필요 없어졌다(2026-10-11).
3. ~~adb 를 쓸 수 있는지~~ 필요 없어졌다.

## Onyx BOOX 펌웨어 (Leaf2·Poke6·Palma·Palma2·Tab Ultra C Pro 에서 확인)

### 펌웨어 얻기·풀기

- 업데이트 서버: `GET http://data.onyx-international.cn/api/firmware/update?where={"buildNumber":0,"buildType":"user","deviceMAC":"","lang":"en_US","model":"Leaf2","submodel":"","fingerprint":""}`(`where` 는 URL 인코딩). 답의 `downloadUrlList` 가 다운로드 주소, `md5`·`size`·`fingerprint` 도 온다. 없으면 빈 답.
  - Leaf2: V4.0(`2025-04-02_13-00_v4.0-rel_77d3ae190`, 1.76GB, `en_US`). Poke6 은 `en_US` 로는 빈 답이고 `zh_CN` 으로만 3.5.4(2024-10-17) 가 왔다. Poke6S 는 `zh_CN` 으로 v4.0-beta4.
  - 2026-10-10 에 본 다른 기종(모두 `BooxKeys.csv` 에 키 있음): Poke5·Leaf3C 3.5.4(`zh_CN`), Page·Tab Mini C 4.0, Palma 4.0.2, Note Air3 4.1.1, Go 10.3·Go Color 7·Go6·Note Air4 C 4.2, Palma2 4.2(2026-08), Tab Ultra C Pro 4.2.1(2026-08).
  - Palma(1): 4.0.2(`2025-06-17_20-38_4.0.2-rel_0617_d4b9d37036`, 안드로이드 11, 1.82GB, `en_US`, `firmware-us.boox.com`). 2026-10-20 에 받아 얼리기를 풀어 봤다(아래 앱 얼리기).
  - `firmware.boox.com`(중국 CDN) 주소는 연결이 멈추곤 한다. `curl -C - --speed-limit 30000 --speed-time 30` 으로 이어받기를 되풀이하면 받아진다. `firmware-us.boox.com` 에는 같은 파일이 없다(404).
- 받은 파일은 `~/Downloads/Pebble Desk/boox/<기종>/update.upx`(+ `source.txt`: 주소·md5). 푼 것은 분석 뒤 지웠다.
- 파일 `update.upx` 는 AES-128-CFB 로 암호화돼 있다. 기종별 키·IV 는 GitHub `Hagb/decryptBooxUpdateUpx` 의 `BooxKeys.csv`(Leaf2·Poke6 둘 다 있다). 푼 결과는 A/B 전체 OTA(`payload.bin`)라 메이북과 같은 순서로 푼다.

```bash
openssl enc -d -aes-128-cfb -K <키> -iv <IV> -nopad -in update.upx -out update.zip   # 첫 4바이트가 PK 면 맞는 키
python3 tools/ota_payload.py update.zip img system system_ext product
7zz x -snl -ofs/system img/system.img
jadx --no-res -d src/framework fs/system/system/framework/framework.jar
```

- BOOX 런처는 `system/priv-app/kcb-release`(패키지 `com.onyx`). Onyx 가 더한 시스템 코드는 `framework.jar` 의 `android.onyx.*`.
- 네오리더(BOOX 기본 리더)는 `system/app/kreader2-release`(패키지 `com.onyx.kreader`). 네이티브 라이브러리(`libneo_*`·`libonyx_*`)는 APK 밖 `system/lib64` 에 있어, 에뮬레이터에서 돌리려면 다시 서명하고 이것들을 APK 에 넣어야 한다. 문서를 열기 전 기기 검사도 따로 있다(docs/READER_APPS.md › 네오리더).

### 기종·버전별로 맞춰 본 것

| | Poke6 3.5.4 | Leaf2 4.0 | Palma2 4.2 (휴대폰형) | Tab Ultra C Pro 4.2.1 (태블릿형, 런처를 Kotlin 으로 새로 씀) |
|---|---|---|---|---|
| 얼리기 = `DISABLED_USER`(3), Onyx·시스템 앱 제외 | 같음 | 같음 | 같음 | 같음 |
| 런처 `com.onyx`(`priv-app/kcb-release`) | 같음 | 같음 | 같음 | 같음 |
| 받는 곳 `onyx.action.open_frozen_app`, extra `args_pkg`·`args_class`·`args_user_id`, `Application.onCreate` 에서 등록 | 같음 | 같음 | 같음 | 같음 |
| 데이터 창구 `com.onyx.system.database.ContentProvider`(exported, 권한 없음) | 같음 | 같음 | 같음 | 같음 |
| 얼리기 설정 `com.onyx.app.freeze.page` | 같음 | 같음 | 같음 | 같음 |
| 매니페스트의 BOOT_COMPLETED 받는 곳 | 없음 | 없음 | 없음 | 없음 |

- 같은 회사가 2024-09 ~ 2026-08 사이에 낸 3.5 ~ 4.2 계열이 모두 같아, 다른 기종도 같을 것으로 본다 **(추정)**. 확인 스크립트는 펌웨어마다 `framework.jar` 의 `ApplicationFreezeHelper`, 런처의 `EACStatusChangedReceiver`·`BroadcastHelper`·`ContentBrowserApplication` 만 `jadx --single-class` 로 풀어 본다.

### 앱 얼리기 (2026-10-10 댓글: 자주 쓰는 앱·책의 앱이 사라진다, Leaf2·Poke6)

- 얼리기 = 앱을 **꺼 둔다**(`setApplicationEnabledSettingAsUser(pkg, COMPONENT_ENABLED_STATE_DISABLED_USER)`, `android.onyx.utils.ApplicationFreezeHelper`). 숨김(hidden)·정지(suspend)가 아니다. Onyx·시스템 앱과 `appFreezeWhiteList` 는 얼리지 않는다. 단 Google Play 끄기(`disableGooglePlay`, Play 스토어·GMS·GSF)도 같은 `DISABLED_USER` 라, Pebble Desk 는 시스템 앱을 얼린 앱으로 보지 않는다.
- 언제: 앱마다 `autoFreeze` 설정(BOOX 설정의 얼리기 관리). 그 앱에서 다른 앱으로 넘어가면 얼릴 차례에 넣고, 약 30초마다 보다가 그 앱이 화면 맨 위가 아니고 포그라운드 서비스도 없으면 작업을 지우고 끈다(`ApplicationFreezeController`). `새로 설치한 앱 얼리기`(`isFreezeNewlyInstalledApplication`)가 켜져 있으면 설치하는 앱마다 켜진다.
- 그래서 앱을 쓰는 동안만 런처 조회(`queryIntentActivities`)에 나오고, 끄고 나면 빠진다. 꺼 둔 앱은 `getPackageInfo` 로는 찾아져 Pebble Desk 설정에서 지워지지는 않는다(목록에서만 사라짐).
- 얼린 앱 열기: BOOX 런처가 동적으로(권한 없이) 받는 방송 `onyx.action.open_frozen_app`(extra `args_pkg`·`args_class`·`args_user_id`)을 받으면 앱을 풀고 연다(`EACStatusChangedReceiver`). BOOX 플로팅 버튼은 같은 일을 `com.onyx.floatbutton.open.apps` 로 한다.
- Pebble Desk 는 `com.onyx` 가 있으면 앱 단위로 꺼진(`DISABLED_USER`) 앱의 실행 화면도 목록에 넣고, 누르면 위 방송을 보낸다(아래 표). 실기기 **(미확인)**.
- BOOX 런처 띄워 두기: 방송을 받는 곳은 런처가 떠 있을 때 코드로 등록한 것이고(`ContentBrowserApplication` → `EACStatusChangedReceiver`), 런처에는 부팅 완료를 받는 매니페스트 항목이 없어 Pebble Desk 가 기본 홈이면 부팅 뒤 꺼져 있을 수 있다(시스템 UI 도 사용자가 `앱 최적화` 를 누를 때만 런처를 부른다). 공개 서비스 `EACService` 는 붙으면 포그라운드 알림을 띄워 쓰지 않는다. 대신 권한 없이 열린 데이터 창구 `com.onyx.system.database.ContentProvider` 에 불안정(unstable) 연결만 해 둔다(`Device.keepBooxLauncher`). 창구는 시작할 때 DB 라이브러리 초기화만 한다(`FlowManager.init`). 안드로이드가 런처를 띄우고 이 앱과 같은 중요도로 살려 둔다(불안정 연결도 `OomAdjuster` 가 똑같이 센다. 런처가 죽어도 이 앱은 죽지 않는다). 홈이 보일 때·잠금이 풀릴 때·화면이 켜질 때 다시 잡는다(잠금 전에는 런처를 띄울 수 없다). 연결은 한 줄(단일 스레드)로 차례대로 잡고, 못 잡으면 전 연결을 둔다.
- 얼린 앱을 누르면: 연결을 다시 잡아(런처가 죽었으면 띄운다) 방송을 보낸다. 막 뜬 런처는 창구를 내놓은 뒤 `Application.onCreate` 에서 받는 곳을 등록하므로 그 사이 방송을 놓칠 수 있다. 그래서 2초 뒤에도 홈이 초점을 갖고 있으면 — 앱이 이미 풀렸으면 직접 열고, 아직 얼려 있으면 한 번 더 보낸다. 그래도 2초 안에 안 열리면 안내 창(`얼리기(freeze) 설정 열기` → `com.onyx.app.freeze.page`, `앱 정보`).
- 얼린 앱 판정에서 뺀 것: 시스템 앱(`FLAG_SYSTEM`·`FLAG_UPDATED_SYSTEM_APP`), 이름에 `com.onyx` 가 든 앱(펌웨어 `isOnyxOrSystemApp` 과 같게), Google Play·GMS·GSF(`disableGooglePlay` 가 같은 방법으로 끈다. 누르면 사용자가 끈 Play 가 다시 켜진다).
- codex 리뷰(2026-10-10, gpt-5.6-sol high): 방법은 "조건부"로 맞음. 반영한 것 — 누를 때 다시 띄우기·재시도, 연결 직렬화·실패 시 전 연결 유지, 시스템 판정을 펌웨어와 같게, Google Play 제외. 기각한 것 — "얼리기·풀기 때 앱 변경 순번이 안 바뀐다"(Leaf2 `PackageManagerService.setEnabledSetting` 명령 덤프에 `updateSequenceNumberLP` 호출이 있다), "런처를 늘 띄워 두는 비용"(기본 홈이 BOOX 런처일 때와 같은 상태).
- **Pebble Desk 자신이 얼려진다**(2026-10-20 사용자 전달: Palma 1 에서 접근성 권한이 때때로 풀린다. Palma 4.0.2 펌웨어로 확인):
  - 런처의 `새로 설치한 앱 자동 얼리기`(`ApplicationSettingUtil.isFreezeNewlyInstalledApplication`, **처음 켜짐**)가 켜져 있으면 처음 설치하는 앱(`PACKAGE_ADDED` 중 `EXTRA_REPLACING` 이 아닌 것. 덮어 설치·업데이트는 아님)을 얼리기 대상(`autoFreeze`)으로 둔다(`AppInstalledAction`). 빼는 앱은 프리셋에서 막은 앱·Google·입력기·TTS·VPN 뿐이고(`PresetEACConfig.getDynamicDisAllowAutoFreezePkg`) 홈 앱·접근성 앱은 빼지 않는다. 그래서 지우고 다시 깔면 또 대상이 된다.
  - 대상 앱에서 다른 앱으로 넘어가면 얼릴 차례에 넣고, 30초마다 보다가 맨 위 작업의 앱이 아니고 멈춘 지 30초가 지났고 포그라운드 서비스가 없으면(`Settings.Global` `freeze_inactive_detect_include_foreground_service`, 처음 1) 작업을 지우고 `DISABLED_USER` 로 끈다(`ApplicationFreezeController`). 부팅 때(`OECService` 가 설정을 읽을 때)는 대상 앱을 묻지 않고 모두 끈다(`EACAutoFreezeImpl.init`). 얼리지 않는 것은 화이트리스트(`OnyxSystemConfig` `KEY_APP_FREEZE_WHITE_LIST`)·Onyx·시스템 앱·입력기다(`ApplicationFreezeHelper.disableAppsAsUser`).
  - 접근성: 얼렸다 풀면 접근성 설정에는 켜진 채(`enabled_accessibility_services` 그대로)인데 시스템이 '멈춘 서비스'(crashed)로 두어 붙이지 않는다(접근성 화면 `작동하지 않음`, `accessibility_enabled` 0). 껐다 켜기·덮어 설치·재부팅으로 다시 붙는다. 앱이 쓰는 켜짐 판정(설정 값)으로는 `켜짐` 이라 홈의 `멈춰 있습니다` 창(5초 뒤 안 붙어 있으면)이 알린다. 강제 종료는 접근성 목록에서 아예 지운다(꺼짐). 메모리 정리처럼 프로세스만 죽으면 시스템이 다시 띄워 붙는다. 셋 다 `Palma_A11` 에뮬레이터(안드로이드 11, 824×1648, 300dpi)에서 `pm disable-user`·`pm enable`·`am force-stop`·`kill -9` 로 확인.
  - 설정 메뉴: BOOX 설정 › 앱 및 알림 › `설정 잠금`(영어 `Freeze Settings`, 한국어 번역이 잘못됨. 휴대폰형 설정도 같은 `FREEZE_SETTINGS` 화면) — 앱마다 얼리기 끄기, `새로 설치한 앱 자동 얼리기`(한국어 문구가 영어로 나온다). 앱 하나는 BOOX 앱 목록에서 길게 눌러 앱 최적화 › `앱 백그라운드 자동 동결하기` 도 같다. Palma 실기기에서 `[설정] - [앱 및 알림] - [설정 잠금]` 이 맞다는 답을 받았다(파브르박, 2026-10-20 14:28).
  - 같은 답에서 아이콘을 길게 눌러 `[최적화]` › `[기타]` 탭 › `[백그라운드에서 활성화 상태]` 를 `무제한` 으로 바꿨다고 했다. 4.0.2 런처에서 가장 가까운 것은 `eac_item_full_pm_access`(영어 `Stay active in the background`, 한국어 `백그라운드 실행 허용합니다`, 켜고 끄기)로, 앱의 백그라운드 제한(앱 작업 `RUN_ANY_IN_BACKGROUND`, `EACPMImpl.setForceAppStandby`)을 푼다. 얼리기와는 따로다(기기 정보의 `백그라운드 제한` 이 이것). `무제한` 을 고르는 화면은 4.0.2 에 없어 사용자 기기의 펌웨어가 다를 수 있다 **(미확인)**. 자동 추가에는 필요 없다: `Palma_A11` 에뮬레이터에서 `appops set … RUN_ANY_IN_BACKGROUND ignore` 로 막은 채 다른 앱에 60초 두거나 프로세스를 죽여도 접근성 서비스는 붙어 있었다/다시 붙었다(시스템이 붙여 두는 서비스라 백그라운드 제한을 받지 않는다). Pebble Desk 는 작업 예약·알람·포그라운드 서비스를 쓰지 않는다.
  - 앱이 아는 법: 펌웨어 공개 함수 `android.onyx.optimization.EInkHelper.getAutoFreezeApps()`(dex 의 숨은 API 표시 `SDK,CORE-PLATFORM-API` 라 일반 앱이 리플렉션으로 부를 수 있다. 시스템 서비스 `oec_service`(`IOECService`) 의 같은 이름 함수를 부르고, 서비스는 권한을 묻지 않는다. 앱 프로세스마다 `ViewRootImpl` 이 이미 이 서비스를 쓴다)가 대상 앱 목록을 준다. 서비스가 준비되지 않았으면 빈 목록. `Device.booxAutoFreeze` 가 이 목록에 이 앱이 있는지 보고, 대상이면 ⑭ 시작하기 맨 앞 단계·`멈춰 있습니다` 창의 얼리기 안내·기기 정보 `BOOX 얼리기(freeze) 켜짐` 을 보인다. 실기기 **(미확인)**. 에뮬레이터 시험은 디버그 소스에 같은 이름의 가짜 클래스(`debug.fakefreeze` 속성의 패키지를 돌려줌)를 잠깐 넣어 했다(부트 클래스에 없으면 앱 클래스로 찾아진다. 커밋하지 않았다).
- 재현: 에뮬레이터에서 `pm disable-user <패키지>` 가 얼리기와 같다(시스템 앱이 아닌 시험 앱으로. 끈 직후 재부팅하면 상태가 아직 파일에 안 써져 풀린다). BOOX 런처 대신 가짜 `com.onyx` 앱(같은 이름의 데이터 창구, `Application.onCreate` 에서 `onyx.action.open_frozen_app`·`com.onyx.app.freeze.page` 받는 곳을 등록해 로그를 남기고 자기 화면을 띄움)을 깐다. "열림"은 `appops set com.onyx SYSTEM_ALERT_WINDOW allow`(백그라운드에서 화면 띄우기 허용), "안 열림"은 `deny` 로 흉내 낸다. 꺼진 런처는 `am force-stop com.onyx` 나 재부팅으로 만든다.

## 리디페이퍼 4 (RIDIPAPER 4)

리디의 7인치 리더(2022-04 출시). 사용자가 Pebble Desk 1.5.12 를 기본 홈으로 하지 않고 쓰는 중이다(2026-10-23 기기 정보 사진).

### 기기 정보 사진 (2026-10-23 14:42, Pebble Desk 1.5.12)

- **기기**: `RIDIPAPER 4 (exynos850) · Hajen Co., Ltd. · Android · 안드로이드 10 (API 29) · 기타`. 빌드 `QP1A.190711.020 dev-keys`. 칩 Exynos 850, 만든 곳 Hajen(하젠).
- **화면**: `1264×1680 · 320dpi · 632×840dp → 앱 기준 354dpi · 571×759dp · 1264×1680 로 셈`(sam 7 mono 와 같은 맞춤).
- **시스템 속성·설정 값**: `init.svc.epdd=running` · `ro.epd.fw.version=Hajen_v.1.0` · `ro.product.ridi.epd.vcom=1560` · `vendor.epd.curr.lot=B035` · `vendor.epd.setup_c…`(잘림) · `screen_brightness=0` · `saved_brightness=0` · `screen_brightness_color=0`. 전자잉크는 `epdd` 데몬·`*.epd.*` 속성(Rockchip 계열이 아니다).
- **권한**: `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 꺼짐`.
- **자동 추가 서비스 기록**: 연결 10/23 14:13, 끊긴 횟수 0. 배터리 최적화 제외 꺼짐 · 대기 그룹 ACTIVE · 백그라운드 제한 꺼짐. 서비스는 붙어 있다.
- **최근에 뜬 다른 화면**: 접근성 설정 화면 셋뿐(이북 앱 화면이 없다).

### 사용자가 알려 준 문제 (2026-10-23, 1.5.12, 홈 사진)

- 자동 추가·접근성이 켜져 있는데 표지가 보이지 않는다. 쓰는 앱은 **밀리의서재 E ink·북커스**(사용자 댓글). 홈 사진에서 책은 들어왔다(제목·앱·진행률 `73% 읽음`, 북커스 책은 반납일까지). 표지만 빈 표지다.
- 원인: 서재 표지는 접근성 서비스의 화면 캡처(`AccessibilityService.takeScreenshot`)로 잘라 오는데, 이 함수는 **안드로이드 11(API 30)부터** 있다. 안드로이드 10 에서는 자르지 않고 넘어간다(`ReaderWatchService.captureCovers`). 그래서 안드로이드 9·10 기기(BOOX Leaf·Poke3 도)는 화면에서 자르는 표지가 모두 빈 표지다. 책 파일·앱 데이터에서 꺼내는 표지(책 파일 읽기를 켠 문리더·네오리더·이지뷰어·채리뷰어의 EPUB, KOReader, 데이터를 옮긴 Readest)는 들어온다. 기본 홈 여부와는 상관없다.
- 우회: 책 상세 › `책 정보 고치기` › `표지 다시 찾기`(docs/TROUBLESHOOTING.md).

## Bigme B7 Pro 사용자 댓글 (2026-10-26, Reddit Dapper-Researcher299)

- **기기**: Bigme B7 Pro, 안드로이드 14(사용자가 알려 줌), 펌웨어 `Bigme_V1.020260716`(아래 기기 정보 사진). 영상으로는 약 7인치, 한쪽 넓은 테두리에 쪽 넘김 단추 둘.
- **증상**: Mihon 으로 만화를 읽어도 들어오지 않고 홈에 `Auto-add has stopped`(자동 추가가 멈춰 있습니다) 창. 홈에는 KOReader 책(표지·진행률)과 `Today 5 min` 이 보여 오늘 읽은 시간은 됐다.
- **해결(우회)**: 접근성에서 Pebble Desk 를 껐다 켜니 자동 추가가 된다(새 만화·이미 읽던 만화 모두). 그 전에는 접근성 스위치가 켜져 있어 그대로 두었다고 한다.
- **원인 (미확인)**: 서비스가 접근성에 켜진 채 붙지 않은 상태. 아래 기기 정보 사진으로는 1.6.1 업데이트와 앱 오류가 같은 때(09:48) 난 뒤로 다시 붙지 않았다. 에뮬레이터에서는 앱 오류(`am crash`, 안드로이드 11·14) 뒤에도, 앱 안 업데이트(안드로이드 14) 뒤에도 저절로 다시 붙었고, 앱 끄기→켜기(`pm disable-user`·`enable`, 안드로이드 11)는 멈춘 채 남았다(DESIGN.md 멈춤 창). 이 기기에서 왜 다시 붙지 않았는지는 모른다.
- **기기 정보 사진 (2026-10-26 13:59, Pebble Desk 1.6.1, 접근성 껐다 켜기 전에 찍은 것으로 보인다 (추정))**:
  - 기기: `HiBreak (Smartphone) · Bigme · Bigme · Android 14 (API 34) · Other` · 화면 1264×1680 · 앱 354dpi · 571×759dp · 펌웨어 `Bigme_V1.020260716`. B7 Pro 인데 모델 이름은 `HiBreak` 로 나온다(기기 종류는 기타).
  - 시스템 속성: `ro.vendor.xrz.default_contrast_level=20`·`ro.vendor.xrz.support_eink_animation`(XRZ 전자잉크 플랫폼), `screen_brightness=103`, 조명 `LightBed_*`·`LightDay_*`·`LightNight_*`·`LightMode=2`(조명 키, 아직 Device 에 없다).
  - 권한: 자동 추가 켜짐 · 읽은 시간 켜짐 · 기본 홈 켜짐.
  - 자동 추가 서비스: 마지막 연결 `10/26 09:46` · 끊김 `-` · 끊긴 횟수 0. 앱이 끝난 까닭(안드로이드 기록, 새것부터) `10/26 09:48 CRASH · 09:48 PKG_UPDATED`. 배터리 최적화 제외 꺼짐 · 대기 그룹 ACTIVE · 백그라운드 제한 꺼짐. 이 앱 이름이 든 기기 설정 키 `app_bleach_filter`(앱마다 거는 명암 필터 목록으로 보인다, 크레마의 bleach 와 비슷 (추정)).
  - 마지막으로 누른 버튼: 키 `10021`(이름도 10021) · scan 5. 쪽 넘김 단추로 보인다(안드로이드 기본 키가 아닌 기기 고유 키 번호).
- **읽은 것**: 09:46 에 서비스가 붙은 뒤 09:48 에 앱이 업데이트(PKG_UPDATED, 1.6.1 로 올린 때로 보인다)와 앱 오류(CRASH)로 끝났고, 그 뒤로 다시 붙은 기록이 없다(붙으면 연결 때가 바뀐다). 그래서 업데이트 직후부터 멈춰 있다가 껐다 켜서 다시 붙은 것으로 본다 **(추정: 사진이 껐다 켜기 전이라면)**. 업데이트 뒤에는 안드로이드가 서비스를 다시 붙이는데, 같은 때 난 앱 오류와 관계가 있을 수 있다 **(미확인)**. 어디서 난 오류인지는 안드로이드 기록에 까닭만 남아 모른다. 기기의 앱 정리·얼리기(PKG_STATE·USER_REQ·FREEZER)는 기록에 없다.
- **재현 시도 (2026-10-26, `Tango_A14` 에뮬레이터, 안드로이드 14)**: 배포판 1.6.0 을 깔고 접근성·사용 기록을 켠 뒤 홈의 `새 버전이 나왔습니다` › `설치` › 알 수 없는 앱 설치 허용 › 시스템 `업데이트` 로 1.6.1 을 받았다. 업데이트 뒤 서비스가 바로 다시 붙었고 앱 오류는 없었다(앱이 끝난 까닭은 `OTHER`(INVALID START) 하나, Dapper 의 `PKG_UPDATED · CRASH` 와 다르다). `am crash` 로 앱 오류를 내도 서비스는 저절로 다시 붙었다. 재현 안 됨: 이 기기에서 무엇이 오류를 냈는지는 오류 내용이 있어야 안다.
- **읽은 시간 (2026-11-02, 1.7.3 기록 보기 사진, ISSUES #13)**: 읽는 중에도 몇십 초마다 화면이 꺼지고(`SCREEN_NON_INTERACTIVE`, 그림은 남는다) 쪽을 넘기면 켜진다. 그동안 KOReader 는 멈추지 않아 사용 기록에 `MOVE_TO_BACKGROUND`·`FOREGROUND` 가 없다(잠금 화면도 없다). 표준 안드로이드·에뮬은 꺼질 때 액티비티가 멈춘다(위 안드로이드 8 절). 그래서 읽는 화면이 내려가지 않은 채 켜지면 이어 세게 했다(DESIGN.md 읽은 시간) 1.7.4 에서 고쳤고, 사용자가 업데이트 뒤 읽은 시간이 맞다고 했다(2026-11-02).
- **다음**: 1.6.2 부터 기기 정보에 `마지막 앱 오류`(예외 이름·이 앱 코드 자리)가 나오고 기록 보기에도 한 줄 남는다(`CrashLog`). 다시 멈추면 껐다 켜기 전에 기기 정보 사진을 부탁한다.

## Bigme B7 Pro 거꾸로 들면 새 화면마다 뒤집혀 보인다 (2026-10-29, Reddit Dapper-Researcher299)

- **근거**: 영상 2개(캡처로 받음)와 기기 정보 사진 1장(Pebble Desk 1.6.6, 거꾸로 든 채). 같은 사용자의 페이지 버튼 댓글에 이어 왔다.
- **증상**: 기기를 위아래 뒤집어 들면(180°, 사용자는 손을 자주 바꿔 든다) 새 화면을 열 때마다 화면이 1~2초 거꾸로 보였다가 돌아온다. 모든 앱·독서 기록을 열 때, 홈으로 돌아갈 때. 똑바로 들면 괜찮고 페이지 버튼은 방향을 따라 바뀐다. 첫 영상에는 회전 뒤 모든 앱이 왼쪽으로 밀리고 오른쪽에 잔상 띠가 남아 손으로 다시 그려야 했는데, Bigme 전자잉크 센터에서 갱신 방식을 낮추니 이것은 없어졌다. 거꾸로 보이는 것은 갱신 방식과 상관없이 남는다.
- **사용자가 확인한 것**: KOReader·Mihon 에서는 새 화면을 열어도 안 생긴다. **Niagara 런처에서도 생긴다**(Pebble Desk 는 새 화면을 더 자주 열어 더 거슬린다).
- **기기 정보 사진 (2026-10-29)**: 기기·펌웨어는 10-26 과 같다. 자동 추가 마지막 연결 `10/29 11:12` · 멈춤 없음. 앱이 끝난 까닭 `10/29 11:10 CRASH · 11:10 PKG_UPDATED`(1.6.6 업데이트, 이번에는 2분 뒤 다시 붙었다) · 마지막 앱 오류 `-`. 사진에서도 내용이 왼쪽으로 밀려(`evice info`) 오른쪽에 빈 띠가 보인다. 회전 직후 덜 갱신된 장면인지, 180° 에서 비워 둘 자리를 돌리지 않은 것인지는 **(미확인)**(사진 왼쪽이 잘려 찍혔을 수도 있다).
- **이 앱 쪽**: 전자잉크 갱신 명령을 보내지 않고(뷰를 다시 그릴 뿐), 매니페스트·코드 어디서도 방향을 정하지 않는다(`screenOrientation`·`configChanges` 없음, 회전을 보는 곳은 크레마 전용 `Crema.reserveHiddenBottom` 뿐). 창 전환 애니메이션·미리 보기 창은 이미 끈다(`windowAnimationStyle @null`·`windowDisablePreview`·`overridePendingTransition(0, 0)`). cutout·insets 도 다루지 않는다.
- **원인 (추정)**: 180° 에서 새 창(액티비티)이 뜰 때 펌웨어가 돌리지 않은 첫 장면을 그린다. 액티비티 하나 안에서 화면을 바꾸는 앱(KOReader·Mihon 둘러보기)은 안 생기는 것과 맞다. Bigme 펌웨어는 회전 버그가 잦다(eWritable 펌웨어 4.6 리뷰: 노트 앱 필기가 180° 로 뒤집힘, 여러 판의 노트에 '일부 상황의 회전' 고침). 같은 증상의 보고는 찾지 못했다.
- **다른 런처 (2026-10-29 코드 확인)**: 180° 를 따로 다루는 런처는 없다. Niagara 1.16.35(디컴파일): 휴대폰 크기(`is_tablet` = sw600dp 미만)면 홈·설정을 `NOSENSOR`, `Always allow landscape mode` 를 켜면 `UNSPECIFIED`. inkOS: 액티비티 하나 + 프래그먼트 14개, 태블릿이 아니면 `PORTRAIT`(거꾸로 들어도 돌지 않음). Olauncher·mLauncher: 액티비티 하나, 방향 정하지 않음. KISS: 기본 `USER_PORTRAIT`. Launcher3(Lawnchair `RotationHelper`): 휴대폰은 `NOSENSOR`, 회전 허용이면 `UNSPECIFIED`, **전환 중에는 `LOCKED`**.
- **할 수 있는 것 (나중에)**:
  1. 하위 화면(`EinkActivity`)을 `SCREEN_ORIENTATION_LOCKED`(그때 회전을 그대로 쓰고 다시 정하지 않는다)로 연다. 깜빡임이 '새 창에서 회전을 다시 정하는 것' 때문이면 없어지고, 펌웨어가 회전과 상관없이 첫 장면을 돌리지 않는 것이면 그대로다. 에뮬레이터로는 전자잉크 깜빡임을 볼 수 없어 사용자 기기에서만 가린다 → 아래 **시험 스위치**로 릴리스에 넣었다(따로 된 시험 APK 대신).
  2. 펌웨어 보기: 공개 다운로드는 없다. [bigme-ota-client](https://github.com/marek-vybiral/bigme-ota-client) 가 Bigme OTA 서버(`ereader.bigme.vip:8090`, 기기 업데이트 앱 `xSettings.apk` 의 요청을 흉내)에서 전체 OTA 주소를 받는데, 요청에 시리얼(`sn`)·기기 코드 `osName`(= `ro.build.versionCode`, HiBreak Pro BW 는 `B651 EEA_M2`)이 든다. B7 Pro 의 값은 공개된 곳에 없다(FCC ID `2A8EM-B7PRO`·모델 `GY-US-B7 Pro` 는 형식이 다르다). 기기 정보 시스템 속성에 `ro.build.versionCode` 를 보이면 다음 사진으로 얻는다. 받으면 `services.jar`(`DisplayRotation`)와 XRZ 전자잉크 쪽을 본다.
  3. 액티비티 하나로 바꾸기: 화면 13개·뒤로 가기·`Sheet`·결과 받는 화면을 다시 짜야 해 보류.
- **시험 스위치 (다음 판)**: 1 을 기기 정보 `시스템 화면 시험` 제목 줄 오른쪽 `회전 고정 꺼짐`(누르면 켜짐, 다음에 여는 화면부터)으로 넣었다. 홈을 뺀 화면은 매니페스트에서 `locked` 로 열리고, 꺼져 있으면 `EinkActivity.onCreate` 가 바로 `UNSPECIFIED` 로 푼다(연 뒤에 잠그면 시스템이 이미 회전을 정한 뒤라 매니페스트에 둔다). 켜면 그 화면이 떠 있는 동안 계속 잠근다(첫 그리기 뒤 푸는 Launcher3 방식은 원인이 '0° 로 정했다가 고침'이면 풀 때 같은 일이 생길 수 있어, 먼저 확실히 가르는 쪽으로). `Ocean5Pro_A14` 에뮬(2026-10-30): 꺼짐이면 하위 화면에서 `user_rotation` 을 바꾸면 따라 돌고, 켜짐이면 새로 연 화면(설정)은 돌지 않으며 뒤로 가 꺼짐 때 연 화면으로 돌아오면 따라 돈다. 180° 에서 열면 180° 그대로 열린다. 같은 판에 2 를 위해 시스템 속성 맨 앞에 `ro.build.versionCode` 를 보인다.
- **다음**: 다음 판이 나오면 사용자에게 `회전 고정` 을 켜고 거꾸로 든 채 모든 앱·독서 기록을 열어 보게 하고, 기기 정보 사진(versionCode)을 부탁한다. 다른 기기에서도 그런지 물었다(답 기다림). 거꾸로 든 채 Mihon 에서 만화를 열 때(읽는 화면은 따로 된 액티비티)도 그런지 물으면 '액티비티가 바뀔 때'인지 굳힐 수 있다.

## Bigme HiBreak 사용자 이슈 (2026-10-27, GitHub #6 abhifx)

- **근거**: 홈 캡처 1장(720×1440)과 독서 기록 시안 1장(사용자가 고친 그림, 실제 화면을 바탕으로 한 것으로 보인다). 기기 정보 사진은 없다. 판·안드로이드 **(미확인)**.
- **증상**: 홈 하단 `All apps ›` 위에 `Today 9h 27m · day 3` 이 겹친다. 독서 기록은 `Oct goal met 4 / 9 …` 처럼 잘리고 이번 주 읽은 책 이름이 `Fury In …` 로 잘린다. 같은 이슈의 독서 통계 따로 보기·음악(오디오북·TTS) 위젯 요청은 기능 요청이라 여기 적지 않는다.
- **읽은 것**: 하단 줄이 97px(56dp)이라 1dp ≈ 1.73px(약 277dpi), 앱 폭은 약 **416dp** 다(앱이 맞춘 대로면 201dpi·572dp). 여백도 70px(40dp)로 맞다. 그래서 **기기가 앱의 밀도를 덮어쓴 것으로 본다 (추정)**(BOOX 앱별 DPI 처럼). 같은 Bigme 의 B7 Pro(위)는 354dpi 가 먹혔다. 글꼴 배율은 1.1 정도(`All apps ›` 24sp 의 대문자 높이 33px). 홈에 `화면이 크게 보입니다` 창이 떴었는지는 **(미확인)**.
- **에뮬레이터**: `HiBreak_A11`(안드로이드 11, 720×1440, 320dpi, 내비 바 없음). 덮어쓴 밀도는 앱이 정한 밀도 대신 277 을 쓰는 시험용 한 줄(커밋하지 않음)과 기기 글꼴 1.1 로 흉내 내면 홈 캡처와 픽셀까지 거의 같다.
- **고침(다음 판)**: 하단 가운데 글자는 양쪽에 닿으면 둘 사이로 옮기고 줄이며, 바형 화면은 `오늘` 을 뺀다. 독서 기록은 자리가 모자라면 제목 줄 링크 글자를 줄이고 연속일 줄을 간결하게 두며, 바형·좁은 화면은 합계를 한 줄 세 칸으로 하고 그 아래 책을 전체 폭으로 둔다(페블은 그대로, DESIGN.md).
- **다음**: 기기 정보 사진으로 앱 dpi·dp 크기와 `intended …dpi not applied` 를 확인한다. 덮어쓴 것이 맞으면 Bigme 의 앱별 DPI 설정 자리를 찾아 TROUBLESHOOTING.md 에 더한다.

## Yeoodop YMP-A1 · Diofox M12 사용자 메일 (2026-10-29, Dirk)

- **근거**: 메일과 홈 캡처 1장(`android13.png`, 720×1280). 기기 정보 사진은 없다. MP3 플레이어로 팔린 안드로이드 13 LCD 소형 기기(4인치급), 화면 아래 홈 단추. 같은 사람의 Bigme B7 Pro(안드로이드 14)·Viwoods AI Paper Reader(16)·TCL 50 Pro NXTPAPER 5G(16)에서는 아래 두 문제가 없다.
- **캡처에서 본 것**: 설정 `기기 상단 바` 켜짐(홈 상단 두 줄 없음)·색 컬러. 맨 아래 약 40px 가 검은 띠(안드로이드 내비게이션 바 자리). 함께 읽는 책 셋 가운데 제목이 두 줄인 둘은 앱 이름·진행률 줄이 없고, 한 줄인 `Cell Fusion` 은 `KOReader · 0% read` 가 보인다.
- **에뮬레이터**: `Yeoodop_A13`(`system-images;android-33;google_apis;arm64-v8a`, 720×1280, 320dpi, `hw.mainKeys=no` 로 내비게이션 바 있음, `hw.keyboard=yes`). 기기 상단 바·컬러를 켜고 기기 글꼴 1.15 로 두면 캡처와 같다(1.0 이면 제목이 한 줄에 들어 진행률이 보인다).
- **원인**: 검은 띠는 테마에 내비게이션 바 색이 없어 안드로이드 기본 검은색. 진행률 빠짐은 홈 칸 높이를 기준 글자(기기 글꼴 크기 빼고)로 재는데 기기 글꼴이 크면 두 줄 제목 아래 줄이 칸을 넘쳐 그리지 않는 것으로 본다 **(Dirk 기기의 글꼴 크기는 미확인)**.
- **고침(다음 판)**: 내비게이션 바를 흰 바탕·어두운 단추로. 함께 읽는 책의 앱 이름·진행률 글자 칸을 칸 폭으로(글자 크기가 소수점이면 마지막 글자 끝이 잘렸다: 앱 글자 90% 의 `15% read` 의 d). 넘칠 때 제목을 한 줄로 줄이는 것은 시험해 보고 넣지 않았다(앱 글자를 90% 로 낮추면 보인다, TROUBLESHOOTING.md).
- **다음**: Dirk 에게 기기 글꼴 크기와, 앱 글자 90% 에서 진행률이 보이는지 확인을 받는다.

## 안드로이드 8 (Oreo, API 26·27) 지원 조사 (2026-10-25)

1.6.0 까지 minSdk 28 이라 8.x 기기(InkPalm 5 등)에는 깔리지 않는다. **결론: 지금은 지원하지 않는다**(2026-10-25). 8 기기 사용자 보고가 없고, 기기가 적고(Likebook P6·InkPalm 5 등 2018~2021년), 리디(minSdk 29)·이지뷰어(29)·킨들(30)·북커스(28)·밀리 휴대폰판(29)은 지금 판이 8 에 깔리지 않는다(교보도서관·밀리 e-ink·YES24·알라딘·교보eBook·문리더·KOReader 는 23~26 이라 깔린다). 쓰는 사람이 생기면 아래대로 한다.

지원하려면 고칠 것:

- **서명**: APK 가 v3 만 서명돼 있어 8 은 서명을 읽지 못한다(v1·v2 만 본다, 8.0 에뮬에서 `INSTALL_PARSE_FAILED_NO_CERTIFICATES`). 길은 둘이다.
  - 이력을 두고 v2 를 켠다: apksigner 가 v2 에는 이력의 가장 오래된 키(디버그 키)를 요구한다(`the oldest signer in the SigningCertificateLineage is missing`). 8 에서는 앱 서명이 디버그 키로 보이고 업데이트도 디버그 키로 이어진다. 8.0 설치·11 에서 1.6.0 위 덮어 설치 확인.
  - 이력을 빼고 v2·v3 를 릴리스 키로만: 10·11·13 에서 1.6.0 위에 덮어 깔리고(이력만 빈다) 8.0 에서도 릴리스 키로 보인다. 1.4.2 이하(디버그 키)에서는 올라올 수 없다. 1.6.0 까지의 앱 안 업데이트는 깔린 이력이 새 APK 이력의 앞부분이어야 받으므로(`Update.continuesSigning`) 거부한다 → 먼저 한 판에서 확인을 "지금 서명 키가 같으면 받는다"로 넓히고 그다음 판에서 이력을 뺀다(그 사이 판을 받지 않은 사람은 한 번 직접 설치).
- **API 28 부터 있는 것**(minSdk 26 lint 의 NewApi 16곳): 업데이트 확인의 `signingInfo`·`GET_SIGNING_CERTIFICATES`(8 은 `GET_SIGNATURES` 로), `longVersionCode`(Update·Bookers2), 굵기 숫자 `Typeface.create(…, 800/500, …)`(Ui, 8 은 굵게·보통만), 토큰 `Handler.postDelayed`(Apps), 기기 정보의 대기 버킷·백그라운드 제한.
- 서재 표지 자르기는 9·10 처럼 되지 않는다(화면 캡처는 11 부터).

**읽은 시간 — 화면이 꺼질 때**: 9 부터는 `SCREEN_NON_INTERACTIVE` 로 읽기를 끊는데(`Reading.kt` `Counter.feed`), 이 이벤트는 8 에 없다. 에뮬레이터 `CremaPebble_A8_27`(8.1)·`CremaPebble_A8_26`(8.0)(페블 규격, google_apis arm64)에서 앱(설정)을 연 채 화면을 90초 끄고 켰다. 전원 키·시간 초과, 잠금 화면 있음·없음 모두에서 끌 때 액티비티가 멈추고(`am_on_stop_called … sleeping`) 사용 기록에 `MOVE_TO_BACKGROUND` 가, 켤 때 `MOVE_TO_FOREGROUND` 가 남았다. 앱은 같은 클래스의 `MOVE_TO_BACKGROUND` 로 읽는 화면 구간을 닫고 켤 때 다시 열므로, 꺼진 동안은 읽은 시간에 들어가지 않는다. 기기마다 다른 잠자기 화면(e-ink 대기 그림)에서도 같은지는 실기기에서 **(미확인)**.

## Pebble Desk 에 넣은 것

| 내용 | 기기 구분 | 확인 |
|---|---|---|
| 상단 줄을 누르면 `com.epd.drop_down` 과 `com.haoqing.action.QUICK_SETTINGS`(`com.android.systemui` 지정)를 둘 다 보낸다(`Device.openQuickSettings`). 받는 쪽이 없는 방송은 아무 일도 하지 않는다 | 가리지 않음 | 페블은 그대로 열림. E6 는 메이북 방송으로 패널이 열림(사용자 확인). AiPaper Mini 는 방송으로는 안 열리고 기기 정보의 `안드로이드 ›` 로 열린다. 원래 기기도 끌어내리기로 여니 상단 줄 누르기는 따로 맞추지 않고 끌어내리기에 맡긴다 |
| 설정 › 화면 › `기기 상단 바 · 홈에서도 보이기`(처음은 꺼짐): 켜면 홈에서도 안드로이드 상단 바를 숨기지 않고, 겹치지 않게 홈 상단(로고·시계·상태 줄)을 뺀다(`HomePrefs.showStatusBar`, `HomeActivity.applyStatusBar`) | 가리지 않음(기기를 보고 켜지 않는다) | 교보 sam 7 처럼 빠른 설정이 상단 바 버튼뿐인 기기용. 실기기 확인 **(아직)** |
| 기기 종류 `Device.kind`: 크레마(`Build.DEVICE`) → 메이북(`ro.haoqing.brand` 있음) → 기타. 모델명을 몰라도 메이북 전 기종을 잡는다. 설정 › 기기 정보의 `기기 종류` | — | 에뮬레이터에 `ro.haoqing.brand` 를 넣어 `메이북` 확인. E6 실기기에서도 `메이북` |
| 조명 켜짐: 크레마 `screen_brightness`·`warm_light` > 0, 메이북 `isLightOn == "true"`(꺼도 밝기 값은 남는다), 그 밖의 기기는 `EinkLightStep`(iReader 조명 단계, 끄면 0. 키가 없으면 0 으로 읽혀 꺼짐) > 0. 그 키를 지켜보다 바뀌면 상태 줄을 다시 그린다 | 기기 종류. `EinkLightStep` 은 제조사로 가르지 않는다 | 에뮬레이터에서 `isLightOn` 을 바꿔 아이콘이 나타나고 사라지는 것 확인. E6 는 `isLightOn=false` 인 사진과 ☀ 가 보이는 홈 사진이 섞여 있어 **확인 필요** |
| ~~페이지 버튼~~(1.5.5 에서 지움): 1.5.4 까지는 메이북 키 291(위)·292(아래)와 표준 `PAGE_UP`·`PAGE_DOWN` → `EinkActivity.onSwipe`. 기기마다 키가 달라 키 처리를 모두 지웠다. 그 뒤 사용자가 눌러 정하는 키로만 넘기게 했다(설정 › 화면 › 페이지 버튼, 기본 키 없음, `PageKeys`, 이슈 #9) | 가리지 않음 | 표준 PAGE 키는 이동 키라, 터치 모드를 벗어나면서 포커스 받을 뷰가 생기면 첫 누름을 안드로이드가 먹는다(`ViewRootImpl.checkForLeavingTouchModeAndConsume`). 에뮬레이터 모든 앱 화면에서 그랬다(2026-10-18 다시 확인: 홈·모든 앱·자동 추가 안내, 2026-10-28 페이지 버튼으로 정한 `93` 도 같다). 화면의 초점을 막지 않고 그대로 둔다(DESIGN.md 페이지 버튼) |
| 화면 폭 572dp 맞춤: 짧은 변이 572dp 가 되도록 앱 밀도를 다시 정한다(`Ui.designDensityDpi`, `EinkActivity.attachBaseContext`). E6 는 320 → 300dpi, 572×772dp 로 페블과 같은 물리 크기. 기기 정보의 `화면` 줄에 기기 값과 `앱 기준` 값을 함께 보인다 | — | E6 크기 에뮬레이터(536×724dp)에서 1.2.0 은 책 3권일 때 **함께 읽는 책 줄 아래 32dp 가 잘렸고**(큰 표지가 최소 136dp 에 걸려 책 자리가 58dp 넘침), 상단 바 54dp 를 흉내 내면(`wm size 1072x1388`) 설정 마지막 줄이 40dp 잘렸다. 밀도 맞춤 뒤 셋 다 들어감(설정은 상단 바 54dp 를 빼도 약 714dp 라 708dp 가 여유 6dp 로 들어간다). 페블 실기기 **(미확인)**. AiPaper Mini(1440px → 403dpi, 572×762dp) 실기기 홈 사진에서 그대로 들어감. Minimal Phone(4.3", 600px → 168dpi)은 거꾸로 밀도를 낮춰 모든 것이 페블의 약 0.7배로 작아진다(아래 기기 절) |
| 기기 정보 화면(사진으로 찍어 보내는 진단). 시스템 속성·설정 값은 아는 키에 더해 `getprop`·`Settings.System` 전체에서 전자잉크·조명 낱말이나 제조사 이름이 든 키를 찾아 있는 것만 보인다(1.2.1 까지는 크레마·메이북 키만 보여 AiPaper Mini 의 고유 값이 안 나왔다). 제조사·브랜드 낱말 중 `phone` 같은 흔한 낱말은 쓰지 않는다(Minimal Phone 에서 전화 관련 기본 키만 잔뜩 나왔다) | — | 에뮬레이터(M6C 이름)에서 한 화면에 들어감, 볼륨 키가 `24 KEYCODE_VOLUME_UP` 로 기록됨. E6 실기기에서도 상단 바 아래에 다 들어감 |
| 안드로이드 13+ **제한된 설정** 안내: ⑭ 시작하기 1쪽의 `켜는 방법` 단계(접근성에서 눌러 보기 → 막힌 창 닫기 → `앱 정보 열기` › ⋮ › 제한된 설정 허용 → 다시 켜기)와 설정 › 독서의 자동 추가 창(늘 `앱 정보 열기`). 앱 정보는 `ACTION_APPLICATION_DETAILS_SETTINGS` + extra `uId`(안드로이드 13 설정 앱 `AppInfoDashboardFragment.getUid()` 는 인자 `uid` 나 extra `uId` 만 읽어, 없으면 -1 로 묻다 실패해 ⋮ 메뉴가 빠진다) | 안드로이드 13+ | 에뮬레이터 API 33(`CremaPebble_A13`): 파일 앱으로 깐 APK 가 `packageSource=3`·`ACCESS_RESTRICTED_SETTINGS: deny` 로 막힘 → 막힌 창 → 시작하기에 안내·`앱 정보 열기` → ⋮ `Allow restricted settings` → 접근성 켜짐 → `켜짐 ✓`. `uId` 없이 열면 ⋮ 가 없다(설정 › 앱 목록에서 들어가면 있다). iReader(안드로이드 14) **(미확인)** |
| BOOX 앱 얼리기: `com.onyx`(BOOX 런처)가 있으면 BOOX 가 얼린 앱(위 판정)의 실행 화면도 앱 목록에 넣는다(앱이 스스로 끈 실행 화면은 빼고). 누르면 `onyx.action.open_frozen_app` 을 `com.onyx` 로 보내 BOOX 런처가 풀고 연다(`AppStore.openFrozen`, 재시도·안내 창). 방송을 받도록 BOOX 런처를 띄워 둔다(`Device.keepBooxLauncher`). 숨긴(hidden) 앱도 지워진 앱으로 보지 않는다 | `com.onyx` 설치 여부 | 에뮬레이터 API 30 + 가짜 `com.onyx`: 얼린 시험 앱이 자주 쓰는 앱에 남음, 시스템 앱(Calendar)·실행 화면만 끈 앱은 안 나옴, 가짜 런처가 없으면 전처럼 빠짐. 누르기: 열림 → 방송 1번 / 안 열림 → 재시도 뒤 안내 창 / 런처 죽은 채로 → 다시 떠서 열림 / 풀렸는데 안 열림 → 직접 열림. 런처를 끄고 홈 복귀·재부팅(기본 홈 Pebble Desk)·화면 끄고 켜기 → 런처가 다시 뜸. BOOX 실기기 **(미확인)** |
| BOOX 얼리기 대상 안내: 이 앱이 BOOX 자동 얼리기 대상이면(`Device.booxAutoFreeze` = 펌웨어 `EInkHelper.getAutoFreezeApps()` 에 이 앱이 있음) ⑭ 시작하기 자동 추가 단계 맨 앞에 얼리기에서 빼기(`얼리기 설정 열기 ›`), 홈 `멈춰 있습니다` 창에 원인 단락과 `얼리기(freeze) 설정 열기`, 기기 정보 권한 줄에 `BOOX 얼리기(freeze) 켜짐/꺼짐/?` | 함수가 있으면(없거나 막히면 null: 안내 없음, 기기 정보는 BOOX 런처가 있을 때만 `?`) | `Palma_A11` 에뮬레이터 + 디버그 소스의 가짜 `EInkHelper`·가짜 `com.onyx` 로 세 화면 확인(2026-10-20). Palma 실기기 **(미확인)** |

넣지 않은 것: `com.haoqing.action.FULL_REFRESH`(잔상 제거 버튼).

- **안드로이드 기본 빠른 설정 펼치기**: `getSystemService("statusbar")` 의 `expandSettingsPanel()`(숨은 함수, `EXPAND_STATUS_BAR` 는 일반 권한)를 일반 앱(릴리스, targetSdk 34)이 리플렉션으로 불러 안드로이드 11 에뮬레이터와 페블에서 열렸다(페블은 크레마 빠른 설정 앱이 아니라 밝기·음량·대비가 있는 시스템 알림창). 메이북 빠른 설정은 안드로이드 기본 패널을 고친 것이라 메이북 방송 대신 이 방법으로도 열릴 가능성이 크다 E6 실기기에서 열렸다(메이북 방송과 같은 패널). Viwoods AiPaper Mini 에서도 제어 센터가 열렸다. 지금은 기기 정보의 시험 버튼에만 쓴다.

## 에뮬레이터로 화면 크기 보기

- 펌웨어 자체는 에뮬레이터에서 부팅되지 않는다(Rockchip 커널·HAL). 펌웨어 앱만 옮겨 심는 것도 수정된 `framework.jar`(`android.util.haoqing`, `EinkManager`)·플랫폼 서명에 묶여 있어 사실상 시스템 이미지를 통째로 바꾸는 일이다. 전자잉크 갱신·대비는 어차피 재현되지 않는다.
- 화면 규격만 맞춘 AVD 로 배치를 본다. 예: `MeebookE6_A11` = `system-images;android-30;google_apis;arm64-v8a`, `hw.lcd.width=1072`, `hw.lcd.height=1448`, `hw.lcd.density=320`, `hw.mainKeys=yes`(내비게이션 바 없음). 페블은 `CremaPebble_A11`(같은 이미지, `hw.lcd.height=1456`, `hw.lcd.density=300`, `hw.mainKeys=yes`). AVD 는 실제 기기와 같게 만든다(안드로이드 버전·해상도·밀도·내비게이션 바 없음). 예전 `CremaPebble`(안드로이드 15·1448px·내비게이션 바 있음)은 앱 높이가 실기기보다 45px+ 작아 홈 책 자리가 잘려 보인다.
- 깔고 홈으로: `adb install -r …apk`, `adb shell cmd package set-home-activity com.woody.pebbledesk/.HomeActivity`.
- **기기 이름을 메이북으로 바꾸기**(앱의 `Build.*`·`Device.kind` 시험용). 에뮬레이터 `-prop` 으로는 `ro.product.*` 가 바뀌지 않는다. 시스템을 쓸 수 있게 켜서 `build.prop` 을 고친다.
  ```bash
  emulator -avd MeebookE6_A11 -writable-system
  adb root && adb remount && adb reboot        # 처음 한 번은 다시 켜야 덮어쓰기가 된다
  adb root && adb remount
  adb shell sed -i -e 's/^ro.product.product.model=.*/ro.product.product.model=M6C/' \
      -e 's/^ro.product.product.device=.*/ro.product.product.device=M6C/' \
      -e 's/^ro.product.product.manufacturer=.*/ro.product.product.manufacturer=Haoqing/' \
      -e 's/^ro.product.product.brand=.*/ro.product.product.brand=Haoqing/' /product/build.prop
  adb shell 'echo ro.haoqing.brand=haoqing-meebook >> /product/build.prop'
  adb reboot                                   # 이후에도 -writable-system 으로 켠다
  ```
  조명은 `adb shell settings put system isLightOn true|false` 로 흉내 낸다.
- 이름만 바뀔 뿐 SystemUI 는 일반 안드로이드 것이라, 메이북 방송(`QUICK_SETTINGS`·`FULL_REFRESH`)을 받는 쪽이 없다. 빠른 설정·전자잉크 동작은 에뮬레이터로 시험할 수 없다.

## 새 기기를 조사할 때

1. 앱 설정 › 기기 정보 사진. 버튼마다 눌러 키 번호를 받고, 기본 런처에서 빠른 설정을 열었다 돌아와 최근 화면을 찍고, 빠른 설정 시험 세 버튼 중 무엇이 열리는지 듣는다.
   - adb 가 있으면 빠른 설정을 연 직후 `adb shell dumpsys activity broadcasts` 의 `Historical broadcasts` 에서 오간 방송 이름·보낸 앱·받은 앱을 볼 수 있다(페블: `com.epd.drop_down` → `com.inno.quicksetting`). 같은 출력의 등록된 수신기 목록에는 SystemUI 등이 받는 방송 이름이 다 있다.
2. `adb shell getprop` 전체, `settings list system`, `pm list packages -f`, `wm size`·`wm density`.
3. 펌웨어 또는 기기의 `framework.jar`·`services.jar`·SystemUI·설정 앱을 jadx 로 풀어 본다:
   - 빠른 설정을 여는 방법(SystemUI 가 받는 방송, `isProtectedBroadcast` 예외)
   - 대비·밝게 바꾸기(bleach)가 앱 그림에 어떻게 걸리는가
   - 앱을 강제 종료하는 경로(`forceStopPackage`), 백그라운드 제한
   - 기본 홈을 막거나 되돌리는 코드
   - 페이지 버튼 키 코드(키 배치 파일 `usr/keylayout/*.kl`, `KeyEvent`)
4. 화면 규격 AVD 에 Pebble Desk 를 깔아 배치를 본다.
5. 이 문서의 **한눈에** 표에 열을 더하고 기기 절을 쓴다.
