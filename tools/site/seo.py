#!/usr/bin/env python3
"""홈페이지의 검색·공유 정보를 채운다: <head> 의 제목·설명·대표 주소·언어판·공유 카드·아이콘, 구조화 데이터(JSON-LD),
화면에 보이는 '자주 묻는 질문'(같은 글로 FAQPage), 방문 통계(Cloudflare Web Analytics), robots.txt · sitemap.xml · llms.txt.

    python3 tools/site/seo.py      # site/index.html · site/en/index.html 을 고쳐 쓴다(다시 돌려도 같다)

글은 여기서 고친다(홈페이지에서 직접 고치면 다음에 돌릴 때 되돌아간다). 사실(안드로이드 9+ = minSdk 28,
인터넷은 표지 검색(한국어 제목은 YES24, 그 밖은 Apple Books·Open Library)·GitHub 새 버전 확인·만화 표지 풀기 도구 받기뿐,
기록은 기기 안)은 코드와 docs/DEVICES.md 에서 확인한 것. 바뀌면 같이 고친다. 판 번호·날짜는 app/build.gradle.kts 와 그 판의 git 태그에서.
주소 BASE 는 GitHub Pages(pebbledesk/pebbledesk.github.io) 기준.
"""
import json
import re
import subprocess

BASE = "https://pebbledesk.github.io/"
REPO = "https://github.com/hissinger/pebble-desk"
# Cloudflare Web Analytics(쿠키 없음): 방문 수·페이지·들어온 곳·나라·기기 종류. 토큰은 공개 값(페이지에 그대로 실린다)
CF_BEACON = "1896b35940664abaa335017941fc327d"
RELEASES = REPO + "/releases/latest"
LICENSE = REPO + "/blob/main/LICENSE"


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout.strip()


def tag_date(version):
    return git("for-each-ref", f"refs/tags/v{version}", "--format=%(creatordate:short)")


# 지금 판과 그 판을 낸 날(태그가 아직 없으면 판 번호를 바꾼 커밋의 날), 첫 판을 낸 날
VERSION = re.search(r'versionName = "([^"]+)"', open("app/build.gradle.kts").read()).group(1)
RELEASED = tag_date(VERSION) or git("log", "-1", "--format=%cs", "-G", "versionName", "--", "app/build.gradle.kts")
FIRST = tag_date("1.0.0")

