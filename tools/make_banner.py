"""Render module/banner.png, shown on the module card in KernelSU Next.

Usage: python tools/make_banner.py [output.png]
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "art", "banner-art.png")
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "module", "banner.png")

W, H = 1280, 512
S = 2  # supersampling
FONTS = "C:/Windows/Fonts"

# Colours from the logo: puzzle blue, yellow, red.
BLUE = (110, 200, 240)
YELLOW = (250, 214, 90)
RED = (240, 100, 110)


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size * S)


def glow(img, x, y, r, color, alpha):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([(x - r) * S, (y - r) * S, (x + r) * S, (y + r) * S], fill=color + (alpha,))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(r * S * .55)))


def chip(img, x, y, text, f):
    # Translucent fill must be composited, not written over the pixels.
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    l, t, r, b = d.textbbox((0, 0), text, font=f)
    pw, ph = 16 * S, 8 * S
    w, h = r - l + 2 * pw, b - t + 2 * ph
    d.rounded_rectangle([x * S, y * S, x * S + w, y * S + h], radius=h // 2,
                        fill=(255, 255, 255, 22), outline=(255, 255, 255, 46), width=S)
    img.alpha_composite(layer)
    ImageDraw.Draw(img).text((x * S + pw - l, y * S + ph - t), text, font=f, fill=(225, 225, 232, 255))
    return x + w / S


def main():
    img = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 255))
    glow(img, 140, 30, 320, BLUE, 70)
    glow(img, 1050, 110, 330, YELLOW, 45)
    glow(img, 980, 520, 300, RED, 85)

    # Art on the right, with a soft shadow.
    art = Image.open(ART).convert("RGBA")
    art = art.crop(art.getbbox())
    side = 470 * S
    art.thumbnail((side, side), Image.LANCZOS)
    ax = (W - 40) * S - art.width
    ay = (H * S - art.height) // 2
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 170), (ax + 14 * S, ay + 24 * S), art)
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(22 * S)))
    img.alpha_composite(art, (ax, ay))

    d = ImageDraw.Draw(img)
    x = 72
    d.text((x * S, 120 * S), "CallCatcher", font=font("seguibl.ttf", 92), fill=(245, 245, 248, 255))
    d.text((x * S + 4 * S, 245 * S), "Phone & WhatsApp call recorder", font=font("seguisb.ttf", 34),
           fill=(190, 190, 200, 255))
    d.text((x * S + 4 * S, 293 * S), "with a built-in player", font=font("seguisb.ttf", 34),
           fill=(130, 130, 142, 255))

    f = font("seguisb.ttf", 21)
    cx = x
    for text in ("Privileged capture", "Opus", "LSPosed"):
        cx = chip(img, cx, 372, text, f) + 12

    img.resize((W, H), Image.LANCZOS).convert("RGB").save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
