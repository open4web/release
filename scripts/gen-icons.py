#!/usr/bin/env python3
"""根据 apps/<key>/app.json 生成占位图标（Android mipmap、Windows .ico、1024px PNG）。

如果 apps/<key>/icon-source.png 存在（建议 1024x1024 透明或满版 PNG），就用它代替文字占位图标。
用法：python3 scripts/gen-icons.py [x5 x6 ...]
依赖：Pillow、Noto Sans CJK 字体（仅文字占位图标需要）。
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def text_glyph(size, text, color=(255, 255, 255, 255), scale=0.56):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, int(size * scale), index=2)  # SC
    box = d.textbbox((0, 0), text, font=font)
    w, h = box[2] - box[0], box[3] - box[1]
    d.text(((size - w) / 2 - box[0], (size - h) / 2 - box[1]), text, font=font, fill=color)
    return img


def full_icon(size, cfg, src):
    bg = hex_rgb(cfg["themeColor"])
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.22), fill=255)
    tile = Image.new("RGBA", (size, size), bg + (255,))
    img.paste(tile, (0, 0), mask)
    if src is not None:
        inner = src.resize((int(size * 0.72),) * 2, Image.LANCZOS)
        off = (size - inner.size[0]) // 2
        img.alpha_composite(inner, (off, off))
    else:
        img.alpha_composite(text_glyph(size, cfg["iconText"]))
    return img


def round_icon(size, cfg, src):
    bg = hex_rgb(cfg["themeColor"])
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(img).ellipse((0, 0, size - 1, size - 1), fill=bg + (255,))
    if src is not None:
        inner = src.resize((int(size * 0.62),) * 2, Image.LANCZOS)
        off = (size - inner.size[0]) // 2
        img.alpha_composite(inner, (off, off))
    else:
        img.alpha_composite(text_glyph(size, cfg["iconText"], scale=0.5))
    return img


def foreground(size, cfg, src):
    # 自适应图标前景：108dp 画布，安全区 66dp
    if src is not None:
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = src.resize((int(size * 0.55),) * 2, Image.LANCZOS)
        off = (size - inner.size[0]) // 2
        img.alpha_composite(inner, (off, off))
        return img
    return text_glyph(size, cfg["iconText"], scale=0.36)


def gen(key):
    adir = os.path.join(ROOT, "apps", key)
    cfg = json.load(open(os.path.join(adir, "app.json"), encoding="utf-8"))
    sp = os.path.join(adir, "icon-source.png")
    src = Image.open(sp).convert("RGBA") if os.path.exists(sp) else None

    full_icon(1024, cfg, src).save(os.path.join(adir, "icon.png"))
    ico = full_icon(256, cfg, src)
    ico.save(os.path.join(adir, "icon.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])

    res = os.path.join(adir, "android-res")
    for name, f in DENSITIES.items():
        d = os.path.join(res, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        full_icon(int(48 * f), cfg, src).save(os.path.join(d, "ic_launcher.png"))
        round_icon(int(48 * f), cfg, src).save(os.path.join(d, "ic_launcher_round.png"))
        foreground(int(108 * f), cfg, src).save(os.path.join(d, "ic_launcher_foreground.png"))
    any26 = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(any26, exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@color/ic_launcher_background" />\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
           '</adaptive-icon>\n')
    for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
        open(os.path.join(any26, n), "w").write(xml)
    vals = os.path.join(res, "values")
    os.makedirs(vals, exist_ok=True)
    open(os.path.join(vals, "ic_launcher_background.xml"), "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{cfg["themeColor"]}</color>\n</resources>\n')
    print("icons generated for", key)


if __name__ == "__main__":
    keys = sys.argv[1:] or sorted(k for k in os.listdir(os.path.join(ROOT, "apps"))
                                  if os.path.isfile(os.path.join(ROOT, "apps", k, "app.json")))
    for k in keys:
        gen(k)