PAGES = {
    "ko": {
        "file": "site/index.html", "url": BASE, "locale": "ko_KR", "img": "",
        "title": "Pebble Desk — 이북리더기 런처",
        # 네이버 서치어드바이저가 80자 안을 권한다
        "desc": "크레마·BOOX·메이북 같은 안드로이드 이북리더기용 무료 런처. "
                "이북 앱에서 편 책이 홈 화면에 들어오고, 책마다 읽은 시간이 남습니다.",
        "faq_title": "자주 묻는 질문", "cta": "APK 받기",
        "faq": [
            ("어떤 기기에서 쓸 수 있나요?",
             "안드로이드 9 이상의 이북리더기에서 씁니다."),
            ("무료인가요?",
             "무료이고 광고도 없습니다. 비상업적 용도로 자유롭게 쓸 수 있습니다(PolyForm Noncommercial 1.0.0)."),
            ("어떻게 설치하나요?",
             "GitHub 릴리스에서 APK 를 받아 기기에 옮기고 파일 관리자에서 눌러 설치합니다. 처음 한 번 '알 수 없는 앱 설치'를 허용합니다. "
             "그다음 Pebble Desk 설정 › 앱 관리 › 기본 홈 앱에서 홈 앱으로 고릅니다. 새 버전은 설정 › 정보 › 업데이트에서 받습니다."),
            ("접근성 권한은 왜 필요한가요?",
             "이북 앱에서 편 책을 알아보려고 씁니다. 지원하는 이북 앱의 화면만 받고, 다른 앱의 화면은 보지 않습니다. "
             "읽은 시간은 안드로이드의 '사용 기록 액세스'로 셉니다."),
            ("기록은 어디에 저장되나요?",
             "기기 안에만 저장되고 어디로도 보내지 않습니다. 인터넷은 표지 검색(한국어 제목은 YES24, 그 밖은 Apple Books·Open Library), "
             "새 버전 확인과 만화 표지 풀기 도구 받기(둘 다 GitHub)에만 씁니다. "
             "앱을 지우면 기록도 함께 지워집니다."),
        ],
        "features": ["이북 앱에서 편 책을 홈 화면에 자동으로 추가(표지·제목·진행률·반납일)", "책마다 읽은 시간, 하루 목표, 연속일",
                     "올해 읽은 책과 완독 기록(별점·감상, 다시 읽기 회차)", "스크롤 대신 쪽 넘김, 애니메이션 없음", "흑백·컬러 화면, 글자 크기·줄 간격"],
    },
    "en": {
        "file": "site/en/index.html", "url": BASE + "en/", "locale": "en_US", "img": "en-",
        "title": "Pebble Desk — E Ink launcher for Android e-readers",
        "desc": "A free, text-first launcher for Android E Ink readers like BOOX, Meebook, iReader, Crema and Bigme. "
                "Books you open in Kindle, Libby, KOReader and more appear on your home screen, and reading time is kept for every book.",
        "faq_title": "Questions", "cta": "Download APK",
        "faq": [
            ("Which devices does it work on?",
             "Android E Ink readers running Android 9 or later."),
            ("Is it free?",
             "Yes, and there are no ads. You can use it freely for noncommercial purposes (PolyForm Noncommercial 1.0.0)."),
            ("How do I install it?",
             "Download the APK from GitHub Releases, copy it to your device and tap it in a file manager. Allow “install unknown apps” the first time. "
             "Then set Pebble Desk as your home app in its Settings › Apps › Default home app. Later versions install from Settings › About › Update."),
            ("Why does it need the accessibility permission?",
             "To recognize the book you open in a reading app. It only receives the screens of supported reading apps and doesn’t look at any other app. "
             "Reading time comes from Android’s Usage access."),
            ("Where is my reading history stored?",
             "Only on your device; nothing is sent anywhere. The internet is used only to search covers (Apple Books and Open Library, or YES24 for Korean titles), "
             "to check for new versions and to download the comic cover unpacking tool (both from GitHub). "
             "Uninstalling the app deletes the history too."),
        ],
        "features": ["Books you open in reading apps are added to the home screen automatically (cover, title, progress, due date)",
                     "Reading time per book, daily goal and streak", "Books this year with finish history (ratings, notes, re-reads)",
                     "Pages instead of scrolling, no animations", "Black-and-white or color, adjustable text size and spacing"],
    },
}

