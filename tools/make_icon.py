#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LOHEN — generateur d'icone de lancement.

Aucune dependance externe : l'icone est rasterisee ici, pixel par pixel, puis
ecrite en PNG par un encodeur maison (zlib + CRC). Le dessin suit la direction
artistique du chapitre 1 (BLOC 05) :

  - la nuit de Velmora, bleu tres profond, jamais de noir pur ;
  - le Phare, seule source d'ambre (#FFA33C) — l'ambre est reserve a Esteban
    et a sa lumiere (05.06) ;
  - le cyan (#6BF2D8) des Figures, en liseré d'horizon seulement (05.07) ;
  - aucun contour, aucun texte : l'icone doit se lire a 48 px.

Sorties :
  android/res/drawable-nodpi/ic_launcher_foreground.png  (432x432, transparent)
  android/res/mipmap-*/ic_launcher.png                   (48/72/96/144/192)
"""
import math
import os
import struct
import zlib

ROOT = "/home/user/Space/android/res"

AMBRE = (0xFF, 0xA3, 0x3C)
CYAN = (0x6B, 0xF2, 0xD8)
NUIT_HAUT = (0x0A, 0x11, 0x22)
NUIT_BAS = (0x04, 0x06, 0x0B)
PIERRE = (0x2B, 0x28, 0x24)
PIERRE_CLAIRE = (0x4A, 0x42, 0x38)
PAPIER = (0xE8, 0xDF, 0xCE)


# --------------------------------------------------------------------------
# Encodeur PNG minimal
# --------------------------------------------------------------------------
def write_png(path, w, h, buf):
    """buf : bytearray de w*h pixels RGBA."""
    raw = bytearray()
    stride = w * 4
    for y in range(h):
        raw.append(0)
        raw += buf[y * stride:(y + 1) * stride]

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        out += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return out

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    return len(png)


def lerp(a, b, t):
    return a + (b - a) * t


def clamp01(x):
    return 0.0 if x < 0.0 else (1.0 if x > 1.0 else x)


def mix(c1, c2, t):
    return (lerp(c1[0], c2[0], t), lerp(c1[1], c2[1], t), lerp(c1[2], c2[2], t))


def smooth(a, b, x):
    t = clamp01((x - a) / (b - a)) if b != a else (1.0 if x >= b else 0.0)
    return t * t * (3.0 - 2.0 * t)


# --------------------------------------------------------------------------
# Le dessin
# --------------------------------------------------------------------------
STARS = [(0.14, 0.16, 0.55), (0.28, 0.09, 0.35), (0.41, 0.22, 0.28),
         (0.72, 0.12, 0.45), (0.86, 0.24, 0.32), (0.62, 0.06, 0.25),
         (0.08, 0.33, 0.22), (0.93, 0.41, 0.28), (0.35, 0.36, 0.18),
         (0.52, 0.14, 0.20)]


def tower_alpha(u, v):
    """Distance signee approchee du Phare : >0 dedans."""
    top, base = 0.245, 0.80
    if v < top or v > base:
        return -1.0
    t = (v - top) / (base - top)
    cx = 0.545 + t * 0.006
    half = lerp(0.030, 0.062, t * t)
    return half - abs(u - cx)


def lamp_glow(u, v):
    """Halo de la lanterne : l'ambre d'Esteban, seule source chaude."""
    dx = (u - 0.545) * 1.0
    dy = (v - 0.222) * 1.35
    d = math.sqrt(dx * dx + dy * dy)
    core = smooth(0.075, 0.0, d)
    halo = smooth(0.42, 0.02, d) * 0.55
    return clamp01(core + halo)


def beam(u, v):
    """Le faisceau balaie vers la gauche, a -7 degres, bord doux."""
    ox, oy = 0.545, 0.222
    dx, dy = u - ox, v - oy
    if dx > -0.005:
        return 0.0
    ang = math.atan2(dy, dx)
    centre = math.radians(187.0)
    spread = math.radians(9.5) + (-dx) * 0.10
    d = abs(ang - centre)
    if d > spread:
        return 0.0
    edge = 1.0 - smooth(spread * 0.45, spread, d)
    atten = smooth(0.95, 0.05, -dx)
    return clamp01(edge * atten * 0.5)


def sea_glow(u, v):
    """Le liseré cyan des Figures sur l'horizon : jamais une nappe."""
    d = abs(v - 0.815)
    line = smooth(0.012, 0.0, d)
    haze = smooth(0.16, 0.0, d) * 0.22
    return clamp01((line * 0.85 + haze) * (0.35 + 0.65 * smooth(0.05, 0.9, u)))


def star_field(u, v):
    best = 0.0
    for sx, sy, si in STARS:
        d = math.hypot((u - sx) * 1.0, (v - sy) * 1.4)
        best = max(best, smooth(0.012, 0.0, d) * si)
    return best


def shade_full(u, v, legacy):
    """Une pixel de l'icone complete (fond + decor). Retourne (r,g,b,a)."""
    a = 1.0
    if legacy:
        # coins adoucis : les launchers anciens ne masquent pas l'icone
        cu, cv = abs(u - 0.5) * 2.0, abs(v - 0.5) * 2.0
        corner = max(cu, cv)
        rad = math.hypot(max(0.0, cu - 0.72), max(0.0, cv - 0.72))
        if corner > 0.72:
            a = smooth(0.30, 0.20, rad)
            if a <= 0.0:
                return (0, 0, 0, 0)

    col = mix(NUIT_HAUT, NUIT_BAS, smooth(0.0, 1.0, v))
    # le halo de la ville, tres bas, tres discret
    col = mix(col, (0x14, 0x1B, 0x2C), smooth(0.95, 0.55, v) * 0.35)

    s = star_field(u, v)
    if s > 0:
        col = mix(col, PAPIER, s * 0.85)

    b = beam(u, v)
    if b > 0:
        col = mix(col, AMBRE, b * 0.75)

    sea = sea_glow(u, v)
    if sea > 0:
        col = mix(col, CYAN, sea * 0.55)

    t = tower_alpha(u, v)
    if t > -0.02:
        edge = smooth(-0.012, 0.006, t)
        # la face gauche recoit la lumiere de la lanterne
        lit = smooth(0.545, 0.470, u)
        stone = mix(PIERRE, PIERRE_CLAIRE, lit * 0.85)
        stone = mix(stone, AMBRE, lamp_glow(u, v) * 0.18)
        col = mix(col, stone, edge)
        # la chambre de veille : une bande claire sous le sommet
        if 0.245 < v < 0.268 and edge > 0.5:
            col = mix(col, PAPIER, 0.20)

    # lanterne : le coeur d'ambre
    g = lamp_glow(u, v)
    if g > 0:
        col = mix(col, AMBRE, g * 0.92)
        col = mix(col, (0xFF, 0xE2, 0xB4), smooth(0.75, 1.0, g) * 0.6)

    # base du Phare, rocher sombre
    if v > 0.80:
        rock = smooth(0.80, 0.86, v)
        col = mix(col, (0x07, 0x09, 0x0E), rock * 0.85)

    # vignettage : l'oeil reste au centre
    vig = smooth(0.95, 0.35, math.hypot((u - 0.5) * 1.15, (v - 0.5) * 1.15))
    col = mix((col[0] * 0.55, col[1] * 0.55, col[2] * 0.55), col, vig)

    return (int(clamp01(col[0] / 255.0) * 255),
            int(clamp01(col[1] / 255.0) * 255),
            int(clamp01(col[2] / 255.0) * 255),
            int(a * 255))


def shade_foreground(u, v):
    """Premier plan adaptatif : decor seul, sur fond transparent.

    Le canevas fait 108 dp dont 72 dp de zone sure : le dessin est replie dans
    les 62 % centraux pour survivre au masquage carre, rond ou squircle.
    """
    # u,v dans [0,1] sur 108 dp -> on remappe sur la zone sure
    su = (u - 0.5) / 0.62 + 0.5
    sv = (v - 0.5) / 0.62 + 0.5
    if su < -0.05 or su > 1.05 or sv < -0.05 or sv > 1.05:
        return (0, 0, 0, 0)

    col = (0.0, 0.0, 0.0)
    alpha = 0.0

    b = beam(su, sv)
    if b > 0:
        col = mix(col, AMBRE, 1.0)
        alpha = max(alpha, b * 0.85)

    sea = sea_glow(su, sv)
    if sea > 0:
        col = mix(col, CYAN, clamp01(sea * 1.4))
        alpha = max(alpha, sea * 0.9)

    t = tower_alpha(su, sv)
    if t > -0.02:
        edge = smooth(-0.012, 0.006, t)
        lit = smooth(0.545, 0.470, su)
        stone = mix(PIERRE, PIERRE_CLAIRE, lit * 0.85)
        col = mix(col, stone, edge)
        alpha = max(alpha, edge)

    g = lamp_glow(su, sv)
    if g > 0:
        hot = mix(AMBRE, (0xFF, 0xE2, 0xB4), smooth(0.7, 1.0, g))
        col = mix(col, hot, clamp01(g * 1.1))
        alpha = max(alpha, clamp01(g * 1.15))

    if sv > 0.80:
        rock = smooth(0.80, 0.90, sv)
        col = mix(col, (0x07, 0x09, 0x0E), rock)
        alpha = max(alpha, rock * 0.9)

    if alpha <= 0.001:
        return (0, 0, 0, 0)
    return (int(col[0]), int(col[1]), int(col[2]), int(clamp01(alpha) * 255))


def render(path, size, shader, supersample=3):
    """Rasterise avec sur-echantillonnage puis filtre boite : bords propres."""
    big = size * supersample
    acc = bytearray(size * size * 4)
    row = [0] * (supersample * big * 4)
    inv = 1.0 / (supersample * supersample)
    for y in range(size):
        for sy in range(supersample):
            v = (y * supersample + sy + 0.5) / big
            base = sy * big * 4
            for x in range(big):
                u = (x + 0.5) / big
                r, g, b, a = shader(u, v)
                o = base + x * 4
                row[o] = r
                row[o + 1] = g
                row[o + 2] = b
                row[o + 3] = a
        for x in range(size):
            rs = gs = bs = as_ = 0
            for sy in range(supersample):
                o = sy * big * 4 + x * supersample * 4
                for sx in range(supersample):
                    p = o + sx * 4
                    a = row[p + 3]
                    # premultiplie : les bords transparents ne virent pas au noir
                    rs += row[p] * a
                    gs += row[p + 1] * a
                    bs += row[p + 2] * a
                    as_ += a
            o = (y * size + x) * 4
            if as_ <= 0:
                acc[o:o + 4] = b"\x00\x00\x00\x00"
            else:
                acc[o] = int(rs / as_)
                acc[o + 1] = int(gs / as_)
                acc[o + 2] = int(bs / as_)
                acc[o + 3] = int(as_ * inv)
    n = write_png(path, size, size, acc)
    print("  %-58s %4dx%-4d %7d o" % (os.path.relpath(path, "/home/user/Space"),
                                      size, size, n))


def main():
    print("Icone LOHEN (direction artistique 05.06 / 05.07)")
    render(os.path.join(ROOT, "drawable-nodpi/ic_launcher_foreground.png"),
           432, shade_foreground, supersample=2)
    for folder, size in (("mipmap-mdpi", 48), ("mipmap-hdpi", 72),
                         ("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144),
                         ("mipmap-xxxhdpi", 192)):
        render(os.path.join(ROOT, folder, "ic_launcher.png"),
               size, lambda u, v: shade_full(u, v, True),
               supersample=3 if size <= 96 else 2)
    print("Icone terminee.")


if __name__ == "__main__":
    main()
