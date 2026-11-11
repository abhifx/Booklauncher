#!/usr/bin/env python3
"""홈페이지 공유 미리보기 그림(site/img/og-ko.png·og-en.png, 1200×630), README 그림(첫 그림 docs/screenshots/hero-ko.png·hero-en.png(흑백),
지원 앱 아이콘 docs/screenshots/app-icons/(site/img/apps/ 를 96px 로))과 아이콘(site/img/icon-*.png, site/favicon.ico)을 만든다.

    python3 tools/site/og.py <BookLauncher 아이콘 512px PNG>

기기 그림은 페이지와 같다: 기기 틀(frame-*.webp) 화면 자리에 캡처를 곱하기로 얹는다(자리는 site/index.html 의 .shot 과 같다).
아이콘은 에뮬에서 IconDump 로 com.woody.booklauncher 를 그린 것.
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

IMG = "site/img"
BG = (251, 251, 249)
INK = (17, 17, 17)
SUB = (107, 107, 104)
SANS = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
# 기기: 틀, 화면 자리(틀 대비 left·top·width·height), 화면 모서리(화면 폭 대비)
DEVICES = {"pebble": ("frame-pebble", (0.1020, 0.0854, 0.7912, 0.7594), 0), "bar": ("frame-palma", (0.1037, 0.0717, 0.7949, 0.8433), 0.046)}
TEXT = {
    "ko": ("이북리더기를 위한 런처", ["지금 읽는 책을", "홈 화면에."], ""),
    "en": ("A launcher for Android E Ink readers", ["The book you’re", "reading, on your", "home screen."], "en-"),
}


def device(name, prefix, height):
    frame_file, (l, t, w, h), radius = DEVICES[name]
    frame = Image.open(f"{IMG}/{frame_file}.webp").convert("RGBA")
    frame = frame.resize((round(frame.width * height / frame.height), height), Image.LANCZOS)
    box = (round(l * frame.width), round(t * frame.height), round((l + w) * frame.width), round((t + h) * frame.height))
    size = (box[2] - box[0], box[3] - box[1])
    shot = Image.open(f"{IMG}/{prefix}{name}-home.webp").convert("RGB").resize(size, Image.LANCZOS)
    under = frame.crop(box).convert("RGB")
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=round(radius * size[0]), fill=255)
    frame.paste(ImageChops.multiply(under, shot), box, mask)
    return frame


def og(lang):
    kicker, lines, prefix = TEXT[lang]
    im = Image.new("RGB", (1200, 630), BG)
    d = ImageDraw.Draw(im)
    x = 72
    d.text((x, 92), "BookLauncher", font=ImageFont.truetype(SANS, 30, index=6), fill=INK)
    d.text((x, 146), kicker, font=ImageFont.truetype(SANS, 22, index=2), fill=SUB)
    big = ImageFont.truetype(SANS, 60 if lang == "ko" else 48, index=6)
    y = 230
    for line in lines:
        d.text((x, y), line, font=big, fill=INK)
        y += 78 if lang == "ko" else 62
    # 오른쪽: 기기 두 대(글자 칸 왼쪽 약 430px 을 비운다)
    peb, bar = device("pebble", prefix, 500), device("bar", prefix, 500)
    right = 1200 - 48
    bx = right - bar.width
    px = bx - 28 - peb.width
    im.paste(peb, (px, 65), peb)
    im.paste(bar, (bx, 65), bar)
    im.save(f"{IMG}/og-{lang}.png", optimize=True)


def hero(lang):
    """README 첫 그림: 페이지 첫 화면처럼 바탕에 기기 두 대(사이 .devices gap, 둘레 여백, 옅은 그림자)"""
    prefix = TEXT[lang][2]
    h = 900
    gap, side, top, bottom = round(h * 0.08), round(h * 0.18), round(h * 0.07), round(h * 0.12)
    peb, bar = device("pebble", prefix, h), device("bar", prefix, h)
    im = Image.new("RGBA", (side * 2 + peb.width + gap + bar.width, top + h + bottom), BG + (255,))
    spots = ((peb, side), (bar, side + peb.width + gap))
    shadow = Image.new("RGBA", im.size, (0, 0, 0, 0))
    for d, x in spots:
        shadow.paste(Image.new("RGBA", d.size, (0, 0, 0, 255)), (x, top + 26), d.getchannel("A").point(lambda v: v * 0.16))
    im = Image.alpha_composite(im, shadow.filter(ImageFilter.GaussianBlur(24)))
    for d, x in spots:
        im.alpha_composite(d, (x, top))
    im.convert("L").save(f"docs/screenshots/hero-{lang}.png", optimize=True)


def app_icons():
    """README 지원 앱 표의 아이콘: 48px 로 보이므로 96px 로 줄여 저장소에(홈페이지 그림을 부르지 않게)"""
    out = "docs/screenshots/app-icons"
    icons = sorted(f for f in os.listdir(f"{IMG}/apps") if f.endswith(".png"))
    os.makedirs(out, exist_ok=True)
    for f in os.listdir(out):  # 홈페이지에서 빠진 앱의 아이콘을 남기지 않는다
        if f.endswith(".png"):
            os.remove(f"{out}/{f}")
    for f in icons:
        Image.open(f"{IMG}/apps/{f}").convert("RGBA").resize((96, 96), Image.LANCZOS).save(f"{out}/{f}", optimize=True)


icon = Image.open(sys.argv[1]).convert("RGBA")
# 192 는 Google 검색 결과의 사이트 아이콘(48 의 배수여야 한다), favicon.ico 는 /favicon.ico 를 바로 찾는 브라우저·로봇용
for px in (32, 180, 192, 512):
    icon.resize((px, px), Image.LANCZOS).save(f"{IMG}/icon-{px}.png", optimize=True)
icon.save("site/favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
for lang in TEXT:
    og(lang)
    hero(lang)
app_icons()
print("ok")