# 기기 이름(화면에는 보이지 않는다: llms.txt, keywords 는 used 만). (한글, 영어, 상태)
# 상태: used = 실제로 쓰고 있는 기기(docs/DEVICES.md·ISSUES.md), android = 그 밖의 안드로이드 기기, no = 안드로이드 앱을 깔 수 없는 기기.
# llms.txt 는 used(쓴다)·android(안드로이드 9 이상인 기기면 쓸 수 있다, 옛 기기는 더 낮다)·no(쓸 수 없다)를 나눠 적는다. AI 가 킨들에서 된다고 답하지 않게.
# 시험해 보지 않은 기기를 '지원한다'고 쓰지 않는다
DEVICES = [
    # 크레마(YES24)
    ("크레마 페블", "Crema Pebble", "used"), ("크레마 S", "Crema S", "android"), ("크레마 모티프", "Crema Motif", "android"),
    ("크레마 그랑데", "Crema Grande", "android"), ("크레마 팔레트", "Crema Palette", "android"), ("크레마 카르타G", "Crema Carta G", "android"),
    ("크레마 카르타 플러스", "Crema Carta Plus", "android"), ("크레마 카르타", "Crema Carta", "android"), ("크레마 사운드업", "Crema Soundup", "android"),
    ("크레마 사운드", "Crema Sound", "android"), ("크레마 샤인", "Crema Shine", "android"),
    # 교보문고 sam · 리디페이퍼
    ("교보 sam 7 mono", "Kyobo sam 7 mono", "used"), ("교보 sam 7 비비드", "Kyobo sam 7 Vivid", "android"),
    ("교보 sam 7.8 플러스", "Kyobo sam 7.8 Plus", "android"), ("교보 sam 10 플러스", "Kyobo sam 10 Plus", "android"), ("교보 sam 12", "Kyobo sam 12", "android"),
    ("리디페이퍼 4", "RIDIPAPER 4", "android"), ("리디페이퍼 프로", "RIDIPAPER Pro", "android"), ("리디페이퍼", "RIDIPAPER", "android"),
    # 메이북
    ("메이북 E6", "Meebook E6", "used"), ("메이북 M6", "Meebook M6", "android"), ("메이북 M6C", "Meebook M6C", "android"),
    ("메이북 M7", "Meebook M7", "android"), ("메이북 M8", "Meebook M8", "android"), ("메이북 P78 Pro", "Meebook P78 Pro", "android"),
    ("메이북 P10 Pro", "Meebook P10 Pro", "android"),
    # 오닉스 BOOX
    ("BOOX 팔마", "BOOX Palma", "used"), ("BOOX 팔마 2", "BOOX Palma 2", "android"), ("BOOX 팔마 2 프로", "BOOX Palma 2 Pro", "android"),
    ("BOOX 리프", "BOOX Leaf", "used"), ("BOOX 리프2", "BOOX Leaf2", "used"), ("BOOX 리프3", "BOOX Leaf3", "android"),
    ("BOOX 리프3C", "BOOX Leaf3C", "android"), ("BOOX 포크3", "BOOX Poke3", "used"), ("BOOX 포크4", "BOOX Poke4", "android"),
    ("BOOX 포크5", "BOOX Poke5", "android"), ("BOOX 포크6", "BOOX Poke6", "used"), ("BOOX 페이지", "BOOX Page", "android"),
    ("BOOX 고 6", "BOOX Go 6", "android"), ("BOOX 고 7", "BOOX Go 7", "android"), ("BOOX 고 컬러 7", "BOOX Go Color 7", "android"),
    ("BOOX 고 10.3", "BOOX Go 10.3", "android"), ("BOOX 노트 에어", "BOOX Note Air", "android"), ("BOOX 노트 에어 4C", "BOOX Note Air4 C", "android"),
    ("BOOX 노트 에어 5C", "BOOX Note Air5 C", "android"), ("BOOX 노트 맥스", "BOOX Note Max", "android"), ("BOOX 탭 울트라", "BOOX Tab Ultra", "android"),
    ("BOOX 탭 울트라 C 프로", "BOOX Tab Ultra C Pro", "android"), ("BOOX 탭 미니 C", "BOOX Tab Mini C", "android"), ("BOOX 노바 에어", "BOOX Nova Air", "android"),
    # 아이리더
    ("아이리더 오션 5 프로", "iReader Ocean 5 Pro", "used"), ("아이리더 오션 5", "iReader Ocean 5", "android"), ("아이리더 오션 4", "iReader Ocean 4", "android"),
    ("아이리더 오션 3", "iReader Ocean 3", "android"), ("아이리더 탱고", "iReader Tango", "used"), ("아이리더 탱고 2S", "iReader Tango 2S", "android"),
    ("아이리더 네오 3 울트라", "iReader Neo 3 Ultra", "used"), ("아이리더 네오 3", "iReader Neo 3", "android"), ("아이리더 네오 2", "iReader Neo 2", "android"),
    ("아이리더 스마트 5", "iReader Smart 5", "android"), ("아이리더 라이트 3", "iReader Light 3", "android"),
    # 그 밖의 안드로이드 전자잉크 기기
    ("빅미 하이브레이크 프로", "Bigme HiBreak Pro", "android"), ("빅미 하이브레이크", "Bigme HiBreak", "android"), ("빅미 B7", "Bigme B7", "android"),
    ("빅미 B6", "Bigme B6", "android"), ("빅미 B751C", "Bigme B751C", "android"), ("빅미 잉크노트", "Bigme inkNote", "android"),
    ("하이센스 A9", "Hisense A9", "android"), ("하이센스 A9 프로", "Hisense A9 Pro", "android"), ("하이센스 A7", "Hisense A7", "android"),
    ("하이센스 A5", "Hisense A5", "android"), ("비우즈 AiPaper 미니", "Viwoods AiPaper Mini", "used"), ("비우즈 AiPaper", "Viwoods AiPaper", "android"),
    ("비우즈 AiPaper 리더", "Viwoods AiPaper Reader", "android"), ("모안 MIX7S", "Moaan MIX7S", "used"), ("미니멀 폰", "Minimal Phone", "used"), ("무디타 콤팩트", "Mudita Kompakt", "android"),
    ("라이크북 P78", "Likebook P78", "android"), ("라이크북 아레스", "Likebook Ares", "android"), ("무잉크 프로", "mooInk Pro", "android"),
    ("무잉크 플러스", "mooInk Plus", "android"), ("슈퍼노트 매타", "Supernote Manta", "android"), ("슈퍼노트 노마드", "Supernote Nomad", "android"),
    # 안드로이드 앱을 깔 수 없는 기기(또는 안드로이드 8 이하)
    ("킨들", "Kindle", "no"), ("킨들 페이퍼화이트", "Kindle Paperwhite", "no"), ("킨들 오아시스", "Kindle Oasis", "no"),
    ("킨들 스크라이브", "Kindle Scribe", "no"), ("킨들 컬러소프트", "Kindle Colorsoft", "no"), ("킨들 베이직", "Kindle Basic", "no"),
    ("코보 클라라", "Kobo Clara", "no"), ("코보 리브라", "Kobo Libra", "no"), ("코보 세이지", "Kobo Sage", "no"), ("코보 엘립사", "Kobo Elipsa", "no"),
    ("포켓북 버스", "PocketBook Verse", "no"), ("포켓북 에라", "PocketBook Era", "no"), ("포켓북 잉크패드", "PocketBook InkPad", "no"),
    ("톨리노", "Tolino", "no"), ("리마커블", "reMarkable", "no"), ("샤오미 잉크팜 5", "Xiaomi InkPalm 5", "no"),
]

