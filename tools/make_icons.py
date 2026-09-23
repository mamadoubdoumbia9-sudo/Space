#!/usr/bin/env python3
"""Icônes de l'application (adaptive icon + legacy) et logo du splash : rose des vents à l'encre sur papier, dessinée en PIL."""
import math, os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
PAPER = (242, 233, 216, 255); INK = (27, 31, 42, 255); GARANCE = (182, 64, 58, 255); LAITON = (201, 162, 75, 255)
DENS = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def rose(size, fg_only=False, ink=INK, scale=1.0):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = size / 2
    R = size * 0.30 * scale
    # anneau
    d.ellipse([cx - R * 1.15, cy - R * 1.15, cx + R * 1.15, cy + R * 1.15], outline=ink, width=max(2, int(size * 0.012)))
    # huit pointes : les cardinales longues, les intermédiaires courtes ; moitié pleine / moitié vide (encre)
    for k in range(8):
        a = math.radians(k * 45 - 90)
        L = R if k % 2 == 0 else R * 0.55
        w = R * 0.16 if k % 2 == 0 else R * 0.1
        tip = (cx + L * math.cos(a), cy + L * math.sin(a))
        left = (cx + w * math.cos(a - math.pi / 2), cy + w * math.sin(a - math.pi / 2))
        right = (cx + w * math.cos(a + math.pi / 2), cy + w * math.sin(a + math.pi / 2))
        d.polygon([tip, left, (cx, cy)], fill=ink)
        d.polygon([tip, right, (cx, cy)], fill=PAPER if not fg_only else (242, 233, 216, 255), outline=ink)
    # le nord frotté à blanc, pointe garance : la boîte au bout du monde
    d.ellipse([cx - R * 0.09, cy - R * 0.09, cx + R * 0.09, cy + R * 0.09], fill=GARANCE)
    # ligne de côte à l'encre, en bas de l'anneau
    pts = []
    for i in range(40):
        t = i / 39
        x = cx - R * 0.9 + 1.8 * R * t
        y = cy + R * 1.45 + math.sin(t * 9) * R * 0.05 + math.sin(t * 23 + 1) * R * 0.02
        pts.append((x, y))
    d.line(pts, fill=ink, width=max(2, int(size * 0.008)))
    return img


def paper(size):
    img = Image.new("RGBA", (size, size), PAPER)
    # grain léger
    import random
    rnd = random.Random(3)
    px = img.load()
    for _ in range(size * size // 40):
        x, y = rnd.randrange(size), rnd.randrange(size)
        v = rnd.randint(-14, 8)
        r, g, b, a = px[x, y]
        px[x, y] = (max(0, min(255, r + v)), max(0, min(255, g + v)), max(0, min(255, b + v)), 255)
    return img.filter(ImageFilter.GaussianBlur(0.4))


def main():
    for dens, m in DENS.items():
        d = os.path.join(RES, f"mipmap-{dens}")
        os.makedirs(d, exist_ok=True)
        s = int(48 * m)
        legacy = paper(s)
        legacy.alpha_composite(rose(s, scale=0.95))
        # coins arrondis pour l'icône classique
        mask = Image.new("L", (s, s), 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=s * 0.2, fill=255)
        legacy.putalpha(mask)
        legacy.save(os.path.join(d, "ic_launcher.png"))
        a = int(108 * m)
        paper(a).save(os.path.join(d, "ic_launcher_background.png"))
        rose(a, fg_only=True, scale=0.62).save(os.path.join(d, "ic_launcher_foreground.png"))
        splash = int(160 * m)
        rose(splash, fg_only=True, ink=(242, 233, 216, 255), scale=0.8).save(os.path.join(d, "splash_logo.png"))
    # icône 512 pour la fiche et la documentation
    big = paper(512); big.alpha_composite(rose(512, scale=0.95))
    os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
    big.save(os.path.join(ROOT, "docs", "icon_512.png"))
    print("icônes écrites dans", RES)


if __name__ == "__main__":
    main()