TERMS = {
    "ko": ["이북리더기 런처", "이북리더 런처", "전자잉크 런처", "eink 런처", "e잉크 런처", "이잉크 런처", "이북리더 홈 앱", "크레마 런처", "독서 기록", "읽은 시간", "전자책 리더기"],
    "en": ["E Ink launcher", "e-reader launcher", "e-ink home screen", "Android e-reader", "reading tracker", "reading log"],
}

# 다른 런처 이름(검색용, '대안·비교'로만 쓴다. 관계가 있는 것처럼 쓰지 않는다): (한글, 영어)
LAUNCHERS = [("토탈 런처", "Total Launcher"), ("노바 런처", "Nova Launcher"), ("나이아가라 런처", "Niagara Launcher"), ("오런처", "Olauncher"), ("키스 런처", "KISS Launcher"),
             ("론체어", "Lawnchair"), ("스마트 런처", "Smart Launcher"), ("마이크로소프트 런처", "Microsoft Launcher"), ("액션 런처", "Action Launcher"),
             ("미니멀리스트 폰", "Minimalist Phone"), ("비포 런처", "Before Launcher"), ("미니멀 런처", "Minimal Launcher"),
             ("이잉크 런처", "E-Ink Launcher"), ("BOOX 런처", "BOOX Launcher"), ("크레마 기본 런처", "Crema Launcher")]

# keywords 에 넣는 기기 회사 이름(우리말로 흔히 부르는 이름 포함: BOOX = 부크·오닉스)
BRANDS = {"ko": ["크레마", "부크", "오닉스 BOOX", "메이북", "아이리더", "교보 sam", "빅미"], "en": ["Crema", "Onyx BOOX", "Meebook", "iReader", "Bigme"]}
# keywords 에 넣는 이북 앱(자동 추가를 지원하는 앱 가운데 많이 쓰는 것. 킨들은 앱만: 킨들 기기는 안드로이드 앱을 깔 수 없다)
KEY_APPS = {"ko": ["교보도서관", "밀리의서재", "리디", "YES24 전자도서관", "킨들 앱", "KOReader"], "en": ["Kindle app", "Libby", "KOReader", "Moon+ Reader", "Google Play Books", "Wattpad"]}


def keywords(lang):
    """이 언어의 말·회사·실제로 쓰는 기기·많이 쓰는 이북 앱, 30개 안팎(긴 목록은 검색엔진이 스팸으로 볼 수 있다. 기기 전체는 llms.txt 에)"""
    i = 0 if lang == "ko" else 1
    words = TERMS[lang] + BRANDS[lang] + [d[i] for d in DEVICES if d[2] == "used"] + KEY_APPS[lang]
    return ", ".join(dict.fromkeys(words))


FAQ_CSS = """  /* 자주 묻는 질문 */
  .faq { max-width: 720px; margin: 56px auto 0; text-align: left; }
  .faq div { border-top: 1px solid var(--line); padding: 20px 0; }
  .faq dt { font-weight: 600; font-size: 18px; margin: 0 0 6px; }
  .faq dd { margin: 0; color: var(--sub); font-size: 16px; text-wrap: pretty; }
"""


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def head(lang, p):
    other = "en" if lang == "ko" else "ko"
    o = PAGES[other]
    og = BASE + f"img/og-{lang}.png"
    icon = ("" if lang == "ko" else "../") + "img/"
    shots = [BASE + f"img/{p['img']}{d}-{s}.webp" for d in ("pebble", "bar") for s in ("home", "reading", "year")]
    data = {
        "@context": "https://schema.org",
        "@graph": [
            {"@type": "WebSite", "@id": BASE + "#website", "url": BASE, "name": "Pebble Desk", "inLanguage": ["ko", "en"]},
            {"@type": "SoftwareApplication", "@id": p["url"] + "#app", "name": "Pebble Desk", "url": p["url"],
             "description": p["desc"], "inLanguage": lang,
             "applicationCategory": "UtilitiesApplication", "applicationSubCategory": "Launcher",
             "softwareVersion": VERSION, "datePublished": FIRST, "dateModified": RELEASED,
             "releaseNotes": f"{REPO}/releases/tag/v{VERSION}",
             "operatingSystem": "Android 9.0 or later",
             "offers": {"@type": "Offer", "price": "0", "priceCurrency": "KRW" if lang == "ko" else "USD"},
             "isAccessibleForFree": True, "downloadUrl": RELEASES, "installUrl": RELEASES, "codeRepository": REPO,
             "license": LICENSE, "image": og, "screenshot": shots, "featureList": p["features"],
             "keywords": keywords(lang),
             "author": {"@type": "Person", "name": "hissinger", "url": "https://github.com/hissinger"}},
            {"@type": "FAQPage", "@id": p["url"] + "#faq", "inLanguage": lang,
             "mainEntity": [{"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": a}} for q, a in p["faq"]]},
        ],
    }
    return f'''<title>{esc(p["title"])}</title>
<meta name="description" content="{esc(p["desc"])}">
<meta name="keywords" content="{esc(keywords(lang))}">
<link rel="canonical" href="{p["url"]}">
<link rel="alternate" hreflang="{lang}" href="{p["url"]}">
<link rel="alternate" hreflang="{other}" href="{o["url"]}">
<link rel="alternate" hreflang="x-default" href="{BASE}">
<meta name="theme-color" content="#fbfbf9">
<link rel="icon" href="{icon}icon-32.png" sizes="32x32" type="image/png">
<link rel="icon" href="{icon}icon-192.png" sizes="192x192" type="image/png">
<link rel="apple-touch-icon" href="{icon}icon-180.png">
<meta property="og:type" content="website">
<meta property="og:site_name" content="Pebble Desk">
<meta property="og:title" content="{esc(p["title"])}">
<meta property="og:description" content="{esc(p["desc"])}">
<meta property="og:url" content="{p["url"]}">
<meta property="og:image" content="{og}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:locale" content="{p["locale"]}">
<meta property="og:locale:alternate" content="{o["locale"]}">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="{esc(p["title"])}">
<meta name="twitter:description" content="{esc(p["desc"])}">
<meta name="twitter:image" content="{og}">
<script type="application/ld+json">
{json.dumps(data, ensure_ascii=False, indent=1)}
</script>
<!-- Cloudflare Web Analytics -->
<script type="module" src="https://static.cloudflareinsights.com/beacon.min.js" data-cf-beacon='{{"token": "{CF_BEACON}"}}'></script>
'''


def faq_section(p):
    items = "\n".join(f"      <div><dt>{esc(q)}</dt><dd>{esc(a)}</dd></div>" for q, a in p["faq"])
    return f'''  <section id="faq">
    <h2>{esc(p["faq_title"])}</h2>
    <dl class="faq">
{items}
    </dl>
  </section>
'''


for lang, p in PAGES.items():
    s = open(p["file"]).read()
    # <head>: viewport 뒤부터 <style> 앞까지를 새로 쓴다
    a = s.index('<meta name="viewport"')
    a = s.index("\n", a) + 1
    b = s.index("<style>")
    s = s[:a] + head(lang, p) + s[b:]
    # 자주 묻는 질문(스타일·구역)과 그 아래 마지막 받기 버튼
    s = re.sub(r"  /\* 자주 묻는 질문 \*/\n(?:  \.faq[^\n]*\n)+\n*", "", s)
    s = s.replace("  footer {", FAQ_CSS + "\n  footer {", 1)
    s = re.sub(r'  <section id="faq">.*?</section>\n', "", s, flags=re.S)
    s = re.sub(r'    <div class="actions" style="margin-top: \d+px">\n.*?</div>\n', "", s, flags=re.S)
    cta = f'    <div class="actions" style="margin-top: 56px">\n      <a class="btn" href="{RELEASES}">{p["cta"]}</a>\n    </div>\n'
    s = s.replace("</main>", faq_section(p).replace("  </section>\n", cta + "  </section>\n") + "</main>", 1)
    open(p["file"], "w").write(s)

open("site/robots.txt", "w").write(f"User-agent: *\nAllow: /\n\nSitemap: {BASE}sitemap.xml\n")
alts = "".join(f'    <xhtml:link rel="alternate" hreflang="{l}" href="{q["url"]}"/>\n' for l, q in PAGES.items()) + \
    f'    <xhtml:link rel="alternate" hreflang="x-default" href="{BASE}"/>\n'
urls = "".join(f"  <url>\n    <loc>{q['url']}</loc>\n    <lastmod>{RELEASED}</lastmod>\n{alts}  </url>\n" for q in PAGES.values())
open("site/sitemap.xml", "w").write(
    '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n'
    + urls + "</urlset>\n")

# llms.txt(https://llmstxt.org): AI 검색이 읽는 요약
en, ko = PAGES["en"], PAGES["ko"]
llms = f"""# Pebble Desk

> {en["desc"]}

Pebble Desk (페블 데스크) is a home-screen app (launcher) for Android E Ink readers.
The home screen shows the book you are reading, three more books you are reading, favorite apps and today’s reading time.
It runs on Android 9 or later and is free with no ads (PolyForm Noncommercial 1.0.0).
Latest version: {VERSION} ({RELEASED}), first released {FIRST}.

## Features

{chr(10).join("- " + f for f in en["features"])}
- Supported reading apps (auto-add): Kindle, Kobo Books, Libby, KOReader, Readest, BookFusion, Moon+ Reader, EzViewer (이지뷰어), CherieViewer (채리뷰어), Woojoo (우주뷰어), Google Play Books, Mihon, Kahon, Futon, Kotatsu, Librera, Wattpad, Kyobo Library, Kyobo eBook, millie (밀리의서재), Aladin, Bookers, RIDI, YES24 Library, my YES, YES24 eBook, ECO Viewer 3.0 (이씨오 범용뷰어 3.0), the built-in readers of BOOX (NeoReader) and iReader devices

## Compared with other launchers

General Android launchers such as {", ".join(e for _, e in LAUNCHERS[:11])} are designed for phones: icons, widgets, scrolling and animation.
Pebble Desk is an alternative made for E Ink e-readers: the book you are reading sits on the home screen, lists turn page by page, nothing animates, and reading time is kept per book.
It is an independent app and is not affiliated with any of these launchers, BOOX or Crema.

## E-readers

Runs on Android E Ink readers with Android 9 or later. In use on: {", ".join(e for _, e, st in DEVICES if st == "used")}.
Other Android E Ink readers (it runs on those with Android 9 or later; older models such as early Crema and RIDIPAPER run older Android; not tested): {", ".join(f"{e} ({k})" for k, e, st in DEVICES if st == "android")}.
Does not run on (these can't install Android apps, or run Android 8 or older): {", ".join(f"{e} ({k})" for k, e, st in DEVICES if st == "no")}. On these, use the reading app’s own features instead.

## FAQ

{chr(10).join(f"- **{q}** {a}" for q, a in en["faq"])}

## 한국어 요약

{ko["desc"]}

- 최신 판: {VERSION}({RELEASED}), 첫 판 {FIRST}
- 다른 런처와 비교: {", ".join(k for k, _ in LAUNCHERS[:11])} 같은 일반 안드로이드 런처는 휴대폰용(아이콘·위젯·스크롤·애니메이션)이다. Pebble Desk 는 이북리더기용 대안으로, 읽는 책이 홈에 있고 목록은 쪽으로 넘기며 움직임이 없고 읽은 시간이 책마다 남는다. 이 런처들, BOOX, 크레마와는 관계없는 독립 앱이다.
- 쓸 수 있는 기기: 안드로이드 9 이상의 전자잉크 이북리더기. 실제로 쓰는 기기: {", ".join(k for k, _, st in DEVICES if st == "used")}. 그 밖의 안드로이드 이북리더기(안드로이드 9 이상인 기기에서 쓸 수 있다. 옛 크레마·리디페이퍼 등은 더 낮은 안드로이드다. 시험하지 않음): {", ".join(k for k, _, st in DEVICES if st == "android")}
- 쓸 수 없는 기기(안드로이드 앱을 깔 수 없거나 안드로이드 8 이하): {", ".join(k for k, _, st in DEVICES if st == "no")}

{chr(10).join(f"- **{q}** {a}" for q, a in ko["faq"])}

## Links

- [Homepage (English)]({en["url"]})
- [홈페이지(한국어)]({ko["url"]})
- [Download (GitHub Releases)]({RELEASES})
- [Source code and README]({REPO})
- [Troubleshooting (Korean)]({REPO}/blob/main/docs/TROUBLESHOOTING.md)
"""
open("site/llms.txt", "w").write(llms)
print("ok")
